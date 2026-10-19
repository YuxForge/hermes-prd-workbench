package org.hermesprd.workbench.bundle;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.estimate.EstimateExcelExporter;
import org.hermesprd.workbench.result.ResultEntity;
import org.hermesprd.workbench.task.ApiModels;
import org.hermesprd.workbench.task.ReviewFeedbackService;
import org.hermesprd.workbench.task.RunEntity;
import org.hermesprd.workbench.task.RunMapper;
import org.hermesprd.workbench.task.RunService;
import org.hermesprd.workbench.task.TaskEntity;
import org.hermesprd.workbench.task.TaskService;
import org.hermesprd.workbench.testcase.TestCaseExcelExporter;
import org.hermesprd.workbench.techspec.TechSpecMarkdownExporter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class TaskBundleExporter {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final TaskService taskService;
    private final RunService runService;
    private final RunMapper runMapper;
    private final ReviewFeedbackService reviewFeedbackService;
    private final EstimateExcelExporter estimateExcelExporter;
    private final TestCaseExcelExporter testCaseExcelExporter;
    private final TechSpecMarkdownExporter techSpecMarkdownExporter;
    private final ObjectMapper objectMapper;

    public TaskBundleExporter(
            TaskService taskService,
            RunService runService,
            RunMapper runMapper,
            ReviewFeedbackService reviewFeedbackService,
            EstimateExcelExporter estimateExcelExporter,
            TestCaseExcelExporter testCaseExcelExporter,
            TechSpecMarkdownExporter techSpecMarkdownExporter,
            ObjectMapper objectMapper
    ) {
        this.taskService = taskService;
        this.runService = runService;
        this.runMapper = runMapper;
        this.reviewFeedbackService = reviewFeedbackService;
        this.estimateExcelExporter = estimateExcelExporter;
        this.testCaseExcelExporter = testCaseExcelExporter;
        this.techSpecMarkdownExporter = techSpecMarkdownExporter;
        this.objectMapper = objectMapper;
    }

    public byte[] exportBundle(String userId, String taskId) throws IOException {
        TaskEntity task = taskService.requireOwned(userId, taskId);

        // Fetch latest succeeded runs for each mode
        List<RunEntity> succeededRuns = runMapper.selectList(new LambdaQueryWrapper<RunEntity>()
                .eq(RunEntity::getTaskId, taskId)
                .eq(RunEntity::getStatus, "SUCCEEDED")
                .orderByDesc(RunEntity::getCreatedAt));

        Map<String, RunEntity> latestRuns = new LinkedHashMap<>();
        for (RunEntity run : succeededRuns) {
            latestRuns.putIfAbsent(run.getMode(), run);
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {

            // 1. Generate 00_Overview
            String overviewMarkdown = buildOverviewMarkdown(task, latestRuns);
            addZipEntry(zos, "00_项目交付清单与资产概览.md", overviewMarkdown.getBytes(StandardCharsets.UTF_8));

            // 2. PRD Markdown + optional document pack
            RunEntity prdRun = latestRuns.get("GENERATE");
            if (prdRun != null) {
                JsonNode payload = getPayload(prdRun);
                if (payload != null) {
                    String prdContent = buildPrdMarkdown(payload);
                    addZipEntry(zos, "01_需求规格说明书_PRD.md", prdContent.getBytes(StandardCharsets.UTF_8));
                    if (payload.has("prd_files") && payload.get("prd_files").isObject()) {
                        var fields = payload.get("prd_files").fields();
                        while (fields.hasNext()) {
                            var entry = fields.next();
                            if (org.hermesprd.workbench.task.PrdWorkspace.isPrdAllowedPath(entry.getKey())
                                    && entry.getValue() != null
                                    && entry.getValue().isTextual()) {
                                addZipEntry(
                                        zos,
                                        "02_PRD文档包/" + entry.getKey(),
                                        entry.getValue().asText().getBytes(StandardCharsets.UTF_8)
                                );
                            }
                        }
                        addZipEntry(zos, "02_PRD文档包/PRD.md", prdContent.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }

            // 3. Demo HTML
            RunEntity demoRun = latestRuns.get("DEMO");
            if (demoRun != null) {
                JsonNode payload = getPayload(demoRun);
                if (payload != null && payload.has("html")) {
                    String html = payload.path("html").asText("");
                    addZipEntry(zos, "02_产品交互原型_Demo.html", html.getBytes(StandardCharsets.UTF_8));
                }
            }

            // 3b. POC original static files
            RunEntity pocRun = latestRuns.get("POC");
            if (pocRun != null) {
                JsonNode payload = getPayload(pocRun);
                if (payload != null && payload.has("poc_files") && payload.get("poc_files").isObject()) {
                    var fields = payload.get("poc_files").fields();
                    while (fields.hasNext()) {
                        var entry = fields.next();
                        if (org.hermesprd.workbench.task.PocWorkspace.isPocAllowedPath(entry.getKey())
                                && entry.getValue() != null
                                && entry.getValue().isTextual()) {
                            addZipEntry(
                                    zos,
                                    "07_POC演示系统/" + entry.getKey(),
                                    entry.getValue().asText().getBytes(StandardCharsets.UTF_8)
                            );
                        }
                    }
                }
            }

            // 4. Review Feedback Markdown
            RunEntity reviewRun = latestRuns.get("REVIEW");
            if (reviewRun != null) {
                JsonNode payload = getPayload(reviewRun);
                if (payload != null) {
                    ApiModels.ReviewFeedbackResponse feedback = reviewFeedbackService.get(userId, reviewRun.getId());
                    String reviewContent = buildReviewMarkdown(payload, feedback);
                    addZipEntry(zos, "03_需求评审与整改确认单_Review.md", reviewContent.getBytes(StandardCharsets.UTF_8));
                }
            }

            // 5. Estimate Excel
            RunEntity estimateRun = latestRuns.get("ESTIMATE");
            if (estimateRun != null) {
                JsonNode payload = getPayload(estimateRun);
                if (payload != null) {
                    byte[] excelBytes = estimateExcelExporter.export(payload, null);
                    addZipEntry(zos, "04_工作量评估与报价表_Estimate.xlsx", excelBytes);
                }
            }

            // 6. Test Case Excel + optional pack
            RunEntity testCaseRun = latestRuns.get("TEST_CASE");
            if (testCaseRun != null) {
                JsonNode payload = getPayload(testCaseRun);
                if (payload != null) {
                    byte[] excelBytes = testCaseExcelExporter.export(payload, null);
                    addZipEntry(zos, "05_系统测试用例清单_TestCase.xlsx", excelBytes);
                    if (payload.has("test_case_files") && payload.get("test_case_files").isObject()) {
                        var fields = payload.get("test_case_files").fields();
                        while (fields.hasNext()) {
                            var entry = fields.next();
                            if (org.hermesprd.workbench.task.TestCaseWorkspace.isAllowedPath(entry.getKey())
                                    && entry.getValue() != null
                                    && entry.getValue().isTextual()) {
                                addZipEntry(
                                        zos,
                                        "05_测试用例包/" + entry.getKey(),
                                        entry.getValue().asText().getBytes(StandardCharsets.UTF_8)
                                );
                            }
                        }
                    }
                }
            }

            // 7. Tech Spec Markdown + optional pack
            RunEntity techSpecRun = latestRuns.get("TECH_SPEC");
            if (techSpecRun != null) {
                JsonNode payload = getPayload(techSpecRun);
                if (payload != null) {
                    String techSpecContent = techSpecMarkdownExporter.export(payload);
                    addZipEntry(zos, "06_研发技术方案_TechSpec.md", techSpecContent.getBytes(StandardCharsets.UTF_8));
                    if (payload.has("tech_spec_files") && payload.get("tech_spec_files").isObject()) {
                        var fields = payload.get("tech_spec_files").fields();
                        while (fields.hasNext()) {
                            var entry = fields.next();
                            if (org.hermesprd.workbench.task.TechSpecWorkspace.isAllowedPath(entry.getKey())
                                    && entry.getValue() != null
                                    && entry.getValue().isTextual()) {
                                addZipEntry(
                                        zos,
                                        "06_技术方案包/" + entry.getKey(),
                                        entry.getValue().asText().getBytes(StandardCharsets.UTF_8)
                                );
                            }
                        }
                    }
                }
            }

            zos.finish();
            return baos.toByteArray();
        }
    }

    private JsonNode getPayload(RunEntity run) {
        ResultEntity result = runService.findResult(run.getId());
        return runService.resultPayload(result);
    }

    private void addZipEntry(ZipOutputStream zos, String entryName, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private String buildOverviewMarkdown(TaskEntity task, Map<String, RunEntity> latestRuns) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 项目交付清单与资产概览\n\n");
        sb.append("- **项目需求名称**：").append(task.getTitle()).append("\n");
        sb.append("- **需求编号**：`").append(task.getId()).append("`\n");
        sb.append("- **导出打包时间**：").append(TIME_FORMATTER.format(java.time.Instant.now())).append(" (UTC+8)\n\n");
        sb.append("## 一核双翼全生命周期资产矩阵\n\n");
        sb.append("| 资产类别 | 交付包内文件名 | 交付状态 | 关键规格与指标摘要 | 最新生成时间 |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- |\n");

        appendOverviewRow(sb, "1. PRD需求规格", "01_需求规格说明书_PRD.md", latestRuns.get("GENERATE"), run -> {
            JsonNode p = getPayload(run);
            int files = p != null && p.has("prd_files") ? p.path("prd_files").size() : 0;
            int chars = p != null && p.has("markdown") ? p.path("markdown").asText().length() : 0;
            if (files > 0) {
                return "可开工文档包 " + files + " 个源文件，合并稿约 " + chars + " 字";
            }
            return "正文字符数约 " + chars + " 字";
        });

        appendOverviewRow(sb, "1b. PRD文档包", "02_PRD文档包/", latestRuns.get("GENERATE"), run -> {
            JsonNode p = getPayload(run);
            int files = p != null && p.has("prd_files") ? p.path("prd_files").size() : 0;
            return files > 0 ? "原始多文件文档包 (" + files + " 个文件)" : "历史结果仅有合并稿";
        });

        appendOverviewRow(sb, "2. 交互Demo原型", "02_产品交互原型_Demo.html", latestRuns.get("DEMO"), run -> {
            JsonNode p = getPayload(run);
            int chars = p != null && p.has("html") ? p.path("html").asText().length() : 0;
            return "完整离线可运行 HTML (" + chars + " 字符)";
        });

        appendOverviewRow(sb, "2b. POC演示系统", "07_POC演示系统/", latestRuns.get("POC"), run -> {
            JsonNode p = getPayload(run);
            int files = p != null && p.has("poc_files") ? p.path("poc_files").size() : 0;
            return "静态多文件 SPA (" + files + " 个源码文件)";
        });

        appendOverviewRow(sb, "3. 需求评审意见", "03_需求评审与整改确认单_Review.md", latestRuns.get("REVIEW"), run -> {
            JsonNode p = getPayload(run);
            int issues = p != null && p.has("blockingIssues") ? p.path("blockingIssues").size() : 0;
            return "阻断性问题 " + issues + " 项";
        });

        appendOverviewRow(sb, "4. 工作量评估表", "04_工作量评估与报价表_Estimate.xlsx", latestRuns.get("ESTIMATE"), run -> {
            JsonNode p = getPayload(run);
            String total = p != null && p.path("totalDays").isNumber()
                    ? p.path("totalDays").asText() + " 天" : "未提供（见明细）";
            int items = p != null && p.has("items") ? p.path("items").size() : 0;
            return "预估总人天 " + total + " (" + items + " 项细化明细)";
        });

        appendOverviewRow(sb, "5. 系统测试用例", "05_系统测试用例清单_TestCase.xlsx", latestRuns.get("TEST_CASE"), run -> {
            JsonNode p = getPayload(run);
            int total = p != null ? p.path("totalCount").asInt(0) : 0;
            if (total <= 0 && p != null && p.has("items")) {
                total = p.path("items").size();
            }
            int p0 = p != null ? p.path("p0Count").asInt(0) : 0;
            int files = p != null && p.has("test_case_files") ? p.path("test_case_files").size() : 0;
            if (files > 0) {
                return "测试用例 " + total + " 条 (P0 " + p0 + "；源文件 " + files + " 个)";
            }
            return "测试用例 " + total + " 条 (P0 冒烟 " + p0 + " 条)";
        });

        appendOverviewRow(sb, "5b. 测试用例包", "05_测试用例包/", latestRuns.get("TEST_CASE"), run -> {
            JsonNode p = getPayload(run);
            int files = p != null && p.has("test_case_files") ? p.path("test_case_files").size() : 0;
            return files > 0 ? "原始多文件用例包 (" + files + " 个文件)" : "历史结果仅有 Excel/JSON items";
        });

        appendOverviewRow(sb, "6. 研发技术方案", "06_研发技术方案_TechSpec.md", latestRuns.get("TECH_SPEC"), run -> {
            JsonNode p = getPayload(run);
            int tables = p != null && p.path("databaseTables").isArray() ? p.path("databaseTables").size() : 0;
            int apis = p != null && p.path("apiEndpoints").isArray() ? p.path("apiEndpoints").size() : 0;
            int tasks = p != null ? p.path("totalTasks").asInt(0) : 0;
            if (tasks <= 0 && p != null && p.has("tasks")) {
                tasks = p.path("tasks").size();
            }
            int files = p != null && p.has("tech_spec_files") ? p.path("tech_spec_files").size() : 0;
            String base = "数据表 " + tables + " 张 / API " + apis + " 个";
            if (files > 0) {
                base += "（源文件 " + files + " 个）";
            }
            return tasks > 0 ? base + "；含历史工单 " + tasks + " 项" : base;
        });

        appendOverviewRow(sb, "6b. 技术方案包", "06_技术方案包/", latestRuns.get("TECH_SPEC"), run -> {
            JsonNode p = getPayload(run);
            int files = p != null && p.has("tech_spec_files") ? p.path("tech_spec_files").size() : 0;
            return files > 0 ? "原始多文件方案包 (" + files + " 个文件)" : "历史结果仅有合并稿";
        });

        sb.append("\n---\n\n");
        sb.append("## 交付包文件使用指引\n\n");
        sb.append("1. **`01_需求规格说明书_PRD.md`**：按 README 组装的合并稿，可直接用 Markdown 阅读器打开，内嵌标准 Mermaid 流程图与状态机。\n");
        sb.append("1b. **`02_PRD文档包/`**：GENERATE 原始多文件（catalog、权限矩阵、各模块 md）及 `PRD.md` 合并稿；历史结果若无 `prd_files` 则仅有合并稿。\n");
        sb.append("2. **`02_产品交互原型_Demo.html`**：独立无外部 CDN 强依赖的纯静态高保真原型，支持直接在 Chrome/Edge 中双击打开进行业务路径交互演练。\n");
        sb.append("2b. **`07_POC演示系统/`**：静态多文件 POC 源码，解压后双击 `index.html` 即可演示；不是 Vue 工程。\n");
        sb.append("3. **`03_需求评审与整改确认单_Review.md`**：记录专家级 AI 对需求的审查结论、阻断性缺陷及团队采纳整改记录。\n");
        sb.append("4. **`04_工作量评估与报价表_Estimate.xlsx`**：商务与技术测算单 Sheet Excel，已完成专业排版，可直接呈报客户或研发排期。\n");
        sb.append("5. **`05_系统测试用例清单_TestCase.xlsx`**：标准测试用例集，明确标注 P0/P1/P2、前置条件、操作步骤与预期结果，支持导入禅道或 Jira。\n");
        sb.append("5b. **`05_测试用例包/`**：TEST_CASE 原始多文件（catalog、按模块 JSON）；历史结果若无 `test_case_files` 则仅有 Excel。\n");
        sb.append("6. **`06_研发技术方案_TechSpec.md`**：可交付前后端的详细技术方案，包含分层架构、数据库表字典与核心 RESTful API 契约。\n");
        sb.append("6b. **`06_技术方案包/`**：TECH_SPEC 原始多文件（architecture、tables/apis/tasks）；历史结果若无 `tech_spec_files` 则仅有合并稿。\n");

        return sb.toString();
    }

    private void appendOverviewRow(StringBuilder sb, String category, String filename, RunEntity run, java.util.function.Function<RunEntity, String> summaryExtractor) {
        sb.append("| ").append(category).append(" | `").append(filename).append("` | ");
        if (run != null) {
            sb.append("✅ 已交付 | ").append(summaryExtractor.apply(run)).append(" | ")
                    .append(TIME_FORMATTER.format(run.getCreatedAt())).append(" |\n");
        } else {
            sb.append("⚠️ 未生成 | 暂未生成此资产 | - |\n");
        }
    }

    private String buildPrdMarkdown(JsonNode payload) {
        StringBuilder sb = new StringBuilder();
        String title = payload.path("title").asText("需求规格说明书 (PRD)");
        String summary = payload.path("summary").asText("");
        String markdown = payload.path("markdown").asText("");

        sb.append("# ").append(title).append("\n\n");
        if (!summary.isBlank()) {
            sb.append("> ").append(summary).append("\n\n");
        }
        sb.append("---\n\n");
        sb.append(markdown).append("\n");
        // 封面标题与正文一并按 1 / 1.1 / 1.1.1 重编号，保证交付包内所有标题可查阅。
        return org.hermesprd.workbench.task.PrdWorkspace.numberHeadings(sb.toString());
    }

    private String buildReviewMarkdown(JsonNode payload, ApiModels.ReviewFeedbackResponse feedback) {
        StringBuilder sb = new StringBuilder();
        String title = payload.path("title").asText("需求评审意见与整改单");
        String summary = payload.path("summary").asText("");

        sb.append("# ").append(title).append("\n\n");
        if (!summary.isBlank()) {
            sb.append("> ").append(summary).append("\n\n");
        }

        // Blocking issues
        sb.append("## 一、 阻断性问题 (Blocking Issues)\n\n");
        JsonNode blockingIssues = payload.get("blockingIssues");
        if (blockingIssues != null && blockingIssues.isArray() && !blockingIssues.isEmpty()) {
            int idx = 0;
            for (JsonNode issue : blockingIssues) {
                String itemTitle = issue.path("title").asText(issue.path("name").asText("问题 " + (idx + 1)));
                String desc = issue.path("description").asText(issue.path("detail").asText(""));
                String impact = issue.path("impact").asText("");
                String fix = issue.path("fix").asText("");

                sb.append("### ").append(idx + 1).append(". ").append(itemTitle).append("\n\n");
                if (!desc.isBlank()) sb.append("- **问题描述**：").append(desc).append("\n");
                if (!impact.isBlank()) sb.append("- **影响分析**：").append(impact).append("\n");
                if (!fix.isBlank()) sb.append("- **整改建议**：").append(fix).append("\n");

                // Check feedback action
                if (feedback != null && feedback.items() != null) {
                    String path = "blockingIssues/" + idx;
                    for (ApiModels.ReviewFeedbackItem item : feedback.items()) {
                        if (path.equals(item.itemPath())) {
                            sb.append("- **采纳决策**：`").append(item.action()).append("`");
                            if (item.note() != null && !item.note().isBlank()) {
                                sb.append("（说明: ").append(item.note()).append("）");
                            }
                            sb.append("\n");
                        }
                    }
                }
                sb.append("\n");
                idx++;
            }
        } else {
            sb.append("未发现阻断性问题，材料达到开发可开工标准。\n\n");
        }

        // Other suggestions
        JsonNode suggestions = payload.get("otherSuggestions");
        if (suggestions != null && suggestions.isArray() && !suggestions.isEmpty()) {
            sb.append("## 二、 优化建议 (Other Suggestions)\n\n");
            int idx = 0;
            for (JsonNode sug : suggestions) {
                String sugText = sug.isTextual() ? sug.asText() : sug.path("detail").asText(sug.path("title").asText(""));
                sb.append("- ").append(sugText).append("\n");
                idx++;
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
