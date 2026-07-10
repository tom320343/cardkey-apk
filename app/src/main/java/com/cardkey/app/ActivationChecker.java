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
            "https://sharechain.qq.com/8a6f6fefa3dbd6588fb816a22d3bddcf?qq_aio_chat_type=2";
    private static final String TIME_URL = "https://time.is/China";
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 15000;
    private static final int MAX_RETRIES = 3;

    public ActivationResult verifyActivation(String deviceId) {
        String content = fetchUrlWithRetry(ACTIVATION_URL);
        if (content == null) {
            ActivationResult result = new ActivationResult(false);
            result.message = "无法连接激活服务器";
            result.debugInfo = "网络请求失败";
            return result;
        }

        String expiryStr = parseActivationData(content, deviceId);
        if (expiryStr == null) {
            ActivationResult result = new ActivationResult(false);
            result.message = "未找到此设备的激活记录";
            result.debugInfo = extractDebugInfo(content, deviceId);
            return result;
        }

        Date expiryDate = parseExpiryTime(expiryStr);
        if (expiryDate == null) {
            ActivationResult result = new ActivationResult(false);
            result.message = "激活时间格式无效: " + expiryStr;
            result.debugInfo = expiryStr;
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
        String expiry;

        expiry = extractFromJsonHtmlContent(content, deviceId);
        if (expiry != null) return expiry;

        expiry = extractFromTitSpan(content, deviceId);
        if (expiry != null) return expiry;

        expiry = extractFromCleanText(content, deviceId);
        if (expiry != null) return expiry;

        return null;
    }

    private String extractFromJsonHtmlContent(String content, String deviceId) {
        Pattern p = Pattern.compile(
                "\"html_content\"\\s*:\\s*\"(" + Pattern.quote(deviceId) + ")-(\\d{12})\"");
        Matcher m = p.matcher(content);
        if (m.find()) {
            return m.group(2);
        }
        return null;
    }

    private String extractFromTitSpan(String content, String deviceId) {
        Pattern p = Pattern.compile(
                "<span[^>]*class=\"tit\"[^>]*>\\s*(" + Pattern.quote(deviceId) + ")-(\\d{12})\\s*</span>");
        Matcher m = p.matcher(content);
        if (m.find()) {
            return m.group(2);
        }
        return null;
    }

    private String extractFromCleanText(String content, String deviceId) {
        String cleaned = content.replaceAll("<[^>]+>", " ");
        cleaned = cleaned.replaceAll("&[a-z]+;", " ");
        cleaned = cleaned.replaceAll("\\s+", " ");
        Pattern p = Pattern.compile(
                "(" + Pattern.quote(deviceId) + ")-(\\d{12})");
        Matcher m = p.matcher(cleaned);
        if (m.find()) {
            return m.group(2);
        }
        return null;
    }

    private String extractDebugInfo(String content, String deviceId) {
        String cleaned = content.replaceAll("<[^>]+>", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 500) {
            cleaned = cleaned.substring(0, 500);
        }
        return "搜索ID: " + deviceId + " | 页面内容: " + cleaned;
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
            Pattern timePattern = Pattern.compile("(\\d{1,2}:\\d{2}:\\d{2})");
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
        }

        return new Date();
    }

    private String fetchUrlWithRetry(String urlStr) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            String result = fetchUrl(urlStr);
            if (result != null && !result.isEmpty()) {
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
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            conn.setInstanceFollowRedirects(true);

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
        public String debugInfo;
        public Date expiryDate;

        public ActivationResult(boolean success) {
            this.success = success;
            this.message = "";
            this.debugInfo = "";
            this.expiryDate = null;
        }
    }
}
