import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct MeView: View {
    @EnvironmentObject private var store: AppStore
    @State private var avatarItem: PhotosPickerItem?
    @State private var persona = ""

    var body: some View {
        let personaAvatarData = store.settings.personaAvatarData
        ZStack {
            KiraTheme.page.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 12) {
                    HStack(spacing: 17) {
                        PhotosPicker(selection: $avatarItem, matching: .images) {
                            ZStack(alignment: .bottomTrailing) {
                                PersonaAvatar(data: personaAvatarData, size: 76)
                                Image(systemName: "pencil")
                                    .font(.caption.bold())
                                    .frame(width: 25, height: 25)
                                    .background(.regularMaterial, in: Circle())
                            }
                        }
                        .buttonStyle(KiraPressStyle())
                        .accessibilityLabel("更换我的头像")
                        VStack(alignment: .leading, spacing: 7) {
                            TextField("用户名", text: $persona)
                                .font(.title2.weight(.bold))
                                .onSubmit { savePersona() }
                            Text("用户名会自动替换角色设定中的 user 与 {{user}}。")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(18)
                    .kiraCard()

                    VStack(spacing: 0) {
                        NavigationLink {
                            APISettingsView()
                        } label: {
                            SettingsRow(
                                icon: "link",
                                title: "连接与账户",
                                detail: store.settings.activeModel.isEmpty
                                    ? "尚未选择模型" : store.settings.activeModel)
                        }
                        Divider().padding(.leading, 58)
                        NavigationLink {
                            BackupRestoreView()
                        } label: {
                            SettingsRow(
                                icon: "arrow.triangle.2.circlepath",
                                title: "备份与还原",
                                detail: "角色、聊天、图片与普通设置")
                        }
                        Divider().padding(.leading, 58)
                        NavigationLink {
                            SyncSettingsView()
                        } label: {
                            SettingsRow(
                                icon: "icloud.and.arrow.up",
                                title: "服务器同步",
                                detail: !SyncConfiguration.configured
                                    ? NSLocalizedString("尚未配置", comment: "")
                                    : SyncConfiguration.automatic
                                    ? NSLocalizedString("自动同步已开启", comment: "")
                                    : NSLocalizedString("已配置 · 手动同步", comment: ""))
                        }
                        Divider().padding(.leading, 58)
                        NavigationLink {
                            PrivacyView()
                        } label: {
                            SettingsRow(
                                icon: "hand.raised",
                                title: "隐私与数据",
                                detail: "聊天与角色保存在本机")
                        }
                        Divider().padding(.leading, 58)
                        NavigationLink {
                            AboutView()
                        } label: {
                            SettingsRow(
                                icon: "info.circle",
                                title: "关于澄语",
                                detail: "0.9.0 (9)")
                        }
                    }
                    .kiraCard()
                }
                .padding(16)
            }
        }
        .navigationTitle("我的")
        .navigationBarTitleDisplayMode(.large)
        .onAppear { persona = store.settings.persona }
        .onDisappear { savePersona() }
        .onChange(of: avatarItem) { item in
            guard let item else { return }
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self),
                      data.count <= 15 * 1024 * 1024 else { return }
                store.updateSettings { $0.personaAvatarData = data }
            }
        }
    }

    private func savePersona() {
        let clean = persona.trimmingCharacters(in: .whitespacesAndNewlines)
        store.updateSettings { $0.persona = clean.isEmpty ? "你" : clean }
    }
}

private struct SyncSettingsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var serverURL = ""
    @State private var token = ""
    @State private var encryptionPassword = ""
    @State private var automatic = false
    @State private var status = NSLocalizedString("尚未同步", comment: "")
    @State private var isError = false
    @State private var busy = false
    @State private var overwriteChoice: SyncOverwriteChoice?

    var body: some View {
        Form {
            Section("服务器连接") {
                TextField("https://sync.example.com", text: $serverURL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                SecureField("同步令牌（至少 24 个字符）", text: $token)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                SecureField("端到端加密密码（至少 8 个字符）", text: $encryptionPassword)
                Toggle("自动同步", isOn: $automatic)
                HStack {
                    Button {
                        testConnection()
                    } label: {
                        Label("测试连接", systemImage: "network")
                    }
                    .disabled(busy)
                    Spacer()
                    Button("保存设置") { _ = saveConfiguration(showSuccess: true) }
                        .disabled(busy)
                }
            } footer: {
                Text("连接自部署的澄语同步服务器，在设备间自动同步角色、群聊、消息、头像、背景和普通设置。")
            }

            Section("同步操作") {
                Button {
                    runSync(.automatic)
                } label: {
                    Label("立即同步", systemImage: "arrow.triangle.2.circlepath")
                }
                .disabled(busy)
                Button {
                    overwriteChoice = .upload
                } label: {
                    Label("上传本机", systemImage: "arrow.up.circle")
                }
                .disabled(busy)
                Button {
                    overwriteChoice = .download
                } label: {
                    Label("下载服务器", systemImage: "arrow.down.circle")
                }
                .disabled(busy)
            } footer: {
                Text("首次连接或发生冲突时，请明确选择保留本机内容或服务器内容。正常情况下“立即同步”会自动判断上传或下载。")
            }

            Section("安全与冲突保护") {
                Text("同步内容会在本机加密后上传；加密密码不会发送给服务器。API Key、GPT/Copilot 令牌、本地模型和语音凭据不会同步。两台设备同时修改时会暂停并要求手动选择，避免静默覆盖。生产环境请使用 HTTPS。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Section {
                Label(status, systemImage: isError
                      ? "exclamationmark.circle" : busy
                      ? "arrow.triangle.2.circlepath" : "checkmark.circle")
                    .font(.footnote)
                    .foregroundStyle(isError ? Color.red : Color.secondary)
            }
        }
        .navigationTitle("服务器同步")
        .disabled(busy)
        .onAppear { loadConfiguration() }
        .alert(item: $overwriteChoice) { choice in
            Alert(
                title: Text(choice == .upload
                            ? "用本机内容覆盖服务器？" : "用服务器内容替换本机？"),
                message: Text(choice == .upload
                              ? "服务器当前快照会被本机内容替换。其他设备下次同步会下载此版本。"
                              : "本机角色、群聊、消息、头像、背景和普通设置会被服务器快照替换。此操作无法撤销。"),
                primaryButton: .destructive(Text(choice == .upload
                                                 ? "上传本机" : "下载服务器")) {
                    runSync(choice == .upload ? .forceUpload : .forceDownload)
                },
                secondaryButton: .cancel(Text("取消")))
        }
    }

    private func loadConfiguration() {
        serverURL = SyncConfiguration.serverURL
        token = SyncConfiguration.token
        encryptionPassword = SyncConfiguration.encryptionPassword
        automatic = SyncConfiguration.automatic
        status = statusText
    }

    private var statusText: String {
        guard let lastSync = SyncConfiguration.lastSync else {
            return SyncConfiguration.status
        }
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        return "\(SyncConfiguration.status) · \(formatter.string(from: lastSync))"
    }

    private func saveConfiguration(showSuccess: Bool) -> Bool {
        let cleanURL = serverURL.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanToken = token.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanURL.hasPrefix("https://") || cleanURL.hasPrefix("http://") else {
            setError("服务器地址必须以 https:// 或 http:// 开头")
            return false
        }
        guard cleanToken.count >= 24 else {
            setError("同步令牌至少需要 24 个字符")
            return false
        }
        guard encryptionPassword.count >= 8 else {
            setError("加密密码至少需要 8 个字符")
            return false
        }
        SyncConfiguration.save(
            serverURL: cleanURL,
            token: cleanToken,
            password: encryptionPassword,
            automatic: automatic)
        if showSuccess {
            status = NSLocalizedString("同步设置已保存", comment: "")
            isError = false
        }
        return true
    }

    private func testConnection() {
        guard saveConfiguration(showSuccess: false) else { return }
        busy = true
        status = NSLocalizedString("正在测试服务器…", comment: "")
        isError = false
        Task {
            do {
                let message = try await RemoteSyncService.testConnection()
                SyncConfiguration.setStatus(message)
                status = message
                busy = false
            } catch {
                setError(error.localizedDescription)
                busy = false
            }
        }
    }

    private func runSync(_ mode: RemoteSyncMode) {
        guard saveConfiguration(showSuccess: false) else { return }
        busy = true
        status = NSLocalizedString(
            mode == .forceUpload ? "正在上传本机内容…"
                : mode == .forceDownload ? "正在下载服务器内容…" : "正在同步…",
            comment: "")
        isError = false
        Task {
            do {
                status = try await store.performRemoteSync(mode)
                isError = false
                busy = false
            } catch {
                setError(error.localizedDescription)
                busy = false
            }
        }
    }

    private func setError(_ value: String) {
        status = NSLocalizedString(value, comment: "")
        isError = true
        SyncConfiguration.setStatus(status)
    }
}

private enum SyncOverwriteChoice: String, Identifiable {
    case upload
    case download
    var id: String { rawValue }
}

private struct SettingsRow: View {
    let icon: String
    let title: LocalizedStringKey
    let detail: String

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(KiraTheme.green)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.body.weight(.medium)).foregroundStyle(.primary)
                Text(detail).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.caption.bold())
                .foregroundStyle(.tertiary)
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 66)
        .contentShape(Rectangle())
    }
}

struct APISettingsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var apiKey = ""
    @State private var isRefreshing = false
    @State private var showLogin = false
    @State private var showLogoutConfirmation = false

    var body: some View {
        Form {
            Section("生成方式") {
                Picker("聊天生成", selection: settingBinding(\.generationMode)) {
                    ForEach(GenerationMode.allCases) { mode in
                        Text(mode.displayName).tag(mode)
                    }
                }
                .pickerStyle(.segmented)
                .onChange(of: store.settings.generationMode) { _ in
                    store.clearAvailableModels()
                    refreshIfReady()
                }
            }

            if store.settings.generationMode == .directAPI {
                Section("直连 API") {
                    Picker("接口协议", selection: settingBinding(\.apiFormat)) {
                        ForEach(APIFormat.allCases) { format in
                            Text(format.displayName).tag(format)
                        }
                    }
                    .onChange(of: store.settings.apiFormat) { format in
                        applyDefaultBaseURL(format)
                    }
                    TextField("API 地址", text: settingBinding(\.baseURL))
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                    SecureField("API Key", text: $apiKey)
                        .textInputAutocapitalization(.never)
                        .onChange(of: apiKey) { store.apiKey = $0 }
                }
            } else {
                Section("账户") {
                    Picker("账户类型", selection: settingBinding(\.accountProvider)) {
                        ForEach(AccountProvider.allCases) { provider in
                            Text(provider.displayName).tag(provider)
                        }
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: store.settings.accountProvider) { _ in
                        store.clearAvailableModels()
                        refreshIfReady()
                    }

                    LabeledContent("状态") {
                        Text(store.accountSummary)
                            .foregroundStyle(currentAccountLoggedIn ? KiraTheme.green : Color.secondary)
                    }

                    if store.settings.accountProvider == .copilot {
                        TextField("GitHub OAuth Client ID", text: settingBinding(\.githubOAuthClientID))
                            .textInputAutocapitalization(.never)
                        TextField("Copilot SDK 网关地址", text: settingBinding(\.copilotEndpoint))
                            .textInputAutocapitalization(.never)
                            .keyboardType(.URL)
                    }

                    Button {
                        showLogin = true
                    } label: {
                        Label(
                            currentAccountLoggedIn ? "重新登录" : "登录账户",
                            systemImage: "person.crop.circle.badge.checkmark")
                    }
                    if currentAccountLoggedIn {
                        Button("退出当前账户", role: .destructive) {
                            showLogoutConfirmation = true
                        }
                    }

                    Text(accountHelp)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section("模型") {
                TextField("模型 ID", text: modelBinding)
                    .textInputAutocapitalization(.never)
                if !store.availableModels.isEmpty {
                    Picker("服务返回的模型", selection: modelBinding) {
                        ForEach(store.availableModels, id: \.self) { model in
                            Text(model).tag(model)
                        }
                    }
                }
                Button {
                    Task {
                        isRefreshing = true
                        await store.refreshModels()
                        isRefreshing = false
                    }
                } label: {
                    HStack {
                        Label("自动获取模型列表", systemImage: "arrow.clockwise")
                        Spacer()
                        if isRefreshing { ProgressView() }
                    }
                }
                .disabled(isRefreshing)
                if !store.modelRefreshError.isEmpty {
                    Text(store.modelRefreshError)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }

            Section("能力") {
                Toggle("联网搜索", isOn: settingBinding(\.webSearch))
                Toggle("显示模型思考过程", isOn: settingBinding(\.showReasoning))
                Toggle("群聊主动发言", isOn: settingBinding(\.groupAutonomousMessages))
            }

            Section {
                Text("API Key 与 OAuth 令牌只保存在本机 Keychain；普通设置和聊天备份中不包含凭据。账户订阅只用于对应服务，不会转换成通用 API Key。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("连接与账户")
        .onAppear {
            apiKey = store.apiKey
            refreshIfReady()
        }
        .sheet(isPresented: $showLogin) {
            AccountLoginView(provider: store.settings.accountProvider)
                .environmentObject(store)
        }
        .alert("退出当前账户？", isPresented: $showLogoutConfirmation) {
            Button("取消", role: .cancel) {}
            Button("退出", role: .destructive) { store.logoutCurrentAccount() }
        } message: {
            Text("只会删除本机 Keychain 中的登录令牌，不会影响你的订阅或网站账户。")
        }
    }

    private var currentAccountLoggedIn: Bool {
        store.settings.accountProvider == .gpt
            ? store.hasGPTAccount : store.hasCopilotAccount
    }

    private var accountHelp: String {
        if store.settings.accountProvider == .gpt {
            return NSLocalizedString("在 OpenAI 官方页面完成设备授权；应用不会要求或读取 ChatGPT 密码。生成能力取决于账户的 Codex 访问权限。", comment: "")
        }
        return NSLocalizedString("使用 GitHub 官方设备授权和你自己的 Copilot 订阅。OAuth App 必须启用 Device Flow，聊天请求发送到你配置的官方 Copilot SDK 网关。", comment: "")
    }

    private var modelBinding: Binding<String> {
        Binding(
            get: {
                guard store.settings.generationMode == .account else {
                    return store.settings.model
                }
                return store.settings.accountProvider == .gpt
                    ? store.settings.gptModel : store.settings.copilotModel
            },
            set: { value in
                store.updateSettings { settings in
                    if settings.generationMode == .directAPI {
                        settings.model = value
                    } else if settings.accountProvider == .gpt {
                        settings.gptModel = value
                    } else {
                        settings.copilotModel = value
                    }
                }
            })
    }

    private func settingBinding<Value>(_ keyPath: WritableKeyPath<AppSettings, Value>) -> Binding<Value> {
        Binding(
            get: { store.settings[keyPath: keyPath] },
            set: { value in store.updateSettings { $0[keyPath: keyPath] = value } })
    }

    private func applyDefaultBaseURL(_ format: APIFormat) {
        let current = store.settings.baseURL.lowercased()
        guard current.isEmpty
                || current.contains("api.openai.com")
                || current.contains("api.anthropic.com")
                || current.contains("generativelanguage.googleapis.com") else { return }
        store.updateSettings { settings in
            switch format {
            case .chatCompletions, .responses:
                settings.baseURL = "https://api.openai.com/v1"
            case .claude:
                settings.baseURL = "https://api.anthropic.com"
            case .gemini:
                settings.baseURL = "https://generativelanguage.googleapis.com"
            }
            settings.model = ""
        }
        Task { await store.refreshModels() }
    }

    private func refreshIfReady() {
        guard store.availableModels.isEmpty else { return }
        if store.settings.generationMode == .directAPI {
            guard !store.settings.baseURL.isEmpty else { return }
        } else if !currentAccountLoggedIn {
            return
        } else if store.settings.accountProvider == .copilot,
                  store.settings.copilotEndpoint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return
        }
        Task { await store.refreshModels() }
    }
}

private struct AccountLoginView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let provider: AccountProvider

    @State private var userCode = ""
    @State private var verificationURL: URL?
    @State private var status = "正在获取验证码…"
    @State private var errorMessage = ""
    @State private var loginTask: Task<Void, Never>?

    var body: some View {
        NavigationStack {
            VStack(spacing: 22) {
                Image(systemName: provider == .gpt ? "sparkles" : "chevron.left.forwardslash.chevron.right")
                    .font(.system(size: 42, weight: .semibold))
                    .foregroundStyle(KiraTheme.green)
                    .frame(width: 82, height: 82)
                    .background(KiraTheme.green.opacity(0.12), in: RoundedRectangle(cornerRadius: 24))

                Text(provider == .gpt ? "登录 GPT 账户" : "登录 GitHub Copilot")
                    .font(.title2.bold())
                Text(status)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)

                if !userCode.isEmpty {
                    Text(userCode)
                        .font(.system(.title, design: .monospaced, weight: .bold))
                        .tracking(2)
                        .textSelection(.enabled)
                        .padding(.vertical, 15)
                        .frame(maxWidth: .infinity)
                        .background(.secondary.opacity(0.1), in: RoundedRectangle(cornerRadius: 16))

                    Button {
                        UIPasteboard.general.string = userCode
                        if let verificationURL { openURL(verificationURL) }
                    } label: {
                        Label(
                            provider == .gpt ? "复制并打开 OpenAI" : "复制并打开 GitHub",
                            systemImage: "safari")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(KiraTheme.green)
                } else if errorMessage.isEmpty {
                    ProgressView()
                }

                if !errorMessage.isEmpty {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                    Button("重试") { startLogin() }
                        .buttonStyle(.borderedProminent)
                        .tint(KiraTheme.green)
                }
                Spacer()
            }
            .padding(24)
            .navigationTitle("设备授权")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
        }
        .interactiveDismissDisabled(loginTask != nil && errorMessage.isEmpty)
        .onAppear { startLogin() }
        .onDisappear {
            loginTask?.cancel()
            loginTask = nil
        }
    }

    private func startLogin() {
        loginTask?.cancel()
        userCode = ""
        verificationURL = nil
        errorMessage = ""
        status = NSLocalizedString("正在获取验证码…", comment: "")
        loginTask = Task {
            do {
                switch provider {
                case .gpt:
                    let challenge = try await OpenAIAccountAuth.requestDeviceCode()
                    try Task.checkCancellation()
                    userCode = challenge.userCode
                    verificationURL = challenge.verificationURL
                    status = NSLocalizedString("请复制验证码并在 OpenAI 官方页面确认", comment: "")
                    let tokens = try await OpenAIAccountAuth.finishDeviceLogin(challenge)
                    try Task.checkCancellation()
                    store.saveGPTAccount(tokens)
                case .copilot:
                    let challenge = try await GitHubCopilotAuth.requestDeviceCode(
                        clientID: store.settings.githubOAuthClientID)
                    try Task.checkCancellation()
                    userCode = challenge.userCode
                    verificationURL = challenge.verificationURL
                    status = NSLocalizedString("请复制验证码并在 GitHub 官方页面确认", comment: "")
                    let result = try await GitHubCopilotAuth.finishDeviceLogin(challenge)
                    try Task.checkCancellation()
                    store.saveCopilotAccount(result)
                }
                store.clearAvailableModels()
                await store.refreshModels()
                loginTask = nil
                dismiss()
            } catch is CancellationError {
                loginTask = nil
            } catch {
                errorMessage = error.localizedDescription
                status = NSLocalizedString("登录失败", comment: "")
                loginTask = nil
            }
        }
    }
}

private struct BackupRestoreView: View {
    @EnvironmentObject private var store: AppStore
    @State private var exportDocument = BackupJSONDocument()
    @State private var showExporter = false
    @State private var showImporter = false
    @State private var showRestoreConfirmation = false
    @State private var pendingRestoreData: Data?
    @State private var pendingSummary = ""
    @State private var status = NSLocalizedString("尚未执行备份或还原", comment: "")
    @State private var isError = false

    var body: some View {
        Form {
            Section("创建备份") {
                Text("把角色、群聊、聊天记录、世界书、头像、聊天背景和普通设置保存为一个 JSON 文件。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button {
                    createBackup()
                } label: {
                    Label("导出备份", systemImage: "square.and.arrow.up")
                }
            }

            Section("还原备份") {
                Text("还原会替换本机现有角色、群聊、聊天记录和普通设置。选择文件后会再次确认。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button {
                    showImporter = true
                } label: {
                    Label("选择备份文件", systemImage: "square.and.arrow.down")
                }
            }

            Section("安全说明") {
                Text("备份未加密，可能包含私人聊天、图片和角色设定。请保存到可信位置，不要公开分享。API Key 与 GPT/Copilot OAuth 令牌不会写入备份，还原也不会覆盖当前 Keychain 凭据。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Section {
                Label(status, systemImage: isError ? "exclamationmark.circle" : "checkmark.circle")
                    .font(.footnote)
                    .foregroundStyle(isError ? Color.red : Color.secondary)
            }
        }
        .navigationTitle("备份与还原")
        .fileExporter(
            isPresented: $showExporter,
            document: exportDocument,
            contentType: .json,
            defaultFilename: defaultBackupName) { result in
                switch result {
                case .success:
                    status = NSLocalizedString("备份已保存", comment: "")
                    isError = false
                case .failure(let error):
                    status = "\(NSLocalizedString("备份失败", comment: ""))：\(error.localizedDescription)"
                    isError = true
                }
            }
        .fileImporter(
            isPresented: $showImporter,
            allowedContentTypes: [.json],
            allowsMultipleSelection: false) { result in
                do {
                    guard let url = try result.get().first else { return }
                    let scoped = url.startAccessingSecurityScopedResource()
                    defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                    let data = try Data(contentsOf: url, options: .mappedIfSafe)
                    guard data.count <= 180 * 1024 * 1024 else {
                        throw KiraError.message(NSLocalizedString("备份文件不能超过 180 MB", comment: ""))
                    }
                    pendingSummary = try store.backupSummary(from: data)
                    pendingRestoreData = data
                    showRestoreConfirmation = true
                    status = "\(NSLocalizedString("备份已读取", comment: "")) · \(pendingSummary)"
                    isError = false
                } catch {
                    status = "\(NSLocalizedString("读取备份失败", comment: ""))：\(error.localizedDescription)"
                    isError = true
                }
            }
        .alert("还原此备份？", isPresented: $showRestoreConfirmation) {
            Button("取消", role: .cancel) { pendingRestoreData = nil }
            Button("还原", role: .destructive) { restoreBackup() }
        } message: {
            Text("\(pendingSummary)\n\n本机现有角色、群聊、聊天记录和普通设置将被替换。Keychain 凭据保持不变。此操作无法撤销。")
        }
    }

    private var defaultBackupName: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMdd-HHmmss"
        return "KiraChat-backup-\(formatter.string(from: Date()))"
    }

    private func createBackup() {
        do {
            exportDocument = BackupJSONDocument(data: try store.makeBackupData())
            showExporter = true
            status = NSLocalizedString("备份已准备，等待选择保存位置", comment: "")
            isError = false
        } catch {
            status = "\(NSLocalizedString("备份失败", comment: ""))：\(error.localizedDescription)"
            isError = true
        }
    }

    private func restoreBackup() {
        guard let data = pendingRestoreData else { return }
        pendingRestoreData = nil
        do {
            try store.restoreBackup(from: data)
            status = NSLocalizedString("备份还原完成", comment: "")
            isError = false
        } catch {
            status = "\(NSLocalizedString("还原失败", comment: ""))：\(error.localizedDescription)"
            isError = true
        }
    }
}

private struct BackupJSONDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data = Data()

    init(data: Data = Data()) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

private struct PrivacyView: View {
    var body: some View {
        List {
            Label("角色卡、聊天、头像和背景保存在应用私有目录。", systemImage: "iphone")
            Label("API Key 存放在 Keychain，不写入普通配置。", systemImage: "key")
            Label("位置只在主动发送时读取，不进行后台定位。", systemImage: "location")
            Label("聊天图片只发送给当前选择的模型服务。", systemImage: "photo")
        }
        .navigationTitle("隐私与数据")
    }
}

private struct AboutView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image("Dounai")
                .resizable()
                .scaledToFill()
                .frame(width: 92, height: 92)
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            Text("澄语").font(.title.bold())
            Text("KiraChat · 0.9.0 (9)")
                .foregroundStyle(.secondary)
            Text("原生 iOS 角色聊天客户端")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(KiraTheme.page)
        .navigationTitle("关于澄语")
    }
}
