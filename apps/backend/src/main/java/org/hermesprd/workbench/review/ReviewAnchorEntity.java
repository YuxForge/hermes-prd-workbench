package org.hermesprd.workbench.review;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("prd_review_anchor")
public class ReviewAnchorEntity {
    @TableId
    private String id;
    private String taskId;
    private String documentId;
    private String issueId;
    private String revisionId;
    private String kind;
    private String selectorVersion;
    private String rendererFingerprint;
    private String renderedTextHash;
    private String exactQuote;
    private String prefixText;
    private String suffixText;
    private Integer startOffset;
    private Integer endOffset;
    private String createdBy;
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

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getIssueId() {
        return issueId;
    }

    public void setIssueId(String issueId) {
        this.issueId = issueId;
    }

    public String getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(String revisionId) {
        this.revisionId = revisionId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getSelectorVersion() {
        return selectorVersion;
    }

    public void setSelectorVersion(String selectorVersion) {
        this.selectorVersion = selectorVersion;
    }

    public String getRendererFingerprint() {
        return rendererFingerprint;
    }

    public void setRendererFingerprint(String rendererFingerprint) {
        this.rendererFingerprint = rendererFingerprint;
    }

    public String getRenderedTextHash() {
        return renderedTextHash;
    }

    public void setRenderedTextHash(String renderedTextHash) {
        this.renderedTextHash = renderedTextHash;
    }

    public String getExactQuote() {
        return exactQuote;
    }

    public void setExactQuote(String exactQuote) {
        this.exactQuote = exactQuote;
    }

    public String getPrefixText() {
        return prefixText;
    }

    public void setPrefixText(String prefixText) {
        this.prefixText = prefixText;
    }

    public String getSuffixText() {
        return suffixText;
    }

    public void setSuffixText(String suffixText) {
        this.suffixText = suffixText;
    }

    public Integer getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(Integer startOffset) {
        this.startOffset = startOffset;
    }

    public Integer getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(Integer endOffset) {
        this.endOffset = endOffset;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
