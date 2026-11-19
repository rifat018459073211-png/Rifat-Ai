package com.rifat.aivideoorganizer;

import android.app.Activity;
import android.content.Intent;
import android.media.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import android.content.ContentValues;
import android.provider.MediaStore;

import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline Bengali video transcription and SRT subtitle export using sherpa-onnx. */
public class VideoTranscriptActivity extends Activity {
    private static final int PICK_VIDEO = 810;
    private static final int SAVE_SRT = 811;
    private static final int SAVE_TXT = 812;
    private static final String MODEL_NAME = "sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09";
    private static final String MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" + MODEL_NAME + ".tar.bz2";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status, transcript;
    private Button runButton, saveSrt, saveTxt;
    private Uri videoUri;
    private String transcriptText = "";
    private String srtText = "";

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private TextView text(String s, int size) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(Color.rgb(35,43,58)); return t;
    }
    private Button button(String s) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b;
    }
    private void status(String s) { main.post(() -> status.setText(s)); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(20),dp(18),dp(28));
        page.setBackgroundColor(Color.rgb(246,248,252));
        scroll.addView(page);
        Button back = button("← ফিরে যাও"); page.addView(back); back.setOnClickListener(v -> finish());
        page.addView(text("Video to Text • বাংলা সাবটাইটেল",23));
        TextView note = text("ভিডিওর অডিও ফোনেই প্রসেস হবে। প্রথমবার প্রায় ৯০ MB-এর বাংলা speech model ডাউনলোড হবে; এরপর অফলাইনে কাজ করবে।",14);
        note.setPadding(0,dp(8),0,dp(14)); page.addView(note);
        Button pick = button("ভিডিও নির্বাচন করো"); page.addView(pick); pick.setOnClickListener(v -> pickVideo());
        status = text("এখনো কোনো ভিডিও নির্বাচন করা হয়নি।",13);
        status.setPadding(0,dp(12),0,dp(12)); page.addView(status);
        runButton = button("বাংলা লেখা ও সাবটাইটেল তৈরি করো");
        runButton.setEnabled(false); page.addView(runButton);
        runButton.setOnClickListener(v -> startTranscription());
        LinearLayout saves = new LinearLayout(this); saves.setOrientation(LinearLayout.HORIZONTAL);
        saveTxt = button("TXT সেভ"); saveSrt = button("SRT সেভ");
        saveTxt.setEnabled(false); saveSrt.setEnabled(false);
        saves.addView(saveTxt,new LinearLayout.LayoutParams(0,-2,1));
        saves.addView(saveSrt,new LinearLayout.LayoutParams(0,-2,1)); page.addView(saves);
        saveTxt.setOnClickListener(v -> saveFile(SAVE_TXT));
        saveSrt.setOnClickListener(v -> saveFile(SAVE_SRT));
        transcript = text("তৈরি হওয়া লেখা এখানে দেখা যাবে।",14);
        transcript.setPadding(0,dp(16),0,0); page.addView(transcript);
        setContentView(scroll);
    }

    private void pickVideo() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("video/*");
        startActivityForResult(i,PICK_VIDEO);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode != RESULT_OK || data == null) return;
        if(requestCode == PICK_VIDEO && data.getData() != null) {
            videoUri = data.getData();
            status.setText("ভিডিও নির্বাচিত হয়েছে।");
            runButton.setEnabled(true);
        } else if(requestCode == SAVE_SRT || requestCode == SAVE_TXT) {
            Uri out = data.getData();
            String value = requestCode == SAVE_SRT ? srtText : transcriptText;
            worker.execute(() -> {
                try(OutputStream stream = getContentResolver().openOutputStream(out)) {
                    if(stream == null) throw new IOException("ফাইল তৈরি করা যায়নি");
                    stream.write(value.getBytes("UTF-8"));
                    main.post(() -> Toast.makeText(this,"ফাইল সেভ হয়েছে।",Toast.LENGTH_LONG).show());
                } catch(Exception e) { main.post(() -> Toast.makeText(this,"সেভ হয়নি: "+e.getMessage(),Toast.LENGTH_LONG).show()); }
            });
        }
    }

    private void saveFile(int requestCode) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(requestCode == SAVE_SRT ? "application/x-subrip" : "text/plain");
        i.putExtra(Intent.EXTRA_TITLE,requestCode == SAVE_SRT ? "subtitles-bn.srt" : "transcript-bn.txt");
        startActivityForResult(i,requestCode);
    }

    private void startTranscription() {
        runButton.setEnabled(false); saveTxt.setEnabled(false); saveSrt.setEnabled(false);
        transcript.setText("");
        worker.execute(() -> {
            try {
                File model = ensureModel();
                List<Caption> captions = transcribeVideo(videoUri,model);
                StringBuilder text = new StringBuilder();
                StringBuilder srt = new StringBuilder();
                int n=1;
                for(Caption c:captions) {
                    if(c.text.trim().isEmpty()) continue;
                    text.append(c.text.trim()).append('\n');
                    srt.append(n++).append('\n')
                        .append(time(c.startMs)).append(" --> ").append(time(Math.max(c.startMs+400,c.endMs))).append('\n')
                        .append(c.text.trim()).append("\n\n");
                }
                transcriptText = text.toString().trim();
                srtText = srt.toString();
                if(transcriptText.isEmpty()) throw new IOException("কোনো স্পষ্ট বাংলা কথা শনাক্ত হয়নি।");
                main.post(() -> {
                    transcript.setText(transcriptText);
                    status.setText("সম্পন্ন। বাংলা transcript ও SRT subtitle তৈরি হয়েছে।");
                    saveTxt.setEnabled(true); saveSrt.setEnabled(true); runButton.setEnabled(true);
                });
            } catch(Exception e) {
                main.post(() -> {
                    status.setText("কাজ সম্পন্ন হয়নি: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
                    runButton.setEnabled(true);
                });
            }
        });
    }

    private File ensureModel() throws Exception {
        File dir = new File(getFilesDir(),MODEL_NAME);
        File encoder = new File(dir,"encoder.onnx");
        File decoder = new File(dir,"decoder.onnx");
        File joiner = new File(dir,"joiner.onnx");
        File tokens = new File(dir,"tokens.txt");
        if(encoder.isFile() && decoder.isFile() && joiner.isFile() && tokens.isFile()) return dir;
        status("প্রথমবারের বাংলা speech model ডাউনলোড হচ্ছে…");
        if(!dir.exists() && !dir.mkdirs()) throw new IOException("Model folder তৈরি হয়নি");
        File archive = new File(getCacheDir(),MODEL_NAME+".tar.bz2");
        HttpURLConnection connection = (HttpURLConnection)new URL(MODEL_URL).openConnection();
        connection.setConnectTimeout(20000); connection.setReadTimeout(120000);
        connection.setRequestProperty("User-Agent","Rifat-Ai Android");
        try {
            int code=connection.getResponseCode();
            if(code<200 || code>=300) throw new IOException("Model download HTTP "+code);
            long total=connection.getContentLengthLong(), done=0;
            try(InputStream in=connection.getInputStream(); OutputStream out=new FileOutputStream(archive)) {
                byte[] buf=new byte[64*1024]; int read;
                while((read=in.read(buf))!=-1) {
                    out.write(buf,0,read); done+=read;
                    if(total>0) { final int pct=(int)(done*100/total); status("বাংলা model ডাউনলোড হচ্ছে… "+pct+"%"); }
                }
            }
        } finally { connection.disconnect(); }
        status("Speech model প্রস্তুত করা হচ্ছে…");
        try(InputStream file=new FileInputStream(archive);
            BZip2CompressorInputStream bz=new BZip2CompressorInputStream(file);
            TarArchiveInputStream tar=new TarArchiveInputStream(bz)) {
            TarArchiveEntry entry;
            while((entry=tar.getNextTarEntry())!=null) {
                String name=entry.getName();
                String prefix=MODEL_NAME+"/";
                if(!name.startsWith(prefix) || entry.isDirectory()) continue;
                String relative=name.substring(prefix.length());
                if(relative.contains("..") || relative.contains("/")) continue;
                File target=new File(dir,relative);
                try(OutputStream out=new FileOutputStream(target)) {
                    byte[] buf=new byte[32*1024]; int read;
                    while((read=tar.read(buf))!=-1) out.write(buf,0,read);
                }
            }
        } finally { archive.delete(); }
        if(!encoder.isFile() || !decoder.isFile() || !joiner.isFile() || !tokens.isFile())
            throw new IOException("ডাউনলোড করা model অসম্পূর্ণ");
        return dir;
    }

    private static class Caption {
        long startMs,endMs; String text;
        Caption(long s,long e,String t){startMs=s;endMs=e;text=t;}
    }

    private List<Caption> transcribeVideo(Uri uri,File modelDir) throws Exception {
        status("ভিডিওর অডিও পড়া হচ্ছে…");
        OnlineTransducerModelConfig transducer = new OnlineTransducerModelConfig();
        transducer.setEncoder(new File(modelDir,"encoder.onnx").getAbsolutePath());
        transducer.setDecoder(new File(modelDir,"decoder.onnx").getAbsolutePath());
        transducer.setJoiner(new File(modelDir,"joiner.onnx").getAbsolutePath());
        OnlineModelConfig model = new OnlineModelConfig();
        model.setTransducer(transducer);
        model.setTokens(new File(modelDir,"tokens.txt").getAbsolutePath());
        model.setModelType("zipformer2");
        model.setModelingUnit("bpe");
        model.setBpeVocab(new File(modelDir,"bpe.model").getAbsolutePath());
        model.setNumThreads(2);
        OnlineRecognizerConfig config = new OnlineRecognizerConfig();
        config.setModelConfig(model);
        config.setEnableEndpoint(true);
        OnlineRecognizer recognizer = new OnlineRecognizer(null,config);
        OnlineStream stream = recognizer.createStream("");
        List<Caption> result = new ArrayList<>();
        long fedSamples=0, segmentStart=0;
        float[] pending=new float[0];
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try(android.os.ParcelFileDescriptor pfd=getContentResolver().openFileDescriptor(uri,"r")) {
            if(pfd==null) throw new IOException("ভিডিও খোলা যায়নি");
            extractor.setDataSource(pfd.getFileDescriptor());
            int audioTrack=-1;
            for(int i=0;i<extractor.getTrackCount();i++) {
                MediaFormat f=extractor.getTrackFormat(i);
                String trackMime=f.getString(MediaFormat.KEY_MIME);
                if(trackMime!=null && trackMime.startsWith("audio/")) { audioTrack=i; break; }
            }
            if(audioTrack<0) throw new IOException("ভিডিওতে অডিও track নেই");
            extractor.selectTrack(audioTrack);
            MediaFormat inputFormat=extractor.getTrackFormat(audioTrack);
            String mime=inputFormat.getString(MediaFormat.KEY_MIME);
            decoder=MediaCodec.createDecoderByType(mime);
            decoder.configure(inputFormat,null,null,0); decoder.start();
            boolean inputDone=false,outputDone=false;
            int sampleRate=inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)?inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE):44100;
            int channels=inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT):2;
            MediaFormat outputFormat=inputFormat;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            while(!outputDone) {
                if(!inputDone) {
                    int inIndex=decoder.dequeueInputBuffer(10000);
                    if(inIndex>=0) {
                        ByteBuffer in=decoder.getInputBuffer(inIndex);
                        if(in==null) throw new IOException("Audio input buffer পাওয়া যায়নি");
                        in.clear();
                        int size=extractor.readSampleData(in,0);
                        if(size<0) {
                            decoder.queueInputBuffer(inIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone=true;
                        } else {
                            decoder.queueInputBuffer(inIndex,0,size,extractor.getSampleTime(),0);
                            extractor.advance();
                        }
                    }
                }
                int outIndex=decoder.dequeueOutputBuffer(info,10000);
                if(outIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outputFormat=decoder.getOutputFormat();
                    if(outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate=outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if(outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels=outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                } else if(outIndex>=0) {
                    ByteBuffer out=decoder.getOutputBuffer(outIndex);
                    if(out!=null && info.size>0) {
                        out.position(info.offset); out.limit(info.offset+info.size);
                        int pcmEncoding=android.media.AudioFormat.ENCODING_PCM_16BIT;
                        if(android.os.Build.VERSION.SDK_INT>=24 && outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            pcmEncoding=outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                        int bytesPerSample=pcmEncoding==android.media.AudioFormat.ENCODING_PCM_FLOAT?4:2;
                        int frameCount=info.size/(bytesPerSample*Math.max(1,channels));
                        float[] mono=new float[frameCount];
                        for(int frame=0;frame<frameCount;frame++) {
                            double sum=0;
                            for(int ch=0;ch<channels;ch++) {
                                float value=pcmEncoding==android.media.AudioFormat.ENCODING_PCM_FLOAT
                                    ? out.getFloat() : out.getShort()/32768.0f;
                                sum+=value;
                            }
                            mono[frame]=(float)(sum/channels);
                        }
                        float[] converted=resampleTo16k(mono,sampleRate);
                        float[] joined=new float[pending.length+converted.length];
                        System.arraycopy(pending,0,joined,0,pending.length);
                        System.arraycopy(converted,0,joined,pending.length,converted.length);
                        int offset=0;
                        while(joined.length-offset>=1600) {
                            float[] chunk=new float[1600];
                            System.arraycopy(joined,offset,chunk,0,1600);
                            stream.acceptWaveform(chunk,16000);
                            fedSamples+=1600;
                            while(recognizer.isReady(stream)) recognizer.decode(stream);
                            if(recognizer.isEndpoint(stream)) {
                                String spoken=recognizer.getResult(stream).getText();
                                if(spoken!=null && !spoken.trim().isEmpty())
                                    result.add(new Caption(segmentStart*1000/16000,fedSamples*1000/16000,spoken));
                                recognizer.reset(stream);
                                segmentStart=fedSamples;
                            }
                            offset+=1600;
                        }
                        pending=new float[joined.length-offset];
                        System.arraycopy(joined,offset,pending,0,pending.length);
                    }
                    boolean eos=(info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    decoder.releaseOutputBuffer(outIndex,false);
                    if(eos) outputDone=true;
                }
            }
            if(pending.length>0) {
                float[] finalChunk=new float[1600];
                System.arraycopy(pending,0,finalChunk,0,Math.min(pending.length,1600));
                stream.acceptWaveform(finalChunk,16000);
                fedSamples+=pending.length;
                while(recognizer.isReady(stream)) recognizer.decode(stream);
            }
            stream.inputFinished();
            while(recognizer.isReady(stream)) recognizer.decode(stream);
            String last=recognizer.getResult(stream).getText();
            if(last!=null && !last.trim().isEmpty())
                result.add(new Caption(segmentStart*1000/16000,Math.max(segmentStart+1,fedSamples)*1000/16000,last));
        } finally {
            try { if(decoder!=null) { decoder.stop(); decoder.release(); } } catch(Exception ignored) {}
            extractor.release();
            try { stream.release(); } catch(Exception ignored) {}
            try { recognizer.release(); } catch(Exception ignored) {}
        }
        return result;
    }

    private static float[] resampleTo16k(float[] input,int sourceRate) {
        if(sourceRate==16000) return input;
        if(sourceRate<=0 || input.length==0) return new float[0];
        int count=(int)((long)input.length*16000/sourceRate);
        float[] output=new float[count];
        for(int i=0;i<count;i++) {
            double position=(double)i*sourceRate/16000.0;
            int left=Math.min(input.length-1,(int)position);
            int right=Math.min(input.length-1,left+1);
            double fraction=position-left;
            output[i]=(float)(input[left]*(1.0-fraction)+input[right]*fraction);
        }
        return output;
    }

    private static String time(long ms) {
        long h=ms/3600000, m=(ms%3600000)/60000, s=(ms%60000)/1000, x=ms%1000;
        return String.format(java.util.Locale.US,"%02d:%02d:%02d,%03d",h,m,s,x);
    }
}
