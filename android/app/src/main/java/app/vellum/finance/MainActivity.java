package app.vellum.finance;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Vellum runs entirely offline from the APK's assets. WebViewAssetLoader serves them from
 * https://appassets.androidplatform.net so the page is a secure context: IndexedDB storage,
 * Web Crypto (passphrase lock) and the clipboard all work. The page's CSP blocks outside connections.
 */
public class MainActivity extends AppCompatActivity {
    private static final String START = "https://appassets.androidplatform.net/assets/www/index.html";
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private static final int PICK_FILE = 41;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notifier.ensureChannel(this);
        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(Intent.createChooser(i, "Choose a file"), PICK_FILE); }
                catch (Exception e) { fileCallback = null; return false; }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                web.evaluateJavascript("window.vellumBack && window.vellumBack()", value -> {
                    if (!"true".equals(value)) { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); setEnabled(true); }
                });
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START + routeOf(getIntent()));
    }

    private static String routeOf(Intent i) {
        String r = i == null ? null : i.getStringExtra("route");
        return (r != null && r.matches("#[a-z]+")) ? r : "";
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String r = routeOf(intent);
        if (!r.isEmpty()) web.evaluateJavascript("location.hash='" + r + "'", null);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && fileCallback != null) {
            Uri[] result = (resultCode == RESULT_OK && data != null && data.getData() != null) ? new Uri[]{data.getData()} : null;
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    private boolean notificationsAllowed() {
        boolean perm = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        return perm && NotificationManagerCompat.from(this).areNotificationsEnabled();
    }

    /** Lets the page save backups and CSV exports to the phone's Downloads folder. */
    public class Bridge {
        @JavascriptInterface
        public void scheduleNotifications(String json) { Notifier.schedule(MainActivity.this, json); }

        @JavascriptInterface
        public boolean notificationsAllowed() { return MainActivity.this.notificationsAllowed(); }

        @JavascriptInterface
        public void requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= 33 && !MainActivity.this.notificationsAllowed())
                runOnUiThread(() -> ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7));
        }

        @JavascriptInterface
        public void testNotification(String title, String body) { Notifier.show(MainActivity.this, "test", title, body, "#home"); }

        @JavascriptInterface
        public boolean saveFile(String name, String mime, String text) {
            try {
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.MIME_TYPE, mime);
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Vellum");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) return false;
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(bytes); }
                } else {
                    File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Vellum");
                    if (!dir.exists() && !dir.mkdirs()) return false;
                    try (FileOutputStream os = new FileOutputStream(new File(dir, name))) { os.write(bytes); }
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
