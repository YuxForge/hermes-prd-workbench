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
import org.hermesprd.workbench.task.TaskEntity;
import org.hermesprd.workbench.task.TaskMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewIssueService {
    private final ReviewAccessService accessService;
    private final ReviewDocumentMapper documentMapper;
    private final ReviewRevisionMapper revisionMapper;
    private final ReviewRevisionIssueMapper revisionIssueMapper;
    private final ReviewRoundMapper roundMapper;
    private final ReviewIssueMapper issueMapper;
    private final ReviewPostMapper postMapper;
    private final ReviewAnchorMapper anchorMapper;
    private final ReviewEventMapper eventMapper;
    private final ReviewIssueWatchMapper watchMapper;
    private final ReviewNotificationService notificationService;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public ReviewIssueService(
            ReviewAccessService accessService,
            ReviewDocumentMapper documentMapper,
            ReviewRevisionMapper revisionMapper,
            ReviewRevisionIssueMapper revisionIssueMapper,
            ReviewRoundMapper roundMapper,
            ReviewIssueMapper issueMapper,
            ReviewPostMapper postMapper,
            ReviewAnchorMapper anchorMapper,
            ReviewEventMapper eventMapper,
            ReviewIssueWatchMapper watchMapper,
            ReviewNotificationService notificationService,
            TaskMapper taskMapper,
            UserMapper userMapper,
            ObjectMapper objectMapper
    ) {
        this.accessService = accessService;
        this.documentMapper = documentMapper;
        this.revisionMapper = revisionMapper;
        this.revisionIssueMapper = revisionIssueMapper;
        this.roundMapper = roundMapper;
        this.issueMapper = issueMapper;
        this.postMapper = postMapper;
        this.anchorMapper = anchorMapper;
        this.eventMapper = eventMapper;
        this.watchMapper = watchMapper;
        this.notificationService = notificationService;
        this.taskMapper = taskMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    public ReviewIssueEntity requireIssue(String issueId) {
        ReviewIssueEntity issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "问题不存在");
        }
        return issue;
    }

    @Transactional
    public ReviewIssueDetail createIssue(String documentId, CreateReviewIssueRequest request) {
        ReviewDocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "文档不存在");
        }
        accessService.requireSpeakAccess(doc.getTaskId());
        TaskEntity task = accessService.requireTask(doc.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        if (doc.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_ARCHIVED", "文档已归档，不可提出问题");
        }

        String originRevId = request.originRevisionId();
        if (originRevId == null || originRevId.isBlank()) {
            originRevId = doc.getHeadRevisionId();
        }
        ReviewRevisionEntity originRev = revisionMapper.selectById(originRevId);
        if (originRev == null || !originRev.getDocumentId().equals(documentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "提问版本不存在或不属于当前文档");
        }

        // Determine assignee: default to task owner if not specified
        String assigneeId = request.assigneeId();
        if (assigneeId == null || assigneeId.isBlank()) {
            assigneeId = task.getUserId();
        }

        // Verify assignee exists in task space or is task owner
        if (!assigneeId.equals(task.getUserId())) {
            ReviewAccessService.Role assigneeRole = accessService.getUserRole(doc.getTaskId(), assigneeId);
            if (assigneeRole == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "指定责任人不是评审空间成员");
            }
        }

        // Determine round
        ReviewRoundEntity openRound = roundMapper.selectOne(
                new LambdaQueryWrapper<ReviewRoundEntity>()
                        .eq(ReviewRoundEntity::getDocumentId, documentId)
                        .eq(ReviewRoundEntity::getStatus, "OPEN")
        );

        String roundId = null;
        if (openRound != null) {
            if (originRev.getId().equals(openRound.getBaselineRevisionId())) {
                if (request.roundId() == null) {
                    roundId = openRound.getId(); // default to open round
                } else {
                    roundId = request.roundId(); // may be explicitly set or set to null/empty
                    if (roundId.isBlank()) roundId = null;
                }
            } else {
                if (request.roundId() != null && !request.roundId().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "所选版本不是当前轮次基线，只能记为轮次外问题");
                }
            }
        }

        Instant now = Instant.now();
        Integer lockedNext = documentMapper.lockNextIssueNo(documentId);
        if (lockedNext == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "文档不存在");
        }
        int displayNo = lockedNext;
        if (documentMapper.incrementNextIssueNo(documentId) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "问题编号分配冲突，请重试");
        }
        doc.setNextIssueNo(displayNo + 1);
        doc.setUpdatedAt(now);

        String issueId = Ids.next("rev_iss_");
        ReviewIssueEntity issue = new ReviewIssueEntity();
        issue.setId(issueId);
        issue.setTaskId(doc.getTaskId());
        issue.setDocumentId(documentId);
        issue.setDisplayNo(displayNo);
        issue.setRoundId(roundId);
        issue.setOriginRevisionId(originRev.getId());
        issue.setTitle(request.title().trim());
        issue.setBody(request.body().trim());
        issue.setType(request.type());
        issue.setSource(request.source());
        issue.setImpact(request.impact());
        issue.setReporterId(currentUser.getId());
        issue.setReporterNameSnapshot(currentUser.getDisplayName());
        issue.setReportedAtNote(request.reportedAtNote());
        issue.setReporterNote(request.reporterNote());
        issue.setAssigneeId(assigneeId);
        issue.setResponseDueAt(request.responseDueAt());
        issue.setStatus("OPEN");
        issue.setRevision(1);
        issue.setLastQuestionSeq(1);
        issue.setLastAnswerSeq(0);
        issue.setCreatedAt(now);
        issue.setUpdatedAt(now);
        issueMapper.insert(issue);

        // Anchor
        if (request.anchor() != null) {
            ReviewAnchorInput a = request.anchor();
            ReviewAnchorEntity anchor = new ReviewAnchorEntity();
            anchor.setId(Ids.next("rev_anc_"));
            anchor.setTaskId(doc.getTaskId());
            anchor.setDocumentId(documentId);
            anchor.setIssueId(issueId);
            anchor.setRevisionId(originRev.getId());
            anchor.setKind("ORIGINAL");
            anchor.setSelectorVersion(a.selectorVersion() != null ? a.selectorVersion() : "v1");
            anchor.setRendererFingerprint(a.rendererFingerprint());
            anchor.setRenderedTextHash(a.renderedTextHash());
            anchor.setExactQuote(a.exactQuote());
            anchor.setPrefixText(a.prefixText());
            anchor.setSuffixText(a.suffixText());
            anchor.setStartOffset(a.startOffset() != null ? a.startOffset() : 0);
            anchor.setEndOffset(a.endOffset() != null ? a.endOffset() : 0);
            anchor.setCreatedBy(currentUser.getId());
            anchor.setCreatedAt(now);
            anchorMapper.insert(anchor);
        }

        // Auto watch for reporter
        ReviewIssueWatchEntity watch = new ReviewIssueWatchEntity();
        watch.setTaskId(doc.getTaskId());
        watch.setIssueId(issueId);
        watch.setUserId(currentUser.getId());
        watch.setCreatedAt(now);
        watchMapper.insert(watch);

        notificationService.recordEventAndNotify(
                doc.getTaskId(),
                documentId,
                issueId,
                1,
                "ISSUE_CREATED",
                issueId,
                currentUser.getId(),
                currentUser.getDisplayName(),
                null,
                "OPEN",
                toJson(Map.of("displayNo", displayNo, "title", issue.getTitle())),
                List.of(assigneeId)
        );

        return getIssueDetail(issueId);
    }

    @Transactional
    public ReviewIssueDetail getIssueDetail(String issueId) {
        ReviewIssueEntity issue = requireIssue(issueId);
        accessService.requireReadAccess(issue.getTaskId());
        UserEntity currentUser = CurrentUser.require();
        notificationService.markReadForIssue(currentUser.getId(), issueId);
        return toIssueDetail(issue, currentUser);
    }

    @Transactional(readOnly = true)
    public ReviewIssueListResponse getDocumentIssues(String documentId, String roundId, String status, int page, int pageSize) {
        ReviewDocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "文档不存在");
        }
        accessService.requireReadAccess(doc.getTaskId());

        LambdaQueryWrapper<ReviewIssueEntity> query = new LambdaQueryWrapper<ReviewIssueEntity>()
                .eq(ReviewIssueEntity::getDocumentId, documentId);
        if (roundId != null && !roundId.isBlank()) {
            query.eq(ReviewIssueEntity::getRoundId, roundId);
        }
        if (status != null && !status.isBlank()) {
            query.eq(ReviewIssueEntity::getStatus, status);
        }
        query.orderByDesc(ReviewIssueEntity::getUpdatedAt);

        List<ReviewIssueEntity> issues = issueMapper.selectList(query);
        return buildIssueListResponse(issues, page, pageSize);
    }

    @Transactional
    public ReviewPostResponse createPost(String issueId, CreateReviewPostRequest request) {
        ReviewIssueEntity issue = requireIssue(issueId);
        ReviewDocumentEntity doc = documentMapper.selectById(issue.getDocumentId());
        accessService.requireSpeakAccess(issue.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        if (doc != null && doc.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_ARCHIVED", "文档已归档，不可发表讨论");
        }

        if (!issue.getRevision().equals(request.expectedRevision())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "问题状态已被修改，请刷新后重试");
        }

        String kind = request.kind().trim().toUpperCase();
        Instant now = Instant.now();
        int newSeq = issue.getRevision() + 1;

        boolean isOwner = accessService.isTaskOwner(issue.getTaskId(), currentUser.getId());
        boolean isAssignee = currentUser.getId().equals(issue.getAssigneeId());

        if ("ANSWER".equals(kind)) {
            if (currentUser.getId().equals(issue.getReporterId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "REVIEW_ACTION_FORBIDDEN", "问题提出者不可对自己的问题作正式答复");
            }
            // Official answer: author must be owner or assignee
            if (!isOwner && !isAssignee) {
                throw new ApiException(HttpStatus.FORBIDDEN, "REVIEW_ACTION_FORBIDDEN", "只有需求所有者或当前责任人可作正式答复");
            }
            if (!"OPEN".equals(issue.getStatus()) && !"AWAITING_CONFIRMATION".equals(issue.getStatus())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "仅待处理或待确认状态的问题可作正式答复");
            }

            issue.setLastAnswerSeq(newSeq);
            if (issue.getFirstAnswerAt() == null) {
                issue.setFirstAnswerAt(now);
            }
        } else if ("FOLLOW_UP".equals(kind)) {
            if (!"OPEN".equals(issue.getStatus()) && !"AWAITING_CONFIRMATION".equals(issue.getStatus())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "仅待处理或待确认状态可追加追问");
            }
            issue.setStatus("OPEN");
            issue.setLastQuestionSeq(newSeq);
        } else if ("CORRECTION".equals(kind)) {
            if (request.correctsPostId() == null || request.correctsPostId().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "更正发言必须指定原发言 ID");
            }
            ReviewPostEntity originalPost = postMapper.selectById(request.correctsPostId());
            if (originalPost == null || !originalPost.getIssueId().equals(issueId) || !originalPost.getAuthorId().equals(currentUser.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "只能更正自己发表过的原发言");
            }
        }

        String postId = Ids.next("rev_pst_");
        ReviewPostEntity post = new ReviewPostEntity();
        post.setId(postId);
        post.setTaskId(issue.getTaskId());
        post.setIssueId(issueId);
        post.setIssueSeq(newSeq);
        post.setKind(kind);
        post.setBody(request.body().trim());
        post.setReplyToPostId(request.replyToPostId());
        post.setCorrectsPostId(request.correctsPostId());
        post.setAuthorId(currentUser.getId());
        post.setAuthorNameSnapshot(currentUser.getDisplayName());
        post.setCreatedAt(now);
        postMapper.insert(post);

        // Auto-transition to AWAITING_CONFIRMATION if replied to by assignee, owner, or kind is ANSWER
        String fromState = issue.getStatus();
        if ("OPEN".equals(fromState) && ("ANSWER".equals(kind) || isAssignee || isOwner)) {
            issue.setStatus("AWAITING_CONFIRMATION");
            if (issue.getProposedResolution() == null || issue.getProposedResolution().isBlank()) {
                issue.setProposedResolution(toJson(Map.of(
                        "kind", "NO_CHANGE",
                        "conclusion", "责任人已答复",
                        "explanation", request.body().trim()
                )));
            }
        }

        issue.setRevision(newSeq);
        issue.setUpdatedAt(now);
        issueMapper.updateById(issue);

        // Notifications
        List<String> recipients = new ArrayList<>();
        if ("ANSWER".equals(kind)) {
            // Reporter + watchers
            recipients.add(issue.getReporterId());
            recipients.addAll(notificationService.getIssueWatchers(issueId));
            notificationService.recordEventAndNotify(
                    issue.getTaskId(),
                    issue.getDocumentId(),
                    issueId,
                    newSeq,
                    "ISSUE_ANSWERED",
                    postId,
                    currentUser.getId(),
                    currentUser.getDisplayName(),
                    null,
                    null,
                    toJson(Map.of("displayNo", safeDisplayNo(issue), "postId", postId)),
                    recipients
            );
        } else if ("FOLLOW_UP".equals(kind)) {
            recipients.add(issue.getAssigneeId());
            notificationService.recordEventAndNotify(
                    issue.getTaskId(),
                    issue.getDocumentId(),
                    issueId,
                    newSeq,
                    "ISSUE_FOLLOW_UP",
                    postId,
                    currentUser.getId(),
                    currentUser.getDisplayName(),
                    null,
                    null,
                    toJson(Map.of("displayNo", safeDisplayNo(issue), "postId", postId)),
                    recipients
            );
        }

        notificationService.markReadForIssue(currentUser.getId(), issueId);

        return new ReviewPostResponse(
                postId,
                issueId,
                newSeq,
                kind,
                post.getBody(),
                post.getReplyToPostId(),
                post.getCorrectsPostId(),
                currentUser.getId(),
                currentUser.getDisplayName(),
                now
        );
    }

    @Transactional
    public ReviewIssueDetail executeAction(String issueId, ReviewIssueActionRequest request) {
        ReviewIssueEntity issue = requireIssue(issueId);
        ReviewDocumentEntity doc = documentMapper.selectById(issue.getDocumentId());
        accessService.requireSpeakAccess(issue.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        if (doc != null && doc.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_ARCHIVED", "文档已归档，不可执行状态流转操作");
        }

        if (!issue.getRevision().equals(request.expectedRevision())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "问题状态已被修改，请刷新后重试");
        }

        String action = request.action().trim().toUpperCase();
        Instant now = Instant.now();
        int newSeq = issue.getRevision() + 1;
        boolean isOwner = accessService.isTaskOwner(issue.getTaskId(), currentUser.getId());
        boolean isAssignee = currentUser.getId().equals(issue.getAssigneeId());
        boolean isReporter = currentUser.getId().equals(issue.getReporterId());
        boolean reporterCanSpeak = accessService.canUserSpeak(issue.getTaskId(), issue.getReporterId());
        int qSeq = issue.getLastQuestionSeq() != null ? issue.getLastQuestionSeq() : 1;
        int aSeq = issue.getLastAnswerSeq() != null ? issue.getLastAnswerSeq() : 0;
        boolean awaitingResponse = "OPEN".equals(issue.getStatus()) && qSeq > aSeq;

        switch (action) {
            case "PROPOSE_RESOLUTION" -> {
                if (!isOwner && !isAssignee) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "REVIEW_ACTION_FORBIDDEN", "只有责任人或所有者可提出解决方案");
                }
                if (!"OPEN".equals(issue.getStatus())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "仅待处理状态可请求确认解决");
                }
                if (aSeq < qSeq) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "必须在进行正式答复后方可请求确认");
                }
                ResolutionInput res = request.resolution();
                if (res == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "必须提供解决方案内容");
                }
                String kind = res.kind().trim().toUpperCase();
                if ("DOC_CHANGED".equals(kind)) {
                    if (res.revisionId() == null || res.revisionId().isBlank()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "正文修订解决必须关联新修订 ID");
                    }
                    ReviewRevisionEntity rev = revisionMapper.selectById(res.revisionId());
                    if (rev == null || !rev.getDocumentId().equals(issue.getDocumentId())) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "关联修订不存在或不属于当前文档");
                    }
                    ReviewRevisionEntity originRev = revisionMapper.selectById(issue.getOriginRevisionId());
                    if (originRev != null && rev.getVersionNo() <= originRev.getVersionNo()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "关联修订版本号必须大于问题提出时的版本号");
                    }
                    Long linked = revisionIssueMapper.selectCount(
                            new LambdaQueryWrapper<ReviewRevisionIssueEntity>()
                                    .eq(ReviewRevisionIssueEntity::getRevisionId, res.revisionId())
                                    .eq(ReviewRevisionIssueEntity::getIssueId, issueId)
                    );
                    if (linked == 0) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "指定修订尚未在正文保存时关联此问题");
                    }
                } else if ("NO_CHANGE".equals(kind)) {
                    if (res.noChangeReason() == null || res.noChangeReason().isBlank()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "无需修改正文必须说明原因依据");
                    }
                } else {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "解决方案类型必须为 DOC_CHANGED 或 NO_CHANGE");
                }

                issue.setStatus("AWAITING_CONFIRMATION");
                issue.setProposedResolution(toJson(res));
                issue.setRevision(newSeq);
                issue.setUpdatedAt(now);
                issueMapper.updateById(issue);

                // Notify reporter if reporterCanSpeak, else owner
                List<String> target = reporterCanSpeak ? List.of(issue.getReporterId()) : List.of(taskMapper.selectById(issue.getTaskId()).getUserId());
                notificationService.recordEventAndNotify(
                        issue.getTaskId(),
                        issue.getDocumentId(),
                        issueId,
                        newSeq,
                        "RESOLUTION_PROPOSED",
                        issueId,
                        currentUser.getId(),
                        currentUser.getDisplayName(),
                        "OPEN",
                        "AWAITING_CONFIRMATION",
                        toJson(Map.of("displayNo", safeDisplayNo(issue), "conclusion", res.conclusion())),
                        target
                );
            }
            case "CONFIRM" -> {
                if (!"AWAITING_CONFIRMATION".equals(issue.getStatus()) && !"OPEN".equals(issue.getStatus())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "只有待确认或待处理状态的问题可以确认解决");
                }
                if (!isReporter) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "REVIEW_ACTION_FORBIDDEN", "只有问题提出者才能确认问题已解决");
                }

                String fromState = issue.getStatus();
                issue.setStatus("RESOLVED");
                issue.setResolvedBy(currentUser.getId());
                issue.setResolvedAt(now);
                issue.setRevision(newSeq);
                issue.setUpdatedAt(now);
                issueMapper.updateById(issue);

                notificationService.recordEventAndNotify(
                        issue.getTaskId(),
                        issue.getDocumentId(),
                        issueId,
                        newSeq,
                        "ISSUE_CONFIRMED",
                        issueId,
                        currentUser.getId(),
                        currentUser.getDisplayName(),
                        fromState,
                        "RESOLVED",
                        toJson(Map.of("displayNo", safeDisplayNo(issue), "proxy", !isReporter)),
                        notificationService.getIssueWatchers(issueId)
                );
            }
            case "REJECT" -> {
                if (!"AWAITING_CONFIRMATION".equals(issue.getStatus())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "只有待确认状态的问题可以拒绝解决确认");
                }
                if (reporterCanSpeak) {
                    if (!isReporter) {
                        throw new ApiException(HttpStatus.FORBIDDEN, "REVIEW_ACTION_FORBIDDEN", "提出者仍在职且可发言，不能由他人代办拒绝");
                    }
                } else {
                    if (!isOwner) {
                        throw new ApiException(HttpStatus.FORBIDDEN, "REVIEW_ACTION_FORBIDDEN", "提出者已不可发言，仅需求所有者可代办拒绝");
                    }
                    if (request.proxyReason() == null || request.proxyReason().isBlank()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "所有者代办拒绝必须填写代办理由");
                    }
                }
                if (request.reason() == null || request.reason().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "拒绝确认必须说明原因");
                }

                issue.setStatus("OPEN");
                issue.setLastQuestionSeq(newSeq); // requires new answer
                issue.setRevision(newSeq);
                issue.setUpdatedAt(now);
                issueMapper.updateById(issue);

                notificationService.recordEventAndNotify(
                        issue.getTaskId(),
                        issue.getDocumentId(),
                        issueId,
                        newSeq,
                        "ISSUE_REJECTED",
                        issueId,
                        currentUser.getId(),
                        currentUser.getDisplayName(),
                        "AWAITING_CONFIRMATION",
                        "OPEN",
                        toJson(Map.of("displayNo", safeDisplayNo(issue), "reason", request.reason().trim())),
                        List.of(issue.getAssigneeId())
                );
            }
            case "REOPEN" -> {
                if (!"RESOLVED".equals(issue.getStatus())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "仅已解决状态的问题可重开");
                }
                if (!isOwner && !(isReporter && reporterCanSpeak)) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "REVIEW_ACTION_FORBIDDEN", "只有提出者本人或需求所有者可重开问题");
                }
                if (request.reason() == null || request.reason().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "重开问题必须说明原因");
                }

                issue.setStatus("OPEN");
                issue.setResolvedBy(null);
                issue.setResolvedAt(null);
                issue.setLastQuestionSeq(newSeq);
                issue.setRevision(newSeq);
                issue.setUpdatedAt(now);
                issueMapper.updateById(issue);

                notificationService.recordEventAndNotify(
                        issue.getTaskId(),
                        issue.getDocumentId(),
                        issueId,
                        newSeq,
                        "ISSUE_REOPENED",
                        issueId,
                        currentUser.getId(),
                        currentUser.getDisplayName(),
                        "RESOLVED",
                        "OPEN",
                        toJson(Map.of("displayNo", safeDisplayNo(issue), "reason", request.reason().trim())),
                        List.of(issue.getAssigneeId())
                );
            }
            case "CANCEL" -> {
                if (!"OPEN".equals(issue.getStatus())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "仅待处理状态的问题可以直接撤销；待确认问题须先拒绝退回待处理");
                }
                if (!isReporter) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "REVIEW_ACTION_FORBIDDEN", "只有问题提出者本人可以撤销自己的问题");
                }
                if (request.reason() == null || request.reason().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "撤销问题必须填写原因或处置说明");
                }

                issue.setStatus("CANCELLED");
                issue.setRevision(newSeq);
                issue.setUpdatedAt(now);
                issueMapper.updateById(issue);

                notificationService.recordEventAndNotify(
                        issue.getTaskId(),
                        issue.getDocumentId(),
                        issueId,
                        newSeq,
                        "ISSUE_CANCELLED",
                        issueId,
                        currentUser.getId(),
                        currentUser.getDisplayName(),
                        "OPEN",
                        "CANCELLED",
                        toJson(Map.of("displayNo", safeDisplayNo(issue), "reason", request.reason().trim())),
                        notificationService.getIssueWatchers(issueId)
                );
            }
            case "RESTORE" -> {
                if (!"CANCELLED".equals(issue.getStatus())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "仅已撤销状态的问题可以恢复");
                }
                if (!isOwner) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "REVIEW_ACTION_FORBIDDEN", "仅需求所有者可以恢复已撤销的问题");
                }
                if (request.reason() == null || request.reason().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "恢复已撤销问题必须说明原因");
                }

                issue.setStatus("OPEN");
                issue.setLastQuestionSeq(newSeq);
                issue.setRevision(newSeq);
                issue.setUpdatedAt(now);
                issueMapper.updateById(issue);

                notificationService.recordEventAndNotify(
                        issue.getTaskId(),
                        issue.getDocumentId(),
                        issueId,
                        newSeq,
                        "ISSUE_RESTORED",
                        issueId,
                        currentUser.getId(),
                        currentUser.getDisplayName(),
                        "CANCELLED",
                        "OPEN",
                        toJson(Map.of("displayNo", safeDisplayNo(issue), "reason", request.reason().trim())),
                        List.of(issue.getAssigneeId())
                );
            }
            case "REMIND" -> {
                if ("RESOLVED".equals(issue.getStatus()) || "CANCELLED".equals(issue.getStatus())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "问题已解决或已撤销，无需提醒");
                }
                Set<String> reminderTargets = new LinkedHashSet<>();
                if (issue.getAssigneeId() != null) {
                    reminderTargets.add(issue.getAssigneeId());
                }
                TaskEntity task = accessService.requireTask(issue.getTaskId());
                reminderTargets.add(task.getUserId());
                reminderTargets.remove(currentUser.getId());

                issue.setRevision(newSeq);
                issue.setUpdatedAt(now);
                issueMapper.updateById(issue);

                notificationService.recordEventAndNotify(
                        issue.getTaskId(),
                        issue.getDocumentId(),
                        issueId,
                        newSeq,
                        "ISSUE_REMINDED",
                        issueId,
                        currentUser.getId(),
                        currentUser.getDisplayName(),
                        issue.getStatus(),
                        issue.getStatus(),
                        toJson(Map.of(
                                "displayNo", safeDisplayNo(issue),
                                "title", issue.getTitle(),
                                "remindedBy", currentUser.getDisplayName()
                        )),
                        new ArrayList<>(reminderTargets)
                );
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "不支持的操作类型: " + action);
        }

        return getIssueDetail(issueId);
    }

    @Transactional
    public ReviewIssueDetail assignIssue(String issueId, AssignReviewIssueRequest request) {
        ReviewIssueEntity issue = requireIssue(issueId);
        ReviewDocumentEntity doc = documentMapper.selectById(issue.getDocumentId());
        accessService.requireSpeakAccess(issue.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        if (doc != null && doc.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_ARCHIVED", "文档已归档，不可转派问题");
        }

        if ("RESOLVED".equals(issue.getStatus()) || "CANCELLED".equals(issue.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "已终结的问题不可转派");
        }

        boolean isOwner = accessService.isTaskOwner(issue.getTaskId(), currentUser.getId());
        boolean isAssignee = currentUser.getId().equals(issue.getAssigneeId());
        if (!isOwner && !isAssignee) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REVIEW_ACTION_FORBIDDEN", "只有责任人或所有者可转派责任人");
        }

        if (!issue.getRevision().equals(request.expectedRevision())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "问题状态已被修改，请刷新后重试");
        }

        String newAssigneeId = request.assigneeId();
        if (newAssigneeId.equals(issue.getReporterId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "责任人不能是该问题提出者");
        }

        ReviewAccessService.Role targetRole = accessService.getUserRole(issue.getTaskId(), newAssigneeId);
        if (targetRole == null || !targetRole.canEdit()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "新责任人必须是需求所有者或编辑者 EDITOR");
        }

        Instant now = Instant.now();
        int newSeq = issue.getRevision() + 1;
        String fromAssignee = issue.getAssigneeId();

        issue.setAssigneeId(newAssigneeId);
        if ("AWAITING_CONFIRMATION".equals(issue.getStatus())) {
            issue.setStatus("OPEN");
        }
        issue.setLastQuestionSeq(newSeq);
        issue.setRevision(newSeq);
        issue.setUpdatedAt(now);
        issueMapper.updateById(issue);

        // Auto watch for new assignee
        ReviewIssueWatchEntity watch = watchMapper.selectOne(
                new LambdaQueryWrapper<ReviewIssueWatchEntity>()
                        .eq(ReviewIssueWatchEntity::getIssueId, issueId)
                        .eq(ReviewIssueWatchEntity::getUserId, newAssigneeId)
        );
        if (watch == null) {
            ReviewIssueWatchEntity w = new ReviewIssueWatchEntity();
            w.setTaskId(issue.getTaskId());
            w.setIssueId(issueId);
            w.setUserId(newAssigneeId);
            w.setCreatedAt(now);
            watchMapper.insert(w);
        }

        notificationService.recordEventAndNotify(
                issue.getTaskId(),
                issue.getDocumentId(),
                issueId,
                newSeq,
                "ISSUE_ASSIGNED",
                issueId,
                currentUser.getId(),
                currentUser.getDisplayName(),
                fromAssignee,
                newAssigneeId,
                toJson(Map.of("displayNo", safeDisplayNo(issue), "reason", request.reason() != null ? request.reason() : "")),
                List.of(newAssigneeId)
        );

        return getIssueDetail(issueId);
    }

    @Transactional
    public ReviewIssueDetail updateIssue(String issueId, UpdateReviewIssueRequest request) {
        ReviewIssueEntity issue = requireIssue(issueId);
        ReviewDocumentEntity doc = documentMapper.selectById(issue.getDocumentId());
        accessService.requireSpeakAccess(issue.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        if (doc != null && doc.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_ARCHIVED", "文档已归档，不可修改问题信息");
        }

        boolean isOwner = accessService.isTaskOwner(issue.getTaskId(), currentUser.getId());
        boolean isAssignee = currentUser.getId().equals(issue.getAssigneeId());
        boolean isReporter = currentUser.getId().equals(issue.getReporterId());
        if (!isOwner && !isAssignee && !isReporter) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REVIEW_ACTION_FORBIDDEN", "无权修改该问题信息");
        }

        if (!issue.getRevision().equals(request.expectedRevision())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "问题状态已被修改，请刷新后重试");
        }

        if (request.title() != null && !request.title().isBlank()) {
            issue.setTitle(request.title().trim());
        }
        if (request.type() != null && !request.type().isBlank()) {
            issue.setType(request.type());
        }
        if (request.source() != null && !request.source().isBlank()) {
            issue.setSource(request.source());
        }
        if (request.impact() != null && !request.impact().isBlank()) {
            issue.setImpact(request.impact());
        }
        if (request.responseDueAt() != null) {
            issue.setResponseDueAt(request.responseDueAt());
        }

        int newSeq = issue.getRevision() + 1;
        issue.setRevision(newSeq);
        issue.setUpdatedAt(Instant.now());
        issueMapper.updateById(issue);

        return getIssueDetail(issueId);
    }

    @Transactional
    public ReviewAnchorDetail addAnchor(String issueId, AddReviewAnchorRequest request) {
        ReviewIssueEntity issue = requireIssue(issueId);
        accessService.requireSpeakAccess(issue.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        ReviewRevisionEntity rev = revisionMapper.selectById(request.revisionId());
        if (rev == null || !rev.getDocumentId().equals(issue.getDocumentId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "指定版本不存在或不属于当前文档");
        }

        if (!issue.getRevision().equals(request.expectedRevision())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "问题状态已被修改，请刷新后重试");
        }

        ReviewAnchorInput a = request.anchor();
        Instant now = Instant.now();
        String anchorId = Ids.next("rev_anc_");
        ReviewAnchorEntity anchor = new ReviewAnchorEntity();
        anchor.setId(anchorId);
        anchor.setTaskId(issue.getTaskId());
        anchor.setDocumentId(issue.getDocumentId());
        anchor.setIssueId(issueId);
        anchor.setRevisionId(request.revisionId());
        anchor.setKind("RELOCATED");
        anchor.setSelectorVersion(a.selectorVersion() != null ? a.selectorVersion() : "v1");
        anchor.setRendererFingerprint(a.rendererFingerprint());
        anchor.setRenderedTextHash(a.renderedTextHash());
        anchor.setExactQuote(a.exactQuote());
        anchor.setPrefixText(a.prefixText());
        anchor.setSuffixText(a.suffixText());
        anchor.setStartOffset(a.startOffset() != null ? a.startOffset() : 0);
        anchor.setEndOffset(a.endOffset() != null ? a.endOffset() : 0);
        anchor.setCreatedBy(currentUser.getId());
        anchor.setCreatedAt(now);
        anchorMapper.insert(anchor);

        issue.setRevision(issue.getRevision() + 1);
        issue.setUpdatedAt(now);
        issueMapper.updateById(issue);

        return toAnchorDetail(anchor);
    }

    @Transactional
    public void watchIssue(String issueId) {
        ReviewIssueEntity issue = requireIssue(issueId);
        accessService.requireSpeakAccess(issue.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        ReviewIssueWatchEntity existing = watchMapper.selectOne(
                new LambdaQueryWrapper<ReviewIssueWatchEntity>()
                        .eq(ReviewIssueWatchEntity::getIssueId, issueId)
                        .eq(ReviewIssueWatchEntity::getUserId, currentUser.getId())
        );
        if (existing == null) {
            ReviewIssueWatchEntity w = new ReviewIssueWatchEntity();
            w.setTaskId(issue.getTaskId());
            w.setIssueId(issueId);
            w.setUserId(currentUser.getId());
            w.setCreatedAt(Instant.now());
            watchMapper.insert(w);
        }
    }

    @Transactional
    public void unwatchIssue(String issueId) {
        ReviewIssueEntity issue = requireIssue(issueId);
        accessService.requireSpeakAccess(issue.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        watchMapper.delete(
                new LambdaQueryWrapper<ReviewIssueWatchEntity>()
                        .eq(ReviewIssueWatchEntity::getIssueId, issueId)
                        .eq(ReviewIssueWatchEntity::getUserId, currentUser.getId())
        );
    }

    @Transactional(readOnly = true)
    public ReviewTimelineResponse getTimeline(String issueId, int page, int pageSize) {
        ReviewIssueEntity issue = requireIssue(issueId);
        accessService.requireReadAccess(issue.getTaskId());

        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));

        List<ReviewPostEntity> posts = postMapper.selectList(
                new LambdaQueryWrapper<ReviewPostEntity>()
                        .eq(ReviewPostEntity::getIssueId, issueId)
                        .orderByAsc(ReviewPostEntity::getIssueSeq)
        );

        List<ReviewEventEntity> events = eventMapper.selectList(
                new LambdaQueryWrapper<ReviewEventEntity>()
                        .eq(ReviewEventEntity::getIssueId, issueId)
                        .orderByAsc(ReviewEventEntity::getIssueSeq)
        );

        List<ReviewTimelineItem> timeline = new ArrayList<>();
        for (ReviewPostEntity p : posts) {
            timeline.add(new ReviewTimelineItem(
                    "POST",
                    p.getIssueSeq(),
                    new ReviewPostResponse(
                            p.getId(),
                            p.getIssueId(),
                            p.getIssueSeq(),
                            p.getKind(),
                            p.getBody(),
                            p.getReplyToPostId(),
                            p.getCorrectsPostId(),
                            p.getAuthorId(),
                            p.getAuthorNameSnapshot(),
                            p.getCreatedAt()
                    ),
                    null,
                    p.getCreatedAt()
            ));
        }

        for (ReviewEventEntity ev : events) {
            Object metaObj = null;
            if (ev.getMetadata() != null && !ev.getMetadata().isBlank()) {
                try {
                    metaObj = objectMapper.readValue(ev.getMetadata(), Object.class);
                } catch (Exception ignored) {}
            }
            timeline.add(new ReviewTimelineItem(
                    "EVENT",
                    ev.getIssueSeq() != null ? ev.getIssueSeq() : 0,
                    null,
                    new ReviewEventResponse(
                            ev.getId(),
                            ev.getEventType(),
                            ev.getActorId(),
                            ev.getActorNameSnapshot(),
                            ev.getFromState(),
                            ev.getToState(),
                            metaObj,
                            ev.getCreatedAt()
                    ),
                    ev.getCreatedAt()
            ));
        }

        timeline.sort(Comparator.comparing(ReviewTimelineItem::issueSeq).thenComparing(ReviewTimelineItem::createdAt));

        int total = timeline.size();
        int from = Math.min((safePage - 1) * safePageSize, total);
        int to = Math.min(from + safePageSize, total);

        return new ReviewTimelineResponse(timeline.subList(from, to), total, safePage, safePageSize, Instant.now());
    }

    public ReviewIssueListResponse buildIssueListResponse(List<ReviewIssueEntity> issues, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));

        int total = issues.size();
        int openCount = 0;
        int awaitingCount = 0;
        int resolvedCount = 0;
        int cancelledCount = 0;

        List<ReviewIssueSummary> summaries = new ArrayList<>();
        for (ReviewIssueEntity issue : issues) {
            if ("OPEN".equals(issue.getStatus())) openCount++;
            else if ("AWAITING_CONFIRMATION".equals(issue.getStatus())) awaitingCount++;
            else if ("RESOLVED".equals(issue.getStatus())) resolvedCount++;
            else if ("CANCELLED".equals(issue.getStatus())) cancelledCount++;

            summaries.add(toIssueSummary(issue));
        }

        String resolutionRate = null;
        int nonCancelled = total - cancelledCount;
        if (nonCancelled > 0) {
            double rate = (double) resolvedCount / nonCancelled * 100.0;
            resolutionRate = String.format("%.1f%%", rate);
        }

        ReviewStatistics stats = new ReviewStatistics(
                total,
                openCount,
                awaitingCount,
                resolvedCount,
                cancelledCount,
                resolutionRate
        );

        int from = Math.min((safePage - 1) * safePageSize, total);
        int to = Math.min(from + safePageSize, total);

        return new ReviewIssueListResponse(
                summaries.subList(from, to),
                total,
                safePage,
                safePageSize,
                Instant.now(),
                stats
        );
    }

    private ReviewIssueSummary toIssueSummary(ReviewIssueEntity issue) {
        ReviewRevisionEntity originRev = revisionMapper.selectById(issue.getOriginRevisionId());
        int originVer = originRev != null ? originRev.getVersionNo() : 1;
        UserEntity reporter = userMapper.selectById(issue.getReporterId());
        UserEntity assignee = userMapper.selectById(issue.getAssigneeId());

        int qSeq = issue.getLastQuestionSeq() != null ? issue.getLastQuestionSeq() : 1;
        int aSeq = issue.getLastAnswerSeq() != null ? issue.getLastAnswerSeq() : 0;
        boolean awaitingResponse = "OPEN".equals(issue.getStatus()) && qSeq > aSeq;
        boolean overdue = issue.getResponseDueAt() != null && Instant.now().isAfter(issue.getResponseDueAt()) && awaitingResponse;

        Long postCount = postMapper.selectCount(
                new LambdaQueryWrapper<ReviewPostEntity>().eq(ReviewPostEntity::getIssueId, issue.getId())
        );

        return new ReviewIssueSummary(
                issue.getId(),
                issue.getTaskId(),
                issue.getDocumentId(),
                issue.getDisplayNo(),
                issue.getRoundId(),
                issue.getOriginRevisionId(),
                originVer,
                issue.getTitle(),
                issue.getType(),
                issue.getSource(),
                issue.getImpact(),
                issue.getReporterId(),
                reporter != null ? reporter.getDisplayName() : issue.getReporterNameSnapshot(),
                issue.getAssigneeId(),
                assignee != null ? assignee.getDisplayName() : "未分配",
                issue.getResponseDueAt(),
                issue.getStatus(),
                issue.getRevision(),
                awaitingResponse,
                overdue,
                issue.getCreatedAt(),
                issue.getUpdatedAt(),
                postCount.intValue()
        );
    }

    private ReviewIssueDetail toIssueDetail(ReviewIssueEntity issue, UserEntity currentUser) {
        ReviewRevisionEntity originRev = revisionMapper.selectById(issue.getOriginRevisionId());
        int originVer = originRev != null ? originRev.getVersionNo() : 1;
        UserEntity reporter = userMapper.selectById(issue.getReporterId());
        UserEntity assignee = userMapper.selectById(issue.getAssigneeId());
        UserEntity resolver = issue.getResolvedBy() != null ? userMapper.selectById(issue.getResolvedBy()) : null;

        int qSeq = issue.getLastQuestionSeq() != null ? issue.getLastQuestionSeq() : 1;
        int aSeq = issue.getLastAnswerSeq() != null ? issue.getLastAnswerSeq() : 0;
        boolean awaitingResponse = "OPEN".equals(issue.getStatus()) && qSeq > aSeq;
        boolean overdue = issue.getResponseDueAt() != null && Instant.now().isAfter(issue.getResponseDueAt()) && awaitingResponse;

        ReviewResolutionDetail resolution = null;
        if (issue.getProposedResolution() != null && !issue.getProposedResolution().isBlank()) {
            try {
                resolution = objectMapper.readValue(issue.getProposedResolution(), ReviewResolutionDetail.class);
            } catch (Exception ignored) {}
        }

        // Anchors
        ReviewAnchorEntity originalAnchor = anchorMapper.selectOne(
                new LambdaQueryWrapper<ReviewAnchorEntity>()
                        .eq(ReviewAnchorEntity::getIssueId, issue.getId())
                        .eq(ReviewAnchorEntity::getKind, "ORIGINAL")
        );

        List<ReviewAnchorEntity> allAnchors = anchorMapper.selectList(
                new LambdaQueryWrapper<ReviewAnchorEntity>()
                        .eq(ReviewAnchorEntity::getIssueId, issue.getId())
                        .orderByDesc(ReviewAnchorEntity::getCreatedAt)
        );
        ReviewAnchorEntity latestAnchor = allAnchors.isEmpty() ? null : allAnchors.get(0);

        Long postCount = postMapper.selectCount(
                new LambdaQueryWrapper<ReviewPostEntity>().eq(ReviewPostEntity::getIssueId, issue.getId())
        );

        boolean watching = watchMapper.selectCount(
                new LambdaQueryWrapper<ReviewIssueWatchEntity>()
                        .eq(ReviewIssueWatchEntity::getIssueId, issue.getId())
                        .eq(ReviewIssueWatchEntity::getUserId, currentUser.getId())
        ) > 0;

        boolean isReporter = currentUser.getId().equals(issue.getReporterId());
        boolean isOwner = accessService.isTaskOwner(issue.getTaskId(), currentUser.getId());
        boolean reporterCanSpeak = accessService.canUserSpeak(issue.getTaskId(), issue.getReporterId());

        boolean hasReplies = postCount != null && postCount > 0;
        boolean isAwaitingConfirmation = "AWAITING_CONFIRMATION".equals(issue.getStatus());
        boolean isOpenWithReplies = "OPEN".equals(issue.getStatus()) && hasReplies;

        boolean canConfirm = (isAwaitingConfirmation || isOpenWithReplies) && isReporter && reporterCanSpeak;
        boolean canProxyConfirm = false;

        return new ReviewIssueDetail(
                issue.getId(),
                issue.getTaskId(),
                issue.getDocumentId(),
                issue.getDisplayNo(),
                issue.getRoundId(),
                issue.getOriginRevisionId(),
                originVer,
                issue.getTitle(),
                issue.getBody(),
                issue.getType(),
                issue.getSource(),
                issue.getImpact(),
                issue.getReporterId(),
                reporter != null ? reporter.getDisplayName() : issue.getReporterNameSnapshot(),
                issue.getReportedAtNote(),
                issue.getReporterNote(),
                issue.getAssigneeId(),
                assignee != null ? assignee.getDisplayName() : "未分配",
                issue.getResponseDueAt(),
                issue.getStatus(),
                issue.getRevision(),
                qSeq,
                aSeq,
                issue.getFirstAnswerAt(),
                awaitingResponse,
                overdue,
                resolution,
                issue.getResolvedBy(),
                resolver != null ? resolver.getDisplayName() : null,
                issue.getResolvedAt(),
                issue.getCreatedAt(),
                issue.getUpdatedAt(),
                originalAnchor != null ? toAnchorDetail(originalAnchor) : null,
                latestAnchor != null ? toAnchorDetail(latestAnchor) : null,
                postCount.intValue(),
                watching,
                canConfirm,
                canProxyConfirm
        );
    }

    private ReviewAnchorDetail toAnchorDetail(ReviewAnchorEntity a) {
        return new ReviewAnchorDetail(
                a.getId(),
                a.getRevisionId(),
                a.getKind(),
                a.getSelectorVersion(),
                a.getRendererFingerprint(),
                a.getRenderedTextHash(),
                a.getExactQuote(),
                a.getPrefixText(),
                a.getSuffixText(),
                a.getStartOffset(),
                a.getEndOffset(),
                a.getCreatedAt()
        );
    }

    private int safeDisplayNo(ReviewIssueEntity issue) {
        return issue.getDisplayNo() != null ? issue.getDisplayNo() : 0;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
