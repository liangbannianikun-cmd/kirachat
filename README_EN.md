<p align="center"><a href="README.md">简体中文</a> · <strong>English</strong> · <a href="README_JA.md">日本語</a></p>

<p align="center">
  <img src="app/src/main/res/mipmap-nodpi/app_icon.png" width="112" alt="KiraChat app icon">
</p>

<h1 align="center">KiraChat</h1>

<p align="center"><strong>Bring SillyTavern character cards, multiple model APIs, local models, and group chat to a native Android / iOS client.</strong></p>

<p align="center">
  <a href="https://github.com/liangbannianikun-cmd/kirachat/releases/tag/v0.9.0"><img src="https://img.shields.io/badge/KiraChat-v0.9.0-22C55E" alt="KiraChat v0.9.0"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/iOS-16%2B-000000?logo=apple&amp;logoColor=white" alt="iOS 16+">
  <img src="https://img.shields.io/badge/UI-中文%20%7C%20English%20%7C%20日本語-5B8FF9" alt="Simplified Chinese, English, and Japanese">
</p>

<p align="center">
  <a href="https://github.com/liangbannianikun-cmd/kirachat/releases/download/v0.9.0/KiraChat-0.9.0-android-debug.apk"><strong>Download Android APK</strong></a>
  ·
  <a href="https://github.com/liangbannianikun-cmd/kirachat/releases/download/v0.9.0/KiraChat-0.9.0-ios-unsigned.ipa"><strong>Download iOS IPA</strong></a>
  ·
  <a href="https://github.com/liangbannianikun-cmd/kirachat/releases/tag/v0.9.0">Release and checksums</a>
</p>

KiraChat is not a WebView wrapper. The Android app uses native Views and the iOS app uses SwiftUI. Conversations, character profiles, lorebooks, groups, voice, and connection settings are redesigned for touch around a familiar WeChat-style structure.

## What it solves

| Problem | KiraChat's approach |
| --- | --- |
| Web role-chat interfaces feel crowded on phones | Native Android/iOS screens, mobile gestures, and a WeChat-style message layout |
| Character cards, models, and providers are spread across tools | Import SillyTavern V1/V2/V3 JSON and PNG `chara`/`ccv3` cards, then connect GPT, Claude, Gemini, and compatible services in one place |
| You want private, on-device chat | Android can download and run Qwen3.5-0.8B/2B locally, including local image understanding |
| Ordinary chat apps do not support multiple characters | Create local groups with mentions, relevance-based participation, parallel generation, and composite avatars |
| Moving characters and chats between devices is difficult | Export/restore local backups or use a self-hosted, client-side encrypted sync server |

## Install now

| Platform | Download | Installation |
| --- | --- | --- |
| Android 8.0+ | **[Download APK](https://github.com/liangbannianikun-cmd/kirachat/releases/download/v0.9.0/KiraChat-0.9.0-android-debug.apk)** | This is currently a debug-signed test build. Allow installation from unknown sources, then open the APK. |
| iOS 16+ | **[Download unsigned IPA](https://github.com/liangbannianikun-cmd/kirachat/releases/download/v0.9.0/KiraChat-0.9.0-ios-unsigned.ipa)** | Sign it with your own certificate or signing tool before installation. |

KiraChat packages and checksums are published in the **[KiraChat v0.9.0 Release](https://github.com/liangbannianikun-cmd/kirachat/releases/tag/v0.9.0)**. Before replacing an existing installation, export a backup from **Me → Backup & Restore**.

### Start chatting in three steps

1. Open **Me → Connections & accounts**. Configure a compatible API, sign in to an account, or download a local Qwen model on Android.
2. Return to **Messages**, tap `+` in the top-right corner, and import a JSON/PNG character card.
3. Open the character and send a message. With at least two local characters, you can also create a local group.

> [!IMPORTANT]
> Account access, quotas, models, and regional availability for GPT, Claude, Gemini, Copilot, and other cloud services are controlled by their providers. The iOS build does not yet include Android's native Realtime WebSocket providers or local GGUF inference; see the platform table below.

## Core capabilities

- **Character-card compatibility:** SillyTavern / Character Card V1–V3 JSON, PNG `chara` and `ccv3`, macros, and embedded lorebooks.
- **Multiple model routes:** GPT-compatible APIs, OpenAI Responses, Claude Messages, Gemini, Azure OpenAI, Ollama, plus GPT and GitHub Copilot account modes.
- **Local and multimodal:** Qwen3.5 local inference on Android, image understanding, gallery, camera, location, and web search.
- **Native group chat:** Create groups without a SillyTavern server, with mentions, randomized/parallel replies, and autonomous character messages.
- **Mobile experience:** WeChat-style messages and composite avatars, MIUIX visuals, dark mode, three UI languages, and system notifications.
- **Control of your data:** Local persistence, backup/restore, and self-hosted encrypted sync. API keys and OAuth tokens are excluded from normal backups.

## Platform support

| Capability | Android | iOS |
| --- | :---: | :---: |
| Native UI, character cards, lorebooks, groups | ✅ | ✅ |
| GPT / Claude / Gemini direct APIs | ✅ | ✅ |
| GPT / GitHub Copilot accounts | ✅ | ✅ |
| Images, camera, location, web search | ✅ | ✅ |
| Qwen3.5 GGUF local inference | ✅ | — |
| Native multi-provider Realtime voice | ✅ | — |
| System dictation + current model + TTS voice chat | ✅ | ✅ |
| Encrypted server sync | ✅ | ✅ |

## Documentation

- [Detailed setup](#detailed-setup)
- [Server sync](#server-sync)
- [Account notes](#account-notes)
- [Realtime voice](#realtime-voice)
- [Direct API compatibility](#direct-api-compatibility)
- [Local Qwen3.5 models](#local-qwen35-models)
- [Build](#build)
- [Data and privacy](#data-and-privacy)
- [Design notes](DESIGN.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](.github/SECURITY.md)

<details>
<summary><strong>Complete feature list</strong></summary>

### Interface and conversations

- Four-tab WeChat-style information architecture: Messages, Characters, Lorebook, and Me, presented in a floating Dock.
- MIUIX-inspired large titles, rounded grouped cards, restrained hierarchy, dark mode, immediate press feedback, interruptible springs, and reduced-motion fallback.
- WeChat-style direct/group bubbles, 1–9 member composite group avatars, message search, mute, pin, per-chat backgrounds, notifications, and long-press actions.
- Users may continue sending text, images, or locations while a reply is being generated. Direct chats queue turns; groups reconsider the latest context.
- Simplified Chinese, English, and Japanese interfaces.

### Characters, cards, and lorebooks

- Tavern/Character Card V1/V2/V3 JSON, PNG `chara`/`ccv3`, first messages, descriptions, personalities, scenarios, examples, creator notes, and character/user macros.
- Embedded `character_book` lorebooks support up to 1,000,000 entries and select relevant entries within a safe per-request context budget.
- Duplicate names can be overwritten or imported under a unique new name. Character cards and avatars can be replaced without losing chat history, group membership, mute/pin state, or backgrounds.
- Character avatars open profile pages; users can replace both character and personal avatars.
- One built-in guide, Dounai GPT, explains connections, models, cards, groups, and voice features.

### Models, media, and search

- GPT-compatible Chat Completions / Responses, Claude Messages, Gemini GenerateContent, Azure OpenAI, Ollama, common SSE/JSON/JSONL responses, and automatic model discovery with cache fallback.
- GPT device authorization and GitHub Copilot OAuth Device Flow via the included Copilot SDK gateway.
- Reasoning output is hidden by default and can be enabled in Settings.
- Web search defaults to on: provider-native search is preferred, then KiraChat's sourced fallback is used when the provider explicitly lacks it.
- Gallery, camera, image compression, multimodal requests, location cards, and compatibility fallback from unsupported `image_url` inputs to `[Image]`.
- Android can download verified Qwen3.5-0.8B/2B GGUF and matching vision projectors, prefer Vulkan GPU offload, and fall back safely when needed.

### Groups, voice, and data

- Local groups require no external SillyTavern server. Members decide relevance concurrently, reply in randomized order, support `@Character`/`@everyone`, and may post autonomous messages when enabled.
- Direct chats can also allow occasional autonomous character messages.
- Android supports native/adapter Realtime voice providers; voice-call duration is saved as a chat bubble. iOS provides dictation + current chat model + system TTS.
- Backup/restore covers characters, groups, messages, lorebooks, avatars, backgrounds, and normal settings while excluding credentials.
- Self-hosted encrypted sync uses client-side AES-GCM and explicit conflict resolution across Android and iOS.
- API keys, GPT/Copilot OAuth tokens, and Realtime credentials use platform-protected secure storage.

</details>

## Detailed setup

1. Install the APK or signed IPA.
2. In **Me → Connections & accounts**, enter an API root or full request URL and API key. The default protocol is GPT-compatible; Responses, Claude Messages, Gemini GenerateContent, Azure OpenAI, Ollama-native, and automatic detection are also available. KiraChat refreshes model lists automatically, but manual model IDs remain supported. Local unauthenticated endpoints may leave the key blank.
3. Choose a generation route:

   - **Direct API:** requests go directly to the configured GPT-compatible, Claude, or Gemini service.
   - **Account → GPT:** start GPT sign-in, copy the device code, and complete authorization on the official `auth.openai.com` page. Available Codex models are loaded after authorization.
   - **Account → GitHub Copilot:** enter a GitHub OAuth App Client ID with Device Flow enabled and the URL of a deployed Copilot SDK gateway, then approve the device code on GitHub.
   - **Local (Android):** download Qwen3.5-0.8B or Qwen3.5-2B in the Local Models card, verify it, and tap **Use**. Local inference requires Android 9+, arm64.

4. In Messages, tap `+` → **Add character** and import a JSON/PNG card.
5. With at least two characters, tap `+` → **Create group** and select members.
6. Open a character profile and tap **Message**. The chat `+` menu contains Gallery, Camera, Voice Call, and Location. Long-press messages to edit, copy, delete, retry, or regenerate.
7. Tap character/user avatars to replace them. Use the top-right menu for search, mute, pin, and chat background.
8. For multi-device sync, deploy [`sync-server`](sync-server/README.md), then enter its URL, token, and a separate encryption password in **Me → Server sync**. Upload the first device and download on other devices before enabling automatic sync.

KiraChat can connect to local-network HTTP endpoints. Use HTTP only on trusted LANs; public endpoints should use HTTPS.

## Server sync

The repository includes a Node.js sync service with no third-party runtime dependencies:

```bash
cd sync-server
export SYNC_TOKENS="replace-with-a-random-token-of-at-least-24-characters"
npm start
```

Use a reverse proxy with HTTPS for public deployments. The sync token controls server access; the end-to-end encryption password remains on clients. The server stores ciphertext, revision, update time, and device type only.

Characters, groups, messages, lorebooks, avatars, backgrounds, persona name, and normal feature settings are synchronized. API keys, GPT/Copilot tokens, Realtime credentials, and local model files are not. Conditional writes stop on concurrent edits and ask the user to upload the local state or download the server state instead of silently overwriting data.

## Account notes

- **GPT:** KiraChat never displays or reads a ChatGPT password. Authorization happens on OpenAI's official page. The mode uses a ChatGPT Codex backend rather than a general OpenAI API key. Plans, quotas, model access, and availability are controlled by OpenAI.
- The device flow is compatible with Hermes Agent's OpenAI Codex login, but KiraChat stores its own encrypted credentials and does not read or overwrite Hermes/Codex credentials.
- KiraChat sends compatible account headers and retries 403 responses with a browser user agent. Cloudflare browser challenges still require a network/browser able to complete the official JavaScript verification.
- This is experimental third-party compatibility; upstream authorization and backend changes may require KiraChat updates.
- **GitHub Copilot:** KiraChat uses GitHub OAuth Device Flow and never asks for a GitHub password. Android cannot host the Copilot CLI, so model discovery and chat use the included [`copilot-gateway`](copilot-gateway/README.md) built on GitHub's Copilot SDK. Each user supplies their own OAuth token and Copilot subscription. Client secrets are not bundled in the APK.

## Realtime voice

**Me → Connections & accounts → Realtime voice** stores provider-specific settings and credentials encrypted by Android Keystore.

- **Native connections:** Qwen3.5 Omni Realtime, GLM-Realtime, Baidu Realtime, OpenAI Realtime, Gemini Live, xAI Voice Agent, and ElevenLabs ElevenAgents.
- **Realtime adapters:** Doubao/Volcengine S2S-O and S2S-SC, TRTC AI Conversation, MiniMax Speech 2.8 + M2.7, Amazon Nova 2 Sonic, and Mistral Voxtral Realtime + LLM + Voxtral TTS.

Model lists refresh from each configured provider. When a provider list is unavailable, the last cache or a compatibility list remains available and model IDs may be entered manually.

Native credentials stay on the device but should still be revocable and rate-limited. Cloud secrets such as TRTC UserSig, Bedrock SigV4, and Volcengine SecretKey must remain in a backend, so KiraChat connects to a configured Realtime adapter WSS rather than asking for those secrets.

An adapter first receives:

```json
{
  "type": "session.start",
  "provider": "trtc",
  "model": "trtc-ai-conversation",
  "voice": "",
  "instructions": "character and recent conversation prompt",
  "input_audio": {"format": "pcm_s16le", "sample_rate": 16000, "channels": 1},
  "output_audio": {"format": "pcm_s16le", "sample_rate": 24000, "channels": 1},
  "metadata": {}
}
```

KiraChat then sends Base64 PCM in `input_audio.append`. The adapter returns `audio.delta`, `transcript.delta`, `transcript.done`, `state`, `response.done`, or `error`, and may also return binary PCM frames. Public deployments must use WSS. Leaving or ending a call immediately releases the microphone and connection.

## Direct API compatibility

A GPT-compatible service should provide at least one generation route:

- `POST /v1/chat/completions`
- `POST /v1/responses`

Model discovery tries suitable `/v1/models`, `/models`, and versioned paths. Manual model IDs remain available when listing fails. Chat accepts `model`, `messages`, and `stream`; Responses accepts `model`, `input`, and `stream`. Most services use Bearer tokens; Azure OpenAI hosts automatically use `api-key`.

Claude-native mode uses `POST /v1/messages` and `GET /v1/models`. Gemini-native mode uses `POST /v1beta/models/{model}:streamGenerateContent?alt=sse` and `GET /v1beta/models`. Both support text and image input and declare their native web-search tools when Web Search is enabled.

Endpoint candidate handling, complete URLs, and Chat/Responses distinction were informed by [CC Switch](https://github.com/farion1231/cc-switch), but KiraChat implements compatibility in the client and does not require a desktop proxy.

## Local Qwen3.5 models

**Me → Connections & accounts → Local models** offers two GGUF packages pinned to specific upstream commits and verified by size and SHA-256:

- **Qwen3.5-0.8B Q4_0:** 563,036,064-byte model plus a 204,987,232-byte `mmproj-F16.gguf` vision projector; lower memory use and faster inference.
- **Qwen3.5-2B Q4_K_M:** 1,280,835,840-byte model plus a 668,227,264-byte vision projector; higher quality with greater memory requirements.

Downloads live in app-specific external storage, preserve `.part` files for resume, and become usable only after complete verification. Models are removed with app-specific data on uninstall.

Inference uses an Android arm64 Vulkan build of [llama.cpp](https://github.com/ggml-org/llama.cpp) `b10202`. It prefers GPU layer offload and falls back to generic ARM CPU only when GPU startup fails before producing output. Runtime files and the MIT notice are in `third_party/llama.cpp`.

Local inference uses a 4096-token context and single-process memory protection. The latest image is encoded with the matching vision projector. Local models use KiraChat's sourced search fallback when Web Search is enabled. Multiple local group replies are queued to prevent several model processes from exhausting memory. The first reply includes GGUF loading time and is slower than later replies.

## Build

### iOS

The iOS project is under `ios/`, uses SwiftUI, Keychain, PhotosUI, Core Location, Speech, and AVFoundation, and requires iOS 16+. Version: `0.9.0 (9)`; Bundle ID: `app.miuix.tavern`.

On macOS with Xcode and XcodeGen:

```bash
cp app/src/main/res/mipmap-nodpi/app_icon.png ios/KiraChat/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png
cp app/src/main/res/raw/dounai_gpt.png ios/KiraChat/Resources/Assets.xcassets/Dounai.imageset/dounai_gpt.png
cd ios
xcodegen generate
xcodebuild -project KiraChat.xcodeproj -scheme KiraChat -sdk iphoneos -destination 'generic/platform=iOS' build
```

`.github/workflows/ios-ipa.yml` generates the project on a GitHub macOS runner, reuses the Android app icon and Dounai avatar, builds a device app, and packages `KiraChat-0.9.0-unsigned.ipa`. It is unsigned and must be re-signed with your Apple Developer team, certificate, and provisioning profile for installation or distribution.

### Android

Requirements:

- JDK 11
- Gradle 6.7.1
- Android SDK Platform 29
- Android Build Tools 29.0.3

Build:

```powershell
.\gradlew.bat :app:assembleDebug
```

Output: `app\build\outputs\apk\debug\app-debug.apk`

Package: `app.miuix.tavern`; minimum Android 8.0 (API 26); target Android 10 (API 29). Local models require Android 9+ and arm64.

## Data and privacy

- Character cards, conversations, and normal settings are stored in private app storage.
- GPT/GitHub OAuth tokens and direct API keys are excluded from normal JSON settings and are never stored through a plaintext fallback.
- Realtime credentials are stored separately per provider using Android Keystore encryption.
- Replaced character/user avatars and chat images are copied into private storage instead of retaining broad gallery access.
- Images are sent only when a message is submitted to the currently configured model service.
- Location permission is requested only after the user taps Location; KiraChat does not continuously track location in the background.
- Android system backup is disabled to prevent conversations and encrypted values from being migrated by the OS.
- KiraChat contains no advertising or analytics.

## Project status

KiraChat is an installable native client that can connect to models and hold conversations. See [DESIGN.md](DESIGN.md) for interaction and visual constraints. Device behavior and third-party service availability can differ by hardware, provider, account, and network.
