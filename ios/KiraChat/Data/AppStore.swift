import Foundation
import SwiftUI

@MainActor
final class AppStore: ObservableObject {
    @Published private(set) var characters: [CharacterCard] = []
    @Published private(set) var groups: [GroupChat] = []
    @Published private(set) var allMessages: [String: [ChatMessage]] = [:]
    @Published var settings = AppSettings() {
        didSet { save() }
    }
    @Published private(set) var generationCounts: [String: Int] = [:]
    @Published private(set) var availableModels: [String] = []
    @Published private(set) var modelRefreshError = ""

    private static let apiKeyAccount = "direct-api-key"
    private static let gptAccessAccount = "gpt-access-token"
    private static let gptRefreshAccount = "gpt-refresh-token"
    private static let gptExpiryAccount = "gpt-expires-at"
    private static let copilotAccessAccount = "copilot-access-token"
    private static let copilotLoginAccount = "copilot-login"
    private var isLoading = true

    init() {
        load()
        isLoading = false
        ensureBuiltInCharacter()
    }

    var apiKey: String {
        get { KeychainStore.read(Self.apiKeyAccount) }
        set { KeychainStore.write(newValue, account: Self.apiKeyAccount) }
    }

    var hasGPTAccount: Bool { !KeychainStore.read(Self.gptAccessAccount).isEmpty }
    var hasCopilotAccount: Bool { !KeychainStore.read(Self.copilotAccessAccount).isEmpty }

    var accountSummary: String {
        switch settings.accountProvider {
        case .gpt:
            return OpenAIAccountAuth.accountSummary(
                from: KeychainStore.read(Self.gptAccessAccount))
        case .copilot:
            guard hasCopilotAccount else { return NSLocalizedString("未登录", comment: "") }
            let login = KeychainStore.read(Self.copilotLoginAccount)
            return login.isEmpty
                ? NSLocalizedString("已登录 · GitHub Copilot", comment: "")
                : "\(NSLocalizedString("已登录", comment: "")) · @\(login)"
        }
    }

    func saveGPTAccount(_ tokens: GPTTokenSet) {
        KeychainStore.write(tokens.accessToken, account: Self.gptAccessAccount)
        KeychainStore.write(tokens.refreshToken, account: Self.gptRefreshAccount)
        KeychainStore.write(String(tokens.expiresAt), account: Self.gptExpiryAccount)
        objectWillChange.send()
    }

    func saveCopilotAccount(_ result: GitHubLoginResult) {
        KeychainStore.write(result.accessToken, account: Self.copilotAccessAccount)
        KeychainStore.write(result.login, account: Self.copilotLoginAccount)
        objectWillChange.send()
    }

    func logoutCurrentAccount() {
        switch settings.accountProvider {
        case .gpt:
            KeychainStore.write("", account: Self.gptAccessAccount)
            KeychainStore.write("", account: Self.gptRefreshAccount)
            KeychainStore.write("", account: Self.gptExpiryAccount)
        case .copilot:
            KeychainStore.write("", account: Self.copilotAccessAccount)
            KeychainStore.write("", account: Self.copilotLoginAccount)
        }
        if settings.generationMode == .account {
            updateSettings { $0.generationMode = .directAPI }
        }
        availableModels = []
        objectWillChange.send()
    }

    func messages(for target: ConversationTarget) -> [ChatMessage] {
        allMessages[target.conversationID] ?? []
    }

    func character(id: String) -> CharacterCard? {
        characters.first { $0.id == id }
    }

    func group(id: String) -> GroupChat? {
        groups.first { $0.id == id }
    }

    func members(of group: GroupChat) -> [CharacterCard] {
        group.memberIDs.compactMap(character(id:))
    }

    func isGenerating(_ target: ConversationTarget) -> Bool {
        (generationCounts[target.conversationID] ?? 0) > 0
    }

    func touch(_ target: ConversationTarget) {
        switch target {
        case .character(let id):
            guard let index = characters.firstIndex(where: { $0.id == id }) else { return }
            characters[index].lastUsed = Date()
            characters[index].unread = 0
        case .group(let id):
            guard let index = groups.firstIndex(where: { $0.id == id }) else { return }
            groups[index].lastUsed = Date()
            groups[index].unread = 0
        }
        save()
    }

    func updateSettings(_ change: (inout AppSettings) -> Void) {
        var updated = settings
        change(&updated)
        settings = updated
    }

    func clearAvailableModels() {
        availableModels = []
        modelRefreshError = ""
    }

    func makeBackupData() throws -> Data {
        let archive = BackupArchive(payload: PersistedState(
            characters: characters,
            groups: groups,
            messages: allMessages,
            settings: settings))
        try archive.validate()
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(archive)
    }

    func backupSummary(from data: Data) throws -> String {
        try BackupArchive.decode(data).summary
    }

    func restoreBackup(from data: Data) throws {
        let archive = try BackupArchive.decode(data)
        let characterIDs = Set(archive.payload.characters.map(\.id))
        var validGroups: [GroupChat] = []
        for var group in archive.payload.groups {
            group.memberIDs = Array(Set(group.memberIDs.filter(characterIDs.contains)))
            if group.memberIDs.count >= 2 { validGroups.append(group) }
        }
        let conversationIDs = Set(
            characterIDs.map { $0 } + validGroups.map { $0.conversationID })
        let validMessages = archive.payload.messages.filter {
            conversationIDs.contains($0.key)
        }

        isLoading = true
        characters = archive.payload.characters
        groups = validGroups
        allMessages = validMessages
        settings = archive.payload.settings
        availableModels = []
        modelRefreshError = ""
        generationCounts = [:]
        isLoading = false
        ensureBuiltInCharacter()
    }

    func addMessage(_ message: ChatMessage, to target: ConversationTarget) {
        allMessages[target.conversationID, default: []].append(message)
        touch(target)
        save()
    }

    func deleteMessage(_ message: ChatMessage, from target: ConversationTarget) {
        allMessages[target.conversationID]?.removeAll { $0.id == message.id }
        save()
    }

    func clearMessages(for target: ConversationTarget) {
        allMessages[target.conversationID] = []
        save()
    }

    func addVoiceCall(duration: Int, to target: ConversationTarget) {
        guard duration > 0 else { return }
        addMessage(.voiceCall(duration: duration), to: target)
    }

    func submit(_ message: ChatMessage, to target: ConversationTarget) {
        addMessage(message, to: target)
        switch target {
        case .character(let id):
            generateCharacterReply(characterID: id, target: target)
        case .group(let id):
            generateGroupReplies(groupID: id, target: target)
        }
    }

    func retry(_ failedMessage: ChatMessage, in target: ConversationTarget) {
        deleteMessage(failedMessage, from: target)
        switch target {
        case .character(let id): generateCharacterReply(characterID: id, target: target)
        case .group(let id): generateGroupReplies(groupID: id, target: target)
        }
    }

    func addCharacter(_ card: CharacterCard) {
        var value = card
        value.lastUsed = Date()
        characters.append(value)
        if !value.firstMessage.isEmpty {
            allMessages[value.id] = [ChatMessage(
                role: .assistant,
                content: value.replacingMacros(in: value.firstMessage, persona: settings.persona))]
        }
        save()
    }

    func overwriteCharacter(existingID: String, with imported: CharacterCard) {
        guard let index = characters.firstIndex(where: { $0.id == existingID }) else { return }
        let old = characters[index]
        var value = imported
        value.id = old.id
        value.lastUsed = old.lastUsed
        value.unread = old.unread
        value.muted = old.muted
        value.pinned = old.pinned
        value.chatBackgroundData = old.chatBackgroundData
        characters[index] = value
        save()
    }

    func uniqueCharacterName(from proposed: String) -> String {
        let base = proposed.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? NSLocalizedString("未命名角色", comment: "") : proposed
        let names = Set(characters.map { $0.name.lowercased() })
        if !names.contains(base.lowercased()) { return base }
        var suffix = 2
        while names.contains("\(base) \(suffix)".lowercased()) { suffix += 1 }
        return "\(base) \(suffix)"
    }

    func matchingCharacter(named name: String) -> CharacterCard? {
        characters.first { $0.name.caseInsensitiveCompare(name) == .orderedSame }
    }

    func deleteCharacter(_ card: CharacterCard) {
        guard !card.isBuiltIn else { return }
        characters.removeAll { $0.id == card.id }
        allMessages[card.id] = nil
        for index in groups.indices.reversed() {
            groups[index].memberIDs.removeAll { $0 == card.id }
            if groups[index].memberIDs.count < 2 {
                allMessages[groups[index].conversationID] = nil
                groups.remove(at: index)
            }
        }
        save()
    }

    func updateCharacter(_ value: CharacterCard) {
        guard let index = characters.firstIndex(where: { $0.id == value.id }) else { return }
        characters[index] = value
        save()
    }

    func createGroup(name: String, memberIDs: [String]) {
        guard Set(memberIDs).count >= 2 else { return }
        var group = GroupChat()
        group.name = name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? NSLocalizedString("新群聊", comment: "") : name
        group.memberIDs = Array(Set(memberIDs))
        groups.append(group)
        save()
    }

    func deleteGroup(_ group: GroupChat) {
        groups.removeAll { $0.id == group.id }
        allMessages[group.conversationID] = nil
        save()
    }

    func updateGroup(_ value: GroupChat) {
        guard let index = groups.firstIndex(where: { $0.id == value.id }) else { return }
        groups[index] = value
        save()
    }

    func importTavernJSON(_ data: Data) throws -> CharacterCard {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw KiraError.message(NSLocalizedString("角色卡 JSON 无效", comment: ""))
        }
        let source = (root["data"] as? [String: Any]) ?? root
        func value(_ key: String, fallback: String = "") -> String {
            (source[key] as? String) ?? (root[key] as? String) ?? fallback
        }
        var card = CharacterCard()
        card.name = value("name", fallback: NSLocalizedString("未命名角色", comment: ""))
            .trimmingCharacters(in: .whitespacesAndNewlines)
        card.description = value("description")
        card.personality = value("personality")
        card.scenario = value("scenario")
        card.firstMessage = value("first_mes")
        card.exampleDialogue = value("mes_example")
        card.creatorNotes = value("creator_notes", fallback: value("creatorcomment"))
        if let book = source["character_book"],
           JSONSerialization.isValidJSONObject(book),
           let bookData = try? JSONSerialization.data(withJSONObject: book),
           let bookText = String(data: bookData, encoding: .utf8) {
            card.worldBookJSON = bookText
        }
        return card
    }

    func refreshModels() async {
        modelRefreshError = ""
        do {
            switch settings.generationMode {
            case .directAPI:
                availableModels = try await APIService.fetchModels(
                    settings: settings,
                    apiKey: apiKey)
            case .account:
                switch settings.accountProvider {
                case .gpt:
                    let tokens = try await validGPTTokens()
                    availableModels = try await AccountAPIService.fetchGPTModels(
                        accessToken: tokens.accessToken)
                case .copilot:
                    guard hasCopilotAccount else {
                        throw KiraError.message(NSLocalizedString("请先登录 GitHub Copilot 账户", comment: ""))
                    }
                    availableModels = try await APIService.fetchModels(
                        settings: copilotSettings(from: settings),
                        apiKey: KeychainStore.read(Self.copilotAccessAccount))
                }
            }
        } catch {
            modelRefreshError = error.localizedDescription
        }
    }

    private func generateCharacterReply(characterID: String, target: ConversationTarget) {
        guard let card = character(id: characterID) else { return }
        let history = messages(for: target)
        let config = settings
        beginGeneration(target.conversationID)
        Task { [weak self] in
            do {
                guard let self else { return }
                let credential = try await self.generationCredential(for: config)
                let reply = try await Self.completeReply(
                    character: card,
                    history: history,
                    settings: config,
                    credential: credential)
                self.addMessage(ChatMessage(role: .assistant, content: reply), to: target)
                self.endGeneration(target.conversationID)
            } catch {
                guard let self else { return }
                self.addMessage(ChatMessage(
                    role: .assistant,
                    content: error.localizedDescription,
                    failed: true), to: target)
                self.endGeneration(target.conversationID)
            }
        }
    }

    private func generateGroupReplies(groupID: String, target: ConversationTarget) {
        guard let group = group(id: groupID) else { return }
        let cards = members(of: group).shuffled()
        guard !cards.isEmpty else { return }
        let history = messages(for: target)
        let config = settings
        generationCounts[target.conversationID, default: 0] += cards.count
        Task { [weak self] in
            guard let self else { return }
            let credential: GenerationCredential
            do {
                credential = try await self.generationCredential(for: config)
            } catch {
                for _ in cards { self.endGeneration(target.conversationID) }
                self.addMessage(ChatMessage(
                    role: .assistant,
                    content: error.localizedDescription,
                    failed: true), to: target)
                return
            }
            await withTaskGroup(of: (CharacterCard, Result<String, Error>).self) { taskGroup in
                for card in cards {
                    taskGroup.addTask {
                        do {
                            let reply = try await Self.completeReply(
                                character: card,
                                history: history,
                                settings: config,
                                credential: credential,
                                groupDecision: true)
                            return (card, .success(reply))
                        } catch {
                            return (card, .failure(error))
                        }
                    }
                }
                for await (card, result) in taskGroup {
                    switch result {
                    case .success(let text):
                        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !clean.isEmpty && clean != "[SKIP]" {
                            self.addMessage(ChatMessage(
                                role: .assistant,
                                content: clean,
                                speaker: card.id), to: target)
                        }
                    case .failure:
                        break
                    }
                    self.endGeneration(target.conversationID)
                }
            }
        }
    }

    private func beginGeneration(_ conversationID: String) {
        generationCounts[conversationID, default: 0] += 1
    }

    private func endGeneration(_ conversationID: String) {
        generationCounts[conversationID] = max(
            0, (generationCounts[conversationID] ?? 1) - 1)
    }

    private func validGPTTokens() async throws -> GPTTokenSet {
        let stored = GPTTokenSet(
            accessToken: KeychainStore.read(Self.gptAccessAccount),
            refreshToken: KeychainStore.read(Self.gptRefreshAccount),
            expiresAt: TimeInterval(KeychainStore.read(Self.gptExpiryAccount)) ?? 0)
        guard !stored.accessToken.isEmpty || !stored.refreshToken.isEmpty else {
            throw KiraError.message(NSLocalizedString("请先登录 GPT 账户", comment: ""))
        }
        let valid = try await OpenAIAccountAuth.validTokens(stored)
        if valid.accessToken != stored.accessToken
            || valid.refreshToken != stored.refreshToken
            || valid.expiresAt != stored.expiresAt {
            saveGPTAccount(valid)
        }
        return valid
    }

    private func generationCredential(for config: AppSettings) async throws -> GenerationCredential {
        switch config.generationMode {
        case .directAPI:
            return .direct(apiKey)
        case .account:
            switch config.accountProvider {
            case .gpt:
                return .gpt((try await validGPTTokens()).accessToken)
            case .copilot:
                let token = KeychainStore.read(Self.copilotAccessAccount)
                guard !token.isEmpty else {
                    throw KiraError.message(NSLocalizedString("请先登录 GitHub Copilot 账户", comment: ""))
                }
                return .copilot(token)
            }
        }
    }

    nonisolated private static func completeReply(
        character: CharacterCard,
        history: [ChatMessage],
        settings: AppSettings,
        credential: GenerationCredential,
        groupDecision: Bool = false
    ) async throws -> String {
        switch credential {
        case .direct(let key):
            return try await APIService.complete(
                character: character,
                history: history,
                settings: settings,
                apiKey: key,
                groupDecision: groupDecision)
        case .gpt(let token):
            return try await AccountAPIService.completeGPT(
                character: character,
                history: history,
                settings: settings,
                accessToken: token,
                groupDecision: groupDecision)
        case .copilot(let token):
            return try await APIService.complete(
                character: character,
                history: history,
                settings: copilotSettings(from: settings),
                apiKey: token,
                groupDecision: groupDecision)
        }
    }

    nonisolated private static func copilotSettings(from source: AppSettings) -> AppSettings {
        var settings = source
        settings.generationMode = .directAPI
        settings.apiFormat = .chatCompletions
        settings.baseURL = source.copilotEndpoint
        settings.model = source.copilotModel
        return settings
    }

    private func copilotSettings(from source: AppSettings) -> AppSettings {
        Self.copilotSettings(from: source)
    }

    private func ensureBuiltInCharacter() {
        if let index = characters.firstIndex(where: { $0.id == CharacterCard.dounaiID }) {
            var fresh = CharacterCard.dounai
            fresh.lastUsed = characters[index].lastUsed
            fresh.unread = characters[index].unread
            fresh.muted = characters[index].muted
            fresh.pinned = characters[index].pinned
            fresh.chatBackgroundData = characters[index].chatBackgroundData
            characters[index] = fresh
        } else {
            let card = CharacterCard.dounai
            characters.insert(card, at: 0)
            allMessages[card.id] = [ChatMessage(
                role: .assistant,
                content: card.replacingMacros(in: card.firstMessage, persona: settings.persona))]
        }
        save()
    }

    private func load() {
        guard let data = try? Data(contentsOf: Self.stateURL),
              let state = try? JSONDecoder().decode(PersistedState.self, from: data) else {
            return
        }
        characters = state.characters
        groups = state.groups
        allMessages = state.messages
        settings = state.settings
    }

    private func save() {
        guard !isLoading else { return }
        let state = PersistedState(
            characters: characters,
            groups: groups,
            messages: allMessages,
            settings: settings)
        guard let data = try? JSONEncoder().encode(state) else { return }
        try? FileManager.default.createDirectory(
            at: Self.stateURL.deletingLastPathComponent(),
            withIntermediateDirectories: true,
            attributes: nil)
        try? data.write(to: Self.stateURL, options: .atomic)
    }

    private static var stateURL: URL {
        let base = FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask).first!
        return base.appendingPathComponent("KiraChat", isDirectory: true)
            .appendingPathComponent("state.json")
    }
}

private enum GenerationCredential {
    case direct(String)
    case gpt(String)
    case copilot(String)
}

enum KiraError: LocalizedError {
    case message(String)

    var errorDescription: String? {
        switch self {
        case .message(let value): return value
        }
    }
}
