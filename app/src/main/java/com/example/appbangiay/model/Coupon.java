package com.example.appbangiay.model;

public class Coupon {
    private String id;
    private String code;
    private double discountPercent; // VD: 10 = giảm 10%
    private double minOrderAmount; // Đơn tối thiểu
    private boolean active;

    public Coupon() {}

    public Coupon(String code, double discountPercent, double minOrderAmount, boolean active) {
        this.code = code;
        this.discountPercent = discountPercent;
        this.minOrderAmount = minOrderAmount;
        this.active = active;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(double discountPercent) { this.discountPercent = discountPercent; }
    public double getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(double minOrderAmount) { this.minOrderAmount = minOrderAmount; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
