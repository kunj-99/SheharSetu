package com.infowave.sheharsetu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.infowave.sheharsetu.Adapter.CategoryAdapter;
import com.infowave.sheharsetu.Adapter.ProductAdapter;
import com.infowave.sheharsetu.Adapter.SubFilterGridAdapter;
import com.infowave.sheharsetu.net.ApiRoutes;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // ===== Views (Header) =====
    private ImageView btnDrawer;
    private TextInputLayout tiSearch;
    private TextInputEditText etSearch;
    private ActivityResultLauncher<Intent> speechLauncher;

    // ===== Lists =====
    private RecyclerView rvCategories, rvSubFiltersGrid, rvProducts;
    private MaterialButtonToggleGroup toggleNewOld;
    private TextView tvSectionTitle;

    // ===== Bottom banner =====
    private ImageButton btnPost, btnHelp;
    private TextView tvMarquee;

    // ===== Drawer =====
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    // ===== Data (dynamic) =====
    private final List<Map<String, Object>> categories = new ArrayList<>();
    private final Map<Integer, List<Map<String, Object>>> mapSubFilters = new HashMap<>();
    private final List<Map<String, Object>> currentProducts = new ArrayList<>();

    // ===== State =====
    private int selectedCategoryId = -1;
    private int selectedSubFilterId = -1;   // -1 = none (sub grid hidden), 0 = ALL
    private Boolean showNew = null;         // null = all, true=new, false=old (server ignore safe)
    private String searchQuery = "";

    // ===== Adapters =====
    private CategoryAdapter catAdapter;
    private ProductAdapter productAdapterRef;

    // ===== Locale Prefs =====
    private static final String PREFS = "app_prefs";
    private static final String KEY_LANG = "app_lang";

    // ===== Network =====
    private RequestQueue queue;
    private static final int PAGE = 1;
    private static final int LIMIT = 50;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySavedLocale();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        new androidx.core.view.WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView()
        ).setAppearanceLightStatusBars(false);

        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });

        queue = Volley.newRequestQueue(this);

        bindHeader();
        setupVoiceLauncher();
        setupSearch();
        setupLanguageToggle();
        setupAppDrawer();

        rvCategories     = findViewById(R.id.rvCategories);
        rvSubFiltersGrid = findViewById(R.id.rvSubFiltersGrid);
        rvProducts       = findViewById(R.id.rvProducts);
        toggleNewOld     = findViewById(R.id.toggleNewOld);
        tvSectionTitle   = findViewById(R.id.tvSectionTitle);

        btnPost   = findViewById(R.id.btnPost);
        btnHelp   = findViewById(R.id.btnHelp);
        tvMarquee = findViewById(R.id.tvMarquee);
        tvMarquee.setSelected(true);

        btnPost.setOnClickListener(v -> startActivity(new Intent(this, CategorySelectActivity.class)));
        btnHelp.setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));

        rvCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvSubFiltersGrid.setLayoutManager(new GridLayoutManager(this, 3));
        rvProducts.setLayoutManager(new GridLayoutManager(this, 2));

        setupAdapters();

        // Load dynamic data
        showProducts();
        fetchCategories();
        fetchProducts(); // featured on first load

        toggleNewOld.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            ensureProductsView();
            showNew = (id == R.id.btnShowNew) ? Boolean.TRUE : Boolean.FALSE;
            fetchProducts();
        });
    }

    // ================= Header/Search/Voice =================

    private void bindHeader() {
        btnDrawer = findViewById(R.id.btnDrawer);
        tiSearch  = findViewById(R.id.tiSearch);
        etSearch  = findViewById(R.id.etSearch);
    }

    private void setupVoiceLauncher() {
        speechLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> list = result.getData()
                                .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (list != null && !list.isEmpty()) {
                            etSearch.setText(list.get(0));
                            performSearch(list.get(0));
                        }
                    }
                });
        tiSearch.setEndIconOnClickListener(v -> startVoiceInput());
    }

    private void setupSearch() {
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
                performSearch(q);
                return true;
            }
            return false;
        });
    }

    private void startVoiceInput() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault());
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search…");
        try { speechLauncher.launch(i); }
        catch (Exception e) { Toast.makeText(this, "Voice search not available", Toast.LENGTH_SHORT).show(); }
    }

    private void performSearch(String query) {
        ensureProductsView();
        searchQuery = TextUtils.isEmpty(query) ? "" : query.toLowerCase(Locale.ROOT).trim();
        fetchProducts();
    }

    private void applySavedLocale() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String lang = sp.getString(KEY_LANG, java.util.Locale.getDefault().getLanguage());
        java.util.Locale locale = new java.util.Locale(lang);
        java.util.Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    private void setupLanguageToggle() {
        if (btnDrawer == null) return;
        btnDrawer.setOnClickListener(v -> {
            if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
        });
        btnDrawer.setOnLongClickListener(v -> {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            String cur = sp.getString(KEY_LANG, "en");
            String next = cur.equals("en") ? "hi" : "en";
            sp.edit().putString(KEY_LANG, next).apply();
            Toast.makeText(this, "Language: " + next, Toast.LENGTH_SHORT).show();
            recreate();
            return true;
        });
    }

    // ================= Drawer =================

    private void setupAppDrawer() {
        drawerLayout   = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navView);
        if (drawerLayout == null || navigationView == null) return;

        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            item.setChecked(true);
            drawerLayout.closeDrawer(GravityCompat.START);
            navigationView.setItemIconTintList(null);

            if (id == R.id.nav_home) {
                Toast.makeText(MainActivity.this, "Home", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_post) {
                startActivity(new Intent(MainActivity.this, CategorySelectActivity.class));
            } else if (id == R.id.nav_my_ads) {
                Toast.makeText(MainActivity.this, "My Ads", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_notifications) {
                Toast.makeText(MainActivity.this, "Notifications", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_invite) {
                shareApp();
            } else if (id == R.id.nav_rate) {
                rateUs();
            } else {
                Toast.makeText(MainActivity.this, "Coming soon", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    // ================= Adapters =================

    private void setupAdapters() {
        catAdapter = new CategoryAdapter(categories, cat -> {
            selectedCategoryId = toInt(cat.get("id"), -1);
            selectedSubFilterId = -1; // show sub-grid
            showNew = null;
            clearSearch();

            boolean hasNewOld = toBool(cat.get("hasNewOld"), false);
            toggleNewOld.setVisibility(hasNewOld ? View.VISIBLE : View.GONE);
            toggleNewOld.clearChecked();

            // Load subcategories for this category and show the grid
            fetchSubFilters(selectedCategoryId);
        });
        rvCategories.setAdapter(catAdapter);

        productAdapterRef = new ProductAdapter(this);
        rvProducts.setAdapter(productAdapterRef);
    }

    private void bindProducts(List<Map<String, Object>> items) {
        rvProducts.setVisibility(View.VISIBLE);
        if (productAdapterRef != null) productAdapterRef.setItems(items);
    }

    private void showSubFilters() {
        rvProducts.setVisibility(View.GONE);
        tvSectionTitle.setVisibility(View.GONE);
        rvSubFiltersGrid.setVisibility(View.VISIBLE);
    }

    private void showProducts() {
        rvSubFiltersGrid.setVisibility(View.GONE);
        tvSectionTitle.setVisibility(View.VISIBLE);
        rvProducts.setVisibility(View.VISIBLE);
        tvSectionTitle.setText(getString(R.string.featured_listings));
    }

    private void ensureProductsView() {
        if (rvSubFiltersGrid.getVisibility() == View.VISIBLE) showProducts();
    }

    private void clearSearch() {
        searchQuery = "";
        if (etSearch != null && etSearch.getText() != null) etSearch.setText("");
    }

    // ================= Network: Fetchers =================

    private String urlCategories() {
        // Prefer ApiRoutes if present; else compose
        String base = ApiRoutes.BASE_URL; // already without trailing slash
        return base + "/list_categories.php";
    }

    private String urlSubcategories(int categoryId) {
        return ApiRoutes.BASE_URL + "/list_subcategories.php?category_id=" + categoryId;
    }

    private String urlProducts() {
        StringBuilder sb = new StringBuilder(ApiRoutes.BASE_URL)
                .append("/list_products.php?page=").append(PAGE)
                .append("&limit=").append(LIMIT)
                .append("&sort=newest");
        if (selectedCategoryId > 0) sb.append("&category_id=").append(selectedCategoryId);
        if (selectedSubFilterId > 0) sb.append("&subcategory_id=").append(selectedSubFilterId);
        if (!TextUtils.isEmpty(searchQuery)) sb.append("&q=").append(android.net.Uri.encode(searchQuery));
        if (showNew != null) sb.append("&is_new=").append(showNew ? "1" : "0"); // server ignores safely
        return sb.toString();
    }

    private void fetchCategories() {
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                urlCategories(),
                null,
                resp -> {
                    try {
                        if (!"success".equalsIgnoreCase(resp.optString("status"))) {
                            Toast.makeText(this, "Categories error", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        JSONArray arr = resp.optJSONArray("data");
                        categories.clear();
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                Map<String, Object> m = new HashMap<>();
                                m.put("id",        o.optInt("id", 0));
                                m.put("name",      o.optString("name", ""));
                                m.put("iconUrl",   o.optString("icon", ""));  // server key 'icon'
                                m.put("hasNewOld", o.optInt("hasNewOld", 0) == 1);
                                categories.add(m);
                            }
                        }
                        catAdapter.notifyDataSetChanged();
                    } catch (Exception e) {
                        Toast.makeText(this, "Parse categories failed", Toast.LENGTH_SHORT).show();
                    }
                },
                err -> Toast.makeText(this, "Network error (categories)", Toast.LENGTH_SHORT).show()
        );
        req.setRetryPolicy(new DefaultRetryPolicy(8000, 1, 1));
        queue.add(req);
    }

    private void fetchSubFilters(int categoryId) {
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                urlSubcategories(categoryId),
                null,
                resp -> {
                    try {
                        if (!"success".equalsIgnoreCase(resp.optString("status"))) {
                            Toast.makeText(this, "Subcategories error", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        JSONArray arr = resp.optJSONArray("data");
                        List<Map<String, Object>> subs = new ArrayList<>();

                        // Add "All"
                        Map<String, Object> all = new HashMap<>();
                        all.put("id", 0);
                        all.put("name", getString(R.string.sub_all));
                        all.put("iconRes", R.drawable.ic_placeholder_circle);
                        subs.add(all);

                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                Map<String, Object> m = new HashMap<>();
                                m.put("id",          o.optInt("id", 0));
                                m.put("category_id", o.optInt("category_id", categoryId));
                                m.put("name",        o.optString("name", ""));
                                m.put("iconUrl",     o.optString("icon", ""));
                                subs.add(m);
                            }
                        }
                        mapSubFilters.put(categoryId, subs);

                        // Bind grid with click → set subId and fetch products
                        rvSubFiltersGrid.setAdapter(new SubFilterGridAdapter(subs, sub -> {
                            selectedSubFilterId = toInt(sub.get("id"), 0); // 0 = ALL
                            clearSearch();
                            showProducts();
                            fetchProducts(); // important: server side filtering
                        }));
                        showSubFilters();
                        catAdapter.setSelectedId(selectedCategoryId);
                    } catch (Exception e) {
                        Toast.makeText(this, "Parse subcategories failed", Toast.LENGTH_SHORT).show();
                    }
                },
                err -> Toast.makeText(this, "Network error (subcategories)", Toast.LENGTH_SHORT).show()
        );
        req.setRetryPolicy(new DefaultRetryPolicy(8000, 1, 1));
        queue.add(req);
    }

    private void fetchProducts() {
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                urlProducts(),
                null,
                resp -> {
                    try {
                        if (!"success".equalsIgnoreCase(resp.optString("status"))) {
                            Toast.makeText(this, "Products error", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        JSONArray arr = resp.optJSONArray("data");
                        currentProducts.clear();
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                Map<String, Object> m = new HashMap<>();
                                m.put("id",            o.optInt("id", 0));
                                m.put("categoryId",    o.optInt("category_id", 0));
                                m.put("subFilterId",   o.optInt("subcategory_id", 0));
                                m.put("title",         o.optString("title", ""));
                                // price: server may send number; show as plain string for card
                                m.put("price",         o.opt("price") == null ? "" : String.valueOf(o.opt("price")));
                                m.put("city",          o.optString("city", ""));
                                m.put("imageUrl",      o.isNull("image_url") ? "" : o.optString("image_url",""));
                                m.put("isNew",         o.optInt("is_new", 0) == 1);
                                currentProducts.add(m);
                            }
                        }
                        bindProducts(new ArrayList<>(currentProducts));
                        tvSectionTitle.setText(getString(R.string.featured_listings));
                    } catch (Exception e) {
                        Toast.makeText(this, "Parse products failed", Toast.LENGTH_SHORT).show();
                    }
                },
                err -> Toast.makeText(this, "Network error (products)", Toast.LENGTH_SHORT).show()
        );
        req.setRetryPolicy(new DefaultRetryPolicy(8000, 1, 1));
        queue.add(req);
    }

    // ================= Back navigation =================

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        if (rvSubFiltersGrid.getVisibility() == View.VISIBLE) {
            // Close sub-grid → show all products of selected category
            showProducts();
            selectedSubFilterId = -1; // none selected
            fetchProducts();
            return;
        }
        if (selectedCategoryId != -1) {
            // Clear category selection → show featured
            selectedCategoryId = -1;
            selectedSubFilterId = -1;
            showNew = null;
            toggleNewOld.setVisibility(View.GONE);
            if (catAdapter != null) catAdapter.setSelectedId(-1);
            showProducts();
            clearSearch();
            fetchProducts();
            return;
        }
        super.onBackPressed();
    }

    // ================= Helpers =================

    private static int toInt(Object o, int def) {
        if (o instanceof Integer) return (Integer) o;
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private static boolean toBool(Object o, boolean def) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o == null) return def;
        String s = String.valueOf(o);
        if ("1".equals(s)) return true;
        if ("0".equals(s)) return false;
        try { return Boolean.parseBoolean(s); } catch (Exception e) { return def; }
    }

    private void shareApp() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        i.putExtra(Intent.EXTRA_TEXT, "Check out SheharSetu: https://play.google.com/store/apps/details?id=" + getPackageName());
        try { startActivity(Intent.createChooser(i, "Share via")); }
        catch (Exception e) { Toast.makeText(this, "No app found to share", Toast.LENGTH_SHORT).show(); }
    }

    private void rateUs() {
        String pkg = getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + pkg)));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
        }
    }
}
