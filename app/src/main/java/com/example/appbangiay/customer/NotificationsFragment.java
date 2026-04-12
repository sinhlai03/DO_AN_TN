package com.example.appbangiay.customer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NotificationsFragment extends Fragment {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

    private final List<Map<String, Object>> items = new ArrayList<>();
    private RecyclerView rv;
    private View layoutEmpty;
    private Adapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rv = view.findViewById(R.id.rv_notifications);
        layoutEmpty = view.findViewById(R.id.layout_empty_notif);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new Adapter();
        rv.setAdapter(adapter);

        // Swipe left or right to delete
        new androidx.recyclerview.widget.ItemTouchHelper(
            new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                    0, androidx.recyclerview.widget.ItemTouchHelper.LEFT | androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
                @Override public boolean onMove(@NonNull RecyclerView rv,
                        @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder tgt) { return false; }

                @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                    int pos = viewHolder.getAdapterPosition();
                    if (pos < 0 || pos >= items.size()) return;
                    Map<String, Object> notif = items.get(pos);
                    String docId = notif.get("__id") != null ? notif.get("__id").toString() : null;
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (docId != null && user != null) {
                        FirebaseFirestore.getInstance()
                                .collection("users").document(user.getUid())
                                .collection("notifications").document(docId)
                                .delete();
                    }
                    items.remove(pos);
                    adapter.notifyItemRemoved(pos);
                    boolean empty = items.isEmpty();
                    rv.setVisibility(empty ? View.GONE : View.VISIBLE);
                    layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                }
            }
        ).attachToRecyclerView(rv);

        loadNotifications();
    }

    private void loadNotifications() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("notifications")
                .limit(50)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        android.util.Log.e("NOTIF_FRAG", "Query error: " + e.getMessage());
                        return;
                    }
                    if (snap == null || getContext() == null) return;
                    items.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) {
                            data.put("__id", doc.getId());
                            items.add(data);
                            if (Boolean.FALSE.equals(data.get("isRead"))) {
                                doc.getReference().update("isRead", true);
                            }
                        }
                    }
                    // Sort by createdAt descending in-memory
                    items.sort((a, b) -> {
                        com.google.firebase.Timestamp ta = a.get("createdAt") instanceof com.google.firebase.Timestamp
                                ? (com.google.firebase.Timestamp) a.get("createdAt") : null;
                        com.google.firebase.Timestamp tb = b.get("createdAt") instanceof com.google.firebase.Timestamp
                                ? (com.google.firebase.Timestamp) b.get("createdAt") : null;
                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;
                        if (tb == null) return -1;
                        return tb.compareTo(ta);
                    });
                    adapter.notifyDataSetChanged();
                    boolean empty = items.isEmpty();
                    rv.setVisibility(empty ? View.GONE : View.VISIBLE);
                    layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                });
    }

    class Adapter extends RecyclerView.Adapter<VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> n = items.get(pos);
            String title   = n.get("title")   != null ? n.get("title").toString()   : "";
            String message = n.get("message") != null ? n.get("message").toString() : "";
            h.tvTitle.setText(title);
            h.tvMessage.setText(message);

            Object ts = n.get("createdAt");
            if (ts instanceof Timestamp) {
                Date d = ((Timestamp) ts).toDate();
                h.tvTime.setText(SDF.format(d));
            } else {
                h.tvTime.setText("");
            }

            boolean unread = Boolean.FALSE.equals(n.get("isRead"));
            h.dotUnread.setVisibility(unread ? View.VISIBLE : View.GONE);
        }

        @Override public int getItemCount() { return items.size(); }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMessage, tvTime;
        View dotUnread;
        VH(View v) {
            super(v);
            tvTitle   = v.findViewById(R.id.tv_notif_title);
            tvMessage = v.findViewById(R.id.tv_notif_message);
            tvTime    = v.findViewById(R.id.tv_notif_time);
            dotUnread = v.findViewById(R.id.dot_unread);
        }
    }
}
