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

    private String stageMedia(int maxItems) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                return "Media permission not granted";
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return "Storage permission not granted";
        }
        String[] projection = new String[]{ MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME };
        int staged = 0;
        JSONArray list = new JSONArray();
        try (Cursor c = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (c == null) return "MediaStore null";
            int idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            File dir = stagingDir();
            while (c.moveToNext() && staged < maxItems) {
                long id = c.getLong(idCol);
                String name = c.getString(nameCol);
                if (name == null) name = "img_" + id + ".jpg";
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                File out = new File(dir, System.currentTimeMillis() + "_" + name);
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream os = new FileOutputStream(out)) {
                    if (in == null) continue;
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    staged++;
                    JSONObject o = new JSONObject();
                    o.put("name", out.getName());
                    o.put("bytes", out.length());
                    list.put(o);
                } catch (Exception ignored) {}
            }
            writeQueueManifest(list, staged);
        } catch (Exception e) {
            return "Stage error: " + e.getMessage();
        }
        return "Staged " + staged + " photo(s)";
    }

    private void writeQueueManifest(JSONArray list, int staged) throws Exception {
        JSONObject manifest = new JSONObject();
        manifest.put("vault_folder", BuildConfig.DRIVE_PHOTOS_FOLDER_ID);
        manifest.put("count", staged);
        manifest.put("files", list);
        manifest.put("oauth_configured", BuildConfig.OAUTH_WEB_CLIENT_ID != null && !BuildConfig.OAUTH_WEB_CLIENT_ID.isEmpty());
        manifest.put("status", "staged");
        try (FileOutputStream fos = new FileOutputStream(new File(stagingDir(), "queue.json"))) {
            fos.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void showBiometric() {
        BiometricManager bm = BiometricManager.from(this);
        int can = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            eval("onBiometricResult('1')");
            return;
        }
        Executor ex = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, ex, new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                biometricFails = 0;
                eval("onBiometricResult('1')");
            }
            @Override public void onAuthenticationError(int code, @NonNull CharSequence err) {
                eval("onBiometricResult('0')");
                eval("onPermissionResult('Biometric: " + String.valueOf(err).replace("'", "") + "')");
            }
            @Override public void onAuthenticationFailed() {
                biometricFails++;
                if (biometricFails >= 3)
                    Toast.makeText(MainActivity.this, "Fingerprint mismatch — use device PIN", Toast.LENGTH_LONG).show();
            }
        });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Cubit Assimilate")
                .setSubtitle("Founder key — fingerprint, face, or PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
        prompt.authenticate(info);
    }

    /** Share staged files via system sheet — Founder can choose Drive. */
    private void shareStaged() {
        File dir = stagingDir();
        File[] files = dir.listFiles((d, n) -> n != null && !n.equals("queue.json"));
        ArrayList<Uri> uris = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile()) continue;
                try {
                    Uri u = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                    uris.add(u);
                } catch (Exception e) {
                    // fallback path
                }
            }
        }
        if (uris.isEmpty()) {
            Toast.makeText(this, "No staged files to share", Toast.LENGTH_SHORT).show();
            eval("onPermissionResult('No staged files')");
            return;
        }
        Intent share = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        share.setType("*/*");
        if (uris.size() == 1) share.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        else share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.putExtra(Intent.EXTRA_SUBJECT, "Cubit Assimilate backup");
        startActivity(Intent.createChooser(share, "Send staged files to Drive / …"));
        eval("onPermissionResult('Share sheet opened with " + uris.size() + " file(s)')");
    }

    public class Bridge {
        @JavascriptInterface
        public void requestMediaPermissions(String ignored) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                            Manifest.permission.POST_NOTIFICATIONS,
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO
                    }, REQ_MEDIA);
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_MEDIA);
                }
            });
        }

        @JavascriptInterface
        public void requestBiometric(String ignored) {
            runOnUiThread(() -> showBiometric());
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
        public void startBackup(String scopes) {
            runOnUiThread(() -> {
                String msg = "ok";
                if (scopes != null && scopes.contains("photos")) msg = stageMedia(25);
                eval("onPermissionResult('" + msg.replace("'", "") + "')");
                Intent svc = new Intent(MainActivity.this, UploadForegroundService.class);
                svc.setAction(UploadForegroundService.ACTION_START);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
                else startService(svc);
                eval("onPermissionResult('Foreground service started')");
            });
        }

        @JavascriptInterface
        public void shareStaged(String ignored) {
            runOnUiThread(() -> shareStaged());
        }

        @JavascriptInterface
        public void clearStaging(String ignored) {
            runOnUiThread(() -> {
                File dir = stagingDir();
                int n = 0;
                File[] files = dir.listFiles();
                if (files != null) for (File f : files) if (f.delete()) n++;
                eval("onPermissionResult('Cleared " + n + " staged item(s)')");
            });
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
                    } else {
                        startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Open settings manually for unknown apps", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public String getStatusJson(String ignored) {
            try {
                JSONObject o = new JSONObject();
                o.put("version", BuildConfig.VERSION_NAME);
                o.put("oauth_configured", BuildConfig.OAUTH_WEB_CLIENT_ID != null && !BuildConfig.OAUTH_WEB_CLIENT_ID.isEmpty());
                o.put("vault_folder", BuildConfig.DRIVE_VAULT_FOLDER_ID);
                o.put("photos_folder", BuildConfig.DRIVE_PHOTOS_FOLDER_ID);
                File[] files = stagingDir().listFiles((d, n) -> !n.equals("queue.json"));
                o.put("staged_count", files == null ? 0 : files.length);
                o.put("media_images", ContextCompat.checkSelfPermission(MainActivity.this,
                        Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED);
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                o.put("ignoring_battery_optimizations", pm != null && pm.isIgnoringBatteryOptimizations(getPackageName()));
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
            final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(tree, takeFlags);
            } catch (Exception ignored) {}
            prefs.edit().putString("saf_tree", tree.toString()).apply();
            DocumentFile root = DocumentFile.fromTreeUri(this, tree);
            int copied = 0;
            JSONArray list = new JSONArray();
            if (root != null) {
                for (DocumentFile f : root.listFiles()) {
                    if (!f.isFile() || copied >= 20) continue;
                    String name = f.getName() != null ? f.getName() : ("doc_" + copied);
                    File out = new File(stagingDir(), System.currentTimeMillis() + "_" + name);
                    try (InputStream in = getContentResolver().openInputStream(f.getUri());
                         OutputStream os = new FileOutputStream(out)) {
                        if (in == null) continue;
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                        copied++;
                        JSONObject o = new JSONObject();
                        o.put("name", out.getName());
                        o.put("bytes", out.length());
                        list.put(o);
                    } catch (Exception ignored) {}
                }
            }
            try { writeQueueManifest(list, copied); } catch (Exception ignored) {}
            eval("onPermissionResult('SAF: staged " + copied + " file(s)')");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEDIA) {
            boolean ok = grantResults.length > 0;
            for (int g : grantResults) if (g != PackageManager.PERMISSION_GRANTED) ok = false;
            eval("onPermissionResult('" + (ok ? "Permissions granted" : "Some permissions denied") + "')");
        }
    }
}
