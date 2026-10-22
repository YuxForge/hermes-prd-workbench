# 线上需求评审修复后测试报告

日期：2026-03-22。范围：按实现评审报告修复契约/后端/前端缺陷后，Docker Compose 本地重新部署与验证。入口 `http://127.0.0.1:8180`。Nacos：不适用，当前冻结基线不接入 Nacos。

## 1. 本轮修复摘要

| 级别 | 项 | 处理 |
| :--- | :--- | :--- |
| P0 | 前端 DTO 与 OpenAPI 对齐（轮次、统计、历史/修订、diff 参数、深链接 `?issue=`） | 已修 |
| P0 | 401 保留当前路径回跳；关闭轮次增加 REJECT | 已修 |
| P0 | 提问仅在基线版本挂 roundId；失败提示进表单 | 已修 |
| P1 | 归档检查未闭环问题清单 | 已修 |
| P1 | 导入仅允许成功 GENERATE 当前稿 | 已修 |
| P1 | 跨文档 relatedIssues 返回 400 | 已修 |
| P1 | next_issue_no `FOR UPDATE` + 自增 | 已修 |
| P1 | 成员变更 `SELECT ... FOR UPDATE` | 已修 |
| P1 | 导出不再把二次鉴权包在只读事务里 | 已修 |
| P1 | 通知已读走 `/notifications/read`；锚点 SHA-256、选区不 trim | 已修 |
| P1 | OWNER 自提不可点官方答复；撤销仅 OPEN | 已修 |

## 2. 环境

- Compose 服务：mysql / hermes / backend / web
- Web：`127.0.0.1:8180`
- 健康：`GET /api/v1/health` → `{"status":"UP","hermes":"CONNECTED"}`
- 部署：`docker compose --env-file .env -f compose.yaml up -d --build`（backend/web 重建并重启）
- 本机 JDK：无 21（仅 8/17），后端单测未在宿主机 Maven 执行；镜像构建使用 `maven:3.9.16-eclipse-temurin-21` 且 `-DskipTests package` 成功

## 3. 自动化检查

| 命令 | 结果 | 状态 |
| :--- | :--- | :---: |
| `pnpm --dir apps/web test` | 24 套件 / 98 项通过 | PASS |
| `pnpm --dir apps/web typecheck` | `vue-tsc --noEmit` 通过 | PASS |
| Docker web `pnpm build` | 镜像内 typecheck + vite build 通过 | PASS |
| Docker backend `mvn -DskipTests package` | JDK 21 镜像编译通过 | PASS |
| `python3 tests/test_contracts.py` | OpenAPI 契约校验通过 | PASS |
| `git diff --check` | 无空白规范错误 | PASS |
| 宿主机 `mvn -Dtest=ReviewServiceTest test` | 本机 JDK 不在 [21,22) | 未执行 |
| `python3 tests/test_review_acceptance.py` | 真实 Chrome 1920/1440/1366/390 闭环通过 | PASS |

## 4. 端到端走查（Playwright + 真实 Chrome）

合成账号与采购协同中文 PRD，未使用真实凭据。

1. 未登录访问 `/reviews/{taskId}` 进入 `/login?redirect=...`，登录后回到评审页。
2. 1920：REVIEWER 对本版本提问 #1，抽屉打开，官方答复页签不可见。
3. 1440：EDITOR 提交官方 ANSWER，并提出 NO_CHANGE 解决确认。
4. 1366：REVIEWER 点击确认解决，状态 RESOLVED。
5. 390：OWNER 以 PASS 结束第 1 轮。
6. 导出 ZIP 含 Markdown、issues.csv、history.json、traceability.json、manifest.json。

截图目录：`tests/artifacts/review_e2e_screenshots/`。

## 5. 修复项 API 回归（Compose 已部署后端）

| 场景 | 实际 |
| :--- | :--- |
| 存在 OPEN 问题归档 | 409，文案含 `#1 遗留问题` |
| 修订关联另一文档问题 | 400，`不属于当前文档` |
| 导入不存在草稿 | 404 `RESOURCE_NOT_FOUND` |
| 问题列表 statistics | `totalIssues/openIssues/awaitingConfirmationIssues/...` |
| 同版本 diff | 200，字段 `fromRevisionId/toRevisionId/lines` |

## 6. 残留与限制

- 宿主机未跑 Maven 单测（无 JDK 21）；新增归档/导入/跨文档用例已写入 `ReviewServiceTest.java`，需在 JDK 21 或后续 CI 执行。
- Recogito.js 未接入，仍使用自研选区适配器 + SHA-256 指纹。
- 协作评审页不再调用旧 `GET /tasks/{id}`，导入 GENERATE 草稿入口依赖任务所有者在工作台侧完成。
- 独立评审：实现与验收由同一会话完成，未另开独立评审人。

## 7. 结论

P0 契约断裂与评审报告列出的主要 P1 已修复并随 Compose 重新部署。真实浏览器四视口闭环与导出包形状通过。功能可用，剩余项见第 6 节。
