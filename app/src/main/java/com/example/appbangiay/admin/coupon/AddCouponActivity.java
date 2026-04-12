package com.example.appbangiay.admin.coupon;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.appbangiay.R;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AddCouponActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_add_coupon);

        EditText edtCode     = findViewById(R.id.edt_coupon_code);
        EditText edtDiscount = findViewById(R.id.edt_discount);
        EditText edtMinOrder = findViewById(R.id.edt_min_order);
        CheckBox cbActive    = findViewById(R.id.cb_active);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        findViewById(R.id.btn_save_coupon).setOnClickListener(v -> {
            String code = edtCode.getText().toString().trim().toUpperCase();
            String discountStr = edtDiscount.getText().toString().trim();
            String minStr = edtMinOrder.getText().toString().trim();

            if (code.isEmpty()) { edtCode.setError("Nhập mã"); return; }
            if (discountStr.isEmpty()) { edtDiscount.setError("Nhập % giảm"); return; }

            double discount = Double.parseDouble(discountStr);
            double minOrder = minStr.isEmpty() ? 0 : Double.parseDouble(minStr);

            if (discount <= 0 || discount > 100) {
                edtDiscount.setError("Phải từ 1-100%");
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("code", code);
            data.put("discountPercent", discount);
            data.put("minOrderAmount", minOrder);
            data.put("active", cbActive.isChecked());

            FirebaseFirestore.getInstance().collection("coupons")
                    .add(data)
                    .addOnSuccessListener(ref -> {
                        Toast.makeText(this, "Đã tạo mã: " + code, Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
    }
}
