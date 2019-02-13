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
    private JobScheduler scheduler;
    private TextView textView;
    private AlarmManager am;
    private PendingIntent pendingIntent;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.text);

        ComponentName receiver = new ComponentName(this, My_Broadcast.class);

        PackageManager pm = this.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, My_Broadcast.class);
        pendingIntent = PendingIntent.getBroadcast(this, 0,
                intent, PendingIntent.FLAG_CANCEL_CURRENT );

        scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);

        refreshText();
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    public void onClickSchedule(View view){
        restartNotify();
        refreshText();
    }

    public void onClickCancel(View view){
        Log.d(TAG, "Clink cancelled");
        am.cancel(pendingIntent);
        scheduler.cancel(JOB_ID);
        refreshText();
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

    private void restartNotify() {

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.add(Calendar.SECOND, 10);
        long time = calendar.getTimeInMillis();

        assert am != null;
        am.cancel(pendingIntent);
        am.set(AlarmManager.RTC_WAKEUP, time + 1000, pendingIntent);

        Log.d(TAG, "Starting..");
    }
}
