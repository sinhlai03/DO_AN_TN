package com.example.appbangiay.admin.coupon;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.model.Coupon;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CouponListActivity extends AppCompatActivity {

    private final List<Coupon> coupons = new ArrayList<>();
    private CouponAdapter adapter;
    private RecyclerView rvCoupons;
    private LinearLayout layoutEmpty;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final ActivityResultLauncher<Intent> addLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK) loadCoupons();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_coupon_list);

        rvCoupons   = findViewById(R.id.rv_coupons);
        layoutEmpty = findViewById(R.id.layout_empty);

        adapter = new CouponAdapter(coupons, position -> deleteCoupon(position));
        rvCoupons.setLayoutManager(new LinearLayoutManager(this));
        rvCoupons.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_coupon).setOnClickListener(v ->
                addLauncher.launch(new Intent(this, AddCouponActivity.class)));

        loadCoupons();
    }

    private void loadCoupons() {
        db.collection("coupons").get().addOnSuccessListener(snap -> {
            coupons.clear();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                Coupon c = doc.toObject(Coupon.class);
                if (c != null) { c.setId(doc.getId()); coupons.add(c); }
            }
            adapter.notifyDataSetChanged();
            updateUI();
        });
    }

    private void deleteCoupon(int position) {
        Coupon c = coupons.get(position);
        new AlertDialog.Builder(this)
                .setTitle("Xóa mã")
                .setMessage("Xóa mã \"" + c.getCode() + "\"?")
                .setPositiveButton("Xóa", (d, w) -> {
                    db.collection("coupons").document(c.getId()).delete()
                            .addOnSuccessListener(v -> {
                                coupons.remove(position);
                                adapter.notifyItemRemoved(position);
                                updateUI();
                                Toast.makeText(this, "Đã xóa", Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Hủy", null).show();
    }

    private void updateUI() {
        boolean empty = coupons.isEmpty();
        rvCoupons.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    // Inner Adapter
    static class CouponAdapter extends RecyclerView.Adapter<CouponAdapter.VH> {
        interface OnDelete { void onDelete(int position); }

        private final List<Coupon> items;
        private final OnDelete listener;
        private final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        CouponAdapter(List<Coupon> items, OnDelete listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_coupon, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Coupon c = items.get(position);
            h.tvCode.setText(c.getCode());
            h.tvInfo.setText("Giảm " + (int) c.getDiscountPercent() + "% • Đơn tối thiểu "
                    + fmt.format(c.getMinOrderAmount()) + " đ");

            if (c.isActive()) {
                h.tvStatus.setText("Đang hoạt động");
                h.tvStatus.setTextColor(0xFF16A34A);
                h.tvStatus.setBackgroundColor(0x1A16A34A);
            } else {
                h.tvStatus.setText("Tạm dừng");
                h.tvStatus.setTextColor(0xFFEF4444);
                h.tvStatus.setBackgroundColor(0x1AEF4444);
            }

            h.btnDelete.setOnClickListener(v -> listener.onDelete(h.getAdapterPosition()));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvCode, tvInfo, tvStatus;
            ImageView btnDelete;
            VH(@NonNull View v) {
                super(v);
                tvCode    = v.findViewById(R.id.tv_coupon_code);
                tvInfo    = v.findViewById(R.id.tv_coupon_info);
                tvStatus  = v.findViewById(R.id.tv_coupon_status);
                btnDelete = v.findViewById(R.id.btn_delete_coupon);
            }
        }
    }
}
