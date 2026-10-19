package org.hermesprd.workbench.testcase;

import com.fasterxml.jackson.databind.JsonNode;
import org.hermesprd.workbench.task.ApiModels;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TestCaseExcelExporter {

    private static final int MAX_CELL_LENGTH = 32767;

    private static String clip(String str) {
        if (str == null) return "";
        return str.length() <= MAX_CELL_LENGTH ? str : str.substring(0, MAX_CELL_LENGTH);
    }

    public record ColumnDef(String key, String title, int preferredWidth, HorizontalAlignment align) {
    }

    public byte[] export(JsonNode payload, ApiModels.ExportTestCaseRequest request) {
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("Payload cannot be null");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("测试用例清单");
            sheet.setDisplayGridlines(true);

            // Fonts
            Font titleFont = workbook.createFont();
            titleFont.setFontName("Microsoft YaHei");
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setBold(true);

            Font disclaimerFont = workbook.createFont();
            disclaimerFont.setFontName("Microsoft YaHei");
            disclaimerFont.setFontHeightInPoints((short) 10);
            disclaimerFont.setColor(IndexedColors.DARK_RED.getIndex());
            disclaimerFont.setBold(true);

            Font kpiFont = workbook.createFont();
            kpiFont.setFontName("Microsoft YaHei");
            kpiFont.setFontHeightInPoints((short) 10);
            kpiFont.setBold(true);

            Font headerFont = workbook.createFont();
            headerFont.setFontName("Microsoft YaHei");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);

            Font dataFont = workbook.createFont();
            dataFont.setFontName("Microsoft YaHei");
            dataFont.setFontHeightInPoints((short) 9);

            Font p0Font = workbook.createFont();
            p0Font.setFontName("Microsoft YaHei");
            p0Font.setFontHeightInPoints((short) 9);
            p0Font.setColor(IndexedColors.DARK_RED.getIndex());
            p0Font.setBold(true);

            Font p1Font = workbook.createFont();
            p1Font.setFontName("Microsoft YaHei");
            p1Font.setFontHeightInPoints((short) 9);
            p1Font.setColor(IndexedColors.DARK_YELLOW.getIndex());
            p1Font.setBold(true);

            Font p2Font = workbook.createFont();
            p2Font.setFontName("Microsoft YaHei");
            p2Font.setFontHeightInPoints((short) 9);
            p2Font.setColor(IndexedColors.DARK_BLUE.getIndex());

            Font totalFont = workbook.createFont();
            totalFont.setFontName("Microsoft YaHei");
            totalFont.setFontHeightInPoints((short) 10);
            totalFont.setBold(true);

            // Cell styles
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.LEFT);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle disclaimerStyle = workbook.createCellStyle();
            disclaimerStyle.setFont(disclaimerFont);
            disclaimerStyle.setAlignment(HorizontalAlignment.LEFT);
            disclaimerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            disclaimerStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            disclaimerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(disclaimerStyle, BorderStyle.THIN, IndexedColors.GREY_40_PERCENT.getIndex());

            CellStyle kpiStyle = workbook.createCellStyle();
            kpiStyle.setFont(kpiFont);
            kpiStyle.setAlignment(HorizontalAlignment.LEFT);
            kpiStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            kpiStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            kpiStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(kpiStyle, BorderStyle.THIN, IndexedColors.GREY_40_PERCENT.getIndex());

            // Header style: Deep blue #1F4E78
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 31, (byte) 78, (byte) 120}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(headerStyle, BorderStyle.THIN, IndexedColors.GREY_50_PERCENT.getIndex());

            // Data styles
            Map<HorizontalAlignment, CellStyle> textStyles = new LinkedHashMap<>();
            for (HorizontalAlignment align : HorizontalAlignment.values()) {
                CellStyle cs = workbook.createCellStyle();
                cs.setFont(dataFont);
                cs.setAlignment(align);
                cs.setVerticalAlignment(VerticalAlignment.CENTER);
                setBorders(cs, BorderStyle.THIN, IndexedColors.GREY_25_PERCENT.getIndex());
                textStyles.put(align, cs);
            }

            CellStyle wrapTextStyle = workbook.createCellStyle();
            wrapTextStyle.setFont(dataFont);
            wrapTextStyle.setAlignment(HorizontalAlignment.LEFT);
            wrapTextStyle.setVerticalAlignment(VerticalAlignment.TOP);
            wrapTextStyle.setWrapText(true);
            setBorders(wrapTextStyle, BorderStyle.THIN, IndexedColors.GREY_25_PERCENT.getIndex());

            CellStyle p0Style = workbook.createCellStyle();
            p0Style.setFont(p0Font);
            p0Style.setAlignment(HorizontalAlignment.CENTER);
            p0Style.setVerticalAlignment(VerticalAlignment.CENTER);
            p0Style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            p0Style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(p0Style, BorderStyle.THIN, IndexedColors.GREY_25_PERCENT.getIndex());

            CellStyle p1Style = workbook.createCellStyle();
            p1Style.setFont(p1Font);
            p1Style.setAlignment(HorizontalAlignment.CENTER);
            p1Style.setVerticalAlignment(VerticalAlignment.CENTER);
            p1Style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            p1Style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(p1Style, BorderStyle.THIN, IndexedColors.GREY_25_PERCENT.getIndex());

            CellStyle p2Style = workbook.createCellStyle();
            p2Style.setFont(p2Font);
            p2Style.setAlignment(HorizontalAlignment.CENTER);
            p2Style.setVerticalAlignment(VerticalAlignment.CENTER);
            p2Style.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            p2Style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(p2Style, BorderStyle.THIN, IndexedColors.GREY_25_PERCENT.getIndex());

            CellStyle totalLabelStyle = workbook.createCellStyle();
            totalLabelStyle.setFont(totalFont);
            totalLabelStyle.setAlignment(HorizontalAlignment.CENTER);
            totalLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            totalLabelStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            totalLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(totalLabelStyle, BorderStyle.MEDIUM, IndexedColors.GREY_50_PERCENT.getIndex());

            // Build Column List
            List<ColumnDef> columns = resolveColumns(request);
            int colCount = columns.size();

            int rowIndex = 0;

            // Row 0: Title
            String title = (request != null && request.title() != null && !request.title().isBlank())
                    ? request.title()
                    : payload.path("title").asText("系统测试用例集");
            Row titleRow = sheet.createRow(rowIndex++);
            titleRow.setHeightInPoints(32);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(clip(title));
            titleCell.setCellStyle(titleStyle);
            if (colCount > 1) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, colCount - 1));
            }

            // Row 1: Scope Disclaimer
            String disclaimer = payload.path("scopeNote").asText(
                    "【测试范围与说明】：本次测试用例集覆盖系统核心业务功能流、边界值校验、逆向异常流与权限安全测试。"
            );
            Row discRow = sheet.createRow(rowIndex++);
            discRow.setHeightInPoints(24);
            Cell discCell = discRow.createCell(0);
            discCell.setCellValue(clip(disclaimer));
            discCell.setCellStyle(disclaimerStyle);
            for (int c = 1; c < colCount; c++) {
                discRow.createCell(c).setCellStyle(disclaimerStyle);
            }
            if (colCount > 1) {
                sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, colCount - 1));
            }

            // Row 2: KPI Summary
            int totalCount = payload.path("totalCount").asInt(0);
            int p0Count = payload.path("p0Count").asInt(0);
            int p1Count = payload.path("p1Count").asInt(0);
            int p2Count = payload.path("p2Count").asInt(0);
            int boundaryCount = payload.path("boundaryCount").asInt(0);
            int exceptionCount = payload.path("exceptionCount").asInt(0);

            String kpiText = String.format(
                    "用例概览：总用例数: %d 条  |  P0 冒烟核心: %d 条  |  P1 重要功能: %d 条  |  P2 次要场景: %d 条  |  边界值测试: %d 条  |  异常流拦截: %d 条",
                    totalCount, p0Count, p1Count, p2Count, boundaryCount, exceptionCount
            );

            Row kpiRow = sheet.createRow(rowIndex++);
            kpiRow.setHeightInPoints(22);
            Cell kpiCell = kpiRow.createCell(0);
            kpiCell.setCellValue(clip(kpiText));
            kpiCell.setCellStyle(kpiStyle);
            for (int c = 1; c < colCount; c++) {
                kpiRow.createCell(c).setCellStyle(kpiStyle);
            }
            if (colCount > 1) {
                sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, colCount - 1));
            }

            // Row 3: Spacer
            Row spacerRow = sheet.createRow(rowIndex++);
            spacerRow.setHeightInPoints(8);

            // Row 4: Header
            Row headerRow = sheet.createRow(rowIndex++);
            headerRow.setHeightInPoints(26);
            for (int c = 0; c < colCount; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(clip(columns.get(c).title()));
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            JsonNode items = payload.path("items");
            int itemSeq = 1;
            int dataStartRow = rowIndex;

            if (items.isArray()) {
                for (JsonNode item : items) {
                    Row dataRow = sheet.createRow(rowIndex++);
                    // Auto height or default 36pt for multi-line steps
                    dataRow.setHeightInPoints(34);

                    for (int c = 0; c < colCount; c++) {
                        ColumnDef col = columns.get(c);
                        Cell cell = dataRow.createCell(c);
                        renderCell(cell, col.key(), itemSeq, item,
                                textStyles.get(col.align()), wrapTextStyle,
                                p0Style, p1Style, p2Style,
                                request != null ? request.extraFieldValues() : null);
                    }
                    int lines = 1;
                    for (int c = 0; c < colCount; c++) {
                        Cell cell = dataRow.getCell(c);
                        if (cell.getCellStyle().getWrapText()) {
                            int count = 0;
                            for (String line : cell.getStringCellValue().split("\\n", -1)) {
                                int width = line.codePoints().map(cp -> cp > 255 ? 2 : 1).sum();
                                count += Math.max(1, (int) Math.ceil((double) width / columns.get(c).preferredWidth()));
                            }
                            lines = Math.max(lines, count);
                        }
                    }
                    dataRow.setHeightInPoints(Math.min(409, Math.max(34, lines * 13 + 8)));
                    itemSeq++;
                }
            }
            int dataEndRow = rowIndex - 1;

            // Total Summary Row at bottom
            if (dataEndRow >= dataStartRow) {
                Row totalRow = sheet.createRow(rowIndex++);
                totalRow.setHeightInPoints(24);

                for (int c = 0; c < colCount; c++) {
                    ColumnDef col = columns.get(c);
                    Cell cell = totalRow.createCell(c);
                    switch (col.key()) {
                        case "index":
                        case "caseId":
                        case "module":
                            cell.setCellValue("【用例总计】");
                            cell.setCellStyle(totalLabelStyle);
                            break;
                        case "title":
                            cell.setCellValue(String.format("共计 %d 条测试用例", itemSeq - 1));
                            cell.setCellStyle(totalLabelStyle);
                            break;
                        case "priority":
                            cell.setCellValue(String.format("P0:%d / P1:%d / P2:%d", p0Count, p1Count, p2Count));
                            cell.setCellStyle(totalLabelStyle);
                            break;
                        default:
                            cell.setCellValue("");
                            cell.setCellStyle(totalLabelStyle);
                            break;
                    }
                }
            }

            // Set Column Widths
            for (int c = 0; c < colCount; c++) {
                sheet.setColumnWidth(c, columns.get(c).preferredWidth() * 256);
            }
            sheet.createFreezePane(2, 5);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to export test cases Excel workbook", e);
        }
    }

    private List<ColumnDef> resolveColumns(ApiModels.ExportTestCaseRequest request) {
        Map<String, ColumnDef> allDefaultColumns = new LinkedHashMap<>();
        allDefaultColumns.put("index", new ColumnDef("index", "序号", 8, HorizontalAlignment.CENTER));
        allDefaultColumns.put("caseId", new ColumnDef("caseId", "用例编号", 14, HorizontalAlignment.CENTER));
        allDefaultColumns.put("module", new ColumnDef("module", "一级模块", 16, HorizontalAlignment.LEFT));
        allDefaultColumns.put("subModule", new ColumnDef("subModule", "二级模块", 16, HorizontalAlignment.LEFT));
        allDefaultColumns.put("title", new ColumnDef("title", "用例标题", 28, HorizontalAlignment.LEFT));
        allDefaultColumns.put("type", new ColumnDef("type", "测试类型", 12, HorizontalAlignment.CENTER));
        allDefaultColumns.put("priority", new ColumnDef("priority", "优先级", 10, HorizontalAlignment.CENTER));
        allDefaultColumns.put("precondition", new ColumnDef("precondition", "前置条件", 25, HorizontalAlignment.LEFT));
        allDefaultColumns.put("steps", new ColumnDef("steps", "操作步骤", 40, HorizontalAlignment.LEFT));
        allDefaultColumns.put("testData", new ColumnDef("testData", "测试数据", 20, HorizontalAlignment.LEFT));
        allDefaultColumns.put("expectedResult", new ColumnDef("expectedResult", "预期结果", 35, HorizontalAlignment.LEFT));
        allDefaultColumns.put("description", new ColumnDef("description", "设计关注点/说明", 25, HorizontalAlignment.LEFT));

        if (request != null && request.columns() != null && !request.columns().isEmpty()) {
            List<ColumnDef> resolved = new ArrayList<>();
            for (ApiModels.ExportTestCaseColumn col : request.columns()) {
                if (col.visible() != null && !col.visible()) {
                    continue;
                }
                if (allDefaultColumns.containsKey(col.key())) {
                    ColumnDef def = allDefaultColumns.get(col.key());
                    String title = (col.title() != null && !col.title().isBlank()) ? col.title() : def.title();
                    resolved.add(new ColumnDef(def.key(), title, def.preferredWidth(), def.align()));
                } else {
                    String title = (col.title() != null && !col.title().isBlank()) ? col.title() : col.key();
                    resolved.add(new ColumnDef(col.key(), title, 16, HorizontalAlignment.LEFT));
                }
            }
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }

        return new ArrayList<>(allDefaultColumns.values());
    }

    private void renderCell(
            Cell cell,
            String key,
            int seq,
            JsonNode item,
            CellStyle textStyle,
            CellStyle wrapTextStyle,
            CellStyle p0Style,
            CellStyle p1Style,
            CellStyle p2Style,
            JsonNode extraFieldValues
    ) {
        switch (key) {
            case "index" -> {
                cell.setCellValue(seq);
                cell.setCellStyle(textStyle);
            }
            case "caseId" -> {
                cell.setCellValue(clip(item.path("caseId").asText("")));
                cell.setCellStyle(textStyle);
            }
            case "module" -> {
                cell.setCellValue(clip(item.path("module").asText("")));
                cell.setCellStyle(textStyle);
            }
            case "subModule" -> {
                cell.setCellValue(clip(item.path("subModule").asText("")));
                cell.setCellStyle(textStyle);
            }
            case "title" -> {
                cell.setCellValue(clip(item.path("title").asText("")));
                cell.setCellStyle(textStyle);
            }
            case "type" -> {
                String raw = item.path("type").asText("").toUpperCase();
                String label = switch (raw) {
                    case "FUNCTION" -> "功能测试";
                    case "BOUNDARY" -> "边界值";
                    case "EXCEPTION" -> "异常流";
                    case "PERMISSION" -> "权限安全";
                    case "CONCURRENCY" -> "并发测试";
                    default -> raw;
                };
                cell.setCellValue(clip(label));
                cell.setCellStyle(textStyle);
            }
            case "priority" -> {
                String p = item.path("priority").asText("P1").toUpperCase();
                cell.setCellValue(clip(p));
                if ("P0".equals(p)) {
                    cell.setCellStyle(p0Style);
                } else if ("P2".equals(p)) {
                    cell.setCellStyle(p2Style);
                } else {
                    cell.setCellStyle(p1Style);
                }
            }
            case "precondition" -> {
                cell.setCellValue(clip(item.path("precondition").asText("")));
                cell.setCellStyle(wrapTextStyle);
            }
            case "steps" -> {
                JsonNode steps = item.path("steps");
                String text = steps.asText("");
                if (steps.isArray()) {
                    StringBuilder ordered = new StringBuilder();
                    for (int i = 0; i < steps.size(); i++) {
                        if (i > 0) ordered.append('\n');
                        ordered.append(i + 1).append(". ").append(steps.get(i).asText());
                    }
                    text = ordered.toString();
                }
                cell.setCellValue(clip(text));
                cell.setCellStyle(wrapTextStyle);
            }
            case "testData" -> {
                cell.setCellValue(clip(item.path("testData").asText("")));
                cell.setCellStyle(textStyle);
            }
            case "expectedResult" -> {
                cell.setCellValue(clip(item.path("expectedResult").asText("")));
                cell.setCellStyle(wrapTextStyle);
            }
            case "description" -> {
                cell.setCellValue(clip(item.path("description").asText("")));
                cell.setCellStyle(wrapTextStyle);
            }
            default -> {
                String val = "";
                if (item.has("extraFields") && item.get("extraFields").has(key)) {
                    val = item.get("extraFields").path(key).asText("");
                } else if (extraFieldValues != null && extraFieldValues.has(key)) {
                    val = extraFieldValues.path(key).asText("");
                }
                cell.setCellValue(clip(val));
                cell.setCellStyle(textStyle);
            }
        }
    }

    private void setBorders(CellStyle style, BorderStyle border, short color) {
        style.setBorderTop(border);
        style.setTopBorderColor(color);
        style.setBorderBottom(border);
        style.setBottomBorderColor(color);
        style.setBorderLeft(border);
        style.setLeftBorderColor(color);
        style.setBorderRight(border);
        style.setRightBorderColor(color);
    }
}
