package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("prd_review_round")
public class ReviewRoundEntity {
    @TableId
    private String id;
    private String taskId;
    private String documentId;
    private String baselineRevisionId;
    private String status;
    private Integer revision;
    private String instruction;
    private Instant dueAt;
    private String conclusion;
    private String summary;
    private String closeSnapshot;
    private String createdBy;
    private String closedBy;
    private Instant createdAt;
    private Instant closedAt;
    @TableField(exist = false)
    private String openDocumentId;

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

    public String getBaselineRevisionId() {
        return baselineRevisionId;
    }

    public void setBaselineRevisionId(String baselineRevisionId) {
        this.baselineRevisionId = baselineRevisionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getCloseSnapshot() {
        return closeSnapshot;
    }

    public void setCloseSnapshot(String closeSnapshot) {
        this.closeSnapshot = closeSnapshot;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public String getOpenDocumentId() {
        return openDocumentId;
    }

    public void setOpenDocumentId(String openDocumentId) {
        this.openDocumentId = openDocumentId;
    }
}
