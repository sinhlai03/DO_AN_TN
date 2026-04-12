package com.example.appbangiay.model;

public class Address {
    private String id;
    private String label;       // "Nhà", "Công ty", etc.
    private String fullAddress;
    private double latitude;
    private double longitude;
    private boolean isPrimary;

    public Address() {}

    public Address(String label, String fullAddress, double latitude, double longitude, boolean isPrimary) {
        this.label = label;
        this.fullAddress = fullAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isPrimary = isPrimary;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getFullAddress() { return fullAddress; }
    public void setFullAddress(String fullAddress) { this.fullAddress = fullAddress; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public boolean isPrimary() { return isPrimary; }
    public void setPrimary(boolean primary) { isPrimary = primary; }
}
