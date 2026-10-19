"""Tests for the GitHub Pages static showcase station and build pipeline."""
import importlib.util
from pathlib import Path
import re
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SHOWCASE_DIR = ROOT / "docs/showcase"

# Import build-showcase module
build_spec = importlib.util.spec_from_file_location("build_showcase", ROOT / "scripts/build-showcase.py")
build_mod = importlib.util.module_from_spec(build_spec)
build_spec.loader.exec_module(build_mod)


class ShowcaseStationTests(unittest.TestCase):
    def test_showcase_source_files_exist(self):
        for name in ["index.html", "showcase.css", "showcase.js", "procurement-demo.html"]:
            file_path = SHOWCASE_DIR / name
            self.assertTrue(file_path.is_file(), f"Expected {name} to exist in docs/showcase")
            self.assertGreater(file_path.stat().st_size, 100, f"{name} is unexpectedly small")

    def test_showcase_html_structure_and_stages(self):
        html_path = SHOWCASE_DIR / "index.html"
        content = html_path.read_text(encoding="utf-8")

        # 5 required product stages
        self.assertIn('id="stage-1"', content)
        self.assertIn('id="stage-2"', content)
        self.assertIn('id="stage-3"', content)
        self.assertIn('id="stage-4"', content)
        self.assertIn('id="stage-5"', content)

        # Conflict presentation
        self.assertIn("5,000", content)
        self.assertIn("10,000", content)
        self.assertIn("8,600", content)

        # Human decision and draft baseline
        self.assertIn("当前稿", content)
        self.assertIn("人工确认单", content)

        # Downstream deliverables
        self.assertIn("deliverables.zip", content)
        self.assertIn("TechSpec", content)
        self.assertIn("测试用例", content)
        self.assertIn("deliverables-explorer", content)
        self.assertIn("panel-prd", content)
        self.assertIn("panel-testcases", content)
        self.assertIn("panel-techspec", content)
        self.assertIn("panel-estimate", content)
        self.assertIn("PO-001", content)
        self.assertIn("po_order", content)
        self.assertIn("AC01", content)


    def test_prototype_iframe_sandbox_security(self):
        html_path = SHOWCASE_DIR / "index.html"
        content = html_path.read_text(encoding="utf-8")

        iframe_matches = re.findall(r"<iframe[^>]*>", content, re.I)
        self.assertTrue(len(iframe_matches) > 0, "No iframe found in index.html")

        for iframe in iframe_matches:
            self.assertIn('sandbox="allow-scripts"', iframe)
            self.assertNotIn("allow-same-origin", iframe)

    def test_showcase_local_links_resolve(self):
        html_path = SHOWCASE_DIR / "index.html"
        content = html_path.read_text(encoding="utf-8")

        # Check href, src and poster attributes that are relative local links
        targets = re.findall(r'(?:href|src|poster)="([^"#:]+)"', content)
        case_assets = {"deliverables.zip", "overview.jpg", "walkthrough.mp4"}
        for target in targets:
            if target.startswith("http://") or target.startswith("https://") or target.startswith("mailto:"):
                continue
            clean_target = target.lstrip("./")
            if clean_target in case_assets:
                resolved = (ROOT / "docs/examples/procurement" / clean_target).resolve()
            else:
                resolved = (SHOWCASE_DIR / target).resolve()
            self.assertTrue(resolved.exists(), f"Target {target} in index.html resolved to non-existent {resolved}")

    def test_build_showcase_execution(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            dist_path = Path(tmpdir) / "dist"
            success = build_mod.build_showcase(dist_path)
            self.assertTrue(success, "build_showcase returned False")
            self.assertTrue((dist_path / "index.html").is_file())
            self.assertTrue((dist_path / "showcase.css").is_file())
            self.assertTrue((dist_path / "showcase.js").is_file())
            self.assertTrue((dist_path / "procurement-demo.html").is_file())
            self.assertTrue((dist_path / "deliverables.zip").is_file())
            self.assertTrue((dist_path / ".nojekyll").is_file())
            self.assertTrue((dist_path / "docs/examples/procurement/deliverables.zip").is_file())


if __name__ == "__main__":
    unittest.main()
