package app.miuix.tavern.util;

import android.util.Base64;

import app.miuix.tavern.model.CharacterCard;

import org.json.JSONArray;
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
    private static final int MAX_CARD_FILE_BYTES = 48 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 16 * 1024 * 1024;
    private static final int MAX_WORLD_BOOK_BYTES = 8 * 1024 * 1024;
    private static final int MAX_WORLD_BOOK_ENTRIES = 1_000_000;

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
        byte[] bytes = readAll(input, MAX_CARD_FILE_BYTES);
        boolean png = isPng(bytes);
        if (!png && bytes.length > MAX_JSON_BYTES) {
            throw new IOException("JSON 角色卡不能超过 16 MB");
        }
        String json = png ? extractPngCharacterJson(bytes)
                : new String(bytes, StandardCharsets.UTF_8);
        if (json == null || json.trim().isEmpty()) {
            throw new JSONException("没有找到 Tavern 角色卡数据");
        }
        final CharacterCard card;
        try {
            card = CharacterCard.fromTavernJson(new JSONObject(json));
        } catch (OutOfMemoryError error) {
            throw new IOException("角色卡内容过多，无法在当前设备上安全导入", error);
        }
        validateWorldBook(card);
        return new Result(card, bytes, png);
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
        String legacyEncoded = null;
        while (offset + 12 <= bytes.length) {
            int length = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            if (length < 0 || offset + 12L + length > bytes.length) break;
            String type = new String(bytes, offset + 4, 4, StandardCharsets.ISO_8859_1);
            boolean metadata = "tEXt".equals(type)
                    || "zTXt".equals(type)
                    || "iTXt".equals(type);
            if (!metadata) {
                offset += length + 12;
                continue;
            }
            byte[] data = new byte[length];
            System.arraycopy(bytes, offset + 8, data, 0, length);

            String encodedV3 = null;
            String encodedLegacy = null;
            if ("tEXt".equals(type)) {
                encodedV3 = readTextChunk(data, "ccv3");
                encodedLegacy = readTextChunk(data, "chara");
            } else if ("zTXt".equals(type)) {
                encodedV3 = readCompressedTextChunk(data, "ccv3");
                encodedLegacy = readCompressedTextChunk(data, "chara");
            } else if ("iTXt".equals(type)) {
                encodedV3 = readInternationalTextChunk(data, "ccv3");
                encodedLegacy = readInternationalTextChunk(data, "chara");
            }
            if (encodedV3 != null && !encodedV3.isEmpty()) {
                return decodeJson(encodedV3);
            }
            if (legacyEncoded == null
                    && encodedLegacy != null && !encodedLegacy.isEmpty()) {
                legacyEncoded = encodedLegacy;
            }
            offset += length + 12;
        }
        if (legacyEncoded == null) return null;
        return decodeJson(legacyEncoded);
    }

    private static String decodeJson(String encoded) throws IOException {
        String clean = encoded == null ? "" : encoded.trim();
        final byte[] decoded;
        try {
            decoded = Base64.decode(clean, Base64.DEFAULT);
        } catch (IllegalArgumentException error) {
            throw new IOException("PNG 角色卡元数据不是有效的 Base64", error);
        }
        if (decoded.length == 0 || decoded.length > MAX_JSON_BYTES) {
            throw new IOException("PNG 内嵌角色卡 JSON 不能超过 16 MB");
        }
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static void validateWorldBook(CharacterCard card)
            throws IOException, JSONException {
        String json = card.worldBookJson == null ? "" : card.worldBookJson;
        if (json.isEmpty()) return;
        if (json.length() > MAX_WORLD_BOOK_BYTES
                || json.getBytes(StandardCharsets.UTF_8).length > MAX_WORLD_BOOK_BYTES) {
            throw new IOException("角色卡世界书不能超过 8 MB");
        }
        JSONArray entries = new JSONObject(json).optJSONArray("entries");
        if (entries != null && entries.length() > MAX_WORLD_BOOK_ENTRIES) {
            throw new IOException("角色卡世界书不能超过 1000000 条");
        }
    }

    private static String readTextChunk(byte[] data, String expectedKeyword) {
        int separator = indexOfZero(data, 0);
        if (separator < 0) return null;
        String key = new String(data, 0, separator, StandardCharsets.ISO_8859_1);
        if (!expectedKeyword.equals(key)) return null;
        return new String(data, separator + 1, data.length - separator - 1, StandardCharsets.ISO_8859_1);
    }

    private static String readCompressedTextChunk(
            byte[] data,
            String expectedKeyword) throws IOException {
        int separator = indexOfZero(data, 0);
        if (separator < 0 || separator + 2 >= data.length) return null;
        String key = new String(data, 0, separator, StandardCharsets.ISO_8859_1);
        if (!expectedKeyword.equals(key)) return null;
        ByteArrayInputStream compressed =
                new ByteArrayInputStream(data, separator + 2, data.length - separator - 2);
        return new String(readAll(
                new InflaterInputStream(compressed),
                MAX_CARD_FILE_BYTES), StandardCharsets.ISO_8859_1);
    }

    private static String readInternationalTextChunk(
            byte[] data,
            String expectedKeyword) throws IOException {
        int keyEnd = indexOfZero(data, 0);
        if (keyEnd < 0 || !expectedKeyword.equals(
                new String(data, 0, keyEnd, StandardCharsets.ISO_8859_1))) {
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
            text = readAll(
                    new InflaterInputStream(new ByteArrayInputStream(text)),
                    MAX_CARD_FILE_BYTES);
        }
        return new String(text, StandardCharsets.UTF_8);
    }

    private static int indexOfZero(byte[] bytes, int start) {
        for (int i = start; i < bytes.length; i++) {
            if (bytes[i] == 0) return i;
        }
        return -1;
    }

    private static byte[] readAll(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() > maxBytes - count) {
                throw new IOException("角色卡文件过大，无法安全导入");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
