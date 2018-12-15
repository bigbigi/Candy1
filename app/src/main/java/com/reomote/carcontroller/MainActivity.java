package com.reomote.carcontroller;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Typeface;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.autofit.widget.TextView;
import com.reomote.carcontroller.utils.Connector;
import com.reomote.carcontroller.utils.ThreadManager;
import com.reomote.carcontroller.utils.TracerouteWithPing;
import com.reomote.carcontroller.widget.Stick;
import com.reomote.carcontroller.widget.VideoView;


public class MainActivity extends Activity implements Stick.Callback,
        TracerouteWithPing.OnTraceRouteListener {
    private static final String IP = "10.2.0.76";
    private static final String PATH = "rtsp://13728735758:abcd1234@10.2.0.76:554/stream1";
    private static final int DURATION = 3000;

    private VideoView mPlayer;
    private TracerouteWithPing mTraceroute;
    private TextView mTitle;
    private TextView mDelayText;
    private TextView mSpeedText;
    private Stick mStick;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPlayer = (VideoView) findViewById(R.id.player);
        mDelayText = (TextView) findViewById(R.id.netdelay);
        mSpeedText = (TextView) findViewById(R.id.netspeed);
        mTitle = (TextView) findViewById(R.id.title);
        mStick = (Stick) findViewById(R.id.stick);
        mStick.setCallback(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mTitle.setLetterSpacing(0.1f);
        }
        Typeface speedTypeFace = null;
        try {
            speedTypeFace = Typeface.createFromAsset(getAssets(), "fonts/akkurat.ttf");
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (speedTypeFace != null) {
            mDelayText.setTypeface(speedTypeFace);
            mSpeedText.setTypeface(speedTypeFace);
        }
        mTraceroute = new TracerouteWithPing(this);
        mTraceroute.setOnTraceRouteListener(this);
        mHandler.sendEmptyMessage(MSG_SPEED);
        mHandler.sendEmptyMessage(MSG_PING);
    }


    @Override
    protected void onResume() {
        super.onResume();
        mPlayer.setVideoPath(PATH);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPlayer.pause();
    }

    @Override
    public void finish() {
        super.finish();
        mHandler.removeCallbacksAndMessages(null);
    }

    //------------------------getSpeed---------------------------
    private long mLastTotalBytes;
    private long mLastTimeStamp;
    private static final int MSG_SPEED = 1;
    private static final int MSG_DELAY = 2;
    private static final int MSG_PING = 3;
    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SPEED:
                    getSpeed();
                    if (!isFinishing()) {
                        sendEmptyMessageDelayed(MSG_SPEED, DURATION);
                    }
                    break;
                case MSG_DELAY:
                    mDelayText.setText(String.format("%d MS", msg.arg2));
                    if (!isFinishing()) {
                        sendEmptyMessageDelayed(MSG_PING, DURATION);
                    }
                    break;
                case MSG_PING:
                    mTraceroute.executeTraceroute(IP, 0);
                    break;
            }
        }
    };

    private void getSpeed() {
        long nowTotalBytes = getTotalRxBytes();
        long nowTimeStamp = System.currentTimeMillis();
        long speed = (getTotalRxBytes() - mLastTotalBytes) * 1000 / (nowTimeStamp - mLastTimeStamp);
        mLastTimeStamp = nowTimeStamp;
        mLastTotalBytes = nowTotalBytes;
        mSpeedText.setText(String.format("%.2f Mbp", (float) speed * 8 / 1000));
    }

    public long getTotalRxBytes() {
        return TrafficStats.getUidRxBytes(Process.myUid()) == TrafficStats.UNSUPPORTED ? 0 : (TrafficStats.getTotalRxBytes() / 1024);//转为KB
    }

    //------------------------getDelay---------------------------

    @Override
    public void onResult(int what, int loss, int delay) {
        Message msg = mHandler.obtainMessage(MSG_DELAY);
        msg.arg1 = loss;
        msg.arg2 = delay;
        mHandler.sendMessage(msg);
    }

    @Override
    public void onTimeout(int what) {

    }

    @Override
    public void onException(int what) {

    }

    //------------------------getDelay---------------------------
    private Connector mConnector;

    @Override
    public void onCallback(final float degree, final float ratio) {
        ThreadManager.single(new Runnable() {
            @Override
            public void run() {
                if (mConnector == null) {
                    mConnector = new Connector();
                }
                int speedInt = (int) (400 * -ratio) + 100 * (int) (-ratio / Math.abs(ratio));//100~500
                String data = null;
                if (Math.abs(degree) > 80) {
                    if (degree < 0) {
                        speedInt = Math.abs(speedInt);
                    } else {
                        speedInt = -Math.abs(speedInt);
                    }
                    int sumInt = (0xFA + 0xAA + 0x01 + 0x02 + (speedInt >> 8 & 0x00ff) + (speedInt & 0x00ff)) & 0x00ff;
                    String speed = String.format("%x", toSign(speedInt, 0xffff)).toUpperCase();
                    String sum = String.format("%x", sumInt).toUpperCase();
                    speed = addZero(speed, 4);
                    sum = addZero(sum, 2);
                    data = "FAAA0102" + speed + sum;
                    Log.d("big", "speedInt:" + speedInt);
                    Log.d("big", "speed:" + speed + "，sum:" + sum);
                } else {
                    int angleInt = (int) degree;
                    if (Math.abs(degree) > 30) {
                        angleInt = (int) (30 * degree / Math.abs(degree));
                    }
                    int sumInt = (0xFA + 0xAA + 0x00 + 0x03 + angleInt + (speedInt >> 8 & 0x00ff) + (speedInt & 0x00ff)) & 0x00ff;
                    String angle = String.format("%x", toSign(angleInt, 0xff)).toUpperCase();
                    String speed = String.format("%x", toSign(speedInt, 0xffff)).toUpperCase();
                    String sum = String.format("%x", sumInt).toUpperCase();
                    angle = addZero(angle, 2);
                    speed = addZero(speed, 4);
                    sum = addZero(sum, 2);
                    data = "FAAA0003" + angle + speed + sum;
                    Log.d("big", "angleInt:" + angleInt + "，speedInt:" + speedInt);
                    Log.d("big", "angle:" + angle + ",speed:" + speed + ",sum:" + sum + ",ratio：" + speedInt);
                }
                mConnector.send(data);//FAAA00031E008D52

            }
        });
    }

    private int toSign(int value, int format) {
        if (value < 0) {
            return ((~Math.abs(value)) & format) + 1;
        }
        return value;
    }

    private String addZero(String s, int length) {
        while (s.length() < length) {
            s = "0" + s;
        }
        return s;
    }
}
