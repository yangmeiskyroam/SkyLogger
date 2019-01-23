package com.skyroam.SkyLogger;

import android.os.Environment;

import com.alibaba.sdk.android.oss.ClientException;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.ServiceException;
import com.alibaba.sdk.android.oss.callback.OSSCompletedCallback;
import com.alibaba.sdk.android.oss.callback.OSSProgressCallback;
import com.alibaba.sdk.android.oss.common.OSSConstants;
import com.alibaba.sdk.android.oss.common.auth.OSSAuthCredentialsProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationToken;
import com.alibaba.sdk.android.oss.common.utils.IOUtils;
import com.alibaba.sdk.android.oss.internal.OSSAsyncTask;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;
import com.alibaba.sdk.android.oss.model.PutObjectResult;
import com.alibaba.sdk.android.oss.model.ResumableUploadRequest;
import com.alibaba.sdk.android.oss.model.ResumableUploadResult;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import static com.skyroam.SkyLogger.OssUtils.UploadStatus.UploadSucess;
import static com.skyroam.SkyLogger.OssUtils.UploadStatus.UploadFaiure;
import static com.skyroam.SkyLogger.OssUtils.UploadStatus.Uploading;
import static com.skyroam.SkyLogger.OssUtils.UploadStatus.ZipDone;
import static com.skyroam.SkyLogger.OssUtils.UploadStatus.Ziping;

public class OssUtils {

    private static Callback mCallback = null;

    public static void setCallback(Callback callback){
        mCallback = callback;
    }

    public static interface Callback{
        void getUploadProcess(int status,String process);
    }

    public class UploadStatus{
        public static final int Ziping = 0;
        public static final int ZipDone = 1;
        public static final int Uploading = 2;
        public static final int UploadSucess = 3;
        public static final int UploadFaiure = 4;
    }

    public static void UploadFiles(final ArrayList<String> UploadlistStr){
        new Thread() {
            @Override
            public void run() {
                super.run();
                if(UploadlistStr.size()>0) {
                    if(mCallback != null)
                        mCallback.getUploadProcess(Ziping,null);
                    ArrayList<String> gzfileArray = new ArrayList<String>();
                    for (int index = 0; index < UploadlistStr.size(); index++) {
                        String srcName = UploadlistStr.get(index);
                        if(srcName.indexOf(".gz")>-1){
                            LogUtils.d(OssUtils.class,"gz文件，不需要压缩："+srcName);
                            gzfileArray.add(srcName);
                        }else{
                            String UploadFileName = buildZipFileName(srcName);
                            boolean result = FileUtils.ZipFiles(FileUtils.GetRootPath()+srcName,FileUtils.GetRootPath()+UploadFileName);
                            if(result == true){
                                LogUtils.i(OssUtils.class,"压缩文件"+srcName+"成功！");
                                gzfileArray.add(UploadFileName);
                            }else{
                                LogUtils.e(OssUtils.class,"压缩文件"+srcName+"失败！");
                            }
                        }
                    }

                    if(mCallback != null)
                        mCallback.getUploadProcess(ZipDone,null);
                    if(gzfileArray.size() > 0){
                        //计算所有要上传文件的大小
                        long totSize = 0;
                        for(int index = 0;index<gzfileArray.size();index++){
                            totSize += FileUtils.GetFileSize(gzfileArray.get(index));
                        }
                        //创建线程开始上传
                        InitUploadStatus(gzfileArray.size(),totSize);
                        for(int index = 0;index<gzfileArray.size();index++){
                            Thread thread = new MyThread(gzfileArray.get(index));
                            thread.start();
                        }
                    }
                }else{
                    LogUtils.e(OssUtils.class,"未选中要上传的文件！");
                }
            }
        }.start();
    }

    private static Map<String, Long> UploadFileAttr = new HashMap();
    private static long UploadProgress = 0;
    private static long TotalUploadSize = 0;
    private static long AllFileTotalSize = 0;
    private static int TotFileNum = 0;
    private static int SuccessFileNum = 0;
    private static int FaileFileNum = 0;
    private static OSSAsyncTask UploadTask;

    private static String buildObjectKey(String FileName){
        String devicebrand = DeviceInfoUtils.getDeviceBrand();
        String systemmodel = DeviceInfoUtils.getSystemModel();
        String sn = DeviceInfoUtils.getSN();
        Calendar calendar = Calendar.getInstance();
        String timestamp = String.format("%04d_%02d_%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH)+1,
                calendar.get(Calendar.DAY_OF_MONTH));

        String objectKey = devicebrand+"/"+systemmodel+"/"+sn+"/"+timestamp+"/"+FileName;

        return objectKey;
    }

    private synchronized static void handleProgressCallback(String FileName, long currentSize, long totalSize){
        LogUtils.d(OssUtils.class,"currentSize: " + currentSize + " totalSize: " + totalSize);
        TotalUploadSize += currentSize - UploadFileAttr.get(FileName);
        UploadFileAttr.put(FileName, currentSize);
        long process = (TotalUploadSize*100)/AllFileTotalSize;
        LogUtils.d(OssUtils.class,"已上传大小："+TotalUploadSize
                +" 总大小："+AllFileTotalSize+" persent: "+(int)process+"%");
        if (UploadProgress != process){
            UploadProgress = process;

            String ResultString = String.format("%d,%d,%d,%d",process,SuccessFileNum,FaileFileNum,TotFileNum-SuccessFileNum-FaileFileNum);
            if(mCallback != null)
                mCallback.getUploadProcess(Uploading,ResultString);
        }
    }

    private synchronized static void handleSuccessCallback(String FileName){
        LogUtils.i(OssUtils.class,"文件"+FileName+"上传成功!");
        SuccessFileNum++;
        LogUtils.i(OssUtils.class,"SuccessFileNum:"+SuccessFileNum+" filename"+FileName);
        LogUtils.i(OssUtils.class,"FaileFileNum:"+FaileFileNum+" filename"+FileName);
        LogUtils.i(OssUtils.class,"uploadFileNum:"+(TotFileNum-SuccessFileNum-FaileFileNum)+" filename"+FileName);
        String ResultString = String.format("%d,%d,%d,",SuccessFileNum,FaileFileNum,TotFileNum-SuccessFileNum-FaileFileNum)+FileName;
        if(mCallback != null)
            mCallback.getUploadProcess(UploadSucess,ResultString);
    }

    private synchronized static void handleFaileCallback(String FileName) {
        LogUtils.e(OssUtils.class,"文件"+FileName+"上传失败!");
        FaileFileNum++;
        String ResultString = String.format("%d,%d,%d,",SuccessFileNum,FaileFileNum,TotFileNum-SuccessFileNum-FaileFileNum)+FileName;
        if(mCallback != null)
            mCallback.getUploadProcess(UploadFaiure,ResultString);
    }

    private static void InitUploadStatus(int totFilesCount,long totFilesSize) {
        UploadFileAttr.clear();
        UploadProgress = 0;
        TotalUploadSize = 0;
        AllFileTotalSize =totFilesSize;
        TotFileNum = totFilesCount;
        SuccessFileNum = 0;
        FaileFileNum = 0;
    }

    public static void UploadCancle(){
        UploadTask.cancel();
    }
    private static void ResumableUpload(final String FileName) {
        String endpoint = "http://oss-cn-hangzhou.aliyuncs.com";
        String stsServer = "http://47.91.198.137:65534/sts/getsts";
        OSSCredentialProvider credentialProvider = new OSSAuthCredentialsProvider(stsServer);

        OSS oss = new OSSClient(MainActivity.getContext(), endpoint, credentialProvider);
        String recordDirectory = Environment.getExternalStorageDirectory().getAbsolutePath() + "/oss_record/";
        File recordDir = new File(recordDirectory);
        if (!recordDir.exists()) {
            recordDir.mkdirs();
        }

        UploadFileAttr.put(FileName, 0L);
        String objectKey = buildObjectKey(FileName);
        final String filepath = FileUtils.GetRootPath() + FileName;

        ResumableUploadRequest request = new ResumableUploadRequest("skyroam-terminal-log", objectKey, filepath, recordDirectory);
        request.setDeleteUploadOnCancelling(false);

        request.setProgressCallback(new OSSProgressCallback<ResumableUploadRequest>() {
            @Override
            public void onProgress(ResumableUploadRequest request, long currentSize, long totalSize) {
                handleProgressCallback(FileName,currentSize,totalSize);
            }
        });

        UploadTask = oss.asyncResumableUpload(request, new OSSCompletedCallback<ResumableUploadRequest, ResumableUploadResult>() {
            @Override
            public void onSuccess(ResumableUploadRequest request, ResumableUploadResult result) {
                handleSuccessCallback(FileName);
            }

            @Override
            public void onFailure(ResumableUploadRequest request, ClientException clientExcepion, ServiceException serviceException) {
                // 异常处理
                if (clientExcepion != null) {
                    LogUtils.e(OssUtils.class,"clientExcepion:"+clientExcepion.getMessage());
                }

                handleFaileCallback(FileName);
            }
        });

    }

    private static void SimpleUpload(final String FileName) {
        String endpoint = "http://oss-cn-hangzhou.aliyuncs.com";
        OSSCredentialProvider credetialProvider = new OSSFederationCredentialProvider() {
            @Override
            public OSSFederationToken getFederationToken() {
                try {
                    URL stsUrl = new URL("http://47.91.198.137:65534/sts/getsts");
                    HttpURLConnection conn = (HttpURLConnection) stsUrl.openConnection();
                    InputStream input = conn.getInputStream();
                    String jsonText = IOUtils.readStreamAsString(input, OSSConstants.DEFAULT_CHARSET_NAME);
                    JSONObject jsonObjs = new JSONObject(jsonText);
                    String ak = jsonObjs.getString("AccessKeyId");
                    String sk = jsonObjs.getString("AccessKeySecret");
                    String token = jsonObjs.getString("SecurityToken");
                    String expiration = jsonObjs.getString("Expiration");
                    return new OSSFederationToken(ak, sk, token, expiration);
                } catch (Exception e) {
                    e.printStackTrace();
                    LogUtils.e(OssUtils.class,"get token failed:"+e.toString());
                }
                return null;
            }
        };

        OSS oss = new OSSClient(MainActivity.getContext(), endpoint, credetialProvider);

        UploadFileAttr.put(FileName, 0L);
        String objectKey = buildObjectKey(FileName);
        final String filepath = FileUtils.GetRootPath() + FileName;

        // 构造上传请求
        PutObjectRequest put = new PutObjectRequest("skyroam-terminal-log", objectKey, filepath);

        // 异步上传时可以设置进度回调
        put.setProgressCallback(new OSSProgressCallback<PutObjectRequest>() {
            @Override
            public void onProgress(PutObjectRequest request, long currentSize, long totalSize) {
                handleProgressCallback(FileName,currentSize,totalSize);
            }
        });

        UploadTask = oss.asyncPutObject(put, new OSSCompletedCallback<PutObjectRequest, PutObjectResult>() {
            @Override
            public void onSuccess(PutObjectRequest request, PutObjectResult result) {
                handleSuccessCallback(FileName);
            }

            @Override
            public void onFailure(PutObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                // 请求异常
                if (clientExcepion != null) {
                    // 本地异常如网络异常等
                    clientExcepion.printStackTrace();
                    LogUtils.e(OssUtils.class,"clientExcepion:"+clientExcepion.getMessage());
                }
                if (serviceException != null) {
                    // 服务异常
                    LogUtils.e(OssUtils.class ,"ErrorCode:"+serviceException.getErrorCode());
                    LogUtils.e(OssUtils.class,"RequestId:"+serviceException.getRequestId());
                    LogUtils.e(OssUtils.class,"HostId:"+serviceException.getHostId());
                    LogUtils.e(OssUtils.class,"RawMessage:"+serviceException.getRawMessage());
                }

                handleFaileCallback(FileName);
            }
        });
    }

    public static class MyThread extends Thread {
        private String name;

        public MyThread(String name) {
            this.name = name;
        }

        public void run() {
            ResumableUpload(name);
        }
    }

    private static String buildZipFileName(String filename){
        return filename+".gz";
    }

}
