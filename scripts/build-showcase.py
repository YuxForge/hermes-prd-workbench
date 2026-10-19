#!/usr/bin/env python3
"""Build and bundle the static showcase station for GitHub Pages deployment."""
import argparse
from pathlib import Path
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]
SHOWCASE_DIR = ROOT / "docs/showcase"
EXAMPLES_DIR = ROOT / "docs/examples/procurement"


def build_showcase(dist_dir: Path) -> bool:
    print(f"Building showcase into: {dist_dir}")
    dist_dir.mkdir(parents=True, exist_ok=True)

    # 1. Copy docs/showcase static files
    for item in SHOWCASE_DIR.glob("*"):
        if item.is_file():
            shutil.copy2(item, dist_dir / item.name)
            print(f"  Copied {item.name} ({item.stat().st_size} bytes)")

    # 2. Copy case assets for both root and relative paths
    nested_examples = dist_dir / "docs/examples/procurement"
    nested_examples.mkdir(parents=True, exist_ok=True)

    assets_to_copy = [
        "deliverables.zip",
        "overview.jpg",
        "walkthrough.mp4",
    ]

    for asset_name in assets_to_copy:
        src = EXAMPLES_DIR / asset_name
        if not src.exists():
            print(f"ERROR: Missing expected asset {src}", file=sys.stderr)
            return False
        # Copy to root of dist
        shutil.copy2(src, dist_dir / asset_name)
        # Copy to nested path to preserve relative ../examples/procurement/ links
        shutil.copy2(src, nested_examples / asset_name)
        print(f"  Copied asset {asset_name} ({src.stat().st_size} bytes)")

    # 3. Create .nojekyll for GitHub Pages
    nojekyll = dist_dir / ".nojekyll"
    nojekyll.write_text("")

    # 4. Verification checks
    index_html = dist_dir / "index.html"
    if not index_html.exists():
        print("ERROR: index.html was not generated in dist", file=sys.stderr)
        return False

    content = index_html.read_text(encoding="utf-8")
    import re
    iframe_matches = re.findall(r'<iframe[^>]*>', content, re.I)
    if not iframe_matches:
        print("ERROR: No iframe found in index.html", file=sys.stderr)
        return False

    for iframe in iframe_matches:
        if 'sandbox="allow-scripts"' not in iframe:
            print("ERROR: Sandbox iframe missing required sandbox attribute", file=sys.stderr)
            return False
        if "allow-same-origin" in iframe:
            print("ERROR: Unsafe allow-same-origin detected in iframe sandbox", file=sys.stderr)
            return False

    demo_html = dist_dir / "procurement-demo.html"
    if not demo_html.exists() or demo_html.stat().st_size < 1000:
        print("ERROR: procurement-demo.html missing or too small", file=sys.stderr)
        return False

    zip_file = dist_dir / "deliverables.zip"
    if not zip_file.exists() or zip_file.stat().st_size < 10000:
        print("ERROR: deliverables.zip missing or invalid", file=sys.stderr)
        return False

    print("Showcase build completed and verified successfully.")
    return True


def main():
    parser = argparse.ArgumentParser(description="Build static showcase for GitHub Pages")
    parser.add_argument("--dist", type=Path, default=ROOT / "_site", help="Output directory")
    args = parser.parse_args()

    success = build_showcase(args.dist)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
