import PhotosUI
import SwiftUI

struct MeView: View {
    @EnvironmentObject private var store: AppStore
    @State private var avatarItem: PhotosPickerItem?
    @State private var persona = ""

    var body: some View {
        ZStack {
            KiraTheme.page.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 12) {
                    HStack(spacing: 17) {
                        PhotosPicker(selection: $avatarItem, matching: .images) {
                            ZStack(alignment: .bottomTrailing) {
                                PersonaAvatar(data: store.settings.personaAvatarData, size: 76)
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
                                detail: store.settings.model.isEmpty
                                    ? "尚未选择模型" : store.settings.model)
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

    var body: some View {
        Form {
            Section("生成方式") {
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

            Section("模型") {
                TextField("模型 ID", text: settingBinding(\.model))
                    .textInputAutocapitalization(.never)
                if !store.availableModels.isEmpty {
                    Picker("接口返回的模型", selection: settingBinding(\.model)) {
                        if store.settings.model.isEmpty {
                            Text("请选择").tag("")
                        }
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
                Text("API Key 只保存在本机 Keychain；普通设置和聊天备份中不包含密钥。局域网 HTTP 仅应连接可信服务。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("连接与账户")
        .onAppear {
            apiKey = store.apiKey
            if !apiKey.isEmpty && store.availableModels.isEmpty {
                Task { await store.refreshModels() }
            }
        }
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

