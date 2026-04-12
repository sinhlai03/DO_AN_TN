package com.example.appbangiay.customer;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MyOrdersActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private final List<Order> allOrders = new ArrayList<>();
    private final List<Order> filteredOrders = new ArrayList<>();
    private MyOrderAdapter adapter;
    private String currentFilter = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_my_orders);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.rv_my_orders);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MyOrderAdapter();
        recyclerView.setAdapter(adapter);
        setupChipFilter();

        loadOrders();
    }

    @Override
    protected void onResume() {
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
                        Toast.makeText(this, "Lỗi tải đơn hàng", Toast.LENGTH_SHORT).show());
    }

    private void setupChipFilter() {
        ChipGroup chipGroup = findViewById(R.id.chip_group_my_orders_filter);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip chip = findViewById(checkedIds.get(0));
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

        adapter.notifyDataSetChanged();
        TextView tvEmpty = findViewById(R.id.tv_empty);
        tvEmpty.setText(allOrders.isEmpty()
                ? "Bạn chưa có đơn hàng nào"
                : "Không có đơn hàng ở trạng thái này");
        tvEmpty.setVisibility(filteredOrders.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ─── Inner Adapter ───
    class MyOrderAdapter extends RecyclerView.Adapter<MyOrderAdapter.VH> {
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
            h.tvStatus.setTextColor(getColor(o.getStatus()));

            if (o.getCreatedAtDate() != null) {
                h.tvDate.setText(sdf.format(o.getCreatedAtDate()));
            }

            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MyOrdersActivity.this, CustomerOrderDetailActivity.class);
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

        private int getColor(String s) {
            if (s == null) return 0xFF999999;
            switch (s) {
                case "pending": return 0xFFFF9800;
                case "confirmed": return 0xFF2196F3;
                case "shipping": return 0xFF4CAF50;
                case "done": return 0xFF4CAF50;
                case "cancelled": return 0xFFF44336;
                default: return 0xFF999999;
            }
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvId, tvDate, tvAmount, tvStatus;
            VH(View v) {
                super(v);
                tvId = v.findViewById(R.id.tv_order_id);
                tvDate = v.findViewById(R.id.tv_order_date);
                tvAmount = v.findViewById(R.id.tv_order_amount);
                tvStatus = v.findViewById(R.id.tv_order_status);
            }
        }
    }
}
