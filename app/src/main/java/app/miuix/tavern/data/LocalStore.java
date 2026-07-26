package app.miuix.tavern.data;

import android.content.Context;
import android.content.SharedPreferences;

import app.miuix.tavern.R;
import app.miuix.tavern.model.AppConfig;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.ChatMessage;
import app.miuix.tavern.model.GroupChat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class LocalStore {
    private static final String PREFS = "miu_tavern_data_v1";
    private static final String CHARACTERS = "characters";
    private static final String GROUPS = "groups";
    private static final String CONFIG = "config";
    private static final String MODEL_CACHE_PREFIX = "model_cache_";
    private static final String DOUNAI_DELETED = "builtin_dounai_deleted";

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
        } catch (JSONException ignored) {
        }
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
    }

    private static String threadKey(String id) {
        return "thread_" + id;
    }

    private static String modelCacheKey(String scope) {
        String value = scope == null ? "" : scope;
        return MODEL_CACHE_PREFIX + Integer.toHexString(value.hashCode());
    }
}
