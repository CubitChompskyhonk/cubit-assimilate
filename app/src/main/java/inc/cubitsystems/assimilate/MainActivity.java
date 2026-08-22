package inc.cubitsystems.assimilate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_MEDIA = 1001;
    private static final int REQ_SAF = 1002;
    private static final String VAULT_URL = "https://drive.google.com/drive/folders/1OPZA65RyCorgJTFEnaOA05W6SzpB6h2U";
    private WebView webView;
    private int biometricFails = 0;
    private SharedPreferences prefs;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("assimilate", MODE_PRIVATE);
        webView = findViewById(R.id.webview);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "Assimilate");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void eval(String js) {
        if (webView != null) webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private File stagingDir() {
        File d = new File(getFilesDir(), "assimilate_staging");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private int copyUriToStaging(Uri uri, String name, JSONArray list) {
        File out = new File(stagingDir(), System.currentTimeMillis() + "_" + name);
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) return 0;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            JSONObject o = new JSONObject();
            o.put("name", out.getName());
            o.put("bytes", out.length());
            list.put(o);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private String stageCollection(Uri collection, String[] projection, String sort, int max, String label) {
        int staged = 0;
        JSONArray list = new JSONArray();
        try (Cursor c = getContentResolver().query(collection, projection, null, null, sort)) {
            if (c == null) return label + ": query null";
            int idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            while (c.moveToNext() && staged < max) {
                long id = c.getLong(idCol);
                String name = nameCol >= 0 ? c.getString(nameCol) : (label + "_" + id);
                if (name == null) name = label + "_" + id;
                Uri uri = ContentUris.withAppendedId(collection, id);
                staged += copyUriToStaging(uri, name, list);
            }
            writeQueueManifest(list, staged);
        } catch (Exception e) {
            return label + " error: " + e.getMessage();
        }
        return "Staged " + staged + " " + label;
    }

    private void writeQueueManifest(JSONArray list, int staged) throws Exception {
        JSONObject manifest = new JSONObject();
        manifest.put("vault_folder", BuildConfig.DRIVE_PHOTOS_FOLDER_ID);
        manifest.put("count", staged);
        manifest.put("files", list);
        manifest.put("oauth_configured", BuildConfig.OAUTH_WEB_CLIENT_ID != null && !BuildConfig.OAUTH_WEB_CLIENT_ID.isEmpty());
        try (FileOutputStream fos = new FileOutputStream(new File(stagingDir(), "queue.json"))) {
            fos.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private boolean hasAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private String stageDownloadsTierD(int max) {
        if (!hasAllFilesAccess()) return "Tier D all-files access not granted — open All files access first";
        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dl == null || !dl.isDirectory()) return "Downloads folder unavailable";
        int staged = 0;
        JSONArray list = new JSONArray();
        File[] files = dl.listFiles();
        if (files == null) return "Downloads empty or unreadable";
        for (File f : files) {
            if (!f.isFile() || staged >= max) continue;
            try (InputStream in = new FileInputStream(f);
                 OutputStream os = new FileOutputStream(new File(stagingDir(), System.currentTimeMillis() + "_" + f.getName()))) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                staged++;
                JSONObject o = new JSONObject();
                o.put("name", f.getName());
                o.put("bytes", f.length());
                list.put(o);
            } catch (Exception ignored) {}
        }
        try { writeQueueManifest(list, staged); } catch (Exception ignored) {}
        return "Staged " + staged + " from Downloads (Tier D)";
    }

    private void showBiometric(Runnable onOk) {
        BiometricManager bm = BiometricManager.from(this);
        int can = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            onOk.run();
            return;
        }
        Executor ex = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, ex, new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                biometricFails = 0;
                onOk.run();
            }
            @Override public void onAuthenticationError(int code, @NonNull CharSequence err) {
                eval("onPermissionResult('Biometric required for this scope')");
            }
            @Override public void onAuthenticationFailed() {
                biometricFails++;
            }
        });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Cubit Assimilate")
                .setSubtitle("Founder key required for expanded access")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
        prompt.authenticate(info);
    }

    private void shareStagedFiles() {
        File dir = stagingDir();
        File[] files = dir.listFiles((d, n) -> n != null && !n.equals("queue.json"));
        ArrayList<Uri> uris = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile()) continue;
                try {
                    uris.add(FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f));
                } catch (Exception ignored) {}
            }
        }
        if (uris.isEmpty()) {
            eval("onPermissionResult('No staged files')");
            return;
        }
        Intent share = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        share.setType("*/*");
        if (uris.size() == 1) share.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        else share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Send staged files to Drive / …"));
        eval("onPermissionResult('Share sheet: " + uris.size() + " file(s)')");
    }

    public class Bridge {
        @JavascriptInterface
        public void requestMediaPermissions(String ignored) {
            runOnUiThread(() -> {
                List<String> need = new ArrayList<>();
                if (Build.VERSION.SDK_INT >= 33) {
                    need.add(Manifest.permission.POST_NOTIFICATIONS);
                    need.add(Manifest.permission.READ_MEDIA_IMAGES);
                    need.add(Manifest.permission.READ_MEDIA_VIDEO);
                    need.add(Manifest.permission.READ_MEDIA_AUDIO);
                } else {
                    need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
                ActivityCompat.requestPermissions(MainActivity.this, need.toArray(new String[0]), REQ_MEDIA);
            });
        }

        @JavascriptInterface
        public void requestBiometric(String ignored) {
            runOnUiThread(() -> showBiometric(() -> eval("onBiometricResult('1')")));
        }

        @JavascriptInterface
        public void requestSaf(String ignored) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(i, REQ_SAF);
            });
        }

        @JavascriptInterface
        public void requestAllFilesAccess(String ignored) {
            runOnUiThread(() -> showBiometric(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 30) {
                        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                        eval("onPermissionResult('Opened All files access — enable Cubit Assimilate, then return')");
                    } else {
                        eval("onPermissionResult('Use storage permission on this Android version')");
                    }
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                }
            }));
        }

        @JavascriptInterface
        public void startBackup(String scopes) {
            runOnUiThread(() -> {
                StringBuilder sb = new StringBuilder();
                if (scopes != null && scopes.contains("photos")) {
                    sb.append(stageCollection(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME},
                            MediaStore.Images.Media.DATE_ADDED + " DESC", 25, "photos")).append('\n');
                }
                if (scopes != null && scopes.contains("video")) {
                    sb.append(stageCollection(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            new String[]{MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME},
                            MediaStore.Video.Media.DATE_ADDED + " DESC", 10, "videos")).append('\n');
                }
                if (scopes != null && scopes.contains("audio")) {
                    sb.append(stageCollection(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME},
                            MediaStore.Audio.Media.DATE_ADDED + " DESC", 15, "audio")).append('\n');
                }
                if (scopes != null && scopes.contains("downloads")) {
                    sb.append(stageDownloadsTierD(20)).append('\n');
                }
                eval("onPermissionResult('" + sb.toString().replace("'", "").replace("\n", " | ") + "')");
                Intent svc = new Intent(MainActivity.this, UploadForegroundService.class);
                svc.setAction(UploadForegroundService.ACTION_START);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
                else startService(svc);
            });
        }

        @JavascriptInterface
        public void shareStaged(String ignored) {
            runOnUiThread(() -> shareStagedFiles());
        }

        @JavascriptInterface
        public void clearStaging(String ignored) {
            runOnUiThread(() -> {
                int n = 0;
                File[] files = stagingDir().listFiles();
                if (files != null) for (File f : files) if (f.delete()) n++;
                eval("onPermissionResult('Cleared " + n + "')");
            });
        }

        @JavascriptInterface
        public void setSchedule(String enabled) {
            prefs.edit().putBoolean("schedule_enabled", "1".equals(enabled) || "true".equalsIgnoreCase(enabled)).apply();
            eval("onPermissionResult('Schedule on boot: " + prefs.getBoolean("schedule_enabled", false) + "')");
        }

        @JavascriptInterface
        public void openVault(String ignored) {
            runOnUiThread(() -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(VAULT_URL))));
        }

        @JavascriptInterface
        public void openAppSettings(String ignored) {
            runOnUiThread(() -> {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            });
        }

        @JavascriptInterface
        public void openBatterySettings(String ignored) {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            });
        }

        @JavascriptInterface
        public void openUnknownSources(String ignored) {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 26) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName())));
                    }
                } catch (Exception ignored2) {}
            });
        }

        @JavascriptInterface
        public String getStatusJson(String ignored) {
            try {
                JSONObject o = new JSONObject();
                o.put("version", BuildConfig.VERSION_NAME);
                o.put("oauth_configured", BuildConfig.OAUTH_WEB_CLIENT_ID != null && !BuildConfig.OAUTH_WEB_CLIENT_ID.isEmpty());
                File[] files = stagingDir().listFiles((d, n) -> n != null && !n.equals("queue.json"));
                o.put("staged_count", files == null ? 0 : files.length);
                o.put("all_files_access", hasAllFilesAccess());
                o.put("schedule_enabled", prefs.getBoolean("schedule_enabled", false));
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                o.put("ignoring_battery_optimizations", pm != null && pm.isIgnoringBatteryOptimizations(getPackageName()));
                o.put("media_images", ContextCompat.checkSelfPermission(MainActivity.this,
                        Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED);
                o.put("media_audio", Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED);
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SAF && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(tree,
                        data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
            } catch (Exception ignored) {}
            DocumentFile root = DocumentFile.fromTreeUri(this, tree);
            int copied = 0;
            JSONArray list = new JSONArray();
            if (root != null) {
                for (DocumentFile f : root.listFiles()) {
                    if (!f.isFile() || copied >= 20) continue;
                    String name = f.getName() != null ? f.getName() : ("doc_" + copied);
                    copied += copyUriToStaging(f.getUri(), name, list);
                }
            }
            try { writeQueueManifest(list, copied); } catch (Exception ignored) {}
            eval("onPermissionResult('SAF staged " + copied + "')");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEDIA) {
            eval("onPermissionResult('Permission dialog complete — check Settings status')");
        }
    }
}
