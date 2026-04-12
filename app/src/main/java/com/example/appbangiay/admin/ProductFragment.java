package com.example.appbangiay.admin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
import com.example.appbangiay.admin.adapter.ProductAdapter;
import com.example.appbangiay.admin.product.AddEditProductActivity;
import com.example.appbangiay.model.Product;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductFragment extends Fragment {

    private static final int PAGE_SIZE = 20;

    private RecyclerView recyclerView;
    private ProductAdapter adapter;
    private final List<Product> productList = new ArrayList<>();
    private FirebaseFirestore db;
    private boolean isDeleting = false;
    private boolean isSyncingLegacyImages = false;
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
        return inflater.inflate(R.layout.fragment_product, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = FirebaseFirestore.getInstance();

        recyclerView = view.findViewById(R.id.rv_products);
        progressInitial = view.findViewById(R.id.progress_products_loading);
        progressMore = view.findViewById(R.id.progress_products_more);
        tvEmpty = view.findViewById(R.id.tv_products_empty);

        layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        adapter = new ProductAdapter(productList, this::openEditProduct, this::deleteProduct);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0 || isLoading || !hasMore) return;

                int totalItems = layoutManager.getItemCount();
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (totalItems > 0 && lastVisible >= totalItems - 4) {
                    loadProducts(false);
                }
            }
        });

        FloatingActionButton fab = view.findViewById(R.id.fab_add_product);
        fab.setOnClickListener(v -> openAddProduct());

        loadProducts(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isDeleting) loadProducts(true);
    }

    private Query buildProductQuery() {
        Query query = db.collection("products");
        if (useCreatedAtOrder) {
            return query.orderBy("createdAt", Query.Direction.DESCENDING);
        }
        return query.orderBy(FieldPath.documentId(), Query.Direction.ASCENDING);
    }

    private void loadProducts(boolean reset) {
        if (isLoading) return;
        if (reset) {
            lastVisibleDoc = null;
            hasMore = true;
        } else if (!hasMore) {
            return;
        }

        isLoading = true;
        showLoading(reset, true);

        Query query = buildProductQuery().limit(PAGE_SIZE);
        if (!reset && lastVisibleDoc != null) {
            query = query.startAfter(lastVisibleDoc);
        }

        query.get()
                .addOnSuccessListener(snapshots -> {
                    if (reset) {
                        productList.clear();
                        adapter.notifyDataSetChanged();
                    }

                    List<DocumentSnapshot> docs = snapshots.getDocuments();
                    syncLegacyProductImages(docs);

                    int startIndex = productList.size();
                    for (DocumentSnapshot doc : docs) {
                        Product product = doc.toObject(Product.class);
                        if (product != null) {
                            product.setId(doc.getId());
                            if ((product.getImageUrls() == null || product.getImageUrls().isEmpty())
                                    && product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                                List<String> migratedImages = new ArrayList<>();
                                migratedImages.add(product.getImageUrl());
                                product.setImageUrls(migratedImages);
                            }
                            productList.add(product);
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
                        loadProducts(reset);
                        return;
                    }
                    Toast.makeText(getContext(), "Lỗi tải sản phẩm", Toast.LENGTH_SHORT).show();
                    updateEmptyState();
                })
                .addOnCompleteListener(task -> {
                    isLoading = false;
                    showLoading(reset, false);
                });
    }

    private void syncLegacyProductImages(List<DocumentSnapshot> docs) {
        if (isSyncingLegacyImages || docs == null || docs.isEmpty()) return;

        WriteBatch batch = db.batch();
        int pendingUpdates = 0;

        for (DocumentSnapshot doc : docs) {
            String imageUrl = doc.getString("imageUrl");
            Object rawImageUrls = doc.get("imageUrls");

            boolean hasMainImage = imageUrl != null && !imageUrl.trim().isEmpty();
            boolean hasImageList = false;
            if (rawImageUrls instanceof List) {
                for (Object item : (List<?>) rawImageUrls) {
                    if (item instanceof String && !((String) item).trim().isEmpty()) {
                        hasImageList = true;
                        break;
                    }
                }
            }

            if (hasMainImage && !hasImageList) {
                List<String> migratedImages = new ArrayList<>();
                migratedImages.add(imageUrl);
                batch.update(doc.getReference(), "imageUrls", migratedImages);
                pendingUpdates++;
            }
        }

        if (pendingUpdates == 0) return;

        final int migratedCount = pendingUpdates;
        isSyncingLegacyImages = true;
        batch.commit()
                .addOnSuccessListener(unused ->
                        Log.d("ProductFragment", "Backfilled imageUrls for " + migratedCount + " products"))
                .addOnFailureListener(e -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Không thể đồng bộ ảnh sản phẩm cũ", Toast.LENGTH_SHORT).show();
                    }
                    Log.e("ProductFragment", "Failed to backfill imageUrls", e);
                })
                .addOnCompleteListener(task -> isSyncingLegacyImages = false);
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
            tvEmpty.setVisibility(!isLoading && productList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void openAddProduct() {
        startActivity(new Intent(getContext(), AddEditProductActivity.class));
    }

    private void openEditProduct(Product product) {
        Intent intent = new Intent(getContext(), AddEditProductActivity.class);
        intent.putExtra("product_id", product.getId());
        startActivity(intent);
    }

    private void deleteProduct(Product product) {
        if (product.getId() == null || product.getId().isEmpty()) {
            Toast.makeText(getContext(), "Lỗi: sản phẩm không có ID", Toast.LENGTH_SHORT).show();
            return;
        }

        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Xoá sản phẩm")
                .setMessage("Bạn có chắc muốn xoá \"" + product.getName() + "\" không?")
                .setPositiveButton("Xoá", (dialog, which) -> {
                    int pos = productList.indexOf(product);
                    if (pos >= 0) {
                        productList.remove(pos);
                        adapter.notifyItemRemoved(pos);
                    }

                    isDeleting = true;
                    db.collection("products").document(product.getId())
                            .delete()
                            .addOnSuccessListener(unused -> {
                                isDeleting = false;
                                Toast.makeText(getContext(), "Đã xoá sản phẩm", Toast.LENGTH_SHORT).show();
                                updateEmptyState();
                            })
                            .addOnFailureListener(e -> {
                                isDeleting = false;
                                Toast.makeText(getContext(), "Xoá thất bại, thử lại", Toast.LENGTH_SHORT).show();
                                loadProducts(true);
                            });
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }
}
