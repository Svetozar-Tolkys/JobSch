package com.example.tolkys.myapplication;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.UUID;

import static android.content.Context.BLUETOOTH_SERVICE;
import static android.content.Context.POWER_SERVICE;


/**
 * Created by Tolkys on 12/26/2018.
 */

public class My_Broadcast extends BroadcastReceiver {

    private static final String TAG = "my_Broadcast";
    private static final int JOB_ID = 11122;
    private JobScheduler scheduler;
    private JobInfo jobInfo;

    private static long alarmTimer = 30*1000;

    private Handler handler = new Handler();
    private static int notificationId = 0;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothManager mBluetoothManager;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private AdvertiseSettings advertiseSettings;
    private AdvertiseData advertiseData;
    private Context mContext;
    private UUID SERVICE_UUID = UUID.fromString("02501801-420d-4048-a24e-18e60180e23c");

    NotificationManager nmgr;
    Notification mNotification;
    public static final String NOTIFICATION_CHANNEL_ID = "101243";

    @Override
    public void onReceive(final Context context, Intent intent) {

        mContext = context;
        ComponentName receiver = new ComponentName(context, My_Broadcast.class);

        try {
            PackageManager pm = context.getPackageManager();

            pm.setComponentEnabledSetting(receiver,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            Toast.makeText(context, "Caught exception", Toast.LENGTH_SHORT).show();
        }

        final AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                intent, PendingIntent.FLAG_CANCEL_CURRENT);
        assert am != null;
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + alarmTimer + 5000, pendingIntent);

        setupBleAdvertiser(context);
        startBleAdvertiser(context);
        new Thread(new Runnable() {
            @Override
            public void run() {

                threadSleep(alarmTimer);
                stopBleAdvertiser();
                assert am != null;
                am.cancel(pendingIntent);
                am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 100, pendingIntent);

            }
        }).start();
    }

    private void setupBleAdvertiser(Context context) {

        mBluetoothManager = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);

        bluetoothAdapter = mBluetoothManager.getAdapter();

        if (mBluetoothLeAdvertiser != null) {
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
            Log.d(TAG, "Adv stopped");
        }

        advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW)
                .build();

        advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                //.addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        // Init advertising.
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (mBluetoothLeAdvertiser == null) {
            Log.w(TAG, "Failed to create advertiser");

        }

        if (mBluetoothGattServer != null) {
            mBluetoothGattServer.close();
            Log.d(TAG, "Gatt closed");
        }

    }

    private void startBleAdvertiser(Context context) {
        mBluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, mAdvertiseCallback);
        // Starts server.
        mBluetoothGattServer = mBluetoothManager.openGattServer(context, mGattServerCallback);
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
        }
    }

    private void stopBleAdvertiser() {
        if (mBluetoothGattServer != null) {
            mBluetoothGattServer.close();
        }
        if (mBluetoothLeAdvertiser != null) {
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
        }
    }

    private void threadSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "LE Advertise Started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "LE Advertise Failed: " + errorCode);
        }
    };

    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothDevice CONNECTED: " + device);
                handler.post(new Runnable() {
                    @Override
                    public void run() {

                        Context application = mContext;
                        Bitmap icon = BitmapFactory.decodeResource(application.getResources(),
                                R.drawable.ic_launcher);
                        nmgr = (NotificationManager) application.getSystemService(Context.NOTIFICATION_SERVICE);
                        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(application)
                                .setSmallIcon(R.drawable.ic_vpn_key_black_24dp)
                                .setLargeIcon(icon)
                                .setContentTitle("Triggered")
                                .setAutoCancel(true)
                                .setPriority(2)
                                .setContentText("Door is open");

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            int importance = NotificationManager.IMPORTANCE_HIGH;
                            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "NOTIFICATION_CHANNEL_NAME", importance);
                            notificationChannel.enableLights(true);
                            notificationChannel.setLightColor(Color.RED);
                            notificationChannel.enableVibration(true);
                            assert nmgr != null;
                            mBuilder.setChannelId(NOTIFICATION_CHANNEL_ID);
                            nmgr.createNotificationChannel(notificationChannel);
                        }
                        assert nmgr != null;

                        mNotification = mBuilder.build();

                        nmgr.notify(notificationId++, mNotification);
                    }
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: " + device);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
        }
    };

}
