package com.abdulasif.pdtstockscanner.bridge;

import android.app.DownloadManager;
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
        File dir = activity.getExternalCacheDir();
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

            long downloadId = dm.enqueue(request);

            // Register BroadcastReceiver for download complete
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            BroadcastReceiverCompat receiver = new BroadcastReceiverCompat(dm, dest, downloadId);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                activity.registerReceiver(receiver, filter);
            }

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
        private final long expectedId;

        BroadcastReceiverCompat(DownloadManager dm, File dest, long expectedId) {
            this.dm = dm;
            this.dest = dest;
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
    private void launchInstaller(File file) {
        String mimeType = "application/vnd.android.package-archive";
        String action = Intent.ACTION_VIEW;
        int flags = Intent.FLAG_ACTIVITY_NEW_TASK;

        try {
            Uri contentUri = FileProvider.getUriForFile(
                    activity,
                    "com.abdulasif.pdtstockscanner.fixed.fileprovider",
                    file
            );

            Intent installIntent = new Intent(action);
            installIntent.setDataAndType(contentUri, mimeType);
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(flags);
            activity.startActivity(installIntent);
        } catch (Exception e) {
            Log.e(TAG, "install intent failed", e);
            // Fallback with file:// URI
            try {
                Intent fallback = new Intent(action);
                fallback.setDataAndType(Uri.fromFile(file), mimeType);
                fallback.addFlags(flags);
                activity.startActivity(fallback);
            } catch (Exception e2) {
                Log.e(TAG, "fallback install failed", e2);
            }
        }
    }
}
