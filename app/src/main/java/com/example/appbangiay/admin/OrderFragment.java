package com.example.appbangiay.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.admin.adapter.OrderAdapter;
import com.example.appbangiay.model.Order;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrderFragment extends Fragment {

    private static final int PAGE_SIZE = 20;

    private RecyclerView recyclerView;
    private OrderAdapter adapter;
    private final List<Order> orders = new ArrayList<>();
    private FirebaseFirestore db;
    private String currentFilter = "all";
    private LinearLayoutManager layoutManager;
    private ProgressBar progressInitial, progressMore;
    private TextView tvEmpty;
    private DocumentSnapshot lastVisibleDoc;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private boolean useCreatedAtOrder = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_order, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = FirebaseFirestore.getInstance();

        recyclerView = view.findViewById(R.id.rv_orders);
        progressInitial = view.findViewById(R.id.progress_orders_loading);
        progressMore = view.findViewById(R.id.progress_orders_more);
        tvEmpty = view.findViewById(R.id.tv_orders_empty);

        layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        adapter = new OrderAdapter(orders, this::updateOrderStatus);
        recyclerView.setAdapter(adapter);

        setupChipFilter(view);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0 || isLoading || !hasMore) return;

                int totalItems = layoutManager.getItemCount();
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (totalItems > 0 && lastVisible >= totalItems - 4) {
                    loadOrders(false);
                }
            }
        });

        loadOrders(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadOrders(true);
    }

    private void setupChipFilter(View view) {
        ChipGroup chipGroup = view.findViewById(R.id.chip_group_filter);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip chip = view.findViewById(checkedIds.get(0));
            currentFilter = chip != null && chip.getTag() != null ? chip.getTag().toString() : "all";
            loadOrders(true);
        });
    }

    private Query buildOrderQuery() {
        Query query = db.collection("orders");
        if (!"all".equals(currentFilter)) {
            query = query.whereEqualTo("status", currentFilter);
        }
        if (useCreatedAtOrder) {
            return query.orderBy("createdAt", Query.Direction.DESCENDING);
        }
        return query.orderBy(FieldPath.documentId(), Query.Direction.DESCENDING);
    }

    private void loadOrders(boolean reset) {
        if (isLoading) return;
        if (reset) {
            lastVisibleDoc = null;
            hasMore = true;
        } else if (!hasMore) {
            return;
        }

        isLoading = true;
        showLoading(reset, true);

        Query query = buildOrderQuery().limit(PAGE_SIZE);
        if (!reset && lastVisibleDoc != null) {
            query = query.startAfter(lastVisibleDoc);
        }

        query.get()
                .addOnSuccessListener(snapshots -> {
                    if (reset) {
                        orders.clear();
                        adapter.notifyDataSetChanged();
                    }

                    List<DocumentSnapshot> docs = snapshots.getDocuments();
                    int startIndex = orders.size();
                    for (DocumentSnapshot doc : docs) {
                        Order order = doc.toObject(Order.class);
                        if (order != null) {
                            order.setId(doc.getId());
                            orders.add(order);
                        }
                    }

                    if (!docs.isEmpty()) {
                        lastVisibleDoc = docs.get(docs.size() - 1);
                    }
                    hasMore = docs.size() == PAGE_SIZE;

                    if (reset) {
                        adapter.notifyDataSetChanged();
                    } else if (!docs.isEmpty()) {
                        adapter.notifyItemRangeInserted(startIndex, docs.size());
                    }
                    updateEmptyState();
                })
                .addOnFailureListener(e -> {
                    String error = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
                    if (useCreatedAtOrder && error.contains("index")) {
                        useCreatedAtOrder = false;
                        isLoading = false;
                        showLoading(reset, false);
                        loadOrders(reset);
                        return;
                    }
                    Toast.makeText(getContext(), "Lỗi tải đơn hàng", Toast.LENGTH_SHORT).show();
                    updateEmptyState();
                })
                .addOnCompleteListener(task -> {
                    isLoading = false;
                    showLoading(reset, false);
                });
    }

    private void showLoading(boolean initial, boolean visible) {
        if (progressInitial != null) {
            progressInitial.setVisibility(initial && visible ? View.VISIBLE : View.GONE);
        }
        if (progressMore != null) {
            progressMore.setVisibility(!initial && visible ? View.VISIBLE : View.GONE);
        }
    }

    private void updateEmptyState() {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(!isLoading && orders.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void updateOrderStatus(Order order, String newStatus) {
        WriteBatch batch = db.batch();
        batch.update(db.collection("orders").document(order.getId()), "status", newStatus);
        if (order.getUserId() != null && !order.getUserId().isEmpty()) {
            batch.set(db.collection("users").document(order.getUserId())
                            .collection("orders").document(order.getId()),
                    java.util.Collections.singletonMap("status", newStatus),
                    SetOptions.merge());
        }

        batch.commit()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(getContext(), "Đã cập nhật trạng thái", Toast.LENGTH_SHORT).show();
                    loadOrders(true);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Cập nhật thất bại", Toast.LENGTH_SHORT).show());
    }
}
