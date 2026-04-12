package com.example.appbangiay.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Review {
    private String id;
    private String userId;
    private String userName;
    private String productId;
    private String comment;
    private String imageBase64; // review photo
    private int rating; // 1-5
    private Timestamp createdAt;

    public Review() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
