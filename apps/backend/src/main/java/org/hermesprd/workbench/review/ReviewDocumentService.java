package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.auth.CurrentUser;
import org.hermesprd.workbench.auth.UserEntity;
import org.hermesprd.workbench.auth.UserMapper;
import org.hermesprd.workbench.review.ReviewApiModels.*;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.Ids;
import org.hermesprd.workbench.task.RunEntity;
import org.hermesprd.workbench.task.RunMapper;
import org.hermesprd.workbench.task.SourceEntity;
import org.hermesprd.workbench.task.SourceMapper;
import org.hermesprd.workbench.task.SourceRoles;
import org.hermesprd.workbench.task.TaskEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class ReviewDocumentService {
    private static final int MAX_DOCUMENT_CODE_POINTS = 500_000;
    private static final int MAX_DOCUMENT_BYTES = 2 * 1024 * 1024;
    private static final int MAX_TITLE_CODE_POINTS = 200;

    private final ReviewAccessService accessService;
    private final ReviewDocumentMapper documentMapper;
    private final ReviewRevisionMapper revisionMapper;
    private final ReviewRevisionIssueMapper revisionIssueMapper;
    private final ReviewRoundMapper roundMapper;
    private final ReviewIssueMapper issueMapper;
    private final ReviewEventMapper eventMapper;
    private final SourceMapper sourceMapper;
    private final RunMapper runMapper;
    private final UserMapper userMapper;
    private final ReviewNotificationService notificationService;
    private final ObjectMapper objectMapper;

    public ReviewDocumentService(
            ReviewAccessService accessService,
            ReviewDocumentMapper documentMapper,
            ReviewRevisionMapper revisionMapper,
            ReviewRevisionIssueMapper revisionIssueMapper,
            ReviewRoundMapper roundMapper,
            ReviewIssueMapper issueMapper,
            ReviewEventMapper eventMapper,
            SourceMapper sourceMapper,
            RunMapper runMapper,
            UserMapper userMapper,
            ReviewNotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.accessService = accessService;
        this.documentMapper = documentMapper;
        this.revisionMapper = revisionMapper;
        this.revisionIssueMapper = revisionIssueMapper;
        this.roundMapper = roundMapper;
        this.issueMapper = issueMapper;
        this.eventMapper = eventMapper;
        this.sourceMapper = sourceMapper;
        this.runMapper = runMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    public ReviewDocumentEntity requireDocument(String documentId) {
        ReviewDocumentEntity document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "评审文档不存在");
        }
        return document;
    }

    @Transactional(readOnly = true)
    public ReviewDocumentListResponse getDocuments(String taskId, int page, int pageSize) {
        accessService.requireReadAccess(taskId);
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));

        List<ReviewDocumentEntity> allDocs = documentMapper.selectList(
                new LambdaQueryWrapper<ReviewDocumentEntity>()
                        .eq(ReviewDocumentEntity::getTaskId, taskId)
                        .orderByDesc(ReviewDocumentEntity::getUpdatedAt)
        );

        List<ReviewDocumentSummary> summaries = new ArrayList<>();
        for (ReviewDocumentEntity doc : allDocs) {
            summaries.add(toDocumentSummary(doc));
        }

        int total = summaries.size();
        int from = Math.min((safePage - 1) * safePageSize, total);
        int to = Math.min(from + safePageSize, total);

        return new ReviewDocumentListResponse(summaries.subList(from, to), total, safePage, safePageSize, Instant.now());
    }

    @Transactional
    public ReviewDocumentDetail getDocumentDetail(String documentId) {
        ReviewDocumentEntity doc = requireDocument(documentId);
        ReviewAccessService.Role role = accessService.requireReadAccess(doc.getTaskId());
        UserEntity currentUser = CurrentUser.require();
        notificationService.markTaskLevelNotificationsRead(currentUser.getId(), doc.getTaskId());

        ReviewDocumentSummary summary = toDocumentSummary(doc);
        ReviewRevisionDetail headRev = null;
        if (doc.getHeadRevisionId() != null) {
            ReviewRevisionEntity rev = revisionMapper.selectById(doc.getHeadRevisionId());
            if (rev != null) {
                headRev = toRevisionDetail(rev);
            }
        }

        ReviewRoundDetail openRound = null;
        ReviewRoundEntity roundEntity = roundMapper.selectOne(
                new LambdaQueryWrapper<ReviewRoundEntity>()
                        .eq(ReviewRoundEntity::getDocumentId, doc.getId())
                        .eq(ReviewRoundEntity::getStatus, "OPEN")
        );
        if (roundEntity != null) {
            openRound = toRoundDetail(roundEntity);
        }

        return new ReviewDocumentDetail(summary, headRev, openRound, role.name());
    }

    @Transactional
    public ReviewDocumentDetail createDocument(String taskId, CreateReviewDocumentRequest request) {
        accessService.requireOwner(taskId);
        UserEntity currentUser = CurrentUser.require();

        validateTitle(request.title());
        validateContent(request.content());

        Instant now = Instant.now();
        String docId = Ids.next("rev_doc_");
        ReviewDocumentEntity doc = new ReviewDocumentEntity();
        doc.setId(docId);
        doc.setTaskId(taskId);
        doc.setTitle(request.title().trim());
        doc.setRevision(1);
        doc.setNextIssueNo(1);
        doc.setCreatedBy(currentUser.getId());
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        documentMapper.insert(doc);

        String revId = Ids.next("rev_ver_");
        ReviewRevisionEntity rev = new ReviewRevisionEntity();
        rev.setId(revId);
        rev.setTaskId(taskId);
        rev.setDocumentId(docId);
        rev.setVersionNo(1);
        rev.setContent(request.content());
        rev.setContentHash(sha256Hex(request.content()));
        rev.setSummary("创建文档初稿");
        rev.setChangeType("INITIAL");
        rev.setCreatedBy(currentUser.getId());
        rev.setAuthorNameSnapshot(currentUser.getDisplayName());
        rev.setCreatedAt(now);
        rev.setOriginKind("MANUAL");
        revisionMapper.insert(rev);

        doc.setHeadRevisionId(revId);
        documentMapper.updateById(doc);

        notificationService.recordEventAndNotify(
                taskId,
                docId,
                null,
                null,
                "REVISION_CREATED",
                revId,
                currentUser.getId(),
                currentUser.getDisplayName(),
                null,
                null,
                toJson(Map.of("versionNo", 1, "summary", rev.getSummary())),
                notificationService.getAllSpaceMembers(taskId)
        );

        return getDocumentDetail(docId);
    }

    @Transactional
    public ReviewDocumentDetail importDraft(String taskId, ImportDraftReviewDocumentRequest request) {
        accessService.requireOwner(taskId);
        UserEntity currentUser = CurrentUser.require();

        SourceEntity source = sourceMapper.selectById(request.sourceId());
        if (source == null || !taskId.equals(source.getTaskId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "来源草稿材料不存在");
        }

        String draftRunId = SourceRoles.draftRunId(source.getOriginalName());
        if (draftRunId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "只能从成功 GENERATE 运行的当前稿导入评审副本");
        }
        RunEntity generateRun = runMapper.selectById(draftRunId);
        if (generateRun == null
                || !taskId.equals(generateRun.getTaskId())
                || !"GENERATE".equals(generateRun.getMode())
                || !"SUCCEEDED".equals(generateRun.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "来源必须是本任务已成功的 GENERATE 当前稿");
        }

        if (source.getUpdatedAt() != null && !source.getUpdatedAt().equals(request.expectedUpdatedAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED", "AI 当前稿已发生变化，请刷新确认");
        }

        validateTitle(request.title());
        String content = source.getContent();
        validateContent(content);

        Instant now = Instant.now();
        String docId = Ids.next("rev_doc_");
        ReviewDocumentEntity doc = new ReviewDocumentEntity();
        doc.setId(docId);
        doc.setTaskId(taskId);
        doc.setTitle(request.title().trim());
        doc.setRevision(1);
        doc.setNextIssueNo(1);
        doc.setCreatedBy(currentUser.getId());
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        documentMapper.insert(doc);

        String revId = Ids.next("rev_ver_");
        ReviewRevisionEntity rev = new ReviewRevisionEntity();
        rev.setId(revId);
        rev.setTaskId(taskId);
        rev.setDocumentId(docId);
        rev.setVersionNo(1);
        rev.setContent(content);
        rev.setContentHash(sha256Hex(content));
        rev.setSummary("从 AI 当前稿导入独立评审副本");
        rev.setChangeType("INITIAL");
        rev.setCreatedBy(currentUser.getId());
        rev.setAuthorNameSnapshot(currentUser.getDisplayName());
        rev.setCreatedAt(now);
        rev.setOriginKind("IMPORT_DRAFT");
        rev.setOriginRef(toJson(Map.of(
                "sourceId", source.getId(),
                "runId", generateRun.getId(),
                "sourceName", source.getOriginalName(),
                "importedAt", now.toString()
        )));
        revisionMapper.insert(rev);

        doc.setHeadRevisionId(revId);
        documentMapper.updateById(doc);

        notificationService.recordEventAndNotify(
                taskId,
                docId,
                null,
                null,
                "REVISION_CREATED",
                revId,
                currentUser.getId(),
                currentUser.getDisplayName(),
                null,
                null,
                toJson(Map.of("versionNo", 1, "summary", rev.getSummary())),
                notificationService.getAllSpaceMembers(taskId)
        );

        return getDocumentDetail(docId);
    }

    @Transactional
    public ReviewDocumentDetail updateTitle(String documentId, UpdateReviewDocumentTitleRequest request) {
        ReviewDocumentEntity doc = requireDocument(documentId);
        accessService.requireEditAccess(doc.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        if (doc.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_ARCHIVED", "文档已归档，不可修改标题");
        }

        if (!doc.getRevision().equals(request.expectedRevision())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "文档已被他人修改，请刷新后重试");
        }

        validateTitle(request.title());
        String fromTitle = doc.getTitle();
        String toTitle = request.title().trim();
        Instant now = Instant.now();

        doc.setTitle(toTitle);
        doc.setRevision(doc.getRevision() + 1);
        doc.setUpdatedAt(now);
        documentMapper.updateById(doc);

        Map<String, Object> meta = Map.of(
                "fromTitle", fromTitle,
                "toTitle", toTitle,
                "reason", request.reason().trim()
        );

        notificationService.recordEventAndNotify(
                doc.getTaskId(),
                doc.getId(),
                null,
                null,
                "TITLE_CHANGED",
                doc.getId(),
                currentUser.getId(),
                currentUser.getDisplayName(),
                fromTitle,
                toTitle,
                toJson(meta),
                null
        );

        return getDocumentDetail(documentId);
    }

    @Transactional
    public ReviewDocumentDetail archiveDocument(String documentId, ArchiveReviewDocumentRequest request) {
        ReviewDocumentEntity doc = requireDocument(documentId);
        accessService.requireOwner(doc.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        if (doc.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT", "文档已经是归档状态");
        }

        if (!doc.getRevision().equals(request.expectedRevision())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "文档已被他人修改，请刷新后重试");
        }

        Long openRounds = roundMapper.selectCount(new LambdaQueryWrapper<ReviewRoundEntity>()
                .eq(ReviewRoundEntity::getDocumentId, doc.getId())
                .eq(ReviewRoundEntity::getStatus, "OPEN"));
        if (openRounds > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT", "文档存在未结束的开放轮次，请先结束或取消轮次");
        }

        List<ReviewIssueEntity> leftoverIssues = issueMapper.selectList(
                new LambdaQueryWrapper<ReviewIssueEntity>()
                        .eq(ReviewIssueEntity::getDocumentId, doc.getId())
                        .in(ReviewIssueEntity::getStatus, List.of("OPEN", "AWAITING_CONFIRMATION"))
                        .orderByAsc(ReviewIssueEntity::getDisplayNo)
        );
        if (!leftoverIssues.isEmpty()) {
            String leftover = leftoverIssues.stream()
                    .map(issue -> "#" + issue.getDisplayNo() + " " + issue.getTitle())
                    .reduce((a, b) -> a + "；" + b)
                    .orElse("");
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REVIEW_STATE_CONFLICT",
                    "文档仍有未闭环问题，请先处理后再归档：" + leftover
            );
        }

        Instant now = Instant.now();
        doc.setArchivedAt(now);
        doc.setRevision(doc.getRevision() + 1);
        doc.setUpdatedAt(now);
        documentMapper.updateById(doc);

        notificationService.recordEventAndNotify(
                doc.getTaskId(),
                doc.getId(),
                null,
                null,
                "DOCUMENT_ARCHIVED",
                doc.getId(),
                currentUser.getId(),
                currentUser.getDisplayName(),
                null,
                null,
                toJson(Map.of("reason", request.reason().trim())),
                notificationService.getAllSpaceMembers(doc.getTaskId())
        );

        return getDocumentDetail(documentId);
    }

    @Transactional
    public ReviewDocumentDetail unarchiveDocument(String documentId, UnarchiveReviewDocumentRequest request) {
        ReviewDocumentEntity doc = requireDocument(documentId);
        accessService.requireOwner(doc.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        if (doc.getArchivedAt() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT", "文档不是归档状态");
        }

        if (!doc.getRevision().equals(request.expectedRevision())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "文档已被他人修改，请刷新后重试");
        }

        Instant now = Instant.now();
        doc.setArchivedAt(null);
        doc.setRevision(doc.getRevision() + 1);
        doc.setUpdatedAt(now);
        documentMapper.updateById(doc);

        notificationService.recordEventAndNotify(
                doc.getTaskId(),
                doc.getId(),
                null,
                null,
                "DOCUMENT_UNARCHIVED",
                doc.getId(),
                currentUser.getId(),
                currentUser.getDisplayName(),
                null,
                null,
                toJson(Map.of("reason", request.reason().trim())),
                notificationService.getAllSpaceMembers(doc.getTaskId())
        );

        return getDocumentDetail(documentId);
    }

    @Transactional
    public ReviewRevisionDetail createRevision(String documentId, CreateReviewRevisionRequest request) {
        ReviewDocumentEntity doc = requireDocument(documentId);
        accessService.requireEditAccess(doc.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        if (doc.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_ARCHIVED", "文档已归档，不可保存新版本");
        }

        if (!doc.getRevision().equals(request.expectedRevision())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "文档并发版本已变化，请刷新并核对最新内容");
        }

        if (!doc.getHeadRevisionId().equals(request.expectedHeadRevisionId())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "基准版本已被更新，请核对双方修改");
        }

        validateContent(request.content());

        ReviewRevisionEntity headRev = revisionMapper.selectById(doc.getHeadRevisionId());
        if (headRev != null && headRev.getContent().equals(request.content())) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_CONTENT_CHANGE", "文档正文未发生任何变更，无需保存新版本");
        }

        Instant now = Instant.now();
        int newVersionNo = headRev != null ? headRev.getVersionNo() + 1 : 1;
        String revId = Ids.next("rev_ver_");

        ReviewRevisionEntity newRev = new ReviewRevisionEntity();
        newRev.setId(revId);
        newRev.setTaskId(doc.getTaskId());
        newRev.setDocumentId(doc.getId());
        newRev.setVersionNo(newVersionNo);
        newRev.setParentRevisionId(doc.getHeadRevisionId());
        newRev.setContent(request.content());
        newRev.setContentHash(sha256Hex(request.content()));
        newRev.setSummary(request.summary().trim());
        newRev.setCreatedBy(currentUser.getId());
        newRev.setAuthorNameSnapshot(currentUser.getDisplayName());
        newRev.setCreatedAt(now);

        if (request.restoredFromRevisionId() != null && !request.restoredFromRevisionId().isBlank()) {
            ReviewRevisionEntity restoreSource = revisionMapper.selectById(request.restoredFromRevisionId());
            if (restoreSource == null || !restoreSource.getDocumentId().equals(doc.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "恢复来源版本不属于当前文档");
            }
            newRev.setChangeType("RESTORE");
            newRev.setOriginKind("RESTORE");
            newRev.setOriginRef(toJson(Map.of(
                    "restoredFromRevisionId", restoreSource.getId(),
                    "restoredFromVersionNo", restoreSource.getVersionNo()
            )));
        } else {
            newRev.setChangeType("EDIT");
            newRev.setOriginKind("MANUAL");
        }

        revisionMapper.insert(newRev);

        // Update document head
        doc.setHeadRevisionId(revId);
        doc.setRevision(doc.getRevision() + 1);
        doc.setUpdatedAt(now);
        documentMapper.updateById(doc);

        if (request.relatedIssues() != null) {
            for (String issueId : request.relatedIssues()) {
                ReviewIssueEntity issue = issueMapper.selectById(issueId);
                if (issue == null || !issue.getDocumentId().equals(doc.getId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "关联问题不存在或不属于当前文档: " + issueId);
                }
                ReviewRevisionIssueEntity link = new ReviewRevisionIssueEntity();
                link.setTaskId(doc.getTaskId());
                link.setDocumentId(doc.getId());
                link.setRevisionId(revId);
                link.setIssueId(issueId);
                link.setChangeNote(request.summary().trim());
                revisionIssueMapper.insert(link);
            }
        }

        notificationService.recordEventAndNotify(
                doc.getTaskId(),
                doc.getId(),
                null,
                null,
                "REVISION_CREATED",
                revId,
                currentUser.getId(),
                currentUser.getDisplayName(),
                null,
                null,
                toJson(Map.of("versionNo", newVersionNo, "summary", newRev.getSummary())),
                notificationService.getAllSpaceMembers(doc.getTaskId())
        );

        return toRevisionDetail(newRev);
    }

    @Transactional(readOnly = true)
    public ReviewHistoryResponse getRevisions(String documentId, int page, int pageSize, String authorId, Instant from, Instant to) {
        ReviewDocumentEntity doc = requireDocument(documentId);
        accessService.requireReadAccess(doc.getTaskId());

        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));

        // Get revisions
        LambdaQueryWrapper<ReviewRevisionEntity> revQuery = new LambdaQueryWrapper<ReviewRevisionEntity>()
                .eq(ReviewRevisionEntity::getDocumentId, documentId);
        if (authorId != null && !authorId.isBlank()) {
            revQuery.eq(ReviewRevisionEntity::getCreatedBy, authorId);
        }
        if (from != null) {
            revQuery.ge(ReviewRevisionEntity::getCreatedAt, from);
        }
        if (to != null) {
            revQuery.le(ReviewRevisionEntity::getCreatedAt, to);
        }
        List<ReviewRevisionEntity> revisions = revisionMapper.selectList(revQuery);

        // Get TITLE_CHANGED events
        LambdaQueryWrapper<ReviewEventEntity> eventQuery = new LambdaQueryWrapper<ReviewEventEntity>()
                .eq(ReviewEventEntity::getDocumentId, documentId)
                .eq(ReviewEventEntity::getEventType, "TITLE_CHANGED");
        if (authorId != null && !authorId.isBlank()) {
            eventQuery.eq(ReviewEventEntity::getActorId, authorId);
        }
        if (from != null) {
            eventQuery.ge(ReviewEventEntity::getCreatedAt, from);
        }
        if (to != null) {
            eventQuery.le(ReviewEventEntity::getCreatedAt, to);
        }
        List<ReviewEventEntity> titleEvents = eventMapper.selectList(eventQuery);

        List<ReviewHistoryItem> historyItems = new ArrayList<>();
        for (ReviewRevisionEntity rev : revisions) {
            Long relatedCount = revisionIssueMapper.selectCount(
                    new LambdaQueryWrapper<ReviewRevisionIssueEntity>()
                            .eq(ReviewRevisionIssueEntity::getRevisionId, rev.getId())
            );
            ReviewRevisionSummary sum = new ReviewRevisionSummary(
                    rev.getId(),
                    rev.getVersionNo(),
                    rev.getSummary(),
                    rev.getChangeType(),
                    rev.getCreatedBy(),
                    rev.getAuthorNameSnapshot(),
                    rev.getCreatedAt(),
                    relatedCount.intValue()
            );
            historyItems.add(new ReviewHistoryItem("REVISION", sum, null, rev.getCreatedAt()));
        }

        for (ReviewEventEntity ev : titleEvents) {
            String fromTitle = ev.getFromState();
            String toTitle = ev.getToState();
            String reason = "";
            try {
                if (ev.getMetadata() != null) {
                    Map<?, ?> metaMap = objectMapper.readValue(ev.getMetadata(), Map.class);
                    Object r = metaMap.get("reason");
                    if (r != null) {
                        reason = r.toString();
                    }
                }
            } catch (Exception ignored) {}

            ReviewTitleChangeItem item = new ReviewTitleChangeItem(
                    fromTitle,
                    toTitle,
                    reason,
                    ev.getActorId(),
                    ev.getActorNameSnapshot(),
                    ev.getCreatedAt()
            );
            historyItems.add(new ReviewHistoryItem("TITLE_CHANGE", null, item, ev.getCreatedAt()));
        }

        // Sort merged list by createdAt desc
        historyItems.sort(Comparator.comparing(ReviewHistoryItem::createdAt).reversed());

        int total = historyItems.size();
        int fromIdx = Math.min((safePage - 1) * safePageSize, total);
        int toIdx = Math.min(fromIdx + safePageSize, total);

        return new ReviewHistoryResponse(historyItems.subList(fromIdx, toIdx), total, safePage, safePageSize, Instant.now());
    }

    @Transactional(readOnly = true)
    public ReviewRevisionDetail getRevision(String documentId, String revisionId) {
        ReviewDocumentEntity doc = requireDocument(documentId);
        accessService.requireReadAccess(doc.getTaskId());

        ReviewRevisionEntity rev = revisionMapper.selectById(revisionId);
        if (rev == null || !rev.getDocumentId().equals(documentId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "指定版本不存在");
        }
        return toRevisionDetail(rev);
    }

    @Transactional(readOnly = true)
    public ReviewDiffResponse getDiff(String documentId, String fromRevisionId, String toRevisionId) {
        ReviewDocumentEntity doc = requireDocument(documentId);
        accessService.requireReadAccess(doc.getTaskId());

        ReviewRevisionEntity fromRev = revisionMapper.selectById(fromRevisionId);
        ReviewRevisionEntity toRev = revisionMapper.selectById(toRevisionId);
        if (fromRev == null || !fromRev.getDocumentId().equals(documentId) ||
                toRev == null || !toRev.getDocumentId().equals(documentId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "参与对比的版本不存在或不属于当前文档");
        }

        List<ReviewDiffLine> diffLines = computeLineDiff(fromRev.getContent(), toRev.getContent());
        return new ReviewDiffResponse(
                fromRev.getId(),
                fromRev.getVersionNo(),
                toRev.getId(),
                toRev.getVersionNo(),
                diffLines
        );
    }

    private List<ReviewDiffLine> computeLineDiff(String oldText, String newText) {
        String[] oldLines = oldText.split("\r?\n", -1);
        String[] newLines = newText.split("\r?\n", -1);

        // Simple LCS-based diff for lines
        int n = oldLines.length;
        int m = newLines.length;
        int[][] dp = new int[n + 1][m + 1];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                if (oldLines[i].equals(newLines[j])) {
                    dp[i + 1][j + 1] = dp[i][j] + 1;
                } else {
                    dp[i + 1][j + 1] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        List<ReviewDiffLine> result = new ArrayList<>();
        int i = n, j = m;
        List<ReviewDiffLine> reversed = new ArrayList<>();

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1].equals(newLines[j - 1])) {
                reversed.add(new ReviewDiffLine("EQUAL", i, j, oldLines[i - 1]));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                reversed.add(new ReviewDiffLine("ADD", null, j, newLines[j - 1]));
                j--;
            } else if (i > 0 && (j == 0 || dp[i][j - 1] < dp[i - 1][j])) {
                reversed.add(new ReviewDiffLine("DELETE", i, null, oldLines[i - 1]));
                i--;
            }
        }

        for (int k = reversed.size() - 1; k >= 0; k--) {
            result.add(reversed.get(k));
        }
        return result;
    }

    private ReviewDocumentSummary toDocumentSummary(ReviewDocumentEntity doc) {
        Integer headVersionNo = null;
        if (doc.getHeadRevisionId() != null) {
            ReviewRevisionEntity head = revisionMapper.selectById(doc.getHeadRevisionId());
            if (head != null) {
                headVersionNo = head.getVersionNo();
            }
        }

        String openRoundId = null;
        ReviewRoundEntity round = roundMapper.selectOne(
                new LambdaQueryWrapper<ReviewRoundEntity>()
                        .eq(ReviewRoundEntity::getDocumentId, doc.getId())
                        .eq(ReviewRoundEntity::getStatus, "OPEN")
        );
        if (round != null) {
            openRoundId = round.getId();
        }

        Long unresolved = issueMapper.selectCount(
                new LambdaQueryWrapper<ReviewIssueEntity>()
                        .eq(ReviewIssueEntity::getDocumentId, doc.getId())
                        .in(ReviewIssueEntity::getStatus, List.of("OPEN", "AWAITING_CONFIRMATION"))
        );

        return new ReviewDocumentSummary(
                doc.getId(),
                doc.getTaskId(),
                doc.getTitle(),
                doc.getHeadRevisionId(),
                headVersionNo,
                doc.getRevision(),
                doc.getNextIssueNo(),
                doc.getArchivedAt(),
                doc.getCreatedBy(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                openRoundId,
                unresolved.intValue()
        );
    }

    private ReviewRevisionDetail toRevisionDetail(ReviewRevisionEntity rev) {
        List<ReviewRevisionIssueEntity> links = revisionIssueMapper.selectList(
                new LambdaQueryWrapper<ReviewRevisionIssueEntity>()
                        .eq(ReviewRevisionIssueEntity::getRevisionId, rev.getId())
        );
        List<String> relatedIssues = new ArrayList<>();
        for (ReviewRevisionIssueEntity l : links) {
            relatedIssues.add(l.getIssueId());
        }

        Object originRefObj = null;
        if (rev.getOriginRef() != null && !rev.getOriginRef().isBlank()) {
            try {
                originRefObj = objectMapper.readValue(rev.getOriginRef(), Object.class);
            } catch (Exception ignored) {}
        }

        return new ReviewRevisionDetail(
                rev.getId(),
                rev.getTaskId(),
                rev.getDocumentId(),
                rev.getVersionNo(),
                rev.getParentRevisionId(),
                rev.getContent(),
                rev.getContentHash(),
                rev.getSummary(),
                rev.getChangeType(),
                rev.getCreatedBy(),
                rev.getAuthorNameSnapshot(),
                rev.getCreatedAt(),
                rev.getOriginKind(),
                originRefObj,
                relatedIssues
        );
    }

    private ReviewRoundDetail toRoundDetail(ReviewRoundEntity r) {
        ReviewRevisionEntity base = revisionMapper.selectById(r.getBaselineRevisionId());
        int baseVer = base != null ? base.getVersionNo() : 1;

        Long totalIssues = issueMapper.selectCount(
                new LambdaQueryWrapper<ReviewIssueEntity>().eq(ReviewIssueEntity::getRoundId, r.getId())
        );
        Long unresolved = issueMapper.selectCount(
                new LambdaQueryWrapper<ReviewIssueEntity>()
                        .eq(ReviewIssueEntity::getRoundId, r.getId())
                        .in(ReviewIssueEntity::getStatus, List.of("OPEN", "AWAITING_CONFIRMATION"))
        );

        Object closeSnapshotObj = null;
        if (r.getCloseSnapshot() != null && !r.getCloseSnapshot().isBlank()) {
            try {
                closeSnapshotObj = objectMapper.readValue(r.getCloseSnapshot(), Object.class);
            } catch (Exception ignored) {}
        }

        return new ReviewRoundDetail(
                r.getId(),
                r.getTaskId(),
                r.getDocumentId(),
                r.getBaselineRevisionId(),
                baseVer,
                r.getStatus(),
                r.getRevision(),
                r.getInstruction(),
                r.getDueAt(),
                r.getConclusion(),
                r.getSummary(),
                closeSnapshotObj,
                r.getCreatedBy(),
                r.getClosedBy(),
                r.getCreatedAt(),
                r.getClosedAt(),
                totalIssues.intValue(),
                unresolved.intValue()
        );
    }

    private void validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "文档标题不能为空");
        }
        int codePoints = title.codePointCount(0, title.length());
        if (codePoints > MAX_TITLE_CODE_POINTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "文档标题不能超过 200 个字符");
        }
    }

    private void validateContent(String content) {
        if (content == null || content.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "正文内容不能为空");
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_DOCUMENT_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "正文内容超出 2 MiB 上限");
        }
        int codePoints = content.codePointCount(0, content.length());
        if (codePoints > MAX_DOCUMENT_CODE_POINTS) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "正文内容不能超过 500000 字符");
        }
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
