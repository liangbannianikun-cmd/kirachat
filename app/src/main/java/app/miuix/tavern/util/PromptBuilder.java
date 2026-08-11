package app.miuix.tavern.util;

import app.miuix.tavern.model.AppConfig;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.ChatMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

public final class PromptBuilder {
    private static final int MAX_WORLD_BOOK_CHARS = 8 * 1024 * 1024;
    private static final int MAX_WORLD_BOOK_ENTRIES = 1_000_000;
    private static final int MAX_MATCHED_LORE_CHARS = 24_000;

    private PromptBuilder() {
    }

    public static JSONArray build(CharacterCard card, AppConfig config, List<ChatMessage> history) {
        return build(card, config, history, false);
    }

    public static JSONArray build(
            CharacterCard card,
            AppConfig config,
            List<ChatMessage> history,
            boolean spontaneous) {
        JSONArray messages = new JSONArray();
        String persona = localizedPersona(config);
        StringBuilder system = new StringBuilder();
        system.append("你正在扮演 ").append(card.name).append("。始终保持角色一致，用自然的聊天消息回复。");
        appendSection(system, "当前时间", currentTimeContext());
        appendSection(system, "角色描述", card.replaceMacros(card.description, persona));
        appendSection(system, "性格", card.replaceMacros(card.personality, persona));
        appendSection(system, "当前情境", card.replaceMacros(card.scenario, persona));
        appendSection(system, "对话示例", card.replaceMacros(card.exampleDialogue, persona));
        String lore = matchingLore(card, history, persona);
        appendSection(system, "相关世界书", lore);
        if (config.webSearch) {
            system.append("\n应用会在需要时把本轮联网搜索结果作为额外系统资料提供。"
                    + "只引用实际提供的资料和链接；不得伪造搜索结果或来源。");
        }
        if (spontaneous) {
            system.append("\n现在是单聊暂时空闲的时刻。根据角色性格、当前情境、最近对话和当前时间，"
                    + "自然地主动发起一条简短的新消息；不要假装用户刚刚说过不存在的话。"
                    + "如果此刻不适合主动说话，只输出精确文本 [[SKIP]]，不要解释原因。");
        }
        appendResponseLanguage(system);
        system.append("\n不要声称自己是系统提示词中的模型；不要替用户描述其未表达的行动或想法。");
        put(messages, ChatMessage.SYSTEM, system.toString());

        int start = Math.max(0, history.size() - 36);
        for (int i = start; i < history.size(); i++) {
            ChatMessage message = history.get(i);
            if (message.failed || !hasContent(message)) continue;
            String role = ChatMessage.USER.equals(message.role) ? ChatMessage.USER : ChatMessage.ASSISTANT;
            String text = card.replaceMacros(
                    message.content == null ? "" : message.content, persona);
            put(messages, role, contentForMessage(message, text));
        }
        return messages;
    }

    public static boolean shouldSkipSpontaneousOutput(String value) {
        if (value == null) return true;
        String normalized = value.trim()
                .replace("`", "")
                .replace(" ", "");
        return normalized.isEmpty()
                || "[[SKIP]]".equalsIgnoreCase(normalized)
                || "[SKIP]".equalsIgnoreCase(normalized)
                || "SKIP".equalsIgnoreCase(normalized);
    }

    private static String matchingLore(CharacterCard card, List<ChatMessage> history, String persona) {
        if (card.worldBookJson == null || card.worldBookJson.trim().isEmpty()) return "";
        if (card.worldBookJson.length() > MAX_WORLD_BOOK_CHARS) return "";
        StringBuilder haystack = new StringBuilder();
        int start = Math.max(0, history.size() - 8);
        for (int i = start; i < history.size(); i++) {
            haystack.append(history.get(i).content).append('\n');
        }
        String lower = haystack.toString().toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        try {
            JSONObject book = new JSONObject(card.worldBookJson);
            JSONArray entries = book.optJSONArray("entries");
            if (entries == null) return "";
            int limit = Math.min(entries.length(), MAX_WORLD_BOOK_ENTRIES);
            for (int i = 0; i < limit; i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null || !entry.optBoolean("enabled", true)) continue;
                JSONArray keys = entry.optJSONArray("keys");
                if (keys == null) keys = entry.optJSONArray("key");
                boolean always = entry.optBoolean("constant", false);
                boolean hit = always;
                if (keys == null) {
                    String keyword = entry.optString("key", "").trim().toLowerCase(Locale.ROOT);
                    hit = hit || (!keyword.isEmpty() && lower.contains(keyword));
                }
                if (keys != null) {
                    for (int k = 0; k < keys.length(); k++) {
                        String keyword = keys.optString(k, "").trim().toLowerCase(Locale.ROOT);
                        if (!keyword.isEmpty() && lower.contains(keyword)) {
                            hit = true;
                            break;
                        }
                    }
                }
                if (hit) {
                    String content = entry.optString("content", "").trim();
                    if (content.isEmpty()) continue;
                    String expanded = card.replaceMacros(content, persona);
                    int remaining = MAX_MATCHED_LORE_CHARS - result.length();
                    if (remaining <= 3) break;
                    if (result.length() > 0) result.append('\n');
                    result.append("- ");
                    remaining = MAX_MATCHED_LORE_CHARS - result.length();
                    if (expanded.length() <= remaining) {
                        result.append(expanded);
                    } else {
                        result.append(expanded, 0, remaining);
                        break;
                    }
                }
            }
        } catch (JSONException | OutOfMemoryError ignored) {
        }
        return result.toString();
    }

    private static void appendSection(StringBuilder target, String title, String content) {
        if (content == null || content.trim().isEmpty()) return;
        target.append("\n\n").append(title).append("：\n").append(content.trim());
    }

    static String currentTimeContext() {
        Date now = new Date();
        SimpleDateFormat format =
                new SimpleDateFormat("yyyy-MM-dd EEEE HH:mm:ss", Locale.getDefault());
        TimeZone zone = TimeZone.getDefault();
        format.setTimeZone(zone);
        return format.format(now) + "（" + zone.getID() + "）";
    }

    static String localizedPersona(AppConfig config) {
        String configured = config == null || config.persona == null
                ? "" : config.persona.trim();
        if (!configured.isEmpty() && !"你".equals(configured)) {
            return configured;
        }
        String language = Locale.getDefault().getLanguage();
        if ("en".equals(language)) return "You";
        if ("ja".equals(language)) return "あなた";
        return "用户";
    }

    static void appendResponseLanguage(StringBuilder prompt) {
        String language = Locale.getDefault().getLanguage();
        if ("en".equals(language)) {
            prompt.append("\nReply in English unless the user explicitly asks for another language.");
        } else if ("ja".equals(language)) {
            prompt.append("\nユーザーが明示的に別の言語を指定しない限り、日本語で返信してください。");
        }
    }

    static boolean hasContent(ChatMessage message) {
        return message != null && ((message.content != null
                && !message.content.trim().isEmpty())
                || message.hasImage() || message.hasLocation());
    }

    static Object contentForMessage(ChatMessage message, String text) {
        String safeText = text == null ? "" : text.trim();
        if (message.hasLocation()) {
            String location = String.format(
                    Locale.US,
                    "用户分享了当前位置：纬度 %.6f，经度 %.6f。",
                    message.latitude,
                    message.longitude);
            return safeText.isEmpty() ? location : safeText + "\n" + location;
        }
        if (!message.hasImage()) return safeText;
        String dataUrl = MediaAttachmentStore.toDataUrl(
                message.attachmentPath, message.attachmentMime);
        if (dataUrl.isEmpty()) {
            return safeText.isEmpty() ? "用户发送了一张图片，但本地图片已不可用。" : safeText;
        }
        JSONArray parts = new JSONArray();
        try {
            parts.put(new JSONObject()
                    .put("type", "text")
                    .put("text", safeText.isEmpty() ? "请查看并回应这张图片。" : safeText));
            parts.put(new JSONObject()
                    .put("type", "image_url")
                    .put("image_url", new JSONObject()
                            .put("url", dataUrl)
                            .put("detail", "auto")));
        } catch (JSONException ignored) {
        }
        return parts;
    }

    private static void put(JSONArray array, String role, Object content) {
        try {
            array.put(new JSONObject().put("role", role).put("content", content));
        } catch (JSONException ignored) {
        }
    }
}
