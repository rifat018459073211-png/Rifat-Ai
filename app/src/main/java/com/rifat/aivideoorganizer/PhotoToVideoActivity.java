package com.rifat.aivideoorganizer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import android.graphics.Color;

import androidx.media3.common.MediaItem;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@androidx.media3.common.util.UnstableApi
public class PhotoToVideoActivity extends Activity {
    private static final int PICK_IMAGE = 401;
    private static final int SAVE_VIDEO = 402;
    private Uri imageUri;
    private Spinner durationSpinner;
    private TextView selectedLabel, statusLabel;
    private ProgressBar progress;
    private Button createButton;
    private Transformer transformer;
    private File outputFile;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ProgressHolder progressHolder = new ProgressHolder();
    private Runnable progressTask;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }

    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.rgb(35, 43, 58));
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        return b;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(24), dp(20), dp(28));
        page.setBackgroundColor(Color.rgb(246, 248, 252));
        scroll.addView(page);

        TextView title = text("Photo to MP4", 25);
        title.setTextColor(Color.rgb(30, 76, 139));
        page.addView(title);

        TextView intro = text("একটি ছবি থেকে ৫, ১০ বা ১৫ সেকেন্ডের MP4 ভিডিও তৈরি করো। ছবিটি ভিডিওর পুরো সময় স্থির থাকবে। এটি অফলাইন ভিডিও-রপ্তানি সুবিধা, AI motion generation নয়।", 14);
        intro.setPadding(0, dp(8), 0, dp(18));
        page.addView(intro);

        Button choose = button("＋ ছবি নির্বাচন করো");
        page.addView(choose);
        choose.setOnClickListener(v -> chooseImage());

        selectedLabel = text("এখনো কোনো ছবি নির্বাচন করা হয়নি।", 13);
        selectedLabel.setPadding(0, dp(12), 0, dp(18));
        page.addView(selectedLabel);

        page.addView(text("ভিডিওর দৈর্ঘ্য", 14));
        durationSpinner = new Spinner(this);
        String[] durations = {"৫ সেকেন্ড", "১০ সেকেন্ড", "১৫ সেকেন্ড"};
        durationSpinner.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, durations));
        page.addView(durationSpinner);

        createButton = button("▶  MP4 তৈরি করো");
        page.addView(createButton);
        createButton.setOnClickListener(v -> createVideo());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(8));
        pp.topMargin = dp(14);
        page.addView(progress, pp);

        statusLabel = text("ভিডিও তৈরি হলে কোথায় সেভ করবে তা বেছে নিতে পারবে।", 13);
        statusLabel.setPadding(0, dp(12), 0, dp(18));
        page.addView(statusLabel);

        Button back = button("← ফিরে যাও");
        page.addView(back);
        back.setOnClickListener(v -> finish());
        setContentView(scroll);
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }

    private void createVideo() {
        if (imageUri == null) {
            statusLabel.setText("আগে একটি ছবি নির্বাচন করো।");
            return;
        }
        int[] seconds = {5, 10, 15};
        long durationMs = seconds[durationSpinner.getSelectedItemPosition()] * 1000L;
        createButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        statusLabel.setText("ছবি থেকে MP4 তৈরি হচ্ছে… 0%");
        try {
            File dir = new File(getCacheDir(), "photo_video_exports");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Export ফোল্ডার তৈরি করা যায়নি।");
            outputFile = new File(dir, "Rifat-Ai-photo-" + System.currentTimeMillis() + ".mp4");

            MediaItem image = new MediaItem.Builder()
                    .setUri(imageUri)
                    .setImageDurationMs(durationMs)
                    .build();
            EditedMediaItem edited = new EditedMediaItem.Builder(image)
                    .setFrameRate(30)
                    .build();

            transformer = new Transformer.Builder(this)
                    .addListener(new Transformer.Listener() {
                        @Override public void onCompleted(Composition composition, ExportResult result) {
                            stopProgress();
                            progress.setProgress(100);
                            progress.setVisibility(View.GONE);
                            statusLabel.setText("MP4 তৈরি হয়েছে। এখন সেভ করার জায়গা বেছে নাও।");
                            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            save.addCategory(Intent.CATEGORY_OPENABLE);
                            save.setType("video/mp4");
                            save.putExtra(Intent.EXTRA_TITLE, "Rifat-Ai-photo-video.mp4");
                            startActivityForResult(save, SAVE_VIDEO);
                        }

                        @Override public void onError(Composition composition, ExportResult result, ExportException error) {
                            stopProgress();
                            progress.setVisibility(View.GONE);
                            createButton.setEnabled(true);
                            statusLabel.setText("MP4 তৈরি হয়নি: " + (error.getMessage() == null
                                    ? "এই ছবি ফরম্যাটটি সমর্থিত নয়।" : error.getMessage()));
                            if (outputFile != null) outputFile.delete();
                        }
                    })
                    .build();
            transformer.start(edited, outputFile.getAbsolutePath());
            startProgress();
        } catch (Exception e) {
            stopProgress();
            progress.setVisibility(View.GONE);
            createButton.setEnabled(true);
            statusLabel.setText("ভিডিও তৈরি শুরু করা যায়নি: " + (e.getMessage() == null ? "অজানা সমস্যা" : e.getMessage()));
            if (outputFile != null) outputFile.delete();
        }
    }

    private void startProgress() {
        stopProgress();
        progressTask = new Runnable() {
            @Override public void run() {
                if (transformer == null || isFinishing()) return;
                int state = transformer.getProgress(progressHolder);
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    int value = Math.max(0, Math.min(100, progressHolder.progress));
                    progress.setProgress(value);
                    statusLabel.setText("ছবি থেকে MP4 তৈরি হচ্ছে… " + value + "%");
                }
                handler.postDelayed(this, 350);
            }
        };
        handler.post(progressTask);
    }

    private void stopProgress() {
        if (progressTask != null) {
            handler.removeCallbacks(progressTask);
            progressTask = null;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == SAVE_VIDEO) {
                if (outputFile != null) outputFile.delete();
                createButton.setEnabled(true);
                statusLabel.setText("সেভ বাতিল হয়েছে। চাইলে আবার তৈরি করতে পারো।");
            }
            return;
        }
        Uri uri = data.getData();
        if (requestCode == PICK_IMAGE) {
            imageUri = uri;
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Exception ignored) {}
            selectedLabel.setText("ছবি নির্বাচন করা হয়েছে।");
            statusLabel.setText("দৈর্ঘ্য বেছে নিয়ে MP4 তৈরি করো।");
        } else if (requestCode == SAVE_VIDEO) {
            copyTo(uri);
        }
    }

    private void copyTo(Uri destination) {
        if (outputFile == null || !outputFile.exists()) {
            statusLabel.setText("তৈরি করা MP4 ফাইল পাওয়া যায়নি।");
            createButton.setEnabled(true);
            return;
        }
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        statusLabel.setText("MP4 সেভ হচ্ছে…");
        io.execute(() -> {
            try (InputStream in = new java.io.FileInputStream(outputFile);
                 OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                if (out == null) throw new Exception("নির্বাচিত জায়গায় ফাইল লেখা যাচ্ছে না।");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                out.flush();
                outputFile.delete();
                runOnUiThread(() -> {
                    progress.setIndeterminate(false);
                    progress.setVisibility(View.GONE);
                    createButton.setEnabled(true);
                    statusLabel.setText("সফল! MP4 ভিডিও সেভ হয়েছে।");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setIndeterminate(false);
                    progress.setVisibility(View.GONE);
                    createButton.setEnabled(true);
                    statusLabel.setText("MP4 সেভ হয়নি: " + (e.getMessage() == null ? "অজানা সমস্যা" : e.getMessage()));
                });
            }
        });
    }

    @Override protected void onDestroy() {
        stopProgress();
        if (transformer != null) transformer.cancel();
        io.shutdownNow();
        super.onDestroy();
    }
}
