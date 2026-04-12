package com.example.appbangiay.admin;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.admin.banner.AddEditBannerActivity;
import com.example.appbangiay.model.Banner;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class BannerFragment extends Fragment {

    private final List<Banner> banners = new ArrayList<>();
    private BannerListAdapter adapter;
    private FirebaseFirestore db;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_banner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        db = FirebaseFirestore.getInstance();

        RecyclerView rv = view.findViewById(R.id.rv_banners);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new BannerListAdapter(banners, this);
        rv.setAdapter(adapter);

        view.findViewById(R.id.btn_add_banner).setOnClickListener(v ->
                startActivity(new Intent(getContext(), AddEditBannerActivity.class)));

        loadBanners();
    }

    @Override public void onResume() { super.onResume(); loadBanners(); }

    private void loadBanners() {
        db.collection("banners").get().addOnSuccessListener(snap -> {
            banners.clear();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                Banner b = doc.toObject(Banner.class);
                if (b != null) { b.setId(doc.getId()); banners.add(b); }
            }
            adapter.notifyDataSetChanged();
        });
    }

    void deleteBanner(String id) {
        db.collection("banners").document(id).delete()
                .addOnSuccessListener(v -> { Toast.makeText(getContext(), "Đã xóa", Toast.LENGTH_SHORT).show(); loadBanners(); });
    }

    // ---- Inner adapter ----
    static class BannerListAdapter extends RecyclerView.Adapter<BannerListAdapter.VH> {

        private final List<Banner> list;
        private final BannerFragment fragment;

        BannerListAdapter(List<Banner> list, BannerFragment fragment) {
            this.list = list;
            this.fragment = fragment;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_banner_admin, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Banner b = list.get(pos);

            String title = (b.getTitle() == null || b.getTitle().isEmpty()) ? "(Không có tiêu đề)" : b.getTitle();
            h.tvTitle.setText(title);
            h.tvStatus.setText(b.isActive() ? "● Đang hiển thị" : "● Ẩn");
            h.tvStatus.setTextColor(b.isActive() ? 0xFF10B981 : 0xFF9CA3AF);

            boolean isVideo = "video".equals(b.getType());

            if (isVideo && b.getVideoUrl() != null && !b.getVideoUrl().isEmpty()) {
                // Show video shell — actual play deferred to onViewAttachedToWindow
                h.imgThumb.setVisibility(View.GONE);
                h.videoThumb.setVisibility(View.VISIBLE);
                h.iconPlay.setVisibility(View.VISIBLE);
                h.tvVideoBadge.setVisibility(View.VISIBLE);
                h.videoThumb.setTag(b.getVideoUrl()); // store URL for lazy load
            } else {
                // Show image banner
                h.imgThumb.setVisibility(View.VISIBLE);
                h.videoThumb.setVisibility(View.GONE);
                h.iconPlay.setVisibility(View.GONE);
                h.tvVideoBadge.setVisibility(View.GONE);
                h.videoThumb.setTag(null);

                if (b.getImageBase64() != null && !b.getImageBase64().isEmpty()) {
                    try {
                        byte[] bytes = Base64.decode(b.getImageBase64(), Base64.DEFAULT);
                        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        h.imgThumb.setImageBitmap(bmp);
                    } catch (Exception ignored) {}
                }
            }

            h.btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), AddEditBannerActivity.class);
                intent.putExtra("bannerId", b.getId());
                v.getContext().startActivity(intent);
            });

            h.btnDelete.setOnClickListener(v ->
                    new android.app.AlertDialog.Builder(v.getContext())
                            .setTitle("Xóa banner?")
                            .setPositiveButton("Xóa", (d, w) -> fragment.deleteBanner(b.getId()))
                            .setNegativeButton("Hủy", null)
                            .show());
        }

        @Override
        public void onViewAttachedToWindow(@NonNull VH h) {
            super.onViewAttachedToWindow(h);
            // Start video only when it becomes visible
            Object tag = h.videoThumb.getTag();
            if (tag instanceof String) {
                String url = (String) tag;
                try {
                    h.videoThumb.setVideoURI(android.net.Uri.parse(url));
                    h.videoThumb.setOnPreparedListener(mp -> {
                        mp.setLooping(true);
                        mp.setVolume(0, 0);
                        try { mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(2f)); } catch (Exception ignored) {}
                        mp.start();
                        h.iconPlay.setVisibility(View.GONE); // hide play icon when playing
                    });
                    h.videoThumb.start();
                } catch (Exception ignored) {}
            }
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull VH h) {
            super.onViewDetachedFromWindow(h);
            // Stop video when scrolled off screen to free resources
            try {
                if (h.videoThumb.isPlaying()) h.videoThumb.stopPlayback();
            } catch (Exception ignored) {}
            if (h.iconPlay.getVisibility() == View.GONE && h.videoThumb.getTag() != null) {
                h.iconPlay.setVisibility(View.VISIBLE); // restore play icon
            }
        }

        @Override
        public void onViewRecycled(@NonNull VH h) {
            super.onViewRecycled(h);
            try { h.videoThumb.stopPlayback(); } catch (Exception ignored) {}
            h.videoThumb.setTag(null);
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView imgThumb, btnEdit, btnDelete, iconPlay;
            com.example.appbangiay.widget.FixedVideoView videoThumb;
            TextView tvTitle, tvStatus, tvVideoBadge;
            VH(@NonNull View v) {
                super(v);
                imgThumb    = v.findViewById(R.id.img_banner_thumb);
                videoThumb  = v.findViewById(R.id.video_banner_thumb);
                iconPlay    = v.findViewById(R.id.icon_video_play);
                tvVideoBadge = v.findViewById(R.id.tv_video_badge);
                tvTitle     = v.findViewById(R.id.tv_banner_item_title);
                tvStatus    = v.findViewById(R.id.tv_banner_status);
                btnEdit     = v.findViewById(R.id.btn_edit_banner);
                btnDelete   = v.findViewById(R.id.btn_delete_banner);
            }
        }
    }
}
