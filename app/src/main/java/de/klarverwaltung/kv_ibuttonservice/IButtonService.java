package de.klarverwaltung.kv_ibuttonservice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class IButtonService extends Service {


    private static final String CHANNEL_ID = "KV-IButtonServiceChannel";

    public static final String LOCAL_BROADCAST = "de.klarverwaltung.local_broadcast";


    private NativeLib lib;

    private volatile boolean isRunning;
    private boolean isStarted = false;



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


        lib.openRchDallas();
        isRunning = true;
        new Thread(() -> {
            int index = 0;
            while (isRunning) {
                try {
                    IbuttonResult result = lib.readDallas();

                    if(result != null){
                        IButtonEvent.getInstance().postEvent(result);

                        Intent intent = new Intent("de.klarverwaltung.IButtonResult");

                        intent.setComponent(new ComponentName(
                                "de.klarverwaltung.taxoPos",               // Replace with the target app's package name
                                "de.vaolo.taxoPos.BootBroadcastReceiver" // Replace with the full class name of the receiver
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
            lib.closeDevice();
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
