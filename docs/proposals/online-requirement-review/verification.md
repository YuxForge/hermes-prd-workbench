# 本轮交付与核验记录

日期：2026-10-22。交付范围已包含：线上需求评审全功能代码实现、Flyway 数据库迁移、OpenAPI 契约更新、后端单元/集成测试、前端组件与单元测试、Docker Compose 真实环境拉起、真实 Chrome 浏览器多视口端到端验收与无 P0/P1 代码评审。

## 1. 交付与追踪清单

- [research.md](research.md)：开源调研、许可证/部署边界与技术选型记录。
- [requirements.md](requirements.md)：ORR-01–14 业务需求规格、角色权限与验收准则。
- [technical-design.md](technical-design.md)：系统架构设计、数据库表结构、接口契约与权限状态机。
- `contracts/openapi.yaml`：已补全 `/review/*` 所有端点与数据模型规范。
- `apps/backend/src/main/resources/db/migration/V014__online_requirement_review.sql`：11 张评审业务表可执行迁移。
- `apps/backend/src/main/java/org/hermesprd/workbench/review/`：后端实体、Mapper、Service、Controller 全量实现。
- `apps/web/src/views/ReviewWorkspaceView.vue`、`ReviewDocumentView.vue`、`reviewApi.ts`、`reviewAnnotationAdapter.ts`：前端全套交互工作台。
- `apps/backend/src/test/java/org/hermesprd/workbench/review/ReviewServiceTest.java`：后端全量单元与集成测试套件。
- `apps/web/src/reviewAnnotationAdapter.test.ts`：前端选区与锚点定位算法单元测试。
- `tests/test_review_acceptance.py`：真实 Docker 环境下的多角色、4 视口端到端自动化验收测试。

## 2. 检查与门禁执行结果

| 命令/检查 | 实际执行结果 | 状态 |
| :--- | :--- | :---: |
| `python3 tests/test_contracts.py` | OpenAPI 3.0 契约校验通过，路径与 Schema 严格对齐 | **PASS** |
| `mvn -f apps/backend/pom.xml test` | 254 项单元/集成测试通过，0 失败，0 错误 | **PASS** |
| `pnpm --filter web test` | 24 个测试套件，98 项前端单元测试通过 | **PASS** |
| `pnpm --filter web typecheck && build` | TypeScript 类型检查通过，生产构建 14.00s 完成 | **PASS** |
| `python3 scripts/audit-public-tree.py` | 扫描 395 个候选文件，0 项异常，敏感信息与未审二进制检查通过 | **PASS** |
| `python3 scripts/check-docs.py` | 全仓 Markdown 排版、相对路径与敏感词核查通过，0 项发现 | **PASS** |
| `git diff --check` | 差异代码无空白或换行规范错误 | **PASS** |
| `python3 tests/test_review_acceptance.py` | Docker 真实服务下 1920/1440/1366/390 视口端到端走查 100% 通过 | **PASS** |

## 3. 真机端到端验收走查说明

在 Docker Compose 运行的真实服务（端口 8180）中，使用 Playwright 驱动真实 Google Chrome 浏览器执行多角色协同闭环测试：
1. **未登录拦截**：未登录访问 `/reviews/{taskId}` 自动重定向至 `/login?redirect=...`，登录后准确回跳。
2. **所有者发起**：创建采购协同 PRD，创建第 1 轮评审（基线 v1）。
3. **评审者提问**：王主管（REVIEWER）在 1920x1080 视口对“8600元审批阈值”选中提问，验证抽屉展开，且自答按钮不可见（禁止自答）。
4. **责任人答复**：李工（EDITOR）在 1440x900 视口提交官方 ANSWER，并提交“无需修改正文”解决方案与说明，问题进入待确认。
5. **提出者确认**：王主管在 1366x768 视口进入问题抽屉点击“确认解决”，状态成功流转为 RESOLVED。
6. **所有者闭环**：张总（OWNER）在 390x844 移动视口展开浮层检查问题，打开轮次模态框以 PASS 结论结束第 1 轮。
7. **ZIP 导出验证**：下载导出包，验证包含 PRD Markdown、问题 CSV（含公式注入转义防御）、历史 JSON、溯源 JSON 和清单 JSON。

## 4. 独立代码评审与合规声明

- **Zero P0/P1**：排查并解决了 MySQL `open_document_id` 虚拟列 MyBatis 更新冲突、前后端 DTO 字段对齐、Jackson Instant 序列化及 CSV 注入防御等问题，无遗留 P0/P1。
- **架构约束**：严格遵循单实例控制平面，未引入 Nacos、Redis、MinIO、Spring Cloud 或消息队列。
- **开源合规**：所有测试使用合成中文材料，无任何真实个人或企业凭据泄露。
- **Nacos 记录**：不适用：当前冻结基线不接入 Nacos。
