# Contributing to KiraChat / 参与贡献

Thank you for helping improve KiraChat. Small, focused pull requests with clear reproduction steps are the easiest to review.

感谢你帮助改进澄语。范围清晰、带复现步骤的小型 Pull Request 最容易审阅和合并。

## Before you start / 开始之前

- Search existing issues before opening a new one.
- Open an issue before a large UI rewrite, protocol change, data migration, or new provider integration.
- Never commit API keys, OAuth tokens, account cookies, private chat data, real-person avatars, signing certificates, or provisioning profiles.
- Keep Android 8.0+ and iOS 16+ compatibility unless an accepted issue explicitly changes the baseline.
- Do not change version `0.9.0 (9)` unless the work is explicitly part of a release.

- 提交前请先搜索已有 Issue。
- 大型界面重做、协议调整、数据迁移和新厂商接入，应先创建 Issue 对齐范围。
- 不要提交 API Key、OAuth Token、账户 Cookie、私人聊天数据、真人头像、签名证书或 Provisioning Profile。
- 除非已接受的 Issue 明确调整基线，否则请保持 Android 8.0+ 与 iOS 16+ 兼容。
- 除非任务明确属于发版工作，否则不要修改版本号 `0.9.0 (9)`。

## Reporting bugs / 报告问题

Use the bug-report template and include:

- KiraChat version and installation source.
- Platform, OS version, device architecture, and available memory when relevant.
- Connection mode and provider protocol, but redact endpoints or account details if they are private.
- Exact reproduction steps, expected behavior, and actual behavior.
- The smallest useful log excerpt with all secrets and personal data removed.

请使用 Bug 模板，并提供版本与安装来源、平台与系统、连接模式、完整复现步骤、预期结果和实际结果。日志只保留必要片段，并删除密钥、令牌、私人地址与聊天内容。

## Local checks / 本地检查

### Android

Requirements: JDK 11, Gradle 6.7.1, Android SDK Platform 29, and Android Build Tools 29.0.3.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

### iOS

The iOS project requires macOS, Xcode, XcodeGen, and an iOS 16+ toolchain.

```bash
cd ios
xcodegen generate
xcodebuild -project KiraChat.xcodeproj -scheme KiraChat -sdk iphonesimulator build
```

If you cannot run one platform locally, state that clearly in the pull request. A successful build is not proof of device, provider, account, microphone, camera, or two-device sync behavior.

如果本地无法验证某个平台，请在 PR 中明确说明。编译成功不等于已经验证真机、厂商账户、麦克风、相机或双设备同步。

## Pull requests / Pull Request 要求

1. Keep the change focused and avoid unrelated formatting or generated-file churn.
2. Explain the user-visible outcome before implementation details.
3. List the checks you ran and the behavior you could not verify.
4. Add or update translations for Simplified Chinese, English, and Japanese when changing user-visible text.
5. Preserve existing user data and migration compatibility.
6. Include screenshots for visible UI changes, with personal information removed.
7. Confirm that no credentials or private content are included.

提交 PR 前，请保持改动聚焦，说明用户可见结果、已运行检查和未验证边界；用户界面文字需要同步更新简体中文、English、日本語。涉及数据结构时必须保留迁移兼容性。

## Security / 安全问题

Do not publish exploitable security reports or live credentials in a public issue. Follow [SECURITY.md](.github/SECURITY.md) and use a private security advisory when possible.

请勿在公开 Issue 中发布可直接利用的安全细节或真实凭据。请按照 [SECURITY.md](.github/SECURITY.md) 使用私有安全报告渠道。
