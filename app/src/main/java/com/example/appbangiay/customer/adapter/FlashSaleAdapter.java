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
import java.util.Random;

public class FlashSaleAdapter extends RecyclerView.Adapter<FlashSaleAdapter.VH> {

    public interface OnItemClick { void onClick(Product product); }

    private final List<Product> items;
    private final OnItemClick listener;
    private final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private final Random random = new Random();

    public FlashSaleAdapter(List<Product> items, OnItemClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_flash_sale, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Product p = items.get(pos);

        h.tvName.setText(p.getName());
        h.tvDiscount.setText("-" + p.getDiscountPercent() + "%");
        h.tvSalePrice.setText(fmt.format(p.getSalePrice()) + " đ");
        h.tvOriginalPrice.setText(fmt.format(p.getPrice()) + " đ");
        h.tvOriginalPrice.setPaintFlags(h.tvOriginalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

        // Image
        String img = p.getImageUrl();
        if (img != null && !img.isEmpty()) {
            try {
                byte[] b = Base64.decode(img, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
                h.imgProduct.setImageBitmap(bmp);
            } catch (Exception ignored) {}
        }

        // Simulate sold progress (based on stock)
        int maxStock = 100;
        int sold = maxStock - Math.min(p.getStock(), maxStock);
        float soldPercent = Math.max(0.2f, (float) sold / maxStock);
        h.tvSold.setText("Đã bán " + sold);

        // Set progress width
        h.viewProgress.post(() -> {
            int parentWidth = ((View) h.viewProgress.getParent()).getWidth();
            ViewGroup.LayoutParams lp = h.viewProgress.getLayoutParams();
            lp.width = (int) (parentWidth * soldPercent);
            h.viewProgress.setLayoutParams(lp);
        });

        h.itemView.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(v.getContext(),
                    com.example.appbangiay.customer.ProductDetailActivity.class);
            intent.putExtra("product_id", p.getId());
            v.getContext().startActivity(intent);
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgProduct;
        TextView tvName, tvDiscount, tvSalePrice, tvOriginalPrice, tvSold;
        View viewProgress;

        VH(View v) {
            super(v);
            imgProduct      = v.findViewById(R.id.img_flash_product);
            tvName           = v.findViewById(R.id.tv_flash_name);
            tvDiscount       = v.findViewById(R.id.tv_flash_discount);
            tvSalePrice      = v.findViewById(R.id.tv_flash_sale_price);
            tvOriginalPrice  = v.findViewById(R.id.tv_flash_original_price);
            tvSold           = v.findViewById(R.id.tv_flash_sold);
            viewProgress     = v.findViewById(R.id.view_flash_progress);
        }
    }
}
