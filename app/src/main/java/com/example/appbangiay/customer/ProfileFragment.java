package com.example.appbangiay.customer;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.appbangiay.LoginActivity;
import com.example.appbangiay.R;
import com.example.appbangiay.chat.ChatActivity;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            TextView tvEmail = view.findViewById(R.id.tv_profile_email);
            TextView tvName  = view.findViewById(R.id.tv_profile_name);
            tvEmail.setText(user.getEmail());
            String name = user.getDisplayName();
            tvName.setText(name != null && !name.isEmpty() ? name : user.getEmail().split("@")[0]);
        }

        // Đơn hàng
        view.findViewById(R.id.btn_my_orders).setOnClickListener(v ->
                startActivity(new Intent(getContext(), MyOrdersActivity.class)));

        // Sản phẩm yêu thích
        view.findViewById(R.id.btn_favorites).setOnClickListener(v ->
                startActivity(new Intent(getContext(), FavoritesActivity.class)));

        // Ví mã giảm giá
        view.findViewById(R.id.btn_coupon_wallet).setOnClickListener(v ->
                startActivity(new Intent(getContext(), CouponWalletActivity.class)));

        // Đánh giá của tôi
        view.findViewById(R.id.btn_my_reviews).setOnClickListener(v ->
                startActivity(new Intent(getContext(), MyReviewsActivity.class)));

        // Sản phẩm đã xem
        view.findViewById(R.id.btn_view_history).setOnClickListener(v ->
                startActivity(new Intent(getContext(), ViewHistoryActivity.class)));

        // Thông tin tài khoản
        view.findViewById(R.id.btn_edit_profile).setOnClickListener(v ->
                startActivity(new Intent(getContext(), EditProfileActivity.class)));

        // Địa chỉ giao hàng
        view.findViewById(R.id.btn_address).setOnClickListener(v ->
                startActivity(new Intent(getContext(), AddressListActivity.class)));

        // Đổi mật khẩu — chỉ cho tài khoản email/password
        View btnChangePass = view.findViewById(R.id.btn_change_password);
        boolean isPasswordUser = false;
        if (user != null) {
            for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
                if ("password".equals(info.getProviderId())) {
                    isPasswordUser = true;
                    break;
                }
            }
        }
        if (isPasswordUser) {
            btnChangePass.setOnClickListener(v -> showChangePasswordDialog());
        } else {
            btnChangePass.setAlpha(0.4f);
            btnChangePass.setOnClickListener(v ->
                    Toast.makeText(getContext(),
                            "Tài khoản Google không thể đổi mật khẩu tại đây",
                            Toast.LENGTH_SHORT).show());
        }

        // Liên hệ hỗ trợ → mở chat
        view.findViewById(R.id.btn_contact_support).setOnClickListener(v ->
                startActivity(new Intent(getContext(), ChatActivity.class)));

        // Về ứng dụng
        view.findViewById(R.id.btn_about).setOnClickListener(v -> showAboutDialog());

        // Đăng xuất
        view.findViewById(R.id.btn_logout_profile).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(getContext(), LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });

        // Version
        try {
            PackageInfo pInfo = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            ((TextView) view.findViewById(R.id.tv_app_version))
                    .setText("ShopGiay v" + pInfo.versionName);
        } catch (Exception ignored) {}
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() == null) return;
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;

        // Update name & email
        String name = u.getDisplayName();
        ((TextView) getView().findViewById(R.id.tv_profile_name))
                .setText(name != null && !name.isEmpty() ? name : u.getEmail().split("@")[0]);
        ((TextView) getView().findViewById(R.id.tv_profile_email)).setText(u.getEmail());

        // Load avatar from Firestore
        ImageView imgAvatar = getView().findViewById(R.id.img_profile_avatar);
        FirebaseFirestore.getInstance().collection("users").document(u.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded() || getView() == null) return;
                    if (doc.exists()) {
                        String avatar = doc.getString("avatar");
                        if (avatar != null && !avatar.isEmpty()) {
                            try {
                                byte[] bytes = Base64.decode(avatar, Base64.DEFAULT);
                                imgAvatar.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                                imgAvatar.setVisibility(android.view.View.VISIBLE);
                            } catch (Exception ignored) {
                                imgAvatar.setVisibility(android.view.View.GONE);
                            }
                        } else {
                            imgAvatar.setVisibility(android.view.View.GONE);
                        }
                    }
                });
    }

    private void showChangePasswordDialog() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        View dialogView = LayoutInflater.from(getContext())
                .inflate(android.R.layout.simple_list_item_1, null);

        // Build custom dialog with EditTexts
        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        EditText edtOld = new EditText(getContext());
        edtOld.setHint("Mật khẩu hiện tại");
        edtOld.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(edtOld);

        EditText edtNew = new EditText(getContext());
        edtNew.setHint("Mật khẩu mới (tối thiểu 6 ký tự)");
        edtNew.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(edtNew);

        EditText edtConfirm = new EditText(getContext());
        edtConfirm.setHint("Xác nhận mật khẩu mới");
        edtConfirm.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(edtConfirm);

        new AlertDialog.Builder(requireContext())
                .setTitle("Đổi mật khẩu")
                .setView(layout)
                .setPositiveButton("Đổi", (d, w) -> {
                    String oldPass = edtOld.getText().toString().trim();
                    String newPass = edtNew.getText().toString().trim();
                    String confirm = edtConfirm.getText().toString().trim();

                    if (oldPass.isEmpty() || newPass.isEmpty()) {
                        Toast.makeText(getContext(), "Vui lòng nhập đầy đủ", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newPass.length() < 6) {
                        Toast.makeText(getContext(), "Mật khẩu mới tối thiểu 6 ký tự", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPass.equals(confirm)) {
                        Toast.makeText(getContext(), "Mật khẩu xác nhận không khớp", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Re-authenticate then update
                    AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPass);
                    user.reauthenticate(credential).addOnSuccessListener(v -> {
                        user.updatePassword(newPass).addOnSuccessListener(v2 -> {
                            Toast.makeText(getContext(), "Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show();
                        }).addOnFailureListener(e -> {
                            Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }).addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Mật khẩu hiện tại không đúng", Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showAboutDialog() {
        String version = "1.0.0";
        try {
            PackageInfo pInfo = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            version = pInfo.versionName;
        } catch (Exception ignored) {}

        new AlertDialog.Builder(requireContext())
                .setTitle("ShopGiay")
                .setMessage("© 2026 ShopGiay")
                .setPositiveButton("Đóng", null)
                .show();
    }
}
