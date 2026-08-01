from __future__ import annotations

import json
import logging
import sqlite3
import sys
import tempfile
import unicodedata
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any


WORKSPACE = Path(r"C:\Users\ikun\OneDrive\文档\sillytavern-pro")
LEGACY_WORKSPACE = Path(r"C:\Users\ikun\Documents\Codex\2026-07-23\github")
WECHAT_PROJECT = LEGACY_WORKSPACE / "WeChatDataAnalysis"
sys.path.insert(0, str(LEGACY_WORKSPACE))
sys.path.insert(0, str(WECHAT_PROJECT / "src"))

from extract_april_chats import (  # noqa: E402
    ACCOUNT_ROOT,
    CONTACT_DB,
    DB_STORAGE,
    WECHAT_INSTALL,
    _decrypt_one,
    _derive_own_username,
    _quote_ident,
    _text,
)
from wechat_decrypt_tool.chat_export_service import (  # noqa: E402
    _Row,
    _parse_message_for_export,
)
from wechat_decrypt_tool.chat_helpers import (  # noqa: E402
    _decode_message_content,
    _load_contact_rows,
    _load_group_nickname_map_from_contact_db,
    _pick_display_name,
    _resolve_msg_table_name,
)
from wechat_decrypt_tool.key_service import get_db_key_workflow  # noqa: E402
from wechat_decrypt_tool.wechat_decrypt import WeChatDatabaseDecryptor  # noqa: E402


OUTPUT_DIR = WORKSPACE / "output" / "points_sama_card"
SCOPED_OUTPUT = OUTPUT_DIR / "points_sama_april_scoped.json"
HEAD_IMAGE_DB = DB_STORAGE / "head_image" / "head_image.db"
MESSAGE_DBS = sorted(
    p
    for p in (DB_STORAGE / "message").glob("message_*.db")
    if p.stem.removeprefix("message_").isdigit()
)

TZ = timezone(timedelta(hours=8), name="Asia/Hong_Kong")
START = datetime(2026, 4, 1, 0, 0, 0, tzinfo=TZ)
END = datetime(2026, 5, 1, 0, 0, 0, tzinfo=TZ)

TARGET_CONVERSATIONS: dict[str, tuple[str, ...]] = {
    "私宅蒸鹅心": ("私宅蒸鹅心", "私宅蒸鵝心"),
    "私宅二代目": ("私宅二代目",),
    "香港gbc同好会": (
        "香港gbc同好会",
        "香港GBC同好会",
        "香港gbc同好會",
        "香港GBC同好會",
    ),
}
TARGET_PERSON_ALIASES = (
    "ポイントsama",
    "雞糞sama",
    "鸡粪sama",
)
@dataclass
class Message:
    time: str
    sender_username: str
    is_sent: bool
    render_type: str
    content: str


def _norm(value: str) -> str:
    return unicodedata.normalize("NFKC", _text(value)).casefold()


def _matches_person_name(value: str) -> bool:
    normalized = _norm(value)
    if not normalized:
        return False
    return normalized in {_norm(alias) for alias in TARGET_PERSON_ALIASES}


def _find_named_contacts(
    contact_db: Path,
    targets: dict[str, tuple[str, ...]],
) -> dict[str, str]:
    wanted: dict[str, str] = {}
    for label, aliases in targets.items():
        for alias in aliases:
            wanted[_norm(alias)] = label

    hits: dict[str, set[str]] = {label: set() for label in targets}
    conn = sqlite3.connect(str(contact_db))
    conn.row_factory = sqlite3.Row
    try:
        tables = [
            _text(row[0])
            for row in conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
            ).fetchall()
            if row and row[0]
        ]
        for table in tables:
            columns = [
                _text(row[1])
                for row in conn.execute(
                    f"PRAGMA table_info({_quote_ident(table)})"
                ).fetchall()
                if row and row[1]
            ]
            by_lower = {column.lower(): column for column in columns}
            username_col = by_lower.get("username") or by_lower.get("user_name")
            if not username_col:
                continue
            display_columns = [
                by_lower[name]
                for name in ("remark", "nick_name", "nickname", "alias")
                if name in by_lower
            ]
            if not display_columns:
                continue
            select_columns = ", ".join(
                [
                    f"{_quote_ident(username_col)} AS username",
                    *[
                        f"{_quote_ident(column)} AS {_quote_ident(column)}"
                        for column in display_columns
                    ],
                ]
            )
            try:
                rows = conn.execute(
                    f"SELECT {select_columns} FROM {_quote_ident(table)}"
                ).fetchall()
            except sqlite3.DatabaseError:
                continue
            for row in rows:
                username = _text(row["username"])
                if not username:
                    continue
                for column in display_columns:
                    label = wanted.get(_norm(row[column]))
                    if label:
                        hits[label].add(username)
    finally:
        conn.close()

    resolved: dict[str, str] = {}
    for label, usernames in hits.items():
        if len(usernames) != 1:
            raise RuntimeError(
                f"Conversation {label!r} resolved to {len(usernames)} internal accounts."
            )
        resolved[label] = next(iter(usernames))
    return resolved


def _find_person_contacts(contact_db: Path) -> set[str]:
    conn = sqlite3.connect(str(contact_db))
    conn.row_factory = sqlite3.Row
    candidates: set[str] = set()
    try:
        tables = [
            _text(row[0])
            for row in conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
            ).fetchall()
            if row and row[0]
        ]
        for table in tables:
            columns = [
                _text(row[1])
                for row in conn.execute(
                    f"PRAGMA table_info({_quote_ident(table)})"
                ).fetchall()
                if row and row[1]
            ]
            by_lower = {column.lower(): column for column in columns}
            username_col = by_lower.get("username") or by_lower.get("user_name")
            display_columns = [
                by_lower[name]
                for name in ("remark", "nick_name", "nickname", "alias")
                if name in by_lower
            ]
            if not username_col or not display_columns:
                continue
            select_columns = ", ".join(
                [
                    f"{_quote_ident(username_col)} AS username",
                    *[
                        f"{_quote_ident(column)} AS {_quote_ident(column)}"
                        for column in display_columns
                    ],
                ]
            )
            try:
                rows = conn.execute(
                    f"SELECT {select_columns} FROM {_quote_ident(table)}"
                ).fetchall()
            except sqlite3.DatabaseError:
                continue
            for row in rows:
                if any(_matches_person_name(_text(row[column])) for column in display_columns):
                    username = _text(row["username"])
                    if username:
                        candidates.add(username)
    finally:
        conn.close()
    return candidates


def _read_conversation_from_db(
    db_path: Path,
    *,
    conversation_username: str,
    own_username: str,
) -> list[Message]:
    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row
    try:
        conn.text_factory = str
        table_name = _resolve_msg_table_name(conn, conversation_username)
        if not table_name:
            return []

        conn.text_factory = bytes
        own_row = conn.execute(
            "SELECT rowid FROM Name2Id WHERE user_name = ? LIMIT 1",
            (own_username,),
        ).fetchone()
        own_rowid = int(own_row[0]) if own_row else -1
        columns = {
            _text(row[1]).lower()
            for row in conn.execute(
                f"PRAGMA table_info({_quote_ident(table_name)})"
            ).fetchall()
        }
        packed_select = (
            "m.packed_info_data AS packed_info_data"
            if "packed_info_data" in columns
            else "NULL AS packed_info_data"
        )
        rows = conn.execute(
            "SELECT m.local_id, m.server_id, m.local_type, m.sort_seq, "
            "m.real_sender_id, m.create_time, m.message_content, m.compress_content, "
            f"{packed_select}, n.user_name AS sender_username "
            f"FROM {_quote_ident(table_name)} m "
            "LEFT JOIN Name2Id n ON m.real_sender_id = n.rowid "
            "WHERE m.create_time >= ? AND m.create_time < ? "
            "ORDER BY m.create_time ASC, m.sort_seq ASC, m.local_id ASC",
            (int(START.timestamp()), int(END.timestamp())),
        ).fetchall()

        output: list[Message] = []
        for db_row in rows:
            sender_username = _text(db_row["sender_username"])
            is_sent = int(db_row["real_sender_id"] or 0) == own_rowid
            if is_sent:
                sender_username = own_username
            raw_text = _decode_message_content(
                db_row["compress_content"],
                db_row["message_content"],
            ).strip()
            row = _Row(
                db_stem=db_path.stem,
                table_name=table_name,
                local_id=int(db_row["local_id"] or 0),
                server_id=int(db_row["server_id"] or 0),
                local_type=int(db_row["local_type"] or 0),
                sort_seq=int(db_row["sort_seq"] or 0),
                create_time=int(db_row["create_time"] or 0),
                raw_text=raw_text,
                sender_username=sender_username,
                is_sent=is_sent,
                packed_info_data=db_row["packed_info_data"],
            )
            parsed = _parse_message_for_export(
                row=row,
                conv_username=conversation_username,
                is_group=True,
                resource_conn=None,
                resource_chat_id=None,
            )
            parsed_sender = _text(parsed.get("senderUsername")) or sender_username
            content = _text(parsed.get("content"))
            if not content:
                content = f"[{_text(parsed.get('renderType')) or 'message'}]"
            output.append(
                Message(
                    time=datetime.fromtimestamp(
                        int(parsed.get("createTime") or 0),
                        TZ,
                    ).isoformat(timespec="seconds"),
                    sender_username=parsed_sender,
                    is_sent=is_sent,
                    render_type=_text(parsed.get("renderType")) or "text",
                    content=content,
                )
            )
        return output
    finally:
        conn.close()


def _anonymize_selected(
    messages: list[Message],
    *,
    own_username: str,
    person_username: str,
    context_radius: int = 2,
) -> tuple[list[dict[str, str]], int]:
    target_indices = {
        index
        for index, message in enumerate(messages)
        if message.sender_username == person_username
    }
    selected_indices: set[int] = set()
    for index in target_indices:
        start = max(0, index - context_radius)
        end = min(len(messages), index + context_radius + 1)
        selected_indices.update(range(start, end))

    aliases: dict[str, str] = {}
    selected: list[dict[str, str]] = []
    previous_index: int | None = None
    for index in sorted(selected_indices):
        message = messages[index]
        if message.sender_username == person_username:
            sender = "ポイントsama"
        elif message.is_sent or message.sender_username == own_username:
            sender = "user"
        elif message.render_type == "system":
            sender = "system"
        else:
            if message.sender_username not in aliases:
                aliases[message.sender_username] = f"group_member_{len(aliases) + 1}"
            sender = aliases[message.sender_username]
        if previous_index is not None and index > previous_index + 1:
            selected.append(
                {
                    "time": "",
                    "sender": "system",
                    "type": "separator",
                    "content": "[中间未选取的消息已省略]",
                }
            )
        selected.append(
            {
                "time": message.time,
                "sender": sender,
                "type": message.render_type,
                "content": message.content,
            }
        )
        previous_index = index
    return selected, len(target_indices)


def _save_avatar(
    *,
    decryptor: WeChatDatabaseDecryptor,
    person_username: str,
    temp_dir: Path,
) -> Path | None:
    head_image_out = temp_dir / "head_image.db"
    _decrypt_one(decryptor, HEAD_IMAGE_DB, head_image_out)
    conn = sqlite3.connect(str(head_image_out))
    try:
        row = conn.execute(
            "SELECT image_buffer FROM head_image "
            "WHERE username = ? ORDER BY update_time DESC LIMIT 1",
            (person_username,),
        ).fetchone()
    finally:
        conn.close()
    if not row or row[0] is None:
        return None
    data = bytes(row[0])
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        extension = "png"
    elif data.startswith(b"\xff\xd8\xff"):
        extension = "jpg"
    elif data.startswith((b"GIF87a", b"GIF89a")):
        extension = "gif"
    elif data.startswith(b"RIFF") and data[8:12] == b"WEBP":
        extension = "webp"
    else:
        raise RuntimeError("The matched avatar has an unrecognized image format.")
    output_path = OUTPUT_DIR / f"points_sama_wechat_avatar_original.{extension}"
    output_path.write_bytes(data)
    return output_path


def main() -> int:
    logging.disable(logging.INFO)
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    if not MESSAGE_DBS:
        raise RuntimeError("No WeChat message databases were found.")

    key_result = get_db_key_workflow(
        wechat_install_path=str(WECHAT_INSTALL),
        db_storage_path=str(DB_STORAGE),
        key_mode="v4",
    )
    key_hex = _text(key_result.get("db_key"))
    if len(key_hex) != 64:
        raise RuntimeError("Key recovery returned an invalid key.")

    with tempfile.TemporaryDirectory(
        prefix="wechat_points_april_scope_",
        dir=str(WORKSPACE),
    ) as temp_name:
        temp_dir = Path(temp_name)
        contact_out = temp_dir / "contact.db"
        decryptor = WeChatDatabaseDecryptor(key_hex)
        _decrypt_one(decryptor, CONTACT_DB, contact_out)
        conversation_usernames = _find_named_contacts(
            contact_out,
            TARGET_CONVERSATIONS,
        )
        direct_person_candidates = _find_person_contacts(contact_out)

        decrypted_messages: list[Path] = []
        for source in MESSAGE_DBS:
            destination = temp_dir / source.name
            _decrypt_one(decryptor, source, destination)
            decrypted_messages.append(destination)

        own_username = ""
        for db_path in decrypted_messages:
            conn = sqlite3.connect(str(db_path))
            try:
                own_username = _derive_own_username(conn)
                if own_username:
                    break
            except RuntimeError:
                continue
            finally:
                conn.close()
        if not own_username:
            raise RuntimeError("Could not identify the local WeChat account.")

        messages_by_conversation: dict[str, list[Message]] = {}
        sender_display_candidates: dict[str, set[str]] = {}
        senders_seen: set[str] = set()
        for label, conversation_username in conversation_usernames.items():
            messages: list[Message] = []
            for db_path in decrypted_messages:
                messages.extend(
                    _read_conversation_from_db(
                        db_path,
                        conversation_username=conversation_username,
                        own_username=own_username,
                    )
                )
            messages.sort(key=lambda message: message.time)
            messages_by_conversation[label] = messages

            senders = list(
                dict.fromkeys(
                    message.sender_username
                    for message in messages
                    if message.sender_username
                )
            )
            senders_seen.update(senders)
            contact_rows = _load_contact_rows(contact_out, senders)
            group_names = _load_group_nickname_map_from_contact_db(
                contact_out,
                conversation_username,
                senders,
            )
            for sender in senders:
                values = sender_display_candidates.setdefault(sender, set())
                group_name = _text(group_names.get(sender))
                contact_name = _pick_display_name(contact_rows.get(sender), sender)
                if group_name:
                    values.add(group_name)
                if contact_name and contact_name != sender:
                    values.add(contact_name)

        matched_by_display = {
            sender
            for sender, names in sender_display_candidates.items()
            if any(_matches_person_name(name) for name in names)
        }
        matched_direct = direct_person_candidates.intersection(senders_seen)
        person_candidates = matched_by_display.union(matched_direct)
        if len(person_candidates) != 1:
            safe_matches = sorted(
                {
                    name
                    for sender in person_candidates
                    for name in sender_display_candidates.get(sender, set())
                    if _matches_person_name(name)
                }
            )
            raise RuntimeError(
                "Could not resolve ポイントsama to exactly one sender in the "
                f"authorized chats; candidate_count={len(person_candidates)}, "
                f"matched_names={safe_matches}"
            )
        person_username = next(iter(person_candidates))

        selected_conversations: dict[str, list[dict[str, str]]] = {}
        counts: dict[str, dict[str, int]] = {}
        for label, messages in messages_by_conversation.items():
            selected, target_count = _anonymize_selected(
                messages,
                own_username=own_username,
                person_username=person_username,
            )
            selected_conversations[label] = selected
            counts[label] = {
                "all_messages_in_april": len(messages),
                "target_messages": target_count,
                "selected_with_context": len(selected),
            }

        if sum(item["target_messages"] for item in counts.values()) == 0:
            raise RuntimeError("The matched person has no April messages in the selected chats.")

        avatar_path = _save_avatar(
            decryptor=decryptor,
            person_username=person_username,
            temp_dir=temp_dir,
        )
        payload = {
            "scope": {
                "timezone": "Asia/Hong_Kong",
                "start_inclusive": START.isoformat(),
                "end_exclusive": END.isoformat(),
                "conversations": list(TARGET_CONVERSATIONS),
                "target": "雞糞sama（现 ID：ポイントsama）",
                "privacy": (
                    "Only the three named conversations were queried. Other senders "
                    "were pseudonymized, raw WeChat identifiers were omitted, and "
                    "temporary decrypted databases were deleted after extraction."
                ),
            },
            "counts": counts,
            "conversations": selected_conversations,
        }
        SCOPED_OUTPUT.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    print(
        json.dumps(
            {
                "status": "ok",
                "scope": {
                    "start": START.isoformat(),
                    "end": END.isoformat(),
                    "conversations": list(TARGET_CONVERSATIONS),
                },
                "counts": counts,
                "target_aliases_seen": sorted(
                    {
                        name
                        for name in sender_display_candidates.get(person_username, set())
                        if _matches_person_name(name)
                    }
                ),
                "avatar_cached": bool(avatar_path),
                "avatar_format": avatar_path.suffix.lower().removeprefix(".")
                if avatar_path
                else "",
                "scoped_output": str(SCOPED_OUTPUT),
                "temporary_plaintext_removed": True,
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
