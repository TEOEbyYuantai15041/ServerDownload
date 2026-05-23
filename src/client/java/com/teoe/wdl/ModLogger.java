package com.teoe.wdl;

import java.util.ArrayList;
import java.util.List;

public class ModLogger {
    private static final List<String> logs = new ArrayList<>();
    private static final int MAX_LOGS = 100;

    public static void log(String message) {
        System.out.println("[TeoeWDL] " + message);
        synchronized (logs) {
            logs.add(message);
            if (logs.size() > MAX_LOGS) {
                logs.remove(0);
            }
        }
    }

    public static List<String> getLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }
}
