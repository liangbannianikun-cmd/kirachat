import AVFoundation
import Speech
import SwiftUI

struct VoiceCallView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let target: ConversationTarget
    let character: CharacterCard
    @StateObject private var controller: VoiceCallController
    @State private var savedRecord = false

    init(target: ConversationTarget, character: CharacterCard) {
        self.target = target
        self.character = character
        _controller = StateObject(wrappedValue: VoiceCallController(
            character: character,
            history: [],
            settings: AppSettings(),
            apiKey: ""))
    }

    var body: some View {
        ZStack {
            Color(red: 0.137, green: 0.145, blue: 0.165).ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    Button {
                        finishCall()
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.title3.weight(.semibold))
                            .frame(width: 48, height: 48)
                    }
                    .foregroundStyle(.white)
                    Spacer()
                }
                .padding(.horizontal, 8)

                Spacer()
                CharacterAvatar(character: character, size: 118)
                Text(character.name)
                    .font(.title2.bold())
                    .foregroundStyle(.white)
                    .padding(.top, 22)
                Text(controller.status)
                    .font(.body)
                    .foregroundStyle(.white)
                    .padding(.top, 18)
                if controller.startedAt != nil {
                    TimelineView(.periodic(from: .now, by: 1)) { _ in
                        Text(ChatMessage.formatCallDuration(controller.elapsedSeconds))
                            .font(.system(.body, design: .monospaced))
                            .foregroundStyle(.secondary)
                            .padding(.top, 7)
                    }
                }
                Text(controller.transcript.isEmpty
                     ? "按住麦克风说话，松开后发送"
                     : controller.transcript)
                    .font(.subheadline)
                    .foregroundStyle(Color.white.opacity(0.66))
                    .multilineTextAlignment(.center)
                    .lineLimit(5)
                    .padding(.horizontal, 34)
                    .padding(.top, 14)
                Spacer()

                HStack(spacing: 42) {
                    CallControl(
                        icon: controller.isRecording ? "mic.fill" : "mic",
                        label: controller.isRecording ? "正在聆听" : "按住说话",
                        color: controller.isRecording ? KiraTheme.green : Color.white.opacity(0.16))
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { _ in controller.startRecording() }
                                .onEnded { _ in controller.stopAndSend() })

                    Button {
                        finishCall()
                    } label: {
                        CallControl(
                            icon: "phone.down.fill",
                            label: "挂断",
                            color: Color(red: 0.925, green: 0.29, blue: 0.29))
                    }
                    .buttonStyle(KiraPressStyle())
                }
                .padding(.bottom, 42)
            }
        }
        .navigationBarBackButtonHidden(true)
        .onAppear {
            controller.configure(
                history: store.messages(for: target),
                settings: store.settings,
                apiKey: store.apiKey)
            controller.connect()
        }
        .onDisappear { saveCallRecord() }
    }

    private func finishCall() {
        controller.stop()
        saveCallRecord()
        dismiss()
    }

    private func saveCallRecord() {
        guard !savedRecord else { return }
        savedRecord = true
        let duration = controller.elapsedSeconds
        if duration > 0 { store.addVoiceCall(duration: duration, to: target) }
    }
}

private struct CallControl: View {
    let icon: String
    let label: LocalizedStringKey
    let color: Color

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 24, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 66, height: 66)
                .background(color, in: Circle())
            Text(label)
                .font(.caption)
                .foregroundStyle(.white)
        }
        .frame(width: 92)
        .contentShape(Rectangle())
    }
}

final class VoiceCallController: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    @Published private(set) var status = NSLocalizedString("正在准备通话…", comment: "")
    @Published private(set) var transcript = ""
    @Published private(set) var isRecording = false
    @Published private(set) var startedAt: Date?

    private let character: CharacterCard
    private var history: [ChatMessage]
    private var settings: AppSettings
    private var apiKey: String
    private let audioEngine = AVAudioEngine()
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let speechRecognizer = SFSpeechRecognizer(locale: Locale.current)
    private let synthesizer = AVSpeechSynthesizer()
    private var currentUtterance = ""
    private var connected = false

    init(
        character: CharacterCard,
        history: [ChatMessage],
        settings: AppSettings,
        apiKey: String
    ) {
        self.character = character
        self.history = history
        self.settings = settings
        self.apiKey = apiKey
        super.init()
        synthesizer.delegate = self
    }

    var elapsedSeconds: Int {
        guard let startedAt else { return 0 }
        return max(1, Int(Date().timeIntervalSince(startedAt).rounded()))
    }

    func configure(history: [ChatMessage], settings: AppSettings, apiKey: String) {
        self.history = history
        self.settings = settings
        self.apiKey = apiKey
    }

    func connect() {
        guard !connected else { return }
        SFSpeechRecognizer.requestAuthorization { [weak self] speechStatus in
            AVAudioSession.sharedInstance().requestRecordPermission { microphoneAllowed in
                DispatchQueue.main.async {
                    guard let self else { return }
                    guard speechStatus == .authorized, microphoneAllowed else {
                        self.status = NSLocalizedString("没有麦克风或语音识别权限", comment: "")
                        return
                    }
                    self.connected = true
                    self.startedAt = Date()
                    self.status = NSLocalizedString("通话中 · 按住说话", comment: "")
                }
            }
        }
    }

    func startRecording() {
        guard connected, !isRecording, !synthesizer.isSpeaking else { return }
        do {
            recognitionTask?.cancel()
            recognitionTask = nil
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(
                .playAndRecord,
                mode: .voiceChat,
                options: [.defaultToSpeaker, .allowBluetooth])
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            recognitionRequest = request
            currentUtterance = ""
            transcript = ""
            let input = audioEngine.inputNode
            let format = input.outputFormat(forBus: 0)
            input.removeTap(onBus: 0)
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
                request.append(buffer)
            }
            recognitionTask = speechRecognizer?.recognitionTask(with: request) { [weak self] result, error in
                DispatchQueue.main.async {
                    guard let self else { return }
                    if let text = result?.bestTranscription.formattedString {
                        self.currentUtterance = text
                        self.transcript = text
                    }
                    if error != nil { self.stopCapture() }
                }
            }
            audioEngine.prepare()
            try audioEngine.start()
            isRecording = true
            status = NSLocalizedString("通话中 · 正在聆听", comment: "")
        } catch {
            status = error.localizedDescription
            stopCapture()
        }
    }

    func stopAndSend() {
        guard isRecording else { return }
        stopCapture()
        let text = currentUtterance.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else {
            status = NSLocalizedString("通话中 · 按住说话", comment: "")
            return
        }
        var user = ChatMessage(role: .user, content: text)
        user.timestamp = Date()
        history.append(user)
        status = NSLocalizedString("通话中 · 正在思考", comment: "")
        let card = character
        let context = history
        let config = settings
        let credential = apiKey
        Task { [weak self] in
            do {
                let reply = try await APIService.complete(
                    character: card,
                    history: context,
                    settings: config,
                    apiKey: credential)
                await MainActor.run {
                    guard let self else { return }
                    self.history.append(ChatMessage(role: .assistant, content: reply))
                    self.transcript = reply
                    self.status = NSLocalizedString("通话中 · 对方正在说话", comment: "")
                    let utterance = AVSpeechUtterance(string: reply)
                    utterance.voice = AVSpeechSynthesisVoice(language: Locale.current.identifier)
                    utterance.rate = AVSpeechUtteranceDefaultSpeechRate
                    self.synthesizer.speak(utterance)
                }
            } catch {
                await MainActor.run {
                    self?.status = error.localizedDescription
                }
            }
        }
    }

    func stop() {
        stopCapture()
        synthesizer.stopSpeaking(at: .immediate)
        connected = false
        try? AVAudioSession.sharedInstance().setActive(
            false,
            options: .notifyOthersOnDeactivation)
    }

    private func stopCapture() {
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }
        recognitionRequest?.endAudio()
        recognitionRequest = nil
        recognitionTask?.finish()
        recognitionTask = nil
        isRecording = false
    }

    func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didFinish utterance: AVSpeechUtterance
    ) {
        DispatchQueue.main.async { [weak self] in
            self?.status = NSLocalizedString("通话中 · 按住说话", comment: "")
        }
    }
}
