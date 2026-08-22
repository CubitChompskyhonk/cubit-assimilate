package inc.cubitsystems.assimilate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
    private static final String VAULT_URL = "https://drive.google.com/drive/folders/1OPZA65RyCorgJTFEnaOA05W6SzpB6h2U";
    private WebView webView;
    private int biometricFails = 0;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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

    /** Enumerate recent media and copy a capped batch into app staging (upload queue). */
    private String stageMedia(int maxItems) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                return "Media permission not granted — nothing staged";
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return "Storage permission not granted — nothing staged";
        }
        String[] projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
        };
        String order = MediaStore.Images.Media.DATE_ADDED + " DESC";
        int staged = 0;
        JSONArray list = new JSONArray();
        try (Cursor c = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, order)) {
            if (c == null) return "MediaStore query returned null";
            int idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            File dir = stagingDir();
            while (c.moveToNext() && staged < maxItems) {
                long id = c.getLong(idCol);
                String name = c.getString(nameCol);
                if (name == null) name = "img_" + id + ".jpg";
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                File out = new File(dir, name);
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream os = new FileOutputStream(out)) {
                    if (in == null) continue;
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    staged++;
                    JSONObject o = new JSONObject();
                    o.put("name", name);
                    o.put("bytes", out.length());
                    list.put(o);
                } catch (Exception ignored) {}
            }
            // Write queue manifest for upload module
            JSONObject manifest = new JSONObject();
            manifest.put("vault", "founder-android-01/photos");
            manifest.put("vault_url", VAULT_URL);
            manifest.put("count", staged);
            manifest.put("files", list);
            manifest.put("status", "staged_pending_drive_upload");
            File mf = new File(dir, "queue.json");
            try (FileOutputStream fos = new FileOutputStream(mf)) {
                fos.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            return "Stage error: " + e.getMessage();
        }
        return "Staged " + staged + " photo(s) → app private queue (pending Drive upload)";
    }

    private void showBiometric() {
        BiometricManager bm = BiometricManager.from(this);
        int can = bm.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "No biometric/PIN available — continuing with soft gate", Toast.LENGTH_LONG).show();
            eval("onBiometricResult('1')");
            return;
        }
        Executor ex = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, ex, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                biometricFails = 0;
                eval("onBiometricResult('1')");
            }
            @Override
            public void onAuthenticationError(int code, @NonNull CharSequence err) {
                // User cancel / lockout — report, allow UI retry
                String msg = err != null ? err.toString() : ("error " + code);
                if (code == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        || code == BiometricPrompt.ERROR_USER_CANCELED) {
                    eval("onBiometricResult('0')");
                    eval("onPermissionResult('Biometric cancelled — use Retry or device PIN')");
                } else if (code == BiometricPrompt.ERROR_LOCKOUT
                        || code == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                    eval("onBiometricResult('0')");
                    eval("onPermissionResult('Too many attempts — unlock phone with PIN, then Retry')");
                } else {
                    eval("onBiometricResult('0')");
                    eval("onPermissionResult('Biometric: " + msg.replace("'", "") + "')");
                }
            }
            @Override
            public void onAuthenticationFailed() {
                // Fingerprint did not match — stay in prompt for another try; track count
                biometricFails++;
                if (biometricFails >= 3) {
                    Toast.makeText(MainActivity.this,
                            "Fingerprint not matching — try device PIN/password on next prompt",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Cubit Assimilate")
                .setSubtitle("Founder key — fingerprint, face, or device PIN")
                .setDescription("If fingerprint fails, choose PIN/password. Mismatch is logged for fix list.")
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
                                | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
        prompt.authenticate(info);
    }

    public class Bridge {
        @JavascriptInterface
        public void requestMediaPermissions(String ignored) {
            runOnUiThread(() -> {
                List<String> need = new ArrayList<>();
                if (Build.VERSION.SDK_INT >= 33) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                        need.add(Manifest.permission.READ_MEDIA_IMAGES);
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED)
                        need.add(Manifest.permission.READ_MEDIA_VIDEO);
                } else {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                        need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
                if (need.isEmpty()) {
                    eval("onPermissionResult('Media permissions already granted')");
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this, need.toArray(new String[0]), REQ_MEDIA);
                }
            });
        }

        @JavascriptInterface
        public void requestBiometric(String ignored) {
            runOnUiThread(() -> showBiometric());
        }

        @JavascriptInterface
        public void startBackup(String scopes) {
            runOnUiThread(() -> {
                String result = "scopes=" + scopes;
                if (scopes != null && scopes.contains("photos")) {
                    result = stageMedia(15);
                } else {
                    result = "No photos scope — skipped MediaStore staging";
                }
                final String r = result;
                eval("onPermissionResult('" + r.replace("'", "") + "')");
                eval("onPermissionResult('Vault: " + VAULT_URL + "')");
                eval("onPermissionResult('Drive auto-upload needs OAuth client (next) — queue is staged on device')");
                Toast.makeText(MainActivity.this, r, Toast.LENGTH_LONG).show();
            });
        }

        @JavascriptInterface
        public void openVault(String ignored) {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(VAULT_URL)));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Open Drive vault manually", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEDIA) {
            boolean ok = grantResults.length > 0;
            for (int g : grantResults) if (g != PackageManager.PERMISSION_GRANTED) ok = false;
            eval("onPermissionResult('" + (ok ? "Media permissions granted" : "Some media permissions denied") + "')");
        }
    }
}
