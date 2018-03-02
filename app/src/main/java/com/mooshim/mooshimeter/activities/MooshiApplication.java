package com.mooshim.mooshimeter.activities;

import android.app.Application;

import com.mooshim.mooshimeter.BuildConfig;
import com.mooshim.mooshimeter.common.Util;
import com.crashlytics.android.Crashlytics;
import io.fabric.sdk.android.Fabric;
import timber.log.Timber;

public class MooshiApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());
        Util.init(this);
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }
}



