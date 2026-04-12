package com.example.appbangiay;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;

import com.example.appbangiay.admin.BannerFragment;
import com.example.appbangiay.admin.CustomerFragment;
import com.example.appbangiay.admin.DashboardFragment;
import com.example.appbangiay.admin.OrderFragment;
import com.example.appbangiay.admin.ProductFragment;
import com.example.appbangiay.admin.SettingsFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AdminActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tô màu status bar thay vì edge-to-edge (tránh header bị phình to)
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        setContentView(R.layout.activity_admin);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
        toolbar.setTitleTextColor(0xFFFFFFFF);

        bottomNav = findViewById(R.id.bottom_nav);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard)  { setTitle("Tổng quan");   loadFragment(new DashboardFragment()); }
            else if (id == R.id.nav_product)  { setTitle("Sản phẩm");     loadFragment(new ProductFragment()); }
            else if (id == R.id.nav_order)    { setTitle("Đơn hàng");     loadFragment(new OrderFragment()); }
            else if (id == R.id.nav_banner)   { setTitle("Banner");        loadFragment(new BannerFragment()); }
            else if (id == R.id.nav_customer) { setTitle("Khách hàng");   loadFragment(new CustomerFragment()); }
            else if (id == R.id.nav_settings) { setTitle("Cài đặt");      loadFragment(new SettingsFragment()); }
            return true;
        });

        // Default tab
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_dashboard);
        }
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public BottomNavigationView getBottomNavigationView() {
        return bottomNav;
    }
}
