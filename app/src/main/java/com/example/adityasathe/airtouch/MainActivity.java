


package com.example.adityasathe.airtouch;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;

public class MainActivity extends Activity {


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Switch sw = (Switch) findViewById(R.id.switch1);
        if (isServiceRunning(EyeKeeper.class)) {
            sw.setChecked(true);
        } else {
            sw.setChecked(false);
        }

        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startService(new Intent(getApplicationContext(), EyeKeeper.class));
                } else {
                    stopService(new Intent(getApplicationContext(), EyeKeeper.class));
                }
            }
        });

        Button setApps = (Button) findViewById(R.id.setApps);
        setApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setAppsToMove();
            }
        });
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager mn = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo sinfo : mn.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(sinfo.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public void setAppsToMove() {
        Intent i = new Intent(this, ChooseAppsActivity.class);
        startActivity(i);
    }

}