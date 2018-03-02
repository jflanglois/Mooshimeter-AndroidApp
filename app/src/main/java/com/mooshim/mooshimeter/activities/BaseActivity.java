package com.mooshim.mooshimeter.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.mooshim.mooshimeter.devices.BLEDeviceBase;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import timber.log.Timber;

public abstract class BaseActivity extends AppCompatActivity {
    // This is the master list of all Mooshimeters
    private static final Map<String, BLEDeviceBase> mMeterDict = new ConcurrentHashMap<>();

    public static int getNDevices() {
        synchronized (mMeterDict) {
            return mMeterDict.size();
        }
    }

    public static Collection<BLEDeviceBase> getDevices() {
        synchronized (mMeterDict) {
            return mMeterDict.values();
        }
    }

    public static void clearDeviceCache() {
        synchronized (mMeterDict) {
            mMeterDict.clear();
        }
    }

    public static BLEDeviceBase getDeviceWithAddress(String addr) {
        synchronized (mMeterDict) {
            return mMeterDict.get(addr);
        }
    }

    public static void putDevice(BLEDeviceBase device) {
        synchronized (mMeterDict) {
            mMeterDict.put(device.getAddress(), device);
        }
    }

    public static void removeDevice(BLEDeviceBase device) {
        synchronized (mMeterDict) {
            mMeterDict.remove(device.getAddress());
        }
    }

    protected void pushActivityToStack(BLEDeviceBase d, Class activity_class) {
        Intent intent = new Intent(this, activity_class);
        if (d != null) {
            intent.putExtra("addr", d.getAddress());
        }
        startActivityForResult(intent, 0);
    }

    protected String cname() {
        return this.getClass().getName();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Timber.d("What do I do with this?");
    }

    @Override
    protected void onCreate(Bundle bundle) {
        Timber.d("onCreate");
        super.onCreate(bundle);
    }

    @Override
    protected void onStart() {
        Timber.d("onStart");
        super.onStart();
    }

    @Override
    protected void onResume() {
        Timber.d("onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Timber.d("onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Timber.d("onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Timber.d("onDestroy");
        super.onDestroy();
    }
}
