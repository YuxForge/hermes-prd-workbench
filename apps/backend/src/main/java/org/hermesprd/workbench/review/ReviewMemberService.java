package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.auth.CurrentUser;
import org.hermesprd.workbench.auth.UserEntity;
import org.hermesprd.workbench.auth.UserMapper;
import org.hermesprd.workbench.review.ReviewApiModels.AddReviewMemberRequest;
import org.hermesprd.workbench.review.ReviewApiModels.MemberReassignmentItem;
import org.hermesprd.workbench.review.ReviewApiModels.RemoveReviewMemberRequest;
import org.hermesprd.workbench.review.ReviewApiModels.ReviewMemberResponse;
import org.hermesprd.workbench.review.ReviewApiModels.ReviewCandidateUserResponse;
import org.hermesprd.workbench.review.ReviewApiModels.UpdateReviewMemberRequest;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.task.TaskEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewMemberService {
    private final ReviewAccessService accessService;
    private final ReviewMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final ReviewIssueMapper issueMapper;
    private final ReviewIssueWatchMapper watchMapper;
    private final ReviewNotificationService notificationService;
    private final ObjectMapper objectMapper;

    public ReviewMemberService(
            ReviewAccessService accessService,
            ReviewMemberMapper memberMapper,
            UserMapper userMapper,
            ReviewIssueMapper issueMapper,
            ReviewIssueWatchMapper watchMapper,
            ReviewNotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.accessService = accessService;
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
        this.issueMapper = issueMapper;
        this.watchMapper = watchMapper;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ReviewMemberResponse> getMembers(String taskId) {
        accessService.requireReadAccess(taskId);
        TaskEntity task = accessService.requireTask(taskId);

        List<ReviewMemberResponse> result = new ArrayList<>();
        // Add owner first
        UserEntity owner = userMapper.selectById(task.getUserId());
        if (owner != null) {
            result.add(new ReviewMemberResponse(
                    owner.getId(),
                    owner.getUsername(),
                    owner.getDisplayName(),
                    "OWNER",
                    1,
                    owner.getId(),
                    task.getCreatedAt()
            ));
        }

        // Add explicit members
        List<ReviewMemberEntity> members = memberMapper.selectList(
                new LambdaQueryWrapper<ReviewMemberEntity>()
                        .eq(ReviewMemberEntity::getTaskId, taskId)
                        .orderByAsc(ReviewMemberEntity::getCreatedAt)
        );
        for (ReviewMemberEntity member : members) {
            UserEntity u = userMapper.selectById(member.getUserId());
            if (u != null) {
                result.add(new ReviewMemberResponse(
                        u.getId(),
                        u.getUsername(),
                        u.getDisplayName(),
                        member.getRole(),
                        member.getRevision(),
                        member.getCreatedBy(),
                        member.getCreatedAt()
                ));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ReviewCandidateUserResponse> searchCandidateUsers(String taskId, String query) {
        accessService.requireReadAccess(taskId);
        TaskEntity task = accessService.requireTask(taskId);

        Set<String> existingUserIds = new HashSet<>();
        existingUserIds.add(task.getUserId());
        List<ReviewMemberEntity> members = memberMapper.selectList(
                new LambdaQueryWrapper<ReviewMemberEntity>().eq(ReviewMemberEntity::getTaskId, taskId)
        );
        for (ReviewMemberEntity m : members) {
            existingUserIds.add(m.getUserId());
        }

        String q = query != null ? query.trim() : "";
        LambdaQueryWrapper<UserEntity> qw = new LambdaQueryWrapper<UserEntity>()
                .orderByAsc(UserEntity::getUsername)
                .last("LIMIT 20");
        if (!q.isEmpty()) {
            qw.and(w -> w.like(UserEntity::getUsername, q)
                    .or()
                    .like(UserEntity::getDisplayName, q));
        }

        List<UserEntity> users = userMapper.selectList(qw);
        return users.stream()
                .filter(u -> !existingUserIds.contains(u.getId()))
                .map(u -> new ReviewCandidateUserResponse(u.getId(), u.getUsername(), u.getDisplayName()))
                .toList();
    }

    @Transactional
    public ReviewMemberResponse addMember(String taskId, AddReviewMemberRequest request) {
        accessService.requireOwner(taskId);
        TaskEntity task = accessService.requireTask(taskId);
        UserEntity currentUser = CurrentUser.require();

        String normalizedUsername = request.username().trim().toLowerCase();
        UserEntity targetUser = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, normalizedUsername)
        );
        if (targetUser == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "用户不存在: " + request.username());
        }

        if (targetUser.getId().equals(task.getUserId())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT", "该用户已是需求所有者，无需重复添加");
        }

        memberMapper.lockByTaskId(taskId);
        ReviewMemberEntity existing = memberMapper.selectOne(
                new LambdaQueryWrapper<ReviewMemberEntity>()
                        .eq(ReviewMemberEntity::getTaskId, taskId)
                        .eq(ReviewMemberEntity::getUserId, targetUser.getId())
        );
        if (existing != null) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT", "该用户已是空间成员");
        }

        long count = memberMapper.selectCount(new LambdaQueryWrapper<ReviewMemberEntity>().eq(ReviewMemberEntity::getTaskId, taskId));
        if (count >= 100) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT", "每个需求空间最多 100 名显式成员");
        }

        String role = request.role().trim().toUpperCase();
        if (!List.of("EDITOR", "REVIEWER", "VIEWER").contains(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "角色必须为 EDITOR, REVIEWER 或 VIEWER");
        }

        Instant now = Instant.now();
        ReviewMemberEntity member = new ReviewMemberEntity();
        member.setTaskId(taskId);
        member.setUserId(targetUser.getId());
        member.setRole(role);
        member.setRevision(1);
        member.setCreatedBy(currentUser.getId());
        member.setCreatedAt(now);
        memberMapper.insert(member);

        Map<String, Object> meta = Map.of(
                "username", targetUser.getUsername(),
                "role", role
        );
        String metaJson = toJson(meta);

        notificationService.recordEventAndNotify(
                taskId,
                null,
                null,
                null,
                "MEMBER_ADDED",
                targetUser.getId(),
                currentUser.getId(),
                currentUser.getDisplayName(),
                null,
                role,
                metaJson,
                List.of(targetUser.getId())
        );

        return new ReviewMemberResponse(
                targetUser.getId(),
                targetUser.getUsername(),
                targetUser.getDisplayName(),
                role,
                1,
                currentUser.getId(),
                now
        );
    }

    @Transactional
    public ReviewMemberResponse updateMember(String taskId, String userId, UpdateReviewMemberRequest request) {
        accessService.requireOwner(taskId);
        TaskEntity task = accessService.requireTask(taskId);
        UserEntity currentUser = CurrentUser.require();

        if (userId.equals(task.getUserId())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT", "不能修改需求所有者的角色");
        }

        ReviewMemberEntity member = memberMapper.lockByTaskAndUser(taskId, userId);
        if (member == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "成员不存在");
        }

        if (!member.getRevision().equals(request.expectedRevision())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "成员信息已被修改，请刷新后重试");
        }

        String newRole = request.role().trim().toUpperCase();
        if (!List.of("EDITOR", "REVIEWER", "VIEWER").contains(newRole)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "角色必须为 EDITOR, REVIEWER 或 VIEWER");
        }

        String oldRole = member.getRole();

        // If downgrading from EDITOR to non-editor, reassign unresolved issues
        if ("EDITOR".equals(oldRole) && !"EDITOR".equals(newRole)) {
            reassignIssuesIfNecessary(taskId, userId, request.reassignment());
        }

        // If downgrading to VIEWER, delete user's watches in this task
        if ("VIEWER".equals(newRole)) {
            watchMapper.delete(
                    new LambdaQueryWrapper<ReviewIssueWatchEntity>()
                            .eq(ReviewIssueWatchEntity::getTaskId, taskId)
                            .eq(ReviewIssueWatchEntity::getUserId, userId)
        );
        }

        member.setRole(newRole);
        member.setRevision(member.getRevision() + 1);
        memberMapper.update(member, new LambdaQueryWrapper<ReviewMemberEntity>()
                .eq(ReviewMemberEntity::getTaskId, taskId)
                .eq(ReviewMemberEntity::getUserId, userId)
                .eq(ReviewMemberEntity::getRevision, request.expectedRevision()));

        UserEntity targetUser = userMapper.selectById(userId);
        return new ReviewMemberResponse(
                userId,
                targetUser != null ? targetUser.getUsername() : "",
                targetUser != null ? targetUser.getDisplayName() : "",
                newRole,
                member.getRevision(),
                member.getCreatedBy(),
                member.getCreatedAt()
        );
    }

    @Transactional
    public void removeMember(String taskId, String userId, RemoveReviewMemberRequest request) {
        accessService.requireOwner(taskId);
        TaskEntity task = accessService.requireTask(taskId);

        if (userId.equals(task.getUserId())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT", "不能移除需求所有者");
        }

        ReviewMemberEntity member = memberMapper.lockByTaskAndUser(taskId, userId);
        if (member == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "成员不存在");
        }

        if (!member.getRevision().equals(request.expectedRevision())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_CHANGED", "成员信息已被修改，请刷新后重试");
        }

        // Must reassign unresolved issues where this user is assignee
        reassignIssuesIfNecessary(taskId, userId, request.reassignment());

        // Delete watches
        watchMapper.delete(
                new LambdaQueryWrapper<ReviewIssueWatchEntity>()
                        .eq(ReviewIssueWatchEntity::getTaskId, taskId)
                        .eq(ReviewIssueWatchEntity::getUserId, userId)
        );

        memberMapper.delete(
                new LambdaQueryWrapper<ReviewMemberEntity>()
                        .eq(ReviewMemberEntity::getTaskId, taskId)
                        .eq(ReviewMemberEntity::getUserId, userId)
        );
    }

    private void reassignIssuesIfNecessary(String taskId, String userId, List<MemberReassignmentItem> reassignment) {
        List<ReviewIssueEntity> assignedIssues = issueMapper.selectList(
                new LambdaQueryWrapper<ReviewIssueEntity>()
                        .eq(ReviewIssueEntity::getTaskId, taskId)
                        .eq(ReviewIssueEntity::getAssigneeId, userId)
                        .in(ReviewIssueEntity::getStatus, List.of("OPEN", "AWAITING_CONFIRMATION"))
        );

        if (assignedIssues.isEmpty()) {
            return;
        }

        Map<String, String> reassignmentMap = new HashMap<>();
        if (reassignment != null) {
            for (MemberReassignmentItem item : reassignment) {
                reassignmentMap.put(item.issueId(), item.newAssigneeId());
            }
        }

        for (ReviewIssueEntity issue : assignedIssues) {
            String newAssigneeId = reassignmentMap.get(issue.getId());
            if (newAssigneeId == null || newAssigneeId.isBlank()) {
                throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT",
                        "成员还有未终结负责问题 #" + issue.getDisplayNo() + "，必须先指定新责任人");
            }
            if (newAssigneeId.equals(issue.getReporterId())) {
                throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT",
                        "问题 #" + issue.getDisplayNo() + " 的新责任人不能是该问题提出者");
            }
            ReviewAccessService.Role targetRole = accessService.getUserRole(taskId, newAssigneeId);
            if (targetRole == null || !targetRole.canEdit()) {
                throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFLICT",
                        "新责任人必须是需求所有者或编辑者 EDITOR");
            }

            // Update issue assignee
            issue.setAssigneeId(newAssigneeId);
            issue.setRevision(issue.getRevision() + 1);
            issue.setLastQuestionSeq(issue.getRevision()); // new question seq to require new response
            if ("AWAITING_CONFIRMATION".equals(issue.getStatus())) {
                issue.setStatus("OPEN");
            }
            issue.setUpdatedAt(Instant.now());
            issueMapper.updateById(issue);

            // Auto watch for new assignee
            ReviewIssueWatchEntity existingWatch = watchMapper.selectOne(
                    new LambdaQueryWrapper<ReviewIssueWatchEntity>()
                            .eq(ReviewIssueWatchEntity::getIssueId, issue.getId())
                            .eq(ReviewIssueWatchEntity::getUserId, newAssigneeId)
            );
            if (existingWatch == null) {
                ReviewIssueWatchEntity watch = new ReviewIssueWatchEntity();
                watch.setTaskId(taskId);
                watch.setIssueId(issue.getId());
                watch.setUserId(newAssigneeId);
                watch.setCreatedAt(Instant.now());
                watchMapper.insert(watch);
            }
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
