package com.example.appbangiay.customer.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.appbangiay.widget.FillVideoView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.model.Banner;

import java.util.List;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.VH> {

    private final List<Banner> banners;

    public BannerAdapter(List<Banner> banners) {
        this.banners = banners;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_banner, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Banner b = banners.get(position);

        // Title + overlay: chỉ hiện khi admin nhập text
        boolean hasTitle = b.getTitle() != null && !b.getTitle().trim().isEmpty();
        if (hasTitle) {
            holder.tvTitle.setText(b.getTitle());
            holder.tvTitle.setVisibility(View.VISIBLE);
            holder.overlayBottom.setVisibility(View.VISIBLE);
        } else {
            holder.tvTitle.setVisibility(View.GONE);
            holder.overlayBottom.setVisibility(View.GONE);
        }

        if ("video".equals(b.getType()) && b.getVideoUrl() != null && !b.getVideoUrl().isEmpty()) {
            // Video banner
            holder.imgBanner.setVisibility(View.GONE);
            holder.videoBanner.setVisibility(View.VISIBLE);

            Uri videoUri = Uri.parse(b.getVideoUrl());
            holder.videoBanner.setVideoURI(videoUri);
            holder.videoBanner.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                mp.setVolume(0f, 0f);
                // Fill banner area (centerCrop equivalent)
                mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                try {
                    mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(2.0f));
                } catch (Exception ignored) {}
                mp.start();
            });
            holder.videoBanner.start();
        } else {
            // Image banner
            holder.videoBanner.setVisibility(View.GONE);
            holder.imgBanner.setVisibility(View.VISIBLE);

            if (b.getImageBase64() != null && !b.getImageBase64().isEmpty()) {
                try {
                    byte[] bytes = Base64.decode(b.getImageBase64(), Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    holder.imgBanner.setImageBitmap(bmp);
                } catch (Exception ignored) {}
            }
        }
    }

    @Override public int getItemCount() { return banners.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgBanner;
        FillVideoView videoBanner;
        TextView tvTitle;
        View overlayBottom;
        VH(@NonNull View v) {
            super(v);
            imgBanner     = v.findViewById(R.id.img_banner);
            videoBanner   = v.findViewById(R.id.video_banner);
            tvTitle       = v.findViewById(R.id.tv_banner_title);
            overlayBottom = v.findViewById(R.id.overlay_bottom);
        }
    }
}
