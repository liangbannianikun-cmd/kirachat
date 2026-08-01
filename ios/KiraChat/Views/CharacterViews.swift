import PhotosUI
import SwiftUI

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
        guard let data = card.worldBookJSON.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let entries = root["entries"] as? [Any] else { return 0 }
        return entries.count
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
                            Text(card.worldBookJSON)
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
}
