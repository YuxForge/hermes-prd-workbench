package org.hermesprd.workbench.review;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ReviewApiModels {
    private ReviewApiModels() {
    }

    public record ReviewTodoCounts(
            int assignedToMe,
            int awaitingMyConfirm,
            int awaitingProxyConfirm,
            int unreadNotifications
    ) {}

    public record ReviewTaskSummary(
            String taskId,
            String title,
            String role,
            ReviewTodoCounts todoCounts,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ReviewTaskListResponse(
            List<ReviewTaskSummary> items,
            int total,
            int page,
            int pageSize,
            Instant asOf
    ) {}

    public record ReviewMemberResponse(
            String userId,
            String username,
            String displayName,
            String role,
            int revision,
            String createdBy,
            Instant createdAt
    ) {}

    public record ReviewCandidateUserResponse(
            String userId,
            String username,
            String displayName
    ) {}

    public record AddReviewMemberRequest(
            @NotBlank @Size(min = 1, max = 32) String username,
            @NotBlank String role
    ) {}

    public record MemberReassignmentItem(
            @NotBlank String issueId,
            @NotBlank String newAssigneeId
    ) {}

    public record UpdateReviewMemberRequest(
            @NotBlank String role,
            @NotNull Integer expectedRevision,
            List<MemberReassignmentItem> reassignment
    ) {}

    public record RemoveReviewMemberRequest(
            @NotNull Integer expectedRevision,
            List<MemberReassignmentItem> reassignment,
            @Size(max = 2000) String reason
    ) {}

    public record ReviewDocumentSummary(
            String id,
            String taskId,
            String title,
            String headRevisionId,
            Integer headVersionNo,
            int revision,
            int nextIssueNo,
            Instant archivedAt,
            String createdBy,
            Instant createdAt,
            Instant updatedAt,
            String openRoundId,
            int unresolvedIssueCount
    ) {}

    public record ReviewDocumentListResponse(
            List<ReviewDocumentSummary> items,
            int total,
            int page,
            int pageSize,
            Instant asOf
    ) {}

    public record CreateReviewDocumentRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank String content
    ) {}

    public record ImportDraftReviewDocumentRequest(
            @NotBlank String sourceId,
            @NotNull Instant expectedUpdatedAt,
            @NotBlank @Size(max = 200) String title
    ) {}

    public record ReviewDocumentDetail(
            ReviewDocumentSummary document,
            ReviewRevisionDetail headRevision,
            ReviewRoundDetail openRound,
            String currentUserRole
    ) {}

    public record UpdateReviewDocumentTitleRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull Integer expectedRevision,
            @NotBlank @Size(max = 2000) String reason
    ) {}

    public record ArchiveReviewDocumentRequest(
            @NotNull Integer expectedRevision,
            @NotBlank @Size(max = 2000) String reason
    ) {}

    public record UnarchiveReviewDocumentRequest(
            @NotNull Integer expectedRevision,
            @NotBlank @Size(max = 2000) String reason
    ) {}

    public record CreateReviewRevisionRequest(
            @NotNull Integer expectedRevision,
            @NotBlank String expectedHeadRevisionId,
            @NotBlank String content,
            @NotBlank @Size(max = 2000) String summary,
            List<String> relatedIssues,
            String restoredFromRevisionId
    ) {}

    public record ReviewRevisionDetail(
            String id,
            String taskId,
            String documentId,
            int versionNo,
            String parentRevisionId,
            String content,
            String contentHash,
            String summary,
            String changeType,
            String createdBy,
            String authorNameSnapshot,
            Instant createdAt,
            String originKind,
            Object originRef,
            List<String> relatedIssues
    ) {}

    public record ReviewRevisionSummary(
            String id,
            int versionNo,
            String summary,
            String changeType,
            String createdBy,
            String authorNameSnapshot,
            Instant createdAt,
            int relatedIssueCount
    ) {}

    public record ReviewTitleChangeItem(
            String fromTitle,
            String toTitle,
            String reason,
            String actorId,
            String actorName,
            Instant createdAt
    ) {}

    public record ReviewHistoryItem(
            String kind,
            @JsonInclude(JsonInclude.Include.NON_NULL) ReviewRevisionSummary revision,
            @JsonInclude(JsonInclude.Include.NON_NULL) ReviewTitleChangeItem titleChange,
            Instant createdAt
    ) {}

    public record ReviewHistoryResponse(
            List<ReviewHistoryItem> items,
            int total,
            int page,
            int pageSize,
            Instant asOf
    ) {}

    public record ReviewDiffLine(
            String type,
            Integer oldLineNo,
            Integer newLineNo,
            String content
    ) {}

    public record ReviewDiffResponse(
            String fromRevisionId,
            int fromVersionNo,
            String toRevisionId,
            int toVersionNo,
            List<ReviewDiffLine> lines
    ) {}

    public record CreateReviewRoundRequest(
            @NotBlank String baselineRevisionId,
            Integer expectedDocumentRevision,
            @Size(max = 2000) String instruction,
            Instant dueAt
    ) {}

    public record ReviewRoundDetail(
            String id,
            String taskId,
            String documentId,
            String baselineRevisionId,
            int baselineVersionNo,
            String status,
            int revision,
            String instruction,
            Instant dueAt,
            String conclusion,
            String summary,
            Object closeSnapshot,
            String createdBy,
            String closedBy,
            Instant createdAt,
            Instant closedAt,
            int totalIssues,
            int unresolvedIssues
    ) {}

    public record CloseReviewRoundRequest(
            @NotNull Integer expectedRevision,
            @NotBlank String conclusion,
            @Size(max = 2000) String summary,
            Map<String, String> unresolvedDispositions
    ) {}

    public record CancelReviewRoundRequest(
            @NotNull Integer expectedRevision,
            @NotBlank @Size(max = 2000) String reason
    ) {}

    public record ReviewAnchorInput(
            String selectorVersion,
            String rendererFingerprint,
            String renderedTextHash,
            @NotBlank @Size(max = 2000) String exactQuote,
            @Size(max = 128) String prefixText,
            @Size(max = 128) String suffixText,
            Integer startOffset,
            Integer endOffset
    ) {}

    public record ReviewAnchorDetail(
            String id,
            String revisionId,
            String kind,
            String selectorVersion,
            String rendererFingerprint,
            String renderedTextHash,
            String exactQuote,
            String prefixText,
            String suffixText,
            int startOffset,
            int endOffset,
            Instant createdAt
    ) {}

    public record CreateReviewIssueRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 10000) String body,
            @NotBlank String type,
            @NotBlank String source,
            @NotBlank String impact,
            String originRevisionId,
            String assigneeId,
            String roundId,
            Instant responseDueAt,
            Instant reportedAtNote,
            @Size(max = 255) String reporterNote,
            ReviewAnchorInput anchor
    ) {}

    public record ReviewResolutionDetail(
            String kind,
            String conclusion,
            String revisionId,
            String changeNote,
            String noChangeReason
    ) {}

    public record ReviewIssueDetail(
            String id,
            String taskId,
            String documentId,
            int displayNo,
            String roundId,
            String originRevisionId,
            int originVersionNo,
            String title,
            String body,
            String type,
            String source,
            String impact,
            String reporterId,
            String reporterName,
            Instant reportedAtNote,
            String reporterNote,
            String assigneeId,
            String assigneeName,
            Instant responseDueAt,
            String status,
            int revision,
            int lastQuestionSeq,
            int lastAnswerSeq,
            Instant firstAnswerAt,
            boolean awaitingResponse,
            boolean overdue,
            ReviewResolutionDetail proposedResolution,
            String resolvedBy,
            String resolvedByName,
            Instant resolvedAt,
            Instant createdAt,
            Instant updatedAt,
            ReviewAnchorDetail originalAnchor,
            ReviewAnchorDetail latestAnchor,
            int postCount,
            boolean watching,
            boolean canConfirm,
            boolean canProxyConfirm
    ) {}

    public record ReviewIssueSummary(
            String id,
            String taskId,
            String documentId,
            int displayNo,
            String roundId,
            String originRevisionId,
            int originVersionNo,
            String title,
            String type,
            String source,
            String impact,
            String reporterId,
            String reporterName,
            String assigneeId,
            String assigneeName,
            Instant responseDueAt,
            String status,
            int revision,
            boolean awaitingResponse,
            boolean overdue,
            Instant createdAt,
            Instant updatedAt,
            int postCount
    ) {}

    public record ReviewStatistics(
            int totalIssues,
            int openIssues,
            int awaitingConfirmationIssues,
            int resolvedIssues,
            int cancelledIssues,
            String resolutionRate
    ) {}

    public record ReviewIssueListResponse(
            List<ReviewIssueSummary> items,
            int total,
            int page,
            int pageSize,
            Instant asOf,
            ReviewStatistics statistics
    ) {}

    public record CreateReviewPostRequest(
            @NotNull Integer expectedRevision,
            @NotBlank String kind,
            @NotBlank @Size(max = 10000) String body,
            String replyToPostId,
            String correctsPostId
    ) {}

    public record ReviewPostResponse(
            String id,
            String issueId,
            int issueSeq,
            String kind,
            String body,
            String replyToPostId,
            String correctsPostId,
            String authorId,
            String authorName,
            Instant createdAt
    ) {}

    public record ReviewEventResponse(
            String id,
            String eventType,
            String actorId,
            String actorName,
            String fromState,
            String toState,
            Object metadata,
            Instant createdAt
    ) {}

    public record ReviewTimelineItem(
            String kind,
            int issueSeq,
            @JsonInclude(JsonInclude.Include.NON_NULL) ReviewPostResponse post,
            @JsonInclude(JsonInclude.Include.NON_NULL) ReviewEventResponse event,
            Instant createdAt
    ) {}

    public record ReviewTimelineResponse(
            List<ReviewTimelineItem> items,
            int total,
            int page,
            int pageSize,
            Instant asOf
    ) {}

    public record UpdateReviewIssueRequest(
            @NotNull Integer expectedRevision,
            @Size(min = 1, max = 200) String title,
            String type,
            String source,
            String impact,
            Instant responseDueAt
    ) {}

    public record AssignReviewIssueRequest(
            @NotNull Integer expectedRevision,
            @NotBlank String assigneeId,
            @Size(max = 2000) String reason
    ) {}

    public record ResolutionInput(
            @NotBlank String kind,
            @NotBlank @Size(max = 2000) String conclusion,
            String revisionId,
            @Size(max = 2000) String changeNote,
            @Size(max = 2000) String noChangeReason
    ) {}

    public record ReviewIssueActionRequest(
            @NotNull Integer expectedRevision,
            @NotBlank String action,
            @Size(max = 2000) String reason,
            @Size(max = 2000) String proxyReason,
            ResolutionInput resolution,
            String duplicateIssueId
    ) {}

    public record AddReviewAnchorRequest(
            @NotNull Integer expectedRevision,
            @NotBlank String revisionId,
            @NotNull ReviewAnchorInput anchor,
            @Size(max = 2000) String reason
    ) {}

    public record ReviewNotificationItem(
            String id,
            String taskId,
            String documentId,
            String issueId,
            String eventId,
            String eventType,
            String actorName,
            String targetTitle,
            Instant createdAt,
            Instant readAt
    ) {}

    public record ReviewNotificationListResponse(
            List<ReviewNotificationItem> items,
            int total,
            int unreadCount,
            int page,
            int pageSize
    ) {}

    public record MarkReviewNotificationsReadRequest(
            @NotNull @Size(min = 1, max = 100) List<String> notificationIds
    ) {}
}
