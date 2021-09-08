package com.example.adityasathe.airtouch;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import static android.content.ContentValues.TAG;

public class ChooseAppsActivity extends ListActivity {

    private static final String PATTERN_APP_ASSIGNMENT_FILE = "PackageInfo";

    private PatternMoveListAdapter patternMoveListAdapter;
    private String[] assignedApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_apps);


        List<String> allowedPatterns = new ArrayList<String>();

        allowedPatterns.add("horizontal");
        allowedPatterns.add("vertical");
        allowedPatterns.add("RHSInclined");
        allowedPatterns.add("LHSInclined");
        allowedPatterns.add("RHSInclined->vertical");
        allowedPatterns.add("LHSInclined->vertical");
        allowedPatterns.add("RHSInclined->horizontal");
        allowedPatterns.add("LHSInclined->horizontal");
        allowedPatterns.add("RHSInclined->LHSInclined");

        if (!checkIfAppsAreAssignedToPatterns()) {
            assignedApps = new String[allowedPatterns.size()];
        } else {
            assignedApps = getAssignedApps();
        }
        patternMoveListAdapter = new PatternMoveListAdapter(getApplicationContext(), R.layout.movelistelement, allowedPatterns, assignedApps);
        setListAdapter(patternMoveListAdapter);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    protected void onListItemClick(ListView l, View v, int moveCode, long id) {
        super.onListItemClick(l, v, moveCode, id);

        Intent i = new Intent(getApplicationContext(), AllAppsActivity.class);
        i.putExtra("moveCode", moveCode);
        startActivityForResult(i, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                int moveCode = data.getExtras().getInt("moveCode");
                String packageName = data.getExtras().getString("appname");
                String[] pac = patternMoveListAdapter.getData();
                pac[moveCode] = packageName;
                patternMoveListAdapter.setData(pac);
                patternMoveListAdapter.notifyDataSetChanged();
                Toast.makeText(getApplicationContext(), moveCode + ": " + packageName, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        persistAssignedApps();
        super.onDestroy();
    }

    private void persistAssignedApps() {
        FileOutputStream modFlag;
        try (FileOutputStream fOut = openFileOutput(PATTERN_APP_ASSIGNMENT_FILE, MODE_PRIVATE); ObjectOutputStream oOut = new ObjectOutputStream(fOut)) {
            oOut.writeObject(assignedApps);
            modFlag = openFileOutput("isModified", MODE_PRIVATE);
            modFlag.close();
        } catch (IOException e) {
            Log.e(TAG, "persistAssignedApps: error writing assigned apps to file", e);
        }
    }

    private boolean checkIfAppsAreAssignedToPatterns() {
        return new File(getFilesDir(), PATTERN_APP_ASSIGNMENT_FILE).exists();
    }

    private String[] getAssignedApps() {
        String[] packages;
        try (FileInputStream in = openFileInput(PATTERN_APP_ASSIGNMENT_FILE); ObjectInputStream oin = new ObjectInputStream(in)) {
            packages = (String[]) oin.readObject();
            return packages;
        } catch (IOException | ClassNotFoundException e) {
            Log.e(TAG, "getAssignedApps: Error while reading PatternsAssignedToApps config-file", e);
        }
        return null;
    }

}
