package com.example.appbangiay.payment;

import android.util.Log;

import com.example.appbangiay.BuildConfig;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MomoPaymentHelper {

    private static final String TAG = "MomoPayment";

    // MoMo Test Credentials
    private static final String PARTNER_CODE = BuildConfig.MOMO_PARTNER_CODE;
    private static final String ACCESS_KEY = BuildConfig.MOMO_ACCESS_KEY;
    private static final String SECRET_KEY = BuildConfig.MOMO_SECRET_KEY;
    private static final String API_ENDPOINT = BuildConfig.MOMO_API_ENDPOINT;
    private static final String REDIRECT_URL = BuildConfig.MOMO_REDIRECT_URL;
    private static final String IPN_URL = BuildConfig.MOMO_IPN_URL;

    public interface PaymentCallback {
        void onSuccess(String payUrl, String orderId);
        void onError(String message);
    }

    public static void createPayment(long amount, String orderInfo, PaymentCallback callback) {
        if (ACCESS_KEY.isEmpty() || SECRET_KEY.isEmpty()) {
            callback.onError("Thiếu cấu hình MoMo trong secrets.properties");
            return;
        }

        new Thread(() -> {
            try {
                String requestId = UUID.randomUUID().toString();
                String orderId = "ORDER_" + System.currentTimeMillis();
                String extraData = "";
                String requestType = "captureWallet";

                // Build raw signature
                String rawSignature = "accessKey=" + ACCESS_KEY
                        + "&amount=" + amount
                        + "&extraData=" + extraData
                        + "&ipnUrl=" + IPN_URL
                        + "&orderId=" + orderId
                        + "&orderInfo=" + orderInfo
                        + "&partnerCode=" + PARTNER_CODE
                        + "&redirectUrl=" + REDIRECT_URL
                        + "&requestId=" + requestId
                        + "&requestType=" + requestType;

                String signature = hmacSHA256(SECRET_KEY, rawSignature);

                // Build JSON body
                JSONObject body = new JSONObject();
                body.put("partnerCode", PARTNER_CODE);
                body.put("accessKey", ACCESS_KEY);
                body.put("requestId", requestId);
                body.put("amount", amount);
                body.put("orderId", orderId);
                body.put("orderInfo", orderInfo);
                body.put("redirectUrl", REDIRECT_URL);
                body.put("ipnUrl", IPN_URL);
                body.put("extraData", extraData);
                body.put("requestType", requestType);
                body.put("signature", signature);
                body.put("lang", "vi");

                Log.d(TAG, "Request: " + body.toString());

                // Send HTTP POST
                URL url = new URL(API_ENDPOINT);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                Scanner scanner = new Scanner(
                        responseCode == 200 ? conn.getInputStream() : conn.getErrorStream(),
                        "UTF-8"
                ).useDelimiter("\\A");
                String response = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                conn.disconnect();

                Log.d(TAG, "Response: " + response);

                JSONObject jsonResponse = new JSONObject(response);
                int resultCode = jsonResponse.getInt("resultCode");

                if (resultCode == 0) {
                    String payUrl = jsonResponse.getString("payUrl");
                    callback.onSuccess(payUrl, orderId);
                } else {
                    String message = jsonResponse.optString("message", "Lỗi không xác định");
                    callback.onError(message);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage(), e);
                callback.onError("Lỗi kết nối: " + e.getMessage());
            }
        }).start();
    }

    private static String hmacSHA256(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
