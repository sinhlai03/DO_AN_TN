package com.example.appbangiay.customer;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FavoritesFragment extends Fragment {

    private final List<FavItem> items = new ArrayList<>();
    private FavAdapter adapter;
    private TextView tvEmpty;
    private final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvEmpty = view.findViewById(R.id.tv_empty_favorites);
        RecyclerView rv = view.findViewById(R.id.rv_favorites);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new FavAdapter();
        rv.setAdapter(adapter);
        loadFavorites();
        View label = view.findViewById(R.id.tv_suggested_label);
        RecyclerView rvSug = view.findViewById(R.id.rv_suggested_favorites);
        loadSuggestedProducts(rvSug, label);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFavorites();
    }

    private void loadFavorites() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("favorites")
                .get()
                .addOnSuccessListener(snap -> {
                    items.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        FavItem item = new FavItem();
                        item.productId = doc.getId();
                        item.name = doc.getString("name");
                        Double price = doc.getDouble("price");
                        item.price = price != null ? price : 0;
                        item.imageUrl = doc.getString("imageUrl");
                        item.category = doc.getString("category");
                        Long disc = doc.getLong("discountPercent");
                        item.discountPercent = disc != null ? disc.intValue() : 0;
                        items.add(item);
                    }
                    if (adapter != null) adapter.notifyDataSetChanged();
                    if (tvEmpty != null)
                        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    static class FavItem {
        String productId, name, imageUrl, category;
        double price;
        int discountPercent;
    }

    class FavAdapter extends RecyclerView.Adapter<FavAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_product_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            FavItem item = items.get(pos);
            h.tvName.setText(item.name);
            if (item.category != null) h.tvCategory.setText(item.category);

            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                try {
                    byte[] b = Base64.decode(item.imageUrl, Base64.DEFAULT);
                    h.imgProduct.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
                } catch (Exception ignored) {}
            }

            if (item.discountPercent > 0) {
                double salePrice = item.price * (1 - item.discountPercent / 100.0);
                h.tvPrice.setText(fmt.format(salePrice) + " đ");
                h.tvOriginal.setVisibility(View.VISIBLE);
                h.tvOriginal.setText(fmt.format(item.price) + " đ");
                h.tvOriginal.setPaintFlags(h.tvOriginal.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                h.tvBadge.setVisibility(View.VISIBLE);
                h.tvBadge.setText("-" + item.discountPercent + "%");
            } else {
                h.tvPrice.setText(fmt.format(item.price) + " đ");
                h.tvOriginal.setVisibility(View.GONE);
                h.tvBadge.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
                intent.putExtra("product_id", item.productId);
                startActivity(intent);
            });
            h.btnAddCart.setVisibility(View.GONE);
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView imgProduct, btnAddCart;
            TextView tvName, tvPrice, tvOriginal, tvCategory, tvBadge;
            VH(View v) {
                super(v);
                imgProduct = v.findViewById(R.id.img_product_card);
                tvName     = v.findViewById(R.id.tv_card_name);
                tvPrice    = v.findViewById(R.id.tv_card_price);
                tvOriginal = v.findViewById(R.id.tv_card_original_price);
                tvCategory = v.findViewById(R.id.tv_card_category);
                tvBadge    = v.findViewById(R.id.tv_sale_badge);
                btnAddCart = v.findViewById(R.id.btn_add_cart);
            }
        }
    }

    private void loadSuggestedProducts(RecyclerView rv, View label) {
        if (rv == null || getContext() == null) return;
        List<com.example.appbangiay.model.Product> list = new ArrayList<>();
        rv.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rv.setAdapter(new RecyclerView.Adapter<SuggestVH>() {
            @NonNull @Override
            public SuggestVH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                return new SuggestVH(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_product_card, parent, false));
            }
            @Override
            public void onBindViewHolder(@NonNull SuggestVH h, int pos) {
                com.example.appbangiay.model.Product p = list.get(pos);
                h.tvName.setText(p.getName() != null ? p.getName() : "");
                if (p.getCategory() != null) h.tvCategory.setText(p.getCategory());
                if (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) {
                    try {
                        byte[] b = Base64.decode(p.getImageUrl(), Base64.DEFAULT);
                        h.img.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
                    } catch (Exception ignored) {}
                }
                if (p.getDiscountPercent() > 0) {
                    double sale = p.getPrice() * (1 - p.getDiscountPercent() / 100.0);
                    h.tvPrice.setText(fmt.format(sale) + " đ");
                    h.tvOriginal.setVisibility(View.VISIBLE);
                    h.tvOriginal.setText(fmt.format(p.getPrice()) + " đ");
                    h.tvOriginal.setPaintFlags(h.tvOriginal.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    h.tvBadge.setVisibility(View.VISIBLE);
                    h.tvBadge.setText("-" + p.getDiscountPercent() + "%");
                } else {
                    h.tvPrice.setText(fmt.format(p.getPrice()) + " đ");
                    h.tvOriginal.setVisibility(View.GONE);
                    h.tvBadge.setVisibility(View.GONE);
                }
                h.btnAddCart.setVisibility(View.GONE);
                h.itemView.setOnClickListener(v -> {
                    if (getContext() == null) return;
                    Intent intent = new Intent(getContext(), ProductDetailActivity.class);
                    intent.putExtra("product_id", p.getId());
                    startActivity(intent);
                });
            }
            @Override public int getItemCount() { return list.size(); }
        });
        FirebaseFirestore.getInstance().collection("products").limit(6).get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        com.example.appbangiay.model.Product p = d.toObject(com.example.appbangiay.model.Product.class);
                        if (p != null) { p.setId(d.getId()); list.add(p); }
                    }
                    if (!list.isEmpty()) {
                        if (label != null) label.setVisibility(View.VISIBLE);
                        if (rv.getAdapter() != null) rv.getAdapter().notifyDataSetChanged();
                    }
                });
    }

    static class SuggestVH extends RecyclerView.ViewHolder {
        ImageView img, btnAddCart;
        TextView tvName, tvPrice, tvOriginal, tvCategory, tvBadge;
        SuggestVH(View v) {
            super(v);
            img        = v.findViewById(R.id.img_product_card);
            tvName     = v.findViewById(R.id.tv_card_name);
            tvPrice    = v.findViewById(R.id.tv_card_price);
            tvOriginal = v.findViewById(R.id.tv_card_original_price);
            tvCategory = v.findViewById(R.id.tv_card_category);
            tvBadge    = v.findViewById(R.id.tv_sale_badge);
            btnAddCart = v.findViewById(R.id.btn_add_cart);
        }
    }
}
