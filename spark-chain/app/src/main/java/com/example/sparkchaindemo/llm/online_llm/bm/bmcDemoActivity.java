package com.example.sparkchaindemo.llm.online_llm.bm;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sparkchaindemo.R;
import com.hjq.permissions.OnPermission;
import com.hjq.permissions.XXPermissions;
import com.iflytek.sparkchain.core.asr.ASR;
import com.iflytek.sparkchain.core.asr.AsrCallbacks;
import com.iflytek.sparkchain.core.asr.AudioAttributes;
import com.iflytek.sparkchain.core.asr.Segment;
import com.iflytek.sparkchain.core.asr.Transcription;
import com.iflytek.sparkchain.core.asr.Vad;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;
/*************************
 * 中文识别大模型Demo
 * create by wxw
 * 2024-12-17
 * **********************************/
public class bmcDemoActivity extends AppCompatActivity implements View.OnClickListener{
    private static final String TAG = "AEELog";
    private TextView tv_bmc_result;

    private Button btn_audio_start,btn_file_start;
    private ASR asr;

    private String startMode = "NONE";
    private String cacheInfo = "";
    private boolean isrun = false;

    AsrCallbacks mAsrCallbacks = new AsrCallbacks() {
        @Override
        public void onResult(ASR.ASRResult asrResult, Object o) {
            //以下信息需要开发者根据自身需求，如无必要，可不需要解析执行。
            String result                      = asrResult.getBestMatchText();    //识别结果返回接口，开发者可通过此方法快速获取识别结果。
            int status                         = asrResult.getStatus();           //识别结果返回进度，0：开始，1：中间，2：结束
            String sid                         = asrResult.getSid();              //本次交互的sid
            List<Vad> vads                     = asrResult.getVads();
            List<Transcription> transcriptions = asrResult.getTranscriptions();
            int vad_begin = -1;
            int vad_end = -1;
            String word = null;
            for(Vad vad:vads){
                vad_begin = vad.getBegin();                                       //起始的端点帧偏移值，单位：帧（1帧=10ms）
                vad_end = vad.getEnd();                                           //结束的端点帧偏移值，单位：帧（1帧=10ms）
            }
            for(Transcription transcription : transcriptions){
                List<Segment> segments = transcription.getSegments();
                for(Segment segment:segments){
                    word = segment.getText();                                      //字词
                }
            }
            String resultBuf = "onResult:{result:"+result+",status:"+status+",begin:"+vad_begin+",end:"+vad_end+",word:"+word+"}";
            Log.d(TAG,resultBuf);

            showInfo(cacheInfo+"识别结果："+result+"\n","SETTEXT");
            if(status == 2){
                cacheInfo = cacheInfo + "识别结果：" + result+"\n";
                stopAsr();
            }
        }

        @Override
        public void onError(ASR.ASRError asrError, Object o) {
            int errCode   = asrError.getCode();    //错误码
            String errMsg = asrError.getErrMsg();  //错误信息
            String sid    = asrError.getSid();     //本次交互的sid
            showInfo("识别出错了！错误码:"+errCode+",错误信息:"+errMsg+",sid:"+sid+"\n","APPEND");
            stopAsr();
        }

        @Override
        public void onBeginOfSpeech() {

        }

        @Override
        public void onEndOfSpeech() {

        }
    };
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.online_llm_bmc);
        initView();
        initASR();
    }
    private void initView(){
        findViewById(R.id.online_llm_bmc_stop_btn).setOnClickListener(this);
        btn_audio_start = findViewById(R.id.online_llm_bmc_audio_start_btn);
        btn_file_start = findViewById(R.id.online_llm_bmc_file_start_btn);

        tv_bmc_result = findViewById(R.id.online_llm_bmc_result);
        tv_bmc_result.setMovementMethod(new ScrollingMovementMethod());
        btn_audio_start.setOnClickListener(this);
        btn_file_start.setOnClickListener(this);
    }

    private void initASR(){
        /**************
         * 初始化入参:
         * language:语种，zh_cn:中文
         * domain:领域，slm:大模型识别
         * accent:方言，mandarin:普通话
         * language,domain，accent这三个参数配套使用。
         * language=zh_cn,domain=slm,accent=mandarin代表中文语音大模型
         * ******************/
        asr = new ASR("zh_cn","slm","mandarin");
        asr.registerCallbacks(mAsrCallbacks);
    }

    private int runAsr_file() {
        int ret = -1;
        if(isrun){
            showInfo("正在识别中，请勿重复开启。\n","APPEND");
            return ret;
        }
        if(asr == null){
            Log.d(TAG, "Asr 初始化失败。");
            showInfo("Asr 初始化失败,请重新进入。\n","APPEND");
            return ret;
        }

        AudioAttributes atr = new AudioAttributes();
        atr.setSampleRate(16000);//采样率。16000:16K
        atr.setEncoding("raw");//音频编码。raw:pcm格式的原始音频
        atr.setChannels(1);//声道。1:单声道
        ret = asr.start(atr, null);
        if (ret != 0) {
            Log.e(TAG, "asr start failed" + ret);
            return ret;
        }
        isrun = true;
        try{
            String audioPath = "/sdcard/iflytek/asr/cn.pcm";//识别音频路径，开发者可根据自身需求修改，但要求有读写权限。Demo仅演示读音频识别。SDK亦支持从麦克风实时读入音频去识别，这里不做展示。
            showInfo("识别音频路径："+audioPath+"\n","APPEND");
            showInfo("正在识别中，请稍等...\n","APPEND");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btn_audio_start.setEnabled(false);
                    btn_file_start.setEnabled(false);
                }
            });
            byte[] data = readStream(audioPath);
            int leftAudioBytes = data.length;
            int byteWrites = 1280;
            int writeLen = 0;
            int index = 0;

            while (leftAudioBytes > 0) {
                if(!isrun){
                    break;
                }
                if (leftAudioBytes > byteWrites) {
                    writeLen = byteWrites;
                } else {
                    writeLen = leftAudioBytes;
                }
                leftAudioBytes -= writeLen;
                byte[] part = Arrays.copyOfRange(data, index * byteWrites,
                        index * byteWrites + writeLen);

                ret = asr.write(part);
                if (ret != 0) {
                    Log.e(TAG, "asr write failed" + ret);
                    return ret;
                }
                index++;
            }
            ret = asr.stop(false);//不报错结束。true:立即结束，false:等大模型返回最后一帧数据后结束
        }catch(Exception e){
            e.printStackTrace();
            ret = asr.stop(true);//报错打断。true:立即结束，false:等大模型返回最后一帧数据后结束
            stopAsr();
        }
        if (ret != 0) {
            Log.e(TAG, "asr stop failed" + ret);
        }
        return ret;
    }
    public byte[] readStream(String filePath) throws Exception {
        FileInputStream fs = new FileInputStream(filePath);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len = 0;
        while (-1 != (len = fs.read(buffer))) {
            outStream.write(buffer, 0, len);
        }
        outStream.close();
        fs.close();
        return outStream.toByteArray();
    }


    private int runAsr_Audio() {
        int ret = -1;
        if(isrun){
            showInfo("正在识别中，请勿重复开启。\n","APPEND");
            return ret;
        }
        if(asr == null){
            Log.d(TAG, "Asr 初始化失败。");
            showInfo("Asr 初始化失败,请重新进入。\n","APPEND");
            return ret;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showInfo("已开启录音，说完后请点击停止识别，即可获得最终结果\n","APPEND");
                btn_audio_start.setText("录音中\n");
                btn_audio_start.setEnabled(false);
                btn_file_start.setEnabled(false);
            }
        });
        asr.dwa("wpgs");
        ret = asr.startListener(null);
        if (ret != 0) {
            Log.e(TAG, "asr start failed" + ret);
        }else{
            isrun = true;
        }
        return ret;
    }



    private void stopAsr(){
        if(isrun){
            if("AUDIO".equals(startMode)){
                if(asr!=null){
                    asr.stopListener(false);
                    showInfo("已停止录音。\n","APPEND");
                }
            }else{
                if(asr!=null) {
                    asr.stop(true);//取消。true:立即结束，false:等大模型返回最后一帧数据后结束
                    showInfo("已停止识别。\n","APPEND");
                }
            }
            startMode = "NONE";
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btn_audio_start.setText("麦克风识别");
                    btn_audio_start.setEnabled(true);
                    btn_file_start.setEnabled(true);
                }
            });
            isrun = false;
        }else{
            showInfo("已停止识别。\n","APPEND");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(asr!=null)
            asr.stop(true);//报错打断。true:立即结束，false:等大模型返回最后一帧数据后结束
    }


    private void getPermission(){
        XXPermissions.with(this).permission("android.permission.RECORD_AUDIO").request(new OnPermission() {
            @Override
            public void hasPermission(List<String> granted, boolean all) {
                Log.d(TAG,"SDK获取系统权限成功:"+all);
                for(int i=0;i<granted.size();i++){
                    Log.d(TAG,"获取到的权限有："+granted.get(i));
                }
                if(all){
                    runAsr_Audio();
                }
            }

            @Override
            public void noPermission(List<String> denied, boolean quick) {
                if(quick){
                    Log.e(TAG,"onDenied:被永久拒绝授权，请手动授予权限");
                    XXPermissions.startPermissionActivity(bmcDemoActivity.this,denied);
                }else{
                    Log.e(TAG,"onDenied:权限获取失败");
                }
            }
        });
    }



    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.online_llm_bmc_file_start_btn:
                startMode = "FILE";
                runAsr_file();
                break;
            case R.id.online_llm_bmc_audio_start_btn:
                startMode = "AUDIO";
                getPermission();
                break;
            case R.id.online_llm_bmc_stop_btn:
                stopAsr();
                break;
        }
    }
    /*************************
     * 显示控件自动下移
     * *******************************/
    public void toend(){
        int scrollAmount = tv_bmc_result.getLayout().getLineTop(tv_bmc_result.getLineCount()) - tv_bmc_result.getHeight();
        if (scrollAmount > 0) {
            tv_bmc_result.scrollTo(0, scrollAmount+10);
        }
    }

    private void showInfo(String text,String mode){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if("APPEND".equals(mode)){
                    cacheInfo = cacheInfo + text;
                    tv_bmc_result.append(text);
                }
                else
                    tv_bmc_result.setText(text);
                toend();
            }
        });
    }
}
