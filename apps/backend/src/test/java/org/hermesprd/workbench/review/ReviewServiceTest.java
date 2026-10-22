package org.hermesprd.workbench.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.auth.CurrentUser;
import org.hermesprd.workbench.auth.UserEntity;
import org.hermesprd.workbench.auth.UserMapper;
import org.hermesprd.workbench.review.ReviewApiModels.*;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.task.RunMapper;
import org.hermesprd.workbench.task.SourceEntity;
import org.hermesprd.workbench.task.SourceMapper;
import org.hermesprd.workbench.task.TaskEntity;
import org.hermesprd.workbench.task.TaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewServiceTest {
    private TaskMapper taskMapper;
    private ReviewMemberMapper memberMapper;
    private UserMapper userMapper;
    private ReviewDocumentMapper documentMapper;
    private ReviewRevisionMapper revisionMapper;
    private ReviewRevisionIssueMapper revisionIssueMapper;
    private ReviewRoundMapper roundMapper;
    private ReviewIssueMapper issueMapper;
    private ReviewPostMapper postMapper;
    private ReviewAnchorMapper anchorMapper;
    private ReviewEventMapper eventMapper;
    private ReviewIssueWatchMapper watchMapper;
    private ReviewNotificationMapper notificationMapper;
    private SourceMapper sourceMapper;
    private RunMapper runMapper;
    private ObjectMapper objectMapper;

    private ReviewAccessService accessService;
    private ReviewNotificationService notificationService;
    private ReviewMemberService memberService;
    private ReviewDocumentService documentService;
    private ReviewRoundService roundService;
    private ReviewIssueService issueService;
    private ReviewExportService exportService;

    private UserEntity ownerUser;
    private UserEntity editorUser;
    private UserEntity reviewerUser;
    private UserEntity viewerUser;
    private UserEntity outsiderUser;
    private TaskEntity task;

    @BeforeEach
    void setUp() {
        taskMapper = mock(TaskMapper.class);
        memberMapper = mock(ReviewMemberMapper.class);
        userMapper = mock(UserMapper.class);
        documentMapper = mock(ReviewDocumentMapper.class);
        revisionMapper = mock(ReviewRevisionMapper.class);
        revisionIssueMapper = mock(ReviewRevisionIssueMapper.class);
        roundMapper = mock(ReviewRoundMapper.class);
        issueMapper = mock(ReviewIssueMapper.class);
        postMapper = mock(ReviewPostMapper.class);
        anchorMapper = mock(ReviewAnchorMapper.class);
        eventMapper = mock(ReviewEventMapper.class);
        watchMapper = mock(ReviewIssueWatchMapper.class);
        notificationMapper = mock(ReviewNotificationMapper.class);
        sourceMapper = mock(SourceMapper.class);
        runMapper = mock(RunMapper.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();

        accessService = new ReviewAccessService(taskMapper, memberMapper, userMapper);
        notificationService = new ReviewNotificationService(
                notificationMapper, eventMapper, watchMapper,
                memberMapper, taskMapper, userMapper, documentMapper, issueMapper
        );
        memberService = new ReviewMemberService(
                accessService, memberMapper, userMapper, issueMapper, watchMapper, notificationService, objectMapper
        );
        documentService = new ReviewDocumentService(
                accessService, documentMapper, revisionMapper, revisionIssueMapper, roundMapper,
                issueMapper, eventMapper, sourceMapper, runMapper, userMapper, notificationService, objectMapper
        );
        roundService = new ReviewRoundService(
                accessService, documentMapper, revisionMapper, roundMapper, issueMapper, notificationService, objectMapper
        );
        issueService = new ReviewIssueService(
                accessService, documentMapper, revisionMapper, revisionIssueMapper, roundMapper,
                issueMapper, postMapper, anchorMapper, eventMapper, watchMapper, notificationService,
                taskMapper, userMapper, objectMapper
        );
        exportService = new ReviewExportService(
                accessService, documentMapper, revisionMapper, revisionIssueMapper, issueMapper, postMapper, eventMapper, roundMapper, objectMapper
        );

        ownerUser = new UserEntity();
        ownerUser.setId("usr_owner");
        ownerUser.setUsername("owner");
        ownerUser.setDisplayName("需求所有者");
        ownerUser.setRole("USER");

        editorUser = new UserEntity();
        editorUser.setId("usr_editor");
        editorUser.setUsername("editor");
        editorUser.setDisplayName("开发编辑者");
        editorUser.setRole("USER");

        reviewerUser = new UserEntity();
        reviewerUser.setId("usr_reviewer");
        reviewerUser.setUsername("reviewer");
        reviewerUser.setDisplayName("测试评审者");
        reviewerUser.setRole("USER");

        viewerUser = new UserEntity();
        viewerUser.setId("usr_viewer");
        viewerUser.setUsername("viewer");
        viewerUser.setDisplayName("只读访客");
        viewerUser.setRole("USER");

        outsiderUser = new UserEntity();
        outsiderUser.setId("usr_outsider");
        outsiderUser.setUsername("outsider");
        outsiderUser.setDisplayName("未加入用户");
        outsiderUser.setRole("ADMIN"); // Admin should still be rejected if not a member

        task = new TaskEntity();
        task.setId("task_1");
        task.setTitle("采购需求系统");
        task.setUserId(ownerUser.getId());
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());

        when(taskMapper.selectById("task_1")).thenReturn(task);
        when(userMapper.selectById("usr_owner")).thenReturn(ownerUser);
        when(userMapper.selectById("usr_editor")).thenReturn(editorUser);
        when(userMapper.selectById("usr_reviewer")).thenReturn(reviewerUser);
        when(userMapper.selectById("usr_viewer")).thenReturn(viewerUser);
        when(userMapper.selectById("usr_outsider")).thenReturn(outsiderUser);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    // -------------------------------------------------------------
    // Access Control Tests
    // -------------------------------------------------------------

    @Test
    void ownerHasImplicitOwnerRole() {
        CurrentUser.set(ownerUser);
        ReviewAccessService.Role role = accessService.requireReadAccess("task_1");
        assertEquals(ReviewAccessService.Role.OWNER, role);
        assertTrue(role.canSpeak());
        assertTrue(role.canEdit());
        assertTrue(role.isOwner());
    }

    @Test
    void adminUserWithoutMembershipGetsNotFoundToHideExistence() {
        CurrentUser.set(outsiderUser);
        ApiException ex = assertThrows(ApiException.class, () -> accessService.requireReadAccess("task_1"));
        assertEquals(HttpStatus.NOT_FOUND, ex.status());
        assertEquals("RESOURCE_NOT_FOUND", ex.code());
    }

    @Test
    void viewerCanReadButCannotSpeakOrEdit() {
        ReviewMemberEntity member = new ReviewMemberEntity();
        member.setTaskId("task_1");
        member.setUserId(viewerUser.getId());
        member.setRole("VIEWER");
        when(memberMapper.selectOne(any())).thenReturn(member);

        CurrentUser.set(viewerUser);
        assertEquals(ReviewAccessService.Role.VIEWER, accessService.requireReadAccess("task_1"));

        ApiException ex = assertThrows(ApiException.class, () -> accessService.requireSpeakAccess("task_1"));
        assertEquals(HttpStatus.FORBIDDEN, ex.status());

        ApiException ex2 = assertThrows(ApiException.class, () -> accessService.requireEditAccess("task_1"));
        assertEquals(HttpStatus.FORBIDDEN, ex2.status());
    }

    // -------------------------------------------------------------
    // Member Management Tests
    // -------------------------------------------------------------

    @Test
    void nonOwnerCannotAddMember() {
        CurrentUser.set(editorUser);
        ReviewMemberEntity member = new ReviewMemberEntity();
        member.setTaskId("task_1");
        member.setUserId(editorUser.getId());
        member.setRole("EDITOR");
        when(memberMapper.selectOne(any())).thenReturn(member);

        ApiException ex = assertThrows(ApiException.class, () ->
                memberService.addMember("task_1", new AddReviewMemberRequest("reviewer", "REVIEWER"))
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.status());
    }

    @Test
    void ownerAddingOwnerAgainThrowsConflict() {
        CurrentUser.set(ownerUser);
        when(userMapper.selectOne(any())).thenReturn(ownerUser);

        ApiException ex = assertThrows(ApiException.class, () ->
                memberService.addMember("task_1", new AddReviewMemberRequest("owner", "EDITOR"))
        );
        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertEquals("REVIEW_STATE_CONFLICT", ex.code());
    }

    // -------------------------------------------------------------
    // Document Creation & Revision Tests
    // -------------------------------------------------------------

    @Test
    void editorCannotCreateDocument() {
        CurrentUser.set(editorUser);
        ReviewMemberEntity member = new ReviewMemberEntity();
        member.setTaskId("task_1");
        member.setUserId(editorUser.getId());
        member.setRole("EDITOR");
        when(memberMapper.selectOne(any())).thenReturn(member);

        ApiException ex = assertThrows(ApiException.class, () ->
                documentService.createDocument("task_1", new CreateReviewDocumentRequest("新PRD", "# 内容"))
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.status());
    }

    @Test
    void ownerCreatesDocumentWithV1Revision() {
        CurrentUser.set(ownerUser);
        when(documentMapper.insert(any(ReviewDocumentEntity.class))).thenReturn(1);
        when(revisionMapper.insert(any(ReviewRevisionEntity.class))).thenReturn(1);

        ReviewDocumentEntity doc = new ReviewDocumentEntity();
        doc.setId("rev_doc_1");
        doc.setTaskId("task_1");
        doc.setTitle("采购系统需求");
        doc.setRevision(1);
        doc.setNextIssueNo(1);
        doc.setHeadRevisionId("rev_ver_1");
        when(documentMapper.selectById(any())).thenReturn(doc);

        ReviewRevisionEntity rev = new ReviewRevisionEntity();
        rev.setId("rev_ver_1");
        rev.setTaskId("task_1");
        rev.setDocumentId("rev_doc_1");
        rev.setVersionNo(1);
        rev.setContent("# 采购需求\n第一节");
        rev.setContentHash("hash123");
        rev.setSummary("初稿");
        rev.setChangeType("INITIAL");
        rev.setOriginKind("MANUAL");
        rev.setCreatedBy(ownerUser.getId());
        rev.setAuthorNameSnapshot(ownerUser.getDisplayName());
        rev.setCreatedAt(Instant.now());
        when(revisionMapper.selectById("rev_ver_1")).thenReturn(rev);

        ReviewDocumentDetail detail = documentService.createDocument(
                "task_1", new CreateReviewDocumentRequest("采购系统需求", "# 采购需求\n第一节")
        );
        assertNotNull(detail);
        assertEquals("采购系统需求", detail.document().title());
        assertEquals(1, detail.headRevision().versionNo());
    }

    @Test
    void savingIdenticalContentThrowsNoContentChange() {
        CurrentUser.set(ownerUser);
        ReviewDocumentEntity doc = new ReviewDocumentEntity();
        doc.setId("rev_doc_1");
        doc.setTaskId("task_1");
        doc.setRevision(1);
        doc.setHeadRevisionId("rev_ver_1");
        when(documentMapper.selectById("rev_doc_1")).thenReturn(doc);

        ReviewRevisionEntity headRev = new ReviewRevisionEntity();
        headRev.setId("rev_ver_1");
        headRev.setContent("# 相同内容");
        headRev.setVersionNo(1);
        when(revisionMapper.selectById("rev_ver_1")).thenReturn(headRev);

        ApiException ex = assertThrows(ApiException.class, () ->
                documentService.createRevision("rev_doc_1", new CreateReviewRevisionRequest(
                        1, "rev_ver_1", "# 相同内容", "尝试相同内容保存", null, null
                ))
        );
        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertEquals("NO_CONTENT_CHANGE", ex.code());
    }

    // -------------------------------------------------------------
    // Issue & Discussion Rules Tests
    // -------------------------------------------------------------

    @Test
    void reporterCannotSelfAnswer() {
        CurrentUser.set(ownerUser); // current user is owner and reporter
        ReviewIssueEntity issue = new ReviewIssueEntity();
        issue.setId("iss_1");
        issue.setTaskId("task_1");
        issue.setDocumentId("doc_1");
        issue.setReporterId(ownerUser.getId());
        issue.setAssigneeId(editorUser.getId());
        issue.setStatus("OPEN");
        issue.setRevision(1);
        issue.setLastQuestionSeq(1);
        issue.setLastAnswerSeq(0);
        when(issueMapper.selectById("iss_1")).thenReturn(issue);

        ApiException ex = assertThrows(ApiException.class, () ->
                issueService.createPost("iss_1", new CreateReviewPostRequest(
                        1, "ANSWER", "我自己答复我自己", null, null
                ))
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.status());
        assertEquals("REVIEW_ACTION_FORBIDDEN", ex.code());
    }

    @Test
    void officialAnswerUpdatesSequenceAndClearsNeverAnswered() {
        CurrentUser.set(editorUser);
        ReviewMemberEntity member = new ReviewMemberEntity();
        member.setTaskId("task_1");
        member.setUserId(editorUser.getId());
        member.setRole("EDITOR");
        when(memberMapper.selectOne(any())).thenReturn(member);

        ReviewIssueEntity issue = new ReviewIssueEntity();
        issue.setId("iss_1");
        issue.setTaskId("task_1");
        issue.setDocumentId("doc_1");
        issue.setReporterId(reviewerUser.getId());
        issue.setAssigneeId(editorUser.getId());
        issue.setStatus("OPEN");
        issue.setRevision(1);
        issue.setLastQuestionSeq(1);
        issue.setLastAnswerSeq(0);
        issue.setFirstAnswerAt(null);
        when(issueMapper.selectById("iss_1")).thenReturn(issue);

        ReviewPostResponse post = issueService.createPost("iss_1", new CreateReviewPostRequest(
                1, "ANSWER", "经确认，8600元需要财务审批", null, null
        ));
        assertNotNull(post);
        assertEquals(2, post.issueSeq());
        assertEquals(2, issue.getLastAnswerSeq());
        assertNotNull(issue.getFirstAnswerAt());
    }

    @Test
    void proposingResolutionBeforeOfficialAnswerFails() {
        CurrentUser.set(editorUser);
        ReviewMemberEntity member = new ReviewMemberEntity();
        member.setTaskId("task_1");
        member.setUserId(editorUser.getId());
        member.setRole("EDITOR");
        when(memberMapper.selectOne(any())).thenReturn(member);

        ReviewIssueEntity issue = new ReviewIssueEntity();
        issue.setId("iss_1");
        issue.setTaskId("task_1");
        issue.setDocumentId("doc_1");
        issue.setReporterId(reviewerUser.getId());
        issue.setAssigneeId(editorUser.getId());
        issue.setStatus("OPEN");
        issue.setRevision(1);
        issue.setLastQuestionSeq(2); // follow up occurred
        issue.setLastAnswerSeq(1);    // last answer is older than question
        when(issueMapper.selectById("iss_1")).thenReturn(issue);

        ApiException ex = assertThrows(ApiException.class, () ->
                issueService.executeAction("iss_1", new ReviewIssueActionRequest(
                        1, "PROPOSE_RESOLUTION", null, null,
                        new ResolutionInput("NO_CHANGE", "无需修改", null, null, "规则已明确"),
                        null
                ))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    }

    @Test
    void ownerProxyConfirmForbiddenWhenReporterCanStillSpeak() {
        CurrentUser.set(ownerUser);
        ReviewMemberEntity reporterMember = new ReviewMemberEntity();
        reporterMember.setTaskId("task_1");
        reporterMember.setUserId(reviewerUser.getId());
        reporterMember.setRole("REVIEWER");
        when(memberMapper.selectOne(any())).thenReturn(reporterMember);

        ReviewIssueEntity issue = new ReviewIssueEntity();
        issue.setId("iss_1");
        issue.setTaskId("task_1");
        issue.setDocumentId("doc_1");
        issue.setReporterId(reviewerUser.getId());
        issue.setAssigneeId(editorUser.getId());
        issue.setStatus("AWAITING_CONFIRMATION");
        issue.setRevision(3);
        when(issueMapper.selectById("iss_1")).thenReturn(issue);

        ApiException ex = assertThrows(ApiException.class, () ->
                issueService.executeAction("iss_1", new ReviewIssueActionRequest(
                        3, "CONFIRM", null, "代办理由", null, null
                ))
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.status());
        assertEquals("REVIEW_ACTION_FORBIDDEN", ex.code());
    }

    @Test
    void nonReporterCannotConfirmResolvedEvenIfOwner() {
        CurrentUser.set(ownerUser);

        ReviewIssueEntity issue = new ReviewIssueEntity();
        issue.setId("iss_1");
        issue.setTaskId("task_1");
        issue.setDocumentId("doc_1");
        issue.setDisplayNo(1);
        issue.setOriginRevisionId("rev_1");
        issue.setTitle("8600元审批");
        issue.setReporterId(reviewerUser.getId());
        issue.setAssigneeId(editorUser.getId());
        issue.setStatus("AWAITING_CONFIRMATION");
        issue.setRevision(3);
        when(issueMapper.selectById("iss_1")).thenReturn(issue);

        ApiException ex = assertThrows(ApiException.class, () ->
                issueService.executeAction("iss_1", new ReviewIssueActionRequest(
                        3, "CONFIRM", null, "非提出者尝试确认", null, null
                ))
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.status());
        assertEquals("只有问题提出者才能确认问题已解决", ex.getMessage());

        // Now reporter confirms -> succeeds
        CurrentUser.set(reviewerUser);
        ReviewMemberEntity reporterMember = new ReviewMemberEntity();
        reporterMember.setTaskId("task_1");
        reporterMember.setUserId(reviewerUser.getId());
        reporterMember.setRole("REVIEWER");
        when(memberMapper.selectOne(any())).thenReturn(reporterMember);

        ReviewIssueDetail detail = issueService.executeAction("iss_1", new ReviewIssueActionRequest(
                3, "CONFIRM", null, "提出者确认解决", null, null
        ));
        assertEquals("RESOLVED", issue.getStatus());
        assertEquals(reviewerUser.getId(), issue.getResolvedBy());
    }

    // -------------------------------------------------------------
    // Round Rules Tests
    // -------------------------------------------------------------

    @Test
    void closingRoundPassWhenUnresolvedIssuesExistThrowsBadRequest() {
        CurrentUser.set(ownerUser);
        ReviewRoundEntity round = new ReviewRoundEntity();
        round.setId("rnd_1");
        round.setTaskId("task_1");
        round.setDocumentId("doc_1");
        round.setStatus("OPEN");
        round.setRevision(1);
        when(roundMapper.selectById("rnd_1")).thenReturn(round);

        ReviewIssueEntity unresolvedIssue = new ReviewIssueEntity();
        unresolvedIssue.setId("iss_open");
        unresolvedIssue.setStatus("OPEN");
        when(issueMapper.selectList(any())).thenReturn(List.of(unresolvedIssue));

        ApiException ex = assertThrows(ApiException.class, () ->
                roundService.closeRound("rnd_1", new CloseReviewRoundRequest(
                        1, "PASS", "通过", null
                ))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertTrue(ex.getMessage().contains("未解决问题"));
    }

    // -------------------------------------------------------------
    // Export Formula Injection Defense Test
    // -------------------------------------------------------------

    @Test
    void exportProtectsAgainstCsvFormulaInjection() {
        CurrentUser.set(ownerUser);
        ReviewDocumentEntity doc = new ReviewDocumentEntity();
        doc.setId("doc_1");
        doc.setTaskId("task_1");
        doc.setTitle("测试文档");
        doc.setHeadRevisionId("rev_1");
        when(documentMapper.selectList(any())).thenReturn(List.of(doc));

        ReviewRevisionEntity rev = new ReviewRevisionEntity();
        rev.setId("rev_1");
        rev.setVersionNo(1);
        rev.setContent("# 正文");
        when(revisionMapper.selectById("rev_1")).thenReturn(rev);

        ReviewIssueEntity malIssue = new ReviewIssueEntity();
        malIssue.setId("iss_mal");
        malIssue.setTaskId("task_1");
        malIssue.setDocumentId("doc_1");
        malIssue.setDisplayNo(1);
        malIssue.setTitle("=1+1"); // Excel formula attack
        malIssue.setType("QUESTION");
        malIssue.setSource("ONLINE");
        malIssue.setImpact("NORMAL");
        malIssue.setReporterNameSnapshot("@hacker"); // CSV command injection
        malIssue.setAssigneeId("usr_editor");
        malIssue.setStatus("OPEN");
        malIssue.setCreatedAt(Instant.now());
        malIssue.setUpdatedAt(Instant.now());
        when(issueMapper.selectList(any())).thenReturn(List.of(malIssue));

        byte[] zipBytes = exportService.exportPackage("task_1", null, null, null, null);
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);
    }

    @Test
    void archiveFailsWhenLeftoverOpenIssuesExist() {
        CurrentUser.set(ownerUser);
        ReviewDocumentEntity doc = new ReviewDocumentEntity();
        doc.setId("rev_doc_1");
        doc.setTaskId("task_1");
        doc.setRevision(1);
        when(documentMapper.selectById("rev_doc_1")).thenReturn(doc);
        when(roundMapper.selectCount(any())).thenReturn(0L);

        ReviewIssueEntity leftover = new ReviewIssueEntity();
        leftover.setDisplayNo(3);
        leftover.setTitle("8600元审批阈值");
        leftover.setStatus("OPEN");
        when(issueMapper.selectList(any())).thenReturn(List.of(leftover));

        ApiException ex = assertThrows(ApiException.class, () ->
                documentService.archiveDocument("rev_doc_1", new ArchiveReviewDocumentRequest(1, "归档"))
        );
        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertTrue(ex.getMessage().contains("#3"));
    }

    @Test
    void importDraftRejectsNonGenerateSource() {
        CurrentUser.set(ownerUser);
        SourceEntity source = new SourceEntity();
        source.setId("src_1");
        source.setTaskId("task_1");
        source.setOriginalName("评审补充-补充.txt");
        source.setUpdatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        when(sourceMapper.selectById("src_1")).thenReturn(source);

        ApiException ex = assertThrows(ApiException.class, () ->
                documentService.importDraft("task_1", new ImportDraftReviewDocumentRequest(
                        "src_1", Instant.parse("2026-04-01T00:00:00Z"), "导入标题"))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    }

    @Test
    void relatedIssuesFromAnotherDocumentAreRejected() {
        CurrentUser.set(ownerUser);
        ReviewDocumentEntity doc = new ReviewDocumentEntity();
        doc.setId("rev_doc_1");
        doc.setTaskId("task_1");
        doc.setRevision(1);
        doc.setHeadRevisionId("rev_ver_1");
        when(documentMapper.selectById("rev_doc_1")).thenReturn(doc);

        ReviewRevisionEntity headRev = new ReviewRevisionEntity();
        headRev.setId("rev_ver_1");
        headRev.setDocumentId("rev_doc_1");
        headRev.setContent("# 旧");
        headRev.setVersionNo(1);
        when(revisionMapper.selectById("rev_ver_1")).thenReturn(headRev);

        ReviewIssueEntity foreign = new ReviewIssueEntity();
        foreign.setId("iss_other");
        foreign.setDocumentId("rev_doc_other");
        when(issueMapper.selectById("iss_other")).thenReturn(foreign);

        ApiException ex = assertThrows(ApiException.class, () ->
                documentService.createRevision("rev_doc_1", new CreateReviewRevisionRequest(
                        1, "rev_ver_1", "# 新内容", "关联外文档问题", List.of("iss_other"), null
                ))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertTrue(ex.getMessage().contains("不属于当前文档"));
    }

    @Test
    void markReadForIssueUpdatesUnreadNotifications() {
        notificationService.markReadForIssue("usr_owner", "rev_iss_1");
        verify(notificationMapper, times(1)).update(eq(null), any());
    }

    @Test
    void markTaskLevelNotificationsReadUpdatesNotifications() {
        notificationService.markTaskLevelNotificationsRead("usr_owner", "task_1");
        verify(notificationMapper, times(1)).update(eq(null), any());
    }

    @Test
    void markReadWithEmptyListMarksAllNotificationsForCurrentUser() {
        CurrentUser.set(ownerUser);
        notificationService.markRead(List.of());
        verify(notificationMapper, times(1)).update(eq(null), any());
    }
}
