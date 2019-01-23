package com.skyroam.SkyLogger;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.text.TextUtils;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

public class LoggingUtils {
    private static Thread mListenThread = null;
    private static LocalSocket mModemSocket = null;
    private static LocalSocket mMobileSocket = null;
    private static LocalSocket mNetSocket = null;
    private static LocalSocketAddress mModemAddress = null;
    private static LocalSocketAddress mMobileAddress = null;
    private static LocalSocketAddress mNetAddress = null;
    private static OutputStream mModemOutputStream = null;
    private static OutputStream mMobileOutputStream = null;
    private static OutputStream mNetOutputStream = null;
    private static InputStream mModemInputStream = null;
    private static InputStream mMobileInputStream = null;
    private static InputStream mNetInputStream = null;
    private static final int BUFFER_SIZE = 1024;
    private static boolean mShouldStop = false;

    private static final int ModemLogType = 0;
    private static final int MobileLogType = 1;
    private static final int NetLogType = 2;
    private static final int MaxLogType = 3;

    private static Timer logging_timer = null;
    private static int timer_count = 0;
    private static String mLogStartTime = null;
    private static String mLogEndTime = null;
    private static String mLogFileName = null;

    private static Callback mCallback = null;
    private static final String KEY_SkyLoggingMode = "SkyLoggingMode";
    private static final String KEY_BootAutoLogging = "BootAutoLogging";
    private static final String KEY_LoggingRunning = "LoggingRunning";
    private static final String KEY_LogStartTime = "LogStartTime";
    private static final String KEY_LoggingLocation = "LoggingLocation";
    private static final String KEY_FilterName = "FilterName";

    public static void setCallback(Callback callback){
        mCallback = callback;
    }

    public static interface Callback{
        public void getLoggingTime(int time);
        public void setFilterResult(boolean result);
    }

    public static String GetFileName(){
        return mLogFileName;
    }
    public static String GetLogTime(boolean start){
        if(start){
            return mLogStartTime;
        }else{
            return mLogEndTime;
        }
    }

    public static String getLoggingLocation(Context context){
        String location = "0.0"+" "+"0.0";
        location = (String)(SharedPreferencesUtils.GetValue(KEY_LoggingLocation,location,context));
        return location;
    }

    public static void setLoggingLocation(double longitude,double atitude,Context context){
        String location= Double.toString(longitude)+" "+Double.toString(atitude);
        SharedPreferencesUtils.SetValue(KEY_LoggingLocation,location,context);
    }

    public static void setLoggingMode(boolean SkyLoggingMode,Context context){
        SharedPreferencesUtils.SetValue(KEY_SkyLoggingMode,(boolean)SkyLoggingMode,context);
    }

    public static String getFilterName(){
        String filtername = "null";
        filtername = (String)SharedPreferencesUtils.GetValue(KEY_FilterName,filtername,MainActivity.getContext());
        return filtername;
    }
    public static void setBootAutoLogging(boolean autologging,Context context){
        SharedPreferencesUtils.SetValue(KEY_BootAutoLogging, (boolean) autologging, context);
        if(autologging){
            LogUtils.i(LoggingUtils.class,"set boot auto logging status:true");
        }else{
            LogUtils.i(LoggingUtils.class,"set boot auto logging status:false");
        }
    }

    public static boolean isSkyLoggingMode(Context context){
        boolean flag = true;
        flag = (boolean)(SharedPreferencesUtils.GetValue(KEY_SkyLoggingMode,(boolean)flag,context));

        return flag;
    }

    public static boolean isBootAutoLogging(Context context){
        boolean autologging = false;
        autologging = (boolean)(SharedPreferencesUtils.GetValue(KEY_BootAutoLogging, (boolean) autologging, context));
        if(autologging){
            LogUtils.i(LoggingUtils.class,"is boot auto logging");
        }else {
            LogUtils.i(LoggingUtils.class,"is not boot auto logging");
        }

        return autologging;
    }
    public static boolean LoggingRunnig(Context context){
        boolean running = false;
        running = (boolean)(SharedPreferencesUtils.GetValue(KEY_LoggingRunning,(boolean)false,context));
        if(running){
            LogUtils.i(LoggingUtils.class,"Logging is running!");
        }else{
            LogUtils.i(LoggingUtils.class,"Logging is not running!");
        }
        return running;
    }

    public static boolean LoggingConnect(Context context) {
        if(isConnected() == false){
            if(connectSocket() == false){
                LogUtils.e(LoggingUtils.class,"logging connect sockett failed!");
                return false;
            }

            long startTime = (long)SharedPreferencesUtils.GetValue(KEY_LogStartTime,(long)0,context);
            long currentTime = System.currentTimeMillis();
            LogUtils.d(LogUtils.class,"startTime:"+startTime+" currentTime:"+currentTime);
            int initialTime = (int)((currentTime - startTime)/1000);
            start_timer(initialTime,context);
        }

        return true;
    }

    public static boolean StartLogging(Context context) {
        if(isConnected() == true){
            LogUtils.e(LoggingUtils.class,"StartLogging falied,connection already exist!");
            return false;
        }
        if(connectSocket() == false){
            LogUtils.e(LoggingUtils.class,"StartLogging falied,socket connect failed!");
            return false;
        }

        if(isSkyLoggingMode(context)){

            InitXMLAndFilter(context);

            String filter_name = select_filter(context);
            if(filter_name == null){
                setFilterName("null",context);
                mCallback.setFilterResult(false);
            }else{
                setFilterName(filter_name,context);
            }

            if(build_filter_config(context,filter_name) == false){
                LogUtils.e(LoggingUtils.class,"StartLogging falied,build filter_config failed!");
                return false;
            }
        }

        SendCmd(true);
        start_timer(0,context);

        LogUtils.i(LoggingUtils.class,"StartLogging success!");
        return true;
    }

    public static boolean StopLogging(final Context context) {
        if(isConnected() == false){
            LogUtils.e(LoggingUtils.class,"StopLogging failed,connection not exist!");
            return false;
        }


        SendCmd(false);
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    Thread.sleep(1000);

                    ArrayList<String> fileArray = FileUtils.GetFileArray(false);
                    String filename = null;
                    if(fileArray != null){
                        for (int index = 0; index < fileArray.size(); index++) {
                            filename = fileArray.get(index);
                            if(filename.equals("mtklog")){
                                FileUtils.DeleteFile0rDir(filename, true);

                                mLogEndTime = buildLogTime();
                                mLogFileName = buildLogFileName(mLogEndTime,context);
                                FileUtils.MoveFileOrDir(filename,mLogFileName);

                                setLoggingMode(true,context);
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    LogUtils.i(MainActivity.class,"StopLogging failed,Exception reason:"+e.toString());
                }

                stop_timer(context);
            }
        }.start();

        LogUtils.i(LoggingUtils.class,"StopLogging success!");
        return true;
    }

    private static void initSocket() {
        mShouldStop = false;
        mModemSocket = new LocalSocket();
        mMobileSocket = new LocalSocket();
        mNetSocket = new LocalSocket();
        mModemAddress = new LocalSocketAddress("com.mediatek.mdlogger.socket1", LocalSocketAddress.Namespace.ABSTRACT);
        mMobileAddress = new LocalSocketAddress("mobilelogd", LocalSocketAddress.Namespace.ABSTRACT);
        mNetAddress = new LocalSocketAddress("netdiag", LocalSocketAddress.Namespace.ABSTRACT);
    }

    private static boolean connectSocket() {
        initSocket();
        if ((mModemSocket == null)||(mMobileSocket == null)||(mNetSocket == null)) {
            LogUtils.e(LoggingUtils.class,"-->connect(), Socket = null, just return.");
            return false;
        }
        try {
            mModemSocket.connect(mModemAddress);
            mModemOutputStream = mModemSocket.getOutputStream();
            mModemInputStream = mModemSocket.getInputStream();

            mMobileSocket.connect(mMobileAddress);
            mMobileOutputStream = mMobileSocket.getOutputStream();
            mMobileInputStream = mMobileSocket.getInputStream();

            mNetSocket.connect(mNetAddress);
            mNetOutputStream = mNetSocket.getOutputStream();
            mNetInputStream = mNetSocket.getInputStream();
        } catch (IOException ex) {
            LogUtils.e(LoggingUtils.class,"Communications error，Exception happens when connect to socket server："+ex.toString());
            return false;
        }

        mListenThread = new Thread() {
            public void run() {
                listen();
            }
        };
        mListenThread.start();
        LogUtils.i(LoggingUtils.class,"Connect to native socket OK. And start local monitor thread now");
        return true;
    }

    private static void SendCmd(boolean start) {
        if(start){
            String  pathCmd = "set_storage_path,"+FileUtils.GetRootPath();
            sendCmdtoSocket(MobileLogType,"autostart=1");
            sendCmdtoSocket(ModemLogType,"setauto,2");
            sendCmdtoSocket(MobileLogType,pathCmd);
            sendCmdtoSocket(MobileLogType,"deep_start");
            sendCmdtoSocket(NetLogType,pathCmd);
            sendCmdtoSocket(NetLogType,"tcpdump_sdcard_start_600");
            sendCmdtoSocket(ModemLogType,pathCmd);
            sendCmdtoSocket(ModemLogType,"deep_start,2,2");

        }else{
            sendCmdtoSocket(MobileLogType,"autostart=0");
            sendCmdtoSocket(ModemLogType,"setauto,0");
            sendCmdtoSocket(NetLogType,"tcpdump_sdcard_stop_noping");
            sendCmdtoSocket(ModemLogType,"deep_pause");
            sendCmdtoSocket(MobileLogType,"deep_stop");

            stop();
        }
    }
    private static void listen() {
        int count;
        byte[] buffer = new byte[BUFFER_SIZE];

        while (!mShouldStop/* true */) {
            for(int type = ModemLogType;type < MaxLogType;type++){
                if(read_data(type) == false){
                    stop();
                    break;
                }
            }
        }

        if (!mShouldStop) {
            LogUtils.e(LoggingUtils.class,"listen break at address");
        }
        return;
    }

    private static boolean read_data(int type){
        int count;
        byte[] buffer = new byte[BUFFER_SIZE];
        InputStream mInputStream = null;
        if(type == ModemLogType) {
            mInputStream = mModemInputStream;
        }else if(type == MobileLogType){
            mInputStream = mMobileInputStream;
        }else if(type == NetLogType){
            mInputStream = mNetInputStream;
        }else{
            count = 0;
            LogUtils.e(LoggingUtils.class,"wrong type:"+type);
            return false;
        }

        try {
            count = mInputStream.read(buffer, 0, BUFFER_SIZE);
        }catch(IOException ex){
            LogUtils.e(LoggingUtils.class,"read failed:"+ex.toString());
            return false;
        }

        if (count < 0) {
            LogUtils.d(LoggingUtils.class,"Get a empty response from native layer, stop listen.");
            return false;
        }
        byte[] resp = new byte[count];
        System.arraycopy(buffer, 0, resp, 0, count);
        dealWithResponse(type,resp);
        return true;
    }

    private static void stop() {
        mShouldStop = true;
        if (mModemSocket != null) {
            try {
                mModemSocket.shutdownInput();
                mModemSocket.shutdownOutput();
                mModemSocket.close();
            } catch (IOException e) {
                LogUtils.d(LoggingUtils.class,"Exception happended while closing modem socket: "+e.toString());
            }
            mModemSocket = null;
        }

        if (mMobileSocket != null) {
            try {
                mMobileSocket.shutdownInput();
                mMobileSocket.shutdownOutput();
                mMobileSocket.close();
            } catch (IOException e) {
                LogUtils.e(LoggingUtils.class,"Exception happended while closing mobile socket: "+e.toString());
            }
            mMobileSocket = null;
        }

        if(mNetSocket != null) {
            try {
                mNetSocket.shutdownInput();
                mNetSocket.shutdownOutput();
                mNetSocket.close();
            } catch (IOException e) {
                LogUtils.e(LoggingUtils.class,"Exception happended while closing net socket: "+e.toString());
            }
            mNetSocket = null;
        }
        mListenThread = null;
    }

    private static void dealWithResponse(int type,byte[] respBuffer) {
        if (respBuffer == null || respBuffer.length == 0) {
            if(type == ModemLogType) {
                LogUtils.d(LoggingUtils.class,"Modem Get an empty response from native, ignore it.");
            }else if(type == MobileLogType){
                LogUtils.d(LoggingUtils.class,"Mobile Get an empty response from native, ignore it.");
            }else if(type == 2){
                LogUtils.d(LoggingUtils.class,"Net Get an empty response from native, ignore it.");
            }
            return;
        }

        if(type == ModemLogType) {
            LogUtils.d(LoggingUtils.class,"-->Modem dealWithResponse(), resp=" + new String(respBuffer));
        }else if(type == MobileLogType){
            LogUtils.d(LoggingUtils.class,"-->Mobile dealWithResponse(), resp=" + new String(respBuffer));
        }else if(type == NetLogType){
            LogUtils.d(LoggingUtils.class,"-->Net dealWithResponse(), resp=" + new String(respBuffer));
        }
    }

    private static boolean sendCmdtoSocket(int type,String cmd) {
        LocalSocketAddress maddress = null;
        OutputStream mOutputStream = null;
        if(type == ModemLogType){
            maddress = mModemAddress;
            mOutputStream = mModemOutputStream;
        }else if(type == MobileLogType){
            maddress = mMobileAddress;
            mOutputStream = mMobileOutputStream;
        }else if(type == NetLogType){
            maddress = mNetAddress;
            mOutputStream = mNetOutputStream;
        }else{
            return false;
        }
        LogUtils.d(LoggingUtils.class,"-->send cmd: [" + cmd + "] to [" + maddress.getName() + "]");
        boolean success = false;
        {
            if (mOutputStream == null) {
                LogUtils.d(LoggingUtils.class,"No connection to daemon, outputstream is null.");
                stop();
            } else {
                StringBuilder builder = new StringBuilder(cmd);
                builder.append('\0');
                try {
                    mOutputStream.write(builder.toString().getBytes());
                    mOutputStream.flush();
                    success = true;
                } catch (IOException ex) {
                    LogUtils.d(LoggingUtils.class,"IOException while sending command to native."+ex.toString());
                    if(type == ModemLogType){
                        mModemOutputStream = null;
                    }else if(type == MobileLogType){
                        mMobileOutputStream = null;
                    }else if(type == NetLogType){
                        mNetOutputStream = null;
                    }
                    stop();
                    return false;
                }
            }
        }
        LogUtils.d(LoggingUtils.class,"<--send cmd done : [" + cmd + "] to [" + maddress.getName() + "]");
        return success;
    }

    private static boolean isConnected() {

        if((mModemSocket == null) || (!mModemSocket.isConnected())){
            return false;
        }
        if((mMobileSocket == null) || (!mMobileSocket.isConnected())){
            return false;
        }
        if((mNetSocket == null) || (!mNetSocket.isConnected())){
            return false;
        }
        return true;
    }

    private static Boolean build_filter_config(Context context, String FilterName){
        if(FilterName == null)
            return true;

        String DstFolderPath = FileUtils.GetRootPath()+"/mtklog/mdlog1_config/";
        File DstPathFile =new File(DstFolderPath);
        if(!DstPathFile.exists()){
            if(DstPathFile.mkdirs() == false) {
                LogUtils.e(LoggingUtils.class,"create mtklog folder failed!");
                return false;
            }
        }

        String DstFilePath =DstFolderPath+"filter_config";
        File filter_config = new File(DstFilePath);
        try {
            filter_config.createNewFile();
            OutputStream myOutput = new FileOutputStream(filter_config, false);
            OutputStreamWriter out = new OutputStreamWriter(myOutput);
            out.write(FileUtils.GetRootPath()+"filter/bin/"+FilterName+"\n");
            out.close();
        }catch(IOException e){
            LogUtils.e(LoggingUtils.class,"create filter_config file failed!:"+e.toString());
            e.printStackTrace();

            return false;
        }

        return true;
    }

    private static String select_filter(Context context){
        String filter_name = parseSDXMLFilter(DeviceInfoUtils.getFinger(),context);
        if(filter_name == null){
            LogUtils.e(LoggingUtils.class,"xml not exist or not get filter name,finger:"+DeviceInfoUtils.getFinger());

        }else{
            LogUtils.i(LoggingUtils.class,"filter name is:"+filter_name);
            File file = new File(FileUtils.GetRootPath()+"filter/bin/"+filter_name);
            if(!file.exists()){
                LogUtils.i(LoggingUtils.class,"filter not exist!");
                return null;
            }
        }

        return filter_name;
    }

    private static void InitXMLAndFilter(Context context){
        if(Copyxml(context)){
            String filter_name = parseSDXMLFilter(DeviceInfoUtils.getFinger(),context);
            if(filter_name != null){
                CopyFilter(context,filter_name);
            }
        }
    }

    private static boolean Copyxml(Context context){
        String xmlFile = "filter.xml";
        File fileDir = new File(FileUtils.GetRootPath()+"filter/");
        if (!fileDir.exists()) {
            if(!fileDir.mkdirs()){
                LogUtils.e(LoggingUtils.class,"mkdirs filter/ failed!");
                return false;
            }
        }

        File DstFile = new File(FileUtils.GetRootPath()+"filter/"+xmlFile);
        if(!DstFile.exists()){
            try {
                InputStream myInput = context.getAssets().open(xmlFile);
                DstFile.createNewFile();
                OutputStream myOutput = new FileOutputStream(DstFile, true);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = myInput.read(buffer)) > 0) {
                    myOutput.write(buffer, 0, length);
                }
                myOutput.flush();
                myOutput.close();
                myInput.close();

                return true;
            } catch (IOException e) {
                LogUtils.e(LoggingUtils.class,"Copy filter.xml failed! reason:"+e.toString());
                return false;
            }
        }else{
            String assetverno = parseAssetXMLVerno(context);
            String sdverno = parseSDXMLVerno(context);
            if((assetverno == null) || (sdverno == null)){
                return false;
            }else{
                LogUtils.i(LoggingUtils.class,"sdcard XML verno:"+sdverno+" asset XML verno:"+assetverno);
                int int_sdverno = Integer.valueOf(sdverno).intValue();
                int int_assetverno = Integer.valueOf(assetverno).intValue();
                if(int_sdverno>=int_assetverno){
                    return true;
                }else{
                    try {
                        DstFile.delete();
                        InputStream myInput = context.getAssets().open(xmlFile);
                        DstFile.createNewFile();
                        OutputStream myOutput = new FileOutputStream(DstFile, true);
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = myInput.read(buffer)) > 0) {
                            myOutput.write(buffer, 0, length);
                        }
                        myOutput.flush();
                        myOutput.close();
                        myInput.close();

                        return true;
                    } catch (IOException e) {
                        LogUtils.e(LoggingUtils.class,"Copy filter.xml failed! reason:"+e.toString());
                        return false;
                    }
                }
            }
        }
    }

    private static boolean CopyFilter(Context context,String filter){
        File fileDir = new File(FileUtils.GetRootPath()+"filter/bin/");
        if (!fileDir.exists()) {
            if(!fileDir.mkdirs()){
                LogUtils.e(LoggingUtils.class,"mkdirs filter/bin/ failed!");
                return false;
            }
        }

        File DstFile = new File(FileUtils.GetRootPath()+"filter/bin/"+filter);
        if(!DstFile.exists()){
            try {
                InputStream myInput = context.getAssets().open(filter);
                DstFile.createNewFile();
                OutputStream myOutput = new FileOutputStream(DstFile, true);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = myInput.read(buffer)) > 0) {
                    myOutput.write(buffer, 0, length);
                }
                myOutput.flush();
                myOutput.close();
                myInput.close();
            } catch (IOException e) {
                LogUtils.e(LoggingUtils.class,"Copy filter file "+filter+"failed! reason:"+e.toString());
                return false;
            }
        }

        return true;
    }

    private static void start_timer(int initial,Context context){
        if(logging_timer != null){
            logging_timer.cancel();
            logging_timer = null;
        }

        if(initial == 0){
            long currentTime = System.currentTimeMillis();
            SharedPreferencesUtils.SetValue(KEY_LogStartTime,(long)currentTime,context);
            SharedPreferencesUtils.SetValue(KEY_LoggingRunning,(boolean)true,context);
        }
        timer_count = initial;
        mLogFileName = null;
        logging_timer = new Timer();
        logging_timer.schedule(new TimerTask() {
            @Override public void run() {
                timer_count++;
                if(mCallback != null) {
                    mCallback.getLoggingTime(timer_count);
                }

            } },1,1000);
    }

    private static void stop_timer(Context context){
        if(logging_timer != null) {
            logging_timer.cancel();
            logging_timer = null;
        }

        if(mCallback != null){
            mCallback.getLoggingTime(0);
        }

        SimpleDateFormat dateformat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        long startTime = (long)SharedPreferencesUtils.GetValue(KEY_LogStartTime,(long)0,context);
        mLogStartTime = dateformat.format(startTime);
        SharedPreferencesUtils.Remove(KEY_LogStartTime,context);
        SharedPreferencesUtils.Remove(KEY_LoggingRunning,context);
    }

    private static String buildLogTime(){
        Calendar calendar = Calendar.getInstance();
        String timestamp = String.format("%04d%02d%02d_%02d%02d%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH)+1,
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                calendar.get(Calendar.SECOND));

        return timestamp;
    }

    private static String buildLogFileName(String timestamp,Context context){
        if(isSkyLoggingMode(context)) {
            if(getFilterName().equals("null")){
                return "MtkLog_" + timestamp + "/";
            }else{
                return "SkyLog_" + timestamp + "/";
            }
        }else{
            return "MtkLog_" + timestamp + "/";
        }
    }

    private static void setFilterName(String filtername,Context context){
        SharedPreferencesUtils.SetValue(KEY_FilterName,(String)filtername,context);
    }

    private static String parseAssetXMLVerno(Context context){
        boolean flag = false;
        String verno = null;
        InputStream fis = null;
        try {
            fis = context.getAssets().open("filter.xml");
            // 获得pull解析器对象
            XmlPullParser parser = Xml.newPullParser();
            // 指定解析的文件和编码格式
            parser.setInput(fis, "utf-8");

            int eventType = parser.getEventType(); // 获得事件类型

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName(); // 获得当前节点的名称

                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("filters".equals(tagName)) {
                            verno = parser.getAttributeValue(null, "verno");
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if ("filters".equals(tagName)) {
                            flag = true;
                        }
                        break;
                    default:
                        break;
                }
                if(flag){
                    break;
                }else{
                    eventType = parser.next(); // 获得下一个事件类型
                }

            }
        } catch (Exception e) {
            LogUtils.e(LoggingUtils.class,"parse asset xml verno failed,reason:"+e.toString());
            return null;
        } finally {
            if(fis != null){
                try {
                    fis.close();
                } catch (IOException e) {
                    LogUtils.e(LoggingUtils.class,"parse asset xml verno FileInputStream close failed,reason:"+e.toString());
                    return null;
                }
            }

            return verno;
        }
    }

    private static String parseSDXMLVerno(Context context){
        boolean flag = false;
        String verno = null;
        FileInputStream fis=null;
        try {
            File file = new File(FileUtils.GetRootPath()+"filter/filter.xml");
            if(!file.exists()){
                return null;
            }
            fis = new FileInputStream(file);

            // 获得pull解析器对象
            XmlPullParser parser = Xml.newPullParser();
            // 指定解析的文件和编码格式
            parser.setInput(fis, "utf-8");

            int eventType = parser.getEventType(); // 获得事件类型

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName(); // 获得当前节点的名称

                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("filters".equals(tagName)) {
                            verno = parser.getAttributeValue(null, "verno");
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if ("filters".equals(tagName)) {
                            flag = true;
                        }
                        break;
                    default:
                        break;
                }
                if(flag){
                    break;
                }else{
                    eventType = parser.next(); // 获得下一个事件类型
                }

            }
        } catch (Exception e) {
            LogUtils.e(LoggingUtils.class,"parse sdcard xml verno failed,reason:"+e.toString());
            return null;
        } finally {
            if(fis != null){
                try {
                    fis.close();
                } catch (IOException e) {
                    LogUtils.e(LoggingUtils.class,"parse sdcard xml verno FileInputStream close failed,reason:"+e.toString());
                    return null;
                }
            }

            return verno;
        }
    }

    private static String parseSDXMLFilter(String finger,Context context){
        boolean flag = false;
        String parse_id = null;
        String parse_finger = null;
        String parse_filter = null;
        FileInputStream fis=null;
        try {
            File file = new File(FileUtils.GetRootPath()+"filter/filter.xml");
            if(!file.exists()){
                return null;
            }
            fis = new FileInputStream(file);

            // 获得pull解析器对象
            XmlPullParser parser = Xml.newPullParser();
            // 指定解析的文件和编码格式
            parser.setInput(fis, "utf-8");

            int eventType = parser.getEventType(); // 获得事件类型

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName(); // 获得当前节点的名称

                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("filter".equals(tagName)) {
                            parse_id = parser.getAttributeValue(null, "id");
                        } else if ("finger".equals(tagName)) {
                            parse_finger = parser.nextText();
                        } else if ("file".equals(tagName)) {
                            parse_filter = parser.nextText();
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if ("filter".equals(tagName)) {
                            if(parse_finger.equals(finger)){
                                flag = true;
                            }else{
                                parse_id = null;
                                parse_finger = null;
                                parse_filter = null;
                            }

                        }
                        break;
                    default:
                        break;
                }
                if(flag){
                    break;
                }else{
                    eventType = parser.next(); // 获得下一个事件类型
                }

            }
        } catch (Exception e) {
            LogUtils.e(LoggingUtils.class,"parse finger failed,reason:"+e.toString());
            return null;
        } finally {
            if(fis != null){
                try {
                    fis.close();
                } catch (IOException e) {
                    LogUtils.e(LoggingUtils.class,"FileInputStream close failed,reason:"+e.toString());
                    return null;
                }
            }

            return parse_filter;
        }
    }
}
