package com.example.appbangiay.customer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.appbangiay.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private ImageView imgAvatar;
    private EditText edtName, edtPhone;
    private TextView tvEmail;
    private String avatarBase64 = "";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) processImage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.blue_primary, getTheme()));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_edit_profile);

        imgAvatar = findViewById(R.id.img_avatar);
        edtName = findViewById(R.id.edt_name);
        edtPhone = findViewById(R.id.edt_phone);
        tvEmail = findViewById(R.id.tv_email);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_change_avatar).setOnClickListener(v -> pickImage());
        imgAvatar.setOnClickListener(v -> pickImage());
        findViewById(R.id.btn_save).setOnClickListener(v -> saveProfile());

        loadProfile();
    }

    private void loadProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        tvEmail.setText(user.getEmail());
        String name = user.getDisplayName();
        if (name != null && !name.isEmpty()) edtName.setText(name);

        // Load extra info from Firestore
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String phone = doc.getString("phone");
                        String avatar = doc.getString("avatar");

                        if (phone != null) edtPhone.setText(phone);
                        if (avatar != null && !avatar.isEmpty()) {
                            avatarBase64 = avatar;
                            displayAvatar(avatar);
                        }
                    }
                });
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        pickImageLauncher.launch(intent);
    }

    private void processImage(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap original = BitmapFactory.decodeStream(is);
            if (is != null) is.close();

            // Resize to max 256px
            int maxSize = 256;
            int w = original.getWidth(), h = original.getHeight();
            float scale = Math.min((float) maxSize / w, (float) maxSize / h);
            if (scale < 1) {
                w = Math.round(w * scale);
                h = Math.round(h * scale);
            }
            Bitmap resized = Bitmap.createScaledBitmap(original, w, h, true);

            // Convert to Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resized.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            avatarBase64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

            // Show preview
            imgAvatar.setPadding(0, 0, 0, 0);
            imgAvatar.setImageBitmap(resized);

        } catch (Exception e) {
            Toast.makeText(this, "Lỗi chọn ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveProfile() {
        String name = edtName.getText().toString().trim();
        String phone = edtPhone.getText().toString().trim();

        if (name.isEmpty()) {
            edtName.setError("Nhập họ và tên");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        // Update Firebase Auth displayName
        UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();
        user.updateProfile(profileUpdate);

        // Update Firestore
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("phone", phone);
        if (!avatarBase64.isEmpty()) {
            data.put("avatar", avatarBase64);
        }

        db.collection("users").document(user.getUid())
                .update(data)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Đã cập nhật thông tin", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    // Doc chưa tồn tại → set
                    data.put("email", user.getEmail());
                    data.put("role", "customer");
                    db.collection("users").document(user.getUid()).set(data)
                            .addOnSuccessListener(u -> {
                                Toast.makeText(this, "Đã cập nhật thông tin", Toast.LENGTH_SHORT).show();
                                setResult(RESULT_OK);
                                finish();
                            });
                });
    }

    private void displayAvatar(String base64) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            imgAvatar.setPadding(0, 0, 0, 0);
            imgAvatar.setImageBitmap(bmp);
        } catch (Exception ignored) {}
    }
}
