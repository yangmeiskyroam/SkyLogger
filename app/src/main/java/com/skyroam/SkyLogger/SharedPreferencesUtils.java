package com.skyroam.SkyLogger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

public class SharedPreferencesUtils {

    private static final String config_file_name = "SkyloggerConfig";
    /**
     * 存储
     */
    public static void SetValue(String key, Object object,Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(config_file_name,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (object instanceof String) {
            editor.putString(key, (String) object);
        } else if (object instanceof Integer) {
            editor.putInt(key, (Integer) object);
        } else if (object instanceof Boolean) {
            editor.putBoolean(key, (Boolean) object);
        } else if (object instanceof Float) {
            editor.putFloat(key, (Float) object);
        } else if (object instanceof Long) {
            editor.putLong(key, (Long) object);
        } else {
            editor.putString(key, object.toString());
        }
        editor.commit();
    }

    /**
     * 获取保存的数据
     */
    public static Object GetValue(String key, Object defaultObject,Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(config_file_name,
                Context.MODE_PRIVATE);

        if (defaultObject instanceof String) {
            return sharedPreferences.getString(key, (String) defaultObject);
        } else if (defaultObject instanceof Integer) {
            return sharedPreferences.getInt(key, (Integer) defaultObject);
        } else if (defaultObject instanceof Boolean) {
            return sharedPreferences.getBoolean(key, (Boolean) defaultObject);
        } else if (defaultObject instanceof Float) {
            return sharedPreferences.getFloat(key, (Float) defaultObject);
        } else if (defaultObject instanceof Long) {
            return sharedPreferences.getLong(key, (Long) defaultObject);
        } else {
            return sharedPreferences.getString(key, null);
        }
    }

    /**
     * 移除某个key值已经对应的值
     */
    public static void Remove(String key,Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(config_file_name,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(key);
        editor.commit();
    }

    /**
     * 清除所有数据
     */
    public static void Clear(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(config_file_name,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.commit();
    }

    /**
     * 查询某个key是否存在
     */
    public static boolean Contain(String key,Context context) {
          SharedPreferences sharedPreferences = context.getSharedPreferences(config_file_name,
                Context.MODE_PRIVATE);
            return sharedPreferences.contains(key);
    }

    /**
     * 返回所有的键值对
     */
    public static Map<String, ?> GetAll(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(config_file_name,
                Context.MODE_PRIVATE);
        return sharedPreferences.getAll();
    }
}
