package com.example.appbangiay.customer;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.customer.adapter.CartAdapter;
import com.example.appbangiay.model.CartItem;
import com.example.appbangiay.model.Product;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartFragment extends Fragment implements CartAdapter.OnCartChange {

    private CartAdapter adapter;
    private RecyclerView rvCart;
    private View layoutEmpty;
    private LinearLayout layoutBottom;
    private TextView tvTotal;
    private final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rvCart       = view.findViewById(R.id.rv_cart);
        layoutEmpty  = view.findViewById(R.id.layout_empty);
        layoutBottom = view.findViewById(R.id.layout_bottom);
        tvTotal      = view.findViewById(R.id.tv_total);

        List<CartItem> items = CartManager.getInstance().getItems();
        adapter = new CartAdapter(items, this);
        rvCart.setLayoutManager(new LinearLayoutManager(getContext()));
        rvCart.setAdapter(adapter);

        view.findViewById(R.id.btn_checkout).setOnClickListener(v -> {
            if (CartManager.getInstance().getItems().isEmpty()) return;
            startActivity(new Intent(getContext(), CheckoutActivity.class));
        });

        CartManager.getInstance().loadCart(() -> {
            if (adapter != null) adapter.notifyDataSetChanged();
            updateUI();
        });

        loadSuggestedProducts(view.findViewById(R.id.rv_suggested_cart));
    }

    @Override public void onResume() {
        super.onResume();
        CartManager.getInstance().loadCart(() -> {
            if (adapter != null) adapter.notifyDataSetChanged();
            updateUI();
        });
    }

    @Override public void onQuantityChanged(CartItem item) {
        CartManager.getInstance().updateQuantity(item.getProduct().getId(), item.getQuantity());
        updateUI();
    }

    @Override public void onItemRemoved(int position, CartItem item) {
        CartManager.getInstance().removeItem(item);
        updateUI();
    }

    private void updateUI() {
        boolean hasItems = !CartManager.getInstance().getItems().isEmpty();
        rvCart.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        layoutBottom.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        layoutEmpty.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        if (hasItems) tvTotal.setText(fmt.format(CartManager.getInstance().getTotal()) + " đ");
    }

    private void loadSuggestedProducts(RecyclerView rv) {
        if (rv == null || getContext() == null) return;
        List<Product> list = new ArrayList<>();
        rv.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rv.setAdapter(new RecyclerView.Adapter<SuggestVH>() {
            @NonNull @Override
            public SuggestVH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                return new SuggestVH(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_product_card, parent, false));
            }
            @Override
            public void onBindViewHolder(@NonNull SuggestVH h, int pos) {
                Product p = list.get(pos);
                h.tvName.setText(p.getName() != null ? p.getName() : "");
                if (p.getCategory() != null) h.tvCategory.setText(p.getCategory());
                if (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) {
                    try {
                        byte[] b = android.util.Base64.decode(p.getImageUrl(), android.util.Base64.DEFAULT);
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
                        Product p = d.toObject(Product.class);
                        if (p != null) { p.setId(d.getId()); list.add(p); }
                    }
                    if (rv.getAdapter() != null) rv.getAdapter().notifyDataSetChanged();
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
