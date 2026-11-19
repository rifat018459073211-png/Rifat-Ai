package com.rifat.aivideoorganizer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;

import androidx.media3.common.MediaItem;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoTrimmerActivity extends Activity {
    private static final int PICK_VIDEO = 201;
    private static final int SAVE_VIDEO = 202;

    private Uri sourceUri;
    private long durationMs;
    private EditText startInput;
    private EditText endInput;
    private TextView selectedLabel;
    private TextView statusLabel;
    private ProgressBar progress;
    private Button exportButton;
    private File exportedFile;
    private Transformer transformer;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(35, 43, 58));
        return view;
    }

    private Button button(String value) {
        Button view = new Button(this);
        view.setText(value);
        view.setAllCaps(false);
        return view;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(24), dp(20), dp(28));
        page.setBackgroundColor(Color.rgb(246, 248, 252));
        scroll.addView(page);

        TextView title = text("ভিডিও কাটার • MP4 Export", 24);
        title.setTextColor(Color.rgb(30, 76, 139));
        page.addView(title);

        TextView intro = text("ফোনের ভিডিও থেকে শুরু ও শেষের সময় বেছে নিয়ে প্রয়োজনীয় অংশটি MP4 হিসেবে সেভ করো। ভিডিও ফোনেই প্রসেস হবে; কোনো API key লাগবে না।", 14);
        intro.setPadding(0, dp(8), 0, dp(18));
        page.addView(intro);

        Button choose = button("ভিডিও নির্বাচন করো");
        page.addView(choose);
        choose.setOnClickListener(v -> chooseVideo());

        selectedLabel = text("এখনো কোনো ভিডিও নির্বাচন করা হয়নি।", 13);
        selectedLabel.setPadding(0, dp(10), 0, dp(16));
        page.addView(selectedLabel);

        page.addView(text("শুরুর সময় (সেকেন্ড)", 14));
        startInput = new EditText(this);
        startInput.setSingleLine(true);
        startInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        startInput.setHint("যেমন 0");
        page.addView(startInput);

        page.addView(text("শেষের সময় (সেকেন্ড)", 14));
        endInput = new EditText(this);
        endInput.setSingleLine(true);
        endInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        endInput.setHint("যেমন 30");
        page.addView(endInput);

        exportButton = button("ক্লিপ তৈরি করো");
        page.addView(exportButton);
        exportButton.setOnClickListener(v -> exportClip());

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(android.view.View.GONE);
        page.addView(progress);

        statusLabel = text("Export শেষ হলে কোথায় MP4 সেভ করবে তা বেছে নিতে পারবে।", 13);
        statusLabel.setPadding(0, dp(12), 0, dp(16));
        page.addView(statusLabel);

        Button back = button("← ফিরে যাও");
        page.addView(back);
        back.setOnClickListener(v -> finish());
        setContentView(scroll);
    }

    private void chooseVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        startActivityForResult(intent, PICK_VIDEO);
    }

    private void exportClip() {
        if (sourceUri == null) {
            statusLabel.setText("আগে একটি ভিডিও নির্বাচন করো।");
            return;
        }
        final long startMs;
        final long endMs;
        try {
            startMs = (long) (Double.parseDouble(startInput.getText().toString().trim()) * 1000d);
            endMs = (long) (Double.parseDouble(endInput.getText().toString().trim()) * 1000d);
        } catch (Exception e) {
            statusLabel.setText("শুরু ও শেষের সময় সেকেন্ডে লিখো।");
            return;
        }
        if (startMs < 0 || endMs <= startMs || endMs > durationMs) {
            statusLabel.setText("সময় ঠিক নয়। শেষের সময় ভিডিওর মোট দৈর্ঘ্যের মধ্যে এবং শুরুর সময়ের পরে হতে হবে।");
            return;
        }

        exportButton.setEnabled(false);
        progress.setVisibility(android.view.View.VISIBLE);
        statusLabel.setText("ভিডিও প্রসেস হচ্ছে। ভিডিওর দৈর্ঘ্য ও ফোনের ক্ষমতা অনুযায়ী সময় লাগতে পারে…");
        io.execute(() -> {
            try {
                File dir = new File(getCacheDir(), "video_exports");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("Export ফোল্ডার তৈরি করা যায়নি।");
                exportedFile = new File(dir, "Rifat-Ai-clip-" + System.currentTimeMillis() + ".mp4");
                MediaItem item = new MediaItem.Builder()
                    .setUri(sourceUri)
                    .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build())
                    .build();
                EditedMediaItem edited = new EditedMediaItem.Builder(item).build();
                transformer = new Transformer.Builder(this)
                    .addListener(new Transformer.Listener() {
                        @Override public void onCompleted(Composition composition, ExportResult result) {
                            runOnUiThread(() -> {
                                progress.setVisibility(android.view.View.GONE);
                                statusLabel.setText("ভিডিও তৈরি হয়েছে। এখন MP4 সেভ করার জায়গা বেছে নাও।");
                                Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                save.addCategory(Intent.CATEGORY_OPENABLE);
                                save.setType("video/mp4");
                                save.putExtra(Intent.EXTRA_TITLE, "Rifat-Ai-clip.mp4");
                                startActivityForResult(save, SAVE_VIDEO);
                            });
                        }

                        @Override public void onError(Composition composition, ExportResult result, ExportException error) {
                            runOnUiThread(() -> {
                                progress.setVisibility(android.view.View.GONE);
                                exportButton.setEnabled(true);
                                statusLabel.setText("ভিডিও Export হয়নি: " + (error.getMessage() == null ? "এই ভিডিও ফরম্যাটটি ফোনে সমর্থিত নয়।" : error.getMessage()));
                                if (exportedFile != null) exportedFile.delete();
                            });
                        }
                    })
                    .build();
                transformer.start(edited, exportedFile.getAbsolutePath());
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(android.view.View.GONE);
                    exportButton.setEnabled(true);
                    statusLabel.setText("Export শুরু করা যায়নি: " + (e.getMessage() == null ? "অজানা সমস্যা" : e.getMessage()));
                    if (exportedFile != null) exportedFile.delete();
                });
            }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == SAVE_VIDEO && exportedFile != null) {
                exportedFile.delete();
                exportButton.setEnabled(true);
                statusLabel.setText("সেভ বাতিল করা হয়েছে। চাইলে আবার Export করতে পারো।");
            }
            return;
        }
        Uri uri = data.getData();
        if (requestCode == PICK_VIDEO) {
            sourceUri = uri;
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(this, uri);
                String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                durationMs = value == null ? 0 : Long.parseLong(value);
                if (durationMs <= 0) throw new Exception("ভিডিওর দৈর্ঘ্য পাওয়া যায়নি।");
                double seconds = durationMs / 1000d;
                endInput.setText(String.format(Locale.US, "%.1f", seconds));
                startInput.setText("0");
                selectedLabel.setText("ভিডিও নির্বাচিত • মোট দৈর্ঘ্য " + String.format(Locale.US, "%.1f সেকেন্ড", seconds));
                statusLabel.setText("শুরু ও শেষের সময় ঠিক করে ক্লিপ তৈরি করো।");
            } catch (Exception e) {
                sourceUri = null;
                selectedLabel.setText("এই ভিডিওর তথ্য পড়া যায়নি। অন্য ভিডিও বেছে নাও।");
            } finally {
                try { retriever.release(); } catch (Exception ignored) {}
            }
        } else if (requestCode == SAVE_VIDEO) {
            copyExportTo(uri);
        }
    }

    private void copyExportTo(Uri destination) {
        if (exportedFile == null || !exportedFile.exists()) {
            statusLabel.setText("Export করা ফাইল পাওয়া যায়নি। আবার চেষ্টা করো।");
            exportButton.setEnabled(true);
            return;
        }
        progress.setVisibility(android.view.View.VISIBLE);
        statusLabel.setText("MP4 ফাইল সেভ হচ্ছে…");
        io.execute(() -> {
            try (InputStream in = new java.io.FileInputStream(exportedFile);
                 OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                if (out == null) throw new Exception("নির্বাচিত জায়গায় ফাইল লেখা যাচ্ছে না।");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                out.flush();
                exportedFile.delete();
                runOnUiThread(() -> {
                    progress.setVisibility(android.view.View.GONE);
                    exportButton.setEnabled(true);
                    statusLabel.setText("সফল! MP4 ক্লিপ সেভ করা হয়েছে।");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(android.view.View.GONE);
                    exportButton.setEnabled(true);
                    statusLabel.setText("ফাইল সেভ হয়নি: " + (e.getMessage() == null ? "অজানা সমস্যা" : e.getMessage()));
                });
            }
        });
    }

    @Override protected void onDestroy() {
        if (transformer != null) transformer.cancel();
        io.shutdownNow();
        super.onDestroy();
    }
}
