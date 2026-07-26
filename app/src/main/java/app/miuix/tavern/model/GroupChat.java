package app.miuix.tavern.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GroupChat {
    public static final String CONVERSATION_PREFIX = "group:";

    public String id;
    public String name;
    public String chatId;
    public String avatarUrl;
    public List<String> members;
    public long lastUsed;
    public int unread;
    public boolean muted;
    public boolean pinned;
    public String chatBackgroundPath;

    public GroupChat() {
        id = UUID.randomUUID().toString();
        name = "新群聊";
        chatId = "MiuTavern-" + System.currentTimeMillis();
        avatarUrl = "";
        members = new ArrayList<>();
        chatBackgroundPath = "";
        lastUsed = System.currentTimeMillis();
    }

    public String conversationId() {
        return CONVERSATION_PREFIX + id;
    }

    public JSONObject toJson() throws JSONException {
        JSONArray memberArray = new JSONArray();
        for (String member : members) memberArray.put(member);
        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("chat_id", chatId)
                .put("avatar_url", avatarUrl)
                .put("members", memberArray)
                .put("lastUsed", lastUsed)
                .put("unread", unread)
                .put("muted", muted)
                .put("pinned", pinned)
                .put("chatBackgroundPath", chatBackgroundPath);
    }

    public static GroupChat fromJson(JSONObject object) {
        GroupChat group = new GroupChat();
        group.id = object.optString("id", group.id);
        group.name = object.optString("name", group.name);
        group.chatId = object.optString("chat_id", object.optString("chatId", group.chatId));
        group.avatarUrl = object.optString("avatar_url", "");
        group.lastUsed = object.optLong("lastUsed",
                object.optLong("date_last_chat", System.currentTimeMillis()));
        group.unread = object.optInt("unread", 0);
        group.muted = object.optBoolean("muted", false);
        group.pinned = object.optBoolean("pinned", false);
        group.chatBackgroundPath = object.optString("chatBackgroundPath", "");
        group.members.clear();
        JSONArray members = object.optJSONArray("members");
        if (members != null) {
            for (int i = 0; i < members.length(); i++) {
                String avatar = members.optString(i, "").trim();
                if (!avatar.isEmpty()) group.members.add(avatar);
            }
        }
        return group;
    }
}
