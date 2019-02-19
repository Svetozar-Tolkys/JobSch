package com.example.tolkys.myapplication;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Calendar;

public class My_MainActivity extends AppCompatActivity {

    private static final int JOB_ID = 11122;
    private static final String TAG = "my_MainActivity";
    private TextView textView;
    private AlarmManager am;
    private PendingIntent pendingIntent;
    private boolean isActivated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.text);

        am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, My_Broadcast.class);
        pendingIntent = PendingIntent.getBroadcast(this, 0,
                intent, PendingIntent.FLAG_CANCEL_CURRENT );

        refreshText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshText();
    }

    public void onClickSchedule(View view){
        PackageManager pm  = My_MainActivity.this.getPackageManager();
        ComponentName componentName = new ComponentName(My_MainActivity.this, My_Broadcast.class);
        pm.setComponentEnabledSetting(componentName,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        isActivated = true;
        saveState();
        restartNotify();
        refreshText();
    }

    public void onClickCancel(View view){
        PackageManager pm  = My_MainActivity.this.getPackageManager();
        ComponentName componentName = new ComponentName(My_MainActivity.this, My_Broadcast.class);
        pm.setComponentEnabledSetting(componentName,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        isActivated = false;
        saveState();
        refreshText();
    }

    private void refreshText() {
        textView = (TextView) findViewById(R.id.text);
        if (loadState()){
            textView.setText("Active");
        } else {
            textView.setText("Inactive");
        }
    }

    private void restartNotify() {

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        long time = calendar.getTimeInMillis();

        assert am != null;
        am.cancel(pendingIntent);
        am.set(AlarmManager.RTC_WAKEUP, time + 1000, pendingIntent);

        Log.d(TAG, "Starting..");
    }

    private void saveState() {
        byte data = isActivated ? (byte) 1 : 0;
        String name = "flag";
        File directory = new File(this.getFilesDir(), "states");
        if (!directory.exists()) directory.mkdir();
        try {
            File file = new File(directory, name);
            if (!file.exists()) {
                file.createNewFile();
            }
            else {
                file.delete();
                file.createNewFile();
            }
            FileOutputStream stream = new FileOutputStream(file);
            stream.write(new byte[] {data});
            stream.close();
        } catch (Exception e) {
            Log.e(TAG, "saveState() : " + e.toString());
        }
        Log.i(TAG, "saveState() : success "+data);
    }

    private boolean loadState() {
        File directory = new File(this.getFilesDir(), "states");
        if (directory.exists()) {
            File[] files = directory.listFiles();
            Log.i(TAG, "loadState() Size: " + files.length);
            for (int i = 0; i < files.length; i++) {
                int size = (int) files[i].length();
                byte[] bytes = new byte[size];
                try {
                    BufferedInputStream buf = new BufferedInputStream(new FileInputStream(files[i]));
                    buf.read(bytes, 0, size);
                    buf.close();
                } catch (Exception e) {
                    Log.e(TAG, "loadState() read error: " + e.toString());
                }
                boolean res = (bytes[0] == (byte) 1);
                Log.i(TAG, "loadState() Result: " + res);
                return res;

            }
        }
        return false;
    }
}
