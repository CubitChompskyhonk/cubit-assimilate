package inc.cubitsystems.assimilate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_MEDIA = 1001;
    private WebView webView;

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
            runOnUiThread(() -> {
                BiometricManager bm = BiometricManager.from(MainActivity.this);
                int can = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
                if (can != BiometricManager.BIOMETRIC_SUCCESS) {
                    Toast.makeText(MainActivity.this, "Biometrics unavailable — allowing for setup", Toast.LENGTH_SHORT).show();
                    eval("onBiometricResult('1')");
                    return;
                }
                Executor ex = ContextCompat.getMainExecutor(MainActivity.this);
                BiometricPrompt prompt = new BiometricPrompt(MainActivity.this, ex, new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        eval("onBiometricResult('1')");
                    }
                    @Override public void onAuthenticationError(int code, @NonNull CharSequence err) {
                        eval("onBiometricResult('0')");
                    }
                    @Override public void onAuthenticationFailed() {
                        eval("onBiometricResult('0')");
                    }
                });
                BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Cubit Assimilate")
                        .setSubtitle("Confirm Founder key to continue")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        .build();
                prompt.authenticate(info);
            });
        }

        @JavascriptInterface
        public void startBackup(String scopes) {
            runOnUiThread(() -> {
                // Drive upload module lands next; record intent and confirm vault target
                Toast.makeText(MainActivity.this, "Backup path ready — Drive upload next module", Toast.LENGTH_LONG).show();
                eval("onPermissionResult('Native: scopes=" + scopes.replace("'", "") + " · vault founder-android-01')");
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEDIA) {
            boolean ok = grantResults.length > 0;
            for (int g : grantResults) if (g != PackageManager.PERMISSION_GRANTED) ok = false;
            eval("onPermissionResult('" + (ok ? "Media permissions granted" : "Some media permissions denied — backup will skip those") + "')");
        }
    }
}
