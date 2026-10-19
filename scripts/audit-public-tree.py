#!/usr/bin/env python3
"""Check the candidate tree, not ignored user files or historical Git objects. Never print matching secrets."""
from pathlib import Path
from pathlib import PurePosixPath
import io
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DIRECTORIES = ["apps/backend/src", "apps/web/src", "apps/web/public", "integrations", "contracts", "config", "deploy", "scripts", "tests", "docs", ".github"]
FILES = ["README.md", "README.zh-CN.md", "AGENTS.md", "LICENSE", "NOTICE", "CONTRIBUTING.md", "SECURITY.md", "CODE_OF_CONDUCT.md", "CHANGELOG.md", ".env.example", ".gitignore", ".dockerignore", ".editorconfig", "compose.yaml", "workbench", "apps/backend/pom.xml", "apps/backend/mvnw", "apps/backend/mvnw.cmd", "apps/backend/.mvn/wrapper/maven-wrapper.properties", "apps/web/package.json", "apps/web/pnpm-lock.yaml", "apps/web/index.html", "apps/web/tsconfig.json", "apps/web/vite.config.ts", "apps/web/pnpm-workspace.yaml", "config/public-files.txt"]
FORBIDDEN = re.compile("(?:" + "kuai" + "lu|快" + "鹭|192\\.168\\.102\\.|/Users/[A-Za-z0-9_.-]+/|/home/" + "deploy/|prd-" + "review|work" + "buddy-ai/memory)", re.I)
CREDENTIAL = re.compile(r"(?:sk-[A-Za-z0-9_-]{24,}|gh[pousr]_[A-Za-z0-9]{30,}|AKIA[0-9A-Z]{16}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)")
PUBLIC_BINARY_ASSETS = {
    "docs/assets/hermes-prd-workbench-demo.gif": b"GIF89a",
    "docs/assets/mobile-workbench.jpg": b"\xff\xd8\xff",
    "docs/assets/poc-preview.jpg": b"\xff\xd8\xff",
    "docs/assets/sandboxed-preview.jpg": b"\xff\xd8\xff",
    "docs/assets/social-preview.jpg": b"\xff\xd8\xff",
    "docs/assets/workbench-demo.jpg": b"\xff\xd8\xff",
    "docs/assets/hermes-prd-workbench-logical-architecture.png": b"\x89PNG\r\n\x1a\n",
    "docs/assets/hermes-prd-workbench-deployment-architecture.png": b"\x89PNG\r\n\x1a\n",
    "docs/examples/procurement/overview.jpg": b"\xff\xd8\xff",
    "docs/examples/procurement/walkthrough.mp4": b"ftyp",
}
MAX_PUBLIC_BINARY_BYTES = 2 * 1024 * 1024
PUBLIC_ARCHIVES = {"docs/examples/procurement/deliverables.zip"}
PUBLIC_VIDEO = "docs/examples/procurement/walkthrough.mp4"


def archive_text(raw, office=False, budget=None):
    """Inspect the one reviewed synthetic export, including XLSX XML, without extracting."""
    texts = []
    if budget is None:
        budget = {"entries": 500, "bytes": 20 * 1024 * 1024}
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        if archive.comment:
            raise ValueError("Public archive comment refused")
        entries = archive.infolist()
        budget["entries"] -= len(entries)
        budget["bytes"] -= sum(entry.file_size for entry in entries)
        if budget["entries"] < 0 or budget["bytes"] < 0:
            raise ValueError("Public archive exceeds expansion limits")
        if len({entry.filename for entry in entries}) != len(entries):
            raise ValueError("Duplicate public archive member")
        for entry in entries:
            name = entry.filename
            path = PurePosixPath(name)
            if path.is_absolute() or '..' in path.parts or '\\' in name or ':' in name:
                raise ValueError("Unsafe public archive path")
            if (entry.external_attr >> 16) & 0o170000 == 0o120000 or entry.flag_bits & 1:
                raise ValueError("Linked/encrypted public archive member")
            if entry.comment or entry.extra:
                raise ValueError("Public archive member metadata refused")
            if any(FORBIDDEN_NAME.search(part) for part in path.parts):
                raise ValueError("Runtime member in public archive")
            if entry.is_dir():
                continue
            data = archive.read(entry)
            texts.append(name)
            if not office and path.suffix == '.xlsx':
                texts.append(archive_text(data, office=True, budget=budget))
            elif (office and path.name == '.rels') or path.suffix in ({'.xml', '.rels'} if office else {'.md', '.json', '.html', '.css', '.js', '.txt'}):
                texts.append(data.decode('utf-8'))
            else:
                raise ValueError("Unreviewed archive member type")
    return '\n'.join(texts)


EXCLUDED_PARTS = {"__pycache__", "node_modules", "target", ".venv", "dist", "coverage", ".runtime", "logs", "backups", ".DS_Store"}
SOURCE_NAME_EXCEPTIONS = {
    "apps/backend/src/main/java/org/hermesprd/workbench/auth/Credentials.java",
    "apps/backend/src/test/java/org/hermesprd/workbench/auth/CredentialsTest.java",
    "apps/backend/src/main/java/org/hermesprd/workbench/shared/SecretFiles.java",
}
FORBIDDEN_NAME = re.compile(r"^(?:\.env(?:\..*)?|\.npmrc|credentials.*|secrets?.*|id_rsa.*)$|\.(?:log|bak|dump|pem|key|zip|tar|gz|sqlite|db)$", re.I)


def available_files():
    seen = set()
    for directory in DIRECTORIES:
        base = ROOT / directory
        if base.is_symlink():
            raise ValueError("Public directory cannot be a symlink")
        for path in base.rglob("*"):
            relative = path.relative_to(ROOT)
            if any(part in EXCLUDED_PARTS for part in relative.parts):
                continue
            if path.is_symlink():
                raise ValueError("Symlink refused: " + relative.as_posix())
            if path.is_file():
                if FORBIDDEN_NAME.search(path.name) and relative.as_posix() not in SOURCE_NAME_EXCEPTIONS | PUBLIC_ARCHIVES:
                    raise ValueError("Runtime/secret file refused: " + relative.as_posix())
                seen.add(path)
    for name in FILES:
        path = ROOT / name
        if path.is_symlink():
            raise ValueError("Symlink refused: " + name)
        if path.is_file():
            seen.add(path)
    return sorted(seen)


def candidates():
    paths = available_files()
    manifest = ROOT / "config/public-files.txt"
    if not manifest.is_file():
        raise ValueError("Reviewed public file manifest is required")
    approved = set(manifest.read_text().splitlines())
    actual = {path.relative_to(ROOT).as_posix() for path in paths}
    if actual != approved:
        raise ValueError("Public file manifest differs from the tree; inspect changes and refresh explicitly")
    return paths


def audit():
    failures = []
    try:
        paths = candidates()
    except ValueError as ex:
        print("FAIL " + str(ex))
        return False
    for path in paths:
        relative = path.relative_to(ROOT).as_posix()
        raw = path.read_bytes()
        if relative in PUBLIC_ARCHIVES:
            try:
                if len(raw) > MAX_PUBLIC_BINARY_BYTES:
                    raise ValueError("Public archive exceeds size limit")
                text = archive_text(raw)
            except (ValueError, UnicodeError, zipfile.BadZipFile, RuntimeError, NotImplementedError):
                failures.append((relative, "invalid or unsafe public archive"))
                continue
            if FORBIDDEN.search(text) or CREDENTIAL.search(text):
                failures.append((relative, "private/credential marker in public archive"))
            continue
        signature = PUBLIC_BINARY_ASSETS.get(relative)
        if signature:
            signature_bytes = raw[4:] if relative == PUBLIC_VIDEO else raw
            if not signature_bytes.startswith(signature):
                failures.append((relative, "public image has an invalid signature"))
                continue
            limit = 8 * 1024 * 1024 if relative == PUBLIC_VIDEO else MAX_PUBLIC_BINARY_BYTES
            if len(raw) > limit:
                failures.append((relative, "public binary exceeds size limit"))
                continue
            if b"Exif\x00\x00" in raw:
                failures.append((relative, "public image contains EXIF metadata"))
                continue
            text = raw.decode("latin-1")
        else:
            try:
                text = raw.decode("utf-8")
            except UnicodeError:
                failures.append((relative, "unreviewed binary"))
                continue
        if FORBIDDEN.search(text):
            failures.append((relative, "private identity/topology marker"))
            continue
        for line in text.splitlines():
            if CREDENTIAL.search(line):
                # Exact synthetic adversarial literals in credential-validator tests are intentional.
                if path.name in {"HtmlPayloadTest.java", "ResultValidatorTest.java", "demoPackage.test.ts"} and any(word in line.lower() for word in ["fake", "dummy", "test", "private key"]):
                    continue
                failures.append((relative, "credential candidate; inspect privately"))
                break
    for path, reason in failures:
        print(f"FAIL {path}: {reason}")
    print(f"Candidate scan: {len(paths)} files, {len(failures)} findings (no matching values printed).")
    return not failures


if __name__ == "__main__":
    if sys.argv[1:] == ["--refresh-manifest"]:
        manifest = ROOT / "config/public-files.txt"
        manifest.parent.mkdir(parents=True, exist_ok=True)
        manifest.touch(exist_ok=True)
        manifest.write_text("\n".join(p.relative_to(ROOT).as_posix() for p in available_files()) + "\n")
        print("Manifest refreshed. Independently review added/removed paths before release.")
    else:
        sys.exit(0 if audit() else 1)
