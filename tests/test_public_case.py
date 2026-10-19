"""Public synthetic downloads remain bounded and scanned recursively."""
import importlib.util
import io
from pathlib import Path
import unittest
from unittest.mock import patch
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('case_audit', ROOT / 'scripts/audit-public-tree.py')
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def bundle(entries):
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, content in entries:
            archive.writestr(name, content)
    return output.getvalue()


class PublicCaseTests(unittest.TestCase):
    def test_os_metadata_is_not_added_to_public_candidates(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'docs').mkdir()
            (root / 'docs/.DS_Store').write_bytes(b'private OS metadata')
            (root / 'docs/case.md').write_text('# Synthetic\n')
            with patch.object(audit, 'ROOT', root), patch.object(audit, 'DIRECTORIES', ['docs']), patch.object(audit, 'FILES', []):
                self.assertEqual([root / 'docs/case.md'], audit.available_files())

    def test_xlsx_text_is_included_in_sensitive_scan(self):
        key = 'sk-' + 'a' * 30
        xlsx = bundle([('[Content_Types].xml', '<Types/>'), ('xl/sharedStrings.xml', key)])
        text = audit.archive_text(bundle([('estimate.xlsx', xlsx)]))
        self.assertIsNotNone(audit.CREDENTIAL.search(text))

    def test_runtime_paths_traversal_and_unreviewed_binary_refused(self):
        for name in ['../escape.md', '/escape.md', 'C:/escape.md', 'a\\b.md', '.env', '.env/material.md', 'secrets/value.txt', 'nested/secrets.txt', 'payload.bin']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                audit.archive_text(bundle([(name, 'synthetic')]))

    def test_compressed_oversize_is_refused_before_read(self):
        with self.assertRaises(ValueError):
            audit.archive_text(bundle([('large.md', 'a' * (21 * 1024 * 1024))]))

    def test_reviewed_synthetic_markdown_and_html_are_scanned(self):
        text = audit.archive_text(bundle([('PRD.md', '# 合成案例\n'), ('Demo.html', '<html>演示</html>')]))
        self.assertIn('合成案例', text)
        self.assertIn('演示', text)

    def test_nested_office_files_share_total_expansion_budget(self):
        xlsx = bundle([('xl/sharedStrings.xml', 'a' * (11 * 1024 * 1024))])
        with self.assertRaises(ValueError):
            audit.archive_text(bundle([('first.xlsx', xlsx), ('second.xlsx', xlsx)]))

    def test_archive_and_member_comments_and_extra_are_refused(self):
        for kind in ['archive', 'member', 'extra']:
            with self.subTest(kind=kind):
                output = io.BytesIO()
                with zipfile.ZipFile(output, 'w') as archive:
                    member = zipfile.ZipInfo('PRD.md')
                    if kind == 'archive':
                        archive.comment = b'synthetic private metadata'
                    elif kind == 'member':
                        member.comment = b'synthetic private metadata'
                    else:
                        member.extra = b'\xff\xff\x04\x00test'
                    archive.writestr(member, '# synthetic')
                with self.assertRaises(ValueError):
                    audit.archive_text(output.getvalue())


if __name__ == '__main__':
    unittest.main()
