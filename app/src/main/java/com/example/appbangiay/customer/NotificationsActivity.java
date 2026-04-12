package com.example.appbangiay.customer;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationsActivity extends AppCompatActivity {

    private final List<NotifItem> items = new ArrayList<>();
    private NotifAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_notifications);

        tvEmpty = findViewById(R.id.tv_empty);
        RecyclerView rv = findViewById(R.id.rv_notifications);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotifAdapter();
        rv.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        loadNotifications();
    }

    private void loadNotifications() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snap -> {
                    items.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        NotifItem item = new NotifItem();
                        item.title = doc.getString("title");
                        item.body = doc.getString("body");
                        item.type = doc.getString("type");
                        Timestamp ts = doc.getTimestamp("createdAt");
                        if (ts != null) {
                            item.time = new SimpleDateFormat("dd/MM/yyyy HH:mm",
                                    Locale.getDefault()).format(ts.toDate());
                        }
                        items.add(item);
                    }

                    // If no notifications in Firestore, show order-based notifications
                    if (items.isEmpty()) {
                        loadOrderNotifications(user);
                    } else {
                        adapter.notifyDataSetChanged();
                        tvEmpty.setVisibility(View.GONE);
                    }
                });
    }

    private void loadOrderNotifications(FirebaseUser user) {
        // Generate notifications from order history
        FirebaseFirestore.getInstance()
                .collection("orders")
                .whereEqualTo("userId", user.getUid())
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        NotifItem item = new NotifItem();
                        String status = doc.getString("status");
                        String orderId = doc.getId().substring(0, Math.min(8, doc.getId().length()));

                        if ("delivered".equals(status)) {
                            item.title = "Đơn hàng đã giao";
                            item.body = "Đơn #" + orderId + " đã được giao thành công";
                        } else if ("shipped".equals(status)) {
                            item.title = "Đang giao hàng";
                            item.body = "Đơn #" + orderId + " đang được vận chuyển";
                        } else if ("confirmed".equals(status)) {
                            item.title = "Đơn hàng đã xác nhận";
                            item.body = "Đơn #" + orderId + " đã được xác nhận";
                        } else {
                            item.title = "Đơn hàng mới";
                            item.body = "Đơn #" + orderId + " đang chờ xác nhận";
                        }
                        item.type = "order";

                        Timestamp ts = doc.getTimestamp("createdAt");
                        if (ts != null) {
                            item.time = new SimpleDateFormat("dd/MM/yyyy HH:mm",
                                    Locale.getDefault()).format(ts.toDate());
                        }
                        items.add(item);
                    }
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("Chưa có thông báo nào");
                });
    }

    static class NotifItem {
        String title, body, type, time;
    }

    class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            NotifItem item = items.get(pos);
            h.tvTitle.setText(item.title != null ? item.title : "");
            h.tvBody.setText(item.body != null ? item.body : "");
            h.tvTime.setText(item.time != null ? item.time : "");

            // Tint icon based on type
            if ("order".equals(item.type)) {
                h.imgIcon.setImageResource(R.drawable.ic_orders);
                h.imgIcon.setColorFilter(0xFF3B82F6);
            } else {
                h.imgIcon.setImageResource(R.drawable.ic_notifications);
                h.imgIcon.setColorFilter(0xFF3B82F6);
            }
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvBody, tvTime;
            ImageView imgIcon;
            VH(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_notif_title);
                tvBody = v.findViewById(R.id.tv_notif_body);
                tvTime = v.findViewById(R.id.tv_notif_time);
                imgIcon = v.findViewById(R.id.img_notif_icon);
            }
        }
    }
}
