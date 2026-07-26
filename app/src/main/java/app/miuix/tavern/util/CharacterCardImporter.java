package app.miuix.tavern.util;

import android.util.Base64;

import app.miuix.tavern.model.CharacterCard;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.InflaterInputStream;

public final class CharacterCardImporter {
    private static final byte[] PNG_SIGNATURE =
            new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    public static final class Result {
        public final CharacterCard card;
        public final byte[] originalBytes;
        public final boolean png;

        Result(CharacterCard card, byte[] originalBytes, boolean png) {
            this.card = card;
            this.originalBytes = originalBytes;
            this.png = png;
        }
    }

    private CharacterCardImporter() {
    }

    public static Result parse(InputStream input) throws IOException, JSONException {
        byte[] bytes = readAll(input);
        boolean png = isPng(bytes);
        String json = png ? extractPngCharacterJson(bytes)
                : new String(bytes, StandardCharsets.UTF_8);
        if (json == null || json.trim().isEmpty()) {
            throw new JSONException("没有找到 Tavern 角色卡数据");
        }
        return new Result(CharacterCard.fromTavernJson(new JSONObject(json)), bytes, png);
    }

    private static boolean isPng(byte[] bytes) {
        if (bytes.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) return false;
        }
        return true;
    }

    private static String extractPngCharacterJson(byte[] bytes) throws IOException {
        int offset = 8;
        while (offset + 12 <= bytes.length) {
            int length = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            if (length < 0 || offset + 12L + length > bytes.length) break;
            String type = new String(bytes, offset + 4, 4, StandardCharsets.ISO_8859_1);
            byte[] data = new byte[length];
            System.arraycopy(bytes, offset + 8, data, 0, length);

            String encoded = null;
            if ("tEXt".equals(type)) {
                encoded = readTextChunk(data);
            } else if ("zTXt".equals(type)) {
                encoded = readCompressedTextChunk(data);
            } else if ("iTXt".equals(type)) {
                encoded = readInternationalTextChunk(data);
            }
            if (encoded != null && !encoded.isEmpty()) {
                byte[] decoded = Base64.decode(encoded.trim(), Base64.DEFAULT);
                return new String(decoded, StandardCharsets.UTF_8);
            }
            offset += length + 12;
        }
        return null;
    }

    private static String readTextChunk(byte[] data) {
        int separator = indexOfZero(data, 0);
        if (separator < 0) return null;
        String key = new String(data, 0, separator, StandardCharsets.ISO_8859_1);
        if (!"chara".equals(key)) return null;
        return new String(data, separator + 1, data.length - separator - 1, StandardCharsets.ISO_8859_1);
    }

    private static String readCompressedTextChunk(byte[] data) throws IOException {
        int separator = indexOfZero(data, 0);
        if (separator < 0 || separator + 2 >= data.length) return null;
        String key = new String(data, 0, separator, StandardCharsets.ISO_8859_1);
        if (!"chara".equals(key)) return null;
        ByteArrayInputStream compressed =
                new ByteArrayInputStream(data, separator + 2, data.length - separator - 2);
        return new String(readAll(new InflaterInputStream(compressed)), StandardCharsets.ISO_8859_1);
    }

    private static String readInternationalTextChunk(byte[] data) throws IOException {
        int keyEnd = indexOfZero(data, 0);
        if (keyEnd < 0 || !"chara".equals(new String(data, 0, keyEnd, StandardCharsets.ISO_8859_1))) {
            return null;
        }
        int cursor = keyEnd + 1;
        if (cursor + 2 > data.length) return null;
        boolean compressed = data[cursor] == 1;
        cursor += 2;
        int languageEnd = indexOfZero(data, cursor);
        if (languageEnd < 0) return null;
        cursor = languageEnd + 1;
        int translatedEnd = indexOfZero(data, cursor);
        if (translatedEnd < 0) return null;
        cursor = translatedEnd + 1;
        if (cursor > data.length) return null;
        byte[] text = new byte[data.length - cursor];
        System.arraycopy(data, cursor, text, 0, text.length);
        if (compressed) {
            text = readAll(new InflaterInputStream(new ByteArrayInputStream(text)));
        }
        return new String(text, StandardCharsets.UTF_8);
    }

    private static int indexOfZero(byte[] bytes, int start) {
        for (int i = start; i < bytes.length; i++) {
            if (bytes[i] == 0) return i;
        }
        return -1;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
