# 澄语 GitHub Copilot SDK 网关

安卓不能直接运行 GitHub Copilot CLI，因此账户模式通过这个 HTTPS 网关调用 GitHub 官方 Copilot SDK。每位用户仍使用自己的 GitHub OAuth 令牌和 Copilot 订阅；网关不应记录 `Authorization` 请求头。

## 配置

1. 在 GitHub Developer settings 创建 OAuth App，启用 **Device Flow**，记下 Client ID。Client Secret 不要放入安卓应用。
2. 使用 Node.js 20.19+，在此目录运行 `npm install` 和 `npm start`。
3. 通过反向代理为服务配置 HTTPS，只公开 `/health` 与 `/v1/*`。
4. 在澄语的“连接与账户 → GitHub Copilot”中填写 OAuth Client ID 和网关地址，例如 `https://copilot.example.com/v1`，然后登录。

模型目录来自 SDK 的 `listModels()`。聊天接口采用 OpenAI 兼容的 `/v1/chat/completions`，但模型调用由 Copilot SDK 完成；用户需要可用的 GitHub Copilot 订阅。
