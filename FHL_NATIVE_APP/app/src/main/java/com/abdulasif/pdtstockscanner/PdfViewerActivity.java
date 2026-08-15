package com.abdulasif.pdtstockscanner;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

/** Native, offline PDF reader used for Data imports. */
public class PdfViewerActivity extends Activity {
    public static final String EXTRA_FILE_PATH = "pdf_file_path";
    public static final String EXTRA_TITLE = "pdf_title";

    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private ImageView pageView;
    private TextView pageStatus;
    private Button previousButton;
    private Button nextButton;
    private int pageIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(10, 14, 25));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 14, 25));
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button closeButton = new Button(this);
        closeButton.setText("Close");
        closeButton.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText(getIntent().getStringExtra(EXTRA_TITLE));
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(closeButton);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header);

        pageView = new ImageView(this);
        pageView.setAdjustViewBounds(true);
        pageView.setBackgroundColor(Color.WHITE);
        pageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(pageView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        previousButton = new Button(this);
        previousButton.setText("‹ Previous");
        nextButton = new Button(this);
        nextButton.setText("Next ›");
        pageStatus = new TextView(this);
        pageStatus.setTextColor(Color.WHITE);
        pageStatus.setGravity(Gravity.CENTER);
        controls.addView(previousButton);
        controls.addView(pageStatus, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        controls.addView(nextButton);
        root.addView(controls);
        setContentView(root);

        previousButton.setOnClickListener(v -> { if (pageIndex > 0) { pageIndex--; renderPage(); } });
        nextButton.setOnClickListener(v -> { if (renderer != null && pageIndex < renderer.getPageCount() - 1) { pageIndex++; renderPage(); } });

        try {
            File pdf = new File(getIntent().getStringExtra(EXTRA_FILE_PATH));
            descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            renderPage();
        } catch (Exception exception) {
            pageStatus.setText("Unable to open this PDF.");
            previousButton.setEnabled(false);
            nextButton.setEnabled(false);
        }
    }

    private void renderPage() {
        if (renderer == null || renderer.getPageCount() == 0) return;
        PdfRenderer.Page page = renderer.openPage(pageIndex);
        int sourceWidth = page.getWidth();
        int sourceHeight = page.getHeight();
        int targetWidth = Math.min(Math.max(sourceWidth, 1), 1600);
        int targetHeight = Math.max(1, Math.round((float) sourceHeight * targetWidth / Math.max(sourceWidth, 1)));
        Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();
        pageView.setImageBitmap(bitmap);
        pageStatus.setText("Page " + (pageIndex + 1) + " of " + renderer.getPageCount());
        previousButton.setEnabled(pageIndex > 0);
        nextButton.setEnabled(pageIndex < renderer.getPageCount() - 1);
    }

    @Override
    protected void onDestroy() {
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) { }
        try { if (descriptor != null) descriptor.close(); } catch (IOException ignored) { }
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
