package com.rifat.aivideoorganizer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.UUID;

public class TextToVoiceActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int SAVE_AUDIO = 901;
    private TextToSpeech tts;
    private EditText input;
    private TextView status;
    private Button speakButton, generateButton, saveButton;
    private File audioFile;
    private boolean ready = false;
    private boolean bengaliAvailable = false;
    private Locale bengaliLocale = new Locale("bn", "BD");

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }

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
        page.setPadding(dp(18), dp(22), dp(18), dp(28));
        page.setBackgroundColor(Color.rgb(246, 248, 252));
        scroll.addView(page);

        page.addView(text("Text to Voice • লেখা থেকে কণ্ঠ", 23));
        TextView note = text("লেখা লিখে শুনতে পারো এবং সমর্থিত হলে MP3 নয়, WAV/অডিও ফাইল হিসেবে সেভ করতে পারো। এটি ফোনে থাকা Android Text-to-Speech engine ব্যবহার করে; কোনো API key লাগে না।", 14);
        note.setPadding(0, dp(10), 0, dp(14));
        page.addView(note);

        input = new EditText(this);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setMinLines(5);
        input.setHint("এখানে বাংলা বা অন্য ভাষার লেখা লিখো…");
        input.setTextSize(16);
        page.addView(input, new LinearLayout.LayoutParams(-1, dp(170)));

        speakButton = button("লেখা পড়ে শোনাও");
        page.addView(speakButton);
        speakButton.setOnClickListener(v -> speakText());

        generateButton = button("অডিও ফাইল তৈরি করো");
        generateButton.setEnabled(false);
        page.addView(generateButton);
        generateButton.setOnClickListener(v -> generateAudioFile());

        Button install = button("ভাষার ভয়েস ডেটা ইনস্টল");
        page.addView(install);
        install.setOnClickListener(v -> {
            try { startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)); }
            catch (Exception e) { status.setText("এই ফোনে TTS data installer পাওয়া যায়নি। Settings থেকে Text-to-Speech engine পরীক্ষা করো।"); }
        });

        saveButton = button("অডিও ফাইল সেভ");
        saveButton.setEnabled(false);
        page.addView(saveButton);
        saveButton.setOnClickListener(v -> saveAudio());

        status = text("ফোনের Text-to-Speech engine প্রস্তুত হচ্ছে…", 13);
        status.setPadding(0, dp(14), 0, dp(12));
        page.addView(status);

        BottomBackLayout.install(this, scroll, () -> finish());
        tts = new TextToSpeech(this, this);
    }

    @Override public void onInit(int result) {
        if (result != TextToSpeech.SUCCESS) {
            status.setText("Android Text-to-Speech engine চালু হয়নি। ফোনের Settings-এ Text-to-Speech output পরীক্ষা করো।");
            return;
        }
        int language = tts.setLanguage(new Locale("bn", "BD"));
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            bengaliLocale = new Locale("bn", "IN");
            int fallback = tts.setLanguage(bengaliLocale);
            bengaliAvailable = fallback != TextToSpeech.LANG_MISSING_DATA && fallback != TextToSpeech.LANG_NOT_SUPPORTED;
        } else {
            bengaliAvailable = true;
        }
        ready = true;
        tts.setSpeechRate(1.0f);
        speakButton.setEnabled(true);
        generateButton.setEnabled(true);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {
                runOnUiThread(() -> status.setText("কণ্ঠ তৈরি হচ্ছে…"));
            }
            @Override public void onDone(String id) {
                runOnUiThread(() -> {
                    if (id.startsWith("file-")) {
                        status.setText("অডিও ফাইল তৈরি হয়েছে। এখন সেভ করতে পারো।");
                        saveButton.setEnabled(audioFile != null && audioFile.isFile());
                    } else {
                        status.setText("লেখা পড়া শেষ হয়েছে।");
                    }
                    speakButton.setEnabled(true);
                    generateButton.setEnabled(true);
                });
            }
            @Override public void onError(String id) {
                runOnUiThread(() -> {
                    status.setText("কণ্ঠ তৈরি করা যায়নি। অন্য Text-to-Speech voice নির্বাচন করে চেষ্টা করো।");
                    speakButton.setEnabled(true);
                    generateButton.setEnabled(true);
                });
            }
        });
        if (bengaliAvailable) status.setText("Bengali voice পাওয়া গেছে। লেখা লিখে পড়ে শোনাও চাপো।");
        else status.setText("এই ফোনের বর্তমান TTS engine-এ বাংলা voice পাওয়া যায়নি। ভাষার ভয়েস ডেটা ইনস্টল করো বা Settings থেকে Bengali voice যোগ করো।");
    }

    private void speakText() {
        if (!ready || tts == null) {
            status.setText("Text-to-Speech engine এখনো প্রস্তুত নয়।");
            return;
        }
        String value = input.getText().toString().trim();
        if (value.isEmpty()) {
            status.setText("আগে কিছু লেখা লিখো।");
            return;
        }
        if (!selectVoiceFor(value)) return;
        speakButton.setEnabled(false);
        Bundle params = new Bundle();
        String id = "speech-" + UUID.randomUUID();
        int result = tts.speak(value, TextToSpeech.QUEUE_FLUSH, params, id);
        if (result != TextToSpeech.SUCCESS) {
            speakButton.setEnabled(true);
            status.setText("লেখা পড়ে শোনানো শুরু করা যায়নি।");
        }
    }

    private boolean selectVoiceFor(String value) {
        Locale locale = containsBengali(value) ? bengaliLocale : Locale.US;
        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (containsBengali(value)) {
                status.setText("এই ফোনে বাংলা voice নেই। ভাষার ভয়েস ডেটা ইনস্টল করো।");
                return false;
            }
            status.setText("এই ফোনে English voice নেই। Settings থেকে voice data যোগ করো।");
            return false;
        }
        return true;
    }

    private void generateAudioFile() {
        if (!ready || tts == null) {
            status.setText("Text-to-Speech engine এখনো প্রস্তুত নয়।");
            return;
        }
        String value = input.getText().toString().trim();
        if (value.isEmpty()) {
            status.setText("আগে কিছু লেখা লিখো।");
            return;
        }
        if (!selectVoiceFor(value)) return;
        audioFile = new File(getCacheDir(), "rifat-voice-" + System.currentTimeMillis() + ".wav");
        saveButton.setEnabled(false);
        generateButton.setEnabled(false);
        Bundle params = new Bundle();
        String id = "file-" + UUID.randomUUID();
        int result = tts.synthesizeToFile(value, params, audioFile, id);
        if (result != TextToSpeech.SUCCESS) {
            generateButton.setEnabled(true);
            status.setText("অডিও ফাইল তৈরি শুরু করা যায়নি।");
        } else {
            status.setText("অডিও ফাইল তৈরি হচ্ছে…");
        }
    }

    private boolean containsBengali(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x0980 && c <= 0x09FF) return true;
        }
        return false;
    }

    private void saveAudio() {
        if (audioFile == null || !audioFile.isFile()) {
            status.setText("আগে অডিও তৈরি করো।");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/wav");
        intent.putExtra(Intent.EXTRA_TITLE, "Rifat-Ai-voice.wav");
        startActivityForResult(intent, SAVE_AUDIO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVE_AUDIO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri destination = data.getData();
        new Thread(() -> {
            try (InputStream in = new FileInputStream(audioFile);
                 OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                if (out == null) throw new Exception("ফাইল লেখা যায়নি");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                out.flush();
                runOnUiThread(() -> status.setText("অডিও ফাইল সেভ হয়েছে।"));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("অডিও সেভ হয়নি: " + e.getMessage()));
            }
        }).start();
    }

    @Override protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
