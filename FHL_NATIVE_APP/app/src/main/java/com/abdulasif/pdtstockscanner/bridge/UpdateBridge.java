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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UpdateBridge - JavaScript Interface for handling app updates via DownloadManager
 * Mirrors the original DownloadHelper.openUpdate() from the Capacitor APK
 * Registered as: window.UpdateBridge
 */
public class UpdateBridge {

    private static final String TAG = "UpdateBridge";
    private final MainActivity activity;
    private volatile File downloadedApk;

    public UpdateBridge(MainActivity activity) {
        this.activity = activity;
    }

    /**
     * Starts downloading the update APK via Android DownloadManager
     * Called from JS: window.UpdateBridge.openUpdate(url)
     */
    @JavascriptInterface
    public void openUpdate(String urlStr) {
        startDownload(String.valueOf(urlStr), true);
    }

    /** Starts a download without launching the installer after completion. */
    @JavascriptInterface
    public void downloadUpdate(String urlStr) {
        startDownload(String.valueOf(urlStr), false);
    }

    /** Opens the Android installer for the APK downloaded through downloadUpdate. */
    @JavascriptInterface
    public void installDownloadedUpdate() {
        final File file = downloadedApk;
        if (file != null && file.exists() && file.length() > 0) {
            launchInstaller(file);
        } else {
            activity.runOnUiThread(new Runnable() {
                @Override public void run() {
                    Toast.makeText(activity, "Download the update first.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void startDownload(String updateUrl, boolean installWhenComplete) {
        Log.i(TAG, "Starting update download: " + updateUrl);

        Handler handler = new Handler(Looper.getMainLooper());

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
            BroadcastReceiverCompat receiver = new BroadcastReceiverCompat(dm, dest, installWhenComplete);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                activity.registerReceiver(receiver, filter);
            }
            long downloadId = dm.enqueue(request);
            receiver.setExpectedId(downloadId);
            // Some Android builds suppress the completion broadcast for a dynamically
            // registered receiver. Poll briefly as a deterministic fallback.
            receiver.pollStatus(downloadId, 0);

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
        private final boolean installWhenComplete;
        private volatile long expectedId = -1;
        private final AtomicBoolean handled = new AtomicBoolean(false);

        BroadcastReceiverCompat(DownloadManager dm, File dest, boolean installWhenComplete) {
            this.dm = dm;
            this.dest = dest;
            this.installWhenComplete = installWhenComplete;
        }

        void setExpectedId(long expectedId) {
            this.expectedId = expectedId;
        }

        void pollStatus(final long id, final int attempt) {
            if (handled.get() || attempt > 30) return;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() {
                    if (handled.get()) return;
                    DownloadManager.Query q = new DownloadManager.Query().setFilterById(new long[]{id});
                    android.database.Cursor c = dm.query(q);
                    int status = -1;
                    if (c != null) {
                        if (c.moveToFirst()) {
                            int idx = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            if (idx >= 0) status = c.getInt(idx);
                        }
                        c.close();
                    }
                    if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                        handleCompletion(id);
                    } else {
                        pollStatus(id, attempt + 1);
                    }
                }
            }, 1000L);
        }

        private void handleCompletion(long id) {
            if (!handled.compareAndSet(false, true)) return;
            expectedId = id;
            try { activity.unregisterReceiver(this); } catch (Exception ignored) {}
            processResult(id);
        }

        private void processResult(long id) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(new long[]{id});
            android.database.Cursor cursor = dm.query(query);
            int status = DownloadManager.STATUS_FAILED;
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    if (statusIdx >= 0) status = cursor.getInt(statusIdx);
                }
                cursor.close();
            }
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                downloadedApk = dest;
                if (installWhenComplete) {
                    launchInstaller(dest);
                    Toast.makeText(activity, "Download complete. Opening installer...", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(activity, "Download complete. Tap Install in Settings.", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(activity, "Download failed. Please retry.", Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra("extra_download_id", -1);
            if (expectedId < 0) {
                expectedId = id;
            } else if (id != expectedId) {
                return;
            }

            handleCompletion(id);
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
                    Uri contentUri = FileProvider.getUriForFile(
                            activity,
                            "com.abdulasif.pdtstockscanner.fixed.fileprovider",
                            file
                    );
                    Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                    installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
                    installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                    installIntent.putExtra(Intent.EXTRA_RETURN_RESULT, false);
                    installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    installIntent.setClipData(ClipData.newRawUri("FHL_ELECTRONICS_APK", contentUri));
                    try {
                        activity.startActivity(installIntent);
                    } catch (Exception primaryFailure) {
                        Log.w(TAG, "ACTION_INSTALL_PACKAGE unavailable; retrying ACTION_VIEW", primaryFailure);
                        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                        viewIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
                        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        viewIntent.setClipData(ClipData.newRawUri("FHL_ELECTRONICS_APK", contentUri));
                        try {
                            activity.startActivity(viewIntent);
                        } catch (Exception secondaryFailure) {
                            Log.e(TAG, "Both APK installer intents failed", secondaryFailure);
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                                    !activity.getPackageManager().canRequestPackageInstalls()) {
                                Intent permissionIntent = new Intent(
                                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + activity.getPackageName())
                                );
                                permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(permissionIntent);
                            } else {
                                throw secondaryFailure;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "install intent failed", e);
                    Toast.makeText(activity, "Could not open APK installer. Open the downloaded APK from Downloads.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
