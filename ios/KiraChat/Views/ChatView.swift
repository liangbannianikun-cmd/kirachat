import CoreLocation
import PhotosUI
import SwiftUI
import UIKit

struct ChatView: View {
    @EnvironmentObject private var store: AppStore
    let target: ConversationTarget
    @State private var composer = ""
    @State private var photoItem: PhotosPickerItem?
    @State private var showCamera = false
    @State private var cameraImage: UIImage?
    @State private var showInfo = false
    @State private var callCharacter: CharacterCard?
    @StateObject private var locationProvider = LocationProvider()
    @FocusState private var composerFocused: Bool

    private var title: String {
        switch target {
        case .character(let id): return store.character(id: id)?.name ?? "聊天"
        case .group(let id): return store.group(id: id)?.name ?? "群聊"
        }
    }

    private var messages: [ChatMessage] { store.messages(for: target) }

    var body: some View {
        ZStack {
            chatBackground
            VStack(spacing: 0) {
                messageList
                composerBar
            }
        }
        .background(KiraTheme.chatSurface.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { showInfo = true } label: {
                    Image(systemName: "ellipsis")
                        .frame(width: 38, height: 38)
                }
                .accessibilityLabel("聊天信息")
            }
        }
        .sheet(isPresented: $showInfo) {
            NavigationStack {
                ChatInfoView(target: target)
                    .environmentObject(store)
            }
        }
        .sheet(isPresented: $showCamera) {
            CameraPicker(image: $cameraImage)
                .ignoresSafeArea()
        }
        .fullScreenCover(item: $callCharacter) { character in
            VoiceCallView(target: target, character: character)
                .environmentObject(store)
        }
        .onAppear { store.touch(target) }
        .onChange(of: photoItem) { item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    sendImage(data)
                }
                photoItem = nil
            }
        }
        .onChange(of: cameraImage) { image in
            guard let data = image?.jpegData(compressionQuality: 0.82) else { return }
            sendImage(data)
            cameraImage = nil
        }
        .onChange(of: locationProvider.location) { location in
            guard let location else { return }
            var message = ChatMessage(role: .user, content: "我分享了当前位置")
            message.attachment = .location
            message.latitude = location.coordinate.latitude
            message.longitude = location.coordinate.longitude
            store.submit(message, to: target)
            locationProvider.clear()
        }
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 13) {
                    ForEach(messages) { message in
                        MessageRow(target: target, message: message)
                            .id(message.id)
                    }
                    if case .character = target, store.isGenerating(target) {
                        HStack(spacing: 7) {
                            ProgressView().controlSize(.small)
                            Text("对方正在输入…")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Spacer()
                        }
                        .padding(.horizontal, 18)
                        .transition(.opacity)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 16)
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: messages.count) { _ in
                guard let last = messages.last else { return }
                withAnimation(.easeOut(duration: 0.2)) {
                    proxy.scrollTo(last.id, anchor: .bottom)
                }
            }
            .onAppear {
                if let last = messages.last { proxy.scrollTo(last.id, anchor: .bottom) }
            }
        }
    }

    private var composerBar: some View {
        HStack(alignment: .bottom, spacing: 8) {
            Menu {
                PhotosPicker(selection: $photoItem, matching: .images) {
                    Label("相册", systemImage: "photo")
                }
                Button("拍摄", systemImage: "camera") { showCamera = true }
                Button("语音通话", systemImage: "phone") { chooseCallCharacter() }
                Button("位置", systemImage: "location") { locationProvider.request() }
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .semibold))
                    .frame(width: 34, height: 38)
                    .background(Color(uiColor: .tertiarySystemFill), in: Circle())
            }
            .accessibilityLabel("更多功能")

            TextField("发送消息", text: $composer, axis: .vertical)
                .lineLimit(1...6)
                .focused($composerFocused)
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(Color(uiColor: .systemBackground),
                            in: RoundedRectangle(cornerRadius: 12, style: .continuous))

            Button("发送") { sendText() }
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 15)
                .frame(height: 38)
                .background(KiraTheme.green, in: RoundedRectangle(
                    cornerRadius: 12,
                    style: .continuous))
                .buttonStyle(KiraPressStyle())
                .disabled(composer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .opacity(composer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.45 : 1)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(.ultraThinMaterial)
    }

    @ViewBuilder
    private var chatBackground: some View {
        let data: Data? = {
            switch target {
            case .character(let id): return store.character(id: id)?.chatBackgroundData
            case .group(let id): return store.group(id: id)?.chatBackgroundData
            }
        }()
        if let data, let image = UIImage(data: data) {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .ignoresSafeArea()
                .overlay(Color.black.opacity(0.04))
        } else {
            KiraTheme.chatSurface.ignoresSafeArea()
        }
    }

    private func sendText() {
        let text = composer.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        composer = ""
        store.submit(ChatMessage(role: .user, content: text), to: target)
    }

    private func sendImage(_ original: Data) {
        let data: Data
        if let image = UIImage(data: original),
           let compressed = image.jpegData(compressionQuality: 0.82) {
            data = compressed
        } else {
            data = original
        }
        var message = ChatMessage(role: .user, content: "[图片]")
        message.attachment = .image
        message.imageData = data
        store.submit(message, to: target)
    }

    private func chooseCallCharacter() {
        switch target {
        case .character(let id):
            callCharacter = store.character(id: id)
        case .group(let id):
            guard let group = store.group(id: id) else { return }
            callCharacter = store.members(of: group).randomElement()
        }
    }
}

private struct MessageRow: View {
    @EnvironmentObject private var store: AppStore
    let target: ConversationTarget
    let message: ChatMessage

    private var isUser: Bool { message.role == .user }

    private var speaker: CharacterCard? {
        if isUser { return nil }
        if !message.speaker.isEmpty { return store.character(id: message.speaker) }
        if case .character(let id) = target { return store.character(id: id) }
        return nil
    }

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            if isUser { Spacer(minLength: 48) }
            if !isUser, let speaker {
                NavigationLink {
                    CharacterProfileView(characterID: speaker.id)
                } label: {
                    CharacterAvatar(character: speaker, size: 40)
                }
                .buttonStyle(KiraPressStyle())
            }
            VStack(alignment: isUser ? .trailing : .leading, spacing: 4) {
                if !isUser, case .group = target, let speaker {
                    Text(speaker.name)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .padding(.leading, 4)
                }
                bubble
            }
            if isUser {
                PersonaAvatar(data: store.settings.personaAvatarData, size: 40)
            } else {
                Spacer(minLength: 48)
            }
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private var bubble: some View {
        Group {
            switch message.attachment {
            case .image:
                if let data = message.imageData, let image = UIImage(data: data) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 210, height: 160)
                        .clipped()
                } else {
                    Text("[图片]")
                }
            case .location:
                VStack(alignment: .leading, spacing: 5) {
                    Label("当前位置", systemImage: "location.fill")
                        .font(.headline)
                    Text(String(format: "%.5f, %.5f",
                                message.latitude ?? 0,
                                message.longitude ?? 0))
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
                .frame(minWidth: 180, alignment: .leading)
            case .voiceCall:
                HStack(spacing: 8) {
                    if !isUser { Image(systemName: "phone") }
                    Text("\(NSLocalizedString("通话时长", comment: "")) \(ChatMessage.formatCallDuration(message.callDurationSeconds ?? 0))")
                        .font(.system(size: 16))
                    if isUser { Image(systemName: "phone") }
                }
                .frame(minWidth: 132)
            case .none:
                Text(message.content)
                    .textSelection(.enabled)
            }
        }
        .font(.system(size: 16))
        .foregroundStyle(message.failed ? Color.red : Color.primary)
        .padding(.horizontal, message.attachment == .image ? 0 : 12)
        .padding(.vertical, message.attachment == .image ? 0 : 9)
        .background(
            isUser ? KiraTheme.bubbleGreen : Color(uiColor: .systemBackground),
            in: BubbleShape(isUser: isUser))
        .clipShape(BubbleShape(isUser: isUser))
        .contextMenu {
            Button {
                UIPasteboard.general.string = message.content
            } label: {
                Label("复制", systemImage: "doc.on.doc")
            }
            if message.failed {
                Button {
                    store.retry(message, in: target)
                } label: {
                    Label("重试", systemImage: "arrow.clockwise")
                }
            }
            Button(role: .destructive) {
                store.deleteMessage(message, from: target)
            } label: {
                Label("删除", systemImage: "trash")
            }
        }
    }
}

private struct BubbleShape: Shape {
    let isUser: Bool

    func path(in rect: CGRect) -> Path {
        let corners: UIRectCorner = isUser
            ? [.topLeft, .bottomLeft, .bottomRight]
            : [.topRight, .bottomLeft, .bottomRight]
        return Path(UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: 16, height: 16)).cgPath)
    }
}

private struct ChatInfoView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let target: ConversationTarget
    @State private var query = ""
    @State private var backgroundItem: PhotosPickerItem?
    @State private var confirmClear = false
    @State private var showRenameGroup = false
    @State private var groupNameDraft = ""

    private var filteredMessages: [ChatMessage] {
        let values = store.messages(for: target)
        let clean = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return clean.isEmpty ? [] : values.filter {
            $0.content.localizedCaseInsensitiveContains(clean)
        }
    }

    var body: some View {
        List {
            if let group = currentGroup {
                Section {
                    Button {
                        groupNameDraft = group.name
                        showRenameGroup = true
                    } label: {
                        HStack {
                            Text("群聊名称")
                                .foregroundStyle(.primary)
                            Spacer()
                            Text(group.name)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                            Image(systemName: "chevron.right")
                                .font(.caption.bold())
                                .foregroundStyle(.tertiary)
                        }
                    }
                }
            }
            Section {
                TextField("查找聊天记录", text: $query)
                ForEach(filteredMessages.prefix(30)) { message in
                    Text(message.content).lineLimit(2)
                }
            }
            Section {
                Toggle("消息免打扰", isOn: mutedBinding)
                Toggle("置顶聊天", isOn: pinnedBinding)
                PhotosPicker(selection: $backgroundItem, matching: .images) {
                    Label("设置当前聊天背景", systemImage: "photo.on.rectangle")
                }
            }
            Section {
                Button("清空聊天记录", role: .destructive) { confirmClear = true }
            }
        }
        .navigationTitle("聊天信息")
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("完成") { dismiss() }
            }
        }
        .confirmationDialog("清空当前聊天记录？", isPresented: $confirmClear) {
            Button("清空", role: .destructive) { store.clearMessages(for: target) }
            Button("取消", role: .cancel) {}
        }
        .alert("修改群聊名称", isPresented: $showRenameGroup) {
            TextField("输入新的群聊名称", text: $groupNameDraft)
            Button("取消", role: .cancel) {}
            Button("保存") { renameGroup() }
                .disabled(groupNameDraft.trimmingCharacters(
                    in: .whitespacesAndNewlines).isEmpty)
        } message: {
            Text("群聊名称最多 50 个字符。")
        }
        .onChange(of: backgroundItem) { item in
            guard let item else { return }
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self) else { return }
                setBackground(data)
            }
        }
    }

    private var currentGroup: GroupChat? {
        guard case .group(let id) = target else { return nil }
        return store.group(id: id)
    }

    private func renameGroup() {
        guard var group = currentGroup else { return }
        let clean = groupNameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        group.name = String(clean.prefix(50))
        store.updateGroup(group)
    }

    private var mutedBinding: Binding<Bool> {
        Binding(get: {
            switch target {
            case .character(let id): return store.character(id: id)?.muted ?? false
            case .group(let id): return store.group(id: id)?.muted ?? false
            }
        }, set: { value in
            switch target {
            case .character(let id):
                guard var card = store.character(id: id) else { return }
                card.muted = value
                store.updateCharacter(card)
            case .group(let id):
                guard var group = store.group(id: id) else { return }
                group.muted = value
                store.updateGroup(group)
            }
        })
    }

    private var pinnedBinding: Binding<Bool> {
        Binding(get: {
            switch target {
            case .character(let id): return store.character(id: id)?.pinned ?? false
            case .group(let id): return store.group(id: id)?.pinned ?? false
            }
        }, set: { value in
            switch target {
            case .character(let id):
                guard var card = store.character(id: id) else { return }
                card.pinned = value
                store.updateCharacter(card)
            case .group(let id):
                guard var group = store.group(id: id) else { return }
                group.pinned = value
                store.updateGroup(group)
            }
        })
    }

    private func setBackground(_ data: Data) {
        switch target {
        case .character(let id):
            guard var card = store.character(id: id) else { return }
            card.chatBackgroundData = data
            store.updateCharacter(card)
        case .group(let id):
            guard var group = store.group(id: id) else { return }
            group.chatBackgroundData = data
            store.updateGroup(group)
        }
    }
}

final class LocationProvider: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var location: CLLocation?
    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func request() {
        switch manager.authorizationStatus {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .authorizedAlways, .authorizedWhenInUse: manager.requestLocation()
        default: break
        }
    }

    func clear() { location = nil }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse
            || manager.authorizationStatus == .authorizedAlways {
            manager.requestLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        location = locations.last
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    }
}

struct CameraPicker: UIViewControllerRepresentable {
    @Environment(\.dismiss) private var dismiss
    @Binding var image: UIImage?

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera)
            ? .camera : .photoLibrary
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {
    }

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        var parent: CameraPicker

        init(parent: CameraPicker) { self.parent = parent }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            parent.image = info[.originalImage] as? UIImage
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}
