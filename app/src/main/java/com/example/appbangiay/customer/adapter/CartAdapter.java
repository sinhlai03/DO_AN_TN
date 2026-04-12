package com.example.appbangiay.customer.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.model.CartItem;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.VH> {

    public interface OnCartChange {
        void onQuantityChanged(CartItem item);
        void onItemRemoved(int position, CartItem item);
    }

    private final List<CartItem> items;
    private final OnCartChange listener;
    private final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    public CartAdapter(List<CartItem> items, OnCartChange listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        CartItem item = items.get(position);
        h.tvName.setText(item.getProduct().getName());
        h.tvPrice.setText(fmt.format(item.getProduct().getPrice()) + " đ");
        h.tvQty.setText(String.valueOf(item.getQuantity()));

        // Size
        String size = item.getSelectedSize();
        if (size != null && !size.isEmpty()) {
            h.tvSize.setVisibility(View.VISIBLE);
            h.tvSize.setText("Size: " + size);
        } else {
            h.tvSize.setVisibility(View.GONE);
        }

        // Decode image
        String img = item.getProduct().getImageUrl();
        if (img != null && !img.isEmpty()) {
            try {
                byte[] b = Base64.decode(img, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
                h.imgProduct.setImageBitmap(bmp);
            } catch (Exception ignored) {}
        }

        h.btnIncrease.setOnClickListener(v -> {
            item.setQuantity(item.getQuantity() + 1);
            notifyItemChanged(h.getAdapterPosition());
            listener.onQuantityChanged(item);
        });

        h.btnDecrease.setOnClickListener(v -> {
            if (item.getQuantity() > 1) {
                item.setQuantity(item.getQuantity() - 1);
                notifyItemChanged(h.getAdapterPosition());
                listener.onQuantityChanged(item);
            }
        });

        h.btnRemove.setOnClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos >= 0 && pos < items.size()) {
                CartItem removed = items.remove(pos);
                notifyItemRemoved(pos);
                listener.onItemRemoved(pos, removed);
            }
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgProduct;
        TextView tvName, tvPrice, tvQty, tvSize, btnIncrease, btnDecrease, btnRemove;

        VH(@NonNull View v) {
            super(v);
            imgProduct  = v.findViewById(R.id.img_cart_product);
            tvName      = v.findViewById(R.id.tv_cart_name);
            tvPrice     = v.findViewById(R.id.tv_cart_price);
            tvSize      = v.findViewById(R.id.tv_cart_size);
            tvQty       = v.findViewById(R.id.tv_quantity);
            btnIncrease = v.findViewById(R.id.btn_increase);
            btnDecrease = v.findViewById(R.id.btn_decrease);
            btnRemove   = v.findViewById(R.id.btn_remove);
        }
    }
}
