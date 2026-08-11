import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

struct CharactersView: View {
    @EnvironmentObject private var store: AppStore
    private let columns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12)
    ]

    var body: some View {
        ZStack {
            KiraTheme.page.ignoresSafeArea()
            ScrollView {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(store.characters) { card in
                        NavigationLink {
                            CharacterProfileView(characterID: card.id)
                        } label: {
                            CharacterTile(card: card)
                        }
                        .buttonStyle(KiraPressStyle())
                    }
                }
                .padding(16)
            }
        }
        .navigationTitle("角色")
        .navigationBarTitleDisplayMode(.large)
    }
}

private struct CharacterTile: View {
    let card: CharacterCard

    var body: some View {
        VStack(alignment: .leading, spacing: 11) {
            CharacterAvatar(character: card, size: 76)
            Text(card.name)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(.primary)
                .lineLimit(1)
            Text(card.isBuiltIn ? "应用内置" : "兼容角色卡")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(15)
        .kiraCard()
        .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

struct CharacterProfileView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let characterID: String
    @State private var avatarItem: PhotosPickerItem?
    @State private var showDefinition = false
    @State private var confirmDelete = false
    @State private var showCardImporter = false
    @State private var replacementError = ""
    @State private var pendingReplacement: CharacterCard?

    private var card: CharacterCard? { store.character(id: characterID) }

    var body: some View {
        Group {
            if let card {
                ScrollView {
                    VStack(spacing: 10) {
                        profileHeader(card)
                        definitionRows(card)
                        actionRows(card)
                    }
                    .padding(.bottom, 28)
                }
                .background(KiraTheme.page)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Menu {
                            Button("查看完整设定", systemImage: "doc.text.magnifyingglass") {
                                showDefinition = true
                            }
                            if !card.isBuiltIn {
                                Button("更换角色卡", systemImage: "arrow.triangle.2.circlepath") {
                                    showCardImporter = true
                                }
                                Button("删除角色", systemImage: "trash", role: .destructive) {
                                    confirmDelete = true
                                }
                            }
                        } label: {
                            Image(systemName: "ellipsis")
                                .frame(width: 38, height: 38)
                        }
                        .accessibilityLabel("更多")
                    }
                }
                .sheet(isPresented: $showDefinition) {
                    NavigationStack {
                        ScrollView {
                            Text(fullDefinition(card))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(18)
                                .textSelection(.enabled)
                        }
                        .navigationTitle("完整设定")
                        .toolbar {
                            ToolbarItem(placement: .confirmationAction) {
                                Button("完成") { showDefinition = false }
                            }
                        }
                    }
                }
                .confirmationDialog(
                    "删除角色“\(card.name)”？",
                    isPresented: $confirmDelete,
                    titleVisibility: .visible) {
                    Button("删除角色", role: .destructive) {
                        store.deleteCharacter(card)
                        dismiss()
                    }
                    Button("取消", role: .cancel) {}
                } message: {
                    Text("角色卡和单聊记录会从本机删除；不足两人的群聊也会解散。")
                }
                .fileImporter(
                    isPresented: $showCardImporter,
                    allowedContentTypes: [.json, .png],
                    allowsMultipleSelection: false) { result in
                        importReplacement(result)
                    }
                .sheet(item: $pendingReplacement) { imported in
                    ReplaceCharacterCardView(
                        existingID: characterID,
                        imported: imported)
                        .environmentObject(store)
                }
                .alert("更换角色卡失败", isPresented: Binding(
                    get: { !replacementError.isEmpty },
                    set: { if !$0 { replacementError = "" } })) {
                    Button("好", role: .cancel) { replacementError = "" }
                } message: {
                    Text(replacementError)
                }
            } else {
                EmptyState(
                    systemImage: "person.crop.circle.badge.questionmark",
                    title: "角色不存在",
                    message: "角色可能已被删除。")
            }
        }
        .onChange(of: avatarItem) { item in
            guard let item else { return }
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self),
                      data.count <= 15 * 1024 * 1024,
                      var updated = card else { return }
                updated.avatarData = data
                store.updateCharacter(updated)
            }
        }
    }

    private func importReplacement(_ result: Result<[URL], Error>) {
        do {
            guard let url = try result.get().first else { return }
            let accessed = url.startAccessingSecurityScopedResource()
            defer { if accessed { url.stopAccessingSecurityScopedResource() } }
            pendingReplacement = try store.importTavernCard(
                CharacterCardFileImporter.load(from: url))
        } catch {
            replacementError = error.localizedDescription
        }
    }

    private func profileHeader(_ card: CharacterCard) -> some View {
        HStack(alignment: .top, spacing: 18) {
            PhotosPicker(selection: $avatarItem, matching: .images) {
                ZStack(alignment: .bottomTrailing) {
                    CharacterAvatar(character: card, size: 86)
                    Image(systemName: "pencil")
                        .font(.caption.bold())
                        .frame(width: 27, height: 27)
                        .background(.regularMaterial, in: Circle())
                }
            }
            .buttonStyle(KiraPressStyle())
            .accessibilityLabel("更换头像")

            VStack(alignment: .leading, spacing: 7) {
                Text(card.name)
                    .font(.title2.weight(.bold))
                Text(card.isBuiltIn ? "来源：应用内置" : "来源：兼容角色卡")
                Text("世界书：\(worldBookCount(card)) 条")
                Text("最近聊天：\(card.lastUsed.formatted(date: .abbreviated, time: .shortened))")
            }
            .font(.subheadline)
            .foregroundStyle(.secondary)
            Spacer(minLength: 0)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(KiraTheme.surface)
    }

    private func definitionRows(_ card: CharacterCard) -> some View {
        VStack(spacing: 0) {
            ProfileTextRow(title: "角色描述", value: card.description, empty: "暂无角色描述")
            Divider().padding(.leading, 18)
            ProfileTextRow(title: "开场白", value: card.firstMessage, empty: "暂无开场白")
            Divider().padding(.leading, 18)
            ProfileTextRow(title: "性格", value: card.personality, empty: "暂无性格设定")
            Divider().padding(.leading, 18)
            ProfileTextRow(title: "场景", value: card.scenario, empty: "暂无场景设定")
            Divider().padding(.leading, 18)
            ProfileTextRow(title: "创作者备注", value: card.creatorNotes, empty: "暂无备注")
        }
        .background(KiraTheme.surface)
    }

    private func actionRows(_ card: CharacterCard) -> some View {
        VStack(spacing: 0) {
            NavigationLink {
                ChatView(target: .character(card.id))
            } label: {
                Label("发消息", systemImage: "bubble.left")
                    .font(.headline)
                    .foregroundStyle(Color(red: 0.34, green: 0.42, blue: 0.59))
                    .frame(maxWidth: .infinity, minHeight: 70)
            }
            .buttonStyle(KiraPressStyle())
            Divider()
            NavigationLink {
                VoiceCallView(target: .character(card.id), character: card)
            } label: {
                Label("语音通话", systemImage: "phone")
                    .font(.headline)
                    .foregroundStyle(Color(red: 0.34, green: 0.42, blue: 0.59))
                    .frame(maxWidth: .infinity, minHeight: 70)
            }
            .buttonStyle(KiraPressStyle())
        }
        .background(KiraTheme.surface)
    }

    private func fullDefinition(_ card: CharacterCard) -> String {
        [
            "角色名称\n\(card.name)",
            "角色描述\n\(card.description)",
            "性格\n\(card.personality)",
            "场景\n\(card.scenario)",
            "开场白\n\(card.firstMessage)",
            "示例对话\n\(card.exampleDialogue)",
            "创作者备注\n\(card.creatorNotes)"
        ].joined(separator: "\n\n")
    }

    private func worldBookCount(_ card: CharacterCard) -> Int {
        guard card.worldBookJSON.utf8.prefix(8 * 1024 * 1024 + 1).count
                <= 8 * 1024 * 1024,
              let data = card.worldBookJSON.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let entries = root["entries"] as? [Any] else { return 0 }
        return entries.count
    }
}

private struct ReplaceCharacterCardView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let existingID: String
    let imported: CharacterCard

    private var existing: CharacterCard? { store.character(id: existingID) }

    private var conflict: CharacterCard? {
        store.characters.first {
            $0.id != existingID
                && $0.name.caseInsensitiveCompare(imported.name) == .orderedSame
        }
    }

    private var replacementName: String {
        conflict == nil ? imported.name : (existing?.name ?? imported.name)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("新角色卡") {
                    LabeledContent("当前角色", value: existing?.name ?? "角色")
                    LabeledContent("更换为", value: replacementName)
                    if !imported.description.isEmpty {
                        Text(imported.description)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineLimit(5)
                    }
                }
                if conflict != nil {
                    Section {
                        Text("新角色卡名称与现有角色重复，因此将继续使用当前角色名称。")
                            .foregroundStyle(.orange)
                    }
                }
                Section {
                    Text("新角色卡将替换当前设定和头像；聊天记录、群聊成员、免打扰、置顶和聊天背景会保留。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("更换角色卡")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("更换") {
                        var value = imported
                        value.name = replacementName
                        store.overwriteCharacter(existingID: existingID, with: value)
                        dismiss()
                    }
                    .disabled(existing == nil)
                }
            }
        }
    }
}

private struct ProfileTextRow: View {
    let title: LocalizedStringKey
    let value: String
    let empty: LocalizedStringKey

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title).font(.headline)
            if value.isEmpty {
                Text(empty).foregroundStyle(.secondary)
            } else {
                Text(value)
                    .foregroundStyle(.secondary)
                    .lineLimit(4)
            }
        }
        .font(.subheadline)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
    }
}

struct WorldbooksView: View {
    @EnvironmentObject private var store: AppStore

    private var linked: [CharacterCard] {
        store.characters.filter { !$0.worldBookJSON.isEmpty }
    }

    var body: some View {
        ZStack {
            KiraTheme.page.ignoresSafeArea()
            if linked.isEmpty {
                EmptyState(
                    systemImage: "book.closed",
                    title: "还没有世界书",
                    message: "导入带 character_book 的角色卡后，世界书会显示在这里。")
            } else {
                List(linked) { card in
                    NavigationLink {
                        ScrollView {
                            Text(preview(card.worldBookJSON))
                                .font(.system(.footnote, design: .monospaced))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding()
                                .textSelection(.enabled)
                        }
                        .navigationTitle(card.name)
                    } label: {
                        HStack(spacing: 13) {
                            CharacterAvatar(character: card, size: 44)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(card.name).font(.headline)
                                Text("已关联角色世界书").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle("世界书")
        .navigationBarTitleDisplayMode(.large)
    }

    private func preview(_ value: String) -> String {
        let limit = 32_000
        guard let end = value.index(
            value.startIndex,
            offsetBy: limit,
            limitedBy: value.endIndex),
              end != value.endIndex else { return value }
        let note = NSLocalizedString(
            "世界书内容较多，已省略屏幕预览；聊天时仍会按相关性读取。",
            comment: "")
        return String(value[..<end]) + "\n\n[\(note)]"
    }
}
