import SwiftUI
import UniformTypeIdentifiers

private enum AppTab: String, CaseIterable, Identifiable {
    case messages
    case characters
    case worldbooks
    case me

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .messages: return "消息"
        case .characters: return "角色"
        case .worldbooks: return "世界书"
        case .me: return "我的"
        }
    }

    var icon: String {
        switch self {
        case .messages: return "bubble.left.and.bubble.right"
        case .characters: return "person.2"
        case .worldbooks: return "book.closed"
        case .me: return "person.crop.circle"
        }
    }
}

struct RootView: View {
    @State private var selection: AppTab = .messages

    var body: some View {
        Group {
            switch selection {
            case .messages:
                NavigationStack { MessagesView() }
            case .characters:
                NavigationStack { CharactersView() }
            case .worldbooks:
                NavigationStack { WorldbooksView() }
            case .me:
                NavigationStack { MeView() }
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            FloatingDock(selection: $selection)
        }
        .background(KiraTheme.page.ignoresSafeArea())
    }
}

private struct FloatingDock: View {
    @Binding var selection: AppTab
    @Namespace private var selectionNamespace

    var body: some View {
        HStack(spacing: 4) {
            ForEach(AppTab.allCases) { tab in
                Button {
                    selection = tab
                } label: {
                    VStack(spacing: 3) {
                        Image(systemName: tab.icon)
                            .font(.system(size: 18, weight: .semibold))
                        Text(tab.title)
                            .font(.system(size: 10.5, weight: .medium))
                    }
                    .foregroundStyle(selection == tab ? KiraTheme.green : Color.secondary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background {
                        if selection == tab {
                            Capsule(style: .continuous)
                                .fill(KiraTheme.green.opacity(0.12))
                                .matchedGeometryEffect(id: "dock", in: selectionNamespace)
                                .padding(.horizontal, 4)
                                .padding(.vertical, 5)
                        }
                    }
                }
                .buttonStyle(KiraPressStyle())
                .accessibilityLabel(tab.title)
                .accessibilityAddTraits(selection == tab ? .isSelected : [])
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 4)
        .background(.ultraThinMaterial, in: Capsule(style: .continuous))
        .overlay(Capsule(style: .continuous).stroke(.primary.opacity(0.06)))
        .shadow(color: .black.opacity(0.12), radius: 18, y: 7)
        .padding(.horizontal, 14)
        .padding(.top, 6)
        .padding(.bottom, 5)
        .animation(.interactiveSpring(response: 0.28, dampingFraction: 1), value: selection)
    }
}

private struct ConversationItem: Identifiable {
    let target: ConversationTarget
    let title: String
    let message: ChatMessage?
    let lastUsed: Date
    let pinned: Bool
    let muted: Bool
    let unread: Int
    let character: CharacterCard?
    let group: GroupChat?

    var id: String { target.conversationID }
}

struct MessagesView: View {
    @EnvironmentObject private var store: AppStore
    @State private var showImporter = false
    @State private var showCreateGroup = false
    @State private var importError = ""
    @State private var pendingImportedCard: CharacterCard?
    @State private var duplicateCard: CharacterCard?

    private var items: [ConversationItem] {
        let characters = store.characters.map { card in
            ConversationItem(
                target: .character(card.id),
                title: card.name,
                message: store.messages(for: .character(card.id)).last,
                lastUsed: card.lastUsed,
                pinned: card.pinned,
                muted: card.muted,
                unread: card.unread,
                character: card,
                group: nil)
        }
        let groups = store.groups.map { group in
            ConversationItem(
                target: .group(group.id),
                title: group.name,
                message: store.messages(for: .group(group.id)).last,
                lastUsed: group.lastUsed,
                pinned: group.pinned,
                muted: group.muted,
                unread: group.unread,
                character: nil,
                group: group)
        }
        return (characters + groups).sorted {
            if $0.pinned != $1.pinned { return $0.pinned }
            return $0.lastUsed > $1.lastUsed
        }
    }

    var body: some View {
        ZStack {
            KiraTheme.page.ignoresSafeArea()
            if items.isEmpty {
                EmptyState(
                    systemImage: "bubble.left.and.bubble.right",
                    title: "还没有消息",
                    message: "添加角色或创建群聊后，会话会显示在这里。")
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(items) { item in
                            NavigationLink(value: item.target) {
                                ConversationRow(item: item)
                            }
                            .buttonStyle(KiraPressStyle())
                            Divider().padding(.leading, 80)
                        }
                    }
                    .background(KiraTheme.surface)
                }
            }
        }
        .navigationTitle("消息")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Menu {
                    Button("创建群聊", systemImage: "person.3") {
                        showCreateGroup = true
                    }
                    Button("添加角色", systemImage: "person.badge.plus") {
                        showImporter = true
                    }
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 18, weight: .semibold))
                        .frame(width: 38, height: 38)
                }
                .accessibilityLabel("创建群聊或添加角色")
            }
        }
        .navigationDestination(for: ConversationTarget.self) { target in
            ChatView(target: target)
        }
        .fileImporter(
            isPresented: $showImporter,
            allowedContentTypes: [.json, .png],
            allowsMultipleSelection: false) { result in
                importCharacter(result)
            }
        .sheet(isPresented: $showCreateGroup) {
            CreateGroupView()
                .environmentObject(store)
        }
        .sheet(item: $pendingImportedCard) { imported in
            ImportCharacterView(
                imported: imported,
                duplicate: duplicateCard)
                .environmentObject(store)
        }
        .alert("无法添加角色", isPresented: Binding(
            get: { !importError.isEmpty },
            set: { if !$0 { importError = "" } })) {
            Button("好", role: .cancel) { importError = "" }
        } message: {
            Text(importError)
        }
    }

    private func importCharacter(_ result: Result<[URL], Error>) {
        do {
            guard let url = try result.get().first else { return }
            let accessed = url.startAccessingSecurityScopedResource()
            defer { if accessed { url.stopAccessingSecurityScopedResource() } }
            let card = try store.importTavernCard(
                CharacterCardFileImporter.load(from: url))
            duplicateCard = store.matchingCharacter(named: card.name)
            pendingImportedCard = card
        } catch {
            importError = error.localizedDescription
        }
    }
}

private struct ConversationRow: View {
    @EnvironmentObject private var store: AppStore
    let item: ConversationItem

    private var preview: String {
        guard let message = item.message else { return NSLocalizedString("暂无消息", comment: "") }
        switch message.attachment {
        case .image: return NSLocalizedString("[图片]", comment: "")
        case .location: return NSLocalizedString("[位置]", comment: "")
        case .voiceCall:
            return "\(NSLocalizedString("通话时长", comment: "")) \(ChatMessage.formatCallDuration(message.callDurationSeconds ?? 0))"
        case .none: return message.content
        }
    }

    var body: some View {
        HStack(spacing: 14) {
            if let character = item.character {
                CharacterAvatar(character: character, size: 54)
            } else if let group = item.group {
                GroupAvatar(members: store.members(of: group), size: 54)
            }
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(item.title)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Spacer()
                    Text(item.lastUsed, style: .time)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                HStack(spacing: 5) {
                    if item.muted { Image(systemName: "bell.slash.fill") }
                    Text(preview).lineLimit(1)
                    Spacer()
                    if item.unread > 0 && !item.muted {
                        Text("\(min(item.unread, 99))")
                            .font(.caption2.bold())
                            .foregroundStyle(.white)
                            .padding(.horizontal, 6)
                            .frame(minHeight: 18)
                            .background(.red, in: Capsule())
                    }
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
        .background(KiraTheme.surface)
        .contentShape(Rectangle())
    }
}

private struct ImportCharacterView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let imported: CharacterCard
    let duplicate: CharacterCard?
    @State private var name: String

    init(imported: CharacterCard, duplicate: CharacterCard?) {
        self.imported = imported
        self.duplicate = duplicate
        _name = State(initialValue: duplicate == nil
            ? imported.name
            : "\(imported.name) 2")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("角色卡") {
                    TextField("角色名称", text: $name)
                    Text(imported.description.isEmpty ? "暂无角色描述" : imported.description)
                        .foregroundStyle(.secondary)
                        .lineLimit(5)
                }
                if let duplicate {
                    Section {
                        Button("覆盖原角色“\(duplicate.name)”", role: .destructive) {
                            var value = imported
                            value.name = duplicate.name
                            store.overwriteCharacter(existingID: duplicate.id, with: value)
                            dismiss()
                        }
                    } footer: {
                        Text("覆盖会保留原聊天记录、置顶、免打扰和聊天背景。")
                    }
                }
            }
            .navigationTitle("添加角色")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("新建") {
                        var value = imported
                        value.name = store.uniqueCharacterName(from: name)
                        store.addCharacter(value)
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}

private struct CreateGroupView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var selected: Set<String> = []

    var body: some View {
        NavigationStack {
            List {
                Section {
                    TextField("群聊名称", text: $name)
                }
                Section("选择至少两位角色") {
                    ForEach(store.characters) { card in
                        Button {
                            if selected.contains(card.id) { selected.remove(card.id) }
                            else { selected.insert(card.id) }
                        } label: {
                            HStack {
                                CharacterAvatar(character: card, size: 42)
                                Text(card.name).foregroundStyle(.primary)
                                Spacer()
                                Image(systemName: selected.contains(card.id)
                                      ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(selected.contains(card.id)
                                                     ? KiraTheme.green : .secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("创建群聊")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("创建") {
                        store.createGroup(name: name, memberIDs: Array(selected))
                        dismiss()
                    }
                    .disabled(selected.count < 2)
                }
            }
        }
    }
}
