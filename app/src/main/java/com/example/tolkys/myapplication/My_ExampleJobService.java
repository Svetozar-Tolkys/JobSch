package com.example.tolkys.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
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
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.UUID;

/**
 * Created by Tolkys on 12/22/2018.
 */

public class My_ExampleJobService extends JobService {

    private static final String TAG = "my_ExampleJobService";
    private boolean jobCancelled = false;

    private Handler handler = new Handler();
    private static int notificationId = 0;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothManager mBluetoothManager;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private AdvertiseSettings advertiseSettings;
    private AdvertiseData advertiseData;

    private UUID SERVICE_UUID = UUID.fromString("0000152B-0000-1000-8000-00805F9B34FB");
    private UUID CHARACTERISTIC_COUNTER_UUID = UUID.fromString("0000152A-0000-1000-8000-00805F9B34FB");
    private UUID DESCRIPTOR_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    NotificationManager nmgr;
    Notification mNotification;
    public static final String NOTIFICATION_CHANNEL_ID = "10124";

    private boolean isJustStarted = true;
    private long startFlagTimeout = 5000;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "Started");
        doBackgroundWork(params);
        return true;
    }

    private void doBackgroundWork(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                isJustStarted = true;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isJustStarted = false;
                    }
                }, startFlagTimeout);
                setupAdvertiser();
                startBleAdvertiser(getBaseContext());
                while(!jobCancelled) {
                    threadSleep(1000);
                    Log.d(TAG, "Work ");
                }
                stopBleAdvertiser();
                Log.d(TAG, "finished");
                jobFinished(params, true);
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

    private void setupAdvertiser() {

        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);

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

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Job cancelled");
        jobCancelled = true;
        return true;
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
                if (!isJustStarted) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {

                            Context application = getApplicationContext();
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
}
