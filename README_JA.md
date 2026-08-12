[简体中文](README.md) · [English](README_EN.md) · **日本語**

# 澄語 KiraChat — Android / iOS 向けネイティブ AI キャラクターチャット

[![KiraChat](https://img.shields.io/badge/KiraChat-v0.9.0-22C55E)](https://github.com/liangbannianikun-cmd/kirachat/releases/tag/v0.9.0)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/iOS-16%2B-000000?logo=apple&logoColor=white)
![Languages](https://img.shields.io/badge/UI-中文%20%7C%20English%20%7C%20日本語-5B8FF9)

**SillyTavern のキャラクターカード、複数のモデル API、ローカルモデル、複数キャラクターのグループチャットを、モバイル向けネイティブアプリにまとめます。**

KiraChat は WebView のラッパーではありません。Android はネイティブ View、iOS は SwiftUI で実装されています。会話、キャラクター詳細、世界設定、グループ、音声、接続設定を、WeChat に近い分かりやすい構成でタッチ操作向けに再設計しています。

このリポジトリでは、SillyTavern のキャラクターカードを Codex、Claude Code、Hermes、OpenClaw、OpenCode に安全に適用する Windows ツール **[Kira Switch](#kira-switch--ai-コーディングツール向けキャラクターカード適用ツール)** も公開しています。

## 解決すること

| よくある課題 | KiraChat のアプローチ |
| --- | --- |
| Web のキャラクターチャットはスマートフォンで操作しづらい | Android / iOS のネイティブ画面、モバイルジェスチャー、WeChat 風メッセージ UI |
| キャラクターカード、モデル、プロバイダーが複数のツールに分散する | SillyTavern V1/V2/V3 JSON と PNG `chara` / `ccv3` を取り込み、GPT、Claude、Gemini、互換サービスへ一か所から接続 |
| 会話を端末内だけで処理したい | Android で Qwen3.5-0.8B / 2B をダウンロードして実行し、ローカル画像認識にも対応 |
| 一般的なチャットアプリでは複数キャラクターを扱えない | メンション、関連度判定、並列生成、複合アイコンを備えたローカルグループ |
| 端末間でキャラクターと会話を移すのが難しい | ローカルのバックアップ／復元、またはセルフホスト型のクライアント側暗号化同期 |

## 今すぐインストール

| プラットフォーム | ダウンロード | インストール方法 |
| --- | --- | --- |
| Android 8.0 以降 | **[APK をダウンロード](https://github.com/liangbannianikun-cmd/kirachat/releases/download/v0.9.0/KiraChat-0.9.0-android-debug.apk)** | 現在はデバッグ署名のテストビルドです。「提供元不明のアプリ」のインストールを許可して APK を開いてください。 |
| iOS 16 以降 | **[未署名 IPA をダウンロード](https://github.com/liangbannianikun-cmd/kirachat/releases/download/v0.9.0/KiraChat-0.9.0-ios-unsigned.ipa)** | インストール前に、ご自身の証明書または署名ツールで署名してください。 |

パッケージとチェックサムは **[KiraChat v0.9.0 Release](https://github.com/liangbannianikun-cmd/kirachat/releases/tag/v0.9.0)** で公開しています。既存のインストールを置き換える前に、**マイ → バックアップと復元** からバックアップを書き出してください。

### 3 ステップでチャットを開始

1. **マイ → 接続とアカウント** を開きます。互換 API、アカウントログイン、または Android のローカル Qwen モデルを設定します。
2. **メッセージ** に戻り、右上の `+` から JSON / PNG キャラクターカードを取り込みます。
3. キャラクターを開いてメッセージを送信します。ローカルキャラクターが 2 人以上いれば、ローカルグループも作成できます。

> [!IMPORTANT]
> GPT、Claude、Gemini、Copilot などのクラウドサービスにおけるアカウント、割り当て、モデル、地域別提供状況は各プロバイダーが管理します。iOS 版には、Android 版のネイティブ Realtime WebSocket プロバイダーとローカル GGUF 推論はまだ含まれていません。下の対応表をご確認ください。

## 主な機能

- **キャラクターカード互換:** SillyTavern / Character Card V1–V3 JSON、PNG `chara` / `ccv3`、マクロ、内蔵世界設定。
- **複数のモデル経路:** GPT 互換 API、OpenAI Responses、Claude Messages、Gemini、Azure OpenAI、Ollama、GPT および GitHub Copilot のアカウントモード。
- **ローカル・マルチモーダル:** Android の Qwen3.5 ローカル推論、画像認識、アルバム、カメラ、位置情報、Web 検索。
- **ネイティブグループチャット:** SillyTavern サーバーなしでグループを作成し、メンション、ランダム／並列返信、キャラクターの自発メッセージに対応。
- **モバイル体験:** WeChat 風メッセージと複合アイコン、MIUIX 風デザイン、ダークモード、3 言語 UI、システム通知。
- **データ管理:** ローカル保存、バックアップ／復元、セルフホスト型暗号化同期。通常のバックアップには API キーと OAuth トークンを含みません。

## プラットフォーム対応

| 機能 | Android | iOS |
| --- | :---: | :---: |
| ネイティブ UI、キャラクターカード、世界設定、グループ | ✅ | ✅ |
| GPT / Claude / Gemini 直接 API | ✅ | ✅ |
| GPT / GitHub Copilot アカウント | ✅ | ✅ |
| 画像、カメラ、位置情報、Web 検索 | ✅ | ✅ |
| Qwen3.5 GGUF ローカル推論 | ✅ | — |
| 複数プロバイダー対応 Realtime 音声 | ✅ | — |
| システム音声入力 + 現在のモデル + TTS 音声チャット | ✅ | ✅ |
| 暗号化サーバー同期 | ✅ | ✅ |

## Kira Switch — AI コーディングツール向けキャラクターカード適用ツール

**Kira Switch は KiraChat ファミリーの Windows デスクトップ用キャラクターコンソールです。** SillyTavern のキャラクターカードを 1 枚取り込み、5 種類のプロンプトファイルを手作業で探して編集することなく、Codex、Claude Code、Hermes、OpenClaw、OpenCode で同じキャラクターを利用できます。

[![Kira Switch](https://img.shields.io/badge/Kira%20Switch-v1.0.0-7C3AED)](https://github.com/liangbannianikun-cmd/kirachat/releases/tag/kira-switch-v1.0.0)
![Windows](https://img.shields.io/badge/Windows-10%2F11-0078D4?logo=windows&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)

### 30 秒で導入

1. **[Kira Switch 1.0.0 Windows Portable](https://github.com/liangbannianikun-cmd/kirachat/releases/download/kira-switch-v1.0.0/Kira-Switch-1.0.0-Windows-Portable.exe)** をダウンロードします。
2. 起動して、JSON、PNG、または CHARX キャラクターカードを取り込みます。
3. 対象クライアントを選び、生成された指示を確認して適用します。キャラクターを読み込むには、クライアントで新しいセッションを開始してください。

Node.js のインストールや API キーは不要です。現在のビルドには商用コード署名がありません。Windows SmartScreen が表示された場合は、ダウンロード URL と GitHub 上のビルド元を確認してください。

![Kira Switch メイン画面](https://raw.githubusercontent.com/liangbannianikun-cmd/kirachat/kira-switch/kira-switch/docs/kira-switch-preview.png)

### 対応内容

- Character Card V1/V2/V3 の `.json`、`chara` / `ccv3` メタデータ入り `.png`、`card.json` を含む `.charx` を取り込みます。
- 説明、性格、場面、最初のメッセージ、会話例、システムプロンプト、別の挨拶、世界設定を解析し、一般的なキャラクター／ユーザーマクロを置き換えます。
- 短縮・標準・完全の 3 種類のプロンプト長を用意し、5 クライアントを個別に有効化、無効化、切り替えできます。
- 管理対象の `KIRA-SWITCH` ブロックだけを変更し、組み込みプロンプト、既存ルール、無関係なファイル内容を保持します。
- 書き込み前に毎回自動バックアップし、履歴から有効化前のスナップショットを復元できます。カスタム適用先にも対応します。
- モデル、資格情報、API キー、ネットワークプロキシ、ツール権限は変更しません。第三者製カードは適用前に内容を確認してください。

| クライアント | 既定ファイル | 既定の適用範囲 |
| --- | --- | --- |
| Codex | `~/.codex/AGENTS.md` | ローカルの全 Codex タスク |
| Claude Code | `~/.claude/CLAUDE.md` | ユーザー全体 |
| Hermes | `~/.hermes/SOUL.md` | Hermes のメイン人格 |
| OpenClaw | `~/.openclaw/workspace/SOUL.md` | 既定ワークスペースの人格 |
| OpenCode | `~/.config/opencode/AGENTS.md` | ユーザー全体 |

Kira Switch と KiraChat は SillyTavern Character Card の互換方針を共有しますが、Kira Switch は独立したデスクトップツールです。**KiraChat の会話、アカウント、キーは同期しません。** ソースと開発ドキュメントは [`kira-switch` ブランチ](https://github.com/liangbannianikun-cmd/kirachat/tree/kira-switch/kira-switch)、バイナリは [Kira Switch v1.0.0 Release](https://github.com/liangbannianikun-cmd/kirachat/releases/tag/kira-switch-v1.0.0) にあります。

## ドキュメント

- [セットアップ詳細](#セットアップ詳細)
- [サーバー同期](#サーバー同期)
- [アカウントに関する注意](#アカウントに関する注意)
- [Realtime 音声](#realtime-音声)
- [直接 API の互換性](#直接-api-の互換性)
- [ローカル Qwen3.5 モデル](#ローカル-qwen35-モデル)
- [ビルド](#ビルド)
- [データとプライバシー](#データとプライバシー)
- [デザインノート](DESIGN.md)

<details>
<summary><strong>全機能一覧</strong></summary>

### UI と会話

- メッセージ、キャラクター、世界設定、マイの 4 タブを、フローティング Dock で表示する WeChat 風の情報設計。
- MIUIX 風の大見出し、角丸グループカード、控えめな階層、ダークモード、即時の押下フィードバック、中断可能なスプリング、モーション低減への対応。
- WeChat 風の個人／グループ吹き出し、1～9 人の複合グループアイコン、メッセージ検索、通知ミュート、ピン留め、チャット別背景、通知、長押し操作。
- 返信生成中もテキスト、画像、位置情報を続けて送信できます。個人チャットはターンを待ち行列に入れ、グループは最新の文脈を再評価します。
- 簡体字中国語、英語、日本語の UI。

### キャラクター、カード、世界設定

- Tavern / Character Card V1/V2/V3 JSON、PNG `chara` / `ccv3`、最初のメッセージ、説明、性格、場面、会話例、作者メモ、キャラクター／ユーザーマクロ。
- 内蔵 `character_book` は最大 1,000,000 件のエントリーを扱い、リクエストごとの安全なコンテキスト予算内で関連項目を選択します。
- 同名カードは上書き、または重複しない新しい名前で取り込めます。会話履歴、グループ参加、ミュート／ピン留め、背景を失わずにカードとアイコンを差し替えられます。
- キャラクターのアイコンから詳細ページを開けます。キャラクターとユーザー双方のアイコンを変更できます。
- 接続、モデル、カード、グループ、音声機能を案内する組み込みガイド「豆乃 GPT」を収録しています。

### モデル、メディア、検索

- GPT 互換 Chat Completions / Responses、Claude Messages、Gemini GenerateContent、Azure OpenAI、Ollama、一般的な SSE / JSON / JSONL 応答、自動モデル取得とキャッシュフォールバック。
- GPT デバイス認証と、同梱 Copilot SDK ゲートウェイを利用する GitHub Copilot OAuth Device Flow。
- 推論過程は既定で非表示。設定から表示を有効にできます。
- Web 検索は既定で有効です。プロバイダー標準の検索を優先し、明示的に非対応の場合は KiraChat の出典付き検索へフォールバックします。
- アルバム、カメラ、画像圧縮、マルチモーダル入力、位置情報カード、`image_url` 非対応時に `[画像]` へ切り替える互換処理。
- Android は検証済み Qwen3.5-0.8B / 2B GGUF と対応する vision projector をダウンロードし、Vulkan GPU オフロードを優先して必要時に安全にフォールバックします。

### グループ、音声、データ

- ローカルグループに外部 SillyTavern サーバーは不要です。メンバーは関連度を並列判定し、ランダム順に返信します。`@キャラクター` / `@everyone` と、任意で自発メッセージに対応します。
- 個人チャットでも、キャラクターが時々自発的に発言する設定を利用できます。
- Android はネイティブ／アダプター型 Realtime 音声に対応し、通話時間を吹き出しに保存します。iOS は音声入力 + 現在のチャットモデル + システム TTS を利用します。
- バックアップ／復元の対象はキャラクター、グループ、メッセージ、世界設定、アイコン、背景、通常設定です。資格情報は除外します。
- セルフホスト型暗号化同期はクライアント側 AES-GCM と明示的な競合解決を Android / iOS で提供します。
- API キー、GPT / Copilot OAuth トークン、Realtime 資格情報はプラットフォームの安全なストレージを使用します。

</details>

## セットアップ詳細

1. APK、または署名済み IPA をインストールします。
2. **マイ → 接続とアカウント** で API のルート URL または完全なリクエスト URL と API キーを入力します。既定のプロトコルは GPT 互換です。Responses、Claude Messages、Gemini GenerateContent、Azure OpenAI、Ollama ネイティブ、自動判定も選べます。モデル一覧は自動更新されますが、モデル ID の手入力も可能です。認証不要のローカルエンドポイントではキーを空欄にできます。
3. 生成経路を選びます。

   - **直接 API:** 設定した GPT 互換、Claude、Gemini サービスに直接リクエストします。
   - **アカウント → GPT:** GPT ログインを開始し、デバイスコードをコピーして公式 `auth.openai.com` ページで認証します。認証後に利用可能な Codex モデルを取得します。
   - **アカウント → GitHub Copilot:** Device Flow を有効にした GitHub OAuth App の Client ID と、デプロイ済み Copilot SDK ゲートウェイの URL を入力し、GitHub 上でデバイスコードを承認します。
   - **ローカル（Android）:** ローカルモデルカードから Qwen3.5-0.8B または Qwen3.5-2B をダウンロードして検証し、**使用** をタップします。ローカル推論には Android 9 以降の arm64 端末が必要です。

4. メッセージ画面で `+` → **キャラクターを追加** を選び、JSON / PNG カードを取り込みます。
5. キャラクターが 2 人以上いれば、`+` → **グループを作成** からメンバーを選択します。
6. キャラクター詳細を開いて **メッセージ** をタップします。チャットの `+` メニューには、アルバム、カメラ、音声通話、位置情報があります。メッセージを長押しすると、編集、コピー、削除、再試行、再生成ができます。
7. キャラクター／ユーザーのアイコンをタップすると差し替えられます。右上のメニューから検索、通知ミュート、ピン留め、チャット背景を設定できます。
8. 複数端末で同期する場合は [`sync-server`](sync-server/README.md) をデプロイし、**マイ → サーバー同期** で URL、トークン、別途用意した暗号化パスワードを入力します。自動同期を有効にする前に、最初の端末からアップロードし、ほかの端末でダウンロードしてください。

KiraChat はローカルネットワーク内の HTTP エンドポイントにも接続できます。HTTP は信頼できる LAN 内だけで使用し、公開エンドポイントには HTTPS を使用してください。

## サーバー同期

リポジトリには、実行時に外部依存を必要としない Node.js 同期サービスが含まれます。

```bash
cd sync-server
export SYNC_TOKENS="replace-with-a-random-token-of-at-least-24-characters"
npm start
```

公開環境では HTTPS のリバースプロキシを使用してください。同期トークンはサーバーへのアクセスを制御し、エンドツーエンド暗号化のパスワードはクライアント内に保持されます。サーバーが保存するのは暗号文、リビジョン、更新時刻、端末種別だけです。

同期対象はキャラクター、グループ、メッセージ、世界設定、アイコン、背景、ユーザー名、通常の機能設定です。API キー、GPT / Copilot トークン、Realtime 資格情報、ローカルモデルファイルは同期しません。同時編集が発生した場合は条件付き書き込みを停止し、データを暗黙に上書きせず、ローカル状態のアップロードかサーバー状態のダウンロードを選ぶよう案内します。

## アカウントに関する注意

- **GPT:** KiraChat が ChatGPT のパスワードを表示または読み取ることはありません。認証は OpenAI の公式ページで行われます。このモードは一般的な OpenAI API キーではなく ChatGPT Codex バックエンドを利用します。プラン、割り当て、モデル利用権、提供状況は OpenAI が管理します。
- デバイスフローは Hermes Agent の OpenAI Codex ログインと互換ですが、KiraChat は独自の暗号化資格情報を保存し、Hermes / Codex の資格情報を読み取ったり上書きしたりしません。
- KiraChat は互換性のあるアカウントヘッダーを送信し、403 応答時にはブラウザーの User-Agent で再試行します。Cloudflare のブラウザーチャレンジには、公式 JavaScript 検証を完了できるネットワーク／ブラウザーが必要です。
- これは実験的な第三者互換機能です。認証方式やバックエンドの変更により、KiraChat の更新が必要になる場合があります。
- **GitHub Copilot:** GitHub OAuth Device Flow を利用し、GitHub パスワードの入力は求めません。Android 上では Copilot CLI をホストできないため、モデル取得とチャットには GitHub Copilot SDK を使った同梱の [`copilot-gateway`](copilot-gateway/README.md) を利用します。各ユーザーが自身の OAuth トークンと Copilot サブスクリプションを用意します。Client Secret は APK に同梱しません。

## Realtime 音声

**マイ → 接続とアカウント → Realtime 音声** では、プロバイダー別の設定と資格情報を Android Keystore で暗号化して保存します。

- **ネイティブ接続:** Qwen3.5 Omni Realtime、GLM-Realtime、Baidu Realtime、OpenAI Realtime、Gemini Live、xAI Voice Agent、ElevenLabs ElevenAgents。
- **Realtime アダプター:** Doubao / Volcengine S2S-O・S2S-SC、TRTC AI Conversation、MiniMax Speech 2.8 + M2.7、Amazon Nova 2 Sonic、Mistral Voxtral Realtime + LLM + Voxtral TTS。

モデル一覧は設定した各プロバイダーから更新します。取得できない場合は直前のキャッシュまたは互換リストを残し、モデル ID の手入力も可能です。

ネイティブ接続の資格情報は端末内に保持されますが、失効可能かつレート制限されたものを使用してください。TRTC UserSig、Bedrock SigV4、Volcengine SecretKey などのクラウド秘密情報はバックエンドに保持する必要があるため、KiraChat はそれらを入力させず、設定済み Realtime アダプターの WSS に接続します。

アダプターには最初に次の内容を送信します。

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

その後、KiraChat は Base64 PCM を `input_audio.append` で送信します。アダプターは `audio.delta`、`transcript.delta`、`transcript.done`、`state`、`response.done`、`error`、またはバイナリ PCM フレームを返します。公開環境では WSS が必須です。通話画面を離れる、または終了すると、マイクと接続を直ちに解放します。

## 直接 API の互換性

GPT 互換サービスは、少なくとも次のどちらかの生成経路を提供する必要があります。

- `POST /v1/chat/completions`
- `POST /v1/responses`

モデル取得では、適切な `/v1/models`、`/models`、バージョン付きパスを順に試します。一覧取得に失敗してもモデル ID を手入力できます。Chat は `model`、`messages`、`stream`、Responses は `model`、`input`、`stream` を受け取ります。多くのサービスは Bearer トークンを使い、Azure OpenAI ホストでは自動的に `api-key` を使用します。

Claude ネイティブモードは `POST /v1/messages` と `GET /v1/models`、Gemini ネイティブモードは `POST /v1beta/models/{model}:streamGenerateContent?alt=sse` と `GET /v1beta/models` を使用します。どちらもテキスト／画像入力に対応し、Web 検索が有効な場合は各プロバイダーのネイティブ検索ツールを宣言します。

エンドポイント候補、完全 URL、Chat / Responses の区別には [CC Switch](https://github.com/farion1231/cc-switch) の考え方を参考にしています。ただし、互換処理は KiraChat クライアント内に実装され、デスクトッププロキシは不要です。

## ローカル Qwen3.5 モデル

**マイ → 接続とアカウント → ローカルモデル** では、上流の特定コミットに固定し、サイズと SHA-256 で検証する 2 種類の GGUF パッケージを提供します。

- **Qwen3.5-0.8B Q4_0:** 563,036,064 バイトのモデルと 204,987,232 バイトの `mmproj-F16.gguf` vision projector。必要メモリが少なく高速です。
- **Qwen3.5-2B Q4_K_M:** 1,280,835,840 バイトのモデルと 668,227,264 バイトの vision projector。より多くのメモリを使う代わりに品質が向上します。

ダウンロード先はアプリ専用外部ストレージです。再開用の `.part` ファイルを保持し、完全な検証が終わるまで使用可能にしません。アプリをアンインストールすると、アプリ固有データと一緒にモデルも削除されます。

推論には [llama.cpp](https://github.com/ggml-org/llama.cpp) `b10202` の Android arm64 Vulkan ビルドを使用します。GPU レイヤーオフロードを優先し、出力開始前に GPU 起動が失敗した場合だけ汎用 ARM CPU へフォールバックします。ランタイムファイルと MIT Notice は `third_party/llama.cpp` にあります。

ローカル推論のコンテキストは 4096 トークンで、単一プロセスのメモリ保護を使用します。最新画像は対応する vision projector でエンコードします。Web 検索が有効な場合、ローカルモデルでは KiraChat の出典付き検索フォールバックを使います。複数のローカルグループ返信は、複数プロセスによるメモリ不足を避けるため待ち行列に入ります。最初の返信には GGUF の読み込み時間が含まれるため、2 回目以降より時間がかかります。

## ビルド

### iOS

iOS プロジェクトは `ios/` にあり、SwiftUI、Keychain、PhotosUI、Core Location、Speech、AVFoundation を使用し、iOS 16 以降が必要です。バージョンは `0.9.0 (9)`、Bundle ID は `app.miuix.tavern` です。

macOS、Xcode、XcodeGen を用意して実行します。

```bash
cp app/src/main/res/mipmap-nodpi/app_icon.png ios/KiraChat/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png
cp app/src/main/res/raw/dounai_gpt.png ios/KiraChat/Resources/Assets.xcassets/Dounai.imageset/dounai_gpt.png
cd ios
xcodegen generate
xcodebuild -project KiraChat.xcodeproj -scheme KiraChat -sdk iphoneos -destination 'generic/platform=iOS' build
```

`.github/workflows/ios-ipa.yml` は GitHub の macOS runner でプロジェクトを生成し、Android のアプリアイコンと豆乃 GPT のアイコンを再利用して端末用アプリをビルドし、`KiraChat-0.9.0-unsigned.ipa` を作成します。未署名のため、インストールまたは配布には Apple Developer Team、証明書、Provisioning Profile で再署名する必要があります。

### Android

必要な環境：

- JDK 11
- Gradle 6.7.1
- Android SDK Platform 29
- Android Build Tools 29.0.3

ビルド：

```powershell
.\gradlew.bat :app:assembleDebug
```

出力先：`app\build\outputs\apk\debug\app-debug.apk`

パッケージ名は `app.miuix.tavern`、最小 Android 8.0（API 26）、ターゲット Android 10（API 29）です。ローカルモデルには Android 9 以降の arm64 端末が必要です。

## データとプライバシー

- キャラクターカード、会話、通常設定はアプリの非公開ストレージに保存します。
- GPT / GitHub OAuth トークンと直接 API キーは通常の JSON 設定から除外し、平文の代替領域には保存しません。
- Realtime 資格情報はプロバイダーごとに分離し、Android Keystore で暗号化します。
- 差し替えたキャラクター／ユーザーアイコンとチャット画像は、アルバムへの広範なアクセスを保持せず非公開ストレージへコピーします。
- 画像はメッセージを送信したときだけ、現在設定中のモデルサービスへ送信します。
- 位置情報権限はユーザーが「位置情報」をタップしたときだけ要求し、バックグラウンドで継続追跡しません。
- 会話や暗号化値が OS によって移行されることを防ぐため、Android のシステムバックアップを無効にしています。
- 広告とアクセス解析は含まれていません。

## プロジェクトの状態

KiraChat はインストール、モデル接続、会話が可能なネイティブクライアントです。操作とビジュアルの設計方針は [DESIGN.md](DESIGN.md) を参照してください。端末上の挙動や第三者サービスの利用可否は、ハードウェア、プロバイダー、アカウント、ネットワークによって異なる場合があります。
