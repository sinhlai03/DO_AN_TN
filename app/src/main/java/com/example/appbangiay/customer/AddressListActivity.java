package com.example.appbangiay.customer;

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
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.model.Address;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class AddressListActivity extends AppCompatActivity {

    private final List<Address> addresses = new ArrayList<>();
    private AddressAdapter adapter;
    private RecyclerView rvAddresses;
    private LinearLayout layoutEmpty;
    private FirebaseFirestore db;
    private String userId;

    private final ActivityResultLauncher<Intent> addAddressLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) loadAddresses();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        setContentView(R.layout.activity_address_list);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        rvAddresses = findViewById(R.id.rv_addresses);
        layoutEmpty = findViewById(R.id.layout_empty_address);

        adapter = new AddressAdapter(addresses, new AddressAdapter.OnAddressAction() {
            @Override public void onSetPrimary(int position) { setPrimary(position); }
            @Override public void onDelete(int position) { deleteAddress(position); }
        });
        rvAddresses.setLayoutManager(new LinearLayoutManager(this));
        rvAddresses.setAdapter(adapter);

        findViewById(R.id.btn_back_address).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_address).setOnClickListener(v -> {
            Intent i = new Intent(this, AddEditAddressActivity.class);
            addAddressLauncher.launch(i);
        });

        loadAddresses();
    }

    private void loadAddresses() {
        db.collection("users").document(userId).collection("addresses")
                .get().addOnSuccessListener(snap -> {
                    addresses.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Address a = doc.toObject(Address.class);
                        if (a != null) { a.setId(doc.getId()); addresses.add(a); }
                    }
                    addresses.sort((a, b) -> Boolean.compare(b.isPrimary(), a.isPrimary()));
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                });
    }

    private void setPrimary(int position) {
        Address addr = addresses.get(position);
        clearAllPrimary(() -> {
            db.collection("users").document(userId).collection("addresses")
                    .document(addr.getId()).update("isPrimary", true)
                    .addOnSuccessListener(v -> {
                        loadAddresses();
                        Toast.makeText(this, "Đã đặt làm mặc định", Toast.LENGTH_SHORT).show();
                    });
        });
    }

    private void deleteAddress(int position) {
        Address addr = addresses.get(position);
        new AlertDialog.Builder(this)
                .setTitle("Xóa địa chỉ")
                .setMessage("Bạn muốn xóa \"" + addr.getLabel() + "\"?")
                .setPositiveButton("Xóa", (d, w) -> {
                    db.collection("users").document(userId).collection("addresses")
                            .document(addr.getId()).delete()
                            .addOnSuccessListener(v -> {
                                addresses.remove(position);
                                adapter.notifyItemRemoved(position);
                                updateEmptyState();
                            });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void clearAllPrimary(Runnable onDone) {
        db.collection("users").document(userId).collection("addresses")
                .whereEqualTo("isPrimary", true).get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) { onDone.run(); return; }
                    int[] count = {snap.size()};
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        doc.getReference().update("isPrimary", false)
                                .addOnCompleteListener(t -> {
                                    count[0]--;
                                    if (count[0] <= 0) onDone.run();
                                });
                    }
                });
    }

    private void updateEmptyState() {
        boolean empty = addresses.isEmpty();
        rvAddresses.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    // ===== Inner Adapter =====
    static class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.VH> {
        interface OnAddressAction {
            void onSetPrimary(int position);
            void onDelete(int position);
        }

        private final List<Address> items;
        private final OnAddressAction listener;

        AddressAdapter(List<Address> items, OnAddressAction listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_address, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Address a = items.get(position);
            h.tvLabel.setText(a.getLabel());
            h.tvFull.setText(a.getFullAddress());
            h.tvPrimary.setVisibility(a.isPrimary() ? View.VISIBLE : View.GONE);
            h.btnSetPrimary.setOnClickListener(v -> listener.onSetPrimary(h.getAdapterPosition()));
            h.btnDelete.setOnClickListener(v -> listener.onDelete(h.getAdapterPosition()));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvLabel, tvFull, tvPrimary;
            ImageView btnSetPrimary, btnDelete;
            VH(@NonNull View v) {
                super(v);
                tvLabel       = v.findViewById(R.id.tv_address_label);
                tvFull        = v.findViewById(R.id.tv_address_full);
                tvPrimary     = v.findViewById(R.id.tv_primary_badge);
                btnSetPrimary = v.findViewById(R.id.btn_set_primary);
                btnDelete     = v.findViewById(R.id.btn_delete_address);
            }
        }
    }
}
