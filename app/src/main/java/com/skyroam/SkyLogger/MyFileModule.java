package com.skyroam.SkyLogger;

import android.os.Environment;
import android.os.Handler;
import android.os.Message;

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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MyFileModule{
    private static long getFolderSize(File file) {
        long size = 0;
        try{
            File[] fileList = file.listFiles();
            for(int i = 0; i < fileList.length; i++){
                if (fileList[i].isDirectory()){
                    size = size + getFolderSize(fileList[i]);
                }else{
                    size = size + fileList[i].length();
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        return size;
    }

    private static long getFileOrFolderSize(File file){
        long size = 0;
        try{
            if(file.exists()){
                if(file.isFile()){
                    size = file.length();
                }else{
                    File[] fileList = file.listFiles();
                    for(int i = 0; i < fileList.length; i++){
                        if (fileList[i].isDirectory()){
                            size = size + getFolderSize(fileList[i]);
                        }else{
                            size = size + fileList[i].length();
                        }
                    }
                }
            }
        }catch (Exception e){
            LogUtils.e(MyFileModule.class,"getFileOrFolderSize failed!"+e.toString());
            e.printStackTrace();
        }

        return size;
    }

    private static String FormetFileOrFolderSize(long size) {
        DecimalFormat df = new DecimalFormat("#.00");
        String fileSizeString = "";
        String wrongSize = "0B";
        if (size == 0) {
            return wrongSize;
        }
        if (size < 1024) {
            fileSizeString = df.format((double) size) + "B";
        } else if (size < 1048576) {
            fileSizeString = df.format((double) size / 1024) + "KB";
        } else if (size < 1073741824) {
            fileSizeString = df.format((double) size / 1048576) + "MB";
        } else {
            fileSizeString = df.format((double) size / 1073741824) + "GB";
        }
        return fileSizeString;
    }

    private static void deleteFileOrDir(String filename,boolean keep_key_log) {
        if (filename != null) {
            String fullname = GetRootPath() + filename;
            File file = new File(fullname);
            if (file.exists()) {
                if (file.isFile()){
                    deleteFile(fullname,keep_key_log);
                }
                else{
                    deleteDirectory(fullname,keep_key_log);
                }
            }else {
                LogUtils.e(MyFileModule.class,"要删除的文件：" + filename + "不存在");
            }
        }
    }

    private static boolean  deleteFile(String fileFullName,boolean keep_key_log) {
        File file = new File(fileFullName);
        // 如果文件路径所对应的文件存在，并且是一个文件，则直接删除
        if (file.exists() && file.isFile()) {

            if(fileFullName.indexOf(".bin") > -1){
                    return true;
            }

            if(keep_key_log == true) {
                if(fileFullName.indexOf("SkyLogger.log") > -1){
                    return true;
                }

                if(fileFullName.indexOf("/mdlog") > -1) {
                    if ((fileFullName.indexOf(".muxz") > -1)
                            || (fileFullName.indexOf("muxz.tmp") > -1)
                            ||(fileFullName.indexOf("version_info.txt") > -1)) {
                        return true;
                    }
                }

                if((fileFullName.indexOf("/mobilelog") > -1)
                        || (fileFullName.indexOf("/netlog") > -1)){
                    return true;
                }
            }

            if (file.delete()) {
                LogUtils.d(MyFileModule.class,"删除单个文件" + fileFullName + "成功！");
                return true;
            } else {
                LogUtils.e(MyFileModule.class,"删除单个文件" + fileFullName + "失败！");
                return false;
            }
        } else {
            LogUtils.e(MyFileModule.class,"删除单个文件失败：" + fileFullName + "不存在！");
            return false;
        }
    }

    private static boolean deleteDirectory(String fulldir,boolean keep_key_log) {
        // 如果dir不以文件分隔符结尾，自动添加文件分隔符
        if (!fulldir.endsWith(File.separator))
            fulldir = fulldir + File.separator;
        File dirFile = new File(fulldir);
        // 如果dir对应的文件不存在，或者不是一个目录，则退出
        if ((!dirFile.exists()) || (!dirFile.isDirectory())) {
            LogUtils.e(MyFileModule.class,"删除目录失败：" + fulldir + "不存在！");
            return false;
        }
        boolean flag = true;
        // 删除文件夹中的所有文件包括子目录
        File[] files = dirFile.listFiles();
        for (int i = 0; i < files.length; i++) {
            // 删除子文件
            if (files[i].isFile()) {
                flag = deleteFile(files[i].getAbsolutePath(),keep_key_log);
                if (!flag)
                    break;
            }
            // 删除子目录
            else if (files[i].isDirectory()) {
                flag = deleteDirectory(files[i].getAbsolutePath(),keep_key_log);
                if (!flag)
                    break;
            }
        }
        if (!flag) {
            LogUtils.e(MyFileModule.class,"删除目录" + fulldir + "失败！");
            return false;
        }

        //文件夹为空
        if(dirFile.listFiles().length  > 0){
            LogUtils.d(MyFileModule.class,"文件夹"+dirFile.getName()+"不为空，不能删除");
            return true;
        }else{
            // 删除当前目录
            if (dirFile.delete()) {
                LogUtils.d(MyFileModule.class,"删除目录" + fulldir + "成功！");
                return true;
            } else {
                return false;
            }
        }
    }

    private static boolean zipFileOrDirectory(ZipOutputStream out, File fileOrDirectory, String curPath) throws IOException {
        FileInputStream in = null;
        boolean result = true;
        try { //判断是否为目录
            if (!fileOrDirectory.isDirectory()) {
                byte[] buffer = new byte[4096];
                int bytes_read;
                in = new FileInputStream(fileOrDirectory);//读目录中的子项
                // 归档压缩目录
                ZipEntry entry = new ZipEntry(curPath + fileOrDirectory.getName());//压缩到压缩目录中的文件名字
                // getName() 方法返回的路径名的名称序列的最后一个名字，这意味着表示此抽象路径名的文件或目录的名称被返回。
                // 将压缩目录写到输出流中
                out.putNextEntry(entry);//out是带有最初传进的文件信息，一直添加子项归档目录信息
                while ((bytes_read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytes_read);
                }
                out.closeEntry();
            }else{
                //列出目录中的所有文件
                File[]entries = fileOrDirectory.listFiles();
                for (int i= 0; i < entries.length;i++) {
                    result = zipFileOrDirectory(out,entries[i],curPath + fileOrDirectory.getName()+ "/");//第一次传入的curPath是空字符串
                    if(result == false)
                        break;
                }
            }
        } catch (IOException ex) {
            result = false;
            ex.printStackTrace();
        }finally{
            if (in!= null){
                try {
                    in.close();
                }catch(IOException ex) {
                    ex.printStackTrace();
                }
            }
            return result;
        }
    }

    private static boolean copyFile(String srcFile, String destFile){
        try{
            InputStream streamFrom = new FileInputStream(srcFile);
            OutputStream streamTo = new FileOutputStream(destFile);
            byte buffer[]=new byte[1024];
            int len;
            while ((len= streamFrom.read(buffer)) > 0){
                streamTo.write(buffer, 0, len);
            }
            streamFrom.close();
            streamTo.close();
            return true;
        } catch(Exception ex){
            return false;
        }
    }

    private static boolean copyFileToDir(String srcFile, String destDir){
        File fileDir = new File(destDir);
        if (!fileDir.exists()) {
            fileDir.mkdir();
        }
        String destFile = destDir +"/" + new File(srcFile).getName();
        try{
            InputStream streamFrom = new FileInputStream(srcFile);
            OutputStream streamTo = new FileOutputStream(destFile);
            byte buffer[]=new byte[1024];
            int len;
            while ((len= streamFrom.read(buffer)) > 0){
                streamTo.write(buffer, 0, len);
            }
            streamFrom.close();
            streamTo.close();
            return true;
        } catch(Exception ex){
            return false;
        }
    }
    private static boolean copyFileOrDir(String srcDir, String destDir){
        File sourceDir = new File(srcDir);
        //判断文件目录是否存在
        if(!sourceDir.exists()){
            return false;
        }
        //判断是否是目录
        if (sourceDir.isDirectory()) {
            File[] fileList = sourceDir.listFiles();
            File targetDir = new File(destDir);
            //创建目标目录
            if(!targetDir.exists()){
                targetDir.mkdirs();
            }
            //遍历要复制该目录下的全部文件
            for(int i= 0;i<fileList.length;i++){
                if(fileList[i].isDirectory()){//如果如果是子目录进行递归
                    copyFileOrDir(fileList[i].getPath()+ "/",
                            destDir + fileList[i].getName() + "/");
                }else{//如果是文件则进行文件拷贝
                    copyFile(fileList[i].getPath(), destDir +fileList[i].getName());
                }
            }
            return true;
        }else {
            copyFileToDir(srcDir,destDir);
            return true;
        }
    }

    //=======================================================================

    public static String GetRootPath() {
        String path;
        boolean sdCardExist = Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED);
        if (sdCardExist) {
            path = Environment.getExternalStorageDirectory().toString() + "/LoggingTools/";
        } else {
            path = MainActivity.getContext().getFilesDir().getAbsolutePath() + File.separator + "LoggingTools/";
        }

        File file =new File(path);
        if  (!file .exists()  && !file .isDirectory())
        {
            LogUtils.d(MyFileModule.class,"目录"+path+"不存在,创建目录");
            file.mkdir();
        }
        return path;
    }

    public static long GetFileSize(String filename){
        String fullfilename = GetRootPath()+filename;
        File file = new File(fullfilename);
        return getFileOrFolderSize(file);

    }

    public static void DeleteFile0rDir(String filename,boolean keep_key_log) {
        if(filename != null)
            deleteFileOrDir(filename,keep_key_log);
    }

    //初始化文件名列表
    public static ArrayList<String> GetFileArray() {
        ArrayList<String> FileNameArray = new ArrayList<String>();
        File file = new File(GetRootPath());
        if (file != null) {
            if (file.isDirectory()) {
                File files[] = file.listFiles();
                if (files != null) {
                    for (int index = 0; index < files.length; index++) {
                        if(files[index].getName().indexOf(".bin") > -1) {
                            ;//bin文件不显示
                        }else{
                            FileNameArray.add(files[index].getName());
                        }
                    }
                }
            }
        }
        return FileNameArray;
    }

    //初始化文件大小列表
    public static ArrayList<String> GetFileSizeArray() {
        ArrayList<String> FileSizeArray = null;
        File file = new File(GetRootPath());
        if (file != null) {
            if (file.isDirectory()) {
                File files[] = file.listFiles();
                FileSizeArray = new ArrayList<String>();
                if (files != null) {
                    for (int index = 0; index < files.length; index++) {
                        if(files[index].getName().indexOf(".bin") > -1) {
                            ;//bin文件不显示
                        }else{
                            String size = FormetFileOrFolderSize(getFileOrFolderSize(files[index]));
                            FileSizeArray.add(size);
                        }
                    }
                }
            }
        }
        return FileSizeArray;
    }

    public static boolean ZipFiles(String path,String[] filelist,String dest){
        //定义压缩输出流
        ZipOutputStream out = null;
        boolean result = true;
        try {
            //传入源文件
            File fileOrDirectory= new File(path);
            File outFile= new File(dest);
            //传入压缩输出流
            //创建文件前几级目录
            if (!outFile.exists()){
                File parentfile=outFile.getParentFile();
                if (!parentfile.exists()){
                    parentfile.mkdirs();
                }
            }
            //可以通过createNewFile()函数这样创建一个空的文件，也可以通过文件流的使用创建
            out = new ZipOutputStream(new FileOutputStream(outFile));

            //判断是否是一个文件或目录
            //如果是文件则压缩
            if (fileOrDirectory.isFile()){
                result =  zipFileOrDirectory(out,fileOrDirectory, "");
            }else {
                //否则列出目录中的所有文件递归进行压缩
                File[]entries = fileOrDirectory.listFiles();
                for (int i= 0; i < entries.length;i++) {
                    for(int list_index = 0;list_index<filelist.length;list_index++) {
                        if (entries[i].getName().equals(filelist[list_index])) {
                            result = zipFileOrDirectory(out, entries[i], fileOrDirectory.getName() + "/");//传入最外层目录名
                            if(result == false)
                                break;
                        }
                    }
                }
            }
        }catch(IOException ex) {
            result = false;
            ex.printStackTrace();
        }finally{
            if (out!= null){
                try {
                    out.close();
                }catch(IOException ex) {
                    ex.printStackTrace();
                }
            }
            return result;
        }
    }

    public static boolean MoveFileOrDir(String srcFile,String dstFile) {
        boolean result = false;
        String srcDir = GetRootPath()+srcFile;
        String destDir = GetRootPath()+dstFile;
        if (copyFileOrDir(srcDir, destDir)) {
            deleteFileOrDir("mtklog",false);
            result = true;
        }
        if(result == true){
            LogUtils.d(MyFileModule.class,"文件夹移动成功！");
        }else{
            LogUtils.e(MyFileModule.class,"文件夹移动失败！");
        }
        return result;
    }

    public static String GetFileMD5(String filename) {
        File file = new File(GetRootPath()+filename);
        if (file == null || !file.isFile() || !file.exists()) {
            return "";
        }
        FileInputStream in = null;
        String result = "";
        byte buffer[] = new byte[8192];
        int len;
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            in = new FileInputStream(file);
            while ((len = in.read(buffer)) != -1) {
                md5.update(buffer, 0, len);
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
        }finally {
            if(null!=in){
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    public static boolean WriteFile(String filePath, String fileName, String string){
        File file = new File(GetRootPath()+filePath);
        boolean result = false;
        if (!file.exists()) {
            if(!file.mkdirs()){
                return result;
            }
        }
        try {
            File fileWrite = new File(GetRootPath()+filePath + fileName);
            // 首先判断文件是否存在
            if (!fileWrite.exists()) {
                if(!fileWrite.createNewFile()){
                    return  false;
                }
            }

            FileOutputStream fileOutputStream = new FileOutputStream(fileWrite);
            fileOutputStream.write(string.getBytes());
            fileOutputStream.flush();
            fileOutputStream.close();
            result = true;
        }catch(Exception e){
            LogUtils.i(MyFileModule.class,"写文件"+fileName+"失败，reason："+e.toString());
        }

        return result;
    }

    private static Map<String, Long> UploadFileAttr = new HashMap();
    private static long UploadProgress = 0;
    private static long TotalUploadSize = 0;
    private static long AllFileTotalSize = 0;
    private static int TotFileNum = 0;
    private static int SuccessFileNum = 0;
    private static int FaileFileNum = 0;

    private static String buildObjectKey(String FileName){
        BasicInfo basicInfo = new BasicInfo();
        String devicebrand = basicInfo.getDeviceBrand();
        String systemmodel = basicInfo.getSystemModel();
        String sn = basicInfo.getSN();
        Calendar calendar = Calendar.getInstance();
        String timestamp = String.format("%04d_%02d_%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH)+1,
                calendar.get(Calendar.DAY_OF_MONTH));

        String objectKey = devicebrand+"/"+systemmodel+"/"+sn+"/"+timestamp+"/"+FileName;

        return objectKey;
    }

    private synchronized static void handleProgressCallback(String FileName,Handler handler,long currentSize, long totalSize){
        LogUtils.d(MyFileModule.class,"currentSize: " + currentSize + " totalSize: " + totalSize);
        TotalUploadSize += currentSize - UploadFileAttr.get(FileName);
        UploadFileAttr.put(FileName, currentSize);
        long process = (TotalUploadSize*100)/AllFileTotalSize;
        LogUtils.d(MyFileModule.class,"已上传大小："+TotalUploadSize
                +" 总大小："+AllFileTotalSize+" persent: "+(int)process+"%");
        if (UploadProgress != process){
            UploadProgress = process;

            String ResultString = String.format("%d,%d",process,TotFileNum);
            Message message = handler.obtainMessage() ;
            message.obj = (int)process;
            message.what = 4;
            message.obj = ResultString;
            handler.sendMessage(message);
        }
    }

    private synchronized static void handleSuccessCallback(String FileName,Handler handler){
        LogUtils.i(MyFileModule.class,"文件"+FileName+"上传成功!");
        SuccessFileNum++;
        LogUtils.i(MyFileModule.class,"SuccessFileNum:"+SuccessFileNum+" filename"+FileName);
        LogUtils.i(MyFileModule.class,"FaileFileNum:"+FaileFileNum+" filename"+FileName);
        LogUtils.i(MyFileModule.class,"uploadFileNum:"+(TotFileNum-SuccessFileNum-FaileFileNum)+" filename"+FileName);
        String ResultString = String.format("%d,%d,%d,",SuccessFileNum,FaileFileNum,TotFileNum-SuccessFileNum-FaileFileNum)+FileName;
        Message message = handler.obtainMessage();
        message.what = 5;
        message.obj = ResultString;
        handler.sendMessage(message);
    }

    private synchronized static void handleFaileCallback(String FileName,Handler handler) {
        LogUtils.e(MyFileModule.class,"文件"+FileName+"上传失败!");
        FaileFileNum++;
        String ResultString = String.format("%d,%d,%d,",SuccessFileNum,FaileFileNum,TotFileNum-SuccessFileNum-FaileFileNum)+FileName;
        Message message =  handler.obtainMessage();
        message.what = 6;
        message.obj = ResultString;
        handler.sendMessage(message);
    }

    public static void InitUploadStatus(int totFilesCount,long totFilesSize) {
        UploadFileAttr.clear();
        UploadProgress = 0;
        TotalUploadSize = 0;
        AllFileTotalSize =totFilesSize;
        TotFileNum = totFilesCount;
        SuccessFileNum = 0;
        FaileFileNum = 0;
    }

    public static void ResumableUpload(final String FileName, final Handler handler) {
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
        final String filepath = GetRootPath() + FileName;

        ResumableUploadRequest request = new ResumableUploadRequest("skyroam-terminal-log", objectKey, filepath, recordDirectory);
        request.setDeleteUploadOnCancelling(false);

        request.setProgressCallback(new OSSProgressCallback<ResumableUploadRequest>() {
            @Override
            public void onProgress(ResumableUploadRequest request, long currentSize, long totalSize) {
                handleProgressCallback(FileName,handler,currentSize,totalSize);
            }
        });

        OSSAsyncTask resumableTask = oss.asyncResumableUpload(request, new OSSCompletedCallback<ResumableUploadRequest, ResumableUploadResult>() {
            @Override
            public void onSuccess(ResumableUploadRequest request, ResumableUploadResult result) {
                handleSuccessCallback(FileName,handler);
            }

            @Override
            public void onFailure(ResumableUploadRequest request, ClientException clientExcepion, ServiceException serviceException) {
                // 异常处理
                if (clientExcepion != null) {
                    LogUtils.e(MyFileModule.class,"clientExcepion:"+clientExcepion.getMessage());
                }

                handleFaileCallback(FileName,handler);
            }
        });

    }

    public static void SimpleUpload(final String FileName, final Handler handler) {
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
                    LogUtils.e(MyFileModule.class,"get token failed:"+e.toString());
                }
                return null;
            }
        };

        OSS oss = new OSSClient(MainActivity.getContext(), endpoint, credetialProvider);

        UploadFileAttr.put(FileName, 0L);
        String objectKey = buildObjectKey(FileName);
        final String filepath = GetRootPath() + FileName;

        // 构造上传请求
        PutObjectRequest put = new PutObjectRequest("skyroam-terminal-log", objectKey, filepath);

        // 异步上传时可以设置进度回调
        put.setProgressCallback(new OSSProgressCallback<PutObjectRequest>() {
            @Override
            public void onProgress(PutObjectRequest request, long currentSize, long totalSize) {
                handleProgressCallback(FileName,handler,currentSize,totalSize);
            }
        });

        OSSAsyncTask task = oss.asyncPutObject(put, new OSSCompletedCallback<PutObjectRequest, PutObjectResult>() {
            @Override
            public void onSuccess(PutObjectRequest request, PutObjectResult result) {
                handleSuccessCallback(FileName,handler);
            }

            @Override
            public void onFailure(PutObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                // 请求异常
                if (clientExcepion != null) {
                    // 本地异常如网络异常等
                    clientExcepion.printStackTrace();
                    LogUtils.e(MyFileModule.class,"clientExcepion:"+clientExcepion.getMessage());
                }
                if (serviceException != null) {
                    // 服务异常
                    LogUtils.e(MyFileModule.class ,"ErrorCode:"+serviceException.getErrorCode());
                    LogUtils.e(MyFileModule.class,"RequestId:"+serviceException.getRequestId());
                    LogUtils.e(MyFileModule.class,"HostId:"+serviceException.getHostId());
                    LogUtils.e(MyFileModule.class,"RawMessage:"+serviceException.getRawMessage());
                }

                handleFaileCallback(FileName,handler);
            }
        });
    }
}
