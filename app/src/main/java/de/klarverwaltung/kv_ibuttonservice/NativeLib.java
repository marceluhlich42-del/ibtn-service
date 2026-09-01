package de.klarverwaltung.kv_ibuttonservice;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

class NativeLib {

    static {
        System.loadLibrary("kv_ibuttonservice");
    }

    boolean openRchDallas(Context context){
        return openDevice("/dev/input/" + findRchDallas(context)) == 1;
    }

    String findRchDallas(Context context) {
        String vendorIdHex = "1a86";  // 6790 in hex
        String productIdHex = "5523"; // 21795 in hex

        String[] strArr = new String[3];
        strArr[0] = "/system/bin/sh";
        strArr[1] = "-c";

        StringBuilder sb = new StringBuilder();

        // 1. Primary check: Search by device name "SZLIF MSR R03"
        sb.append("NAME_MATCH=$(busybox find /sys/class/input -path \\*event\\* -name name -follow -maxdepth 3 ");
        sb.append("-exec ls {} + -exec cat {} + | busybox sed ':a;N;/name\\n/s/\\n/:/;ta;P;D' ");
        sb.append("| grep \"SZLIF MSR R03\" | grep -v \"Control\" | busybox sed -r 's/.*event([0-9]+).*/\\1/'); ");

        sb.append("if [ -n \"$NAME_MATCH\" ]; then ");
        sb.append("  echo \"event$NAME_MATCH\"; ");
        sb.append("else ");

        // 2. Fallback check: Search by Vendor ID (6790 / 0x1a86) and Product ID (21795 / 0x5523)
        sb.append("  busybox find /sys/class/input/ -name \"event*\" -maxdepth 1 | while read dev; do ");
        sb.append("    if [ -f \"$dev/device/id/vendor\" ] && [ -f \"$dev/device/id/product\" ]; then ");
        sb.append("      v=$(cat \"$dev/device/id/vendor\"); ");
        sb.append("      p=$(cat \"$dev/device/id/product\"); ");
        sb.append("      if { [ \"$v\" = \"").append(vendorIdHex).append("\" ] || [ \"$v\" = \"6790\" ]; } && ");
        sb.append("         { [ \"$p\" = \"").append(productIdHex).append("\" ] || [ \"$p\" = \"21795\" ]; }; then ");
        sb.append("        echo \"${dev##*/}\"; break; ");
        sb.append("      fi; ");
        sb.append("    fi; ");
        sb.append("  done; ");
        sb.append("fi");

        strArr[2] = sb.toString();

        try {
            Process process = Runtime.getRuntime().exec(strArr);
            String readLine = new BufferedReader(new InputStreamReader(process.getInputStream())).readLine();

            if (readLine == null || readLine.trim().isEmpty()) {
                logToFile(context, "SUCCESS (No match found): Neither device name nor VID/PID matched");
                return null;
            }

            String result = readLine.trim();
            logToFile(context, "SUCCESS: Found " + result);
            return result;

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            logToFile(context, "FAILURE: Exception occurred -> " + sw.toString());
            return null;
        }
    }

    public void logToFile(Context context, String message) {
        // Saves to: /storage/emulated/0/Android/data/<your.package.name>/files/app_log.txt
        File logDir = context.getExternalFilesDir(null);
        if (logDir == null) return;

        File logFile = new File(logDir, "app_log.txt");
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String formattedMessage = "[" + timestamp + "] " + message + "\n";

        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(formattedMessage);
        } catch (IOException e) {
            e.printStackTrace();
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