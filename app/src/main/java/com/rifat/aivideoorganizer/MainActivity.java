package com.rifat.aivideoorganizer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import android.graphics.Color;
import android.util.TypedValue;

public class MainActivity extends Activity {
    private VideoView videoView;
    private TextView selectedName, categoryStatus;
    private Uri selectedVideo;
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private TextView label(String s, int size) { TextView t=new TextView(this); t.setText(s); t.setTextSize(TypedValue.COMPLEX_UNIT_SP,size); t.setTextColor(Color.rgb(30,35,45)); return t; }
    private Button button(String s) { Button b=new Button(this); b.setText(s); return b; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(18),dp(22),dp(18),dp(28)); page.setBackgroundColor(Color.rgb(246,248,252)); scroll.addView(page);
        TextView title=label("AI Video Organizer",25); title.setGravity(Gravity.CENTER); title.setTextColor(Color.rgb(35,75,145)); page.addView(title);
        TextView intro=label("ভিডিও বেছে নাও, দেখে নাও, তারপর ক্যাটাগরি নির্বাচন করো।",15); intro.setPadding(0,dp(10),0,dp(14)); page.addView(intro);
        Button choose=button("＋ ভিডিও নির্বাচন করো"); page.addView(choose); choose.setOnClickListener(v->openVideoPicker());
        selectedName=label("এখনো কোনো ভিডিও নেওয়া হয়নি",14); selectedName.setPadding(0,dp(8),0,dp(8)); page.addView(selectedName);
        videoView=new VideoView(this); videoView.setBackgroundColor(Color.BLACK); page.addView(videoView,new LinearLayout.LayoutParams(-1,dp(220)));
        MediaController controls=new MediaController(this); controls.setAnchorView(videoView); videoView.setMediaController(controls);
        TextView heading=label("ক্যাটাগরি",19); heading.setPadding(0,dp(20),0,dp(8)); page.addView(heading);
        categoryStatus=label("ভিডিও নির্বাচন করে একটি ক্যাটাগরি বেছে নাও।",14); page.addView(categoryStatus);
        addCategory(page,"😂 Funny Clips"); addCategory(page,"⚔ Action Clips"); addCategory(page,"🎬 Animation Clips"); addCategory(page,"⭐ Best Moments");
        TextView note=label("প্রাথমিক সংস্করণ: ভিডিও নির্বাচন, প্রিভিউ ও ক্যাটাগরি লেবেল। AI দিয়ে মুহূর্ত শনাক্ত করা, স্বয়ংক্রিয় কাটিং এবং লিংক থেকে ভিডিও আনা পরের ধাপে যোগ হবে।",13); note.setPadding(0,dp(22),0,0); page.addView(note);
        setContentView(scroll);
    }
    private void addCategory(LinearLayout page,String category) { Button b=button(category); page.addView(b); b.setOnClickListener(v->{ if(selectedVideo==null) categoryStatus.setText("আগে একটি ভিডিও নির্বাচন করো।"); else categoryStatus.setText("নির্বাচিত ক্যাটাগরি: "+category+"\nএখন এটি শুধু নির্বাচন দেখায়; ক্লিপ কাটা পরের ধাপে যোগ হবে।"); }); }
    private void openVideoPicker() { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("video/*"); startActivityForResult(i,101); }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) { super.onActivityResult(requestCode,resultCode,data); if(requestCode==101 && resultCode==RESULT_OK && data!=null && data.getData()!=null) { selectedVideo=data.getData(); try { getContentResolver().takePersistableUriPermission(selectedVideo,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored) {} selectedName.setText("ভিডিও নির্বাচিত হয়েছে: "+selectedVideo.getLastPathSegment()); videoView.setVideoURI(selectedVideo); videoView.start(); categoryStatus.setText("ভিডিও প্রস্তুত। এখন ক্যাটাগরি নির্বাচন করো।"); } }
    @Override protected void onStop() { if(videoView!=null) videoView.pause(); super.onStop(); }
}
