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
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class IButtonService extends Service {
    private int broadcastIndex = 0;
    private SerialInputOutputManager usbIoManager;

    private static final String CHANNEL_ID = "KV-IButtonServiceChannel";

    public static final String LOCAL_BROADCAST = "de.klarverwaltung.local_broadcast";


    private NativeLib lib;

    private volatile boolean isRunning;
    private boolean isStarted = false;

    private UsbSerialPort usbSerialPort = null;
    private boolean useUsbFallback = false;

    private final ByteArrayOutputStream usbBuffer = new ByteArrayOutputStream();

    @Override
    public void onCreate() {
        super.onCreate();
        lib = new NativeLib();

        // 1. Setup Notification Channel
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "KV-IButton Service",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }

        // 2. Try Native Connection
        boolean nativeSuccess = lib.openRchDallas();

        if (!nativeSuccess) {
            setupUsbFallback();
        } else {
            // Only start the manual polling thread if native succeeded
            startNativePollingThread();
        }
    }

    private void setupUsbFallback() {
        try {
            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (usbManager != null) {
                HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
                UsbDevice targetDevice = null;

                for (UsbDevice device : deviceList.values()) {
                    // Looking for the standard CH340/CH341 Vendor and Product IDs
                    if (device.getVendorId() == 6790 && device.getProductId() == 21795) {
                        targetDevice = device;
                        break;
                    }
                }

                if (targetDevice != null) {

                    // Use the built-in default prober - no custom drivers needed
                    UsbSerialProber prober = UsbSerialProber.getDefaultProber();
                    UsbSerialDriver driver = prober.probeDevice(targetDevice);

                    if (driver != null) {
                        UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());
                        if (connection != null) {
                            usbSerialPort = driver.getPorts().get(0);
                            usbSerialPort.open(connection);
                            usbSerialPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                            useUsbFallback = true;

                            // 3. Start the async reader
                            startUsbIoManager();

                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startUsbIoManager() {
        usbIoManager = new SerialInputOutputManager(usbSerialPort, new SerialInputOutputManager.Listener() {
            @Override
            public void onNewData(byte[] data) {
                for (byte b : data) {
                    // 1. Add the incoming byte to our buffer
                    usbBuffer.write(b);

                    // 2. Check if this byte is our end operator (0x63 or 0x64)
                    if (b == 0x63 || b == 0x64) {
                        // Extract the complete message
                        byte[] completeMessage = usbBuffer.toByteArray();

                        // Clear the buffer immediately for the next incoming message
                        usbBuffer.reset();

                        // Process the full message
                        handleCompleteIbuttonRead(completeMessage, b);
                    }
                }
            }

            @Override
            public void onRunError(Exception e) {
                usbBuffer.reset();
                e.printStackTrace();
            }
        });

        // Starts the background thread automatically
        usbIoManager.start();
    }


    private void handleCompleteIbuttonRead(byte[] messageBytes, byte endOperator) {

        int ibuttonState = (endOperator == 0x63) ? 46 : 32;

        String asciiData = new String(messageBytes, StandardCharsets.US_ASCII);

        if(asciiData.length() >= 16 + 1){
            asciiData = asciiData.substring(1, 16 + 1);
        }

        IbuttonResult result = new IbuttonResult(asciiData, ibuttonState);

        broadcastIbuttonResult(result);
    }

    private void startNativePollingThread() {
        isRunning = true;
        new Thread(() -> {
            while (isRunning) {
                try {
                    IbuttonResult result = lib.readDallas();
                    if (result != null) {
                        broadcastIbuttonResult(result);
                    }

                    // Tip: If lib.readDallas() is non-blocking, add Thread.sleep(50)
                    // here to prevent 100% CPU usage.
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
        }).start();
    }

    private void broadcastIbuttonResult(IbuttonResult result) {
        if (result == null) return;

        IButtonEvent.getInstance().postEvent(result);

        Intent intent = new Intent("de.klarverwaltung.IButtonResult");
        intent.setComponent(new ComponentName(
                "de.klarverwaltung.taxoPos",
                "de.klarverwaltung.taxoPos.BootBroadcastReceiver"
        ));

        intent.putExtra("ibtn_result", result.content);
        intent.putExtra("ibtn_state", result.returnCode);
        intent.putExtra("ibtn_index", broadcastIndex++);

        sendBroadcast(intent);
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
