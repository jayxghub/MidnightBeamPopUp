package dev.midnightbeam;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Remote/D-pad focused overlay; lives inside MidnightBeam to access its protected DimService. */
public final class QuickPopupService extends Service {
    private static final String PREFS = "quick_popup";
    private static final String KEY_PRESETS = "presets";
    private static final String CHANNEL = "quick_popup";
    private static final int NOTE_ID = 10117;
    private static final int BG = 0xff201916;
    private static final int PANEL = 0xff342a23;
    private static final int GOLD = 0xffe2b77c;
    private static final int TEXT = 0xfff9ead7;
    private static final int MUTED = 0xffc6b49e;

    private WindowManager manager;
    private View window;
    private JSONArray presets;
    private LinearLayout presetBar;
    private EditText nameInput;
    private SeekBar dark, warm, kelvin;
    private TextView darkText, warmText, kelvinText, statusText;
    private int chosen = -1;
    private boolean loading = false;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "MidnightBeam Quick", NotificationManager.IMPORTANCE_MIN));
            startForeground(NOTE_ID, new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setContentTitle("MidnightBeam Quick")
                    .setContentText("Floating controls open").setOngoing(true).build());
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (window != null) {
            dismiss();
            return START_NOT_STICKY;
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant MidnightBeam Display over other apps", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        loadPresets();
        try {
            showPopup();
        } catch (Exception exception) {
            Toast.makeText(this, "Popup failed: " + exception.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            dismiss();
        }
        return START_NOT_STICKY;
    }

    private void loadPresets() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        try { presets = new JSONArray(p.getString(KEY_PRESETS, "[]")); }
        catch (Exception ignored) { presets = new JSONArray(); }
        if (presets.length() == 0) {
            presets.put(record("Evening", 30, 25, 3000));
            presets.put(record("Wind Down", 65, 55, 1800));
            presets.put(record("Sleep", 85, 85, 1200));
            store();
        }
    }

    private JSONObject record(String name, int darkness, int warmth, int temp) {
        JSONObject item = new JSONObject();
        try {
            item.put("name", name);
            item.put("dark", darkness);
            item.put("warm", warmth);
            item.put("temp", temp);
        } catch (JSONException ignored) {}
        return item;
    }

    private void showPopup() {
        manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        // Right-hand quick panel: approximately 28% of the screen width, with
        // minimum D-pad-readable width for 720p televisions.
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int width = Math.min(screenWidth - dp(20),
                Math.max(dp(310), Math.round(screenWidth * 0.28f)));
        int height = Math.min(screenHeight - dp(20), Math.round(screenHeight * 0.91f));
        final LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(12));
        panel.setBackground(shape(BG, 15, GOLD));

        ScrollView root = new ScrollView(this) {
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK ||
                    event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.getAction() == KeyEvent.ACTION_UP) dismiss();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        root.setFillViewport(false);
        root.setFocusableInTouchMode(true);
        root.addView(panel);

        LinearLayout title = row();
        title.addView(text("NIGHT CONTROLS", 17, TEXT, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button close = button("Close", new Runnable() {
            @Override public void run() { dismiss(); }
        });
        title.addView(close);
        panel.addView(title);
        panel.addView(gap(8));
        panel.addView(text("PRESETS  •  OK to apply", 13, GOLD, true));
        // Vertical preset buttons keep each choice reachable with Up/Down.
        presetBar = new LinearLayout(this);
        presetBar.setOrientation(LinearLayout.VERTICAL);
        panel.addView(presetBar);
        panel.addView(gap(6));

        darkText = text("Darkness", 17, TEXT, true);
        dark = addSlider(panel, darkText, 95, 35);
        warmText = text("Warm colour overlay", 17, TEXT, true);
        warm = addSlider(panel, warmText, 100, 25);
        kelvinText = text("Colour temperature", 17, TEXT, true);
        kelvin = addSlider(panel, kelvinText, 100, sliderOfTemp(3000));

        SeekBar.OnSeekBarChangeListener change = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                refreshLabels();
                if (fromUser && !loading) applyValues();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        };
        dark.setOnSeekBarChangeListener(change);
        warm.setOnSeekBarChangeListener(change);
        kelvin.setOnSeekBarChangeListener(change);
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        loading = true;
        dark.setProgress(preferences.getInt("dark", 35));
        warm.setProgress(preferences.getInt("warm", 25));
        kelvin.setProgress(sliderOfTemp(preferences.getInt("temp", 3000)));
        loading = false;
        refreshLabels();

        panel.addView(gap(9));
        panel.addView(text("PRESET NAME", 13, GOLD, true));
        nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setText("Custom");
        nameInput.setSelectAllOnFocus(true);
        nameInput.setTextSize(17);
        nameInput.setTextColor(TEXT);
        nameInput.setHint("Name your preset");
        nameInput.setHintTextColor(MUTED);
        nameInput.setPadding(dp(12), dp(9), dp(12), dp(9));
        nameInput.setBackground(shape(PANEL, 8, GOLD));
        panel.addView(nameInput);

        LinearLayout edits = row();
        edits.addView(button("Save new", new Runnable() {
            @Override public void run() { save(false); }
        }), weight(1));
        edits.addView(button("Rename / update", new Runnable() {
            @Override public void run() { save(true); }
        }), weight(1.2f));
        panel.addView(edits);

        LinearLayout actions = row();
        actions.addView(button("Delete preset", new Runnable() {
            @Override public void run() { delete(); }
        }), weight(1));
        actions.addView(button("Filter off", new Runnable() {
            @Override public void run() { switchOff(); }
        }), weight(1));
        panel.addView(actions);

        LinearLayout bottom = row();
        bottom.addView(button("Full settings", new Runnable() {
            @Override public void run() { openMainSettings(); }
        }), weight(1));
        bottom.addView(button("Apply + close", new Runnable() {
            @Override public void run() { applyValues(); dismiss(); }
        }), weight(1));
        panel.addView(bottom);
        panel.addView(gap(6));
        statusText = text("D-pad to navigate • Left/Right to adjust • Back to close", 13, MUTED, false);
        panel.addView(statusText);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        params.x = dp(12);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        window = root;
        manager.addView(window, params);
        refreshPresetButtons();
        close.requestFocus();
    }

    private void refreshPresetButtons() {
        if (presetBar == null) return;
        presetBar.removeAllViews();
        for (int i = 0; i < presets.length(); i++) {
            final int index = i;
            JSONObject item = presets.optJSONObject(i);
            if (item == null) continue;
            String label = (i == chosen ? "● " : "☾ ") + item.optString("name", "Preset");
            Button presetButton = button(label, new Runnable() {
                @Override public void run() { select(index); }
            });
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    -1, dp(42));
            buttonParams.setMargins(dp(3), dp(1), dp(3), dp(1));
            presetButton.setLayoutParams(buttonParams);
            presetBar.addView(presetButton);
        }
    }

    private void select(int index) {
        JSONObject item = presets.optJSONObject(index);
        if (item == null) return;
        chosen = index;
        loading = true;
        dark.setProgress(item.optInt("dark", 30));
        warm.setProgress(item.optInt("warm", 25));
        kelvin.setProgress(sliderOfTemp(item.optInt("temp", 3000)));
        nameInput.setText(item.optString("name", "Preset"));
        loading = false;
        refreshLabels();
        applyValues();
        message("Applied " + item.optString("name", "Preset"));
        refreshPresetButtons();
    }

    private void save(boolean update) {
        String name = nameInput.getText().toString().trim();
        if (name.length() == 0) { message("Enter a preset name"); nameInput.requestFocus(); return; }
        if (name.length() > 32) name = name.substring(0, 32);
        JSONObject item = record(name, dark.getProgress(), warm.getProgress(), tempOfSlider(kelvin.getProgress()));
        if (update && chosen >= 0 && chosen < presets.length()) {
            try { presets.put(chosen, item); } catch (JSONException ignored) {}
        } else {
            int existing = -1;
            for (int i = 0; i < presets.length(); i++) {
                JSONObject p = presets.optJSONObject(i);
                if (p != null && name.equalsIgnoreCase(p.optString("name"))) { existing = i; break; }
            }
            if (existing >= 0) {
                try { presets.put(existing, item); } catch (JSONException ignored) {}
                chosen = existing;
            } else {
                if (presets.length() >= 24) { message("Limit: 24 presets"); return; }
                presets.put(item);
                chosen = presets.length() - 1;
            }
        }
        store();
        refreshPresetButtons();
        hideKeyboard();
        message("Saved " + name);
    }

    private void delete() {
        if (chosen < 0 || chosen >= presets.length()) { message("Choose a preset first"); return; }
        JSONArray kept = new JSONArray();
        for (int i = 0; i < presets.length(); i++) if (i != chosen) kept.put(presets.opt(i));
        presets = kept;
        chosen = -1;
        nameInput.setText("");
        store();
        refreshPresetButtons();
        message("Preset deleted");
    }

    private void store() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_PRESETS, presets.toString()).apply();
    }

    private void applyValues() {
        int red = warm.getProgress();
        int bright = Math.max(5, 100 - dark.getProgress());
        int temp = tempOfSlider(kelvin.getProgress());
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt("dark", dark.getProgress())
                .putInt("warm", red)
                .putInt("temp", temp).apply();
        Intent intent = new Intent(this, DimService.class);
        intent.putExtra("red", red);
        intent.putExtra("bright", bright);
        intent.putExtra("temp", temp);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            message("Applied");
        } catch (Exception e) {
            message("Filter error: " + e.getClass().getSimpleName());
        }
    }

    /** Keep the original MidnightBeam configuration accessible after Stickman
     * launches only the Quick activity. */
    private void openMainSettings() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
            dismiss();
        } catch (Exception e) {
            message("Could not open settings");
        }
    }

    private void switchOff() {
        Intent intent = new Intent(this, DimService.class);
        intent.putExtra("off", true);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
        } catch (Exception e) { message("Off failed"); }
        dismiss();
    }

    private void refreshLabels() {
        if (darkText == null) return;
        darkText.setText("Darkness    " + dark.getProgress() + "%");
        warmText.setText("Warm overlay    " + warm.getProgress() + "%");
        kelvinText.setText("Colour temperature    " + tempOfSlider(kelvin.getProgress()) + " K");
    }

    private int tempOfSlider(int slider) { return 6500 - 55 * slider; }
    private int sliderOfTemp(int temp) { return Math.max(0, Math.min(100, (6500 - temp + 27) / 55)); }
    private void message(String m) { if (statusText != null) statusText.setText(m); }
    private SeekBar addSlider(LinearLayout layout, TextView label, int max, int initial) {
        layout.addView(gap(7));
        layout.addView(label);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(initial);
        sb.setFocusable(true);
        layout.addView(sb);
        return sb;
    }
    private Button button(String name, final Runnable callback) {
        final Button b = new Button(this);
        b.setText(name);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(13);
        b.setFocusable(true);
        b.setBackground(shape(PANEL, 10, 0xff6e5744));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        b.setLayoutParams(lp);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focus) {
                b.setBackground(shape(focus ? 0xff61462e : PANEL, 10, focus ? GOLD : 0xff6e5744));
                b.setTextColor(focus ? 0xffffffff : TEXT);
            }
        });
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { callback.run(); }
        });
        return b;
    }
    private LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, -2, w); }
    private LinearLayout row() {
        LinearLayout result = new LinearLayout(this);
        result.setGravity(Gravity.CENTER_VERTICAL);
        result.setOrientation(LinearLayout.HORIZONTAL);
        return result;
    }
    private View gap(int height) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return spacer;
    }
    private TextView text(String label, int size, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(label);
        result.setTextColor(color);
        result.setTextSize(size);
        if (bold) result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return result;
    }
    private GradientDrawable shape(int fill, int radius, int border) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(fill);
        result.setCornerRadius(dp(radius));
        if (border != 0) result.setStroke(dp(1), border);
        return result;
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void hideKeyboard() {
        InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (input != null && window != null) input.hideSoftInputFromWindow(window.getWindowToken(), 0);
    }
    private void dismiss() {
        if (window != null && manager != null) {
            try { manager.removeViewImmediate(window); } catch (Exception ignored) {}
            window = null;
        }
        stopForeground(true);
        stopSelf();
    }
    @Override public void onDestroy() {
        if (window != null && manager != null) {
            try { manager.removeViewImmediate(window); } catch (Exception ignored) {}
            window = null;
        }
        super.onDestroy();
    }
}