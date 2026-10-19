package org.hermesprd.workbench.testcase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.task.ApiModels;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCaseExcelExporterTest {
    private final TestCaseExcelExporter exporter = new TestCaseExcelExporter();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void preservesOrderedArrayStepsAndFitsWrappedRowsWithoutChangingPayload() throws Exception {
        var payload = mapper.readTree("""
                {"items":[{"caseId":"TC-ARRAY","steps":["以主管登录并打开待办", "核对5000.00元申请明细", "通过后检查财务待办为空", "由采购确认可创建订单"]}]}
                """);
        String before = payload.toString();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(exporter.export(payload, null)))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("1. 以主管登录并打开待办\n2. 核对5000.00元申请明细\n3. 通过后检查财务待办为空\n4. 由采购确认可创建订单", sheet.getRow(5).getCell(8).getStringCellValue());
            assertTrue(sheet.getRow(5).getHeightInPoints() > 34);
            assertNotNull(sheet.getPaneInformation());
        }
        assertEquals(before, payload.toString());
    }

    @Test
    void exportsValidSingleSheetTestCaseExcelWorkbook() throws Exception {
        var payload = mapper.readTree("""
                {
                  "mode": "TEST_CASE",
                  "title": "律所一体化云平台系统测试用例集",
                  "summary": "共计 2 条测试用例",
                  "scopeNote": "本次测试用例集覆盖系统核心业务功能流、边界值校验、逆向异常流与权限安全测试。",
                  "totalCount": 2,
                  "p0Count": 1,
                  "p1Count": 1,
                  "p2Count": 0,
                  "boundaryCount": 0,
                  "exceptionCount": 1,
                  "items": [
                    {
                      "caseId": "TC-001",
                      "module": "案源渠道",
                      "subModule": "渠道配置",
                      "title": "新增渠道基础配置成功",
                      "type": "FUNCTION",
                      "priority": "P0",
                      "precondition": "系统管理员已登录",
                      "steps": "1. 点击新增按钮\\n2. 填写渠道名称\\n3. 点击保存",
                      "testData": "渠道名称='抖音广告'",
                      "expectedResult": "提示保存成功并显示在列表首行",
                      "description": "正向主干流验证"
                    },
                    {
                      "caseId": "TC-002",
                      "module": "案源渠道",
                      "subModule": "渠道配置",
                      "title": "渠道名称重复时拦截报错",
                      "type": "EXCEPTION",
                      "priority": "P1",
                      "precondition": "已存在'抖音广告'",
                      "steps": "1. 点击新增\\n2. 输入'抖音广告'\\n3. 点击保存",
                      "testData": "渠道名称='抖音广告'",
                      "expectedResult": "表单提示渠道名称已存在",
                      "description": "唯一性约束拦截"
                    }
                  ]
                }
                """);

        ApiModels.ExportTestCaseRequest request = new ApiModels.ExportTestCaseRequest(
                "律所平台测试用例清单",
                null,
                null,
                null
        );

        byte[] bytes = exporter.export(payload, request);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        assertEquals(0x50, bytes[0]);
        assertEquals(0x4B, bytes[1]);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(1, wb.getNumberOfSheets());
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("测试用例清单", sheet.getSheetName());

            // Title
            assertEquals("律所平台测试用例清单", sheet.getRow(0).getCell(0).getStringCellValue());
            // Disclaimer / Scope
            assertTrue(sheet.getRow(1).getCell(0).getStringCellValue().contains("本次测试用例集覆盖系统核心业务功能流"));
            // KPI overview
            assertTrue(sheet.getRow(2).getCell(0).getStringCellValue().contains("总用例数: 2 条"));

            // Header (Row 4)
            assertEquals("序号", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals("用例编号", sheet.getRow(4).getCell(1).getStringCellValue());
            assertEquals("一级模块", sheet.getRow(4).getCell(2).getStringCellValue());
            assertEquals("二级模块", sheet.getRow(4).getCell(3).getStringCellValue());
            assertEquals("用例标题", sheet.getRow(4).getCell(4).getStringCellValue());
            assertEquals("测试类型", sheet.getRow(4).getCell(5).getStringCellValue());
            assertEquals("优先级", sheet.getRow(4).getCell(6).getStringCellValue());
            assertEquals("前置条件", sheet.getRow(4).getCell(7).getStringCellValue());
            assertEquals("操作步骤", sheet.getRow(4).getCell(8).getStringCellValue());
            assertEquals("测试数据", sheet.getRow(4).getCell(9).getStringCellValue());
            assertEquals("预期结果", sheet.getRow(4).getCell(10).getStringCellValue());

            // Data rows
            assertEquals(1, (int) sheet.getRow(5).getCell(0).getNumericCellValue());
            assertEquals("TC-001", sheet.getRow(5).getCell(1).getStringCellValue());
            assertEquals("案源渠道", sheet.getRow(5).getCell(2).getStringCellValue());
            assertEquals("渠道配置", sheet.getRow(5).getCell(3).getStringCellValue());
            assertEquals("新增渠道基础配置成功", sheet.getRow(5).getCell(4).getStringCellValue());
            assertEquals("功能测试", sheet.getRow(5).getCell(5).getStringCellValue());
            assertEquals("P0", sheet.getRow(5).getCell(6).getStringCellValue());
            assertEquals("1. 点击新增按钮\n2. 填写渠道名称\n3. 点击保存", sheet.getRow(5).getCell(8).getStringCellValue());

            // Row 6 (second case)
            assertEquals(2, (int) sheet.getRow(6).getCell(0).getNumericCellValue());
            assertEquals("TC-002", sheet.getRow(6).getCell(1).getStringCellValue());
            assertEquals("异常流", sheet.getRow(6).getCell(5).getStringCellValue());
            assertEquals("P1", sheet.getRow(6).getCell(6).getStringCellValue());

            // Total summary row at bottom
            assertEquals("【用例总计】", sheet.getRow(7).getCell(0).getStringCellValue());
            assertTrue(sheet.getRow(7).getCell(4).getStringCellValue().contains("共计 2 条测试用例"));
        }
    }

    @Test
    void supportsCustomColumnsExport() throws Exception {
        var payload = mapper.readTree("""
                {
                  "mode": "TEST_CASE",
                  "title": "系统测试用例集",
                  "totalCount": 1,
                  "items": [
                    {
                      "caseId": "TC-001",
                      "module": "模块A",
                      "title": "用例1",
                      "type": "FUNCTION",
                      "priority": "P0",
                      "expectedResult": "成功",
                      "extraFields": {
                        "executor": "张测试",
                        "resultStatus": "PASSED"
                      }
                    }
                  ]
                }
                """);

        ApiModels.ExportTestCaseRequest request = new ApiModels.ExportTestCaseRequest(
                "定制列用例集",
                List.of(
                        new ApiModels.ExportTestCaseColumn("caseId", "编号", true),
                        new ApiModels.ExportTestCaseColumn("title", "标题", true),
                        new ApiModels.ExportTestCaseColumn("executor", "执行人", true),
                        new ApiModels.ExportTestCaseColumn("resultStatus", "执行结果", true)
                ),
                null,
                null
        );

        byte[] bytes = exporter.export(payload, request);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("编号", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals("标题", sheet.getRow(4).getCell(1).getStringCellValue());
            assertEquals("执行人", sheet.getRow(4).getCell(2).getStringCellValue());
            assertEquals("执行结果", sheet.getRow(4).getCell(3).getStringCellValue());

            assertEquals("TC-001", sheet.getRow(5).getCell(0).getStringCellValue());
            assertEquals("用例1", sheet.getRow(5).getCell(1).getStringCellValue());
            assertEquals("张测试", sheet.getRow(5).getCell(2).getStringCellValue());
            assertEquals("PASSED", sheet.getRow(5).getCell(3).getStringCellValue());
        }
    }
}
