package com.rifat.aivideoorganizer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.json.*;

public class TextToImageActivity extends Activity {
    private static final String API = "https://aihorde.net/api/v2";
    private final Handler main = new Handler(Looper.getMainLooper());
    private EditText prompt;
    private TextView status;
    private Button generate;
    private ImageView preview;
    private ProgressBar progress;
    private volatile boolean running = false;

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(Color.rgb(35,43,58));
        return t;
    }
    private Button button(String value) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); return b;
    }
    private void update(String value) { main.post(() -> status.setText(value)); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(16),dp(18),dp(28));
        page.setBackgroundColor(Color.rgb(246,248,252));
        scroll.addView(page);

        Button back = button("←");
        back.setTextSize(22); back.setGravity(Gravity.CENTER);
        page.addView(back,new LinearLayout.LayoutParams(dp(48),dp(46)));
        back.setOnClickListener(v -> finish());

        TextView heading = label("Text to Image",24);
        heading.setTextColor(Color.rgb(28,66,124));
        heading.setPadding(0,dp(14),0,dp(6)); page.addView(heading);
        TextView hint = label("তোমার বর্ণনা লিখে AI ছবি তৈরি করো। Volunteer-powered AI Horde ব্যবহার হয়; ফ্রি হলেও anonymous queue-তে অপেক্ষা বেশি হতে পারে।",14);
        page.addView(hint);
        prompt = new EditText(this);
        prompt.setHint("যেমন: পাহাড়ের পাশে শান্ত একটি নদী, cinematic landscape");
        prompt.setMinLines(3); prompt.setGravity(Gravity.TOP|Gravity.START);
        prompt.setTextSize(15);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1,-2);
        pp.topMargin=dp(16); page.addView(prompt,pp);

        generate = button("✦  GENERATE IMAGE");
        generate.setTextColor(Color.WHITE);
        android.graphics.drawable.GradientDrawable green = new android.graphics.drawable.GradientDrawable();
        green.setColor(Color.rgb(25,155,91)); green.setCornerRadius(dp(14));
        generate.setBackground(green);
        generate.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_gallery, 0, 0, 0);
        generate.setCompoundDrawablePadding(dp(10));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1,dp(54));
        gp.topMargin=dp(12); page.addView(generate,gp);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true); progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pr = new LinearLayout.LayoutParams(-1,dp(6));
        pr.topMargin=dp(12); page.addView(progress,pr);
        status = label("Prompt লিখে Generate চাপো।",13);
        status.setPadding(0,dp(10),0,dp(8)); page.addView(status);
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1,-2);
        ip.topMargin=dp(8); page.addView(preview,ip);
        generate.setOnClickListener(v -> startGeneration());
        BottomBackLayout.install(this, scroll, () -> recreate());
    }

    private void startGeneration() {
        String text = prompt.getText().toString().trim();
        if(text.length()<4) { prompt.setError("আরও বিস্তারিত prompt লিখো"); return; }
        if(running) return;
        running=true; generate.setEnabled(false); progress.setVisibility(View.VISIBLE);
        preview.setImageDrawable(null);
        new Thread(() -> {
            try {
                String model = findAvailableModel();
                update("AI worker খোঁজা হয়েছে। ছবি তৈরির অনুরোধ পাঠানো হচ্ছে…");
                JSONObject params = new JSONObject();
                params.put("cfg_scale",7.0); params.put("steps",20);
                params.put("width",512); params.put("height",512);
                params.put("n",1); params.put("sampler_name","k_euler");
                JSONObject body = new JSONObject();
                body.put("prompt",text);
                body.put("params",params);
                body.put("nsfw",false);
                body.put("censor_nsfw",true);
                body.put("trusted_workers",true);
                body.put("models",new JSONArray().put(model));
                body.put("r2",true);
                body.put("shared",false);
                JSONObject queued = requestJson("POST",API+"/generate/async",body);
                String id = queued.optString("id","");
                if(id.isEmpty()) throw new IOException("AI Horde job ID দেয়নি।");
                String imageUrl = null;
                for(int attempt=0;attempt<180;attempt++) {
                    Thread.sleep(4000);
                    JSONObject check = requestJson("GET",API+"/generate/check/"+id,null);
                    int finished = check.optInt("finished",0);
                    int waiting = check.optInt("waiting",0);
                    int queue = check.optInt("queue_position",-1);
                    update("ছবি তৈরি হচ্ছে… সম্পন্ন "+finished+" • অপেক্ষায় "+waiting+
                        (queue>=0 ? " • Queue #"+queue : "")+"\nVolunteer worker-এর ওপর নির্ভর করে সময় লাগতে পারে।");
                    if(finished>0) {
                        JSONObject result = requestJson("GET",API+"/generate/status/"+id,null);
                        JSONArray generations = result.optJSONArray("generations");
                        if(generations!=null && generations.length()>0)
                            imageUrl=generations.getJSONObject(0).optString("img",null);
                        break;
                    }
                    if(check.optBoolean("faulted",false)) throw new IOException("AI worker কাজটি সম্পন্ন করতে পারেনি।");
                }
                if(imageUrl==null || imageUrl.isEmpty()) throw new IOException("অনেকক্ষণ অপেক্ষার পরও ছবি পাওয়া যায়নি। পরে আবার চেষ্টা করো।");
                update("তৈরি হওয়া ছবি ডাউনলোড হচ্ছে…");
                byte[] bytes = download(imageUrl);
                if(bytes.length<1000) throw new IOException("AI থেকে পাওয়া ছবির ফাইলটি অসম্পূর্ণ।");
                android.graphics.Bitmap bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                if(bitmap==null) throw new IOException("AI থেকে পাওয়া ছবিটি খোলা যাচ্ছে না।");
                ByteArrayOutputStream normalized = new ByteArrayOutputStream();
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,normalized);
                saveImage(normalized.toByteArray());
                main.post(() -> {
                    preview.setImageBitmap(bitmap);
                    status.setText("ছবি তৈরি ও Gallery/Pictures/Rifat-Ai-তে সেভ হয়েছে।");
                    Toast.makeText(this,"AI ছবি সেভ হয়েছে",Toast.LENGTH_LONG).show();
                });
            } catch(Exception e) {
                update("ছবি তৈরি হয়নি: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
            } finally {
                running=false;
                main.post(() -> { generate.setEnabled(true); progress.setVisibility(View.GONE); });
            }
        },"RifatAi-TextToImage").start();
    }

    private String findAvailableModel() throws Exception {
        JSONArray models = requestArray("GET",API+"/status/models");
        String fallback = null;
        for(int i=0;i<models.length();i++) {
            JSONObject m=models.getJSONObject(i);
            String name=m.optString("name","");
            if(name.isEmpty()) continue;
            if(fallback==null) fallback=name;
            if(m.optInt("count",0)>0) return name;
        }
        if(fallback!=null) return fallback;
        throw new IOException("এখন কোনো AI image worker পাওয়া যাচ্ছে না। পরে চেষ্টা করো।");
    }

    private JSONObject requestJson(String method,String address,JSONObject payload) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(20000); c.setReadTimeout(30000);
        c.setRequestProperty("apikey","0000000000");
        c.setRequestProperty("Client-Agent","Rifat-Ai:0.4:android-app");
        c.setRequestProperty("Accept","application/json");
        if(payload!=null) {
            c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json");
            try(OutputStream out=c.getOutputStream()) { out.write(payload.toString().getBytes(StandardCharsets.UTF_8)); }
        }
        int code=c.getResponseCode();
        InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
        String response=readText(in);
        c.disconnect();
        if(code<200||code>=300) throw new IOException("AI Horde HTTP "+code+": "+response.substring(0,Math.min(240,response.length())));
        return new JSONObject(response);
    }

    private JSONArray requestArray(String method,String address) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(20000); c.setReadTimeout(30000);
        c.setRequestProperty("apikey","0000000000");
        c.setRequestProperty("Client-Agent","Rifat-Ai:0.4:android-app");
        int code=c.getResponseCode();
        InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
        String response=readText(in); c.disconnect();
        if(code<200||code>=300) throw new IOException("AI model list HTTP "+code);
        return new JSONArray(response);
    }

    private String readText(InputStream in) throws Exception {
        if(in==null) return "";
        try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {
            StringBuilder b=new StringBuilder(); String line;
            while((line=r.readLine())!=null) b.append(line);
            return b.toString();
        }
    }

    private byte[] download(String address) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(20000); c.setReadTimeout(90000);
        int code=c.getResponseCode();
        if(code<200||code>=300) throw new IOException("ছবি ডাউনলোড HTTP "+code);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=c.getInputStream()) {
            byte[] buffer=new byte[16384]; int n; int total=0;
            while((n=in.read(buffer))!=-1) {
                total+=n; if(total>15*1024*1024) throw new IOException("ছবির ফাইল ১৫ MB-এর বেশি।");
                out.write(buffer,0,n);
            }
        } finally { c.disconnect(); }
        return out.toByteArray();
    }

    private Uri saveImage(byte[] bytes) throws Exception {
        ContentValues values=new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,"Rifat-Ai-"+System.currentTimeMillis()+".png");
        values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Rifat-Ai");
        Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        if(uri==null) throw new IOException("Gallery-তে ফাইল তৈরি করা যায়নি।");
        try(OutputStream out=getContentResolver().openOutputStream(uri)) {
            if(out==null) throw new IOException("Gallery output stream পাওয়া যায়নি।");
            out.write(bytes);
        }
        return uri;
    }
}
