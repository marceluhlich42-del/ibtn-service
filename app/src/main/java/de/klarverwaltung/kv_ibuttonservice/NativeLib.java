package de.klarverwaltung.kv_ibuttonservice;

import android.os.Build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

class NativeLib {

    static {
        System.loadLibrary("kv_ibuttonservice");
    }

    boolean openRchDallas(){
        return openDevice("/dev/input/" + findRchDallas()) == 1;
    }

    String findRchDallas(){
        String[] strArr = new String[3];
        strArr[0] = "/system/bin/sh";
        strArr[1] = "-c";
        StringBuilder sb = new StringBuilder("busybox find /sys/class/input -path \\*event\\* -name name -follow -maxdepth 3 -exec ls {} + -exec cat {} + | busybox sed ':a;N;/name\\n/s/\\n/:/;ta;P;D' | grep \"SZLIF MSR R03\"");
        sb.append(" | grep -v \"Control\"");
        sb.append(" | busybox sed -r 's/.*event([0-9]+).*/\\1/'");
        strArr[2] = sb.toString();
        try {
            String readLine = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(strArr).getInputStream())).readLine();
            if (readLine == null) {
                return null;
            }
            return "event" + readLine;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * A native method that is implemented by the 'kv_ibuttonservice' native library,
     * which is packaged with this application.
     */
    public native int openDevice(String path);
    public native void closeDevice();

    public native IbuttonResult readDallas();
}