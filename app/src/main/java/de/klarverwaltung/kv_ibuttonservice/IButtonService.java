package de.klarverwaltung.kv_ibuttonservice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDeviceConnection;
import android.content.Context;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.Ch34xSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;

public class IButtonService extends Service {


    private static final String CHANNEL_ID = "KV-IButtonServiceChannel";

    public static final String LOCAL_BROADCAST = "de.klarverwaltung.local_broadcast";


    private NativeLib lib;

    private volatile boolean isRunning;
    private boolean isStarted = false;

    private UsbSerialPort usbSerialPort = null;
    private boolean useUsbFallback = false;



    @Override
    public void onCreate() {
        super.onCreate();
        lib =  new NativeLib();
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "KV-IButton Service",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);


        boolean nativeSuccess = lib.openRchDallas(getApplicationContext());

        if (!nativeSuccess) {
            lib.logToFile(getApplicationContext(), "Native open failed. Attempting USB fallback.");
            try {
                UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                if (usbManager != null) {
                    HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
                    UsbDevice targetDevice = null;
                    for (UsbDevice device : deviceList.values()) {
                        if (device.getVendorId() == 6790 && device.getProductId() == 21795) {
                            targetDevice = device;
                            break;
                        }
                    }

                    if (targetDevice != null) {
                        lib.logToFile(getApplicationContext(), "Found target USB device: " + targetDevice.getDeviceName());
                        ProbeTable customTable = new ProbeTable();
                        customTable.addProduct(6790, 21795, Ch34xSerialDriver.class); // Trying CH34x driver as fallback

                        UsbSerialProber prober = new UsbSerialProber(customTable);
                        UsbSerialDriver driver = prober.probeDevice(targetDevice);

                        if (driver != null) {
                            UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());
                            if (connection != null) {
                                usbSerialPort = driver.getPorts().get(0);
                                usbSerialPort.open(connection);
                                usbSerialPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                                useUsbFallback = true;
                                lib.logToFile(getApplicationContext(), "USB port opened successfully.");
                            } else {
                                lib.logToFile(getApplicationContext(), "Failed to open USB device connection. Check permissions.");
                            }
                        } else {
                            lib.logToFile(getApplicationContext(), "No suitable USB serial driver found for device.");
                        }
                    } else {
                        lib.logToFile(getApplicationContext(), "Target USB device not found.");
                    }
                }
            } catch (Exception e) {
                lib.logToFile(getApplicationContext(), "USB Fallback exception: " + e.getMessage());
                e.printStackTrace();
            }
        }

        isRunning = true;
        new Thread(() -> {
            int index = 0;
            byte[] buffer = new byte[8192];
            while (isRunning) {
                try {
                    IbuttonResult result = null;

                    if (!useUsbFallback) {
                        result = lib.readDallas();
                    } else {
                        if (usbSerialPort != null) {
                            int len = usbSerialPort.read(buffer, 1000);
                            if (len > 0) {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < len; i++) {
                                    sb.append(String.format("%02X", buffer[i]));
                                }
                                String hexData = sb.toString();
                                lib.logToFile(getApplicationContext(), "USB READ: " + hexData);
                                // For testing we just write to log, but we can also mock a result
                                // result = new IbuttonResult(hexData, 0);
                            }
                        }
                    }

                    if(result != null){
                        IButtonEvent.getInstance().postEvent(result);

                        Intent intent = new Intent("de.klarverwaltung.IButtonResult");

                        intent.setComponent(new ComponentName(
                                "de.klarverwaltung.taxoPos",               // Replace with the target app's package name
                                "de.klarverwaltung.taxoPos.BootBroadcastReceiver" // Replace with the full class name of the receiver
                        ));


                        intent.putExtra("ibtn_result",result.content);
                        intent.putExtra("ibtn_state",result.returnCode);
                        intent.putExtra("ibtn_index",index++);


                        sendBroadcast(intent);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
        }).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(!isStarted) {
            isStarted = true;
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("KV-IButtonService läuft")
                    .setContentText("Dieser Service dient der Behandlung von Dallaskeyereignisse.")
                    .setSmallIcon(android.R.drawable.ic_notification_overlay)
                    .build();

            startForeground(1, notification);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (useUsbFallback && usbSerialPort != null) {
                try {
                    usbSerialPort.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                lib.closeDevice();
            }
        }
        finally {
            isRunning = false;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
