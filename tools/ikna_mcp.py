"""Opt-in local MCP tools for preparing Ikna's plain-text imports."""
import argparse
import hashlib
from pathlib import Path
import sys
import tempfile
import unicodedata
import uuid

MAX_CARDS = 1000


def normalized(value: str) -> str:
    return " ".join(unicodedata.normalize("NFC", value).casefold().split())


def pack_text(cards: list[dict[str, str]]) -> str:
    if not 1 <= len(cards) <= MAX_CARDS:
        raise ValueError(f"Supply between 1 and {MAX_CARDS} cards")
    seen = set()
    lines = []
    for card in cards:
        raw = tuple(card.get(key, "") for key in ("expression", "example", "meaning"))
        fields = tuple(unicodedata.normalize("NFC", field.strip()) for field in raw)
        if any(not field or len(field) > 2000 or any(ch in field for ch in "|\r\n\0") for field in fields):
            raise ValueError("Fill each field on one line without pipes")
        expression, example, meaning = fields
        if normalized(expression) not in normalized(example):
            raise ValueError(f"Expression is missing from its example: {expression}")
        key = normalized(expression)
        if key in seen:
            raise ValueError(f"Repeated expression in batch: {expression}")
        seen.add(key)
        lines.append(" | ".join(fields))
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--enable-deck-tools", action="store_true")
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    if not args.enable_deck_tools:
        parser.error("Explicitly opt in with --enable-deck-tools after reviewing these permissions")
    print("Ikna deck tools create local import files. Use a model that knows the language or subject well and verify its examples and meanings.", file=sys.stderr)
    from mcp.server.fastmcp import FastMCP

    folder = args.output_dir.resolve()
    folder.mkdir(parents=True, exist_ok=True)
    server = FastMCP("Ikna deck preparation")
    previews: dict[str, tuple[str, int, str]] = {}

    @server.tool()
    def preview_cards(topic: str, level: str, style: str, count: int, deck: str,
                      cards: list[dict[str, str]]) -> dict[str, str]:
        """After asking for missing brief details, show this sample to the learner and request approval."""
        if not all(field.strip() for field in (topic, level, style, deck)):
            raise ValueError("Provide topic, level, style and the target deck")
        if not 1 <= count <= MAX_CARDS or not min(3, count) <= len(cards) <= min(5, count):
            raise ValueError("Use a sample of 3–5 cards, or the full sample for a smaller batch")
        sample = pack_text(cards)
        token = hashlib.sha256((deck + "\0" + str(count) + "\0" + sample).encode()).hexdigest()
        if len(previews) >= 16:
            previews.pop(next(iter(previews)))
        previews[token] = (deck.strip(), count, sample)
        return {"deck": deck.strip(), "sample": sample, "approval_token": token}

    @server.tool()
    def prepare_import(approval_token: str, learner_confirmed: bool,
                       cards: list[dict[str, str]]) -> dict[str, str | int]:
        """After the learner approves the sample and destination, prepare one local import file."""
        preview = previews.get(approval_token)
        if not learner_confirmed or preview is None:
            raise ValueError("Show a preview and obtain learner confirmation first")
        deck, count, sample = preview
        content = pack_text(cards)
        if len(cards) != count or not content.startswith(sample):
            raise ValueError("Start the batch with the approved sample and match the confirmed count")
        destination = folder / ("ikna-import-" + uuid.uuid4().hex + ".txt")
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=folder, delete=False) as handle:
            temporary = Path(handle.name)
            try:
                handle.write(content)
            except BaseException:
                handle.close()
                temporary.unlink(missing_ok=True)
                raise
        try:
            temporary.replace(destination)
        finally:
            temporary.unlink(missing_ok=True)
        previews.pop(approval_token, None)
        return {"file": str(destination), "deck": deck, "prepared": count}

    server.run(transport="stdio")


if __name__ == "__main__":
    main()
