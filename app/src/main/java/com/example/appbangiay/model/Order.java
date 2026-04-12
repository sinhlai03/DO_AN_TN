package com.example.appbangiay.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@IgnoreExtraProperties
public class Order {
    public static final String STATUS_PENDING   = "pending";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_SHIPPING  = "shipping";
    public static final String STATUS_DONE      = "done";
    public static final String STATUS_CANCELLED = "cancelled";

    private String id;
    private String userId;
    private String userName;
    private String userPhone;
    private List<Map<String, Object>> items;
    private double totalAmount;
    private double total;
    private String status;
    private String address;
    private Object createdAt;

    public Order() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserPhone() { return userPhone; }
    public void setUserPhone(String userPhone) { this.userPhone = userPhone; }

    public List<Map<String, Object>> getItems() { return items; }
    public void setItems(List<Map<String, Object>> items) { this.items = items; }

    public double getTotalAmount() { return totalAmount > 0 ? totalAmount : total; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Object getCreatedAt() { return createdAt; }
    public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }

    public Date getCreatedAtDate() {
        if (createdAt instanceof Timestamp) {
            return ((Timestamp) createdAt).toDate();
        } else if (createdAt instanceof String) {
            try {
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .parse((String) createdAt);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
