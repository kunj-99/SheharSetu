package com.infowave.sheharsetu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.infowave.sheharsetu.Adapter.CategoryGridAdapter;
import com.infowave.sheharsetu.Adapter.I18n;
import com.infowave.sheharsetu.Adapter.LanguageManager;
import com.infowave.sheharsetu.Adapter.SubcategoryGridAdapter;
import com.infowave.sheharsetu.net.ApiRoutes;
import com.infowave.sheharsetu.net.VolleySingleton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategorySelectActivity extends AppCompatActivity {

    private MaterialToolbar topBar;
    private RecyclerView rvCategories, rvSubcategories;
    private TextView tvSubTitle;
    private View conditionRow;
    private ChipGroup cgCondition;
    private Chip chipNew, chipUsed;
    private MaterialButton btnContinue;

    private final List<Category> categories = new ArrayList<>();
    private final Map<String, List<Subcategory>> subMap = new HashMap<>();

    private Category selectedCategory = null;
    private Subcategory selectedSub = null;
    private String selectedCondition = null; // "new" / "used" / null

    private CategoryGridAdapter categoryAdapter;
    private SubcategoryGridAdapter subcategoryAdapter;

    // === Locale prefs (same as LanguageSelection / I18n) ===
    private static final String PREFS    = LanguageSelection.PREFS;         // "sheharsetu_prefs"
    private static final String KEY_LANG = LanguageSelection.KEY_LANG_CODE; // "app_lang_code"

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply user-selected language to this Activity
        applySavedLocale();

        // Status / navigation bar colors as before
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        new androidx.core.view.WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView()
        ).setAppearanceLightStatusBars(false);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_category_select);

        bindViews();
        prefetchAndApplyStaticTexts();
        setupLists();
        setupClicks();

        seedData();
        updateCtaState();
    }

    private void applySavedLocale() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String lang = sp.getString(KEY_LANG, "en");
        LanguageManager.apply(this, lang);
    }

    private void bindViews() {
        topBar = findViewById(R.id.topBar);
        if (topBar != null) {
            topBar.setNavigationOnClickListener(v -> onBackPressed());
        }

        rvCategories    = findViewById(R.id.rvCategories);
        rvSubcategories = findViewById(R.id.rvSubcategories);
        tvSubTitle      = findViewById(R.id.tvSubTitle);

        conditionRow = findViewById(R.id.conditionRow);
        cgCondition  = findViewById(R.id.cgCondition);
        chipNew      = findViewById(R.id.chipNew);
        chipUsed     = findViewById(R.id.chipUsed);

        btnContinue  = findViewById(R.id.btnContinue);
    }

    /**
     * Prefetch and apply translations for static labels on this screen.
     */
    private void prefetchAndApplyStaticTexts() {
        List<String> keys = new ArrayList<>();

        if (topBar != null && topBar.getTitle() != null) {
            keys.add(topBar.getTitle().toString());
        }
        if (tvSubTitle != null && tvSubTitle.getText() != null) {
            keys.add(tvSubTitle.getText().toString());
        }
        if (chipNew != null && chipNew.getText() != null) {
            keys.add(chipNew.getText().toString());
        }
        if (chipUsed != null && chipUsed.getText() != null) {
            keys.add(chipUsed.getText().toString());
        }
        if (btnContinue != null && btnContinue.getText() != null) {
            keys.add(btnContinue.getText().toString());
        }

        // Runtime messages
        keys.add("Failed to load categories.");
        keys.add("Parsing error (categories).");
        keys.add("Unable to load categories. Please check internet.");
        keys.add("Failed to load subcategories.");
        keys.add("Parsing error (subcategories).");
        keys.add("Unable to load subcategories. Please check internet.");
        keys.add("Please select a category.");
        keys.add("Please select a subcategory.");
        keys.add("Please select condition (New/Used).");

        I18n.prefetch(this, keys, () -> {
            if (topBar != null && topBar.getTitle() != null) {
                topBar.setTitle(I18n.t(CategorySelectActivity.this,
                        topBar.getTitle().toString()));
            }
            if (tvSubTitle != null && tvSubTitle.getText() != null) {
                tvSubTitle.setText(I18n.t(CategorySelectActivity.this,
                        tvSubTitle.getText().toString()));
            }
            if (chipNew != null && chipNew.getText() != null) {
                chipNew.setText(I18n.t(CategorySelectActivity.this,
                        chipNew.getText().toString()));
            }
            if (chipUsed != null && chipUsed.getText() != null) {
                chipUsed.setText(I18n.t(CategorySelectActivity.this,
                        chipUsed.getText().toString()));
            }
            if (btnContinue != null && btnContinue.getText() != null) {
                btnContinue.setText(I18n.t(CategorySelectActivity.this,
                        btnContinue.getText().toString()));
            }
        });
    }

    private void seedData() {
        loadCategoriesFromApi();
    }

    private void loadCategoriesFromApi() {
        String url = ApiRoutes.GET_CATEGORIES;

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    try {
                        JSONObject root = new JSONObject(response);
                        String status = root.optString("status", "");
                        if (!"success".equalsIgnoreCase(status)) {
                            Toast.makeText(this,
                                    I18n.t(this,
                                            root.optString("message",
                                                    "Failed to load categories.")),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        JSONArray dataArr = root.optJSONArray("data");
                        categories.clear();

                        List<String> catNameKeys = new ArrayList<>();

                        if (dataArr != null) {
                            for (int i = 0; i < dataArr.length(); i++) {
                                JSONObject obj = dataArr.getJSONObject(i);

                                String id = obj.optString("id", "");
                                String name = obj.optString("name", "");
                                String iconUrl = obj.optString("icon", "");
                                boolean requiresCond = obj.optInt("hasNewOld", 0) == 1;

                                if (!id.isEmpty() && !name.isEmpty()) {
                                    categories.add(new Category(
                                            id,
                                            name,
                                            iconUrl,
                                            requiresCond
                                    ));
                                    catNameKeys.add(name);
                                }
                            }
                        }

                        // Prefetch translations for category names then submit to adapter
                        I18n.prefetch(this, catNameKeys, () ->
                                categoryAdapter.submit(mapToCategoryItems(categories))
                        );

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this,
                                I18n.t(this, "Parsing error (categories)."),
                                Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    error.printStackTrace();
                    Toast.makeText(this,
                            I18n.t(this, "Unable to load categories. Please check internet."),
                            Toast.LENGTH_SHORT).show();
                }
        );

        VolleySingleton.getInstance(this).add(req);
    }

    private void setupLists() {
        rvCategories.setLayoutManager(new GridLayoutManager(this, 3));
        rvCategories.setHasFixedSize(true);
        addGridSpacing(rvCategories, 12);

        categoryAdapter = new CategoryGridAdapter(mapToCategoryItems(categories), (item, pos) -> {
            selectedCategory = new Category(item.id, item.name, item.iconUrl, item.requiresCondition);
            selectedSub = null;
            selectedCondition = null;

            loadSubcategories(item.id);

            tvSubTitle.setVisibility(View.VISIBLE);
            rvSubcategories.setVisibility(View.VISIBLE);
            conditionRow.setVisibility(View.GONE);

            cgCondition.clearCheck();
            updateCtaState();
        });
        rvCategories.setAdapter(categoryAdapter);

        rvSubcategories.setLayoutManager(new GridLayoutManager(this, 3));
        rvSubcategories.setHasFixedSize(true);
        addGridSpacing(rvSubcategories, 12);

        subcategoryAdapter = new SubcategoryGridAdapter(new ArrayList<>(), (s, pos) -> {
            selectedSub = new Subcategory(s.id, s.parentId, s.name, s.iconUrl, s.requiresCondition);

            boolean needCond = (selectedSub.requiresCondition != null)
                    ? selectedSub.requiresCondition
                    : (selectedCategory != null && selectedCategory.requiresCondition);

            conditionRow.setVisibility(needCond ? View.VISIBLE : View.GONE);
            if (!needCond) selectedCondition = null;
            cgCondition.clearCheck();

            updateCtaState();
        });
        rvSubcategories.setAdapter(subcategoryAdapter);
    }

    private void setupClicks() {
        cgCondition.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipNew) selectedCondition = "new";
            else if (checkedId == R.id.chipUsed) selectedCondition = "used";
            else selectedCondition = null;
            updateCtaState();
        });

        btnContinue.setOnClickListener(v -> {
            if (selectedCategory == null) {
                Toast.makeText(this,
                        I18n.t(this, "Please select a category."),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedSub == null) {
                Toast.makeText(this,
                        I18n.t(this, "Please select a subcategory."),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            boolean needCond = (selectedSub.requiresCondition != null)
                    ? selectedSub.requiresCondition
                    : selectedCategory.requiresCondition;
            if (needCond && selectedCondition == null) {
                Toast.makeText(this,
                        I18n.t(this, "Please select condition (New/Used)."),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(CategorySelectActivity.this, DynamicFormActivity.class);
            intent.putExtra(DynamicFormActivity.EXTRA_CATEGORY, selectedCategory.name);

            intent.putExtra("category_id", selectedCategory.id);
            intent.putExtra("category_name", selectedCategory.name);
            intent.putExtra("subcategory_id", selectedSub.id);
            intent.putExtra("subcategory_name", selectedSub.name);
            if (selectedCondition != null) intent.putExtra("condition", selectedCondition);

            startActivity(intent);
        });
    }

    private void loadSubcategories(String catId) {
        // If already loaded once, re-use and just translate names via cache.
        if (subMap.containsKey(catId)) {
            List<Subcategory> cached = subMap.get(catId);
            List<SubcategoryGridAdapter.Item> ui = new ArrayList<>();
            if (cached != null) {
                for (Subcategory s : cached) {
                    ui.add(new SubcategoryGridAdapter.Item(
                            s.id,
                            s.parentId,
                            I18n.t(this, s.name),
                            s.iconUrl,
                            s.requiresCondition
                    ));
                }
            }
            subcategoryAdapter.submit(ui);
            return;
        }

        String url = ApiRoutes.GET_SUBCATEGORIES + "?category_id=" + catId;

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    try {
                        JSONObject root = new JSONObject(response);
                        String status = root.optString("status", "");
                        if (!"success".equalsIgnoreCase(status)) {
                            Toast.makeText(this,
                                    I18n.t(this,
                                            root.optString("message",
                                                    "Failed to load subcategories.")),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        JSONArray dataArr = root.optJSONArray("data");
                        List<Subcategory> subs = new ArrayList<>();
                        List<String> subNameKeys = new ArrayList<>();

                        if (dataArr != null) {
                            for (int i = 0; i < dataArr.length(); i++) {
                                JSONObject obj = dataArr.getJSONObject(i);

                                String id = obj.optString("id", "");
                                String name = obj.optString("name", "");
                                String iconUrl = obj.optString("icon", "");
                                boolean requiresCond = obj.optInt("hasNewOld", 0) == 1;

                                if (!id.isEmpty() && !name.isEmpty()) {
                                    Subcategory s = new Subcategory(
                                            id,
                                            catId,
                                            name,
                                            iconUrl,
                                            requiresCond
                                    );
                                    subs.add(s);
                                    subNameKeys.add(name);
                                }
                            }
                        }

                        subMap.put(catId, subs);

                        // Prefetch translations for subcategory names
                        I18n.prefetch(this, subNameKeys, () -> {
                            List<SubcategoryGridAdapter.Item> ui = new ArrayList<>();
                            for (Subcategory s : subs) {
                                ui.add(new SubcategoryGridAdapter.Item(
                                        s.id,
                                        s.parentId,
                                        I18n.t(CategorySelectActivity.this, s.name),
                                        s.iconUrl,
                                        s.requiresCondition
                                ));
                            }
                            subcategoryAdapter.submit(ui);
                        });

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this,
                                I18n.t(this, "Parsing error (subcategories)."),
                                Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    error.printStackTrace();
                    Toast.makeText(this,
                            I18n.t(this, "Unable to load subcategories. Please check internet."),
                            Toast.LENGTH_SHORT).show();
                }
        );

        VolleySingleton.getInstance(this).add(req);
    }

    private void updateCtaState() {
        boolean hasCat = selectedCategory != null;
        boolean hasSub = selectedSub != null;
        boolean needCond = false;

        if (hasCat && hasSub) {
            needCond = (selectedSub.requiresCondition != null)
                    ? selectedSub.requiresCondition
                    : selectedCategory.requiresCondition;
        }
        boolean condOk = !needCond || (selectedCondition != null);
        btnContinue.setEnabled(hasCat && hasSub && condOk);
    }

    private List<CategoryGridAdapter.Item> mapToCategoryItems(List<Category> list) {
        List<CategoryGridAdapter.Item> out = new ArrayList<>();
        for (Category c : list) {
            String displayName = I18n.t(this, c.name);
            out.add(new CategoryGridAdapter.Item(
                    c.id,
                    displayName,
                    c.iconUrl,
                    c.requiresCondition
            ));
        }
        return out;
    }

    @SafeVarargs
    private <T> List<T> list(T... arr) {
        List<T> l = new ArrayList<>();
        for (T t : arr) l.add(t);
        return l;
    }

    private void addGridSpacing(RecyclerView rv, int dp) {
        int px = Math.round(getResources().getDisplayMetrics().density * dp);
        rv.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.left = px / 2;
                outRect.right = px / 2;
                outRect.top = px / 2;
                outRect.bottom = px / 2;
            }
        });
    }

    // Models
    static class Category {
        final String id;
        final String name;
        final String iconUrl;
        final boolean requiresCondition;

        Category(String id, String name, String iconUrl, boolean requiresCondition) {
            this.id = id;
            this.name = name;
            this.iconUrl = iconUrl;
            this.requiresCondition = requiresCondition;
        }
    }

    static class Subcategory {
        final String id;
        final String parentId;
        final String name;
        final String iconUrl;
        final Boolean requiresCondition;

        Subcategory(String id, String parentId, String name, String iconUrl, @Nullable Boolean requiresCondition) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
            this.iconUrl = iconUrl;
            this.requiresCondition = requiresCondition;
        }
    }
}
