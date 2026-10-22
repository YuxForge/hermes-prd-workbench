package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("prd_review_post")
public class ReviewPostEntity {
    @TableId
    private String id;
    private String taskId;
    private String issueId;
    private Integer issueSeq;
    private String kind;
    private String body;
    private String replyToPostId;
    private String correctsPostId;
    private String authorId;
    private String authorNameSnapshot;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public Integer getIssueSeq() {
        return issueSeq;
    }

    public void setIssueSeq(Integer issueSeq) {
        this.issueSeq = issueSeq;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getReplyToPostId() {
        return replyToPostId;
    }

    public void setReplyToPostId(String replyToPostId) {
        this.replyToPostId = replyToPostId;
    }

    public String getCorrectsPostId() {
        return correctsPostId;
    }

    public void setCorrectsPostId(String correctsPostId) {
        this.correctsPostId = correctsPostId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getAuthorNameSnapshot() {
        return authorNameSnapshot;
    }

    public void setAuthorNameSnapshot(String authorNameSnapshot) {
        this.authorNameSnapshot = authorNameSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
