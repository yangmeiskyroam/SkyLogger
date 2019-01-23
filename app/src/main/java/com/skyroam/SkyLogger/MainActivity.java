package com.skyroam.SkyLogger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback
 {
     private static String[] PERMISSIONS = { Manifest.permission.READ_PHONE_STATE,
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE,
                                                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};

     private final int REQUEST_WRITE_EXTERNAL_STORAGE = 101;
     private final int REQUEST_READ_EXTERNAL_STORAGE = 102;
     private final int REQUEST_DELETE_EXTERNAL_STORAGE = 103;

     private int location_timer_count = 0;
     private LocationUtils mlocation = null;
     private Timer location_timer = null;

     private TextView Lable_tv = null;
     private TextView DeviceInfo_tv = null;
     private TextView tv = null;
     private ListView lv = null;
     private TextView LogTimerView;
     private ProgressBar mprocessbar;
     private Button logging_btn;
     private Button upload_btn;
     private Button delete_btn;
     private CheckBox checkbox_all;
     private TextView mProcessView;

     ArrayList<String> mFilenameArray = null;
     ArrayList<String> mFilesizeArray = null;
     ArrayList<String> CheckedlistStr = null;
     private List<HashMap<String, Object>> AllList = null;
     private MyAdapter adapter;

     private static Context mContext;

     private boolean mUploading = false;
     private boolean mLogging = false;
     private boolean mLoggingStopFlag = false;

     private IntentFilter intentFilter;
     private BroadcastUtils networkChangeReceiver;

     public static Context getContext() {
         return mContext;
     }

     @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        LogUtils.i(MainActivity.class,"The onCreate() event");
        LogUtils.i(MainActivity.class,"apk buildTime:"+BuildConfig.BUILD_TIME);

        mContext=getApplicationContext();
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);//设置竖屏

        //监听网络
//        intentFilter = new IntentFilter();
//        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
//        networkChangeReceiver = new BroadcastUtils();
//        registerReceiver(networkChangeReceiver, intentFilter);

        OssUtils.setCallback(new OssUtils.Callback() {
            @Override
            public void getUploadProcess(int uploadstatus,String process) {
                if(uploadstatus == OssUtils.UploadStatus.Ziping){
                    sendCmd(2,false);
                }else if(uploadstatus == OssUtils.UploadStatus.ZipDone){
                    sendCmd(2,true);
                }else if(uploadstatus == OssUtils.UploadStatus.Uploading){
                    sendCmd(3,process);
                }else if(uploadstatus == OssUtils.UploadStatus.UploadSucess){
                    sendCmd(4,process);
                }else if(uploadstatus == OssUtils.UploadStatus.UploadFaiure){
                    sendCmd(5,process);
                }
            }
        });

        LoggingUtils.setCallback(new LoggingUtils.Callback() {
            @Override
            public void getLoggingTime(int time) {
                sendCmd(1,time);
            }

            @Override
            public void setFilterResult(boolean result){
                sendCmd(6,result);
            }
        });

        InitViews();

    }

     /** 当活动即将可见时调用 */
     @Override
     protected void onStart() {
         super.onStart();
         LogUtils.d(MainActivity.class, "The onStart() event");
     }

     /** 当活动可见时调用 */
     @Override
     protected void onResume() {
         super.onResume();
         LogUtils.d(MainActivity.class, "The onResume() event");
     }

     /** 当其他活动获得焦点时调用 */
     @Override
     protected void onPause() {
         super.onPause();
         LogUtils.d(MainActivity.class, "The onPause() event");
     }

     /** 当活动不再可见时调用 */
     @Override
     protected void onStop() {
         super.onStop();
         LogUtils.d(MainActivity.class, "The onStop() event");
     }

     /** 当活动将被销毁时调用 */
     @Override
     public void onDestroy() {
         LogUtils.i(MainActivity.class, "The onDestroy() event,mLogging:"+mLogging+" mUploading:"+mUploading);
         super.onDestroy();

        //unregisterReceiver(networkChangeReceiver);

         if(mLogging){
             LoggingUtils.setCallback(null);
             LoggingUtils.StopLogging(getContext());
         }
         if(mUploading){
             OssUtils.setCallback(null);
             OssUtils.UploadCancle();
         }
     }

     @Override
     public boolean onKeyDown(int keyCode, KeyEvent event) {
         LogUtils.i(MainActivity.class, "The onKeyDown() event,mLogging:"+mLogging+" mUploading:"+mUploading);
         if(keyCode == KeyEvent.KEYCODE_BACK){
             if(mLogging || mUploading) {
                 Intent i = new Intent(Intent.ACTION_MAIN);
                 i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                 i.addCategory(Intent.CATEGORY_HOME);
                 startActivity(i);
                 return true;
             }
         }
         return super.onKeyDown(keyCode, event);
     }


     Handler mHandler = new Handler(Looper.getMainLooper()){
         @Override
         public void handleMessage(Message msg) {
             super.handleMessage(msg);
             switch(msg.what)
             {
                 case 1://刷新日志时间显示
                     handleLoggingMessage(msg);
                     break;
                 case 2://压缩进度
                     handleCompressMessage(msg);
                     break;
                 case 3: //上传进度
                     handleProgressMessage(msg);
                     break;
                 case 4://上传成功
                     handleSuccessMessage(msg);
                     break;
                 case 5://上传失败
                     handleFaileMessage(msg);
                     break;
                 case 6://设置filter结果
                     handleFilterSet(msg);
                     break;
                 default:
                     break;
             }
         }
     };

     @SuppressLint("StringFormatInvalid")
     private void setListTitleText(boolean checked){
         String text;
         if(checked == true){
             if(CheckedlistStr.size() > 0){
                 if(CheckedlistStr.size() > 1) {
                     text = String.format(getResources().getString(R.string.items_selected), CheckedlistStr.size());
                 }else{
                     text = String.format(getResources().getString(R.string.item_selected), CheckedlistStr.size());
                 }
             }else{
                 text = String.format(getResources().getString(R.string.item_selected), 0);
             }

         }else{
             if(mFilenameArray.size() > 1) {
                 text = String.format(getResources().getString(R.string.file_items), mFilenameArray.size());
             }else{
                 text = String.format(getResources().getString(R.string.file_item), mFilenameArray.size());
             }
         }

         tv.setText(text);
    }

     private void InitViews() {
         Lable_tv = (TextView) this.findViewById(R.id.lable);
         Lable_tv.setText(R.string.device_info);
         Lable_tv.setClickable(true);
         DeviceInfo_tv = (TextView) this.findViewById(R.id.info);
         DeviceInfo_tv.setText(DeviceInfoUtils.getDeviceBrand()+":"+DeviceInfoUtils.getSystemModel()+":"+DeviceInfoUtils.getSN());
         tv = (TextView) this.findViewById(R.id.tv);
         lv = (ListView) this.findViewById(R.id.lv);
         LogTimerView = (TextView)findViewById(R.id.LogView);
         mprocessbar = (ProgressBar)findViewById(R.id.ProgressBar);
         mProcessView = (TextView)findViewById(R.id.ProcessView);
         logging_btn = (Button)findViewById(R.id.log_button);
         logging_btn.setText(R.string.logstart_btn);
         upload_btn = (Button)findViewById(R.id.upload_button);
         upload_btn.setText(R.string.uplaod_btn);
         delete_btn = (Button)findViewById(R.id.delete_button);
         delete_btn.setText(R.string.delete_btn);
         checkbox_all = (CheckBox)findViewById(R.id.checkbox);
         checkbox_all.setText(R.string.file_select_all);

         InitListView();

         mtklogModeLister();
         checkLogging();
     }

     private void mtklogModeLister(){
         Lable_tv.setOnClickListener(new View.OnClickListener() {
             long[] mHints = new long[3];
             @Override
             public void onClick(View arg0) {
                 if(mLogging == false){
                     System.arraycopy(mHints, 1, mHints, 0, mHints.length - 1);
                     mHints[mHints.length - 1] = SystemClock.uptimeMillis();
                     if(SystemClock.uptimeMillis()-mHints[0]<=2000){
                         toast(getResources().getString(R.string.MTKlog_mode));
                         LoggingUtils.setLoggingMode(false,getContext());
                     }
                 }
             }
         });
     }
     private void checkLogging(){
         if (LoggingUtils.isBootAutoLogging(getContext())) {
             if(LoggingUtils.StartLogging(getContext())){
                 LoggingUtils.setBootAutoLogging(false, getContext());
             }
         }else{
             if(LoggingUtils.LoggingRunnig(getContext()) == true){
                 LoggingUtils.LoggingConnect(getContext());
             }
         }
     }
     private void InitListView(){
         mFilenameArray = FileUtils.GetFileArray(true);
         mFilesizeArray = FileUtils.GetFileSizeArray(true);
         setListTitleText(false);
         ShowListView();
     }

     // 显示带有checkbox的listview
     private void ShowListView() {
         AllList = new ArrayList<HashMap<String, Object>>();
         for (int i = 0; i < mFilenameArray.size(); i++) {
             HashMap<String, Object> map = new HashMap<String, Object>();
             map.put("item_tv", mFilenameArray.get(i));
             map.put("item_tv_z", mFilesizeArray.get(i));
             map.put("item_cb", false);
             AllList.add(map);

             adapter = new MyAdapter(this, AllList, R.layout.listviewitem,
                     new String[] { "item_tv", "item_tv_z","item_cb" }, new int[] {
                     R.id.item_tv, R.id.item_tv_z,R.id.item_cb });
             lv.setAdapter(adapter);
         }

         CheckedlistStr = new ArrayList<String>();
         lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {

             @Override
             public void onItemClick(AdapterView<?> arg0, View view,
                                     int position, long arg3) {
                 ViewHolder holder = (ViewHolder) view.getTag();
                 holder.cb.toggle();// 在每次获取点击的item时改变checkbox的状态
                 MyAdapter.isSelected.put(position, holder.cb.isChecked()); // 同时修改map的值保存状态
                 if (holder.cb.isChecked() == true) {
                     CheckedlistStr.add(mFilenameArray.get(position));
                 } else {
                     CheckedlistStr.remove(mFilenameArray.get(position));
                 }
                 setListTitleText(true);
             }
         });

         lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
             @Override
             public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                 if(FileUtils.isFolder(mFilenameArray.get(position))==false){
                     LogUtils.i(MainActivity.class,mFilenameArray.get(position)+"不是文件夹，长按无效！");
                     return false;
                 }
                 String dir = mFilenameArray.get(position)+"/";
                 String OldFixContent;
                 String OldDescribe;
                 String OldContent = FileUtils.ReadFile(dir,"README.txt");
                 if(OldContent == null){
                     OldFixContent = null;
                     OldDescribe = null;
                 }else {
                     int Pos = OldContent.indexOf("描述:");
                     if (Pos == -1) {
                         OldFixContent = OldContent;
                         OldDescribe = null;
                     } else {
                         OldFixContent = OldContent.substring(0, Pos);
                         OldDescribe = OldContent.substring(Pos + "描述:".length());
                     }
                 }
                 ShowLogDescribeModifyDialog(dir,OldFixContent,OldDescribe);
                 return true;
             }
         });
     }

     private void CheckAllSelect(boolean checked) {
         if(checked == true){
             CheckedlistStr = new ArrayList<String>();
             for(int i=0;i<AllList.size();i++){
                 MyAdapter.isSelected.put(i,true);
                 CheckedlistStr.add(mFilenameArray.get(i));
             }
             if(adapter != null)
                 adapter.notifyDataSetChanged();//注意这一句必须加上，否则checkbox无法正常更新状态
             setListTitleText(true);
         }
         else{
             int list_size = AllList.size();
             for(int i=0;i<list_size;i++){
                 MyAdapter.isSelected.put(i, false);
                 CheckedlistStr.remove(mFilenameArray.get(i));
             }
             if(adapter != null)
                adapter.notifyDataSetChanged();
             setListTitleText(true);

         }
     }

     private void RefreshListView(){
         int index;
         for (index = AllList.size() - 1; index >= 0; index--) {
             AllList.remove(index);
         }

         for (index = CheckedlistStr.size() - 1; index >= 0; index--) {
             CheckedlistStr.remove(index);
         }

         for(index = mFilenameArray.size()-1;index >= 0;index--){
             mFilenameArray.remove(index);
             mFilesizeArray.remove(index);
         }

         mFilenameArray = FileUtils.GetFileArray(true);
         mFilesizeArray = FileUtils.GetFileSizeArray(true);
         for (int i = 0; i < mFilenameArray.size(); i++) {
             HashMap<String, Object> map = new HashMap<String, Object>();
             map.put("item_tv", mFilenameArray.get(i));
             map.put("item_tv_z", mFilesizeArray.get(i));
             map.put("item_cb", false);
             AllList.add(map);
         }
         setListTitleText(false);
         adapter = new MyAdapter(this, AllList, R.layout.listviewitem,
                 new String[] { "item_tv", "item_tv_z","item_cb" }, new int[] {
                 R.id.item_tv, R.id.item_tv_z,R.id.item_cb });
         lv.setAdapter(adapter);
         adapter.notifyDataSetChanged();
     }

     private String buildFixLog(){
         StringBuilder sb = new StringBuilder("");
         sb.append("设备厂商:"+DeviceInfoUtils.getDeviceBrand()+"\n");
         sb.append("设备型号:"+DeviceInfoUtils.getSystemModel()+"\n");
         sb.append("设备SN:"+DeviceInfoUtils.getSN()+"\n");
         String[] imeis = DeviceInfoUtils.getIMEI();
         if(imeis != null){
             sb.append("设备IMEI:"+imeis[0]+" "+imeis[1]+" "+imeis[2]+"\n");
         }else{
             sb.append("设备IMEI: NULL"+"\n");
         }
         sb.append("Log时间:"+LoggingUtils.GetLogTime(true)+"-"+LoggingUtils.GetLogTime(false)+"\n");

         String location = LoggingUtils.getLoggingLocation(getContext());
         String[] location_split = location.split(" ");
         sb.append("经度:"+location_split[0]+" 纬度:"+location_split[1]+"\n");
         return sb.toString();
     }

     private void writeDescribe(String dir,String describe){
         String content;
         String fixcontent = buildFixLog();
         if(!(TextUtils.isEmpty(describe))){
             content = fixcontent+"描述:"+describe+"\n";
         }else{
             content = fixcontent;
         }
         FileUtils.WriteFile(dir,"README.txt",content);
     }

     private void ShowLogDescribeDialog(final String dir){
         final EditText et = new EditText(this);
         new AlertDialog.Builder(this).setTitle(R.string.dialog_log_describe)
                 .setView(et)
                 .setCancelable(false)
                 .setPositiveButton(R.string.dialog_yes_button, new DialogInterface.OnClickListener() {
                     @Override
                     public void onClick(DialogInterface dialogInterface, int i) {
                         String describe = et.getText().toString();
                         if(!(TextUtils.isEmpty(describe))){
                             writeDescribe(dir,describe);
                         }else{
                             writeDescribe(dir,null);
                         }
                     }
                 }).setNegativeButton(R.string.dialog_cancle_button, new DialogInterface.OnClickListener() {
                     @Override
                     public void onClick(DialogInterface dialog, int which) {
                         writeDescribe(dir,null);
                     }
                 }).show();
     }

     private void showNoFiltertDialog() {
         /*@setView 装入一个EditView
          */
         final EditText editText = new EditText(MainActivity.this);
         editText.setText(R.string.no_filter_info);
         editText.setFocusable(false);
         AlertDialog.Builder inputDialog =
                 new AlertDialog.Builder(MainActivity.this);
         inputDialog.setTitle("Note:").setView(editText);
         inputDialog.setPositiveButton("Yes",
                 new DialogInterface.OnClickListener() {
                     @Override
                     public void onClick(DialogInterface dialog, int which) {
                     }
                 }).show();
     }

     private void modifyDescribe(String dir,String oldFixContent,String describe){
         String content;
         if(!(TextUtils.isEmpty(describe))){
             content = oldFixContent+"描述:"+describe+"\n";
         }else{
             content = oldFixContent;
         }
         FileUtils.WriteFile(dir,"README.txt",content);
     }

     private void ShowLogDescribeModifyDialog(final String dir,final String oldFixContent,String oldContent){
         final EditText et = new EditText(this);
         if(!TextUtils.isEmpty(oldContent)){
             et.setText(oldContent);
         }
         new AlertDialog.Builder(this).setTitle(R.string.dialog_log_describe)
                 .setView(et)
                 .setCancelable(false)
                 .setPositiveButton(R.string.dialog_yes_button, new DialogInterface.OnClickListener() {
                     @Override
                     public void onClick(DialogInterface dialogInterface, int i) {
                         String describe = et.getText().toString();
                         if(!(TextUtils.isEmpty(describe))){
                             modifyDescribe(dir,oldFixContent,describe);
                         }else{
                             modifyDescribe(dir,oldFixContent,null);
                         }
                     }
                 }).setNegativeButton(R.string.dialog_cancle_button, new DialogInterface.OnClickListener() {
             @Override
             public void onClick(DialogInterface dialog, int which) {
                 modifyDescribe(dir,oldFixContent,null);
             }
         }).show();
     }

     private void sendCmd(int what,Object obj)
     {
         Message message = mHandler.obtainMessage();;
         message.what = what;
         message.obj = obj;
         mHandler.sendMessage(message);
     }

     private void handleLoggingMessage(Message msg){
         int value = (int)msg.obj;
         if(value == -1){//连接中...
             mLogging = true;

             LogTimerView.setText(R.string.log_connect);
             LogTimerView.setTextSize(30);
             logging_btn.setEnabled(false);
         }else if(value == -2){//断开中
             LogTimerView.setText(R.string.log_disconnect);
             LogTimerView.setTextSize(30);
             logging_btn.setEnabled(false);
         }else if(value == 0){//定时器计时结束 断开
             mLogging = false;
             mLoggingStopFlag = false;

             LogTimerView.setText("");
             LogTimerView.setTextSize(35);
             logging_btn.setText(R.string.logstart_btn);
             logging_btn.setEnabled(true);

             RefreshListView();
             String dstFilename = LoggingUtils.GetFileName();
             if(!TextUtils.isEmpty(dstFilename)){
                 ShowLogDescribeDialog(dstFilename);
             }

         }else{//计时
             if(mLoggingStopFlag == false){
                 int hour = value/3600;
                 int minute = (value-hour*3600)/60;
                 int second = (value-hour*3600-minute*60);
                 String text = String.format("%02d",hour)+":"+String.format("%02d",minute)+":"+String.format("%02d",second);
                 LogTimerView.setText(text);
                 LogTimerView.setTextSize(35);
                 logging_btn.setText(R.string.logend_btn);
                 logging_btn.setEnabled(true);

                 mLogging = true;
             }
         }
     }

     private void handleCompressMessage(Message msg){
         boolean compress_status = (boolean)msg.obj;

         if(compress_status){
             mProcessView.setText(R.string.ready_upload);
             DeleteSelectFiles(true);
         } else{
             mUploading = true;

             mProcessView.setText(R.string.compressing);
             upload_btn.setEnabled(false);
             delete_btn.setEnabled(false);
         }
     }

     private  void handleProgressMessage(Message msg){
         String[] result= ((String)msg.obj).split(",");
         String ViewText = String.format(getResources().getString(R.string.upload_process), Integer.valueOf(result[1]),Integer.valueOf(result[2]),Integer.valueOf(result[3]));
         mProcessView.setText(ViewText);
         mprocessbar.setProgress(Integer.valueOf(result[0]));
     }

     private void handleSuccessMessage(Message msg){
         String[] result= ((String)msg.obj).split(",");
         int SuccessFileNum = Integer.valueOf(result[0]);
         int FaileFileNum = Integer.valueOf(result[1]);
         int UploadingFileNum = Integer.valueOf(result[2]);
         String filename = result[3];
         String ViewText = String.format(getResources().getString(R.string.upload_process), SuccessFileNum,FaileFileNum,UploadingFileNum);
         mProcessView.setText(ViewText);
         if(UploadingFileNum == 0) {
             mUploading = false;
             mprocessbar.setProgress(0);
             upload_btn.setEnabled(true);
             delete_btn.setEnabled(true);
             ViewText = String.format(getResources().getString(R.string.upload_complete), FaileFileNum);
             mProcessView.setText(ViewText);
         }

         DeleteOneFile(filename);
     }

     private void handleFaileMessage(Message msg){
         String[] result= ((String)msg.obj).split(",");
         int SuccessFileNum = Integer.valueOf(result[0]);
         int FaileFileNum = Integer.valueOf(result[1]);
         int UploadingFileNum = Integer.valueOf(result[2]);
         String ViewText = String.format(getResources().getString(R.string.upload_process), SuccessFileNum,FaileFileNum,UploadingFileNum);
         mProcessView.setText(ViewText);
         if(UploadingFileNum == 0) {
             mUploading = false;
             mprocessbar.setProgress(0);
             upload_btn.setEnabled(true);
             delete_btn.setEnabled(true);
             ViewText = String.format(getResources().getString(R.string.upload_complete), FaileFileNum);
             mProcessView.setText(ViewText);
         }
     }

     private void toast(String message) {
         View toastRoot = LayoutInflater.from(getContext()).inflate(R.layout.toast, null);
         Toast toast = new Toast(getContext());
         toast.setGravity(Gravity.CENTER, 0, 0);
         toast.setView(toastRoot);
         TextView tv = (TextView) toastRoot.findViewById(R.id.toast_notice);
         tv.setText(message);
         toast.show();
     }

     private void handleFilterSet(Message msg){
         boolean result = (boolean)msg.obj;
         if(result == false){
             //toast(getResources().getString(R.string.filter_not_found));
             showNoFiltertDialog();
         }
     }

     private void UploadFiles(){
         OssUtils.UploadFiles(CheckedlistStr);
     }

     private void DeleteOneFile(String filename){
         LogUtils.d(MainActivity.class,"要删除的文件或文件夹："+ filename);
         FileUtils.DeleteFile0rDir(filename, false);

         RefreshListView();
     }

     //删除日志
     private void DeleteSelectFiles(boolean keep_gz_file){
        if(CheckedlistStr.size()>0) {
            String[] filelist = (String[]) CheckedlistStr.toArray(new String[CheckedlistStr.size()]);
            ArrayList<String> deletefileArray = new ArrayList<String>();
            int size = CheckedlistStr.size();
            for (int index = 0; index < size; index++) {
                filelist[index] = CheckedlistStr.get(index);
                if(keep_gz_file == true){
                    if(filelist[index].indexOf(".gz")>-1){
                        ;//保留压缩文件
                    }else{
                        deletefileArray.add(filelist[index]);
                    }
                }else{
                    deletefileArray.add(filelist[index]);
                }
            }

            for (int index = 0; index < deletefileArray.size(); index++) {
                LogUtils.d(MainActivity.class,"要删除的文件或文件夹："+ deletefileArray.get(index));
                FileUtils.DeleteFile0rDir(deletefileArray.get(index), false);
            }
            RefreshListView();

        }else{
            LogUtils.e(MainActivity.class,"未选中要删除的文件或文件夹！");
        }
     }

     //获得位置信息
     private void getLocation() {
         mlocation = new LocationUtils();
//         //获取不精确位置信息 cellid
//         MyLocation.CoarseLocation coarse_location = MyLocation.get_coarse_location();
//         if((coarse_location != null)&&(coarse_location.type != 0xff)){
//             LogUtils.i(MainActivity.class,"get coarse location information!");
//         }else{
//             LogUtils.e(MainActivity.class,"not get coarse location information!");
//         }
         //获取经纬度
         if(mlocation.begin_fine_location() == true){
             create_location_timer();
         }
     }

     private void create_location_timer() {
         if(location_timer!= null){
             location_timer.cancel();
             location_timer = null;
         }
         location_timer = new Timer();
         location_timer.schedule(new TimerTask() { @Override public void run() {
             location_timer_count++;
             LogUtils.d(MainActivity.class,"location location_timer_count:"+location_timer_count);
             if(location_timer_count>=10) {
                 location_timer_count = 0;
                 location_timer.cancel();
                 location_timer = null;
                 mlocation.end_fine_location();
                 if((mlocation.get_Latitude_result() == 0)&&(mlocation.get_Longitude_result() == 0)){
                     LogUtils.e(MainActivity.class,"faild to get location information!");
                     LoggingUtils.setLoggingLocation(mlocation.get_Longitude_result(),mlocation.get_Latitude_result(),getContext());
                 }else{
                     LogUtils.i(MainActivity.class,"success to get location information!");
                     LoggingUtils.setLoggingLocation(mlocation.get_Longitude_result(),mlocation.get_Latitude_result(),getContext());
                     //MyLocation.begin_detail_location(MyLocation.get_Latitude_result(),MyLocation.get_Longitude_result());
                 }
             }else{
                 if(!((mlocation.get_Latitude_result() == 0)&&(mlocation.get_Longitude_result() == 0))){
                     location_timer_count = 0;
                     location_timer.cancel();
                     location_timer = null;
                     mlocation.end_fine_location();
                     LogUtils.i(MainActivity.class,"success to get location information!");
                     LoggingUtils.setLoggingLocation(mlocation.get_Longitude_result(),mlocation.get_Latitude_result(),getContext());
                     //mlocation.begin_detail_location(mlocation.get_Latitude_result(),mlocation.get_Longitude_result());
                 }
             }
         } },1,1000);

     }

     private void DeleteBtnHandle(){
         DeleteSelectFiles(false);
     }

     //上传日志 上传的文件先打包，然后删除文件，上传压缩包
     private void UploadBtnHandle(){
         LogUtils.i(MainActivity.class,"upload button clicket,begin uploading!");
         UploadFiles();
     }

     //抓取日志
     private void CatchBtnHandle(){
         //catch log的时候首先将filter bin 写到sd卡
         if (!mLogging) {
             LogUtils.i(MainActivity.class,"logging button clicket,begin logging!");
             sendCmd(1,-1);
             getLocation();
             new Thread() {
                 @Override
                 public void run() {
                     super.run();
                     try {
                         Thread.sleep(1000);
                         if(!LoggingUtils.StartLogging(getContext())){
                             sendCmd(1,0);
                         }
                     }catch (InterruptedException e) {
                         LogUtils.i(MainActivity.class,"Exception reason:"+e.toString());
                         sendCmd(1,0);
                     }
                 }
             }.start();
         } else {
             LogUtils.i(MainActivity.class,"logging button clicket,stop logging!");
             sendCmd(1,-2);
             mLoggingStopFlag = true;
             LoggingUtils.StopLogging(getContext());
         }
     }

     public void onCheckboxClicked(View view) {
         // Is the view now checked?
         boolean checked = ((CheckBox) view).isChecked();
         switch(view.getId()) {
             case R.id.checkbox:
                 CheckAllSelect(checked);
                 break;
             default:
                break;
         }
     }
     public void buttonclick(View v)
     {
         int permissionCheck;
         switch(v.getId())
         {
             case R.id.log_button:
                 permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                 if(permissionCheck != PackageManager.PERMISSION_GRANTED){
                     ActivityCompat.requestPermissions(this, PERMISSIONS,REQUEST_WRITE_EXTERNAL_STORAGE);
                 }
                 else{
                     CatchBtnHandle();
                 }
                 break;
             case R.id.upload_button:
                 permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
                 if(permissionCheck != PackageManager.PERMISSION_GRANTED){
                     ActivityCompat.requestPermissions(this, PERMISSIONS,REQUEST_READ_EXTERNAL_STORAGE);
                 }
                 else{
                     UploadBtnHandle();
                 }
                 break;
              case R.id.delete_button:
                  permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
                  if(permissionCheck != PackageManager.PERMISSION_GRANTED){
                      ActivityCompat.requestPermissions(this, PERMISSIONS,REQUEST_DELETE_EXTERNAL_STORAGE);
                  }
                  else{
                      DeleteBtnHandle();
                  }
                  break;
              default:
                  break;
         }
     }

     @Override
     public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
         switch (requestCode) {
             case REQUEST_WRITE_EXTERNAL_STORAGE:
                 if((grantResults.length > 0) && (grantResults[1] == PackageManager.PERMISSION_GRANTED)){
                     CatchBtnHandle();
                 }else{
                     LogUtils.e(MainActivity.class,"no permission:WRITE_EXTERNAL_STORAGE");
                 }
                break;
             case REQUEST_READ_EXTERNAL_STORAGE:
                 if((grantResults.length > 0) && (grantResults[2] == PackageManager.PERMISSION_GRANTED)){
                     UploadBtnHandle();
                 }else{
                     LogUtils.e(MainActivity.class,"no permission:READ_EXTERNAL_STORAGE");
                 }
                 break;
             case REQUEST_DELETE_EXTERNAL_STORAGE:
                 if((grantResults.length > 0) && (grantResults[2] == PackageManager.PERMISSION_GRANTED)){
                     DeleteBtnHandle();
                 }else{
                     LogUtils.e(MainActivity.class,"no permission:DELETE_EXTERNAL_STORAGE");
                 }
                 break;
             default:
                 break;
         }
     }
 }
