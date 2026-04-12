package com.example.appbangiay;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText edtFullname, edtEmail, edtPhone, edtPassword, edtConfirmPassword;
    private ImageButton btnTogglePassword, btnToggleConfirmPassword;
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.register_scroll), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        initViews();
        setupListeners();
    }

    private void initViews() {
        edtFullname = findViewById(R.id.edt_fullname);
        edtEmail = findViewById(R.id.edt_email);
        edtPhone = findViewById(R.id.edt_phone);
        edtPassword = findViewById(R.id.edt_password);
        edtConfirmPassword = findViewById(R.id.edt_confirm_password);
        btnTogglePassword = findViewById(R.id.btn_toggle_password);
        btnToggleConfirmPassword = findViewById(R.id.btn_toggle_confirm_password);
    }

    private void setupListeners() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.tv_go_login).setOnClickListener(v -> finish());
        findViewById(R.id.btn_register).setOnClickListener(v -> registerWithEmail());

        btnTogglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            toggleVisibility(edtPassword, btnTogglePassword, isPasswordVisible);
        });

        btnToggleConfirmPassword.setOnClickListener(v -> {
            isConfirmPasswordVisible = !isConfirmPasswordVisible;
            toggleVisibility(edtConfirmPassword, btnToggleConfirmPassword, isConfirmPasswordVisible);
        });
    }

    // ==================== REGISTER ====================

    private void registerWithEmail() {
        String fullname = edtFullname.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String phone = edtPhone.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();
        String confirmPassword = edtConfirmPassword.getText().toString().trim();

        if (!validateInput(fullname, email, phone, password, confirmPassword)) return;

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        var user = firebaseAuth.getCurrentUser();
                        if (user != null) {
                            // Update display name
                            UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(fullname)
                                    .build();
                            user.updateProfile(profileUpdate);

                            // Save user info to Firestore
                            saveUserToFirestore(user.getUid(), fullname, email, phone);
                        }

                        Toast.makeText(this, "Đăng ký thành công! Vui lòng đăng nhập.",
                                Toast.LENGTH_SHORT).show();
                        firebaseAuth.signOut();
                        finish();
                    } else {
                        String errorMessage = task.getException() != null
                                ? task.getException().getMessage()
                                : "Đăng ký thất bại";
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(String uid, String fullname, String email, String phone) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("fullname", fullname);
        userData.put("email", email);
        userData.put("phone", phone);
        userData.put("role", "customer");
        userData.put("createdAt", com.google.firebase.Timestamp.now());

        firestore.collection("users").document(uid)
                .set(userData)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lưu thông tin thất bại", Toast.LENGTH_SHORT).show()
                );
    }

    // ==================== VALIDATION ====================

    private boolean validateInput(String fullname, String email, String phone,
                                  String password, String confirmPassword) {
        if (TextUtils.isEmpty(fullname)) {
            edtFullname.setError("Vui lòng nhập họ tên");
            edtFullname.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(email)) {
            edtEmail.setError("Vui lòng nhập email");
            edtEmail.requestFocus();
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("Email không hợp lệ");
            edtEmail.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(phone)) {
            edtPhone.setError("Vui lòng nhập số điện thoại");
            edtPhone.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            edtPassword.setError("Vui lòng nhập mật khẩu");
            edtPassword.requestFocus();
            return false;
        }
        if (password.length() < 6) {
            edtPassword.setError("Mật khẩu tối thiểu 6 ký tự");
            edtPassword.requestFocus();
            return false;
        }
        if (!password.equals(confirmPassword)) {
            edtConfirmPassword.setError("Mật khẩu không khớp");
            edtConfirmPassword.requestFocus();
            return false;
        }
        return true;
    }

    // ==================== UTILITIES ====================

    private void toggleVisibility(EditText editText, ImageButton toggleBtn, boolean visible) {
        if (visible) {
            editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            toggleBtn.setImageResource(R.drawable.ic_visibility);
        } else {
            editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            toggleBtn.setImageResource(R.drawable.ic_visibility_off);
        }
        editText.setSelection(editText.getText().length());
    }
}
