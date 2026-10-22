package org.hermesprd.workbench.review;

import jakarta.validation.Valid;
import org.hermesprd.workbench.review.ReviewApiModels.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/review")
public class ReviewController {
    private final ReviewMemberService memberService;
    private final ReviewDocumentService documentService;
    private final ReviewRoundService roundService;
    private final ReviewIssueService issueService;
    private final ReviewQueryService queryService;
    private final ReviewNotificationService notificationService;
    private final ReviewExportService exportService;

    public ReviewController(
            ReviewMemberService memberService,
            ReviewDocumentService documentService,
            ReviewRoundService roundService,
            ReviewIssueService issueService,
            ReviewQueryService queryService,
            ReviewNotificationService notificationService,
            ReviewExportService exportService
    ) {
        this.memberService = memberService;
        this.documentService = documentService;
        this.roundService = roundService;
        this.issueService = issueService;
        this.queryService = queryService;
        this.notificationService = notificationService;
        this.exportService = exportService;
    }

    @GetMapping("/tasks")
    public ReviewTaskListResponse getTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return queryService.getTasks(page, pageSize);
    }

    @GetMapping("/tasks/{taskId}/members")
    public List<ReviewMemberResponse> getMembers(@PathVariable String taskId) {
        return memberService.getMembers(taskId);
    }

    @GetMapping("/tasks/{taskId}/candidate-users")
    public List<ReviewCandidateUserResponse> searchCandidateUsers(
            @PathVariable String taskId,
            @RequestParam(required = false) String query
    ) {
        return memberService.searchCandidateUsers(taskId, query);
    }

    @PostMapping("/tasks/{taskId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewMemberResponse addMember(
            @PathVariable String taskId,
            @Valid @RequestBody AddReviewMemberRequest request
    ) {
        return memberService.addMember(taskId, request);
    }

    @PatchMapping("/tasks/{taskId}/members/{userId}")
    public ReviewMemberResponse updateMember(
            @PathVariable String taskId,
            @PathVariable String userId,
            @Valid @RequestBody UpdateReviewMemberRequest request
    ) {
        return memberService.updateMember(taskId, userId, request);
    }

    @PostMapping("/tasks/{taskId}/members/{userId}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable String taskId,
            @PathVariable String userId,
            @Valid @RequestBody RemoveReviewMemberRequest request
    ) {
        memberService.removeMember(taskId, userId, request);
    }

    @GetMapping("/tasks/{taskId}/documents")
    public ReviewDocumentListResponse getDocuments(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return documentService.getDocuments(taskId, page, pageSize);
    }

    @PostMapping("/tasks/{taskId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewDocumentDetail createDocument(
            @PathVariable String taskId,
            @Valid @RequestBody CreateReviewDocumentRequest request
    ) {
        return documentService.createDocument(taskId, request);
    }

    @PostMapping("/tasks/{taskId}/documents/import-draft")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewDocumentDetail importDraft(
            @PathVariable String taskId,
            @Valid @RequestBody ImportDraftReviewDocumentRequest request
    ) {
        return documentService.importDraft(taskId, request);
    }

    @GetMapping(value = "/tasks/{taskId}/export", produces = "application/zip")
    public ResponseEntity<byte[]> exportPackage(
            @PathVariable String taskId,
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) String revisionId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        byte[] zipBytes = exportService.exportPackage(taskId, documentId, revisionId, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"review-handover-" + taskId + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }

    @GetMapping("/documents/{documentId}")
    public ReviewDocumentDetail getDocument(@PathVariable String documentId) {
        return documentService.getDocumentDetail(documentId);
    }

    @PatchMapping("/documents/{documentId}")
    public ReviewDocumentDetail updateTitle(
            @PathVariable String documentId,
            @Valid @RequestBody UpdateReviewDocumentTitleRequest request
    ) {
        return documentService.updateTitle(documentId, request);
    }

    @PostMapping("/documents/{documentId}/archive")
    public ReviewDocumentDetail archiveDocument(
            @PathVariable String documentId,
            @Valid @RequestBody ArchiveReviewDocumentRequest request
    ) {
        return documentService.archiveDocument(documentId, request);
    }

    @PostMapping("/documents/{documentId}/unarchive")
    public ReviewDocumentDetail unarchiveDocument(
            @PathVariable String documentId,
            @Valid @RequestBody UnarchiveReviewDocumentRequest request
    ) {
        return documentService.unarchiveDocument(documentId, request);
    }

    @GetMapping("/documents/{documentId}/revisions")
    public ReviewHistoryResponse getRevisions(
            @PathVariable String documentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String authorId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        return documentService.getRevisions(documentId, page, pageSize, authorId, from, to);
    }

    @GetMapping("/documents/{documentId}/revisions/{revisionId}")
    public ReviewRevisionDetail getRevision(
            @PathVariable String documentId,
            @PathVariable String revisionId
    ) {
        return documentService.getRevision(documentId, revisionId);
    }

    @PostMapping("/documents/{documentId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewRevisionDetail createRevision(
            @PathVariable String documentId,
            @Valid @RequestBody CreateReviewRevisionRequest request
    ) {
        return documentService.createRevision(documentId, request);
    }

    @GetMapping("/documents/{documentId}/diff")
    public ReviewDiffResponse getDiff(
            @PathVariable String documentId,
            @RequestParam String fromRevisionId,
            @RequestParam String toRevisionId
    ) {
        return documentService.getDiff(documentId, fromRevisionId, toRevisionId);
    }

    @GetMapping("/documents/{documentId}/rounds")
    public List<ReviewRoundDetail> getRounds(
            @PathVariable String documentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return roundService.getRounds(documentId, page, pageSize);
    }

    @PostMapping("/documents/{documentId}/rounds")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewRoundDetail createRound(
            @PathVariable String documentId,
            @Valid @RequestBody CreateReviewRoundRequest request
    ) {
        return roundService.createRound(documentId, request);
    }

    @PostMapping("/rounds/{roundId}/close")
    public ReviewRoundDetail closeRound(
            @PathVariable String roundId,
            @Valid @RequestBody CloseReviewRoundRequest request
    ) {
        return roundService.closeRound(roundId, request);
    }

    @PostMapping("/rounds/{roundId}/cancel")
    public ReviewRoundDetail cancelRound(
            @PathVariable String roundId,
            @Valid @RequestBody CancelReviewRoundRequest request
    ) {
        return roundService.cancelRound(roundId, request);
    }

    @PostMapping("/documents/{documentId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewIssueDetail createIssue(
            @PathVariable String documentId,
            @Valid @RequestBody CreateReviewIssueRequest request
    ) {
        return issueService.createIssue(documentId, request);
    }

    @GetMapping("/documents/{documentId}/issues")
    public ReviewIssueListResponse getDocumentIssues(
            @PathVariable String documentId,
            @RequestParam(required = false) String roundId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return issueService.getDocumentIssues(documentId, roundId, status, page, pageSize);
    }

    @GetMapping("/issues")
    public ReviewIssueListResponse queryIssues(
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) String roundId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String impact,
            @RequestParam(required = false) String reporterId,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer displayNo,
            @RequestParam(required = false) Boolean awaitingResponse,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) String filterCategory,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder
    ) {
        return queryService.queryIssues(
                taskId, documentId, roundId, status, type, source, impact,
                reporterId, assigneeId, keyword, displayNo, awaitingResponse,
                overdue, filterCategory, page, pageSize, sortBy, sortOrder
        );
    }

    @GetMapping("/issues/{issueId}")
    public ReviewIssueDetail getIssue(@PathVariable String issueId) {
        return issueService.getIssueDetail(issueId);
    }

    @PatchMapping("/issues/{issueId}")
    public ReviewIssueDetail updateIssue(
            @PathVariable String issueId,
            @Valid @RequestBody UpdateReviewIssueRequest request
    ) {
        return issueService.updateIssue(issueId, request);
    }

    @GetMapping("/issues/{issueId}/timeline")
    public ReviewTimelineResponse getTimeline(
            @PathVariable String issueId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        return issueService.getTimeline(issueId, page, pageSize);
    }

    @PostMapping("/issues/{issueId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewPostResponse createPost(
            @PathVariable String issueId,
            @Valid @RequestBody CreateReviewPostRequest request
    ) {
        return issueService.createPost(issueId, request);
    }

    @PostMapping("/issues/{issueId}/assign")
    public ReviewIssueDetail assignIssue(
            @PathVariable String issueId,
            @Valid @RequestBody AssignReviewIssueRequest request
    ) {
        return issueService.assignIssue(issueId, request);
    }

    @PostMapping("/issues/{issueId}/actions")
    public ReviewIssueDetail executeAction(
            @PathVariable String issueId,
            @Valid @RequestBody ReviewIssueActionRequest request
    ) {
        return issueService.executeAction(issueId, request);
    }

    @PostMapping("/issues/{issueId}/anchors")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewAnchorDetail addAnchor(
            @PathVariable String issueId,
            @Valid @RequestBody AddReviewAnchorRequest request
    ) {
        return issueService.addAnchor(issueId, request);
    }

    @PostMapping("/issues/{issueId}/watch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void watchIssue(@PathVariable String issueId) {
        issueService.watchIssue(issueId);
    }

    @DeleteMapping("/issues/{issueId}/watch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unwatchIssue(@PathVariable String issueId) {
        issueService.unwatchIssue(issueId);
    }

    @GetMapping("/notifications")
    public ReviewNotificationListResponse getNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return notificationService.getNotifications(unreadOnly, page, pageSize);
    }

    @PostMapping("/notifications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@Valid @RequestBody MarkReviewNotificationsReadRequest request) {
        notificationService.markRead(request.notificationIds());
    }
}
