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
    private var remoteSyncRunning = false
    private var applyingRemoteSync = false
    private var syncScheduleGeneration = 0
    private var lastForegroundSyncAttempt = Date.distantPast

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

    func makeSyncPayloadData() throws -> Data {
        let payload = SyncPayload(
            characters: characters.map { card in
                SyncCharacter(
                    id: card.id,
                    name: card.name,
                    description: card.description,
                    personality: card.personality,
                    scenario: card.scenario,
                    firstMessage: card.firstMessage,
                    exampleDialogue: card.exampleDialogue,
                    creatorNotes: card.creatorNotes,
                    worldBookJSON: card.worldBookJSON,
                    avatarData: card.avatarData,
                    chatBackgroundData: card.chatBackgroundData,
                    lastUsed: milliseconds(card.lastUsed),
                    unread: card.unread,
                    muted: card.muted,
                    pinned: card.pinned)
            },
            groups: groups.map { group in
                SyncGroup(
                    id: group.id,
                    name: group.name,
                    memberIDs: group.memberIDs,
                    lastUsed: milliseconds(group.lastUsed),
                    unread: group.unread,
                    muted: group.muted,
                    pinned: group.pinned,
                    chatBackgroundData: group.chatBackgroundData)
            },
            messages: allMessages.mapValues { values in
                values.map { message in
                    SyncMessage(
                        id: message.id.uuidString,
                        role: message.role.rawValue,
                        content: message.content,
                        speaker: message.speaker,
                        attachment: message.attachment.rawValue,
                        imageData: message.imageData,
                        latitude: message.latitude,
                        longitude: message.longitude,
                        callDurationSeconds: message.callDurationSeconds,
                        timestamp: milliseconds(message.timestamp),
                        failed: message.failed)
                }
            },
            settings: SyncPortableSettings(
                persona: settings.persona,
                personaAvatarData: settings.personaAvatarData,
                webSearch: settings.webSearch,
                showReasoning: settings.showReasoning,
                characterAutonomousMessages: settings.characterAutonomousMessages,
                groupAutonomousMessages: settings.groupAutonomousMessages))
        try payload.validate()
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(payload)
        guard data.count <= 180 * 1024 * 1024 else {
            throw KiraError.message(NSLocalizedString("同步内容不能超过 180 MB", comment: ""))
        }
        return data
    }

    func restoreSyncPayloadData(_ data: Data) throws {
        guard data.count <= 180 * 1024 * 1024 else {
            throw KiraError.message(NSLocalizedString("同步内容不能超过 180 MB", comment: ""))
        }
        let payload = try JSONDecoder().decode(SyncPayload.self, from: data)
        try payload.validate()
        let restoredCharacters = payload.characters.map { card in
            CharacterCard(
                id: card.id,
                name: card.name,
                description: card.description,
                personality: card.personality,
                scenario: card.scenario,
                firstMessage: card.firstMessage,
                exampleDialogue: card.exampleDialogue,
                creatorNotes: card.creatorNotes,
                worldBookJSON: card.worldBookJSON,
                avatarData: card.avatarData,
                lastUsed: date(card.lastUsed),
                unread: card.unread,
                muted: card.muted,
                pinned: card.pinned,
                chatBackgroundData: card.chatBackgroundData)
        }
        let characterIDs = Set(restoredCharacters.map(\.id))
        let restoredGroups = payload.groups.compactMap { group -> GroupChat? in
            var seen = Set<String>()
            let members = group.memberIDs.filter {
                characterIDs.contains($0) && seen.insert($0).inserted
            }
            guard members.count >= 2 else { return nil }
            return GroupChat(
                id: group.id,
                name: group.name,
                memberIDs: members,
                lastUsed: date(group.lastUsed),
                unread: group.unread,
                muted: group.muted,
                pinned: group.pinned,
                chatBackgroundData: group.chatBackgroundData)
        }
        let validConversationIDs = characterIDs.union(
            restoredGroups.map(\.conversationID))
        let restoredMessages = payload.messages.compactMapValues { values in
            values.map { message in
                ChatMessage(
                    id: UUID(uuidString: message.id) ?? UUID(),
                    role: MessageRole(rawValue: message.role) ?? .assistant,
                    content: message.content,
                    speaker: message.speaker,
                    attachment: AttachmentKind(rawValue: message.attachment) ?? .none,
                    imageData: message.imageData,
                    latitude: message.latitude,
                    longitude: message.longitude,
                    callDurationSeconds: message.callDurationSeconds,
                    timestamp: date(message.timestamp),
                    failed: message.failed)
            }
        }.filter { validConversationIDs.contains($0.key) }

        var restoredSettings = settings
        restoredSettings.persona = payload.settings.persona
        restoredSettings.personaAvatarData = payload.settings.personaAvatarData
        restoredSettings.webSearch = payload.settings.webSearch
        restoredSettings.showReasoning = payload.settings.showReasoning
        restoredSettings.characterAutonomousMessages =
            payload.settings.characterAutonomousMessages
        restoredSettings.groupAutonomousMessages = payload.settings.groupAutonomousMessages

        isLoading = true
        characters = restoredCharacters
        groups = restoredGroups
        allMessages = restoredMessages
        settings = restoredSettings
        availableModels = []
        modelRefreshError = ""
        generationCounts = [:]
        isLoading = false
        ensureBuiltInCharacter()
    }

    func performRemoteSync(_ mode: RemoteSyncMode) async throws -> String {
        guard !remoteSyncRunning else {
            throw KiraError.message(NSLocalizedString("同步正在进行中", comment: ""))
        }
        remoteSyncRunning = true
        defer { remoteSyncRunning = false }
        do {
            let localPayload = try makeSyncPayloadData()
            let outcome = try await RemoteSyncService.synchronize(
                mode: mode, localPayload: localPayload)
            switch outcome {
            case .unchanged(let revision, let message),
                    .uploaded(let revision, let message):
                SyncConfiguration.record(
                    revision: revision,
                    digest: SHA256Digest.hex(localPayload),
                    status: message)
                return message
            case .downloaded(let revision, let payload, let message):
                applyingRemoteSync = true
                defer { applyingRemoteSync = false }
                try restoreSyncPayloadData(payload)
                let restoredPayload = try makeSyncPayloadData()
                SyncConfiguration.record(
                    revision: revision,
                    digest: SHA256Digest.hex(restoredPayload),
                    status: message)
                return message
            }
        } catch {
            SyncConfiguration.setStatus(error.localizedDescription)
            throw error
        }
    }

    func syncWhenAppBecomesActive() {
        guard SyncConfiguration.automatic, SyncConfiguration.configured,
              Date().timeIntervalSince(lastForegroundSyncAttempt) >= 15 else { return }
        lastForegroundSyncAttempt = Date()
        Task { _ = try? await performRemoteSync(.automatic) }
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

    func generateAutonomousMessage(to target: ConversationTarget) {
        guard settings.characterAutonomousMessages,
              !isGenerating(target),
              case .character(let characterID) = target,
              let card = character(id: characterID) else { return }
        let history = messages(for: target)
        let config = settings
        beginGeneration(target.conversationID)
        Task { [weak self] in
            guard let self else { return }
            do {
                let credential = try await self.generationCredential(for: config)
                let reply = try await Self.completeReply(
                    character: card,
                    history: history,
                    settings: config,
                    credential: credential,
                    spontaneous: true)
                if !Self.shouldSkipAutonomousReply(reply) {
                    self.addMessage(
                        ChatMessage(role: .assistant, content: reply),
                        to: target)
                }
            } catch {
                // Autonomous messages are optional; avoid adding failure bubbles.
            }
            self.endGeneration(target.conversationID)
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
        if value.avatarData == nil { value.avatarData = old.avatarData }
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

    func importTavernCard(_ data: Data) throws -> CharacterCard {
        let payload = try CharacterCardFileImporter.decode(data)
        var card = try importTavernJSON(payload.json)
        if let avatar = payload.avatarPNG { card.avatarData = avatar }
        return card
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
           JSONSerialization.isValidJSONObject(book) {
            if let dictionary = book as? [String: Any],
               let entries = dictionary["entries"] as? [Any],
               entries.count > 1_000_000 {
                throw KiraError.message(NSLocalizedString(
                    "角色卡世界书不能超过 1000000 条", comment: ""))
            }
            let bookData = try JSONSerialization.data(withJSONObject: book)
            if let bookText = String(data: bookData, encoding: .utf8) {
                card.worldBookJSON = bookText
            }
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
        groupDecision: Bool = false,
        spontaneous: Bool = false
    ) async throws -> String {
        switch credential {
        case .direct(let key):
            return try await APIService.complete(
                character: character,
                history: history,
                settings: settings,
                apiKey: key,
                groupDecision: groupDecision,
                spontaneous: spontaneous)
        case .gpt(let token):
            return try await AccountAPIService.completeGPT(
                character: character,
                history: history,
                settings: settings,
                accessToken: token,
                groupDecision: groupDecision,
                spontaneous: spontaneous)
        case .copilot(let token):
            return try await APIService.complete(
                character: character,
                history: history,
                settings: copilotSettings(from: settings),
                apiKey: token,
                groupDecision: groupDecision,
                spontaneous: spontaneous)
        }
    }

    nonisolated private static func shouldSkipAutonomousReply(_ value: String) -> Bool {
        let normalized = value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "`", with: "")
            .replacingOccurrences(of: " ", with: "")
            .uppercased()
        return normalized.isEmpty
            || normalized == "[SKIP]"
            || normalized == "[[SKIP]]"
            || normalized == "SKIP"
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
        scheduleAutomaticSync()
    }

    private func scheduleAutomaticSync() {
        guard !applyingRemoteSync, SyncConfiguration.automatic,
              SyncConfiguration.configured else { return }
        syncScheduleGeneration += 1
        let generation = syncScheduleGeneration
        Task {
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            guard generation == syncScheduleGeneration else { return }
            _ = try? await performRemoteSync(.automatic)
        }
    }

    private func milliseconds(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000).rounded())
    }

    private func date(_ milliseconds: Int64) -> Date {
        Date(timeIntervalSince1970: Double(milliseconds) / 1000)
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
