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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
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

public class FavoritesActivity extends AppCompatActivity {

    private final List<FavItem> items = new ArrayList<>();
    private FavAdapter adapter;
    private TextView tvEmpty;
    private final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_favorites);

        tvEmpty = findViewById(R.id.tv_empty_favorites);
        RecyclerView rv = findViewById(R.id.rv_favorites);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new FavAdapter();
        rv.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        loadFavorites();
    }

    @Override
    protected void onResume() {
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
                    adapter.notifyDataSetChanged();
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

            // Image
            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                try {
                    byte[] b = Base64.decode(item.imageUrl, Base64.DEFAULT);
                    h.imgProduct.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
                } catch (Exception ignored) {}
            }

            // Price
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

            // Click → go to product detail
            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(FavoritesActivity.this, ProductDetailActivity.class);
                intent.putExtra("product_id", item.productId);
                startActivity(intent);
            });

            // Hide add-to-cart button on favorites screen
            h.btnAddCart.setVisibility(View.GONE);
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView imgProduct, btnAddCart;
            TextView tvName, tvPrice, tvOriginal, tvCategory, tvBadge;

            VH(View v) {
                super(v);
                imgProduct = v.findViewById(R.id.img_product_card);
                tvName = v.findViewById(R.id.tv_card_name);
                tvPrice = v.findViewById(R.id.tv_card_price);
                tvOriginal = v.findViewById(R.id.tv_card_original_price);
                tvCategory = v.findViewById(R.id.tv_card_category);
                tvBadge = v.findViewById(R.id.tv_sale_badge);
                btnAddCart = v.findViewById(R.id.btn_add_cart);
            }
        }
    }
}
