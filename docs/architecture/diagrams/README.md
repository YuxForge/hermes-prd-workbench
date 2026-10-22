# Data model and backend class atlas

Open [model-atlas.html](model-atlas.html) in a browser. It is a self-contained static HTML document containing six inline SVG diagrams, a table/entity map, a foreign-key inventory, and source/scope notes. Google Fonts is optional at display time; local fallbacks are declared.

Source snapshot: `main` at `2b9f373`, version `0.4.0-beta.2`, 2026-10-20. These diagrams describe repository implementation, not the schema of any running installation.

## Views and traceability

| View | Implementation evidence |
| --- | --- |
| ER data model, all eight tables | Flyway `V001`–`V013`, under `apps/backend/src/main/resources/db/migration/`; corresponding `*Entity.java` classes |
| Task/run orchestration | `task/TaskController`, `task/TaskService`, `task/RunService`, `task/TaskMutationGate`, `hermes/HermesRunsClient`, `result/ResultValidator` |
| Agent tools and review feedback | `hermes/InternalToolController`, `hermes/ToolService`, `task/ReviewFeedbackService`, `task/RunService`, `task/RunMapper`, `result/ResultMapper` |
| Persistence interface inheritance | `TaskMapper`, `SourceMapper`, `RunMapper`, `ResultMapper`; MyBatis-Plus `BaseMapper<T>` |
| Authentication | `auth/AuthController`, `AuthService`, `SessionAuthInterceptor`, `CurrentUser`, `UserMapper`, `SessionMapper` |
| Telemetry and update logs | `telemetry/AdminAnalyticsController`, `TelemetryService`, `TelemetryEventMapper`, `updatelog/AdminUpdateLogController`, `UpdateLogService`, `UpdateLogMapper` |

Java paths above are relative to `apps/backend/src/main/java/org/hermesprd/workbench/`. Contract cross-checks: `contracts/openapi.yaml` (run modes, run response, draft updates, review feedback) and the result schemas under `contracts/`. Existing regression references include `RunServiceThinSliceTest`, `TaskServiceTest`, `ReviewFeedbackServiceTest`, `TaskMutationGateTest`, `AuthServiceTest`, `TelemetryServiceTest`, and `UpdateLogServiceTest`. These business tests were not executed for this documentation-only change.

The ER diagram distinguishes six enforced foreign keys from two logical user links. The class diagrams show selected operations and typed relationships; they omit getters/setters, most DTO/configuration members, and cross-view edges listed in the atlas appendix. Interface `extends` is generalization, not realization. `...` denotes abbreviated parameters; `List`/`Map` omit generic parameters in selected signatures. Database links are not represented as Java object fields.

## Design and checks

The user selected the repository web palette. Colors and the body font stack come from `apps/web/src/styles.css`; the atlas uses `#eef1f3`, `#1a2024`, `#4b555c`, and `#0f766e`, with an adjusted secondary text color. The installed skill and profile library were left unchanged. Each diagram uses the `doc-wide` 1280×720 canvas, a local horizontal scroller and print scaling.

Validation performed: the installed diagram-design `scripts/self_check.py`, static node/member bounds and connector endpoint/transit checks, label-mask overlap checks, `git diff --check`, and a sensitive-pattern scan of the new documentation. A distinct self-review checked the final six views against DDL, constructors, method declarations, the call chain, and contract modes; no independent reviewer participated.

Browser visual verification remains pending: the browser tool rejected the local `file://` URL under its protocol policy. No browser screenshots or computed-font/viewport evidence were produced. Static checks do not replace visual review; the diagram file was submitted to the Codex file panel for inspection. Please review the output palette, typography and rendering before treating visual acceptance as complete.

No SQL was executed, and no schema, runtime, credentials or product behavior changed. Product startup and real Agent acceptance were not run for this static documentation artifact. Nacos: 不适用：当前冻结基线不接入 Nacos.
