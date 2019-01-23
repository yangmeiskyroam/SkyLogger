package com.skyroam.SkyLogger;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class BroadcastUtils extends BroadcastReceiver {
    private static final String ACTION_BOOT = "android.intent.action.BOOT_COMPLETED";
    private static final String ACTION_SHUTDOWN = "android.intent.action.ACTION_SHUTDOWN";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION_BOOT)) { //开机启动完成后，要做的事情
            LogUtils.i(BroadcastUtils.class, "BOOT_COMPLETED onReceive()");
            if (LoggingUtils.isBootAutoLogging(context)) {
                if(LoggingUtils.StartLogging(context)){
                    LoggingUtils.setBootAutoLogging(false, context);
                }
            }
        } else if (intent.getAction().equals(ACTION_SHUTDOWN)) {
            LogUtils.i(BroadcastUtils.class, "ACTION_SHUTDOWN onReceive()");
            if (LoggingUtils.LoggingRunnig(context) == true) {
                LoggingUtils.setBootAutoLogging(true, context);
            }
        }else if(intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)){
            //获取联网状态的NetworkInfo对象
            LogUtils.i(BroadcastUtils.class, "CONNECTIVITY_ACTION onReceive()");
            ConnectivityManager manager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetwork = manager.getActiveNetworkInfo();
            if (activeNetwork != null) {
                if (activeNetwork.isConnected()) {
                    if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                        LogUtils.i(BroadcastUtils.class, "当前WiFi连接可用 ");
                        new Thread() {
                            @Override
                            public void run() {
                                super.run();
                                if(ping()){

                                }
                            }
                        }.start();
                    }else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                        LogUtils.i(BroadcastUtils.class, "当前移动网络连接可用 ");
                    }
                }else{
                    LogUtils.e(BroadcastUtils.class, "当前没有网络连接，请确保你已经打开网络 ");
                }
            }else{
                LogUtils.e(BroadcastUtils.class, "当前没有网络连接，请确保你已经打开网络 ");
            }
        }
    }

    /* @author suncat
     * @category 判断是否有外网连接（普通方法不能判断外网的网络是否连接，比如连接上局域网）
     * @return
     */
    private static final boolean ping() {

        String result = null;
        try {
            String ip = "www.baidu.com";// ping 的地址，可以换成任何一种可靠的外网
            Process p = Runtime.getRuntime().exec("ping -c 3 -w 100 " + ip);// ping网址3次
            // 读取ping的内容，可以不加
            InputStream input = p.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(input));
            StringBuffer stringBuffer = new StringBuffer();
            String content = "";
            while ((content = in.readLine()) != null) {
                stringBuffer.append(content);
            }
            LogUtils.d(BroadcastUtils.class, "ping result content : " + stringBuffer.toString());
            // ping的状态
            int status = p.waitFor();
            if (status == 0) {
                result = "success";
                return true;
            } else {
                result = "failed";
            }
        } catch (IOException e) {
            result = "IOException";
        } catch (InterruptedException e) {
            result = "InterruptedException";
        } finally {
            LogUtils.d(BroadcastUtils.class, "result = " + result);
        }
        return false;
    }
}
