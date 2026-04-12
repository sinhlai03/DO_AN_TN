package com.example.appbangiay;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;

import com.example.appbangiay.customer.CartFragment;
import com.example.appbangiay.customer.FavoritesFragment;
import com.example.appbangiay.customer.HomeFragment;
import com.example.appbangiay.customer.MyOrdersFragment;
import com.example.appbangiay.customer.NotificationsFragment;
import com.example.appbangiay.customer.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.customer_bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_home)     { loadFragment(new HomeFragment()); }
            else if (id == R.id.nav_favorite) { loadFragment(new FavoritesFragment()); }
            else if (id == R.id.nav_orders)   { loadFragment(new MyOrdersFragment()); }
            else if (id == R.id.nav_cart)     { loadFragment(new CartFragment()); }
            else if (id == R.id.nav_notif)    { loadFragment(new NotificationsFragment()); }
            else if (id == R.id.nav_profile)  { loadFragment(new ProfileFragment()); }
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.customer_fragment_container, fragment)
                .commit();
    }
}