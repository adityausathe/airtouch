package com.example.adityasathe.airtouch;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static android.content.ContentValues.TAG;

// todo: consider multithreading concerns while sharing variables between threads
public class EyeKeeper extends Service implements SensorEventListener {

    private static final String PATTERN_APP_ASSIGNMENT_FILE = "PackageInfo";
    private BroadcastReceiver volumeChangeEventReceiver;

    private transient boolean collectedEnoughSamples;
    private transient boolean isRecordingPattern;
    private transient long prevAccChangeEventTimeStamp;
    private transient boolean isAppsAssignedToPatterns;
    private transient boolean isDecodingInProgress;

    private List<float[]> accDataSamples;

    private List<List<String>> allowedPatterns;
    private String[] assignedApps;

    private transient List<String> decodedPattern;
    private AudioManager audioManager;

    public EyeKeeper() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!checkIfAppsAreAssignedToPatterns()) {
            Toast.makeText(getApplicationContext(), "Set the moves first", Toast.LENGTH_LONG).show();
            isAppsAssignedToPatterns = false;
        } else {
            assignedApps = getAssignedApps();
            isAppsAssignedToPatterns = true;
        }

        initAllowedPatterns();

        prevAccChangeEventTimeStamp = System.currentTimeMillis();
        accDataSamples = new ArrayList<>();


        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            Log.i(TAG, "onCreate: found accelerometer sensor");
            Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            throw new RuntimeException("accelerometer sensor not supported");
        }


        collectedEnoughSamples = false;
        isRecordingPattern = false;
        isDecodingInProgress = false;

        // piggyback on volume-buttons to get our trigger for recording the patterns.
        volumeChangeEventReceiver = new BroadcastReceiver() {
            private transient long prevVolChangeEventTimeStamp = System.currentTimeMillis();
            private transient int prevVolumeLevel = -1;

            // todo: there's scope to tune the start and stop trigger-recognition logic;
            @Override
            public void onReceive(Context context, Intent intent) {
                int streamVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
                Log.i(TAG, "onReceive: vol: " + streamVolume);
                if (prevVolumeLevel == streamVolume) {
                    return;
                } else {
                    prevVolumeLevel = streamVolume;
                }
                if (!isAppsAssignedToPatterns) {
                    Log.w(TAG, "onReceive: Apps are not assigned to the patterns.");
                    return;
                }
                long currentEventTime = System.currentTimeMillis();
                long timeDelta = currentEventTime - prevVolChangeEventTimeStamp;

                Log.d(TAG, MessageFormat.format("onReceive: collectedEnoughSamples= {0}, interval= {1}", collectedEnoughSamples, timeDelta));

                // probably not bulletproof against all imaginable race-conditions that might happen.
                // Also, it can be improved considering accessibility aspects.
                if (timeDelta < 750 && !(isRecordingPattern || isDecodingInProgress)) {
                    // we're not recording and we received two consecutive button presses, thus start.
                    collectedEnoughSamples = false;
                    startRecordingPattern();
                } else if (timeDelta > 1500 && isRecordingPattern) {
                    // we've recorded enough samples.
                    collectedEnoughSamples = true;
                } else if (timeDelta < 750 && isRecordingPattern && collectedEnoughSamples) {
                    // we're recording and we received two consecutive button presses, thus stop.
                    stopRecordingAndDecodePattern();
                }
                prevVolChangeEventTimeStamp = currentEventTime;
            }
        };
        audioManager = ((AudioManager) getSystemService(AUDIO_SERVICE));
        audioManager.registerMediaButtonEventReceiver(new ComponentName(getPackageName(), "myreceiver"));
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        registerReceiver(volumeChangeEventReceiver, filter);
    }

    private void startRecordingPattern() {
        isRecordingPattern = true;
        Log.i(TAG, "onReceive: Recording Moves...");
        Toast.makeText(getApplicationContext(), "Started recording moves...", Toast.LENGTH_SHORT).show();
        reloadAssignedAppsChangesIfNeeded();
    }

    private void stopRecordingAndDecodePattern() {
        if (!isDecodingInProgress) {
            isDecodingInProgress = true;
            isRecordingPattern = false;

            Log.i(TAG, "onReceive: Stop Recording Pattern");
            Toast.makeText(getApplicationContext(), "Completed recording moves.", Toast.LENGTH_SHORT).show();

            processRecordedSamplesAndInterpretPattern();
            if (decodedPattern != null) {
                findApp(decodedPattern);
            }
            isDecodingInProgress = false;
        }
    }

    private void initAllowedPatterns() {
        allowedPatterns = new ArrayList<>();
        allowedPatterns.add(Collections.singletonList("horizontal"));
        allowedPatterns.add(Collections.singletonList("vertical"));
        allowedPatterns.add(Collections.singletonList("RHSInclined"));
        allowedPatterns.add(Collections.singletonList("LHSInclined"));
        allowedPatterns.add(Arrays.asList("RHSInclined", "vertical"));
        allowedPatterns.add(Arrays.asList("LHSInclined", "vertical"));
        allowedPatterns.add(Arrays.asList("RHSInclined", "horizontal"));
        allowedPatterns.add(Arrays.asList("LHSInclined", "horizontal"));
        allowedPatterns.add(Arrays.asList("RHSInclined", "LHSInclined"));
    }

    private void findApp(List<String> decodedPattern) {
        for (int i = 0; i < allowedPatterns.size(); i++) {
            if (decodedPattern.equals(allowedPatterns.get(i))) {
                Log.i(TAG, "findApp: app found=" + assignedApps[i] + " for decoded-pattern = " + decodedPattern);
                launchApp(assignedApps[i]);
            }
        }
    }


    private void launchApp(String app) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(app);
        if (null == launch) {
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_FROM_BACKGROUND);
        startActivity(launch);
    }

    /**
     * Currently broken on SDK API=24 :(
     */
    private void processRecordedSamplesAndInterpretPattern() {
        List<float[]> filteredSamples = new ArrayList<>();

        float[] prevAcc = accDataSamples.get(0);
        for (int index = 1; index < accDataSamples.size(); index++) {
            float[] currentAcc = accDataSamples.get(index);
            float[] deltaAcc = new float[3];
            int f = 0;
            for (int d = 0; d < 2; d++) {
                deltaAcc[d] = currentAcc[d] - prevAcc[d];
                if ((deltaAcc[d] > -2) && (deltaAcc[d] < 2)) {
                    f++;
                    deltaAcc[d] = 0;
                }
            }
            if (f != 2)
                filteredSamples.add(deltaAcc);

            prevAcc = accDataSamples.get(index);
        }

        detectMovementDirection(filteredSamples);

        accDataSamples.clear();
    }

    private void detectMovementDirection(List<float[]> filteredSamples) {
        decodedPattern = new ArrayList<>();
        List<String> rawPattern = new ArrayList<>();
        for (float[] sample : filteredSamples) {

            if ((sample[0] == 0) && (sample[1] == 0)) {
                //rawPattern.add("S");
                continue;
            }
            if (sample[0] == 0) {
                rawPattern.add("V");
                continue;
            }

            float angle = sample[1] / sample[0];

            if ((angle > 3.75) || (angle < -3.75)) {
                rawPattern.add("V");
            } else if ((angle > -0.27) && (angle < 0.27)) {
                rawPattern.add("H");
            } else if ((angle > 0.27) && (angle < 3.75)) {
                rawPattern.add("RHS");
            } else if ((angle < -0.27) && (angle > -3.75)) {
                rawPattern.add("LHS");
            }

        }
        int i = 1;
        for (String rp : rawPattern) {
            Log.d(TAG, "detectMovementDirection: rawPattern= " + rp + "-" + i++);
        }

        decodePattern(filteredSamples, rawPattern);
    }


    private void decodePattern(List<float[]> filteredData, List<String> rawPattern) {
        float[] hCap = new float[1024];
        float[] vCap = new float[1024];
        float[] lCap = new float[1024];
        float[] rCap = new float[1024];

        int[] hCnt = new int[1024];
        int[] vCnt = new int[1024];
        int[] lCnt = new int[1024];
        int[] rCnt = new int[1024];

        int index = 0;
        int hlu = 0, vlu = 0, llu = 0, rlu = 0;

        int mCounter = 0;
        int maxSegmentSize = 30;

        for (String token : rawPattern) {
            float acc = (float) Math.sqrt(filteredData.get(index)[0] * filteredData.get(index)[0] + filteredData.get(index)[1] * filteredData.get(index)[1]);

            if (index != 0) {
                hCap[index] = hCap[index - 1];
                vCap[index] = vCap[index - 1];
                lCap[index] = lCap[index - 1];
                rCap[index] = rCap[index - 1];
            } else {
                hCap[index] = 0;
                vCap[index] = 0;
                lCap[index] = 0;
                rCap[index] = 0;
            }

            switch (token) {

                case "H": {
                    hCnt[index]++;
                    hCap[index] += 0.4f * acc + 0.6f * hCnt[index];
                    hlu = index;
                    break;
                }
                case "V": {
                    vCnt[index]++;
                    vCap[index] += 0.4f * acc + 0.6f * vCnt[index];
                    vlu = index;
                    break;
                }
                case "LHS": {
                    lCnt[index]++;
                    lCap[index] += 0.4f * acc + 0.6f * lCnt[index];
                    llu = index;
                    break;
                }
                case "RHS": {
                    rCnt[index]++;
                    rCap[index] += 0.4f * acc + 0.6f * rCnt[index];
                    rlu = index;
                    break;
                }
            }

            if ((++mCounter >= maxSegmentSize)) {
                float maxx = Math.max(hCap[index], Math.max(rCap[index], Math.max(vCap[index], lCap[index])));
                if (maxx == hCap[index]) {
                    if ((index - hlu) > (maxSegmentSize / 3)) {
                        System.out.println("[+++++++++] move recorded: horizontal");
                        decodedPattern.add("horizontal");
                        hCap[index] = 0;
                        rCap[index] -= rCap[hlu];
                        vCap[index] -= vCap[hlu];
                        lCap[index] -= lCap[hlu];

                        mCounter = index - hlu;
                        hCnt[index] -= hCnt[hlu];
                        vCnt[index] -= vCnt[hlu];
                        lCnt[index] -= lCnt[hlu];
                        rCnt[index] -= rCnt[hlu];

                    }
                } else if (maxx == vCap[index]) {
                    if ((index - vlu) > (maxSegmentSize / 3)) {
                        System.out.println("[+++++++++] move recorded: vertical");
                        decodedPattern.add("vertical");
                        vCap[index] = 0;
                        rCap[index] -= rCap[vlu];
                        hCap[index] -= hCap[vlu];
                        lCap[index] -= lCap[vlu];

                        mCounter = index - vlu;
                        hCnt[index] -= hCnt[vlu];
                        vCnt[index] -= vCnt[vlu];
                        lCnt[index] -= lCnt[vlu];
                        rCnt[index] -= rCnt[vlu];

                    }
                } else if (maxx == lCap[index]) {
                    if ((index - llu) > (maxSegmentSize / 3)) {
                        System.out.println("[+++++++++] move recorded: lhs");
                        decodedPattern.add("LHSInclined");
                        lCap[index] = 0;
                        rCap[index] -= rCap[llu];
                        vCap[index] -= vCap[llu];
                        hCap[index] -= hCap[llu];

                        mCounter = index - llu;
                        hCnt[index] -= hCnt[llu];
                        vCnt[index] -= vCnt[llu];
                        lCnt[index] -= lCnt[llu];
                        rCnt[index] -= rCnt[llu];

                    }
                } else if (maxx == rCap[index]) {
                    if ((index - rlu) > (maxSegmentSize / 3)) {
                        System.out.println("[+++++++++] move recorded: rhs");
                        decodedPattern.add("RHSInclined");
                        rCap[index] = 0;
                        hCap[index] -= hCap[rlu];
                        vCap[index] -= vCap[rlu];
                        lCap[index] -= lCap[rlu];

                        mCounter = index - rlu;
                        hCnt[index] -= hCnt[rlu];
                        vCnt[index] -= vCnt[rlu];
                        lCnt[index] -= lCnt[rlu];
                        rCnt[index] -= rCnt[rlu];
                    }
                }

            }
            index++;
        }

        index--;

        if ((++mCounter >= maxSegmentSize)) {
            float maxx = Math.max(hCap[index], Math.max(rCap[index], Math.max(vCap[index], lCap[index])));
            if (maxx == hCap[index]) {
                System.out.println("[+++++++++] move recorded: horizontal");
                decodedPattern.add("horizontal");
                hCap[index] = 0;
                rCap[index] -= rCap[hlu];
                vCap[index] -= vCap[hlu];
                lCap[index] -= lCap[hlu];

                hCnt[index] -= hCnt[hlu];
                vCnt[index] -= vCnt[hlu];
                lCnt[index] -= lCnt[hlu];
                rCnt[index] -= rCnt[hlu];

            } else if (maxx == vCap[index]) {
                System.out.println("[+++++++++] move recorded: vertical");
                decodedPattern.add("vertical");
                vCap[index] = 0;
                rCap[index] -= rCap[vlu];
                hCap[index] -= hCap[vlu];
                lCap[index] -= lCap[vlu];

                hCnt[index] -= hCnt[vlu];
                vCnt[index] -= vCnt[vlu];
                lCnt[index] -= lCnt[vlu];
                rCnt[index] -= rCnt[vlu];

            } else if (maxx == lCap[index]) {
                System.out.println("[+++++++++] move recorded: lhs");
                decodedPattern.add("LHSInclined");
                lCap[index] = 0;
                rCap[index] -= rCap[llu];
                vCap[index] -= vCap[llu];
                hCap[index] -= hCap[llu];

                hCnt[index] -= hCnt[llu];
                vCnt[index] -= vCnt[llu];
                lCnt[index] -= lCnt[llu];
                rCnt[index] -= rCnt[llu];

            } else if (maxx == rCap[index]) {
                System.out.println("[+++++++++] move recorded: rhs");
                decodedPattern.add("RHSInclined");
                rCap[index] = 0;
                hCap[index] -= hCap[rlu];
                vCap[index] -= vCap[rlu];
                lCap[index] -= lCap[rlu];

                hCnt[index] -= hCnt[rlu];
                vCnt[index] -= vCnt[rlu];
                lCnt[index] -= lCnt[rlu];
                rCnt[index] -= rCnt[rlu];
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand: EyeKeeper service started");
        return super.onStartCommand(intent, flags, startId);
    }


    @Override
    public void onDestroy() {
        unregisterReceiver(volumeChangeEventReceiver);
        Log.i(TAG, "onDestroy: EyeKeeper service stopped");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long curTime = System.currentTimeMillis();

        if ((curTime - prevAccChangeEventTimeStamp) > 0 && isRecordingPattern) {
            prevAccChangeEventTimeStamp = curTime;
            float[] sampleHolder = new float[3];
            System.arraycopy(event.values, 0, sampleHolder, 0, 3);
            accDataSamples.add(sampleHolder);
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

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

    /**
     * since we're in a scope of a background service, user might change pattern-app assignment without restarting the service.
     * Hence, check if such modification has happened and refresh the apps if it did happen.
     */
    private void reloadAssignedAppsChangesIfNeeded() {
        File isModified = new File(getFilesDir(), "isModified");
        if (isModified.exists()) {
            assignedApps = getAssignedApps();
            isModified.delete();
        }
    }
}
