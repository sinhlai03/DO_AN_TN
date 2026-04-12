package com.example.appbangiay.model;

import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Banner {
    private String id;
    private String imageBase64;
    private String title;
    private boolean active;
    private String type; // "image" or "video"
    private String videoUrl;

    public Banner() {}

    public Banner(String imageBase64, String title, boolean active) {
        this.imageBase64 = imageBase64;
        this.title = title;
        this.active = active;
        this.type = "image";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getType() { return type != null ? type : "image"; }
    public void setType(String type) { this.type = type; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
}
