package com.example.appbangiay.util;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

import org.json.JSONObject;

public class CloudinaryUploader {

    private static final String TAG = "CloudinaryUploader";
    private static final String CLOUD_NAME = "dfdsj2ena";
    private static final String UPLOAD_PRESET = "videogiay";
    private static final String UPLOAD_URL = "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/video/upload";

    public interface UploadCallback {
        void onSuccess(String videoUrl);
        void onError(String error);
    }

    public static void uploadVideo(ContentResolver resolver, Uri videoUri, UploadCallback callback) {
        new Thread(() -> {
            try {
                // Read video bytes
                InputStream is = resolver.openInputStream(videoUri);
                if (is == null) { callback.onError("Không đọc được file"); return; }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
                is.close();
                byte[] videoBytes = baos.toByteArray();

                // Multipart upload
                String boundary = UUID.randomUUID().toString();
                URL url = new URL(UPLOAD_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setDoOutput(true);
                conn.setConnectTimeout(60000);
                conn.setReadTimeout(120000);

                OutputStream os = conn.getOutputStream();

                // upload_preset field
                writeField(os, boundary, "upload_preset", UPLOAD_PRESET);

                // file field
                os.write(("--" + boundary + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"file\"; filename=\"video.mp4\"\r\n").getBytes());
                os.write(("Content-Type: video/mp4\r\n\r\n").getBytes());
                os.write(videoBytes);
                os.write("\r\n".getBytes());

                // End
                os.write(("--" + boundary + "--\r\n").getBytes());
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                java.util.Scanner scanner = new java.util.Scanner(
                        code == 200 ? conn.getInputStream() : conn.getErrorStream(), "UTF-8"
                ).useDelimiter("\\A");
                String response = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                conn.disconnect();

                Log.d(TAG, "Response " + code + ": " + response);

                if (code == 200) {
                    JSONObject json = new JSONObject(response);
                    String secureUrl = json.getString("secure_url");
                    callback.onSuccess(secureUrl);
                } else {
                    callback.onError("Upload lỗi: " + code);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage(), e);
                callback.onError("Lỗi: " + e.getMessage());
            }
        }).start();
    }

    private static void writeField(OutputStream os, String boundary, String name, String value) throws Exception {
        os.write(("--" + boundary + "\r\n").getBytes());
        os.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes());
        os.write((value + "\r\n").getBytes());
    }
}
