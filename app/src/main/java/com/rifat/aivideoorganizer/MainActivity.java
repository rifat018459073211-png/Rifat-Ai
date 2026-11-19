package com.rifat.aivideoorganizer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import android.util.TypedValue;

public class MainActivity extends Activity {
    private VideoView videoView;
    private TextView selectedName, categoryStatus, durationStatus;
    private EditText customDuration;
    private Uri selectedVideo;
    private String selectedDuration = "AI নিজে নির্বাচন করবে";
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private TextView label(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(Color.rgb(30,35,45));
        return t;
    }
    private Button button(String s) { Button b = new Button(this); b.setText(s); return b; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(22),dp(18),dp(28));
        page.setBackgroundColor(Color.rgb(246,248,252));
        scroll.addView(page);

        TextView title = label("AI Video Organizer",25);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.rgb(35,75,145));
        page.addView(title);
        TextView intro = label("ভিডিও বেছে নাও, সেরা মুহূর্ত খুঁজে ক্লিপ তৈরি করো।",15);
        intro.setPadding(0,dp(10),0,dp(14));
        page.addView(intro);

        Button choose = button("＋ ভিডিও নির্বাচন করো");
        page.addView(choose);
        choose.setOnClickListener(v -> openVideoPicker());
        selectedName = label("এখনো কোনো ভিডিও নেওয়া হয়নি",14);
        selectedName.setPadding(0,dp(8),0,dp(8));
        page.addView(selectedName);

        videoView = new VideoView(this);
        videoView.setBackgroundColor(Color.BLACK);
        page.addView(videoView,new LinearLayout.LayoutParams(-1,dp(220)));
        MediaController controls = new MediaController(this);
        controls.setAnchorView(videoView);
        videoView.setMediaController(controls);

        TextView durationHeading = label("ক্লিপের দৈর্ঘ্য",19);
        durationHeading.setPadding(0,dp(20),0,dp(8));
        page.addView(durationHeading);
        TextView durationHint = label("AI নিজে সেরা দৈর্ঘ্য বেছে নিতে পারে, অথবা তুমি নির্দিষ্ট সময় দিতে পারো।",14);
        page.addView(durationHint);

        RadioGroup durations = new RadioGroup(this);
        durations.setOrientation(RadioGroup.VERTICAL);
        String[] options = {"AI নিজে নির্বাচন করবে","৫ সেকেন্ড","১০ সেকেন্ড","২০ সেকেন্ড","৩০ সেকেন্ড","২ মিনিট","৩ মিনিট","১০ মিনিট","২৫ মিনিট","নিজের সময় লিখব"};
        int aiId = View.generateViewId();
        for (String option : options) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(option);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP,15);
            durations.addView(rb);
            if (option.startsWith("AI নিজে")) aiId = rb.getId();
        }
        durations.check(aiId);
        page.addView(durations);

        customDuration = new EditText(this);
        customDuration.setSingleLine(true);
        customDuration.setHint("নিজের সময়, যেমন ৪৫ সেকেন্ড বা ৫ মিনিট");
        customDuration.setInputType( android.text.InputType.TYPE_CLASS_TEXT );
        customDuration.setVisibility(View.GONE);
        page.addView(customDuration);

        durationStatus = label("নির্বাচিত: AI নিজে নির্বাচন করবে",14);
        durationStatus.setPadding(0,dp(8),0,dp(12));
        page.addView(durationStatus);
        durations.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton chosen = findViewById(checkedId);
            if (chosen == null) return;
            selectedDuration = chosen.getText().toString();
            boolean custom = selectedDuration.equals("নিজের সময় লিখব");
            customDuration.setVisibility(custom ? View.VISIBLE : View.GONE);
            durationStatus.setText("নির্বাচিত: " + selectedDuration);
        });
        Button applyDuration = button("সময় নিশ্চিত করো");
        page.addView(applyDuration);
        applyDuration.setOnClickListener(v -> {
            if (selectedDuration.equals("নিজের সময় লিখব")) {
                String value = customDuration.getText().toString().trim();
                if (value.isEmpty()) {
                    customDuration.setError("সময় লিখো");
                    return;
                }
                durationStatus.setText("নির্বাচিত কাস্টম সময়: " + value);
            } else {
                durationStatus.setText("নির্বাচিত: " + selectedDuration);
            }
        });

        TextView heading = label("ক্যাটাগরি",19);
        heading.setPadding(0,dp(20),0,dp(8));
        page.addView(heading);
        categoryStatus = label("ভিডিও নির্বাচন করে একটি ক্যাটাগরি বেছে নাও।",14);
        page.addView(categoryStatus);
        addCategory(page,"😂 Funny Clips");
        addCategory(page,"⚔ Action / Fighting Clips");
        addCategory(page,"🎬 Animation Clips");
        addCategory(page,"⭐ Best Moments");

        Button analyze = button("▶ ভিডিও বিশ্লেষণ শুরু");
        page.addView(analyze);
        analyze.setOnClickListener(v -> {
            if (selectedVideo == null) {
                categoryStatus.setText("আগে একটি ভিডিও নির্বাচন করো।");
                return;
            }
            String chosenCategory = categoryStatus.getText().toString();
            if (!chosenCategory.startsWith("নির্বাচিত ক্যাটাগরি:")) {
                categoryStatus.setText("আগে একটি ক্যাটাগরি নির্বাচন করো।");
                return;
            }
            showProcessingScreen(selectedVideo);
        });

        TextView note = label("AI বিশ্লেষণ ও FFmpeg ক্লিপ তৈরি Backend সংযোগের পর চালু হবে।",13);
        note.setPadding(0,dp(22),0,0);
        page.addView(note);
        setContentView(scroll);
    }

    private void addCategory(LinearLayout page, String category) {
        Button b = button(category);
        page.addView(b);
        b.setOnClickListener(v -> {
            if (selectedVideo == null) categoryStatus.setText("আগে একটি ভিডিও নির্বাচন করো।");
            else categoryStatus.setText("নির্বাচিত ক্যাটাগরি: " + category + "\nক্লিপ বিশ্লেষণ ও এক্সপোর্ট পরের ধাপে যুক্ত হবে।");
        });
    }

    private void showProcessingScreen(Uri video) {
        ScrollView processingScroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22),dp(30),dp(22),dp(24));
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setBackgroundColor(Color.rgb(246,248,252));
        processingScroll.addView(panel);

        TextView title = label("AI ভিডিও প্রসেসিং",24);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        TextView file = label("নির্বাচিত ভিডিও: " + video.getLastPathSegment(),14);
        file.setPadding(0,dp(18),0,dp(18));
        panel.addView(file);

        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1,dp(8));
        progressParams.setMargins(0,dp(12),0,dp(20));
        panel.addView(progress, progressParams);

        TextView percent = label("অগ্রগতি: অপেক্ষমাণ",18);
        percent.setGravity(Gravity.CENTER);
        panel.addView(percent);

        TextView status = label(
            "প্রসেসিং স্ক্রিন প্রস্তুত। কিন্তু Gemini Backend এখনো অ্যাপের সঙ্গে সংযুক্ত নয়, তাই ভিডিও আপলোড বা AI বিশ্লেষণ শুরু হয়নি। Backend যুক্ত হলে এখানে প্রকৃত আপলোড/প্রসেসিং অগ্রগতি ও ধাপ দেখানো হবে।",
            15
        );
        status.setPadding(0,dp(18),0,dp(24));
        panel.addView(status);

        Button back = button("← ভিডিও নির্বাচনে ফিরে যাও");
        panel.addView(back);
        back.setOnClickListener(v -> setContentView(buildMainScreen()));
        setContentView(processingScroll);
    }

    private View buildMainScreen() {
        // Recreate the main screen by restarting this Activity without losing the selected video.
        recreate();
        return new View(this);
    }

    private void openVideoPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        startActivityForResult(i,101);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedVideo = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(selectedVideo,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            selectedName.setText("ভিডিও নির্বাচিত হয়েছে: " + selectedVideo.getLastPathSegment());
            videoView.setVideoURI(selectedVideo);
            videoView.start();
            categoryStatus.setText("ভিডিও প্রস্তুত। এখন ক্যাটাগরি নির্বাচন করো।");
        }
    }

    @Override protected void onStop() {
        if (videoView != null) videoView.pause();
        super.onStop();
    }
}
