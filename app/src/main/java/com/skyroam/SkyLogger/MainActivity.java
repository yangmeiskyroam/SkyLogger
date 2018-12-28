package com.skyroam.SkyLogger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
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
     private int logging_start_timer_count = 0;
     private int logging_stop_timer_count = 0;
     private boolean logging_stop_flag = false;
     private MyLocation mlocation = null;
     private int logging_count = 0;
     private Timer logging_timer = null;
     private Timer location_timer = null;
     private int upload_status = 0;//1： 正在压缩 2：压缩完成 3:开始上传

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
     private Handler mHandler = null;
     private static Context mContext;

     public static Context getContext() {
         return mContext;
     }

     @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mContext=getApplicationContext();
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);//设置竖屏

        upload_status = 0;
        mHandler = create_handle();
        InitViews();
        InitListView();
    }

     @Override
     public boolean onKeyDown(int keyCode, KeyEvent event) {
         LogUtils.d(MainActivity.class,"logging:"+logging_start_timer_count+" upload status:"+upload_status);
         if(keyCode == KeyEvent.KEYCODE_BACK){
             if((logging_start_timer_count != 0) || (upload_status != 0)) {
                 Intent i = new Intent(Intent.ACTION_MAIN);
                 i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                 i.addCategory(Intent.CATEGORY_HOME);
                 startActivity(i);
                 return true;
             }
         }
         return super.onKeyDown(keyCode, event);
     }

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
         tv = (TextView) this.findViewById(R.id.tv);
         lv = (ListView) this.findViewById(R.id.lv);
         LogTimerView = (TextView)findViewById(R.id.LogView);;
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
     }

     private void InitListView(){
         mFilenameArray = MyFileModule.GetFileArray();
         mFilesizeArray = MyFileModule.GetFileSizeArray();
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

         mFilenameArray = MyFileModule.GetFileArray();
         mFilesizeArray = MyFileModule.GetFileSizeArray();
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

     private String buildLFileName(){
         Calendar calendar = Calendar.getInstance();
         boolean result = false;
         String timestamp = String.format("%4d%2d%02d_%02d%02d%02d",
                 calendar.get(Calendar.YEAR),
                 calendar.get(Calendar.MONTH)+1,
                 calendar.get(Calendar.DAY_OF_MONTH),
                 calendar.get(Calendar.HOUR_OF_DAY),
                 calendar.get(Calendar.MINUTE),
                 calendar.get(Calendar.SECOND));

         return "SkyLog_"+timestamp+"/";
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
                             MyFileModule.WriteFile(dir,"README.txt",describe);
                         }
                     }
                 }).setNegativeButton(R.string.dialog_cancle_button,null).show();
     }

     private void sendCmd(int what,Object obj)
     {
         if(mHandler != null){
             Message message = mHandler.obtainMessage();;
             message.what = what;
             message.obj = obj;
             mHandler.sendMessage(message);
         }
     }

     private void handleFreshLogTimeMessage(Message msg){
         String text = (String)msg.obj;
         LogTimerView.setText(text);
         if(text == null){
             logging_btn.setEnabled(true);
             logging_btn.setText(R.string.logstart_btn);

             ArrayList<String> fileArray = MyFileModule.GetFileArray();
             String filename = null;
             String dstFilename = null;
             if(fileArray != null){
                 for (int index = 0; index < fileArray.size(); index++) {
                     filename = fileArray.get(index);
                     if(filename.indexOf("mtklog")>-1){
                         MyFileModule.DeleteFile0rDir(filename, true);
                         dstFilename = buildLFileName();
                         MyFileModule.MoveFileOrDir(filename,dstFilename);
                         break;
                     }
                 }
             }
             RefreshListView();
             if(!TextUtils.isEmpty(dstFilename)){
                 ShowLogDescribeDialog(dstFilename);
             }
         }else if(text.equals(getResources().getString(R.string.log_connect))){
             LogTimerView.setTextSize(30);
             logging_btn.setText(R.string.logend_btn);
             logging_btn.setEnabled(false);
         }else if(text.equals(getResources().getString(R.string.log_disconnect))){
             LogTimerView.setTextSize(30);
             logging_btn.setText(R.string.logstart_btn);
             logging_btn.setEnabled(false);
         }
         else{
             LogTimerView.setTextSize(35);
             logging_btn.setEnabled(true);
         }

     }

     private void handleCompressMessage(Message msg){
         int compress_status = (int)msg.obj;

         upload_status = compress_status;
         if(compress_status == 1){
             mProcessView.setText(R.string.compressing);
             upload_btn.setEnabled(false);
             delete_btn.setEnabled(false);
         }
         else if(compress_status == 2){
             mProcessView.setText(R.string.ready_upload);
             DeleteSelectFiles(true);
         }
     }

     private  void handleProgressMessage(Message msg){
         String[] result= ((String)msg.obj).split(",");

         if(upload_status == 2){
             upload_status = 3;
             String ViewText = String.format(getResources().getString(R.string.upload_process), 0,0,Integer.valueOf(result[1]));
             mProcessView.setText(ViewText);
         }
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
             upload_status = 0;
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
             upload_status = 0;
             mprocessbar.setProgress(0);
             upload_btn.setEnabled(true);
             delete_btn.setEnabled(true);
             ViewText = String.format(getResources().getString(R.string.upload_complete), FaileFileNum);
             mProcessView.setText(ViewText);
         }
     }

     private Handler create_handle() {
         Handler handler = new Handler(this.getMainLooper()){
             @Override
             public void handleMessage(Message msg) {
                 super.handleMessage(msg);
                 switch(msg.what)
                 {
                     case 1://压缩进度
                         handleCompressMessage(msg);
                         break;
                     case 2://刷新日志时间显示
                         handleFreshLogTimeMessage(msg);
                         break;
                     case 4: //上传进度
                         handleProgressMessage(msg);
                         break;
                     case 5://上传成功
                         handleSuccessMessage(msg);
                         break;
                     case 6://上传失败
                         handleFaileMessage(msg);
                         break;
                     default:
                         break;
                 }
             }
         };
         return handler;
     }

     public class MyThread extends Thread {
         private String name;

         public MyThread(String name) {
             this.name = name;
         }

         public void run() {
             MyFileModule.ResumableUpload(name,mHandler);
         }
     }

     private String buildZipFileName(String filename){
         return filename+".gz";
     }

     private void UploadFiles(){

         new Thread() {
             @Override
             public void run() {
                 super.run();
                 if(CheckedlistStr.size()>0) {

                     //刷新进度指示：正在压缩
                     sendCmd(1,1);

                     ArrayList<String> gzfileArray = new ArrayList<String>();
                     for (int index = 0; index < CheckedlistStr.size(); index++) {
                         String[]  filelist = new String[1];
                         filelist[0] = CheckedlistStr.get(index);
                         if(filelist[0].indexOf(".gz")>-1){
                             LogUtils.d(MainActivity.class,"gz文件，不需要压缩："+filelist[0]);
                             gzfileArray.add(filelist[0]);
                         }else{
                             String UploadFileName = buildZipFileName(filelist[0]);
                             String UploadFileFullName = MyFileModule.GetRootPath()+UploadFileName;
                             boolean result = MyFileModule.ZipFiles(MyFileModule.GetRootPath(),filelist,UploadFileFullName);
                             if(result == true){
                                 LogUtils.i(MainActivity.class,"压缩文件"+filelist[0]+"成功！");
                                 gzfileArray.add(UploadFileName);
                             }else{
                                 LogUtils.e(MainActivity.class,"压缩文件"+filelist[0]+"失败！");
                             }
                         }
                     }

                     //刷新进度指示：压缩完成准备上传
                     sendCmd(1,2);

                     //计算所有要上传文件的大小
                     long totSize = 0;
                     for(int index = 0;index<gzfileArray.size();index++){
                         totSize += MyFileModule.GetFileSize(gzfileArray.get(index));
                     }

                     //创建线程开始上传
                     MyFileModule.InitUploadStatus(gzfileArray.size(),totSize);
                     for(int index = 0;index<gzfileArray.size();index++){
                         Thread thread = new MyThread(gzfileArray.get(index));
                         thread.start();
                     }
                 }else{
                     LogUtils.e(MainActivity.class,"未选中要上传的文件！");
                 }
             }
         }.start();
     }

     private void DeleteOneFile(String filename){
         LogUtils.d(MainActivity.class,"要删除的文件或文件夹："+ filename);
         MyFileModule.DeleteFile0rDir(filename, false);

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
                MyFileModule.DeleteFile0rDir(deletefileArray.get(index), false);
            }
            RefreshListView();

        }else{
            LogUtils.e(MainActivity.class,"未选中要删除的文件或文件夹！");
        }
     }

     private void stop_loggging_timer(boolean now){
         if(now == true){
             logging_timer.cancel();
             logging_timer = null;
             logging_start_timer_count = 0;
             logging_stop_timer_count = 0;
             logging_stop_flag = false;
             logging_count++;
             sendCmd(2,null);
         }else{
             logging_stop_flag = true;
         }
     }

     private void start_logging_timer(){
         logging_timer = new Timer();
         logging_timer.schedule(new TimerTask() {
             @Override public void run() {

                 logging_start_timer_count++;
                 if(logging_stop_flag == true){
                     logging_stop_timer_count++;
                     if(logging_stop_timer_count <5 ){
                         sendCmd(2,getResources().getString(R.string.log_disconnect));
                     }else{
                         logging_timer.cancel();
                         logging_timer = null;
                         logging_start_timer_count = 0;
                         logging_stop_timer_count = 0;
                         logging_stop_flag = false;
                         sendCmd(2,null);
                     }
                 }else{
                     if(logging_start_timer_count > 5){
                         int timer_count = logging_start_timer_count - 5;
                         int hour = timer_count/3600;
                         int minute = (timer_count-hour*3600)/60;
                         int second = (timer_count-hour*3600-minute*60);

                         String text = String.format("%02d",hour)+":"+String.format("%02d",minute)+":"+String.format("%02d",second);
                         sendCmd(2,text);
                     }else{
                         sendCmd(2,getResources().getString(R.string.log_connect));
                     }
                 }

             } },1,1000);
     }
     //获取基本信息
     private void getbasicinfo(){
         BasicInfo.getSN();
         BasicInfo.getIMEI();
         BasicInfo.getIMSI();
         BasicInfo.getSystemVersion();
         BasicInfo.getSystemModel();
         BasicInfo.getDeviceBrand();
         BasicInfo.getPLMNs();
     }

     //获得位置信息
     private void getLocation() {
         mlocation = new MyLocation();
         //获取cellid
         MyLocation.CoarseLocation coarse_location = mlocation.get_coarse_location();
         if((coarse_location != null)&&(coarse_location.type != 0xff)){
             LogUtils.i(MainActivity.class,"get coarse location information!");
         }else{
             LogUtils.e(MainActivity.class,"not get coarse location information!");
         }
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
                 }else{
                     LogUtils.i(MainActivity.class,"success to get location information!");
                     //mlocation.begin_detail_location(mlocation.get_Latitude_result(),mlocation.get_Longitude_result());
                 }
             }else{
                 if(!((mlocation.get_Latitude_result() == 0)&&(mlocation.get_Longitude_result() == 0))){
                     location_timer_count = 0;
                     location_timer.cancel();
                     location_timer = null;
                     mlocation.end_fine_location();
                     LogUtils.i(MainActivity.class,"success to get location information!");
                     //mlocation.begin_detail_location(mlocation.get_Latitude_result(),mlocation.get_Longitude_result());
                 }
             }
         } },1,2000);

     }

     private String select_filter(){

         String filter_name = null;
         String finger = BasicInfo.getFinger();
         if(finger.indexOf("BLUBOO/S3/S3:8.1.0/O11019/1532067291:userdebug/release-keys")>-1){ //S3
             filter_name = "catcher_filter_1_ulwctg_n_Skyroam_Filter_S3_L4C_OTA_NAS.bin";
         } else if(finger.indexOf("WIKO/W_K600ID/W_K600:8.1.0/O11019/1544008159:user/release-keys")>-1){
             filter_name = "catcher_filter_1_ulwctg_n_Skyroam_Filter_Wiko_UserDebug_User_P5.bin";
         } else if(finger.indexOf("WIKO/W_K600ID/W_K600:8.1.0/O11019/1542601555:userdebug/release-keys") > -1){
           filter_name = "catcher_filter_1_ulwctg_n_Skyroam_Filter_Wiko_UserDebug_P52.bin";
         } else{
             LogUtils.e(MainActivity.class,"unknown finger:"+finger);
         }

         return filter_name;
     }

     private void start_catch_log(){

         getbasicinfo();
         //getLocation();
         start_logging_timer();

         new Thread() {
             @Override
             public void run() {
                 super.run();
                 String filter_name = select_filter();
                 if((MyMtkLogger.CopyFilterFile(MainActivity.this,filter_name) == true)
                         &&(MyMtkLogger.StartmMtkLog(MainActivity.this,filter_name) == true)) {
                     ;
                 }else {
                     LogUtils.e(MainActivity.class, "begin to catch log failed!");
                     stop_loggging_timer(true);
                 }
             }
         }.start();
     }

     private void stop_catch_log(){
         stop_loggging_timer(false);
         if(MyMtkLogger.StopMtkLog() == true){
            ;
         }else{
             LogUtils.e(MainActivity.class,"stop to catch log failed!");
         }
     }

     private void DeleteBtnHandle(){
         DeleteSelectFiles(false);
     }

     //上传日志 上传的文件先打包，然后删除文件，上传压缩包
     private void UploadBtnHandle(){
         if(mHandler != null){
             UploadFiles();
         }
     }

     //抓取日志
     private void CatchBtnHandle(){
         //catch log的时候首先将filter bin 写到sd卡
         logging_count++;
         if (logging_count % 2 == 1) {
             start_catch_log();
         }
         else {
             stop_catch_log();
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
