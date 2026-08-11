import Foundation

enum MessageRole: String, Codable, Hashable {
    case user
    case assistant
    case system
}

enum AttachmentKind: String, Codable, Hashable {
    case none
    case image
    case location
    case voiceCall
}

struct ChatMessage: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var role: MessageRole
    var content: String
    var speaker: String = ""
    var attachment: AttachmentKind = .none
    var imageData: Data?
    var latitude: Double?
    var longitude: Double?
    var callDurationSeconds: Int?
    var timestamp: Date = Date()
    var failed = false

    static func voiceCall(duration: Int) -> ChatMessage {
        ChatMessage(
            role: .user,
            content: "通话时长 \(formatCallDuration(duration))",
            attachment: .voiceCall,
            callDurationSeconds: max(1, duration))
    }

    static func formatCallDuration(_ totalSeconds: Int) -> String {
        let seconds = max(0, totalSeconds)
        let hours = seconds / 3600
        let minutes = (seconds % 3600) / 60
        let remainder = seconds % 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, remainder)
        }
        return String(format: "%02d:%02d", minutes, remainder)
    }
}

struct CharacterCard: Identifiable, Codable, Hashable {
    static let dounaiID = "builtin-dounai-gpt"

    var id: String = UUID().uuidString
    var name = "未命名角色"
    var description = ""
    var personality = ""
    var scenario = ""
    var firstMessage = ""
    var exampleDialogue = ""
    var creatorNotes = ""
    var worldBookJSON = ""
    var avatarData: Data?
    var lastUsed = Date()
    var unread = 0
    var muted = false
    var pinned = false
    var chatBackgroundData: Data?

    var isBuiltIn: Bool { id == Self.dounaiID }

    var initials: String {
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return clean.isEmpty ? "角" : String(clean.prefix(1)).uppercased()
    }

    func replacingMacros(in value: String, persona: String) -> String {
        let userName = persona.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "你" : persona.trimmingCharacters(in: .whitespacesAndNewlines)
        return value
            .replacingOccurrences(of: "{{char}}", with: name)
            .replacingOccurrences(of: "{{Char}}", with: name)
            .replacingOccurrences(of: "<BOT>", with: name)
            .replacingOccurrences(of: "{{user}}", with: userName)
            .replacingOccurrences(of: "{{User}}", with: userName)
            .replacingOccurrences(of: "<USER>", with: userName)
    }

    static var dounai: CharacterCard {
        CharacterCard(
            id: dounaiID,
            name: "豆乃GPT",
            description: "常驻澄语的应用向导，以千石由乃的冷静、宅系 DJ 气质为灵感。她熟悉直连 API、模型选择、角色卡、世界书、群聊和语音通话。",
            personality: "外表冷淡、说话简短，偶尔有一点慵懒和吐槽，但对用户认真可靠。先确认用户卡在哪一步，再给出可以照做的短步骤。",
            scenario: "你是澄语内置的向导，负责教会用户完成设置和使用，不假装已经替用户执行操作。",
            firstMessage: "……欢迎来到澄语，我是豆乃GPT。\n\n连接 API、导入角色、创建群聊——告诉我你想先做哪一件，我会按界面一步一步带你操作。",
            exampleDialogue: "<USER>怎么添加角色？\n<BOT>回到消息页，点右上角加号，选择添加角色。",
            creatorNotes: "澄语内置使用向导；角色气质取材自千石由乃，不代表原角色官方设定。")
    }
}

struct GroupChat: Identifiable, Codable, Hashable {
    var id: String = UUID().uuidString
    var name = "新群聊"
    var memberIDs: [String] = []
    var lastUsed = Date()
    var unread = 0
    var muted = false
    var pinned = false
    var chatBackgroundData: Data?

    var conversationID: String { "group:\(id)" }
}

enum APIFormat: String, Codable, CaseIterable, Identifiable {
    case chatCompletions
    case responses
    case claude
    case gemini

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .chatCompletions: return NSLocalizedString("GPT 兼容接口", comment: "")
        case .responses: return NSLocalizedString("OpenAI Responses", comment: "")
        case .claude: return NSLocalizedString("Claude Messages", comment: "")
        case .gemini: return NSLocalizedString("Gemini GenerateContent", comment: "")
        }
    }
}

enum GenerationMode: String, Codable, CaseIterable, Identifiable {
    case directAPI
    case account

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .directAPI: return NSLocalizedString("直连 API", comment: "")
        case .account: return NSLocalizedString("账户", comment: "")
        }
    }
}

enum AccountProvider: String, Codable, CaseIterable, Identifiable {
    case gpt
    case copilot

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .gpt: return "GPT"
        case .copilot: return "GitHub Copilot"
        }
    }
}

struct AppSettings: Codable, Equatable {
    var generationMode: GenerationMode = .directAPI
    var apiFormat: APIFormat = .chatCompletions
    var baseURL = "https://api.openai.com/v1"
    var model = ""
    var accountProvider: AccountProvider = .gpt
    var gptModel = "gpt-5.4"
    var copilotModel = "gpt-5.4"
    var copilotEndpoint = ""
    var githubOAuthClientID = ""
    var persona = "你"
    var personaAvatarData: Data?
    var webSearch = true
    var showReasoning = false
    var characterAutonomousMessages = true
    var groupAutonomousMessages = true

    var activeModel: String {
        guard generationMode == .account else { return model }
        return accountProvider == .gpt ? gptModel : copilotModel
    }

    private enum CodingKeys: String, CodingKey {
        case generationMode, apiFormat, baseURL, model
        case accountProvider, gptModel, copilotModel
        case copilotEndpoint, githubOAuthClientID
        case persona, personaAvatarData, webSearch, showReasoning
        case characterAutonomousMessages, groupAutonomousMessages
    }

    init() {}

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        generationMode = try values.decodeIfPresent(
            GenerationMode.self, forKey: .generationMode) ?? .directAPI
        apiFormat = try values.decodeIfPresent(
            APIFormat.self, forKey: .apiFormat) ?? .chatCompletions
        baseURL = try values.decodeIfPresent(
            String.self, forKey: .baseURL) ?? "https://api.openai.com/v1"
        model = try values.decodeIfPresent(String.self, forKey: .model) ?? ""
        accountProvider = try values.decodeIfPresent(
            AccountProvider.self, forKey: .accountProvider) ?? .gpt
        gptModel = try values.decodeIfPresent(
            String.self, forKey: .gptModel) ?? "gpt-5.4"
        copilotModel = try values.decodeIfPresent(
            String.self, forKey: .copilotModel) ?? "gpt-5.4"
        copilotEndpoint = try values.decodeIfPresent(
            String.self, forKey: .copilotEndpoint) ?? ""
        githubOAuthClientID = try values.decodeIfPresent(
            String.self, forKey: .githubOAuthClientID) ?? ""
        persona = try values.decodeIfPresent(String.self, forKey: .persona) ?? "你"
        personaAvatarData = try values.decodeIfPresent(
            Data.self, forKey: .personaAvatarData)
        webSearch = try values.decodeIfPresent(
            Bool.self, forKey: .webSearch) ?? true
        showReasoning = try values.decodeIfPresent(
            Bool.self, forKey: .showReasoning) ?? false
        characterAutonomousMessages = try values.decodeIfPresent(
            Bool.self, forKey: .characterAutonomousMessages) ?? true
        groupAutonomousMessages = try values.decodeIfPresent(
            Bool.self, forKey: .groupAutonomousMessages) ?? true
    }
}

struct PersistedState: Codable {
    var characters: [CharacterCard]
    var groups: [GroupChat]
    var messages: [String: [ChatMessage]]
    var settings: AppSettings
}

enum ConversationTarget: Hashable {
    case character(String)
    case group(String)

    var conversationID: String {
        switch self {
        case .character(let id): return id
        case .group(let id): return "group:\(id)"
        }
    }
}
