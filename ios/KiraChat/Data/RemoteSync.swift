import CryptoKit
import Foundation
import Security

enum RemoteSyncMode {
    case automatic
    case forceUpload
    case forceDownload
}

enum RemoteSyncOutcome {
    case unchanged(revision: Int64, message: String)
    case uploaded(revision: Int64, message: String)
    case downloaded(revision: Int64, payload: Data, message: String)
}

enum SyncConfiguration {
    private static let defaults = UserDefaults.standard
    private static let serverURLKey = "kirachat.sync.serverURL"
    private static let automaticKey = "kirachat.sync.automatic"
    private static let deviceIDKey = "kirachat.sync.deviceID"
    private static let revisionKey = "kirachat.sync.revision"
    private static let digestKey = "kirachat.sync.digest"
    private static let lastSyncKey = "kirachat.sync.lastSync"
    private static let statusKey = "kirachat.sync.status"
    private static let tokenAccount = "sync-token"
    private static let passwordAccount = "sync-encryption-password"

    static var serverURL: String { defaults.string(forKey: serverURLKey) ?? "" }
    static var automatic: Bool { defaults.bool(forKey: automaticKey) }
    static var token: String { KeychainStore.read(tokenAccount) }
    static var encryptionPassword: String { KeychainStore.read(passwordAccount) }
    static var revision: Int64 { Int64(defaults.integer(forKey: revisionKey)) }
    static var digest: String { defaults.string(forKey: digestKey) ?? "" }
    static var lastSync: Date? { defaults.object(forKey: lastSyncKey) as? Date }
    static var status: String {
        defaults.string(forKey: statusKey) ?? NSLocalizedString("尚未同步", comment: "")
    }

    static var configured: Bool {
        !serverURL.isEmpty && token.count >= 24 && encryptionPassword.count >= 8
    }

    static var deviceID: String {
        if let value = defaults.string(forKey: deviceIDKey), !value.isEmpty { return value }
        let value = UUID().uuidString
        defaults.set(value, forKey: deviceIDKey)
        return value
    }

    static func save(serverURL: String, token: String, password: String, automatic: Bool) {
        let cleanURL = serverURL.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanToken = token.trimmingCharacters(in: .whitespacesAndNewlines)
        let changed = self.serverURL != cleanURL || self.token != cleanToken
            || encryptionPassword != password
        defaults.set(cleanURL, forKey: serverURLKey)
        defaults.set(automatic, forKey: automaticKey)
        KeychainStore.write(cleanToken, account: tokenAccount)
        KeychainStore.write(password, account: passwordAccount)
        if changed {
            defaults.removeObject(forKey: revisionKey)
            defaults.removeObject(forKey: digestKey)
            defaults.removeObject(forKey: lastSyncKey)
        }
    }

    static func record(revision: Int64, digest: String, status: String) {
        defaults.set(revision, forKey: revisionKey)
        defaults.set(digest, forKey: digestKey)
        defaults.set(Date(), forKey: lastSyncKey)
        defaults.set(status, forKey: statusKey)
    }

    static func setStatus(_ value: String) {
        defaults.set(value, forKey: statusKey)
    }
}

enum RemoteSyncService {
    private static let maximumResponseBytes = 256 * 1024 * 1024

    static func testConnection() async throws -> String {
        try requireConfiguration()
        let healthData = try await request(path: "/v1/health", authenticated: false)
        let health = try JSONDecoder().decode(HealthResponse.self, from: healthData)
        guard health.service == "kirachat-sync" else {
            throw syncError("该地址不是澄语同步服务器")
        }
        if let meta = try await fetchMeta() {
            return String(format: NSLocalizedString("连接成功 · 服务器修订版 %lld", comment: ""), meta.revision)
        }
        return NSLocalizedString("连接成功 · 服务器还没有同步内容", comment: "")
    }

    static func synchronize(mode: RemoteSyncMode, localPayload: Data) async throws -> RemoteSyncOutcome {
        try requireConfiguration()
        let localDigest = SyncCryptography.digest(localPayload)
        switch mode {
        case .forceUpload:
            let remote = try await fetchMeta()
            return try await upload(localPayload, remote: remote)
        case .forceDownload:
            return try await download()
        case .automatic:
            let remote = try await fetchMeta()
            guard let remote else {
                return try await upload(localPayload, remote: nil)
            }
            guard SyncConfiguration.revision > 0 else {
                throw syncError("首次同步请明确选择“上传本机”或“下载服务器”")
            }
            if remote.revision == SyncConfiguration.revision {
                if localDigest == SyncConfiguration.digest {
                    let message = NSLocalizedString("已是最新版本", comment: "")
                    return .unchanged(revision: remote.revision, message: message)
                }
                return try await upload(localPayload, remote: remote)
            }
            if remote.revision > SyncConfiguration.revision,
               localDigest == SyncConfiguration.digest {
                return try await download()
            }
            throw syncError("检测到同步冲突，请选择上传本机或下载服务器")
        }
    }

    private static func upload(
        _ payload: Data,
        remote: SyncMeta?
    ) async throws -> RemoteSyncOutcome {
        let body = UploadRequest(
            baseRevision: remote?.revision ?? 0,
            deviceId: SyncConfiguration.deviceID,
            platform: "ios",
            blob: try SyncCryptography.encrypt(payload, password: SyncConfiguration.encryptionPassword))
        let encoded = try JSONEncoder().encode(body)
        let data = try await request(path: "/v1/sync/snapshot", method: "PUT", body: encoded)
        let meta = try JSONDecoder().decode(SyncMeta.self, from: data)
        guard meta.revision > 0 else { throw syncError("同步服务器没有返回修订号") }
        let message = String(
            format: NSLocalizedString("本机内容已上传 · 修订版 %lld", comment: ""),
            meta.revision)
        return .uploaded(revision: meta.revision, message: message)
    }

    private static func download() async throws -> RemoteSyncOutcome {
        let data = try await request(path: "/v1/sync/snapshot")
        let snapshot = try JSONDecoder().decode(SyncSnapshot.self, from: data)
        let payload = try SyncCryptography.decrypt(
            snapshot.blob, password: SyncConfiguration.encryptionPassword)
        let message = String(
            format: NSLocalizedString("已下载服务器内容 · 修订版 %lld", comment: ""),
            snapshot.revision)
        return .downloaded(revision: snapshot.revision, payload: payload, message: message)
    }

    private static func fetchMeta() async throws -> SyncMeta? {
        do {
            let data = try await request(path: "/v1/sync/meta")
            return try JSONDecoder().decode(SyncMeta.self, from: data)
        } catch let error as SyncHTTPError where error.status == 404 {
            return nil
        }
    }

    private static func request(
        path: String,
        method: String = "GET",
        body: Data? = nil,
        authenticated: Bool = true
    ) async throws -> Data {
        let cleanBase = SyncConfiguration.serverURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let url = URL(string: cleanBase + path),
              ["http", "https"].contains(url.scheme?.lowercased() ?? "") else {
            throw syncError("服务器地址必须以 https:// 或 http:// 开头")
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 120
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if authenticated {
            request.setValue("Bearer \(SyncConfiguration.token)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            request.httpBody = body
            request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard data.count <= maximumResponseBytes else { throw syncError("服务器响应过大") }
        guard let http = response as? HTTPURLResponse else { throw syncError("服务器没有返回 HTTP 响应") }
        guard (200...299).contains(http.statusCode) else {
            if http.statusCode == 401 { throw syncError("同步令牌无效") }
            if http.statusCode == 409 { throw syncError("服务器内容已更新，请重新同步") }
            if http.statusCode == 404 { throw SyncHTTPError(status: 404) }
            throw syncError("同步服务器 HTTP \(http.statusCode)")
        }
        return data
    }

    private static func requireConfiguration() throws {
        guard SyncConfiguration.configured else {
            throw syncError("请先填写服务器地址、同步令牌和加密密码")
        }
    }

    private static func syncError(_ value: String) -> KiraError {
        .message(NSLocalizedString(value, comment: ""))
    }
}

private enum SyncCryptography {
    private static let prefix = Data("KiraChat Sync v1\0".utf8)
    private static let aad = Data("kirachat-sync-v1".utf8)

    static func encrypt(_ plaintext: Data, password: String) throws -> EncryptedSyncBlob {
        let salt = randomData(count: 16)
        let nonceData = randomData(count: 12)
        let key = SymmetricKey(data: derive(password: password, salt: salt))
        let nonce = try AES.GCM.Nonce(data: nonceData)
        let sealed = try AES.GCM.seal(plaintext, using: key, nonce: nonce, authenticating: aad)
        var ciphertext = sealed.ciphertext
        ciphertext.append(sealed.tag)
        return EncryptedSyncBlob(
            format: "kirachat-sync-encrypted",
            schemaVersion: 1,
            kdf: "sha256-chain-10000",
            salt: salt.base64EncodedString(),
            nonce: nonceData.base64EncodedString(),
            ciphertext: ciphertext.base64EncodedString())
    }

    static func decrypt(_ blob: EncryptedSyncBlob, password: String) throws -> Data {
        guard blob.format == "kirachat-sync-encrypted", blob.schemaVersion == 1,
              blob.kdf == "sha256-chain-10000",
              let salt = Data(base64Encoded: blob.salt), salt.count == 16,
              let nonceData = Data(base64Encoded: blob.nonce), nonceData.count == 12,
              let combined = Data(base64Encoded: blob.ciphertext), combined.count > 16 else {
            throw KiraError.message(NSLocalizedString("服务器加密数据损坏", comment: ""))
        }
        let ciphertext = combined.dropLast(16)
        let tag = combined.suffix(16)
        do {
            let box = try AES.GCM.SealedBox(
                nonce: AES.GCM.Nonce(data: nonceData),
                ciphertext: ciphertext,
                tag: tag)
            return try AES.GCM.open(
                box,
                using: SymmetricKey(data: derive(password: password, salt: salt)),
                authenticating: aad)
        } catch {
            throw KiraError.message(NSLocalizedString("无法解密同步内容，请检查加密密码", comment: ""))
        }
    }

    static func digest(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private static func derive(password: String, salt: Data) -> Data {
        var initial = prefix
        initial.append(Data(password.utf8))
        initial.append(salt)
        var key = Data(SHA256.hash(data: initial))
        for _ in 1..<10_000 {
            var round = key
            round.append(salt)
            key = Data(SHA256.hash(data: round))
        }
        return key
    }

    private static func randomData(count: Int) -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        _ = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        return Data(bytes)
    }
}

private struct HealthResponse: Codable { let service: String }
private struct SyncMeta: Codable { let revision: Int64 }
private struct SyncSnapshot: Codable {
    let revision: Int64
    let blob: EncryptedSyncBlob
}
private struct UploadRequest: Codable {
    let baseRevision: Int64
    let deviceId: String
    let platform: String
    let blob: EncryptedSyncBlob
}
private struct EncryptedSyncBlob: Codable {
    let format: String
    let schemaVersion: Int
    let kdf: String
    let salt: String
    let nonce: String
    let ciphertext: String
}
private struct SyncHTTPError: Error { let status: Int }

struct SyncPayload: Codable {
    static let formatName = "kirachat-sync-payload"
    var format = SyncPayload.formatName
    var schemaVersion = 1
    var characters: [SyncCharacter]
    var groups: [SyncGroup]
    var messages: [String: [SyncMessage]]
    var settings: SyncPortableSettings

    func validate() throws {
        guard format == Self.formatName, schemaVersion == 1 else {
            throw KiraError.message(NSLocalizedString("服务器同步格式不受支持", comment: ""))
        }
        guard characters.count <= 1000, groups.count <= 1000, messages.count <= 2000,
              Set(characters.map(\.id)).count == characters.count,
              Set(groups.map(\.id)).count == groups.count,
              characters.allSatisfy({ !$0.id.isEmpty && !$0.name.isEmpty }),
              groups.allSatisfy({ !$0.id.isEmpty }),
              messages.values.reduce(0, { $0 + $1.count }) <= 200_000 else {
            throw KiraError.message(NSLocalizedString("服务器同步内容超出安全限制", comment: ""))
        }
        var assetBytes = settings.personaAvatarData?.count ?? 0
        for card in characters {
            assetBytes += card.avatarData?.count ?? 0
            assetBytes += card.chatBackgroundData?.count ?? 0
            try validateAsset(card.avatarData)
            try validateAsset(card.chatBackgroundData)
        }
        for group in groups {
            assetBytes += group.chatBackgroundData?.count ?? 0
            try validateAsset(group.chatBackgroundData)
        }
        for values in messages.values {
            for message in values {
                assetBytes += message.imageData?.count ?? 0
                try validateAsset(message.imageData)
            }
        }
        try validateAsset(settings.personaAvatarData)
        guard assetBytes <= 128 * 1024 * 1024 else {
            throw KiraError.message(NSLocalizedString("同步图片超出安全限制", comment: ""))
        }
    }

    private func validateAsset(_ data: Data?) throws {
        guard (data?.count ?? 0) <= 20 * 1024 * 1024 else {
            throw KiraError.message(NSLocalizedString("同步图片超出安全限制", comment: ""))
        }
    }
}

struct SyncCharacter: Codable {
    var id: String
    var name: String
    var description: String
    var personality: String
    var scenario: String
    var firstMessage: String
    var exampleDialogue: String
    var creatorNotes: String
    var worldBookJSON: String
    var avatarData: Data?
    var chatBackgroundData: Data?
    var lastUsed: Int64
    var unread: Int
    var muted: Bool
    var pinned: Bool
}

struct SyncGroup: Codable {
    var id: String
    var name: String
    var memberIDs: [String]
    var lastUsed: Int64
    var unread: Int
    var muted: Bool
    var pinned: Bool
    var chatBackgroundData: Data?
}

struct SyncMessage: Codable {
    var id: String
    var role: String
    var content: String
    var speaker: String
    var attachment: String
    var imageData: Data?
    var latitude: Double?
    var longitude: Double?
    var callDurationSeconds: Int?
    var timestamp: Int64
    var failed: Bool
}

struct SyncPortableSettings: Codable {
    var persona: String
    var personaAvatarData: Data?
    var webSearch: Bool
    var showReasoning: Bool
    var characterAutonomousMessages: Bool
    var groupAutonomousMessages: Bool

    private enum CodingKeys: String, CodingKey {
        case persona, personaAvatarData, webSearch, showReasoning
        case characterAutonomousMessages, groupAutonomousMessages
    }

    init(
        persona: String,
        personaAvatarData: Data?,
        webSearch: Bool,
        showReasoning: Bool,
        characterAutonomousMessages: Bool,
        groupAutonomousMessages: Bool
    ) {
        self.persona = persona
        self.personaAvatarData = personaAvatarData
        self.webSearch = webSearch
        self.showReasoning = showReasoning
        self.characterAutonomousMessages = characterAutonomousMessages
        self.groupAutonomousMessages = groupAutonomousMessages
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        persona = try values.decodeIfPresent(String.self, forKey: .persona) ?? "你"
        personaAvatarData = try values.decodeIfPresent(Data.self, forKey: .personaAvatarData)
        webSearch = try values.decodeIfPresent(Bool.self, forKey: .webSearch) ?? true
        showReasoning = try values.decodeIfPresent(Bool.self, forKey: .showReasoning) ?? false
        characterAutonomousMessages = try values.decodeIfPresent(
            Bool.self, forKey: .characterAutonomousMessages) ?? true
        groupAutonomousMessages = try values.decodeIfPresent(
            Bool.self, forKey: .groupAutonomousMessages) ?? true
    }
}

enum SHA256Digest {
    static func hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}
