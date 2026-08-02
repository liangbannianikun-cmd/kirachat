import Foundation

struct BackupArchive: Codable {
    static let currentSchema = 1
    static let formatName = "kirachat-ios-backup"

    var format = BackupArchive.formatName
    var schemaVersion = BackupArchive.currentSchema
    var appVersion = "0.9.0"
    var platform = "ios"
    var createdAt = Date()
    var payload: PersistedState

    static func decode(_ data: Data) throws -> BackupArchive {
        let archive = try JSONDecoder().decode(BackupArchive.self, from: data)
        guard archive.format == formatName else {
            throw KiraError.message(NSLocalizedString("这不是澄语 iOS 备份文件", comment: ""))
        }
        guard (1...currentSchema).contains(archive.schemaVersion) else {
            throw KiraError.message(NSLocalizedString("备份版本不受支持", comment: ""))
        }
        try archive.validate()
        return archive
    }

    var summary: String {
        let messages = payload.messages.values.reduce(0) { $0 + $1.count }
        return String(
            format: NSLocalizedString("%d 位角色 · %d 个群聊 · %d 条消息", comment: ""),
            payload.characters.count,
            payload.groups.count,
            messages)
    }

    func validate() throws {
        guard payload.characters.count <= 1000,
              payload.groups.count <= 1000,
              payload.messages.count <= 2000 else {
            throw KiraError.message(NSLocalizedString("备份内容超出安全限制", comment: ""))
        }
        let characterIDs = payload.characters.map(\.id)
        guard Set(characterIDs).count == characterIDs.count,
              characterIDs.allSatisfy({ !$0.trimmingCharacters(in: .whitespaces).isEmpty }) else {
            throw KiraError.message(NSLocalizedString("备份包含重复或无效角色", comment: ""))
        }
        let groupIDs = payload.groups.map(\.id)
        guard Set(groupIDs).count == groupIDs.count else {
            throw KiraError.message(NSLocalizedString("备份包含重复群聊", comment: ""))
        }
        let messageCount = payload.messages.values.reduce(0) { $0 + $1.count }
        guard messageCount <= 200_000 else {
            throw KiraError.message(NSLocalizedString("备份消息数量超出安全限制", comment: ""))
        }

        var assetBytes = payload.settings.personaAvatarData?.count ?? 0
        guard (payload.settings.personaAvatarData?.count ?? 0) <= 20 * 1024 * 1024 else {
            throw KiraError.message(NSLocalizedString("备份中的单个图片不能超过 20 MB", comment: ""))
        }
        for card in payload.characters {
            assetBytes += card.avatarData?.count ?? 0
            assetBytes += card.chatBackgroundData?.count ?? 0
            guard (card.avatarData?.count ?? 0) <= 20 * 1024 * 1024,
                  (card.chatBackgroundData?.count ?? 0) <= 20 * 1024 * 1024 else {
                throw KiraError.message(NSLocalizedString("备份中的单个图片不能超过 20 MB", comment: ""))
            }
        }
        for group in payload.groups {
            assetBytes += group.chatBackgroundData?.count ?? 0
            guard (group.chatBackgroundData?.count ?? 0) <= 20 * 1024 * 1024 else {
                throw KiraError.message(NSLocalizedString("备份中的单个图片不能超过 20 MB", comment: ""))
            }
        }
        for messages in payload.messages.values {
            for message in messages {
                assetBytes += message.imageData?.count ?? 0
                guard (message.imageData?.count ?? 0) <= 20 * 1024 * 1024 else {
                    throw KiraError.message(NSLocalizedString("备份中的单个图片不能超过 20 MB", comment: ""))
                }
            }
        }
        guard assetBytes <= 128 * 1024 * 1024 else {
            throw KiraError.message(NSLocalizedString("备份图片总量不能超过 128 MB", comment: ""))
        }
    }
}
