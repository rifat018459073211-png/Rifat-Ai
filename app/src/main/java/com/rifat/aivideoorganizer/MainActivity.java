package com.rifat.aivideoorganizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ContentValues;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.provider.MediaStore;
import android.content.pm.PackageInstaller;
import androidx.core.content.FileProvider;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Base64;
import android.util.TypedValue;
import android.text.InputType;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String API_URL = "https://rifat-ai.rifat018459073211.workers.dev/v1/generate";
    private static final String RELEASE_API = "https://api.github.com/repos/rifat018459073211-png/Rifat-Ai/releases/latest";
    private static final String APK_ASSET_NAME = "Rifat-Ai.apk";
    private static final long MAX_APK_BYTES = 100L * 1024L * 1024L;
    private static final String PREFS = "rifat_ai_secure_settings";
    private static final String TOKEN_KEY = "worker_token";
    private static final String KEY_ALIAS = "RifatAiWorkerTokenKey";
    private static final int FRAME_COUNT = 5;
    private static final int MAX_REQUEST_BYTES = 950 * 1024;

    private VideoView videoView;
    private TextView selectedName, categoryStatus, durationStatus;
    private EditText customDuration;
    private Uri selectedVideo;
    private String selectedCategory = "";
    private String selectedDuration = "AI নিজে নির্বাচন করবে";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean updateCheckRunning = false;
    private File downloadedUpdate;
    private Uri videoToVideoUri;
    private TextView videoToVideoSelectedText, videoToVideoStatus;
    private ProgressBar videoToVideoProgress;
    private Button videoToVideoGenerateButton;
    private FrameLayout rootLayout;
    private View startupGate;
    private View mainContent;

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }

    // Lightweight native entrance motion; no extra animation library or network dependency.
    private void animateChildren(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(dp(12));
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(Math.min(i * 35L, 350L))
                .setDuration(320L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        }
    }

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
        ScrollView dashboardScroll = new ScrollView(this);
        LinearLayout dashboard = new LinearLayout(this);
        dashboard.setOrientation(LinearLayout.VERTICAL);
        dashboard.setPadding(dp(16), dp(22), dp(16), dp(28));
        dashboard.setBackgroundColor(Color.rgb(246,248,252));
        dashboardScroll.addView(dashboard);

        TextView title = label("Rifat-Ai", 29);
        title.setTextColor(Color.rgb(28,66,124));
        title.setGravity(Gravity.CENTER);
        dashboard.addView(title);
        TextView intro = label("Create • Learn • Organize", 13);
        intro.setTextColor(Color.rgb(105,116,133));
        intro.setGravity(Gravity.CENTER);
        intro.setPadding(0, dp(4), 0, dp(20));
        dashboard.addView(intro);

        TextView section = label("তোমার ওয়ার্কস্পেস", 19);
        section.setPadding(dp(2), 0, 0, dp(10));
        dashboard.addView(section);
        addFeatureGrid(dashboard,
            new String[]{"AI ভিডিও অর্গানাইজার","টিউটোরিয়াল ও শেখা","ভিডিও জেনারেটর","সংরক্ষিত বিশ্লেষণ"},
            new String[]{"ভিডিও বিশ্লেষণ ও সেরা মুহূর্ত খুঁজে নাও","ভিডিও ও AI ব্যবহারের গাইড শিখো","নিজের ভিডিও তৈরির টুল দেখো","আগের বিশ্লেষণ আবার দেখো"},
            new int[]{0,1,2,3},
            new Runnable[]{() -> showVideoOrganizerPage(), () -> showLearningCenter(), () -> showVideoGeneratorPage(), () -> showHistory()});
        Button tutorialVideo = greenButton("▶  ভিডিও টিউটোরিয়াল");
        LinearLayout.LayoutParams greenParams = new LinearLayout.LayoutParams(-1, dp(54));
        greenParams.topMargin = dp(10);
        dashboard.addView(tutorialVideo, greenParams);
        tutorialVideo.setOnClickListener(v -> showVideoTutorials());
        Button create = greenButton("＋  CREATE VIDEO");
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(-1, dp(56));
        createParams.topMargin = dp(10);
        dashboard.addView(create, createParams);
        create.setOnClickListener(v -> showCreateVideoPage());
        addDashboardCard(dashboard, "অ্যাপ আপডেট", "সর্বশেষ সংস্করণ পরীক্ষা করো", () -> checkForUpdates(true));
        TextView footer = label("Rifat-Ai • Free workspace", 12);
        footer.setTextColor(Color.rgb(125,132,145));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(18), 0, 0);
        dashboard.addView(footer);

        mainContent = dashboardScroll;
        rootLayout = new FrameLayout(this);
        rootLayout.addView(dashboardScroll, new FrameLayout.LayoutParams(-1, -1));
        startupGate = buildCheckingScreen();
        rootLayout.addView(startupGate, new FrameLayout.LayoutParams(-1, -1));
        setContentView(rootLayout);
        animateChildren(dashboard);
        checkForUpdates(false);
    }

    private Button greenButton(String text) {
        Button b = button(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setAllCaps(false);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(25, 155, 91));
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) b.setElevation(dp(3));
        return b;
    }

    private void addFeatureGrid(LinearLayout parent, String[] titles, String[] details, int[] art, Runnable[] actions) {
        for (int i = 0; i < titles.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 2 && i + col < titles.length; col++) {
                final int index = i + col;
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(roundBackground(Color.WHITE, Color.rgb(226,232,241), 16));
                card.setClipToOutline(true);
                card.setElevation(dp(2));
                FeatureArtView artwork = new FeatureArtView(this, art[index]);
                card.addView(artwork, new LinearLayout.LayoutParams(-1, dp(112)));
                TextView heading = label(titles[index], 15);
                heading.setTextColor(Color.rgb(35,66,112));
                heading.setPadding(dp(11), dp(10), dp(9), 0);
                TextView detail = label(details[index], 12);
                detail.setTextColor(Color.rgb(105,113,127));
                detail.setPadding(dp(11), dp(5), dp(9), dp(12));
                card.addView(heading);
                card.addView(detail);
                card.setClickable(true);
                card.setFocusable(true);
                card.setOnClickListener(v -> actions[index].run());
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
                cp.setMargins(dp(4), dp(4), dp(4), dp(10));
                row.addView(card, cp);
            }
            parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private android.graphics.drawable.GradientDrawable roundBackground(int fill, int stroke, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private void addDashboardCard(LinearLayout parent, String title, String description, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.setBackground(roundBackground(Color.WHITE, Color.rgb(225,231,241), 14));
        TextView heading = label(title, 15);
        heading.setTextColor(Color.rgb(35,75,145));
        TextView detail = label(description, 12);
        detail.setTextColor(Color.rgb(85,92,105));
        detail.setPadding(0, dp(4), 0, 0);
        card.addView(heading);
        card.addView(detail);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        parent.addView(card, params);
        card.setClickable(true);
        card.setOnClickListener(v -> action.run());
    }

    private void showCreateVideoPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(22), dp(16), dp(28));
        page.setBackgroundColor(Color.rgb(246,248,252));
        scroll.addView(page);
        TextView title = label("Create Video", 25);
        title.setTextColor(Color.rgb(28,66,124));
        page.addView(title);
        TextView subtitle = label("তোমার প্রয়োজন অনুযায়ী একটি পদ্ধতি বেছে নাও।", 14);
        subtitle.setTextColor(Color.rgb(100,110,125));
        subtitle.setPadding(0, dp(6), 0, dp(14));
        page.addView(subtitle);
        addFeatureGrid(page,
            new String[]{"Text to Video","Image to Video","Video to Video","Ready-made Videos"},
            new String[]{"লেখা থেকে ভিডিও তৈরির পদ্ধতি","ছবি থেকে চলমান দৃশ্য","নিজের ভিডিও রূপান্তর","ডেমো ও নমুনা ভিডিও"},
            new int[]{4,5,6,7},
            new Runnable[]{() -> showGeneratorInfo("Text to Video","একটি দৃশ্য, বিষয়, পরিবেশ, ক্যামেরার গতি ও ভিডিওর ধরন লিখে AI-কে নির্দেশ দেওয়া হয়। Rifat-Ai-তে বাস্তব জেনারেশন ইঞ্জিন এখনো যুক্ত হয়নি।"),
                () -> showGeneratorInfo("Image to Video","একটি ছবি বেছে নিয়ে কোন অংশ নড়বে, ক্যামেরা কীভাবে চলবে এবং দৃশ্যের মুড কেমন হবে তা বর্ণনা করা হয়। এই জেনারেশন ইঞ্জিন এখনো যুক্ত হয়নি।"),
                () -> showVideoToVideoPage(),
                () -> showReadyMadeVideoLibrary()});
        Button tutorial = greenButton("▶  ভিডিও টিউটোরিয়াল");
        page.addView(tutorial, new LinearLayout.LayoutParams(-1, dp(52)));
        tutorial.setOnClickListener(v -> showVideoTutorials());
        Button back = button("←  হোমে ফিরে যাও");
        page.addView(back);
        back.setOnClickListener(v -> recreate());
        setContentView(scroll);
        animateChildren(page);
    }

    private void showGeneratorInfo(String titleText, String description) {
        new AlertDialog.Builder(this).setTitle(titleText).setMessage(description)
            .setPositiveButton("ঠিক আছে", null).show();
    }


    // First local Video-to-Video release: GPU cartoon stylization exported as MP4.
    private void showVideoToVideoPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(28));
        page.setBackgroundColor(Color.rgb(246,248,252));
        scroll.addView(page);
        TextView title = label("Video to Video • Cartoon", 23);
        title.setTextColor(Color.rgb(28,66,124));
        page.addView(title);
        TextView note = label("ভিডিও বেছে নিয়ে ফোনের GPU দিয়ে কার্টুন-স্টাইল ফিল্টার প্রয়োগ করো। এটি প্রথম স্থানীয় স্টাইল-রূপান্তর সংস্করণ; diffusion AI মডেল নয়।", 13);
        note.setTextColor(Color.rgb(95,105,120));
        note.setPadding(0, dp(8), 0, dp(14));
        page.addView(note);
        RadioGroup mode = new RadioGroup(this);
        mode.setOrientation(RadioGroup.VERTICAL);
        RadioButton realMode = new RadioButton(this);
        realMode.setText("আসল ভিডিও → কার্টুন");
        realMode.setId(View.generateViewId());
        realMode.setChecked(true);
        RadioButton cartoonMode = new RadioButton(this);
        cartoonMode.setText("কার্টুন ভিডিও → অন্য কার্টুন স্টাইল");
        cartoonMode.setId(View.generateViewId());
        mode.addView(realMode);
        mode.addView(cartoonMode);
        page.addView(mode);
        TextView styleLabel = label("কার্টুন স্টাইল", 14);
        styleLabel.setTextColor(Color.rgb(35,66,112));
        styleLabel.setPadding(0, dp(12), 0, dp(5));
        page.addView(styleLabel);
        Spinner styles = new Spinner(this);
        String[] styleNames = {"Classic Anime", "Soft Cartoon", "Bold Comic"};
        styles.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, styleNames));
        page.addView(styles);
        Button pick = greenButton("＋  ভিডিও নির্বাচন করো");
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(-1, dp(52));
        pickParams.topMargin = dp(14);
        page.addView(pick, pickParams);
        videoToVideoSelectedText = label(videoToVideoUri == null ? "এখনো কোনো ভিডিও নির্বাচন করা হয়নি" : "ভিডিও নির্বাচিত হয়েছে", 13);
        videoToVideoSelectedText.setTextColor(Color.rgb(85,95,110));
        videoToVideoSelectedText.setPadding(0, dp(10), 0, dp(8));
        page.addView(videoToVideoSelectedText);
        pick.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            startActivityForResult(intent, 302);
        });
        videoToVideoProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        videoToVideoProgress.setMax(100);
        videoToVideoProgress.setProgress(0);
        videoToVideoProgress.setVisibility(View.GONE);
        page.addView(videoToVideoProgress, new LinearLayout.LayoutParams(-1, dp(8)));
        videoToVideoStatus = label("ভিডিও নির্বাচন করে Generate চাপো।", 12);
        videoToVideoStatus.setTextColor(Color.rgb(90,100,115));
        videoToVideoStatus.setPadding(0, dp(8), 0, dp(12));
        page.addView(videoToVideoStatus);
        videoToVideoGenerateButton = greenButton("▶  GENERATE CARTOON VIDEO");
        page.addView(videoToVideoGenerateButton, new LinearLayout.LayoutParams(-1, dp(54)));
        videoToVideoGenerateButton.setOnClickListener(v -> {
            if (videoToVideoUri == null) {
                Toast.makeText(this, "আগে একটি ভিডিও নির্বাচন করো", Toast.LENGTH_SHORT).show();
                return;
            }
            startCartoonVideoExport((int) styles.getSelectedItemPosition());
        });
        TextView rule = label("নিয়ম: দুই ধরনের ইনপুটেই আউটপুট কার্টুন-স্টাইল থাকবে। কার্টুন ইনপুটকে বাস্তব মানুষের মতো করার কোনো মোড নেই।", 12);
        rule.setTextColor(Color.rgb(45,115,82));
        rule.setPadding(0, dp(14), 0, dp(12));
        page.addView(rule);
        Button back = button("←  ফিরে যাও");
        page.addView(back);
        back.setOnClickListener(v -> showCreateVideoPage());
        setContentView(scroll);
    }

    private void startCartoonVideoExport(int style) {
        if (videoToVideoGenerateButton != null) videoToVideoGenerateButton.setEnabled(false);
        if (videoToVideoProgress != null) {
            videoToVideoProgress.setVisibility(View.VISIBLE);
            videoToVideoProgress.setProgress(0);
        }
        if (videoToVideoStatus != null) videoToVideoStatus.setText("ভিডিও প্রস্তুত করা হচ্ছে…");
        executor.execute(() -> {
            File input = null;
            try {
                input = new File(getCacheDir(), "v2v_input_" + System.currentTimeMillis() + ".mp4");
                try (InputStream in = getContentResolver().openInputStream(videoToVideoUri);
                     OutputStream out = new FileOutputStream(input)) {
                    if (in == null) throw new java.io.IOException("ভিডিওটি পড়া যাচ্ছে না");
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                }
                File movies = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Rifat-Ai");
                if (!movies.exists() && !movies.mkdirs()) throw new java.io.IOException("আউটপুট ফোল্ডার তৈরি করা যায়নি");
                File output = new File(movies, "Cartoon_" + System.currentTimeMillis() + ".mp4");
                final File inputFile = input;
                final File outputFile = output;
                new com.daasuu.gpuv.composer.GPUMp4Composer(input.getAbsolutePath(), output.getAbsolutePath())
                        .filter(new CartoonGlFilter(style))
                        .videoBitrate(4_000_000)
                        .listener(new com.daasuu.gpuv.composer.GPUMp4Composer.Listener() {
                            @Override public void onProgress(double progress) {
                                runOnUiThread(() -> {
                                    if (videoToVideoProgress != null && progress >= 0)
                                        videoToVideoProgress.setProgress((int)Math.max(0, Math.min(100, progress * 100)));
                                    if (videoToVideoStatus != null) videoToVideoStatus.setText("কার্টুন ভিডিও তৈরি হচ্ছে… " +
                                            (progress < 0 ? "" : ((int)(progress * 100)) + "%"));
                                });
                            }
                            @Override public void onCompleted() {
                                Uri saved = null;
                                String error = null;
                                try { saved = publishCartoonVideo(outputFile); }
                                catch (Exception e) { error = e.getMessage(); }
                                final Uri savedUri = saved;
                                final String saveError = error;
                                runOnUiThread(() -> {
                                    if (videoToVideoGenerateButton != null) videoToVideoGenerateButton.setEnabled(true);
                                    if (videoToVideoProgress != null) videoToVideoProgress.setProgress(100);
                                    if (videoToVideoStatus != null) videoToVideoStatus.setText(savedUri != null
                                            ? "সম্পন্ন! কার্টুন ভিডিও MP4 হিসেবে সংরক্ষিত হয়েছে।"
                                            : "ভিডিও তৈরি হয়েছে, তবে সংরক্ষণে সমস্যা: " + saveError);
                                    if (savedUri != null) {
                                        Toast.makeText(MainActivity.this, "কার্টুন ভিডিও তৈরি হয়েছে", Toast.LENGTH_LONG).show();
                                        shareCartoonVideo(savedUri);
                                    }
                                });
                                inputFile.delete();
                            }
                            @Override public void onCanceled() {
                                runOnUiThread(() -> {
                                    if (videoToVideoGenerateButton != null) videoToVideoGenerateButton.setEnabled(true);
                                    if (videoToVideoStatus != null) videoToVideoStatus.setText("রূপান্তর বাতিল হয়েছে।");
                                });
                                inputFile.delete();
                            }
                            @Override public void onFailed(Exception exception) {
                                runOnUiThread(() -> {
                                    if (videoToVideoGenerateButton != null) videoToVideoGenerateButton.setEnabled(true);
                                    if (videoToVideoStatus != null) videoToVideoStatus.setText("রূপান্তর ব্যর্থ: " + exception.getMessage());
                                    new AlertDialog.Builder(MainActivity.this).setTitle("ভিডিও তৈরি হয়নি")
                                            .setMessage(exception.getMessage()).setPositiveButton("ঠিক আছে", null).show();
                                });
                                inputFile.delete();
                            }
                        }).start();
            } catch (Exception e) {
                if (input != null) input.delete();
                runOnUiThread(() -> {
                    if (videoToVideoGenerateButton != null) videoToVideoGenerateButton.setEnabled(true);
                    if (videoToVideoStatus != null) videoToVideoStatus.setText("ভিডিও প্রস্তুত করা যায়নি: " + e.getMessage());
                });
            }
        });
    }

    private Uri publishCartoonVideo(File file) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, file.getName());
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Rifat-Ai");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new java.io.IOException("গ্যালারিতে ফাইল তৈরি করা যায়নি");
            try (InputStream in = new java.io.FileInputStream(file);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new java.io.IOException("গ্যালারি ফাইলে লেখা যায়নি");
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            } catch (Exception e) {
                getContentResolver().delete(uri, null, null);
                throw e;
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContentResolver().update(uri, ready, null, null);
            file.delete();
            return uri;
        }
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
    }

    private void shareCartoonVideo(Uri uri) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("video/mp4");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "কার্টুন ভিডিও শেয়ার বা সংরক্ষণ করো"));
    }

    private static final class CartoonGlFilter extends com.daasuu.gpuv.egl.filter.GlFilter {
        private static final String FRAGMENT =
                "precision mediump float; varying highp vec2 vTextureCoord; uniform lowp sampler2D sTexture;" +
                "uniform highp float texelX; uniform highp float texelY; uniform highp float levels;" +
                "uniform highp float edgePower; uniform highp float edgeLimit;" +
                "float lum(vec3 c){return dot(c,vec3(0.299,0.587,0.114));}" +
                "void main(){vec2 uv=vTextureCoord; vec3 c=texture2D(sTexture,uv).rgb;" +
                "float e=0.0;" +
                "e+=abs(lum(c)-lum(texture2D(sTexture,uv+vec2(texelX,0.0)).rgb));" +
                "e+=abs(lum(c)-lum(texture2D(sTexture,uv-vec2(texelX,0.0)).rgb));" +
                "e+=abs(lum(c)-lum(texture2D(sTexture,uv+vec2(0.0,texelY)).rgb));" +
                "e+=abs(lum(c)-lum(texture2D(sTexture,uv-vec2(0.0,texelY)).rgb));" +
                "float line=smoothstep(edgeLimit,edgeLimit+0.10,e*edgePower);" +
                "vec3 toon=floor(c*levels)/levels;" +
                "vec3 ink=mix(toon,vec3(0.035,0.035,0.055),line);" +
                "gl_FragColor=vec4(ink,1.0);}";
        private final int style;
        private int width = 720, height = 1280;
        CartoonGlFilter(int style) {
            super(DEFAULT_VERTEX_SHADER, FRAGMENT);
            this.style = style;
        }
        @Override public void setFrameSize(int width, int height) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
        }
        @Override protected void onDraw() {
            android.opengl.GLES20.glUniform1f(getHandle("texelX"), 1f / width);
            android.opengl.GLES20.glUniform1f(getHandle("texelY"), 1f / height);
            float levels = style == 1 ? 7f : (style == 2 ? 4f : 5f);
            float power = style == 2 ? 4.0f : (style == 1 ? 2.3f : 3.0f);
            float limit = style == 2 ? 0.11f : (style == 1 ? 0.20f : 0.16f);
            android.opengl.GLES20.glUniform1f(getHandle("levels"), levels);
            android.opengl.GLES20.glUniform1f(getHandle("edgePower"), power);
            android.opengl.GLES20.glUniform1f(getHandle("edgeLimit"), limit);
        }
    }

    private void showVideoTutorials() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(26));
        page.setBackgroundColor(Color.rgb(246,248,252));
        scroll.addView(page);
        TextView title = label("ভিডিও টিউটোরিয়াল", 24);
        title.setTextColor(Color.rgb(28,66,124));
        page.addView(title);
        TextView note = label("এখানে Rifat-Ai ব্যবহারের ভিডিও শেখার বিষয়গুলো সাজানো হয়েছে। ভিডিও ফাইল/লিংক যোগ না হওয়া পর্যন্ত নিচেরগুলো লিখিত গাইড হিসেবে থাকবে।", 13);
        note.setTextColor(Color.rgb(105,113,127));
        note.setPadding(0, dp(8), 0, dp(14));
        page.addView(note);
        String[] names = {"অ্যাপ পরিচিতি","ভিডিও নির্বাচন","AI বিশ্লেষণ","ক্যাটাগরি ও সময়","History ব্যবহার","নিরাপত্তা ও Token","APK আপডেট"};
        String[] bodies = {"Rifat-Ai-এর হোম স্ক্রিন ও প্রধান অপশনগুলোর পরিচিতি।","ফোন থেকে ভিডিও নির্বাচন ও প্রিভিউ চালানো।","নমুনা ফ্রেম দিয়ে AI বিশ্লেষণ কীভাবে হয়।","ক্লিপের সময় ও ক্যাটাগরি বেছে নেওয়া।","আগের ফলাফল দেখা ও মুছে ফেলা।","গোপন Token নিরাপদে রাখা।","নতুন APK ডাউনলোড ও ইনস্টল করা।"};
        for(int i=0;i<names.length;i++){
            final int k=i;
            Button item=greenButton("▶  "+names[i]);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));
            p.bottomMargin=dp(8);
            page.addView(item,p);
            item.setOnClickListener(v -> showTutorialDetail(names[k],bodies[k]));
        }
        Button back=button("←  ফিরে যাও");
        page.addView(back);
        back.setOnClickListener(v -> showCreateVideoPage());
        setContentView(scroll);
    }

    private void showReadyMadeVideoLibrary() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(26));
        page.setBackgroundColor(Color.rgb(246,248,252));
        scroll.addView(page);
        TextView title = label("Ready-made Video Demos", 23);
        title.setTextColor(Color.rgb(28,66,124));
        page.addView(title);
        TextView note = label("এখানে ৫০টি ডেমো ভিডিওর ধারণা/প্রম্পট সাজানো হয়েছে। এগুলো এখনো প্লে করা যায় এমন AI-তৈরি ভিডিও ফাইল নয়; বাস্তব ভিডিও দেখাতে লাইসেন্সসম্মত ভিডিও ফাইল বা প্রকাশ্য ভিডিও লিংক যোগ করতে হবে।", 13);
        note.setTextColor(Color.rgb(105,113,127));
        note.setPadding(0, dp(8), 0, dp(12));
        page.addView(note);
        String[] themes = {"সূর্যোদয়ের ড্রোন দৃশ্য","বৃষ্টিভেজা শহর","সমুদ্রের ঢেউ","পাহাড়ি রাস্তা","রাতের আকাশ","ফুলের বাগান","বরফে ঢাকা বন","মরুভূমির সূর্যাস্ত","নদীর ওপর সেতু","মেঘের ভেলা","কফি তৈরির দৃশ্য","রান্নার ক্লোজআপ","শহরের টাইমল্যাপস","ট্রেনের জানালা","সমুদ্রতীরের হাঁটা","বনের ঝরনা","রঙিন প্রজাপতি","মহাকাশের কল্পদৃশ্য","রোবটের ভবিষ্যৎ শহর","কার্টুন চরিত্রের যাত্রা","সাইকেল রাইড","ফুটবল মাঠ","ক্রিকেট স্টেডিয়াম","স্কেটবোর্ড দৃশ্য","নিয়ন আলোয় রাস্তা","পুরোনো গ্রামের সকাল","ধানক্ষেতের বাতাস","নৌকার ভ্রমণ","জলপ্রপাত","আগ্নেয়গিরির দূর দৃশ্য","রাতের শহরের আলো","ক্যামেরা প্যান শট","স্লো মোশন বৃষ্টি","পাতার ওপর শিশির","মোমবাতির আলো","কাগজের অ্যানিমেশন","3D জ্যামিতিক আকৃতি","অ্যাবস্ট্রাক্ট তরঙ্গ","ফ্যান্টাসি দুর্গ","জাদুকরী বন","মেঘের ওপর দ্বীপ","রঙিন কাচের প্রতিফলন","বইয়ের পাতা ওল্টানো","পেন্সিল আঁকার টাইমল্যাপস","ফ্যাশন প্রোডাক্ট শট","জুতার বিজ্ঞাপনধর্মী দৃশ্য","মোবাইল ফোনের প্রোডাক্ট শট","গেমিং সেটআপ","ফায়ারওয়ার্কস দূর থেকে","শীতের কুয়াশাচ্ছন্ন সকাল"};
        for(int i=0;i<themes.length;i++){
            final String theme=themes[i];
            LinearLayout item=new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(13),dp(11),dp(13),dp(11));
            item.setBackground(roundBackground(Color.WHITE,Color.rgb(225,231,241),12));
            TextView n=label(String.format(java.util.Locale.getDefault(),"%02d  %s",i+1,theme),14);
            n.setTextColor(Color.rgb(35,66,112));
            TextView d=label("ডেমো প্রম্পট আইডিয়া • ভিডিও ফাইল এখনো সংযুক্ত নয়",11);
            d.setTextColor(Color.rgb(115,122,134));
            item.addView(n); item.addView(d);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.bottomMargin=dp(7);
            page.addView(item,p);
            item.setOnClickListener(v -> showGeneratorInfo(theme,"ডেমো আইডিয়া: "+theme+"। এটি এখনো প্লে করা যায় এমন ভিডিও নয়; ভিডিও জেনারেটর ইঞ্জিন যুক্ত হলে এই প্রম্পট ব্যবহার করা যাবে।"));
        }
        Button back=button("←  Create Video-তে ফিরে যাও");
        page.addView(back);
        back.setOnClickListener(v -> showCreateVideoPage());
        setContentView(scroll);
    }

    private class FeatureArtView extends View {
        private final int type;
        private final android.graphics.Paint paint = new android.graphics.Paint(3);
        FeatureArtView(android.content.Context c, int t) { super(c); type=t; setLayerType(View.LAYER_TYPE_SOFTWARE,null); }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            int[][] palettes={{0xffdcecff,0xff8eb8ed},{0xffd9f4e7,0xff80cba5},{0xffffe8d6,0xffffb27b},{0xffe8e2ff,0xffaa9be8},
                {0xffd9eaff,0xff83b5e8},{0xffffe6ef,0xffee9bb9},{0xffe4f0ff,0xff9dbce9},{0xffe1f3ec,0xff91cdb4}};
            int[] colors=palettes[type%palettes.length];
            paint.setShader(new android.graphics.LinearGradient(0,0,getWidth(),getHeight(),colors[0],colors[1],android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,getWidth(),getHeight(),paint);
            paint.setShader(null);
            float cx=getWidth()/2f, cy=getHeight()/2f;
            paint.setColor(0x55ffffff); canvas.drawCircle(cx,cy,dp(43),paint);
            paint.setColor(0xff294e79); paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(dp(3)); paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            if(type==0){ canvas.drawRoundRect(cx-dp(28),cy-dp(19),cx+dp(28),cy+dp(19),dp(7),dp(7),paint); canvas.drawCircle(cx,cy,dp(10),paint); canvas.drawRoundRect(cx-dp(13),cy-dp(25),cx+dp(4),cy-dp(19),dp(3),dp(3),paint); }
            else if(type==1||type==5){ canvas.drawRect(cx-dp(25),cy-dp(23),cx+dp(25),cy+dp(23),paint); canvas.drawCircle(cx+dp(12),cy-dp(10),dp(5),paint); android.graphics.Path path=new android.graphics.Path(); path.moveTo(cx-dp(22),cy+dp(15)); path.lineTo(cx-dp(5),cy-dp(4)); path.lineTo(cx+dp(5),cy+dp(6)); path.lineTo(cx+dp(15),cy-dp(3)); path.lineTo(cx+dp(23),cy+dp(15)); canvas.drawPath(path,paint); }
            else if(type==2||type==4){ canvas.drawRoundRect(cx-dp(25),cy-dp(18),cx+dp(25),cy+dp(18),dp(6),dp(6),paint); android.graphics.Path p=new android.graphics.Path(); p.moveTo(cx-dp(7),cy-dp(11)); p.lineTo(cx+dp(13),cy); p.lineTo(cx-dp(7),cy+dp(11)); p.close(); paint.setStyle(android.graphics.Paint.Style.FILL); canvas.drawPath(p,paint); paint.setStyle(android.graphics.Paint.Style.STROKE); }
            else { canvas.drawCircle(cx,cy,dp(23),paint); canvas.drawLine(cx,cy-dp(34),cx,cy+dp(34),paint); canvas.drawLine(cx-dp(34),cy,cx+dp(34),cy,paint); canvas.drawLine(cx-dp(24),cy-dp(24),cx+dp(24),cy+dp(24),paint); }
            paint.setStyle(android.graphics.Paint.Style.FILL);
        }
    }

    private void showVideoOrganizerPage() {
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

        Button historyButton = button("▤ সংরক্ষিত বিশ্লেষণ / History");
        page.addView(historyButton);
        historyButton.setOnClickListener(v -> showHistory());

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
        page.addView(label("AI নিজে সেরা দৈর্ঘ্য বেছে নিতে পারে, অথবা তুমি নির্দিষ্ট সময় দিতে পারো।",14));

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
        customDuration.setInputType(InputType.TYPE_CLASS_TEXT);
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
                if (value.isEmpty()) { customDuration.setError("সময় লিখো"); return; }
                selectedDuration = value + " (কাস্টম)";
                durationStatus.setText("নির্বাচিত কাস্টম সময়: " + selectedDuration);
            } else durationStatus.setText("নির্বাচিত: " + selectedDuration);
        });

        TextView heading = label("ক্যাটাগরি",19);
        heading.setPadding(0,dp(20),0,dp(8));
        page.addView(heading);
        categoryStatus = label("ভিডিও নির্বাচন করে একটি ক্যাটাগরি বেছে নাও।",14);
        page.addView(categoryStatus);
        addCategory(page,"Funny Clips");
        addCategory(page,"Action Clips");
        addCategory(page,"Animation Clips");
        addCategory(page,"Best Moments");

        Button analyze = button("▶ ভিডিও বিশ্লেষণ শুরু");
        page.addView(analyze);
        analyze.setOnClickListener(v -> {
            if (selectedVideo == null) {
                categoryStatus.setText("আগে একটি ভিডিও নির্বাচন করো।");
                return;
            }
            if (selectedCategory.isEmpty()) {
                categoryStatus.setText("আগে একটি ক্যাটাগরি নির্বাচন করো।");
                return;
            }
            if (selectedDuration.equals("নিজের সময় লিখব")) {
                String custom = customDuration.getText().toString().trim();
                if (custom.isEmpty()) {
                    customDuration.setError("ক্লিপের সময় লিখো, যেমন ৪৫ সেকেন্ড বা ২ মিনিট");
                    customDuration.requestFocus();
                    return;
                }
                selectedDuration = custom + " (কাস্টম)";
                durationStatus.setText("নির্বাচিত কাস্টম সময়: " + selectedDuration);
            }
            String token = readStoredToken();
            if (token == null || token.isEmpty()) showTokenDialog();
            else startAnalysis(token);
        });

        TextView note = label("বিশ্লেষণের জন্য ভিডিওর সম্পূর্ণ ফাইল নয়, কয়েকটি নমুনা ফ্রেম Gemini-তে পাঠানো হবে।",13);
        note.setPadding(0,dp(22),0,0);
        page.addView(note);
        Button backHome = button("← হোমে ফিরে যাও");
        page.addView(backHome, 0);
        backHome.setOnClickListener(v -> recreate());
        setContentView(scroll);
        animateChildren(page);
    }

    private void showLearningCenter() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(26));
        page.setBackgroundColor(Color.rgb(246,248,252));
        scroll.addView(page);
        TextView title = label("টিউটোরিয়াল ও শেখার কেন্দ্র", 24);
        title.setTextColor(Color.rgb(35,75,145));
        page.addView(title);
        TextView intro = label("সহজ বাংলায় ধাপে ধাপে শেখার গাইড। প্রতিটি বিষয় খুলে পড়তে পারো।", 14);
        intro.setPadding(0, dp(8), 0, dp(16));
        page.addView(intro);
        addDashboardCard(page, "১. Rifat-Ai কীভাবে কাজ করে",
            "ভিডিও নির্বাচন থেকে AI বিশ্লেষণ পর্যন্ত পুরো প্রক্রিয়া", () -> showTutorialDetail("Rifat-Ai কীভাবে কাজ করে",
            "১) AI ভিডিও অর্গানাইজার খুলে ফোন থেকে একটি ভিডিও নির্বাচন করো।\n\n২) ভিডিওর ক্যাটাগরি এবং কাঙ্ক্ষিত ক্লিপের দৈর্ঘ্য বেছে নাও।\n\n৩) বিশ্লেষণ শুরু করলে অ্যাপ ভিডিওর পাঁচটি নমুনা ফ্রেম নেয় এবং Cloudflare Worker-এর মাধ্যমে Gemini-তে পাঠায়।\n\n৪) Gemini নমুনা দেখে সম্ভাব্য ভালো মুহূর্তের আনুমানিক টাইমস্ট্যাম্প ও কারণ জানায়।\n\nমনে রেখো: পাঁচটি নমুনা ফ্রেম দিয়ে পুরো ভিডিওর প্রতিটি মুহূর্ত দেখা হয় না; তাই সময়গুলো আনুমানিক।"));
        addDashboardCard(page, "২. ভিডিও নির্বাচন ও প্রিভিউ",
            "ফোনের গ্যালারি/ফাইল থেকে ভিডিও নেওয়া", () -> showTutorialDetail("ভিডিও নির্বাচন ও প্রিভিউ",
            "AI ভিডিও অর্গানাইজার খুলে ‘ভিডিও নির্বাচন করো’ চাপো। Android-এর ফাইল পিকার থেকে একটি ভিডিও বেছে নাও। ভিডিওটি প্রিভিউতে দেখা যাবে। ফাইলের অনুমতি না দিলে আবার নির্বাচন করতে হতে পারে।"));
        addDashboardCard(page, "৩. ক্যাটাগরি ও ক্লিপের দৈর্ঘ্য",
            "Funny, Action, Animation ও Best Moments বেছে নেওয়া", () -> showTutorialDetail("ক্যাটাগরি ও ক্লিপের দৈর্ঘ্য",
            "ভিডিও নির্বাচনের পর একটি ক্যাটাগরি বেছে নাও। তারপর AI-কে দৈর্ঘ্য নিজে বেছে দিতে পারো, অথবা তালিকা থেকে সময় নির্বাচন করতে পারো। নিজের সময় দিলে উদাহরণ হিসেবে ৪৫ সেকেন্ড বা ২ মিনিট লিখতে পারো।"));
        addDashboardCard(page, "৪. AI বিশ্লেষণ কীভাবে কাজ করে",
            "নমুনা ফ্রেম, Gemini ও ফলাফল বোঝা", () -> showTutorialDetail("AI বিশ্লেষণ কীভাবে কাজ করে",
            "অ্যাপ সম্পূর্ণ ভিডিও আপলোড করে না। ভিডিওর শুরু, মাঝামাঝি ও শেষসহ পাঁচটি নমুনা ফ্রেম তৈরি করে। এগুলো Worker-এর মাধ্যমে Gemini-তে যায়। AI দৃশ্যগুলো দেখে সম্ভাব্য মুহূর্তের পরামর্শ দেয়। নমুনার মাঝের অংশ সরাসরি দেখা হয় না, তাই ফলাফল যাচাই করে নেবে।"));
        addDashboardCard(page, "৫. History ব্যবহার",
            "পুরোনো বিশ্লেষণ দেখা ও মুছে ফেলা", () -> showTutorialDetail("History ব্যবহার",
            "হোম থেকে ‘সংরক্ষিত বিশ্লেষণ’ খুললে সাম্প্রতিক ২০টি ফলাফল পাওয়া যাবে। কোনো এন্ট্রিতে চাপলে বিস্তারিত দেখা যাবে। Clear History চাপলে নিশ্চিত করার পর সংরক্ষিত ফলাফল ফোন থেকে মুছে যাবে।"));
        addDashboardCard(page, "৬. Token ও নিরাপত্তা",
            "Backend token কী এবং কীভাবে নিরাপদে রাখবে", () -> showTutorialDetail("Token ও নিরাপত্তা",
            "APP_ACCESS_TOKEN হলো Worker-এ প্রবেশের গোপন অনুমতি। এটি কাউকে পাঠাবে না, স্ক্রিনশট বা GitHub কোডে প্রকাশ করবে না। অ্যাপে দিলে Android Keystore দিয়ে এনক্রিপ্ট করে ফোনে রাখা হয়। সন্দেহ হলে Cloudflare-এ token পরিবর্তন করো।"));
        addDashboardCard(page, "৭. আপডেট ও সমস্যা সমাধান",
            "নতুন APK, ইন্টারনেট ও সাধারণ সমস্যা", () -> showTutorialDetail("আপডেট ও সমস্যা সমাধান",
            "অ্যাপ চালুর সময় নতুন সংস্করণ পরীক্ষা করে। আপডেট থাকলে লাল পর্দায় নতুন APK ডাউনলোডের নির্দেশ দেখায়। APK ইনস্টল করতে Android-এর অনুমতি লাগবে। আপডেট যাচাইয়ের সার্ভার না চললে আবার চেষ্টা করতে পারো অথবা অফলাইনে অ্যাপ খুলতে পারো। বিশ্লেষণের জন্য ইন্টারনেট লাগবে।"));
        Button back = button("← হোমে ফিরে যাও");
        page.addView(back);
        back.setOnClickListener(v -> recreate());
        setContentView(scroll);
        animateChildren(page);
    }

    private void showTutorialDetail(String titleText, String bodyText) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(24), dp(20), dp(28));
        page.setBackgroundColor(Color.rgb(246,248,252));
        scroll.addView(page);
        TextView title = label(titleText, 23);
        title.setTextColor(Color.rgb(35,75,145));
        page.addView(title);
        TextView body = label(bodyText, 16);
        body.setPadding(0, dp(18), 0, dp(24));
        page.addView(body);
        Button back = button("← টিউটোরিয়ালে ফিরে যাও");
        page.addView(back);
        back.setOnClickListener(v -> showLearningCenter());
        setContentView(scroll);
        animateChildren(page);
    }

    private void showVideoGeneratorPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(24), dp(20), dp(28));
        page.setBackgroundColor(Color.rgb(246,248,252));
        scroll.addView(page);
        TextView title = label("ভিডিও জেনারেটর", 24);
        title.setTextColor(Color.rgb(35,75,145));
        page.addView(title);
        TextView status = label("এই ফিচারটি এখনো তৈরি হয়নি। ভুলভাবে কাজ করছে এমন বোতাম না দিয়ে এখানে এর শেখার ও পরিকল্পনার অংশ রাখা হয়েছে।", 15);
        status.setPadding(0, dp(12), 0, dp(18));
        page.addView(status);
        addDashboardCard(page, "✍️ Text-to-Video",
            "লেখা থেকে ভিডিও তৈরির ধারণা ও উদাহরণ", () -> showTutorialDetail("Text-to-Video",
            "Text-to-Video ব্যবস্থায় একটি বর্ণনা লিখলে AI সেই নির্দেশনা অনুযায়ী ভিডিও তৈরি করে। ভালো ফলের জন্য বিষয়, পরিবেশ, ক্যামেরার দৃশ্য, সময়কাল ও ভিজ্যুয়াল স্টাইল পরিষ্কারভাবে বর্ণনা করতে হয়। Rifat-Ai-তে এই জেনারেশন সুবিধা এখনো যুক্ত হয়নি।"));
        addDashboardCard(page, "🖼️ Image-to-Video",
            "ছবি থেকে অ্যানিমেটেড ভিডিও তৈরির ধারণা", () -> showTutorialDetail("Image-to-Video",
            "Image-to-Video-তে একটি স্থির ছবি থেকে নড়াচড়া-সহ ছোট ভিডিও তৈরি করা হয়। কোন অংশ নড়বে, ক্যামেরা কীভাবে চলবে এবং দৃশ্যের ধারাবাহিকতা কেমন হবে তা নির্ধারণ করতে হয়। এই ফিচারটি এখনো Rifat-Ai-তে চালু নয়।"));
        addDashboardCard(page, "🎬 ভবিষ্যৎ ফিচার",
            "ভিডিও জেনারেশন, প্রিভিউ ও এক্সপোর্ট", () -> showTutorialDetail("ভবিষ্যৎ ফিচার",
            "পরিকল্পিত ধাপ: বিনামূল্যের মডেল/সার্ভিস যাচাই, প্রম্পট লেখা, জেনারেট হওয়া ভিডিওর প্রিভিউ, ডাউনলোড ও এক্সপোর্ট। কোনো ফিচার যুক্ত হয়েছে বলে দেখানো হবে না যতক্ষণ না বাস্তবে তৈরি ও পরীক্ষা করা হয়।"));
        Button openOrganizer = button("AI ভিডিও অর্গানাইজার খুলুন");
        page.addView(openOrganizer);
        openOrganizer.setOnClickListener(v -> showVideoOrganizerPage());
        Button back = button("← হোমে ফিরে যাও");
        page.addView(back);
        back.setOnClickListener(v -> recreate());
        setContentView(scroll);
        animateChildren(page);
    }

    private void checkForUpdates(boolean showUpToDate) {
        if (updateCheckRunning) {
            if (showUpToDate) Toast.makeText(this, "আপডেট পরীক্ষা চলছে…", Toast.LENGTH_SHORT).show();
            return;
        }
        updateCheckRunning = true;
        if (showUpToDate) Toast.makeText(this, "নতুন আপডেট খোঁজা হচ্ছে…", Toast.LENGTH_SHORT).show();
        updateExecutor.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(15000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "Rifat-Ai-Android-Updater");
                try {
                    int status = c.getResponseCode();
                    if (status == 404) { runOnUiThread(() -> { updateCheckRunning = false; revealMainScreen(); }); return; }
                    if (status < 200 || status >= 300) throw new Exception("GitHub থেকে আপডেটের তথ্য পাওয়া যায়নি (HTTP " + status + ")");
                    JSONObject release = new JSONObject(readStream(c.getInputStream()));
                    String tag = release.optString("tag_name", "").trim();
                    String latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
                    String currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    if (latestVersion.isEmpty()) throw new Exception("রিলিজের ভার্সন নম্বর পাওয়া যায়নি।");
                    if (compareVersions(latestVersion, currentVersion) <= 0) {
                        runOnUiThread(() -> {
                            updateCheckRunning = false;
                            if (startupGate != null) revealMainScreen();
                            else if (showUpToDate) new AlertDialog.Builder(this)
                                .setTitle("আপডেট নেই")
                                .setMessage("তুমি সর্বশেষ ভার্সন (" + currentVersion + ") ব্যবহার করছ।")
                                .setPositiveButton("ঠিক আছে", null).show();
                        });
                        return;
                    }
                    JSONArray assets = release.optJSONArray("assets");
                    JSONObject apk = null;
                    if (assets != null) for (int i = 0; i < assets.length(); i++) {
                        JSONObject item = assets.getJSONObject(i);
                        if (APK_ASSET_NAME.equals(item.optString("name"))) { apk = item; break; }
                    }
                    if (apk == null) throw new Exception("নতুন ভার্সনের APK GitHub Release-এ পাওয়া যায়নি।");
                    String downloadUrl = apk.optString("browser_download_url", "");
                    Uri downloadUri = Uri.parse(downloadUrl);
                    if (!"https".equalsIgnoreCase(downloadUri.getScheme()) ||
                        !"github.com".equalsIgnoreCase(downloadUri.getHost())) {
                        throw new Exception("নিরাপত্তার কারণে অচেনা ডাউনলোড ঠিকানা গ্রহণ করা হয়নি।");
                    }
                    long size = apk.optLong("size", 0);
                    if (size > MAX_APK_BYTES) throw new Exception("APK ফাইলটি নির্ধারিত ১০০ MB সীমার চেয়ে বড়।");
                    String digest = apk.optString("digest", "");
                    String notes = release.optString("body", "").trim();
                    String finalNotes = notes.isEmpty() ? "নতুন ভার্সনে উন্নতি ও বাগ সংশোধন রয়েছে।" : notes;
                    runOnUiThread(() -> {
                        updateCheckRunning = false;
                        showForcedUpdateScreen(currentVersion, latestVersion, finalNotes, downloadUrl, digest, size);
                    });
                } finally { c.disconnect(); }
            } catch (Exception e) {
                String message = safeMessage(e);
                runOnUiThread(() -> {
                    updateCheckRunning = false;
                    if (startupGate != null) showStartupError(message);
                    else if (showUpToDate) new AlertDialog.Builder(this)
                        .setTitle("আপডেট পরীক্ষা করা যায়নি")
                        .setMessage(message)
                        .setPositiveButton("ঠিক আছে", null).show();
                });
            }
        });
    }

    private int compareVersions(String a, String b) {
        String[] aa = a.split("[^0-9]+");
        String[] bb = b.split("[^0-9]+");
        int count = Math.max(aa.length, bb.length);
        for (int i = 0; i < count; i++) {
            long av = i < aa.length && !aa[i].isEmpty() ? parseVersionPart(aa[i]) : 0;
            long bv = i < bb.length && !bb[i].isEmpty() ? parseVersionPart(bb[i]) : 0;
            if (av != bv) return Long.compare(av, bv);
        }
        return 0;
    }

    private long parseVersionPart(String value) {
        try { return Long.parseLong(value); } catch (Exception e) { return 0; }
    }

    private View buildCheckingScreen() {
        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(28), dp(28), dp(28));
        panel.setBackgroundColor(Color.rgb(22, 28, 42));
        TextView title = label("Rifat-Ai", 28);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);
        TextView status = label("আপডেট পরীক্ষা করা হচ্ছে…", 16);
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(16), 0, dp(10));
        panel.addView(status);
        panel.addView(new ProgressBar(this));
        return panel;
    }

    private void revealMainScreen() {
        if (startupGate == null) return;
        if (rootLayout != null) rootLayout.removeView(startupGate);
        else if (mainContent != null) setContentView(mainContent);
        startupGate = null;
        rootLayout = null;
    }

    private void showStartupError(String message) {
        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(28), dp(28), dp(28));
        panel.setBackgroundColor(Color.rgb(22, 28, 42));
        TextView title = label("আপডেট যাচাই করা যায়নি", 23);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);
        TextView detail = label(message + "\n\nইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করো।", 15);
        detail.setTextColor(Color.WHITE);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, dp(16), 0, dp(20));
        panel.addView(detail);
        Button retry = button("আবার চেষ্টা করো");
        panel.addView(retry);
        retry.setOnClickListener(v -> {
            panel.removeView(retry);
            panel.addView(new ProgressBar(this));
            startupGate = panel;
            checkForUpdates(false);
        });
        Button continueOffline = button("এখন অফলাইনে চালিয়ে যাও");
        panel.addView(continueOffline);
        continueOffline.setOnClickListener(v -> revealMainScreen());
        setContentView(panel);
        startupGate = panel;
        rootLayout = null;
    }

    private void showForcedUpdateScreen(String current, String latest, String notes, String url, String digest, long size) {
        String sizeText = size > 0 ? String.format(java.util.Locale.US, "%.1f MB", size / 1048576.0) : "আকার অজানা";
        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(26), dp(30), dp(26), dp(30));
        panel.setBackgroundColor(Color.rgb(190, 18, 38));

        TextView warning = label("⚠", 58);
        warning.setTextColor(Color.WHITE);
        warning.setGravity(Gravity.CENTER);
        panel.addView(warning);

        TextView title = label("অ্যাপের নতুন আপডেট প্রয়োজন", 25);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(12), 0, dp(14));
        panel.addView(title);

        TextView message = label("তোমার ব্যবহৃত Rifat-Ai APK পুরোনো হয়ে গেছে। অ্যাপটি ব্যবহার করতে নতুন APK ডাউনলোড ও ইনস্টল করো।", 17);
        message.setTextColor(Color.WHITE);
        message.setGravity(Gravity.CENTER);
        panel.addView(message);

        TextView versions = label("বর্তমান: " + current + "   •   নতুন: " + latest + "\nAPK: " + sizeText, 14);
        versions.setTextColor(Color.WHITE);
        versions.setGravity(Gravity.CENTER);
        versions.setPadding(0, dp(18), 0, dp(14));
        panel.addView(versions);

        if (!notes.trim().isEmpty()) {
            TextView details = label("আপডেটের বিবরণ: " + notes, 13);
            details.setTextColor(Color.WHITE);
            details.setGravity(Gravity.CENTER);
            details.setPadding(0, 0, 0, dp(16));
            panel.addView(details);
        }

        Button download = button("⬇  নতুন APK ডাউনলোড করুন");
        download.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        download.setAllCaps(false);
        download.setTextColor(Color.rgb(160, 12, 30));
        download.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        panel.addView(download, new LinearLayout.LayoutParams(-1, dp(58)));
        download.setOnClickListener(v -> downloadUpdate(url, digest, size, latest));

        TextView footer = label("নতুন APK ইনস্টল না করা পর্যন্ত মূল ফিচার বন্ধ থাকবে।", 13);
        footer.setTextColor(Color.WHITE);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(18), 0, 0);
        panel.addView(footer);

        startupGate = null;
        rootLayout = null;
        setContentView(panel);
    }

    private void downloadUpdate(String url, String expectedDigest, long expectedSize, String version) {
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        TextView status = label("আপডেট ডাউনলোড হচ্ছে…", 15);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(8));
        box.addView(status);
        box.addView(progress);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Rifat-Ai আপডেট")
            .setView(box)
            .setCancelable(false)
            .setNegativeButton("বন্ধ", (d, w) -> {})
            .create();
        dialog.show();
        updateExecutor.execute(() -> {
            File target = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", "Rifat-Ai-Android-Updater");
                try {
                    int code = c.getResponseCode();
                    if (code < 200 || code >= 300) throw new Exception("APK ডাউনলোড ব্যর্থ (HTTP " + code + ")");
                    long declared = c.getContentLengthLong();
                    if (declared > MAX_APK_BYTES) throw new Exception("APK ফাইলটি ১০০ MB-এর বেশি।");
                    File dir = new File(getCacheDir(), "updates");
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("আপডেট ফোল্ডার তৈরি করা যায়নি।");
                    target = new File(dir, "Rifat-Ai-" + version + ".apk");
                    MessageDigest sha = MessageDigest.getInstance("SHA-256");
                    long total = 0;
                    try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(target)) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = in.read(buffer)) != -1) {
                            total += n;
                            if (total > MAX_APK_BYTES) throw new Exception("APK ফাইলটি ১০০ MB সীমা ছাড়িয়েছে।");
                            out.write(buffer, 0, n);
                            sha.update(buffer, 0, n);
                            int percent = declared > 0 ? (int) Math.min(100, total * 100 / declared) : -1;
                            int shown = percent;
                            runOnUiThread(() -> {
                                if (!isFinishing()) {
                                    progress.setIndeterminate(shown < 0);
                                    if (shown >= 0) progress.setProgress(shown);
                                    status.setText(shown >= 0 ? "ডাউনলোড: " + shown + "%" : "ডাউনলোড হচ্ছে…");
                                }
                            });
                        }
                    }
                    if (expectedSize > 0 && total != expectedSize) throw new Exception("ডাউনলোড করা APK-এর আকার মেলেনি।");
                    if (expectedDigest.startsWith("sha256:")) {
                        String actual = toHex(sha.digest());
                        String expected = expectedDigest.substring(7).toLowerCase(java.util.Locale.US);
                        if (!actual.equals(expected)) throw new Exception("APK নিরাপত্তা যাচাই ব্যর্থ হয়েছে। ফাইলটি ইনস্টল করা হবে না।");
                    }
                    if (total < 1000) throw new Exception("ডাউনলোড করা APK ফাইলটি অসম্পূর্ণ মনে হচ্ছে।");
                    downloadedUpdate = target;
                    runOnUiThread(() -> {
                        if (dialog.isShowing()) dialog.dismiss();
                        new AlertDialog.Builder(this)
                            .setTitle("ডাউনলোড সম্পন্ন")
                            .setMessage("ভার্সন " + version + " ডাউনলোড হয়েছে। এখন Android-এর ইনস্টলেশন ধাপ খুলবে।")
                            .setNegativeButton("পরে", null)
                            .setPositiveButton("ইনস্টল", (d, w) -> installDownloadedUpdate())
                            .show();
                    });
                } finally { c.disconnect(); }
            } catch (Exception e) {
                if (target != null) target.delete();
                String message = safeMessage(e);
                runOnUiThread(() -> {
                    if (dialog.isShowing()) dialog.dismiss();
                    new AlertDialog.Builder(this).setTitle("আপডেট ডাউনলোড হয়নি")
                        .setMessage(message).setPositiveButton("ঠিক আছে", null).show();
                });
            }
        });
    }

    private String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private void installDownloadedUpdate() {
        if (downloadedUpdate == null || !downloadedUpdate.exists()) {
            Toast.makeText(this, "আগে আপডেট ডাউনলোড করো।", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            Toast.makeText(this, "Rifat-Ai-এর জন্য এই উৎস থেকে অ্যাপ ইনস্টল করার অনুমতি দাও, তারপর আবার Install চাপো।", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", downloadedUpdate);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        } catch (Exception e) {
            Toast.makeText(this, "ইনস্টল স্ক্রিন খোলা যায়নি: " + safeMessage(e), Toast.LENGTH_LONG).show();
        }
    }

    private void addCategory(LinearLayout page, String category) {
        Button b = button(category);
        page.addView(b);
        b.setOnClickListener(v -> {
            if (selectedVideo == null) categoryStatus.setText("আগে একটি ভিডিও নির্বাচন করো।");
            else {
                selectedCategory = category;
                categoryStatus.setText("নির্বাচিত ক্যাটাগরি: " + category);
            }
        });
    }

    private void showTokenDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Cloudflare APP_ACCESS_TOKEN");
        new AlertDialog.Builder(this)
            .setTitle("Backend access token")
            .setMessage("Cloudflare Worker-এ সেট করা APP_ACCESS_TOKEN এখানে লিখো। এটি অ্যাপের কোডে রাখা হবে না; ফোনে Android Keystore দিয়ে এনক্রিপ্ট করে সংরক্ষণ করা হবে।")
            .setView(input)
            .setNegativeButton("বাতিল", (d,w) -> {})
            .setPositiveButton("নিরাপদে সংরক্ষণ", (d,w) -> {
                String token = input.getText().toString().trim();
                if (token.isEmpty()) {
                    Toast.makeText(this,"Token খালি রাখা যাবে না।",Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    saveToken(token);
                    startAnalysis(token);
                } catch (Exception e) {
                    Toast.makeText(this,"Token নিরাপদে সংরক্ষণ করা যায়নি: " + safeMessage(e),Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        java.security.Key existing = ks.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());
        return generator.generateKey();
    }

    private void saveToken(String token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv,0,combined,0,iv.length);
        System.arraycopy(encrypted,0,combined,iv.length,encrypted.length);
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putString(TOKEN_KEY,Base64.encodeToString(combined,Base64.NO_WRAP)).apply();
    }

    private String readStoredToken() {
        try {
            String stored = getSharedPreferences(PREFS,MODE_PRIVATE).getString(TOKEN_KEY,null);
            if (stored == null) return null;
            byte[] combined = Base64.decode(stored,Base64.NO_WRAP);
            if (combined.length <= 12) return null;
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - 12];
            System.arraycopy(combined,0,iv,0,12);
            System.arraycopy(combined,12,encrypted,0,encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,getOrCreateSecretKey(),new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
        } catch (Exception e) { return null; }
    }

    private void startAnalysis(String token) {
        showProcessingScreen();
        executor.execute(() -> {
            try {
                String result = analyzeVideo(token);
                runOnUiThread(() -> showResult(result));
            } catch (Exception e) {
                String message = safeMessage(e);
                runOnUiThread(() -> showFailure(message));
            }
        });
    }

    private String analyzeVideo(String token) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        JSONArray parts = new JSONArray();
        try {
            retriever.setDataSource(this,selectedVideo);
            String durationText = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = durationText == null ? 0 : Long.parseLong(durationText);
            if (durationMs <= 0) throw new Exception("ভিডিওর দৈর্ঘ্য পড়া যায়নি।");
            JSONObject prompt = new JSONObject();
            prompt.put("text",
                "Analyze these sampled frames from one video in chronological order. " +
                "Video total duration: " + (durationMs / 1000.0) + " seconds. " +
                "Requested category: " + selectedCategory + ". Requested clip duration: " + selectedDuration + ". " +
                "Analyze only the supplied frames, which are sampled across the full video. " +
                "Return concise Bengali guidance with approximate timestamps in seconds, a short reason for each moment, " +
                "and a suggested clip start/end time. Keep the suggested duration close to the requested duration when possible. " +
                "Never invent visual details between frames. Clearly state that timestamps are estimates based on sparse samples. " +
                "If the frames do not provide enough evidence, say so.");
            parts.put(prompt);
            for (int i=0;i<FRAME_COUNT;i++) {
                long timeMs = FRAME_COUNT == 1 ? 0 : (durationMs - 1) * i / (FRAME_COUNT - 1);
                Bitmap frame = retriever.getFrameAtTime(timeMs * 1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame == null) continue;
                Bitmap scaled = scaleBitmap(frame,384);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG,55,output);
                frame.recycle();
                if (scaled != frame) scaled.recycle();
                JSONObject inline = new JSONObject();
                inline.put("mime_type","image/jpeg");
                inline.put("data",Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP));
                JSONObject imagePart = new JSONObject();
                imagePart.put("inline_data",inline);
                parts.put(imagePart);
            }
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
        if (parts.length() <= 1) throw new Exception("ভিডিও থেকে নমুনা ফ্রেম নেওয়া যায়নি।");
        JSONObject content = new JSONObject();
        content.put("parts",parts);
        JSONArray contents = new JSONArray();
        contents.put(content);
        JSONObject requestBody = new JSONObject();
        requestBody.put("contents",contents);
        requestBody.put("generationConfig",new JSONObject().put("temperature",0.2));
        byte[] body = requestBody.toString().getBytes(StandardCharsets.UTF_8);
        if (body.length > MAX_REQUEST_BYTES) throw new Exception("নমুনা ফ্রেমের ডেটা ১ MB সীমা ছাড়িয়েছে। অন্য ছোট ভিডিও দিয়ে চেষ্টা করো।");

        HttpURLConnection connection = (HttpURLConnection)new URL(API_URL).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(90000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setRequestProperty("Authorization","Bearer " + token);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String response = readStream(stream);
            if (status == 401) throw new Exception("Backend token সঠিক নয়। অ্যাপের ডেটা মুছে নতুন token দিতে হবে।");
            if (status == 429) throw new Exception("Gemini quota শেষ বা rate limit হয়েছে। পরে আবার চেষ্টা করো।");
            if (status < 200 || status >= 300) {
                try {
                    JSONObject err = new JSONObject(response);
                    throw new Exception(err.optString("error","Backend error") + " (HTTP " + status + ")");
                } catch (org.json.JSONException ignored) {
                    throw new Exception("Backend error (HTTP " + status + ")");
                }
            }
            JSONObject parsed = new JSONObject(response);
            JSONArray candidates = parsed.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) throw new Exception("Gemini কোনো বিশ্লেষণের ফল দেয়নি।");
            JSONObject candidate = candidates.getJSONObject(0);
            JSONObject responseContent = candidate.optJSONObject("content");
            JSONArray responseParts = responseContent == null ? null : responseContent.optJSONArray("parts");
            StringBuilder answer = new StringBuilder();
            if (responseParts != null) {
                for (int i=0;i<responseParts.length();i++) {
                    String text = responseParts.getJSONObject(i).optString("text","");
                    if (!text.isEmpty()) answer.append(text).append("\n");
                }
            }
            if (answer.length() == 0) throw new Exception("Gemini-এর উত্তর পড়া যায়নি।");
            return answer.toString().trim();
        } finally {
            connection.disconnect();
        }
    }

    private Bitmap scaleBitmap(Bitmap source, int maxWidth) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int longest = Math.max(sourceWidth, sourceHeight);
        if (longest <= maxWidth) return source;
        float ratio = (float) maxWidth / (float) longest;
        int width = Math.max(1, Math.round(sourceWidth * ratio));
        int height = Math.max(1, Math.round(sourceHeight * ratio));
        return Bitmap.createScaledBitmap(source, width, height, true);
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (InputStream in=stream; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count=in.read(buffer)) != -1) out.write(buffer,0,count);
            return out.toString("UTF-8");
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? "অজানা সমস্যা হয়েছে।" : message;
    }

    private void showProcessingScreen() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22),dp(30),dp(22),dp(24));
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setBackgroundColor(Color.rgb(246,248,252));
        TextView title = label("AI ভিডিও বিশ্লেষণ",24);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);
        TextView info = label("ভিডিও থেকে ৫টি নমুনা ফ্রেম নিয়ে Gemini-তে পাঠানো হচ্ছে। সম্পূর্ণ ভিডিও আপলোড হচ্ছে না।",15);
        info.setPadding(0,dp(18),0,dp(18));
        panel.addView(info);
        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        panel.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));
        TextView status = label("বিশ্লেষণ চলছে…",16);
        status.setPadding(0,dp(18),0,0);
        panel.addView(status);
        setContentView(panel);
        animateChildren(panel);
    }

    private void saveHistory(String result) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("rifat_ai_history", MODE_PRIVATE);
            JSONArray history = new JSONArray(prefs.getString("items", "[]"));
            JSONObject item = new JSONObject();
            item.put("result", result);
            item.put("category", selectedCategory);
            item.put("duration", selectedDuration);
            item.put("video", selectedName == null ? "ভিডিও" : selectedName.getText().toString());
            item.put("time", System.currentTimeMillis());
            JSONArray updated = new JSONArray();
            updated.put(item);
            for (int i = 0; i < history.length() && i < 19; i++) updated.put(history.getJSONObject(i));
            prefs.edit().putString("items", updated.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void showHistory() {
        JSONArray history;
        try {
            history = new JSONArray(getSharedPreferences("rifat_ai_history", MODE_PRIVATE).getString("items", "[]"));
        } catch (Exception e) { history = new JSONArray(); }
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(22), dp(18), dp(24));
        panel.setBackgroundColor(Color.rgb(246,248,252));
        TextView title = label("সংরক্ষিত বিশ্লেষণ", 23);
        title.setTextColor(Color.rgb(35,75,145));
        panel.addView(title);
        if (history.length() == 0) {
            panel.addView(label("এখনো কোনো বিশ্লেষণ সংরক্ষিত হয়নি।", 15));
        } else {
            for (int i = 0; i < history.length(); i++) {
                JSONObject item = history.optJSONObject(i);
                if (item == null) continue;
                String date = new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                    .format(new java.util.Date(item.optLong("time")));
                Button entry = button(item.optString("category", "ভিডিও") + " • " + date);
                panel.addView(entry);
                final String result = item.optString("result", "");
                final String meta = item.optString("video", "ভিডিও") + "\\n" +
                    "ক্যাটাগরি: " + item.optString("category", "") + " • দৈর্ঘ্য: " + item.optString("duration", "");
                entry.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("আগের বিশ্লেষণ")
                    .setMessage(meta + "\\n\\n" + result)
                    .setPositiveButton("বন্ধ", null).show());
            }
            Button clear = button("History মুছে ফেলো");
            panel.addView(clear);
            clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("History মুছবে?")
                .setMessage("সংরক্ষিত বিশ্লেষণগুলো ফোন থেকে মুছে যাবে।")
                .setNegativeButton("বাতিল", null)
                .setPositiveButton("মুছে ফেলো", (d,w) -> {
                    getSharedPreferences("rifat_ai_history", MODE_PRIVATE).edit().clear().apply();
                    showHistory();
                }).show());
        }
        Button back = button("← ফিরে যাও");
        panel.addView(back);
        back.setOnClickListener(v -> recreate());
        setContentView(panel);
        animateChildren(panel);
    }

    private void showResult(String result) {
        saveHistory(result);
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20),dp(24),dp(20),dp(24));
        panel.setBackgroundColor(Color.rgb(246,248,252));
        scroll.addView(panel);
        TextView resultTitle = label("Gemini বিশ্লেষণের ফলাফল",23);
        resultTitle.setTextColor(Color.rgb(35,75,145));
        panel.addView(resultTitle);
        TextView resultMeta = label("ক্যাটাগরি: " + selectedCategory + "  •  লক্ষ্য দৈর্ঘ্য: " + selectedDuration,13);
        resultMeta.setPadding(0,dp(8),0,0);
        panel.addView(resultMeta);
        TextView answer = label(result,16);
        answer.setPadding(0,dp(18),0,dp(22));
        panel.addView(answer);
        panel.addView(label("এটি নমুনা ফ্রেম-ভিত্তিক আনুমানিক বিশ্লেষণ। এখনো স্বয়ংক্রিয় ক্লিপ কাটা বা সেভ করা হয়নি।",13));
        Button back = button("← ফিরে যাও");
        panel.addView(back);
        back.setOnClickListener(v -> recreate());
        setContentView(scroll);
        animateChildren(panel);
    }

    private void showFailure(String message) {
        new AlertDialog.Builder(this)
            .setTitle("বিশ্লেষণ সম্পন্ন হয়নি")
            .setMessage(message)
            .setPositiveButton("ঠিক আছে",(d,w) -> recreate())
            .show();
    }

    private void openVideoPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        startActivityForResult(i,101);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == 302 && resultCode == RESULT_OK && data != null && data.getData() != null) {\n            videoToVideoUri = data.getData();\n            try { getContentResolver().takePersistableUriPermission(videoToVideoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}\n            if (videoToVideoSelectedText != null) videoToVideoSelectedText.setText("ভিডিও নির্বাচিত হয়েছে: " + videoToVideoUri.getLastPathSegment());\n            if (videoToVideoStatus != null) videoToVideoStatus.setText("ভিডিও প্রস্তুত। স্টাইল বেছে Generate চাপো।");\n        }\n        if (requestCode == 101 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedVideo = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(selectedVideo,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            selectedName.setText("ভিডিও নির্বাচিত হয়েছে: " + selectedVideo.getLastPathSegment());
            videoView.setVideoURI(selectedVideo);
            videoView.start();
            selectedCategory = "";
            categoryStatus.setText("ভিডিও প্রস্তুত। এখন ক্যাটাগরি নির্বাচন করো।");
        }
    }

    @Override protected void onStop() {
        if (videoView != null) videoView.pause();
        super.onStop();
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        updateExecutor.shutdownNow();
        super.onDestroy();
    }
}
