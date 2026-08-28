#!/usr/bin/env python3
"""Lightweight Markdown local-link checker for repository docs."""

from __future__ import annotations

import pathlib
import re
import sys
from urllib.parse import unquote

LINK_RE = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
EXTERNAL_PREFIXES = ("http://", "https://", "mailto:", "#")


def main() -> int:
    root = pathlib.Path(__file__).resolve().parents[1]
    markdown_files = sorted(path for path in root.rglob("*.md") if ".git" not in path.parts)
    errors: list[str] = []
    for markdown_file in markdown_files:
        text = markdown_file.read_text(encoding="utf-8")
        for match in LINK_RE.finditer(text):
            target = match.group(1).strip()
            if target.startswith(EXTERNAL_PREFIXES):
                continue
            target_path = target.split("#", 1)[0]
            if not target_path:
                continue
            resolved = (markdown_file.parent / unquote(target_path)).resolve()
            try:
                resolved.relative_to(root)
            except ValueError:
                errors.append(f"{markdown_file.relative_to(root)}: link escapes repository: {target}")
                continue
            if not resolved.exists():
                errors.append(f"{markdown_file.relative_to(root)}: missing local link: {target}")
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print(f"checked {len(markdown_files)} markdown files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
