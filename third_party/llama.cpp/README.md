# llama.cpp Android runtime

澄语内置基于 `llama.cpp` `b10202`（提交 `1553725965470086976c8a06f31467970c9b2702`）构建的 Android arm64 本地推理程序。

- 上游仓库：https://github.com/ggml-org/llama.cpp
- 构建目标：Android API 28、arm64-v8a
- GPU 后端：Vulkan，应用会优先把全部可用模型层卸载到 GPU
- CPU 后端：通用 ARM，仅在 Vulkan/GPU 启动失败且尚未生成内容时回退
- 多模态后端：MTMD，支持本地图片与匹配的 `mmproj` 视觉投影文件
- 构建方式：NDK r28c、`GGML_VULKAN=ON`、`GGML_OPENMP=OFF`、静态链接 llama.cpp/ggml/MTMD 后端
- 系统动态依赖：`libvulkan.so`、`libm.so`、`libdl.so`、`libc.so`
- 许可证：MIT，见同目录 `LICENSE`
- APK 内运行文件校验值：见同目录 `SHA256SUMS`

运行程序面向 Android API 28，因此应用仍保留原有 `minSdk 26`，但本地模型功能只会在 Android 9 或更高版本的 arm64 设备上启用。云端 API 和账户模式不受影响。

Qwen 本地推理会把 system/user 作为两条原生消息交给 `llama cli`，并使用精简 ChatML Jinja 模板。模板保留 Qwen3.5 非思考模式所需的空 `<think></think>` 助手预填，但移除了 GGUF 完整模板中会在空消息探测阶段抛异常的工具分支；运行参数同时启用 `--single-turn` 和 `--skip-chat-parsing`。图片会先在设备上缩放并转成临时 JPEG，再通过 MTMD 和模型对应的视觉投影文件编码。应用只显示清洗后的最终正文，不会把思考标签或 `[end of text]` 标记写入聊天气泡。运行时仍优先 Vulkan/GPU；只有在 GPU 启动失败且尚未生成内容时才回退 CPU。
