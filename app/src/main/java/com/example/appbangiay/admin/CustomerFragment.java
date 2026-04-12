package com.example.appbangiay.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.admin.adapter.CustomerAdapter;
import com.example.appbangiay.model.User;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CustomerFragment extends Fragment {

    private static final int PAGE_SIZE = 20;

    private RecyclerView recyclerView;
    private CustomerAdapter adapter;
    private final List<User> allCustomers = new ArrayList<>();
    private final List<User> customerList = new ArrayList<>();
    private FirebaseFirestore db;
    private LinearLayoutManager layoutManager;
    private ProgressBar progressInitial, progressMore;
    private TextView tvEmpty, tvStatVisible, tvStatPhone, tvStatNew;
    private EditText edtSearch;
    private DocumentSnapshot lastVisibleDoc;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private boolean useCreatedAtOrder = true;
    private String currentFilter = "all";
    private String currentQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_customer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = FirebaseFirestore.getInstance();

        recyclerView = view.findViewById(R.id.rv_customers);
        progressInitial = view.findViewById(R.id.progress_customers_loading);
        progressMore = view.findViewById(R.id.progress_customers_more);
        tvEmpty = view.findViewById(R.id.tv_customers_empty);
        tvStatVisible = view.findViewById(R.id.tv_customer_stat_visible);
        tvStatPhone = view.findViewById(R.id.tv_customer_stat_phone);
        tvStatNew = view.findViewById(R.id.tv_customer_stat_new);
        edtSearch = view.findViewById(R.id.edt_customer_search);

        layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        adapter = new CustomerAdapter(customerList);
        recyclerView.setAdapter(adapter);

        setupPagination();
        setupFilter(view);
        setupSearch();
        loadCustomers(true);
    }

    private void setupFilter(View view) {
        ChipGroup chipGroup = view.findViewById(R.id.chip_group_customer_filter);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip chip = view.findViewById(checkedIds.get(0));
            currentFilter = chip != null && chip.getTag() != null ? chip.getTag().toString() : "all";
            applyCustomerFilter();
        });
    }

    private void setupSearch() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                currentQuery = s != null ? s.toString().trim().toLowerCase(Locale.ROOT) : "";
                applyCustomerFilter();
            }
        });
    }

    private void setupPagination() {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0 || isLoading || !hasMore) return;

                int totalItems = layoutManager.getItemCount();
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (totalItems > 0 && lastVisible >= totalItems - 4) {
                    loadCustomers(false);
                }
            }
        });
    }

    private Query buildCustomerQuery() {
        Query query = db.collection("users").whereEqualTo("role", "customer");
        if (useCreatedAtOrder) {
            return query.orderBy("createdAt", Query.Direction.DESCENDING);
        }
        return query.orderBy(FieldPath.documentId(), Query.Direction.ASCENDING);
    }

    private void loadCustomers(boolean reset) {
        if (isLoading) return;
        if (reset) {
            lastVisibleDoc = null;
            hasMore = true;
        } else if (!hasMore) {
            return;
        }

        isLoading = true;
        showLoading(reset, true);

        Query query = buildCustomerQuery().limit(PAGE_SIZE);
        if (!reset && lastVisibleDoc != null) {
            query = query.startAfter(lastVisibleDoc);
        }

        query.get()
                .addOnSuccessListener(snapshots -> {
                    if (reset) {
                        allCustomers.clear();
                        customerList.clear();
                        adapter.notifyDataSetChanged();
                    }

                    List<DocumentSnapshot> docs = snapshots.getDocuments();
                    for (DocumentSnapshot doc : docs) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            user.setUid(doc.getId());
                            allCustomers.add(user);
                        }
                    }

                    if (!docs.isEmpty()) {
                        lastVisibleDoc = docs.get(docs.size() - 1);
                    }
                    hasMore = docs.size() == PAGE_SIZE;

                    applyCustomerFilter();
                })
                .addOnFailureListener(e -> {
                    String error = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
                    if (useCreatedAtOrder && error.contains("index")) {
                        useCreatedAtOrder = false;
                        isLoading = false;
                        showLoading(reset, false);
                        loadCustomers(reset);
                        return;
                    }

                    Toast.makeText(getContext(), "Lỗi tải khách hàng", Toast.LENGTH_SHORT).show();
                    updateEmptyState();
                })
                .addOnCompleteListener(task -> {
                    isLoading = false;
                    showLoading(reset, false);
                });
    }

    private void applyCustomerFilter() {
        customerList.clear();
        for (User user : allCustomers) {
            if (!matchesFilter(user) || !matchesSearch(user)) continue;
            customerList.add(user);
        }
        adapter.notifyDataSetChanged();
        updateStats();
        updateEmptyState();
    }

    private boolean matchesFilter(User user) {
        if ("all".equals(currentFilter)) return true;
        if ("new".equals(currentFilter)) return isNewCustomer(user);
        if ("has_phone".equals(currentFilter)) return hasPhone(user);
        if ("no_phone".equals(currentFilter)) return !hasPhone(user);
        return true;
    }

    private boolean matchesSearch(User user) {
        if (currentQuery.isEmpty()) return true;
        return contains(user.getFullname(), currentQuery)
                || contains(user.getEmail(), currentQuery)
                || contains(user.getPhone(), currentQuery);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean hasPhone(User user) {
        return user.getPhone() != null && !user.getPhone().trim().isEmpty();
    }

    private boolean isNewCustomer(User user) {
        if (user.getCreatedAt() == null) return false;
        Calendar now = Calendar.getInstance();
        Calendar joined = Calendar.getInstance();
        joined.setTime(user.getCreatedAt().toDate());
        return now.get(Calendar.YEAR) == joined.get(Calendar.YEAR)
                && now.get(Calendar.MONTH) == joined.get(Calendar.MONTH);
    }

    private void updateStats() {
        if (tvStatVisible == null) return;

        int phoneCount = 0;
        int newCount = 0;
        for (User user : customerList) {
            if (hasPhone(user)) phoneCount++;
            if (isNewCustomer(user)) newCount++;
        }

        tvStatVisible.setText(String.valueOf(customerList.size()));
        tvStatPhone.setText(String.valueOf(phoneCount));
        tvStatNew.setText(String.valueOf(newCount));
    }

    private void showLoading(boolean initialLoad, boolean visible) {
        if (progressInitial != null) {
            progressInitial.setVisibility(initialLoad && visible ? View.VISIBLE : View.GONE);
        }
        if (progressMore != null) {
            progressMore.setVisibility(!initialLoad && visible ? View.VISIBLE : View.GONE);
        }
    }

    private void updateEmptyState() {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(!isLoading && customerList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }
}
