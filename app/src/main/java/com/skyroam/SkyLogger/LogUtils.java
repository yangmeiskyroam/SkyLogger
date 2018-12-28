package com.skyroam.SkyLogger;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtils {
     public static boolean isShowLog = true;
     private  static String getAppName() {
          try {
               PackageManager packageManager = MainActivity.getContext().getPackageManager();
               PackageInfo packageInfo = packageManager.getPackageInfo(MainActivity.getContext().getPackageName(), 0);
               int labelRes = packageInfo.applicationInfo.labelRes;
               return MainActivity.getContext().getResources().getString(labelRes);
          }catch (Exception e) {
               e.printStackTrace();
          }

          return null;
     }

     private static void writeToFile(char type, String tag, String msg) {
         MyFileModule fileModule = new MyFileModule();
         String logPath = fileModule.GetRootPath();
         String fileName = logPath + getAppName() + ".log";
         File file = new File(logPath);
         if (!file.exists()) {
             file.mkdirs();//创建父路径
         }

         Calendar calendar = Calendar.getInstance();
         String timestamp = String.format("%04d_%02d_%02d:%02d_%02d_%02d",
                 calendar.get(Calendar.YEAR),
                 calendar.get(Calendar.MONTH)+1,
                 calendar.get(Calendar.DAY_OF_MONTH),
                 calendar.get(Calendar.HOUR_OF_DAY),
                 calendar.get(Calendar.MINUTE),
                 calendar.get(Calendar.SECOND));
         String log = timestamp + " "+type + " " + tag + " " + msg + "\n";

         FileOutputStream fos = null;//FileOutputStream会自动调用底层的close()方法，不用关闭
         BufferedWriter bw = null;
         try {
             fos = new FileOutputStream(fileName, true);
             bw = new BufferedWriter(new OutputStreamWriter(fos));
             bw.write(log);
         }catch (FileNotFoundException e) {
             e.printStackTrace();
         }catch (IOException e) {
             e.printStackTrace();
         }finally {
             try {
                 if (bw != null) {
                     bw.close();
                 }
             }catch (IOException e) {
                 e.printStackTrace();
             }
         }
     }

     private static final char DEBUG = 'd';
     private static final char INFO = 'i';
     private static final char ERROR = 'e';

     public static void i(Object objTag, String msg) {
          String tag;
          // 如果objTag是String，则直接使用
          // 如果objTag不是String，则使用它的类名
          // 如果在匿名内部类，写this的话是识别不了该类，所以获取当前对象全类名来分隔
          if (objTag instanceof String) {
               tag = (String) objTag;
          } else if (objTag instanceof Class) {
               tag = ((Class) objTag).getSimpleName();
          } else {
               tag = objTag.getClass().getName(); String[] split = tag.split("\\.");
               tag=split[split.length-1].split("\\$")[0];
          }

          if (!(TextUtils.isEmpty(msg))) {
               writeToFile(INFO,tag,msg);
               if(isShowLog){
                    if(objTag instanceof Class) {
                         Logger logger = LoggerFactory.getLogger((Class) objTag);
                         logger.info(msg);
                    }
               }
          }
     }

     public static void e(Object objTag, String msg) {
          String tag;
          if (objTag instanceof String) {
               tag = (String) objTag;
          } else if (objTag instanceof Class) {
               tag = ((Class) objTag).getSimpleName();
          } else {
               tag = objTag.getClass().getName(); String[] split = tag.split("\\.");
               tag=split[split.length-1].split("\\$")[0];
          }

          if (!(TextUtils.isEmpty(msg))) {
               writeToFile(ERROR,tag,msg);
               if(isShowLog){
                    if(objTag instanceof Class) {
                         Logger logger = LoggerFactory.getLogger((Class) objTag);
                         logger.error(msg);
                    }
               }
          }
     }

     public static void d(Object objTag, String msg) {
          String tag;
          if (objTag instanceof String) {
               tag = (String) objTag;
          } else if (objTag instanceof Class) {
               tag = ((Class) objTag).getSimpleName();
          } else {
               tag = objTag.getClass().getName(); String[] split = tag.split("\\.");
               tag=split[split.length-1].split("\\$")[0];
          }

          if (!(TextUtils.isEmpty(msg))) {
               //writeToFile(DEBUG,tag,msg);
               if(isShowLog){
                    if(objTag instanceof Class) {
                         Logger logger = LoggerFactory.getLogger((Class) objTag);
                         logger.debug(msg);
                    }
               }
          }
     }
}
