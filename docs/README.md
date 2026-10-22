# Documentation

The main entries describe the preview's current behavior. Unimplemented proposals are listed separately below; historical internal plans and customer evidence are excluded.

| Topic | Entry |
| --- | --- |
| Flagship example, downloadable outputs and 60-second walkthrough | [Device procurement and approvals](examples/procurement/README.md) |
| Interactive online showcase (no registration required) | [Live demo station](https://yuxforge.github.io/hermes-prd-workbench/) |
| Product and requirements | [product.md](product.md) |
| Architecture, runtime and data | [architecture/overview.md](architecture/overview.md) |
| Data model and backend class diagrams | [Source model atlas](architecture/diagrams/model-atlas.html) · [scope and verification](architecture/diagrams/README.md) |
| API, JSON and diagnostic contracts | [architecture/contracts.md](architecture/contracts.md) |
| Self-hosting, secrets, backup and restore | [deployment/self-hosting.md](deployment/self-hosting.md) |
| Native development | [development/getting-started.md](development/getting-started.md) |
| Test cases | [testing/test-cases.md](testing/test-cases.md) |
| Privacy | [privacy.md](privacy.md) |
| Open-source design decisions | [maintainers/open-source-decisions.md](maintainers/open-source-decisions.md) |
| Licenses and dependency provenance | [maintainers/third-party.md](maintainers/third-party.md) |
| Release process | [maintainers/releasing.md](maintainers/releasing.md) |
| Implementation evidence | [maintainers/change-report.md](maintainers/change-report.md) |

Executable API contracts are under contracts/ and migrations only under apps/backend/src/main/resources/db/migration/. Do not copy migration SQL into a second directory.

## Proposals — not implemented

| Topic | Entry |
| --- | --- |
| Human online requirement review, separate from AI review | [Open-source research](proposals/online-requirement-review/research.md) · [Requirements](proposals/online-requirement-review/requirements.md) · [Technical design](proposals/online-requirement-review/technical-design.md) · [Verification status](proposals/online-requirement-review/verification.md) |
