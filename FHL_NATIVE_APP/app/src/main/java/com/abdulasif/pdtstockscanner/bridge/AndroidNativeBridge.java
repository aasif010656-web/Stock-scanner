package com.abdulasif.pdtstockscanner.bridge;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.JavascriptInterface;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

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

    /** Posts a grouped price-update summary to Android notification center. */
    @JavascriptInterface
    public void postPriceUpdateNotification(String title, String body) {
        activity.runOnUiThread(() -> {
            final String channelId = "price_updates";
            NotificationManager manager = (NotificationManager) activity.getSystemService(Activity.NOTIFICATION_SERVICE);
            if (manager == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(channelId, "Price Updates", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("Promoter price update notifications");
                manager.createNotificationChannel(channel);
            }
            if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4021);
                return;
            }
            String safeBody = body == null ? "Your product prices are changed" : body;
            NotificationCompat.Builder notification = new NotificationCompat.Builder(activity, channelId)
                    .setSmallIcon(com.abdulasif.pdtstockscanner.R.mipmap.ic_launcher)
                    .setContentTitle(title == null ? "Price update" : title)
                    .setContentText(safeBody)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(safeBody))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);
            manager.notify((int)(System.currentTimeMillis() & 0x7fffffff), notification.build());
        });
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
        try {
            File apkFile = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "FHL_ELECTRONICS_update.apk");
            if (!apkFile.exists() || apkFile.length() == 0) {
                File cacheDir = activity.getExternalCacheDir();
                if (cacheDir == null) cacheDir = activity.getCacheDir();
                apkFile = new File(cacheDir, "FHL_ELECTRONICS_update.apk");
            }
            if (!apkFile.exists() || apkFile.length() == 0) return;
            openInstaller(apkFile);
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
            File cacheDir = activity.getExternalCacheDir();
            if (cacheDir == null) cacheDir = activity.getCacheDir();
            File file = new File(cacheDir, fileName == null || fileName.trim().isEmpty() ? "FHL_ELECTRONICS_update.apk" : fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(decodedBytes);
                fos.flush();
            }
            openInstaller(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openInstaller(final File file) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                            !activity.getPackageManager().canRequestPackageInstalls()) {
                        Intent permissionIntent = new Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName())
                        );
                        permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(permissionIntent);
                        return;
                    }
                    Uri contentUri = FileProvider.getUriForFile(
                            activity,
                            "com.abdulasif.pdtstockscanner.fixed.fileprovider",
                            file
                    );
                    Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                    installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
                    installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                    installIntent.putExtra(Intent.EXTRA_RETURN_RESULT, false);
                    installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    installIntent.setClipData(ClipData.newRawUri("FHL_ELECTRONICS_APK", contentUri));
                    try {
                        activity.startActivity(installIntent);
                    } catch (Exception primaryFailure) {
                        Log.w("AndroidNativeBridge", "ACTION_INSTALL_PACKAGE unavailable; retrying ACTION_VIEW", primaryFailure);
                        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                        viewIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
                        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        viewIntent.setClipData(ClipData.newRawUri("FHL_ELECTRONICS_APK", contentUri));
                        activity.startActivity(viewIntent);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
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
