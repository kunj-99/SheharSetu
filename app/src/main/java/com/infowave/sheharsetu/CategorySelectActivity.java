package com.infowave.sheharsetu;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.infowave.sheharsetu.Adapter.CategoryGridAdapter;
import com.infowave.sheharsetu.Adapter.SubcategoryGridAdapter;
import com.infowave.sheharsetu.net.ApiRoutes;
import com.infowave.sheharsetu.net.VolleySingleton;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Status / navigation bar colors as before
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        new androidx.core.view.WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView()
        ).setAppearanceLightStatusBars(false);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_category_select);

        bindViews();
        setupLists();
        setupClicks();

        // yahi method naam same rakha – ab ye API se data layega
        seedData();

        updateCtaState();
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

        btnContinue = findViewById(R.id.btnContinue);
    }

    /**
     * OLD: Static demo data
     * NEW: Server se categories fetch karega (list_categories.php)
     */
    private void seedData() {
        loadCategoriesFromApi();
    }

    /** Categories ko backend se load karega. */
    private void loadCategoriesFromApi() {
        String url = ApiRoutes.GET_CATEGORIES; // .../list_categories.php

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    try {
                        JSONObject root = new JSONObject(response);
                        String status = root.optString("status", "");
                        if (!"success".equalsIgnoreCase(status)) {
                            Toast.makeText(this,
                                    root.optString("message", "Failed to load categories."),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        JSONArray dataArr = root.optJSONArray("data");
                        categories.clear();

                        if (dataArr != null) {
                            @DrawableRes int ph = R.drawable.ic_placeholder_circle; // same as pehle
                            for (int i = 0; i < dataArr.length(); i++) {
                                JSONObject obj = dataArr.getJSONObject(i);

                                // PHP se: id, name, icon, hasNewOld (0/1)
                                String id   = obj.optString("id", "");
                                String name = obj.optString("name", "");
                                // icon URL aa raha hoga, but abhi UI same rakhne ke liye
                                // local placeholder drawable hi use karenge
                                boolean requiresCond = obj.optInt("hasNewOld", 0) == 1;

                                if (!id.isEmpty() && !name.isEmpty()) {
                                    categories.add(new Category(
                                            id,
                                            name,
                                            safeImg(R.drawable.image1, ph),
                                            requiresCond
                                    ));
                                }
                            }
                        }

                        // Adapter ko new list de do (UI same rahega, data dynamic ho jayega)
                        categoryAdapter.submit(mapToCategoryItems(categories));

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Parsing error (categories).", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    error.printStackTrace();
                    Toast.makeText(this,
                            "Unable to load categories. Please check internet.",
                            Toast.LENGTH_SHORT).show();
                }
        );

        VolleySingleton.getInstance(this).add(req);
    }

    private int safeImg(@DrawableRes int tryRes, @DrawableRes int fallback) {
        return tryRes != 0 ? tryRes : fallback;
    }

    private void setupLists() {
        // Categories grid (3 columns)
        rvCategories.setLayoutManager(new GridLayoutManager(this, 3));
        rvCategories.setHasFixedSize(true);
        addGridSpacing(rvCategories, 12);

        categoryAdapter = new CategoryGridAdapter(mapToCategoryItems(categories), (item, pos) -> {
            // user ne koi category select ki
            selectedCategory = new Category(item.id, item.name, item.iconRes, item.requiresCondition);
            selectedSub = null;
            selectedCondition = null;

            // ab is category ke subcategories server se laayenge
            loadSubcategories(item.id);

            tvSubTitle.setVisibility(View.VISIBLE);
            rvSubcategories.setVisibility(View.VISIBLE);
            conditionRow.setVisibility(View.GONE);

            cgCondition.clearCheck();
            updateCtaState();
        });
        rvCategories.setAdapter(categoryAdapter);

        // Subcategories grid
        rvSubcategories.setLayoutManager(new GridLayoutManager(this, 3));
        rvSubcategories.setHasFixedSize(true);
        addGridSpacing(rvSubcategories, 12);

        subcategoryAdapter = new SubcategoryGridAdapter(new ArrayList<>(), (s, pos) -> {
            selectedSub = new Subcategory(s.id, s.parentId, s.name, s.iconRes, s.requiresCondition);

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
            if (checkedId == R.id.chipNew)      selectedCondition = "new";
            else if (checkedId == R.id.chipUsed) selectedCondition = "used";
            else                                 selectedCondition = null;
            updateCtaState();
        });

        btnContinue.setOnClickListener(v -> {
            // Validate selections
            if (selectedCategory == null) {
                Toast.makeText(this, "Please select a category.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedSub == null) {
                Toast.makeText(this, "Please select a subcategory.", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean needCond = (selectedSub.requiresCondition != null)
                    ? selectedSub.requiresCondition
                    : selectedCategory.requiresCondition;
            if (needCond && selectedCondition == null) {
                Toast.makeText(this, "Please select condition (New/Used).", Toast.LENGTH_SHORT).show();
                return;
            }

            // Build and launch DynamicFormActivity
            Intent intent = new Intent(CategorySelectActivity.this, DynamicFormActivity.class);

            // DynamicFormActivity ke liye primary category string
            intent.putExtra(DynamicFormActivity.EXTRA_CATEGORY, selectedCategory.name);

            // Rich context (IDs + names + condition)
            intent.putExtra("category_id", selectedCategory.id);
            intent.putExtra("category_name", selectedCategory.name);
            intent.putExtra("subcategory_id", selectedSub.id);
            intent.putExtra("subcategory_name", selectedSub.name);
            if (selectedCondition != null) intent.putExtra("condition", selectedCondition); // "new" or "used"

            startActivity(intent);
            // Do NOT finish(); so user can come back and change.
        });
    }

    /**
     * Ab ye method server se subcategories fetch karega
     * (list_subcategories.php?category_id=XYZ)
     */
    private void loadSubcategories(String catId) {
        // Cache use karna ho to:
        if (subMap.containsKey(catId)) {
            List<Subcategory> cached = subMap.get(catId);
            List<SubcategoryGridAdapter.Item> ui = new ArrayList<>();
            if (cached != null) {
                for (Subcategory s : cached) {
                    ui.add(new SubcategoryGridAdapter.Item(
                            s.id, s.parentId, s.name, s.iconRes, s.requiresCondition
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
                                    root.optString("message", "Failed to load subcategories."),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        JSONArray dataArr = root.optJSONArray("data");
                        List<Subcategory> subs = new ArrayList<>();
                        List<SubcategoryGridAdapter.Item> ui = new ArrayList<>();

                        if (dataArr != null) {
                            @DrawableRes int ph = R.drawable.ic_placeholder_circle;
                            for (int i = 0; i < dataArr.length(); i++) {
                                JSONObject obj = dataArr.getJSONObject(i);

                                // expected fields: id, name, maybe hasNewOld
                                String id   = obj.optString("id", "");
                                String name = obj.optString("name", "");
                                boolean requiresCond = obj.optInt("hasNewOld", 0) == 1;

                                if (!id.isEmpty() && !name.isEmpty()) {
                                    Subcategory s = new Subcategory(
                                            id,
                                            catId,
                                            name,
                                            safeImg(R.drawable.image1, ph),
                                            requiresCond
                                    );
                                    subs.add(s);
                                    ui.add(new SubcategoryGridAdapter.Item(
                                            s.id, s.parentId, s.name, s.iconRes, s.requiresCondition
                                    ));
                                }
                            }
                        }

                        // cache
                        subMap.put(catId, subs);

                        // adapter update
                        subcategoryAdapter.submit(ui);

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Parsing error (subcategories).", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    error.printStackTrace();
                    Toast.makeText(this,
                            "Unable to load subcategories. Please check internet.",
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
            out.add(new CategoryGridAdapter.Item(c.id, c.name, c.iconRes, c.requiresCondition));
        }
        return out;
    }

    private <T> List<T> list(T... arr) {
        List<T> l = new ArrayList<>();
        for (T t : arr) l.add(t);
        return l;
    }

    // Simple spacing between grid items
    private void addGridSpacing(RecyclerView rv, int dp) {
        int px = Math.round(getResources().getDisplayMetrics().density * dp);
        rv.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.left = px/2;
                outRect.right = px/2;
                outRect.top = px/2;
                outRect.bottom = px/2;
            }
        });
    }

    // Models
    static class Category {
        final String id; final String name; @DrawableRes final int iconRes; final boolean requiresCondition;
        Category(String id, String name, int iconRes, boolean requiresCondition) {
            this.id = id; this.name = name; this.iconRes = iconRes; this.requiresCondition = requiresCondition;
        }
    }
    static class Subcategory {
        final String id; final String parentId; final String name; @DrawableRes final int iconRes;
        final Boolean requiresCondition; // null => inherit category
        Subcategory(String id, String parentId, String name, int iconRes, @Nullable Boolean requiresCondition) {
            this.id = id; this.parentId = parentId; this.name = name; this.iconRes = iconRes; this.requiresCondition = requiresCondition;
        }
    }
}
