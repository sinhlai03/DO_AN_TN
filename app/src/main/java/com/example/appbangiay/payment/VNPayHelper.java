package com.example.appbangiay.payment;

import android.util.Log;

import com.example.appbangiay.BuildConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * VNPAY Sandbox payment helper.
 * Builds payment URL entirely client-side — no backend required.
 * Follows official VNPAY Java demo signature algorithm.
 */
public class VNPayHelper {

    private static final String TAG = "VNPay";

    private static final String TMN_CODE    = BuildConfig.VNPAY_TMN_CODE;
    private static final String HASH_SECRET = BuildConfig.VNPAY_HASH_SECRET;
    private static final String PAY_URL     = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final String RETURN_URL  = "appbangiay://vnpay-return";
    private static final String VERSION     = "2.1.0";

    public interface PaymentCallback {
        void onSuccess(String payUrl, String txnRef);
        void onError(String message);
    }

    public static void createPaymentUrl(long amount, String orderInfo, PaymentCallback callback) {
        if (TMN_CODE.isEmpty() || HASH_SECRET.isEmpty()) {
            callback.onError("Thiếu cấu hình VNPAY trong secrets.properties");
            return;
        }

        try {
            String txnRef = "ORDER" + getRandomNumber(8);

            // Timezone theo VNPAY official Java demo
            Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
            String createDate = formatter.format(cld.getTime());
            cld.add(Calendar.MINUTE, 15);
            String expireDate = formatter.format(cld.getTime());

            Map<String, String> params = new HashMap<>();
            params.put("vnp_Version",    VERSION);
            params.put("vnp_Command",    "pay");
            params.put("vnp_TmnCode",    TMN_CODE);
            params.put("vnp_Amount",     String.valueOf(amount * 100));
            params.put("vnp_CreateDate", createDate);
            params.put("vnp_CurrCode",   "VND");
            params.put("vnp_IpAddr",     "127.0.0.1");
            params.put("vnp_Locale",     "vn");
            params.put("vnp_OrderInfo",  orderInfo);
            params.put("vnp_OrderType",  "other");
            params.put("vnp_ReturnUrl",  RETURN_URL);
            params.put("vnp_TxnRef",     txnRef);
            params.put("vnp_ExpireDate", expireDate);

            // Sort alphabetically
            List<String> fieldNames = new ArrayList<>(params.keySet());
            Collections.sort(fieldNames);

            // Build hashData + query — theo đúng VNPAY official Java demo:
            // hashData: fieldName (raw) + '=' + URLEncode(value, US_ASCII)
            // query   : URLEncode(key) + '=' + URLEncode(value, US_ASCII)
            StringBuilder hashData = new StringBuilder();
            StringBuilder query    = new StringBuilder();
            Iterator<String> itr   = fieldNames.iterator();
            while (itr.hasNext()) {
                String fieldName  = itr.next();
                String fieldValue = params.get(fieldName);
                if (fieldValue != null && fieldValue.length() > 0) {
                    hashData.append(fieldName).append('=')
                            .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()))
                         .append('=')
                         .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    if (itr.hasNext()) {
                        hashData.append('&');
                        query.append('&');
                    }
                }
            }

            String secureHash = hmacSHA512(HASH_SECRET, hashData.toString());
            query.append("&vnp_SecureHash=").append(secureHash);

            String fullUrl = PAY_URL + "?" + query;
            Log.d(TAG, "PayURL: " + fullUrl);
            callback.onSuccess(fullUrl, txnRef);

        } catch (Exception e) {
            Log.e(TAG, "Error building VNPAY URL", e);
            callback.onError("Lỗi tạo thanh toán: " + e.getMessage());
        }
    }

    private static String getRandomNumber(int len) {
        Random rnd = new Random();
        String chars = "0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    private static String hmacSHA512(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
