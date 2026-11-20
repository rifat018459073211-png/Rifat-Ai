package com.rifat.aivideoorganizer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.media.MediaMetadataRetriever;
import androidx.media3.common.MediaItem;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.InputStream;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * On-device heuristic highlight finder. It ranks visually changing/high-contrast
 * moments; it is not a semantic or generative AI model.
 */
public class BestMomentsActivity extends Activity {
    private static final int PICK_VIDEO = 710;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private LinearLayout results;
    private Uri videoUri;
    private File pendingExport;
    private Button pendingAction;
    private static final int SAVE_CLIP = 711;

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size);
        t.setTextColor(Color.rgb(35,43,58)); return t;
    }
    private Button button(String value) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); return b;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(20),dp(18),dp(28));
        page.setBackgroundColor(Color.rgb(246,248,252));
        scroll.addView(page);
        Button back = button("← ফিরে যাও");
        page.addView(back); back.setOnClickListener(v -> finish());
        page.addView(text("ভিডিওর সেরা মুহূর্ত",23));
        TextView info = text("ভিডিওর দৃশ্য পরিবর্তন ও ফ্রেমের বৈচিত্র্য দেখে সম্ভাব্য হাইলাইট খুঁজবে। বিশ্লেষণ ফোনেই হবে।",14);
        info.setPadding(0,dp(8),0,dp(14)); page.addView(info);
        Button choose = button("ভিডিও নির্বাচন করো");
        page.addView(choose); choose.setOnClickListener(v -> pickVideo());
        status = text("এখনো কোনো ভিডিও নির্বাচন করা হয়নি।",13);
        status.setPadding(0,dp(12),0,dp(12)); page.addView(status);
        results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); page.addView(results);
        BottomBackLayout.install(this, scroll, () -> recreate());
    }

    private void pickVideo() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("video/*");
        startActivityForResult(i,PICK_VIDEO);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == SAVE_CLIP) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                if (pendingExport != null) pendingExport.delete();
                if (pendingAction != null) pendingAction.setEnabled(true);
                status.setText("ক্লিপ সেভ বাতিল হয়েছে।");
                return;
            }
            Uri destination = data.getData();
            worker.execute(() -> {
                try (InputStream in = new FileInputStream(pendingExport);
                     OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                    if (out == null) throw new Exception("নির্বাচিত জায়গায় লেখা যায়নি");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    out.flush();
                    if (pendingExport != null) pendingExport.delete();
                    runOnUiThread(() -> {
                        if (pendingAction != null) pendingAction.setEnabled(true);
                        status.setText("সফল! Best Moment MP4 ক্লিপ সেভ হয়েছে।");
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (pendingAction != null) pendingAction.setEnabled(true);
                        status.setText("ক্লিপ সেভ হয়নি: " + e.getMessage());
                    });
                }
            });
            return;
        }
        if(requestCode != PICK_VIDEO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        videoUri = data.getData();
        try { getContentResolver().takePersistableUriPermission(videoUri,data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION)); } catch(Exception ignored) {}
        status.setText("ভিডিও বিশ্লেষণ হচ্ছে…");
        results.removeAllViews();
        worker.execute(() -> analyze(videoUri));
    }

    private static class Moment {
        long atMs; double score;
        Moment(long at,double value){atMs=at;score=value;}
    }

    private void analyze(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this,uri);
            String raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long duration = raw == null ? 0 : Long.parseLong(raw);
            if(duration < 1000) throw new IllegalArgumentException("ভিডিওর দৈর্ঘ্য পড়া যায়নি।");
            List<Moment> scores = new ArrayList<>();
            double[] previous = null;
            for(long t=0; t<duration; t+=1000) {
                Bitmap frame = retriever.getFrameAtTime(t*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if(frame == null) continue;
                Bitmap small = Bitmap.createScaledBitmap(frame,32,32,true);
                if(small != frame) frame.recycle();
                double[] features = features(small); small.recycle();
                double score = previous == null ? 0 : distance(previous,features);
                // A modest contrast term helps avoid ranking only flat scene changes.
                score += features[3] * 0.25;
                scores.add(new Moment(t,score)); previous=features;
            }
            Collections.sort(scores,(a,b)->Double.compare(b.score,a.score));
            List<Moment> selected = new ArrayList<>();
            for(Moment m:scores) {
                boolean far=true;
                for(Moment kept:selected) if(Math.abs(kept.atMs-m.atMs)<5000){far=false;break;}
                if(far) selected.add(m);
                if(selected.size()==5) break;
            }
            Collections.sort(selected,Comparator.comparingLong(m->m.atMs));
            runOnUiThread(()->showResults(selected,duration));
        } catch(Exception e) {
            runOnUiThread(()->status.setText("বিশ্লেষণ করা যায়নি: "+e.getMessage()));
        } finally { try { retriever.release(); } catch(Exception ignored){} }
    }

    private double[] features(Bitmap b) {
        double r=0,g=0,bl=0,luma=0,luma2=0; int n=b.getWidth()*b.getHeight();
        for(int y=0;y<b.getHeight();y++) for(int x=0;x<b.getWidth();x++) {
            int c=b.getPixel(x,y); double rr=Color.red(c)/255.0, gg=Color.green(c)/255.0, bb=Color.blue(c)/255.0;
            r+=rr;g+=gg;bl+=bb; double l=.2126*rr+.7152*gg+.0722*bb;luma+=l;luma2+=l*l;
        }
        double mean=luma/n, variance=Math.max(0,luma2/n-mean*mean);
        return new double[]{r/n,g/n,bl/n,Math.sqrt(variance)};
    }
    private double distance(double[] a,double[] b) {
        return Math.sqrt(Math.pow(a[0]-b[0],2)+Math.pow(a[1]-b[1],2)+Math.pow(a[2]-b[2],2))*2
            + Math.abs(a[3]-b[3]);
    }
    private void showResults(List<Moment> moments,long duration) {
        status.setText("বিশ্লেষণ শেষ। নিচের সময়গুলো দৃশ্যের পরিবর্তন ও বৈচিত্র্যের ভিত্তিতে বাছাই করা হয়েছে—এগুলো AI দিয়ে বিষয়বস্তু বোঝার ফল নয়।");
        results.removeAllViews();
        if(moments.isEmpty()){results.addView(text("কোনো বিশ্লেষণযোগ্য ফ্রেম পাওয়া যায়নি।",14));return;}
        int index=1;
        for(Moment m:moments) {
            long end=Math.min(duration,m.atMs+8000);
            String line=String.format(Locale.getDefault(),"হাইলাইট %d  •  %s – %s",index++,time(m.atMs),time(end));
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(10),dp(12),dp(10));
            card.setBackgroundColor(Color.WHITE);
            TextView item=text(line,16);
            card.addView(item);
            Button cut = button("এই মুহূর্তের ৮ সেকেন্ডের MP4 ক্লিপ সেভ");
            card.addView(cut);
            cut.setOnClickListener(v -> exportMoment(m, cut));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.bottomMargin=dp(8);
            results.addView(card,p);
        }
    }
    private void exportMoment(Moment moment, Button action) {
        if (videoUri == null) {
            status.setText("ভিডিও আবার নির্বাচন করো।");
            return;
        }
        long startMs = Math.max(0, moment.atMs);
        long endMs = Math.min(startMs + 8000, Math.max(startMs + 500, getVideoDurationMs()));
        if (endMs <= startMs) {
            status.setText("এই মুহূর্তের ক্লিপের সময় নির্ধারণ করা যায়নি।");
            return;
        }
        action.setEnabled(false);
        pendingAction = action;
        status.setText("MP4 ক্লিপ তৈরি হচ্ছে…");
        try {
            File folder = new File(getCacheDir(), "best_moments");
            if (!folder.exists() && !folder.mkdirs()) throw new Exception("ক্লিপ ফোল্ডার তৈরি হয়নি");
            pendingExport = new File(folder, "Rifat-Ai-highlight-" + System.currentTimeMillis() + ".mp4");
            MediaItem item = new MediaItem.Builder().setUri(videoUri)
                .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs).setEndPositionMs(endMs).build()).build();
            EditedMediaItem edited = new EditedMediaItem.Builder(item).build();
            Transformer transformer = new Transformer.Builder(this).addListener(new Transformer.Listener() {
                @Override public void onCompleted(Composition composition, ExportResult result) {
                    runOnUiThread(() -> {
                        status.setText("ক্লিপ তৈরি হয়েছে। কোথায় সেভ করবে বেছে নাও।");
                        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        save.addCategory(Intent.CATEGORY_OPENABLE);
                        save.setType("video/mp4");
                        save.putExtra(Intent.EXTRA_TITLE, "Rifat-Ai-highlight.mp4");
                        startActivityForResult(save, SAVE_CLIP);
                    });
                }
                @Override public void onError(Composition composition, ExportResult result, ExportException error) {
                    runOnUiThread(() -> {
                        action.setEnabled(true);
                        status.setText("ক্লিপ তৈরি হয়নি: " + (error.getMessage() == null ? "ভিডিও ফরম্যাট সমর্থিত নয়" : error.getMessage()));
                        if (pendingExport != null) pendingExport.delete();
                    });
                }
            }).build();
            transformer.start(edited, pendingExport.getAbsolutePath());
        } catch (Exception e) {
            action.setEnabled(true);
            status.setText("ক্লিপ Export শুরু হয়নি: " + e.getMessage());
            if (pendingExport != null) pendingExport.delete();
        }
    }

    private long getVideoDurationMs() {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, videoUri);
            String raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return raw == null ? 0 : Long.parseLong(raw);
        } catch (Exception e) {
            return 0;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }    private String time(long ms) {
        long sec=ms/1000; return String.format(Locale.getDefault(),"%02d:%02d",sec/60,sec%60);
    }
    @Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();}
}
