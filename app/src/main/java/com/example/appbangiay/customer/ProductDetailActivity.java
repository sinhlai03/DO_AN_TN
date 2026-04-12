package com.example.appbangiay.customer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import android.view.LayoutInflater;

import com.example.appbangiay.R;

import com.example.appbangiay.model.Product;
import com.example.appbangiay.model.Review;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductDetailActivity extends AppCompatActivity {

    private int quantity = 1;
    private int currentAvailableStock = 0;
    private Product product;
    private String productId;
    private String selectedSize = null; // selected size by customer
    private final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    // Reviews
    private LinearLayout llReviews;
    private int selectedRating = 5;
    private String reviewImageBase64 = "";
    private boolean canReview = false;
    private boolean openReviewWhenEligible = false;

    // Favorites
    private boolean isFavorite = false;
    private ImageView btnFavorite;

    private final ActivityResultLauncher<Intent> pickReviewImage =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            reviewImageBase64 = uriToBase64(uri);
                            ImageView preview = findViewById(R.id.img_review_preview);
                            byte[] b = Base64.decode(reviewImageBase64, Base64.DEFAULT);
                            preview.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
                            preview.setVisibility(View.VISIBLE);
                        } catch (Exception e) {
                            Toast.makeText(this, "Lỗi chọn ảnh", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        productId = getIntent().getStringExtra("product_id");
        if (productId == null) { finish(); return; }
        openReviewWhenEligible = getIntent().getBooleanExtra("open_review", false);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Reviews container
        llReviews = findViewById(R.id.ll_reviews);
        setReviewEligibility(false);

        // Star selector
        setupStarSelector();

        // Pick image
        findViewById(R.id.btn_pick_review_image).setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_PICK);
            pick.setType("image/*");
            pickReviewImage.launch(pick);
        });

        // Submit review
        findViewById(R.id.btn_submit_review).setOnClickListener(v -> submitReview());

        // Favorite button
        btnFavorite = findViewById(R.id.btn_favorite);
        btnFavorite.setOnClickListener(v -> toggleFavorite());
        checkFavoriteStatus();

        loadProduct(productId);
        loadReviews();
    }

    private void setupStarSelector() {
        LinearLayout llStars = findViewById(R.id.ll_rate_stars);
        llStars.removeAllViews();
        for (int i = 1; i <= 5; i++) {
            final int star = i;
            TextView tv = new TextView(this);
            tv.setText("★");
            tv.setTextSize(28);
            tv.setClickable(true);
            tv.setFocusable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(4, 0, 4, 0);
            tv.setLayoutParams(lp);
            tv.setMinWidth(48);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTextColor(i <= selectedRating ? 0xFFF59E0B : 0xFFDDDDDD);
            tv.setOnClickListener(v -> {
                selectedRating = star;
                updateStarSelector();
            });
            llStars.addView(tv);
        }
    }

    private void updateStarSelector() {
        LinearLayout llStars = findViewById(R.id.ll_rate_stars);
        for (int i = 0; i < llStars.getChildCount(); i++) {
            View child = llStars.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor((i + 1) <= selectedRating ?
                        0xFFF59E0B : 0xFFDDDDDD);
            }
        }
    }

    private void loadProduct(String productId) {
        FirebaseFirestore.getInstance().collection("products").document(productId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { finish(); return; }
                    product = doc.toObject(Product.class);
                    if (product == null) { finish(); return; }
                    product.setId(doc.getId());
                    bindData();
                    checkReviewEligibility();
                    saveViewHistory();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi tải sản phẩm", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void bindData() {
        bindProductImages();

        // Category
        ((TextView) findViewById(R.id.tv_detail_category))
                .setText(product.getCategory() != null ? product.getCategory() : "");

        // Name
        ((TextView) findViewById(R.id.tv_detail_name)).setText(product.getName());

        // Price
        TextView tvPrice = findViewById(R.id.tv_detail_price);
        TextView tvOriginal = findViewById(R.id.tv_detail_original_price);
        TextView tvSaleBadge = findViewById(R.id.tv_detail_sale_badge);

        if (product.getDiscountPercent() > 0) {
            tvPrice.setText(fmt.format(product.getSalePrice()) + " đ");
            tvOriginal.setVisibility(View.VISIBLE);
            tvOriginal.setText(fmt.format(product.getPrice()) + " đ");
            tvOriginal.setPaintFlags(tvOriginal.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tvSaleBadge.setVisibility(View.VISIBLE);
            tvSaleBadge.setText("-" + product.getDiscountPercent() + "%");
        } else {
            tvPrice.setText(fmt.format(product.getPrice()) + " đ");
        }

        currentAvailableStock = product.getStock();
        updateStockDisplay();

        // Sizes
        List<String> sizes = product.getSizes();
        LinearLayout layoutSizeSection = findViewById(R.id.layout_size_section);
        ChipGroup chipGroupSize = findViewById(R.id.chip_group_size_select);
        TextView tvSelectedSize = findViewById(R.id.tv_selected_size);

        if (sizes != null && !sizes.isEmpty()) {
            layoutSizeSection.setVisibility(View.VISIBLE);
            chipGroupSize.removeAllViews();
            for (String size : sizes) {
                Chip chip = new Chip(this);
                chip.setText(size);
                chip.setCheckable(true);
                chip.setTextSize(13f);
                chip.setChipBackgroundColorResource(R.color.white);
                chip.setTextColor(getColor(R.color.text_primary));
                chip.setCheckedIconVisible(false);
                chip.setRippleColorResource(com.google.android.material.R.color.m3_chip_ripple_color);
                int sizeStock = product.getStockForSize(size);
                boolean available = sizeStock > 0;
                chip.setEnabled(available);
                chip.setAlpha(available ? 1f : 0.45f);
                if (!available) {
                    chip.setText(size + " hết");
                }
                chipGroupSize.addView(chip);
            }
            chipGroupSize.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) {
                    selectedSize = null;
                    tvSelectedSize.setText("Chưa chọn");
                    tvSelectedSize.setTextColor(getColor(android.R.color.darker_gray));
                    currentAvailableStock = product.getStock();
                } else {
                    Chip checked = group.findViewById(checkedIds.get(0));
                    selectedSize = checked != null ? checked.getText().toString().replace(" hết", "") : null;
                    tvSelectedSize.setText("Size " + selectedSize);
                    tvSelectedSize.setTextColor(getColor(R.color.blue_primary));
                    currentAvailableStock = product.getStockForSize(selectedSize);
                }
                adjustQuantityForCurrentStock();
                updateStockDisplay();
            });
        } else {
            layoutSizeSection.setVisibility(View.GONE);
        }

        // Description
        TextView tvDesc = findViewById(R.id.tv_detail_description);
        String desc = product.getDescription();
        tvDesc.setText(desc != null && !desc.isEmpty() ? desc : "Chưa có mô tả.");

        // Quantity
        TextView tvQty = findViewById(R.id.tv_detail_qty);
        tvQty.setText(String.valueOf(quantity));

        findViewById(R.id.btn_qty_minus).setOnClickListener(v -> {
            if (quantity > 1) { quantity--; tvQty.setText(String.valueOf(quantity)); }
        });
        findViewById(R.id.btn_qty_plus).setOnClickListener(v -> {
            if (quantity < getEffectiveAvailableStock()) { quantity++; tvQty.setText(String.valueOf(quantity)); }
            else Toast.makeText(this, "Đã đạt giới hạn kho", Toast.LENGTH_SHORT).show();
        });

        // Add to cart
        findViewById(R.id.btn_add_to_cart).setOnClickListener(v -> {
            List<String> productSizes = product.getSizes();
            if (productSizes != null && !productSizes.isEmpty() && selectedSize == null) {
                Toast.makeText(this, "Vui lòng chọn size!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (quantity > getEffectiveAvailableStock()) {
                Toast.makeText(this, "Size đã chọn không còn đủ số lượng", Toast.LENGTH_SHORT).show();
                return;
            }
            for (int i = 0; i < quantity; i++) CartManager.getInstance().addProduct(product, selectedSize);
            com.google.android.material.snackbar.Snackbar
                    .make(v, "Đã thêm " + quantity + " sản phẩm" +
                            (selectedSize != null ? " (Size " + selectedSize + ")" : "") + " vào giỏ",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(getColor(R.color.blue_primary))
                    .setTextColor(getColor(R.color.white))
                    .show();
        });

        // Order now — add then go directly to checkout
        findViewById(R.id.btn_order_now).setOnClickListener(v -> {
            List<String> productSizes = product.getSizes();
            if (productSizes != null && !productSizes.isEmpty() && selectedSize == null) {
                Toast.makeText(this, "Vui lòng chọn size!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (quantity > getEffectiveAvailableStock()) {
                Toast.makeText(this, "Size đã chọn không còn đủ số lượng", Toast.LENGTH_SHORT).show();
                return;
            }
            CartManager.getInstance().clear();
            for (int i = 0; i < quantity; i++) CartManager.getInstance().addProduct(product, selectedSize);
            startActivity(new Intent(this, CheckoutActivity.class));
        });
    }

    private int getEffectiveAvailableStock() {
        if (selectedSize != null) {
            return currentAvailableStock;
        }
        return product != null ? product.getStock() : 0;
    }

    private void updateStockDisplay() {
        TextView tvStock = findViewById(R.id.tv_detail_stock);
        if (tvStock == null || product == null) return;
        int displayStock = getEffectiveAvailableStock();
        tvStock.setText(String.valueOf(Math.max(displayStock, 0)));
    }

    private void adjustQuantityForCurrentStock() {
        int available = getEffectiveAvailableStock();
        if (available <= 0) {
            quantity = 1;
        } else if (quantity > available) {
            quantity = available;
        }
        TextView tvQty = findViewById(R.id.tv_detail_qty);
        if (tvQty != null) tvQty.setText(String.valueOf(quantity));
    }

    // ─── Reviews ───

    private void loadReviews() {
        java.text.SimpleDateFormat dateFmt = new java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        FirebaseFirestore.getInstance().collection("reviews")
                .whereEqualTo("productId", productId)
                .get()
                .addOnSuccessListener(snap -> {
                    if (isFinishing() || isDestroyed()) return;
                    llReviews.removeAllViews();
                    double totalRating = 0;
                    List<Review> reviewList = new ArrayList<>();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Review r = doc.toObject(Review.class);
                        if (r != null) {
                            r.setId(doc.getId());
                            reviewList.add(r);
                            totalRating += r.getRating();
                        }
                    }

                    // Sort by createdAt descending
                    reviewList.sort((a, b) -> {
                        if (b.getCreatedAt() == null) return -1;
                        if (a.getCreatedAt() == null) return 1;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    });

                    // Show first 5 reviews, add "Xem thêm" if more exist
                    int limit = Math.min(5, reviewList.size());
                    for (int i = 0; i < limit; i++) {
                        try {
                            View row = LayoutInflater.from(this)
                                    .inflate(R.layout.item_review, llReviews, false);
                            bindReviewView(row, reviewList.get(i), dateFmt);
                            llReviews.addView(row);
                        } catch (Exception ignored) {}
                    }

                    // "Xem thêm" button if there are more reviews
                    if (reviewList.size() > 5) {
                        final int[] shown = {5};
                        TextView btnMore = new TextView(this);
                        btnMore.setText("Xem thêm đánh giá (" + (reviewList.size() - 5) + ")");
                        btnMore.setTextColor(0xFF3B82F6);
                        btnMore.setTextSize(14);
                        btnMore.setPadding(0, 24, 0, 12);
                        btnMore.setGravity(android.view.Gravity.CENTER);
                        btnMore.setClickable(true);
                        btnMore.setFocusable(true);
                        llReviews.addView(btnMore);

                        btnMore.setOnClickListener(v -> {
                            llReviews.removeView(btnMore);
                            int next = Math.min(shown[0] + 5, reviewList.size());
                            for (int i = shown[0]; i < next; i++) {
                                try {
                                    View row = LayoutInflater.from(this)
                                            .inflate(R.layout.item_review, llReviews, false);
                                    bindReviewView(row, reviewList.get(i), dateFmt);
                                    llReviews.addView(row);
                                } catch (Exception ignored) {}
                            }
                            shown[0] = next;
                            if (next < reviewList.size()) {
                                btnMore.setText("Xem thêm đánh giá (" + (reviewList.size() - next) + ")");
                                llReviews.addView(btnMore);
                            }
                        });
                    }

                    TextView tvAvg = findViewById(R.id.tv_avg_rating);
                    TextView tvCount = findViewById(R.id.tv_review_count);

                    if (!reviewList.isEmpty()) {
                        double avg = totalRating / reviewList.size();
                        tvAvg.setText(String.format(Locale.US, "★ %.1f/5", avg));
                        tvCount.setText(reviewList.size() + " đánh giá");
                    } else {
                        tvAvg.setText("");
                        tvCount.setText("Chưa có đánh giá");
                    }
                })
                .addOnFailureListener(e -> {
                    if (isFinishing() || isDestroyed()) return;
                    TextView tvCount = findViewById(R.id.tv_review_count);
                    if (tvCount != null) tvCount.setText("Chưa có đánh giá");
                });
    }

    private void bindReviewView(View row, Review r, java.text.SimpleDateFormat dateFmt) {
        // Avatar
        TextView tvAvatar = row.findViewById(R.id.tv_review_avatar);
        String name = r.getUserName();
        if (name == null || name.isEmpty()) name = "?";
        tvAvatar.setText(name.substring(0, 1).toUpperCase());

        // Name
        ((TextView) row.findViewById(R.id.tv_review_name)).setText(name);

        // Stars
        LinearLayout llStars = row.findViewById(R.id.ll_review_stars);
        llStars.removeAllViews();
        for (int i = 1; i <= 5; i++) {
            TextView star = new TextView(this);
            star.setText("★");
            star.setTextSize(12);
            star.setTextColor(i <= r.getRating() ? 0xFFF59E0B : 0xFFDDDDDD);
            llStars.addView(star);
        }

        // Time
        TextView tvTime = row.findViewById(R.id.tv_review_time);
        if (r.getCreatedAt() != null) {
            try { tvTime.setText(dateFmt.format(r.getCreatedAt().toDate())); }
            catch (Exception e) { tvTime.setText(""); }
        }

        // Comment
        TextView tvComment = row.findViewById(R.id.tv_review_comment);
        if (r.getComment() != null && !r.getComment().isEmpty()) {
            tvComment.setVisibility(View.VISIBLE);
            tvComment.setText(r.getComment());
        } else {
            tvComment.setVisibility(View.GONE);
        }

        // Image
        ImageView imgPhoto = row.findViewById(R.id.img_review_photo);
        if (r.getImageBase64() != null && !r.getImageBase64().isEmpty()) {
            try {
                byte[] b = Base64.decode(r.getImageBase64(), Base64.DEFAULT);
                imgPhoto.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
                imgPhoto.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                imgPhoto.setVisibility(View.GONE);
            }
        } else {
            imgPhoto.setVisibility(View.GONE);
        }
    }

    private void submitReview() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(this, "Vui lòng đăng nhập", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!canReview) {
                Toast.makeText(this, "Chỉ có thể đánh giá sau khi đơn hàng hoàn thành", Toast.LENGTH_SHORT).show();
                return;
            }

            EditText edtComment = findViewById(R.id.edt_review_comment);
            String comment = edtComment.getText().toString().trim();

            if (comment.isEmpty() && reviewImageBase64.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập nhận xét hoặc thêm ảnh", Toast.LENGTH_SHORT).show();
                return;
            }

            String userName = user.getEmail() != null ? user.getEmail() : "Ẩn danh";
            String displayName = user.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) userName = displayName;

            Map<String, Object> data = new HashMap<>();
            data.put("userId", user.getUid());
            data.put("userName", userName);
            data.put("productId", productId);
            data.put("comment", comment);
            data.put("rating", selectedRating);
            data.put("createdAt", Timestamp.now());

            if (reviewImageBase64 != null && !reviewImageBase64.isEmpty()) {
                data.put("imageBase64", reviewImageBase64);
            }

            FirebaseFirestore.getInstance().collection("reviews").add(data)
                    .addOnSuccessListener(ref -> {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(this, "Đã gửi đánh giá!", Toast.LENGTH_SHORT).show();
                        edtComment.setText("");
                        reviewImageBase64 = "";
                        ImageView preview = findViewById(R.id.img_review_preview);
                        if (preview != null) preview.setVisibility(View.GONE);
                        selectedRating = 5;
                        setupStarSelector();
                        loadReviews();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Lỗi gửi đánh giá", Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String uriToBase64(Uri uri) throws IOException {
        InputStream is = getContentResolver().openInputStream(uri);
        Bitmap original = BitmapFactory.decodeStream(is);
        if (is != null) is.close();

        // Resize
        int maxSize = 400;
        float scale = Math.min((float) maxSize / original.getWidth(), (float) maxSize / original.getHeight());
        Bitmap resized = Bitmap.createScaledBitmap(original,
                (int) (original.getWidth() * scale), (int) (original.getHeight() * scale), true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resized.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    private void bindProductImages() {
        ImageView imgProduct = findViewById(R.id.img_detail_product);
        LinearLayout layoutThumbs = findViewById(R.id.layout_detail_image_thumbnails);
        View scrollThumbs = findViewById(R.id.scroll_product_images);

        List<String> productImages = getProductImages();
        if (productImages.isEmpty()) {
            imgProduct.setImageDrawable(null);
            scrollThumbs.setVisibility(View.GONE);
            return;
        }

        setMainProductImage(productImages.get(0));
        layoutThumbs.removeAllViews();
        scrollThumbs.setVisibility(productImages.size() > 1 ? View.VISIBLE : View.GONE);

        for (String image : productImages) {
            View thumb = LayoutInflater.from(this)
                    .inflate(R.layout.item_product_detail_thumb, layoutThumbs, false);
            ImageView imgThumb = thumb.findViewById(R.id.img_thumb);
            Bitmap bitmap = decodeBase64Bitmap(image);
            if (bitmap != null) imgThumb.setImageBitmap(bitmap);
            thumb.setOnClickListener(v -> setMainProductImage(image));
            layoutThumbs.addView(thumb);
        }
    }

    private List<String> getProductImages() {
        List<String> images = new ArrayList<>();
        if (product == null) return images;

        List<String> savedImages = product.getImageUrls();
        if (savedImages != null) {
            for (String image : savedImages) {
                if (image != null && !image.isEmpty()) images.add(image);
            }
        }

        if (images.isEmpty()) {
            String mainImage = product.getImageUrl();
            if (mainImage != null && !mainImage.isEmpty()) images.add(mainImage);
        }
        return images;
    }

    private void setMainProductImage(String base64Image) {
        ImageView imgProduct = findViewById(R.id.img_detail_product);
        Bitmap bitmap = decodeBase64Bitmap(base64Image);
        if (bitmap != null) imgProduct.setImageBitmap(bitmap);
    }

    private Bitmap decodeBase64Bitmap(String base64Image) {
        if (base64Image == null || base64Image.isEmpty()) return null;
        try {
            byte[] b = Base64.decode(base64Image, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(b, 0, b.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void setReviewEligibility(boolean eligible) {
        canReview = eligible;
        View reviewActions = findViewById(R.id.layout_review_actions);
        reviewActions.setVisibility(eligible ? View.VISIBLE : View.GONE);

        if (eligible && openReviewWhenEligible) openReviewComposer();
    }

    @SuppressWarnings("unchecked")
    private void checkReviewEligibility() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            setReviewEligibility(false);
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("orders")
                .whereEqualTo("userId", user.getUid())
                .whereEqualTo("status", "done")
                .get()
                .addOnSuccessListener(snap -> {
                    boolean hasCompletedPurchase = false;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get("items");
                        if (containsPurchasedProduct(items)) {
                            hasCompletedPurchase = true;
                            break;
                        }
                    }

                    if (hasCompletedPurchase) {
                        setReviewEligibility(true);
                    } else {
                        setReviewEligibility(false);
                    }
                })
                .addOnFailureListener(e -> setReviewEligibility(false));
    }

    private boolean containsPurchasedProduct(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return false;
        for (Map<String, Object> item : items) {
            Object value = item.get("productId");
            if (value != null && productId.equals(value.toString())) return true;
        }
        return false;
    }

    private void openReviewComposer() {
        openReviewWhenEligible = false;
        NestedScrollView scrollView = findViewById(R.id.scroll_product_detail);
        View reviewForm = findViewById(R.id.layout_review_actions);
        EditText edtComment = findViewById(R.id.edt_review_comment);
        reviewForm.post(() -> {
            scrollView.smoothScrollTo(0, reviewForm.getTop());
            edtComment.requestFocus();
        });
    }

    // ─── Favorites ───
    private void checkFavoriteStatus() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("favorites").document(productId)
                .get()
                .addOnSuccessListener(doc -> {
                    isFavorite = doc.exists();
                    updateFavoriteIcon();
                });
    }

    private void toggleFavorite() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Vui lòng đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        var favRef = FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("favorites").document(productId);

        if (isFavorite) {
            // Remove
            favRef.delete().addOnSuccessListener(v -> {
                isFavorite = false;
                updateFavoriteIcon();
                Toast.makeText(this, "Đã bỏ yêu thích", Toast.LENGTH_SHORT).show();
            });
        } else {
            // Add — store basic product info for listing
            Map<String, Object> data = new HashMap<>();
            if (product != null) {
                data.put("name", product.getName());
                data.put("price", product.getPrice());
                data.put("imageUrl", product.getImageUrl());
                data.put("category", product.getCategory());
                data.put("discountPercent", product.getDiscountPercent());
            }
            data.put("addedAt", com.google.firebase.Timestamp.now());

            favRef.set(data).addOnSuccessListener(v -> {
                isFavorite = true;
                updateFavoriteIcon();
                Toast.makeText(this, "Đã thêm vào yêu thích", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void updateFavoriteIcon() {
        if (isFavorite) {
            btnFavorite.setColorFilter(Color.parseColor("#EF4444"));
        } else {
            btnFavorite.setColorFilter(Color.parseColor("#CCCCCC"));
        }
    }

    private void saveViewHistory() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || product == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("name", product.getName());
        data.put("price", product.getPrice());
        data.put("imageUrl", product.getImageUrl());
        data.put("category", product.getCategory());
        data.put("discountPercent", product.getDiscountPercent());
        data.put("viewedAt", com.google.firebase.Timestamp.now());

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("viewHistory").document(productId)
                .set(data);
    }
}
