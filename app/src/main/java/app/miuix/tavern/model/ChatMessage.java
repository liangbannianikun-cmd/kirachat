package app.miuix.tavern.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public final class ChatMessage {
    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";
    public static final String SYSTEM = "system";
    public static final String ATTACHMENT_NONE = "";
    public static final String ATTACHMENT_IMAGE = "image";
    public static final String ATTACHMENT_LOCATION = "location";

    public String id;
    public String role;
    public String content;
    public String speaker;
    public String attachmentType;
    public String attachmentPath;
    public String attachmentMime;
    public double latitude;
    public double longitude;
    public long timestamp;
    public boolean failed;

    public ChatMessage(String role, String content) {
        this.id = UUID.randomUUID().toString();
        this.role = role;
        this.content = content == null ? "" : content;
        this.speaker = "";
        this.attachmentType = ATTACHMENT_NONE;
        this.attachmentPath = "";
        this.attachmentMime = "";
        this.timestamp = System.currentTimeMillis();
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("id", id)
                .put("role", role)
                .put("content", content)
                .put("speaker", speaker)
                .put("attachmentType", attachmentType)
                .put("attachmentPath", attachmentPath)
                .put("attachmentMime", attachmentMime)
                .put("latitude", latitude)
                .put("longitude", longitude)
                .put("timestamp", timestamp)
                .put("failed", failed);
    }

    public static ChatMessage fromJson(JSONObject object) {
        ChatMessage message = new ChatMessage(
                object.optString("role", ASSISTANT),
                object.optString("content", ""));
        message.id = object.optString("id", message.id);
        message.speaker = object.optString("speaker", "");
        message.attachmentType = object.optString("attachmentType", ATTACHMENT_NONE);
        message.attachmentPath = object.optString("attachmentPath", "");
        message.attachmentMime = object.optString("attachmentMime", "");
        message.latitude = object.optDouble("latitude", 0);
        message.longitude = object.optDouble("longitude", 0);
        message.timestamp = object.optLong("timestamp", System.currentTimeMillis());
        message.failed = object.optBoolean("failed", false);
        return message;
    }

    public boolean hasImage() {
        return ATTACHMENT_IMAGE.equals(attachmentType)
                && attachmentPath != null
                && !attachmentPath.trim().isEmpty();
    }

    public boolean hasLocation() {
        return ATTACHMENT_LOCATION.equals(attachmentType);
    }
}
