package com.infowave.sheharsetu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.infowave.sheharsetu.Adapter.LanguageAdapter;
import com.infowave.sheharsetu.Adapter.LanguageManager;
import com.infowave.sheharsetu.core.SessionManager;
import com.infowave.sheharsetu.net.ApiRoutes;
import com.infowave.sheharsetu.net.VolleySingleton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Language list ab server se aata hai: GET ApiRoutes.GET_LANGUAGES
 * JSON format:
 * {
 *   "ok": true,
 *   "data": [
 *     {"code":"hi","native_name":"हिन्दी","english_name":"Hindi","enabled":1},
 *     ...
 *   ]
 * }
 */
public class LanguageSelection extends AppCompatActivity implements LanguageAdapter.OnLanguageClick {

    public static final String PREFS = "sheharsetu_prefs";
    public static final String KEY_LANG_CODE = "app_lang_code";
    public static final String KEY_LANG_NAME = "app_lang_name";

    private RecyclerView rv;
    private ProgressBar progress;
    private final List<String[]> languages = new ArrayList<>();
    private LanguageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Status bar black + light icons off (i.e., white icons)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.BLACK);
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        // already chosen? go next
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = sp.getString(KEY_LANG_CODE, null);
        if (saved != null) {
            LanguageManager.apply(this, saved);
            goNext();
            return;
        }

        setContentView(R.layout.activity_language_selection);

        rv = findViewById(R.id.rvLanguages);
        progress = findViewById(R.id.progressLanguages);

        rv.setLayoutManager(new GridLayoutManager(this, 3));
        rv.setHasFixedSize(true);

        adapter = new LanguageAdapter(languages, this);
        rv.setAdapter(adapter);

        fetchLanguages();
    }

    /** API call to load languages dynamically */
    private void fetchLanguages() {
        showLoading(true);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                ApiRoutes.GET_LANGUAGES,
                null,
                resp -> {
                    try {
                        languages.clear();
                        boolean ok = resp.optBoolean("ok", false);
                        if (!ok) {
                            Toast.makeText(this, "Failed to load languages", Toast.LENGTH_SHORT).show();
                            showLoading(false);
                            return;
                        }
                        JSONArray arr = resp.optJSONArray("data");
                        if (arr == null || arr.length() == 0) {
                            Toast.makeText(this, "No languages found", Toast.LENGTH_SHORT).show();
                            showLoading(false);
                            return;
                        }

                        // Prefer English on top if present
                        int englishIndex = -1;

                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o == null) continue;
                            int enabled = o.optInt("enabled", 1);
                            if (enabled != 1) continue;

                            String code = o.optString("code", "").trim();
                            String nativeName = o.optString("native_name", "").trim();
                            String englishName = o.optString("english_name", "").trim();
                            if (code.isEmpty() || nativeName.isEmpty()) continue;

                            // LanguageAdapter expects String[]{code, native, english}
                            languages.add(new String[]{code, nativeName, englishName});

                            if ("en".equalsIgnoreCase(code)) englishIndex = languages.size() - 1;
                        }

                        // Ensure English first (optional nicety)
                        if (englishIndex > 0) {
                            String[] en = languages.remove(englishIndex);
                            languages.add(0, en);
                        }

                        adapter.notifyDataSetChanged();
                    } catch (Exception e) {
                        Toast.makeText(this, "Parse error", Toast.LENGTH_SHORT).show();
                    }
                    showLoading(false);
                },
                err -> {
                    Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show();
                    showLoading(false);
                }
        ){
            @Override public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> h = new HashMap<>();
                h.put("Content-Type", "application/json; charset=utf-8");
                // Accept-Language optional: server ko hint dena ho to
                String current = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LANG_CODE, "en");
                h.put("Accept-Language", current == null ? "en" : current);
                return h;
            }
        };
        req.setRetryPolicy(new DefaultRetryPolicy(12000, 1, 1.0f));
        VolleySingleton.getInstance(this).add(req);
    }

    private void showLoading(boolean show) {
        if (progress != null) progress.setVisibility(show ? View.VISIBLE : View.GONE);
        if (rv != null) rv.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    public void onLanguageSelected(String[] lang) {
        // lang[0] = code, lang[1] = native_name
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_LANG_CODE, lang[0])
                .putString(KEY_LANG_NAME, lang[1])
                .apply();

        LanguageManager.apply(this, lang[0]);
        goNext();
    }

    private void goNext() {
        // first time → register page
        startActivity(new Intent(this, UserInfoActivity.class));
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
}
