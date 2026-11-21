package dev.midnightbeam;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/** Launcher shortcut, suitable for binding to Stickman. */
public final class QuickPopupActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Intent i = new Intent(this, QuickPopupService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception ex) {
            Toast.makeText(this, "Quick popup failed: " + ex.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
        finish();
        overridePendingTransition(0, 0);
    }
}