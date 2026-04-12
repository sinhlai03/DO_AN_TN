package com.example.appbangiay.admin.product;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.appbangiay.R;
import com.example.appbangiay.model.Product;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddEditProductActivity extends AppCompatActivity {

    private static final int MAX_IMAGE_SIZE = 800; // px
    private static final int MAX_IMAGE_COUNT = 5;

    private static final String[] CATEGORIES = {"Sneaker", "Thể thao", "Sandal", "Công sở"};
    private static final String[] SIZE_VALUES = {"36","37","38","39","40","41","42","43","44","45","46"};
    private static final int[] SIZE_CHIP_IDS = {
        R.id.chip_size_36, R.id.chip_size_37, R.id.chip_size_38,
        R.id.chip_size_39, R.id.chip_size_40, R.id.chip_size_41,
        R.id.chip_size_42, R.id.chip_size_43, R.id.chip_size_44,
        R.id.chip_size_45, R.id.chip_size_46
    };

    private ImageView imgProduct;
    private EditText edtName, edtPrice, edtDescription, edtStock, edtDiscount;
    private TextView tvSaleEndTime, tvPickImageHint, tvImagePickStatus, tvSizeStockHint;
    private LinearLayout layoutImageThumbs, layoutSizeStockInputs;
    private Spinner spinnerCategory;
    private ChipGroup chipGroupSizes;
    private FirebaseFirestore db;
    private String productId;
    private boolean isEditMode = false;
    private final List<String> base64Images = new ArrayList<>();
    private final Map<String, EditText> sizeStockInputs = new HashMap<>();
    private final Map<String, Long> existingSizeStock = new HashMap<>();
    private com.google.firebase.Timestamp saleEndTimestamp = null;
    private final SimpleDateFormat dtFmt = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    private final ActivityResultLauncher<String[]> pickImagesLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris == null || uris.isEmpty()) return;

                List<String> newImages = new ArrayList<>();
                int limit = Math.min(uris.size(), MAX_IMAGE_COUNT);
                for (int i = 0; i < limit; i++) {
                    Uri uri = uris.get(i);
                    if (uri == null) continue;
                    try {
                        Bitmap bitmap = resizeBitmap(uri);
                        newImages.add(bitmapToBase64(bitmap));
                    } catch (IOException e) {
                        Toast.makeText(this, "Lỗi đọc ảnh", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                base64Images.clear();
                base64Images.addAll(newImages);
                updateImagePreviews();

                if (uris.size() > MAX_IMAGE_COUNT) {
                    Toast.makeText(this,
                            "Chỉ lưu tối đa " + MAX_IMAGE_COUNT + " ảnh đầu tiên",
                            Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_product);

        db = FirebaseFirestore.getInstance();
        productId = getIntent().getStringExtra("product_id");
        isEditMode = productId != null;

        initViews();
        setupToolbar();

        if (isEditMode) loadProduct();

        findViewById(R.id.img_product_preview_wrapper).setOnClickListener(v -> pickImages());
        imgProduct.setOnClickListener(v -> pickImages());
        findViewById(R.id.btn_save_product).setOnClickListener(v -> saveProduct());
    }

    private void initViews() {
        imgProduct      = findViewById(R.id.img_product_preview);
        edtName         = findViewById(R.id.edt_product_name);
        edtPrice        = findViewById(R.id.edt_product_price);
        edtDescription  = findViewById(R.id.edt_product_description);
        edtStock        = findViewById(R.id.edt_product_stock);
        edtDiscount     = findViewById(R.id.edt_product_discount);
        tvSaleEndTime   = findViewById(R.id.tv_sale_end_time);
        tvPickImageHint = findViewById(R.id.tv_pick_image_hint);
        tvImagePickStatus = findViewById(R.id.tv_image_pick_status);
        layoutImageThumbs = findViewById(R.id.layout_image_thumbnails);
        tvSizeStockHint = findViewById(R.id.tv_size_stock_hint);
        layoutSizeStockInputs = findViewById(R.id.layout_size_stock_inputs);

        // Sale end time picker
        tvSaleEndTime.setOnClickListener(v -> showDateTimePicker());

        spinnerCategory = findViewById(R.id.spinner_category);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, CATEGORIES);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(catAdapter);

        chipGroupSizes = findViewById(R.id.chip_group_sizes);
        chipGroupSizes.setOnCheckedStateChangeListener((group, checkedIds) -> renderSizeStockInputs());
        updateImagePreviews();
        renderSizeStockInputs();
    }

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar_add_product);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(isEditMode ? "Sửa sản phẩm" : "Thêm sản phẩm");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void pickImages() {
        pickImagesLauncher.launch(new String[]{"image/*"});
    }

    private Bitmap resizeBitmap(Uri uri) throws IOException {
        InputStream input = getContentResolver().openInputStream(uri);
        Bitmap original = BitmapFactory.decodeStream(input);
        if (input != null) input.close();

        int w = original.getWidth();
        int h = original.getHeight();
        if (w <= MAX_IMAGE_SIZE && h <= MAX_IMAGE_SIZE) return original;

        float ratio = (float) MAX_IMAGE_SIZE / Math.max(w, h);
        return Bitmap.createScaledBitmap(original, (int)(w * ratio), (int)(h * ratio), true);
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out);
        return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT);
    }

    private void loadProduct() {
        db.collection("products").document(productId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    Product p = doc.toObject(Product.class);
                    if (p == null) return;

                    edtName.setText(p.getName());
                    edtPrice.setText(String.valueOf(p.getPrice()));
                    // Select đúng category trong spinner
                    for (int i = 0; i < CATEGORIES.length; i++) {
                        if (CATEGORIES[i].equals(p.getCategory())) {
                            spinnerCategory.setSelection(i);
                            break;
                        }
                    }
                    edtDescription.setText(p.getDescription());
                    edtStock.setText(String.valueOf(p.getStock()));
                    edtDiscount.setText(String.valueOf(p.getDiscountPercent()));

                    existingSizeStock.clear();
                    if (p.getSizeStock() != null) {
                        existingSizeStock.putAll(p.getSizeStock());
                    }

                    // Load sizes
                    List<String> savedSizes = p.getSizes();
                    if (savedSizes != null) {
                        for (int i = 0; i < SIZE_CHIP_IDS.length; i++) {
                            Chip chip = findViewById(SIZE_CHIP_IDS[i]);
                            if (chip != null) chip.setChecked(savedSizes.contains(SIZE_VALUES[i]));
                        }
                    }
                    renderSizeStockInputs();

                    // Sale end time
                    if (p.getSaleEndTime() != null) {
                        saleEndTimestamp = p.getSaleEndTime();
                        tvSaleEndTime.setText(dtFmt.format(p.getSaleEndTime().toDate()));
                    }

                    base64Images.clear();
                    List<String> savedImages = p.getImageUrls();
                    if (savedImages != null && !savedImages.isEmpty()) {
                        for (String image : savedImages) {
                            if (image != null && !image.isEmpty()) base64Images.add(image);
                        }
                    } else {
                        String img = p.getImageUrl();
                        if (img != null && !img.isEmpty()) base64Images.add(img);
                    }

                    updateImagePreviews();
                });
    }

    private void saveProduct() {
        String name        = edtName.getText().toString().trim();
        String priceStr    = edtPrice.getText().toString().trim();
        String category    = spinnerCategory.getSelectedItem().toString();
        String description = edtDescription.getText().toString().trim();
        String stockStr    = edtStock.getText().toString().trim();

        if (TextUtils.isEmpty(name))     { edtName.setError("Nhập tên"); return; }
        if (TextUtils.isEmpty(priceStr)) { edtPrice.setError("Nhập giá"); return; }

        double price;
        int stock;
        try {
            price = Double.parseDouble(priceStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Giá không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        String discountStr = edtDiscount.getText().toString().trim();
        int discountPercent = 0;
        if (!discountStr.isEmpty()) {
            discountPercent = Integer.parseInt(discountStr);
            if (discountPercent < 0 || discountPercent > 99) {
                edtDiscount.setError("0-99");
                return;
            }
        }

        // Collect selected sizes
        List<String> selectedSizes = new ArrayList<>();
        for (int i = 0; i < SIZE_CHIP_IDS.length; i++) {
            Chip chip = findViewById(SIZE_CHIP_IDS[i]);
            if (chip != null && chip.isChecked()) selectedSizes.add(SIZE_VALUES[i]);
        }

        Map<String, Long> sizeStock = new HashMap<>();
        if (!selectedSizes.isEmpty()) {
            for (String size : selectedSizes) {
                EditText input = sizeStockInputs.get(size);
                String value = input != null ? input.getText().toString().trim() : "";
                if (TextUtils.isEmpty(value)) {
                    Toast.makeText(this, "Vui lòng nhập tồn kho cho size " + size, Toast.LENGTH_SHORT).show();
                    if (input != null) input.requestFocus();
                    return;
                }

                try {
                    int sizeQty = Integer.parseInt(value);
                    if (sizeQty < 0) throw new NumberFormatException();
                    sizeStock.put(size, (long) sizeQty);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Tồn kho size " + size + " không hợp lệ", Toast.LENGTH_SHORT).show();
                    if (input != null) input.requestFocus();
                    return;
                }
            }
            stock = calculateSizeStockTotal();
        } else {
            if (TextUtils.isEmpty(stockStr)) { edtStock.setError("Nhập tồn kho"); return; }
            try {
                stock = Integer.parseInt(stockStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Tồn kho không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("name",        name);
        data.put("price",       price);
        data.put("category",    category);
        data.put("description", description);
        data.put("imageUrl",    base64Images.isEmpty() ? "" : base64Images.get(0));
        data.put("imageUrls",   new ArrayList<>(base64Images));
        data.put("stock",       stock);
        data.put("discountPercent", discountPercent);
        data.put("sizes",       selectedSizes);
        data.put("sizeStock",   selectedSizes.isEmpty() ? null : sizeStock);
        if (discountPercent > 0 && saleEndTimestamp != null) {
            data.put("saleEndTime", saleEndTimestamp);
        } else {
            data.put("saleEndTime", null);
        }

        if (isEditMode) {
            db.collection("products").document(productId).update(data)
                    .addOnSuccessListener(u -> { Toast.makeText(this, "Đã cập nhật!", Toast.LENGTH_SHORT).show(); finish(); })
                    .addOnFailureListener(e -> Toast.makeText(this, "Lỗi cập nhật", Toast.LENGTH_SHORT).show());
        } else {
            data.put("createdAt", Timestamp.now());
            db.collection("products").add(data)
                    .addOnSuccessListener(u -> { Toast.makeText(this, "Thêm thành công!", Toast.LENGTH_SHORT).show(); finish(); })
                    .addOnFailureListener(e -> Toast.makeText(this, "Lỗi thêm sản phẩm", Toast.LENGTH_SHORT).show());
        }
    }

    private void showDateTimePicker() {
        Calendar cal = Calendar.getInstance();
        if (saleEndTimestamp != null) cal.setTime(saleEndTimestamp.toDate());

        new android.app.DatePickerDialog(this, (dp, year, month, day) -> {
            new android.app.TimePickerDialog(this, (tp, hour, minute) -> {
                Calendar picked = Calendar.getInstance();
                picked.set(year, month, day, hour, minute, 0);
                saleEndTimestamp = new com.google.firebase.Timestamp(picked.getTime());
                tvSaleEndTime.setText(dtFmt.format(picked.getTime()));
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateImagePreviews() {
        if (layoutImageThumbs == null || imgProduct == null) return;

        layoutImageThumbs.removeAllViews();
        boolean hasImages = !base64Images.isEmpty();
        tvPickImageHint.setVisibility(hasImages ? View.GONE : View.VISIBLE);

        if (!hasImages) {
            imgProduct.setImageDrawable(null);
            tvImagePickStatus.setText("Chưa chọn ảnh - có thể chọn tối đa " + MAX_IMAGE_COUNT + " ảnh");
            return;
        }

        try {
            byte[] bytes = Base64.decode(base64Images.get(0), Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            imgProduct.setImageBitmap(bmp);
        } catch (Exception e) {
            imgProduct.setImageDrawable(null);
        }

        int extraCount = Math.max(0, base64Images.size() - 1);
        tvImagePickStatus.setText(extraCount == 0
                ? "1 ảnh chính"
                : "1 ảnh chính + " + extraCount + " ảnh phụ");

        for (int i = 0; i < base64Images.size(); i++) {
            String image = base64Images.get(i);
            View thumb = getLayoutInflater().inflate(R.layout.item_admin_product_image_thumb, layoutImageThumbs, false);
            ImageView imageView = thumb.findViewById(R.id.img_thumb);
            TextView label = thumb.findViewById(R.id.tv_thumb_label);
            label.setText(i == 0 ? "Chính" : "Phụ " + i);
            try {
                byte[] bytes = Base64.decode(image, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                imageView.setImageBitmap(bmp);
            } catch (Exception ignored) {
                imageView.setImageDrawable(null);
            }
            final int index = i;
            thumb.setOnClickListener(v -> setPrimaryImage(index));
            layoutImageThumbs.addView(thumb);
        }
    }

    private void setPrimaryImage(int index) {
        if (index <= 0 || index >= base64Images.size()) return;
        String selected = base64Images.remove(index);
        base64Images.add(0, selected);
        updateImagePreviews();
        Toast.makeText(this, "Đặt làm ảnh chính", Toast.LENGTH_SHORT).show();
    }

    private void renderSizeStockInputs() {
        if (layoutSizeStockInputs == null || edtStock == null) return;

        for (Map.Entry<String, EditText> entry : sizeStockInputs.entrySet()) {
            String value = entry.getValue().getText().toString().trim();
            if (!value.isEmpty()) {
                try {
                    existingSizeStock.put(entry.getKey(), Long.parseLong(value));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        List<String> selectedSizes = getSelectedSizes();
        layoutSizeStockInputs.removeAllViews();
        sizeStockInputs.clear();

        boolean hasSizes = !selectedSizes.isEmpty();
        layoutSizeStockInputs.setVisibility(hasSizes ? View.VISIBLE : View.GONE);
        tvSizeStockHint.setVisibility(hasSizes ? View.VISIBLE : View.GONE);
        edtStock.setEnabled(!hasSizes);
        edtStock.setAlpha(hasSizes ? 0.65f : 1f);
        if (!hasSizes) return;

        for (String size : selectedSizes) {
            View row = getLayoutInflater().inflate(R.layout.item_admin_size_stock_input, layoutSizeStockInputs, false);
            TextView label = row.findViewById(R.id.tv_size_stock_label);
            EditText input = row.findViewById(R.id.edt_size_stock_value);

            label.setText("Size " + size);
            Long saved = existingSizeStock.get(size);
            if (saved != null) {
                input.setText(String.valueOf(saved));
            }

            input.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    edtStock.setText(String.valueOf(calculateSizeStockTotal()));
                }
            });

            sizeStockInputs.put(size, input);
            layoutSizeStockInputs.addView(row);
        }

        edtStock.setText(String.valueOf(calculateSizeStockTotal()));
    }

    private int calculateSizeStockTotal() {
        int total = 0;
        for (EditText input : sizeStockInputs.values()) {
            String value = input.getText().toString().trim();
            if (!value.isEmpty()) {
                try {
                    total += Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return total;
    }

    private List<String> getSelectedSizes() {
        List<String> selectedSizes = new ArrayList<>();
        for (int i = 0; i < SIZE_CHIP_IDS.length; i++) {
            Chip chip = findViewById(SIZE_CHIP_IDS[i]);
            if (chip != null && chip.isChecked()) selectedSizes.add(SIZE_VALUES[i]);
        }
        return selectedSizes;
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
