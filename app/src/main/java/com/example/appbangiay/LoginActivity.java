package com.example.appbangiay;

import android.widget.CheckBox;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText edtEmail, edtPassword;
    private ImageButton btnTogglePassword;
    private CheckBox cbRemember;
    private boolean isPasswordVisible = false;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore db;

    // Legacy Google Sign-In fallback
    private GoogleSignInClient googleSignInClient;
    private final ActivityResultLauncher<Intent> legacyGoogleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null && account.getIdToken() != null) {
                            firebaseAuthWithGoogle(account.getIdToken());
                        }
                    } catch (ApiException e) {
                        Log.e(TAG, "Legacy Google Sign-In failed", e);
                        Toast.makeText(this, "Đăng nhập Google thất bại: " + e.getStatusCode(),
                                Toast.LENGTH_LONG).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_scroll), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        firebaseAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Setup legacy Google Sign-In as fallback
        String webClientId = getString(R.string.default_web_client_id);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        initViews();
        setupListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // If user unchecked remember me, sign out to force re-login
        boolean rememberMe = getSharedPreferences("prefs", MODE_PRIVATE)
                .getBoolean("remember_me", true);
        if (!rememberMe) {
            firebaseAuth.signOut();
            return;
        }
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            checkRoleAndNavigate(currentUser.getUid());
        }
    }

    private void initViews() {
        edtEmail = findViewById(R.id.edt_email);
        edtPassword = findViewById(R.id.edt_password);
        btnTogglePassword = findViewById(R.id.btn_toggle_password);
        cbRemember = findViewById(R.id.cb_remember);
        cbRemember.setChecked(true); // Default: remember login
    }

    private void setupListeners() {
        btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility());

        findViewById(R.id.tv_forgot_password).setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));

        findViewById(R.id.tv_sign_up).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        findViewById(R.id.btn_login).setOnClickListener(v -> loginWithEmail());
        findViewById(R.id.btn_google).setOnClickListener(v -> loginWithGoogle());
    }

    // ==================== EMAIL/PASSWORD LOGIN ====================

    private void loginWithEmail() {
        String email    = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (!validateInput(email, password)) return;

        // Save remember me preference
        getSharedPreferences("prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("remember_me", cbRemember.isChecked())
                .apply();

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null) checkRoleAndNavigate(user.getUid());
                    } else {
                        String msg = task.getException() != null
                                ? task.getException().getMessage() : "Đăng nhập thất bại";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private boolean validateInput(String email, String password) {
        if (TextUtils.isEmpty(email)) {
            edtEmail.setError("Vui lòng nhập email"); edtEmail.requestFocus(); return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("Email không hợp lệ"); edtEmail.requestFocus(); return false;
        }
        if (TextUtils.isEmpty(password)) {
            edtPassword.setError("Vui lòng nhập mật khẩu"); edtPassword.requestFocus(); return false;
        }
        if (password.length() < 6) {
            edtPassword.setError("Mật khẩu tối thiểu 6 ký tự"); edtPassword.requestFocus(); return false;
        }
        return true;
    }

    // ==================== GOOGLE SIGN-IN ====================

    private void loginWithGoogle() {
        // Try Credential Manager first, fallback to legacy if it fails
        try {
            loginWithCredentialManager();
        } catch (Exception e) {
            Log.w(TAG, "Credential Manager not available, using legacy", e);
            loginWithLegacyGoogle();
        }
    }

    private void loginWithCredentialManager() {
        String webClientId = getString(R.string.default_web_client_id);

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        CredentialManager credentialManager = CredentialManager.create(this);
        credentialManager.getCredentialAsync(this, request, null,
                Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        runOnUiThread(() -> handleGoogleSignInResult(result));
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        Log.w(TAG, "Credential Manager failed, trying legacy: " + e.getMessage());
                        // Fallback to legacy Google Sign-In
                        runOnUiThread(() -> loginWithLegacyGoogle());
                    }
                });
    }

    private void loginWithLegacyGoogle() {
        Log.d(TAG, "Using legacy Google Sign-In");
        // Sign out first to always show account picker
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            legacyGoogleLauncher.launch(signInIntent);
        });
    }

    private void handleGoogleSignInResult(GetCredentialResponse response) {
        Credential credential = response.getCredential();
        if (!(credential instanceof CustomCredential)) return;

        CustomCredential custom = (CustomCredential) credential;
        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(custom.getType())) return;

        GoogleIdTokenCredential googleCred = GoogleIdTokenCredential.createFrom(custom.getData());
        firebaseAuthWithGoogle(googleCred.getIdToken());
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(idToken, null);

        firebaseAuth.signInWithCredential(firebaseCredential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null) checkRoleAndNavigate(user.getUid());
                    } else {
                        Toast.makeText(this, "Xác thực Firebase thất bại", Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ==================== ROLE-BASED ROUTING ====================

    private void checkRoleAndNavigate(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String role = doc.getString("role");
                        if ("admin".equals(role)) {
                            navigateTo(AdminActivity.class);
                        } else {
                            navigateTo(MainActivity.class);
                        }
                    } else {
                        // Google user chưa có doc → tạo mới với role customer
                        createGoogleUserDoc(uid);
                        navigateTo(MainActivity.class);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check role", e);
                    navigateTo(MainActivity.class);
                });
    }

    private void createGoogleUserDoc(String uid) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("fullname",  user.getDisplayName() != null ? user.getDisplayName() : "");
        data.put("email",     user.getEmail() != null ? user.getEmail() : "");
        data.put("phone",     "");
        data.put("role",      "customer");
        data.put("createdAt", Timestamp.now());

        db.collection("users").document(uid).set(data)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create user doc", e));
    }

    // ==================== UTILITIES ====================

    private void navigateTo(Class<?> destination) {
        Intent intent = new Intent(this, destination);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            edtPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            btnTogglePassword.setImageResource(R.drawable.ic_visibility_off);
        } else {
            edtPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            btnTogglePassword.setImageResource(R.drawable.ic_visibility);
        }
        isPasswordVisible = !isPasswordVisible;
        edtPassword.setSelection(edtPassword.getText().length());
    }
}
