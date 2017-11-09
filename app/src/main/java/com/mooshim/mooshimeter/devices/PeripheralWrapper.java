/*
 * Copyright (c) Mooshim Engineering LLC 2015.
 *
 * This file is part of Mooshimeter-AndroidApp.
 *
 * Foobar is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Foobar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with Mooshimeter-AndroidApp.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.mooshim.mooshimeter.devices;

import android.content.Context;
import android.util.Log;

import com.idevicesinc.sweetblue.BleCharacteristic;
import com.idevicesinc.sweetblue.BleDevice;
import com.idevicesinc.sweetblue.ReadWriteListener;

import com.idevicesinc.sweetblue.BleDeviceState;
import com.idevicesinc.sweetblue.DeviceStateListener;
import com.mooshim.mooshimeter.interfaces.NotifyHandler;
import com.mooshim.mooshimeter.common.StatLockManager;
import com.mooshim.mooshimeter.common.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public class PeripheralWrapper {
    private static final String TAG="PeripheralWrapper";

    protected Context mContext;
    protected BleDevice mDevice;

    private Map<UUID,NotifyHandler> mNotifyCB;
    private final List<Runnable> mConnectCBs;
    private final List<Runnable> mDisconnectCBs;

    public static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private class Interruptable implements Callable<Void> {
        public int mRval = 0;
        @Override
        public Void call() throws InterruptedException {
            return null;
        }
    }

    // Anything that has to do with the BluetoothGatt needs to go through here
    private int protectedCall(final Interruptable r,boolean force_main_thread) {
        if(Util.onCBThread()) {
            Log.e(TAG,"DON'T DO BLE STUFF FROM THE CB THREAD!");
            new Exception().printStackTrace();
        }
        Runnable payload = new Runnable() {
            @Override
            public void run() {
                try {
                    r.call();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                }
            }
        };
        if(force_main_thread) {
            Util.blockUntilRunOnMainThread(payload);
        } else {
            payload.run();
        }
        return r.mRval;
    }

    private int protectedCall(final Interruptable r) {
        return protectedCall(r,false);
    }
    
    public PeripheralWrapper(final BleDevice device, final Context context) {
        mContext = context;
        mDevice = device;

        mDevice.setListener_State(new DeviceStateListener() {
            @Override
            public void onEvent(BleDevice.StateListener.StateEvent e) {
                if(e.didEnter(BleDeviceState.ADVERTISING)) {
                    for(Runnable cb : mDisconnectCBs) {
                        Util.dispatchCb(cb);
                    }
                }
                if(e.didEnter(BleDeviceState.CONNECTED)) {
                    for(Runnable cb : mDisconnectCBs) {
                        Util.dispatchCb(cb);
                    }
                }
            }
        });

        mNotifyCB          = new ConcurrentHashMap<>();

        mConnectCBs    = new ArrayList<>();
        mDisconnectCBs = new ArrayList<>();;
    }

    public void addConnectCB(Runnable cb) {
        synchronized (mConnectCBs) {
            mConnectCBs.add(cb);
        }
    }

    public void cancelConnectCB(Runnable cb) {
        synchronized (mConnectCBs) {
            if(mConnectCBs.contains(cb)) {
                mConnectCBs.remove(cb);
            }
        }
    }

    public void addDisconnectCB(Runnable cb) {
        synchronized (mDisconnectCBs) {
            mDisconnectCBs.add(cb);
        }
    }

    public void cancelDisconnectCB(Runnable cb) {
        synchronized (mDisconnectCBs) {
            if(mDisconnectCBs.contains(cb)) {
                mDisconnectCBs.remove(cb);
            }
        }
    }

    public boolean isConnected() {
        return mDevice.isAny(BleDeviceState.CONNECTING_OVERALL);
    }

    public boolean isConnecting() {
        return mDevice.isAny(BleDeviceState.CONNECTING);
    }

    public int connect() {
        return protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                mDevice.connect();
                return null;
            }
        });
    }

    public int disconnect() {
        return protectedCall(new Interruptable() {
            @Override
            public Void call() throws InterruptedException {
                mDevice.disconnect();
                return null;
            }
        });
    }

    public int reqRSSI() {
        return mDevice.getRssi();
    }

    public byte[] req(UUID uuid) {
        final StatLockManager l = new StatLockManager();
        final byte[][] rval = new byte[1][];
        mDevice.read(uuid,new BleDevice.ReadWriteListener() {
            @Override public void onEvent(BleDevice.ReadWriteListener.ReadWriteEvent result)
            {
                if( result.wasSuccess() )
                {
                    rval[0] = result.data();
                } else {
                    Log.e(TAG,"Read fail");
                }
                l.sig();
            }
        });
        if(l.awaitMilli(1000)) {
            //Timeout
            return null;
        }
        return rval[0];
    }

    public int send(final UUID uuid, final byte[] value) {
        final StatLockManager l = new StatLockManager();
        final int[] rval = new int[1];
        mDevice.write(uuid, value, new BleDevice.ReadWriteListener() {
            @Override
            public void onEvent(ReadWriteEvent e) {
                if(e.wasSuccess()) {
                    rval[0] = 0;
                } else {
                    rval[0] = e.status().ordinal();
                }
                l.sig();
            }
        });
        if(l.awaitMilli(1000)) {
            //Timeout
            return -1;
        }
        return rval[0];
    }

    public NotifyHandler getNotificationCallback(UUID uuid) {
        return mNotifyCB.get(uuid);
    }

    public boolean isNotificationEnabled(UUID uuid) {
        return mDevice.isNotifyEnabled(uuid);
    }

    public int enableNotify(final UUID uuid, final boolean enable, final NotifyHandler on_notify) {
        mDevice.enableNotify(uuid, new BleDevice.ReadWriteListener() {
            @Override
            public void onEvent(ReadWriteEvent e) {
                if(e.wasSuccess()) {
                    final byte[] payload = e.data().clone();
                    final double timestamp = Util.getUTCTime();
                    Util.dispatchCb(new Runnable() {
                        @Override
                        public void run() {
                            on_notify.onReceived(timestamp, payload);
                        }
                    });
                } else {
                    Log.e(TAG,"Notification failure");
                }
            }
        });
        // Set up the notify callback
        if(on_notify != null) {
            mNotifyCB.put(uuid, on_notify);
        } else {
            mNotifyCB.remove(uuid);
        }
        return 0;
    }

    public String getAddress() {
        return mDevice.getMacAddress();
    }

    public BleDevice getBLEDevice() {
        return mDevice;
    }
}