package com.example.appbangiay.admin.banner;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.appbangiay.R;
import com.example.appbangiay.util.CloudinaryUploader;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class AddEditBannerActivity extends AppCompatActivity {

    private static final int MAX_SIZE = 1000;

    private ImageView imgPreview;
    private TextView tvHint, tvVideoStatus;
    private EditText edtTitle;
    private RadioGroup rgType;
    private FirebaseFirestore db;
    private String bannerId;
    private boolean isEditMode = false;
    private String base64Image = "";
    private String videoUrl = "";
    private String bannerType = "image";

    // Image picker
    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            Bitmap bmp = resize(uri);
                            imgPreview.setImageBitmap(bmp);
                            tvHint.setVisibility(View.GONE);
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos);
                            base64Image = Base64.encodeToString(bos.toByteArray(), Base64.DEFAULT);
                        } catch (IOException e) {
                            Toast.makeText(this, "Lỗi đọc ảnh", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

    // Video picker
    private final ActivityResultLauncher<Intent> pickVideoLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) uploadVideo(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_banner);
        db = FirebaseFirestore.getInstance();

        imgPreview    = findViewById(R.id.img_banner_preview);
        tvHint        = findViewById(R.id.tv_pick_banner_hint);
        tvVideoStatus = findViewById(R.id.tv_video_status);
        edtTitle      = findViewById(R.id.edt_banner_title);
        rgType        = findViewById(R.id.rg_banner_type);

        bannerId   = getIntent().getStringExtra("bannerId");
        isEditMode = bannerId != null;

        // Type switch
        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_image) {
                bannerType = "image";
                tvHint.setText("Bấm để chọn ảnh");
                tvVideoStatus.setVisibility(View.GONE);
            } else {
                bannerType = "video";
                tvHint.setText("Bấm để chọn video");
                tvVideoStatus.setVisibility(videoUrl.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });

        // Pick media
        FrameLayout wrapper = findViewById(R.id.img_banner_wrapper);
        wrapper.setOnClickListener(v -> {
            if ("video".equals(bannerType)) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("video/*");
                pickVideoLauncher.launch(intent);
            } else {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                pickImageLauncher.launch(intent);
            }
        });

        // Load existing if edit
        if (isEditMode) {
            TextView tvHeader = findViewById(R.id.tv_banner_header);
            if (tvHeader != null) tvHeader.setText("Sửa banner");
            loadBanner();
        }

        // Back button
        View btnBack = findViewById(R.id.btn_back_banner);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Save
        findViewById(R.id.btn_save_banner).setOnClickListener(v -> saveBanner());
    }

    private void loadBanner() {
        db.collection("banners").document(bannerId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String title  = doc.getString("title");
                    String imgB64 = doc.getString("imageBase64");
                    String type   = doc.getString("type");
                    String vUrl   = doc.getString("videoUrl");

                    edtTitle.setText(title != null ? title : "");

                    if ("video".equals(type)) {
                        bannerType = "video";
                        rgType.check(R.id.rb_video);
                        if (vUrl != null) {
                            videoUrl = vUrl;
                            tvVideoStatus.setText("Video đã upload");
                            tvVideoStatus.setVisibility(View.VISIBLE);
                            tvHint.setVisibility(View.GONE);
                        }
                    } else {
                        rgType.check(R.id.rb_image);
                        if (imgB64 != null && !imgB64.isEmpty()) {
                            base64Image = imgB64;
                            byte[] b = Base64.decode(imgB64, Base64.DEFAULT);
                            Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
                            imgPreview.setImageBitmap(bmp);
                            tvHint.setVisibility(View.GONE);
                        }
                    }
                });
    }

    private void uploadVideo(Uri uri) {
        tvVideoStatus.setVisibility(View.VISIBLE);
        tvVideoStatus.setText("Đang tải video lên...");
        tvHint.setVisibility(View.GONE);
        imgPreview.setImageResource(R.drawable.ic_video);

        CloudinaryUploader.uploadVideo(getContentResolver(), uri, new CloudinaryUploader.UploadCallback() {
            @Override
            public void onSuccess(String url) {
                runOnUiThread(() -> {
                    videoUrl = url;
                    tvVideoStatus.setText("Upload thành công!");
                    Toast.makeText(AddEditBannerActivity.this, "Video đã upload", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    tvVideoStatus.setText("Upload thất bại: " + error);
                    Toast.makeText(AddEditBannerActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void saveBanner() {
        if ("image".equals(bannerType) && TextUtils.isEmpty(base64Image)) {
            Toast.makeText(this, "Chọn ảnh banner", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("video".equals(bannerType) && TextUtils.isEmpty(videoUrl)) {
            Toast.makeText(this, "Chọn và chờ upload video", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = edtTitle.getText().toString().trim();

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("active", true);
        data.put("type", bannerType);

        if ("video".equals(bannerType)) {
            data.put("videoUrl", videoUrl);
            data.put("imageBase64", ""); // clear image
        } else {
            data.put("imageBase64", base64Image);
            data.put("videoUrl", ""); // clear video
        }

        if (isEditMode) {
            db.collection("banners").document(bannerId).update(data)
                    .addOnSuccessListener(v -> { Toast.makeText(this, "Đã cập nhật", Toast.LENGTH_SHORT).show(); finish(); });
        } else {
            db.collection("banners").add(data)
                    .addOnSuccessListener(ref -> { Toast.makeText(this, "Đã thêm banner", Toast.LENGTH_SHORT).show(); finish(); });
        }
    }

    private Bitmap resize(Uri uri) throws IOException {
        InputStream is = getContentResolver().openInputStream(uri);
        Bitmap origin = BitmapFactory.decodeStream(is);
        if (is != null) is.close();
        int w = origin.getWidth(), h = origin.getHeight();
        if (w <= MAX_SIZE && h <= MAX_SIZE) return origin;
        float ratio = Math.min((float) MAX_SIZE / w, (float) MAX_SIZE / h);
        return Bitmap.createScaledBitmap(origin, (int)(w * ratio), (int)(h * ratio), true);
    }
}
