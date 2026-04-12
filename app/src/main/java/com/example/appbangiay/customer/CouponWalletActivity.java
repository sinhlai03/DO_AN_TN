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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CouponWalletActivity extends AppCompatActivity {

    private final List<WalletCoupon> items = new ArrayList<>();
    private WalletAdapter adapter;
    private TextView tvEmpty;
    private boolean pickMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_coupon_wallet);

        tvEmpty = findViewById(R.id.tv_empty_wallet);
        RecyclerView rv = findViewById(R.id.rv_wallet);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WalletAdapter();
        rv.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        pickMode = getIntent().getBooleanExtra("pick_mode", false);

        loadWallet();
    }

    private void loadWallet() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("wallet").get()
                .addOnSuccessListener(snap -> {
                    items.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        WalletCoupon w = new WalletCoupon();
                        w.code = doc.getString("code");
                        Double disc = doc.getDouble("discountPercent");
                        w.discountPercent = disc != null ? disc : 0;
                        Double min = doc.getDouble("minOrderAmount");
                        w.minOrderAmount = min != null ? min : 0;
                        items.add(w);
                    }
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void removeFromWallet(WalletCoupon w, int pos) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("wallet").document(w.code)
                .delete()
                .addOnSuccessListener(v -> {
                    items.remove(pos);
                    adapter.notifyItemRemoved(pos);
                    tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    Toast.makeText(this, "Đã xoá mã " + w.code, Toast.LENGTH_SHORT).show();
                });
    }

    static class WalletCoupon {
        String code;
        double discountPercent, minOrderAmount;
    }

    class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.VH> {
        private final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_wallet_coupon, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            WalletCoupon w = items.get(pos);
            h.tvCode.setText(w.code);
            h.tvDiscount.setText("Giảm " + (int) w.discountPercent + "%");
            h.tvMinOrder.setText("Đơn từ " + fmt.format(w.minOrderAmount) + " đ");
            h.btnDelete.setOnClickListener(v -> removeFromWallet(w, h.getAdapterPosition()));

            // Pick mode: clicking the item returns the coupon code
            if (pickMode) {
                h.itemView.setOnClickListener(v -> {
                    Intent data = new Intent();
                    data.putExtra("selected_coupon_code", w.code);
                    setResult(RESULT_OK, data);
                    finish();
                });
            }
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvCode, tvDiscount, tvMinOrder, btnDelete;
            VH(View v) {
                super(v);
                tvCode     = v.findViewById(R.id.tv_wallet_code);
                tvDiscount = v.findViewById(R.id.tv_wallet_discount);
                tvMinOrder = v.findViewById(R.id.tv_wallet_min_order);
                btnDelete  = v.findViewById(R.id.btn_wallet_delete);
            }
        }
    }
}
