# Security Policy / 安全策略

## Reporting a vulnerability / 报告安全问题

Please do not open a public issue for an exploitable vulnerability, leaked credential, authentication bypass, private-data exposure, or remotely reachable server problem.

请勿使用公开 Issue 报告可直接利用的漏洞、凭据泄露、认证绕过、私人数据暴露或可远程攻击的服务器问题。

Use a [private GitHub security advisory](https://github.com/liangbannianikun-cmd/kirachat/security/advisories/new) and include:

- Affected KiraChat version, platform, and component.
- Reproduction steps or a minimal proof of concept.
- Expected impact and whether the issue is remotely reachable.
- Any suggested mitigation.

请通过 [GitHub 私有安全报告](https://github.com/liangbannianikun-cmd/kirachat/security/advisories/new) 提交受影响版本、平台、复现步骤、影响范围和建议缓解方式。

Never include live API keys, OAuth tokens, account cookies, signing certificates, private conversations, or personal information. Replace them with clearly marked test values.

请勿上传真实 API Key、OAuth Token、账户 Cookie、签名证书、私人聊天或个人信息，请统一替换为明确的测试值。

## Supported version / 支持版本

Security fixes currently target the latest published KiraChat release and the current `main` branch. Older test builds may not receive separate patches.

安全修复目前面向最新公开版本与 `main` 分支，较早的测试构建不保证单独维护。
