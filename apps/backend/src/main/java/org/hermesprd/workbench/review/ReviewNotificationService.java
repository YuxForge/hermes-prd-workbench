package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.hermesprd.workbench.auth.CurrentUser;
import org.hermesprd.workbench.auth.UserEntity;
import org.hermesprd.workbench.auth.UserMapper;
import org.hermesprd.workbench.review.ReviewApiModels.MarkReviewNotificationsReadRequest;
import org.hermesprd.workbench.review.ReviewApiModels.ReviewNotificationItem;
import org.hermesprd.workbench.review.ReviewApiModels.ReviewNotificationListResponse;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.Ids;
import org.hermesprd.workbench.task.TaskEntity;
import org.hermesprd.workbench.task.TaskMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReviewNotificationService {
    private final ReviewNotificationMapper notificationMapper;
    private final ReviewEventMapper eventMapper;
    private final ReviewIssueWatchMapper watchMapper;
    private final ReviewMemberMapper memberMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final ReviewDocumentMapper documentMapper;
    private final ReviewIssueMapper issueMapper;

    public ReviewNotificationService(
            ReviewNotificationMapper notificationMapper,
            ReviewEventMapper eventMapper,
            ReviewIssueWatchMapper watchMapper,
            ReviewMemberMapper memberMapper,
            TaskMapper taskMapper,
            UserMapper userMapper,
            ReviewDocumentMapper documentMapper,
            ReviewIssueMapper issueMapper
    ) {
        this.notificationMapper = notificationMapper;
        this.eventMapper = eventMapper;
        this.watchMapper = watchMapper;
        this.memberMapper = memberMapper;
        this.taskMapper = taskMapper;
        this.userMapper = userMapper;
        this.documentMapper = documentMapper;
        this.issueMapper = issueMapper;
    }

    @Transactional
    public void recordEventAndNotify(
            String taskId,
            String documentId,
            String issueId,
            Integer issueSeq,
            String eventType,
            String objectId,
            String actorId,
            String actorNameSnapshot,
            String fromState,
            String toState,
            String metadataJson,
            List<String> targetRecipients
    ) {
        Instant now = Instant.now();
        ReviewEventEntity event = new ReviewEventEntity();
        event.setId(Ids.next("rev_evt_"));
        event.setTaskId(taskId);
        event.setDocumentId(documentId);
        event.setIssueId(issueId);
        event.setIssueSeq(issueSeq);
        event.setEventType(eventType);
        event.setObjectId(objectId);
        event.setActorId(actorId);
        event.setActorNameSnapshot(actorNameSnapshot);
        event.setFromState(fromState);
        event.setToState(toState);
        event.setMetadata(metadataJson);
        event.setCreatedAt(now);
        eventMapper.insert(event);

        if (targetRecipients != null && !targetRecipients.isEmpty()) {
            Set<String> uniqueRecipients = new HashSet<>(targetRecipients);
            uniqueRecipients.remove(actorId); // Never notify oneself

            for (String recipientId : uniqueRecipients) {
                ReviewNotificationEntity notification = new ReviewNotificationEntity();
                notification.setId(Ids.next("rev_ntf_"));
                notification.setTaskId(taskId);
                notification.setRecipientId(recipientId);
                notification.setEventId(event.getId());
                notification.setDocumentId(documentId);
                notification.setIssueId(issueId);
                notification.setCreatedAt(now);
                notificationMapper.insert(notification);
            }
        }
    }

    public List<String> getAllSpaceMembers(String taskId) {
        List<String> userIds = new ArrayList<>();
        TaskEntity task = taskMapper.selectById(taskId);
        if (task != null && task.getUserId() != null) {
            userIds.add(task.getUserId());
        }
        List<ReviewMemberEntity> members = memberMapper.selectList(
                new LambdaQueryWrapper<ReviewMemberEntity>().eq(ReviewMemberEntity::getTaskId, taskId)
        );
        for (ReviewMemberEntity member : members) {
            userIds.add(member.getUserId());
        }
        return userIds;
    }

    public List<String> getIssueWatchers(String issueId) {
        List<ReviewIssueWatchEntity> watches = watchMapper.selectList(
                new LambdaQueryWrapper<ReviewIssueWatchEntity>().eq(ReviewIssueWatchEntity::getIssueId, issueId)
        );
        List<String> watchers = new ArrayList<>();
        for (ReviewIssueWatchEntity w : watches) {
            watchers.add(w.getUserId());
        }
        return watchers;
    }

    @Transactional(readOnly = true)
    public ReviewNotificationListResponse getNotifications(boolean unreadOnly, int page, int pageSize) {
        UserEntity currentUser = CurrentUser.require();
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));

        // Get all notifications for current user
        LambdaQueryWrapper<ReviewNotificationEntity> query = new LambdaQueryWrapper<ReviewNotificationEntity>()
                .eq(ReviewNotificationEntity::getRecipientId, currentUser.getId())
                .orderByDesc(ReviewNotificationEntity::getCreatedAt);

        if (unreadOnly) {
            query.isNull(ReviewNotificationEntity::getReadAt);
        }

        List<ReviewNotificationEntity> allNotifications = notificationMapper.selectList(query);

        // Filter out notifications for tasks the user no longer has access to
        List<ReviewNotificationItem> validItems = new ArrayList<>();
        for (ReviewNotificationEntity ntf : allNotifications) {
            TaskEntity task = taskMapper.selectById(ntf.getTaskId());
            if (task == null) continue;
            boolean hasAccess = task.getUserId().equals(currentUser.getId()) ||
                    memberMapper.selectCount(new LambdaQueryWrapper<ReviewMemberEntity>()
                            .eq(ReviewMemberEntity::getTaskId, ntf.getTaskId())
                            .eq(ReviewMemberEntity::getUserId, currentUser.getId())) > 0;
            if (!hasAccess) continue;

            ReviewEventEntity event = eventMapper.selectById(ntf.getEventId());
            String actorName = event != null ? event.getActorNameSnapshot() : "未知用户";
            String targetTitle = "协作评审";
            if (ntf.getIssueId() != null) {
                ReviewIssueEntity issue = issueMapper.selectById(ntf.getIssueId());
                if (issue != null) {
                    targetTitle = "#" + issue.getDisplayNo() + " " + issue.getTitle();
                }
            } else if (ntf.getDocumentId() != null) {
                ReviewDocumentEntity doc = documentMapper.selectById(ntf.getDocumentId());
                if (doc != null) {
                    targetTitle = doc.getTitle();
                }
            } else {
                targetTitle = task.getTitle();
            }

            validItems.add(new ReviewNotificationItem(
                    ntf.getId(),
                    ntf.getTaskId(),
                    ntf.getDocumentId(),
                    ntf.getIssueId(),
                    ntf.getEventId(),
                    event != null ? event.getEventType() : "UNKNOWN",
                    actorName,
                    targetTitle,
                    ntf.getCreatedAt(),
                    ntf.getReadAt()
            ));
        }

        int total = validItems.size();
        int unreadCount = (int) validItems.stream().filter(item -> item.readAt() == null).count();

        int fromIndex = Math.min((safePage - 1) * safePageSize, total);
        int toIndex = Math.min(fromIndex + safePageSize, total);
        List<ReviewNotificationItem> pagedItems = validItems.subList(fromIndex, toIndex);

        return new ReviewNotificationListResponse(pagedItems, total, unreadCount, safePage, safePageSize);
    }

    @Transactional
    public void markRead(List<String> notificationIds) {
        UserEntity currentUser = CurrentUser.require();
        Instant now = Instant.now();

        UpdateWrapper<ReviewNotificationEntity> updateWrapper = new UpdateWrapper<ReviewNotificationEntity>()
                .set("read_at", now)
                .eq("recipient_id", currentUser.getId())
                .isNull("read_at");

        if (notificationIds != null && !notificationIds.isEmpty()) {
            updateWrapper.in("id", notificationIds);
        }

        notificationMapper.update(null, updateWrapper);
    }

    @Transactional
    public void markReadForIssue(String userId, String issueId) {
        if (userId == null || issueId == null) return;
        Instant now = Instant.now();
        notificationMapper.update(
                null,
                new UpdateWrapper<ReviewNotificationEntity>()
                        .set("read_at", now)
                        .eq("recipient_id", userId)
                        .eq("issue_id", issueId)
                        .isNull("read_at")
        );
    }

    @Transactional
    public void markTaskLevelNotificationsRead(String userId, String taskId) {
        if (userId == null || taskId == null) return;
        Instant now = Instant.now();
        notificationMapper.update(
                null,
                new UpdateWrapper<ReviewNotificationEntity>()
                        .set("read_at", now)
                        .eq("recipient_id", userId)
                        .eq("task_id", taskId)
                        .isNull("issue_id")
                        .isNull("read_at")
        );
    }
}
