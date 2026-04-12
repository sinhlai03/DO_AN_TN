package com.example.appbangiay.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.appbangiay.R;
import com.example.appbangiay.model.Order;
import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderDetailActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String orderId;
    private String cachedUserId = "";
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
        setContentView(R.layout.activity_order_detail);

        db = FirebaseFirestore.getInstance();
        orderId = getIntent().getStringExtra("orderId");

        if (orderId == null || orderId.isEmpty()) { finish(); return; }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_delete_order).setOnClickListener(v -> confirmDelete());
        loadOrderDetail();
    }

    private void loadOrderDetail() {
        db.collection("orders").document(orderId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Đơn hàng không tồn tại", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    displayOrder(doc);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @SuppressWarnings("unchecked")
    private void displayOrder(DocumentSnapshot doc) {
        // Header
        String shortId = orderId.length() >= 8 ? orderId.substring(0, 8).toUpperCase() : orderId.toUpperCase();
        ((TextView) findViewById(R.id.tv_header_order_id)).setText("Đơn #" + shortId);

        // Status
        String status = doc.getString("status");
        TextView tvStatus = findViewById(R.id.tv_detail_status);
        tvStatus.setText(getStatusLabel(status));
        tvStatus.setBackgroundColor(getStatusColor(status));
        updateStatusChips(status);
        bindStatusActions(status);

        // Customer info
        cachedUserId = doc.getString("userId") != null ? doc.getString("userId") : "";
        String name = doc.getString("userName");
        String phone = doc.getString("userPhone");
        String email = doc.getString("userEmail");
        String address = doc.getString("address");
        String payment = doc.getString("paymentMethod");
        String customerNote = doc.getString("customerNote");

        ((TextView) findViewById(R.id.tv_detail_name)).setText("Người nhận: " + (name != null && !name.isEmpty() ? name : "N/A"));
        ((TextView) findViewById(R.id.tv_detail_phone)).setText("Số điện thoại: " + (phone != null && !phone.isEmpty() ? phone : "N/A"));
        ((TextView) findViewById(R.id.tv_detail_email)).setText("Email: " + (email != null ? email : "N/A"));
        ((TextView) findViewById(R.id.tv_detail_address)).setText("Địa chỉ: " + (address != null ? address : "N/A"));
        ((TextView) findViewById(R.id.tv_detail_payment)).setText("Thanh toán: " + (payment != null ? payment : "N/A"));
        TextView tvNote = findViewById(R.id.tv_detail_note);
        if (customerNote != null && !customerNote.trim().isEmpty()) {
            tvNote.setText("Ghi chú khách: " + customerNote.trim());
            tvNote.setVisibility(View.VISIBLE);
        } else {
            tvNote.setVisibility(View.GONE);
        }
        if ((name == null || name.isEmpty() || phone == null || phone.isEmpty()) && !cachedUserId.isEmpty()) {
            loadCustomerFallback();
        }

        // Items
        LinearLayout layoutItems = findViewById(R.id.layout_detail_items);
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
                } else {
                    tvSize.setText("Size: chưa lưu");
                }
                tvSize.setVisibility(View.VISIBLE);

                // Load ảnh sản phẩm
                String imgStr = item.get("imageUrl") != null ? item.get("imageUrl").toString() : null;
                if (imgStr != null && !imgStr.isEmpty()) {
                    try {
                        byte[] b = android.util.Base64.decode(imgStr, android.util.Base64.DEFAULT);
                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.length);
                        ((android.widget.ImageView) row.findViewById(R.id.img_product)).setImageBitmap(bmp);
                    } catch (Exception ignored) {}
                }

                layoutItems.addView(row);
            }
        }

        // Date
        Object createdAt = doc.get("createdAt");
        Date date = null;
        if (createdAt instanceof Timestamp) {
            date = ((Timestamp) createdAt).toDate();
        } else if (createdAt instanceof String) {
            try {
                date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .parse((String) createdAt);
            } catch (Exception ignored) {}
        }
        ((TextView) findViewById(R.id.tv_detail_date)).setText(date != null ? SDF.format(date) : "N/A");

        // Coupon
        String coupon = doc.getString("couponCode");
        if (coupon != null && !coupon.isEmpty()) {
            findViewById(R.id.layout_coupon_row).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.tv_detail_coupon)).setText(coupon);
        }

        // Total
        Double total = doc.getDouble("totalAmount");
        if (total == null || total == 0) total = doc.getDouble("total");
        ((TextView) findViewById(R.id.tv_detail_total)).setText(fmt.format(total != null ? total : 0) + " đ");
    }

    private void updateStatus(String newStatus) {
        String statusLabel = getStatusLabel(newStatus);

        resolveUserId().addOnSuccessListener(userId -> {
            WriteBatch batch = db.batch();
            batch.update(db.collection("orders").document(orderId), "status", newStatus);
            if (userId != null && !userId.isEmpty()) {
                batch.set(db.collection("users").document(userId)
                                .collection("orders").document(orderId),
                        java.util.Collections.singletonMap("status", newStatus),
                        SetOptions.merge());
                cachedUserId = userId;
            }
            batch.commit()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Đã cập nhật: " + statusLabel, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    loadOrderDetail();
                    sendOrderNotification(newStatus, statusLabel);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void updateStatusChips(String status) {
        int currentIndex = Arrays.asList(STATUS_VALUES).indexOf(status);
        if (currentIndex < 0) currentIndex = 0;
        int[] chipIds = {
                R.id.chip_order_pending,
                R.id.chip_order_confirmed,
                R.id.chip_order_shipping,
                R.id.chip_order_done,
                R.id.chip_order_cancelled
        };

        for (int i = 0; i < chipIds.length; i++) {
            Chip chip = findViewById(chipIds[i]);
            if (chip == null) continue;
            chip.setClickable(false);
            chip.setCheckable(true);
            chip.setChecked(i == currentIndex);
            chip.setEnabled(i <= currentIndex || Order.STATUS_CANCELLED.equals(status) || Order.STATUS_DONE.equals(status));
            chip.setAlpha(chip.isEnabled() ? 1f : 0.6f);
        }
    }

    private void bindStatusActions(String currentStatus) {
        if (currentStatus == null || currentStatus.isEmpty()) {
            currentStatus = Order.STATUS_PENDING;
        }

        TextView btnPrimary = findViewById(R.id.btn_primary_status_action);
        TextView btnSecondary = findViewById(R.id.btn_secondary_status_action);
        TextView tvHint = findViewById(R.id.tv_status_action_hint);

        btnPrimary.setVisibility(View.VISIBLE);
        btnSecondary.setVisibility(View.VISIBLE);

        switch (currentStatus) {
            case Order.STATUS_PENDING:
                btnPrimary.setText("Xác nhận đơn hàng");
                btnPrimary.setOnClickListener(v -> updateStatus(Order.STATUS_CONFIRMED));
                btnSecondary.setText("Hủy đơn");
                btnSecondary.setOnClickListener(v -> updateStatus(Order.STATUS_CANCELLED));
                tvHint.setText("Xác nhận khi đã kiểm tra đơn và thông tin giao hàng đầy đủ.");
                break;

            case Order.STATUS_CONFIRMED:
                btnPrimary.setText("Bắt đầu giao hàng");
                btnPrimary.setOnClickListener(v -> updateStatus(Order.STATUS_SHIPPING));
                btnSecondary.setText("Hủy đơn");
                btnSecondary.setOnClickListener(v -> updateStatus(Order.STATUS_CANCELLED));
                tvHint.setText("Chuyển sang giao hàng khi shop đã chuẩn bị xong sản phẩm.");
                break;

            case Order.STATUS_SHIPPING:
                btnPrimary.setText("Đánh dấu hoàn thành");
                btnPrimary.setOnClickListener(v -> updateStatus(Order.STATUS_DONE));
                btnSecondary.setVisibility(View.GONE);
                tvHint.setText("Đánh dấu hoàn thành sau khi khách đã nhận hàng thành công.");
                break;

            case Order.STATUS_DONE:
                btnPrimary.setVisibility(View.GONE);
                btnSecondary.setVisibility(View.GONE);
                tvHint.setText("Đơn hàng đã hoàn thành. Khách hàng có thể đánh giá sản phẩm.");
                break;

            case Order.STATUS_CANCELLED:
                btnPrimary.setVisibility(View.GONE);
                btnSecondary.setVisibility(View.GONE);
                tvHint.setText("Đơn hàng đã hủy và không còn thao tác tiếp theo.");
                break;

            default:
                btnPrimary.setText("Xác nhận đơn hàng");
                btnPrimary.setOnClickListener(v -> updateStatus(Order.STATUS_CONFIRMED));
                btnSecondary.setText("Hủy đơn");
                btnSecondary.setOnClickListener(v -> updateStatus(Order.STATUS_CANCELLED));
                tvHint.setText("Chọn hành động phù hợp với tiến trình đơn hàng.");
                break;
        }
    }

    private void loadCustomerFallback() {
        db.collection("users").document(cachedUserId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String name = doc.getString("fullname");
                    String phone = doc.getString("phone");
                    if (name != null && !name.isEmpty()) {
                        ((TextView) findViewById(R.id.tv_detail_name)).setText("Người nhận: " + name);
                    }
                    if (phone != null && !phone.isEmpty()) {
                        ((TextView) findViewById(R.id.tv_detail_phone)).setText("Số điện thoại: " + phone);
                    }
                });
    }

    private void sendOrderNotification(String newStatus, String statusLabel) {
        if (cachedUserId.isEmpty()) {
            // Fallback: read from Firestore
            db.collection("orders").document(orderId).get()
                    .addOnSuccessListener(doc -> {
                        String uid = doc.getString("userId");
                        if (uid != null && !uid.isEmpty()) {
                            writeNotification(uid, newStatus, statusLabel);
                        }
                    });
        } else {
            writeNotification(cachedUserId, newStatus, statusLabel);
        }
    }

    private void writeNotification(String userId, String newStatus, String statusLabel) {
        String shortId = orderId.length() >= 8
                ? orderId.substring(0, 8).toUpperCase() : orderId.toUpperCase();

        java.util.Map<String, Object> notif = new java.util.HashMap<>();
        notif.put("title", "Cập nhật đơn hàng #" + shortId);
        notif.put("message", "Đơn hàng của bạn đã chuyển sang trạng thái: " + statusLabel);
        notif.put("orderId", orderId);
        notif.put("status", newStatus);
        notif.put("isRead", false);
        notif.put("createdAt", com.google.firebase.Timestamp.now());

        db.collection("users").document(userId)
                .collection("notifications")
                .add(notif)
                .addOnSuccessListener(ref ->
                        android.util.Log.d("NOTIF", "Notification sent to " + userId))
                .addOnFailureListener(e ->
                        android.util.Log.e("NOTIF", "Failed: " + e.getMessage()));
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa đơn hàng")
                .setMessage("Bạn chắc chắn muốn xóa đơn này?")
                .setPositiveButton("Xóa", (d, w) -> deleteOrder())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteOrder() {
        resolveUserId().addOnSuccessListener(userId -> {
            WriteBatch batch = db.batch();
            batch.delete(db.collection("orders").document(orderId));
            if (userId != null && !userId.isEmpty()) {
                batch.delete(db.collection("users").document(userId)
                        .collection("orders").document(orderId));
            }
            batch.commit()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Đã xóa đơn hàng", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private com.google.android.gms.tasks.Task<String> resolveUserId() {
        if (!cachedUserId.isEmpty()) {
            return com.google.android.gms.tasks.Tasks.forResult(cachedUserId);
        }
        return db.collection("orders").document(orderId).get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    DocumentSnapshot doc = task.getResult();
                    String userId = doc != null ? doc.getString("userId") : "";
                    cachedUserId = userId != null ? userId : "";
                    return cachedUserId;
                });
    }

    private String getStatusLabel(String status) {
        if (status == null) return "N/A";
        int idx = Arrays.asList(STATUS_VALUES).indexOf(status);
        return idx >= 0 ? STATUS_LABELS[idx] : status;
    }

    private int getStatusColor(String status) {
        if (status == null) return 0xFFCCCCCC;
        switch (status) {
            case "pending":   return 0xFFFFF3E0;
            case "confirmed": return 0xFFE3F2FD;
            case "shipping":  return 0xFFE8F5E9;
            case "done":      return 0xFFE8F5E9;
            case "cancelled": return 0xFFFFEBEE;
            default: return 0xFFF5F5F5;
        }
    }
}
