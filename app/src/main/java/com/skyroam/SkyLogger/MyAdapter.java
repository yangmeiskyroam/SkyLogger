package com.skyroam.SkyLogger;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;

public class MyAdapter extends BaseAdapter {
        public static HashMap<Integer, Boolean> isSelected;
        private Context context = null;
        private LayoutInflater inflater = null;
        private List<HashMap<String, Object>> list = null;
        private String keyString[] = null;
        private String itemString = null; // 记录每个item中textview的值
        private int idValue[] = null;// id值

        public MyAdapter(Context context, List<HashMap<String, Object>> list,
                int resource, String[] from, int[] to) {
            this.context = context;
            this.list = list;
            keyString = new String[from.length];
            idValue = new int[to.length];
            System.arraycopy(from, 0, keyString, 0, from.length);
            System.arraycopy(to, 0, idValue, 0, to.length);
            inflater = LayoutInflater.from(context);
            init();
        }

        // 初始化 设置所有checkbox都为未选择
        public void init() {
            isSelected = new HashMap<Integer, Boolean>();
            for (int i = 0; i < list.size(); i++) {
                isSelected.put(i, false);
            }
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int arg0) {
            return list.get(arg0);
        }

        @Override
        public long getItemId(int arg0) {
            return 0;
        }

        @Override
        public View getView(int position, View view, ViewGroup viewGroup) {
            ViewHolder holder = new ViewHolder();

            if (view == null) {
                view = inflater.inflate(R.layout.listviewitem, null);
            }
            holder.tv = (TextView) view.findViewById(R.id.item_tv);
            holder.tv_z = (TextView) view.findViewById(R.id.item_tv_z);
            holder.cb = (CheckBox) view.findViewById(R.id.item_cb);
            view.setTag(holder);

            HashMap<String, Object> map = list.get(position);
            if (map != null) {
                itemString = (String) map.get(keyString[0]);
                holder.tv.setText(itemString);
                itemString = (String) map.get(keyString[1]);
                holder.tv_z.setText(itemString);
            }
            holder.cb.setChecked(isSelected.get(position));
            return view;
        }
    }