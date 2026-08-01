package app.miuix.tavern.network;

import android.content.Context;
import android.os.Build;
import android.os.StatFs;

import app.miuix.tavern.model.AppConfig;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Downloads and validates the GGUF files used by the bundled llama.cpp runtime. */
public final class LocalModelManager {
    public static final int STATE_IDLE = 0;
    public static final int STATE_DOWNLOADING = 1;
    public static final int STATE_VERIFYING = 2;
    public static final int STATE_INSTALLED = 3;
    public static final int STATE_ERROR = 4;

    public static final class ModelSpec {
        public final String id;
        public final String displayName;
        public final String quantization;
        public final String description;
        public final String fileName;
        public final String url;
        public final long bytes;
        public final String sha256;
        public final String visionFileName;
        public final String visionUrl;
        public final long visionBytes;
        public final String visionSha256;

        ModelSpec(
                String id,
                String displayName,
                String quantization,
                String description,
                String fileName,
                String url,
                long bytes,
                String sha256,
                String visionFileName,
                String visionUrl,
                long visionBytes,
                String visionSha256) {
            this.id = id;
            this.displayName = displayName;
            this.quantization = quantization;
            this.description = description;
            this.fileName = fileName;
            this.url = url;
            this.bytes = bytes;
            this.sha256 = sha256;
            this.visionFileName = visionFileName;
            this.visionUrl = visionUrl;
            this.visionBytes = visionBytes;
            this.visionSha256 = visionSha256;
        }

        public long totalBytes() {
            return bytes + visionBytes;
        }
    }

    public static final class Snapshot {
        public final ModelSpec model;
        public final int state;
        public final long downloadedBytes;
        public final String error;

        Snapshot(ModelSpec model, int state, long downloadedBytes, String error) {
            this.model = model;
            this.state = state;
            this.downloadedBytes = downloadedBytes;
            this.error = error == null ? "" : error;
        }

        public int percent() {
            if (model.totalBytes() <= 0) return 0;
            return (int) Math.min(
                    100L, downloadedBytes * 100L / model.totalBytes());
        }
    }

    public interface Listener {
        void onModelChanged(Snapshot snapshot);
    }

    private static final ModelSpec QWEN_08 = new ModelSpec(
            AppConfig.LOCAL_MODEL_QWEN_08,
            "Qwen3.5-0.8B",
            "Q4_0 · 733 MB（含视觉）",
            "速度更快，支持图片理解，建议至少 3 GB 可用内存",
            "Qwen3.5-0.8B-Q4_0.gguf",
            "https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF/resolve/"
                    + "8fea620810c4afa23dd6443f999a48574c1611a3/"
                    + "Qwen3.5-0.8B-Q4_0.gguf?download=true",
            563036064L,
            "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf",
            "Qwen3.5-0.8B-mmproj-F16.gguf",
            "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/"
                    + "6ab461498e2023f6e3c1baea90a8f0fe38ab64d0/"
                    + "mmproj-F16.gguf?download=true",
            204987232L,
            "56e4c6cfe73b0c82e3e82bc518d7591997e61d81f723fc41a586f4fa69ea2453");

    private static final ModelSpec QWEN_2B = new ModelSpec(
            AppConfig.LOCAL_MODEL_QWEN_2B,
            "Qwen3.5-2B",
            "Q4_K_M · 1.82 GB（含视觉）",
            "效果更好，支持图片理解，建议至少 5 GB 可用内存",
            "Qwen3.5-2B-Q4_K_M.gguf",
            "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/"
                    + "f6d5376be1edb4d416d56da11e5397a961aca8ae/"
                    + "Qwen3.5-2B-Q4_K_M.gguf?download=true",
            1280835840L,
            "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223",
            "Qwen3.5-2B-mmproj-F16.gguf",
            "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/"
                    + "f6d5376be1edb4d416d56da11e5397a961aca8ae/"
                    + "mmproj-F16.gguf?download=true",
            668227264L,
            "7035e9cb8d7c6a9681d07eef9a364783e86ea4cd73faab2eabb4f43a101830c7");

    private static final Map<String, ModelSpec> MODELS;

    static {
        LinkedHashMap<String, ModelSpec> models = new LinkedHashMap<>();
        models.put(QWEN_08.id, QWEN_08);
        models.put(QWEN_2B.id, QWEN_2B);
        MODELS = Collections.unmodifiableMap(models);
    }

    private static volatile LocalModelManager instance;

    private final Context context;
    private final OkHttpClient http;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final Map<String, DownloadTask> active = new ConcurrentHashMap<>();
    private final Map<String, String> errors = new ConcurrentHashMap<>();

    private LocalModelManager(Context context) {
        this.context = context.getApplicationContext();
        http = new OkHttpClient.Builder()
                .dns(ReliableDns.INSTANCE)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public static LocalModelManager get(Context context) {
        if (instance == null) {
            synchronized (LocalModelManager.class) {
                if (instance == null) instance = new LocalModelManager(context);
            }
        }
        return instance;
    }

    public static ModelSpec[] models() {
        return MODELS.values().toArray(new ModelSpec[0]);
    }

    public static ModelSpec find(String id) {
        ModelSpec model = MODELS.get(id);
        return model == null ? QWEN_08 : model;
    }

    public static boolean isRuntimeSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false;
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) return true;
        }
        return false;
    }

    public static String runtimeSupportMessage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return "本地模型需要 Android 9 或更高版本";
        }
        return "本地模型目前需要 64 位 ARM 设备";
    }

    public void addListener(Listener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        if (listener != null) listeners.remove(listener);
    }

    public Snapshot snapshot(String id) {
        ModelSpec model = find(id);
        DownloadTask task = active.get(model.id);
        if (task != null) {
            return new Snapshot(model, task.state, task.downloadedBytes, task.error);
        }
        if (isInstalled(model.id)) {
            return new Snapshot(model, STATE_INSTALLED, model.totalBytes(), "");
        }
        long available = installedOrPartialBytes(
                modelFile(model), partialFile(model), markerFile(model),
                model.bytes, model.sha256);
        available += installedOrPartialBytes(
                visionFile(model), visionPartialFile(model), visionMarkerFile(model),
                model.visionBytes, model.visionSha256);
        String error = errors.get(model.id);
        return new Snapshot(
                model,
                error == null || error.isEmpty() ? STATE_IDLE : STATE_ERROR,
                available,
                error);
    }

    public boolean isInstalled(String id) {
        ModelSpec model = find(id);
        return isResourceInstalled(
                modelFile(model), markerFile(model), model.bytes, model.sha256)
                && isResourceInstalled(
                visionFile(model), visionMarkerFile(model),
                model.visionBytes, model.visionSha256);
    }

    public File installedFile(String id) {
        ModelSpec model = find(id);
        return isResourceInstalled(
                modelFile(model), markerFile(model), model.bytes, model.sha256)
                ? modelFile(model) : null;
    }

    public File installedVisionFile(String id) {
        ModelSpec model = find(id);
        return isResourceInstalled(
                visionFile(model), visionMarkerFile(model),
                model.visionBytes, model.visionSha256)
                ? visionFile(model) : null;
    }

    public void startDownload(String id) {
        ModelSpec model = find(id);
        if (!isRuntimeSupported()) {
            errors.put(model.id, runtimeSupportMessage());
            notifyChanged(model);
            return;
        }
        if (isInstalled(model.id)) {
            notifyChanged(model);
            return;
        }
        prepareCompleteForValidation(
                modelFile(model), partialFile(model), markerFile(model),
                model.bytes);
        prepareCompleteForValidation(
                visionFile(model), visionPartialFile(model), visionMarkerFile(model),
                model.visionBytes);
        long downloaded = installedOrPartialBytes(
                modelFile(model), partialFile(model), markerFile(model),
                model.bytes, model.sha256);
        downloaded += installedOrPartialBytes(
                visionFile(model), visionPartialFile(model), visionMarkerFile(model),
                model.visionBytes, model.visionSha256);
        if (!hasEnoughSpace(model, downloaded)) {
            errors.put(model.id, "存储空间不足，无法完成模型下载");
            notifyChanged(model);
            return;
        }
        DownloadTask task = new DownloadTask(model, Math.min(downloaded, model.bytes));
        if (active.putIfAbsent(model.id, task) != null) return;
        errors.remove(model.id);
        notifyChanged(model);
        executor.execute(() -> runDownload(task));
    }

    public void pauseDownload(String id) {
        DownloadTask task = active.get(find(id).id);
        if (task == null) return;
        task.cancelled = true;
        task.state = STATE_IDLE;
        Call call = task.call;
        if (call != null) call.cancel();
        notifyChanged(task.model);
    }

    public boolean deleteModel(String id) {
        ModelSpec model = find(id);
        DownloadTask task = active.get(model.id);
        if (task != null) {
            task.deleteWhenStopped = true;
            pauseDownload(model.id);
            errors.remove(model.id);
            return true;
        }
        boolean ok = deleteIfPresent(modelFile(model));
        ok = deleteIfPresent(partialFile(model)) && ok;
        ok = deleteIfPresent(markerFile(model)) && ok;
        ok = deleteIfPresent(visionFile(model)) && ok;
        ok = deleteIfPresent(visionPartialFile(model)) && ok;
        ok = deleteIfPresent(visionMarkerFile(model)) && ok;
        errors.remove(model.id);
        notifyChanged(model);
        return ok;
    }

    private void runDownload(DownloadTask task) {
        ModelSpec model = task.model;
        try {
            ensureModelDirectory();
            installResource(
                    task,
                    "模型",
                    model.url,
                    modelFile(model),
                    partialFile(model),
                    markerFile(model),
                    model.bytes,
                    model.sha256,
                    0L);
            if (task.cancelled) return;
            installResource(
                    task,
                    "视觉组件",
                    model.visionUrl,
                    visionFile(model),
                    visionPartialFile(model),
                    visionMarkerFile(model),
                    model.visionBytes,
                    model.visionSha256,
                    model.bytes);
            if (task.cancelled) return;
            task.downloadedBytes = model.totalBytes();
            errors.remove(model.id);
        } catch (Exception error) {
            if (!task.cancelled) {
                String message = error.getMessage();
                task.error = message == null || message.trim().isEmpty()
                        ? "模型下载失败" : message.trim();
                errors.put(model.id, task.error);
            }
        } finally {
            active.remove(model.id, task);
            if (task.deleteWhenStopped) {
                deleteIfPresent(modelFile(model));
                deleteIfPresent(partialFile(model));
                deleteIfPresent(markerFile(model));
                deleteIfPresent(visionFile(model));
                deleteIfPresent(visionPartialFile(model));
                deleteIfPresent(visionMarkerFile(model));
                errors.remove(model.id);
            }
            notifyChanged(model);
        }
    }

    private void installResource(
            DownloadTask task,
            String label,
            String url,
            File target,
            File partial,
            File marker,
            long expectedBytes,
            String expectedSha256,
            long progressBase) throws Exception {
        if (isResourceInstalled(target, marker, expectedBytes, expectedSha256)) {
            task.downloadedBytes = progressBase + expectedBytes;
            notifyChanged(task.model);
            return;
        }
        prepareCompleteForValidation(target, partial, marker, expectedBytes);
        long partialBytes = partial.isFile()
                ? Math.min(partial.length(), expectedBytes) : 0L;
        task.downloadedBytes = progressBase + partialBytes;
        task.state = STATE_DOWNLOADING;
        notifyChanged(task.model);
        if (partialBytes < expectedBytes) {
            downloadBytes(
                    task, label, url, partial, expectedBytes, progressBase);
        }
        if (task.cancelled) return;
        task.state = STATE_VERIFYING;
        task.downloadedBytes = progressBase + expectedBytes;
        notifyChanged(task.model);
        String actual = sha256(partial, task);
        if (task.cancelled) return;
        if (!expectedSha256.equalsIgnoreCase(actual)) {
            throw new IOException(label + "校验失败，下载文件可能已损坏");
        }
        deleteIfPresent(target);
        if (!partial.renameTo(target)) {
            throw new IOException("无法保存已下载的" + label + "文件");
        }
        writeMarker(marker, expectedSha256);
    }

    private void downloadBytes(
            DownloadTask task,
            String label,
            String url,
            File partial,
            long expectedBytes,
            long progressBase) throws IOException {
        long offset = partial.isFile() ? partial.length() : 0L;
        if (offset > expectedBytes) {
            if (!partial.delete()) throw new IOException("无法重置不完整的模型文件");
            offset = 0L;
        }
        Request.Builder request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Chengyu/0.9 Android local-model-downloader");
        if (offset > 0L) request.header("Range", "bytes=" + offset + "-");
        task.call = http.newCall(request.build());
        try (Response response = task.call.execute()) {
            int code = response.code();
            if (code != 200 && code != 206) {
                throw new IOException(label + "下载失败（HTTP " + code + "）");
            }
            boolean append = code == 206 && offset > 0L;
            if (!append) {
                offset = 0L;
                task.downloadedBytes = progressBase;
            }
            ResponseBody body = response.body();
            if (body == null) throw new IOException(label + "服务器没有返回文件内容");
            try (InputStream input = new BufferedInputStream(body.byteStream(), 64 * 1024);
                 FileOutputStream output = new FileOutputStream(partial, append)) {
                byte[] buffer = new byte[128 * 1024];
                long total = offset;
                long lastUpdate = 0L;
                int read;
                while (!task.cancelled && (read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    total += read;
                    task.downloadedBytes = progressBase + total;
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate >= 250L) {
                        lastUpdate = now;
                        notifyChanged(task.model);
                    }
                }
                output.flush();
            }
        }
        if (task.cancelled) return;
        long size = partial.length();
        task.downloadedBytes = progressBase + size;
        if (size != expectedBytes) {
            throw new IOException(label + "下载不完整（" + readableBytes(size) + " / "
                    + readableBytes(expectedBytes) + "）");
        }
    }

    private String sha256(File file, DownloadTask task) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while (!task.cancelled && (read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        if (task.cancelled) return "";
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private boolean hasEnoughSpace(ModelSpec model, long downloaded) {
        try {
            File directory = ensureModelDirectory();
            long remaining = Math.max(0L, model.totalBytes() - downloaded);
            long reserve = 96L * 1024L * 1024L;
            return new StatFs(directory.getAbsolutePath()).getAvailableBytes()
                    >= remaining + reserve;
        } catch (Exception ignored) {
            return false;
        }
    }

    private File ensureModelDirectory() throws IOException {
        File base = context.getExternalFilesDir("models");
        if (base == null) base = new File(context.getFilesDir(), "models");
        if (!base.isDirectory() && !base.mkdirs()) {
            throw new IOException("无法创建本地模型目录");
        }
        return base;
    }

    private File modelDirectory() {
        File base = context.getExternalFilesDir("models");
        return base == null ? new File(context.getFilesDir(), "models") : base;
    }

    private File modelFile(ModelSpec model) {
        return new File(modelDirectory(), model.fileName);
    }

    private File partialFile(ModelSpec model) {
        return new File(modelDirectory(), model.fileName + ".part");
    }

    private File markerFile(ModelSpec model) {
        return new File(modelDirectory(), model.fileName + ".sha256");
    }

    private File visionFile(ModelSpec model) {
        return new File(modelDirectory(), model.visionFileName);
    }

    private File visionPartialFile(ModelSpec model) {
        return new File(modelDirectory(), model.visionFileName + ".part");
    }

    private File visionMarkerFile(ModelSpec model) {
        return new File(modelDirectory(), model.visionFileName + ".sha256");
    }

    private static boolean isResourceInstalled(
            File target,
            File marker,
            long expectedBytes,
            String expectedSha256) {
        if (!target.isFile() || target.length() != expectedBytes || !marker.isFile()) {
            return false;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(marker))) {
            return expectedSha256.equalsIgnoreCase(reader.readLine());
        } catch (IOException ignored) {
            return false;
        }
    }

    private static long installedOrPartialBytes(
            File target,
            File partial,
            File marker,
            long expectedBytes,
            String expectedSha256) {
        if (isResourceInstalled(
                target, marker, expectedBytes, expectedSha256)) {
            return expectedBytes;
        }
        if (partial.isFile()) return Math.min(partial.length(), expectedBytes);
        if (target.isFile()) return Math.min(target.length(), expectedBytes);
        return 0L;
    }

    private static void prepareCompleteForValidation(
            File target,
            File partial,
            File marker,
            long expectedBytes) {
        if (marker.isFile() || partial.exists()
                || !target.isFile() || target.length() != expectedBytes) {
            return;
        }
        target.renameTo(partial);
    }

    private void writeMarker(File marker, String sha256) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(marker), StandardCharsets.UTF_8)) {
            writer.write(sha256);
            writer.write('\n');
        }
    }

    private static boolean deleteIfPresent(File file) {
        return !file.exists() || file.delete();
    }

    private void notifyChanged(ModelSpec model) {
        Snapshot snapshot = snapshot(model.id);
        for (Listener listener : listeners) listener.onModelChanged(snapshot);
    }

    public static String readableBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.getDefault(), "%.2f GB",
                    bytes / (1024d * 1024d * 1024d));
        }
        return String.format(Locale.getDefault(), "%.0f MB",
                bytes / (1024d * 1024d));
    }

    private static final class DownloadTask {
        final ModelSpec model;
        volatile int state = STATE_DOWNLOADING;
        volatile long downloadedBytes;
        volatile boolean cancelled;
        volatile boolean deleteWhenStopped;
        volatile String error = "";
        volatile Call call;

        DownloadTask(ModelSpec model, long downloadedBytes) {
            this.model = model;
            this.downloadedBytes = downloadedBytes;
        }
    }
}
