package com.example.appbangiay.customer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.appbangiay.R;
import com.example.appbangiay.customer.adapter.BannerAdapter;
import com.example.appbangiay.customer.adapter.HomeProductAdapter;
import com.example.appbangiay.model.Banner;
import com.example.appbangiay.model.Coupon;
import com.example.appbangiay.model.Product;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private HomeProductAdapter adapter;
    private final List<Product> allProducts     = new ArrayList<>();
    private final List<Product> filteredProducts = new ArrayList<>();
    private final List<Banner>  banners          = new ArrayList<>();
    private final List<Coupon>  coupons          = new ArrayList<>();
    private final List<Product> flashSaleProducts = new ArrayList<>();
    private final List<String>  categoryList     = new ArrayList<>();
    private FirebaseFirestore db;
    private String currentSearchQuery = "";
    private String selectedCategory = "Tất cả";

    // Banner auto-slide
    private ViewPager2 vpBanner;
    private LinearLayout llDots;
    private final Handler autoSlideHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoSlideRunnable = new Runnable() {
        @Override public void run() {
            if (banners.isEmpty()) return;
            int next = (vpBanner.getCurrentItem() + 1) % banners.size();
            vpBanner.setCurrentItem(next, true);
            autoSlideHandler.postDelayed(this, 3500);
        }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = FirebaseFirestore.getInstance();

        // Header padding handled by layout XML (no edge-to-edge)

        // RecyclerView grid 2 cột
        RecyclerView rv = view.findViewById(R.id.rv_home_products);
        rv.setLayoutManager(new GridLayoutManager(getContext(), 2));
        adapter = new HomeProductAdapter(filteredProducts, product -> {
                CartManager.getInstance().addProduct(product);
                com.google.android.material.snackbar.Snackbar snackbar = com.google.android.material.snackbar.Snackbar
                        .make(view, "Đã thêm: " + product.getName(), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT);
                snackbar.setAnchorView(null);
                snackbar.setBackgroundTint(requireContext().getColor(R.color.blue_primary));
                snackbar.setTextColor(requireContext().getColor(R.color.white));
                snackbar.show();
        });
        rv.setAdapter(adapter);

        // FAB Chat AI
        view.findViewById(R.id.fab_chat).setOnClickListener(v -> {
            Intent chatIntent = new Intent(getContext(), com.example.appbangiay.chat.ChatActivity.class);
            startActivity(chatIntent);
        });

        // Animate FAB: pulse + bounce
        startFabAnimations(view);

        // Banner ViewPager2
        vpBanner = view.findViewById(R.id.vp_banner);
        llDots   = view.findViewById(R.id.ll_dots);
        BannerAdapter bannerAdapter = new BannerAdapter(banners);
        vpBanner.setAdapter(bannerAdapter);
        vpBanner.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { updateDots(position); }
        });

        // Search → open SearchActivity
        view.findViewById(R.id.btn_open_search).setOnClickListener(v ->
                startActivity(new Intent(getContext(), SearchActivity.class)));

        // Categories horizontal
        RecyclerView rvCats = view.findViewById(R.id.rv_categories);
        rvCats.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        // Coupons horizontal
        RecyclerView rvCoupons = view.findViewById(R.id.rv_coupons_home);
        rvCoupons.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        // Xem tất cả
        view.findViewById(R.id.tv_see_all).setOnClickListener(v -> {
            startActivity(new android.content.Intent(getContext(), AllProductsActivity.class));
        });

        loadBanners();
        loadProducts();
        loadCoupons(view);
        loadFlashSale(view);

        // Xem tất cả ưu đãi
        view.findViewById(R.id.tv_see_all_coupon).setOnClickListener(v ->
                startActivity(new Intent(getContext(), AllOffersActivity.class)));
    }

    @Override public void onResume() {
        super.onResume();
        if (!banners.isEmpty()) autoSlideHandler.postDelayed(autoSlideRunnable, 3500);
    }

    @Override public void onPause() {
        super.onPause();
        autoSlideHandler.removeCallbacks(autoSlideRunnable);
    }

    private void loadBanners() {
        db.collection("banners").whereEqualTo("active", true).get()
                .addOnSuccessListener(snap -> {
                    banners.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Banner b = doc.toObject(Banner.class);
                        if (b != null) { b.setId(doc.getId()); banners.add(b); }
                    }
                    if (vpBanner.getAdapter() != null) vpBanner.getAdapter().notifyDataSetChanged();
                    setupDots(banners.size());
                    View layoutBanner = requireView().findViewById(R.id.layout_banner);
                    if (!banners.isEmpty()) {
                        layoutBanner.setVisibility(View.VISIBLE);
                        autoSlideHandler.removeCallbacks(autoSlideRunnable);
                        autoSlideHandler.postDelayed(autoSlideRunnable, 3500);
                    } else {
                        layoutBanner.setVisibility(View.GONE);
                    }
                });
    }

    private void setupDots(int count) {
        llDots.removeAllViews();
        for (int i = 0; i < count; i++) {
            ImageView dot = new ImageView(getContext());
            int size = (int)(8 * getResources().getDisplayMetrics().density);
            int margin = (int)(4 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(i == 0 ? 0xFFFFFFFF : 0x80FFFFFF);
            dot.setBackground(circle);
            llDots.addView(dot);
        }
    }

    private void updateDots(int selected) {
        for (int i = 0; i < llDots.getChildCount(); i++) {
            View dot = llDots.getChildAt(i);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(i == selected ? 0xFFFFFFFF : 0x80FFFFFF);
            dot.setBackground(circle);
        }
    }

    private void loadProducts() {
        db.collection("products").get()
                .addOnSuccessListener(snapshots -> {
                    allProducts.clear();
                    java.util.Set<String> catSet = new java.util.LinkedHashSet<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Product p = doc.toObject(Product.class);
                        if (p != null) {
                            p.setId(doc.getId());
                            allProducts.add(p);
                            if (p.getCategory() != null && !p.getCategory().isEmpty()) {
                                catSet.add(p.getCategory());
                            }
                        }
                    }

                    // Setup categories
                    categoryList.clear();
                    categoryList.add("Tất cả");
                    categoryList.addAll(catSet);

                    RecyclerView rvCats = requireView().findViewById(R.id.rv_categories);
                    com.example.appbangiay.customer.adapter.CategoryAdapter catAdapter =
                            new com.example.appbangiay.customer.adapter.CategoryAdapter(categoryList, cat -> {
                                selectedCategory = cat;
                                filterProducts();
                            });
                    rvCats.setAdapter(catAdapter);

                    filterProducts();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Lỗi tải sản phẩm", Toast.LENGTH_SHORT).show());
    }

    private void filterProducts() {
        filteredProducts.clear();
        for (Product p : allProducts) {
            boolean matchQuery = currentSearchQuery.isEmpty() ||
                    p.getName().toLowerCase().contains(currentSearchQuery.toLowerCase());
            boolean matchCat = "Tất cả".equals(selectedCategory) ||
                    selectedCategory.equals(p.getCategory());
            if (matchQuery && matchCat) filteredProducts.add(p);
        }
        adapter.notifyDataSetChanged();
    }

    private void loadCoupons(View rootView) {
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();

        db.collection("coupons").whereEqualTo("active", true).get()
                .addOnSuccessListener(snap -> {
                    coupons.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Coupon c = doc.toObject(Coupon.class);
                        if (c != null) { c.setId(doc.getId()); coupons.add(c); }
                    }

                    LinearLayout layoutCoupons = rootView.findViewById(R.id.layout_coupons);
                    RecyclerView rvCoupons = rootView.findViewById(R.id.rv_coupons_home);
                    if (coupons.isEmpty()) return;
                    layoutCoupons.setVisibility(View.VISIBLE);

                    // Preload wallet codes to know which coupons are already saved
                    if (user != null) {
                        db.collection("users").document(user.getUid())
                                .collection("wallet").get()
                                .addOnSuccessListener(walletSnap -> {
                                    java.util.Set<String> saved = new java.util.HashSet<>();
                                    for (DocumentSnapshot d : walletSnap.getDocuments()) saved.add(d.getId());
                                    rvCoupons.setAdapter(new CouponHomeAdapter(coupons, saved));
                                })
                                .addOnFailureListener(e -> rvCoupons.setAdapter(new CouponHomeAdapter(coupons, new java.util.HashSet<>())));
                    } else {
                        rvCoupons.setAdapter(new CouponHomeAdapter(coupons, new java.util.HashSet<>()));
                    }
                });
    }

    private void saveToWallet(Coupon c, android.widget.TextView btnSaveView) {
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            com.google.android.material.snackbar.Snackbar.make(requireView(), "Vui lòng đăng nhập", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
            return;
        }

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("code", c.getCode());
        data.put("discountPercent", c.getDiscountPercent());
        data.put("minOrderAmount", c.getMinOrderAmount());
        data.put("savedAt", System.currentTimeMillis());

        db.collection("users").document(user.getUid())
                .collection("wallet").document(c.getCode())
                .set(data)
                .addOnSuccessListener(v -> {
                    com.google.android.material.snackbar.Snackbar
                            .make(requireView(), "Đã lưu mã " + c.getCode() + " vào ví", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                            .setBackgroundTint(requireContext().getColor(R.color.blue_primary))
                            .setTextColor(requireContext().getColor(R.color.white))
                            .show();
                    if (btnSaveView != null) {
                        btnSaveView.setText("Đã lưu");
                        btnSaveView.setEnabled(false);
                        btnSaveView.setAlpha(0.4f);
                    }
                })
                .addOnFailureListener(e ->
                    com.google.android.material.snackbar.Snackbar.make(requireView(), "Lỗi lưu mã", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show());
    }

    // Inline adapter for coupon cards on home
    private class CouponHomeAdapter extends RecyclerView.Adapter<CouponHomeAdapter.VH> {
        private final List<Coupon> items;
        private final java.util.Set<String> savedCodes;
        private final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        CouponHomeAdapter(List<Coupon> items, java.util.Set<String> savedCodes) {
            this.items = items;
            this.savedCodes = savedCodes;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_coupon_home, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Coupon c = items.get(position);
            h.tvDiscount.setText("Giảm " + (int) c.getDiscountPercent() + "%");
            h.tvMinOrder.setText("Đơn từ " + fmt.format(c.getMinOrderAmount()) + " đ");
            h.tvCode.setText(c.getCode());

            h.btnCopy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("coupon", c.getCode()));
                com.google.android.material.snackbar.Snackbar.make(v,
                        "Đã sao chép: " + c.getCode(),
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
            });

            boolean alreadySaved = savedCodes.contains(c.getCode());
            if (alreadySaved) {
                h.btnSaveWallet.setText("Đã lưu");
                h.btnSaveWallet.setEnabled(false);
                h.btnSaveWallet.setAlpha(0.4f);
            } else {
                h.btnSaveWallet.setText("Lưu vào ví");
                h.btnSaveWallet.setEnabled(true);
                h.btnSaveWallet.setAlpha(1f);
                h.btnSaveWallet.setOnClickListener(v -> saveToWallet(c, h.btnSaveWallet));
            }
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvDiscount, tvMinOrder, tvCode, btnSaveWallet;
            View btnCopy;
            VH(@NonNull View v) {
                super(v);
                tvDiscount    = v.findViewById(R.id.tv_discount);
                tvMinOrder    = v.findViewById(R.id.tv_min_order);
                tvCode        = v.findViewById(R.id.tv_code);
                btnCopy       = v.findViewById(R.id.btn_copy);
                btnSaveWallet = v.findViewById(R.id.btn_save_wallet);
            }
        }
    }

    // ─── Flash Sale ───
    private void loadFlashSale(View root) {
        RecyclerView rvFlash = root.findViewById(R.id.rv_flash_sale);
        rvFlash.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        com.example.appbangiay.customer.adapter.FlashSaleAdapter flashAdapter =
                new com.example.appbangiay.customer.adapter.FlashSaleAdapter(flashSaleProducts, product -> {
                    CartManager.getInstance().addProduct(product);
                    com.google.android.material.snackbar.Snackbar.make(root,
                            "Đã thêm: " + product.getName(), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                });
        rvFlash.setAdapter(flashAdapter);

        // Start flash sale badge animation
        startFlashSaleAnimation(root);

        db.collection("products").whereGreaterThan("discountPercent", 0).get()
                .addOnSuccessListener(snap -> {
                    flashSaleProducts.clear();
                    java.util.Date nearestEnd = null;

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Product p = doc.toObject(Product.class);
                        if (p != null && p.isSaleActive()) {
                            p.setId(doc.getId());
                            flashSaleProducts.add(p);

                            // Track nearest end time
                            if (p.getSaleEndTime() != null) {
                                java.util.Date end = p.getSaleEndTime().toDate();
                                if (nearestEnd == null || end.before(nearestEnd)) {
                                    nearestEnd = end;
                                }
                            }
                        }
                    }
                    flashAdapter.notifyDataSetChanged();

                    if (!flashSaleProducts.isEmpty()) {
                        root.findViewById(R.id.layout_flash_sale).setVisibility(View.VISIBLE);
                        startFlashTitleAnimation(root);
                        startCountdown(root, nearestEnd);
                    }
                });
    }

    private void startFlashTitleAnimation(View root) {
        // Shimmer light sweep across Flash Sale badge
        View shimmer = root.findViewById(R.id.flash_shimmer);
        shimmer.post(() -> {
            View badge = root.findViewById(R.id.tv_flash_title);
            float badgeWidth = badge.getWidth();
            android.animation.ObjectAnimator sweep = android.animation.ObjectAnimator.ofFloat(
                    shimmer, "translationX", -shimmer.getWidth(), badgeWidth);
            sweep.setDuration(2000);
            sweep.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            sweep.setStartDelay(500);
            sweep.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            sweep.start();
        });
    }

    private void startCountdown(View root, java.util.Date endTime) {
        TextView tvH = root.findViewById(R.id.tv_flash_hour);
        TextView tvM = root.findViewById(R.id.tv_flash_min);
        TextView tvS = root.findViewById(R.id.tv_flash_sec);

        // Fallback: end of today if no end time set
        if (endTime == null) {
            Calendar eod = Calendar.getInstance();
            eod.set(Calendar.HOUR_OF_DAY, 23);
            eod.set(Calendar.MINUTE, 59);
            eod.set(Calendar.SECOND, 59);
            endTime = eod.getTime();
        }

        final java.util.Date finalEnd = endTime;
        Handler countdownHandler = new Handler(Looper.getMainLooper());
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                long diff = finalEnd.getTime() - System.currentTimeMillis();
                if (diff <= 0) {
                    tvH.setText("00"); tvM.setText("00"); tvS.setText("00");
                    // Fade out animation
                    View layout = root.findViewById(R.id.layout_flash_sale);
                    layout.animate().alpha(0f).setDuration(500).withEndAction(() ->
                            layout.setVisibility(View.GONE)).start();
                    return;
                }

                long totalSec = diff / 1000;
                int h = (int) (totalSec / 3600);
                int m = (int) ((totalSec % 3600) / 60);
                int s = (int) (totalSec % 60);

                tvH.setText(String.format(Locale.US, "%02d", h));
                tvM.setText(String.format(Locale.US, "%02d", m));
                tvS.setText(String.format(Locale.US, "%02d", s));

                countdownHandler.postDelayed(this, 1000);
            }
        };
        countdownHandler.post(tick);
    }

    // ─── Flash Sale Badge Animation (Shopee/Lazada style) ───
    private void startFlashSaleAnimation(View root) {
        View badge = root.findViewById(R.id.tv_flash_title);
        View shimmer = root.findViewById(R.id.flash_shimmer);
        if (badge == null) return;

        // 1. Breathing scale pulse: 1.0 → 1.07 → 1.0, repeating
        android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator
                .ofFloat(badge, "scaleX", 1f, 1.07f, 1f);
        android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator
                .ofFloat(badge, "scaleY", 1f, 1.07f, 1f);
        scaleX.setDuration(800);
        scaleY.setDuration(800);
        scaleX.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        scaleY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        scaleX.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        scaleX.start();
        scaleY.start();

        // 2. Background color blink: bright red ↔ deep red
        android.animation.ValueAnimator colorAnim = android.animation.ValueAnimator
                .ofArgb(0xFFEF4444, 0xFFB91C1C, 0xFFEF4444);
        colorAnim.setDuration(600);
        colorAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        colorAnim.setInterpolator(new android.view.animation.LinearInterpolator());
        colorAnim.addUpdateListener(anim -> {
            int color = (int) anim.getAnimatedValue();
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(color);
            gd.setCornerRadius(badge.getContext().getResources().getDisplayMetrics().density * 6);
            badge.setBackground(gd);
        });
        colorAnim.start();

        // 3. Shimmer sweep across badge
        if (shimmer != null) {
            badge.post(() -> {
                float badgeWidth = badge.getWidth();
                android.animation.ObjectAnimator sweep = android.animation.ObjectAnimator
                        .ofFloat(shimmer, "translationX", -badgeWidth * 0.3f, badgeWidth * 1.1f);
                sweep.setDuration(1200);
                sweep.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                sweep.setRepeatMode(android.animation.ValueAnimator.RESTART);
                sweep.setInterpolator(new android.view.animation.LinearInterpolator());
                sweep.setStartDelay(400);
                sweep.start();
            });
        }
    }

    // ─── FAB pulse + bounce animation ───
    private void startFabAnimations(View root) {
        View pulse1 = root.findViewById(R.id.pulse1);
        View pulse2 = root.findViewById(R.id.pulse2);
        View fab = root.findViewById(R.id.fab_chat);

        // Pulse ring 1
        android.animation.ObjectAnimator scaleX1 = android.animation.ObjectAnimator.ofFloat(pulse1, "scaleX", 1f, 1.8f);
        android.animation.ObjectAnimator scaleY1 = android.animation.ObjectAnimator.ofFloat(pulse1, "scaleY", 1f, 1.8f);
        android.animation.ObjectAnimator alpha1 = android.animation.ObjectAnimator.ofFloat(pulse1, "alpha", 0.4f, 0f);
        android.animation.AnimatorSet pulseSet1 = new android.animation.AnimatorSet();
        pulseSet1.playTogether(scaleX1, scaleY1, alpha1);
        pulseSet1.setDuration(1500);
        pulseSet1.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        pulseSet1.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                pulse1.setScaleX(1f); pulse1.setScaleY(1f); pulse1.setAlpha(0.4f);
                pulseSet1.setStartDelay(300);
                pulseSet1.start();
            }
        });
        pulseSet1.start();

        // Pulse ring 2 (delayed)
        android.animation.ObjectAnimator scaleX2 = android.animation.ObjectAnimator.ofFloat(pulse2, "scaleX", 1f, 2.0f);
        android.animation.ObjectAnimator scaleY2 = android.animation.ObjectAnimator.ofFloat(pulse2, "scaleY", 1f, 2.0f);
        android.animation.ObjectAnimator alpha2 = android.animation.ObjectAnimator.ofFloat(pulse2, "alpha", 0.3f, 0f);
        android.animation.AnimatorSet pulseSet2 = new android.animation.AnimatorSet();
        pulseSet2.playTogether(scaleX2, scaleY2, alpha2);
        pulseSet2.setDuration(1500);
        pulseSet2.setStartDelay(500);
        pulseSet2.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        pulseSet2.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                pulse2.setScaleX(1f); pulse2.setScaleY(1f); pulse2.setAlpha(0.3f);
                pulseSet2.setStartDelay(300);
                pulseSet2.start();
            }
        });
        pulseSet2.start();

        // Bounce up/down
        android.animation.ObjectAnimator bounce = android.animation.ObjectAnimator.ofFloat(
                root.findViewById(R.id.fab_chat_container), "translationY", 0f, -10f, 0f, -5f, 0f);
        bounce.setDuration(2000);
        bounce.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        bounce.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        bounce.start();
    }
}
