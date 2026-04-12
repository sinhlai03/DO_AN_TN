package com.example.appbangiay.customer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.WindowCompat;

import com.example.appbangiay.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.List;
import java.util.Locale;

public class MapPickerActivity extends AppCompatActivity {

    public static final String EXTRA_ADDRESS = "picked_address";
    private static final int REQ_LOCATION = 101;

    // Vietnam default center (Hà Nội)
    private static final double DEFAULT_LAT = 21.0278;
    private static final double DEFAULT_LNG = 105.8342;
    private static final int DEFAULT_ZOOM = 15;

    private MapView mapView;
    private TextView tvPickedAddress;
    private FusedLocationProviderClient fusedClient;
    private Geocoder geocoder;
    private String currentPickedAddress = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        // OSMDroid config with proper cache
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_map_picker);

        mapView       = findViewById(R.id.map_view);
        tvPickedAddress = findViewById(R.id.tv_picked_address);
        fusedClient   = LocationServices.getFusedLocationProviderClient(this);
        geocoder      = new Geocoder(this, new Locale("vi", "VN"));

        setupMap();
        setupButtons();
        requestLocationAndCenter();
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(DEFAULT_ZOOM);
        mapView.getController().setCenter(new GeoPoint(DEFAULT_LAT, DEFAULT_LNG));

        // Show my location dot
        MyLocationNewOverlay locationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(this), mapView);
        locationOverlay.enableMyLocation();
        mapView.getOverlays().add(locationOverlay);

        // When map is scrolled → reverse geocode center
        mapView.addOnFirstLayoutListener((v, left, top, right, bottom) -> {
            reverseGeocodeCenter();
        });

        // Listen to map scroll events via custom scroll listener
        mapView.setOnTouchListener((v, event) -> {
            v.performClick();
            // Debounce reverse geocode on map move
            mapView.removeCallbacks(reverseGeocodeRunnable);
            mapView.postDelayed(reverseGeocodeRunnable, 800);
            return false;
        });
    }

    private final Runnable reverseGeocodeRunnable = this::reverseGeocodeCenter;

    private void reverseGeocodeCenter() {
        GeoPoint center = (GeoPoint) mapView.getMapCenter();
        if (center == null) return;

        new Thread(() -> {
            try {
                List<Address> results = geocoder.getFromLocation(
                        center.getLatitude(), center.getLongitude(), 1);
                if (results != null && !results.isEmpty()) {
                    Address addr = results.get(0);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i <= addr.getMaxAddressLineIndex(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(addr.getAddressLine(i));
                    }
                    currentPickedAddress = sb.toString();
                } else {
                    currentPickedAddress = String.format(Locale.US,
                            "%.5f, %.5f", center.getLatitude(), center.getLongitude());
                }
                runOnUiThread(() -> tvPickedAddress.setText(currentPickedAddress));
            } catch (Exception e) {
                currentPickedAddress = String.format(Locale.US,
                        "%.5f, %.5f", center.getLatitude(), center.getLongitude());
                runOnUiThread(() -> tvPickedAddress.setText(currentPickedAddress));
            }
        }).start();
    }

    private void setupButtons() {
        findViewById(R.id.btn_map_back).setOnClickListener(v -> finish());

        // GPS: jump to my location
        findViewById(R.id.btn_my_location).setOnClickListener(v -> requestLocationAndCenter());

        // Confirm: return address to caller
        findViewById(R.id.btn_confirm_location).setOnClickListener(v -> {
            if (currentPickedAddress.isEmpty()) {
                Toast.makeText(this, "Vui lòng đợi địa chỉ được nhận diện", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent result = new Intent();
            result.putExtra(EXTRA_ADDRESS, currentPickedAddress);
            setResult(RESULT_OK, result);
            finish();
        });
    }

    private void requestLocationAndCenter() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }
        fusedClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                centerMap(location.getLatitude(), location.getLongitude());
            } else {
                // Default to Hà Nội center
                centerMap(DEFAULT_LAT, DEFAULT_LNG);
                Toast.makeText(this, "Không lấy được vị trí, hiển thị Hà Nội", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void centerMap(double lat, double lng) {
        GeoPoint point = new GeoPoint(lat, lng);
        mapView.getController().animateTo(point, (double) 16, 500L);
        reverseGeocodeCenter();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestLocationAndCenter();
        }
    }

    @Override
    protected void onResume() { super.onResume(); mapView.onResume(); }

    @Override
    protected void onPause() { super.onPause(); mapView.onPause(); }
}
