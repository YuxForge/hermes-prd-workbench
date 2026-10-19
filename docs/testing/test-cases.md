# Preview acceptance

Use a fresh project-owned MySQL schema, pinned Hermes source with the reviewed API dependency lock and dedicated Profile, backend and formal Vue at http://127.0.0.1:5174 for native browser acceptance. The Compose entry is 8180 for deployment testing; do not confuse it with a prototype. Record commit, versions, model/provider summary without endpoints/keys, role, steps, expected/actual, requestId/runId and private evidence paths.

| ID | Operation | Acceptance |
| --- | --- | --- |
| OSS-T01 | Scan candidate tree, pages and exported names | No company defaults, private network/host dependencies or personal grants |
| OSS-T02 | Default/explicit/invalid Demo presets | Generic HTML default, all resources load, unsupported IDs fail |
| OSS-T03 | Clean Compose setup/start/config/doctor | Four components start, credentials hidden, real DB/Hermes readiness |
| OSS-T04 | Linux amd64, macOS arm64 and Windows WSL2/Compose | Publish only actually verified platforms; retain fixed runtime versions |
| OSS-T05 | Missing keys, invalid model, occupied port, missing Node | Explicit useful failure, no silent skip or fake success |
| OSS-T06 | Repeated setup/stop/start and backup/restore | Secrets/data preserved, dedicated profile only, failed work manually rerun |
| OSS-T07 | Fresh migration and unknown nonempty schema | Flyway V001–V013, eight business tables plus Flyway history/constraints, no personal ADMIN, unknown schema refused |
| OSS-T08 | Follow README and check links/version/contract | Documentation agrees with current code and release status |
| OSS-T09 | Candidate, logs, image layers and source package | No credentials, user material, prompts or private responses in public artifacts |
| OSS-T10 | Public refs, images, Office and external artifacts | No customer/private material; publication rights documented |
| OSS-T11 | Malicious HTML in iframe and new tab | Cannot access workbench storage/DOM/API; no same-origin or top navigation |
| OSS-T12 | Static diagnostics, malformed/missing validator/timeout | No eval/VM execution, no credential inheritance, behaviorProof false |
| OSS-T13 | Mermaid configuration/events/resources/size and Word export | Strict and sanitized; unsafe diagrams retain source; native SVG labels remain visible in flowcharts and sequence actors/messages/notes/alt/loop; preview/export share trusted configuration |
| OSS-T14 | Telemetry off/on, unauthenticated/invalid metadata and retention | Off does not send/persist, auth/limits/allowlist, no raw error text |
| OSS-T15 | License, dependency provenance and SBOM | Third-party original terms retained; ownership gate complete before publication |
| OSS-T16 | Untrusted PR and release workflow/source archive | No PR secrets/publication authority; allowlisted files and hashes |
| OSS-T18 | Compose browser Origin through default/custom Web ports | Same-origin login and writes pass with Host port preserved; hostile/null origins and unauthorized role access remain denied; native development preflight retains explicit allowlist |
| OSS-T17 | Chinese synthetic materials, seven real Agent modes | Tools submit parseable persistent results; refresh/manual retry/export work |
| CASE-T01 | Flagship case download and optional estimate totals | Missing summary/item totals display 未提供; supplied zero/precision/conflicting totals and immutable payload remain unchanged; no derived replacement conclusion |
| CASE-T02 | Test-case XLSX string/array steps | Ordered array entries become numbered lines, strings remain unchanged, wrapped rows fit and headers freeze; original result is not rewritten |

Regression also covers PRD-01/02 and THIN-REQ-01/02/03/05/07 from product.md: create requirement; Chinese MD/DOCX/XLSX upload and empty-material validation; current draft save/unsaved/concurrency; review decision inheritance; real runs; refresh/history/export; explicit failure/manual rerun; user cannot access admin.

At 390, 1366, 1440 and 1920 widths check text/buttons/dialogs/menus/scroll, empty/loading/error states and operation accessibility. Click through success, validation, failure and refresh in a real browser. Mock/API-only tests do not satisfy module completion. Existing internal screenshots and source documents are not reused as public evidence.

Automation commands: scripts/check.sh; Maven/JUnit, frontend typecheck/Vitest/build, Python plugin/deployment tests, candidate/text scans and diff checks. The report must separately list unexecuted real-model/platform/restore/history/license gates.

OSS-T18 regression: `CorsRequestTest` exercises Spring MVC same-origin/default/custom-port behavior and negative preflights. CI additionally runs `python3 tests/nginx_proxy_smoke.py` with the shipped configuration, pinned real Nginx, a synthetic echo upstream and isolated ephemeral containers/network; it asserts complete Host/Origin/method/query forwarding without model credentials or databases. Real Nginx acceptance must send Origin on login, authenticated task creation and a PUT draft/feedback action, using localhost and127.0.0.1; include a custom external port, foreign/null origins and refresh. A doctor or API request without Origin alone cannot close this gate. WSL2 platform claims still require an actual WSL2 execution record.

OSS-T13 regression uses the real installed Mermaid library through Markdown/render/SVG sanitization. JSDOM stubs only SVG geometry; those tests prove label preservation, not browser layout or canvas PNG conversion. Use tests/fixtures/prd-mermaid-regression.md for actual browser and Word-export visual acceptance. Existing PRD results and drafts are not rewritten to repair diagrams.
