package com.example.appbangiay.customer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.appbangiay.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddEditAddressActivity extends AppCompatActivity {

    private EditText edtLabel, edtFull;
    private CheckBox cbPrimary;
    private FirebaseFirestore db;
    private String userId;
    private FusedLocationProviderClient fusedLocationClient;
    private double pendingLat = 0, pendingLng = 0;

    private final ActivityResultLauncher<String> locationPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) fetchCurrentLocation();
                else Toast.makeText(this, "Cần bật định vị để lấy vị trí", Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        setContentView(R.layout.activity_add_address);

        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        edtLabel  = findViewById(R.id.edt_address_label);
        edtFull   = findViewById(R.id.edt_address_full);
        cbPrimary = findViewById(R.id.cb_primary);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        findViewById(R.id.btn_get_location).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                fetchCurrentLocation();
            } else {
                locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });

        findViewById(R.id.btn_save_address).setOnClickListener(v -> saveAddress());
    }

    @SuppressLint("MissingPermission")
    private void fetchCurrentLocation() {
        Toast.makeText(this, "Đang lấy vị trí...", Toast.LENGTH_SHORT).show();
        CancellationTokenSource cts = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        pendingLat = location.getLatitude();
                        pendingLng = location.getLongitude();
                        reverseGeocode(location);
                    } else {
                        Toast.makeText(this, "Không lấy được vị trí", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void reverseGeocode(Location location) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<android.location.Address> results = geocoder.getFromLocation(
                    location.getLatitude(), location.getLongitude(), 1);
            if (results != null && !results.isEmpty()) {
                android.location.Address addr = results.get(0);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i <= addr.getMaxAddressLineIndex(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(addr.getAddressLine(i));
                }
                edtFull.setText(sb.toString());
                Toast.makeText(this, "Đã lấy vị trí thành công!", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Không thể xác định địa chỉ", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAddress() {
        String label = edtLabel.getText().toString().trim();
        String full = edtFull.getText().toString().trim();
        if (label.isEmpty()) { edtLabel.setError("Nhập nhãn"); return; }
        if (full.isEmpty()) { edtFull.setError("Nhập địa chỉ"); return; }

        boolean isPrimary = cbPrimary.isChecked();
        Map<String, Object> data = new HashMap<>();
        data.put("label", label);
        data.put("fullAddress", full);
        data.put("latitude", pendingLat);
        data.put("longitude", pendingLng);
        data.put("isPrimary", isPrimary);

        if (isPrimary) {
            clearAllPrimary(() -> saveToFirestore(data));
        } else {
            saveToFirestore(data);
        }
    }

    private void saveToFirestore(Map<String, Object> data) {
        db.collection("users").document(userId).collection("addresses")
                .add(data)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Đã lưu địa chỉ!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi lưu: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void clearAllPrimary(Runnable onDone) {
        db.collection("users").document(userId).collection("addresses")
                .whereEqualTo("isPrimary", true).get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) { onDone.run(); return; }
                    int[] count = {snap.size()};
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        doc.getReference().update("isPrimary", false)
                                .addOnCompleteListener(t -> {
                                    count[0]--;
                                    if (count[0] <= 0) onDone.run();
                                });
                    }
                });
    }
}
