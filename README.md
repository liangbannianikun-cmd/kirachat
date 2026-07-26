# 澄语

澄语是一个面向普通 Android 手机和平板的原生角色聊天客户端。它没有把网页装进 WebView，而是重新实现了适合触屏的角色、会话、世界书、群聊和连接流程。

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
- 角色联网搜索默认开启（可在设置关闭）：Responses 使用 `web_search`，Chat 搜索模型使用 `web_search_options`，搜索来源会附在回复末尾并可点击
- 兼容 Hermes 的 ChatGPT/Codex 设备授权与 Responses SSE 流式生成
- “账户”统一承载 GPT 与 Claude：GPT 使用设备授权，Claude 使用本机加密保存的 API Key / Bearer 访问令牌
- 直连 API / 账户两种聊天生成路由；自动获取直连接口和当前账户的可用模型，支持输入筛选与离线缓存回退
- 消息页右上角统一提供“创建群聊 / 添加角色”；角色页负责浏览与管理
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
- GPT OAuth 令牌、Claude 凭据和直连 API Key 使用 Android Keystore + AES-GCM 加密
- Realtime 令牌服务 Bearer 使用 Android Keystore + AES-GCM 加密
- 单一应用内置向导“豆乃GPT”，设定和头像随 APK 提供，资料页与角色列表明确标记“应用内置”，并指导连接、模型、角色卡、群聊和语音功能
- 升级时只移除旧版三个演示角色，保留用户导入的角色和既有本地群聊
- 完整跟随系统深色模式：页面、卡片、聊天背景、气泡、输入区、弹窗与系统栏使用分层暗色表面，同时保持微信式绿色操作反馈

## 安装与开始

1. 安装 APK。
2. 在“我的 → 连接与账户”填写 API 根地址或完整请求 URL 和 API Key。接口协议默认“GPT 兼容接口”，也可选择 Responses、Claude Messages、Gemini GenerateContent、Azure OpenAI、Ollama 原生接口或自动识别。应用会按协议自动请求模型列表，也可以手动输入模型 ID；本地免鉴权接口可不填 Key。
3. 选择聊天生成方式：

   - **直连 API**：聊天请求直接发往上一步配置的 GPT 兼容、Claude 或 Gemini 接口。
   - **账户 → GPT**：点击“登录 GPT 账户”，复制设备验证码并在自动打开的 `auth.openai.com` 页面完成登录；授权成功后会自动获取该账户可见的 Codex 模型。
   - **账户 → Claude**：粘贴 Claude API Key 或 Bearer 访问令牌并保存；应用会加密保存凭据并自动获取 Claude 模型。该入口不伪造第三方消费者 OAuth 登录。

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
- Claude：使用官方 API Key 或 Bearer 访问令牌连接 Claude API；凭据只保存在本应用的 Keystore 密文中。Anthropic 没有为任意第三方 Android 消费者提供可直接复用的账号密码登录流程，因此这里不会要求 Claude 密码，也不会模拟网页登录。

## Realtime 语音通话

语音通话使用 `gpt-realtime-2.1`。ChatGPT/Codex 登录令牌不能代替 OpenAI API 的 Realtime 凭据；应用也不会把长期 OpenAI API Key 存在 APK 或手机普通设置中。需要由你控制的服务使用标准 API Key 申请短期 client secret，手机只获取短期令牌。

项目根目录包含一个仅依赖 Node.js 18+ 的示例：

```powershell
$env:OPENAI_API_KEY = "你的 OpenAI API Key"
$env:MIUTAVERN_TOKEN = "自定义一个访问口令"
node .\realtime-token-server.example.mjs
```

然后在“我的 → 连接与账户 → Realtime 语音”填写：

- 短期令牌服务：`http://电脑局域网地址:8787/token`
- 令牌服务 Bearer：与 `MIUTAVERN_TOKEN` 相同

公网部署必须使用 HTTPS。局域网 HTTP 只适合可信网络；API 项目还需要具备 Realtime 模型权限和可用额度。本版本使用短期令牌连接 WebSocket 并由客户端处理 PCM 音频，通话页离开或挂断后会立即释放麦克风与连接。

## 直连 API 兼容基线

GPT 兼容直连服务至少需要提供一种生成路由：

- `POST /v1/chat/completions`
- `POST /v1/responses`

模型列表会依次尝试适合当前地址的 `/v1/models`、`/models` 和版本化路径；列表不可用时仍可手动填写模型。Chat 路由接受 `model`、`messages` 和 `stream`，Responses 路由接受 `model`、`input` 和 `stream`。大多数服务使用 Bearer Key；Azure OpenAI 域名自动使用 `api-key`。

Claude 原生协议使用 `POST /v1/messages` 和 `GET /v1/models`；Gemini 原生协议使用 `POST /v1beta/models/{model}:streamGenerateContent?alt=sse` 和 `GET /v1beta/models`。两者均支持文字与相册/拍照的多模态输入，并在“联网搜索”开启时分别声明 Claude web search 与 Gemini Google Search 工具。

端点兼容策略参考了 [CC Switch](https://github.com/farion1231/cc-switch) 的模型端点候选、完整 URL 和 Chat/Responses 协议区分方式，但本应用直接在客户端适配，不依赖本地桌面代理。

## 构建

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
- GPT OAuth 令牌、Claude 凭据和直连 API Key 不进入普通设置 JSON，也不会以明文回退保存。
- Realtime 令牌服务 Bearer 不进入普通设置 JSON；短期 OpenAI token 只保留在内存中。
- 更换后的角色头像和用户头像复制到应用私有目录，不持续依赖相册读取权限。
- 聊天图片也会复制并压缩到应用私有目录；只有在发送消息时才交给当前配置的模型服务。
- 位置权限仅用于用户主动点击“位置”后取得经纬度并发送；应用不在后台持续定位。
- Android 备份被关闭，避免会话和密文随系统备份迁移。
- 应用不包含统计、广告或自建账号系统。

## 项目状态

这是可安装、可连接、可聊天的原生客户端。请参阅 [DESIGN.md](DESIGN.md) 了解交互与视觉约束。
