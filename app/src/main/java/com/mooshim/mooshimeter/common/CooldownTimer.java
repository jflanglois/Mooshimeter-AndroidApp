package com.mooshim.mooshimeter.common;

public class CooldownTimer {
    public boolean expired = true;
    private Runnable cb = () -> expired = true;

    public void fire(int ms) {
        Util.cancelDelayedCB(cb);
        Util.postDelayed(cb, ms);
        expired = false;
    }
}
