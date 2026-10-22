package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hermesprd.workbench.auth.CurrentUser;
import org.hermesprd.workbench.auth.UserEntity;
import org.hermesprd.workbench.auth.UserMapper;
import org.hermesprd.workbench.review.ReviewApiModels.*;
import org.hermesprd.workbench.task.TaskEntity;
import org.hermesprd.workbench.task.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ReviewQueryService {
    private final TaskMapper taskMapper;
    private final ReviewMemberMapper memberMapper;
    private final ReviewIssueMapper issueMapper;
    private final ReviewNotificationMapper notificationMapper;
    private final ReviewPostMapper postMapper;
    private final ReviewAccessService accessService;
    private final ReviewIssueService issueService;

    public ReviewQueryService(
            TaskMapper taskMapper,
            ReviewMemberMapper memberMapper,
            ReviewIssueMapper issueMapper,
            ReviewNotificationMapper notificationMapper,
            ReviewPostMapper postMapper,
            ReviewAccessService accessService,
            ReviewIssueService issueService
    ) {
        this.taskMapper = taskMapper;
        this.memberMapper = memberMapper;
        this.issueMapper = issueMapper;
        this.notificationMapper = notificationMapper;
        this.postMapper = postMapper;
        this.accessService = accessService;
        this.issueService = issueService;
    }

    @Transactional(readOnly = true)
    public ReviewTaskListResponse getTasks(int page, int pageSize) {
        UserEntity currentUser = CurrentUser.require();
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));

        // Get all task IDs owned by user
        List<TaskEntity> ownedTasks = taskMapper.selectList(
                new LambdaQueryWrapper<TaskEntity>().eq(TaskEntity::getUserId, currentUser.getId())
        );

        // Get all member memberships
        List<ReviewMemberEntity> memberships = memberMapper.selectList(
                new LambdaQueryWrapper<ReviewMemberEntity>().eq(ReviewMemberEntity::getUserId, currentUser.getId())
        );

        Map<String, String> taskIdToRole = new HashMap<>();
        for (TaskEntity t : ownedTasks) {
            taskIdToRole.put(t.getId(), "OWNER");
        }
        for (ReviewMemberEntity m : memberships) {
            if (!taskIdToRole.containsKey(m.getTaskId())) {
                taskIdToRole.put(m.getTaskId(), m.getRole());
            }
        }

        List<ReviewTaskSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, String> entry : taskIdToRole.entrySet()) {
            String taskId = entry.getKey();
            String role = entry.getValue();
            TaskEntity task = taskMapper.selectById(taskId);
            if (task == null) continue;

            // Compute todoCounts
            int assignedToMe = issueMapper.selectCount(
                    new LambdaQueryWrapper<ReviewIssueEntity>()
                            .eq(ReviewIssueEntity::getTaskId, taskId)
                            .eq(ReviewIssueEntity::getAssigneeId, currentUser.getId())
                            .eq(ReviewIssueEntity::getStatus, "OPEN")
            ).intValue();

            boolean userCanSpeak = !"VIEWER".equals(role);
            int awaitingMyConfirm = 0;
            if (userCanSpeak) {
                awaitingMyConfirm = issueMapper.selectCount(
                        new LambdaQueryWrapper<ReviewIssueEntity>()
                                .eq(ReviewIssueEntity::getTaskId, taskId)
                                .eq(ReviewIssueEntity::getReporterId, currentUser.getId())
                                .eq(ReviewIssueEntity::getStatus, "AWAITING_CONFIRMATION")
                ).intValue();
            }

            int awaitingProxyConfirm = 0;
            if ("OWNER".equals(role)) {
                List<ReviewIssueEntity> awaitingIssues = issueMapper.selectList(
                        new LambdaQueryWrapper<ReviewIssueEntity>()
                                .eq(ReviewIssueEntity::getTaskId, taskId)
                                .eq(ReviewIssueEntity::getStatus, "AWAITING_CONFIRMATION")
                );
                for (ReviewIssueEntity issue : awaitingIssues) {
                    if (!accessService.canUserSpeak(taskId, issue.getReporterId())) {
                        awaitingProxyConfirm++;
                    }
                }
            }

            int unreadNtf = notificationMapper.selectCount(
                    new LambdaQueryWrapper<ReviewNotificationEntity>()
                            .eq(ReviewNotificationEntity::getTaskId, taskId)
                            .eq(ReviewNotificationEntity::getRecipientId, currentUser.getId())
                            .isNull(ReviewNotificationEntity::getReadAt)
            ).intValue();

            ReviewTodoCounts todoCounts = new ReviewTodoCounts(
                    assignedToMe,
                    awaitingMyConfirm,
                    awaitingProxyConfirm,
                    unreadNtf
            );

            summaries.add(new ReviewTaskSummary(
                    task.getId(),
                    task.getTitle(),
                    role,
                    todoCounts,
                    task.getCreatedAt(),
                    task.getUpdatedAt()
            ));
        }

        summaries.sort(Comparator.comparing(ReviewTaskSummary::updatedAt).reversed());

        int total = summaries.size();
        int from = Math.min((safePage - 1) * safePageSize, total);
        int to = Math.min(from + safePageSize, total);

        return new ReviewTaskListResponse(summaries.subList(from, to), total, safePage, safePageSize, Instant.now());
    }

    @Transactional(readOnly = true)
    public ReviewIssueListResponse queryIssues(
            String taskId,
            String documentId,
            String roundId,
            String status,
            String type,
            String source,
            String impact,
            String reporterId,
            String assigneeId,
            String keyword,
            Integer displayNo,
            Boolean awaitingResponse,
            Boolean overdue,
            String filterCategory,
            int page,
            int pageSize,
            String sortBy,
            String sortOrder
    ) {
        UserEntity currentUser = CurrentUser.require();

        // Determine accessible task IDs
        Set<String> accessibleTaskIds = new HashSet<>();
        if (taskId != null && !taskId.isBlank()) {
            accessService.requireReadAccess(taskId);
            accessibleTaskIds.add(taskId);
        } else {
            List<TaskEntity> owned = taskMapper.selectList(
                    new LambdaQueryWrapper<TaskEntity>().eq(TaskEntity::getUserId, currentUser.getId())
            );
            for (TaskEntity t : owned) accessibleTaskIds.add(t.getId());
            List<ReviewMemberEntity> mems = memberMapper.selectList(
                    new LambdaQueryWrapper<ReviewMemberEntity>().eq(ReviewMemberEntity::getUserId, currentUser.getId())
            );
            for (ReviewMemberEntity m : mems) accessibleTaskIds.add(m.getTaskId());
        }

        if (accessibleTaskIds.isEmpty()) {
            return new ReviewIssueListResponse(List.of(), 0, page, pageSize, Instant.now(), new ReviewStatistics(0, 0, 0, 0, 0, null));
        }

        LambdaQueryWrapper<ReviewIssueEntity> query = new LambdaQueryWrapper<ReviewIssueEntity>()
                .in(ReviewIssueEntity::getTaskId, accessibleTaskIds);

        if (documentId != null && !documentId.isBlank()) {
            query.eq(ReviewIssueEntity::getDocumentId, documentId);
        }
        if (roundId != null && !roundId.isBlank()) {
            query.eq(ReviewIssueEntity::getRoundId, roundId);
        }
        if (status != null && !status.isBlank()) {
            query.eq(ReviewIssueEntity::getStatus, status);
        }
        if (type != null && !type.isBlank()) {
            query.eq(ReviewIssueEntity::getType, type);
        }
        if (source != null && !source.isBlank()) {
            query.eq(ReviewIssueEntity::getSource, source);
        }
        if (impact != null && !impact.isBlank()) {
            query.eq(ReviewIssueEntity::getImpact, impact);
        }
        if (reporterId != null && !reporterId.isBlank()) {
            query.eq(ReviewIssueEntity::getReporterId, reporterId);
        }
        if (assigneeId != null && !assigneeId.isBlank()) {
            query.eq(ReviewIssueEntity::getAssigneeId, assigneeId);
        }
        if (displayNo != null) {
            query.eq(ReviewIssueEntity::getDisplayNo, displayNo);
        }

        List<ReviewIssueEntity> candidates = issueMapper.selectList(query);

        // In-memory filtering for composite logic: keyword in posts, awaitingResponse, overdue, filterCategory
        Instant now = Instant.now();
        List<ReviewIssueEntity> filtered = new ArrayList<>();

        for (ReviewIssueEntity issue : candidates) {
            boolean isAwaitingResponse = "OPEN".equals(issue.getStatus()) && issue.getLastQuestionSeq() > issue.getLastAnswerSeq();
            boolean isOverdue = issue.getResponseDueAt() != null && now.isAfter(issue.getResponseDueAt()) && isAwaitingResponse;

            if (Boolean.TRUE.equals(awaitingResponse) && !isAwaitingResponse) continue;
            if (Boolean.TRUE.equals(overdue) && !isOverdue) continue;

            if (filterCategory != null && !filterCategory.isBlank()) {
                switch (filterCategory.toUpperCase()) {
                    case "ASSIGNED_TO_ME" -> {
                        if (!currentUser.getId().equals(issue.getAssigneeId()) ||
                                !"OPEN".equals(issue.getStatus())) {
                            continue;
                        }
                    }
                    case "REPORTED_BY_ME" -> {
                        if (!currentUser.getId().equals(issue.getReporterId())) {
                            continue;
                        }
                    }
                    case "AWAITING_MY_CONFIRM" -> {
                        boolean reporterCanSpeak = accessService.canUserSpeak(issue.getTaskId(), issue.getReporterId());
                        if (!"AWAITING_CONFIRMATION".equals(issue.getStatus()) ||
                                !currentUser.getId().equals(issue.getReporterId()) ||
                                !reporterCanSpeak) {
                            continue;
                        }
                    }
                    case "AWAITING_PROXY_CONFIRM" -> {
                        boolean isOwner = accessService.isTaskOwner(issue.getTaskId(), currentUser.getId());
                        boolean reporterCanSpeak = accessService.canUserSpeak(issue.getTaskId(), issue.getReporterId());
                        if (!"AWAITING_CONFIRMATION".equals(issue.getStatus()) || !isOwner || reporterCanSpeak) {
                            continue;
                        }
                    }
                    case "UNRESOLVED" -> {
                        if (!"OPEN".equals(issue.getStatus()) && !"AWAITING_CONFIRMATION".equals(issue.getStatus())) {
                            continue;
                        }
                    }
                }
            }

            // Keyword check
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase();
                boolean matchTitle = issue.getTitle() != null && issue.getTitle().toLowerCase().contains(kw);
                boolean matchBody = issue.getBody() != null && issue.getBody().toLowerCase().contains(kw);
                boolean matchPost = false;
                if (!matchTitle && !matchBody) {
                    Long postMatch = postMapper.selectCount(
                            new LambdaQueryWrapper<ReviewPostEntity>()
                                    .eq(ReviewPostEntity::getIssueId, issue.getId())
                                    .like(ReviewPostEntity::getBody, keyword)
                    );
                    matchPost = postMatch > 0;
                }
                if (!matchTitle && !matchBody && !matchPost) {
                    continue;
                }
            }

            filtered.add(issue);
        }

        // Sorting
        Comparator<ReviewIssueEntity> comp;
        if ("displayNo".equalsIgnoreCase(sortBy)) {
            comp = Comparator.comparing(ReviewIssueEntity::getDisplayNo);
        } else if ("createdAt".equalsIgnoreCase(sortBy)) {
            comp = Comparator.comparing(ReviewIssueEntity::getCreatedAt);
        } else if ("responseDueAt".equalsIgnoreCase(sortBy)) {
            comp = Comparator.comparing(ReviewIssueEntity::getResponseDueAt, Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            comp = Comparator.comparing(ReviewIssueEntity::getUpdatedAt);
        }

        if (!"ASC".equalsIgnoreCase(sortOrder)) {
            comp = comp.reversed();
        }
        filtered.sort(comp);

        return issueService.buildIssueListResponse(filtered, page, pageSize);
    }
}
