package com.example.appbangiay.model;

import com.google.firebase.Timestamp;

public class User {
    private String uid;
    private String fullname;
    private String email;
    private String phone;
    private String role;
    private Timestamp createdAt;

    public User() {}

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFullname() { return fullname; }
    public void setFullname(String fullname) { this.fullname = fullname; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
