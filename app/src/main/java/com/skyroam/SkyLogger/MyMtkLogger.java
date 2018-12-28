package com.skyroam.SkyLogger;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;

public class MyMtkLogger {
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

    private static void init(){
        mShouldStop = false;
        mModemSocket = new LocalSocket();
        mMobileSocket = new LocalSocket();
        mNetSocket = new LocalSocket();
        mModemAddress = new LocalSocketAddress("com.mediatek.mdlogger.socket1", LocalSocketAddress.Namespace.ABSTRACT);
        mMobileAddress = new LocalSocketAddress("mobilelogd", LocalSocketAddress.Namespace.ABSTRACT);
        mNetAddress = new LocalSocketAddress("netdiag", LocalSocketAddress.Namespace.ABSTRACT);
    }

    private static boolean connect() {
        if ((mModemSocket == null)||(mMobileSocket == null)||(mNetSocket == null)) {
            LogUtils.e(MyMtkLogger.class,"-->connect(), Socket = null, just return.");
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
            LogUtils.e(MyMtkLogger.class,"Communications error，Exception happens when connect to socket server："+ex.toString());
            return false;
        }

        mListenThread = new Thread() {
            public void run() {
                listen();
            }
        };
        mListenThread.start();
        LogUtils.i(MyMtkLogger.class,"Connect to native socket OK. And start local monitor thread now");
        return true;
    }
    private static void listen() {
        int count;
        byte[] buffer = new byte[BUFFER_SIZE];

        LogUtils.i(MyMtkLogger.class,"Monitor thread running");
        while (!mShouldStop/* true */) {
            for(int type = ModemLogType;type < MaxLogType;type++){
                if(read_data(type) == false){
                    stop();
                    break;
                }

            }
        }

        if (!mShouldStop) {
            LogUtils.e(MyMtkLogger.class,"listen break at address");
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
            LogUtils.e(MyMtkLogger.class,"wrong type:"+type);
            return false;
        }

        try {
            count = mInputStream.read(buffer, 0, BUFFER_SIZE);
        }catch(IOException ex){
            LogUtils.e(MyMtkLogger.class,"read failed:"+ex.toString());
            return false;
        }

        if (count < 0) {
            LogUtils.d(MyMtkLogger.class,"Get a empty response from native layer, stop listen.");
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
                LogUtils.d(MyMtkLogger.class,"Exception happended while closing modem socket: "+e.toString());
            }
            mModemSocket = null;
        }

        if (mMobileSocket != null) {
            try {
                mMobileSocket.shutdownInput();
                mMobileSocket.shutdownOutput();
                mMobileSocket.close();
            } catch (IOException e) {
                LogUtils.e(MyMtkLogger.class,"Exception happended while closing mobile socket: "+e.toString());
            }
            mMobileSocket = null;
        }

        if (mNetSocket != null) {
            try {
                mNetSocket.shutdownInput();
                mNetSocket.shutdownOutput();
                mNetSocket.close();
            } catch (IOException e) {
                LogUtils.e(MyMtkLogger.class,"Exception happended while closing net socket: "+e.toString());
            }
            mNetSocket = null;
        }
        mListenThread = null;
    }

    private static void dealWithResponse(int type,byte[] respBuffer) {
        if (respBuffer == null || respBuffer.length == 0) {
            if(type == ModemLogType) {
                LogUtils.d(MyMtkLogger.class,"Modem Get an empty response from native, ignore it.");
            }else if(type == MobileLogType){
                LogUtils.d(MyMtkLogger.class,"Mobile Get an empty response from native, ignore it.");
            }else if(type == 2){
                LogUtils.d(MyMtkLogger.class,"Net Get an empty response from native, ignore it.");
            }
            return;
        }

        if(type == ModemLogType) {
            LogUtils.d(MyMtkLogger.class,"-->Modem dealWithResponse(), resp=" + new String(respBuffer));
        }else if(type == MobileLogType){
            LogUtils.d(MyMtkLogger.class,"-->Mobile dealWithResponse(), resp=" + new String(respBuffer));
        }else if(type == NetLogType){
            LogUtils.d(MyMtkLogger.class,"-->Net dealWithResponse(), resp=" + new String(respBuffer));
        }
    }

    private static boolean sendCmd(int type,String cmd) {
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
        LogUtils.d(MyMtkLogger.class,"-->send cmd: [" + cmd + "] to [" + maddress.getName() + "]");
        boolean success = false;
        {
            if (mOutputStream == null) {
                LogUtils.d(MyMtkLogger.class,"No connection to daemon, outputstream is null.");
                stop();
            } else {
                StringBuilder builder = new StringBuilder(cmd);
                builder.append('\0');
                try {
                    mOutputStream.write(builder.toString().getBytes());
                    mOutputStream.flush();
                    success = true;
                } catch (IOException ex) {
                    LogUtils.d(MyMtkLogger.class,"IOException while sending command to native."+ex.toString());
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
        LogUtils.d(MyMtkLogger.class,"<--send cmd done : [" + cmd + "] to [" + maddress.getName() + "]");
        return success;
    }

    private static boolean isConnected(int type) {
        boolean isConnectedNow = false;
        if(type == ModemLogType){
            isConnectedNow = (mModemSocket != null && mModemSocket.isConnected());
        }else if(type == MobileLogType){
            isConnectedNow = (mMobileSocket != null && mMobileSocket.isConnected());
        }else if(type == NetLogType){
            isConnectedNow = (mNetSocket != null && mNetSocket.isConnected());
        }

        return isConnectedNow;
    }

    private static Boolean build_filter_config(Context context,String FilterName){
        if(FilterName == null)
            return true;

        String DstFolderPath = MyFileModule.GetRootPath()+"/mtklog/mdlog1_config/";
        File DstPathFile =new File(DstFolderPath);
        if(DstPathFile.mkdirs() == false) {
            LogUtils.e(MyMtkLogger.class,"create mtklog folder failed!");
            return false;
        }
        String DstFilePath =DstFolderPath+"filter_config";
        File filter_config = new File(DstFilePath);
        try {
            filter_config.createNewFile();
            OutputStream myOutput = new FileOutputStream(filter_config, false);
            OutputStreamWriter out = new OutputStreamWriter(myOutput);
            out.write(MyFileModule.GetRootPath()+FilterName+"\n");
            out.close();
        }catch(IOException e){
            LogUtils.e(MyMtkLogger.class,"create filter_config file failed!:"+e.toString());
            e.printStackTrace();

            return false;
        }

        return true;
    }

    private static String CalMD5(Context context, String strAssertFileName) {
        InputStream ims = null;
        String result = "";
        try {
           ims  = context.getAssets().open(strAssertFileName);
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024];
            int length;
            while ((length = ims.read(buffer)) != -1) {
                md5.update(buffer, 0, length);
            }
            byte[] bytes = md5.digest();

            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) {
                    temp = "0" + temp;
                }
                result += temp;
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = "";
        }finally {
            if(null!=ims){
                try {
                    ims.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    result = "";
                }
            }
        }
        return result;
    }

    //==========================================================================

    public static boolean CopyFilterFile(Context context,String SrcFilterName)
    {
        if(SrcFilterName == null)
            return true;

        File DstFilterFile = new File(MyFileModule.GetRootPath()+SrcFilterName);
        if(DstFilterFile.exists() == true) {
            String newMD5 = CalMD5(context,SrcFilterName);
            String oldMD5 = MyFileModule.GetFileMD5(SrcFilterName);
            if((newMD5 != null)&&(oldMD5 != null)){
                if(newMD5.equals(oldMD5)){
                    LogUtils.d(MyMtkLogger.class,"Filter file exist, and MD5值相同："+newMD5);
                    return true;
                }else{
                    LogUtils.i(MyMtkLogger.class,"Filter file exist, but MD5值不相同，"+"newMD5:"+newMD5+" oldMD5:"+oldMD5);
                }
            }else{
                LogUtils.d(MyMtkLogger.class,"计算MD5错误！");
            }
        }else{
            LogUtils.d(MyMtkLogger.class,"Filter file not exist, copy Filter file!");
        }

        try {
            if(DstFilterFile.exists()){
                DstFilterFile.delete();
            }
            DstFilterFile.createNewFile();
            InputStream myInput = context.getAssets().open(SrcFilterName);
            OutputStream myOutput = new FileOutputStream(DstFilterFile, true);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = myInput.read(buffer)) > 0) {
                myOutput.write(buffer, 0, length);
            }
            myOutput.flush();
            myOutput.close();
            myInput.close();
        }catch(IOException e){
            LogUtils.e(MyMtkLogger.class,"Copy Filter File failed!:"+e.toString());
            e.printStackTrace();

            return false;
        }

        return true;
    }

    public static boolean StartmMtkLog(Context context,String FilterName) {
        boolean result = false;
        init();
        if(connect() == true) {

            if(build_filter_config(context,FilterName) == false){
                LogUtils.e(MyMtkLogger.class,"build filter_config failed!");
            }

            String  pathCmd = "set_storage_path,"+MyFileModule.GetRootPath();

            sendCmd(MobileLogType,"autostart=1");
            sendCmd(ModemLogType,"setauto,2");
            sendCmd(MobileLogType,pathCmd);
            sendCmd(MobileLogType,"deep_start");
            sendCmd(NetLogType,pathCmd);
            sendCmd(NetLogType,"tcpdump_sdcard_start_600");
            sendCmd(ModemLogType,pathCmd);
            sendCmd(ModemLogType,"deep_start,2,2");

            result = true;
        }
        return result;
    }

    public static boolean StopMtkLog() {

        for(int type = ModemLogType;type < MaxLogType;type++){
            if(isConnected(type) == false){
                LogUtils.e(MyMtkLogger.class,"StopMtkLog:log connection not exist,type:"+type);
            }
        }

        sendCmd(MobileLogType,"autostart=0");
        sendCmd(ModemLogType,"setauto,0");
        sendCmd(NetLogType,"tcpdump_sdcard_stop_noping");
        sendCmd(ModemLogType,"deep_pause");
        sendCmd(MobileLogType,"deep_stop");

        stop();
        return true;
    }

}
