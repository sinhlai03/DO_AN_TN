package com.example.appbangiay.chat;

import android.util.Log;

import com.example.appbangiay.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class GroqChatService {

    private static final String TAG = "GroqChat";
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";

    // Models prioritized for Vietnamese (best first)
    private static final String[] MODELS = {
            "qwen-3-32b",
            "llama-3.3-70b-versatile",
            "kimi-k2",
            "meta-llama/llama-4-scout-17b-16e-instruct"
    };
    private static final AtomicInteger modelIndex = new AtomicInteger(0);

    // Round-robin API keys
    private static final String[] API_KEYS = parseApiKeys(BuildConfig.GROQ_API_KEYS_RAW);
    private static final AtomicInteger keyIndex = new AtomicInteger(0);

    public interface ChatCallback {
        void onResponse(String reply);
        void onError(String error);
    }

    private final List<JSONObject> conversationHistory = new ArrayList<>();
    private String systemPrompt = "";


    public void setContext(String productsInfo, String couponsInfo) {
        systemPrompt = "Bạn là trợ lý tư vấn bán hàng cho cửa hàng giày ShopGiay. "
                + "Hãy trả lời bằng tiếng Việt, tự nhiên, thân thiện như một nhân viên bán hàng online nhiều kinh nghiệm.\n\n"
                + "DỮ LIỆU SẢN PHẨM HIỆN CÓ:\n" + productsInfo + "\n\n"
                + "MÃ KHUYẾN MÃI HIỆN CÓ:\n" + couponsInfo + "\n\n"
                + "QUY TẮC:\n"
                + "- Tư vấn sản phẩm phù hợp với nhu cầu khách\n"
                + "- Khi khách hỏi mua giày, luôn quan tâm tới size trước khi chốt đơn\n"
                + "- Nếu khách chưa biết size, hãy hỏi chiều dài bàn chân theo cm hoặc size thường mang\n"
                + "- Khi tư vấn size, nói theo hướng gợi ý như 'mình nghiêng về size 41' hoặc 'bạn có thể cân nhắc 42', không khẳng định tuyệt đối nếu không chắc\n"
                + "- Nếu mẫu khách chọn không có size phù hợp, hãy gợi ý mẫu khác đang có đúng size đó\n"
                + "- Gợi ý mã khuyến mãi nếu có\n"
                + "- Giá hiển thị dạng 'xxx.xxx đ'\n"
                + "- Nếu không có sản phẩm phù hợp, hãy nói rõ\n"
                + "- Không bịa thông tin ngoài dữ liệu được cung cấp\n"
                + "- Xưng hô tự nhiên bằng 'mình - bạn'\n"
                + "- Mỗi câu trả lời nên gọn trong 2-4 câu, mềm mại, không cứng kiểu máy móc\n"
                + "- Có thể mở đầu nhẹ nhàng như 'mình thấy', 'mẫu này hợp với nhu cầu của bạn', 'để mình chốt size giúp bạn'\n"
                + "- TUYỆT ĐỐI KHÔNG dùng emoji, icon, ký hiệu đặc biệt\n"
                + "- KHÔNG dùng markdown: không dùng **, ##, --, *,  dấu gạch đầu dòng\n"
                + "- Trả lời bằng văn bản thuần túy, tự nhiên như đang nói chuyện\n\n"
                + "QUAN TRỌNG - HIỂN THỊ SẢN PHẨM:\n"
                + "- Khi gợi ý sản phẩm, LUÔN thêm tag ở cuối câu trả lời: [PRODUCTS: id1, id2, id3]\n"
                + "- Trong đó id1, id2... là ID sản phẩm từ dữ liệu ở trên (phần ID: trước mỗi sản phẩm)\n"
                + "- Chỉ gợi ý tối đa 4 sản phẩm mỗi lần\n"
                + "- Tag này sẽ bị ẩn, user không nhìn thấy, app sẽ tự hiển thị card sản phẩm\n\n"
                + "QUAN TRỌNG - THÊM GIỎ HÀNG:\n"
                + "- Chỉ thêm tag [ADD_CART: id1, id2] khi khách đã chốt mẫu và size\n"
                + "- App sẽ tự động thêm sản phẩm vào giỏ hàng\n\n"
                + "QUAN TRỌNG - MÃ KHUYẾN MÃI:\n"
                + "- Khi khách muốn dùng mã khuyến mãi hoặc bạn gợi ý mã → thêm tag [COUPON: MACODE]\n"
                + "- App sẽ tự động áp dụng mã khi đặt hàng\n\n"
                + "QUAN TRỌNG - ĐẶT HÀNG:\n"
                + "- Chỉ thêm tag [ORDER] khi khách đã chốt mẫu, size và sẵn sàng thanh toán\n"
                + "- Tag [ORDER] sẽ hiển thị nút Đặt hàng trong app\n\n"
                + "QUAN TRỌNG - GIF MEME:\n"
                + "- Khi phù hợp mới thêm tag [GIF: từ khóa tiếng Anh] ở cuối câu trả lời\n"
                + "- Từ khóa phải là tiếng Anh, ngắn gọn, nhẹ nhàng, phù hợp ngữ cảnh\n"
                + "- Ví dụ: [GIF: hello wave], [GIF: thumbs up], [GIF: running shoes], [GIF: shopping happy]\n"
                + "- Tag này sẽ bị ẩn, user không nhìn thấy\n";
    }

    public void sendMessage(String userMessage, ChatCallback callback) {
        new Thread(() -> {
            try {
                if (API_KEYS.length == 0) {
                    callback.onError("Thiếu cấu hình GROQ_API_KEYS_RAW trong secrets.properties");
                    return;
                }

                // Add user message to history
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", userMessage);
                conversationHistory.add(userMsg);

                // Build messages array
                JSONArray messages = new JSONArray();

                // System prompt
                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                messages.put(sysMsg);

                // Keep last 10 messages for context
                int start = Math.max(0, conversationHistory.size() - 10);
                for (int i = start; i < conversationHistory.size(); i++) {
                    messages.put(conversationHistory.get(i));
                }

                // Try each model until one works
                String lastError = "";
                for (int attempt = 0; attempt < MODELS.length; attempt++) {
                    String model = MODELS[modelIndex.get() % MODELS.length];
                    String apiKey = API_KEYS[keyIndex.getAndUpdate(i -> (i + 1) % API_KEYS.length)];

                    Log.d(TAG, "Attempt " + (attempt + 1) + " with model: " + model);

                    JSONObject body = new JSONObject();
                    body.put("model", model);
                    body.put("messages", messages);
                    body.put("max_tokens", 1024);
                    body.put("temperature", 0.7);

                    int[] result = {0};
                    String response = callApi(body, apiKey, result);
                    int code = result[0];

                    if (code == 200) {
                        JSONObject json = new JSONObject(response);
                        String reply = json.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");

                        // Add assistant reply to history
                        JSONObject assistantMsg = new JSONObject();
                        assistantMsg.put("role", "assistant");
                        assistantMsg.put("content", reply);
                        conversationHistory.add(assistantMsg);

                        callback.onResponse(reply);
                        return;
                    }

                    // Rate limit or server error → rotate model
                    Log.w(TAG, "Model " + model + " failed (" + code + "), rotating...");
                    lastError = "Model " + model + ": " + code;
                    modelIndex.getAndUpdate(i -> (i + 1) % MODELS.length);
                }

                // All models failed
                callback.onError("Tất cả model đều lỗi. " + lastError);

            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage(), e);
                callback.onError("Lỗi kết nối: " + e.getMessage());
            }
        }).start();
    }

    private String callApi(JSONObject body, String apiKey, int[] codeOut) throws Exception {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        codeOut[0] = code;
        Scanner scanner = new Scanner(
                code == 200 ? conn.getInputStream() : conn.getErrorStream(),
                "UTF-8"
        ).useDelimiter("\\A");
        String response = scanner.hasNext() ? scanner.next() : "";
        scanner.close();
        conn.disconnect();
        return response;
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    public String exportHistory() {
        return new JSONArray(conversationHistory).toString();
    }

    public void restoreHistory(String rawHistory) {
        conversationHistory.clear();
        if (rawHistory == null || rawHistory.trim().isEmpty()) return;

        try {
            JSONArray array = new JSONArray(rawHistory);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    conversationHistory.add(item);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Restore history failed", e);
            conversationHistory.clear();
        }
    }

    private static String[] parseApiKeys(String rawKeys) {
        if (rawKeys == null || rawKeys.trim().isEmpty()) {
            return new String[0];
        }

        String[] values = rawKeys.split(",");
        List<String> keys = new ArrayList<>();
        for (String value : values) {
            String key = value.trim();
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
        return keys.toArray(new String[0]);
    }
}
