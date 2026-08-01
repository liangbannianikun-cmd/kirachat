import PhotosUI
import SwiftUI
import UIKit

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
