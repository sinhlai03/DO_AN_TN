package com.example.appbangiay.customer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.example.appbangiay.R;
import com.example.appbangiay.model.CartItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.app.Activity;

public class CheckoutActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private TextView tvAddress, tvSubtotal, tvTotal, tvDiscount;
    private LinearLayout layoutDiscountRow, layoutItems;
    private EditText edtCoupon, edtCustomerNote;
    private RadioGroup rgPayment;
    private TextView btnPlaceOrder;

    private String selectedAddress = "";
    private double subtotal = 0;
    private double discountPercent = 0;
    private String appliedCouponCode = "";
    private String pendingMomoOrderId = "";
    private final List<CartItem> checkoutItems = new ArrayList<>();
    private boolean directCheckout = false;
    private boolean isPlacingOrder = false;

    private final ActivityResultLauncher<Intent> addressPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            (ActivityResult result) -> {
                // Reload primary address after returning from address list
                loadPrimaryAddress();
            });

    private final ActivityResultLauncher<Intent> walletPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            (ActivityResult result) -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String code = result.getData().getStringExtra("selected_coupon_code");
                    if (code != null && !code.isEmpty()) {
                        edtCoupon.setText(code);
                        applyCoupon();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_checkout);

        tvAddress        = findViewById(R.id.tv_checkout_address);
        tvSubtotal       = findViewById(R.id.tv_subtotal);
        tvTotal          = findViewById(R.id.tv_total);
        tvDiscount       = findViewById(R.id.tv_discount);
        layoutDiscountRow = findViewById(R.id.layout_discount_row);
        layoutItems      = findViewById(R.id.layout_order_items);
        edtCoupon        = findViewById(R.id.edt_coupon_code);
        edtCustomerNote  = findViewById(R.id.edt_customer_note);
        rgPayment        = findViewById(R.id.rg_payment);
        btnPlaceOrder    = findViewById(R.id.btn_place_order);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        // Open address list to pick/manage addresses
        findViewById(R.id.btn_change_address).setOnClickListener(v ->
                addressPickerLauncher.launch(new Intent(this, AddressListActivity.class)));
        findViewById(R.id.btn_apply_coupon).setOnClickListener(v -> applyCoupon());
        btnPlaceOrder.setOnClickListener(v -> placeOrder());

        // Wallet coupon picker
        View btnWallet = findViewById(R.id.btn_pick_wallet_coupon);
        if (btnWallet != null) {
            btnWallet.setOnClickListener(v -> {
                Intent i = new Intent(this, CouponWalletActivity.class);
                i.putExtra("pick_mode", true);
                walletPickerLauncher.launch(i);
            });
        }

        resolveCheckoutItems();
        loadPrimaryAddress();
        displayCartItems();
        updatePrices();

        // Auto-apply coupon from chat
        String couponFromChat = getIntent().getStringExtra("coupon_code");
        if (couponFromChat != null && !couponFromChat.isEmpty()) {
            edtCoupon.setText(couponFromChat);
            applyCoupon();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPrimaryAddress();
    }

    private void loadPrimaryAddress() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid()).collection("addresses")
                .whereEqualTo("isPrimary", true).limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        DocumentSnapshot doc = snap.getDocuments().get(0);
                        String label = doc.getString("label");
                        String full = doc.getString("fullAddress");
                        selectedAddress = full != null ? full : "";
                        tvAddress.setText((label != null ? label + ": " : "") + selectedAddress);
                        tvAddress.setTextColor(0xFF1A1A2E);
                    } else {
                        tvAddress.setText("Chưa chọn địa chỉ - Vui lòng thêm địa chỉ");
                        tvAddress.setTextColor(0xFFEF4444);
                    }
                });
    }

    private void resolveCheckoutItems() {
        checkoutItems.clear();

        Intent intent = getIntent();
        String productId = intent.getStringExtra("direct_checkout_product_id");
        if (productId == null || productId.isEmpty()) {
            directCheckout = false;
            checkoutItems.addAll(CartManager.getInstance().getItems());
            return;
        }

        directCheckout = true;
        com.example.appbangiay.model.Product product = new com.example.appbangiay.model.Product();
        product.setId(productId);
        product.setName(intent.getStringExtra("direct_checkout_name"));
        product.setCategory(intent.getStringExtra("direct_checkout_category"));
        product.setImageUrl(intent.getStringExtra("direct_checkout_image"));
        product.setPrice(intent.getDoubleExtra("direct_checkout_price", 0));

        int quantity = Math.max(1, intent.getIntExtra("direct_checkout_quantity", 1));
        String selectedSize = intent.getStringExtra("direct_checkout_size");
        checkoutItems.add(new CartItem(product, quantity, selectedSize));
    }

    private List<CartItem> getCheckoutItems() {
        return checkoutItems;
    }

    private void displayCartItems() {
        layoutItems.removeAllViews();
        List<CartItem> items = getCheckoutItems();
        subtotal = 0;

        for (CartItem item : items) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_checkout_product, layoutItems, false);

            ImageView img = row.findViewById(R.id.img_product);
            TextView tvName = row.findViewById(R.id.tv_name);
            TextView tvPrice = row.findViewById(R.id.tv_price);
            TextView tvQty = row.findViewById(R.id.tv_qty);

            tvName.setText(item.getProduct().getName());
            tvPrice.setText(fmt.format(item.getSubtotal()) + " đ");
            tvQty.setText("x" + item.getQuantity());

            // Size
            TextView tvSize = row.findViewById(R.id.tv_checkout_size);
            String size = item.getSelectedSize();
            if (size != null && !size.isEmpty()) {
                tvSize.setVisibility(View.VISIBLE);
                tvSize.setText("Size: " + size);
            } else {
                tvSize.setVisibility(View.GONE);
            }

            String imgStr = item.getProduct().getImageUrl();
            if (imgStr != null && !imgStr.isEmpty()) {
                try {
                    byte[] b = Base64.decode(imgStr, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
                    img.setImageBitmap(bmp);
                } catch (Exception ignored) {}
            }

            subtotal += item.getSubtotal();
            layoutItems.addView(row);
        }
    }

    private void applyCoupon() {
        String code = edtCoupon.getText().toString().trim().toUpperCase();
        if (code.isEmpty()) { edtCoupon.setError("Nhập mã"); return; }

        db.collection("coupons")
                .whereEqualTo("code", code)
                .whereEqualTo("active", true)
                .limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        Toast.makeText(this, "Mã không hợp lệ", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DocumentSnapshot doc = snap.getDocuments().get(0);
                    double minOrder = doc.getDouble("minOrderAmount") != null ? doc.getDouble("minOrderAmount") : 0;
                    if (subtotal < minOrder) {
                        Toast.makeText(this, "Đơn tối thiểu " + fmt.format(minOrder) + " đ", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    discountPercent = doc.getDouble("discountPercent") != null ? doc.getDouble("discountPercent") : 0;
                    appliedCouponCode = code;
                    updatePrices();
                    Toast.makeText(this, "Đã áp dụng mã " + code, Toast.LENGTH_SHORT).show();
                });
    }

    private void updatePrices() {
        tvSubtotal.setText(fmt.format(subtotal) + " đ");

        double discountAmount = subtotal * discountPercent / 100;
        if (discountPercent > 0) {
            layoutDiscountRow.setVisibility(View.VISIBLE);
            tvDiscount.setText("-" + fmt.format(discountAmount) + " đ");
        } else {
            layoutDiscountRow.setVisibility(View.GONE);
        }

        double total = subtotal - discountAmount;
        tvTotal.setText(fmt.format(total) + " đ");
    }

    private void placeOrder() {
        if (isPlacingOrder) return;

        if (selectedAddress.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn địa chỉ giao hàng", Toast.LENGTH_SHORT).show();
            return;
        }

        List<CartItem> items = getCheckoutItems();
        if (items.isEmpty()) {
            Toast.makeText(this, "Giỏ hàng trống", Toast.LENGTH_SHORT).show();
            return;
        }

        int checkedId = rgPayment.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_cod) {
            saveOrder("COD", "pending");
        } else {
            showVNPayPayment();
        }
    }


    private void openPayUrl(String payUrl) {
        try {
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(payUrl));
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Thanh toán")
                    .setMessage("Không thể mở trang thanh toán.\nLink:\n" + payUrl)
                    .setPositiveButton("Sao chép link", (d, w) -> {
                        android.content.ClipboardManager clipboard =
                                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("Pay URL", payUrl));
                        Toast.makeText(this, "Đã copy link!", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Đóng", null)
                    .show();
        }
    }

    private void showVNPayPayment() {
        double discountAmount = subtotal * discountPercent / 100;
        long amount = Math.round(subtotal - discountAmount);
        String orderInfo = "Thanh toan don hang ShopGiay";

        final android.app.AlertDialog loading = new android.app.AlertDialog.Builder(this)
                .setMessage("Đang tạo thanh toán VNPAY...")
                .setCancelable(false).create();
        if (!isFinishing() && !isDestroyed()) loading.show();

        com.example.appbangiay.payment.VNPayHelper.createPaymentUrl(
                amount, orderInfo,
                new com.example.appbangiay.payment.VNPayHelper.PaymentCallback() {
                    @Override
                    public void onSuccess(String payUrl, String txnRef) {
                        runOnUiThread(() -> {
                            if (loading.isShowing()) loading.dismiss();
                            if (isFinishing() || isDestroyed()) return;
                            pendingMomoOrderId = txnRef; // reuse field
                            openPayUrl(payUrl);
                        });
                    }
                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (loading.isShowing()) loading.dismiss();
                            Toast.makeText(CheckoutActivity.this,
                                    "Lỗi VNPAY: " + message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }


    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (intent == null || intent.getData() == null) return;

        android.net.Uri data = intent.getData();
        String host = data.getHost();

        if ("vnpay-return".equals(host)) {
            String responseCode = data.getQueryParameter("vnp_ResponseCode");
            if ("00".equals(responseCode)) {
                saveOrder("VNPAY", "confirmed");
            } else {
                Toast.makeText(this, "Thanh toán VNPAY thất bại (code: " + responseCode + ")", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveOrder(String paymentMethod, String status) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        List<CartItem> items = getCheckoutItems();
        setPlacingOrder(true);
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(userDoc -> proceedSaveOrder(
                        user,
                        userDoc.getString("fullname"),
                        userDoc.getString("phone"),
                        items,
                        paymentMethod,
                        status))
                .addOnFailureListener(e -> proceedSaveOrder(
                        user,
                        null,
                        null,
                        items,
                        paymentMethod,
                        status));
    }

    private void proceedSaveOrder(FirebaseUser user,
                                  String userName,
                                  String userPhone,
                                  List<CartItem> items,
                                  String paymentMethod,
                                  String status) {
        Map<String, Integer> reservedQuantities = buildRequiredStockMap(items);
        Map<String, Map<String, Integer>> reservedSizeQuantities = buildRequiredSizeStockMap(items);
        Map<String, Object> order = buildOrderPayload(user, userName, userPhone, items, paymentMethod, status);
        double total = ((Number) order.get("total")).doubleValue();

        DocumentReference orderRef = db.collection("orders").document();
        DocumentReference userOrderRef = db.collection("users")
                .document(user.getUid())
                .collection("orders")
                .document(orderRef.getId());

        db.runTransaction((Transaction.Function<String>) transaction -> {
            for (Map.Entry<String, Integer> entry : reservedQuantities.entrySet()) {
                DocumentReference productRef = db.collection("products").document(entry.getKey());
                DocumentSnapshot productSnap = transaction.get(productRef);

                Long stockValue = productSnap.getLong("stock");
                int availableStock = stockValue != null ? stockValue.intValue() : 0;
                int requestedQuantity = entry.getValue();

                if (availableStock < requestedQuantity) {
                    String productName = productSnap.getString("name");
                    String label = productName != null && !productName.isEmpty() ? productName : "Sản phẩm";
                    throw new CheckoutException(label + " chỉ còn " + availableStock + " sản phẩm.");
                }

                Map<String, Object> updates = new HashMap<>();
                updates.put("stock", availableStock - requestedQuantity);

                Map<String, Integer> sizeRequests = reservedSizeQuantities.get(entry.getKey());
                Map<String, Object> sizeStockMap = productSnap.get("sizeStock") instanceof Map
                        ? (Map<String, Object>) productSnap.get("sizeStock") : null;

                if (sizeRequests != null && !sizeRequests.isEmpty() && sizeStockMap != null && !sizeStockMap.isEmpty()) {
                    for (Map.Entry<String, Integer> sizeEntry : sizeRequests.entrySet()) {
                        int availableSizeStock = getIntFromMap(sizeStockMap, sizeEntry.getKey());
                        if (availableSizeStock < sizeEntry.getValue()) {
                            String productName = productSnap.getString("name");
                            String label = productName != null && !productName.isEmpty() ? productName : "Sản phẩm";
                            throw new CheckoutException(label + " size " + sizeEntry.getKey()
                                    + " chỉ còn " + availableSizeStock + " đôi.");
                        }
                        updates.put("sizeStock." + sizeEntry.getKey(), availableSizeStock - sizeEntry.getValue());
                    }
                }

                transaction.update(productRef, updates);
            }

            transaction.set(orderRef, order);
            transaction.set(userOrderRef, order);
            return orderRef.getId();
        }).addOnSuccessListener(orderId -> {
            if (!directCheckout) {
                clearUserCart(user.getUid());
            }

            String methodLabel = paymentMethod.equals("COD") ? "Khi nhận hàng" : "VNPAY";
            new AlertDialog.Builder(this)
                    .setTitle("Đặt hàng thành công")
                    .setMessage("Mã đơn: " + orderId.substring(0, 8).toUpperCase()
                            + "\nTổng: " + fmt.format(total) + " đ"
                            + "\nThanh toán: " + methodLabel)
                    .setPositiveButton("Về trang chủ", (d, w) -> {
                        setResult(RESULT_OK);
                        finish();
                    })
                    .setCancelable(false)
                    .show();
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Không thể đặt hàng: " + e.getMessage(), Toast.LENGTH_SHORT).show())
                .addOnCompleteListener(task -> setPlacingOrder(false));
    }

    private Map<String, Integer> buildRequiredStockMap(List<CartItem> items) {
        Map<String, Integer> reservedQuantities = new HashMap<>();
        for (CartItem item : items) {
            String productId = item.getProduct().getId();
            if (productId == null || productId.isEmpty()) continue;

            int currentQty = reservedQuantities.containsKey(productId)
                    ? reservedQuantities.get(productId) : 0;
            reservedQuantities.put(productId, currentQty + item.getQuantity());
        }
        return reservedQuantities;
    }

    private Map<String, Map<String, Integer>> buildRequiredSizeStockMap(List<CartItem> items) {
        Map<String, Map<String, Integer>> sizeReservations = new HashMap<>();
        for (CartItem item : items) {
            String productId = item.getProduct().getId();
            String size = item.getSelectedSize();
            if (productId == null || productId.isEmpty() || size == null || size.isEmpty()) continue;

            Map<String, Integer> productSizeMap = sizeReservations.containsKey(productId)
                    ? sizeReservations.get(productId) : new HashMap<>();
            int currentQty = productSizeMap.containsKey(size) ? productSizeMap.get(size) : 0;
            productSizeMap.put(size, currentQty + item.getQuantity());
            sizeReservations.put(productId, productSizeMap);
        }
        return sizeReservations;
    }

    private Map<String, Object> buildOrderPayload(FirebaseUser user,
                                                  String userName,
                                                  String userPhone,
                                                  List<CartItem> items,
                                                  String paymentMethod,
                                                  String status) {
        List<Map<String, Object>> orderItems = new ArrayList<>();
        for (CartItem item : items) {
            Map<String, Object> map = new HashMap<>();
            map.put("productId", item.getProduct().getId());
            map.put("name", item.getProduct().getName());
            map.put("price", item.getProduct().getPrice());
            map.put("quantity", item.getQuantity());
            map.put("subtotal", item.getSubtotal());
            if (item.getSelectedSize() != null && !item.getSelectedSize().isEmpty()) {
                map.put("selectedSize", item.getSelectedSize());
            }
            if (item.getProduct().getImageUrl() != null) {
                map.put("imageUrl", item.getProduct().getImageUrl());
            }
            orderItems.add(map);
        }

        double discountAmount = subtotal * discountPercent / 100;
        double total = subtotal - discountAmount;

        Map<String, Object> order = new HashMap<>();
        order.put("userId", user.getUid());
        order.put("userName", userName != null ? userName : "");
        order.put("userPhone", userPhone != null ? userPhone : "");
        order.put("userEmail", user.getEmail());
        order.put("address", selectedAddress);
        order.put("customerNote", edtCustomerNote != null ? edtCustomerNote.getText().toString().trim() : "");
        order.put("items", orderItems);
        order.put("subtotal", subtotal);
        order.put("discountPercent", discountPercent);
        order.put("discountAmount", discountAmount);
        order.put("couponCode", appliedCouponCode);
        order.put("total", total);
        order.put("totalAmount", total);
        order.put("paymentMethod", paymentMethod);
        order.put("status", status);
        order.put("createdAt", com.google.firebase.Timestamp.now());
        return order;
    }

    private void clearUserCart(String userId) {
        CartManager.getInstance().clear();
        db.collection("users").document(userId)
                .collection("cart")
                .get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        doc.getReference().delete();
                    }
                });
    }

    private void setPlacingOrder(boolean placingOrder) {
        isPlacingOrder = placingOrder;
        if (btnPlaceOrder == null) return;

        btnPlaceOrder.setEnabled(!placingOrder);
        btnPlaceOrder.setAlpha(placingOrder ? 0.6f : 1f);
        btnPlaceOrder.setText(placingOrder ? "Đang xử lý..." : "Đặt hàng");
    }

    private int getIntFromMap(Map<String, Object> source, String key) {
        if (source == null || key == null || !source.containsKey(key) || source.get(key) == null) return 0;
        Object value = source.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static class CheckoutException extends RuntimeException {
        CheckoutException(String message) {
            super(message);
        }
    }
}
