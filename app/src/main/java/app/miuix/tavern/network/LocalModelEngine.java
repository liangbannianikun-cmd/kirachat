package app.miuix.tavern.network;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import app.miuix.tavern.model.AppConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Runs one-shot Qwen generation through the bundled arm64 llama.cpp executable. */
final class LocalModelEngine {
    private static final Semaphore INFERENCE_SLOT = new Semaphore(1, true);
    private static final String QWEN_CHAT_TEMPLATE =
            "{%- for message in messages %}\n"
                    + "{{- '<|im_start|>' + message.role + '\\n' "
                    + "+ message.content + '<|im_end|>\\n' }}\n"
                    + "{%- endfor %}\n"
                    + "{%- if add_generation_prompt %}\n"
                    + "{{- '<|im_start|>assistant\\n<think>\\n\\n</think>\\n\\n' }}\n"
                    + "{%- endif %}";
    // A conservative character cap keeps CJK-heavy prompts inside the 4096-token context.
    private static final int MAX_SYSTEM_CHARS = 3200;
    private static final int MAX_USER_CHARS = 700;
    private static final int GPU_LAYERS = 999;

    private LocalModelEngine() {
    }

    static void generate(
            Context context,
            ApiClient.Call call,
            AppConfig config,
            JSONArray messages,
            ApiClient.StreamCallback callback) throws Exception {
        if (!LocalModelManager.isRuntimeSupported()) {
            throw new IOException(LocalModelManager.runtimeSupportMessage());
        }
        LocalModelManager manager = LocalModelManager.get(context);
        LocalModelManager.ModelSpec model = LocalModelManager.find(config.localModel);
        File modelFile = manager.installedFile(model.id);
        if (modelFile == null) {
            throw new IOException("请先在“连接与账户 → 本地模型”下载并启用 "
                    + model.displayName);
        }

        boolean acquired = false;
        while (!call.isCancelled()) {
            if (INFERENCE_SLOT.tryAcquire(200, TimeUnit.MILLISECONDS)) {
                acquired = true;
                break;
            }
        }
        if (!acquired || call.isCancelled()) return;

        File promptDirectory = new File(context.getCacheDir(), "local-model-prompts");
        File systemFile = null;
        File userFile = null;
        File templateFile = null;
        File imageFile = null;
        try {
            if (!promptDirectory.isDirectory() && !promptDirectory.mkdirs()) {
                throw new IOException("无法创建本地模型临时目录");
            }
            PromptParts prompt = buildPrompt(messages);
            String token = UUID.randomUUID().toString();
            systemFile = new File(promptDirectory, token + "-system.txt");
            userFile = new File(promptDirectory, token + "-user.txt");
            templateFile = new File(promptDirectory, token + "-qwen-chat.jinja");
            writeUtf8(systemFile, prompt.system);
            writeUtf8(userFile, prompt.user);
            writeUtf8(templateFile, QWEN_CHAT_TEMPLATE);
            imageFile = extractLatestImage(messages, promptDirectory, token);
            File visionFile = imageFile == null
                    ? null : manager.installedVisionFile(model.id);
            if (imageFile != null && visionFile == null) {
                throw new IOException("请在“连接与账户 → 本地模型”继续下载 "
                        + model.displayName + " 的视觉组件后再发送图片");
            }

            File nativeDirectory = new File(
                    context.getApplicationInfo().nativeLibraryDir);
            File runner = new File(nativeDirectory, "libqwen_runner.so");
            if (!runner.isFile()) {
                throw new IOException("本地推理组件缺失，请重新安装完整 APK");
            }

            RunResult result = runInference(
                    buildCommand(runner, modelFile, systemFile, userFile,
                            templateFile, visionFile, imageFile, GPU_LAYERS),
                    promptDirectory, nativeDirectory, call, callback, prompt.user);
            if (result.cancelled) return;

            // Always try Vulkan first. Retry on CPU only when GPU startup
            // failed before any response text was emitted.
            if (result.exitCode != 0
                    && result.emittedChars == 0
                    && isGpuStartupFailure(result)) {
                result = runInference(
                        buildCommand(runner, modelFile, systemFile, userFile,
                                templateFile, visionFile, imageFile, 0),
                        promptDirectory, nativeDirectory, call, callback, prompt.user);
                if (result.cancelled) return;
            }
            if (result.exitCode != 0) throw inferenceFailure(result);
            if (result.emittedChars == 0) throw emptyOutputFailure(result);
        } finally {
            call.attachProcess(null);
            deleteQuietly(systemFile);
            deleteQuietly(userFile);
            deleteQuietly(templateFile);
            deleteQuietly(imageFile);
            INFERENCE_SLOT.release();
        }
    }

    private static List<String> buildCommand(
            File runner,
            File modelFile,
            File systemFile,
            File userFile,
            File templateFile,
            File visionFile,
            File imageFile,
            int gpuLayers) {
        List<String> command = new ArrayList<>();
        command.add(runner.getAbsolutePath());
        // b10202's unified executable dispatches the interactive generator
        // through the explicit cli subcommand.
        command.add("cli");
        command.add("-m");
        command.add(modelFile.getAbsolutePath());
        if (visionFile != null && imageFile != null) {
            command.add("-mm");
            command.add(visionFile.getAbsolutePath());
            command.add("--image");
            command.add(imageFile.getAbsolutePath());
            command.add("--image-max-tokens");
            command.add("384");
            if (gpuLayers == 0) command.add("--no-mmproj-offload");
        }
        // llama-cli b10202 routes predefined prompts through its internal
        // chat-completions server. Keep system and user content as separate
        // messages so the model's native Qwen3.5 template is applied once.
        command.add("--system-prompt-file");
        command.add(systemFile.getAbsolutePath());
        command.add("--file");
        command.add(userFile.getAbsolutePath());
        command.add("--single-turn");
        // Enable Jinja before supplying the template. The bundled model's
        // full template throws while llama.cpp probes an empty synthetic
        // conversation, so use a minimal ChatML renderer with Qwen3.5's
        // official non-thinking assistant prefill.
        command.add("--jinja");
        command.add("--chat-template-file");
        command.add(templateFile.getAbsolutePath());
        command.add("--reasoning");
        command.add("off");
        // Qwen recommends disabling response parsers. This also avoids the
        // automatic-parser exception raised by its full Jinja chat template.
        command.add("--skip-chat-parsing");
        command.add("--ctx-size");
        command.add("4096");
        command.add("--predict");
        command.add("512");
        command.add("--batch-size");
        command.add("512");
        command.add("--ubatch-size");
        command.add("128");
        command.add("--threads");
        command.add(String.valueOf(Math.max(2,
                Math.min(4, Runtime.getRuntime().availableProcessors()))));
        command.add("--gpu-layers");
        command.add(String.valueOf(gpuLayers));
        command.add("--fit");
        command.add("off");
        command.add("--no-warmup");
        command.add("--temp");
        command.add("0.75");
        command.add("--top-p");
        command.add("0.9");
        command.add("--no-display-prompt");
        command.add("--simple-io");
        command.add("--color");
        command.add("off");
        command.add("--verbosity");
        command.add("1");
        return command;
    }

    private static RunResult runInference(
            List<String> command,
            File promptDirectory,
            File nativeDirectory,
            ApiClient.Call call,
            ApiClient.StreamCallback callback,
            String echoedPrompt) throws Exception {
        if (call.isCancelled()) return RunResult.cancelled();
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(promptDirectory);
        builder.environment().put("LD_LIBRARY_PATH", nativeDirectory.getAbsolutePath());
        java.lang.Process process = builder.start();
        call.attachProcess(process);

        ErrorCollector errors = new ErrorCollector(process.getErrorStream());
        Thread errorThread = new Thread(errors, "local-model-errors");
        errorThread.setDaemon(true);
        errorThread.start();
        StringBuilder rawOutput = new StringBuilder();
        try {
            try (InputStreamReader output = new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[192];
                int read;
                while (!call.isCancelled() && (read = output.read(buffer)) != -1) {
                    if (read > 0) {
                        rawOutput.append(buffer, 0, read);
                    }
                }
            }
            if (call.isCancelled()) {
                process.destroy();
                return RunResult.cancelled();
            }
            int exitCode = process.waitFor();
            errorThread.join(500L);
            String cleanOutput = sanitizeOutput(rawOutput.toString(), echoedPrompt);
            if (!cleanOutput.isEmpty()) callback.onDelta(cleanOutput);
            return new RunResult(
                    exitCode, errors.text().trim(), cleanOutput.length(), false);
        } finally {
            call.attachProcess(null);
        }
    }

    static String sanitizeOutput(String raw) {
        return sanitizeOutput(raw, "");
    }

    private static String sanitizeOutput(String raw, String echoedPrompt) {
        if (raw == null || raw.isEmpty()) return "";
        String clean = raw
                .replaceAll("\\u001B\\[[0-?]*[ -/]*[@-~]", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        // The unified b10202 executable writes its startup banner, model
        // metadata and prompt echo to stdout. Keep only the assistant answer.
        int promptMarker = clean.lastIndexOf("\n> ");
        if (promptMarker >= 0) {
            clean = clean.substring(promptMarker + 3);
            String normalizedPrompt = echoedPrompt == null
                    ? "" : echoedPrompt.replace("\r\n", "\n").replace('\r', '\n');
            if (!normalizedPrompt.isEmpty() && clean.startsWith(normalizedPrompt)) {
                clean = clean.substring(normalizedPrompt.length());
            }
            clean = clean.replaceFirst("^\\n+", "");
        }
        int timing = clean.indexOf("\n[ Prompt:");
        if (timing >= 0) clean = clean.substring(0, timing);
        int exiting = clean.indexOf("\nExiting...");
        if (exiting >= 0) clean = clean.substring(0, exiting);
        clean = clean
                .replaceAll("(?is)<think>.*?</think>\\s*", "")
                .replace("<|im_end|>", "")
                .replace("<|endoftext|>", "")
                .replaceAll("(?is)\\s*\\[end of text]\\s*$", "");
        int danglingThink = clean.toLowerCase(Locale.ROOT).lastIndexOf("<think>");
        if (danglingThink >= 0) clean = clean.substring(0, danglingThink);
        return clean.trim();
    }

    private static File extractLatestImage(
            JSONArray messages,
            File directory,
            String token) throws IOException {
        String dataUrl = "";
        for (int i = messages.length() - 1; i >= 0 && dataUrl.isEmpty(); i--) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null || !"user".equals(message.optString("role"))) {
                continue;
            }
            JSONArray parts = message.optJSONArray("content");
            if (parts == null) return null;
            for (int j = parts.length() - 1; j >= 0; j--) {
                JSONObject part = parts.optJSONObject(j);
                if (part == null) continue;
                String type = part.optString("type", "");
                if (!"image_url".equals(type)
                        && !"input_image".equals(type)
                        && !"image".equals(type)) {
                    continue;
                }
                Object imageValue = part.opt("image_url");
                if (imageValue instanceof JSONObject) {
                    dataUrl = ((JSONObject) imageValue).optString("url", "");
                } else if (imageValue instanceof String) {
                    dataUrl = (String) imageValue;
                }
                if (dataUrl.isEmpty()) dataUrl = part.optString("url", "");
                if (!dataUrl.isEmpty()) break;
            }
            // Only the current user turn may contribute media. Reusing an
            // older image on every later text message is both surprising and
            // needlessly expensive on-device.
            break;
        }
        if (dataUrl.isEmpty()) return null;
        if (!dataUrl.startsWith("data:image/") || !dataUrl.contains(";base64,")) {
            throw new IOException("本地多模态当前只能读取应用相册或相机中的图片");
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0 || comma + 1 >= dataUrl.length()) {
            throw new IOException("图片数据格式无效，请重新选择图片");
        }
        final byte[] encoded;
        try {
            encoded = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
        } catch (IllegalArgumentException error) {
            throw new IOException("图片数据损坏，请重新选择图片", error);
        }
        if (encoded.length == 0) throw new IOException("图片内容为空");

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(encoded, 0, encoded.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("无法读取该图片格式，请改用 JPG 或 PNG");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / options.inSampleSize > 1536) options.inSampleSize *= 2;
        Bitmap bitmap = BitmapFactory.decodeByteArray(
                encoded, 0, encoded.length, options);
        if (bitmap == null) throw new IOException("无法解码图片，请重新选择");
        File output = new File(directory, token + "-vision.jpg");
        try {
            int maxSide = Math.max(bitmap.getWidth(), bitmap.getHeight());
            Bitmap prepared = bitmap;
            if (maxSide > 1344) {
                float scale = 1344f / maxSide;
                prepared = Bitmap.createScaledBitmap(
                        bitmap,
                        Math.max(1, Math.round(bitmap.getWidth() * scale)),
                        Math.max(1, Math.round(bitmap.getHeight() * scale)),
                        true);
            }
            try (FileOutputStream stream = new FileOutputStream(output)) {
                if (!prepared.compress(Bitmap.CompressFormat.JPEG, 90, stream)) {
                    throw new IOException("无法准备本地视觉输入");
                }
            } finally {
                if (prepared != bitmap) prepared.recycle();
            }
        } finally {
            bitmap.recycle();
        }
        return output;
    }

    private static boolean isGpuStartupFailure(RunResult result) {
        String error = result.error.toLowerCase(Locale.ROOT);
        return result.exitCode < 0
                || result.exitCode >= 128
                || error.contains("vulkan")
                || error.contains("gpu")
                || error.contains("device lost")
                || error.contains("signal 11")
                || error.contains("segmentation");
    }

    private static IOException inferenceFailure(RunResult result) {
        return new IOException(result.error.isEmpty()
                ? "本地模型运行失败（退出码 " + result.exitCode + "）"
                : "本地模型运行失败：" + tail(result.error, 1200));
    }

    private static IOException emptyOutputFailure(RunResult result) {
        return new IOException(result.error.isEmpty()
                ? "本地模型运行完成但没有返回文字内容，请尝试重新发送或降低上下文长度。"
                : "本地模型运行完成但没有返回文字内容：" + tail(result.error, 1200));
    }

    static PromptParts buildPrompt(JSONArray messages) {
        StringBuilder system = new StringBuilder();
        StringBuilder history = new StringBuilder();
        String lastUser = "请根据以上设定和对话自然回复。";
        int lastUserIndex = -1;
        for (int i = messages.length() - 1; i >= 0; i--) {
            JSONObject message = messages.optJSONObject(i);
            if (message != null && "user".equals(message.optString("role"))) {
                lastUserIndex = i;
                lastUser = plainContent(message.opt("content"));
                if (lastUser.replace("[图片]", "").trim().isEmpty()
                        && lastUser.contains("[图片]")) {
                    lastUser = "[图片]\n请观察图片的实际视觉内容；“[图片]”只是消息占位符，"
                            + "不是图片中的文字。请结合当前角色设定自然回应。";
                }
                break;
            }
        }
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            String role = message.optString("role", "user");
            String content = plainContent(message.opt("content")).trim();
            if (content.isEmpty()) continue;
            if ("system".equals(role)) {
                if (system.length() > 0) system.append("\n\n");
                system.append(content);
            } else if (i != lastUserIndex) {
                history.append("\n\n")
                        .append("assistant".equals(role) ? "助手" : "用户")
                        .append("：")
                        .append(content);
            }
        }
        if (history.length() > 0) {
            system.append("\n\n以下是此前对话记录，请保持上下文连贯：")
                    .append(history);
        }
        system.append("\n\n当前回答由端侧本地模型生成。"
                + "如果系统提示包含应用提供的联网搜索结果，可据此回答并标注来源；"
                + "如果没有搜索结果，不得声称已经搜索网络，也不要编造实时来源。"
                + "请直接给出最终回答，不要输出 <think>、</think> 或内部思考过程。");
        return new PromptParts(
                limitKeepingEnds(system.toString(), MAX_SYSTEM_CHARS),
                limitKeepingEnds(lastUser, MAX_USER_CHARS));
    }

    private static String plainContent(Object content) {
        if (content instanceof String) return (String) content;
        if (!(content instanceof JSONArray)) {
            return content == null || content == JSONObject.NULL
                    ? "" : String.valueOf(content);
        }
        JSONArray parts = (JSONArray) content;
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part == null) continue;
            String type = part.optString("type");
            if ("text".equals(type) || "input_text".equals(type)) {
                appendPart(value, part.optString("text"));
            } else if ("image_url".equals(type)
                    || "input_image".equals(type)
                    || "image".equals(type)) {
                if (!value.toString().trim().endsWith("[图片]")) {
                    appendPart(value, "[图片]");
                }
            }
        }
        return value.toString();
    }

    private static void appendPart(StringBuilder target, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (target.length() > 0) target.append('\n');
        target.append(value.trim());
    }

    private static String limitKeepingEnds(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) return value == null ? "" : value;
        int head = maxChars / 3;
        int tail = maxChars - head;
        return value.substring(0, head)
                + "\n\n[较早内容已为适配本地上下文而省略]\n\n"
                + value.substring(value.length() - tail);
    }

    private static void writeUtf8(File file, String value) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(value == null ? "" : value);
        }
    }

    private static String tail(String value, int maxChars) {
        return value.length() <= maxChars
                ? value : value.substring(value.length() - maxChars);
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) file.delete();
    }

    static final class PromptParts {
        final String system;
        final String user;

        PromptParts(String system, String user) {
            this.system = system;
            this.user = user;
        }
    }

    private static final class RunResult {
        final int exitCode;
        final String error;
        final int emittedChars;
        final boolean cancelled;

        RunResult(int exitCode, String error, int emittedChars, boolean cancelled) {
            this.exitCode = exitCode;
            this.error = error == null ? "" : error;
            this.emittedChars = emittedChars;
            this.cancelled = cancelled;
        }

        static RunResult cancelled() {
            return new RunResult(0, "", 0, true);
        }
    }

    private static final class ErrorCollector implements Runnable {
        private final InputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        ErrorCollector(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            try {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    synchronized (output) {
                        if (output.size() < 64 * 1024) output.write(buffer, 0, read);
                    }
                }
            } catch (IOException ignored) {
            }
        }

        String text() {
            synchronized (output) {
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }
}
