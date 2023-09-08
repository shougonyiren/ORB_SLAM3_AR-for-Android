package com.vslam.orbslam3.vslamactivity;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Process;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 兼容部分设备只有在开飞行模式下才可以与相机建立socket连接
 * <p>
 * 主要流程：在openCamera()前绑定相机WIFI -> bindNetwork()，
 * 在closeCamera()前或监听到相机断开时解绑WIFI -> unbindNetwork()
 * <p>
 * 次要流程：如果app有在非连接相机进程和相机通讯的，注意initWithOtherProcess()和notifyOtherProcessBind()的使用
 */

/**
 * Compatible Some devices can only establish a socket connection with the camera when the airplane mode is turned on
 * <p>
 * Main operation: bind the camera WIFI before openCamera() -> bindNetwork().
 * Unbind the WIFI before closeCamera() or when the camera is disconnected -> unbindNetwork()
 * <p>
 * Secondary operation: If the app is communicating with the camera in a non-connected camera process,
 * pay attention to the use of initWithOtherProcess() and notifyOtherProcessBind()
 */
public class CameraBindNetworkManager {

    public enum ErrorCode {
        OK, BIND_NETWORK_FAIL
    }

    private static String ACTION_BIND_NETWORK_NOTIFY = "com.arashivision.sdk.demo.ACTION_BIND_NETWORK_NOTIFY";
    private static String EXTRA_KEY_IS_BIND = "extra_key_is_bind";

    private static CameraBindNetworkManager sInstance;

    private boolean mHasBindNetwork = false;
    private boolean mIsBindingNetwork = false;
    private String mProcessName = null;

    private CameraBindNetworkManager() {
    }

    public static CameraBindNetworkManager getInstance() {
        if (sInstance == null) {
            sInstance = new CameraBindNetworkManager();
        }
        return sInstance;
    }

    // 在Application检测如果当前是非连接相机进程且需要与相机通讯的话就调用此方法，否则不需要
    // In Application, if it is currently a non-connected camera process and needs to communicate with the camera, call this method, otherwise it is not needed
    public void initWithOtherProcess() {
        mProcessName = getProcessName();
        bindNetwork(null);
        registerChildProcessBindNetworkReceiver();
    }

    private void registerChildProcessBindNetworkReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_BIND_NETWORK_NOTIFY);
        MyApp.getInstance().registerReceiver(mOtherProcessBindNetworkReceiver, intentFilter);
    }

    // 相机进程控制绑定/解绑的同时调用此方法对其它进程绑定/解绑
    // 如果没有在其它进程和相机通讯的，不需要此方法
    // The camera process controls the binding/unbinding while calling this method to bind/unbind other processes
    // If there is no communication with the camera in other processes, this method is not needed
    public void notifyOtherProcessBind(boolean isBind) {
        Intent intent = new Intent();
        intent.setAction(ACTION_BIND_NETWORK_NOTIFY);
        intent.putExtra(EXTRA_KEY_IS_BIND, isBind);
        MyApp.getInstance().sendBroadcast(intent);
    }

    // 在需要连接相机前绑定相机WIFI
    // Bind the camera WIFI before connecting the camera
    public void bindNetwork(IBindNetWorkCallback bindNetWorkCallback) {
        if (mIsBindingNetwork) {
            if (bindNetWorkCallback != null) {
                bindNetWorkCallback.onResult(ErrorCode.OK);
            }
        } else if (mHasBindNetwork) {
            if (bindNetWorkCallback != null) {
                bindNetWorkCallback.onResult(ErrorCode.OK);
            }
        } else {
            bindWifiNet(bindNetWorkCallback);
        }
    }

    // 需要断开相机前解绑WIFI，然后再调用closeCamera()
    // Need to unbind the WIFI before disconnecting the camera, and then call closeCamera()
    public void unbindNetwork() {
        if (mHasBindNetwork) {
            unbindWifiNet();
        }
        if (mIsBindingNetwork) {
            mIsBindingNetwork = false;
        }
    }

    private void bindWifiNet(IBindNetWorkCallback bindNetWorkCallback) {
        if (mIsBindingNetwork) {
            return;
        }
        mIsBindingNetwork = true;
        Network network = getWifiNetwork();
        if (network != null) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    if (ConnectivityManager.setProcessDefaultNetwork(network)) {
                        mHasBindNetwork = true;
                        mIsBindingNetwork = false;
                        if (bindNetWorkCallback != null) {
                            bindNetWorkCallback.onResult(ErrorCode.OK);
                        }
                    } else {
                        mIsBindingNetwork = false;
                        if (bindNetWorkCallback != null) {
                            bindNetWorkCallback.onResult(ErrorCode.BIND_NETWORK_FAIL);
                        }
                    }
                } else {
                    ConnectivityManager connectivityManager = (ConnectivityManager) MyApp.getInstance().getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (connectivityManager.bindProcessToNetwork(network)) {
                        mHasBindNetwork = true;
                        mIsBindingNetwork = false;
                        if (bindNetWorkCallback != null) {
                            bindNetWorkCallback.onResult(ErrorCode.OK);
                        }
                    } else {
                        mIsBindingNetwork = false;
                        if (bindNetWorkCallback != null) {
                            bindNetWorkCallback.onResult(ErrorCode.BIND_NETWORK_FAIL);
                        }
                    }
                }
            } catch (IllegalStateException e) {
                mIsBindingNetwork = false;
                if (bindNetWorkCallback != null) {
                    bindNetWorkCallback.onResult(ErrorCode.BIND_NETWORK_FAIL);
                }
            }
        } else {
            mIsBindingNetwork = false;
            if (bindNetWorkCallback != null) {
                bindNetWorkCallback.onResult(ErrorCode.BIND_NETWORK_FAIL);
            }
        }
    }

    private Network getWifiNetwork() {
        ConnectivityManager connManager = (ConnectivityManager) MyApp.getInstance()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        for (Network network : connManager.getAllNetworks()) {
            NetworkInfo netInfo = connManager.getNetworkInfo(network);
            if (netInfo != null && netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                return network;
            }
        }
        return null;
    }

    private void unbindWifiNet() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (ConnectivityManager.setProcessDefaultNetwork(null)) {
                mHasBindNetwork = false;
            }
        } else {
            ConnectivityManager connectivityManager = (ConnectivityManager) MyApp.getInstance().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager.bindProcessToNetwork(null)) {
                mHasBindNetwork = false;
            }
        }
    }

    private BroadcastReceiver mOtherProcessBindNetworkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), ACTION_BIND_NETWORK_NOTIFY)) {
                boolean isBind = intent.getBooleanExtra(EXTRA_KEY_IS_BIND, false);
                if (isBind) {
                    bindNetwork(null);
                } else {
                    unbindNetwork();
                }
            }
        }
    };

    private String getProcessName() {
        int pid = Process.myPid();
        ActivityManager manager = (ActivityManager) MyApp.getInstance().getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcessesList = manager.getRunningAppProcesses();
        if (runningAppProcessesList == null) {
            runningAppProcessesList = new ArrayList<>();
        }
        for (ActivityManager.RunningAppProcessInfo process : runningAppProcessesList) {
            if (process.pid == pid) {
                return process.processName;
            }
        }
        return null;
    }

    /************************* interface *************************/
    public interface IBindNetWorkCallback {
        void onResult(ErrorCode errorCode);
    }

}
