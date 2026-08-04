package app.miuix.tavern.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import app.miuix.tavern.R;
import app.miuix.tavern.model.AppConfig;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.ChatMessage;
import app.miuix.tavern.model.GroupChat;
import app.miuix.tavern.network.RemoteSyncManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LocalStore {
    private static final String PREFS = "miu_tavern_data_v1";
    private static final String CHARACTERS = "characters";
    private static final String GROUPS = "groups";
    private static final String CONFIG = "config";
    private static final String MODEL_CACHE_PREFIX = "model_cache_";
    private static final String DOUNAI_DELETED = "builtin_dounai_deleted";
    private static final String BACKUP_FORMAT = "kirachat-android-backup";
    private static final int BACKUP_SCHEMA = 1;
    private static final long MAX_BACKUP_ASSET_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_BACKUP_TOTAL_ASSET_BYTES = 128L * 1024L * 1024L;

    private final Context context;
    private final SharedPreferences prefs;

    public LocalStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        seedIfNeeded();
    }

    private synchronized void seedIfNeeded() {
        if (!prefs.contains(CHARACTERS)) saveCharacters(new ArrayList<>());
        migrateBuiltInCharacters();
        pruneInvalidGroups();
    }

    private void migrateBuiltInCharacters() {
        List<CharacterCard> cards = getCharacters();
        boolean changed = false;
        boolean hasDounai = false;
        boolean dounaiDeleted = prefs.getBoolean(DOUNAI_DELETED, false);
        for (int i = cards.size() - 1; i >= 0; i--) {
            String id = cards.get(i).id;
            if ("demo-xiaoyu".equals(id) || "demo-ash".equals(id) || "demo-rin".equals(id)) {
                cards.remove(i);
                changed = true;
            } else if (CharacterCard.BUILTIN_DOUNAI_ID.equals(id)) {
                if (dounaiDeleted) {
                    cards.remove(i);
                    changed = true;
                    continue;
                }
                hasDounai = true;
                CharacterCard existing = cards.get(i);
                if ((existing.description != null
                        && existing.description.contains("SillyTavern"))
                        || existing.avatarPath == null
                        || existing.avatarPath.trim().isEmpty()) {
                    String avatarPath = existing.avatarPath == null
                            || existing.avatarPath.trim().isEmpty()
                            ? ensureDounaiAvatar()
                            : existing.avatarPath;
                    CharacterCard updated = CharacterCard.dounaiGpt(avatarPath);
                    updated.lastUsed = existing.lastUsed;
                    updated.unread = existing.unread;
                    updated.muted = existing.muted;
                    updated.pinned = existing.pinned;
                    updated.chatBackgroundPath = existing.chatBackgroundPath;
                    cards.set(i, updated);
                    changed = true;
                }
            }
        }
        if (!hasDounai && !dounaiDeleted) {
            cards.add(CharacterCard.dounaiGpt(ensureDounaiAvatar()));
            changed = true;
        }
        if (changed) saveCharacters(cards);
    }

    private String ensureDounaiAvatar() {
        File directory = new File(context.getFilesDir(), "builtin");
        File target = new File(directory, "dounai_gpt.png");
        if (target.isFile() && target.length() > 0) return target.getAbsolutePath();
        if (!directory.exists() && !directory.mkdirs()) return "";
        try (InputStream input = context.getResources().openRawResource(R.raw.dounai_gpt);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return target.getAbsolutePath();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void pruneInvalidGroups() {
        List<GroupChat> groups = getGroups();
        boolean changed = false;
        for (int i = groups.size() - 1; i >= 0; i--) {
            GroupChat group = groups.get(i);
            List<String> validMembers = validGroupMemberIds(group.members);
            if (validMembers.size() < 2) {
                groups.remove(i);
                prefs.edit().remove(threadKey(group.conversationId())).apply();
                changed = true;
            } else if (!validMembers.equals(group.members)) {
                group.members.clear();
                group.members.addAll(validMembers);
                changed = true;
            }
        }
        if (changed) saveGroups(groups);
    }

    private List<String> validGroupMemberIds(List<String> storedMembers) {
        List<String> validMembers = new ArrayList<>();
        if (storedMembers == null) return validMembers;
        for (String storedMember : storedMembers) {
            CharacterCard card = getCharacter(storedMember);
            if (card == null) {
                card = getCharacterBySourceAvatar(storedMember);
            }
            if (card != null && !validMembers.contains(card.id)) {
                validMembers.add(card.id);
            }
        }
        return validMembers;
    }

    public synchronized List<CharacterCard> getCharacters() {
        List<CharacterCard> cards = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(CHARACTERS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) cards.add(CharacterCard.fromJson(object));
            }
        } catch (JSONException ignored) {
        }
        Collections.sort(cards, new Comparator<CharacterCard>() {
            @Override
            public int compare(CharacterCard left, CharacterCard right) {
                return Long.compare(right.lastUsed, left.lastUsed);
            }
        });
        return cards;
    }

    public synchronized CharacterCard getCharacter(String id) {
        for (CharacterCard card : getCharacters()) {
            if (card.id.equals(id)) return card;
        }
        return null;
    }

    public synchronized CharacterCard getCharacterBySourceAvatar(String sourceAvatar) {
        if (sourceAvatar == null || sourceAvatar.trim().isEmpty()) return null;
        for (CharacterCard card : getCharacters()) {
            if (sourceAvatar.equals(card.sourceAvatar)) return card;
        }
        return null;
    }

    public synchronized void upsertCharacter(CharacterCard incoming) {
        List<CharacterCard> cards = getCharacters();
        boolean replaced = false;
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).id.equals(incoming.id)) {
                cards.set(i, incoming);
                replaced = true;
                break;
            }
        }
        if (!replaced) cards.add(incoming);
        saveCharacters(cards);
    }

    public synchronized void deleteCharacter(String id) {
        if (id == null || id.trim().isEmpty()) return;
        List<CharacterCard> cards = getCharacters();
        CharacterCard removed = null;
        for (int i = cards.size() - 1; i >= 0; i--) {
            if (id.equals(cards.get(i).id)) {
                removed = cards.remove(i);
                break;
            }
        }
        if (removed == null) return;
        if (CharacterCard.BUILTIN_DOUNAI_ID.equals(id)) {
            prefs.edit().putBoolean(DOUNAI_DELETED, true).apply();
        }
        saveCharacters(cards);
        prefs.edit().remove(threadKey(id)).apply();

        List<GroupChat> groups = getGroups();
        boolean groupsChanged = false;
        for (int i = groups.size() - 1; i >= 0; i--) {
            GroupChat group = groups.get(i);
            boolean memberRemoved = false;
            for (int m = group.members.size() - 1; m >= 0; m--) {
                String member = group.members.get(m);
                if (id.equals(member)
                        || (!removed.sourceAvatar.isEmpty()
                        && removed.sourceAvatar.equals(member))) {
                    group.members.remove(m);
                    memberRemoved = true;
                }
            }
            if (!memberRemoved) continue;
            groupsChanged = true;
            if (group.members.size() < 2) {
                groups.remove(i);
                prefs.edit().remove(threadKey(group.conversationId())).apply();
            }
        }
        if (groupsChanged) saveGroups(groups);
    }

    public synchronized void touchCharacter(String id) {
        CharacterCard card = getCharacter(id);
        if (card == null) return;
        card.lastUsed = System.currentTimeMillis();
        card.unread = 0;
        upsertCharacter(card);
    }

    public synchronized List<ChatMessage> getMessages(String characterId) {
        List<ChatMessage> messages = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(threadKey(characterId), "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) messages.add(ChatMessage.fromJson(object));
            }
        } catch (JSONException ignored) {
        }
        return messages;
    }

    public synchronized void saveMessages(String characterId, List<ChatMessage> messages) {
        JSONArray array = new JSONArray();
        for (ChatMessage message : messages) {
            try {
                array.put(message.toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(threadKey(characterId), array.toString()).apply();
        if (characterId.startsWith(GroupChat.CONVERSATION_PREFIX)) {
            touchGroup(characterId.substring(GroupChat.CONVERSATION_PREFIX.length()));
        } else {
            touchCharacter(characterId);
        }
    }

    public synchronized List<GroupChat> getGroups() {
        List<GroupChat> groups = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(GROUPS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) groups.add(GroupChat.fromJson(object));
            }
        } catch (JSONException ignored) {
        }
        Collections.sort(groups, new Comparator<GroupChat>() {
            @Override
            public int compare(GroupChat left, GroupChat right) {
                return Long.compare(right.lastUsed, left.lastUsed);
            }
        });
        return groups;
    }

    public synchronized GroupChat getGroup(String id) {
        for (GroupChat group : getGroups()) {
            if (group.id.equals(id)) return group;
        }
        return null;
    }

    public synchronized void upsertGroup(GroupChat incoming) {
        List<GroupChat> groups = getGroups();
        List<String> validMembers = validGroupMemberIds(incoming.members);
        incoming.members.clear();
        incoming.members.addAll(validMembers);
        if (incoming.members.size() < 2) {
            for (int i = groups.size() - 1; i >= 0; i--) {
                if (groups.get(i).id.equals(incoming.id)) groups.remove(i);
            }
            prefs.edit().remove(threadKey(incoming.conversationId())).apply();
            saveGroups(groups);
            return;
        }
        boolean replaced = false;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).id.equals(incoming.id)) {
                groups.set(i, incoming);
                replaced = true;
                break;
            }
        }
        if (!replaced) groups.add(incoming);
        saveGroups(groups);
    }

    public synchronized void touchGroup(String id) {
        GroupChat group = getGroup(id);
        if (group == null) return;
        group.lastUsed = System.currentTimeMillis();
        group.unread = 0;
        upsertGroup(group);
    }

    public synchronized AppConfig getConfig() {
        try {
            return AppConfig.fromJson(new JSONObject(prefs.getString(CONFIG, "{}")));
        } catch (JSONException ignored) {
            return new AppConfig();
        }
    }

    public synchronized void saveConfig(AppConfig config) {
        try {
            prefs.edit().putString(CONFIG, config.toJson().toString()).apply();
            RemoteSyncManager.schedule(context);
        } catch (JSONException ignored) {
        }
    }

    /** Creates a portable JSON backup without API keys or OAuth/realtime credentials. */
    public synchronized JSONObject createBackup() throws JSONException, IOException {
        JSONObject assets = new JSONObject();
        Map<String, String> assetIds = new HashMap<>();
        long[] totalAssetBytes = {0L};

        JSONArray characters = new JSONArray();
        for (CharacterCard card : getCharacters()) {
            JSONObject value = card.toJson();
            attachAsset(value, "avatarPath", "avatarAsset", assets,
                    assetIds, totalAssetBytes);
            attachAsset(value, "chatBackgroundPath", "chatBackgroundAsset", assets,
                    assetIds, totalAssetBytes);
            value.put("sourceAvatar", "");
            characters.put(value);
        }

        JSONArray groups = new JSONArray();
        JSONObject messages = new JSONObject();
        for (GroupChat group : getGroups()) {
            JSONObject value = group.toJson();
            attachAsset(value, "chatBackgroundPath", "chatBackgroundAsset", assets,
                    assetIds, totalAssetBytes);
            groups.put(value);
            messages.put(group.conversationId(), backupMessages(
                    getMessages(group.conversationId()), assets, assetIds,
                    totalAssetBytes));
        }
        for (CharacterCard card : getCharacters()) {
            messages.put(card.id, backupMessages(
                    getMessages(card.id), assets, assetIds, totalAssetBytes));
        }

        JSONObject config = getConfig().toJson();
        attachAsset(config, "personaAvatarPath", "personaAvatarAsset", assets,
                assetIds, totalAssetBytes);

        return new JSONObject()
                .put("format", BACKUP_FORMAT)
                .put("schemaVersion", BACKUP_SCHEMA)
                .put("appVersion", "0.9.0")
                .put("platform", "android")
                .put("createdAt", System.currentTimeMillis())
                .put("characters", characters)
                .put("groups", groups)
                .put("messages", messages)
                .put("config", config)
                .put("assets", assets);
    }

    /** Replaces local content with a validated backup. Encrypted credentials stay untouched. */
    public synchronized void restoreBackup(JSONObject root)
            throws JSONException, IOException {
        if (!BACKUP_FORMAT.equals(root.optString("format", ""))) {
            throw new IOException("这不是澄语 Android 备份文件");
        }
        int schema = root.optInt("schemaVersion", 0);
        if (schema < 1 || schema > BACKUP_SCHEMA) {
            throw new IOException("备份版本不受支持");
        }
        JSONArray characterArray = root.optJSONArray("characters");
        JSONArray groupArray = root.optJSONArray("groups");
        JSONObject messageObject = root.optJSONObject("messages");
        JSONObject configObject = root.optJSONObject("config");
        JSONObject assets = root.optJSONObject("assets");
        if (characterArray == null || groupArray == null || messageObject == null
                || configObject == null || assets == null) {
            throw new IOException("备份内容不完整");
        }
        if (characterArray.length() > 1000 || groupArray.length() > 1000
                || messageObject.length() > 2000 || assets.length() > 10000) {
            throw new IOException("备份内容超出安全限制");
        }

        File restoreDirectory = new File(
                new File(context.getFilesDir(), "backup_restore"),
                System.currentTimeMillis() + "-" + UUID.randomUUID().toString());
        if (!restoreDirectory.mkdirs()) {
            throw new IOException("无法创建备份还原目录");
        }
        Map<String, String> restoredAssets = restoreAssets(assets, restoreDirectory);

        JSONArray restoredCharacters = new JSONArray();
        boolean includesDounai = false;
        for (int i = 0; i < characterArray.length(); i++) {
            JSONObject value = new JSONObject(characterArray.getJSONObject(i).toString());
            restoreAssetPath(value, "avatarPath", "avatarAsset", restoredAssets);
            restoreAssetPath(value, "chatBackgroundPath", "chatBackgroundAsset", restoredAssets);
            CharacterCard card = CharacterCard.fromJson(value);
            if (card.id == null || card.id.trim().isEmpty()
                    || card.name == null || card.name.trim().isEmpty()) {
                throw new IOException("备份包含无效角色");
            }
            if (CharacterCard.BUILTIN_DOUNAI_ID.equals(card.id)) includesDounai = true;
            restoredCharacters.put(card.toJson());
        }

        JSONArray restoredGroups = new JSONArray();
        for (int i = 0; i < groupArray.length(); i++) {
            JSONObject value = new JSONObject(groupArray.getJSONObject(i).toString());
            restoreAssetPath(value, "chatBackgroundPath", "chatBackgroundAsset", restoredAssets);
            GroupChat group = GroupChat.fromJson(value);
            if (group.id == null || group.id.trim().isEmpty()) {
                throw new IOException("备份包含无效群聊");
            }
            restoredGroups.put(group.toJson());
        }

        JSONObject restoredMessages = new JSONObject();
        int totalMessages = 0;
        java.util.Iterator<String> conversationIds = messageObject.keys();
        while (conversationIds.hasNext()) {
            String conversationId = conversationIds.next();
            JSONArray source = messageObject.optJSONArray(conversationId);
            if (source == null) continue;
            totalMessages += source.length();
            if (totalMessages > 200000) throw new IOException("备份消息数量超出安全限制");
            JSONArray target = new JSONArray();
            for (int i = 0; i < source.length(); i++) {
                JSONObject value = new JSONObject(source.getJSONObject(i).toString());
                restoreAssetPath(value, "attachmentPath", "attachmentAsset", restoredAssets);
                target.put(ChatMessage.fromJson(value).toJson());
            }
            restoredMessages.put(conversationId, target);
        }

        JSONObject restoredConfig = new JSONObject(configObject.toString());
        restoreAssetPath(restoredConfig, "personaAvatarPath", "personaAvatarAsset",
                restoredAssets);
        AppConfig.fromJson(restoredConfig);

        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("thread_") || key.startsWith(MODEL_CACHE_PREFIX)) {
                editor.remove(key);
            }
        }
        editor.putString(CHARACTERS, restoredCharacters.toString());
        editor.putString(GROUPS, restoredGroups.toString());
        editor.putString(CONFIG, restoredConfig.toString());
        editor.putBoolean(DOUNAI_DELETED, !includesDounai);
        java.util.Iterator<String> restoredConversationIds = restoredMessages.keys();
        while (restoredConversationIds.hasNext()) {
            String id = restoredConversationIds.next();
            editor.putString(threadKey(id), restoredMessages.getJSONArray(id).toString());
        }
        if (!editor.commit()) throw new IOException("无法写入还原后的数据");
        migrateBuiltInCharacters();
        pruneInvalidGroups();
        RemoteSyncManager.schedule(context);
    }

    public static String backupSummary(JSONObject root) {
        JSONArray characters = root.optJSONArray("characters");
        JSONArray groups = root.optJSONArray("groups");
        JSONObject messages = root.optJSONObject("messages");
        int messageCount = 0;
        if (messages != null) {
            java.util.Iterator<String> keys = messages.keys();
            while (keys.hasNext()) {
                JSONArray values = messages.optJSONArray(keys.next());
                if (values != null) messageCount += values.length();
            }
        }
        return (characters == null ? 0 : characters.length()) + " 位角色 · "
                + (groups == null ? 0 : groups.length()) + " 个群聊 · "
                + messageCount + " 条消息";
    }

    private JSONArray backupMessages(
            List<ChatMessage> source,
            JSONObject assets,
            Map<String, String> assetIds,
            long[] totalAssetBytes) throws JSONException, IOException {
        JSONArray result = new JSONArray();
        for (ChatMessage message : source) {
            JSONObject value = message.toJson();
            attachAsset(value, "attachmentPath", "attachmentAsset", assets,
                    assetIds, totalAssetBytes);
            result.put(value);
        }
        return result;
    }

    private void attachAsset(
            JSONObject value,
            String pathKey,
            String assetKey,
            JSONObject assets,
            Map<String, String> assetIds,
            long[] totalAssetBytes) throws JSONException, IOException {
        String path = value.optString(pathKey, "").trim();
        value.put(pathKey, "");
        if (path.isEmpty()) return;
        File file = new File(path);
        if (!file.isFile() || !file.canRead()) return;
        String canonical = file.getCanonicalPath();
        String existing = assetIds.get(canonical);
        if (existing != null) {
            value.put(assetKey, existing);
            return;
        }
        long size = file.length();
        if (size <= 0 || size > MAX_BACKUP_ASSET_BYTES
                || totalAssetBytes[0] + size > MAX_BACKUP_TOTAL_ASSET_BYTES) {
            throw new IOException("备份中的单个图片不能超过 20 MB，总图片不能超过 128 MB");
        }
        byte[] data = readFile(file, (int) size);
        String id = UUID.randomUUID().toString();
        String mime = path.toLowerCase(java.util.Locale.ROOT).endsWith(".png")
                ? "image/png" : "image/jpeg";
        assets.put(id, new JSONObject()
                .put("mime", mime)
                .put("data", Base64.encodeToString(data, Base64.NO_WRAP)));
        assetIds.put(canonical, id);
        totalAssetBytes[0] += data.length;
        value.put(assetKey, id);
    }

    private Map<String, String> restoreAssets(JSONObject assets, File directory)
            throws JSONException, IOException {
        Map<String, String> restored = new HashMap<>();
        long total = 0L;
        java.util.Iterator<String> ids = assets.keys();
        while (ids.hasNext()) {
            String id = ids.next();
            JSONObject asset = assets.optJSONObject(id);
            if (asset == null || !id.matches("[A-Za-z0-9-]{1,80}")) {
                throw new IOException("备份包含无效图片资源");
            }
            byte[] data;
            try {
                data = Base64.decode(asset.optString("data", ""), Base64.DEFAULT);
            } catch (IllegalArgumentException error) {
                throw new IOException("备份图片数据损坏");
            }
            if (data.length <= 0 || data.length > MAX_BACKUP_ASSET_BYTES
                    || total + data.length > MAX_BACKUP_TOTAL_ASSET_BYTES) {
                throw new IOException("备份图片超出安全限制");
            }
            String extension = "image/png".equals(asset.optString("mime", ""))
                    ? ".png" : ".jpg";
            File target = new File(directory, id + extension);
            try (FileOutputStream output = new FileOutputStream(target)) {
                output.write(data);
            }
            restored.put(id, target.getAbsolutePath());
            total += data.length;
        }
        return restored;
    }

    private static void restoreAssetPath(
            JSONObject value,
            String pathKey,
            String assetKey,
            Map<String, String> assets) throws JSONException, IOException {
        String id = value.optString(assetKey, "").trim();
        value.remove(assetKey);
        if (id.isEmpty()) {
            value.put(pathKey, "");
            return;
        }
        String path = assets.get(id);
        if (path == null) throw new IOException("备份引用了缺失的图片资源");
        value.put(pathKey, path);
    }

    private static byte[] readFile(File file, int expected) throws IOException {
        byte[] data = new byte[expected];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count < 0) break;
                offset += count;
            }
        }
        if (offset != data.length) throw new IOException("无法完整读取备份图片");
        return data;
    }

    public synchronized List<String> getModelCache(String scope) {
        List<String> models = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(
                    prefs.getString(modelCacheKey(scope), "{}"));
            JSONArray array = root.optJSONArray("models");
            if (array == null) return models;
            for (int i = 0; i < array.length(); i++) {
                String model = array.optString(i, "").trim();
                if (!model.isEmpty() && !models.contains(model)) models.add(model);
            }
        } catch (JSONException ignored) {
        }
        return models;
    }

    public synchronized void saveModelCache(String scope, List<String> models) {
        JSONArray array = new JSONArray();
        for (String model : models) {
            if (model != null && !model.trim().isEmpty()) array.put(model.trim());
        }
        try {
            JSONObject root = new JSONObject()
                    .put("savedAt", System.currentTimeMillis())
                    .put("models", array);
            prefs.edit().putString(modelCacheKey(scope), root.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private synchronized void saveCharacters(List<CharacterCard> cards) {
        JSONArray array = new JSONArray();
        for (CharacterCard card : cards) {
            try {
                array.put(card.toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(CHARACTERS, array.toString()).apply();
        RemoteSyncManager.schedule(context);
    }

    private synchronized void saveGroups(List<GroupChat> groups) {
        JSONArray array = new JSONArray();
        for (GroupChat group : groups) {
            try {
                array.put(group.toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(GROUPS, array.toString()).apply();
        RemoteSyncManager.schedule(context);
    }

    private static String threadKey(String id) {
        return "thread_" + id;
    }

    private static String modelCacheKey(String scope) {
        String value = scope == null ? "" : scope;
        return MODEL_CACHE_PREFIX + Integer.toHexString(value.hashCode());
    }
}
