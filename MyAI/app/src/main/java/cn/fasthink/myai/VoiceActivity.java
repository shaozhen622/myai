package cn.fasthink.myai;


import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoiceActivity extends AppCompatActivity {

    private Button speakBtn;
    private TextView recordResult;
    private ExecutorService mExecutorService;
    private MediaRecorder mMediaRecorder;
    private File mAudioFile;
    private long mStartRecordTime, mStopRecordTime;

    private Handler mMainThreadHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //录音JNI函数不具备线程安全性，所以用单线程
        mExecutorService = Executors.newSingleThreadExecutor();
        mMainThreadHandler = new Handler(Looper.getMainLooper());
        /**
         * 初始化按钮
         */
        recordResult = (TextView) findViewById(R.id.recordResult);
        speakBtn = (Button) findViewById(R.id.speck_btn);

        speakBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //根据不同的touch action，执行不同的逻辑
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startRecord();
                        break;
                    case MotionEvent.ACTION_UP:
                        stopRecord();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        break;
                    default:
                        break;
                }
                return true;
            }

            /**
             * 开始录音
             */
            private void startRecord() {
                //改变UI状态
                speakBtn.setText("正在录音");

                //提交后台任务，执行录音逻辑
                mExecutorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        //释放之前录音的recorder
                        releaseRecorder();
                        //执行录音逻辑，如果失败，提示用户
                        if (!doStart()) {
                            recordFail();
                        }
                    }
                });
            }

            /**
             *停止录音
             */
            private void stopRecord() {
                //改变UI状态
                speakBtn.setText("按住说话");
                //提交后台任务，执行录音逻辑
                mExecutorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        //执行停止录音逻辑,失败提醒用户
                        if (!doStop()) {
                            recordFail();
                        }
                        //释放释放MediaRecorder
                        releaseRecorder();
                    }
                });
            }

            /**
             * 启动录音逻辑
             */
            private boolean doStart() {
                boolean result = true;
                try {
                    //创建一个MediaRecorder
                    mMediaRecorder = new MediaRecorder();
                    //创建一个录音文件
                    mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/myai/" + System.currentTimeMillis() + ".m4a");
                    mAudioFile.getParentFile().mkdirs();
                    mAudioFile.createNewFile();
                    /**
                     * 配置MediaRecorder
                     */
                    //从mic采集
                    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    //保存文件为MP4格式-该值为所有系统都支持的采样频率
                    mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    //采样频率
                    mMediaRecorder.setAudioSamplingRate(44100);
                    //设置编码格式-AAC是通用格式
                    mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    //设置编码率
                    mMediaRecorder.setAudioEncodingBitRate(96000);
                    //设置录音文件的位置
                    mMediaRecorder.setOutputFile(mAudioFile.getAbsolutePath());
                    //执行开始录音
                    mMediaRecorder.prepare();
                    mMediaRecorder.start();
                    //记录开始录音的时间，用于统计时长
                    mStartRecordTime = System.currentTimeMillis();
                } catch (IOException | RuntimeException e) {
                    e.printStackTrace();
                    result = false;
                }


                return result;
            }

            /**
             * 停止录音逻辑
             */
            private boolean doStop() {
                boolean result = true;
                //停止录音
                try {
                    mMediaRecorder.stop();
                    //记录停止时间，统计时长
                    mStopRecordTime = System.currentTimeMillis();
                    //记录超过1s的录音
                    final int second = (int) (mStopRecordTime - mStartRecordTime) / 1000;
                    if (second >= 1) {
                        //给用户toast提示失败。需要在主线程执行
                        mMainThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                recordResult.setText("录音成功，时长:" + second + "s");
                            }
                        });
                    }
                } catch (RuntimeException e) {
                    result = false;
                }
                return result;
            }

            /**
             * 释放MediaRecorder
             */
            private void releaseRecorder() {
                //检查mMediaRecorder是否为空
                if (mMediaRecorder != null) {
                    mMediaRecorder.release();
                    mMediaRecorder = null;
                }
                //
            }

            /**
             * 录音错误处理
             */
            private void recordFail() {
                mAudioFile = null;
                //给用户toast提示失败。需要在主线程执行
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(VoiceActivity.this, "录音失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }


        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        //activity销毁时，停止后台任务
        mExecutorService.shutdownNow();
    }
}
