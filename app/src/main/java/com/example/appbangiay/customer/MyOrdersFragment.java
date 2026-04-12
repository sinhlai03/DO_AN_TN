package com.example.appbangiay.customer;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.model.Order;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MyOrdersFragment extends Fragment {

    private final List<Order> allOrders = new ArrayList<>();
    private final List<Order> filteredOrders = new ArrayList<>();
    private OrdersAdapter adapter;
    private TextView tvEmpty;
    private String currentFilter = "all";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_my_orders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvEmpty = view.findViewById(R.id.tv_empty_orders);
        RecyclerView rv = view.findViewById(R.id.rv_my_orders);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new OrdersAdapter();
        rv.setAdapter(adapter);
        setupChipFilter(view);
        loadOrders();
        View label = view.findViewById(R.id.tv_suggested_label);
        RecyclerView rvSug = view.findViewById(R.id.rv_suggested_orders);
        loadSuggestedProducts(rvSug, label);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadOrders();
    }

    private void loadOrders() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("orders")
                .whereEqualTo("userId", user.getUid())
                .get()
                .addOnSuccessListener(snap -> {
                    allOrders.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        try {
                            Order o = doc.toObject(Order.class);
                            if (o != null) {
                                o.setId(doc.getId());
                                allOrders.add(o);
                            }
                        } catch (Exception ignored) {}
                    }
                    Collections.sort(allOrders, (a, b) -> {
                        if (a.getCreatedAtDate() == null && b.getCreatedAtDate() == null) return 0;
                        if (a.getCreatedAtDate() == null) return 1;
                        if (b.getCreatedAtDate() == null) return -1;
                        return b.getCreatedAtDate().compareTo(a.getCreatedAtDate());
                    });
                    applyFilter();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Lỗi tải đơn hàng", Toast.LENGTH_SHORT).show());
    }

    private void setupChipFilter(View view) {
        ChipGroup chipGroup = view.findViewById(R.id.chip_group_my_orders_filter);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip chip = view.findViewById(checkedIds.get(0));
            if (chip == null) return;
            Object tag = chip.getTag();
            currentFilter = tag != null ? tag.toString() : "all";
            applyFilter();
        });
    }

    private void applyFilter() {
        filteredOrders.clear();
        if ("all".equals(currentFilter)) {
            filteredOrders.addAll(allOrders);
        } else {
            for (Order order : allOrders) {
                if (currentFilter.equals(order.getStatus())) {
                    filteredOrders.add(order);
                }
            }
        }

        if (adapter != null) adapter.notifyDataSetChanged();
        if (tvEmpty != null) {
            tvEmpty.setText(allOrders.isEmpty()
                    ? "Bạn chưa có đơn hàng nào"
                    : "Không có đơn hàng ở trạng thái này");
            tvEmpty.setVisibility(filteredOrders.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.VH> {
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        private final String[] STATUS_VALUES = {"pending", "confirmed", "shipping", "done", "cancelled"};
        private final String[] STATUS_LABELS = {"Chờ xác nhận", "Đã xác nhận", "Đang giao", "Hoàn thành", "Đã huỷ"};

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_my_order, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Order o = filteredOrders.get(pos);
            String id = o.getId() != null ? o.getId() : "";
            h.tvId.setText("#" + (id.length() >= 8 ? id.substring(0, 8).toUpperCase() : id.toUpperCase()));
            h.tvAmount.setText(String.format("%,.0f đ", o.getTotalAmount()));
            h.tvStatus.setText(getLabel(o.getStatus()));
            h.tvStatus.setTextColor(getStatusColor(o.getStatus()));
            if (o.getCreatedAtDate() != null) h.tvDate.setText(sdf.format(o.getCreatedAtDate()));

            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), CustomerOrderDetailActivity.class);
                intent.putExtra("orderId", o.getId());
                startActivity(intent);
            });
        }

        @Override public int getItemCount() { return filteredOrders.size(); }

        private String getLabel(String s) {
            if (s == null) return "N/A";
            for (int i = 0; i < STATUS_VALUES.length; i++) {
                if (STATUS_VALUES[i].equals(s)) return STATUS_LABELS[i];
            }
            return s;
        }

        private int getStatusColor(String s) {
            if (s == null) return 0xFF999999;
            switch (s) {
                case "pending":   return 0xFFFF9800;
                case "confirmed": return 0xFF2196F3;
                case "shipping":  return 0xFF4CAF50;
                case "done":      return 0xFF4CAF50;
                case "cancelled": return 0xFFF44336;
                default:          return 0xFF999999;
            }
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvId, tvDate, tvAmount, tvStatus;
            VH(View v) {
                super(v);
                tvId     = v.findViewById(R.id.tv_order_id);
                tvDate   = v.findViewById(R.id.tv_order_date);
                tvAmount = v.findViewById(R.id.tv_order_amount);
                tvStatus = v.findViewById(R.id.tv_order_status);
            }
        }
    }

    private void loadSuggestedProducts(RecyclerView rv, View label) {
        if (rv == null || getContext() == null) return;
        final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
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
