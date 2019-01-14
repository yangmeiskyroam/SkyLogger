package com.skyroam.SkyLogger;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import java.lang.reflect.Method;
import java.util.List;

public class BasicInfo {
    private static final int MAX_CARD_NUM = 3;

    private static String getProperty(String key, String defaultValue) {
        String value = defaultValue;
        try { Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            value = (String)(get.invoke(c, key, "unknown" ));
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.e(BasicInfo.class,"获取属性失败："+e.toString());
        }finally {
            return value;
        }
    }

    private static void setProperty(String key, String value) {
        try { Class<?> c = Class.forName("android.os.SystemProperties");
            Method set = c.getMethod("set", String.class, String.class);
            set.invoke(c, key, value );
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.e(BasicInfo.class,"设置属性失败："+e.toString());
        }
    }


    public static String getSN(){
        String sn = getProperty("sys.skyroam.osi.sn","unknown");
        LogUtils.d(BasicInfo.class,"sn:"+sn);
        return sn;
    }

    //获取imei
    public static String[] getIMEI() {
        try {
            String[] imeis = new String[MAX_CARD_NUM];
            int index = 0;
            if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(MainActivity.getContext(), Manifest.permission.READ_PHONE_STATE)) {
                //实例化TelephonyManager对象
                TelephonyManager telephonyManager = (TelephonyManager) MainActivity.getContext().getSystemService(Context.TELEPHONY_SERVICE);
                //获取IMEI号
                String imei0 = telephonyManager.getImei(0);
                if(imei0 != null) {
                    imeis[index++] = imei0;
                    LogUtils.d(BasicInfo.class,"imei0:"+imei0);
                }else{
                    imeis[index++] = "000000000000000";
                }
                String imei1 = telephonyManager.getImei(1);
                if(imei1 != null) {
                    imeis[index++] = imei1;
                    LogUtils.d(BasicInfo.class,"imei1:"+imei1);
                }else{
                    imeis[index++] = "000000000000000";
                }
                String imei2 = telephonyManager.getImei(2);
                if(imei2 != null) {
                    imeis[index++] = imei2;
                    LogUtils.d(BasicInfo.class,"imei2:"+imei2);
                }else{
                    imeis[index++] = "000000000000000";
                }

                return imeis;
            }else{
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.e(BasicInfo.class,"get imei exception : " + e.toString());
            return null;
        }
    }

    //获取imsi
    public static String[] getIMSI() {
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(MainActivity.getContext(), Manifest.permission.READ_PHONE_STATE)) {
            SubscriptionInfo sir = null;
            String subscriberId = null;
            String[] imsis = new String[MAX_CARD_NUM];
            int imsi_index = 0;
            SubscriptionManager subManager = SubscriptionManager.from(MainActivity.getContext());
            TelephonyManager telephonyManager = (TelephonyManager) MainActivity.getContext().getSystemService(MainActivity.getContext().TELEPHONY_SERVICE);
            if ((subManager != null)&&(telephonyManager != null))
            {
                List<SubscriptionInfo> subInfoList = subManager.getActiveSubscriptionInfoList();
                if (subInfoList != null)
                {
                    for (int index = 0; index < subInfoList.size(); index++)
                    {
                        sir = subInfoList.get(index);
                        if (sir != null) {
                            try {
                                Method method = TelephonyManager.class.getDeclaredMethod("getSubscriberId", int.class);
                                subscriberId = (String) method.invoke(telephonyManager, sir.getSubscriptionId());
                                if(subscriberId != null){
                                    imsis[imsi_index++] = subscriberId;
                                    LogUtils.d(BasicInfo.class,"imsi"+sir.getSimSlotIndex()+":"+subscriberId);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                LogUtils.e(BasicInfo.class,"get imsi exception : " + e.toString());
                            }
                        }
                    }
                    return imsis;
                }
                else
                {
                    LogUtils.e(BasicInfo.class,"subInfoList is null");
                    return null;
                }
            }
            else
            {
                LogUtils.e(BasicInfo.class,"subManager is null or telephonyManager is null");
                return null;
            }
        }
        else
        {
            LogUtils.e(BasicInfo.class,"no READ_PHONE_STATE permission");
            return null;
        }
    }

    //系统版本
    public static String getSystemVersion() {
        LogUtils.d(BasicInfo.class,"system version:"+android.os.Build.VERSION.RELEASE);
        return android.os.Build.VERSION.RELEASE;
    }

    public static String ModemVersion(){
        String modem_ver = getProperty("gsm.version.baseband","unknown");
        LogUtils.d(BasicInfo.class,"modem version:"+modem_ver);

        return modem_ver;
    }

    public static String getFinger(){
        String finger = getProperty("ro.build.fingerprint","unknown");
        LogUtils.d(BasicInfo.class,"finger:"+finger);

        return finger;
    }

    public static String getMtkLogStatus(){
        String status = getProperty("debug.SkyLogger.Running","unknown");
        LogUtils.d(BasicInfo.class,"SkyLoggerStatus:"+status);

        return status;
    }

    public static void setMtkLogStatus(String status){
        setProperty("debug.SkyLogger.Running",status);
    }
    // 手机型号
    public static String getSystemModel() {
        LogUtils.d(BasicInfo.class,"system model:"+android.os.Build.MODEL);
        return android.os.Build.MODEL;
    }

    //手机厂商
    public static String getDeviceBrand() {
        LogUtils.d(BasicInfo.class,"system brand:"+android.os.Build.BRAND);
        return android.os.Build.BRAND;
    }

    //获取注册的plmn
    public static String[] getPLMNs() {
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(MainActivity.getContext(), Manifest.permission.READ_PHONE_STATE))
        {
            SubscriptionInfo sir = null;
            String nwopertor = null;
            String[] plmns = new String[MAX_CARD_NUM];
            int plmn_index = 0;
            SubscriptionManager subManager = SubscriptionManager.from(MainActivity.getContext());
            TelephonyManager telephonyManager = (TelephonyManager) MainActivity.getContext().getSystemService(MainActivity.getContext().TELEPHONY_SERVICE);
            if ((subManager != null) && (telephonyManager != null)) {
                List<SubscriptionInfo> subInfoList = subManager.getActiveSubscriptionInfoList();
                if (subInfoList != null) {
                    for (int index = 0; index < subInfoList.size(); index++) {
                        sir = subInfoList.get(index);
                        if (sir != null) {
                            try {
                                Method method = TelephonyManager.class.getDeclaredMethod("getNetworkOperator", int.class);
                                nwopertor = (String) method.invoke(telephonyManager, sir.getSubscriptionId());
                                if ((nwopertor != null)) {
                                    if(nwopertor.length() > 0) {
                                        plmns[plmn_index++] = nwopertor;
                                        LogUtils.d(BasicInfo.class,"plmn" + sir.getSimSlotIndex() + ":" + nwopertor);
                                    }
                                }else{
                                    LogUtils.e(BasicInfo.class,"nwopertor == null");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                LogUtils.e(BasicInfo.class,"get plmn exception : " + e.toString());
                            }
                        }
                    }
                    return plmns;
                } else {
                    LogUtils.e(BasicInfo.class,"subInfoList is null");
                    return null;
                }
            } else {
                LogUtils.e(BasicInfo.class,"subManager is null or telephonyManager is null");
                return null;
            }
        } else {
            LogUtils.e(BasicInfo.class,"no READ_PHONE_STATE permission");
            return null;
        }
    }

}

