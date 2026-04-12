package com.example.appbangiay.customer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.model.Coupon;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AllOffersActivity extends AppCompatActivity {

    private final List<Coupon> coupons = new ArrayList<>();
    private OffersAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_all_offers);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_open_wallet).setOnClickListener(v ->
                startActivity(new Intent(this, CouponWalletActivity.class)));

        RecyclerView rv = findViewById(R.id.rv_all_coupons);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OffersAdapter();
        rv.setAdapter(adapter);

        loadCoupons();
    }

    private void loadCoupons() {
        FirebaseFirestore.getInstance().collection("coupons")
                .whereEqualTo("active", true).get()
                .addOnSuccessListener(snap -> {
                    coupons.clear();
                    for (var doc : snap.getDocuments()) {
                        Coupon c = doc.toObject(Coupon.class);
                        if (c != null) { c.setId(doc.getId()); coupons.add(c); }
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void saveToWallet(Coupon c) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { Toast.makeText(this, "Vui lòng đăng nhập", Toast.LENGTH_SHORT).show(); return; }

        Map<String, Object> data = new HashMap<>();
        data.put("code", c.getCode());
        data.put("discountPercent", c.getDiscountPercent());
        data.put("minOrderAmount", c.getMinOrderAmount());
        data.put("savedAt", System.currentTimeMillis());

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("wallet").document(c.getCode())
                .set(data)
                .addOnSuccessListener(v ->
                        Toast.makeText(this, "Đã lưu mã " + c.getCode() + " vào ví!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi lưu mã", Toast.LENGTH_SHORT).show());
    }

    class OffersAdapter extends RecyclerView.Adapter<OffersAdapter.VH> {
        private final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_coupon_home, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Coupon c = coupons.get(pos);
            h.tvDiscount.setText("Giảm " + (int) c.getDiscountPercent() + "%");
            h.tvMinOrder.setText("Đơn từ " + fmt.format(c.getMinOrderAmount()) + " đ");
            h.tvCode.setText(c.getCode());
            h.btnCopy.setOnClickListener(v -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("coupon", c.getCode()));
                Toast.makeText(AllOffersActivity.this, "Đã sao chép: " + c.getCode(), Toast.LENGTH_SHORT).show();
            });
            h.btnSaveWallet.setOnClickListener(v -> saveToWallet(c));
        }

        @Override public int getItemCount() { return coupons.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvDiscount, tvMinOrder, tvCode, btnCopy, btnSaveWallet;
            VH(View v) {
                super(v);
                tvDiscount    = v.findViewById(R.id.tv_discount);
                tvMinOrder    = v.findViewById(R.id.tv_min_order);
                tvCode        = v.findViewById(R.id.tv_code);
                btnCopy       = v.findViewById(R.id.btn_copy);
                btnSaveWallet = v.findViewById(R.id.btn_save_wallet);
            }
        }
    }
}
