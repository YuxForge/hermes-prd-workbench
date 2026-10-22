import json
from pathlib import Path
import unittest

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]


class PublicContractTests(unittest.TestCase):
    def test_all_result_schemas_are_valid(self):
        schemas = list((ROOT / 'contracts').glob('*.schema.json'))
        self.assertEqual(7, len(schemas))
        for path in schemas:
            with self.subTest(schema=path.name):
                Draft202012Validator.check_schema(json.loads(path.read_text()))

    def test_estimate_keeps_fractional_effort_and_explicit_scope(self):
        schema = json.loads((ROOT / 'contracts/estimate-result.schema.json').read_text())
        payload = {'mode': 'ESTIMATE', 'title': 'Synthetic estimate', 'summary': 'Explicit assumptions',
                   'scopeNote': 'User supplied scope includes design', 'totalDays': 12.75,
                   'items': [{'module': 'note', 'name': 'List', 'type': 'PAGE', 'devDays': 7.5,
                              'testDays': 5.25, 'complexity': '中'}]}
        Draft202012Validator(schema).validate(payload)
        payload['items'][0]['devDays'] = -1
        self.assertTrue(list(Draft202012Validator(schema).iter_errors(payload)))

    def test_browser_only_technical_contract_preserves_local_shapes(self):
        schema = json.loads((ROOT / 'contracts/tech-spec-result.schema.json').read_text())
        payload = {'mode': 'TECH_SPEC', 'title': 'Synthetic local app', 'summary': 'No server database',
                   'architectureMarkdown': 'Local storage adapter',
                   'databaseTables': [{'tableName': 'note', 'columns': [{'name': 'id', 'type': 'string'}],
                                       'indexes': [{'name': 'order', 'columns': ['createdAt', 'id'], 'intent': 'local sorting'}]}],
                   'apiEndpoints': [{'method': 'LOCAL', 'path': 'Store.save', 'summary': 'Save',
                                     'errorCodes': 'STORAGE_UNAVAILABLE: memory fallback'},
                                    {'method': 'VIEW', 'path': 'List.render', 'summary': 'Render',
                                     'errorCodes': ['TITLE_REQUIRED', {'code': 'SAVE_FAILED'}]}]}
        Draft202012Validator(schema).validate(payload)
        payload['apiEndpoints'][0]['method'] = ''
        self.assertTrue(list(Draft202012Validator(schema).iter_errors(payload)))

    def test_openapi_contract_is_valid_and_has_review_routes(self):
        import yaml
        openapi_doc = yaml.safe_load((ROOT / 'contracts/openapi.yaml').read_text(encoding='utf-8'))
        self.assertIn('/api/v1/review/tasks', openapi_doc['paths'])
        self.assertIn('/api/v1/review/issues', openapi_doc['paths'])
        self.assertIn('ReviewTaskSummary', openapi_doc['components']['schemas'])
        self.assertIn('ReviewIssueDetail', openapi_doc['components']['schemas'])
