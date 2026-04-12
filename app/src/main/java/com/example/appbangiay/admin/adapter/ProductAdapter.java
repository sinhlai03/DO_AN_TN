package com.example.appbangiay.admin.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.model.Product;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    public interface OnProductAction {
        void onAction(Product product);
    }

    private final List<Product> products;
    private final OnProductAction onEdit;
    private final OnProductAction onDelete;
    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    public ProductAdapter(List<Product> products, OnProductAction onEdit, OnProductAction onDelete) {
        this.products = products;
        this.onEdit   = onEdit;
        this.onDelete = onDelete;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product p = products.get(position);

        holder.tvName.setText(p.getName());
        holder.tvCategory.setText(p.getCategory());
        holder.tvPrice.setText(currencyFormat.format(p.getPrice()) + " đ");
        holder.tvStock.setText("Kho: " + p.getStock());

        // Decode base64 image
        String img = p.getImageUrl();
        if (img != null && !img.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(img, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                holder.imgProduct.setImageBitmap(bmp);
            } catch (Exception e) {
                holder.imgProduct.setImageResource(android.R.color.darker_gray);
            }
        } else {
            holder.imgProduct.setImageResource(android.R.color.darker_gray);
        }

        holder.btnEdit.setOnClickListener(v -> onEdit.onAction(p));
        holder.btnDelete.setOnClickListener(v -> onDelete.onAction(p));
    }

    @Override
    public int getItemCount() { return products.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProduct;
        TextView tvName, tvCategory, tvPrice, tvStock;
        ImageButton btnEdit, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct  = itemView.findViewById(R.id.img_product);
            tvName      = itemView.findViewById(R.id.tv_product_name);
            tvCategory  = itemView.findViewById(R.id.tv_product_category);
            tvPrice     = itemView.findViewById(R.id.tv_product_price);
            tvStock     = itemView.findViewById(R.id.tv_product_stock);
            btnEdit     = itemView.findViewById(R.id.btn_edit_product);
            btnDelete   = itemView.findViewById(R.id.btn_delete_product);
        }
    }
}
