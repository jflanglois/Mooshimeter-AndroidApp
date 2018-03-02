package com.mooshim.mooshimeter.activities;

import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.CooldownTimer;
import com.mooshim.mooshimeter.common.MeterReading;
import com.mooshim.mooshimeter.common.SpeaksOnLargeChange;
import com.mooshim.mooshimeter.common.Util;
import com.mooshim.mooshimeter.databinding.ActivityMeterBinding;
import com.mooshim.mooshimeter.databinding.ElementDeviceActivityTitlebarBinding;
import com.mooshim.mooshimeter.devices.MooshimeterDeviceBase;
import com.mooshim.mooshimeter.interfaces.MooshimeterControlInterface;
import com.mooshim.mooshimeter.interfaces.MooshimeterDelegate;
import com.mooshim.mooshimeter.interfaces.NotifyHandler;

import java.util.List;

import timber.log.Timber;

public class DeviceActivity extends BaseActivity implements MooshimeterDelegate {
    public static final String AUTORANGE = "AUTORANGE";

    private ActivityMeterBinding viewBinding;
    private ElementDeviceActivityTitlebarBinding titlebarBinding;

    private static MooshimeterControlInterface.Channel chanEnum(int c) {
        return MooshimeterControlInterface.Channel.values()[c];
    }

    // BLE
    private MooshimeterDeviceBase mMeter;

    // GUI
    private final TextView[] value_labels = new TextView[2];

    private static final Button[] input_set_buttons = {null, null};
    private static final Button[] range_buttons = {null, null};
    private static final Button[] zero_buttons = {null, null};
    private static final Button[] sound_buttons = {null, null};

    private float batteryVoltage = 0;

    // GUI housekeeping
    private Drawable getAutoBG() {
        return getResources().getDrawable(R.drawable.button_auto);
    }

    private Drawable getNormalBG() {
        return getResources().getDrawable(R.drawable.button_normal);
    }

    // Helpers
    private CooldownTimer autorange_cooldown = new CooldownTimer();
    private SpeaksOnLargeChange speaksOnLargeChange = new SpeaksOnLargeChange();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);

        viewBinding = DataBindingUtil.setContentView(this, R.layout.activity_meter);

        // Bind the GUI elements
        value_labels[0] = viewBinding.ch1ValueLabel;
        value_labels[1] = viewBinding.ch2ValueLabel;

        input_set_buttons[0] = viewBinding.ch1InputSetButton;
        range_buttons[0] = viewBinding.ch1RangeButton;
        zero_buttons[0] = viewBinding.ch1ZeroButton;
        sound_buttons[0] = viewBinding.ch1SoundButton;

        input_set_buttons[1] = viewBinding.ch2InputSetButton;
        range_buttons[1] = viewBinding.ch2RangeButton;
        zero_buttons[1] = viewBinding.ch2ZeroButton;
        sound_buttons[1] = viewBinding.ch2SoundButton;

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
            titlebarBinding = ElementDeviceActivityTitlebarBinding.inflate(getLayoutInflater());
            actionBar.setCustomView(titlebarBinding.getRoot());
            actionBar.setDisplayShowCustomEnabled(true);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        setResult(RESULT_OK);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //this.optionsMenu = menu;
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.device_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.opt_prefs:
                pushActivityToStack(mMeter, PreferencesActivity.class);
                break;
            default:
                finish();
                return true;
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // I still don't understand why, but mMeter keeps getting set to null!  WHY ANDROID WHY?!
        if (mMeter == null) {
            Timber.e("GOT A NULL MMETER IN onPause!");
            return;
        }
        if (!(mMeter.speech_on.get(MooshimeterControlInterface.Channel.CH1) || mMeter.speech_on.get(MooshimeterControlInterface.Channel.CH2))) {
            Util.dispatch(() -> {
                mMeter.pause();
                mMeter.removeDelegate();
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final MooshimeterDelegate d = this;

        // When resuming, Android may have destroyed objects we care about
        // Just fall back to the scan screen...
        if (mMeter == null
                || !mMeter.isConnected()) {
            onBackPressed();
            return;
        }

        Util.dispatch(() -> {
            mMeter.addDelegate(d);
            mMeter.stream();
            Timber.i("Stream requested");
            refreshAllControls();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // For some reason, mMeter ends up null in lifecycle transitions sometimes
        // Double check here... still haven't figured this bug out.  FIXME
        Intent intent = getIntent();
        try {
            mMeter = (MooshimeterDeviceBase) getDeviceWithAddress(intent.getStringExtra("addr"));
        } catch (ClassCastException e) {
            Timber.e("Squirrelly ClassCastException! Diagnose me!");
            mMeter = null;
        }
        if (mMeter == null) {
            onBackPressed();
        }
    }

    private void setError(final String txt) {
        final Context c = this;
        runOnUiThread(() -> Toast.makeText(c, txt, Toast.LENGTH_LONG).show());
    }

    /////////////////////////
    // Widget Refreshers
    /////////////////////////

    private void refreshAllControls() {
        runOnUiThread(() -> {
            rateButtonRefresh();
            depthButtonRefresh();
            loggingButtonRefresh();
            for (int c = 0; c < 2; c++) {
                inputSetButtonRefresh(c);
                rangeButtonRefresh(c);
                soundButtonRefresh(c);
                zeroButtonRefresh(c, mMeter.getOffset(chanEnum(c)));
            }
        });
    }

    private void disableButtonRefresh(Button b, String title, boolean en) {
        final Drawable bg = en ? getNormalBG() : getAutoBG();
        b.setText(title);
        b.setBackground(bg);
    }

    private void autoButtonRefresh(final Button b, final String title, boolean auto) {
        final Drawable bg = auto ? getAutoBG() : getNormalBG();
        SpannableStringBuilder sb = new SpannableStringBuilder();

        String auto_string = auto ? "AUTO" : "MANUAL";
        sb.append(auto_string);
        sb.append("\n");
        int i = sb.length();
        sb.append(title);
        sb.setSpan(new RelativeSizeSpan((float) 1.6), i, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        Util.setText(b, sb);
        runOnUiThread(() -> {
            float button_height = b.getHeight(); // Button height in raw pixels
            b.setTextSize(TypedValue.COMPLEX_UNIT_PX, button_height / 3); // Divisor arrived at empirically
            b.setBackground(bg);
        });
    }

    private Drawable getDrawableByURI(String uri) {
        int imageResource = getResources().getIdentifier(uri, null, getPackageName());
        return getResources().getDrawable(imageResource);
    }

    private void refreshTitle() {
        // Approximate remaining charge
        double soc_percent = (batteryVoltage - 2.0) * 100.0;
        soc_percent = Math.max(0, soc_percent);
        soc_percent = Math.min(100, soc_percent);
        final Drawable bat_img = getDrawableByURI("drawable/bat_icon_" + Integer.toString((int) soc_percent));

        int rssiVal = mMeter.getRSSI();
        // rssi is always negative, map it to a percentage.
        // Let's just make -100db be 0%, and 0db be 100%
        rssiVal += 100;
        rssiVal = Math.max(0, rssiVal);
        rssiVal = Math.min(100, rssiVal);
        final Drawable rssi_img = getDrawableByURI("drawable/sig_icon_" + Integer.toString(rssiVal));

        runOnUiThread(() -> {
            titlebarBinding.titleTextview.setText(mMeter.getBLEDevice().getName());
            titlebarBinding.batStatImg.setImageDrawable(bat_img);
            titlebarBinding.rssiImg.setImageDrawable(rssi_img);
        });
    }

    private void mathLabelRefresh(final MeterReading val) {
        Util.setText(viewBinding.powerLabel, val.toString());
        Util.setText(viewBinding.powerButton, mMeter.getSelectedDescriptor(MooshimeterControlInterface.Channel.MATH).name);
    }

    private void mathButtonRefresh() {
        mathLabelRefresh(mMeter.getValue(MooshimeterControlInterface.Channel.MATH));
    }

    private void rateButtonRefresh() {
        int rate = mMeter.getSampleRateHz();
        String title = getString(R.string.rate_hz, rate);
        autoButtonRefresh(viewBinding.rateButton, title, mMeter.getRateAuto());
    }

    private void depthButtonRefresh() {
        int depth = mMeter.getBufferDepth();
        String title = getString(R.string.depth_sample, depth);
        autoButtonRefresh(viewBinding.depthButton, title, mMeter.getDepthAuto());
    }

    private void loggingButtonRefresh() {
        int s = mMeter.getLoggingStatus();
        final String title;
        final boolean logging_ok = s == 0;
        if (logging_ok) {
            title = mMeter.getLoggingOn() ? "Logging:ON" : "Logging:OFF";
        } else {
            title = mMeter.getLoggingStatusMessage();
        }
        runOnUiThread(() -> disableButtonRefresh(viewBinding.logButton, title, logging_ok));
    }

    private void inputSetButtonRefresh(final int c) {
        final String s = mMeter.getInputLabel(chanEnum(c));
        Util.setText(input_set_buttons[c], s);
    }

    private void rangeButtonRefresh(final int c) {
        String lval = mMeter.getRangeLabel(chanEnum(c));
        autoButtonRefresh(range_buttons[c], lval, mMeter.getRangeAuto(chanEnum(c)));
    }

    private void valueLabelRefresh(final int c, final MeterReading val) {
        final TextView v = value_labels[c];
        final String label_text = val.toString();
        Util.setText(v, label_text);
    }

    private void zeroButtonRefresh(final int c, MeterReading value) {
        Timber.i("zerorefresh");
        final String s;
        if (value.value == 0.0) {
            // No offset applied
            s = "ZERO";
        } else {
            s = value.toString();
        }
        Util.setText(zero_buttons[c], s);
    }

    private void soundButtonRefresh(final int c) {
        Timber.i("soundrefresh");
        final Button b = sound_buttons[c];
        final String s = "SOUND:" + (mMeter.speech_on.get(chanEnum(c)) ? "ON" : "OFF");
        Util.setText(b, s);
    }

    /////////////////////////
    // Button Click Handlers
    ////////////////////////

    PopupMenu popupMenu = null;

    private void makePopupMenu(List<String> options, View anchor, NotifyHandler on_choice) {
        if (popupMenu != null) {
            return;
        }
        popupMenu = new PopupMenu(this, anchor);
        popupMenu = Util.generatePopupMenuWithOptions(this, options, anchor, on_choice, () -> popupMenu = null);
    }

    private void onInputSetClick(final int c) {
        final List<MooshimeterDeviceBase.InputDescriptor> l = mMeter.getInputList(chanEnum(c));
        final List<String> sl = Util.stringifyCollection(l);
        makePopupMenu(sl, input_set_buttons[c], new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                popupMenu = null;
                if (0 != mMeter.setInput(chanEnum(c), l.get((Integer) payload))) {
                    setError("Invalid input change!");
                }
                refreshAllControls();
            }
        });
    }

    private void onRangeClick(final int c) {
        List<String> options = mMeter.getRangeList(chanEnum(c));
        options.add(0, AUTORANGE);
        makePopupMenu(options, range_buttons[c], new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                popupMenu = null;
                int choice = (Integer) payload;
                mMeter.setRangeAuto(chanEnum(c), choice == 0);
                if (!mMeter.getRangeAuto(chanEnum(c))) {
                    mMeter.setRange(chanEnum(c), mMeter.getSelectedDescriptor(chanEnum(c)).ranges.get(choice - 1));
                }
                refreshAllControls();
            }
        });
    }

    private void onSoundClick(final int c) {
        Timber.i("onCh" + c + "SoundClick");
        if (mMeter.speech_on.get(chanEnum(c))) {
            mMeter.speech_on.put(chanEnum(c), false);
        } else {
            mMeter.speech_on.put(chanEnum(c == 0 ? 1 : 0), false);
            mMeter.speech_on.put(chanEnum(c), true);
        }
        soundButtonRefresh(0);
        soundButtonRefresh(1);
    }

    public void onCh1InputSetClick(View v) {
        Timber.i("onCh1InputSetClick");
        onInputSetClick(0);
    }

    public void onCh1RangeClick(View v) {
        Timber.i("onCh1RangeClick");
        onRangeClick(0);
    }

    public void onCh2InputSetClick(View v) {
        Timber.i("onCh2InputSetClick");
        onInputSetClick(1);
    }

    public void onCh2RangeClick(View v) {
        Timber.i("onCh2RangeClick");
        onRangeClick(1);
    }

    public void onRateClick(View v) {
        Timber.i("onRateClick");
        List<String> options = mMeter.getSampleRateList();
        options.add(0, AUTORANGE);
        makePopupMenu(options, viewBinding.rateButton, new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                popupMenu = null;
                int choice = (Integer) payload;
                boolean new_rate_auto = (choice == 0);
                mMeter.setRateAuto(new_rate_auto);
                if (!new_rate_auto) {
                    mMeter.setSampleRateIndex(choice - 1);
                }
                refreshAllControls();
            }
        });
    }

    public void onDepthClick(View v) {
        Timber.i("onDepthClick");
        List<String> options = mMeter.getBufferDepthList();
        options.add(0, AUTORANGE);
        makePopupMenu(options, viewBinding.depthButton, new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                popupMenu = null;
                int choice = (Integer) payload;
                boolean new_depth_auto = choice == 0;
                mMeter.setDepthAuto(new_depth_auto);
                if (!new_depth_auto) {
                    mMeter.setBufferDepthIndex(choice - 1);
                }
                refreshAllControls();
            }
        });
    }

    public void onGraphClick(View v) {
        Timber.i("onGraphClick");
        pushActivityToStack(mMeter, GraphingActivity.class);
    }

    public void onLoggingClick(View v) {
        Timber.i("onLoggingClick");
        if (mMeter.getLoggingStatus() != 0) {
            setError(mMeter.getLoggingStatusMessage());
            mMeter.setLoggingOn(false);
        } else {
            mMeter.setLoggingOn(!mMeter.getLoggingOn());
        }
    }

    public void onZeroClick(final int c) {
        MooshimeterControlInterface.Channel channel = chanEnum(c);
        float offset = mMeter.getOffset(channel).value;
        if (offset == 0) {
            mMeter.setOffset(channel, mMeter.getValue(channel).value);
        } else {
            mMeter.setOffset(channel, 0);
        }
    }

    public void onCh1ZeroClick(View v) {
        Timber.i("onCh1ZeroClick");
        onZeroClick(0);
    }

    public void onCh1SoundClick(View v) {
        onSoundClick(0);
    }

    public void onCh2ZeroClick(View v) {
        Timber.i("onCh2ZeroClick");
        onZeroClick(1);
    }

    public void onCh2SoundClick(View v) {
        onSoundClick(1);
    }

    public void onPowerButtonClick(View v) {
        Timber.i("onPowerButtonClick");
        final List<MooshimeterDeviceBase.InputDescriptor> list = mMeter.getInputList(MooshimeterControlInterface.Channel.MATH);
        final List<String> slist = Util.stringifyCollection(list);
        makePopupMenu(slist, viewBinding.powerButton, new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                MooshimeterDeviceBase.InputDescriptor id = list.get((Integer) payload);
                mMeter.setInput(MooshimeterControlInterface.Channel.MATH, id);
                runOnUiThread(() -> mathButtonRefresh());
            }
        });
    }

    //////////////////////////
    // MooshimeterDelegate calls
    //////////////////////////

    @Override
    public void onDisconnect() {
        //transitionToActivity(mMeter, ScanActivity.class);
        finish();
    }

    @Override
    public void onRssiReceived(int rssi) {
        refreshTitle();
    }

    @Override
    public void onBatteryVoltageReceived(float voltage) {
        batteryVoltage = voltage;
        refreshTitle();
    }

    @Override
    public void onSampleReceived(double timestamp_utc, MooshimeterControlInterface.Channel c, MeterReading val) {
        switch (c) {
            case CH1:
            case CH2:
                valueLabelRefresh(c.ordinal(), val);
                // Run the autorange only on channel 2
                if (c == MooshimeterControlInterface.Channel.CH2 && autorange_cooldown.expired) {
                    autorange_cooldown.fire(500);
                    Util.dispatch(() -> {
                        if (mMeter.applyAutorange()) {
                            refreshAllControls();
                        }
                    });
                }
                if (mMeter.speech_on.get(c)) {
                    speaksOnLargeChange.decideAndSpeak(val);
                }
                break;
            case MATH:
                mathLabelRefresh(val);
                break;
        }
    }

    @Override
    public void onBufferReceived(double timestamp_utc, MooshimeterControlInterface.Channel c, float dt, float[] val) {

    }

    @Override
    public void onSampleRateChanged(int i, int sample_rate_hz) {
        rateButtonRefresh();
    }

    @Override
    public void onBufferDepthChanged(int i, int buffer_depth) {
        depthButtonRefresh();
    }

    @Override
    public void onLoggingStatusChanged(boolean on, int new_state, String message) {
        loggingButtonRefresh();
    }

    @Override
    public void onRangeChange(MooshimeterControlInterface.Channel c, MooshimeterDeviceBase.RangeDescriptor new_range) {
        rangeButtonRefresh(c.ordinal());
    }

    @Override
    public void onInputChange(MooshimeterControlInterface.Channel c, MooshimeterDeviceBase.InputDescriptor descriptor) {
        inputSetButtonRefresh(c.ordinal());
    }

    @Override
    public void onOffsetChange(MooshimeterControlInterface.Channel c, MeterReading offset) {
        zeroButtonRefresh(c.ordinal(), offset);
    }
}
