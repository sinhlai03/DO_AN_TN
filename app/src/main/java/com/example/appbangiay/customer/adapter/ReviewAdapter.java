package com.example.appbangiay.customer.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.model.Review;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.VH> {

    private final List<Review> reviews;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public ReviewAdapter(List<Review> reviews) {
        this.reviews = reviews;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_review, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Review r = reviews.get(pos);

        // Avatar letter — guard against null or empty userName
        String name = r.getUserName();
        if (name == null || name.isEmpty()) name = "?";
        h.tvAvatar.setText(name.substring(0, 1).toUpperCase());

        h.tvName.setText(name);

        // Stars
        h.llStars.removeAllViews();
        for (int i = 1; i <= 5; i++) {
            TextView star = new TextView(h.itemView.getContext());
            star.setText("★");
            star.setTextSize(12);
            star.setTextColor(i <= r.getRating() ? Color.parseColor("#F59E0B") : Color.parseColor("#DDD"));
            h.llStars.addView(star);
        }

        // Time
        if (r.getCreatedAt() != null) {
            try {
                h.tvTime.setText(fmt.format(r.getCreatedAt().toDate()));
            } catch (Exception e) {
                h.tvTime.setText("");
            }
        } else {
            h.tvTime.setText("");
        }

        // Comment
        if (r.getComment() != null && !r.getComment().isEmpty()) {
            h.tvComment.setVisibility(View.VISIBLE);
            h.tvComment.setText(r.getComment());
        } else {
            h.tvComment.setVisibility(View.GONE);
        }

        // Image
        if (r.getImageBase64() != null && !r.getImageBase64().isEmpty()) {
            h.imgPhoto.setVisibility(View.VISIBLE);
            try {
                byte[] b = Base64.decode(r.getImageBase64(), Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
                h.imgPhoto.setImageBitmap(bmp);
            } catch (Exception ignored) {
                h.imgPhoto.setVisibility(View.GONE);
            }
        } else {
            h.imgPhoto.setVisibility(View.GONE);
        }
    }

    @Override public int getItemCount() { return reviews.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvName, tvTime, tvComment;
        LinearLayout llStars;
        ImageView imgPhoto;

        VH(View v) {
            super(v);
            tvAvatar  = v.findViewById(R.id.tv_review_avatar);
            tvName    = v.findViewById(R.id.tv_review_name);
            tvTime    = v.findViewById(R.id.tv_review_time);
            tvComment = v.findViewById(R.id.tv_review_comment);
            llStars   = v.findViewById(R.id.ll_review_stars);
            imgPhoto  = v.findViewById(R.id.img_review_photo);
        }
    }
}
