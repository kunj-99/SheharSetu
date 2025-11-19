package com.infowave.sheharsetu;

import android.Manifest;
import android.content.Intent;
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
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.infowave.sheharsetu.Adapter.DynamicFormAdapter;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;



public class DynamicFormActivity extends AppCompatActivity implements DynamicFormAdapter.Callbacks {
   // clears LIGHT_STATUS_BAR so icons are light

    public static final String EXTRA_CATEGORY = "categoryName";
    public static final String RESULT_JSON = "formResultJson";

    private TextView tvTitle;
    private RecyclerView rvForm;
    private Button btnSubmit;

    private DynamicFormAdapter adapter;

    private String currentPhotoFieldKey;          // which PHOTOS field we’re updating
    private String pendingLocationFieldKey;       // which LOCATION field asked for “use my location”

    private FusedLocationProviderClient fused;

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

    // NOTE: pendingLocationFieldKey is declared above this lambda -> no illegal forward reference
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


        tvTitle  = findViewById(R.id.tvTitle);
        rvForm   = findViewById(R.id.rvForm);
        btnSubmit= findViewById(R.id.btnSubmit);

        fused = LocationServices.getFusedLocationProviderClient(this);

        String category = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (category == null) category = "General";
        tvTitle.setText("Dynamic Form (" + category + ")");

        List<Map<String, Object>> schema = buildSchema(category);

        // Pass this Activity as callbacks target
        adapter = new DynamicFormAdapter(schema, this);
        rvForm.setLayoutManager(new LinearLayoutManager(this));
        rvForm.setAdapter(adapter);

        btnSubmit.setOnClickListener(v -> {
            JSONObject result = adapter.validateAndBuildResult();
            if (result == null) {
                toast("Please complete required fields correctly.");
                return;
            }
            Intent data = new Intent();
            data.putExtra(RESULT_JSON, result.toString());
            setResult(RESULT_OK, data);
            finish();
        });


        WindowCompat.setDecorFitsSystemWindows(getWindow(), true); // edge-to-edge नहीं चाहिए
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);

        WindowInsetsControllerCompat wic =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        wic.setAppearanceLightStatusBars(false);


        // push your content below the status bar
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    bars.top,                   // top padding = status bar height
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
            return insets;
        });
    }

    /** Build schema: Vehicles-style if Vehicles/Electronics/etc., else your previous categories. */
    private List<Map<String, Object>> buildSchema(String category) {
        String catLower = category.trim().toLowerCase(Locale.ROOT);

        if (catLower.contains("vehicle") || catLower.contains("bike") || catLower.contains("car") || catLower.contains("electronics")) {
            // --- VEHICLES STYLE ---
            java.util.ArrayList<Map<String, Object>> list = new java.util.ArrayList<>();
            add(list, "photos", field("Upload Photos", "Clear, no blur", "PHOTOS", true));

            add(list, "brand",        field("Brand",        "Enter brand",              "TEXT",     true));
            add(list, "model",        dropdown("Model",     "Select model",             true, Arrays.asList("Select...", "Model A", "Model B", "Model C")));
            add(list, "year",         field("Year",         "Enter year (e.g., 2022)",  "NUMBER",   true));
            add(list, "fuel_type",    dropdown("Fuel Type", "Select fuel",              true, Arrays.asList("Select...", "Petrol", "Diesel", "CNG", "Electric", "Hybrid")));
            add(list, "kilometers",   field("Kilometers",   "Total driven (km)",        "NUMBER",   false));
            add(list, "transmission", dropdown("Transmission","Select transmission",    false, Arrays.asList("Select...", "Manual", "Automatic", "AMT", "CVT")));

            add(list, "price",        field("Price (₹)",    "₹",                        "CURRENCY", true));
            add(list, "negotiable",   field("Negotiable",   "",                         "SWITCH",   false));

            add(list, "location",     field("Location",     "Enter location",           "LOCATION", false));
            add(list, "whatsapp",     field("WhatsApp",     "",                         "SWITCH",   false));
            add(list, "description",  field("Description",  "Write details...",         "TEXTAREA", false));
            add(list, "accept_terms", checkbox("Accept Terms","You must accept terms to continue", true));
            return list;
        }

        // --- previous generic schemas ---
        java.util.ArrayList<Map<String, Object>> list = new java.util.ArrayList<>();
        add(list, "full_name",  field("Full Name", "Enter your full name", "TEXT",  true));
        add(list, "phone",      field("Phone Number", "Enter your mobile number", "PHONE", true));
        add(list, "email",      field("Email", "Enter a valid email", "EMAIL", false));
        add(list, "dob",        field("Date of Birth", "Select your date of birth", "DATE", false));

        if ("waste buyer".equalsIgnoreCase(catLower)) {
            add(list, "material_type", dropdown("Material Type", "Choose a material", true,
                    Arrays.asList("Select...", "Aluminum", "Copper", "Iron/Steel", "Plastic", "Paper")));
            add(list, "quantity",      field("Required Quantity (kg)", "Enter quantity in kilograms", "NUMBER", true));
            add(list, "transport_needed", checkbox("Accept Terms", "Tick if you need transport (and accept terms)", false));
        } else if ("transport".equalsIgnoreCase(catLower)) {
            add(list, "vehicle",       dropdown("Vehicle Type", "Select vehicle type", true,
                    Arrays.asList("Select...", "Mini Truck", "Pickup", "Tempo", "Tractor")));
            add(list, "distance_km",   field("Approx Distance (km)", "Enter approx. distance in km", "NUMBER", true));
            add(list, "scheduled_date",field("Scheduled Date", "Select a date", "DATE", true));
        } else if ("agriculture".equalsIgnoreCase(catLower)) {
            add(list, "product",       field("Agri Product", "Enter product name (e.g., Cotton, Wheat)", "TEXT", true));
            add(list, "grade",         dropdown("Product Grade", "Select grade", false, Arrays.asList("Select...", "A", "B", "C")));
            add(list, "price",         field("Expected Price (₹/unit)", "Enter your expected price", "NUMBER", false));
        } else {
            add(list, "remarks",       field("Remarks", "Additional notes (optional)", "TEXT", false));
        }
        add(list, "accept_terms", checkbox("Accept Terms", "You must accept terms to continue", true));
        return list;
    }

    private void add(List<Map<String, Object>> list, String key, Map<String, Object> m) { m.put("key", key); list.add(m); }
    private Map<String, Object> field(String label, String hint, String type, boolean required) {
        Map<String, Object> m = new HashMap<>();
        m.put("label", label);
        m.put("hint", hint);
        m.put("type", type); // TEXT, NUMBER, PHONE, EMAIL, DATE, DROPDOWN, CHECKBOX, SWITCH, TEXTAREA, CURRENCY, LOCATION, PHOTOS
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

    /* ================== Helpers ================== */

    private void requestReadPhotoPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            // Using system picker: no explicit READ_MEDIA_IMAGES required here.
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
                    // Push into adapter so UI updates immediately
                    adapter.setTextAnswer(fieldKey, addr);
                    toast("Location set");
                } catch (Exception e) {
                    toast("Geocoder failed");
                }
            });
        } catch (SecurityException ignored) { }
    }



    // === Apply app theme (purple + white) without using styles.xml ===
    private void applyThemeBarsAndWidgets() {
        // Status & Navigation bars
        try {
            getWindow().setStatusBarColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.black));
            getWindow().setNavigationBarColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.black));
            // Make status bar icons light (on dark bar)
            getWindow().getDecorView().setSystemUiVisibility(0);
        } catch (Exception ignored) { }

        // Screen background
        View root = findViewById(R.id.root);
        if (root != null) {
            root.setBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.ss_surface));
        }

        // Toolbar (if you have one with id topBar)
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

        // Submit button
        try {
            android.widget.Button btn = findViewById(R.id.btnSubmit);
            if (btn != null) {
                // background
                btn.setBackground(
                        androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_btn_primary));
                // text color
                btn.setTextColor(
                        androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
            }
        } catch (Exception ignored) { }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
