package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("prd_review_issue_watch")
public class ReviewIssueWatchEntity {
    private String taskId;
    private String issueId;
    private String userId;
    private Instant createdAt;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getIssueId() {
        return issueId;
    }

    public void setIssueId(String issueId) {
        this.issueId = issueId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
