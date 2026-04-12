package com.example.appbangiay.customer;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.customer.adapter.HomeProductAdapter;
import com.example.appbangiay.customer.widget.FlowLayout;
import com.example.appbangiay.model.Product;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SearchActivity extends AppCompatActivity {

    private final List<Product> allProducts = new ArrayList<>();
    private final List<Product> filtered = new ArrayList<>();
    private HomeProductAdapter adapter;
    private TextView tvEmpty, tvResultCount;
    private RecyclerView rvResults;
    private View layoutSuggestions;
    private EditText edtSearch;
    private ImageView btnClear;

    // Popular keywords (static + dynamic from categories)
    private final String[] popularKeywords = {
            "Nike", "Adidas", "Jordan", "Sneaker",
            "Chạy bộ", "Thể thao", "Cao gót", "Sandal"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_search);

        tvEmpty = findViewById(R.id.tv_empty);
        tvResultCount = findViewById(R.id.tv_result_count);
        rvResults = findViewById(R.id.rv_results);
        layoutSuggestions = findViewById(R.id.layout_suggestions);
        edtSearch = findViewById(R.id.edt_search);
        btnClear = findViewById(R.id.btn_clear);

        rvResults.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new HomeProductAdapter(filtered, product -> {
            CartManager.getInstance().addProduct(product);
            com.google.android.material.snackbar.Snackbar
                    .make(rvResults, "Đã thêm: " + product.getName(),
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(getColor(R.color.blue_primary))
                    .setTextColor(getColor(R.color.white))
                    .show();
        });
        rvResults.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Clear button
        btnClear.setOnClickListener(v -> {
            edtSearch.setText("");
            edtSearch.requestFocus();
        });

        // Auto-show keyboard
        edtSearch.requestFocus();
        getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void onTextChanged(CharSequence s, int i, int i1, int i2) {
                String q = s.toString().trim();
                btnClear.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                filterProducts(q);
            }
            @Override public void afterTextChanged(Editable e) {}
        });

        edtSearch.setOnEditorActionListener((v, actionId, event) -> {
            filterProducts(edtSearch.getText().toString().trim());
            return true;
        });

        // Build popular chips right away
        buildPopularChips();

        // Load products + categories
        loadProducts();
    }

    private void buildPopularChips() {
        FlowLayout flowPopular = findViewById(R.id.flow_popular);
        for (String keyword : popularKeywords) {
            flowPopular.addView(createChip(keyword, 0xFF3B82F6, 0xFFEBF5FF));
        }
    }

    private void buildCategoryChips(Set<String> categories) {
        FlowLayout flowCats = findViewById(R.id.flow_categories);
        flowCats.removeAllViews();
        for (String cat : categories) {
            flowCats.addView(createChip(cat, 0xFF10B981, 0xFFECFDF5));
        }
    }

    private TextView createChip(String text, int borderColor, int bgColor) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(13);
        chip.setTextColor(0xFF333333);

        int hPad = (int) (14 * getResources().getDisplayMetrics().density);
        int vPad = (int) (8 * getResources().getDisplayMetrics().density);
        chip.setPadding(hPad, vPad, hPad, vPad);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20 * getResources().getDisplayMetrics().density);
        bg.setColor(bgColor);
        bg.setStroke((int) (1 * getResources().getDisplayMetrics().density), borderColor);
        chip.setBackground(bg);

        chip.setOnClickListener(v -> {
            edtSearch.setText(text);
            edtSearch.setSelection(text.length());
        });

        return chip;
    }

    private void loadProducts() {
        FirebaseFirestore.getInstance().collection("products").get()
                .addOnSuccessListener(snap -> {
                    allProducts.clear();
                    Set<String> catSet = new LinkedHashSet<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Product p = doc.toObject(Product.class);
                        if (p != null) {
                            p.setId(doc.getId());
                            allProducts.add(p);
                            if (p.getCategory() != null && !p.getCategory().isEmpty()) {
                                catSet.add(p.getCategory());
                            }
                        }
                    }
                    buildCategoryChips(catSet);
                });
    }

    private void filterProducts(String query) {
        filtered.clear();
        if (query.isEmpty()) {
            adapter.notifyDataSetChanged();
            layoutSuggestions.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            tvResultCount.setVisibility(View.GONE);
            rvResults.setVisibility(View.GONE);
            return;
        }

        layoutSuggestions.setVisibility(View.GONE);
        String lower = query.toLowerCase();

        for (Product p : allProducts) {
            boolean matchName = p.getName() != null &&
                    p.getName().toLowerCase().contains(lower);
            boolean matchCategory = p.getCategory() != null &&
                    p.getCategory().toLowerCase().contains(lower);
            boolean matchDesc = p.getDescription() != null &&
                    p.getDescription().toLowerCase().contains(lower);
            if (matchName || matchCategory || matchDesc) {
                filtered.add(p);
            }
        }

        adapter.notifyDataSetChanged();

        if (filtered.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Không tìm thấy sản phẩm \"" + query + "\"");
            tvResultCount.setVisibility(View.GONE);
            rvResults.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            tvResultCount.setVisibility(View.VISIBLE);
            tvResultCount.setText("Tìm thấy " + filtered.size() + " sản phẩm");
            rvResults.setVisibility(View.VISIBLE);
        }
    }
}
