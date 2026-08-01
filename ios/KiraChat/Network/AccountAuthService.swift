import Foundation

struct GPTTokenSet {
    let accessToken: String
    let refreshToken: String
    let expiresAt: TimeInterval
}

struct OpenAIDeviceChallenge {
    let userCode: String
    let verificationURL: URL
    fileprivate let deviceAuthID: String
    fileprivate let interval: TimeInterval
}

struct GitHubDeviceChallenge {
    let userCode: String
    let verificationURL: URL
    fileprivate let deviceCode: String
    fileprivate let interval: TimeInterval
    fileprivate let expiresAt: Date
    fileprivate let clientID: String
}

struct GitHubLoginResult {
    let accessToken: String
    let login: String
}

enum OpenAIAccountAuth {
    private static let issuer = "https://auth.openai.com"
    private static let clientID = "app_EMoamEEZ73f0CkXaXp7hrann"
    private static let redirectURI = "https://auth.openai.com/deviceauth/callback"
    private static let browserUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"

    static func requestDeviceCode() async throws -> OpenAIDeviceChallenge {
        let url = URL(string: "\(issuer)/api/accounts/deviceauth/usercode")!
        var lastResponse: HTTPResult?
        for attempt in 1...4 {
            try Task.checkCancellation()
            let result = try await postJSON(url, body: ["client_id": clientID])
            lastResponse = result
            if result.status != 429 {
                try validate(result, prefix: NSLocalizedString("获取登录验证码失败", comment: ""))
                let json = try object(result.data)
                guard let userCode = json["user_code"] as? String,
                      let deviceAuthID = json["device_auth_id"] as? String,
                      !userCode.isEmpty, !deviceAuthID.isEmpty else {
                    throw KiraError.message(NSLocalizedString("OpenAI 没有返回完整的设备验证码", comment: ""))
                }
                let interval = number(json["interval"], fallback: 5)
                return OpenAIDeviceChallenge(
                    userCode: userCode,
                    verificationURL: URL(string: "\(issuer)/codex/device")!,
                    deviceAuthID: deviceAuthID,
                    interval: max(3, interval))
            }
            if attempt < 4 {
                let retry = result.retryAfter > 0 ? result.retryAfter : pow(2, Double(attempt))
                try await Task.sleep(nanoseconds: UInt64(min(60, max(1, retry)) * 1_000_000_000))
            }
        }
        if lastResponse?.status == 429 {
            throw KiraError.message(NSLocalizedString("OpenAI 暂时限制登录请求，请稍后再试", comment: ""))
        }
        throw KiraError.message(NSLocalizedString("获取登录验证码失败", comment: ""))
    }

    static func finishDeviceLogin(_ challenge: OpenAIDeviceChallenge) async throws -> GPTTokenSet {
        let pollURL = URL(string: "\(issuer)/api/accounts/deviceauth/token")!
        let deadline = Date().addingTimeInterval(15 * 60)
        var authorization: [String: Any]?
        while Date() < deadline {
            try Task.checkCancellation()
            try await Task.sleep(nanoseconds: UInt64(challenge.interval * 1_000_000_000))
            let result = try await postJSON(pollURL, body: [
                "device_auth_id": challenge.deviceAuthID,
                "user_code": challenge.userCode
            ])
            if result.status == 200 {
                authorization = try object(result.data)
                break
            }
            if [403, 404, 429].contains(result.status), !result.cloudflareChallenge {
                continue
            }
            try validate(result, prefix: NSLocalizedString("等待 OpenAI 授权失败", comment: ""))
        }
        guard let authorization,
              let code = authorization["authorization_code"] as? String,
              let verifier = authorization["code_verifier"] as? String,
              !code.isEmpty, !verifier.isEmpty else {
            throw KiraError.message(NSLocalizedString("登录等待已超时，请重试", comment: ""))
        }
        let result = try await postForm(
            URL(string: "\(issuer)/oauth/token")!,
            values: [
                "grant_type": "authorization_code",
                "code": code,
                "redirect_uri": redirectURI,
                "client_id": clientID,
                "code_verifier": verifier
            ])
        try validate(result, prefix: NSLocalizedString("交换登录令牌失败", comment: ""))
        return try parseTokens(result.data, previousRefreshToken: "")
    }

    static func validTokens(_ tokens: GPTTokenSet, forceRefresh: Bool = false) async throws -> GPTTokenSet {
        let expiry = tokens.expiresAt > 0 ? tokens.expiresAt : jwtExpiry(tokens.accessToken)
        if !forceRefresh, !tokens.accessToken.isEmpty,
           (expiry <= 0 || Date().timeIntervalSince1970 + 120 < expiry) {
            return tokens
        }
        guard !tokens.refreshToken.isEmpty else {
            if !forceRefresh, !tokens.accessToken.isEmpty { return tokens }
            throw KiraError.message(NSLocalizedString("GPT 登录已失效，请重新登录", comment: ""))
        }
        let result = try await postForm(
            URL(string: "\(issuer)/oauth/token")!,
            values: [
                "grant_type": "refresh_token",
                "refresh_token": tokens.refreshToken,
                "client_id": clientID
            ])
        if [400, 401, 403].contains(result.status) {
            throw KiraError.message(NSLocalizedString("GPT 登录已过期，请重新登录", comment: ""))
        }
        try validate(result, prefix: NSLocalizedString("刷新 GPT 登录失败", comment: ""))
        return try parseTokens(result.data, previousRefreshToken: tokens.refreshToken)
    }

    static func accountID(from token: String) -> String {
        let auth = jwtClaims(token)?["https://api.openai.com/auth"] as? [String: Any]
        return auth?["chatgpt_account_id"] as? String ?? ""
    }

    static func accountSummary(from token: String) -> String {
        guard !token.isEmpty else { return NSLocalizedString("未登录", comment: "") }
        let auth = jwtClaims(token)?["https://api.openai.com/auth"] as? [String: Any]
        let plan = (auth?["chatgpt_plan_type"] as? String ?? "").trimmingCharacters(in: .whitespaces)
        return plan.isEmpty ? NSLocalizedString("已登录 · ChatGPT", comment: "") : "\(NSLocalizedString("已登录 · ChatGPT", comment: "")) \(plan.capitalized)"
    }

    private static func parseTokens(_ data: Data, previousRefreshToken: String) throws -> GPTTokenSet {
        let json = try object(data)
        guard let access = json["access_token"] as? String, !access.isEmpty else {
            throw KiraError.message(NSLocalizedString("OpenAI 没有返回访问令牌", comment: ""))
        }
        let refresh = (json["refresh_token"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? previousRefreshToken
        let expiresIn = number(json["expires_in"], fallback: 0)
        let expiresAt = expiresIn > 0
            ? Date().timeIntervalSince1970 + expiresIn
            : jwtExpiry(access)
        return GPTTokenSet(accessToken: access, refreshToken: refresh, expiresAt: expiresAt)
    }

    private static func jwtClaims(_ token: String) -> [String: Any]? {
        let parts = token.split(separator: ".")
        guard parts.count > 1 else { return nil }
        var encoded = String(parts[1]).replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        encoded += String(repeating: "=", count: (4 - encoded.count % 4) % 4)
        guard let data = Data(base64Encoded: encoded) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    }

    private static func jwtExpiry(_ token: String) -> TimeInterval {
        number(jwtClaims(token)?["exp"], fallback: 0)
    }

    private static func postJSON(_ url: URL, body: [String: Any]) async throws -> HTTPResult {
        var request = baseRequest(url)
        request.httpMethod = "POST"
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        return try await send(request)
    }

    private static func postForm(_ url: URL, values: [String: String]) async throws -> HTTPResult {
        var request = baseRequest(url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = formData(values)
        return try await send(request)
    }

    private static func baseRequest(_ url: URL) -> URLRequest {
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(browserUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("zh-CN,zh;q=0.9,en;q=0.8", forHTTPHeaderField: "Accept-Language")
        return request
    }
}

enum GitHubCopilotAuth {
    private static let userAgent = "KiraChat-iOS/0.9"

    static func requestDeviceCode(clientID: String) async throws -> GitHubDeviceChallenge {
        let clean = clientID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else {
            throw KiraError.message(NSLocalizedString("请先填写 GitHub OAuth Client ID", comment: ""))
        }
        let result = try await postForm(
            URL(string: "https://github.com/login/device/code")!,
            values: ["client_id": clean, "scope": "read:user"])
        try validate(result, prefix: NSLocalizedString("获取 GitHub 验证码失败", comment: ""))
        let json = try object(result.data)
        guard let deviceCode = json["device_code"] as? String,
              let userCode = json["user_code"] as? String,
              !deviceCode.isEmpty, !userCode.isEmpty else {
            throw KiraError.message(NSLocalizedString("GitHub 没有返回完整的设备验证码", comment: ""))
        }
        let verification = (json["verification_uri"] as? String)
            .flatMap(URL.init(string:)) ?? URL(string: "https://github.com/login/device")!
        let interval = max(5, number(json["interval"], fallback: 5))
        let expires = max(60, number(json["expires_in"], fallback: 900))
        return GitHubDeviceChallenge(
            userCode: userCode,
            verificationURL: verification,
            deviceCode: deviceCode,
            interval: interval,
            expiresAt: Date().addingTimeInterval(expires),
            clientID: clean)
    }

    static func finishDeviceLogin(_ challenge: GitHubDeviceChallenge) async throws -> GitHubLoginResult {
        var interval = challenge.interval
        while Date() < challenge.expiresAt {
            try Task.checkCancellation()
            try await Task.sleep(nanoseconds: UInt64(interval * 1_000_000_000))
            let result = try await postForm(
                URL(string: "https://github.com/login/oauth/access_token")!,
                values: [
                    "client_id": challenge.clientID,
                    "device_code": challenge.deviceCode,
                    "grant_type": "urn:ietf:params:oauth:grant-type:device_code"
                ])
            try validate(result, prefix: NSLocalizedString("等待 GitHub 授权失败", comment: ""))
            let json = try object(result.data)
            if let token = json["access_token"] as? String, !token.isEmpty {
                return GitHubLoginResult(
                    accessToken: token,
                    login: try await fetchLogin(accessToken: token))
            }
            switch json["error"] as? String ?? "" {
            case "authorization_pending": continue
            case "slow_down": interval += 5
            case "access_denied":
                throw KiraError.message(NSLocalizedString("你已拒绝 GitHub 授权", comment: ""))
            case "expired_token":
                throw KiraError.message(NSLocalizedString("GitHub 验证码已过期，请重试", comment: ""))
            case let error where !error.isEmpty:
                throw KiraError.message((json["error_description"] as? String) ?? error)
            default: continue
            }
        }
        throw KiraError.message(NSLocalizedString("GitHub 登录等待已超时，请重试", comment: ""))
    }

    private static func fetchLogin(accessToken: String) async throws -> String {
        var request = URLRequest(url: URL(string: "https://api.github.com/user")!)
        request.timeoutInterval = 20
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("2022-11-28", forHTTPHeaderField: "X-GitHub-Api-Version")
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        let result = try await send(request)
        try validate(result, prefix: NSLocalizedString("读取 GitHub 账户失败", comment: ""))
        return (try object(result.data)["login"] as? String) ?? ""
    }

    private static func postForm(_ url: URL, values: [String: String]) async throws -> HTTPResult {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 20
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.httpBody = formData(values)
        return try await send(request)
    }
}

private struct HTTPResult {
    let status: Int
    let data: Data
    let retryAfter: TimeInterval
    let cloudflareChallenge: Bool
}

private func send(_ request: URLRequest) async throws -> HTTPResult {
    let (data, response) = try await URLSession.shared.data(for: request)
    guard let http = response as? HTTPURLResponse else {
        throw KiraError.message(NSLocalizedString("服务没有返回 HTTP 响应", comment: ""))
    }
    let text = String(data: data, encoding: .utf8)?.lowercased() ?? ""
    let challenged = http.value(forHTTPHeaderField: "cf-mitigated")?.lowercased() == "challenge"
        || text.contains("cloudflare") || text.contains("cf-chl")
        || text.contains("enable javascript")
    return HTTPResult(
        status: http.statusCode,
        data: data,
        retryAfter: TimeInterval(http.value(forHTTPHeaderField: "Retry-After") ?? "") ?? 0,
        cloudflareChallenge: challenged)
}

private func validate(_ result: HTTPResult, prefix: String) throws {
    guard (200..<300).contains(result.status) else {
        if result.status == 403, result.cloudflareChallenge {
            throw KiraError.message("\(prefix)（HTTP 403）：\(NSLocalizedString("当前网络触发了 Cloudflare 验证，请切换网络或代理后重试", comment: ""))")
        }
        var detail = ""
        if let json = try? object(result.data) {
            if let error = json["error"] as? [String: Any] {
                detail = (error["message"] as? String) ?? (error["code"] as? String) ?? ""
            } else {
                detail = (json["error_description"] as? String)
                    ?? (json["message"] as? String)
                    ?? (json["error"] as? String)
                    ?? ""
            }
        }
        if detail.isEmpty { detail = String(data: result.data, encoding: .utf8) ?? "" }
        let clean = detail.trimmingCharacters(in: .whitespacesAndNewlines)
        throw KiraError.message("\(prefix)（HTTP \(result.status)）\(clean.isEmpty ? "" : "：\(clean.prefix(240))")")
    }
}

private func object(_ data: Data) throws -> [String: Any] {
    guard let value = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
        throw KiraError.message(NSLocalizedString("服务返回了无法解析的数据", comment: ""))
    }
    return value
}

private func number(_ value: Any?, fallback: TimeInterval) -> TimeInterval {
    if let number = value as? NSNumber { return number.doubleValue }
    if let string = value as? String, let number = TimeInterval(string) { return number }
    return fallback
}

private func formData(_ values: [String: String]) -> Data {
    let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
    let body = values.sorted { $0.key < $1.key }.map { key, value in
        let encodedKey = key.addingPercentEncoding(withAllowedCharacters: allowed) ?? key
        let encodedValue = value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
        return "\(encodedKey)=\(encodedValue)"
    }.joined(separator: "&")
    return Data(body.utf8)
}
