package app.miuix.tavern.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class MediaAttachmentStore {
    private static final int MAX_EDGE = 1600;

    private MediaAttachmentStore() {
    }

    public static File createCameraFile(Context context) throws IOException {
        File directory = mediaDirectory(context);
        File file = new File(directory, "camera-" + System.currentTimeMillis() + ".jpg");
        if (!file.createNewFile()) throw new IOException("无法创建拍照文件");
        return file;
    }

    public static String saveGalleryImage(Context context, Uri uri) throws IOException {
        Bitmap bitmap = decodeUri(context, uri, MAX_EDGE);
        if (bitmap == null) throw new IOException("所选文件不是有效图片");
        return saveBitmap(context, bitmap);
    }

    public static String normalizeCameraImage(Context context, File source) throws IOException {
        Bitmap bitmap = decodeFile(source, MAX_EDGE);
        if (bitmap == null) throw new IOException("相机没有返回有效图片");
        String output = saveBitmap(context, bitmap);
        if (!source.getAbsolutePath().equals(output)) source.delete();
        return output;
    }

    public static Bitmap decodePreview(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        return decodeFile(new File(path), 640);
    }

    public static String toDataUrl(String path, String mime) {
        if (path == null || path.trim().isEmpty()) return "";
        File file = new File(path);
        if (!file.isFile()) return "";
        try (InputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            String type = mime == null || mime.trim().isEmpty()
                    ? "image/jpeg" : mime.trim();
            return "data:" + type + ";base64,"
                    + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
        } catch (IOException ignored) {
            return "";
        }
    }

    private static String saveBitmap(Context context, Bitmap bitmap) throws IOException {
        File output = new File(
                mediaDirectory(context), "image-" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)) {
                throw new IOException("图片压缩失败");
            }
        } finally {
            bitmap.recycle();
        }
        return output.getAbsolutePath();
    }

    private static File mediaDirectory(Context context) throws IOException {
        File directory = new File(context.getFilesDir(), "chat-media");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建媒体目录");
        }
        return directory;
    }

    private static Bitmap decodeUri(Context context, Uri uri, int maxEdge)
            throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, maxEdge);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            return input == null ? null : BitmapFactory.decodeStream(input, null, options);
        }
    }

    private static Bitmap decodeFile(File file, int maxEdge) {
        if (file == null || !file.isFile() || file.length() == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, maxEdge);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private static int sampleFor(int width, int height, int maxEdge) {
        int sample = 1;
        int largest = Math.max(width, height);
        while (largest / sample > maxEdge) sample *= 2;
        return sample;
    }
}
