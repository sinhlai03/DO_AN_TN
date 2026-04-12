package com.example.appbangiay.admin.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.model.Order;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.ViewHolder> {

    public interface OnStatusUpdateListener {
        void onUpdate(Order order, String newStatus);
    }

    private final List<Order> orders;
    private final OnStatusUpdateListener listener;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public OrderAdapter(List<Order> orders, OnStatusUpdateListener listener) {
        this.orders   = orders;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order o = orders.get(position);

        String orderId = o.getId() != null ? o.getId() : "";
        holder.tvOrderId.setText("#" + (orderId.length() >= 8 ? orderId.substring(0, 8).toUpperCase() : orderId.toUpperCase()));
        holder.tvCustomer.setText(o.getUserName() != null ? o.getUserName() : "Khách hàng");
        holder.tvAmount.setText(String.format("%,.0f đ", o.getTotalAmount()));
        holder.tvStatus.setText(getStatusLabel(o.getStatus()));
        holder.tvStatus.setBackgroundColor(getStatusColor(o.getStatus(), holder.itemView));

        if (o.getCreatedAtDate() != null) {
            holder.tvDate.setText(SDF.format(o.getCreatedAtDate()));
        }

        // Click item → mở chi tiết
        holder.itemView.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(
                    v.getContext(), com.example.appbangiay.admin.OrderDetailActivity.class);
            intent.putExtra("orderId", o.getId());
            v.getContext().startActivity(intent);
        });

        // Nút chuyển trạng thái nhanh
        holder.btnNextStatus.setOnClickListener(v -> {
            String next = getNextStatus(o.getStatus());
            if (next != null) listener.onUpdate(o, next);
        });

        String status = o.getStatus();
        boolean isTerminal = Order.STATUS_DONE.equals(status) || Order.STATUS_CANCELLED.equals(status);
        holder.btnNextStatus.setVisibility(isTerminal ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() { return orders.size(); }

    private String getStatusLabel(String status) {
        if (status == null) return "N/A";
        switch (status) {
            case Order.STATUS_PENDING:   return "Chờ xác nhận";
            case Order.STATUS_CONFIRMED: return "Đã xác nhận";
            case Order.STATUS_SHIPPING:  return "Đang giao";
            case Order.STATUS_DONE:      return "Hoàn thành";
            case Order.STATUS_CANCELLED: return "Đã huỷ";
            default: return status;
        }
    }

    private int getStatusColor(String status, View view) {
        if (status == null) return 0xFFCCCCCC;
        switch (status) {
            case Order.STATUS_PENDING:   return 0xFFFFF3E0;
            case Order.STATUS_CONFIRMED: return 0xFFE3F2FD;
            case Order.STATUS_SHIPPING:  return 0xFFE8F5E9;
            case Order.STATUS_DONE:      return 0xFFE8F5E9;
            case Order.STATUS_CANCELLED: return 0xFFFFEBEE;
            default: return 0xFFF5F5F5;
        }
    }

    private String getNextStatus(String current) {
        if (current == null) return Order.STATUS_CONFIRMED;
        switch (current) {
            case Order.STATUS_PENDING:   return Order.STATUS_CONFIRMED;
            case Order.STATUS_CONFIRMED: return Order.STATUS_SHIPPING;
            case Order.STATUS_SHIPPING:  return Order.STATUS_DONE;
            default: return null;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvCustomer, tvAmount, tvStatus, tvDate;
        MaterialButton btnNextStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId      = itemView.findViewById(R.id.tv_order_id);
            tvCustomer     = itemView.findViewById(R.id.tv_order_customer);
            tvAmount       = itemView.findViewById(R.id.tv_order_amount);
            tvStatus       = itemView.findViewById(R.id.tv_order_status);
            tvDate         = itemView.findViewById(R.id.tv_order_date);
            btnNextStatus  = itemView.findViewById(R.id.btn_next_status);
        }
    }
}
