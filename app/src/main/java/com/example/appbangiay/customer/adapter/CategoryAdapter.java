package com.example.appbangiay.customer.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

    public interface OnCategoryClick { void onClick(String category); }

    private final List<String> categories;
    private final OnCategoryClick listener;
    private int selectedIndex = 0;

    // Map category name → icon resource
    private static final Map<String, Integer> ICON_MAP = new HashMap<>();
    static {
        ICON_MAP.put("Tất cả",    R.drawable.ic_cat_all);
        ICON_MAP.put("Sneaker",   R.drawable.ic_cat_sneaker);
        ICON_MAP.put("Thể thao",  R.drawable.ic_cat_sport);
        ICON_MAP.put("Sandal",    R.drawable.ic_cat_sandal);
        ICON_MAP.put("Công sở",   R.drawable.ic_cat_office);
    }

    private static final int[] COLORS = {
            Color.parseColor("#3B82F6"),
            Color.parseColor("#EF4444"),
            Color.parseColor("#10B981"),
            Color.parseColor("#F59E0B"),
            Color.parseColor("#8B5CF6"),
            Color.parseColor("#EC4899"),
    };

    public CategoryAdapter(List<String> categories, OnCategoryClick listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        String cat = categories.get(position);
        h.tvName.setText(cat);

        // Set icon
        Integer iconRes = ICON_MAP.get(cat);
        h.imgIcon.setImageResource(iconRes != null ? iconRes : R.drawable.ic_cat_all);

        boolean isSelected = position == selectedIndex;
        int color = COLORS[position % COLORS.length];

        if (isSelected) {
            h.cardIcon.setCardBackgroundColor(color);
            h.imgIcon.setColorFilter(Color.WHITE);
            h.tvName.setTextColor(color);
            h.tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            h.cardIcon.setCardBackgroundColor(Color.parseColor("#F0F4FF"));
            h.imgIcon.setColorFilter(Color.parseColor("#666666"));
            h.tvName.setTextColor(Color.parseColor("#555555"));
            h.tvName.setTypeface(null, android.graphics.Typeface.NORMAL);
        }

        h.itemView.setOnClickListener(v -> {
            int old = selectedIndex;
            selectedIndex = h.getAdapterPosition();
            notifyItemChanged(old);
            notifyItemChanged(selectedIndex);
            listener.onClick(cat);
        });
    }

    @Override public int getItemCount() { return categories.size(); }

    static class VH extends RecyclerView.ViewHolder {
        CardView cardIcon;
        ImageView imgIcon;
        TextView tvName;

        VH(View v) {
            super(v);
            cardIcon = v.findViewById(R.id.card_category_icon);
            imgIcon  = v.findViewById(R.id.img_category_icon);
            tvName   = v.findViewById(R.id.tv_category_name);
        }
    }
}
