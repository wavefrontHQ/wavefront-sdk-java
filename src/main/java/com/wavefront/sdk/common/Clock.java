package com.wavefront.sdk.common;

public abstract class Clock {
    static long currTime = 0;

    static long nanoTime() {
        if (currTime == 0) {
            return System.nanoTime();
        }
        return currTime;
    }
}
