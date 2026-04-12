package com.example.appbangiay.admin.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.admin.CustomerDetailActivity;
import com.example.appbangiay.model.User;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.ViewHolder> {

    private final List<User> customers;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public CustomerAdapter(List<User> customers) {
        this.customers = customers;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_customer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User u = customers.get(position);
        holder.tvName.setText(u.getFullname() != null ? u.getFullname() : "N/A");
        holder.tvEmail.setText(u.getEmail() != null ? u.getEmail() : "N/A");
        boolean hasPhone = u.getPhone() != null && !u.getPhone().trim().isEmpty();
        holder.tvPhone.setText(hasPhone ? u.getPhone() : "Chưa có số điện thoại");

        String initials = u.getFullname() != null && !u.getFullname().isEmpty()
                ? String.valueOf(u.getFullname().charAt(0)).toUpperCase()
                : "?";
        holder.tvAvatar.setText(initials);

        boolean isNew = isNewCustomer(u);
        holder.tvBadge.setText(isNew ? "Khách mới" : (hasPhone ? "Đủ liên hệ" : "Thiếu SĐT"));
        holder.tvBadge.setTextColor(holder.itemView.getContext().getColor(
                isNew ? R.color.blue_primary : (hasPhone ? R.color.text_primary : R.color.text_hint)));

        if (u.getCreatedAt() != null) {
            holder.tvJoined.setText("Tham gia từ " + SDF.format(u.getCreatedAt().toDate()));
        } else {
            holder.tvJoined.setText("Chưa có ngày tham gia");
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), CustomerDetailActivity.class);
            intent.putExtra("uid", u.getUid());
            intent.putExtra("name", u.getFullname());
            intent.putExtra("email", u.getEmail());
            intent.putExtra("phone", u.getPhone());
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() { return customers.size(); }

    private boolean isNewCustomer(User user) {
        if (user.getCreatedAt() == null) return false;
        Calendar now = Calendar.getInstance();
        Calendar joined = Calendar.getInstance();
        joined.setTime(user.getCreatedAt().toDate());
        return now.get(Calendar.YEAR) == joined.get(Calendar.YEAR)
                && now.get(Calendar.MONTH) == joined.get(Calendar.MONTH);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvName, tvEmail, tvPhone, tvJoined, tvBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar  = itemView.findViewById(R.id.tv_customer_avatar);
            tvName    = itemView.findViewById(R.id.tv_customer_name);
            tvEmail   = itemView.findViewById(R.id.tv_customer_email);
            tvPhone   = itemView.findViewById(R.id.tv_customer_phone);
            tvJoined  = itemView.findViewById(R.id.tv_customer_joined);
            tvBadge   = itemView.findViewById(R.id.tv_customer_badge);
        }
    }
}

