package com.example.appbangiay.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.appbangiay.R;
import com.example.appbangiay.widget.DonutChartView;
import com.example.appbangiay.widget.BarChartView;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardFragment extends Fragment {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_MONTH = 1;
    private static final int FILTER_YEAR = 2;

    private FirebaseFirestore db;
    private TextView tvTotalProducts, tvTotalOrders, tvTotalCustomers, tvTotalRevenue;
    private TextView tvFilterLabel;
    private TextView chipAll, chipMonth, chipYear, btnPickDate;
    private DonutChartView chartDonut;
    private BarChartView chartBar;

    // Filter state
    private int currentFilter = FILTER_ALL;
    private int selectedMonth; // 1-12
    private int selectedYear;

    // Store stats
    private int totalProducts = 0, totalOrders = 0, totalCustomers = 0;
    private double totalRevenue = 0;
    private int totalStock = 0;

    // Cache all orders for filtering
    private List<DocumentSnapshot> allOrders = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = FirebaseFirestore.getInstance();

        // Init current month/year
        Calendar cal = Calendar.getInstance();
        selectedMonth = cal.get(Calendar.MONTH) + 1;
        selectedYear = cal.get(Calendar.YEAR);

        // Bind views
        tvTotalProducts  = view.findViewById(R.id.tv_total_products);
        tvTotalOrders    = view.findViewById(R.id.tv_total_orders);
        tvTotalCustomers = view.findViewById(R.id.tv_total_customers);
        tvTotalRevenue   = view.findViewById(R.id.tv_total_revenue);
        tvFilterLabel    = view.findViewById(R.id.tv_filter_label);
        chartDonut       = view.findViewById(R.id.chart_donut);
        chartBar         = view.findViewById(R.id.chart_bar);

        chipAll      = view.findViewById(R.id.chip_all);
        chipMonth    = view.findViewById(R.id.chip_month);
        chipYear     = view.findViewById(R.id.chip_year);
        btnPickDate  = view.findViewById(R.id.btn_pick_date);

        // Chip click listeners
        chipAll.setOnClickListener(v -> setFilter(FILTER_ALL));
        chipMonth.setOnClickListener(v -> setFilter(FILTER_MONTH));
        chipYear.setOnClickListener(v -> setFilter(FILTER_YEAR));
        btnPickDate.setOnClickListener(v -> showDatePicker());

        loadStats();
    }

    // ==================== FILTER LOGIC ====================

    private void setFilter(int filter) {
        currentFilter = filter;
        updateChipStyles();

        if (filter == FILTER_ALL) {
            btnPickDate.setVisibility(View.GONE);
            tvFilterLabel.setText("Tất cả");
        } else {
            btnPickDate.setVisibility(View.VISIBLE);
            updateFilterLabel();
        }

        applyFilter();
    }

    private void updateChipStyles() {
        // Reset all chips
        chipAll.setBackgroundResource(R.drawable.bg_chip_unselected);
        chipAll.setTextColor(0xFF6B7280);
        chipMonth.setBackgroundResource(R.drawable.bg_chip_unselected);
        chipMonth.setTextColor(0xFF6B7280);
        chipYear.setBackgroundResource(R.drawable.bg_chip_unselected);
        chipYear.setTextColor(0xFF6B7280);

        // Highlight selected
        TextView selected;
        if (currentFilter == FILTER_MONTH) selected = chipMonth;
        else if (currentFilter == FILTER_YEAR) selected = chipYear;
        else selected = chipAll;

        selected.setBackgroundResource(R.drawable.bg_chip_selected);
        selected.setTextColor(0xFFFFFFFF);
    }

    private void updateFilterLabel() {
        if (currentFilter == FILTER_MONTH) {
            tvFilterLabel.setText("Tháng " + selectedMonth + "/" + selectedYear);
            btnPickDate.setText("T" + selectedMonth + "/" + selectedYear);
        } else if (currentFilter == FILTER_YEAR) {
            tvFilterLabel.setText("Năm " + selectedYear);
            btnPickDate.setText("Năm " + selectedYear);
        }
    }

    private void showDatePicker() {
        if (currentFilter == FILTER_MONTH) {
            showMonthYearPicker();
        } else if (currentFilter == FILTER_YEAR) {
            showYearPicker();
        }
    }

    private void showMonthYearPicker() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(android.R.layout.simple_list_item_1, null);

        // Create a horizontal layout with 2 NumberPickers
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setPadding(40, 40, 40, 40);

        // Month picker
        TextView tvMonth = new TextView(requireContext());
        tvMonth.setText("Tháng: ");
        tvMonth.setTextSize(16);
        layout.addView(tvMonth);

        NumberPicker monthPicker = new NumberPicker(requireContext());
        monthPicker.setMinValue(1);
        monthPicker.setMaxValue(12);
        monthPicker.setValue(selectedMonth);
        monthPicker.setWrapSelectorWheel(true);
        layout.addView(monthPicker);

        // Spacer
        View spacer = new View(requireContext());
        spacer.setLayoutParams(new android.widget.LinearLayout.LayoutParams(60, 1));
        layout.addView(spacer);

        // Year picker
        TextView tvYear = new TextView(requireContext());
        tvYear.setText("Năm: ");
        tvYear.setTextSize(16);
        layout.addView(tvYear);

        NumberPicker yearPicker = new NumberPicker(requireContext());
        yearPicker.setMinValue(2020);
        yearPicker.setMaxValue(2030);
        yearPicker.setValue(selectedYear);
        yearPicker.setWrapSelectorWheel(false);
        layout.addView(yearPicker);

        new AlertDialog.Builder(requireContext())
                .setTitle("Chọn tháng / năm")
                .setView(layout)
                .setPositiveButton("Xác nhận", (d, w) -> {
                    selectedMonth = monthPicker.getValue();
                    selectedYear = yearPicker.getValue();
                    updateFilterLabel();
                    applyFilter();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showYearPicker() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setPadding(40, 40, 40, 40);

        NumberPicker yearPicker = new NumberPicker(requireContext());
        yearPicker.setMinValue(2020);
        yearPicker.setMaxValue(2030);
        yearPicker.setValue(selectedYear);
        yearPicker.setWrapSelectorWheel(false);
        layout.addView(yearPicker);

        new AlertDialog.Builder(requireContext())
                .setTitle("Chọn năm")
                .setView(layout)
                .setPositiveButton("Xác nhận", (d, w) -> {
                    selectedYear = yearPicker.getValue();
                    updateFilterLabel();
                    applyFilter();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ==================== FILTER & DISPLAY ====================

    private void applyFilter() {
        totalOrders = 0;
        totalRevenue = 0;

        Map<String, Double> categoryRevenue = new HashMap<>();

        for (DocumentSnapshot doc : allOrders) {
            // Check if order matches the filter
            if (!matchesFilter(doc)) continue;

            totalOrders++;

            String status = doc.getString("status");
            Double amount = doc.getDouble("totalAmount");
            if (amount == null || amount == 0) amount = doc.getDouble("total");

            if ("done".equals(status) && amount != null) {
                totalRevenue += amount;
            }

            // Aggregate by items categories
            List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get("items");
            if (items != null && amount != null && amount > 0) {
                for (Map<String, Object> item : items) {
                    String name = (String) item.get("name");
                    Object subtotalObj = item.get("subtotal");
                    double itemSub = 0;
                    if (subtotalObj instanceof Number) itemSub = ((Number) subtotalObj).doubleValue();

                    String category = "Khác";
                    if (name != null && !name.isEmpty()) {
                        String[] parts = name.split(" ");
                        category = parts[0];
                    }

                    categoryRevenue.merge(category, itemSub, Double::sum);
                }
            }
        }

        tvTotalOrders.setText(String.valueOf(totalOrders));
        tvTotalRevenue.setText(formatCurrency(totalRevenue));

        // Update donut chart
        if (!categoryRevenue.isEmpty()) {
            List<Map.Entry<String, Double>> entries = new ArrayList<>(categoryRevenue.entrySet());
            entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            List<String> labels = new ArrayList<>();
            List<Float> values = new ArrayList<>();
            double others = 0;

            for (int i = 0; i < entries.size(); i++) {
                if (i < 5) {
                    labels.add(entries.get(i).getKey());
                    values.add(entries.get(i).getValue().floatValue());
                } else {
                    others += entries.get(i).getValue();
                }
            }
            if (others > 0) {
                labels.add("Khác");
                values.add((float) others);
            }

            chartDonut.setData(labels, values);
        } else {
            chartDonut.setData(new ArrayList<>(), new ArrayList<>());
        }

        tryUpdateBar();
    }

    private boolean matchesFilter(DocumentSnapshot doc) {
        if (currentFilter == FILTER_ALL) return true;

        Timestamp ts = doc.getTimestamp("createdAt");
        if (ts == null) return false;

        Calendar cal = Calendar.getInstance();
        cal.setTime(ts.toDate());
        int orderMonth = cal.get(Calendar.MONTH) + 1;
        int orderYear = cal.get(Calendar.YEAR);

        if (currentFilter == FILTER_MONTH) {
            return orderMonth == selectedMonth && orderYear == selectedYear;
        } else if (currentFilter == FILTER_YEAR) {
            return orderYear == selectedYear;
        }
        return true;
    }

    // ==================== LOAD DATA ====================

    private int dataReadyCount = 0;

    private void loadStats() {
        dataReadyCount = 0;

        // Count products + total stock
        db.collection("products").get().addOnSuccessListener(snap -> {
            totalProducts = snap.size();
            tvTotalProducts.setText(String.valueOf(totalProducts));

            totalStock = 0;
            for (DocumentSnapshot doc : snap.getDocuments()) {
                Long s = doc.getLong("stock");
                if (s != null) totalStock += s.intValue();
            }

            dataReadyCount++;
            if (dataReadyCount >= 3) tryUpdateBar();
        });

        // Load ALL orders (cache for filtering)
        db.collection("orders").get().addOnSuccessListener(snap -> {
            allOrders = snap.getDocuments();
            applyFilter(); // Apply current filter (default: ALL)

            dataReadyCount++;
            if (dataReadyCount >= 3) tryUpdateBar();
        });

        // Count customers
        db.collection("users")
                .whereEqualTo("role", "customer")
                .get()
                .addOnSuccessListener(snap -> {
                    totalCustomers = snap.size();
                    tvTotalCustomers.setText(String.valueOf(totalCustomers));

                    dataReadyCount++;
                    if (dataReadyCount >= 3) tryUpdateBar();
                });
    }

    private void tryUpdateBar() {
        if (dataReadyCount < 3) return;

        List<String> barLabels = new ArrayList<>();
        List<Float> barValues = new ArrayList<>();
        List<String> displayVals = new ArrayList<>();

        barLabels.add("Sản phẩm");
        barValues.add((float) totalProducts);
        displayVals.add(String.valueOf(totalProducts));

        barLabels.add("Đơn hàng");
        barValues.add((float) totalOrders);
        displayVals.add(String.valueOf(totalOrders));

        barLabels.add("Khách hàng");
        barValues.add((float) totalCustomers);
        displayVals.add(String.valueOf(totalCustomers));

        barLabels.add("Doanh thu");
        barValues.add((float) totalRevenue);
        displayVals.add(formatCurrency(totalRevenue));

        barLabels.add("Tồn kho");
        barValues.add((float) totalStock);
        displayVals.add(String.valueOf(totalStock));

        chartBar.setData(barLabels, barValues, displayVals);
    }

    private String formatCurrency(double amount) {
        return String.format("%,.0f đ", amount);
    }
}
