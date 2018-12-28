package com.skyroam.SkyLogger;

import java.io.File;
import java.util.Calendar;

public class MyLogcat {

    private String mFilepath;
    private static String timestamp;

    public MyLogcat() {
        File path;
        Calendar calendar = Calendar.getInstance();
        timestamp = String.format("%04d%02d%02d_%02d_%02d_%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH)+1,
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                calendar.get(Calendar.SECOND));
        mFilepath = MyFileModule.GetRootPath();
    }

    public void begincatchlog() {
        catchmainlog();
        catchradiolog();
    }

    public void stopcatchlog(){
        ShCommand.CommandResult result = ShCommand.execCommand("ps", false);
        String count[] = result.successMsg.split("\\s+");
        for(int i=0;i<count.length;i++)
        {
            if(count[i].indexOf("logcat") != -1){
                String cmd = "kill "+count[i-7];
                LogUtils.d(MyLogcat.class,"logcat 执行kill命令:"+"\n"+cmd);
                ShCommand.execCommand(cmd, false);
            }
        }
    }

    public void catchmainlog(){

        new Thread() {
            @Override
            public void run() {
                try {
                    String full_file_name = mFilepath+"mainlog_"+timestamp+".txt";
                    String cmd = "logcat -b main -f "+full_file_name;
                    ShCommand.execCommand(cmd,false);
                } catch (Exception e) {
                    LogUtils.d(MyLogcat.class,"read logcat process failed. message: " + e.getMessage());
                }
            }
        }.start();
    }

    public void catchradiolog(){
        new Thread() {
            @Override
            public void run() {
                try {
                    String full_file_name = mFilepath+"radiolog_"+timestamp+".txt";
                    String command = "logcat -b radio -f "+full_file_name;
                    ShCommand.execCommand(command,false);
                } catch (Exception e) {
                    LogUtils.d(MyLogcat.class,"read logcat process failed. message: " + e.getMessage());
                }
            }
        }.start();
    }
}
