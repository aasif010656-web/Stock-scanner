package com.abdulasif.pdtstockscanner.bridge;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.abdulasif.pdtstockscanner.MainActivity;

import java.io.File;

/**
 * UpdateBridge - JavaScript Interface for handling app updates via DownloadManager
 * Mirrors the original DownloadHelper.openUpdate() from the Capacitor APK
 * Registered as: window.UpdateBridge
 */
public class UpdateBridge {

    private static final String TAG = "UpdateBridge";
    private final MainActivity activity;

    public UpdateBridge(MainActivity activity) {
        this.activity = activity;
    }

    /**
     * Starts downloading the update APK via Android DownloadManager
     * Called from JS: window.UpdateBridge.openUpdate(url)
     */
    @JavascriptInterface
    public void openUpdate(String urlStr) {
        Log.i(TAG, "openUpdate called with: " + urlStr);

        Handler handler = new Handler(Looper.getMainLooper());
        String updateUrl = String.valueOf(urlStr);

        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, "Download service unavailable.", Toast.LENGTH_LONG).show();
                }
            });
            return;
        }

        // Get cache directory
        File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = activity.getExternalCacheDir();
        }
        if (dir == null) {
            dir = activity.getCacheDir();
        }
        File dest = new File(dir, "FHL_ELECTRONICS_update.apk");
        try {
            dest.delete();
        } catch (Exception e) {
            Log.e(TAG, "delete failed", e);
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(updateUrl));
            request.setTitle("FHL ELECTRONICS Update");
            request.setDescription("Downloading update...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setDestinationUri(Uri.fromFile(dest));
            request.allowScanningByMediaScanner();
            request.setVisibleInDownloadsUi(true);

            handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, "Starting download...", Toast.LENGTH_SHORT).show();
                }
            });

            // Register before enqueue so a very fast/local download cannot finish before the receiver exists.
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            BroadcastReceiverCompat receiver = new BroadcastReceiverCompat(dm, dest);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                activity.registerReceiver(receiver, filter);
            }
            long downloadId = dm.enqueue(request);
            receiver.setExpectedId(downloadId);

        } catch (Exception e) {
            Log.e(TAG, "openUpdate error", e);
        }
    }

    /**
     * BroadcastReceiver to handle download completion and launch installer
     */
    private class BroadcastReceiverCompat extends android.content.BroadcastReceiver {
        private final DownloadManager dm;
        private final File dest;
        private volatile long expectedId = -1;

        BroadcastReceiverCompat(DownloadManager dm, File dest) {
            this.dm = dm;
            this.dest = dest;
        }

        void setExpectedId(long expectedId) {
            this.expectedId = expectedId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra("extra_download_id", -1);
            if (id != expectedId) return;

            try {
                context.unregisterReceiver(this);
            } catch (Exception e) {
                // ignore
            }

            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(new long[]{expectedId});
            android.database.Cursor cursor = dm.query(query);
            int status = 16; // STATUS_RUNNING

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int statusIdx = cursor.getColumnIndex("status");
                    if (statusIdx >= 0) {
                        status = cursor.getInt(statusIdx);
                    }
                }
                cursor.close();
            }

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                launchInstaller(dest);
                Toast.makeText(context, "Download complete. Opening installer...", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, "Download failed, opening browser...", Toast.LENGTH_LONG).show();
                // Fallback: open in browser
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(dest.getAbsolutePath()));
                    browserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(browserIntent);
                } catch (Exception e) {
                    Log.e(TAG, "fallback failed", e);
                }
            }
        }
    }

    /**
     * Launches the package installer for the given APK file
     */
    private void launchInstaller(final File file) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!file.exists() || file.length() == 0) {
                        Log.e(TAG, "Downloaded APK is missing or empty: " + file);
                        return;
                    }
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
                    Intent installIntent = new Intent(Intent.ACTION_VIEW);
                    installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
                    installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                    installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    installIntent.setClipData(ClipData.newRawUri("FHL_ELECTRONICS_APK", contentUri));
                    activity.startActivity(installIntent);
                } catch (Exception e) {
                    Log.e(TAG, "install intent failed", e);
                    Toast.makeText(activity, "Could not open APK installer. Open the downloaded APK from Downloads.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
