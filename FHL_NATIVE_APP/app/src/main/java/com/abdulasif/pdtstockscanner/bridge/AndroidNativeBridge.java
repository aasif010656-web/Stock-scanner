package com.abdulasif.pdtstockscanner.bridge;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.FileProvider;

import com.abdulasif.pdtstockscanner.MainActivity;
import com.abdulasif.pdtstockscanner.ScannerActivity;

import java.io.File;
import java.io.FileOutputStream;

/**
 * AndroidNative JavaScript Bridge
 * Provides native functionality to the web app via window.AndroidNative
 * Mirrors the original MainActivity$1 from the Capacitor APK
 */
public class AndroidNativeBridge {

    private final MainActivity activity;
    private final ActivityResultLauncher<Intent> scannerLauncher;

    public AndroidNativeBridge(MainActivity activity, ActivityResultLauncher<Intent> scannerLauncher) {
        this.activity = activity;
        this.scannerLauncher = scannerLauncher;
    }

    /**
     * Opens the native barcode scanner (CameraX + MLKit)
     * Called from JS: window.AndroidNative.startNativeScanner()
     */
    @JavascriptInterface
    public void startNativeScanner() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(activity, ScannerActivity.class);
                scannerLauncher.launch(intent);
            }
        });
    }

    /**
     * Stops the native scanner (no-op in this implementation)
     * Called from JS: window.AndroidNative.stopNativeScanner()
     */
    @JavascriptInterface
    public void stopNativeScanner() {
        // No-op
    }

    /**
     * Checks if the update APK download is complete
     * Returns "complete" or "downloading"
     * Called from JS: window.AndroidNative.checkDownloadStatus()
     */
    @JavascriptInterface
    public String checkDownloadStatus() {
        String status = "downloading";
        try {
            File cacheDir = activity.getExternalCacheDir();
            File apkFile = new File(cacheDir, "FHL_ELECTRONICS_update.apk");
            if (apkFile.exists() && apkFile.length() > 10 * 1024 * 1024) {
                status = "complete";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return status;
    }

    /**
     * Launches the package installer for the downloaded update APK
     * Called from JS: window.AndroidNative.installLastDownloadedApk()
     */
    @JavascriptInterface
    public void installLastDownloadedApk() {
        String mimeType = "application/vnd.android.package-archive";
        String action = Intent.ACTION_VIEW;
        int flags = 0x10000000; // FLAG_ACTIVITY_NEW_TASK

        try {
            File cacheDir = activity.getExternalCacheDir();
            File apkFile = new File(cacheDir, "FHL_ELECTRONICS_update.apk");

            if (apkFile.exists()) {
                Uri contentUri = FileProvider.getUriForFile(
                        activity,
                        "com.abdulasif.pdtstockscanner.fixed.fileprovider",
                        apkFile
                );

                Intent installIntent = new Intent(action);
                installIntent.setDataAndType(contentUri, mimeType);
                installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                installIntent.addFlags(flags);
                activity.startActivity(installIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Decodes base64 data to a file and launches the package installer
     * Called from JS: window.AndroidNative.installApk(base64Data, fileName)
     */
    @JavascriptInterface
    public void installApk(String base64Data, String fileName) {
        try {
            byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
            File file = new File(activity.getExternalCacheDir(), fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(decodedBytes);
            fos.close();

            Uri contentUri = FileProvider.getUriForFile(
                    activity,
                    "com.abdulasif.pdtstockscanner.fixed.fileprovider",
                    file
            );

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(0x10000000); // FLAG_ACTIVITY_NEW_TASK
            activity.startActivity(installIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Decodes base64 data to a file and shares it via share intent
     * Called from JS: window.AndroidNative.shareFile(base64Data, fileName)
     */
    @JavascriptInterface
    public void shareFile(String base64Data, String fileName) {
        try {
            byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
            File file = new File(activity.getExternalCacheDir(), fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(decodedBytes);
            fos.close();

            Uri contentUri = FileProvider.getUriForFile(
                    activity,
                    "com.abdulasif.pdtstockscanner.fixed.fileprovider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(shareIntent, "Share Excel Report");
            activity.startActivity(chooser);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
