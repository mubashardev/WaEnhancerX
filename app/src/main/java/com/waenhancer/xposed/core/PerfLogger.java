package com.waenhancer.xposed.core;

import android.os.SystemClock;

import de.robv.android.xposed.XposedBridge;

public final class PerfLogger {
    private static final String TAG = "[WAE-PERF] ";
    private static final long DEFAULT_THRESHOLD_MS = 4L;

    private PerfLogger() {
    }

    public static long start() {
        return SystemClock.elapsedRealtimeNanos();
    }

    public static void end(String label, long startNs) {
        end(label, startNs, DEFAULT_THRESHOLD_MS);
    }

    public static void end(String label, long startNs, long thresholdMs) {
        long elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L;
        if (elapsedMs >= thresholdMs) {
            XposedBridge.log(TAG + label + " took " + elapsedMs + "ms");
        }
    }

    public static void log(String label, long elapsedMs, long thresholdMs) {
        if (elapsedMs >= thresholdMs) {
            XposedBridge.log(TAG + label + " took " + elapsedMs + "ms");
        }
    }
}
