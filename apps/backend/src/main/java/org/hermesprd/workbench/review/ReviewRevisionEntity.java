package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("prd_review_revision")
public class ReviewRevisionEntity {
    @TableId
    private String id;
    private String taskId;
    private String documentId;
    private Integer versionNo;
    private String parentRevisionId;
    private String content;
    private String contentHash;
    private String summary;
    private String changeType;
    private String createdBy;
    private String authorNameSnapshot;
    private Instant createdAt;
    private String originKind;
    private String originRef;

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

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getParentRevisionId() {
        return parentRevisionId;
    }

    public void setParentRevisionId(String parentRevisionId) {
        this.parentRevisionId = parentRevisionId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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

    public String getOriginKind() {
        return originKind;
    }

    public void setOriginKind(String originKind) {
        this.originKind = originKind;
    }

    public String getOriginRef() {
        return originRef;
    }

    public void setOriginRef(String originRef) {
        this.originRef = originRef;
    }
}
