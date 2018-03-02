package com.mooshim.mooshimeter.interfaces;

public abstract class NotifyHandler {
    public abstract void onReceived(double timestamp_utc, Object payload);
}
