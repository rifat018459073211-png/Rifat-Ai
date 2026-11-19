package com.rifat.aivideoorganizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
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
        mainContent = scroll;
        rootLayout = new FrameLayout(this);
        rootLayout.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        startupGate = buildCheckingScreen();
        rootLayout.addView(startupGate, new FrameLayout.LayoutParams(-1, -1));
        setContentView(rootLayout);
        animateChildren(page);
        checkForUpdates(false);
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
        if (requestCode == 101 && resultCode == RESULT_OK && data != null && data.getData() != null) {
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
