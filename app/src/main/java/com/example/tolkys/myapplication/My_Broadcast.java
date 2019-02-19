package com.example.tolkys.myapplication;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;

import static android.content.Context.BLUETOOTH_SERVICE;
import static android.content.Context.POWER_SERVICE;
import static com.example.tolkys.myapplication.My_MainActivity.*;


/**
 * Created by Tolkys on 12/26/2018.
 */

public class My_Broadcast extends BroadcastReceiver {

    private static final String TAG = "my_Broadcast";

    private static long alarmTimer = 10*60*1000;

    private Handler handler = new Handler();
    private static int notificationId = 0;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothManager mBluetoothManager;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private AdvertiseSettings advertiseSettings;
    private AdvertiseData advertiseData;
    private Context mContext;

    private UUID SERVICE_UUID = UUID.fromString("0000152B-0000-1000-8000-00805F9B34FB");
    private UUID CHARACTERISTIC_COUNTER_UUID = UUID.fromString("0000152A-0000-1000-8000-00805F9B34FB");
    private UUID DESCRIPTOR_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private boolean isJustStarted = true;
    private boolean isCanceled = false;

    private int counter = 0;

    NotificationManager nmgr;
    Notification mNotification;
    public static final String NOTIFICATION_CHANNEL_ID = "101243";

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.d(TAG, "Come here!");
        mContext = context;
        final AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                intent, PendingIntent.FLAG_CANCEL_CURRENT);
        assert am != null;
        stopBleAdvertiser();
        setupBleAdvertiser();
        startBleAdvertiser(context);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + alarmTimer - 1000, pendingIntent);
        new Thread(new Runnable() {
            @Override
            public void run() {
                long inTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - inTime < alarmTimer) {
                    long upTime = System.currentTimeMillis();
                    while(System.currentTimeMillis() - upTime < 1000){
                        if (!loadState() | isCanceled) {
                            am.cancel(pendingIntent);
                            stopBleAdvertiser();
                            Log.d(TAG, "Terminated");
                            return;
                        }
                    }
                }
                stopBleAdvertiser();
                Log.d(TAG, "Finished");
            }
        }).start();
    }

    private void startBleAdvertiser(Context context) {
        mBluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, mAdvertiseCallback);
        // Starts server.
        mBluetoothGattServer = mBluetoothManager.openGattServer(context, mGattServerCallback);
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
        }
        mBluetoothGattServer.addService(createService());
    }

    private void setupBleAdvertiser() {

        mBluetoothManager = (BluetoothManager) mContext.getSystemService(BLUETOOTH_SERVICE);

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

    private BluetoothGattService createService() {
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Counter characteristic (read-only, supports subscriptions)
        BluetoothGattCharacteristic direction = new BluetoothGattCharacteristic(CHARACTERISTIC_COUNTER_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattDescriptor counterConfig = new BluetoothGattDescriptor(DESCRIPTOR_CONFIG_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        direction.addDescriptor(counterConfig);

        service.addCharacteristic(direction);
        return service;
    }

    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "LE Advertise Started.");
            counter = 0;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    isJustStarted = false;
                    Log.d(TAG, "Triggered");
                }
            },1000);
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "LE Advertise Failed: " + errorCode);
            if (counter < 10) {
                mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
                threadSleep(1000);
                mBluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, mAdvertiseCallback);
                counter++;
                Log.d(TAG, "Restart "+counter);
            } else {
                isCanceled = true;
            }
        }
    };

    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothDevice CONNECTED: " + device);
                if (!isJustStarted) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {

                            Context application = mContext.getApplicationContext();
                            Bitmap icon = BitmapFactory.decodeResource(application.getResources(),
                                    R.drawable.ic_launcher);
                            nmgr = (NotificationManager) application.getSystemService(Context.NOTIFICATION_SERVICE);
                            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(application)
                                    .setSmallIcon(R.drawable.ic_vpn_key_black_24dp)
                                    .setLargeIcon(icon)
                                    .setContentTitle("Triggered")
                                    .setAutoCancel(true)
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
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: " + device);
                mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
                threadSleep(1000);
                mBluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, mAdvertiseCallback);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_COUNTER_UUID.equals(characteristic.getUuid())) {
                int value;
                if (isJustStarted) {
                    value = 0;
                } else {
                    value = 1;
                }
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[] {(byte)value});
                Log.i(TAG, "Read counter: "+value);
            } else {
                // Invalid characteristic
                Log.w(TAG, "Invalid Characteristic Read: " + characteristic.getUuid());
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
        }
    };

    private boolean loadState() {
        File directory = new File(mContext.getFilesDir(), "states");
        if (directory.exists()) {
            File[] files = directory.listFiles();
            // Log.i(TAG, "loadState() Size: " + files.length);
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
                return (bytes[0] == (byte) 1);
                // Log.i(TAG, "loadState() Long: " + prevTime);
            }
        }
        return false;
    }

    public byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    public long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getLong();
    }

}
