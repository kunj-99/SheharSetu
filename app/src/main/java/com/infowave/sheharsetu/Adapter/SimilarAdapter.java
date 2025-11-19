package com.infowave.sheharsetu.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.infowave.sheharsetu.R;

public class SimilarAdapter extends RecyclerView.Adapter<SimilarAdapter.VH> {

    private final String[] titles;
    private final String[] prices;
    private final int[] images;

    public SimilarAdapter(String[] titles, String[] prices, int[] images) {
        this.titles = titles;
        this.prices = prices;
        this.images = images;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_similar, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        h.title.setText(titles[position]);
        h.price.setText(prices[position]);
        h.image.setImageResource(images[position]);
        h.itemView.setOnClickListener(v ->
                Toast.makeText(v.getContext(), "Open: " + titles[position], Toast.LENGTH_SHORT).show());
    }

    @Override public int getItemCount() {
        return Math.min(titles.length, Math.min(prices.length, images.length));
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView image; final TextView title; final TextView price;
        VH(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.simImage);
            title = itemView.findViewById(R.id.simTitle);
            price = itemView.findViewById(R.id.simPrice);
        }
    }
}
