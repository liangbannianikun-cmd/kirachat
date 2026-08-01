package app.miuix.tavern.network;

import app.miuix.tavern.BuildConfig;
import app.miuix.tavern.data.SecureStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class PointsClient {
    public interface AccountCallback {
        void onSuccess(PointsAccount account);

        void onError(String message);
    }

    public static final class PointsAccount {
        public long balance;
        public String address = "";
        public boolean checkedInToday;
        public int dailyReward;
        public String networkName = "";
        public String chainId = "";
        public String contractAddress = "";
        public String accountUrl = "";
        public String latestTxUrl = "";
        public int awarded;

        static PointsAccount fromJson(JSONObject object) throws JSONException {
            PointsAccount account = new PointsAccount();
            try {
                account.balance = Long.parseLong(object.optString("balance", "0"));
            } catch (NumberFormatException error) {
                throw new JSONException("Invalid points balance");
            }
            account.address = object.optString("address", "");
            account.checkedInToday = object.optBoolean("checkedInToday", false);
            account.dailyReward = object.optInt("dailyReward", 0);
            account.contractAddress = object.optString("contractAddress", "");
            account.accountUrl = object.optString("accountUrl", "");
            account.latestTxUrl = object.optString("latestTxUrl", "");
            account.awarded = object.optInt("awarded", 0);
            JSONObject network = object.optJSONObject("network");
            if (network != null) {
                account.networkName = network.optString("name", "");
                account.chainId = network.optString("chainId", "");
            }
            return account;
        }
    }

    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient HTTP =
            new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(45, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .build();

    private final SecureStore secureStore;
    private final String baseUrl;

    public PointsClient(SecureStore secureStore) {
        this.secureStore = secureStore;
        this.baseUrl = normalizeBaseUrl(BuildConfig.POINTS_SERVER_URL);
    }

    public boolean isConfigured() {
        return !baseUrl.isEmpty();
    }

    public void load(AccountCallback callback) {
        if (!isConfigured()) {
            callback.onError("这个版本尚未配置积分服务地址");
            return;
        }
        String token = secureStore.getPointsAccessToken();
        if (token.isEmpty()) {
            enroll(callback);
            return;
        }
        execute("GET", "/v1/points", null, token, callback);
    }

    public void claimDaily(AccountCallback callback) {
        if (!isConfigured()) {
            callback.onError("这个版本尚未配置积分服务地址");
            return;
        }
        String token = secureStore.getPointsAccessToken();
        if (token.isEmpty()) {
            enroll(new AccountCallback() {
                @Override
                public void onSuccess(PointsAccount account) {
                    claimDaily(callback);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
            return;
        }
        execute(
                "POST",
                "/v1/points/claim",
                "{\"action\":\"daily_checkin\"}",
                token,
                callback);
    }

    private void enroll(AccountCallback callback) {
        execute("POST", "/v1/points/enroll", "{}", "", new AccountCallback() {
            @Override
            public void onSuccess(PointsAccount account) {
                callback.onSuccess(account);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void execute(
            String method,
            String path,
            String json,
            String token,
            AccountCallback callback) {
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + path)
                .header("Accept", "application/json");
        if (!token.isEmpty()) builder.header("Authorization", "Bearer " + token);
        if ("POST".equals(method)) {
            builder.post(RequestBody.create(JSON, json == null ? "{}" : json));
        } else {
            builder.get();
        }
        HTTP.newCall(builder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                callback.onError(readableNetworkError(error));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody body = response.body()) {
                    String payload = body == null ? "" : body.string();
                    JSONObject object = payload.isEmpty()
                            ? new JSONObject() : new JSONObject(payload);
                    if (!response.isSuccessful()) {
                        callback.onError(object.optString(
                                "message", "积分服务返回 " + response.code()));
                        return;
                    }
                    String accessToken = object.optString("accessToken", "");
                    if (!accessToken.isEmpty()) {
                        secureStore.setPointsAccessToken(accessToken);
                    }
                    callback.onSuccess(PointsAccount.fromJson(object));
                } catch (Exception error) {
                    callback.onError("积分服务返回了无法识别的数据");
                }
            }
        });
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String readableNetworkError(IOException error) {
        String detail = error.getMessage();
        if (detail == null || detail.trim().isEmpty()) return "无法连接积分服务";
        return "无法连接积分服务 · " + detail;
    }
}
