import Foundation

enum CharacterCardFileImporter {
    struct Payload {
        let json: Data
        let avatarPNG: Data?
    }

    private static let pngSignature: [UInt8] = [
        0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    ]
    private static let maxCardFileBytes = 48 * 1024 * 1024
    private static let maxJSONBytes = 16 * 1024 * 1024

    static func load(from url: URL) throws -> Data {
        if let fileSize = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize,
           fileSize > maxCardFileBytes {
            throw KiraError.message(NSLocalizedString(
                "角色卡文件不能超过 48 MB", comment: ""))
        }
        let data = try Data(contentsOf: url, options: .mappedIfSafe)
        guard data.count <= maxCardFileBytes else {
            throw KiraError.message(NSLocalizedString(
                "角色卡文件不能超过 48 MB", comment: ""))
        }
        return data
    }

    static func decode(_ data: Data) throws -> Payload {
        guard data.count <= maxCardFileBytes else {
            throw KiraError.message(NSLocalizedString(
                "角色卡文件不能超过 48 MB", comment: ""))
        }
        guard isPNG(data) else {
            guard data.count <= maxJSONBytes else {
                throw KiraError.message(NSLocalizedString(
                    "JSON 角色卡不能超过 16 MB", comment: ""))
            }
            return Payload(json: data, avatarPNG: nil)
        }
        let json = try extractCharacterJSON(from: data)
        return Payload(json: json, avatarPNG: data)
    }

    private static func isPNG(_ data: Data) -> Bool {
        guard data.count >= pngSignature.count else { return false }
        return data.prefix(pngSignature.count).elementsEqual(pngSignature)
    }

    private static func extractCharacterJSON(from png: Data) throws -> Data {
        var offset = pngSignature.count
        var legacyEncoded: String?
        while offset + 12 <= png.count {
            guard let length = uint32BE(png, at: offset),
                  length <= png.count - offset - 12 else {
                throw KiraError.message(NSLocalizedString(
                    "PNG 角色卡结构无效", comment: ""))
            }
            let typeStart = offset + 4
            let payloadStart = offset + 8
            let payloadEnd = payloadStart + length
            let type = String(
                data: png.subdata(in: typeStart..<(typeStart + 4)),
                encoding: .isoLatin1) ?? ""
            if type == "tEXt",
               let field = textField(png.subdata(in: payloadStart..<payloadEnd)) {
                if field.keyword == "ccv3" {
                    return try decodeBase64JSON(field.value, keyword: field.keyword)
                }
                if field.keyword == "chara", legacyEncoded == nil {
                    legacyEncoded = field.value
                }
            }
            offset = payloadEnd + 4
        }
        if let legacyEncoded {
            return try decodeBase64JSON(legacyEncoded, keyword: "chara")
        }
        throw KiraError.message(NSLocalizedString(
            "PNG 中没有找到 ccv3 或 chara 角色卡数据", comment: ""))
    }

    private static func textField(_ data: Data) -> (keyword: String, value: String)? {
        guard let separator = data.firstIndex(of: 0) else { return nil }
        let keyword = String(data: data[..<separator], encoding: .isoLatin1) ?? ""
        let valueStart = data.index(after: separator)
        let value = String(data: data[valueStart...], encoding: .isoLatin1) ?? ""
        return (keyword, value)
    }

    private static func decodeBase64JSON(
        _ encoded: String,
        keyword: String
    ) throws -> Data {
        guard let decoded = Data(
            base64Encoded: encoded.trimmingCharacters(in: .whitespacesAndNewlines),
            options: .ignoreUnknownCharacters),
              !decoded.isEmpty,
              String(data: decoded, encoding: .utf8) != nil else {
            throw KiraError.message(String(
                format: NSLocalizedString("PNG %@ 元数据不是有效的 Base64 UTF-8 JSON", comment: ""),
                keyword))
        }
        guard decoded.count <= maxJSONBytes else {
            throw KiraError.message(NSLocalizedString(
                "PNG 内嵌角色卡 JSON 不能超过 16 MB", comment: ""))
        }
        return decoded
    }

    private static func uint32BE(_ data: Data, at offset: Int) -> Int? {
        guard offset >= 0, offset + 4 <= data.count else { return nil }
        return (Int(data[offset]) << 24)
            | (Int(data[offset + 1]) << 16)
            | (Int(data[offset + 2]) << 8)
            | Int(data[offset + 3])
    }
}
