package app.miuix.tavern.data;

import android.util.Base64;

import app.miuix.tavern.model.AppConfig;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.ChatMessage;
import app.miuix.tavern.model.GroupChat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Cross-platform payload used inside the encrypted remote-sync envelope. */
public final class SyncPayloadCodec {
    public static final String FORMAT = "kirachat-sync-payload";
    public static final int SCHEMA = 1;
    public static final int MAX_PAYLOAD_BYTES = 180 * 1024 * 1024;
    private static final int MAX_ASSET_BYTES = 20 * 1024 * 1024;
    private static final long MAX_TOTAL_ASSET_BYTES = 128L * 1024L * 1024L;

    private SyncPayloadCodec() {
    }

    public static byte[] encode(LocalStore store) throws Exception {
        long[] assetBytes = {0L};
        JSONArray characters = new JSONArray();
        for (CharacterCard card : store.getCharacters()) {
            JSONObject value = new JSONObject()
                    .put("id", card.id)
                    .put("name", card.name)
                    .put("description", card.description)
                    .put("personality", card.personality)
                    .put("scenario", card.scenario)
                    .put("firstMessage", card.firstMessage)
                    .put("exampleDialogue", card.exampleDialogue)
                    .put("creatorNotes", card.creatorNotes)
                    .put("worldBookJSON", card.worldBookJson)
                    .put("lastUsed", card.lastUsed)
                    .put("unread", card.unread)
                    .put("muted", card.muted)
                    .put("pinned", card.pinned);
            putFile(value, "avatarData", card.avatarPath, assetBytes);
            putFile(value, "chatBackgroundData", card.chatBackgroundPath, assetBytes);
            characters.put(value);
        }

        JSONArray groups = new JSONArray();
        JSONObject messages = new JSONObject();
        for (GroupChat group : store.getGroups()) {
            JSONArray memberIds = new JSONArray();
            for (String id : group.members) memberIds.put(id);
            JSONObject value = new JSONObject()
                    .put("id", group.id)
                    .put("name", group.name)
                    .put("memberIDs", memberIds)
                    .put("lastUsed", group.lastUsed)
                    .put("unread", group.unread)
                    .put("muted", group.muted)
                    .put("pinned", group.pinned);
            putFile(value, "chatBackgroundData", group.chatBackgroundPath, assetBytes);
            groups.put(value);
            messages.put(group.conversationId(), encodeMessages(
                    store.getMessages(group.conversationId()), assetBytes));
        }
        for (CharacterCard card : store.getCharacters()) {
            messages.put(card.id, encodeMessages(store.getMessages(card.id), assetBytes));
        }

        AppConfig config = store.getConfig();
        JSONObject settings = new JSONObject()
                .put("persona", config.persona)
                .put("webSearch", config.webSearch)
                .put("showReasoning", config.showReasoning)
                .put("groupAutonomousMessages", config.groupAutonomousMessages);
        putFile(settings, "personaAvatarData", config.personaAvatarPath, assetBytes);

        byte[] encoded = new JSONObject()
                .put("format", FORMAT)
                .put("schemaVersion", SCHEMA)
                .put("characters", characters)
                .put("groups", groups)
                .put("messages", messages)
                .put("settings", settings)
                .toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("同步内容不能超过 180 MB");
        }
        return encoded;
    }

    public static void restore(LocalStore store, byte[] data) throws Exception {
        if (data == null || data.length == 0 || data.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("同步内容大小无效");
        }
        JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
        if (!FORMAT.equals(root.optString("format"))
                || root.optInt("schemaVersion") != SCHEMA) {
            throw new IOException("服务器同步格式不受支持");
        }
        JSONArray sourceCharacters = requiredArray(root, "characters");
        JSONArray sourceGroups = requiredArray(root, "groups");
        JSONObject sourceMessages = root.optJSONObject("messages");
        JSONObject sourceSettings = root.optJSONObject("settings");
        if (sourceMessages == null || sourceSettings == null
                || sourceCharacters.length() > 1000 || sourceGroups.length() > 1000
                || sourceMessages.length() > 2000) {
            throw new IOException("服务器同步内容不完整或超出安全限制");
        }

        JSONObject assets = new JSONObject();
        long[] assetBytes = {0L};
        Set<String> characterIds = new HashSet<>();
        JSONArray characters = new JSONArray();
        for (int i = 0; i < sourceCharacters.length(); i++) {
            JSONObject source = sourceCharacters.getJSONObject(i);
            String id = source.optString("id", "").trim();
            String name = source.optString("name", "").trim();
            if (id.isEmpty() || name.isEmpty() || !characterIds.add(id)) {
                throw new IOException("同步内容包含重复或无效角色");
            }
            JSONObject value = new JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("description", source.optString("description", ""))
                    .put("personality", source.optString("personality", ""))
                    .put("scenario", source.optString("scenario", ""))
                    .put("firstMessage", source.optString("firstMessage", ""))
                    .put("exampleDialogue", source.optString("exampleDialogue", ""))
                    .put("creatorNotes", source.optString("creatorNotes", ""))
                    .put("worldBookJson", source.optString("worldBookJSON", ""))
                    .put("lastUsed", source.optLong("lastUsed", System.currentTimeMillis()))
                    .put("unread", source.optInt("unread", 0))
                    .put("muted", source.optBoolean("muted", false))
                    .put("pinned", source.optBoolean("pinned", false))
                    .put("avatarPath", "")
                    .put("sourceAvatar", "")
                    .put("chatBackgroundPath", "");
            attachInlineAsset(source, "avatarData", value, "avatarAsset", assets, assetBytes);
            attachInlineAsset(source, "chatBackgroundData", value,
                    "chatBackgroundAsset", assets, assetBytes);
            characters.put(value);
        }

        Set<String> groupIds = new HashSet<>();
        JSONArray groups = new JSONArray();
        for (int i = 0; i < sourceGroups.length(); i++) {
            JSONObject source = sourceGroups.getJSONObject(i);
            String id = source.optString("id", "").trim();
            if (id.isEmpty() || !groupIds.add(id)) {
                throw new IOException("同步内容包含重复或无效群聊");
            }
            JSONArray members = source.optJSONArray("memberIDs");
            if (members == null) members = new JSONArray();
            JSONObject value = new JSONObject()
                    .put("id", id)
                    .put("name", source.optString("name", "新群聊"))
                    .put("members", members)
                    .put("lastUsed", source.optLong("lastUsed", System.currentTimeMillis()))
                    .put("unread", source.optInt("unread", 0))
                    .put("muted", source.optBoolean("muted", false))
                    .put("pinned", source.optBoolean("pinned", false))
                    .put("chatBackgroundPath", "");
            attachInlineAsset(source, "chatBackgroundData", value,
                    "chatBackgroundAsset", assets, assetBytes);
            groups.put(value);
        }

        JSONObject messages = new JSONObject();
        int totalMessages = 0;
        java.util.Iterator<String> conversations = sourceMessages.keys();
        while (conversations.hasNext()) {
            String conversationId = conversations.next();
            JSONArray source = sourceMessages.optJSONArray(conversationId);
            if (source == null) continue;
            totalMessages += source.length();
            if (totalMessages > 200000) {
                throw new IOException("同步消息数量超出安全限制");
            }
            JSONArray target = new JSONArray();
            for (int i = 0; i < source.length(); i++) {
                JSONObject message = source.getJSONObject(i);
                String attachment = message.optString("attachment", "none");
                String androidAttachment;
                if ("image".equals(attachment)) androidAttachment = ChatMessage.ATTACHMENT_IMAGE;
                else if ("location".equals(attachment)) androidAttachment = ChatMessage.ATTACHMENT_LOCATION;
                else if ("voiceCall".equals(attachment)) androidAttachment = ChatMessage.ATTACHMENT_VOICE_CALL;
                else androidAttachment = ChatMessage.ATTACHMENT_NONE;
                JSONObject value = new JSONObject()
                        .put("id", message.optString("id", UUID.randomUUID().toString()))
                        .put("role", message.optString("role", ChatMessage.ASSISTANT))
                        .put("content", message.optString("content", ""))
                        .put("speaker", message.optString("speaker", ""))
                        .put("attachmentType", androidAttachment)
                        .put("attachmentPath", "")
                        .put("attachmentMime", "")
                        .put("latitude", optionalDouble(message, "latitude"))
                        .put("longitude", optionalDouble(message, "longitude"))
                        .put("callDurationSeconds", message.optLong("callDurationSeconds", 0))
                        .put("timestamp", message.optLong("timestamp", System.currentTimeMillis()))
                        .put("failed", message.optBoolean("failed", false));
                attachInlineAsset(message, "imageData", value,
                        "attachmentAsset", assets, assetBytes);
                target.put(value);
            }
            messages.put(conversationId, target);
        }

        JSONObject config = store.getConfig().toJson();
        config.put("persona", sourceSettings.optString("persona", config.optString("persona", "你")))
                .put("webSearch", sourceSettings.optBoolean("webSearch", true))
                .put("showReasoning", sourceSettings.optBoolean("showReasoning", false))
                .put("groupAutonomousMessages",
                        sourceSettings.optBoolean("groupAutonomousMessages", true))
                .put("personaAvatarPath", "");
        attachInlineAsset(sourceSettings, "personaAvatarData", config,
                "personaAvatarAsset", assets, assetBytes);

        JSONObject backup = new JSONObject()
                .put("format", "kirachat-android-backup")
                .put("schemaVersion", 1)
                .put("characters", characters)
                .put("groups", groups)
                .put("messages", messages)
                .put("config", config)
                .put("assets", assets);
        store.restoreBackup(backup);
    }

    private static JSONArray encodeMessages(List<ChatMessage> source, long[] assetBytes)
            throws Exception {
        JSONArray result = new JSONArray();
        for (ChatMessage message : source) {
            String attachment = "none";
            if (ChatMessage.ATTACHMENT_IMAGE.equals(message.attachmentType)) attachment = "image";
            else if (ChatMessage.ATTACHMENT_LOCATION.equals(message.attachmentType)) attachment = "location";
            else if (ChatMessage.ATTACHMENT_VOICE_CALL.equals(message.attachmentType)) attachment = "voiceCall";
            JSONObject value = new JSONObject()
                    .put("id", message.id)
                    .put("role", message.role)
                    .put("content", message.content)
                    .put("speaker", message.speaker)
                    .put("attachment", attachment)
                    .put("latitude", message.hasLocation() ? message.latitude : JSONObject.NULL)
                    .put("longitude", message.hasLocation() ? message.longitude : JSONObject.NULL)
                    .put("callDurationSeconds", message.hasVoiceCall()
                            ? message.callDurationSeconds : JSONObject.NULL)
                    .put("timestamp", message.timestamp)
                    .put("failed", message.failed);
            if (message.hasImage()) {
                putFile(value, "imageData", message.attachmentPath, assetBytes);
            }
            result.put(value);
        }
        return result;
    }

    private static void putFile(
            JSONObject target, String key, String path, long[] total) throws Exception {
        if (path == null || path.trim().isEmpty()) return;
        File file = new File(path);
        if (!file.isFile() || !file.canRead()) return;
        if (file.length() <= 0 || file.length() > MAX_ASSET_BYTES
                || total[0] + file.length() > MAX_TOTAL_ASSET_BYTES) {
            throw new IOException("同步图片超出安全限制");
        }
        byte[] data = readFile(file, (int) file.length());
        target.put(key, Base64.encodeToString(data, Base64.NO_WRAP));
        total[0] += data.length;
    }

    private static void attachInlineAsset(
            JSONObject source, String dataKey, JSONObject target, String assetKey,
            JSONObject assets, long[] total) throws Exception {
        String encoded = source.optString(dataKey, "").trim();
        if (encoded.isEmpty()) return;
        byte[] data;
        try {
            data = Base64.decode(encoded, Base64.DEFAULT);
        } catch (IllegalArgumentException error) {
            throw new IOException("同步图片数据损坏");
        }
        if (data.length <= 0 || data.length > MAX_ASSET_BYTES
                || total[0] + data.length > MAX_TOTAL_ASSET_BYTES) {
            throw new IOException("同步图片超出安全限制");
        }
        String id = UUID.randomUUID().toString();
        assets.put(id, new JSONObject()
                .put("mime", isPng(data) ? "image/png" : "image/jpeg")
                .put("data", Base64.encodeToString(data, Base64.NO_WRAP)));
        target.put(assetKey, id);
        total[0] += data.length;
    }

    private static JSONArray requiredArray(JSONObject root, String key) throws IOException {
        JSONArray value = root.optJSONArray(key);
        if (value == null) throw new IOException("服务器同步内容缺少 " + key);
        return value;
    }

    private static double optionalDouble(JSONObject value, String key) {
        return value.isNull(key) ? 0 : value.optDouble(key, 0);
    }

    private static boolean isPng(byte[] data) {
        return data.length >= 8 && data[0] == (byte) 0x89 && data[1] == 0x50
                && data[2] == 0x4E && data[3] == 0x47;
    }

    private static byte[] readFile(File file, int expected) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream(expected)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
}
