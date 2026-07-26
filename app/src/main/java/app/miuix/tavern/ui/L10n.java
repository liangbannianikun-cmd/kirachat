package app.miuix.tavern.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.text.TextUtils;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class L10n {
    private static final Map<String, String> EN = new LinkedHashMap<>();
    private static final Map<String, String> JA = new LinkedHashMap<>();
    private static final List<String> KEYS = new ArrayList<>();
    private static final Pattern MEMBER_COUNT =
            Pattern.compile("^(\\d+) 位成员$");
    private static final Pattern RECENT_COUNT =
            Pattern.compile("^(\\d+) 个最近会话$");
    private static final Pattern CHARACTER_BOOK_COUNT =
            Pattern.compile("^(\\d+) 位角色 · (\\d+) 本世界书$");
    private static final Pattern PEOPLE_COUNT =
            Pattern.compile("^(.+) · (\\d+) 人$");
    private static final Pattern TYPING =
            Pattern.compile("^(.+)正在输入…$");
    private static final Pattern VIEW_PROFILE =
            Pattern.compile("^查看(.+)的资料$");
    private static final Pattern VIEW_CHARACTER_PROFILE =
            Pattern.compile("^查看(.+)的角色资料$");
    private static final Pattern CHANGE_AVATAR =
            Pattern.compile("^更换(.+)的头像$");
    private static final Pattern CHAT_WITH =
            Pattern.compile("^开始与(.+)聊天$");
    private static final Pattern CALL_WITH =
            Pattern.compile("^与(.+)进行语音通话$");
    private static final Pattern CLEAR_SINGLE =
            Pattern.compile("^清空与 (.+) 的聊天？$");
    private static final Pattern CLEAR_NAMED =
            Pattern.compile("^清空“(.+)”的聊天？$");
    private static final Pattern DELETE_CHARACTER =
            Pattern.compile("^删除角色“(.+)”？$");
    private static final Pattern CREATED_GROUP =
            Pattern.compile("^已创建“(.+)”$");
    private static final Pattern ENTRY_COUNT =
            Pattern.compile("^(\\d+) 条条目$");
    private static final Pattern TRIGGER_COUNT =
            Pattern.compile("^(\\d+) 条可触发设定$");
    private static final Pattern SETTING_COUNT =
            Pattern.compile("^(\\d+) 条设定$");
    private static final Pattern MODELS_FILTERED =
            Pattern.compile("^已获取 (\\d+) 个模型 · 可输入筛选$");
    private static final Pattern CACHED_MODELS =
            Pattern.compile("^获取失败 · 保留 (\\d+) 个缓存模型$");

    static {
        add("消息", "Messages", "メッセージ");
        add("角色", "Characters", "キャラクター");
        add("角色库", "Characters", "キャラクター");
        add("世界书", "Lorebook", "世界書");
        add("我的", "Me", "自分");
        add("设置", "Settings", "設定");
        add("返回", "Back", "戻る");
        add("更多", "More", "その他");
        add("更多功能", "More actions", "その他の機能");
        add("新建会话", "New chat", "新しいチャット");
        add("创建群聊", "New group", "グループを作成");
        add("添加角色", "Add character", "キャラクターを追加");
        add("搜索最近会话内容", "Search recent chats", "最近のチャットを検索");
        add("搜索角色名称或描述", "Search characters", "キャラクターを検索");
        add("还没有最近会话", "No recent chats yet", "最近のチャットはありません");
        add("点右上角“＋”添加角色或创建群聊；单聊会在第一次发送消息后出现在这里。",
                "Tap “+” at the top right to add a character or create a group. Direct chats appear here after the first message.",
                "右上の「＋」からキャラクター追加やグループ作成ができます。個別チャットは最初のメッセージ後に表示されます。");
        add("安静地补充上下文", "Context, only when relevant", "必要な時だけ文脈を補足");
        add("按最近对话关键词自动激活", "Activated by recent conversation keywords", "最近の会話キーワードで自動有効化");
        add("还没有带世界书的角色卡", "No character has a lorebook yet", "世界書付きのキャラクターはまだありません");
        add("已启用", "Enabled", "有効");
        add("本地优先 · 可选 GPT 登录", "Local first · Optional GPT sign-in", "ローカル優先 · GPTログインは任意");
        add("连接与账户", "Connections & accounts", "接続とアカウント");
        add("隐私与存储", "Privacy & storage", "プライバシーと保存");
        add("关于澄语", "About 澄语", "澄语について");
        add("设计说明", "Design notes", "デザインについて");
        add("密钥由 Android Keystore 加密", "Keys are encrypted by Android Keystore", "キーはAndroid Keystoreで暗号化されます");
        add("直连 API、GPT / Claude 账户、人格", "Direct API, GPT / Claude accounts, persona", "直接API、GPT / Claudeアカウント、ペルソナ");
        add("安全", "Secure", "安全");
        add(" · 点击头像可更换", " · Tap the avatar to change it", " · アバターをタップして変更");
        add("聊天信息", "Chat info", "チャット情報");
        add("查找聊天记录", "Search chat history", "チャット履歴を検索");
        add("消息免打扰", "Mute notifications", "通知をミュート");
        add("置顶聊天", "Pin chat", "チャットを固定");
        add("设置当前聊天背景", "Set chat background", "チャット背景を設定");
        add("从相册选择", "Choose from gallery", "ギャラリーから選択");
        add("恢复默认背景", "Restore default background", "標準の背景に戻す");
        add("清空聊天记录", "Clear chat history", "チャット履歴を消去");
        add("聊天背景已更新", "Chat background updated", "チャット背景を更新しました");
        add("已恢复默认背景", "Default background restored", "標準の背景に戻しました");
        add("正在设置聊天背景…", "Setting chat background…", "チャット背景を設定中…");
        add("查找聊天记录", "Search chat history", "チャット履歴を検索");
        add("搜索聊天记录", "Search messages", "メッセージを検索");
        add("输入关键词查找当前会话中的消息", "Enter a keyword to search this chat", "キーワードを入力してこのチャットを検索");
        add("没有找到相关聊天记录", "No matching messages", "一致するメッセージはありません");
        add("找不到这个会话", "Conversation not found", "チャットが見つかりません");
        add("[图片]", "[Image]", "［画像］");
        add("[位置]", "[Location]", "［位置］");
        add("昨天", "Yesterday", "昨日");

        add("发消息", "Message", "メッセージ");
        add("发送消息", "Send a message", "メッセージを送信");
        add("发送", "Send", "送信");
        add("保存", "Save", "保存");
        add("复制", "Copy", "コピー");
        add("编辑", "Edit", "編集");
        add("删除", "Delete", "削除");
        add("重试", "Retry", "再試行");
        add("重新生成", "Regenerate", "再生成");
        add("编辑消息", "Edit message", "メッセージを編集");
        add("已复制", "Copied", "コピーしました");
        add("取消", "Cancel", "キャンセル");
        add("完成", "Done", "完了");
        add("清空", "Clear", "消去");
        add("收到一条新回复", "New reply received", "新しい返信があります");
        add("服务没有返回文字内容", "The service returned no text", "サービスからテキストが返されませんでした");
        add("没有可用的相机应用", "No camera app is available", "利用できるカメラアプリがありません");
        add("正在获取当前位置…", "Getting current location…", "現在地を取得中…");
        add("需要位置权限才能发送当前位置", "Location permission is required to send your location", "現在地を送信するには位置情報の権限が必要です");
        add("请看这张图片", "Please look at this image", "この画像を見てください");
        add("我分享了当前位置", "I shared my current location", "現在地を共有しました");
        add("当前聊天背景", "Current chat background", "現在のチャット背景");
        add("当前群聊背景", "Current group background", "現在のグループ背景");
        add("正在输入…", "Typing…", "入力中…");
        add("发送失败，长按可重试", "Send failed. Touch and hold to retry.", "送信に失敗しました。長押しして再試行できます。");
        add("（已停止）", "(Stopped)", "（停止しました）");
        add("请求失败：", "Request failed: ", "リクエスト失敗：");
        add("请求中断：", "Request interrupted: ", "リクエスト中断：");
        add("聊天消息", "Chat message", "チャットメッセージ");
        add("群聊消息", "Group message", "グループメッセージ");
        add("群成员", "Group member", "グループメンバー");
        add("所有人", "Everyone", "全員");
        add("@ 群成员", "@ Group member", "@ グループメンバー");
        add("提及群成员", "Mention a group member", "グループメンバーをメンション");
        add("选择通话角色", "Choose a call participant", "通話するキャラクターを選択");
        add("这轮没有成员选择回复", "No member chose to reply this round", "今回は返信するメンバーがいませんでした");
        add("群聊不存在", "Group not found", "グループが見つかりません");
        add("找不到群成员，请重新添加角色卡", "Group members were not found. Add the character cards again.", "グループメンバーが見つかりません。キャラクターカードを追加し直してください。");

        add("相册", "Gallery", "ギャラリー");
        add("拍摄", "Camera", "撮影");
        add("位置", "Location", "位置");
        add("语音通话", "Voice call", "音声通話");
        add("返回并挂断", "Back and hang up", "戻って通話を終了");
        add("挂断", "Hang up", "通話終了");
        add("静音", "Mute", "ミュート");
        add("已静音", "Muted", "ミュート中");
        add("扬声器", "Speaker", "スピーカー");
        add("扬声器开", "Speaker on", "スピーカー オン");
        add("正在准备通话…", "Preparing call…", "通話を準備中…");
        add("正在重新连接…", "Reconnecting…", "再接続中…");
        add("正在等待对方回应…", "Waiting for a response…", "応答を待っています…");
        add("允许麦克风后即可开始实时对话", "Allow microphone access to start a realtime conversation", "マイクを許可するとリアルタイム会話を開始できます");
        add("没有麦克风权限", "Microphone permission is missing", "マイクの権限がありません");
        add("前往设置", "Open settings", "設定を開く");
        add("需要配置短期令牌服务", "A short-lived token service is required", "短期トークンサービスの設定が必要です");
        add("通话连接失败", "Call connection failed", "通話の接続に失敗しました");
        add("ChatGPT 登录不能直接用于 Realtime API。", "ChatGPT sign-in cannot be used directly with the Realtime API.", "ChatGPTログインはRealtime APIに直接使用できません。");
        add("语音通话需要麦克风权限。你可以在系统应用设置中重新允许。",
                "Voice calls need microphone access. You can allow it again in system app settings.",
                "音声通話にはマイクの権限が必要です。システムのアプリ設定から再度許可できます。");
        add("请在“连接与账户”中填写你控制的短期令牌服务。",
                "Enter a short-lived token service you control under Connections & accounts.",
                "「接続とアカウント」で管理している短期トークンサービスを設定してください。");

        add("角色资料", "Character profile", "キャラクター情報");
        add("来源：", "Source: ", "提供元：");
        add("应用内置", "Built in", "アプリ内蔵");
        add("兼容角色卡", "Imported character card", "互換キャラクターカード");
        add("本地角色卡", "Local character card", "ローカルキャラクターカード");
        add("世界书：", "Lorebook: ", "世界書：");
        add("最近聊天：", "Last chat: ", "最近のチャット：");
        add("角色描述", "Description", "キャラクター説明");
        add("描述、性格与场景", "Description, personality, and scenario", "説明、性格、シナリオ");
        add("开场白", "First message", "最初のメッセージ");
        add("创作者备注", "Creator notes", "作成者メモ");
        add("查看完整设定", "View full definition", "完全な設定を見る");
        add("更换头像", "Change avatar", "アバターを変更");
        add("删除角色", "Delete character", "キャラクターを削除");
        add("描述", "Description", "説明");
        add("性格", "Personality", "性格");
        add("场景", "Scenario", "シナリオ");
        add("示例对话", "Example dialogue", "会話例");
        add("暂无角色描述", "No description", "説明はありません");
        add("暂无开场白", "No first message", "最初のメッセージはありません");
        add("暂无备注", "No notes", "メモはありません");
        add("未关联", "Not linked", "関連なし");
        add("这个角色还没有描述。", "This character has no description.", "このキャラクターには説明がありません。");
        add("这个角色还没有开场白。", "This character has no first message.", "このキャラクターには最初のメッセージがありません。");
        add("这个角色还没有创作者备注。", "This character has no creator notes.", "このキャラクターには作成者メモがありません。");
        add("这个角色还没有详细设定。", "This character has no detailed definition.", "このキャラクターには詳細設定がありません。");
        add("这个角色没有关联世界书。", "This character has no linked lorebook.", "このキャラクターに関連する世界書はありません。");
        add("当前没有聊天记录", "There is no chat history", "チャット履歴はありません");
        add("聊天记录已清空", "Chat history cleared", "チャット履歴を消去しました");
        add("角色已删除", "Character deleted", "キャラクターを削除しました");
        add("头像已更新", "Avatar updated", "アバターを更新しました");
        add("正在更新头像…", "Updating avatar…", "アバターを更新中…");
        add("角色不存在", "Character not found", "キャラクターが見つかりません");

        add("群聊名称", "Group name", "グループ名");
        add("例如：深夜电台", "Example: Midnight Radio", "例：深夜ラジオ");
        add("还需要一位角色", "One more character is required", "あと1人必要です");
        add("本地角色", "Local character", "ローカルキャラクター");
        add("群聊至少需要两位角色。请从消息页右上角“＋”继续添加角色卡，不需要同步服务器。",
                "A group needs at least two characters. Add another character from “+” on the Messages page. No server sync is required.",
                "グループには2人以上のキャラクターが必要です。「メッセージ」右上の「＋」から追加してください。サーバー同期は不要です。");

        add("连接与账户", "Connections & accounts", "接続とアカウント");
        add("可使用 GPT 兼容、Claude、Gemini 等 API 直连，也可选择已配置的 GPT 或 Claude 账户生成聊天。",
                "Connect directly to GPT-compatible, Claude, or Gemini APIs, or generate chats with a configured GPT or Claude account.",
                "GPT互換、Claude、Gemini APIへ直接接続するか、設定済みのGPT / Claudeアカウントでチャットを生成できます。");
        add("直连 API", "Direct API", "直接API");
        add("多协议", "Multiple protocols", "複数プロトコル");
        add("接口协议", "API protocol", "APIプロトコル");
        add("GPT 兼容接口（默认）", "GPT-compatible API (default)", "GPT互換API（標準）");
        add("Ollama 原生接口", "Native Ollama API", "OllamaネイティブAPI");
        add("自动识别", "Auto detect", "自動判定");
        add("API 地址", "API URL", "API URL");
        add("sk-…（本地服务可留空）", "sk-… (optional for local services)", "sk-…（ローカルサービスは省略可）");
        add("模型", "Model", "モデル");
        add("点按选择或输入模型 ID", "Tap to choose or enter a model ID", "タップしてモデルIDを選択または入力");
        add("刷新列表", "Refresh", "一覧を更新");
        add("等待获取模型列表", "Waiting for model list", "モデル一覧を待っています");
        add("测试 API", "Test API", "APIをテスト");
        add("允许角色联网搜索", "Allow web search", "ウェブ検索を許可");
        add("群聊主动发言", "Spontaneous group messages", "グループで自発的に発言");
        add("群聊空闲时，角色会偶尔自行发起话题",
                "Characters occasionally start a topic while the group is idle",
                "グループが空いている時、キャラクターが時々自分から話題を始めます");
        add("显示模型思考过程", "Show model reasoning", "モデルの思考過程を表示");
        add("默认关闭；过滤 reasoning_content、reasoning 等内部推理字段",
                "Off by default; filters internal fields such as reasoning_content and reasoning.",
                "初期状態はオフ。reasoning_content、reasoningなどの内部推論フィールドを除外します。");
        add("账户", "Account", "アカウント");
        add("账户模型", "Account model", "アカウントモデル");
        add("点按选择账户可用模型", "Tap to choose an available account model", "タップして利用可能なモデルを選択");
        add("登录 GPT 账户", "Sign in to GPT", "GPTにログイン");
        add("重新登录", "Sign in again", "再ログイン");
        add("退出", "Sign out", "ログアウト");
        add("保存 Claude 凭据", "Save Claude credential", "Claude認証情報を保存");
        add("更新 Claude 凭据", "Update Claude credential", "Claude認証情報を更新");
        add("尚未配置 Claude 账户", "Claude account is not configured", "Claudeアカウントは未設定です");
        add("Claude 凭据已保存在本机", "Claude credential is stored on this device", "Claude認証情報は端末に保存されています");
        add("Claude API Key / 访问令牌", "Claude API key / access token", "Claude APIキー / アクセストークン");
        add("聊天生成方式", "Chat generation", "チャット生成方法");
        add("聊天身份", "Chat identity", "チャット上の名前");
        add("我的称呼", "My name", "自分の呼び名");
        add("头像请在“我的”页面点击个人头像更换。",
                "Change your avatar by tapping it on the Me page.",
                "アバターは「自分」ページでタップして変更できます。");
        add("本地密钥保护", "Local credential protection", "ローカル認証情報の保護");
        add("保存设置", "Save settings", "設定を保存");
        add("设置已保存在本机", "Settings saved on this device", "設定を端末に保存しました");
        add("Realtime 语音", "Realtime voice", "Realtime音声");
        add("实验性", "Experimental", "試験機能");
        add("短期令牌服务", "Short-lived token service", "短期トークンサービス");
        add("令牌服务 Bearer", "Token service bearer", "トークンサービスBearer");
        add("可选，仅用于访问你自己的令牌服务", "Optional; only used to access your token service", "任意。自分のトークンサービスへのアクセスにのみ使用");
        add("服务需返回 OpenAI Realtime client secret JSON（含 value 字段）。",
                "The service must return OpenAI Realtime client-secret JSON containing a value field.",
                "サービスはvalueフィールドを含むOpenAI Realtime client secret JSONを返す必要があります。");
        add("在 OpenAI 页面输入此验证码", "Enter this code on the OpenAI page", "OpenAIページでこのコードを入力");
        add("复制并打开 OpenAI", "Copy and open OpenAI", "コピーしてOpenAIを開く");
        add("正在获取验证码…", "Getting code…", "コードを取得中…");
        add("取消登录", "Cancel sign-in", "ログインをキャンセル");
        add("正在连接 OpenAI…", "Connecting to OpenAI…", "OpenAIに接続中…");
        add("等待你在 OpenAI 页面确认", "Waiting for confirmation on the OpenAI page", "OpenAIページでの確認を待っています");
        add("已取消登录", "Sign-in cancelled", "ログインをキャンセルしました");
        add("验证码已复制", "Code copied", "コードをコピーしました");
        add("正在测试…", "Testing…", "テスト中…");
        add("正在获取模型…", "Getting models…", "モデルを取得中…");
        add("填写 API 地址后自动获取", "Models load after an API URL is entered", "API URL入力後に自動取得");
        add("填写 API Key 后自动获取", "Models load after an API key is entered", "APIキー入力後に自動取得");
        add("请先填写 API 地址", "Enter an API URL first", "先にAPI URLを入力してください");
        add("请先填写 API Key", "Enter an API key first", "先にAPIキーを入力してください");
        add("请选择或填写直连 API 模型", "Choose or enter a Direct API model", "直接APIモデルを選択または入力してください");
        add("聊天直接发送到上方 API 地址，使用所选模型。",
                "Chats are sent directly to the API URL above using the selected model.",
                "チャットは選択したモデルで上のAPI URLへ直接送信されます。");
        add("选择接口协议", "Choose API protocol", "APIプロトコルを選択");
        add("移除", "Remove", "削除");
        add("移除 Claude 账户？", "Remove Claude account?", "Claudeアカウントを削除しますか？");
        add("退出 GPT 账户？", "Sign out of GPT?", "GPTからログアウトしますか？");
        add("只会删除本机加密保存的 Claude 凭据。", "Only the encrypted Claude credential on this device will be removed.", "端末に暗号化保存されたClaude認証情報のみ削除します。");
        add("只会删除本机保存的 OAuth 令牌，不会影响 OpenAI 账户。", "Only the OAuth token on this device will be removed. Your OpenAI account is unaffected.", "端末のOAuthトークンのみ削除します。OpenAIアカウントには影響しません。");
        add("已删除本机 Claude 凭据", "Claude credential removed from this device", "端末のClaude認証情報を削除しました");
        add("已删除本机 GPT 登录", "GPT sign-in removed from this device", "端末のGPTログイン情報を削除しました");
        add("Claude 凭据已加密保存在本机", "Claude credential encrypted and saved on this device", "Claude認証情報を暗号化して端末に保存しました");
        add("请输入 Claude API Key 或访问令牌", "Enter a Claude API key or access token", "Claude APIキーまたはアクセストークンを入力してください");

        add("检测到同名角色", "Character name already exists", "同名のキャラクターがあります");
        add("覆盖原角色", "Replace existing", "既存を上書き");
        add("新增角色", "Add as new", "新規追加");
        add("为新角色修改名称", "Rename the new character", "新しいキャラクター名");
        add("角色名称不能与本机已有角色重复。", "Character names must be unique on this device.", "端末内で同じキャラクター名は使用できません。");
        add("输入新的角色名称", "Enter a new character name", "新しいキャラクター名を入力");
        add("新增", "Add", "追加");
        add("请输入角色名称", "Enter a character name", "キャラクター名を入力してください");
        add("该名称已存在，请换一个名称", "That name already exists. Choose another.", "その名前は既に使用されています。別の名前を入力してください。");
        add("正在读取角色卡…", "Reading character card…", "キャラクターカードを読み込み中…");
        add("正在导入角色…", "Importing character…", "キャラクターをインポート中…");
        add("正在覆盖角色…", "Replacing character…", "キャラクターを上書き中…");
        add("正在更换头像…", "Changing avatar…", "アバターを変更中…");
        add("头像已更换", "Avatar changed", "アバターを変更しました");
        add("新角色", "New character", "新しいキャラクター");

        add("当前没有聊天记录", "There is no chat history", "チャット履歴はありません");
        add("角色卡不受影响。", "The character card is not affected.", "キャラクターカードには影響しません。");
        add("本机保存的消息会被删除，角色卡不受影响。",
                "Messages stored on this device will be deleted. The character card is not affected.",
                "端末に保存されたメッセージを削除します。キャラクターカードには影響しません。");
        add("本机保存的群聊消息会被清空，群组与角色卡仍会保留。",
                "Group messages stored on this device will be cleared. The group and character cards remain.",
                "端末のグループメッセージを消去します。グループとキャラクターカードは残ります。");
        add("本机保存的消息会被删除，角色卡和聊天设置不受影响。",
                "Messages stored on this device will be deleted. The character card and chat settings remain.",
                "端末のメッセージを削除します。キャラクターカードとチャット設定は残ります。");
        add("角色卡和单聊记录会从本机删除；包含该角色且不足两人的群聊也会被移除。此操作无法撤销。",
                "The character card and direct-chat history will be deleted. Groups left with fewer than two characters will also be removed. This cannot be undone.",
                "キャラクターカードと個別チャット履歴を削除します。メンバーが2人未満になるグループも削除されます。この操作は元に戻せません。");

        add("无法打开所选文件", "Unable to open the selected file", "選択したファイルを開けません");
        add("无法打开所选图片", "Unable to open the selected image", "選択した画像を開けません");
        add("无法读取所选图片", "Unable to read the selected image", "選択した画像を読み込めません");
        add("所选文件不是有效图片", "The selected file is not a valid image", "選択したファイルは有効な画像ではありません");
        add("图片不能超过 15 MB", "Images cannot exceed 15 MB", "画像は15 MB以下にしてください");
        add("头像图片不能超过 15 MB", "Avatar images cannot exceed 15 MB", "アバター画像は15 MB以下にしてください");
        add("无法创建头像目录", "Unable to create the avatar folder", "アバターフォルダーを作成できません");
        add("无法保存新头像", "Unable to save the new avatar", "新しいアバターを保存できません");
        add("无法替换旧头像", "Unable to replace the old avatar", "以前のアバターを置き換えられません");
        add("头像更新失败", "Avatar update failed", "アバターの更新に失敗しました");
        add("角色名称已存在，请重新命名", "The character name already exists. Rename it.", "キャラクター名が既に存在します。名前を変更してください。");

        add("当前位置", "Current location", "現在地");
        add("用户", "User", "ユーザー");
        add("我", "Me", "自分");
        add("你", "You", "あなた");
        add("还没有角色描述", "No character description yet", "キャラクター説明はまだありません");
        add("开始一段新对话", "Start a new conversation", "新しいチャットを始める");
        add("[置顶] ", "[Pinned] ", "[固定] ");
        add("[免打扰] ", "[Muted] ", "[通知オフ] ");
        add("群聊已创建，发一条消息开始聊天",
                "Group created. Send a message to start chatting.",
                "グループを作成しました。メッセージを送信してチャットを始めましょう。");
        add("本地群聊", "Local group", "ローカルグループ");
        add("更换我的头像", "Change my avatar", "自分のアバターを変更");
        add("0.9.0 · 原生角色聊天客户端",
                "0.9.0 · Native character chat client",
                "0.9.0 · ネイティブキャラクターチャット");
        add("导入 Tavern V2 角色卡时，内嵌的 character_book 会一并保存。每次生成前只选择命中关键词的条目，避免把整本设定塞进提示词。",
                "Embedded character_book data is saved when importing Tavern V2 cards. Before each reply, only entries matching recent keywords are selected.",
                "Tavern V2カードのインポート時に、埋め込みのcharacter_bookも保存します。返信前には最近のキーワードに一致する項目だけを選択します。");
        add("微信式信息架构负责熟悉感；MIUIX 的大标题、分组卡片与圆角负责层级；按下即反馈和可中断弹簧负责触感。所有动效都会尊重系统“移除动画”设置。",
                "A familiar WeChat-style structure is paired with MIUIX titles, grouped cards, rounded hierarchy, immediate press feedback, and interruptible spring motion. System reduced-motion settings are respected.",
                "WeChat風の分かりやすい構成に、MIUIXの大見出し、グループカード、角丸、即時の押下フィードバック、割り込み可能なスプリング動作を組み合わせています。システムの視差効果を減らす設定にも対応します。");
        add("导入失败：", "Import failed: ", "インポート失敗：");
        add("更换失败：", "Change failed: ", "変更失敗：");
        add("连接失败：", "Connection failed: ", "接続失敗：");
        add("登录失败：", "Sign-in failed: ", "ログイン失敗：");
        add("登录失败 · ", "Sign-in failed · ", "ログイン失敗 · ");
        add("获取失败 · ", "Failed to load · ", "取得失敗 · ");
        add("已导入 ", "Imported ", "インポート完了：");
        add("已覆盖 ", "Replaced ", "上書き完了：");
        add("已获取 ", "Loaded ", "取得済み：");
        add("请先配置 ", "Configure ", "先に設定してください：");
        add("配置 ", "Configure ", "設定：");
        add(" 个模型 · 可输入筛选", " models · Type to filter", "件のモデル · 入力して絞り込み");
        add(" 个缓存模型", " cached models", "件のキャッシュ済みモデル");
        add(" 个模型", " models", "件のモデル");
        add(" 条设定", " settings", "件の設定");
        add(" 条可触发设定", " triggerable entries", "件の発動可能な設定");
        add(" 条条目", " entries", "件の項目");
        add(" · 实验性", " · Experimental", " · 試験的");

        KEYS.addAll(EN.keySet());
        KEYS.sort(Comparator.comparingInt(String::length).reversed());
    }

    private L10n() {
    }

    private static void add(String zh, String en, String ja) {
        EN.put(zh, en);
        JA.put(zh, ja);
    }

    public static String tr(Context context, CharSequence value) {
        if (value == null) return "";
        String source = value.toString();
        String language = language(context);
        if (!"en".equals(language) && !"ja".equals(language)) return source;
        Map<String, String> map = "ja".equals(language) ? JA : EN;
        String exact = map.get(source);
        if (exact != null) return exact;
        String dynamic = dynamic(source, "ja".equals(language));
        if (dynamic != null) return dynamic;
        String translated = source;
        for (String key : KEYS) {
            if (translated.contains(key)) {
                translated = translated.replace(
                        key, map.get(key));
            }
        }
        return translated;
    }

    public static CharSequence[] tr(
            Context context, CharSequence[] values) {
        CharSequence[] result = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = tr(context, values[i]);
        }
        return result;
    }

    public static void setRaw(TextView view) {
        if (view instanceof MiuixUi.LocalizedTextView) {
            ((MiuixUi.LocalizedTextView) view).setAutoLocalize(false);
        }
    }

    private static String language(Context context) {
        Configuration configuration =
                context.getResources().getConfiguration();
        Locale locale;
        if (Build.VERSION.SDK_INT >= 24) {
            locale = configuration.getLocales().isEmpty()
                    ? Locale.getDefault() : configuration.getLocales().get(0);
        } else {
            locale = configuration.locale;
        }
        return locale == null ? "" : locale.getLanguage();
    }

    private static String dynamic(String source, boolean japanese) {
        Matcher matcher = MEMBER_COUNT.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? matcher.group(1) + "人のメンバー"
                    : matcher.group(1) + " members";
        }
        matcher = RECENT_COUNT.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? "最近のチャット " + matcher.group(1) + "件"
                    : matcher.group(1) + " recent chats";
        }
        matcher = CHARACTER_BOOK_COUNT.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? matcher.group(1) + "人のキャラクター · 世界書"
                    + matcher.group(2) + "冊"
                    : matcher.group(1) + " characters · "
                    + matcher.group(2) + " lorebooks";
        }
        matcher = PEOPLE_COUNT.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? matcher.group(1) + " · " + matcher.group(2) + "人"
                    : matcher.group(1) + " · " + matcher.group(2) + " people";
        }
        matcher = TYPING.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? matcher.group(1) + "が入力中…"
                    : matcher.group(1) + " is typing…";
        }
        matcher = VIEW_CHARACTER_PROFILE.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? matcher.group(1) + "のキャラクター情報を見る"
                    : "View " + matcher.group(1) + "'s character profile";
        }
        matcher = VIEW_PROFILE.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? matcher.group(1) + "の情報を見る"
                    : "View " + matcher.group(1) + "'s profile";
        }
        matcher = CHANGE_AVATAR.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? matcher.group(1) + "のアバターを変更"
                    : "Change " + matcher.group(1) + "'s avatar";
        }
        matcher = CHAT_WITH.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? matcher.group(1) + "とチャットを開始"
                    : "Start chatting with " + matcher.group(1);
        }
        matcher = CALL_WITH.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? matcher.group(1) + "と音声通話"
                    : "Voice call with " + matcher.group(1);
        }
        matcher = CLEAR_SINGLE.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? matcher.group(1) + "とのチャットを消去しますか？"
                    : "Clear chat with " + matcher.group(1) + "?";
        }
        matcher = CLEAR_NAMED.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? "「" + matcher.group(1) + "」のチャットを消去しますか？"
                    : "Clear “" + matcher.group(1) + "”?";
        }
        matcher = DELETE_CHARACTER.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? "キャラクター「" + matcher.group(1) + "」を削除しますか？"
                    : "Delete “" + matcher.group(1) + "”?";
        }
        matcher = CREATED_GROUP.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? "「" + matcher.group(1) + "」を作成しました"
                    : "Created “" + matcher.group(1) + "”";
        }
        matcher = ENTRY_COUNT.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? matcher.group(1) + "件の項目"
                    : matcher.group(1) + " entries";
        }
        matcher = TRIGGER_COUNT.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? matcher.group(1) + "件の発動可能な設定"
                    : matcher.group(1) + " triggerable entries";
        }
        matcher = SETTING_COUNT.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? matcher.group(1) + "件の設定"
                    : matcher.group(1) + " settings";
        }
        matcher = MODELS_FILTERED.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? matcher.group(1) + "件のモデル · 入力して絞り込み"
                    : matcher.group(1) + " models · Type to filter";
        }
        matcher = CACHED_MODELS.matcher(source);
        if (matcher.matches()) {
            return japanese
                    ? "取得失敗 · キャッシュ済みモデル" + matcher.group(1) + "件を保持"
                    : "Failed to load · Kept " + matcher.group(1) + " cached models";
        }
        return null;
    }
}
