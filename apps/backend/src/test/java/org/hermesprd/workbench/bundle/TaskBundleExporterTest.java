package org.hermesprd.workbench.bundle;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class TaskBundleExporterTest {
    private TaskService taskService;
    private RunService runService;
    private RunMapper runMapper;
    private ReviewFeedbackService reviewFeedbackService;
    private EstimateExcelExporter estimateExcelExporter;
    private TestCaseExcelExporter testCaseExcelExporter;
    private ObjectMapper objectMapper;
    private TaskBundleExporter bundleExporter;

    @BeforeEach
    void setUp() {
        taskService = Mockito.mock(TaskService.class);
        runService = Mockito.mock(RunService.class);
        runMapper = Mockito.mock(RunMapper.class);
        reviewFeedbackService = Mockito.mock(ReviewFeedbackService.class);
        estimateExcelExporter = Mockito.mock(EstimateExcelExporter.class);
        testCaseExcelExporter = Mockito.mock(TestCaseExcelExporter.class);
        objectMapper = new ObjectMapper();

        bundleExporter = new TaskBundleExporter(
                taskService,
                runService,
                runMapper,
                reviewFeedbackService,
                estimateExcelExporter,
                testCaseExcelExporter,
                new TechSpecMarkdownExporter(),
                objectMapper
        );
    }

    @Test
    void overviewDoesNotTurnMissingEstimateTotalIntoZero() throws Exception {
        TaskEntity task = new TaskEntity();
        task.setId("task_missing_total");
        task.setTitle("合成估算");
        when(taskService.requireOwned("user_1", task.getId())).thenReturn(task);
        RunEntity run = new RunEntity();
        run.setId("run_est_missing");
        run.setMode("ESTIMATE");
        run.setStatus("SUCCEEDED");
        run.setCreatedAt(Instant.now());
        when(runMapper.selectList(any())).thenReturn(List.of(run));
        ResultEntity result = new ResultEntity();
        when(runService.findResult(run.getId())).thenReturn(result);
        var payload = objectMapper.readTree("""
                {"items":[{"devDays":2,"testDays":1}]}
                """);
        when(runService.resultPayload(result)).thenReturn(payload);
        when(estimateExcelExporter.export(eq(payload), any())).thenReturn(new byte[]{1});
        boolean found = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bundleExporter.exportBundle("user_1", task.getId())), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("00_项目交付清单与资产概览.md")) {
                    String overview = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(overview.contains("预估总人天 未提供（见明细）"));
                    assertTrue(!overview.contains("预估总人天 0"));
                    found = true;
                }
            }
        }
        assertTrue(found);
    }

    @Test
    void exportsFullZipBundleWithAllAssets() throws Exception {
        String userId = "user_1";
        String taskId = "task_bundle_test";

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setTitle("智能协同办公管理系统");
        task.setUserId(userId);
        task.setCreatedAt(Instant.now());

        when(taskService.requireOwned(userId, taskId)).thenReturn(task);

        Instant now = Instant.now();
        List<RunEntity> runs = new ArrayList<>();

        RunEntity prdRun = new RunEntity();
        prdRun.setId("run_prd");
        prdRun.setTaskId(taskId);
        prdRun.setMode("GENERATE");
        prdRun.setStatus("SUCCEEDED");
        prdRun.setCreatedAt(now.minusSeconds(300));
        runs.add(prdRun);

        RunEntity demoRun = new RunEntity();
        demoRun.setId("run_demo");
        demoRun.setTaskId(taskId);
        demoRun.setMode("DEMO");
        demoRun.setStatus("SUCCEEDED");
        demoRun.setCreatedAt(now.minusSeconds(200));
        runs.add(demoRun);

        RunEntity pocRun = new RunEntity();
        pocRun.setId("run_poc");
        pocRun.setTaskId(taskId);
        pocRun.setMode("POC");
        pocRun.setStatus("SUCCEEDED");
        pocRun.setCreatedAt(now.minusSeconds(180));
        runs.add(pocRun);

        RunEntity reviewRun = new RunEntity();
        reviewRun.setId("run_review");
        reviewRun.setTaskId(taskId);
        reviewRun.setMode("REVIEW");
        reviewRun.setStatus("SUCCEEDED");
        reviewRun.setCreatedAt(now.minusSeconds(150));
        runs.add(reviewRun);

        RunEntity estRun = new RunEntity();
        estRun.setId("run_est");
        estRun.setTaskId(taskId);
        estRun.setMode("ESTIMATE");
        estRun.setStatus("SUCCEEDED");
        estRun.setCreatedAt(now.minusSeconds(100));
        runs.add(estRun);

        RunEntity tcRun = new RunEntity();
        tcRun.setId("run_tc");
        tcRun.setTaskId(taskId);
        tcRun.setMode("TEST_CASE");
        tcRun.setStatus("SUCCEEDED");
        tcRun.setCreatedAt(now.minusSeconds(50));
        runs.add(tcRun);

        RunEntity tsRun = new RunEntity();
        tsRun.setId("run_ts");
        tsRun.setTaskId(taskId);
        tsRun.setMode("TECH_SPEC");
        tsRun.setStatus("SUCCEEDED");
        tsRun.setCreatedAt(now);
        runs.add(tsRun);

        when(runMapper.selectList(any())).thenReturn(runs);

        // Mock results
        ResultEntity prdResult = new ResultEntity();
        when(runService.findResult("run_prd")).thenReturn(prdResult);
        when(runService.resultPayload(prdResult)).thenReturn(objectMapper.readTree("""
                {"title": "OA系统PRD", "summary": "OA办公PRD说明", "markdown": "## 1. 业务目标\\n打通全流程。"}
                """));

        ResultEntity demoResult = new ResultEntity();
        when(runService.findResult("run_demo")).thenReturn(demoResult);
        when(runService.resultPayload(demoResult)).thenReturn(objectMapper.readTree("""
                {"title": "OA Demo", "summary": "交互Demo", "html": "<!DOCTYPE html><html><body><h1>OA工作台</h1></body></html>"}
                """));

        ResultEntity pocResult = new ResultEntity();
        when(runService.findResult("run_poc")).thenReturn(pocResult);
        when(runService.resultPayload(pocResult)).thenReturn(objectMapper.readTree("""
                {"title":"OA POC","summary":"静态POC","html":"<!DOCTYPE html><html><body><div class=app>POC</div></body></html>","poc_files":{"index.html":"<!DOCTYPE html><html><body><div class=app>POC</div></body></html>","css/app.css":".app{color:red}"}}
                """));

        ResultEntity reviewResult = new ResultEntity();
        when(runService.findResult("run_review")).thenReturn(reviewResult);
        when(runService.resultPayload(reviewResult)).thenReturn(objectMapper.readTree("""
                {"title": "OA评审", "summary": "评审意见", "blockingIssues": [{"title": "缺少状态机定义", "description": "审批无驳回状态", "impact": "阻塞开发", "fix": "补充REJECTED状态"}]}
                """));
        when(reviewFeedbackService.get(userId, "run_review")).thenReturn(new ApiModels.ReviewFeedbackResponse("run_review", 1, List.of(
                new ApiModels.ReviewFeedbackItem("blockingIssues/0", "EDITED", "已补充状态定义")
        )));

        ResultEntity estResult = new ResultEntity();
        when(runService.findResult("run_est")).thenReturn(estResult);
        var estPayload = objectMapper.readTree("""
                {"title": "OA估算", "summary": "工作量估算", "totalDays": 25.0, "items": [{"name": "审批流", "devDays": 2.0}]}
                """);
        when(runService.resultPayload(estResult)).thenReturn(estPayload);
        when(estimateExcelExporter.export(eq(estPayload), any())).thenReturn("DUMMY_ESTIMATE_EXCEL".getBytes(StandardCharsets.UTF_8));

        ResultEntity tcResult = new ResultEntity();
        when(runService.findResult("run_tc")).thenReturn(tcResult);
        var tcPayload = objectMapper.readTree("""
                {"title": "OA测试用例", "summary": "用例集", "totalCount": 20, "p0Count": 8, "items": [{"caseId": "TC-001"}],
                 "test_case_files":{"README.md":"# 用例","data/catalog.json":"{\\"modules\\":[]}","modules/oa.json":"[]"}}
                """);
        when(runService.resultPayload(tcResult)).thenReturn(tcPayload);
        when(testCaseExcelExporter.export(eq(tcPayload), any())).thenReturn("DUMMY_TEST_CASE_EXCEL".getBytes(StandardCharsets.UTF_8));

        ResultEntity tsResult = new ResultEntity();
        when(runService.findResult("run_ts")).thenReturn(tsResult);
        when(runService.resultPayload(tsResult)).thenReturn(objectMapper.readTree("""
                {
                    "title": "OA技术方案",
                    "summary": "系统分层架构方案",
                    "architectureMarkdown": "## 系统架构\\nSpringBoot + Vue3",
                    "databaseTables": [
                        {"tableName": "oa_flow", "columns": [{"name": "id", "type": "BIGINT"}]}
                    ],
                    "apiEndpoints": [
                        {"method": "POST", "path": "/api/flow", "summary": "创建流程"}
                    ],
                    "tech_spec_files": {
                        "README.md": "# 方案",
                        "architecture.md": "## 系统架构",
                        "data/tables.json": "[]",
                        "data/apis.json": "[]"
                    }
                }
                """));

        byte[] zipBytes = bundleExporter.exportBundle(userId, taskId);
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);

        // Read zip entries
        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                byte[] content = zis.readAllBytes();
                assertTrue(content.length > 0);
                if ("00_项目交付清单与资产概览.md".equals(entry.getName())) {
                    String md = new String(content, StandardCharsets.UTF_8);
                    assertTrue(md.contains("智能协同办公管理系统"));
                    assertTrue(md.contains("一核双翼全生命周期资产矩阵"));
                    assertTrue(md.contains("01_需求规格说明书_PRD.md"));
                    assertTrue(md.contains("06_研发技术方案_TechSpec.md"));
                }
                if ("03_需求评审与整改确认单_Review.md".equals(entry.getName())) {
                    String md = new String(content, StandardCharsets.UTF_8);
                    assertTrue(md.contains("缺少状态机定义"));
                    assertTrue(md.contains("已补充状态定义"));
                }
            }
        }

        assertEquals(16, entryNames.size());
        assertTrue(entryNames.contains("00_项目交付清单与资产概览.md"));
        assertTrue(entryNames.contains("01_需求规格说明书_PRD.md"));
        assertTrue(entryNames.contains("02_产品交互原型_Demo.html"));
        assertTrue(entryNames.contains("07_POC演示系统/index.html"));
        assertTrue(entryNames.contains("07_POC演示系统/css/app.css"));
        assertTrue(entryNames.contains("03_需求评审与整改确认单_Review.md"));
        assertTrue(entryNames.contains("04_工作量评估与报价表_Estimate.xlsx"));
        assertTrue(entryNames.contains("05_系统测试用例清单_TestCase.xlsx"));
        assertTrue(entryNames.contains("05_测试用例包/README.md"));
        assertTrue(entryNames.contains("05_测试用例包/data/catalog.json"));
        assertTrue(entryNames.contains("05_测试用例包/modules/oa.json"));
        assertTrue(entryNames.contains("06_研发技术方案_TechSpec.md"));
        assertTrue(entryNames.contains("06_技术方案包/README.md"));
        assertTrue(entryNames.contains("06_技术方案包/architecture.md"));
        assertTrue(entryNames.contains("06_技术方案包/data/tables.json"));
        assertTrue(entryNames.contains("06_技术方案包/data/apis.json"));
    }

    @Test
    void exportsGenerateDocumentPackWhenPrdFilesPresent() throws Exception {
        String userId = "user_1";
        String taskId = "task_prd_pack";
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setTitle("示例财务");
        task.setUserId(userId);
        task.setCreatedAt(Instant.now());
        when(taskService.requireOwned(userId, taskId)).thenReturn(task);

        RunEntity prdRun = new RunEntity();
        prdRun.setId("run_prd_pack");
        prdRun.setTaskId(taskId);
        prdRun.setMode("GENERATE");
        prdRun.setStatus("SUCCEEDED");
        prdRun.setCreatedAt(Instant.now());
        when(runMapper.selectList(any())).thenReturn(List.of(prdRun));

        ResultEntity prdResult = new ResultEntity();
        when(runService.findResult("run_prd_pack")).thenReturn(prdResult);
        when(runService.resultPayload(prdResult)).thenReturn(objectMapper.readTree("""
                {
                  "title": "示例财务PRD",
                  "summary": "可开工文档包",
                  "markdown": "# 概述\\n记账",
                  "prd_files": {
                    "README.md": "# 顺序",
                    "modules/voucher.md": "# 凭证"
                  }
                }
                """));

        byte[] zipBytes = bundleExporter.exportBundle(userId, taskId);
        List<String> entryNames = new ArrayList<>();
        String mergedPrd = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                if ("01_需求规格说明书_PRD.md".equals(entry.getName())) {
                    mergedPrd = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        assertTrue(entryNames.contains("01_需求规格说明书_PRD.md"));
        assertTrue(entryNames.contains("02_PRD文档包/README.md"));
        assertTrue(entryNames.contains("02_PRD文档包/modules/voucher.md"));
        assertTrue(entryNames.contains("02_PRD文档包/PRD.md"));
        assertNotNull(mergedPrd);
        assertTrue(mergedPrd.contains("# 1 示例财务PRD"));
        assertTrue(mergedPrd.contains("# 2 概述"));
    }
}
