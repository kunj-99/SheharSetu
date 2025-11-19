package com.infowave.sheharsetu.Adapter;

import android.app.DatePickerDialog;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.infowave.sheharsetu.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Adapter for dynamic form:
 * Field map keys:
 *  - key, label, hint, type (TEXT, NUMBER, PHONE, EMAIL, DATE, DROPDOWN, CHECKBOX, SWITCH, TEXTAREA, CURRENCY, LOCATION, PHOTOS)
 *  - required (Boolean)
 *  - options (List<String>) for DROPDOWN
 */
public class DynamicFormAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /* ================= Callbacks to Activity ================= */
    public interface Callbacks {
        void pickCoverPhoto(String fieldKey);   // if you have a separate "cover" picker
        void pickMorePhotos(String fieldKey);   // launch ACTION_OPEN_DOCUMENT/MULTIPLE
        void requestMyLocation(String fieldKey);
        void showToast(String msg);
    }

    /* ================= View Types ================= */
    private static final int T_TEXT      = 1;
    private static final int T_DATE      = 2;
    private static final int T_DROPDOWN  = 3;
    private static final int T_CHECKBOX  = 4;
    private static final int T_SWITCH    = 5;
    private static final int T_TEXTAREA  = 6;
    private static final int T_CURRENCY  = 7;
    private static final int T_LOCATION  = 8;
    private static final int T_PHOTOS    = 9;

    private final List<Map<String, Object>> fields;
    private final Map<String, Object> answers = new HashMap<>();
    private final SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private final Callbacks callbacks;

    public DynamicFormAdapter(List<Map<String, Object>> fields, Callbacks callbacks) {
        this.fields = fields != null ? fields : new ArrayList<>();
        this.callbacks = callbacks;
        // initialize answers
        for (Map<String, Object> f : this.fields) {
            String key = s(f.get("key"));
            String type = s(f.get("type")).toUpperCase(Locale.ROOT);
            switch (type) {
                case "CHECKBOX":
                case "SWITCH":
                    answers.put(key, false);
                    break;
                case "PHOTOS": {
                    Map<String, Object> ph = new HashMap<>();
                    ph.put("cover", "");
                    ph.put("more", new ArrayList<String>());
                    answers.put(key, ph);
                    break;
                }
                default:
                    answers.put(key, "");
            }
        }
    }

    @Override public int getItemViewType(int position) {
        String t = s(fields.get(position).get("type")).toUpperCase(Locale.ROOT);
        switch (t) {
            case "DATE":     return T_DATE;
            case "DROPDOWN": return T_DROPDOWN;
            case "CHECKBOX": return T_CHECKBOX;
            case "SWITCH":   return T_SWITCH;
            case "TEXTAREA": return T_TEXTAREA;
            case "CURRENCY": return T_CURRENCY;
            case "LOCATION": return T_LOCATION;
            case "PHOTOS":   return T_PHOTOS;
            default:         return T_TEXT;
        }
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (vt == T_DATE)        return new VHDate(inf.inflate(R.layout.item_form_date, parent, false));
        if (vt == T_DROPDOWN)    return new VHDropdown(inf.inflate(R.layout.item_form_dropdown, parent, false));
        if (vt == T_CHECKBOX)    return new VHCheckbox(inf.inflate(R.layout.item_form_checkbox, parent, false));
        if (vt == T_SWITCH)      return new VHSwitch(inf.inflate(R.layout.item_form_switch, parent, false));
        if (vt == T_TEXTAREA)    return new VHTextArea(inf.inflate(R.layout.item_form_textarea, parent, false));
        if (vt == T_CURRENCY)    return new VHCurrencies(inf.inflate(R.layout.item_form_currency, parent, false));
        if (vt == T_LOCATION)    return new VHLocation(inf.inflate(R.layout.item_form_location, parent, false));
        if (vt == T_PHOTOS)      return new VHPhotos(inf.inflate(R.layout.item_form_photos, parent, false));
        return new VHText(inf.inflate(R.layout.item_form_text, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
        Map<String, Object> f = fields.get(pos);
        String key   = s(f.get("key"));
        String label = s(f.get("label"));
        String hint  = s(f.get("hint"));
        String type  = s(f.get("type"));

        if (h instanceof VHText) {
            VHText vh = (VHText) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.etValue.setHint(hint);
            if ("NUMBER".equalsIgnoreCase(type)) {
                vh.etValue.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            } else if ("PHONE".equalsIgnoreCase(type)) {
                vh.etValue.setInputType(InputType.TYPE_CLASS_PHONE);
            } else if ("EMAIL".equalsIgnoreCase(type)) {
                vh.etValue.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            } else {
                vh.etValue.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            }
            vh.etValue.setText(s(answers.get(key)));
            vh.etValue.addTextChangedListener(new SimpleTextWatcher(s -> answers.put(key, s)));

        } else if (h instanceof VHTextArea) {
            VHTextArea vh = (VHTextArea) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.etValue.setHint(hint);
            vh.etValue.setText(s(answers.get(key)));
            vh.etValue.addTextChangedListener(new SimpleTextWatcher(s -> answers.put(key, s)));

        } else if (h instanceof VHCurrencies) {
            VHCurrencies vh = (VHCurrencies) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.etValue.setHint(hint);
            vh.etValue.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            vh.etValue.setText(s(answers.get(key)));
            vh.etValue.addTextChangedListener(new SimpleTextWatcher(s -> answers.put(key, s)));

        } else if (h instanceof VHDate) {
            VHDate vh = (VHDate) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.etDate.setHint(hint);
            vh.etDate.setOnClickListener(v -> {
                Calendar c = Calendar.getInstance();
                DatePickerDialog dlg = new DatePickerDialog(v.getContext(),
                        (view, year, month, day) -> {
                            Calendar chosen = Calendar.getInstance();
                            chosen.set(year, month, day, 0, 0, 0);
                            String val = df.format(chosen.getTime());
                            vh.etDate.setText(val);
                            answers.put(key, val);
                        },
                        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
                dlg.show();
            });
            vh.etDate.setText(s(answers.get(key)));

        } else if (h instanceof VHDropdown) {
            VHDropdown vh = (VHDropdown) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            @SuppressWarnings("unchecked")
            List<String> opts = (List<String>) f.get("options");
            if (opts == null || opts.isEmpty()) {
                opts = new ArrayList<>();
                opts.add("Select...");
            } else if (!"Select...".equalsIgnoreCase(opts.get(0))) {
                // Ensure first item is the hint
                List<String> withHint = new ArrayList<>();
                withHint.add("Select...");
                withHint.addAll(opts);
                opts = withHint;
            }

            final Context ctx = vh.itemView.getContext();
            final List<String> finalOpts = opts;

            // Custom adapter: disables pos=0 and applies hint color
            ArrayAdapter<String> ad = new ArrayAdapter<String>(ctx, R.layout.spinner_item, finalOpts) {
                @Override public boolean isEnabled(int position) {
                    return position != 0; // disable "Select..."
                }
                @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    View v = super.getDropDownView(position, convertView, parent);
                    TextView tv = (TextView) v;
                    int color = (position == 0)
                            ? ContextCompat.getColor(ctx, R.color.ss_hint)
                            : ContextCompat.getColor(ctx, R.color.ss_on_surface);
                    tv.setTextColor(color);
                    return v;
                }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    TextView tv = v.findViewById(R.id.spinnerText);
                    if (tv == null && v instanceof TextView) tv = (TextView) v;
                    if (tv != null) {
                        int color = (position == 0)
                                ? ContextCompat.getColor(ctx, R.color.ss_hint)
                                : ContextCompat.getColor(ctx, R.color.ss_on_surface);
                        tv.setTextColor(color);
                    }
                    return v;
                }
            };
            ad.setDropDownViewResource(R.layout.spinner_dropdown_item);
            vh.spinner.setAdapter(ad);

            // Restore selection if we have a saved answer
            String saved = s(answers.get(key));
            int idx = Math.max(0, finalOpts.indexOf(saved));
            vh.spinner.setSelection(idx);

            vh.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    // Ignore hint row
                    if (position == 0) {
                        answers.put(key, ""); // treat as empty
                    } else {
                        answers.put(key, finalOpts.get(position));
                    }

                    // Ensure selected view uses correct color
                    if (view instanceof TextView) {
                        ((TextView) view).setTextColor(
                                ContextCompat.getColor(ctx, position == 0 ? R.color.ss_hint : R.color.ss_on_surface)
                        );
                    } else if (view != null) {
                        TextView tv = view.findViewById(R.id.spinnerText);
                        if (tv != null) {
                            tv.setTextColor(
                                    ContextCompat.getColor(ctx, position == 0 ? R.color.ss_hint : R.color.ss_on_surface)
                            );
                        }
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });

        } else if (h instanceof VHCheckbox) {
            VHCheckbox vh = (VHCheckbox) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.cb.setText(hint);
            boolean checked = answers.get(key) instanceof Boolean && (Boolean) answers.get(key);
            vh.cb.setChecked(checked);
            vh.cb.setOnCheckedChangeListener((buttonView, isChecked) -> answers.put(key, isChecked));

        } else if (h instanceof VHSwitch) {
            VHSwitch vh = (VHSwitch) h;
            vh.tvLabel.setText(label);
            boolean on = answers.get(key) instanceof Boolean && (Boolean) answers.get(key);
            vh.sw.setChecked(on);
            vh.sw.setOnCheckedChangeListener((buttonView, isChecked) -> answers.put(key, isChecked));

        } else if (h instanceof VHLocation) {
            VHLocation vh = (VHLocation) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.etLocation.setHint(hint);
            vh.etLocation.setText(s(answers.get(key)));
            vh.etLocation.addTextChangedListener(new SimpleTextWatcher(s -> answers.put(key, s)));
            vh.btnUseMyLocation.setOnClickListener(v -> { if (callbacks != null) callbacks.requestMyLocation(key); });

        } else if (h instanceof VHPhotos) {
            VHPhotos vh = (VHPhotos) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.tvHelper.setText(TextUtils.isEmpty(hint) ? "Clear, no blur" : hint);
            if (vh.tvTip != null) {
                vh.tvTip.setText("Tip: The first selected photo becomes the cover. Tap any thumbnail to change or remove.");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> ph = (Map<String, Object>) answers.get(key);
            String cover = ph == null ? "" : s(ph.get("cover"));
            @SuppressWarnings("unchecked")
            List<String> more = ph == null ? new ArrayList<>() : (List<String>) ph.get("more");
            if (more == null) more = new ArrayList<>();

            if (vh.rv.getLayoutManager() == null) {
                vh.rv.setLayoutManager(new LinearLayoutManager(vh.itemView.getContext(), RecyclerView.HORIZONTAL, false));
            }
            PhotosStripAdapter psa = new PhotosStripAdapter(
                    key,
                    cover,
                    more,
                    new PhotosStripAdapter.Events() {
                        @Override
                        public void onAddMore(String fieldKey) {
                            if (callbacks != null) callbacks.pickMorePhotos(fieldKey);
                        }

                        @Override
                        public void onSetCover(String fieldKey, int indexInList, Uri uri) {
                            setCoverFromMore(fieldKey, indexInList);
                        }

                        @Override
                        public void onRemove(String fieldKey, String uriStr) {
                            removePhoto(fieldKey, uriStr);
                        }
                    }
            );

            vh.rv.setAdapter(psa);

            String msg = (TextUtils.isEmpty(cover) ? "Cover: not selected" : "Cover: selected")
                    + "   |   More: " + more.size() + " selected";
            vh.tvPhotoStatus.setText(msg);
        }
    }

    @Override public int getItemCount() { return fields.size(); }

    /* ================= Photos helpers ================= */

    private void setCoverFromMore(String fieldKey, int indexInMore) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) answers.get(fieldKey);
        if (ph == null) return;
        String currentCover = s(ph.get("cover"));
        @SuppressWarnings("unchecked")
        List<String> more = (List<String>) ph.get("more");
        if (more == null || indexInMore < 0 || indexInMore >= more.size()) return;

        String newCover = more.get(indexInMore);
        more.set(indexInMore, currentCover);
        ph.put("cover", newCover);
        notifyDataSetChanged();
    }

    public void removePhoto(String fieldKey, String uri) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) answers.get(fieldKey);
        if (ph == null) return;

        String cover = s(ph.get("cover"));
        @SuppressWarnings("unchecked")
        List<String> more = (List<String>) ph.get("more");

        boolean removed = false;
        if (!TextUtils.isEmpty(cover) && cover.equals(uri)) {
            ph.put("cover", "");
            removed = true;
        }
        if (more != null) removed = more.remove(uri) || removed;

        if (removed) {
            if (TextUtils.isEmpty(s(ph.get("cover"))) && more != null && !more.isEmpty()) {
                ph.put("cover", more.get(0));
                more.remove(0);
                toast("Cover removed. Promoted next image as cover.");
            } else {
                toast("Photo removed.");
            }
            notifyDataSetChanged();
        }
    }

    /** Set a text-type answer (e.g., programmatic location). */
    public void setTextAnswer(String fieldKey, String value) {
        answers.put(fieldKey, value == null ? "" : value);
        notifyDataSetChanged();
    }

    /** Set cover photo from picker (optional separate picker). */
    public void setCoverPhoto(String fieldKey, Uri uri) {
        if (uri == null) return;
        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) answers.get(fieldKey);
        if (ph == null) return;
        ph.put("cover", uri.toString());
        notifyDataSetChanged();
    }

    /** Add multiple photos; if no cover yet, first becomes cover. */
    public void addMorePhotos(String fieldKey, List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;

        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) answers.get(fieldKey);
        if (ph == null) return;

        String cover = s(ph.get("cover"));
        @SuppressWarnings("unchecked")
        List<String> more = (List<String>) ph.get("more");
        if (more == null) {
            more = new ArrayList<>();
            ph.put("more", more);
        }

        boolean coverWasEmpty = TextUtils.isEmpty(cover);

        if (coverWasEmpty) {
            ph.put("cover", uris.get(0).toString());
            for (int i = 1; i < uris.size(); i++) {
                Uri u = uris.get(i);
                if (u != null) more.add(u.toString());
            }
        } else {
            for (Uri u : uris) if (u != null) more.add(u.toString());
        }

        notifyDataSetChanged();
        if (coverWasEmpty) {
            toast("First photo set as cover. Tap any thumbnail to change or remove.");
        } else {
            toast("Added " + uris.size() + " photo(s). Tap a thumbnail to set cover or remove.");
        }
    }

    /* ================= Validation & JSON ================= */

    public JSONObject validateAndBuildResult() {
        try {
            for (Map<String, Object> f : fields) {
                String key = s(f.get("key"));
                String label = s(f.get("label"));
                String type = s(f.get("type"));
                boolean required = req(f);

                Object val = answers.get(key);
                String sval = val == null ? "" : String.valueOf(val);

                if (required) {
                    if ("CHECKBOX".equalsIgnoreCase(type)) {
                        if (!(val instanceof Boolean) || !((Boolean) val)) {
                            toast("Please accept: " + label); return null;
                        }
                    } else if ("DROPDOWN".equalsIgnoreCase(type)) {
                        @SuppressWarnings("unchecked") List<String> opts = (List<String>) f.get("options");
                        if (opts != null && !opts.isEmpty()) {
                            if (TextUtils.isEmpty(sval) || "Select...".equalsIgnoreCase(sval)) {
                                toast("Please select " + label); return null;
                            }
                        }
                    } else if ("PHOTOS".equalsIgnoreCase(type)) {
                        @SuppressWarnings("unchecked") Map<String, Object> ph = (Map<String, Object>) val;
                        String cover = ph == null ? "" : s(ph.get("cover"));
                        if (TextUtils.isEmpty(cover)) { toast("Please add a cover photo"); return null; }
                    } else {
                        if (TextUtils.isEmpty(sval)) { toast(label + " is required"); return null; }
                    }
                }

                if ("EMAIL".equalsIgnoreCase(type) && !TextUtils.isEmpty(sval) &&
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(sval).matches()) {
                    toast("Please enter a valid Email");
                    return null;
                }
            }

            JSONObject result = new JSONObject();
            for (Map.Entry<String, Object> e : answers.entrySet()) {
                String key = e.getKey();
                Object v = e.getValue();
                if (v instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) v;
                    JSONObject jo = new JSONObject();
                    for (Map.Entry<?, ?> me : m.entrySet()) {
                        if (me.getValue() instanceof List) {
                            JSONArray arr = new JSONArray();
                            for (Object o : (List<?>) me.getValue()) arr.put(String.valueOf(o));
                            jo.put(String.valueOf(me.getKey()), arr);
                        } else {
                            jo.put(String.valueOf(me.getKey()), String.valueOf(me.getValue()));
                        }
                    }
                    result.put(key, jo);
                } else {
                    result.put(key, v);
                }
            }
            return result;

        } catch (Exception e) {
            toast("Error building JSON");
            return null;
        }
    }

    private void toast(String s) { if (callbacks != null) callbacks.showToast(s); }

    /* ================= Photos strip adapter ================= */

    static class PhotosStripAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        interface Events {
            void onAddMore(String fieldKey);
            void onSetCover(String fieldKey, int indexInList, Uri uri);
            void onRemove(String fieldKey, String uriStr);
        }

        private static final int V_THUMB = 1;
        private static final int V_ADD   = 2;

        private final String fieldKey;
        private final String cover;          // uri string (may be empty)
        private final List<String> more;     // uri strings
        private final Events events;

        PhotosStripAdapter(String fieldKey, String cover, List<String> more, Events events) {
            this.fieldKey = fieldKey;
            this.cover = cover == null ? "" : cover;
            this.more  = more == null ? new ArrayList<>() : more;
            this.events = events;
        }

        @Override public int getItemCount() { return (TextUtils.isEmpty(cover) ? 0 : 1) + more.size() + 1; }

        @Override public int getItemViewType(int position) {
            int thumbs = (TextUtils.isEmpty(cover) ? 0 : 1) + more.size();
            return position == thumbs ? V_ADD : V_THUMB;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (vt == V_ADD) {
                View v = inf.inflate(R.layout.item_photo_add, parent, false);
                return new VHAdd(v);
            } else {
                View v = inf.inflate(R.layout.item_photo_thumb, parent, false);
                return new VHT(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
            if (holder instanceof VHAdd) {
                holder.itemView.setOnClickListener(v -> { if (events != null) events.onAddMore(fieldKey); });
                return;
            }

            // ---- Thumbnail binding ----
            Uri uri;
            boolean isCover;
            int idxInMore;

            if (!TextUtils.isEmpty(cover)) {
                if (pos == 0) {
                    idxInMore = -1;
                    uri = Uri.parse(cover); isCover = true;
                } else {
                    idxInMore = pos - 1;
                    uri = Uri.parse(more.get(idxInMore)); isCover = false;
                }
            } else {
                idxInMore = pos;
                uri = Uri.parse(more.get(idxInMore)); isCover = false;
            }

            VHT vh = (VHT) holder;
            vh.iv.setImageURI(uri);
            vh.badge.setVisibility(isCover ? View.VISIBLE : View.GONE);
            if (vh.container != null) {
                vh.container.setBackgroundResource(isCover ? R.drawable.bg_thumb_cover : R.drawable.bg_thumb_normal);
            }
            vh.name.setText(fileName(vh.itemView.getContext(), uri));

            // Remove button
            vh.remove.setVisibility(View.VISIBLE);
            vh.remove.bringToFront();
            vh.remove.setOnClickListener(v -> {
                if (events != null) events.onRemove(fieldKey, uri.toString());
            });

            // Tap/long-press to set cover for non-cover items
            if (!isCover) {
                vh.itemView.setOnClickListener(v -> {
                    if (events != null) events.onSetCover(fieldKey, idxInMore, uri);
                });
                vh.itemView.setOnLongClickListener(v -> {
                    if (events != null) events.onSetCover(fieldKey, idxInMore, uri);
                    return true;
                });
            } else {
                vh.itemView.setOnClickListener(null);
                vh.itemView.setOnLongClickListener(null);
            }
        }

        static class VHT extends RecyclerView.ViewHolder {
            View container; ImageView iv; TextView name; TextView badge; ImageView remove;
            VHT(@NonNull View v) {
                super(v);
                container = v.findViewById(R.id.thumbContainer);
                iv = v.findViewById(R.id.ivThumb);
                name = v.findViewById(R.id.tvName);
                badge = v.findViewById(R.id.tvBadge);
                remove = v.findViewById(R.id.ivRemove);
            }
        }
        static class VHAdd extends RecyclerView.ViewHolder { VHAdd(@NonNull View v) { super(v); } }

        private static String fileName(Context ctx, Uri uri) {
            String result = null;
            if ("content".equals(uri.getScheme())) {
                try (Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (idx >= 0) result = cursor.getString(idx);
                    }
                } catch (Exception ignored) {}
            }
            if (result == null) {
                result = uri.getLastPathSegment();
                if (result == null) result = uri.toString();
            }
            return result;
        }
    }

    /* ================= ViewHolders ================= */

    static class VHText extends RecyclerView.ViewHolder {
        TextView tvLabel; EditText etValue;
        VHText(@NonNull View v) { super(v); tvLabel = v.findViewById(R.id.tvLabel); etValue = v.findViewById(R.id.etValue); }
    }
    static class VHTextArea extends RecyclerView.ViewHolder {
        TextView tvLabel; EditText etValue;
        VHTextArea(@NonNull View v) { super(v); tvLabel = v.findViewById(R.id.tvLabel); etValue = v.findViewById(R.id.etValue); }
    }
    static class VHCurrencies extends RecyclerView.ViewHolder {
        TextView tvLabel; EditText etValue;
        VHCurrencies(@NonNull View v) { super(v); tvLabel = v.findViewById(R.id.tvLabel); etValue = v.findViewById(R.id.etValue); }
    }
    static class VHDate extends RecyclerView.ViewHolder {
        TextView tvLabel; EditText etDate;
        VHDate(@NonNull View v) { super(v); tvLabel = v.findViewById(R.id.tvLabel); etDate = v.findViewById(R.id.etDate); }
    }
    static class VHDropdown extends RecyclerView.ViewHolder {
        TextView tvLabel; Spinner spinner;
        VHDropdown(@NonNull View v) { super(v); tvLabel = v.findViewById(R.id.tvLabel); spinner = v.findViewById(R.id.spinner); }
    }
    static class VHCheckbox extends RecyclerView.ViewHolder {
        TextView tvLabel; CheckBox cb;
        VHCheckbox(@NonNull View v) { super(v); tvLabel = v.findViewById(R.id.tvLabel); cb = v.findViewById(R.id.cb); }
    }
    static class VHSwitch extends RecyclerView.ViewHolder {
        TextView tvLabel; Switch sw;
        VHSwitch(@NonNull View v) { super(v); tvLabel = v.findViewById(R.id.tvLabel); sw = v.findViewById(R.id.sw); }
    }
    static class VHLocation extends RecyclerView.ViewHolder {
        TextView tvLabel; EditText etLocation; Button btnUseMyLocation;
        VHLocation(@NonNull View v) { super(v); tvLabel = v.findViewById(R.id.tvLabel); etLocation = v.findViewById(R.id.etLocation); btnUseMyLocation = v.findViewById(R.id.btnUseMyLocation); }
    }
    static class VHPhotos extends RecyclerView.ViewHolder {
        TextView tvLabel, tvHelper, tvTip, tvPhotoStatus; RecyclerView rv;
        VHPhotos(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            tvHelper = v.findViewById(R.id.tvHelper);
            tvTip = v.findViewById(R.id.tvTip);        // optional (safe if null)
            tvPhotoStatus = v.findViewById(R.id.tvPhotoStatus);
            rv = v.findViewById(R.id.rvPhotosStrip);
        }
    }

    /* ================= Utils ================= */
    private static String s(Object o) { return o == null ? "" : String.valueOf(o); }
    private static boolean req(Map<String, Object> f) {
        Object r = f.get("required");
        return r instanceof Boolean && (Boolean) r;
    }

    public interface OnText { void on(String s); }
    static class SimpleTextWatcher implements android.text.TextWatcher {
        private final OnText cb; SimpleTextWatcher(OnText cb){this.cb=cb;}
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { if (cb!=null) cb.on(s.toString()); }
        @Override public void afterTextChanged(android.text.Editable s) {}
    }
}
