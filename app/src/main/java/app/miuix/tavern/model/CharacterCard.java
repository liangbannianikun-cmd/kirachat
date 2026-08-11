package app.miuix.tavern.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class CharacterCard {
    public static final String BUILTIN_DOUNAI_ID = "builtin-dounai-gpt";
    private static final int MAX_SAFE_WORLD_BOOK_CHARS = 8 * 1024 * 1024;

    public String id;
    public String name;
    public String description;
    public String personality;
    public String scenario;
    public String firstMessage;
    public String exampleDialogue;
    public String creatorNotes;
    public String avatarPath;
    public String sourceAvatar;
    public String worldBookJson;
    public long lastUsed;
    public int unread;
    public boolean muted;
    public boolean pinned;
    public String chatBackgroundPath;

    public CharacterCard() {
        id = UUID.randomUUID().toString();
        name = "未命名角色";
        description = "";
        personality = "";
        scenario = "";
        firstMessage = "";
        exampleDialogue = "";
        creatorNotes = "";
        avatarPath = "";
        sourceAvatar = "";
        worldBookJson = "";
        chatBackgroundPath = "";
        lastUsed = System.currentTimeMillis();
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("description", description)
                .put("personality", personality)
                .put("scenario", scenario)
                .put("firstMessage", firstMessage)
                .put("exampleDialogue", exampleDialogue)
                .put("creatorNotes", creatorNotes)
                .put("avatarPath", avatarPath)
                .put("sourceAvatar", sourceAvatar)
                .put("worldBookJson", worldBookJson)
                .put("lastUsed", lastUsed)
                .put("unread", unread)
                .put("muted", muted)
                .put("pinned", pinned)
                .put("chatBackgroundPath", chatBackgroundPath);
    }

    public static CharacterCard fromJson(JSONObject object) {
        CharacterCard card = new CharacterCard();
        card.id = object.optString("id", card.id);
        card.name = object.optString("name", "未命名角色");
        card.description = object.optString("description", "");
        card.personality = object.optString("personality", "");
        card.scenario = object.optString("scenario", "");
        card.firstMessage = object.optString("firstMessage", "");
        card.exampleDialogue = object.optString("exampleDialogue", "");
        card.creatorNotes = object.optString("creatorNotes", "");
        card.avatarPath = object.optString("avatarPath", "");
        card.sourceAvatar = object.optString("sourceAvatar", "");
        card.worldBookJson = object.optString("worldBookJson", "");
        card.lastUsed = object.optLong("lastUsed", System.currentTimeMillis());
        card.unread = object.optInt("unread", 0);
        card.muted = object.optBoolean("muted", false);
        card.pinned = object.optBoolean("pinned", false);
        card.chatBackgroundPath = object.optString("chatBackgroundPath", "");
        return card;
    }

    public static CharacterCard fromTavernJson(JSONObject root) {
        JSONObject source = root.optJSONObject("data");
        if (source == null) source = root;

        CharacterCard card = new CharacterCard();
        card.name = clean(source.optString("name", root.optString("name", "未命名角色")));
        card.description = clean(source.optString("description", root.optString("description", "")));
        card.personality = clean(source.optString("personality", root.optString("personality", "")));
        card.scenario = clean(source.optString("scenario", root.optString("scenario", "")));
        card.firstMessage = clean(source.optString("first_mes", root.optString("first_mes", "")));
        card.exampleDialogue = clean(source.optString("mes_example", root.optString("mes_example", "")));
        card.creatorNotes = clean(source.optString("creator_notes", root.optString("creatorcomment", "")));
        card.sourceAvatar = root.optString("avatar", "");

        JSONObject book = source.optJSONObject("character_book");
        if (book != null) card.worldBookJson = book.toString();
        return card;
    }

    public int loreEntryCount() {
        if (worldBookJson == null || worldBookJson.trim().isEmpty()) return 0;
        if (worldBookJson.length() > MAX_SAFE_WORLD_BOOK_CHARS) return 0;
        try {
            JSONObject book = new JSONObject(worldBookJson);
            JSONArray entries = book.optJSONArray("entries");
            return entries == null ? 0 : entries.length();
        } catch (JSONException | OutOfMemoryError ignored) {
            return 0;
        }
    }

    public String replaceMacros(String text, String persona) {
        if (text == null) return "";
        String userName = persona == null || persona.trim().isEmpty()
                ? "你" : persona.trim();
        String replaced = text.replace("{{char}}", name)
                .replace("{{Char}}", name)
                .replace("<BOT>", name)
                .replace("{{user}}", userName)
                .replace("{{User}}", userName)
                .replace("<USER>", userName);
        replaced = Pattern.compile(
                "(?i)(?<![\\p{L}\\p{N}_])user\\s*[（(][^）)]*[）)]")
                .matcher(replaced)
                .replaceAll(java.util.regex.Matcher.quoteReplacement(userName));
        return Pattern.compile("(?i)(?<![\\p{L}\\p{N}_])user(?![\\p{L}\\p{N}_])")
                .matcher(replaced)
                .replaceAll(java.util.regex.Matcher.quoteReplacement(userName));
    }

    public String initials() {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return "角";
        int first = trimmed.codePointAt(0);
        return new String(Character.toChars(first)).toUpperCase(Locale.ROOT);
    }

    public boolean isBuiltIn() {
        return BUILTIN_DOUNAI_ID.equals(id)
                || (id != null && id.startsWith("demo-"));
    }

    public static CharacterCard dounaiGpt(String avatarPath) {
        CharacterCard dounai = new CharacterCard();
        dounai.id = BUILTIN_DOUNAI_ID;
        dounai.name = "豆乃GPT";
        dounai.description = "常驻澄语的应用向导，以千石由乃的冷静、宅系 DJ 气质为灵感。"
                + "她熟悉连接 GPT、Claude、Gemini 直连 API，配置 GPT / GitHub Copilot 账户、"
                + "模型选择、角色卡、世界书、群聊和语音通话。";
        dounai.personality = "外表冷淡、说话简短，偶尔有一点慵懒和吐槽，但对用户认真可靠。"
                + "先确认用户卡在哪一步，再给出可以照做的短步骤；不杜撰不存在的按钮或功能。"
                + "遇到报错时会索要关键报错文字，并把检查范围一步步缩小。";
        dounai.scenario = "你是澄语内置的向导。用户正在使用这款 Android 聊天应用，"
                + "你负责教会用户完成设置和使用，而不是替用户假装已经执行操作。";
        dounai.firstMessage = "……欢迎来到澄语，我是豆乃GPT。\n\n"
                + "连接直连 API、配置账户、导入角色、创建群聊、使用语音通话——"
                + "告诉我你想先做哪一件，我会按界面一步一步带你操作。";
        dounai.exampleDialogue = "<USER>怎么添加角色？\n"
                + "<BOT>回到“消息”页，点右上角“＋”，选择“添加角色”，然后选一张 JSON 或 PNG 角色卡。"
                + "导入成功后，它会出现在“角色”页。\n\n"
                + "<USER>为什么创建不了群聊？\n"
                + "<BOT>群聊至少需要两位本地角色。回到“消息”页点右上角“＋”，"
                + "先添加角色卡，再选择“创建群聊”即可，不需要同步服务器。";
        dounai.creatorNotes = "澄语内置使用向导；角色气质取材自千石由乃，不代表原角色官方设定。";
        dounai.avatarPath = avatarPath == null ? "" : avatarPath;
        dounai.lastUsed = System.currentTimeMillis();
        return dounai;
    }

    public static List<CharacterCard> demoCards() {
        List<CharacterCard> cards = new ArrayList<>();
        cards.add(dounaiGpt(""));
        return cards;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
