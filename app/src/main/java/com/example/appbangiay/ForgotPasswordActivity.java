package com.example.appbangiay;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText edtEmail;
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_forgot_password);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.forgot_scroll), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        firebaseAuth = FirebaseAuth.getInstance();

        initViews();
    }

    private void initViews() {
        edtEmail = findViewById(R.id.edt_forgot_email);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.tv_back_login).setOnClickListener(v -> finish());
        findViewById(R.id.btn_send_reset).setOnClickListener(v -> sendResetEmail());
    }

    private void sendResetEmail() {
        String email = edtEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            edtEmail.setError("Vui lòng nhập email");
            edtEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("Email không hợp lệ");
            edtEmail.requestFocus();
            return;
        }

        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Đã gửi email đặt lại mật khẩu. Vui lòng kiểm tra hộp thư!",
                                Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        String errorMessage = task.getException() != null
                                ? task.getException().getMessage()
                                : "Gửi email thất bại";
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
