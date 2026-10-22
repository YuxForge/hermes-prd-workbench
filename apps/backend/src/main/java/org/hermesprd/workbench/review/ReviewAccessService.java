package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hermesprd.workbench.auth.CurrentUser;
import org.hermesprd.workbench.auth.UserEntity;
import org.hermesprd.workbench.auth.UserMapper;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.task.TaskEntity;
import org.hermesprd.workbench.task.TaskMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReviewAccessService {
    public enum Role {
        OWNER, EDITOR, REVIEWER, VIEWER;

        public boolean canSpeak() {
            return this == OWNER || this == EDITOR || this == REVIEWER;
        }

        public boolean canEdit() {
            return this == OWNER || this == EDITOR;
        }

        public boolean isOwner() {
            return this == OWNER;
        }
    }

    private final TaskMapper taskMapper;
    private final ReviewMemberMapper memberMapper;
    private final UserMapper userMapper;

    public ReviewAccessService(TaskMapper taskMapper, ReviewMemberMapper memberMapper, UserMapper userMapper) {
        this.taskMapper = taskMapper;
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
    }

    public TaskEntity requireTask(String taskId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "需求空间不存在");
        }
        return task;
    }

    public Role getUserRole(String taskId, String userId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            return null;
        }
        if (userId.equals(task.getUserId())) {
            return Role.OWNER;
        }
        ReviewMemberEntity member = memberMapper.selectOne(
                new LambdaQueryWrapper<ReviewMemberEntity>()
                        .eq(ReviewMemberEntity::getTaskId, taskId)
                        .eq(ReviewMemberEntity::getUserId, userId)
        );
        if (member == null) {
            return null;
        }
        try {
            return Role.valueOf(member.getRole());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Role requireReadAccess(String taskId) {
        UserEntity user = CurrentUser.require();
        Role role = getUserRole(taskId, user.getId());
        if (role == null) {
            // ADMIN role does NOT bypass review permissions, and non-members receive 404 to hide existence
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "需求空间不存在或无权访问");
        }
        return role;
    }

    public Role requireSpeakAccess(String taskId) {
        Role role = requireReadAccess(taskId);
        if (!role.canSpeak()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REVIEW_ACTION_FORBIDDEN", "只读成员不可提问或讨论");
        }
        return role;
    }

    public Role requireEditAccess(String taskId) {
        Role role = requireReadAccess(taskId);
        if (!role.canEdit()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REVIEW_ACTION_FORBIDDEN", "无编辑权限");
        }
        return role;
    }

    public Role requireOwner(String taskId) {
        Role role = requireReadAccess(taskId);
        if (!role.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REVIEW_ACTION_FORBIDDEN", "仅需求所有者可执行此操作");
        }
        return role;
    }

    public boolean canUserSpeak(String taskId, String userId) {
        Role role = getUserRole(taskId, userId);
        return role != null && role.canSpeak();
    }

    public boolean isTaskOwner(String taskId, String userId) {
        TaskEntity task = taskMapper.selectById(taskId);
        return task != null && userId.equals(task.getUserId());
    }

    public UserEntity requireUser(String userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "用户不存在");
        }
        return user;
    }
}
