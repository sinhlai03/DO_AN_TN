package com.example.appbangiay.customer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.appbangiay.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CustomerOrderDetailActivity extends AppCompatActivity {

    private final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private final String[] STATUS_VALUES = {"pending", "confirmed", "shipping", "done", "cancelled"};
    private final String[] STATUS_LABELS = {"Chờ xác nhận", "Đã xác nhận", "Đang giao", "Hoàn thành", "Đã huỷ"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_customer_order_detail);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        String orderId = getIntent().getStringExtra("orderId");
        if (orderId == null) { finish(); return; }

        loadOrder(orderId);
    }

    @SuppressWarnings("unchecked")
    private void loadOrder(String orderId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("orders").document(orderId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || !user.getUid().equals(doc.getString("userId"))) {
                        Toast.makeText(this, "Đơn hàng không tồn tại", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    // Header
                    String shortId = orderId.length() >= 8 ? orderId.substring(0, 8).toUpperCase() : orderId.toUpperCase();
                    ((TextView) findViewById(R.id.tv_header_title)).setText("Đơn #" + shortId);

                    // Status
                    String status = doc.getString("status");
                    boolean canReviewOrder = "done".equals(status);
                    TextView tvStatus = findViewById(R.id.tv_status);
                    tvStatus.setText(getLabel(status));
                    tvStatus.setTextColor(getStatusColor(status));

                    // Address & payment
                    String userName = doc.getString("userName");
                    String userPhone = doc.getString("userPhone");
                    String address = doc.getString("address");
                    String payment = doc.getString("paymentMethod");
                    String customerNote = doc.getString("customerNote");
                    ((TextView) findViewById(R.id.tv_receiver_name)).setText(
                            "Người nhận: " + ((userName != null && !userName.isEmpty()) ? userName : "N/A"));
                    ((TextView) findViewById(R.id.tv_receiver_phone)).setText(
                            "Số điện thoại: " + ((userPhone != null && !userPhone.isEmpty()) ? userPhone : "N/A"));
                    ((TextView) findViewById(R.id.tv_address)).setText(address != null ? address : "N/A");
                    ((TextView) findViewById(R.id.tv_payment)).setText(
                            "COD".equals(payment) ? "Thanh toán khi nhận hàng" : "VNPAY");
                    TextView tvNote = findViewById(R.id.tv_customer_note);
                    if (customerNote != null && !customerNote.trim().isEmpty()) {
                        tvNote.setText("Ghi chú: " + customerNote.trim());
                        tvNote.setVisibility(View.VISIBLE);
                    } else {
                        tvNote.setVisibility(View.GONE);
                    }

                    // Items
                    LinearLayout layoutItems = findViewById(R.id.layout_items);
                    layoutItems.removeAllViews();
                    List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get("items");
                    if (items != null) {
                        for (Map<String, Object> item : items) {
                            View row = LayoutInflater.from(this).inflate(R.layout.item_checkout_product, layoutItems, false);
                            ((TextView) row.findViewById(R.id.tv_name)).setText(
                                    item.get("name") != null ? item.get("name").toString() : "");
                            int qty = item.get("quantity") != null ? ((Number) item.get("quantity")).intValue() : 0;
                            double sub = item.get("subtotal") != null ? ((Number) item.get("subtotal")).doubleValue() : 0;
                            ((TextView) row.findViewById(R.id.tv_qty)).setText("x" + qty);
                            ((TextView) row.findViewById(R.id.tv_price)).setText(fmt.format(sub) + " đ");
                            TextView tvSize = row.findViewById(R.id.tv_checkout_size);
                            String selectedSize = item.get("selectedSize") != null ? item.get("selectedSize").toString() : null;
                            if (selectedSize != null && !selectedSize.isEmpty()) {
                                tvSize.setText("Size: " + selectedSize);
                                tvSize.setVisibility(View.VISIBLE);
                            } else {
                                tvSize.setVisibility(View.GONE);
                            }

                            String imgStr = item.get("imageUrl") != null ? item.get("imageUrl").toString() : null;
                            if (imgStr != null && !imgStr.isEmpty()) {
                                try {
                                    byte[] b = android.util.Base64.decode(imgStr, android.util.Base64.DEFAULT);
                                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.length);
                                    ((android.widget.ImageView) row.findViewById(R.id.img_product)).setImageBitmap(bmp);
                                } catch (Exception ignored) {}
                            }

                            // Per-product Mua lai button
                            TextView btnBuyItem = row.findViewById(R.id.btn_buy_item);
                            btnBuyItem.setVisibility(View.VISIBLE);
                            btnBuyItem.setText("Mua lại");
                            final String pid = item.get("productId") != null ? item.get("productId").toString() : null;
                            final String orderSelectedSize = selectedSize;
                            final int itemQty = qty;
                            btnBuyItem.setOnClickListener(bv -> {
                                if (pid == null) return;
                                CartManager.getInstance().clear();
                                FirebaseFirestore.getInstance().collection("products").document(pid).get()
                                        .addOnSuccessListener(pdoc -> {
                                            if (pdoc.exists()) {
                                                com.example.appbangiay.model.Product p = pdoc.toObject(com.example.appbangiay.model.Product.class);
                                                if (p != null) {
                                                    p.setId(pdoc.getId());
                                                    for (int i = 0; i < itemQty; i++)
                                                        CartManager.getInstance().addProduct(p, orderSelectedSize);
                                                }
                                            }
                                            startActivity(new android.content.Intent(this, CheckoutActivity.class));
                                        });
                            });

                            View btnReviewItem = row.findViewById(R.id.btn_review_item);
                            if (canReviewOrder && pid != null && !pid.isEmpty()) {
                                btnReviewItem.setVisibility(View.VISIBLE);
                                btnReviewItem.setOnClickListener(v -> {
                                    android.content.Intent intent = new android.content.Intent(this, ProductDetailActivity.class);
                                    intent.putExtra("product_id", pid);
                                    intent.putExtra("open_review", true);
                                    startActivity(intent);
                                });
                            } else {
                                btnReviewItem.setVisibility(View.GONE);
                            }

                            layoutItems.addView(row);
                        }
                    }

                    // Date
                    Object createdAt = doc.get("createdAt");
                    Date date = null;
                    if (createdAt instanceof Timestamp) date = ((Timestamp) createdAt).toDate();
                    ((TextView) findViewById(R.id.tv_date)).setText(date != null ? SDF.format(date) : "N/A");

                    // Total
                    Double total = doc.getDouble("totalAmount");
                    if (total == null || total == 0) total = doc.getDouble("total");
                    ((TextView) findViewById(R.id.tv_total)).setText(fmt.format(total != null ? total : 0) + " đ");
                });
    }

    private String getLabel(String s) {
        int idx = s != null ? Arrays.asList(STATUS_VALUES).indexOf(s) : -1;
        return idx >= 0 ? STATUS_LABELS[idx] : (s != null ? s : "N/A");
    }

    private int getStatusColor(String s) {
        if (s == null) return 0xFF999999;
        switch (s) {
            case "pending": return 0xFFFF9800;
            case "confirmed": return 0xFF2196F3;
            case "shipping": case "done": return 0xFF4CAF50;
            case "cancelled": return 0xFFF44336;
            default: return 0xFF999999;
        }
    }
}
