package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.auth.CurrentUser;
import org.hermesprd.workbench.auth.UserEntity;
import org.hermesprd.workbench.review.ReviewApiModels.*;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.Ids;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ReviewRoundService {
    private final ReviewAccessService accessService;
    private final ReviewDocumentMapper documentMapper;
    private final ReviewRevisionMapper revisionMapper;
    private final ReviewRoundMapper roundMapper;
    private final ReviewIssueMapper issueMapper;
    private final ReviewNotificationService notificationService;
    private final ObjectMapper objectMapper;

    public ReviewRoundService(
            ReviewAccessService accessService,
            ReviewDocumentMapper documentMapper,
            ReviewRevisionMapper revisionMapper,
            ReviewRoundMapper roundMapper,
            ReviewIssueMapper issueMapper,
            ReviewNotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.accessService = accessService;
        this.documentMapper = documentMapper;
        this.revisionMapper = revisionMapper;
        this.roundMapper = roundMapper;
        this.issueMapper = issueMapper;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    public ReviewRoundEntity requireRound(String roundId) {
        ReviewRoundEntity round = roundMapper.selectById(roundId);
        if (round == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "评审轮次不存在");
        }
        return round;
    }

    @Transactional(readOnly = true)
    public List<ReviewRoundDetail> getRounds(String documentId, int page, int pageSize) {
        ReviewDocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "文档不存在");
        }
        accessService.requireReadAccess(doc.getTaskId());

        List<ReviewRoundEntity> rounds = roundMapper.selectList(
                new LambdaQueryWrapper<ReviewRoundEntity>()
                        .eq(ReviewRoundEntity::getDocumentId, documentId)
                        .orderByDesc(ReviewRoundEntity::getCreatedAt)
        );

        List<ReviewRoundDetail> details = new ArrayList<>();
        for (ReviewRoundEntity r : rounds) {
            details.add(toRoundDetail(r));
        }
        return details;
    }

    @Transactional
    public ReviewRoundDetail createRound(String documentId, CreateReviewRoundRequest request) {
        ReviewDocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "文档不存在");
        }
        accessService.requireOwner(doc.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        if (doc.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_ARCHIVED", "文档已归档，不可发起新轮次");
        }

        if (request.expectedDocumentRevision() != null && !doc.getRevision().equals(request.expectedDocumentRevision())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "文档并发版本已变化，请刷新后重试");
        }

        Long openRounds = roundMapper.selectCount(
                new LambdaQueryWrapper<ReviewRoundEntity>()
                        .eq(ReviewRoundEntity::getDocumentId, documentId)
                        .eq(ReviewRoundEntity::getStatus, "OPEN")
        );
        if (openRounds > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ROUND_ALREADY_OPEN", "该文档已有进行中的开放评审轮次");
        }

        ReviewRevisionEntity baseRev = revisionMapper.selectById(request.baselineRevisionId());
        if (baseRev == null || !baseRev.getDocumentId().equals(documentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "基线版本不存在或不属于当前文档");
        }

        Instant now = Instant.now();
        String roundId = Ids.next("rev_rnd_");
        ReviewRoundEntity round = new ReviewRoundEntity();
        round.setId(roundId);
        round.setTaskId(doc.getTaskId());
        round.setDocumentId(documentId);
        round.setBaselineRevisionId(request.baselineRevisionId());
        round.setStatus("OPEN");
        round.setRevision(1);
        round.setInstruction(request.instruction() != null ? request.instruction().trim() : null);
        round.setDueAt(request.dueAt());
        round.setCreatedBy(currentUser.getId());
        round.setCreatedAt(now);
        roundMapper.insert(round);

        doc.setRevision(doc.getRevision() + 1);
        doc.setUpdatedAt(now);
        documentMapper.updateById(doc);

        notificationService.recordEventAndNotify(
                doc.getTaskId(),
                doc.getId(),
                null,
                null,
                "ROUND_OPENED",
                roundId,
                currentUser.getId(),
                currentUser.getDisplayName(),
                null,
                "OPEN",
                toJson(Map.of("roundId", roundId, "baselineVersionNo", baseRev.getVersionNo())),
                notificationService.getAllSpaceMembers(doc.getTaskId())
        );

        return toRoundDetail(round);
    }

    @Transactional
    public ReviewRoundDetail closeRound(String roundId, CloseReviewRoundRequest request) {
        ReviewRoundEntity round = requireRound(roundId);
        accessService.requireOwner(round.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        if (!"OPEN".equals(round.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT", "仅进行中的轮次可被结束");
        }

        if (!round.getRevision().equals(request.expectedRevision())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "轮次已被他人修改，请刷新后重试");
        }

        String conclusion = request.conclusion().trim().toUpperCase();
        if (!List.of("PASS", "PASS_WITH_RESIDUAL", "REJECT").contains(conclusion)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "评审结论必须为 PASS, PASS_WITH_RESIDUAL 或 REJECT");
        }

        // Section 6.1 validation:
        // roundIssues: issues belonging to this round
        List<ReviewIssueEntity> roundIssues = issueMapper.selectList(
                new LambdaQueryWrapper<ReviewIssueEntity>().eq(ReviewIssueEntity::getRoundId, roundId)
        );

        // unresolvedInDocument: status IN (OPEN, AWAITING_CONFIRMATION) in this entire document
        List<ReviewIssueEntity> unresolvedInDoc = issueMapper.selectList(
                new LambdaQueryWrapper<ReviewIssueEntity>()
                        .eq(ReviewIssueEntity::getDocumentId, round.getDocumentId())
                        .in(ReviewIssueEntity::getStatus, List.of("OPEN", "AWAITING_CONFIRMATION"))
        );

        if ("PASS".equals(conclusion)) {
            if (!unresolvedInDoc.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                        "文档尚存在 " + unresolvedInDoc.size() + " 项未解决问题，不能选择通过评审");
            }
            for (ReviewIssueEntity issue : roundIssues) {
                if (!"RESOLVED".equals(issue.getStatus()) && !"CANCELLED".equals(issue.getStatus())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                            "本轮问题 #" + issue.getDisplayNo() + " 尚未解决或撤销，不能通过评审");
                }
            }
        } else {
            // PASS_WITH_RESIDUAL or REJECT
            if (unresolvedInDoc.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                        "当前文档已无未解决问题，不能选择带遗留项通过或暂不通过");
            }
            Map<String, String> dispositions = request.unresolvedDispositions();
            if (dispositions == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "必须提供未解决问题处置说明");
            }
            for (ReviewIssueEntity issue : unresolvedInDoc) {
                String note = dispositions.get(issue.getId());
                if (note == null || note.trim().isEmpty()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                            "必须为文档未解决问题 #" + issue.getDisplayNo() + " 填写遗留处置说明");
                }
            }
        }

        Instant now = Instant.now();
        List<String> unresolvedIds = unresolvedInDoc.stream().map(ReviewIssueEntity::getId).toList();

        Map<String, Object> closeSnapshotMap = Map.of(
                "roundIssuesCount", roundIssues.size(),
                "unresolvedIssueIds", unresolvedIds,
                "conclusion", conclusion,
                "summary", request.summary() != null ? request.summary() : "",
                "unresolvedDispositions", request.unresolvedDispositions() != null ? request.unresolvedDispositions() : Map.of(),
                "closedAt", now.toString(),
                "closedBy", currentUser.getId()
        );

        round.setStatus("CLOSED");
        round.setConclusion(conclusion);
        round.setSummary(request.summary() != null ? request.summary().trim() : null);
        round.setCloseSnapshot(toJson(closeSnapshotMap));
        round.setClosedBy(currentUser.getId());
        round.setClosedAt(now);
        round.setRevision(round.getRevision() + 1);
        roundMapper.updateById(round);

        notificationService.recordEventAndNotify(
                round.getTaskId(),
                round.getDocumentId(),
                null,
                null,
                "ROUND_CLOSED",
                roundId,
                currentUser.getId(),
                currentUser.getDisplayName(),
                "OPEN",
                "CLOSED",
                toJson(Map.of("roundId", roundId, "conclusion", conclusion)),
                notificationService.getAllSpaceMembers(round.getTaskId())
        );

        return toRoundDetail(round);
    }

    @Transactional
    public ReviewRoundDetail cancelRound(String roundId, CancelReviewRoundRequest request) {
        ReviewRoundEntity round = requireRound(roundId);
        accessService.requireOwner(round.getTaskId());
        UserEntity currentUser = CurrentUser.require();

        if (!"OPEN".equals(round.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT", "仅进行中的轮次可被取消");
        }

        if (!round.getRevision().equals(request.expectedRevision())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "轮次已被他人修改，请刷新后重试");
        }

        Instant now = Instant.now();
        round.setStatus("CANCELLED");
        round.setSummary(request.reason().trim());
        round.setClosedBy(currentUser.getId());
        round.setClosedAt(now);
        round.setRevision(round.getRevision() + 1);
        roundMapper.updateById(round);

        notificationService.recordEventAndNotify(
                round.getTaskId(),
                round.getDocumentId(),
                null,
                null,
                "ROUND_CANCELLED",
                roundId,
                currentUser.getId(),
                currentUser.getDisplayName(),
                "OPEN",
                "CANCELLED",
                toJson(Map.of("roundId", roundId, "reason", request.reason().trim())),
                notificationService.getAllSpaceMembers(round.getTaskId())
        );

        return toRoundDetail(round);
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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
