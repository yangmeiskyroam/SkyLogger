package com.skyroam.SkyLogger;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class MyLocation {
    //http://api.map.baidu.com/geocoder?output=json&location=39.913542,116.379763&ak=esNPFDwwsXWtsQfw4NMNmur1
    private final String BAIDU_GPS_PREFIX = "http://api.map.baidu.com/geocoder?output=json&location=";
    private final String BAIDU_GPS_SUFFIX = "&ak=esNPFDwwsXWtsQfw4NMNmur1";

    private LocationManager locationManager;
    private double Latitude;
    private double Longitude;
    private String detail_Location_string;
    public class CoarseLocation{
        public int type;//0：gsm  1：cdma 2：wcdma 3:lte
        public int lac,cid;
        public int mcc,mnc;
        public CoarseLocation(){
            this.type = 0xff;
        }
    }

    public MyLocation() {
        this.detail_Location_string = null;
        Latitude = Longitude = 0;
    }

    private void set_detail_location(String location) {
        detail_Location_string = location;
        LogUtils.i(MyLocation.class,"the detail location is :"+location);
    }

    private void set_fine_location(Location location) {
        Latitude = location.getLatitude();
        Longitude = location.getLongitude();
        LogUtils.i(MyLocation.class,"get fine location,Latitude:"+Latitude+" Longitude:"+Longitude);
    }

    // Gps 消息监听器
    private  LocationListener locationListener = new LocationListener() {

        // 位置发生改变后调用
        public void onLocationChanged(Location location) {
            if(location != null) {
                set_fine_location(location);
            }
        }
        // provider 被用户关闭后调用
        public void onProviderDisabled(String provider) {
            LogUtils.i(MyLocation.class,"onProviderDisabled");
        }

        // provider 被用户开启后调用
        public void onProviderEnabled(String provider) {
            LogUtils.i(MyLocation.class,"onProviderEnabled");
        }
        // provider 状态变化时调用
        public void onStatusChanged(String provider, int status,Bundle extras) {
            LogUtils.i(MyLocation.class,"onStatusChanged");
        }
    };

    private boolean check_openGPS() {
        // TODO Auto-generated method stub

        if (locationManager
                .isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                || locationManager
                .isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                ){
            return true;
        }

        return false;
    }

    private boolean check_fine_location_permission(){
        if (ContextCompat.checkSelfPermission(MainActivity.getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }else{
            return true;
        }
    }
    private String getProvider() {
        // TODO Auto-generated method stub
        // 构建位置查询条件
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setCostAllowed(true);
        criteria.setPowerRequirement(Criteria.POWER_LOW);

        return locationManager.getBestProvider(criteria, true);
    }

    private static void getJSONData(final Handler mHandler, final String urlPath) {

        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    URL url = new URL(urlPath);
                    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                    httpURLConnection.setReadTimeout(5000);
                    httpURLConnection.setRequestMethod("GET");
                    if (httpURLConnection.getResponseCode() == 200) {
                        InputStream inputStream = httpURLConnection.getInputStream();
                        InputStreamReader isr = new InputStreamReader(inputStream);
                        BufferedReader br = new BufferedReader(isr);
                        String temp = null;
                        StringBuffer jsonsb = new StringBuffer();
                        while ((temp = br.readLine()) != null) {
                            jsonsb.append(temp);
                        }
                        //return jsonsb;
                        Message message = new Message();
                        message.obj = jsonsb;
                        message.what = 0;
                        mHandler.sendMessage(message);
                    }
                } catch (MalformedURLException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }.start();
    }
    //创建http请求监听线程
    private Handler create_handle() {
        Handler mHandler = new Handler(MainActivity.getContext().getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch(msg.what)
                {
                    case 0:
                        StringBuffer jsonsb = (StringBuffer) msg.obj;
                        try {
                            parseAddressJSON(jsonsb);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        break;
                    default:
                        break;
                }
            }
        };
        return mHandler;
    }
    private void getCurrentAddressByGPS(final Handler mHandler, double latitude, double longitude) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(BAIDU_GPS_PREFIX).append(latitude).append(",")
                .append(longitude).append(BAIDU_GPS_SUFFIX);
        getJSONData(mHandler, stringBuffer.toString());
    }

    private boolean parseAddressJSON(StringBuffer sb) throws JSONException {
        try {
            if (sb != null) {
                JSONObject jsonAllData = new JSONObject(sb.toString());
                String resultStr = jsonAllData.getString("result");
                JSONObject resultArray = new JSONObject(resultStr);
                String jsonDataPlacemarkStr = resultArray.getString("formatted_address");
                set_detail_location(jsonDataPlacemarkStr);

                return true;
            }
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }

    //-----------------------------------------------------------------------------------------

    //获取cell信息
    public CoarseLocation get_coarse_location() {
        if(PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(MainActivity.getContext(), Manifest.permission.READ_PHONE_STATE)) {
            CoarseLocation location = new CoarseLocation();
            TelephonyManager telephonyManager = (TelephonyManager) MainActivity.getContext().getSystemService(MainActivity.getContext().TELEPHONY_SERVICE);
            List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
            if(cellInfoList != null){
                LogUtils.d(MyLocation.class,"cellInfoList size is:"+cellInfoList.size());
                for (CellInfo cellInfo : cellInfoList){
                    if (cellInfo instanceof CellInfoGsm){
                        CellInfoGsm gsmcell = (CellInfoGsm)cellInfo;
                        location.type = 0;
                        location.cid = gsmcell.getCellIdentity().getCid();
                        location.lac = gsmcell.getCellIdentity().getLac();
                        location.mcc = gsmcell.getCellIdentity().getMcc();
                        location.mnc = gsmcell.getCellIdentity().getMnc();
                        LogUtils.i(MyLocation.class,"gsm cellid:"+location.cid+"  lac:"+location.lac);
                        LogUtils.i(MyLocation.class,"gsm mcc:"+location.mcc+"  mnc:"+location.mnc);
                    }else if(cellInfo instanceof CellInfoCdma){
                        CellInfoCdma cdmacell = (CellInfoCdma)cellInfo;
                        location.type = 1;
                        location.mnc = cdmacell.getCellIdentity().getSystemId();
                        location.cid = cdmacell.getCellIdentity().getBasestationId();
                        location.lac = cdmacell.getCellIdentity().getNetworkId();
                        LogUtils.i(MyLocation.class,"cdma cid:"+location.cid+"  lac:"+location.lac);
                        LogUtils.i(MyLocation.class,"cdma mnc:"+location.mnc);
                    }else if(cellInfo instanceof CellInfoWcdma){
                        CellInfoWcdma wcdmacell = (CellInfoWcdma)cellInfo;
                        location.type = 2;
                        location.cid = wcdmacell.getCellIdentity().getCid();
                        location.lac = wcdmacell.getCellIdentity().getLac();
                        location.mcc = wcdmacell.getCellIdentity().getMcc();
                        location.mnc = wcdmacell.getCellIdentity().getMnc();
                        LogUtils.i(MyLocation.class,"wcdma cellid:"+location.cid+"  lac:"+location.lac);
                        LogUtils.i(MyLocation.class,"wcdma mcc:"+location.mcc+"  mnc:"+location.mnc);

                        return location;
                    }else if (cellInfo instanceof CellInfoLte){
                        CellInfoLte ltecell = (CellInfoLte)cellInfo;
                        location.type = 3;
                    }
                }
            }else{
                LogUtils.e(MyLocation.class,"cellInfoList is null");
            }
        } else{
            LogUtils.e(MyLocation.class,"no permission:READ_PHONE_STATE");
        }
        return null;
    }

    public boolean begin_fine_location() {
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(MainActivity.getContext(), Manifest.permission.ACCESS_FINE_LOCATION)) {
            // 获取 LocationManager 服务
            locationManager = (LocationManager) (LocationManager) MainActivity.getContext().getSystemService(Context.LOCATION_SERVICE);
            // 获取 Location Provider
            String provider = getProvider();
            if (provider != null) {
                LogUtils.i(MyLocation.class,"fine location provider: " + provider);
                if (check_openGPS() == true) {
                    LogUtils.i(MyLocation.class,"fine location already open GPS!");
                    //注册监听函数
                    locationManager.requestLocationUpdates(provider, 2000, (float) 0.1, locationListener);
                    return true;

                } else {
                    LogUtils.i(MyLocation.class,"fine locatin do not open GPS!");
                }
            } else {
                LogUtils.i(MyLocation.class,"fine locatin do not have provider!");
            }
        }
        else{
            LogUtils.i(MyLocation.class,"fine locatin do not have permission!");
        }

        return false;
    }

    //关闭定位
    public void end_fine_location() {
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
            locationManager = null;
        }
        if (locationListener != null) {
            locationListener = null;
        }
    }

    public double get_Latitude_result(){
        return Latitude;
    }

    public double get_Longitude_result(){
        return Longitude;
    }

    public void begin_detail_location(double latitude,double longitude){
        Handler mHandler = create_handle();
        if(mHandler != null){
            getCurrentAddressByGPS(mHandler,latitude,longitude);
        }
    }

    public String get_detail_location_result() {
        return detail_Location_string;
    }
}