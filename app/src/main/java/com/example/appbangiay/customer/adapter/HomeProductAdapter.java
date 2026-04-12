package com.example.appbangiay.customer.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.model.Product;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class HomeProductAdapter extends RecyclerView.Adapter<HomeProductAdapter.ViewHolder> {

    public interface OnProductClick { void onClick(Product product); }

    private final List<Product> products;
    private final OnProductClick listener;
    private final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    public HomeProductAdapter(List<Product> products, OnProductClick listener) {
        this.products = products;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Product p = products.get(position);
        h.tvName.setText(p.getName());
        h.tvCategory.setText(p.getCategory() != null ? p.getCategory() : "");

        int discount = p.getDiscountPercent();
        if (discount > 0) {
            // Show sale
            h.tvSaleBadge.setVisibility(View.VISIBLE);
            h.tvSaleBadge.setText("SALE " + discount + "%");

            h.tvPrice.setText(fmt.format(p.getSalePrice()) + " đ");
            h.tvOriginalPrice.setVisibility(View.VISIBLE);
            h.tvOriginalPrice.setText(fmt.format(p.getPrice()) + " đ");
            h.tvOriginalPrice.setPaintFlags(h.tvOriginalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            // No sale
            h.tvSaleBadge.setVisibility(View.GONE);
            h.tvPrice.setText(fmt.format(p.getPrice()) + " đ");
            h.tvOriginalPrice.setVisibility(View.GONE);
        }

        // Decode base64 image
        String img = p.getImageUrl();
        if (img != null && !img.isEmpty()) {
            try {
                byte[] b = Base64.decode(img, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
                h.imgProduct.setImageBitmap(bmp);
            } catch (Exception e) {
                h.imgProduct.setImageResource(android.R.color.darker_gray);
            }
        } else {
            h.imgProduct.setImageResource(android.R.color.darker_gray);
        }

        h.itemView.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(v.getContext(),
                    com.example.appbangiay.customer.ProductDetailActivity.class);
            intent.putExtra("product_id", p.getId());
            v.getContext().startActivity(intent);
        });
        h.btnAddCart.setOnClickListener(v -> listener.onClick(p));
    }

    @Override
    public int getItemCount() { return products.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProduct, btnAddCart;
        TextView tvName, tvCategory, tvPrice, tvSaleBadge, tvOriginalPrice;

        ViewHolder(@NonNull View v) {
            super(v);
            imgProduct      = v.findViewById(R.id.img_product_card);
            tvName           = v.findViewById(R.id.tv_card_name);
            tvCategory       = v.findViewById(R.id.tv_card_category);
            tvPrice          = v.findViewById(R.id.tv_card_price);
            tvSaleBadge      = v.findViewById(R.id.tv_sale_badge);
            tvOriginalPrice  = v.findViewById(R.id.tv_card_original_price);
            btnAddCart       = v.findViewById(R.id.btn_add_cart);
        }
    }
}
