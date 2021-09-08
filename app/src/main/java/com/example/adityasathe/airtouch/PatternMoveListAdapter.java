package com.example.adityasathe.airtouch;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Aditya Sathe on 12/19/2015.
 */
public class PatternMoveListAdapter extends ArrayAdapter<String> {
    private List<String> appsList;
    private Context context;
    private String[] packageName;

    public PatternMoveListAdapter(Context context, int textViewResourceId,
                                  List<String> appsList, String[] packageName) {
        super(context, textViewResourceId, appsList);
        this.context = context;
        this.appsList = appsList;
        this.packageName = packageName;
    }

    public String[] getData() {
        return this.packageName;
    }

    public void setData(String[] packageName) {
        this.packageName = packageName;
    }

    @Override
    public int getCount() {
        return ((null != appsList) ? appsList.size() : 0);
    }

    @Override
    public String getItem(int position) {
        return ((null != appsList) ? appsList.get(position) : null);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (null == view) {
            LayoutInflater layoutInflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = layoutInflater.inflate(R.layout.movelistelement, null);
        }
        String data = appsList.get(position);
        if (null != data) {
            TextView moveName = (TextView) view.findViewById(R.id.move_name);
            TextView appName = (TextView) view.findViewById(R.id.app_paackage);
            moveName.setText(data);
            appName.setText(packageName[position]);
        }
        return view;
    }

}