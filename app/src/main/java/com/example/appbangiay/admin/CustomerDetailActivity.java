package com.example.appbangiay.admin;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.admin.adapter.OrderAdapter;
import com.example.appbangiay.model.Order;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class CustomerDetailActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_detail);

        db = FirebaseFirestore.getInstance();
        uid   = getIntent().getStringExtra("uid");
        String name  = getIntent().getStringExtra("name");
        String email = getIntent().getStringExtra("email");
        String phone = getIntent().getStringExtra("phone");

        // Toolbar back
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Header
        TextView tvAvatar = findViewById(R.id.tv_detail_avatar);
        TextView tvName   = findViewById(R.id.tv_detail_name);
        TextView tvEmail  = findViewById(R.id.tv_detail_email);

        String initials = (name != null && !name.isEmpty())
                ? String.valueOf(name.charAt(0)).toUpperCase() : "?";
        tvAvatar.setText(initials);
        tvName.setText(name != null ? name : "N/A");
        tvEmail.setText(email != null ? email : "N/A");

        // Info rows
        ((TextView) findViewById(R.id.tv_detail_phone)).setText(phone != null ? phone : "Chưa có SĐT");
        ((TextView) findViewById(R.id.tv_detail_email2)).setText(email != null ? email : "N/A");
        ((TextView) findViewById(R.id.tv_detail_uid)).setText(uid != null ? uid : "N/A");

        // Order history
        RecyclerView rv = findViewById(R.id.rv_customer_orders);
        rv.setLayoutManager(new LinearLayoutManager(this));

        List<Order> orders = new ArrayList<>();
        OrderAdapter orderAdapter = new OrderAdapter(orders, (order, newStatus) -> { /* read-only view */ });
        rv.setAdapter(orderAdapter);

        if (uid != null) {
            db.collection("orders")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener(snap -> {
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            Order o = doc.toObject(Order.class);
                            if (o != null) { o.setId(doc.getId()); orders.add(o); }
                        }
                        orderAdapter.notifyDataSetChanged();
                    });
        }

        // Delete user button
        findViewById(R.id.btn_delete_user).setOnClickListener(v -> confirmDelete(name));
    }

    private void confirmDelete(String name) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa người dùng")
                .setMessage("Bạn chắc chắn muốn xóa \"" + (name != null ? name : "người dùng này") + "\"?\nHành động này không thể hoàn tác.")
                .setPositiveButton("Xóa", (d, w) -> deleteUser())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteUser() {
        if (uid == null || uid.isEmpty()) return;

        // Delete user document from users collection
        db.collection("users").document(uid).delete()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Đã xóa người dùng", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
