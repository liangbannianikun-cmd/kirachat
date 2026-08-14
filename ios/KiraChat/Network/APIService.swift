import Foundation

enum APIService {
    static func complete(
        character: CharacterCard,
        history: [ChatMessage],
        settings: AppSettings,
        apiKey: String,
        groupDecision: Bool = false,
        spontaneous: Bool = false
    ) async throws -> String {
        let model = settings.model.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !model.isEmpty else {
            throw KiraError.message(NSLocalizedString("请先在连接与账户中选择模型", comment: ""))
        }
        let system = systemPrompt(
            character: character,
            history: history,
            settings: settings,
            groupDecision: groupDecision,
            spontaneous: spontaneous)
        let request: URLRequest
        switch settings.apiFormat {
        case .chatCompletions:
            request = try chatRequest(
                system: system,
                history: history,
                settings: settings,
                apiKey: apiKey)
        case .responses:
            request = try responsesRequest(
                system: system,
                history: history,
                settings: settings,
                apiKey: apiKey)
        case .claude:
            request = try claudeRequest(
                system: system,
                history: history,
                settings: settings,
                apiKey: apiKey)
        case .gemini:
            request = try geminiRequest(
                system: system,
                history: history,
                settings: settings,
                apiKey: apiKey)
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response: response, data: data)
        let text = try extractText(data: data, format: settings.apiFormat)
        let clean = settings.showReasoning ? text : stripReasoning(text)
        guard !clean.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw KiraError.message(NSLocalizedString("服务没有返回文字内容", comment: ""))
        }
        return clean.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func fetchModels(settings: AppSettings, apiKey: String) async throws -> [String] {
        let url = try modelListURL(settings: settings, apiKey: apiKey)
        var request = URLRequest(url: url)
        request.timeoutInterval = 25
        applyHeaders(to: &request, format: settings.apiFormat, apiKey: apiKey)
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response: response, data: data)
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw KiraError.message(NSLocalizedString("模型列表格式无效", comment: ""))
        }
        var models: [String] = []
        if settings.apiFormat == .gemini,
           let values = root["models"] as? [[String: Any]] {
            models = values.compactMap { item in
                guard let name = item["name"] as? String else { return nil }
                return name.replacingOccurrences(of: "models/", with: "")
            }
        } else if let values = root["data"] as? [[String: Any]] {
            models = values.compactMap { ($0["id"] as? String) ?? ($0["name"] as? String) }
        } else if let values = root["models"] as? [[String: Any]] {
            models = values.compactMap { ($0["id"] as? String) ?? ($0["name"] as? String) }
        }
        let result = Array(Set(models.filter { !$0.isEmpty })).sorted()
        guard !result.isEmpty else {
            throw KiraError.message(NSLocalizedString("接口没有返回可用模型", comment: ""))
        }
        return result
    }

    static func systemPrompt(
        character: CharacterCard,
        history: [ChatMessage],
        settings: AppSettings,
        groupDecision: Bool,
        spontaneous: Bool = false
    ) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateStyle = .full
        formatter.timeStyle = .long
        let traditional = usesTraditionalChinese(Locale.current)
        var sections = traditional ? [
            "目前日期、時間和時區：\(formatter.string(from: Date()))",
            "你是\(character.name)。",
            character.description,
            character.personality.isEmpty ? "" : "個性：\(character.personality)",
            character.scenario.isEmpty ? "" : "場景：\(character.scenario)",
            character.exampleDialogue.isEmpty ? "" : "對話範例：\n\(character.exampleDialogue)",
            "使用者名稱：\(settings.persona)",
            "除非使用者明確要求其他語言，否則請以繁體中文回覆。"
        ] : [
            "当前日期、时间和时区：\(formatter.string(from: Date()))",
            "你是\(character.name)。",
            character.description,
            character.personality.isEmpty ? "" : "性格：\(character.personality)",
            character.scenario.isEmpty ? "" : "场景：\(character.scenario)",
            character.exampleDialogue.isEmpty ? "" : "示例对话：\n\(character.exampleDialogue)",
            "用户名称：\(settings.persona)"
        ]
        let lore = matchingWorldBook(
            character: character,
            history: history,
            persona: settings.persona)
        if !lore.isEmpty {
            sections.append(traditional ? "相關世界書：\n\(lore)" : "相关世界书：\n\(lore)")
        }
        if groupDecision {
            sections.append(traditional
                ? "這是群聊。只在話題與你相關、你被點名或你確實能推進對話時回覆；否則只輸出 [SKIP]。不要替其他成員說話。"
                : "这是群聊。只在话题与你相关、你被点名或你确实能推进对话时回复；否则只输出 [SKIP]。不要替其他成员说话。")
        }
        if spontaneous {
            sections.append(traditional
                ? "現在是單聊暫時空閒的時刻。請根據角色個性、目前情境、最近對話和目前時間，自然地主動發起一則簡短的新訊息；不要假裝使用者剛才說過不存在的話。如果此刻不適合主動說話，只輸出精確文字 [SKIP]，不要解釋原因。"
                : "现在是单聊暂时空闲的时刻。请根据角色性格、当前情境、最近对话和当前时间，自然地主动发起一条简短的新消息；不要假装用户刚刚说过不存在的话。如果此刻不适合主动说话，只输出精确文本 [SKIP]，不要解释原因。")
        }
        return sections.filter { !$0.isEmpty }.joined(separator: "\n\n")
    }

    private static func usesTraditionalChinese(_ locale: Locale) -> Bool {
        guard locale.languageCode == "zh" else { return false }
        if locale.scriptCode?.caseInsensitiveCompare("Hant") == .orderedSame {
            return true
        }
        guard let region = locale.regionCode?.uppercased() else { return false }
        return ["TW", "HK", "MO"].contains(region)
    }

    private static func matchingWorldBook(
        character: CharacterCard,
        history: [ChatMessage],
        persona: String
    ) -> String {
        let source = character.worldBookJSON
        guard !source.isEmpty,
              let data = source.data(using: .utf8),
              let book = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let entries = book["entries"] as? [[String: Any]] else { return "" }

        let haystack = history.suffix(8)
            .map(\.content)
            .joined(separator: "\n")
            .lowercased()
        let maxEntries = min(entries.count, 1_000_000)
        let maxCharacters = 24_000
        var result = ""
        for entry in entries.prefix(maxEntries) {
            guard (entry["enabled"] as? Bool) ?? true else { continue }
            var matched = (entry["constant"] as? Bool) ?? false
            var keys = (entry["keys"] as? [String])
                ?? (entry["key"] as? [String])
                ?? []
            if keys.isEmpty, let key = (entry["key"] as? String)
                ?? (entry["keys"] as? String) {
                keys = [key]
            }
            if !matched {
                matched = keys.contains { key in
                    let clean = key.trimmingCharacters(in: .whitespacesAndNewlines)
                        .lowercased()
                    return !clean.isEmpty && haystack.contains(clean)
                }
            }
            guard matched,
                  let content = entry["content"] as? String,
                  !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                continue
            }
            let expanded = character.replacingMacros(in: content, persona: persona)
            let prefix = result.isEmpty ? "- " : "\n- "
            let remaining = maxCharacters - result.count - prefix.count
            guard remaining > 0 else { break }
            result += prefix
            if expanded.count <= remaining {
                result += expanded
            } else {
                result += String(expanded.prefix(remaining))
                break
            }
        }
        return result
    }

    private static func chatRequest(
        system: String,
        history: [ChatMessage],
        settings: AppSettings,
        apiKey: String
    ) throws -> URLRequest {
        let url = try endpointURL(base: settings.baseURL, suffix: "/chat/completions")
        var messages: [[String: Any]] = [["role": "system", "content": system]]
        messages.append(contentsOf: history.suffix(60).map(openAIMessage))
        var body: [String: Any] = [
            "model": settings.model,
            "messages": messages,
            "stream": false
        ]
        if settings.webSearch {
            let host = url.host?.lowercased() ?? ""
            if host.contains("openrouter.ai") {
                body["plugins"] = [["id": "web"]]
            } else if host.contains("openai.com") {
                body["web_search_options"] = [String: Any]()
            }
        }
        return try jsonRequest(url: url, body: body, format: .chatCompletions, apiKey: apiKey)
    }

    private static func responsesRequest(
        system: String,
        history: [ChatMessage],
        settings: AppSettings,
        apiKey: String
    ) throws -> URLRequest {
        let url = try endpointURL(base: settings.baseURL, suffix: "/responses")
        var body: [String: Any] = [
            "model": settings.model,
            "instructions": system,
            "input": history.suffix(60).map(responsesMessage),
            "stream": false
        ]
        if settings.webSearch {
            body["tools"] = [["type": "web_search"]]
        }
        return try jsonRequest(url: url, body: body, format: .responses, apiKey: apiKey)
    }

    private static func claudeRequest(
        system: String,
        history: [ChatMessage],
        settings: AppSettings,
        apiKey: String
    ) throws -> URLRequest {
        let url = try endpointURL(base: settings.baseURL, suffix: "/v1/messages")
        var body: [String: Any] = [
            "model": settings.model,
            "system": system,
            "messages": history.suffix(60).map(claudeMessage),
            "max_tokens": 4096,
            "stream": false
        ]
        if settings.webSearch {
            body["tools"] = [[
                "type": "web_search_20250305",
                "name": "web_search",
                "max_uses": 5
            ]]
        }
        return try jsonRequest(url: url, body: body, format: .claude, apiKey: apiKey)
    }

    private static func geminiRequest(
        system: String,
        history: [ChatMessage],
        settings: AppSettings,
        apiKey: String
    ) throws -> URLRequest {
        let root = normalizedRoot(settings.baseURL, removing: ["/v1beta"])
        guard var components = URLComponents(
            string: "\(root)/v1beta/models/\(settings.model):generateContent") else {
            throw KiraError.message(NSLocalizedString("API 地址无效", comment: ""))
        }
        if !apiKey.isEmpty {
            components.queryItems = [URLQueryItem(name: "key", value: apiKey)]
        }
        guard let url = components.url else {
            throw KiraError.message(NSLocalizedString("API 地址无效", comment: ""))
        }
        var body: [String: Any] = [
            "systemInstruction": ["parts": [["text": system]]],
            "contents": history.suffix(60).map(geminiMessage)
        ]
        if settings.webSearch {
            body["tools"] = [["google_search": [String: Any]()]]
        }
        return try jsonRequest(url: url, body: body, format: .gemini, apiKey: apiKey)
    }

    private static func openAIMessage(_ message: ChatMessage) -> [String: Any] {
        if let image = message.imageData {
            return [
                "role": roleName(message.role, assistant: "assistant"),
                "content": [
                    ["type": "text", "text": message.content],
                    ["type": "image_url", "image_url": ["url": dataURL(image)]]
                ]
            ]
        }
        return [
            "role": roleName(message.role, assistant: "assistant"),
            "content": message.content
        ]
    }

    private static func responsesMessage(_ message: ChatMessage) -> [String: Any] {
        var content: [[String: Any]] = [[
            "type": message.role == .assistant ? "output_text" : "input_text",
            "text": message.content
        ]]
        if let image = message.imageData, message.role != .assistant {
            content.append(["type": "input_image", "image_url": dataURL(image)])
        }
        return [
            "role": roleName(message.role, assistant: "assistant"),
            "content": content
        ]
    }

    private static func claudeMessage(_ message: ChatMessage) -> [String: Any] {
        var content: [[String: Any]] = []
        if let image = message.imageData {
            content.append([
                "type": "image",
                "source": [
                    "type": "base64",
                    "media_type": "image/jpeg",
                    "data": image.base64EncodedString()
                ]
            ])
        }
        content.append(["type": "text", "text": message.content])
        return [
            "role": message.role == .assistant ? "assistant" : "user",
            "content": content
        ]
    }

    private static func geminiMessage(_ message: ChatMessage) -> [String: Any] {
        var parts: [[String: Any]] = [["text": message.content]]
        if let image = message.imageData {
            parts.append([
                "inline_data": [
                    "mime_type": "image/jpeg",
                    "data": image.base64EncodedString()
                ]
            ])
        }
        return [
            "role": message.role == .assistant ? "model" : "user",
            "parts": parts
        ]
    }

    private static func roleName(_ role: MessageRole, assistant: String) -> String {
        switch role {
        case .assistant: return assistant
        case .system: return "system"
        case .user: return "user"
        }
    }

    private static func jsonRequest(
        url: URL,
        body: [String: Any],
        format: APIFormat,
        apiKey: String
    ) throws -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 120
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        applyHeaders(to: &request, format: format, apiKey: apiKey)
        return request
    }

    private static func applyHeaders(
        to request: inout URLRequest,
        format: APIFormat,
        apiKey: String
    ) {
        guard !apiKey.isEmpty else { return }
        switch format {
        case .claude:
            request.setValue(apiKey, forHTTPHeaderField: "x-api-key")
            request.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")
        case .gemini:
            request.setValue(apiKey, forHTTPHeaderField: "x-goog-api-key")
        default:
            request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        }
    }

    private static func endpointURL(base: String, suffix: String) throws -> URL {
        let clean = base.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let lower = clean.lowercased()
        let known = ["/chat/completions", "/responses", "/v1/messages"]
        let root: String
        if known.contains(where: { lower.hasSuffix($0) }) {
            root = String(clean[..<clean.index(clean.endIndex, offsetBy: -known.first { lower.hasSuffix($0) }!.count)])
        } else {
            root = clean
        }
        let adjusted: String
        if root.hasSuffix("/v1") {
            adjusted = root + suffix
        } else if suffix == "/v1/messages" {
            adjusted = root + suffix
        } else {
            adjusted = root + "/v1" + suffix
        }
        guard let url = URL(string: adjusted) else {
            throw KiraError.message(NSLocalizedString("API 地址无效", comment: ""))
        }
        return url
    }

    private static func modelListURL(settings: AppSettings, apiKey: String) throws -> URL {
        if settings.apiFormat == .gemini {
            let root = normalizedRoot(settings.baseURL, removing: ["/v1beta"])
            var components = URLComponents(string: "\(root)/v1beta/models")
            if !apiKey.isEmpty {
                components?.queryItems = [URLQueryItem(name: "key", value: apiKey)]
            }
            if let url = components?.url { return url }
        }
        let clean = normalizedRoot(
            settings.baseURL,
            removing: ["/chat/completions", "/responses", "/v1/messages"])
        let path: String
        if settings.apiFormat == .claude {
            path = clean.hasSuffix("/v1") ? "/models" : "/v1/models"
        } else {
            path = clean.hasSuffix("/v1") ? "/models" : "/v1/models"
        }
        guard let url = URL(string: clean + path) else {
            throw KiraError.message(NSLocalizedString("API 地址无效", comment: ""))
        }
        return url
    }

    private static func normalizedRoot(_ base: String, removing suffixes: [String]) -> String {
        var value = base.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        for suffix in suffixes where value.lowercased().hasSuffix(suffix.lowercased()) {
            value.removeLast(suffix.count)
            break
        }
        return value.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }

    private static func validate(response: URLResponse, data: Data) throws {
        guard let http = response as? HTTPURLResponse else {
            throw KiraError.message(NSLocalizedString("服务没有返回 HTTP 响应", comment: ""))
        }
        guard (200..<300).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw KiraError.message("HTTP \(http.statusCode) · \(body.prefix(900))")
        }
    }

    private static func extractText(data: Data, format: APIFormat) throws -> String {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw KiraError.message(NSLocalizedString("服务返回了无法解析的数据", comment: ""))
        }
        switch format {
        case .chatCompletions:
            if let choices = root["choices"] as? [[String: Any]],
               let message = choices.first?["message"] as? [String: Any] {
                if let text = message["content"] as? String { return text }
                if let parts = message["content"] as? [[String: Any]] {
                    return parts.compactMap { $0["text"] as? String }.joined()
                }
            }
        case .responses:
            if let value = root["output_text"] as? String { return value }
            if let output = root["output"] as? [[String: Any]] {
                return output.compactMap { $0["content"] as? [[String: Any]] }
                    .flatMap { $0 }
                    .compactMap { ($0["text"] as? String) ?? ($0["output_text"] as? String) }
                    .joined()
            }
        case .claude:
            if let content = root["content"] as? [[String: Any]] {
                return content.compactMap { $0["text"] as? String }.joined()
            }
        case .gemini:
            if let candidates = root["candidates"] as? [[String: Any]],
               let content = candidates.first?["content"] as? [String: Any],
               let parts = content["parts"] as? [[String: Any]] {
                return parts.compactMap { $0["text"] as? String }.joined()
            }
        }
        return ""
    }

    private static func stripReasoning(_ value: String) -> String {
        let pattern = "(?is)<think>.*?</think>|<analysis>.*?</analysis>"
        guard let expression = try? NSRegularExpression(pattern: pattern) else { return value }
        return expression.stringByReplacingMatches(
            in: value,
            range: NSRange(value.startIndex..., in: value),
            withTemplate: "")
    }

    private static func dataURL(_ data: Data) -> String {
        "data:image/jpeg;base64,\(data.base64EncodedString())"
    }
}
