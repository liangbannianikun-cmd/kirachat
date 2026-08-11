import Foundation

enum AccountAPIService {
    private static let responsesURL = URL(string: "https://chatgpt.com/backend-api/codex/responses")!
    private static let modelsURL = URL(string: "https://chatgpt.com/backend-api/codex/models?client_version=1.0.0")!
    private static let codexUserAgent = "codex_cli_rs/0.130.0 (KiraChat/0.9; iOS)"
    private static let browserUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"

    static func completeGPT(
        character: CharacterCard,
        history: [ChatMessage],
        settings: AppSettings,
        accessToken: String,
        groupDecision: Bool = false,
        spontaneous: Bool = false
    ) async throws -> String {
        let model = settings.gptModel.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !model.isEmpty else {
            throw KiraError.message(NSLocalizedString("请先选择 GPT 模型", comment: ""))
        }
        let instructions = APIService.systemPrompt(
            character: character,
            history: history,
            settings: settings,
            groupDecision: groupDecision,
            spontaneous: spontaneous)
        var body: [String: Any] = [
            "model": model,
            "input": history.suffix(60).map(responsesMessage),
            "instructions": instructions,
            "stream": true,
            "store": false,
            "reasoning": ["effort": "medium", "summary": "auto"]
        ]
        if settings.webSearch { body["tools"] = [["type": "web_search"]] }
        let bodyData = try JSONSerialization.data(withJSONObject: body)
        let result = try await codexRequest(
            url: responsesURL,
            method: "POST",
            accessToken: accessToken,
            body: bodyData)
        try validateCodex(result, operation: NSLocalizedString("GPT 账户请求失败", comment: ""))
        let text = parseResponses(data: result.data, showReasoning: settings.showReasoning)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else {
            throw KiraError.message(NSLocalizedString("GPT 账户没有返回文字内容", comment: ""))
        }
        return text
    }

    static func fetchGPTModels(accessToken: String) async throws -> [String] {
        let result = try await codexRequest(
            url: modelsURL,
            method: "GET",
            accessToken: accessToken,
            body: nil)
        try validateCodex(result, operation: NSLocalizedString("获取 GPT 模型失败", comment: ""))
        guard let root = try JSONSerialization.jsonObject(with: result.data) as? [String: Any],
              let values = root["models"] as? [[String: Any]] else {
            throw KiraError.message(NSLocalizedString("GPT 模型列表格式无效", comment: ""))
        }
        let ranked: [(String, Int)] = values.compactMap { item in
            let visibility = (item["visibility"] as? String ?? "").lowercased()
            guard visibility != "hide", visibility != "hidden",
                  let slug = item["slug"] as? String, !slug.isEmpty else { return nil }
            return (slug, (item["priority"] as? NSNumber)?.intValue ?? 10_000)
        }
        let models = ranked.sorted { left, right in
            left.1 == right.1
                ? left.0.localizedCaseInsensitiveCompare(right.0) == .orderedAscending
                : left.1 < right.1
        }.map(\.0)
        guard !models.isEmpty else {
            throw KiraError.message(NSLocalizedString("此 GPT 账户没有返回可见模型", comment: ""))
        }
        var unique: [String] = []
        for model in models where !unique.contains(model) { unique.append(model) }
        return unique
    }

    private static func codexRequest(
        url: URL,
        method: String,
        accessToken: String,
        body: Data?
    ) async throws -> CodexHTTPResult {
        let first = try await sendCodex(
            url: url,
            method: method,
            accessToken: accessToken,
            body: body,
            browserUserAgent: false)
        if first.status == 403 {
            return try await sendCodex(
                url: url,
                method: method,
                accessToken: accessToken,
                body: body,
                browserUserAgent: true)
        }
        return first
    }

    private static func sendCodex(
        url: URL,
        method: String,
        accessToken: String,
        body: Data?,
        browserUserAgent: Bool
    ) async throws -> CodexHTTPResult {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = method == "GET" ? 30 : 150
        request.httpBody = body
        request.setValue("text/event-stream, application/json", forHTTPHeaderField: "Accept")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("codex_cli_rs", forHTTPHeaderField: "originator")
        request.setValue(
            browserUserAgent ? Self.browserUserAgent : Self.codexUserAgent,
            forHTTPHeaderField: "User-Agent")
        if body != nil {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        let requestID = UUID().uuidString
        request.setValue(requestID, forHTTPHeaderField: "session_id")
        request.setValue(requestID, forHTTPHeaderField: "x-client-request-id")
        let accountID = OpenAIAccountAuth.accountID(from: accessToken)
        if !accountID.isEmpty {
            request.setValue(accountID, forHTTPHeaderField: "ChatGPT-Account-ID")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw KiraError.message(NSLocalizedString("服务没有返回 HTTP 响应", comment: ""))
        }
        return CodexHTTPResult(status: http.statusCode, data: data)
    }

    private static func validateCodex(_ result: CodexHTTPResult, operation: String) throws {
        guard (200..<300).contains(result.status) else {
            var detail = ""
            if let root = try? JSONSerialization.jsonObject(with: result.data) as? [String: Any] {
                if let error = root["error"] as? [String: Any] {
                    detail = error["message"] as? String ?? ""
                } else {
                    detail = root["detail"] as? String
                        ?? root["message"] as? String
                        ?? root["error"] as? String
                        ?? ""
                }
            }
            if detail.isEmpty { detail = String(data: result.data, encoding: .utf8) ?? "" }
            let lower = detail.lowercased()
            if result.status == 403,
               lower.contains("cloudflare") || lower.contains("cf-chl")
                    || lower.contains("enable javascript") || lower.contains("challenge") {
                detail = NSLocalizedString("当前网络触发了 ChatGPT 的 Cloudflare 验证，请切换网络或代理后重试", comment: "")
            } else if result.status == 403 {
                detail = NSLocalizedString("当前账户或工作区没有 Codex 访问权限，请确认订阅和工作区策略后重新登录", comment: "")
            }
            throw KiraError.message("\(operation)（HTTP \(result.status)）\(detail.isEmpty ? "" : "：\(detail.prefix(900))")")
        }
    }

    private static func parseResponses(data: Data, showReasoning: Bool) -> String {
        let raw = String(data: data, encoding: .utf8) ?? ""
        var output = ""
        var reasoning = ""
        var sawEvent = false
        for line in raw.components(separatedBy: .newlines) {
            guard line.hasPrefix("data:") else { continue }
            sawEvent = true
            let value = line.dropFirst(5).trimmingCharacters(in: .whitespaces)
            guard value != "[DONE]", let eventData = value.data(using: .utf8),
                  let event = try? JSONSerialization.jsonObject(with: eventData) as? [String: Any] else { continue }
            let type = event["type"] as? String ?? ""
            if type == "response.output_text.delta" {
                output += event["delta"] as? String ?? ""
            } else if type == "response.reasoning_summary_text.delta"
                        || type == "response.reasoning_text.delta"
                        || type == "response.reasoning.delta" {
                reasoning += event["delta"] as? String ?? ""
            } else if type == "error" || type == "response.failed" {
                let nestedResponse = event["response"] as? [String: Any]
                let error = (event["error"] as? [String: Any])
                    ?? (nestedResponse?["error"] as? [String: Any])
                if output.isEmpty { output = error?["message"] as? String ?? "" }
            }
        }
        if !sawEvent,
           let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            output = extractOutput(root)
        }
        if showReasoning, !reasoning.isEmpty {
            return "<think>\n\(reasoning)\n</think>\n\n\(output)"
        }
        return output
    }

    private static func extractOutput(_ root: [String: Any]) -> String {
        if let text = root["output_text"] as? String { return text }
        guard let output = root["output"] as? [[String: Any]] else { return "" }
        return output.compactMap { $0["content"] as? [[String: Any]] }
            .flatMap { $0 }
            .compactMap { ($0["text"] as? String) ?? ($0["output_text"] as? String) }
            .joined()
    }

    private static func responsesMessage(_ message: ChatMessage) -> [String: Any] {
        var content: [[String: Any]] = [[
            "type": message.role == .assistant ? "output_text" : "input_text",
            "text": message.content
        ]]
        if let image = message.imageData, message.role != .assistant {
            content.append([
                "type": "input_image",
                "image_url": "data:image/jpeg;base64,\(image.base64EncodedString())"
            ])
        }
        return [
            "role": message.role == .assistant ? "assistant" : "user",
            "content": content
        ]
    }
}

private struct CodexHTTPResult {
    let status: Int
    let data: Data
}
