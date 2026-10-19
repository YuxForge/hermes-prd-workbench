#!/usr/bin/env python3
from pathlib import Path
import importlib.util
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("audit", ROOT / "scripts/audit-public-tree.py")
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)
errors = []
for path in audit.candidates():
    relative = path.relative_to(ROOT).as_posix()
    if path.name == "LICENSE" or path.suffix == ".svg" or relative in audit.PUBLIC_BINARY_ASSETS or relative in audit.PUBLIC_ARCHIVES:
        continue
    text = path.read_text()
    if not text.endswith("\n") or "\r" in text or any(line.rstrip() != line for line in text.splitlines()):
        errors.append((path, "newline/whitespace"))
    if path.suffix == ".md":
        if len(re.findall(r"^```", text, re.M)) % 2:
            errors.append((path, "unclosed fence"))
        for target in re.findall(r"\]\(([^)]+)\)", text):
            if "://" in target or target.startswith("#") or target.startswith("mailto:"):
                continue
            destination = (path.parent / target.split("#")[0]).resolve()
            if not destination.exists():
                errors.append((path, "missing local link"))
for path, reason in errors:
    print(f"FAIL {path.relative_to(ROOT)}: {reason}")
print(f"Text/Markdown checks: {len(errors)} findings")
sys.exit(1 if errors else 0)
