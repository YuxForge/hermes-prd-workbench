package org.hermesprd.workbench.estimate;

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

class EstimateExcelExporterTest {
    private final EstimateExcelExporter exporter = new EstimateExcelExporter();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void missingOptionalTotalsStayMissingInsteadOfZeroOrDerivedValues() throws Exception {
        var payload = mapper.readTree("""
                {"title":"合成估算","scopeNote":"研发自测", "items":[
                  {"name":"页面","type":"PAGE","devDays":2.125,"testDays":0.5},
                  {"name":"接口","type":"API","devDays":0,"testDays":0,"totalDays":0}
                ]}
                """);
        String before = payload.toString();
        var request = new ApiModels.ExportEstimateRequest(null, 1000.0, null, null, null);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(exporter.export(payload, request)))) {
            Sheet sheet = wb.getSheetAt(0);
            String kpi = sheet.getRow(2).getCell(0).getStringCellValue();
            assertTrue(kpi.contains("总预估人天: 未提供"));
            assertTrue(kpi.contains("开发: 未提供"));
            assertTrue(kpi.contains("自测: 未提供"));
            assertTrue(kpi.contains("预估开发总金额: 未提供"));
            assertEquals(2.125, sheet.getRow(5).getCell(7).getNumericCellValue());
            assertEquals("未提供", sheet.getRow(5).getCell(9).getStringCellValue());
            assertEquals("未提供", sheet.getRow(5).getCell(11).getStringCellValue());
            assertEquals(0.0, sheet.getRow(6).getCell(9).getNumericCellValue());
            assertEquals(0.0, sheet.getRow(6).getCell(11).getNumericCellValue());
            assertEquals("未提供", sheet.getRow(7).getCell(9).getStringCellValue());
            assertEquals("未提供", sheet.getRow(7).getCell(11).getStringCellValue());
        }
        assertEquals(before, payload.toString());
    }

    @Test
    void customPriceColumnsWithoutRateStayMissingIncludingSummary() throws Exception {
        var payload = mapper.readTree("""
                {"items":[{"devDays":1,"testDays":1,"totalDays":2}]}
                """);
        var request = new ApiModels.ExportEstimateRequest(null, null, List.of(
                new ApiModels.ExportEstimateColumn("unitPrice", null, true),
                new ApiModels.ExportEstimateColumn("totalPrice", null, true)
        ), null, null);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(exporter.export(payload, request)))) {
            assertEquals("未提供", wb.getSheetAt(0).getRow(5).getCell(0).getStringCellValue());
            assertEquals("未提供", wb.getSheetAt(0).getRow(5).getCell(1).getStringCellValue());
            assertEquals("未提供", wb.getSheetAt(0).getRow(6).getCell(1).getStringCellValue());
        }
    }

    @Test
    void suppliedSummaryIsNotReconciledWithDevelopmentAndTestDays() throws Exception {
        var payload = mapper.readTree("""
                {"totalDays":99.125,"devDays":2,"testDays":1,"items":[]}
                """);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(exporter.export(payload, null)))) {
            assertTrue(wb.getSheetAt(0).getRow(2).getCell(0).getStringCellValue().contains("总预估人天: 99.125 天"));
        }
    }

    @Test
    void exportsValidSingleSheetExcelWorkbookWithFormulasAndDisclaimer() throws Exception {
        var payload = mapper.readTree("""
                {
                  "mode": "ESTIMATE",
                  "title": "律所一体化云平台工作量评估",
                  "summary": "预估 35.6 人天",
                  "scopeNote": "本次报价仅包含开发和测试（研发自测）的工作量，不含产品设计、运维、项目管理的工作量。",
                  "totalDays": 35.6,
                  "devDays": 26.8,
                  "testDays": 8.8,
                  "items": [
                    {
                      "module": "线索管理",
                      "subModule": "公海池",
                      "type": "PAGE",
                      "name": "线索列表台账",
                      "detail": "支持复杂组合筛选与 Excel 批量导入导出",
                      "complexity": "COMPLEX",
                      "devDays": 0.8,
                      "testDays": 0.2,
                      "totalDays": 1.0,
                      "description": "基础页面0.5天+批量导入导出0.3天"
                    },
                    {
                      "module": "线索管理",
                      "subModule": "公海池",
                      "type": "API",
                      "name": "POST /api/v1/leads/export",
                      "detail": "异步导出数据流",
                      "complexity": "MEDIUM",
                      "devDays": 0.5,
                      "testDays": 0.1,
                      "totalDays": 0.6,
                      "description": "标准接口0.5天"
                    }
                  ]
                }
                """);

        ApiModels.ExportEstimateRequest request = new ApiModels.ExportEstimateRequest(
                "律所一体化云平台报价单",
                1200.0,
                null,
                null,
                null
        );

        byte[] bytes = exporter.export(payload, request);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        // Check zip magic bytes PK..
        assertEquals(0x50, bytes[0]);
        assertEquals(0x4B, bytes[1]);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(1, wb.getNumberOfSheets());
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("工作量评估与报价单", sheet.getSheetName());

            // Title
            assertEquals("律所一体化云平台报价单", sheet.getRow(0).getCell(0).getStringCellValue());
            // Disclaimer
            assertTrue(sheet.getRow(1).getCell(0).getStringCellValue().contains("本次报价仅包含开发和测试"));
            // KPI row
            assertTrue(sheet.getRow(2).getCell(0).getStringCellValue().contains("1200.00/天"));

            // Check Header row (Row 4)
            assertEquals("序号", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals("一级模块", sheet.getRow(4).getCell(1).getStringCellValue());

            // Check Data rows
            assertEquals(1, (int) sheet.getRow(5).getCell(0).getNumericCellValue());
            assertEquals("线索管理", sheet.getRow(5).getCell(1).getStringCellValue());
            assertEquals("公海池", sheet.getRow(5).getCell(2).getStringCellValue());
            assertEquals("页面", sheet.getRow(5).getCell(3).getStringCellValue());
            assertEquals(0.8, sheet.getRow(5).getCell(7).getNumericCellValue(), 0.001);
            assertEquals(0.2, sheet.getRow(5).getCell(8).getNumericCellValue(), 0.001);
            assertEquals(1.0, sheet.getRow(5).getCell(9).getNumericCellValue(), 0.001);

            // Check total summary formula row (Row 7)
            assertEquals("【全项目总计】", sheet.getRow(7).getCell(0).getStringCellValue());
            assertTrue(sheet.getRow(7).getCell(7).getCellFormula().contains("SUM"));
        }
    }

    @Test
    void supportsCustomColumnSelection() throws Exception {
        var payload = mapper.readTree("""
                {
                  "mode": "ESTIMATE",
                  "title": "极简评估",
                  "summary": "10天",
                  "scopeNote": "本次报价仅包含开发和测试的工作量...",
                  "totalDays": 10.0,
                  "devDays": 8.0,
                  "testDays": 2.0,
                  "items": [
                    {
                      "module": "系统管理",
                      "subModule": "用户",
                      "type": "PAGE",
                      "name": "用户管理页",
                      "detail": "增删改查",
                      "complexity": "SIMPLE",
                      "devDays": 0.5,
                      "testDays": 0.2,
                      "totalDays": 0.7,
                      "description": "基础页面"
                    }
                  ]
                }
                """);

        ApiModels.ExportEstimateRequest request = new ApiModels.ExportEstimateRequest(
                null,
                null,
                List.of(
                        new ApiModels.ExportEstimateColumn("module", "模块名称", true),
                        new ApiModels.ExportEstimateColumn("name", "页面/接口", true),
                        new ApiModels.ExportEstimateColumn("totalDays", "工时", true),
                        new ApiModels.ExportEstimateColumn("devDays", "开发", false) // hidden
                ),
                null,
                null
        );

        byte[] bytes = exporter.export(payload, request);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("模块名称", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals("页面/接口", sheet.getRow(4).getCell(1).getStringCellValue());
            assertEquals("工时", sheet.getRow(4).getCell(2).getStringCellValue());
            // Col 3 does not exist or null
            assertEquals(3, sheet.getRow(4).getLastCellNum());
        }
    }

    @Test
    void clipsCellTextOver32767CharactersWithoutThrowing() throws Exception {
        String longText = "A".repeat(35000);
        var payload = mapper.readTree(String.format("""
                {
                  "mode": "ESTIMATE",
                  "title": "测试长文本",
                  "summary": "超长文本",
                  "scopeNote": "%s",
                  "totalDays": 1.0,
                  "devDays": 0.8,
                  "testDays": 0.2,
                  "items": [
                    {
                      "module": "模块1",
                      "subModule": "子模块1",
                      "type": "PAGE",
                      "name": "页面1",
                      "detail": "%s",
                      "complexity": "SIMPLE",
                      "devDays": 0.8,
                      "testDays": 0.2,
                      "totalDays": 1.0,
                      "description": "%s"
                    }
                  ]
                }
                """, longText, longText, longText));

        byte[] bytes = exporter.export(payload, null);
        assertNotNull(bytes);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            String scopeNote = sheet.getRow(1).getCell(0).getStringCellValue();
            assertEquals(32767, scopeNote.length(), "Cell text should be clipped at 32767 chars");
        }
    }
}
