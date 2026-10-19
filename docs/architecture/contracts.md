# API and result contracts

The authoritative HTTP specification is [OpenAPI](../../contracts/openapi.yaml). Seven JSON Schemas under contracts/ define REVIEW/GENERATE/DEMO/POC/ESTIMATE/TEST_CASE/TECH_SPEC results. `$id` uses a project URN; it is not a remote URL the application must fetch.

Browser API is /api/v1. Login/register/health and published update notes are public; tasks, runs, draft/feedback, artifact exports and telemetry require a session. Admin endpoints require ADMIN. Internal endpoints require the plugin secret and current run/mode ownership. No administrator-bootstrap HTTP endpoint exists.

| Contract | Current behavior |
| --- | --- |
| CreateRun.skill | DEMO default generic-admin-html; unsupported IDs return INVALID_REQUEST without private fallback |
| Demo workspaceFile | Latest sandbox index.html is authoritative; missing content fails, no stale-inline fallback |
| Demo package_files | Optional safe relative POSIX UTF-8 text map; no build/private-host claim |
| verify_demo / verify_poc | passed, diagnostic=true, behaviorProof=false; static structure/syntax only, missing dependencies fail |
| Generate/test-case/tech-spec packages | Current-run catalog and file whitelist, basic required structure, no second-model or business re-judgment |
| Telemetry events | Authenticated, opt-in collection; identifiers/count metadata allowlist, no raw error-message persistence |
| Health | UP only if SELECT 1/schema and Hermes health succeed; connection errors do not expose credentials |

Telemetry accepts USER_ACTION or CLIENT_ERROR, upper-case module/action identifiers of at most 32/64 characters, bounded status/duration/errorCode, at most ten metadata keys. Raw errorMessage is accepted only within 500 characters for compatibility and discarded. Kept metadata: valid taskId/runId, completeness and bounded file/source counts; unknown fields never persist. Browser collection and backend persistence must both be enabled. Retention defaults to 30 days, bounded 1–365. Operational failed runs stay in agent_run and are separate from optional events.

Update producer, consumer, tests and these descriptions together whenever the formal contracts change. A successful Agent result is stored directly after minimum validation; Java does not repair malformed JSON, match citations exactly or rewrite conclusions.

Estimate Schema describes canonical producer fields; runtime minimal validation also accepts documented name/type/qaDays aliases without rewriting the saved payload. Effort may use fractional precision beyond one decimal. Totals and scope remain Agent supplied; the service does not repair or reconcile them.

Estimate exports display absent optional summary/item totals as “未提供”, not zero or a derived Agent conclusion. Supplied totals (including zero or inconsistent summaries) remain supplied values. Excel numeric column sums are display calculations only; a column with any missing item total remains incomplete rather than summing missing values as zero. The bundle overview follows the same missing-value convention. Regression: `EstimateExcelExporterTest` and `TaskBundleExporterTest.overviewDoesNotTurnMissingEstimateTotalIntoZero`.

TEST_CASE `steps` accepts a string or ordered string array. XLSX exports preserve string text or present array entries as numbered lines, with wrapped-row height and frozen identifiers/header for reading. This is an export projection only; saved Agent steps are not rewritten. Regression: `TestCaseExcelExporterTest.preservesOrderedArrayStepsAndFitsWrappedRowsWithoutChangingPayload`.

TECH_SPEC supports physical or local logical data models, structured index intent, local/view adapter method labels, and textual or structured error-code lists. UI and Markdown export render these values as text; stored results stay immutable. No HTTP transport or server database is invented for a browser-only solution.

## Browser origin transport

The Compose Web entry serves the page and relative `/api/v1` requests on one HTTP origin. Nginx forwards the original Host including its port and leaves Origin unchanged. Spring therefore recognizes valid same-origin writes on default/custom Web ports; actual cross-origin or opaque-origin requests still require the explicit backend allowlist. Native development has its separate configured origins. CORS403 is not an administrator authorization grant. No response/body/schema or authentication semantics changed.
