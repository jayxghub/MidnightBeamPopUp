package dev.midnightbeam;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Compact, D-pad-first floating controls for Fire TV and Android TV.
 *
 * Four fixed preset slots. Selecting a slot loads its saved values; changing
 * a slider saves the current slot immediately, even if the filter is off.
 * All four presets are user-adjustable and survive normal app restarts.
 * The panel never scrolls and does not require a keyboard.
 */
public final class QuickPopupService extends Service {
    private static final String PREFS = "quick_popup";
    private static final String KEY_PRESETS = "presets";
    private static final String KEY_SELECTED = "selected";
    private static final String CHANNEL = "quick_popup";
    private static final int NOTE_ID = 10117;

    private static final int ESPRESSO = 0xff1c1311;
    private static final int COPPER = 0xff422519;
    private static final int CARD = 0xff32221b;
    private static final int CARD_DARK = 0xff281b16;
    private static final int AMBER = 0xffe8ac60;
    private static final int GLOW = 0xffffcc87;
    private static final int WARM_ORANGE = 0xffdf8a48;
    private static final int CREAM = 0xfff8ebdc;
    private static final int MUTED = 0xffbca28e;
    private static final int STROKE = 0xff6b4534;
    private static final int TRACK = 0xff5b4840;

    private static final String[] LABELS =
            {"Evening", "Wind Down", "Sleep", "Custom"};
    private static final String[] SYMBOLS =
            {"\u2600", "\u263e", "\u2727", "\u25ce"};

    private WindowManager windowManager;
    private View overlay;
    private JSONArray presets;
    private Button toggle;
    private final Button[] presetButtons = new Button[4];
    private SeekBar darkness;
    private SeekBar warmth;
    private SeekBar temperature;
    private TextView darknessValue;
    private TextView warmthValue;
    private TextView temperatureValue;
    private int selected = 0;
    private boolean filterEnabled = true;
    private boolean loading = false;
    private float scale = 1f;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager notifications =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notifications.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "MidnightBeam Quick", NotificationManager.IMPORTANCE_MIN));
            startForeground(NOTE_ID, new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setContentTitle("MidnightBeam Quick")
                    .setContentText("Night filter controls are open")
                    .setOngoing(true).build());
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Binding the same Stickman button toggles this panel open and closed.
        if (overlay != null) {
            closePanel();
            return START_NOT_STICKY;
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow MidnightBeam to display over other apps",
                    Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        loadState();
        try {
            showPanel();
        } catch (Exception error) {
            Toast.makeText(this, "Quick controls: " + error.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
            closePanel();
        }
        return START_NOT_STICKY;
    }

    private JSONObject preset(String name, int dark, int warm, int temp) {
        JSONObject result = new JSONObject();
        try {
            result.put("name", name);
            result.put("dark", dark);
            result.put("warm", warm);
            result.put("temp", temp);
        } catch (JSONException ignored) {}
        return result;
    }

    private JSONArray defaults() {
        JSONArray result = new JSONArray();
        result.put(preset(LABELS[0], 25, 35, 3500));
        result.put(preset(LABELS[1], 55, 65, 2400));
        result.put(preset(LABELS[2], 85, 90, 1200));
        result.put(preset(LABELS[3], 40, 50, 3000));
        return result;
    }

    private void loadState() {
        SharedPreferences quick = getSharedPreferences(PREFS, MODE_PRIVATE);
        JSONArray existing;
        try { existing = new JSONArray(quick.getString(KEY_PRESETS, "[]")); }
        catch (Exception e) { existing = new JSONArray(); }

        // Migrate the previous prototype's variable-length preset list into
        // exactly four named slots without losing matching stored values.
        JSONArray fixed = defaults();
        for (int i = 0; i < existing.length(); i++) {
            JSONObject old = existing.optJSONObject(i);
            if (old == null) continue;
            for (int j = 0; j < LABELS.length; j++) {
                if (LABELS[j].equalsIgnoreCase(old.optString("name"))) {
                    try {
                        fixed.put(j, preset(LABELS[j],
                                clamp(old.optInt("dark", fixed.optJSONObject(j).optInt("dark")), 0, 95),
                                clamp(old.optInt("warm", fixed.optJSONObject(j).optInt("warm")), 0, 100),
                                clamp(old.optInt("temp", fixed.optJSONObject(j).optInt("temp")), 1000, 6500)));
                    } catch (JSONException ignored) {}
                }
            }
        }
        presets = fixed;
        selected = clamp(quick.getInt(KEY_SELECTED, 0), 0, 3);
        filterEnabled = !getSharedPreferences(DimService.PREFS, MODE_PRIVATE)
                .getBoolean("off", false);
        persist();
    }

    private void persist() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_PRESETS, presets.toString())
                .putInt(KEY_SELECTED, selected)
                .apply();
    }

    private JSONObject current() {
        JSONObject obj = presets.optJSONObject(selected);
        return obj != null ? obj : defaults().optJSONObject(selected);
    }

    private void showPanel() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenW = metrics.widthPixels;
        int screenH = metrics.heightPixels;
        // Scale the controls on shorter 720p devices rather than introducing
        // a ScrollView. A 540dp-high design fits in the available TV height.
        float availableHeightDp = (screenH - 28f * metrics.density) / metrics.density;
        scale = Math.min(1f, Math.max(.67f, availableHeightDp / 544f));

        int width = Math.min(screenW - dp(18),
                Math.max(dp(320), Math.round(screenW * .295f)));
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        FrameLayout frame = new FrameLayout(this) {
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK ||
                        event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.getAction() == KeyEvent.ACTION_UP) closePanel();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        frame.setFocusableInTouchMode(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(13), dp(18), dp(15));
        GradientDrawable panelBg = gradient(18,
                new int[]{0xf840251b, 0xf9231915, 0xf91b1513},
                GradientDrawable.Orientation.TL_BR, STROKE);
        panel.setBackground(panelBg);
        panel.setElevation(dp(12));
        FrameLayout.LayoutParams panelParams =
                new FrameLayout.LayoutParams(-1, -2);
        frame.addView(panel, panelParams);

        // Thin copper/amber light band, using Android's native GPU gradient.
        View glowBand = new View(this);
        glowBand.setBackground(gradient(16,
                new int[]{0x22ffc47b, 0x10a96836, 0x00a96836},
                GradientDrawable.Orientation.LEFT_RIGHT, 0));
        panel.addView(glowBand, new LinearLayout.LayoutParams(-1, dp(5)));

        LinearLayout title = horizontal();
        TextView moon = text("\u263e", 25, GLOW, true);
        title.addView(moon, new LinearLayout.LayoutParams(dp(33), -2));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.addView(text("MidnightBeam", 17, CREAM, true));
        heading.addView(text("Q U I C K   C O N T R O L S", 9, AMBER, true));
        title.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button dismiss = button("\u2715", new Runnable() {
            @Override public void run() { closePanel(); }
        }, dp(30));
        dismiss.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15 * scale);
        title.addView(dismiss, new LinearLayout.LayoutParams(dp(36), dp(35)));
        panel.addView(title);
        panel.addView(space(12));

        // Prominent, compact, fully keyboard/D-pad accessible switch.
        LinearLayout switchCard = horizontal();
        switchCard.setPadding(dp(11), dp(5), dp(8), dp(5));
        switchCard.setBackground(gradient(12, new int[]{0xff4a2c1f, 0xff35221b},
                GradientDrawable.Orientation.LEFT_RIGHT, 0xff825033));
        TextView label = text("Night filter", 15, CREAM, true);
        switchCard.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        toggle = button("", new Runnable() {
            @Override public void run() {
                filterEnabled = !filterEnabled;
                refreshToggle();
                if (filterEnabled) applyFilter();
                else setFilterOff();
            }
        }, dp(40));
        toggle.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean focused) { refreshToggle(); }
        });
        switchCard.addView(toggle, new LinearLayout.LayoutParams(dp(85), dp(37)));
        panel.addView(switchCard);
        refreshToggle();
        panel.addView(space(18));

        darkness = sliderGroup(panel, "\u2600", "Darkness", AMBER, 95, 0);
        warmth = sliderGroup(panel, "\u2668", "Warm overlay", WARM_ORANGE, 100, 1);
        temperature = sliderGroup(panel, "\u2726", "Colour temperature", AMBER, 100, 2);

        SeekBar.OnSeekBarChangeListener changed = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                updateReadouts();
                if (fromUser && !loading) {
                    saveCurrentPreset();
                    if (filterEnabled) applyFilter();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        };
        darkness.setOnSeekBarChangeListener(changed);
        warmth.setOnSeekBarChangeListener(changed);
        temperature.setOnSeekBarChangeListener(changed);
        loadSelectedSliders();

        panel.addView(space(13));
        View separator = new View(this);
        separator.setBackgroundColor(0x555d3d2c);
        panel.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));
        panel.addView(space(11));

        LinearLayout presetTitle = horizontal();
        presetTitle.addView(text("Presets", 16, CREAM, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        presetTitle.addView(text("AUTO-SAVED", 10, AMBER, true));
        panel.addView(presetTitle);
        panel.addView(space(7));

        LinearLayout first = horizontal();
        LinearLayout second = horizontal();
        for (int i = 0; i < 4; i++) {
            final int index = i;
            Button presetButton = button("", new Runnable() {
                @Override public void run() { choosePreset(index); }
            }, dp(45));
            presetButton.setGravity(Gravity.CENTER);
            presetButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13 * scale);
            presetButton.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean focus) { refreshPresetButtons(); }
            });
            presetButtons[i] = presetButton;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(45), 1);
            lp.setMargins(dp(3), dp(2), dp(3), dp(2));
            if (i < 2) first.addView(presetButton, lp);
            else second.addView(presetButton, lp);
        }
        panel.addView(first);
        panel.addView(second);
        refreshPresetButtons();

        panel.addView(space(13));
        Button settings = button("\u2699  Open full settings", new Runnable() {
            @Override public void run() { openSettings(); }
        }, dp(44));
        settings.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13 * scale);
        settings.setBackground(gradient(12, new int[]{0xff413129, 0xff32251f},
                GradientDrawable.Orientation.LEFT_RIGHT, 0xff705141));
        panel.addView(settings, new LinearLayout.LayoutParams(-1, dp(44)));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        params.x = dp(12);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        overlay = frame;
        windowManager.addView(overlay, params);
        toggle.requestFocus();
    }

    private SeekBar sliderGroup(LinearLayout parent, String icon, String title,
                                int tint, int max, int which) {
        LinearLayout heading = horizontal();
        heading.addView(text(icon, 18, tint, true),
                new LinearLayout.LayoutParams(dp(29), -2));
        TextView label = text(title, 13, CREAM, false);
        heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text("", 13, GLOW, true);
        heading.addView(value);
        if (which == 0) darknessValue = value;
        if (which == 1) warmthValue = value;
        if (which == 2) temperatureValue = value;
        parent.addView(heading);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        bar.setFocusable(true);
        bar.setSplitTrack(false);
        bar.setProgressTintList(ColorStateList.valueOf(tint));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(TRACK));
        bar.setThumbTintList(ColorStateList.valueOf(GLOW));
        bar.setPadding(dp(5), 0, dp(5), 0);
        bar.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean focus) {
                label.setTextColor(focus ? GLOW : CREAM);
            }
        });
        parent.addView(bar, new LinearLayout.LayoutParams(-1, dp(30)));
        parent.addView(space(10));
        return bar;
    }

    private void choosePreset(int index) {
        selected = index;
        persist();
        loadSelectedSliders();
        refreshPresetButtons();
        if (filterEnabled) applyFilter();
        // Maintain intuitive focus on whichever preset was selected.
        if (presetButtons[index] != null) presetButtons[index].requestFocus();
    }

    private void loadSelectedSliders() {
        JSONObject p = current();
        loading = true;
        darkness.setProgress(clamp(p.optInt("dark", 30), 0, 95));
        warmth.setProgress(clamp(p.optInt("warm", 40), 0, 100));
        temperature.setProgress(sliderOfTemperature(p.optInt("temp", 3000)));
        loading = false;
        updateReadouts();
    }

    private void saveCurrentPreset() {
        JSONObject saved = preset(LABELS[selected], darkness.getProgress(),
                warmth.getProgress(), kelvinOfSlider(temperature.getProgress()));
        try { presets.put(selected, saved); } catch (JSONException ignored) {}
        persist();
    }

    private void updateReadouts() {
        if (darknessValue == null || warmthValue == null || temperatureValue == null ||
                darkness == null || warmth == null || temperature == null) return;
        darknessValue.setText(darkness.getProgress() + "%");
        warmthValue.setText(warmth.getProgress() + "%");
        temperatureValue.setText(kelvinOfSlider(temperature.getProgress()) + " K");
    }

    private void refreshToggle() {
        if (toggle == null) return;
        boolean focused = toggle.hasFocus();
        toggle.setText(filterEnabled ? "ON   \u25cf" : "OFF   \u25cb");
        toggle.setTextColor(filterEnabled ? ESPRESSO : CREAM);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13 * scale);
        toggle.setBackground(gradient(24,
                filterEnabled ? new int[]{0xfff3b661, 0xffffce87} :
                        new int[]{0xff584239, 0xff382c27},
                GradientDrawable.Orientation.LEFT_RIGHT,
                focused ? GLOW : (filterEnabled ? AMBER : STROKE)));
    }

    private void refreshPresetButtons() {
        for (int i = 0; i < 4; i++) {
            Button b = presetButtons[i];
            if (b == null) continue;
            boolean active = i == selected;
            boolean focused = b.hasFocus();
            b.setText(SYMBOLS[i] + "   " + LABELS[i]);
            b.setTextColor(active || focused ? GLOW : CREAM);
            b.setBackground(gradient(11,
                    active ? new int[]{0xff76502e, 0xff45291c} :
                            new int[]{CARD, CARD_DARK},
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    active || focused ? AMBER : STROKE));
        }
    }

    private void applyFilter() {
        // DimService's explicit "off=false" makes enabling deterministic
        // even after the user previously disabled the filter.
        Intent service = new Intent(this, DimService.class);
        service.putExtra("red", warmth.getProgress());
        service.putExtra("bright", Math.max(5, 100 - darkness.getProgress()));
        service.putExtra("temp", kelvinOfSlider(temperature.getProgress()));
        service.putExtra("off", false);
        startDimService(service);
    }

    private void setFilterOff() {
        Intent service = new Intent(this, DimService.class);
        service.putExtra("off", true);
        startDimService(service);
    }

    private void startDimService(Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
        } catch (Exception error) {
            Toast.makeText(this, "Filter unavailable: " +
                    error.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(intent); closePanel(); }
        catch (Exception ignored) {
            Toast.makeText(this, "Full settings could not open",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private Button button(String title, final Runnable click, int size) {
        final Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextColor(CREAM);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13 * scale);
        b.setPadding(dp(7), 0, dp(7), 0);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setBackground(gradient(11, new int[]{CARD, CARD_DARK},
                GradientDrawable.Orientation.LEFT_RIGHT, STROKE));
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { click.run(); }
        });
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean focused) {
                b.setTextColor(focused ? GLOW : CREAM);
                b.setBackground(gradient(11,
                        focused ? new int[]{0xff674329, 0xff40281d} :
                                new int[]{CARD, CARD_DARK},
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        focused ? GLOW : STROKE));
            }
        });
        return b;
    }

    private LinearLayout horizontal() {
        LinearLayout result = new LinearLayout(this);
        result.setGravity(Gravity.CENTER_VERTICAL);
        result.setOrientation(LinearLayout.HORIZONTAL);
        return result;
    }

    private TextView text(String value, int sp, int colour, boolean bold) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(colour);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * scale);
        label.setSingleLine(true);
        if (bold) label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private GradientDrawable gradient(int radius, int[] colours,
                                      GradientDrawable.Orientation orientation, int border) {
        GradientDrawable drawable =
                new GradientDrawable(orientation, colours);
        drawable.setCornerRadius(dp(radius));
        if (border != 0) drawable.setStroke(Math.max(1, dp(1)), border);
        return drawable;
    }

    private View space(int height) {
        View result = new View(this);
        result.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return result;
    }

    private int dp(float value) {
        return Math.max(1, Math.round(value *
                getResources().getDisplayMetrics().density * scale));
    }
    private int clamp(int n, int min, int max) {
        return Math.max(min, Math.min(max, n));
    }
    private int kelvinOfSlider(int pos) { return 6500 - 55 * pos; }
    private int sliderOfTemperature(int kelvin) {
        return clamp(Math.round((6500 - kelvin) / 55f), 0, 100);
    }

    private void closePanel() {
        if (overlay != null && windowManager != null) {
            try { windowManager.removeViewImmediate(overlay); }
            catch (Exception ignored) {}
            overlay = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        if (overlay != null && windowManager != null) {
            try { windowManager.removeViewImmediate(overlay); }
            catch (Exception ignored) {}
            overlay = null;
        }
        super.onDestroy();
    }
}
