package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.auth.CurrentUser;
import org.hermesprd.workbench.auth.UserEntity;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.task.TaskEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ReviewExportService {
    private final ReviewAccessService accessService;
    private final ReviewDocumentMapper documentMapper;
    private final ReviewRevisionMapper revisionMapper;
    private final ReviewRevisionIssueMapper revisionIssueMapper;
    private final ReviewIssueMapper issueMapper;
    private final ReviewPostMapper postMapper;
    private final ReviewEventMapper eventMapper;
    private final ReviewRoundMapper roundMapper;
    private final ObjectMapper objectMapper;

    public ReviewExportService(
            ReviewAccessService accessService,
            ReviewDocumentMapper documentMapper,
            ReviewRevisionMapper revisionMapper,
            ReviewRevisionIssueMapper revisionIssueMapper,
            ReviewIssueMapper issueMapper,
            ReviewPostMapper postMapper,
            ReviewEventMapper eventMapper,
            ReviewRoundMapper roundMapper,
            ObjectMapper objectMapper
    ) {
        this.accessService = accessService;
        this.documentMapper = documentMapper;
        this.revisionMapper = revisionMapper;
        this.revisionIssueMapper = revisionIssueMapper;
        this.issueMapper = issueMapper;
        this.postMapper = postMapper;
        this.eventMapper = eventMapper;
        this.roundMapper = roundMapper;
        ObjectMapper mapper = objectMapper.copy();
        mapper.findAndRegisterModules();
        this.objectMapper = mapper;
    }

    public byte[] exportPackage(String taskId, String documentId, String revisionId, Instant from, Instant to) {
        accessService.requireReadAccess(taskId);
        TaskEntity task = accessService.requireTask(taskId);
        Instant asOf = Instant.now();

        // Collect documents
        List<ReviewDocumentEntity> docs;
        if (documentId != null && !documentId.isBlank()) {
            ReviewDocumentEntity doc = documentMapper.selectById(documentId);
            if (doc == null || !doc.getTaskId().equals(taskId)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "文档不存在");
            }
            docs = List.of(doc);
        } else {
            docs = documentMapper.selectList(
                    new LambdaQueryWrapper<ReviewDocumentEntity>().eq(ReviewDocumentEntity::getTaskId, taskId)
            );
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            List<String> exportedFiles = new ArrayList<>();

            // 1. Export Markdown files
            for (ReviewDocumentEntity doc : docs) {
                String targetRevId = (docs.size() == 1 && revisionId != null && !revisionId.isBlank())
                        ? revisionId : doc.getHeadRevisionId();

                if (targetRevId != null) {
                    ReviewRevisionEntity rev = revisionMapper.selectById(targetRevId);
                    if (rev != null) {
                        String entryName = "documents/doc_" + doc.getId() + "_v" + rev.getVersionNo() + ".md";
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.write(rev.getContent().getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();
                        exportedFiles.add(entryName);
                    }
                }
            }

            // 2. Export issues CSV
            LambdaQueryWrapper<ReviewIssueEntity> issueQuery = new LambdaQueryWrapper<ReviewIssueEntity>()
                    .eq(ReviewIssueEntity::getTaskId, taskId);
            if (documentId != null && !documentId.isBlank()) {
                issueQuery.eq(ReviewIssueEntity::getDocumentId, documentId);
            }
            if (from != null) {
                issueQuery.ge(ReviewIssueEntity::getCreatedAt, from);
            }
            if (to != null) {
                issueQuery.le(ReviewIssueEntity::getCreatedAt, to);
            }
            issueQuery.orderByAsc(ReviewIssueEntity::getDisplayNo);
            List<ReviewIssueEntity> issues = issueMapper.selectList(issueQuery);

            StringBuilder csv = new StringBuilder();
            csv.append("DisplayNo,Id,DocumentId,Title,Type,Source,Impact,Reporter,Assignee,Status,CreatedAt,UpdatedAt\n");
            for (ReviewIssueEntity iss : issues) {
                csv.append(escapeCsv(String.valueOf(iss.getDisplayNo()))).append(",")
                        .append(escapeCsv(iss.getId())).append(",")
                        .append(escapeCsv(iss.getDocumentId())).append(",")
                        .append(escapeCsv(iss.getTitle())).append(",")
                        .append(escapeCsv(iss.getType())).append(",")
                        .append(escapeCsv(iss.getSource())).append(",")
                        .append(escapeCsv(iss.getImpact())).append(",")
                        .append(escapeCsv(iss.getReporterNameSnapshot())).append(",")
                        .append(escapeCsv(iss.getAssigneeId())).append(",")
                        .append(escapeCsv(iss.getStatus())).append(",")
                        .append(escapeCsv(iss.getCreatedAt().toString())).append(",")
                        .append(escapeCsv(iss.getUpdatedAt().toString())).append("\n");
            }
            zos.putNextEntry(new ZipEntry("issues.csv"));
            zos.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            exportedFiles.add("issues.csv");

            // 3. Export history JSON
            List<ReviewPostEntity> posts = postMapper.selectList(
                    new LambdaQueryWrapper<ReviewPostEntity>().eq(ReviewPostEntity::getTaskId, taskId)
            );
            List<ReviewEventEntity> events = eventMapper.selectList(
                    new LambdaQueryWrapper<ReviewEventEntity>().eq(ReviewEventEntity::getTaskId, taskId)
            );
            Map<String, Object> history = Map.of(
                    "posts", posts,
                    "events", events
            );
            zos.putNextEntry(new ZipEntry("history.json"));
            zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(history));
            zos.closeEntry();
            exportedFiles.add("history.json");

            // 4. Export traceability JSON
            List<ReviewRevisionIssueEntity> links = revisionIssueMapper.selectList(
                    new LambdaQueryWrapper<ReviewRevisionIssueEntity>().eq(ReviewRevisionIssueEntity::getTaskId, taskId)
            );
            zos.putNextEntry(new ZipEntry("traceability.json"));
            zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(links));
            zos.closeEntry();
            exportedFiles.add("traceability.json");

            // 5. Export manifest JSON
            List<ReviewRoundEntity> rounds = roundMapper.selectList(
                    new LambdaQueryWrapper<ReviewRoundEntity>().eq(ReviewRoundEntity::getTaskId, taskId)
            );
            Map<String, Object> manifest = new HashMap<>();
            manifest.put("taskId", taskId);
            manifest.put("taskTitle", task.getTitle());
            manifest.put("exportedAt", asOf.toString());
            manifest.put("files", exportedFiles);
            manifest.put("totalIssues", issues.size());
            manifest.put("rounds", rounds);
            manifest.put("issues", issues);

            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            zos.closeEntry();

        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "生成导出包失败: " + e.getMessage());
        }

        // Re-verify permission with a fresh check before sending
        accessService.requireReadAccess(taskId);

        return baos.toByteArray();
    }

    private String escapeCsv(String value) {
        if (value == null) return "\"\"";
        // Formula injection defense: if cell starts with =, +, -, @, \t, \r, \n, prefix with single quote
        String sanitized = value;
        if (!sanitized.isEmpty()) {
            char first = sanitized.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r' || first == '\n') {
                sanitized = "'" + sanitized;
            }
        }
        return "\"" + sanitized.replace("\"", "\"\"") + "\"";
    }
}
