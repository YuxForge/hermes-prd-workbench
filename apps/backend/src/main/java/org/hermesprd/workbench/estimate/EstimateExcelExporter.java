package org.hermesprd.workbench.estimate;

import com.fasterxml.jackson.databind.JsonNode;
import org.hermesprd.workbench.task.ApiModels;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
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
public class EstimateExcelExporter {

    private static final int MAX_CELL_LENGTH = 32767;

    private static String clip(String str) {
        if (str == null) return "";
        return str.length() <= MAX_CELL_LENGTH ? str : str.substring(0, MAX_CELL_LENGTH);
    }

    public record ColumnDef(String key, String title, int preferredWidth, HorizontalAlignment align) {
    }

    public byte[] export(JsonNode payload, ApiModels.ExportEstimateRequest request) {
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("Payload cannot be null");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("工作量评估与报价单");
            sheet.setDisplayGridlines(true);

            DataFormat dataFormat = workbook.createDataFormat();

            // Font styles
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

            // Data cell styles
            Map<HorizontalAlignment, CellStyle> textStyles = new LinkedHashMap<>();
            for (HorizontalAlignment align : HorizontalAlignment.values()) {
                CellStyle cs = workbook.createCellStyle();
                cs.setFont(dataFont);
                cs.setAlignment(align);
                cs.setVerticalAlignment(VerticalAlignment.CENTER);
                setBorders(cs, BorderStyle.THIN, IndexedColors.GREY_25_PERCENT.getIndex());
                textStyles.put(align, cs);
            }

            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setFont(dataFont);
            numberStyle.setAlignment(HorizontalAlignment.RIGHT);
            numberStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            numberStyle.setDataFormat(dataFormat.getFormat("0.0"));
            setBorders(numberStyle, BorderStyle.THIN, IndexedColors.GREY_25_PERCENT.getIndex());

            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setFont(dataFont);
            currencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            currencyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            currencyStyle.setDataFormat(dataFormat.getFormat("¥#,##0.00"));
            setBorders(currencyStyle, BorderStyle.THIN, IndexedColors.GREY_25_PERCENT.getIndex());

            // Total styles
            CellStyle totalLabelStyle = workbook.createCellStyle();
            totalLabelStyle.setFont(totalFont);
            totalLabelStyle.setAlignment(HorizontalAlignment.CENTER);
            totalLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            totalLabelStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            totalLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(totalLabelStyle, BorderStyle.MEDIUM, IndexedColors.GREY_50_PERCENT.getIndex());

            CellStyle totalNumStyle = workbook.createCellStyle();
            totalNumStyle.setFont(totalFont);
            totalNumStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalNumStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            totalNumStyle.setDataFormat(dataFormat.getFormat("0.0"));
            totalNumStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            totalNumStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(totalNumStyle, BorderStyle.MEDIUM, IndexedColors.GREY_50_PERCENT.getIndex());

            CellStyle totalCurrencyStyle = workbook.createCellStyle();
            totalCurrencyStyle.setFont(totalFont);
            totalCurrencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalCurrencyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            totalCurrencyStyle.setDataFormat(dataFormat.getFormat("¥#,##0.00"));
            totalCurrencyStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            totalCurrencyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(totalCurrencyStyle, BorderStyle.MEDIUM, IndexedColors.GREY_50_PERCENT.getIndex());

            // Build Column List
            List<ColumnDef> columns = resolveColumns(request);
            int colCount = columns.size();

            int rowIndex = 0;

            // Row 0: Title
            String title = (request != null && request.title() != null && !request.title().isBlank())
                    ? request.title()
                    : payload.path("title").asText("功能点 (FPA) 工作量评估与报价表");
            Row titleRow = sheet.createRow(rowIndex++);
            titleRow.setHeightInPoints(32);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(clip(title));
            titleCell.setCellStyle(titleStyle);
            if (colCount > 1) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, colCount - 1));
            }

            // Row 1: Mandatory Scope Disclaimer
            String disclaimer = payload.path("scopeNote").asText(
                    "【特别说明】：本次报价仅包含开发和测试（研发自测）的工作量，不含产品设计、运维、项目管理的工作量。"
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

            // Row 2: KPI summary
            JsonNode items = payload.path("items");
            int pageCount = 0;
            int apiCount = 0;
            if (items.isArray()) {
                for (JsonNode it : items) {
                    String t = it.path("type").asText("").toUpperCase();
                    if ("PAGE".equals(t)) pageCount++;
                    else if ("API".equals(t) || "THIRD_PARTY_API".equals(t)) apiCount++;
                }
            }

            Double dailyRate = request != null ? request.dailyRate() : null;
            StringBuilder kpiBuilder = new StringBuilder();
            kpiBuilder.append(String.format("评估概览：总预估人天: %s  |  开发: %s  |  自测: %s  |  页面数: %d  |  接口数: %d",
                    suppliedDays(payload, "totalDays"), suppliedDays(payload, "devDays"),
                    suppliedDays(payload, "testDays"), pageCount, apiCount));
            if (dailyRate != null && dailyRate > 0) {
                kpiBuilder.append(String.format("  |  人天基准单价: ¥%.2f/天", dailyRate));
                if (payload.path("totalDays").isNumber()) {
                    kpiBuilder.append(String.format("  |  预估开发总金额: ¥%,.2f", payload.path("totalDays").asDouble() * dailyRate));
                } else {
                    kpiBuilder.append("  |  预估开发总金额: 未提供");
                }
            }

            Row kpiRow = sheet.createRow(rowIndex++);
            kpiRow.setHeightInPoints(22);
            Cell kpiCell = kpiRow.createCell(0);
            kpiCell.setCellValue(clip(kpiBuilder.toString()));
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

            // Row 4: Table Header
            int headerRowIndex = rowIndex++;
            Row headerRow = sheet.createRow(headerRowIndex);
            headerRow.setHeightInPoints(26);
            for (int c = 0; c < colCount; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(clip(columns.get(c).title()));
                cell.setCellStyle(headerStyle);
            }

            // Data Rows
            int dataStartRow = rowIndex;
            int itemSeq = 1;
            if (items.isArray()) {
                for (JsonNode item : items) {
                    Row dataRow = sheet.createRow(rowIndex++);
                    dataRow.setHeightInPoints(20);

                    for (int c = 0; c < colCount; c++) {
                        ColumnDef col = columns.get(c);
                        Cell cell = dataRow.createCell(c);
                        renderCell(cell, col.key(), itemSeq, item, dailyRate,
                                textStyles.get(col.align()), numberStyle, currencyStyle,
                                request != null ? request.extraFieldValues() : null);
                    }
                    itemSeq++;
                }
            }
            int dataEndRow = rowIndex - 1;

            // Total Summary Row (at the bottom)
            if (dataEndRow >= dataStartRow) {
                Row totalRow = sheet.createRow(rowIndex++);
                totalRow.setHeightInPoints(24);

                for (int c = 0; c < colCount; c++) {
                    ColumnDef col = columns.get(c);
                    Cell cell = totalRow.createCell(c);

                    String colLetter = CellReference.convertNumToColString(c);
                    String formula = String.format("SUM(%s%d:%s%d)", colLetter, dataStartRow + 1, colLetter, dataEndRow + 1);

                    switch (col.key()) {
                        case "index":
                        case "module":
                            cell.setCellValue("【全项目总计】");
                            cell.setCellStyle(totalLabelStyle);
                            break;
                        case "devDays":
                        case "testDays":
                        case "totalDays":
                            if (hasAllItemValues(items, col.key())) cell.setCellFormula(formula);
                            else cell.setCellValue("未提供");
                            cell.setCellStyle(totalNumStyle);
                            break;
                        case "totalPrice":
                            if (dailyRate != null && dailyRate > 0 && hasAllItemValues(items, "totalDays")) cell.setCellFormula(formula);
                            else cell.setCellValue("未提供");
                            cell.setCellStyle(totalCurrencyStyle);
                            break;
                        default:
                            cell.setCellValue("");
                            cell.setCellStyle(totalLabelStyle);
                            break;
                    }
                }
            }

            // Set column widths
            for (int c = 0; c < colCount; c++) {
                sheet.setColumnWidth(c, columns.get(c).preferredWidth() * 256);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to export estimate Excel workbook", e);
        }
    }

    private List<ColumnDef> resolveColumns(ApiModels.ExportEstimateRequest request) {
        Map<String, ColumnDef> allDefaultColumns = new LinkedHashMap<>();
        allDefaultColumns.put("index", new ColumnDef("index", "序号", 8, HorizontalAlignment.CENTER));
        allDefaultColumns.put("module", new ColumnDef("module", "一级模块", 15, HorizontalAlignment.LEFT));
        allDefaultColumns.put("subModule", new ColumnDef("subModule", "二级模块", 15, HorizontalAlignment.LEFT));
        allDefaultColumns.put("type", new ColumnDef("type", "类型", 12, HorizontalAlignment.CENTER));
        allDefaultColumns.put("name", new ColumnDef("name", "页面/接口名称", 24, HorizontalAlignment.LEFT));
        allDefaultColumns.put("detail", new ColumnDef("detail", "功能/接口详情", 32, HorizontalAlignment.LEFT));
        allDefaultColumns.put("complexity", new ColumnDef("complexity", "复杂度", 10, HorizontalAlignment.CENTER));
        allDefaultColumns.put("devDays", new ColumnDef("devDays", "开发人天", 12, HorizontalAlignment.RIGHT));
        allDefaultColumns.put("testDays", new ColumnDef("testDays", "自测人天", 12, HorizontalAlignment.RIGHT));
        allDefaultColumns.put("totalDays", new ColumnDef("totalDays", "合计人天", 12, HorizontalAlignment.RIGHT));

        if (request != null && request.dailyRate() != null && request.dailyRate() > 0) {
            allDefaultColumns.put("unitPrice", new ColumnDef("unitPrice", "人天单价(元)", 14, HorizontalAlignment.RIGHT));
            allDefaultColumns.put("totalPrice", new ColumnDef("totalPrice", "预估金额(元)", 16, HorizontalAlignment.RIGHT));
        }

        allDefaultColumns.put("description", new ColumnDef("description", "测算依据与说明", 35, HorizontalAlignment.LEFT));

        if (request != null && request.columns() != null && !request.columns().isEmpty()) {
            List<ColumnDef> resolved = new ArrayList<>();
            for (ApiModels.ExportEstimateColumn col : request.columns()) {
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
            Double dailyRate,
            CellStyle textStyle,
            CellStyle numberStyle,
            CellStyle currencyStyle,
            JsonNode extraFieldValues
    ) {
        switch (key) {
            case "index" -> {
                cell.setCellValue(seq);
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
            case "type" -> {
                String raw = item.path("type").asText("").toUpperCase();
                String label = switch (raw) {
                    case "PAGE" -> "页面";
                    case "API" -> "内部接口";
                    case "THIRD_PARTY_API" -> "第三方接口";
                    default -> raw;
                };
                cell.setCellValue(clip(label));
                cell.setCellStyle(textStyle);
            }
            case "name" -> {
                cell.setCellValue(clip(item.path("name").asText("")));
                cell.setCellStyle(textStyle);
            }
            case "detail" -> {
                cell.setCellValue(clip(item.path("detail").asText("")));
                cell.setCellStyle(textStyle);
            }
            case "complexity" -> {
                String raw = item.path("complexity").asText("").toUpperCase();
                String label = switch (raw) {
                    case "SIMPLE" -> "简单";
                    case "MEDIUM" -> "中等";
                    case "COMPLEX" -> "复杂";
                    default -> raw;
                };
                cell.setCellValue(clip(label));
                cell.setCellStyle(textStyle);
            }
            case "devDays" -> {
                cell.setCellValue(item.path("devDays").asDouble(0.0));
                cell.setCellStyle(numberStyle);
            }
            case "testDays" -> {
                cell.setCellValue(item.path("testDays").asDouble(0.0));
                cell.setCellStyle(numberStyle);
            }
            case "totalDays" -> {
                if (item.path("totalDays").isNumber()) cell.setCellValue(item.path("totalDays").asDouble());
                else cell.setCellValue("未提供");
                cell.setCellStyle(numberStyle);
            }
            case "unitPrice" -> {
                if (dailyRate != null && dailyRate > 0) {
                    cell.setCellValue(dailyRate);
                } else {
                    cell.setCellValue("未提供");
                }
                cell.setCellStyle(currencyStyle);
            }
            case "totalPrice" -> {
                if (item.path("totalDays").isNumber() && dailyRate != null && dailyRate > 0) {
                    cell.setCellValue(item.path("totalDays").asDouble() * dailyRate);
                } else {
                    cell.setCellValue("未提供");
                }
                cell.setCellStyle(currencyStyle);
            }
            case "description" -> {
                cell.setCellValue(clip(item.path("description").asText("")));
                cell.setCellStyle(textStyle);
            }
            default -> {
                // Check custom extra fields in item or extraFieldValues
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

    private static String suppliedDays(JsonNode payload, String key) {
        return payload.path(key).isNumber() ? payload.path(key).asText() + " 天" : "未提供";
    }

    private static boolean hasAllItemValues(JsonNode items, String key) {
        for (JsonNode item : items) {
            if (!item.path(key).isNumber()) return false;
        }
        return true;
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
