package com.example.tolkys.myapplication;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.Calendar;

public class My_MainActivity extends AppCompatActivity {

    private static final int JOB_ID = 11122;
    private static final String TAG = "my_MainActivity";
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.text);
        refreshText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshText();
    }

    public void onClickSchedule(View view){
        ComponentName componentName = new ComponentName(this, My_ExampleJobService.class);
        JobInfo info = new JobInfo.Builder(JOB_ID, componentName)
                .setPersisted(true)
                .setMinimumLatency(10)
                .setOverrideDeadline(100)
                .build();

        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        int resultCode = scheduler.schedule(info);
        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            Log.d(TAG, "Job scheduled");
        } else {
            Log.d(TAG, "Job scheduling failed");
        }
        refreshText();
    }

    public void onClickCancel(View view){
        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        scheduler.cancel(JOB_ID);
        Log.d(TAG, "Job cancelled");
        refreshText();
    }

    private void refreshText() {
        textView = (TextView) findViewById(R.id.text);
        if (isJobServiceOn(this)){
            textView.setText("Active");
        } else {
            textView.setText("Inactive");
        }
    }

    public static boolean isJobServiceOn( Context context ) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService( Context.JOB_SCHEDULER_SERVICE ) ;

        boolean hasBeenScheduled = false ;

        assert scheduler != null;
        for ( JobInfo jobInfo : scheduler.getAllPendingJobs() ) {
            if ( jobInfo.getId() == JOB_ID ) {
                hasBeenScheduled = true ;
                break ;
            }
        }

        return hasBeenScheduled ;
    }
}
