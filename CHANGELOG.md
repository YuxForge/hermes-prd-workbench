# Changelog

## Unreleased

## 0.5.0-beta.1 — preview

- Add independent **Online Requirement Review (需求线上评审)** module and collaborative workspace:
  - Topbar and workbench direct navigation for team review collaboration, multi-revision baseline diffs, and audit exports.
  - 4-tier project member roles (OWNER, EDITOR, REVIEWER, VIEWER) with strict RBAC enforcement.
  - Fuzzy user search autocomplete dropdown for assigning issues and adding workspace members by username or display name.
- Precise document text selection and physical line number mapping:
  - Refactored selection algorithm stripping Markdown syntax noise to locate exact document lines using prefix/suffix anchors.
  - Rich Markdown live preview in the question submission drawer with default assignee detection.
- WeChat-style conversational dialogue flow:
  - Dialogue bubble cards for issue discussion, replacing noisy event logs with clean left/right participant avatars, timestamps, and formatted Markdown text.
  - Milestone cards celebrating issue resolution.
- Streamlined resolution workflow and auto-progression:
  - Eliminated complex resolution application modal: maintainer replies automatically transition issues to "Awaiting Confirmation".
  - Exclusive "Resolved" and "Cancel" smooth action buttons visible only to the original reporter.
  - Reminder nudge mechanism for pending unanswered questions.
- Unread notification and pending task auto-clear closure:
  - Automatically marks notifications as read when viewing issues, replying, or resolving.
  - Re-scoped "Assigned to Me" pending counters strictly to OPEN issues, eliminating contradictory metrics.
  - Flyway migration V017 to clean up historical stale unread notifications for replied issues.
- Mermaid flowchart and diagram vector rendering:
  - Integrated Mermaid vector engine in review document views and issue drawers with diagram keyword detection and responsive SVG containers.
- High-concurrency database physical foreign key decoupling:
  - Flyway migration V016 drops all physical foreign keys across review tables to comply with modern enterprise database design standards.

## 0.4.0-beta.2 — preview

- Use shared, trusted Mermaid root-level SVG-text configuration for PRD previews and Word export, preserving Chinese node labels through strict SVG sanitization; add real Mermaid flowchart/sequence regression coverage.

- Preserve the browser Host port through the Compose Nginx proxy, fixing HTTP403 Invalid CORS request on same-origin login and writes through default/custom Web ports. Keep explicit cross-origin restrictions and add positive/negative transport regression coverage.

- Record Windows 11 WSL2 browser and Word-export visual acceptance, add an English project home, synthetic-data product media, and public community entry points.

- Make Maven copy packaged Skill resources even when reproducible source archives carry epoch timestamps; release checks now start from a clean backend target.

## 0.4.0-beta.1 — preview

- Move applications to apps/backend and apps/web; centralize synthetic fixtures and public documentation.
- Replace company defaults and private host scaffolding with a portable HTML template and configurable model service.
- Explicit local administrator bootstrap; remove fixed personal SQL privileges and secret output.
- Static generated-code diagnostics, isolated new-tab preview, strict sanitized diagrams, opt-in bounded telemetry.
- Compose deployment, lifecycle commands, contribution/security/license documentation and release checks.

This is a fresh-install preview limited to macOS arm64 with Docker Desktop Linux containers. Validation evidence and release gates are recorded in docs/maintainers/change-report.md. Internal customer release notes are excluded.

- Patch application dependencies, lock Hermes API runtime dependencies with hashes, and use a fresh MySQL 8.4 LTS volume.
- Enforce BCrypt’s 72 UTF-8 byte boundary without silent password truncation.
