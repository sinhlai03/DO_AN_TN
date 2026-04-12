package com.example.appbangiay.chat;

import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appbangiay.R;
import com.example.appbangiay.customer.CartManager;
import com.example.appbangiay.customer.CheckoutActivity;
import com.example.appbangiay.model.Product;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatActivity extends AppCompatActivity {

    private static final String PREF_CHAT = "chat_history";
    private static final String KEY_SESSIONS_INDEX = "sessions_index";
    private static final String KEY_ACTIVE_SESSION_ID = "active_session_id";
    private static final String KEY_SESSION_PREFIX = "session_";
    private static final String LEGACY_KEY_MESSAGES = "messages";
    private static final String LEGACY_KEY_ORDER_STATE = "order_state";
    private static final String LEGACY_KEY_CONVERSATION = "conversation";
    private static final String DEFAULT_SESSION_TITLE = "Cuoc tro chuyen moi";

    private static final String ACTION_BUY_PRODUCT = "buy_product";
    private static final String ACTION_PICK_SIZE = "pick_size";
    private static final String ACTION_SIZE_ADVICE = "size_advice";
    private static final String ACTION_PICK_QUANTITY = "pick_quantity";
    private static final String ACTION_ADD_TO_CART = "add_to_cart";
    private static final String ACTION_CHECKOUT_NOW = "checkout_now";
    private static final String ACTION_CHANGE_SIZE = "change_size";

    private static final double[][] GENERIC_SIZE_CHART = {
            {22.5, 36.0},
            {23.0, 36.5},
            {23.5, 37.5},
            {24.0, 38.5},
            {24.5, 39.0},
            {25.0, 40.0},
            {25.5, 40.5},
            {26.0, 41.0},
            {26.5, 42.0},
            {27.0, 42.5},
            {27.5, 43.0},
            {28.0, 44.0},
            {28.5, 44.5},
            {29.0, 45.0},
            {29.5, 45.5},
            {30.0, 46.0}
    };

    private static final double[][] NIKE_SIZE_CHART = {
            {24.0, 38.5},
            {24.5, 39.0},
            {25.0, 40.0},
            {25.5, 40.5},
            {26.0, 41.0},
            {26.5, 42.0},
            {27.0, 42.5},
            {27.5, 43.0},
            {28.0, 44.0},
            {28.5, 44.5},
            {29.0, 45.0},
            {29.5, 45.5},
            {30.0, 46.0}
    };

    private enum ChatOrderStage {
        IDLE,
        WAITING_SIZE,
        WAITING_SIZE_ADVICE,
        WAITING_QUANTITY,
        WAITING_FINAL_ACTION
    }

    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter adapter;
    private RecyclerView rvMessages;
    private EditText edtMessage;
    private LinearLayout layoutTyping;
    private GroqChatService chatService;
    private final NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat sessionTimeFmt = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Product cache for displaying cards
    private final Map<String, Product> productMap = new HashMap<>();
    private final List<ChatSessionInfo> sessionHistory = new ArrayList<>();
    private String pendingCoupon = "";
    private String lastUserMessage = "";
    private Product pendingOrderProduct;
    private String pendingSelectedSize = "";
    private int pendingQuantity = 1;
    private ChatOrderStage chatOrderStage = ChatOrderStage.IDLE;
    private String activeSessionId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(android.R.color.white, getTheme()));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        setContentView(R.layout.activity_chat);

        rvMessages = findViewById(R.id.rv_messages);
        edtMessage = findViewById(R.id.edt_message);
        layoutTyping = findViewById(R.id.layout_typing);

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);
        adapter = new ChatAdapter();
        rvMessages.setAdapter(adapter);

        chatService = new GroqChatService();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_send).setOnClickListener(v -> sendMessage());
        findViewById(R.id.btn_new_chat).setOnClickListener(v -> startNewChat());
        findViewById(R.id.btn_history_chat).setOnClickListener(v -> showChatHistorySheet());

        edtMessage.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });

        // Quick suggestion chips
        setupChip(R.id.chip_1, "Giày chạy bộ nào tốt?");
        setupChip(R.id.chip_2, "Có khuyến mãi gì?");
        setupChip(R.id.chip_3, "Size 42 có giày nào?");

        loadContextData();
    }

    private void setupChip(int chipId, String text) {
        findViewById(chipId).setOnClickListener(v -> {
            edtMessage.setText(text);
            sendMessage();
            // Hide suggestions after first use
            findViewById(R.id.scroll_suggestions).setVisibility(View.GONE);
        });
    }

    private void loadContextData() {
        StringBuilder productsInfo = new StringBuilder();
        StringBuilder couponsInfo = new StringBuilder();

        db.collection("products").get().addOnSuccessListener(snap -> {
            for (DocumentSnapshot doc : snap.getDocuments()) {
                String id = doc.getId();
                String name = doc.getString("name");
                Double price = doc.getDouble("price");
                String desc = doc.getString("description");
                String category = doc.getString("category");
                String imageUrl = doc.getString("imageUrl");
                Long stock = doc.getLong("stock");
                Long discountPct = doc.getLong("discountPercent");
                List<String> sizes = extractSizes(doc.get("sizes"));
                Map<String, Long> sizeStock = extractSizeStock(doc.get("sizeStock"));

                // Build product object and cache it
                Product p = new Product();
                p.setId(id);
                p.setName(name);
                p.setPrice(price != null ? price : 0);
                p.setCategory(category);
                p.setDescription(desc);
                p.setImageUrl(imageUrl);
                p.setStock(stock != null ? stock.intValue() : 0);
                p.setDiscountPercent(discountPct != null ? discountPct.intValue() : 0);
                p.setSizes(sizes);
                p.setSizeStock(sizeStock);
                productMap.put(id, p);

                // Build context string with ID for AI
                productsInfo.append("ID: ").append(id)
                        .append(" | ").append(name != null ? name : "")
                        .append(" | Giá: ").append(price != null ? fmt.format(price) + " đ" : "N/A")
                        .append(" | Loại: ").append(category != null ? category : "N/A")
                        .append(" | Mô tả: ").append(desc != null ? desc : "")
                        .append(" | Tồn kho: ").append(stock != null ? stock : 0)
                        .append(" | Size hiện có: ").append(formatSizes(getSortedSizes(p)));
                if (discountPct != null && discountPct > 0) {
                    productsInfo.append(" | Giảm: ").append(discountPct).append("%");
                }
                productsInfo.append("\n");
            }

            db.collection("coupons").get().addOnSuccessListener(cSnap -> {
                for (DocumentSnapshot doc : cSnap.getDocuments()) {
                    String code = doc.getString("code");
                    Long pct = doc.getLong("discountPercent");
                    Boolean active = doc.getBoolean("active");
                    if (active != null && active) {
                        couponsInfo.append("- Mã: ").append(code != null ? code : "")
                                .append(" | Giảm: ").append(pct != null ? pct + "%" : "N/A")
                                .append("\n");
                    }
                }

                chatService.setContext(productsInfo.toString(), couponsInfo.toString());
                loadChatHistory();
            });
        });
    }

    // ─── Chat Init ───
    private void loadChatHistory() {
        migrateLegacySingleSessionIfNeeded();
        sessionHistory.clear();
        sessionHistory.addAll(loadSessionHistoryFromPrefs());
        createFreshSession(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveChatState();
    }

    private void createFreshSession(boolean withGreeting) {
        activeSessionId = generateSessionId();
        resetVisibleConversation();
        if (withGreeting) {
            addBotMessageWithGif("Chào bạn, mình là trợ lý của ShopGiay. Bạn đang muốn tìm mẫu đi học, đi chơi hay chạy bộ để mình gợi ý nhanh nhé?",
                    "https://media.giphy.com/media/bcKmIWkUMCjVm/giphy.gif");
        } else {
            saveChatState();
        }
    }

    private void startNewChat() {
        if (hasMeaningfulConversation()) {
            saveChatState();
            createFreshSession(true);
            Toast.makeText(this, "Đã tạo đoạn chat mới", Toast.LENGTH_SHORT).show();
            return;
        }

        if (activeSessionId.isEmpty()) {
            createFreshSession(true);
        } else {
            resetVisibleConversation();
            addBotMessageWithGif("Chào bạn, mình là trợ lý của ShopGiay. Bạn đang muốn tìm mẫu đi học, đi chơi hay chạy bộ để mình gợi ý nhanh nhé?",
                    "https://media.giphy.com/media/bcKmIWkUMCjVm/giphy.gif");
        }
    }

    private void resetVisibleConversation() {
        messages.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
        if (chatService != null) chatService.clearHistory();
        pendingCoupon = "";
        lastUserMessage = "";
        resetPendingOrder();
        updateSuggestionVisibility();
    }

    private boolean hasMeaningfulConversation() {
        if (pendingOrderProduct != null || chatOrderStage != ChatOrderStage.IDLE) return true;
        for (ChatMessage msg : messages) {
            if (msg.isUser && msg.text != null && !msg.text.trim().isEmpty()) return true;
        }
        return messages.size() > 1;
    }

    private void updateSuggestionVisibility() {
        View suggestions = findViewById(R.id.scroll_suggestions);
        if (suggestions == null) return;

        boolean hasUserMessages = false;
        for (ChatMessage msg : messages) {
            if (msg.isUser) {
                hasUserMessages = true;
                break;
            }
        }
        suggestions.setVisibility(hasUserMessages ? View.GONE : View.VISIBLE);
    }

    // ─── Giphy API search ───
    private static final String GIPHY_API_KEY = "dc6zaTOxFJmzC";
    
    private String searchGiphy(String query) {
        try {
            String encoded = java.net.URLEncoder.encode(query, "UTF-8");
            java.net.URL url = new java.net.URL(
                    "https://api.giphy.com/v1/gifs/search?api_key=" + GIPHY_API_KEY
                    + "&q=" + encoded + "&limit=10&rating=g&lang=en");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            
            if (conn.getResponseCode() == 200) {
                java.util.Scanner sc = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A");
                String json = sc.hasNext() ? sc.next() : "";
                org.json.JSONObject obj = new org.json.JSONObject(json);
                org.json.JSONArray data = obj.getJSONArray("data");
                if (data.length() > 0) {
                    // Random pick from results for variety
                    int idx = (int) (Math.random() * Math.min(data.length(), 5));
                    return data.getJSONObject(idx)
                            .getJSONObject("images")
                            .getJSONObject("fixed_height")
                            .getString("url");
                }
            }
        } catch (Exception e) {
            android.util.Log.e("GiphySearch", "Failed: " + e.getMessage());
        }
        return null;
    }

    // ─── Response Parsing ───
    private void parseResponseTags(ChatMessage msg, String rawReply) {
        // Extract product IDs from [PRODUCTS: id1, id2, ...]
        Pattern productPattern = Pattern.compile("\\[PRODUCTS:\\s*([^\\]]+)\\]");
        Matcher productMatcher = productPattern.matcher(rawReply);
        if (productMatcher.find()) {
            String ids = productMatcher.group(1);
            if (ids != null) {
                String[] idArray = ids.split(",");
                for (String id : idArray) {
                    String trimId = id.trim();
                    Product p = productMap.get(trimId);
                    if (p != null) {
                        msg.suggestedProducts.add(p);
                    }
                }
            }
        }

        // Handle [ADD_CART: id1, id2] — auto add products to cart
        Pattern addCartPattern = Pattern.compile("\\[ADD_CART:\\s*([^\\]]+)\\]");
        Matcher addCartMatcher = addCartPattern.matcher(rawReply);
        if (addCartMatcher.find()) {
            String ids = addCartMatcher.group(1);
            if (ids != null) {
                String[] idArray = ids.split(",");
                for (String id : idArray) {
                    String trimId = id.trim();
                    Product p = productMap.get(trimId);
                    if (p != null) {
                        msg.taggedAddProducts.add(p);
                    }
                }
            }
        }

        // Handle [COUPON: CODE] — save for checkout
        Pattern couponPattern = Pattern.compile("\\[COUPON:\\s*([^\\]]+)\\]");
        Matcher couponMatcher = couponPattern.matcher(rawReply);
        if (couponMatcher.find()) {
            pendingCoupon = couponMatcher.group(1).trim();
        }

        // Check for [ORDER] tag
        msg.showOrderButton = rawReply.contains("[ORDER]") && chatOrderStage == ChatOrderStage.IDLE;

        // Extract GIF search query
        Pattern gifPattern = Pattern.compile("\\[GIF:\\s*([^\\]]+)\\]");
        Matcher gifMatcher = gifPattern.matcher(rawReply);
        if (gifMatcher.find()) {
            msg.gifSearchQuery = gifMatcher.group(1).trim();
        }

        // Clean display text — remove all tags
        String cleanText = rawReply
                .replaceAll("\\[PRODUCTS:[^\\]]*\\]", "")
                .replaceAll("\\[ADD_CART:[^\\]]*\\]", "")
                .replaceAll("\\[COUPON:[^\\]]*\\]", "")
                .replaceAll("\\[ORDER\\]", "")
                .replaceAll("\\[GIF:[^\\]]*\\]", "")
                .trim();
        msg.text = cleanText;
    }

    // ─── Typing animation ───
    private void showTyping() {
        layoutTyping.setVisibility(View.VISIBLE);
        View dot1 = findViewById(R.id.dot1);
        View dot2 = findViewById(R.id.dot2);
        View dot3 = findViewById(R.id.dot3);

        animateDot(dot1, 0);
        animateDot(dot2, 150);
        animateDot(dot3, 300);
    }

    private void animateDot(View dot, long delay) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.5f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.5f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(dot, "alpha", 0.4f, 1f, 0.4f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, alpha);
        set.setDuration(800);
        set.setStartDelay(delay);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();

        dot.postDelayed(() -> {
            if (layoutTyping.getVisibility() == View.VISIBLE) {
                animateDot(dot, 0);
            }
        }, 1000 + delay);
    }

    private void hideTyping() {
        layoutTyping.setVisibility(View.GONE);
    }

    // ─── Send & receive ───
    private void sendMessage() {
        String text = edtMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        lastUserMessage = text;
        addUserMessage(text);
        edtMessage.setText("");

        if (handleOrderConversation(text)) {
            return;
        }

        showTyping();

        chatService.sendMessage(text, new GroqChatService.ChatCallback() {
            @Override
            public void onResponse(String reply) {
                runOnUiThread(() -> {
                    hideTyping();
                    ChatMessage msg = new ChatMessage(reply, false, new Date());
                    parseResponseTags(msg, reply);
                    messages.add(msg);
                    int pos = messages.size() - 1;
                    adapter.notifyItemInserted(pos);
                    rvMessages.smoothScrollToPosition(pos);
                    handleBotReply(msg);
                    
                    // Search Giphy async if AI provided a keyword
                    if (msg.gifSearchQuery != null) {
                        final int msgPos = pos;
                        new Thread(() -> {
                            String gifUrl = searchGiphy(msg.gifSearchQuery);
                            if (gifUrl != null) {
                                runOnUiThread(() -> {
                                    msg.gifUrl = gifUrl;
                                    adapter.notifyItemChanged(msgPos);
                                    saveChatState();
                                });
                            }
                        }).start();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    hideTyping();
                    addBotMessage("Mình đang hơi trục trặc một chút. Bạn nhắn lại giúp mình ngay nhé.");
                    Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void addUserMessage(String text) {
        messages.add(new ChatMessage(text, true, new Date()));
        int pos = messages.size() - 1;
        adapter.notifyItemInserted(pos);
        rvMessages.smoothScrollToPosition(pos);
        updateSuggestionVisibility();
        saveChatState();
    }

    private void addBotMessage(String text) {
        ChatMessage msg = new ChatMessage(text, false, new Date());
        messages.add(msg);
        int pos = messages.size() - 1;
        adapter.notifyItemInserted(pos);
        rvMessages.smoothScrollToPosition(pos);
        updateSuggestionVisibility();
        saveChatState();
    }

    private void checkBeforeOrder() {
        // Check cart
        if (CartManager.getInstance().getItems().isEmpty()) {
            addBotMessage("Giỏ hàng của bạn đang trống rồi. Mình gợi ý mẫu trước, rồi chốt đơn cho bạn ngay nhé.");
            return;
        }

        // Check address
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Vui lòng đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users").document(user.getUid())
                .collection("addresses")
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        // No address — show dialog
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Chưa có địa chỉ giao hàng")
                                .setMessage("Bạn cần thêm địa chỉ giao hàng trước khi đặt hàng.")
                                .setPositiveButton("Thêm địa chỉ", (d, w) -> {
                                    startActivity(new Intent(this,
                                            com.example.appbangiay.customer.AddressListActivity.class));
                                })
                                .setNegativeButton("Để sau", null)
                                .show();
                    } else {
                        // Has address — go to checkout with coupon
                        Intent intent = new Intent(this, CheckoutActivity.class);
                        if (!pendingCoupon.isEmpty()) {
                            intent.putExtra("coupon_code", pendingCoupon);
                        }
                        startActivity(intent);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi kiểm tra địa chỉ", Toast.LENGTH_SHORT).show();
                });
    }

    private boolean handleOrderConversation(String text) {
        switch (chatOrderStage) {
            case WAITING_SIZE:
                if (needsSizeAdvice(text)) {
                    chatOrderStage = ChatOrderStage.WAITING_SIZE_ADVICE;
                    addBotMessage("Bạn gửi giúp mình chiều dài bàn chân theo cm, hoặc size bạn hay mang gần đây nhé. Ví dụ: 26cm hoặc Nike 42.");
                    return true;
                }

                String requestedSize = extractSizeFromText(text);
                if (requestedSize != null) {
                    handleChosenSize(requestedSize);
                    return true;
                }

                addBotActionMessage(buildSizePrompt(), buildSizeActions(pendingOrderProduct));
                return true;

            case WAITING_SIZE_ADVICE:
                SizeAdvice advice = recommendSizeFromMessage(text);
                if (advice != null) {
                    pendingSelectedSize = advice.recommendedSize;
                    chatOrderStage = ChatOrderStage.WAITING_QUANTITY;
                    addBotActionMessage(advice.message, buildQuantityActions());
                    return true;
                }

                addBotMessage("Mình chưa đủ dữ liệu để chốt size đâu. Bạn gửi giúp mình theo một trong hai kiểu này nhé: 26cm hoặc size bạn thường mang như 42.");
                return true;

            case WAITING_QUANTITY:
                String changedSize = extractSizeFromText(text);
                if (changedSize != null) {
                    handleChosenSize(changedSize);
                    return true;
                }

                int quantity = extractQuantity(text);
                if (quantity > 0) {
                    pendingQuantity = quantity;
                    chatOrderStage = ChatOrderStage.WAITING_FINAL_ACTION;
                    addBotActionMessage(buildPendingSummary(), buildFinalActions());
                    return true;
                }

                addBotActionMessage("Mình đang giữ sẵn " + getSafeProductName(pendingOrderProduct) + " cho bạn. Bạn muốn lấy mấy đôi để mình chốt luôn?", buildQuantityActions());
                return true;

            case WAITING_FINAL_ACTION:
                String resized = extractSizeFromText(text);
                if (resized != null) {
                    handleChosenSize(resized);
                    return true;
                }

                int updatedQty = extractQuantity(text);
                if (updatedQty > 0) {
                    pendingQuantity = updatedQty;
                    addBotActionMessage(buildPendingSummary(), buildFinalActions());
                    return true;
                }

                if (wantsCheckout(text)) {
                    startDirectCheckout();
                    return true;
                }

                if (wantsAddToCart(text)) {
                    addPendingSelectionToCart();
                    return true;
                }

                if (wantsChangeSize(text)) {
                    chatOrderStage = ChatOrderStage.WAITING_SIZE;
                    addBotActionMessage(buildSizePrompt(), buildSizeActions(pendingOrderProduct));
                    return true;
                }

                addBotActionMessage(buildPendingSummary(), buildFinalActions());
                return true;

            case IDLE:
            default:
                return false;
        }
    }

    private void handleBotReply(ChatMessage msg) {
        boolean startedFromTaggedProducts = processTaggedAddProducts(msg.taggedAddProducts);
        if (startedFromTaggedProducts) {
            msg.showOrderButton = false;
            notifyMessageChanged(msg);
            return;
        }

        if (!isPurchaseIntent(lastUserMessage)) {
            return;
        }

        if (msg.suggestedProducts.size() == 1) {
            Product product = msg.suggestedProducts.get(0);
            if (productHasSizes(product)) {
                msg.showOrderButton = false;
                notifyMessageChanged(msg);
                beginChatPurchase(product, extractSizeFromText(lastUserMessage));
            }
        } else if (msg.suggestedProducts.size() > 1) {
            addBotMessage("Bạn chọn giúp mình một mẫu bên trên nhé, mình sẽ chốt size rồi lên đơn cho bạn ngay trong chat.");
        }
    }

    private boolean processTaggedAddProducts(List<Product> taggedProducts) {
        if (taggedProducts == null || taggedProducts.isEmpty()) {
            return false;
        }

        List<Product> needsSize = new ArrayList<>();
        StringBuilder added = new StringBuilder();

        for (Product product : taggedProducts) {
            if (productHasSizes(product)) {
                needsSize.add(product);
                continue;
            }

            CartManager.getInstance().addProduct(product);
            if (added.length() > 0) added.append(", ");
            added.append(getSafeProductName(product));
        }

        if (added.length() > 0) {
            Toast.makeText(this, "Đã thêm " + added + " vào giỏ", Toast.LENGTH_SHORT).show();
        }

        if (needsSize.isEmpty()) {
            return false;
        }

        if (needsSize.size() == 1) {
            beginChatPurchase(needsSize.get(0), extractSizeFromText(lastUserMessage));
        } else {
            addBotMessage("Mình cần chốt đúng size trước khi thêm vào giỏ. Bạn chọn giúp mình một mẫu bên trên để mình tư vấn chuẩn hơn nhé.");
        }
        return true;
    }

    private void beginChatPurchase(Product product, String preselectedSize) {
        if (product == null) return;

        pendingOrderProduct = product;
        pendingSelectedSize = "";
        pendingQuantity = 1;

        if (!productHasSizes(product)) {
            chatOrderStage = ChatOrderStage.WAITING_QUANTITY;
            addBotActionMessage(
                    getSafeProductName(product) + " không cần chọn size riêng đâu. Bạn muốn lấy mấy đôi để mình chuẩn bị luôn?",
                    buildQuantityActions());
            return;
        }

        if (preselectedSize != null && !preselectedSize.isEmpty()) {
            handleChosenSize(preselectedSize);
            return;
        }

        chatOrderStage = ChatOrderStage.WAITING_SIZE;
        addBotActionMessage(buildSizePrompt(), buildSizeActions(product));
    }

    private void handleChosenSize(String requestedSize) {
        if (pendingOrderProduct == null) return;

        List<String> sizes = getSortedSizes(pendingOrderProduct);
        if (sizes.contains(requestedSize)) {
            pendingSelectedSize = requestedSize;
            chatOrderStage = ChatOrderStage.WAITING_QUANTITY;
            addBotActionMessage(
                    "Oke, mình ghi nhận " + getSafeProductName(pendingOrderProduct) + " size " + requestedSize
                            + ". Bạn muốn lấy mấy đôi để mình chốt tiếp nhé?",
                    buildQuantityActions());
            return;
        }

        chatOrderStage = ChatOrderStage.WAITING_SIZE;
        addBotActionMessage(
                getSafeProductName(pendingOrderProduct) + " hiện chưa có size " + requestedSize
                        + " đâu. Shop đang có các size: " + formatSizes(sizes)
                        + ". Bạn chọn size khác giúp mình, hoặc xem vài mẫu khác đang có đúng size đó ở bên dưới nhé.",
                buildSizeActions(pendingOrderProduct));

        List<Product> alternatives = findAlternativeProducts(pendingOrderProduct, requestedSize);
        if (!alternatives.isEmpty()) {
            ChatMessage altMsg = new ChatMessage(
                    "Mình chọn sẵn vài mẫu khác đang có size " + requestedSize + " để bạn tham khảo nhanh đây.",
                    false,
                    new Date());
            altMsg.suggestedProducts.addAll(alternatives);
            addBotMessage(altMsg);
        }
    }

    private void handleChatAction(ChatAction action) {
        if (action == null) return;

        switch (action.type) {
            case ACTION_BUY_PRODUCT:
                beginChatPurchase(productMap.get(action.value), null);
                break;

            case ACTION_PICK_SIZE:
                handleChosenSize(action.value);
                break;

            case ACTION_SIZE_ADVICE:
                chatOrderStage = ChatOrderStage.WAITING_SIZE_ADVICE;
                addBotMessage("Bạn gửi giúp mình chiều dài bàn chân theo cm, hoặc size bạn hay mang để mình tư vấn sát hơn nhé. Ví dụ: 26cm hoặc size 42.");
                break;

            case ACTION_PICK_QUANTITY:
                pendingQuantity = Math.max(1, parseIntSafe(action.value, 1));
                chatOrderStage = ChatOrderStage.WAITING_FINAL_ACTION;
                addBotActionMessage(buildPendingSummary(), buildFinalActions());
                break;

            case ACTION_ADD_TO_CART:
                addPendingSelectionToCart();
                break;

            case ACTION_CHECKOUT_NOW:
                startDirectCheckout();
                break;

            case ACTION_CHANGE_SIZE:
                chatOrderStage = ChatOrderStage.WAITING_SIZE;
                addBotActionMessage(buildSizePrompt(), buildSizeActions(pendingOrderProduct));
                break;

            default:
                break;
        }
    }

    private void addPendingSelectionToCart() {
        if (!hasReadySelection()) {
            addBotActionMessage(buildSizePrompt(), buildSizeActions(pendingOrderProduct));
            return;
        }

        for (int i = 0; i < pendingQuantity; i++) {
            CartManager.getInstance().addProduct(pendingOrderProduct, pendingSelectedSize);
        }

        String sizeLabel = pendingSelectedSize != null && !pendingSelectedSize.isEmpty()
                ? " size " + pendingSelectedSize
                : "";
        addBotMessage("Mình đã bỏ " + pendingQuantity + " đôi " + getSafeProductName(pendingOrderProduct)
                + sizeLabel + " vào giỏ cho bạn rồi. Nếu muốn mình có thể chốt thêm mẫu khác ngay tiếp.");
        resetPendingOrder();
    }

    private void startDirectCheckout() {
        if (!hasReadySelection()) {
            addBotActionMessage(buildSizePrompt(), buildSizeActions(pendingOrderProduct));
            return;
        }

        Intent intent = new Intent(this, CheckoutActivity.class);
        intent.putExtra("direct_checkout_product_id", pendingOrderProduct.getId());
        intent.putExtra("direct_checkout_name", pendingOrderProduct.getName());
        intent.putExtra("direct_checkout_category", pendingOrderProduct.getCategory());
        intent.putExtra("direct_checkout_image", pendingOrderProduct.getImageUrl());
        intent.putExtra("direct_checkout_price", pendingOrderProduct.getPrice());
        intent.putExtra("direct_checkout_size", pendingSelectedSize);
        intent.putExtra("direct_checkout_quantity", pendingQuantity);
        if (!pendingCoupon.isEmpty()) {
            intent.putExtra("coupon_code", pendingCoupon);
        }
        startActivity(intent);
        String sizeLabel = pendingSelectedSize != null && !pendingSelectedSize.isEmpty()
                ? " size " + pendingSelectedSize
                : "";
        addBotMessage("Mình đã giữ " + getSafeProductName(pendingOrderProduct)
                + sizeLabel + " số lượng " + pendingQuantity
                + " và mở trang thanh toán cho bạn rồi nhé.");
        resetPendingOrder();
    }

    private boolean hasReadySelection() {
        if (pendingOrderProduct == null) return false;
        if (productHasSizes(pendingOrderProduct) && (pendingSelectedSize == null || pendingSelectedSize.isEmpty())) {
            return false;
        }
        return pendingQuantity > 0;
    }

    private void resetPendingOrder() {
        pendingOrderProduct = null;
        pendingSelectedSize = "";
        pendingQuantity = 1;
        chatOrderStage = ChatOrderStage.IDLE;
    }

    private List<ChatAction> buildSizeActions(Product product) {
        List<ChatAction> actions = new ArrayList<>();
        for (String size : getSortedSizes(product)) {
            actions.add(new ChatAction(size, ACTION_PICK_SIZE, size));
        }
        actions.add(new ChatAction("Nhờ tư vấn size", ACTION_SIZE_ADVICE, ""));
        return actions;
    }

    private List<ChatAction> buildQuantityActions() {
        List<ChatAction> actions = new ArrayList<>();
        actions.add(new ChatAction("1 đôi", ACTION_PICK_QUANTITY, "1"));
        actions.add(new ChatAction("2 đôi", ACTION_PICK_QUANTITY, "2"));
        actions.add(new ChatAction("3 đôi", ACTION_PICK_QUANTITY, "3"));
        return actions;
    }

    private List<ChatAction> buildFinalActions() {
        List<ChatAction> actions = new ArrayList<>();
        actions.add(new ChatAction("Bỏ vào giỏ", ACTION_ADD_TO_CART, ""));
        actions.add(new ChatAction("Thanh toán luôn", ACTION_CHECKOUT_NOW, ""));
        actions.add(new ChatAction("Đổi size", ACTION_CHANGE_SIZE, ""));
        return actions;
    }

    private String buildSizePrompt() {
        if (pendingOrderProduct == null) return "Bạn đang muốn xem size nào để mình chốt giúp?";
        return getSafeProductName(pendingOrderProduct) + " hiện shop có các size: "
                + formatSizes(getSortedSizes(pendingOrderProduct))
                + ". Bạn thường mang size nào? Nếu chưa chắc, bấm Nhờ tư vấn size để mình tính giúp.";
    }

    private String buildPendingSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Mình đang giữ ")
                .append(getSafeProductName(pendingOrderProduct));

        if (pendingSelectedSize != null && !pendingSelectedSize.isEmpty()) {
            summary.append(" size ").append(pendingSelectedSize);
        }

        summary.append(" số lượng ").append(pendingQuantity)
                .append(". Bạn muốn mình bỏ vào giỏ hay mở thanh toán luôn?");
        return summary.toString();
    }

    private boolean needsSizeAdvice(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("tư vấn size")
                || lower.contains("nhờ tư vấn size")
                || lower.contains("không biết size")
                || lower.contains("khong biet size")
                || lower.contains("không chắc size")
                || lower.contains("khong chac size")
                || lower.contains("gợi ý size")
                || lower.contains("goi y size");
    }

    private boolean wantsCheckout(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("thanh toán")
                || lower.contains("thanh toan")
                || lower.contains("checkout")
                || lower.contains("mua ngay")
                || lower.contains("thanh toán luôn")
                || lower.contains("thanh toan luon")
                || lower.contains("đặt luôn")
                || lower.contains("dat luon");
    }

    private boolean wantsAddToCart(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("thêm vào giỏ")
                || lower.contains("them vao gio")
                || lower.contains("add to cart")
                || lower.contains("bỏ vào giỏ")
                || lower.contains("bo vao gio")
                || lower.contains("đưa vào giỏ")
                || lower.contains("dua vao gio");
    }

    private boolean wantsChangeSize(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("đổi size")
                || lower.contains("doi size")
                || lower.contains("chọn size khác")
                || lower.contains("chon size khac");
    }

    private boolean isPurchaseIntent(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("mua")
                || lower.contains("đặt")
                || lower.contains("dat")
                || lower.contains("thanh toán")
                || lower.contains("thanh toan")
                || lower.contains("checkout")
                || lower.contains("chốt đơn")
                || lower.contains("chot don");
    }

    private SizeAdvice recommendSizeFromMessage(String text) {
        if (pendingOrderProduct == null) return null;

        Double footLength = extractFootLengthCm(text);
        Double statedSize = extractRawSizeValue(text);
        if (footLength == null && statedSize == null) return null;

        double baseRawSize = footLength != null
                ? convertFootLengthToRawSize(footLength, pendingOrderProduct)
                : statedSize;

        double adjustedRawSize = baseRawSize + getBrandFitBias(pendingOrderProduct);
        boolean preferLarger = false;
        boolean preferSmaller = false;

        if (mentionsWideFoot(text) || mentionsLoosePreference(text)) {
            adjustedRawSize += 0.5;
            preferLarger = true;
        } else if (mentionsSlimFoot(text) || mentionsSnugPreference(text)) {
            adjustedRawSize -= 0.5;
            preferSmaller = true;
        }

        String recommendedSize = findClosestAvailableSize(pendingOrderProduct, adjustedRawSize, preferLarger, preferSmaller);
        if (recommendedSize == null) return null;

        String alternativeSize = null;
        if (preferLarger) {
            alternativeSize = findAdjacentAvailableSize(pendingOrderProduct, recommendedSize, false);
        } else if (preferSmaller) {
            alternativeSize = findAdjacentAvailableSize(pendingOrderProduct, recommendedSize, true);
        } else if (Math.abs(adjustedRawSize - Math.rint(adjustedRawSize)) == 0.5) {
            String lowerNeighbor = findAdjacentAvailableSize(pendingOrderProduct, recommendedSize, false);
            String higherNeighbor = findAdjacentAvailableSize(pendingOrderProduct, recommendedSize, true);
            alternativeSize = lowerNeighbor != null && !lowerNeighbor.equals(recommendedSize)
                    ? lowerNeighbor : higherNeighbor;
        }

        SizeAdvice advice = new SizeAdvice();
        advice.recommendedSize = recommendedSize;
        advice.message = buildSizeAdviceMessage(
                footLength,
                baseRawSize,
                recommendedSize,
                alternativeSize,
                preferLarger,
                preferSmaller,
                pendingOrderProduct);
        return advice;
    }

    private String extractSizeFromText(String text) {
        Double size = extractRawSizeValue(text);
        if (size == null) return null;
        if (pendingOrderProduct != null && productHasSizes(pendingOrderProduct)) {
            return findClosestAvailableSize(pendingOrderProduct, size, true, false);
        }
        return formatSizeNumber(size);
    }

    private Double extractRawSizeValue(String text) {
        Matcher matcher = Pattern.compile("\\b(3[6-9]|4[0-6])(?:([\\.,]5))?\\b").matcher(text);
        if (matcher.find()) {
            try {
                return Double.parseDouble((matcher.group(1) + (matcher.group(2) != null ? matcher.group(2) : "")).replace(',', '.'));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double extractFootLengthCm(String text) {
        Matcher matcher = Pattern.compile("(2[2-9](?:[\\.,]\\d)?)\\s*cm", Pattern.CASE_INSENSITIVE).matcher(text);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1).replace(',', '.'));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int extractQuantity(String text) {
        Matcher pairMatcher = Pattern.compile("\\b([1-9]|10)\\s*(đôi|doi|cặp|cap)\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (pairMatcher.find()) {
            return parseIntSafe(pairMatcher.group(1), -1);
        }

        Matcher xMatcher = Pattern.compile("\\bx\\s*([1-9]|10)\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (xMatcher.find()) {
            return parseIntSafe(xMatcher.group(1), -1);
        }

        Matcher plainMatcher = Pattern.compile("\\b([1-9]|10)\\b").matcher(text);
        if (plainMatcher.find()) {
            return parseIntSafe(plainMatcher.group(1), -1);
        }

        return -1;
    }

    private boolean mentionsWideFoot(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("chân bè") || lower.contains("chan be")
                || lower.contains("chân rộng") || lower.contains("chan rong");
    }

    private boolean mentionsSlimFoot(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("chân thon") || lower.contains("chan thon")
                || lower.contains("chân nhỏ") || lower.contains("chan nho");
    }

    private boolean mentionsLoosePreference(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("thoải mái") || lower.contains("thoai mai")
                || lower.contains("rộng rãi") || lower.contains("rong rai")
                || lower.contains("dư mũi") || lower.contains("du mui");
    }

    private boolean mentionsSnugPreference(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("ôm chân") || lower.contains("om chan")
                || lower.contains("fit") || lower.contains("gọn chân")
                || lower.contains("gon chan");
    }

    private double convertFootLengthToRawSize(double cm, Product product) {
        double[][] chart = getSizeChart(product);
        if (cm <= chart[0][0]) return chart[0][1];
        if (cm >= chart[chart.length - 1][0]) return chart[chart.length - 1][1];

        for (int i = 0; i < chart.length - 1; i++) {
            double leftCm = chart[i][0];
            double rightCm = chart[i + 1][0];
            if (cm >= leftCm && cm <= rightCm) {
                double ratio = (cm - leftCm) / (rightCm - leftCm);
                return chart[i][1] + ratio * (chart[i + 1][1] - chart[i][1]);
            }
        }
        return chart[chart.length - 1][1];
    }

    private String findClosestAvailableSize(Product product, double desiredSize, boolean preferLarger, boolean preferSmaller) {
        List<String> sizes = getSortedSizes(product);
        if (sizes.isEmpty()) return null;

        String best = sizes.get(0);
        double bestDistance = Math.abs(parseDoubleSafe(best, desiredSize) - desiredSize);

        for (String size : sizes) {
            double numericSize = parseDoubleSafe(size, desiredSize);
            double distance = Math.abs(numericSize - desiredSize);
            if (distance < bestDistance
                    || (Math.abs(distance - bestDistance) < 0.0001
                    && isBetterTieBreaker(numericSize, parseDoubleSafe(best, desiredSize), preferLarger, preferSmaller))) {
                best = size;
                bestDistance = distance;
            }
        }

        return best;
    }

    private boolean isBetterTieBreaker(double candidate, double current, boolean preferLarger, boolean preferSmaller) {
        if (preferLarger) return candidate > current;
        if (preferSmaller) return candidate < current;
        return candidate > current;
    }

    private String findAdjacentAvailableSize(Product product, String baseSize, boolean upward) {
        List<String> sizes = getSortedSizes(product);
        int index = sizes.indexOf(baseSize);
        if (index < 0) return null;

        int nextIndex = upward ? index + 1 : index - 1;
        if (nextIndex < 0 || nextIndex >= sizes.size()) return null;
        return sizes.get(nextIndex);
    }

    private String buildSizeAdviceMessage(Double footLength,
                                          double baseRawSize,
                                          String recommendedSize,
                                          String alternativeSize,
                                          boolean preferLarger,
                                          boolean preferSmaller,
                                          Product product) {
        StringBuilder builder = new StringBuilder();
        builder.append(buildChartReference(product)).append(" ");

        if (footLength != null) {
            builder.append("Với chân dài ")
                    .append(formatDecimal(footLength))
                    .append(" cm, bạn đang rơi vào khoảng ")
                    .append(formatDecimal(baseRawSize))
                    .append(".");
        } else {
            builder.append("Nếu bạn thường mang khoảng size ")
                    .append(formatDecimal(baseRawSize))
                    .append(", mình lấy đó làm mốc để quy đổi cho mẫu này.");
        }

        builder.append(" Hiện shop có các size ")
                .append(formatSizes(getSortedSizes(product)))
                .append(", nên mình nghiêng về size ")
                .append(recommendedSize)
                .append(".");

        if (preferLarger && alternativeSize != null) {
            builder.append(" Nếu bạn thích ôm chân hơn một chút thì có thể cân nhắc size ")
                    .append(alternativeSize)
                    .append(".");
        } else if (preferSmaller && alternativeSize != null) {
            builder.append(" Nếu bạn muốn đi thoải mái hơn thì có thể nhích lên size ")
                    .append(alternativeSize)
                    .append(".");
        } else if (alternativeSize != null && !alternativeSize.equals(recommendedSize)) {
            builder.append(" Nếu bạn thích đi dư mũi hơn thì có thể thử thêm size ")
                    .append(alternativeSize)
                    .append(".");
        }

        builder.append(" Nếu thấy ổn, bạn chọn giúp mình số lượng nhé.");
        return builder.toString();
    }

    private String buildChartReference(Product product) {
        String brandKey = detectBrandKey(product);
        if ("nike".equals(brandKey)) {
            return "Mình đang tham chiếu bảng size Nike phổ biến.";
        }
        if ("converse".equals(brandKey) || "vans".equals(brandKey)) {
            return "Mình đang tham chiếu bảng size phổ biến và lưu ý là form của dòng này thường dài hơn một chút.";
        }
        return "Mình đang tham chiếu bảng size giày phổ biến của shop.";
    }

    private double[][] getSizeChart(Product product) {
        String brandKey = detectBrandKey(product);
        if ("nike".equals(brandKey)) {
            return NIKE_SIZE_CHART;
        }
        return GENERIC_SIZE_CHART;
    }

    private double getBrandFitBias(Product product) {
        String brandKey = detectBrandKey(product);
        if ("converse".equals(brandKey) || "vans".equals(brandKey)) {
            return -0.5;
        }
        return 0.0;
    }

    private String detectBrandKey(Product product) {
        if (product == null) return "generic";
        String content = ((product.getName() != null ? product.getName() : "") + " "
                + (product.getDescription() != null ? product.getDescription() : "") + " "
                + (product.getCategory() != null ? product.getCategory() : ""))
                .toLowerCase(Locale.ROOT);

        if (content.contains("nike")) return "nike";
        if (content.contains("adidas")) return "adidas";
        if (content.contains("converse")) return "converse";
        if (content.contains("vans")) return "vans";
        if (content.contains("puma")) return "puma";
        if (content.contains("new balance")) return "new_balance";
        if (content.contains("asics")) return "asics";
        return "generic";
    }

    private String formatSizeNumber(double size) {
        if (Math.abs(size - Math.rint(size)) < 0.0001) {
            return String.valueOf((int) Math.rint(size));
        }
        return formatDecimal(size);
    }

    private String formatDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    private double parseDoubleSafe(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private List<Product> findAlternativeProducts(Product baseProduct, String desiredSize) {
        List<Product> candidates = new ArrayList<>();
        for (Product product : productMap.values()) {
            if (product == null || product.getId() == null) continue;
            if (baseProduct != null && product.getId().equals(baseProduct.getId())) continue;
            if (product.getStock() <= 0) continue;
            if (!getSortedSizes(product).contains(desiredSize)) continue;
            candidates.add(product);
        }

        candidates.sort((left, right) -> {
            boolean leftSameCategory = baseProduct != null && safeEquals(left.getCategory(), baseProduct.getCategory());
            boolean rightSameCategory = baseProduct != null && safeEquals(right.getCategory(), baseProduct.getCategory());
            if (leftSameCategory != rightSameCategory) {
                return leftSameCategory ? -1 : 1;
            }
            double basePrice = baseProduct != null ? baseProduct.getPrice() : 0;
            double leftDiff = Math.abs(left.getPrice() - basePrice);
            double rightDiff = Math.abs(right.getPrice() - basePrice);
            return Double.compare(leftDiff, rightDiff);
        });

        if (candidates.size() > 4) {
            return new ArrayList<>(candidates.subList(0, 4));
        }
        return candidates;
    }

    private List<String> extractSizes(Object rawSizes) {
        List<String> sizes = new ArrayList<>();
        if (rawSizes instanceof List) {
            for (Object item : (List<?>) rawSizes) {
                if (item != null) {
                    String size = item.toString().trim();
                    if (!size.isEmpty()) {
                        sizes.add(size);
                    }
                }
            }
        }
        sizes.sort((left, right) -> Integer.compare(parseIntSafe(left, 0), parseIntSafe(right, 0)));
        return sizes;
    }

    private Map<String, Long> extractSizeStock(Object rawSizeStock) {
        Map<String, Long> sizeStock = new HashMap<>();
        if (!(rawSizeStock instanceof Map)) return sizeStock;

        Map<?, ?> rawMap = (Map<?, ?>) rawSizeStock;
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            try {
                sizeStock.put(entry.getKey().toString(), Long.parseLong(entry.getValue().toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        return sizeStock;
    }

    private List<String> getSortedSizes(Product product) {
        if (product == null || product.getSizes() == null) return new ArrayList<>();
        List<String> sizes = new ArrayList<>();
        Map<String, Long> sizeStock = product.getSizeStock();
        for (String size : product.getSizes()) {
            if (sizeStock == null || sizeStock.isEmpty()) {
                sizes.add(size);
            } else {
                Long qty = sizeStock.get(size);
                if (qty != null && qty > 0) {
                    sizes.add(size);
                }
            }
        }
        sizes.sort((left, right) -> Integer.compare(parseIntSafe(left, 0), parseIntSafe(right, 0)));
        return sizes;
    }

    private String formatSizes(List<String> sizes) {
        if (sizes == null || sizes.isEmpty()) return "Chưa cập nhật";
        return android.text.TextUtils.join(", ", sizes);
    }

    private boolean productHasSizes(Product product) {
        return product != null && product.getSizes() != null && !product.getSizes().isEmpty();
    }

    private int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String getSafeProductName(Product product) {
        return product != null && product.getName() != null && !product.getName().isEmpty()
                ? product.getName() : "sản phẩm";
    }

    private boolean safeEquals(String left, String right) {
        if (left == null) return right == null;
        return left.equals(right);
    }

    private void addBotActionMessage(String text, List<ChatAction> actions) {
        ChatMessage msg = new ChatMessage(text, false, new Date());
        msg.quickActions.addAll(actions);
        addBotMessage(msg);
    }

    private void addBotMessage(ChatMessage msg) {
        messages.add(msg);
        int pos = messages.size() - 1;
        adapter.notifyItemInserted(pos);
        rvMessages.smoothScrollToPosition(pos);
        updateSuggestionVisibility();
        saveChatState();
    }

    private void notifyMessageChanged(ChatMessage msg) {
        int index = messages.indexOf(msg);
        if (index >= 0) adapter.notifyItemChanged(index);
        saveChatState();
    }

    private SharedPreferences getChatPrefs() {
        return getSharedPreferences(PREF_CHAT, MODE_PRIVATE);
    }

    private String getScopedKey(String key) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String scope = user != null ? user.getUid() : "guest";
        return scope + "_" + key;
    }

    private String getSessionDataKey(String sessionId) {
        return getScopedKey(KEY_SESSION_PREFIX + sessionId);
    }

    private String generateSessionId() {
        return "chat_" + System.currentTimeMillis() + "_" + Math.abs((int) System.nanoTime());
    }

    private void saveChatState() {
        if (adapter == null || chatService == null) return;
        if (!hasMeaningfulConversation()) return;
        if (activeSessionId == null || activeSessionId.isEmpty()) {
            activeSessionId = generateSessionId();
        }

        try {
            List<ChatSessionInfo> sessions = loadSessionHistoryFromPrefs();
            ChatSessionInfo current = findSessionInfo(sessions, activeSessionId);
            if (current == null) {
                current = new ChatSessionInfo(activeSessionId);
                sessions.add(current);
            }

            current.title = generateSessionTitle();
            current.lastMessage = buildSessionPreview();
            current.updatedAt = System.currentTimeMillis();
            sortSessions(sessions);

            JSONObject sessionData = new JSONObject();
            sessionData.put("messages", serializeMessages());
            sessionData.put("orderState", serializeOrderState());
            sessionData.put("conversation", chatService.exportHistory());

            SharedPreferences.Editor editor = getChatPrefs().edit();
            editor.putString(getScopedKey(KEY_SESSIONS_INDEX), serializeSessionHistory(sessions).toString());
            editor.putString(getScopedKey(KEY_ACTIVE_SESSION_ID), activeSessionId);
            editor.putString(getSessionDataKey(activeSessionId), sessionData.toString());
            editor.apply();

            sessionHistory.clear();
            sessionHistory.addAll(sessions);
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Save chat state failed", e);
        }
    }

    private boolean restoreChatState() {
        migrateLegacySingleSessionIfNeeded();
        sessionHistory.clear();
        sessionHistory.addAll(loadSessionHistoryFromPrefs());
        if (sessionHistory.isEmpty()) return false;

        SharedPreferences prefs = getChatPrefs();
        String preferredSessionId = prefs.getString(getScopedKey(KEY_ACTIVE_SESSION_ID), "");
        if (preferredSessionId == null || preferredSessionId.isEmpty()
                || findSessionInfo(sessionHistory, preferredSessionId) == null) {
            preferredSessionId = sessionHistory.get(0).id;
        }

        return restoreSessionById(preferredSessionId);
    }

    private boolean restoreSessionById(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return false;

        String rawSession = getChatPrefs().getString(getSessionDataKey(sessionId), null);
        if (rawSession == null || rawSession.trim().isEmpty()) return false;

        try {
            JSONObject sessionObj = new JSONObject(rawSession);
            JSONArray array = sessionObj.optJSONArray("messages");
            JSONObject orderState = sessionObj.optJSONObject("orderState");
            String conversation = sessionObj.optString("conversation", "[]");

            messages.clear();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    ChatMessage msg = new ChatMessage(
                            obj.optString("text", ""),
                            obj.optBoolean("isUser", false),
                            new Date(obj.optLong("time", System.currentTimeMillis())));
                    msg.gifUrl = obj.isNull("gifUrl") ? null : obj.optString("gifUrl", null);
                    msg.gifSearchQuery = obj.isNull("gifSearchQuery") ? null : obj.optString("gifSearchQuery", null);
                    msg.showOrderButton = obj.optBoolean("showOrderButton", false);

                    JSONArray productIds = obj.optJSONArray("productIds");
                    if (productIds != null) {
                        for (int j = 0; j < productIds.length(); j++) {
                            Product product = productMap.get(productIds.optString(j));
                            if (product != null) msg.suggestedProducts.add(product);
                        }
                    }

                    JSONArray actions = obj.optJSONArray("quickActions");
                    if (actions != null) {
                        for (int j = 0; j < actions.length(); j++) {
                            JSONObject actionObj = actions.optJSONObject(j);
                            if (actionObj == null) continue;
                            msg.quickActions.add(new ChatAction(
                                    actionObj.optString("label", ""),
                                    actionObj.optString("type", ""),
                                    actionObj.optString("value", "")));
                        }
                    }

                    messages.add(msg);
                }
            }

            restoreOrderState(orderState != null ? orderState.toString() : null);
            chatService.restoreHistory(conversation);
            activeSessionId = sessionId;
            getChatPrefs().edit().putString(getScopedKey(KEY_ACTIVE_SESSION_ID), activeSessionId).apply();
            sessionHistory.clear();
            sessionHistory.addAll(loadSessionHistoryFromPrefs());

            adapter.notifyDataSetChanged();
            if (!messages.isEmpty()) {
                rvMessages.scrollToPosition(messages.size() - 1);
            }
            updateSuggestionVisibility();
            return !messages.isEmpty();
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Restore chat state failed", e);
            resetVisibleConversation();
            return false;
        }
    }

    private void migrateLegacySingleSessionIfNeeded() {
        SharedPreferences prefs = getChatPrefs();
        String indexKey = getScopedKey(KEY_SESSIONS_INDEX);
        String legacyMessagesKey = getScopedKey(LEGACY_KEY_MESSAGES);
        String legacyOrderStateKey = getScopedKey(LEGACY_KEY_ORDER_STATE);
        String legacyConversationKey = getScopedKey(LEGACY_KEY_CONVERSATION);

        if (prefs.contains(indexKey)) return;

        String rawMessages = prefs.getString(legacyMessagesKey, null);
        String rawOrderState = prefs.getString(legacyOrderStateKey, null);
        String rawConversation = prefs.getString(legacyConversationKey, null);
        if ((rawMessages == null || rawMessages.trim().isEmpty())
                && (rawConversation == null || rawConversation.trim().isEmpty())) {
            return;
        }

        try {
            String sessionId = generateSessionId();
            JSONObject sessionData = new JSONObject();
            sessionData.put("messages", rawMessages != null && !rawMessages.trim().isEmpty()
                    ? new JSONArray(rawMessages) : new JSONArray());
            sessionData.put("orderState", rawOrderState != null && !rawOrderState.trim().isEmpty()
                    ? new JSONObject(rawOrderState) : new JSONObject());
            sessionData.put("conversation", rawConversation != null ? rawConversation : "[]");

            ChatSessionInfo info = new ChatSessionInfo(sessionId);
            info.title = deriveTitleFromRawMessages(rawMessages);
            info.lastMessage = derivePreviewFromRawMessages(rawMessages);
            info.updatedAt = System.currentTimeMillis();

            List<ChatSessionInfo> migrated = new ArrayList<>();
            migrated.add(info);

            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(indexKey, serializeSessionHistory(migrated).toString());
            editor.putString(getScopedKey(KEY_ACTIVE_SESSION_ID), sessionId);
            editor.putString(getSessionDataKey(sessionId), sessionData.toString());
            editor.remove(legacyMessagesKey);
            editor.remove(legacyOrderStateKey);
            editor.remove(legacyConversationKey);
            editor.apply();
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Legacy migration failed", e);
        }
    }

    private List<ChatSessionInfo> loadSessionHistoryFromPrefs() {
        List<ChatSessionInfo> sessions = new ArrayList<>();
        String rawIndex = getChatPrefs().getString(getScopedKey(KEY_SESSIONS_INDEX), null);
        if (rawIndex == null || rawIndex.trim().isEmpty()) return sessions;

        try {
            JSONArray array = new JSONArray(rawIndex);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                ChatSessionInfo info = new ChatSessionInfo(obj.optString("id", ""));
                if (info.id.isEmpty()) continue;
                info.title = obj.optString("title", DEFAULT_SESSION_TITLE);
                info.lastMessage = obj.optString("lastMessage", "");
                info.updatedAt = obj.optLong("updatedAt", 0L);
                sessions.add(info);
            }
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Load session index failed", e);
        }

        sortSessions(sessions);
        return sessions;
    }

    private JSONArray serializeSessionHistory(List<ChatSessionInfo> sessions) {
        JSONArray array = new JSONArray();
        for (ChatSessionInfo info : sessions) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", info.id);
                obj.put("title", info.title != null ? info.title : DEFAULT_SESSION_TITLE);
                obj.put("lastMessage", info.lastMessage != null ? info.lastMessage : "");
                obj.put("updatedAt", info.updatedAt);
                array.put(obj);
            } catch (Exception e) {
                android.util.Log.e("ChatActivity", "Serialize session failed", e);
            }
        }
        return array;
    }

    private ChatSessionInfo findSessionInfo(List<ChatSessionInfo> sessions, String sessionId) {
        for (ChatSessionInfo info : sessions) {
            if (info.id.equals(sessionId)) return info;
        }
        return null;
    }

    private void sortSessions(List<ChatSessionInfo> sessions) {
        Collections.sort(sessions, (left, right) -> Long.compare(right.updatedAt, left.updatedAt));
    }

    private String generateSessionTitle() {
        for (ChatMessage msg : messages) {
            if (msg.isUser && msg.text != null && !msg.text.trim().isEmpty()) {
                return ellipsizeSingleLine(msg.text.trim(), 32);
            }
        }
        return DEFAULT_SESSION_TITLE;
    }

    private String buildSessionPreview() {
        if (messages.isEmpty()) return "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            String text = messages.get(i).text;
            if (text != null && !text.trim().isEmpty()) {
                return ellipsizeSingleLine(text.trim(), 52);
            }
        }
        return "";
    }

    private String deriveTitleFromRawMessages(String rawMessages) {
        String title = DEFAULT_SESSION_TITLE;
        if (rawMessages == null || rawMessages.trim().isEmpty()) return title;

        try {
            JSONArray array = new JSONArray(rawMessages);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null && obj.optBoolean("isUser", false)) {
                    String text = obj.optString("text", "").trim();
                    if (!text.isEmpty()) return ellipsizeSingleLine(text, 32);
                }
            }
        } catch (Exception ignored) {
        }
        return title;
    }

    private String derivePreviewFromRawMessages(String rawMessages) {
        if (rawMessages == null || rawMessages.trim().isEmpty()) return "";

        try {
            JSONArray array = new JSONArray(rawMessages);
            for (int i = array.length() - 1; i >= 0; i--) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                String text = obj.optString("text", "").trim();
                if (!text.isEmpty()) return ellipsizeSingleLine(text, 52);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String ellipsizeSingleLine(String text, int maxLength) {
        if (text == null) return "";
        String normalized = text.replace('\n', ' ').trim();
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, Math.max(0, maxLength - 1)).trim() + "...";
    }

    private JSONArray serializeMessages() {
        JSONArray array = new JSONArray();
        for (ChatMessage msg : messages) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("text", msg.text != null ? msg.text : "");
                obj.put("isUser", msg.isUser);
                obj.put("time", msg.time != null ? msg.time.getTime() : System.currentTimeMillis());
                obj.put("gifUrl", msg.gifUrl != null ? msg.gifUrl : JSONObject.NULL);
                obj.put("gifSearchQuery", msg.gifSearchQuery != null ? msg.gifSearchQuery : JSONObject.NULL);
                obj.put("showOrderButton", msg.showOrderButton);

                JSONArray productIds = new JSONArray();
                for (Product product : msg.suggestedProducts) {
                    if (product != null && product.getId() != null) productIds.put(product.getId());
                }
                obj.put("productIds", productIds);

                JSONArray actions = new JSONArray();
                for (ChatAction action : msg.quickActions) {
                    JSONObject actionObj = new JSONObject();
                    actionObj.put("label", action.label != null ? action.label : "");
                    actionObj.put("type", action.type != null ? action.type : "");
                    actionObj.put("value", action.value != null ? action.value : "");
                    actions.put(actionObj);
                }
                obj.put("quickActions", actions);
                array.put(obj);
            } catch (Exception e) {
                android.util.Log.e("ChatActivity", "Serialize message failed", e);
            }
        }
        return array;
    }

    private JSONObject serializeOrderState() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("pendingCoupon", pendingCoupon != null ? pendingCoupon : "");
            obj.put("lastUserMessage", lastUserMessage != null ? lastUserMessage : "");
            obj.put("pendingSelectedSize", pendingSelectedSize != null ? pendingSelectedSize : "");
            obj.put("pendingQuantity", pendingQuantity);
            obj.put("chatOrderStage", chatOrderStage != null ? chatOrderStage.name() : ChatOrderStage.IDLE.name());
            obj.put("pendingOrderProductId",
                    pendingOrderProduct != null && pendingOrderProduct.getId() != null
                            ? pendingOrderProduct.getId() : "");
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Serialize order state failed", e);
        }
        return obj;
    }

    private void restoreOrderState(String rawOrderState) {
        resetPendingOrder();
        pendingCoupon = "";
        lastUserMessage = "";

        if (rawOrderState == null || rawOrderState.trim().isEmpty()) {
            return;
        }

        try {
            JSONObject obj = new JSONObject(rawOrderState);
            pendingCoupon = obj.optString("pendingCoupon", "");
            lastUserMessage = obj.optString("lastUserMessage", "");
            pendingSelectedSize = obj.optString("pendingSelectedSize", "");
            pendingQuantity = Math.max(1, obj.optInt("pendingQuantity", 1));

            String stageName = obj.optString("chatOrderStage", ChatOrderStage.IDLE.name());
            try {
                chatOrderStage = ChatOrderStage.valueOf(stageName);
            } catch (IllegalArgumentException ignored) {
                chatOrderStage = ChatOrderStage.IDLE;
            }

            String pendingProductId = obj.optString("pendingOrderProductId", "");
            pendingOrderProduct = productMap.get(pendingProductId);
            if (pendingOrderProduct == null) {
                resetPendingOrder();
            }
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Restore order state failed", e);
            resetPendingOrder();
        }
    }

    private void showChatHistorySheet() {
        saveChatState();
        sessionHistory.clear();
        sessionHistory.addAll(loadSessionHistoryFromPrefs());

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_chat_history, null, false);
        RecyclerView rvSessions = content.findViewById(R.id.rv_chat_sessions);
        TextView tvEmpty = content.findViewById(R.id.tv_chat_sessions_empty);
        View btnNewChat = content.findViewById(R.id.btn_start_new_chat);

        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        ChatSessionAdapter sessionAdapter = new ChatSessionAdapter(new ArrayList<>(sessionHistory),
                session -> {
                    dialog.dismiss();
                    if (!session.id.equals(activeSessionId)) {
                        saveChatState();
                        restoreSessionById(session.id);
                    }
                },
                session -> deleteSessionWithConfirmation(session, dialog));
        rvSessions.setAdapter(sessionAdapter);

        tvEmpty.setVisibility(sessionHistory.isEmpty() ? View.VISIBLE : View.GONE);
        rvSessions.setVisibility(sessionHistory.isEmpty() ? View.GONE : View.VISIBLE);
        btnNewChat.setOnClickListener(v -> {
            dialog.dismiss();
            startNewChat();
        });

        dialog.setContentView(content);
        dialog.show();
    }

    private void deleteSessionWithConfirmation(ChatSessionInfo session, android.app.Dialog dialog) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Xóa đoạn chat")
                .setMessage("Đoạn chat này sẽ bị xóa khỏi lịch sử.")
                .setPositiveButton("Xóa", (d, w) -> {
                    removeSession(session.id);
                    if (dialog != null && dialog.isShowing()) dialog.dismiss();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void removeSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return;

        List<ChatSessionInfo> sessions = loadSessionHistoryFromPrefs();
        ChatSessionInfo removed = findSessionInfo(sessions, sessionId);
        if (removed == null) return;

        sessions.remove(removed);
        SharedPreferences.Editor editor = getChatPrefs().edit();
        editor.remove(getSessionDataKey(sessionId));
        editor.putString(getScopedKey(KEY_SESSIONS_INDEX), serializeSessionHistory(sessions).toString());

        boolean removingActive = sessionId.equals(activeSessionId);
        if (!removingActive) {
            editor.apply();
            sessionHistory.clear();
            sessionHistory.addAll(sessions);
            Toast.makeText(this, "Đã xóa đoạn chat", Toast.LENGTH_SHORT).show();
            return;
        }

        if (sessions.isEmpty()) {
            editor.remove(getScopedKey(KEY_ACTIVE_SESSION_ID));
            editor.apply();
            activeSessionId = "";
            sessionHistory.clear();
            createFreshSession(true);
        } else {
            String nextSessionId = sessions.get(0).id;
            editor.putString(getScopedKey(KEY_ACTIVE_SESSION_ID), nextSessionId);
            editor.apply();
            sessionHistory.clear();
            sessionHistory.addAll(sessions);
            restoreSessionById(nextSessionId);
        }

        Toast.makeText(this, "Đã xóa đoạn chat", Toast.LENGTH_SHORT).show();
    }

    // ─── Data class ───
    static class ChatSessionInfo {
        String id;
        String title = DEFAULT_SESSION_TITLE;
        String lastMessage = "";
        long updatedAt = 0L;

        ChatSessionInfo(String id) {
            this.id = id;
        }
    }

    static class SizeAdvice {
        String recommendedSize;
        String message;
    }

    static class ChatAction {
        String label;
        String type;
        String value;

        ChatAction(String label, String type, String value) {
            this.label = label;
            this.type = type;
            this.value = value;
        }
    }

    static class ChatMessage {
        String text;
        boolean isUser;
        Date time;
        String gifUrl;
        String gifSearchQuery;
        List<Product> suggestedProducts = new ArrayList<>();
        List<Product> taggedAddProducts = new ArrayList<>();
        List<ChatAction> quickActions = new ArrayList<>();
        boolean showOrderButton = false;

        ChatMessage(String text, boolean isUser, Date time) {
            this.text = text;
            this.isUser = isUser;
            this.time = time;
        }
    }

    interface OnSessionClickListener {
        void onSessionClick(ChatSessionInfo session);
    }

    interface OnSessionDeleteListener {
        void onSessionDelete(ChatSessionInfo session);
    }

    private void addBotMessageWithGif(String text, String gifUrl) {
        ChatMessage msg = new ChatMessage(text, false, new Date());
        msg.gifUrl = gifUrl;
        messages.add(msg);
        int pos = messages.size() - 1;
        adapter.notifyItemInserted(pos);
        rvMessages.smoothScrollToPosition(pos);
        updateSuggestionVisibility();
        saveChatState();
    }

    class ChatSessionAdapter extends RecyclerView.Adapter<ChatSessionAdapter.SessionVH> {
        private final List<ChatSessionInfo> items;
        private final OnSessionClickListener clickListener;
        private final OnSessionDeleteListener deleteListener;

        ChatSessionAdapter(List<ChatSessionInfo> items,
                           OnSessionClickListener clickListener,
                           OnSessionDeleteListener deleteListener) {
            this.items = items;
            this.clickListener = clickListener;
            this.deleteListener = deleteListener;
        }

        @NonNull
        @Override
        public SessionVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_session, parent, false);
            return new SessionVH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SessionVH holder, int position) {
            ChatSessionInfo session = items.get(position);
            holder.tvTitle.setText(session.title != null && !session.title.isEmpty()
                    ? session.title : DEFAULT_SESSION_TITLE);
            holder.tvPreview.setText(session.lastMessage != null && !session.lastMessage.isEmpty()
                    ? session.lastMessage : "Chưa có tin nhắn");
            holder.tvTime.setText(session.updatedAt > 0
                    ? sessionTimeFmt.format(new Date(session.updatedAt)) : "Vừa xong");

            boolean isActive = session.id.equals(activeSessionId);
            holder.tvActive.setVisibility(isActive ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> clickListener.onSessionClick(session));
            holder.btnDelete.setOnClickListener(v -> deleteListener.onSessionDelete(session));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class SessionVH extends RecyclerView.ViewHolder {
            final TextView tvTitle;
            final TextView tvPreview;
            final TextView tvTime;
            final TextView tvActive;
            final ImageView btnDelete;

            SessionVH(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tv_chat_session_title);
                tvPreview = itemView.findViewById(R.id.tv_chat_session_preview);
                tvTime = itemView.findViewById(R.id.tv_chat_session_time);
                tvActive = itemView.findViewById(R.id.tv_chat_session_active);
                btnDelete = itemView.findViewById(R.id.btn_delete_chat_session);
            }
        }
    }

    // ─── Adapter ───
    class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            ChatMessage msg = messages.get(pos);
            String timeStr = timeFmt.format(msg.time);

            if (msg.isUser) {
                h.layoutUser.setVisibility(View.VISIBLE);
                h.layoutBot.setVisibility(View.GONE);
                h.tvUser.setText(msg.text);
                h.tvUserTime.setText(timeStr);
            } else {
                h.layoutUser.setVisibility(View.GONE);
                h.layoutBot.setVisibility(View.VISIBLE);
                h.tvBot.setText(msg.text);
                h.tvBotTime.setText(timeStr);

                // Product cards
                h.layoutProductCards.removeAllViews();
                if (!msg.suggestedProducts.isEmpty()) {
                    h.scrollProducts.setVisibility(View.VISIBLE);
                    for (Product p : msg.suggestedProducts) {
                        View card = LayoutInflater.from(h.itemView.getContext())
                                .inflate(R.layout.item_chat_product_card, h.layoutProductCards, false);
                        bindProductCard(card, p);
                        h.layoutProductCards.addView(card);
                    }
                } else {
                    h.scrollProducts.setVisibility(View.GONE);
                }

                h.layoutQuickActions.removeAllViews();
                if (!msg.quickActions.isEmpty()) {
                    h.scrollQuickActions.setVisibility(View.VISIBLE);
                    for (ChatAction action : msg.quickActions) {
                        TextView chip = new TextView(h.itemView.getContext());
                        chip.setText(action.label);
                        chip.setTextSize(12f);
                        chip.setTextColor(getColor(R.color.blue_primary));
                        chip.setBackgroundResource(R.drawable.bg_chip_suggestion);
                        chip.setPadding(dp(14), dp(9), dp(14), dp(9));
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        lp.setMarginEnd(dp(8));
                        chip.setLayoutParams(lp);
                        chip.setClickable(true);
                        chip.setFocusable(true);
                        chip.setOnClickListener(v -> handleChatAction(action));
                        h.layoutQuickActions.addView(chip);
                    }
                } else {
                    h.scrollQuickActions.setVisibility(View.GONE);
                }

                // Order button
                if (msg.showOrderButton) {
                    h.btnOrder.setVisibility(View.VISIBLE);
                    h.btnOrder.setOnClickListener(v -> checkBeforeOrder());
                } else {
                    h.btnOrder.setVisibility(View.GONE);
                }

                // GIF meme
                if (msg.gifUrl != null && !msg.gifUrl.isEmpty()) {
                    h.imgGif.setVisibility(View.VISIBLE);
                    com.bumptech.glide.Glide.with(h.imgGif.getContext())
                            .asGif()
                            .load(msg.gifUrl)
                            .into(h.imgGif);
                } else {
                    h.imgGif.setVisibility(View.GONE);
                }
            }

            // Slide-in animation
            TranslateAnimation anim = new TranslateAnimation(
                    msg.isUser ? 200 : -200, 0, 0, 0);
            anim.setDuration(250);
            anim.setInterpolator(new AccelerateDecelerateInterpolator());
            h.itemView.startAnimation(anim);
        }

        private void bindProductCard(View card, Product p) {
            // Image
            ImageView img = card.findViewById(R.id.img_chat_product);
            String imgStr = p.getImageUrl();
            if (imgStr != null && !imgStr.isEmpty()) {
                try {
                    byte[] b = Base64.decode(imgStr, Base64.DEFAULT);
                    img.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
                } catch (Exception e) {
                    img.setImageResource(android.R.color.darker_gray);
                }
            }

            // Name
            ((TextView) card.findViewById(R.id.tv_chat_product_name)).setText(p.getName());

            // Price
            TextView tvPrice = card.findViewById(R.id.tv_chat_product_price);
            TextView tvOriginal = card.findViewById(R.id.tv_chat_product_original_price);

            if (p.getDiscountPercent() > 0) {
                tvPrice.setText(fmt.format(p.getSalePrice()) + " đ");
                tvOriginal.setVisibility(View.VISIBLE);
                tvOriginal.setText(fmt.format(p.getPrice()) + " đ");
                tvOriginal.setPaintFlags(tvOriginal.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                tvPrice.setText(fmt.format(p.getPrice()) + " đ");
                tvOriginal.setVisibility(View.GONE);
            }

            TextView tvSizes = card.findViewById(R.id.tv_chat_product_sizes);
            List<String> sizes = getSortedSizes(p);
            tvSizes.setText(productHasSizes(p)
                    ? "Size có sẵn: " + formatSizes(sizes)
                    : "Mẫu không yêu cầu chọn size");

            TextView btnBuy = card.findViewById(R.id.btn_chat_buy);
            btnBuy.setText(productHasSizes(p) ? "Chọn size" : "Mua nhanh");
            btnBuy.setOnClickListener(v -> beginChatPurchase(p, null));

            card.setOnClickListener(v -> {
                Intent intent = new Intent(ChatActivity.this, com.example.appbangiay.customer.ProductDetailActivity.class);
                intent.putExtra("product_id", p.getId());
                startActivity(intent);
            });
        }

        private int dp(int value) {
            return (int) (value * ChatActivity.this.getResources().getDisplayMetrics().density);
        }

        @Override public int getItemCount() { return messages.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvUser, tvBot, tvUserTime, tvBotTime, btnOrder;
            LinearLayout layoutUser, layoutBot, layoutProductCards, layoutQuickActions;
            HorizontalScrollView scrollProducts, scrollQuickActions;
            ImageView imgGif;

            VH(View v) {
                super(v);
                tvUser = v.findViewById(R.id.tv_user_msg);
                tvBot = v.findViewById(R.id.tv_bot_msg);
                tvUserTime = v.findViewById(R.id.tv_user_time);
                tvBotTime = v.findViewById(R.id.tv_bot_time);
                layoutUser = v.findViewById(R.id.layout_user_msg);
                layoutBot = v.findViewById(R.id.layout_bot_msg);
                scrollProducts = v.findViewById(R.id.scroll_products);
                layoutProductCards = v.findViewById(R.id.layout_product_cards);
                scrollQuickActions = v.findViewById(R.id.scroll_quick_actions);
                layoutQuickActions = v.findViewById(R.id.layout_quick_actions);
                btnOrder = v.findViewById(R.id.btn_chat_order);
                imgGif = v.findViewById(R.id.img_gif);
            }
        }
    }
}
