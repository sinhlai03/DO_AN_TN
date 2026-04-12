package com.example.appbangiay.customer;

import android.content.Intent;
import android.graphics.BitmapFactory;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MyReviewsActivity extends AppCompatActivity {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_PENDING = 1;
    private static final int TYPE_REVIEWED = 2;

    private final List<Object> rows = new ArrayList<>();
    private final List<PendingReviewItem> pendingItems = new ArrayList<>();
    private final List<ReviewedItem> reviewedItems = new ArrayList<>();
    private ReviewCenterAdapter adapter;
    private TextView tvEmpty;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_my_reviews);

        tvEmpty = findViewById(R.id.tv_empty);
        RecyclerView rv = findViewById(R.id.rv_reviews);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReviewCenterAdapter();
        rv.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReviewCenter();
    }

    private void loadReviewCenter() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        loadReviewedItems(user);
    }

    private void loadReviewedItems(FirebaseUser user) {
        db.collection("reviews")
                .whereEqualTo("userId", user.getUid())
                .get()
                .addOnSuccessListener(snap -> {
                    reviewedItems.clear();
                    Set<String> reviewedProductIds = new HashSet<>();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        ReviewedItem item = new ReviewedItem();
                        item.comment = doc.getString("comment");
                        item.imageBase64 = doc.getString("imageBase64");
                        item.productId = doc.getString("productId");
                        Long rating = doc.getLong("rating");
                        item.rating = rating != null ? rating.intValue() : 5;
                        item.timestamp = doc.getTimestamp("createdAt");
                        item.productName = "Sản phẩm";
                        if (item.timestamp != null) {
                            item.date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                    .format(item.timestamp.toDate());
                        }
                        if (item.productId != null && !item.productId.isEmpty()) {
                            reviewedProductIds.add(item.productId);
                        }
                        reviewedItems.add(item);
                    }

                    reviewedItems.sort((a, b) -> {
                        if (a.timestamp == null && b.timestamp == null) return 0;
                        if (a.timestamp == null) return 1;
                        if (b.timestamp == null) return -1;
                        return b.timestamp.compareTo(a.timestamp);
                    });

                    fetchReviewedProductInfo();
                    loadPendingItems(user, reviewedProductIds);
                })
                .addOnFailureListener(e -> {
                    reviewedItems.clear();
                    pendingItems.clear();
                    rebuildRows();
                });
    }

    @SuppressWarnings("unchecked")
    private void loadPendingItems(FirebaseUser user, Set<String> reviewedProductIds) {
        db.collection("orders")
                .whereEqualTo("userId", user.getUid())
                .whereEqualTo("status", "done")
                .get()
                .addOnSuccessListener(snap -> {
                    pendingItems.clear();
                    Set<String> seenProductIds = new HashSet<>(reviewedProductIds);

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Timestamp createdAt = doc.getTimestamp("createdAt");
                        List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get("items");
                        if (items == null) continue;

                        for (Map<String, Object> itemMap : items) {
                            String productId = getStringValue(itemMap.get("productId"));
                            if (productId.isEmpty() || seenProductIds.contains(productId)) continue;

                            PendingReviewItem item = new PendingReviewItem();
                            item.orderId = doc.getId();
                            item.productId = productId;
                            item.productName = getStringValue(itemMap.get("name"));
                            item.imageBase64 = getStringValue(itemMap.get("imageUrl"));
                            item.completedAt = createdAt;
                            item.completedDate = createdAt != null
                                    ? new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                    .format(createdAt.toDate())
                                    : "";

                            pendingItems.add(item);
                            seenProductIds.add(productId);
                        }
                    }

                    pendingItems.sort((a, b) -> {
                        if (a.completedAt == null && b.completedAt == null) return 0;
                        if (a.completedAt == null) return 1;
                        if (b.completedAt == null) return -1;
                        return b.completedAt.compareTo(a.completedAt);
                    });

                    rebuildRows();
                })
                .addOnFailureListener(e -> {
                    pendingItems.clear();
                    rebuildRows();
                });
    }

    private void fetchReviewedProductInfo() {
        for (int i = 0; i < reviewedItems.size(); i++) {
            final int index = i;
            ReviewedItem item = reviewedItems.get(i);
            if (item.productId == null || item.productId.isEmpty()) continue;

            db.collection("products").document(item.productId).get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists() || index >= reviewedItems.size()) return;
                        String name = doc.getString("name");
                        if (name != null && !name.isEmpty()) {
                            reviewedItems.get(index).productName = name;
                            rebuildRows();
                        }
                    });
        }
    }

    private void rebuildRows() {
        rows.clear();

        if (!pendingItems.isEmpty()) {
            rows.add(new SectionHeader("Chờ đánh giá", pendingItems.size()));
            rows.addAll(pendingItems);
        }

        if (!reviewedItems.isEmpty()) {
            rows.add(new SectionHeader("Đã đánh giá", reviewedItems.size()));
            rows.addAll(reviewedItems);
        }

        adapter.notifyDataSetChanged();
        tvEmpty.setText("Chưa có sản phẩm nào để đánh giá");
        tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String getStringValue(Object value) {
        return value != null ? value.toString() : "";
    }

    private void openReviewProduct(String productId) {
        if (productId == null || productId.isEmpty()) return;
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra("product_id", productId);
        intent.putExtra("open_review", true);
        startActivity(intent);
    }

    private void openProduct(String productId) {
        if (productId == null || productId.isEmpty()) return;
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra("product_id", productId);
        startActivity(intent);
    }

    static class SectionHeader {
        final String title;
        final int count;

        SectionHeader(String title, int count) {
            this.title = title;
            this.count = count;
        }
    }

    static class PendingReviewItem {
        String orderId;
        String productId;
        String productName;
        String imageBase64;
        String completedDate;
        Timestamp completedAt;
    }

    static class ReviewedItem {
        String productId;
        String productName;
        String comment;
        String imageBase64;
        String date;
        int rating;
        Timestamp timestamp;
    }

    class ReviewCenterAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            Object row = rows.get(position);
            if (row instanceof SectionHeader) return TYPE_HEADER;
            if (row instanceof PendingReviewItem) return TYPE_PENDING;
            return TYPE_REVIEWED;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderVH(inflater.inflate(R.layout.item_review_section_header, parent, false));
            }
            if (viewType == TYPE_PENDING) {
                return new PendingVH(inflater.inflate(R.layout.item_pending_review, parent, false));
            }
            return new ReviewedVH(inflater.inflate(R.layout.item_my_review, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);

            if (holder instanceof HeaderVH) {
                SectionHeader header = (SectionHeader) row;
                ((HeaderVH) holder).tvTitle.setText(header.title + " (" + header.count + ")");
                return;
            }

            if (holder instanceof PendingVH) {
                PendingReviewItem item = (PendingReviewItem) row;
                PendingVH h = (PendingVH) holder;
                h.tvProductName.setText(item.productName != null && !item.productName.isEmpty()
                        ? item.productName : "Sản phẩm");
                String shortOrderId = item.orderId != null && item.orderId.length() >= 8
                        ? item.orderId.substring(0, 8).toUpperCase()
                        : (item.orderId != null ? item.orderId.toUpperCase() : "");
                h.tvMeta.setText(shortOrderId.isEmpty()
                        ? "Đơn hàng hoàn thành"
                        : "Đơn #" + shortOrderId + (item.completedDate.isEmpty() ? "" : " - " + item.completedDate));
                bindImage(h.imgProduct, item.imageBase64);
                h.btnReviewNow.setOnClickListener(v -> openReviewProduct(item.productId));
                h.itemView.setOnClickListener(v -> openReviewProduct(item.productId));
                return;
            }

            ReviewedItem item = (ReviewedItem) row;
            ReviewedVH h = (ReviewedVH) holder;
            h.tvProductName.setText(item.productName);
            h.tvComment.setText(item.comment != null ? item.comment : "");
            h.tvComment.setVisibility(item.comment != null && !item.comment.isEmpty() ? View.VISIBLE : View.GONE);
            h.tvDate.setText(item.date != null ? item.date : "");

            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < item.rating; i++) stars.append("★");
            for (int i = item.rating; i < 5; i++) stars.append("☆");
            h.tvStars.setText(stars.toString());

            if (item.imageBase64 != null && !item.imageBase64.isEmpty()) {
                try {
                    byte[] b = Base64.decode(item.imageBase64, Base64.DEFAULT);
                    h.imgPhoto.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
                    h.imgPhoto.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    h.imgPhoto.setVisibility(View.GONE);
                }
            } else {
                h.imgPhoto.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> openProduct(item.productId));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        private void bindImage(ImageView imageView, String base64Image) {
            if (base64Image == null || base64Image.isEmpty()) {
                imageView.setImageDrawable(null);
                return;
            }
            try {
                byte[] b = Base64.decode(base64Image, Base64.DEFAULT);
                imageView.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
            } catch (Exception e) {
                imageView.setImageDrawable(null);
            }
        }

        class HeaderVH extends RecyclerView.ViewHolder {
            final TextView tvTitle;

            HeaderVH(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tv_section_title);
            }
        }

        class PendingVH extends RecyclerView.ViewHolder {
            final ImageView imgProduct;
            final TextView tvProductName;
            final TextView tvMeta;
            final TextView btnReviewNow;

            PendingVH(View itemView) {
                super(itemView);
                imgProduct = itemView.findViewById(R.id.img_pending_review_product);
                tvProductName = itemView.findViewById(R.id.tv_pending_review_product_name);
                tvMeta = itemView.findViewById(R.id.tv_pending_review_meta);
                btnReviewNow = itemView.findViewById(R.id.btn_review_now);
            }
        }

        class ReviewedVH extends RecyclerView.ViewHolder {
            final TextView tvProductName;
            final TextView tvStars;
            final TextView tvComment;
            final TextView tvDate;
            final ImageView imgPhoto;

            ReviewedVH(View itemView) {
                super(itemView);
                tvProductName = itemView.findViewById(R.id.tv_review_product_name);
                tvStars = itemView.findViewById(R.id.tv_review_stars);
                tvComment = itemView.findViewById(R.id.tv_review_comment);
                tvDate = itemView.findViewById(R.id.tv_review_date);
                imgPhoto = itemView.findViewById(R.id.img_review_photo);
            }
        }
    }
}
