package com.example.appbangiay.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.appbangiay.LoginActivity;
import com.example.appbangiay.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            ((TextView) view.findViewById(R.id.tv_admin_email)).setText(user.getEmail());
            String name = user.getDisplayName();
            ((TextView) view.findViewById(R.id.tv_admin_name)).setText(name != null ? name : "Admin");
        }

        // App version
        try {
            String version = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            ((TextView) view.findViewById(R.id.tv_app_version))
                    .setText("ShopGiay Admin v" + version);
        } catch (Exception ignored) {}

        // Logout
        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Đăng xuất")
                    .setMessage("Bạn có chắc muốn đăng xuất không?")
                    .setPositiveButton("Đăng xuất", (d, w) -> {
                        FirebaseAuth.getInstance().signOut();
                        Intent intent = new Intent(getContext(), LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    })
                    .setNegativeButton("Huỷ", null)
                    .show();
        });

        // Coupon
        view.findViewById(R.id.btn_coupon_manage).setOnClickListener(v -> {
            startActivity(new Intent(getContext(),
                    com.example.appbangiay.admin.coupon.CouponListActivity.class));
        });

        // Helper to switch tab
        java.util.function.Consumer<Integer> switchTab = (tabId) -> {
            if (getActivity() instanceof com.example.appbangiay.AdminActivity) {
                ((com.example.appbangiay.AdminActivity) getActivity())
                        .getBottomNavigationView().setSelectedItemId(tabId);
            }
        };

        // Products
        view.findViewById(R.id.btn_product_manage).setOnClickListener(v ->
                switchTab.accept(R.id.nav_product));

        // Orders
        view.findViewById(R.id.btn_order_manage).setOnClickListener(v ->
                switchTab.accept(R.id.nav_order));

        // Customers
        view.findViewById(R.id.btn_customer_manage).setOnClickListener(v ->
                switchTab.accept(R.id.nav_customer));

        // Banner
        view.findViewById(R.id.btn_banner_manage).setOnClickListener(v ->
                switchTab.accept(R.id.nav_banner));

        // Change password
        view.findViewById(R.id.btn_change_password).setOnClickListener(v -> {
            if (user == null || user.getEmail() == null) return;
            new AlertDialog.Builder(requireContext())
                    .setTitle("Đổi mật khẩu")
                    .setMessage("Gửi email đặt lại mật khẩu tới\n" + user.getEmail() + "?")
                    .setPositiveButton("Gửi", (d, w) -> {
                        FirebaseAuth.getInstance()
                                .sendPasswordResetEmail(user.getEmail())
                                .addOnSuccessListener(unused ->
                                        Toast.makeText(getContext(), "Đã gửi email đặt lại mật khẩu!", Toast.LENGTH_LONG).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    })
                    .setNegativeButton("Huỷ", null)
                    .show();
        });
    }
}

