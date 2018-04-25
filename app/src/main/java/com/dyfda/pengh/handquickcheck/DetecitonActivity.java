package com.dyfda.pengh.handquickcheck;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.posapi.PosApi;
import android.posapi.PrintQueue;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;

import pub.devrel.easypermissions.EasyPermissions;

public class DetecitonActivity extends AppCompatActivity implements View.OnClickListener {

    private PosApi mPosSDK = null;
    private PrintQueue mPrintQueue = null;
    MediaPlayer player;
    private ScanBroadcastReceiver scanBroadcastReceiver;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA};
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private int mCurSerialNo = 3; // usart3
    private int mBaudrate = 4; // 9600
    private byte mGpioPower = 0x1E;// PB14
    private byte mGpioTrig = 0x29;// PC9


    private EditText etInput;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_detection);
        etInput = findViewById(R.id.et_input);
        // 获取PosApi实例
        mPosSDK = PosApi.getInstance(this);

        // 根据型号进行初始化mPosApi类
        if (Build.MODEL.contains("LTE")
                || android.os.Build.DISPLAY.contains("3508")
                || android.os.Build.DISPLAY.contains("403")
                || android.os.Build.DISPLAY.contains("35S09")) {
            mPosSDK.initPosDev("ima35s09");
        } else if (Build.MODEL.contains("5501")) {
            mPosSDK.initPosDev("ima35s12");
        } else {
            mPosSDK.initPosDev(PosApi.PRODUCT_MODEL_IMA80M01);
        }
        mPosSDK.setOnComEventListener(mCommEventListener);
        // 打印队列初始化
        mPrintQueue = new PrintQueue(this, mPosSDK);
        // 打印队列初始化
        mPrintQueue.init();

        //注册获取扫描信息的广播接收器
        IntentFilter mFilter = new IntentFilter();
        mFilter.addAction(PosApi.ACTION_POS_COMM_STATUS);
        registerReceiver(receiver, mFilter);

        // 物理扫描键按下时候会有动作为ismart.intent.scandown的广播发出，可监听该广播实现触发扫描动作
        scanBroadcastReceiver = new ScanBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("ismart.intent.scandown");
        this.registerReceiver(scanBroadcastReceiver, intentFilter);

        // 扫描提示音
        // player = MediaPlayer.create(getApplicationContext(), );
        verifyStoragePermissions(this);
        initPrwmission();
    }



    @Override
    public void onClick(View view) {

        switch (view.getId()) {

            case R.id.btn_print:
                printText();
                break;

            case R.id.btn_take_pic:
                Intent startCameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
               // startCameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(tempFile));
                if (startCameraIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(startCameraIntent, 100);
                }

                break;
            default:
                break;
        }
    }

    /**
     * 打印文字
     */
    public void printText() {

        try {
            // 获取编辑框中的字符串
            String str2 = etInput.getText().toString().trim();

            // 直接把字符串转成byte数组，然后添加到打印队列，这里打印多个\n是为了打印的文字能够出到外面，方便客户看到
            addPrintTextWithSize(1, 50, (str2 + "\n\n\n\n\n\n").getBytes("GBK"));

            // 打印队列启动
            mPrintQueue.printStart();

        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    /*
     * 打印文字 size 1 --倍大小 2--2倍大小
     */
    private void addPrintTextWithSize(int size, int concentration, byte[] data) {
        if (data == null)
            return;
        // 2倍字体大小
        byte[] _2x = new byte[]{0x1b, 0x57, 0x02};
        // 1倍字体大小
        byte[] _1x = new byte[]{0x1b, 0x57, 0x01};
        byte[] mData = null;
        if (size == 1) {
            mData = new byte[3 + data.length];
            // 1倍字体大小 默认
            System.arraycopy(_1x, 0, mData, 0, _1x.length);
            System.arraycopy(data, 0, mData, _1x.length, data.length);

            mPrintQueue.addText(concentration, mData);

        } else if (size == 2) {
            mData = new byte[3 + data.length];
            // 1倍字体大小 默认
            System.arraycopy(_2x, 0, mData, 0, _2x.length);
            System.arraycopy(data, 0, mData, _2x.length, data.length);

            mPrintQueue.addText(concentration, mData);

        }

    }


    /**
     * 初始化回调
     */
    PosApi.OnCommEventListener mCommEventListener = new PosApi.OnCommEventListener() {
        @Override
        public void onCommState(int cmdFlag, int state, byte[] resp, int respLen) {
            // TODO Auto-generated method stub
            switch (cmdFlag) {
                case PosApi.POS_INIT:
                    if (state == PosApi.COMM_STATUS_SUCCESS) {
                        Toast.makeText(getApplicationContext(), "设备初始化成功",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), "设备初始化失败",
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        // after 1000ms openDevice
        // 必须延迟一秒，否则将会出现第一次扫描和打印延迟的现象
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                // 打开GPIO，给扫描头上电
                openDevice();

            }
        }, 1000);

        super.onResume();
    }


    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();

        // 注销获取扫描数据的广播
        unregisterReceiver(receiver);

        // 注销物理SCAN按键的接收广播
        unregisterReceiver(scanBroadcastReceiver);

        // 关闭下层串口以及打印机
        mPosSDK.closeDev();

        if (mPrintQueue != null) {
            // 打印队列关闭
            mPrintQueue.close();
        }
    }

    // 打开串口以及GPIO口
    private void openDevice() {
        // open power
        mPosSDK.gpioControl(mGpioPower, 0, 1);

        mPosSDK.extendSerialInit(mCurSerialNo, mBaudrate, 1, 1, 1, 1);

    }


    /**
     * 扫描信息获取
     */
    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            String action = intent.getAction();
            if (action.equalsIgnoreCase(PosApi.ACTION_POS_COMM_STATUS)) {

                // 串口标志判断
                int cmdFlag = intent.getIntExtra(PosApi.KEY_CMD_FLAG, -1);

                // 获取串口返回的字节数组
                byte[] buffer = intent
                        .getByteArrayExtra(PosApi.KEY_CMD_DATA_BUFFER);

                switch (cmdFlag) {
                    // 如果为扫描数据返回串口
                    case PosApi.POS_EXPAND_SERIAL3:
                        if (buffer == null)
                            return;
                        try {
                            // 将字节数组转成字符串
                            String str = new String(buffer, "GBK");

                            // 开启提示音，提示客户条码或者二维码已经被扫到
                            //  player.start();

                            // 显示到编辑框中
                            etInput.setText(str);

                        } catch (UnsupportedEncodingException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        break;

                }
                // 扫描完本次后清空，以便下次扫描
                buffer = null;

            }
        }

    };

    private void initPrwmission() {
        //新增6.0权限
        String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.INTERNET};
        if (!EasyPermissions.hasPermissions(DetecitonActivity.this, perms)) {
            EasyPermissions.requestPermissions(this, "检测需要相关权限",
                    200, perms);
        }
    }


    /**
     * 物理SCAN按键监听
     */
    boolean isScan = false;

    class ScanBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            //监听到SCAN按键按下广播，执行扫描
            ScanDomn();
        }
    }

    /**
     * 执行扫描，扫描后的结果会通过action为PosApi.ACTION_POS_COMM_STATUS的广播发回
     */
    public void ScanDomn() {
        if (!isScan) {
            mPosSDK.gpioControl(mGpioTrig, 0, 0);
            isScan = true;
            handler.removeCallbacks(run);
            // 3秒后还没有扫描到信息则强制关闭扫描头
            handler.postDelayed(run, 3000);
        } else {
            mPosSDK.gpioControl(mGpioTrig, 0, 1);
            mPosSDK.gpioControl(mGpioTrig, 0, 0);
            isScan = true;
            handler.removeCallbacks(run);
            // 3秒后还没有扫描到信息则强制关闭扫描头
            handler.postDelayed(run, 3000);
        }
    }

    Handler handler = new Handler();
    Runnable run = new Runnable() {
        @Override
        public void run() {
            // TODO Auto-generated method stub
            // 强制关闭扫描头
            mPosSDK.gpioControl(mGpioTrig, 0, 1);
            isScan = false;
        }
    };

    // 检查读写权限
    public static void verifyStoragePermissions(Activity activity) {
        try {
            // 检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(activity,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity,
                        PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
