package com.abdulasif.pdtstockscanner;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import com.abdulasif.pdtstockscanner.bridge.AndroidNativeBridge;
import com.abdulasif.pdtstockscanner.bridge.UpdateBridge;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Main Activity - Capacitor-style WebView host with JS bridges
 * Mirrors the original Capacitor BridgeActivity behavior
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ActivityResultLauncher<Intent> scannerLauncher;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ValueCallback<Uri[]> filePathCallback;
    private AndroidNativeBridge nativeBridge;
    private UpdateBridge updateBridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Register scanner activity launcher
        scannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            String code = result.getData().getStringExtra("BARCODE");
                            onBarcodeScanned(code);
                        }
                    }
                }
        );
        // Android WebView does not open HTML <input type=file> reliably with the
        // default WebChromeClient. Route file inputs through the system picker.
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (filePathCallback == null) return;
                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri data = result.getData().getData();
                        if (data != null) results = new Uri[]{data};
                    }
                    filePathCallback.onReceiveValue(results);
                    filePathCallback = null;
                }
        );

        // Initialize WebView
        webView = findViewById(R.id.webview);
        setupWebView();

        // Create and register JS bridges
        nativeBridge = new AndroidNativeBridge(this, scannerLauncher);
        webView.addJavascriptInterface(nativeBridge, "AndroidNative");

        updateBridge = new UpdateBridge(this);
        webView.addJavascriptInterface(updateBridge, "UpdateBridge");

        // Load the app from assets
        String htmlPath = "file:///android_asset/public/index.html";
        webView.loadUrl(htmlPath);
    }

    public void sendBiometricResultToWeb(boolean success) {
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript("window.onNativeBiometricResult && window.onNativeBiometricResult(" + success + ")", null);
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setSupportZoom(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel",
                        "text/csv"
                });
                fileChooserLauncher.launch(intent);
                return true;
            }
        });
    }

    private void onBarcodeScanned(String code) {
        if (code == null) return;

        String escapedCode = code.replace("'", "\\'");
        String js = "if(typeof window.lookupBarcode==='function') window.lookupBarcode('" + escapedCode + "');";
        webView.evaluateJavascript(js, null);

        android.widget.Toast.makeText(this, "Scanned: " + code, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript(
                "(function(){" +
                "if(typeof window.handleNativeBack==='function'){return window.handleNativeBack()?'handled':'exit';}" +
                "if(typeof window.back==='function'){return window.back()?'handled':'exit';}" +
                "return 'exit';" +
                "})()",
                value -> {
                    if (value == null || value.contains("exit")) {
                        runOnUiThread(() -> superOnBackPressed());
                    }
                });
    }

    private void superOnBackPressed() {
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            webView.evaluateJavascript("if(typeof window.volumeKeyPressed==='function') window.volumeKeyPressed('up');", null);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            webView.evaluateJavascript("if(typeof window.volumeKeyPressed==='function') window.volumeKeyPressed('down');", null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }
}
