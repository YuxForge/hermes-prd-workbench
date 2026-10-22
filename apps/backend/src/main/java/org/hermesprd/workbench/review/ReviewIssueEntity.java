package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("prd_review_issue")
public class ReviewIssueEntity {
    @TableId
    private String id;
    private String taskId;
    private String documentId;
    private Integer displayNo;
    private String roundId;
    private String originRevisionId;
    private String title;
    private String body;
    private String type;
    private String source;
    private String impact;
    private String reporterId;
    private String reporterNameSnapshot;
    private Instant reportedAtNote;
    private String reporterNote;
    private String assigneeId;
    private Instant responseDueAt;
    private String status;
    private Integer revision;
    private Integer lastQuestionSeq;
    private Integer lastAnswerSeq;
    private Instant firstAnswerAt;
    private String proposedResolution;
    private String resolvedBy;
    private Instant resolvedAt;
    private Instant createdAt;
    private Instant updatedAt;

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

    public Integer getDisplayNo() {
        return displayNo;
    }

    public void setDisplayNo(Integer displayNo) {
        this.displayNo = displayNo;
    }

    public String getRoundId() {
        return roundId;
    }

    public void setRoundId(String roundId) {
        this.roundId = roundId;
    }

    public String getOriginRevisionId() {
        return originRevisionId;
    }

    public void setOriginRevisionId(String originRevisionId) {
        this.originRevisionId = originRevisionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getImpact() {
        return impact;
    }

    public void setImpact(String impact) {
        this.impact = impact;
    }

    public String getReporterId() {
        return reporterId;
    }

    public void setReporterId(String reporterId) {
        this.reporterId = reporterId;
    }

    public String getReporterNameSnapshot() {
        return reporterNameSnapshot;
    }

    public void setReporterNameSnapshot(String reporterNameSnapshot) {
        this.reporterNameSnapshot = reporterNameSnapshot;
    }

    public Instant getReportedAtNote() {
        return reportedAtNote;
    }

    public void setReportedAtNote(Instant reportedAtNote) {
        this.reportedAtNote = reportedAtNote;
    }

    public String getReporterNote() {
        return reporterNote;
    }

    public void setReporterNote(String reporterNote) {
        this.reporterNote = reporterNote;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(String assigneeId) {
        this.assigneeId = assigneeId;
    }

    public Instant getResponseDueAt() {
        return responseDueAt;
    }

    public void setResponseDueAt(Instant responseDueAt) {
        this.responseDueAt = responseDueAt;
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

    public Integer getLastQuestionSeq() {
        return lastQuestionSeq;
    }

    public void setLastQuestionSeq(Integer lastQuestionSeq) {
        this.lastQuestionSeq = lastQuestionSeq;
    }

    public Integer getLastAnswerSeq() {
        return lastAnswerSeq;
    }

    public void setLastAnswerSeq(Integer lastAnswerSeq) {
        this.lastAnswerSeq = lastAnswerSeq;
    }

    public Instant getFirstAnswerAt() {
        return firstAnswerAt;
    }

    public void setFirstAnswerAt(Instant firstAnswerAt) {
        this.firstAnswerAt = firstAnswerAt;
    }

    public String getProposedResolution() {
        return proposedResolution;
    }

    public void setProposedResolution(String proposedResolution) {
        this.proposedResolution = proposedResolution;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
