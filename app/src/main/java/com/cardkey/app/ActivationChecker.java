package com.cardkey.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActivationChecker {

    private static final String ACTIVATION_URL =
            "https://sharechain.qq.com/8a6f6fefa3dbd6588fb816a22d3dbbcf?qq_aio_chat_type=2";
    private static final String TIME_URL = "https://time.is/China";
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;
    private static final int MAX_RETRIES = 3;

    public ActivationResult verifyActivation(String deviceId) {
        String content = fetchUrlWithRetry(ACTIVATION_URL);
        if (content == null) {
            ActivationResult result = new ActivationResult(false);
            result.message = "无法连接激活服务器";
            return result;
        }

        String expiryStr = parseActivationData(content, deviceId);
        if (expiryStr == null) {
            ActivationResult result = new ActivationResult(false);
            result.message = "未找到此设备的激活记录";
            return result;
        }

        Date expiryDate = parseExpiryTime(expiryStr);
        if (expiryDate == null) {
            ActivationResult result = new ActivationResult(false);
            result.message = "激活时间格式无效";
            return result;
        }

        Date currentChinaTime = fetchCurrentChinaTime();
        if (currentChinaTime == null) {
            currentChinaTime = new Date();
        }

        ActivationResult result = new ActivationResult(false);
        result.expiryDate = expiryDate;

        if (currentChinaTime.before(expiryDate)) {
            result.success = true;
            result.message = "验证通过";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm", Locale.getDefault());
            result.message = "卡密已过期 (过期时间: "
                    + sdf.format(expiryDate) + ")";
        }

        return result;
    }

    private String parseActivationData(String content, String deviceId) {
        Pattern pattern = Pattern.compile(
                "(" + Pattern.quote(deviceId) + ")-(\\d{12})");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return null;
    }

    private Date parseExpiryTime(String timeStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(
                    "yyyyMMddHHmm", Locale.getDefault());
            sdf.setLenient(false);
            return sdf.parse(timeStr);
        } catch (Exception e) {
            return null;
        }
    }

    private Date fetchCurrentChinaTime() {
        String html = fetchUrlWithRetry(TIME_URL);
        if (html == null) {
            return null;
        }

        try {
            Pattern timePattern = Pattern.compile(
                    "(\\d{1,2}:\\d{2}:\\d{2})");
            Matcher matcher = timePattern.matcher(html);
            if (matcher.find()) {
                String timeStr = matcher.group(1);
                String[] parts = timeStr.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                int second = Integer.parseInt(parts[2]);

                java.util.Calendar cal = java.util.Calendar.getInstance(
                        java.util.TimeZone.getTimeZone("Asia/Shanghai"));
                cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
                cal.set(java.util.Calendar.MINUTE, minute);
                cal.set(java.util.Calendar.SECOND, second);
                return cal.getTime();
            }
        } catch (Exception e) {
            // fallback to system time
        }

        return new Date();
    }

    private String fetchUrlWithRetry(String urlStr) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            String result = fetchUrl(urlStr);
            if (result != null) {
                return result;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }
        return null;
    }

    private String fetchUrl(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return null;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();

        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static class ActivationResult {
        public boolean success;
        public String message;
        public Date expiryDate;

        public ActivationResult(boolean success) {
            this.success = success;
            this.message = "";
            this.expiryDate = null;
        }
    }
}
