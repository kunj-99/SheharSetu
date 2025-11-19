package com.infowave.sheharsetu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class SplashScreen extends AppCompatActivity {

    private static final long SPLASH_DELAY = 1200L; // 1.2s

    public static final String PREFS          = "sheharsetu_prefs";
    public static final String KEY_LANG_CODE  = "app_lang_code";       // भाषा चुनी हुई?
    public static final String KEY_ONBOARDED  = "onboarding_done";     // register done?
    public static final String KEY_ACCESS     = "access_token";        // JWT / access
    public static final String KEY_ACCESS_EXP = "access_expiry_epoch"; // expiry epoch (sec)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        // Status bar black + white icons
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.BLACK);
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        // ✅ Root ko system bars ke hisaab se padding do
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        new Handler(Looper.getMainLooper()).postDelayed(this::routeNext, SPLASH_DELAY);
    }

    private void routeNext() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        // 1) भाषा चुनी है?
        String lang = sp.getString(KEY_LANG_CODE, null);
        if (lang == null || lang.trim().isEmpty()) {
            go(LanguageSelection.class);
            return;
        }

        // 2) Valid token है?
        if (hasValidToken(sp)) {
            go(MainActivity.class);
            return;
        }

        // 3) Registered / Onboarded?
        boolean onboarded = sp.getBoolean(KEY_ONBOARDED, false);
        if (onboarded) {
            go(LoginActivity.class);
        } else {
            go(UserInfoActivity.class); // Register
        }
    }

    private void go(Class<?> cls) {
        startActivity(new Intent(this, cls));
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    /** Check if saved access token is still valid */
    private boolean hasValidToken(SharedPreferences sp) {
        String token = sp.getString(KEY_ACCESS, null);
        long expAt   = sp.getLong(KEY_ACCESS_EXP, 0L);
        long now     = System.currentTimeMillis() / 1000L;
        return token != null && expAt > now + 15; // 15s buffer
    }
}
