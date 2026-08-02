# 澄语

澄语是一个面向手机和平板的原生角色聊天客户端。Android 端使用原生 View，iOS 端使用 SwiftUI；两端都没有把网页装进 WebView，而是重新实现适合触屏的角色、会话、世界书、群聊和连接流程。

## 已实现

- 微信式四栏信息架构：消息、角色、世界书、我的；入口集中在带选中胶囊的悬浮 Dock
- MIUIX 风格的大标题、圆角分组卡片、低对比背景和轻量层级
- 遵循 Apple 流体交互原则的按下即时反馈、可中断弹簧和系统“移除动画”降级
- Tavern V1/V2 JSON 角色卡导入
- 带 `chara` 元数据的 PNG 角色卡导入，并保留原图作为头像
- `{{char}}`、`{{user}}`、`<BOT>`、`<USER>` 宏替换
- 角色首句、描述、性格、场景和示例对话提示词组合
- 角色卡内嵌 `character_book` 世界书；按最近消息关键词选择最多 6 条
- 多协议直连 API：GPT 兼容 Chat Completions / Responses、Claude Messages、Gemini GenerateContent、Azure OpenAI 与 Ollama
- Claude 使用原生 `/v1/messages`、`x-api-key` / Bearer 与 `anthropic-version`；Gemini 使用原生 `/v1beta/models`、`streamGenerateContent` 与 `x-goog-api-key`
- 接口协议使用单一选择框，默认“GPT 兼容接口”，切换 Claude / Gemini 时会同步设置适合的官方根地址与模型，并自动刷新模型列表
- 兼容 Azure OpenAI `api-key` 请求头，以及 SSE、普通 JSON、JSONL 等常见返回方式
- 默认过滤 DeepSeek、Kimi、GLM 等模型的内部推理字段；可在设置中主动开启“显示模型思考过程”
- 角色联网搜索默认开启（可在设置关闭）：优先使用模型/厂商原生搜索（Responses `web_search`、Claude Web Search、Gemini Google Search、Qwen `enable_search`、OpenRouter Server Tool 等）；厂商明确不支持时才由应用检索并注入带来源结果
- 兼容 Hermes 的 ChatGPT/Codex 设备授权与 Responses SSE 流式生成
- “账户”统一承载 GPT 与 GitHub Copilot：GPT 使用 OpenAI 设备授权，Copilot 使用 GitHub OAuth Device Flow 和官方 Copilot SDK 网关
- 直连 API / 账户 / 本地模型三种聊天生成路由；自动获取直连接口和当前账户的可用模型，支持输入筛选与离线缓存回退
- API 设置内可断点下载、校验并直接启用 Qwen3.5-0.8B 与 Qwen3.5-2B；本地提示词和聊天内容不离开设备
- 消息页右上角统一提供“创建群聊 / 添加角色”；角色页负责浏览与管理
- “我的 → 备份与还原”可导出或完整还原角色、群聊、聊天记录、世界书、头像、聊天背景与普通设置；API Key 和 OAuth 令牌不会进入备份
- 本地角色可以直接自由组合群聊，不需要同步外部服务器
- 微信式群聊消息：每位角色显示独立头像和发言名；每轮随机回复顺序，并由角色按话题关联性决定是否发言
- 群聊输入栏支持 `@角色名` 和 `@所有人`，被提及的角色会优先按明确点名处理
- 群聊会话使用微信式 1–9 人组合头像，流式消息气泡保持稳定宽度
- 聊天页左下角 `+` 提供微信式“相册 / 拍摄 / 语音通话 / 位置”面板
- 图片会压缩保存到应用私有目录，并作为视觉输入发送给支持多模态的模型
- 多模态请求会优先发送标准 `image_url`；仅当兼容接口明确拒绝该类型时，自动降级为纯文本 `[图片]` 重试
- 当前位置以坐标卡片保存并写入模型上下文；定位权限只在点击“位置”后申请
- 角色卡点击进入微信式角色资料页，可查看描述、开场白、世界书和完整设定
- 从消息列表或角色资料进入聊天时使用方向一致的推入/返回过渡；系统关闭动画时自动降级为短淡入淡出
- 角色详情右上角三点可删除角色；删除会同步移出群聊，成员不足两人的群聊会立即解散并清除对应会话
- 导入同名角色时可明确选择“覆盖原角色”或“新增角色”；新增时必须修改为本机未使用的名称，覆盖会保留聊天记录、置顶、免打扰和聊天背景
- 角色资料中的 `user（…）`、`user(...)` 与标准用户宏会自动显示为“我的”页面设置的用户名
- 单聊或群聊中点击模型消息头像可直接进入对应角色资料页
- 聊天右上角三点进入微信式“聊天信息”：查找聊天记录、消息免打扰、置顶聊天和当前聊天背景
- 模型在聊天页退到后台后完成回复时发送系统通知；免打扰会同时隐藏未读红点并停止该会话通知
- 角色资料页点击头像即可从相册更换，并复制到应用私有目录持久保存
- “我的”页面点击个人头像即可更换；单聊和群聊右侧会显示该头像
- 实验性 `gpt-realtime-2.1` 双向语音通话：角色提示词、最近聊天、语义 VAD、实时播放、字幕、静音和扬声器切换
- 微信式深色横向长按菜单，支持复制、编辑、删除、重新生成以及失败消息重试
- 每次生成都会把手机当前日期、时间和时区加入系统提示；单聊生成时只在顶部显示“角色正在输入”，群聊不显示正在输入状态，也不创建三点空气泡
- 模型正在回复时仍可继续发送文字、图片或位置；单聊会按消息顺序进入下一轮，群聊会基于最新上下文重新随机决策
- 群聊成员会基于同一份最新消息同时开始判断和生成；“群聊主动发言”默认开启，空闲时角色会不定期自行发起话题
- 界面跟随系统语言，当前提供简体中文、English 与日本語
- 本地会话持久化
- GPT OAuth 令牌、GitHub OAuth 令牌和直连 API Key 使用 Android Keystore + AES-GCM 加密
- 各厂商 Realtime API Key / 会话凭据分别使用 Android Keystore + AES-GCM 加密
- 单一应用内置向导“豆乃GPT”，设定和头像随 APK 提供，资料页与角色列表明确标记“应用内置”，并指导连接、模型、角色卡、群聊和语音功能
- 升级时只移除旧版三个演示角色，保留用户导入的角色和既有本地群聊
- 完整跟随系统深色模式：页面、卡片、聊天背景、气泡、输入区、弹窗与系统栏使用分层暗色表面，同时保持微信式绿色操作反馈

## 安装与开始

1. 安装 APK。
2. 在“我的 → 连接与账户”填写 API 根地址或完整请求 URL 和 API Key。接口协议默认“GPT 兼容接口”，也可选择 Responses、Claude Messages、Gemini GenerateContent、Azure OpenAI、Ollama 原生接口或自动识别。应用会按协议自动请求模型列表，也可以手动输入模型 ID；本地免鉴权接口可不填 Key。
3. 选择聊天生成方式：

   - **直连 API**：聊天请求直接发往上一步配置的 GPT 兼容、Claude 或 Gemini 接口。
   - **账户 → GPT**：点击“登录 GPT 账户”，复制设备验证码并在自动打开的 `auth.openai.com` 页面完成登录；授权成功后会自动获取该账户可见的 Codex 模型。
   - **账户 → GitHub Copilot**：填写启用 Device Flow 的 GitHub OAuth App Client ID 和已部署的 Copilot SDK 网关，随后在 GitHub 官方页面确认设备验证码。登录后会通过网关自动读取当前 Copilot 账户可用模型。
   - **本地**：在同页“本地模型”卡片下载 Qwen3.5-0.8B 或 Qwen3.5-2B，校验完成后点“使用”。本地运行要求 Android 9+ 的 64 位 ARM 设备。

4. 在“消息”页点右上角 `+`，选择“添加角色”导入 JSON/PNG 角色卡。
5. 本地至少有两位角色后，在同一菜单选择“创建群聊”，勾选成员即可使用。
6. 点击角色进入资料页，再点“发消息”开始聊天；聊天页左下角 `+` 可选择相册、拍照、语音通话或发送当前位置。长按消息可编辑、复制、删除或重新生成；右上角三点可查找记录、免打扰、置顶或更换当前聊天背景。
7. 在角色资料页点击头像可以更换角色头像；在“我的”页点击个人头像可以更换自己的头像。点击“语音通话”可进入 Realtime 通话页。

应用允许连接局域网 HTTP 兼容接口。请只在可信局域网使用 HTTP；公网 API 必须使用 HTTPS。

## 账户说明

- GPT：应用不显示 ChatGPT 密码输入框，也无法读取你的密码；授权与账号验证只发生在 OpenAI 官方页面。
- 设备授权流程与 [Hermes Agent](https://github.com/NousResearch/hermes-agent) 的 OpenAI Codex 登录兼容，令牌保存在本应用自己的 Keystore 密文中，不会读取或覆盖 Hermes/Codex 的本地凭据。
- 该模式使用 ChatGPT Codex 后端，不是通用 OpenAI API 密钥直连。账户计划、模型权限、使用额度和服务可用性由 OpenAI 决定。
- 应用会为 Codex 请求补齐账户 ID、`originator` 等兼容头，并在 403 时使用浏览器标识重试；对疑似污染的 `chatgpt.com` DNS 会优先尝试 HTTPS DNS。若 OpenAI 登录网页本身仍显示 Cloudflare 403，需切换可访问的网络或代理，客户端无法代替浏览器完成 JavaScript 安全验证。
- 这是实验性兼容功能，不是 OpenAI 为任意第三方 Android 客户端承诺的稳定接口；上游授权或后端协议变化时可能需要更新应用。
- GitHub Copilot：应用只实现 GitHub 官方 OAuth Device Flow，不要求 GitHub 密码。安卓无法直接运行 Copilot CLI，因此模型目录和聊天经 [`copilot-gateway`](copilot-gateway/README.md) 调用 GitHub 官方 Copilot SDK；每位用户使用自己的 OAuth 令牌和 Copilot 订阅。Client Secret 不会放入 APK。

## Realtime 语音通话

旧版 OpenAI 短期令牌服务已移除。“我的 → 连接与账户 → Realtime 语音”现在按厂商分别保存配置和 Keystore 加密凭据，支持以下两种接入：

- 原生直连：Qwen3.5 Omni Realtime、GLM-Realtime、百度 Realtime、OpenAI Realtime、Gemini Live、xAI Voice Agent、ElevenLabs ElevenAgents。
- 实时适配器：豆包/火山 S2S-O 与 S2S-SC、TRTC AI 实时对话、MiniMax Speech 2.8 + M2.7、Amazon Nova 2 Sonic、Mistral Voxtral Realtime + LLM + Voxtral TTS。

进入设置或切换语音厂商后，应用会自动读取厂商当前模型目录；OpenAI、Gemini、xAI、Mistral、Qwen 和智谱使用各自模型 API，云签名型服务从已配置实时适配器的 `/models` 读取。厂商目录不可用时保留上次缓存或内置兼容列表，仍允许手动填写模型 ID。

原生直连凭据不会写入 APK 或普通设置，但它仍存在终端设备上；只应在自己的设备使用可撤销、限额的 Key。TRTC UserSig、Bedrock SigV4、火山 SecretKey 等必须留在后端，因此应用不会要求这些云端 SecretKey，而是连接你填写的实时适配器 WSS。

实时适配器接收的首个文本帧为：

```json
{
  "type": "session.start",
  "provider": "trtc",
  "model": "trtc-ai-conversation",
  "voice": "",
  "instructions": "角色与最近聊天提示词",
  "input_audio": {"format": "pcm_s16le", "sample_rate": 16000, "channels": 1},
  "output_audio": {"format": "pcm_s16le", "sample_rate": 24000, "channels": 1},
  "metadata": {}
}
```

随后应用发送 `input_audio.append`（Base64 PCM）；适配器返回 `audio.delta`、`transcript.delta`、`transcript.done`、`state`、`response.done` 或 `error`。适配器也可直接返回二进制 PCM 音频帧。公网必须使用 WSS；通话页离开或挂断后会立即释放麦克风和连接。

## 直连 API 兼容基线

GPT 兼容直连服务至少需要提供一种生成路由：

- `POST /v1/chat/completions`
- `POST /v1/responses`

模型列表会依次尝试适合当前地址的 `/v1/models`、`/models` 和版本化路径；列表不可用时仍可手动填写模型。Chat 路由接受 `model`、`messages` 和 `stream`，Responses 路由接受 `model`、`input` 和 `stream`。大多数服务使用 Bearer Key；Azure OpenAI 域名自动使用 `api-key`。

Claude 原生协议使用 `POST /v1/messages` 和 `GET /v1/models`；Gemini 原生协议使用 `POST /v1beta/models/{model}:streamGenerateContent?alt=sse` 和 `GET /v1beta/models`。两者均支持文字与相册/拍照的多模态输入，并在“联网搜索”开启时分别声明 Claude web search 与 Gemini Google Search 工具。

端点兼容策略参考了 [CC Switch](https://github.com/farion1231/cc-switch) 的模型端点候选、完整 URL 和 Chat/Responses 协议区分方式，但本应用直接在客户端适配，不依赖本地桌面代理。

## Qwen3.5 本地模型

“我的 → 连接与账户 → 本地模型”提供两个经过固定提交和 SHA-256 校验的 GGUF 下载项：

- `Qwen3.5-0.8B Q4_0`：主模型 563,036,064 字节；同时下载 204,987,232 字节的 `mmproj-F16.gguf` 视觉组件，支持本地图片理解，适合优先考虑速度和内存占用的设备。
- `Qwen3.5-2B Q4_K_M`：主模型 1,280,835,840 字节；同时下载 668,227,264 字节的 `mmproj-F16.gguf` 视觉组件，质量更高但需要更多内存。

下载写入应用专属外部存储，支持保留 `.part` 文件后继续下载；完成后先核对预期文件大小，再计算完整 SHA-256，校验通过才显示“使用”。卸载应用时模型会随应用专属数据删除。模型下载地址固定到具体 Hugging Face 提交，避免上游同名文件静默替换。

端侧推理由 [llama.cpp](https://github.com/ggml-org/llama.cpp) `b10202` 的 Android arm64 Vulkan 构建提供：默认优先将模型层卸载到 GPU，仅在 GPU 启动失败且尚未生成内容时回退通用 ARM CPU。运行文件和 MIT 许可证说明位于 `third_party/llama.cpp`。应用仍保持 `minSdk 26` 和版本 `0.9.0 (9)`；只有本地模型入口要求 Android 9（API 28）或更高版本及 arm64，直连 API 与账户模式继续兼容原系统范围。

本地接入使用 4096 token 上下文和单进程内存保护，最新一张图片会经匹配的视觉组件在设备端编码。联网搜索开启后，网络 API 先调用模型或厂商的原生搜索；只有服务端明确拒绝搜索工具且尚未输出正文时，才把应用检索的带来源摘要注入提示词重试。本地模型没有厂商搜索能力，直接使用应用检索；应用兜底只发送当前查询给公共搜索服务，不上传本地聊天历史。群聊的多个本地回复会排队推理，避免同时加载多个模型进程导致系统因内存不足终止应用。首次回答需要载入 GGUF，耗时会明显长于后续网络请求。

## 构建

### iOS

iOS 工程位于 `ios/`，使用 SwiftUI、Keychain、PhotosUI、Core Location、Speech 与 AVFoundation，最低支持 iOS 16。版本保持 `0.9.0 (9)`，Bundle ID 为 `app.miuix.tavern`。

在 macOS 安装 Xcode 与 XcodeGen 后执行：

```bash
cp app/src/main/res/mipmap-nodpi/app_icon.png ios/KiraChat/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png
cp app/src/main/res/raw/dounai_gpt.png ios/KiraChat/Resources/Assets.xcassets/Dounai.imageset/dounai_gpt.png
cd ios
xcodegen generate
xcodebuild -project KiraChat.xcodeproj -scheme KiraChat -sdk iphoneos -destination 'generic/platform=iOS' build
```

仓库的 `.github/workflows/ios-ipa.yml` 会在 GitHub macOS Runner 上自动生成工程、复用 Android 端应用图标和豆乃GPT头像、构建设备 App，并打包 `KiraChat-0.9.0-unsigned.ipa`。这个 IPA 没有内置开发者签名：正式安装或分发前仍需使用自己的 Apple Developer Team、证书和描述文件重新签名。

iOS 首版已移植四栏 Dock、角色资料、Tavern JSON 角色卡、世界书、本地群聊、微信式消息气泡、图片/拍照/位置、多协议直连 API、GPT/Copilot 账户登录、自动模型列表、联网搜索声明、Keychain 凭据、三语界面，以及使用系统听写 + 当前聊天模型 + 系统 TTS 的按住说话语音对话。Android 端各厂商原生 Realtime WebSocket 与本地 Qwen GGUF 尚未移入 iOS；界面不会把这些尚未移植的能力伪装为可用。

### Android

要求：

- JDK 11
- Gradle 6.7.1
- Android SDK Platform 29
- Android Build Tools 29.0.3

构建命令：

```powershell
.\gradlew.bat :app:assembleDebug
```

APK 位于：

```text
app\build\outputs\apk\debug\app-debug.apk
```

应用包名为 `app.miuix.tavern`，最低 Android 8.0（API 26），目标 Android 10（API 29）。

## 数据与隐私

- 角色卡、聊天和普通设置保存在应用私有存储。
- GPT OAuth 令牌、GitHub OAuth 令牌和直连 API Key 不进入普通设置 JSON，也不会以明文回退保存。GitHub OAuth Client ID 和 Copilot SDK 网关地址属于公开配置，会保存在普通设置中。
- 各厂商 Realtime API Key / 会话凭据不进入普通设置 JSON，按厂商分别加密保存在 Android Keystore。
- 更换后的角色头像和用户头像复制到应用私有目录，不持续依赖相册读取权限。
- 聊天图片也会复制并压缩到应用私有目录；只有在发送消息时才交给当前配置的模型服务。
- 位置权限仅用于用户主动点击“位置”后取得经纬度并发送；应用不在后台持续定位。
- Android 备份被关闭，避免会话和密文随系统备份迁移。
- 应用不包含统计或广告。

## 项目状态

这是可安装、可连接、可聊天的原生客户端。请参阅 [DESIGN.md](DESIGN.md) 了解交互与视觉约束。
