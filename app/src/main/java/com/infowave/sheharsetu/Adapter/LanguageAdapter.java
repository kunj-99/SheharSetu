package com.infowave.sheharsetu.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.infowave.sheharsetu.R;

import java.util.List;

public class LanguageAdapter extends RecyclerView.Adapter<LanguageAdapter.VH> {

    public interface OnLanguageClick {
        void onLanguageSelected(String[] lang);
    }

    private final List<String[]> languages;
    private final OnLanguageClick onLanguageClick;
    private int selectedPosition = 0;

    public LanguageAdapter(List<String[]> languages, OnLanguageClick onLanguageClick) {
        this.languages = languages;
        this.onLanguageClick = onLanguageClick;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_language, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String[] language = languages.get(position);

        holder.tvNativeName.setText(language[1]);
        holder.tvEnglishName.setText(language[2]);
        holder.radioButton.setChecked(position == selectedPosition);

        holder.itemView.setOnClickListener(v -> {
            int oldPosition = selectedPosition;
            selectedPosition = position;

            if (oldPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(oldPosition);
            }
            notifyItemChanged(selectedPosition);

            if (onLanguageClick != null) {
                onLanguageClick.onLanguageSelected(language);
            }
        });

        holder.radioButton.setOnClickListener(v -> {
            int oldPosition = selectedPosition;
            selectedPosition = position;

            if (oldPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(oldPosition);
            }
            notifyItemChanged(selectedPosition);

            if (onLanguageClick != null) {
                onLanguageClick.onLanguageSelected(language);
            }
        });
    }

    @Override
    public int getItemCount() {
        return languages.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvNativeName;
        TextView tvEnglishName;
        RadioButton radioButton;

        VH(@NonNull View itemView) {
            super(itemView);
            tvNativeName = itemView.findViewById(R.id.tvNativeName);
            tvEnglishName = itemView.findViewById(R.id.tvEnglishName);
            radioButton = itemView.findViewById(R.id.radioButton);
        }
    }
}
