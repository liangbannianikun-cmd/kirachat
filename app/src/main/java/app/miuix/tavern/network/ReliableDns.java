package app.miuix.tavern.network;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Dns;

/**
 * Uses an HTTPS DNS answer for the ChatGPT host, then falls back to Android's
 * resolver. This avoids poisoned A records while preserving TLS/SNI for the
 * original hostname.
 */
final class ReliableDns implements Dns {
    static final ReliableDns INSTANCE = new ReliableDns();

    private ReliableDns() {
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws java.net.UnknownHostException {
        if ("chatgpt.com".equalsIgnoreCase(hostname)) {
            try {
                List<InetAddress> secure = lookupAOverHttps(hostname);
                if (!secure.isEmpty()) return secure;
            } catch (Exception ignored) {
            }
        }
        return Dns.SYSTEM.lookup(hostname);
    }

    private static List<InetAddress> lookupAOverHttps(String hostname)
            throws Exception {
        String query = "https://dns.google/resolve?name="
                + URLEncoder.encode(hostname, "UTF-8") + "&type=A";
        HttpURLConnection connection =
                (HttpURLConnection) new URL(query).openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(8_000);
        connection.setRequestProperty("Accept", "application/dns-json");
        connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 15; Mobile) "
                        + "AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36");
        try {
            if (connection.getResponseCode() != 200) return new ArrayList<>();
            String body;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                body = output.toString("UTF-8");
            }
            JSONArray answers = new JSONObject(body).optJSONArray("Answer");
            List<InetAddress> result = new ArrayList<>();
            if (answers == null) return result;
            for (int i = 0; i < answers.length(); i++) {
                JSONObject answer = answers.optJSONObject(i);
                if (answer == null || answer.optInt("type") != 1) continue;
                byte[] address = parseIpv4(answer.optString("data", ""));
                if (address != null) {
                    result.add(InetAddress.getByAddress(hostname, address));
                }
            }
            return result;
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] parseIpv4(String value) {
        String[] parts = value.split("\\.");
        if (parts.length != 4) return null;
        byte[] result = new byte[4];
        try {
            for (int i = 0; i < parts.length; i++) {
                int part = Integer.parseInt(parts[i]);
                if (part < 0 || part > 255) return null;
                result[i] = (byte) part;
            }
            return result;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
