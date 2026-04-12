package com.example.appbangiay.customer;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.customer.adapter.HomeProductAdapter;
import com.example.appbangiay.model.Product;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class AllProductsActivity extends AppCompatActivity {

    private final List<Product> products = new ArrayList<>();
    private HomeProductAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_all_products);

        RecyclerView rv = findViewById(R.id.rv_all_products);
        rv.setLayoutManager(new GridLayoutManager(this, 2));

        adapter = new HomeProductAdapter(products, product -> {
            CartManager.getInstance().addProduct(product);
            com.google.android.material.snackbar.Snackbar
                    .make(rv, "Đã thêm: " + product.getName(),
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(getColor(R.color.blue_primary))
                    .setTextColor(getColor(R.color.white))
                    .show();
        });
        rv.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        loadProducts();
    }

    private void loadProducts() {
        FirebaseFirestore.getInstance().collection("products").get()
                .addOnSuccessListener(snap -> {
                    products.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Product p = doc.toObject(Product.class);
                        if (p != null) { p.setId(doc.getId()); products.add(p); }
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi tải sản phẩm", Toast.LENGTH_SHORT).show());
    }
}
