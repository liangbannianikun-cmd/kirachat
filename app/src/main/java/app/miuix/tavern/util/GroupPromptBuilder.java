package app.miuix.tavern.util;

import app.miuix.tavern.model.AppConfig;
import app.miuix.tavern.model.CharacterCard;
import app.miuix.tavern.model.ChatMessage;
import app.miuix.tavern.model.GroupChat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public final class GroupPromptBuilder {
    private GroupPromptBuilder() {
    }

    public static JSONArray build(
            GroupChat group,
            CharacterCard target,
            List<CharacterCard> members,
            AppConfig config,
            List<ChatMessage> history) {
        return build(group, target, members, config, history, false);
    }

    public static JSONArray build(
            GroupChat group,
            CharacterCard target,
            List<CharacterCard> members,
            AppConfig config,
            List<ChatMessage> history,
            boolean spontaneous) {
        String persona = PromptBuilder.localizedPersona(config);
        StringBuilder system = new StringBuilder();
        system.append("你正在群聊“").append(group.name).append("”中扮演 ")
                .append(target.name).append("。只输出 ").append(target.name)
                .append(" 本人的下一条聊天消息，不写发言人标签，不替其他成员或用户发言。");
        append(system, "当前时间", PromptBuilder.currentTimeContext());
        append(system, "角色描述", target.replaceMacros(target.description, persona));
        append(system, "性格", target.replaceMacros(target.personality, persona));
        append(system, "当前情境", target.replaceMacros(target.scenario, persona));
        StringBuilder participantNames = new StringBuilder(persona).append("（用户）");
        for (CharacterCard member : members) {
            participantNames.append("、").append(member.name);
        }
        append(system, "群聊成员", participantNames.toString());
        boolean mentioned = !spontaneous && isMentioned(history, target.name);
        if (spontaneous) {
            system.append("\n现在是群聊暂时空闲的时刻。若符合你的性格、当前情境和最近话题，"
                    + "可以自然地自行发起一句新消息；若此刻不适合主动说话，只输出精确文本 "
                    + "[[SKIP]]，不要解释原因。");
        } else if (mentioned) {
            system.append("\n最近消息明确 @了你。你必须自然回复，不得输出跳过标记。");
        } else {
            system.append("\n先判断最近消息与你的角色关系、知识、经历或当前话题是否有足够关联。"
                    + "有自然发言动机才回复；若没有必要参与，只输出精确文本 [[SKIP]]，"
                    + "不要解释原因，不要添加标点。");
        }
        system.append("\n回复应自然承接最近发言；避免重复其他成员已经说过的内容。");
        if (config.webSearch) {
            system.append("\n应用会在需要时把本轮联网搜索结果作为额外系统资料提供。"
                    + "只引用实际提供的资料和链接；不得伪造搜索结果或来源。");
        }
        PromptBuilder.appendResponseLanguage(system);

        JSONArray prompt = new JSONArray();
        put(prompt, ChatMessage.SYSTEM, system.toString());
        int start = Math.max(0, history.size() - 36);
        for (int i = start; i < history.size(); i++) {
            ChatMessage message = history.get(i);
            if (message.failed || !PromptBuilder.hasContent(message)) continue;
            if (ChatMessage.USER.equals(message.role)) {
                String text = persona + "：" + (
                        message.content == null ? "" : message.content);
                put(prompt, ChatMessage.USER,
                        PromptBuilder.contentForMessage(message, text));
            } else if (target.name.equals(message.speaker)) {
                put(prompt, ChatMessage.ASSISTANT, message.content);
            } else {
                String speaker = message.speaker == null || message.speaker.trim().isEmpty()
                        ? "其他成员" : message.speaker;
                put(prompt, ChatMessage.USER, speaker + "：" + message.content);
            }
        }
        return prompt;
    }

    public static boolean shouldSkipOutput(String value) {
        if (value == null) return true;
        String normalized = value.trim()
                .replace("`", "")
                .replace(" ", "");
        return normalized.isEmpty()
                || "[[SKIP]]".equalsIgnoreCase(normalized)
                || "[SKIP]".equalsIgnoreCase(normalized)
                || "SKIP".equalsIgnoreCase(normalized);
    }

    private static boolean isMentioned(
            List<ChatMessage> history, String targetName) {
        if (targetName == null || targetName.trim().isEmpty()) return false;
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            String text = message == null || message.content == null
                    ? "" : message.content;
            if (text.contains("@" + targetName)
                    || text.contains("＠" + targetName)
                    || text.contains("@所有人")
                    || text.contains("＠所有人")) {
                return true;
            }
            if (message != null && ChatMessage.USER.equals(message.role)) break;
        }
        return false;
    }

    private static void append(StringBuilder target, String title, String content) {
        if (content == null || content.trim().isEmpty()) return;
        target.append("\n\n").append(title).append("：\n").append(content.trim());
    }

    private static void put(JSONArray array, String role, Object content) {
        try {
            array.put(new JSONObject().put("role", role).put("content", content));
        } catch (JSONException ignored) {
        }
    }
}
