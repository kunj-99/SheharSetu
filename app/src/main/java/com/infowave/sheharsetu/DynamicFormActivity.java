package com.infowave.sheharsetu;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.graphics.Insets;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.infowave.sheharsetu.Adapter.DynamicFormAdapter;
import com.infowave.sheharsetu.net.ApiRoutes;
import com.infowave.sheharsetu.net.VolleySingleton;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DynamicFormActivity extends AppCompatActivity implements DynamicFormAdapter.Callbacks {

    public static final String EXTRA_CATEGORY = "categoryName";
    public static final String RESULT_JSON    = "formResultJson";

    private TextView tvTitle;
    private RecyclerView rvForm;
    private Button btnSubmit;

    private DynamicFormAdapter adapter;

    private String currentPhotoFieldKey;
    private String pendingLocationFieldKey;

    private FusedLocationProviderClient fused;

    // NEW: user + category info (for create_listing.php)
    private long userId;
    private long categoryId;
    private long subcategoryId;

    /* ---------------- Photo pickers ---------------- */

    private final ActivityResultLauncher<String> coverPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && currentPhotoFieldKey != null) {
                    adapter.setCoverPhoto(currentPhotoFieldKey, uri);
                }
            });

    private final ActivityResultLauncher<String> morePicker =
            registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris != null && currentPhotoFieldKey != null) {
                    adapter.addMorePhotos(currentPhotoFieldKey, uris);
                }
            });

    /* ---------------- Permissions ---------------- */

    private final ActivityResultLauncher<String> locationPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) fillMyLocation(pendingLocationFieldKey);
                else toast("Location permission denied");
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dynamic_form);
        applyThemeBarsAndWidgets();

        tvTitle   = findViewById(R.id.tvTitle);
        rvForm    = findViewById(R.id.rvForm);
        btnSubmit = findViewById(R.id.btnSubmit);

        fused = LocationServices.getFusedLocationProviderClient(this);

        // category name (UI only)
        String category = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (category == null) category = "General";
        tvTitle.setText("Dynamic Form (" + category + ")");

        // IDs from CategorySelectActivity (ensure you are putting them in intent)
        categoryId    = getIntent().getLongExtra("category_id", 0L);
        subcategoryId = getIntent().getLongExtra("subcategory_id", 0L);

        // user_id from SharedPreferences (you must set it after login/OTP verify)
        SharedPreferences prefs = getSharedPreferences("user", MODE_PRIVATE);
        userId = prefs.getLong("user_id", 0L);

        List<Map<String, Object>> schema = buildSchema(category);

        adapter = new DynamicFormAdapter(schema, this);
        rvForm.setLayoutManager(new LinearLayoutManager(this));
        rvForm.setAdapter(adapter);

        btnSubmit.setOnClickListener(v -> {
            JSONObject result = adapter.validateAndBuildResult();
            if (result == null) {
                toast("Please complete required fields correctly.");
                return;
            }
            // पहले हम result JSON को server पर भेजेंगे
            submitListing(result);
        });

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);

        WindowInsetsControllerCompat wic =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        wic.setAppearanceLightStatusBars(false);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    bars.top,
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
            return insets;
        });
    }

    /** Build schema (same जैसा पहले) */
    private List<Map<String, Object>> buildSchema(String category) {
        String catLower = category.trim().toLowerCase(Locale.ROOT);

        if (catLower.contains("vehicle") || catLower.contains("bike") ||
                catLower.contains("car") || catLower.contains("electronics")) {

            java.util.ArrayList<Map<String, Object>> list = new java.util.ArrayList<>();
            add(list, "photos", field("Upload Photos", "Clear, no blur", "PHOTOS", true));

            add(list, "brand",      field("Brand",        "Enter brand",              "TEXT",     true));
            add(list, "model",      dropdown("Model",     "Select model",             true, Arrays.asList("Select...", "Model A", "Model B", "Model C")));
            add(list, "year",       field("Year",         "Enter year (e.g., 2022)",  "NUMBER",   true));
            add(list, "fuel_type",  dropdown("Fuel Type", "Select fuel",              true, Arrays.asList("Select...", "Petrol", "Diesel", "CNG", "Electric", "Hybrid")));
            add(list, "kilometers", field("Kilometers",   "Total driven (km)",        "NUMBER",   false));
            add(list, "transmission", dropdown("Transmission","Select transmission",  false, Arrays.asList("Select...", "Manual", "Automatic", "AMT", "CVT")));

            add(list, "price",      field("Price (₹)",    "₹",                        "CURRENCY", true));
            add(list, "negotiable", field("Negotiable",   "",                         "SWITCH",   false));

            add(list, "location",   field("Location",     "Enter location",           "LOCATION", false));
            add(list, "whatsapp",   field("WhatsApp",     "",                         "SWITCH",   false));
            add(list, "description",field("Description",  "Write details...",         "TEXTAREA", false));
            add(list, "accept_terms", checkbox("Accept Terms","You must accept terms to continue", true));
            return list;
        }

        // Generic
        java.util.ArrayList<Map<String, Object>> list = new java.util.ArrayList<>();
        add(list, "full_name",  field("Full Name", "Enter your full name", "TEXT",  true));
        add(list, "phone",      field("Phone Number", "Enter your mobile number", "PHONE", true));
        add(list, "email",      field("Email", "Enter a valid email", "EMAIL", false));
        add(list, "dob",        field("Date of Birth", "Select your date of birth", "DATE", false));

        // अलग category logic यहाँ add कर सकते हैं (जैसा पहले था)

        add(list, "remarks",   field("Remarks", "Additional notes (optional)", "TEXT", false));
        add(list, "accept_terms", checkbox("Accept Terms", "You must accept terms to continue", true));
        return list;
    }

    private void add(List<Map<String, Object>> list, String key, Map<String, Object> m) {
        m.put("key", key);
        list.add(m);
    }

    private Map<String, Object> field(String label, String hint, String type, boolean required) {
        Map<String, Object> m = new HashMap<>();
        m.put("label", label);
        m.put("hint", hint);
        m.put("type", type);
        m.put("required", required);
        return m;
    }

    private Map<String, Object> dropdown(String label, String hint, boolean required, List<String> options) {
        Map<String, Object> m = field(label, hint, "DROPDOWN", required);
        m.put("options", options);
        return m;
    }

    private Map<String, Object> checkbox(String label, String hint, boolean required) {
        return field(label, hint, "CHECKBOX", required);
    }

    /* ================== Callbacks from Adapter ================== */

    @Override
    public void pickCoverPhoto(String fieldKey) {
        currentPhotoFieldKey = fieldKey;
        requestReadPhotoPermissionIfNeeded();
        coverPicker.launch("image/*");
    }

    @Override
    public void pickMorePhotos(String fieldKey) {
        currentPhotoFieldKey = fieldKey;
        requestReadPhotoPermissionIfNeeded();
        morePicker.launch("image/*");
    }

    @Override
    public void requestMyLocation(String fieldKey) {
        pendingLocationFieldKey = fieldKey;
        locationPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @Override
    public void showToast(String msg) { toast(msg); }

    /* ================== Networking: submit listing ================== */

    private void submitListing(JSONObject formResult) {
        if (userId <= 0) {
            toast("User not logged in. Please login again.");
            return;
        }
        if (categoryId <= 0) {
            toast("Category information missing.");
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("user_id", userId);
            payload.put("category_id", categoryId);
            if (subcategoryId > 0) payload.put("subcategory_id", subcategoryId);

            String title = buildTitleFromForm(formResult);
            payload.put("title", title);
            payload.put("form_data", formResult);

            btnSubmit.setEnabled(false);

            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.POST,
                    ApiRoutes.CREATE_LISTING,
                    payload,
                    resp -> {
                        btnSubmit.setEnabled(true);
                        boolean ok = resp.optBoolean("success", false);
                        String msg = resp.optString("message", ok ? "Listing created" : "Failed to create listing");
                        toast(msg);
                        if (ok) {
                            // long listingId = resp.optLong("listing_id", 0L);
                            // यहाँ बाद में detail screen पर जा सकते हैं
                            finish();
                        }
                    },
                    error -> {
                        btnSubmit.setEnabled(true);
                        String msg = "Server error";
                        if (error != null && error.networkResponse != null) {
                            msg = "Error " + error.networkResponse.statusCode;
                        }
                        toast(msg);
                    }
            );

            VolleySingleton.getInstance(this).add(req);

        } catch (Exception e) {
            btnSubmit.setEnabled(true);
            toast("Error preparing request");
        }
    }

    /** Title बनाने के लिए छोटा helper */
    private String buildTitleFromForm(JSONObject form) {
        try {
            String brand  = form.optString("brand", "").trim();
            String model  = form.optString("model", "").trim();
            String year   = form.optString("year", "").trim();
            String product= form.optString("product", "").trim();

            StringBuilder sb = new StringBuilder();
            if (!brand.isEmpty()) sb.append(brand);
            if (!model.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(model);
            }
            if (!year.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(year);
            }
            if (sb.length() == 0 && !product.isEmpty()) {
                sb.append(product);
            }
            if (sb.length() == 0) {
                sb.append("Listing");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Listing";
        }
    }

    /* ================== Helpers ================== */

    private void requestReadPhotoPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            // system picker – कोई extra permission नहीं
        } else {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1231);
        }
    }

    private void fillMyLocation(String fieldKey) {
        if (fieldKey == null) return;
        try {
            fused.getLastLocation().addOnSuccessListener(loc -> {
                if (loc == null) { toast("Unable to fetch location"); return; }
                try {
                    Geocoder geo = new Geocoder(this, Locale.getDefault());
                    java.util.List<Address> res = geo.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                    String addr;
                    if (res != null && !res.isEmpty()) {
                        Address a = res.get(0);
                        String locality = a.getLocality() == null ? "" : a.getLocality();
                        String admin    = a.getAdminArea() == null ? "" : a.getAdminArea();
                        addr = (locality + (admin.isEmpty() ? "" : ", " + admin)).trim();
                        if (addr.isEmpty()) addr = a.getFeatureName();
                    } else {
                        addr = loc.getLatitude() + "," + loc.getLongitude();
                    }
                    adapter.setTextAnswer(fieldKey, addr);
                    toast("Location set");
                } catch (Exception e) {
                    toast("Geocoder failed");
                }
            });
        } catch (SecurityException ignored) { }
    }

    private void applyThemeBarsAndWidgets() {
        try {
            getWindow().setStatusBarColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.black));
            getWindow().setNavigationBarColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.black));
            getWindow().getDecorView().setSystemUiVisibility(0);
        } catch (Exception ignored) { }

        View root = findViewById(R.id.root);
        if (root != null) {
            root.setBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.ss_surface));
        }

        try {
            androidx.appcompat.widget.Toolbar tb = findViewById(R.id.topBar);
            if (tb != null) {
                tb.setBackgroundColor(
                        androidx.core.content.ContextCompat.getColor(this, R.color.ss_primary));
                tb.setTitleTextColor(
                        androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
                if (tb.getNavigationIcon() != null) {
                    tb.getNavigationIcon().setTint(
                            androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
                }
            }
        } catch (Exception ignored) { }

        try {
            Button btn = findViewById(R.id.btnSubmit);
            if (btn != null) {
                btn.setBackground(
                        androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_btn_primary));
                btn.setTextColor(
                        androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
            }
        } catch (Exception ignored) { }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
