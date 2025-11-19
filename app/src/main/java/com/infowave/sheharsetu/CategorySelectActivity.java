package com.infowave.sheharsetu;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.infowave.sheharsetu.Adapter.CategoryGridAdapter;
import com.infowave.sheharsetu.Adapter.SubcategoryGridAdapter;

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
        
        // Set status bar color to black
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        new androidx.core.view.WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView()
        ).setAppearanceLightStatusBars(false);
        
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_category_select);

        bindViews();
        seedData();
        setupLists();
        setupClicks();
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

    /** Provide demo data (replace images with your drawables). */
    private void seedData() {
        @DrawableRes int ph = R.drawable.ic_placeholder_circle;

        categories.add(new Category("veh","Vehicles",      safeImg(R.drawable.image1, ph), true));
        categories.add(new Category("elec","Electronics",  safeImg(R.drawable.image1, ph), true));
        categories.add(new Category("agri","Agriculture",  safeImg(R.drawable.image1, ph), false));
        categories.add(new Category("live","Livestock",    safeImg(R.drawable.image1, ph), false));
        categories.add(new Category("rent","Rentals",      safeImg(R.drawable.image1, ph), false));

        subMap.put("veh", list(
                new Subcategory("car","veh","Cars",                 safeImg(R.drawable.image1, ph),  true),
                new Subcategory("bike","veh","Bikes & Scooters",    safeImg(R.drawable.image1, ph),  true),
                new Subcategory("tractor","veh","Tractors",         safeImg(R.drawable.image1, ph),  true),
                new Subcategory("cycle","veh","Bicycles",           safeImg(R.drawable.image1, ph),  true)
        ));
        subMap.put("elec", list(
                new Subcategory("mobile","elec","Mobiles",          safeImg(R.drawable.image1, ph),  true),
                new Subcategory("tv","elec","TVs",                  safeImg(R.drawable.image1, ph),  true),
                new Subcategory("laptop","elec","Laptops",          safeImg(R.drawable.image1, ph),  true),
                new Subcategory("appliance","elec","Appliances",    safeImg(R.drawable.image1, ph),  true)
        ));
        subMap.put("agri", list(
                new Subcategory("seed","agri","Seeds & Fertilizers",safeImg(R.drawable.image1, ph),  false),
                new Subcategory("tool","agri","Tools & Parts",      safeImg(R.drawable.image1, ph),  true),  // only here condition
                new Subcategory("chem","agri","Agri Chemicals",     safeImg(R.drawable.image1, ph),  false)
        ));
        subMap.put("live", list(
                new Subcategory("animal","live","Animals",          safeImg(R.drawable.image1, ph),  false),
                new Subcategory("dairy","live","Dairy Products",    safeImg(R.drawable.image1, ph),  false)
        ));
        subMap.put("rent", list(
                new Subcategory("flat","rent","Flat/House Rent",    safeImg(R.drawable.image1, ph),  false),
                new Subcategory("shop","rent","Shop/Office Rent",   safeImg(R.drawable.image1, ph),  false)
        ));
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
            selectedCategory = new Category(item.id, item.name, item.iconRes, item.requiresCondition);
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

            // Primary category string expected by DynamicFormActivity's schema:
            // We'll pass the category NAME. (If it doesn't match special cases there, it falls back to "General".)
            intent.putExtra(DynamicFormActivity.EXTRA_CATEGORY, selectedCategory.name);

            // Pass rich context too (useful if you want custom schemas later)
            intent.putExtra("category_id", selectedCategory.id);
            intent.putExtra("category_name", selectedCategory.name);
            intent.putExtra("subcategory_id", selectedSub.id);
            intent.putExtra("subcategory_name", selectedSub.name);
            if (selectedCondition != null) intent.putExtra("condition", selectedCondition); // "new" or "used"

            startActivity(intent);
            // Do NOT finish(); so user can come back and change.
        });
    }

    private void loadSubcategories(String catId) {
        List<Subcategory> subs = subMap.get(catId);
        List<SubcategoryGridAdapter.Item> ui = new ArrayList<>();
        if (subs != null) {
            for (Subcategory s : subs) {
                ui.add(new SubcategoryGridAdapter.Item(
                        s.id, s.parentId, s.name, s.iconRes, s.requiresCondition));
            }
        }
        subcategoryAdapter.submit(ui);
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
