package com.example.appbangiay.model;

import com.google.firebase.Timestamp;
import java.util.List;
import java.util.Map;

public class Product {
    private String id;
    private String name;
    private double price;
    private String category;
    private String description;
    private String imageUrl;
    private List<String> imageUrls;
    private int stock;
    private Map<String, Long> sizeStock;
    private int discountPercent; // 0 = no discount, >0 = sale %
    private com.google.firebase.Timestamp saleEndTime;
    private Timestamp createdAt;
    private List<String> sizes; // available sizes, e.g. ["36","37","38","39","40"]


    public Product() {}

    public Product(String name, double price, String category,
                   String description, String imageUrl, int stock) {
        this.name = name;
        this.price = price;
        this.category = category;
        this.description = description;
        this.imageUrl = imageUrl;
        this.stock = stock;
        this.createdAt = Timestamp.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public Map<String, Long> getSizeStock() { return sizeStock; }
    public void setSizeStock(Map<String, Long> sizeStock) { this.sizeStock = sizeStock; }

    public int getStockForSize(String size) {
        if (sizeStock != null && size != null && sizeStock.containsKey(size) && sizeStock.get(size) != null) {
            return sizeStock.get(size).intValue();
        }
        return stock;
    }

    public int getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(int discountPercent) { this.discountPercent = discountPercent; }

    public double getSalePrice() {
        if (discountPercent > 0) return price * (100 - discountPercent) / 100.0;
        return price;
    }

    public com.google.firebase.Timestamp getSaleEndTime() { return saleEndTime; }
    public void setSaleEndTime(com.google.firebase.Timestamp saleEndTime) { this.saleEndTime = saleEndTime; }

    public boolean isSaleActive() {
        if (discountPercent <= 0) return false;
        if (saleEndTime == null) return true; // no end time = always active
        return saleEndTime.toDate().after(new java.util.Date());
    }

    public List<String> getSizes() { return sizes; }
    public void setSizes(List<String> sizes) { this.sizes = sizes; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
