package com.example.appbangiay.customer;

import com.example.appbangiay.model.CartItem;
import com.example.appbangiay.model.Product;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quản lý giỏ hàng trên Firestore theo user.
 * Path: users/{userId}/cart/{productId}
 */
public class CartManager {

    private static final CartManager INSTANCE = new CartManager();
    private final List<CartItem> items = new ArrayList<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface OnCartLoaded { void onLoaded(); }

    private CartManager() {}

    public static CartManager getInstance() { return INSTANCE; }

    public List<CartItem> getItems() { return items; }

    private String getUserId() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
    }

    /** Load giỏ hàng từ Firestore */
    public void loadCart(OnCartLoaded callback) {
        String uid = getUserId();
        if (uid.isEmpty()) { callback.onLoaded(); return; }

        db.collection("users").document(uid).collection("cart")
                .get().addOnSuccessListener(snap -> {
                    items.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Product p = new Product();
                        p.setId(doc.getString("productId"));
                        p.setName(doc.getString("name"));
                        p.setPrice(doc.getDouble("price") != null ? doc.getDouble("price") : 0);
                        p.setCategory(doc.getString("category"));
                        p.setImageUrl(doc.getString("imageUrl"));
                        int qty = doc.getLong("quantity") != null ? doc.getLong("quantity").intValue() : 1;
                        String size = doc.getString("selectedSize");
                        items.add(new CartItem(p, qty, size));
                    }
                    callback.onLoaded();
                }).addOnFailureListener(e -> callback.onLoaded());
    }


    /** Thêm sản phẩm không có size (backward compatible) */
    public void addProduct(Product product) {
        addProduct(product, null);
    }

    /** Thêm sản phẩm kèm size đã chọn */
    public void addProduct(Product product, String selectedSize) {
        String uid = getUserId();
        if (uid.isEmpty()) return;

        // Dùng cartKey = productId + "_" + size để phân biệt cùng SP khác size
        String cartKey = buildCartKey(product.getId(), selectedSize);

        for (CartItem item : items) {
            if (buildCartKey(item.getProduct().getId(), item.getSelectedSize()).equals(cartKey)) {
                item.setQuantity(item.getQuantity() + 1);
                saveItem(uid, item);
                return;
            }
        }
        CartItem newItem = new CartItem(product, 1, selectedSize);
        items.add(newItem);
        saveItem(uid, newItem);
    }

    private String buildCartKey(String productId, String size) {
        return (size != null && !size.isEmpty()) ? productId + "_" + size : productId;
    }

    /** Cập nhật số lượng theo CartItem (chính xác theo size) */
    public void updateQuantity(CartItem target, int qty) {
        String uid = getUserId();
        if (uid.isEmpty()) return;

        if (qty <= 0) {
            removeItem(target);
            return;
        }
        target.setQuantity(qty);
        saveItem(uid, target);
    }

    /** Cập nhật số lượng theo productId (backward compat, không dùng nếu có size) */
    public void updateQuantity(String productId, int qty) {
        String uid = getUserId();
        if (uid.isEmpty()) return;

        for (CartItem item : items) {
            if (item.getProduct().getId().equals(productId)) {
                updateQuantity(item, qty);
                return;
            }
        }
    }

    /** Xóa sản phẩm theo cartKey (productId_size hoặc productId) */

    public void removeProduct(String productId) {
        String uid = getUserId();
        if (uid.isEmpty()) return;

        List<CartItem> toRemove = new ArrayList<>();
        for (CartItem item : items) {
            if (item.getProduct().getId().equals(productId)) toRemove.add(item);
        }
        for (CartItem item : toRemove) {
            items.remove(item);
            String key = buildCartKey(item.getProduct().getId(), item.getSelectedSize());
            db.collection("users").document(uid).collection("cart").document(key).delete();
        }
    }

    /** Xóa item cụ thể kèm size */
    public void removeItem(CartItem target) {
        String uid = getUserId();
        if (uid.isEmpty()) return;
        items.remove(target);
        String key = buildCartKey(target.getProduct().getId(), target.getSelectedSize());
        db.collection("users").document(uid).collection("cart").document(key).delete();
    }

    /** Tổng tiền */
    public double getTotal() {
        double total = 0;
        for (CartItem item : items) total += item.getSubtotal();
        return total;
    }

    /** Xóa toàn bộ */
    public void clear() {
        String uid = getUserId();
        if (!uid.isEmpty()) {
            for (CartItem item : items) {
                db.collection("users").document(uid).collection("cart")
                        .document(item.getProduct().getId()).delete();
            }
        }
        items.clear();
    }

    private void saveItem(String uid, CartItem item) {
        Map<String, Object> data = new HashMap<>();
        data.put("productId", item.getProduct().getId());
        data.put("name", item.getProduct().getName());
        data.put("price", item.getProduct().getPrice());
        data.put("category", item.getProduct().getCategory());
        data.put("imageUrl", item.getProduct().getImageUrl());
        data.put("quantity", item.getQuantity());
        data.put("selectedSize", item.getSelectedSize()); // null if no size

        String key = buildCartKey(item.getProduct().getId(), item.getSelectedSize());
        db.collection("users").document(uid).collection("cart")
                .document(key).set(data);
    }
}
