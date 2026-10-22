-- Online requirement review (collaborative review) schema for MySQL 8.4+.
-- 11 tables with composite FKs, checks, restricted deletes, and immutable revision history.

CREATE TABLE prd_review_member (
    task_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    revision INT NOT NULL DEFAULT 1,
    created_by VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id, user_id),
    KEY idx_review_member_user_task (user_id, task_id),
    CONSTRAINT fk_review_member_task FOREIGN KEY (task_id) REFERENCES prd_task (id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_member_user FOREIGN KEY (user_id) REFERENCES prd_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_member_creator FOREIGN KEY (created_by) REFERENCES prd_user (id) ON DELETE RESTRICT,
    CONSTRAINT chk_review_member_role CHECK (role IN ('EDITOR', 'REVIEWER', 'VIEWER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prd_review_document (
    id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    head_revision_id VARCHAR(64) NULL,
    revision INT NOT NULL DEFAULT 1,
    next_issue_no INT NOT NULL DEFAULT 1,
    archived_at DATETIME(3) NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_doc_task_id (task_id, id),
    KEY idx_review_doc_task_updated (task_id, updated_at, id),
    CONSTRAINT fk_review_doc_task FOREIGN KEY (task_id) REFERENCES prd_task (id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_doc_creator FOREIGN KEY (created_by) REFERENCES prd_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prd_review_revision (
    id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    version_no INT NOT NULL,
    parent_revision_id VARCHAR(64) NULL,
    content LONGTEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    author_name_snapshot VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    origin_kind VARCHAR(32) NOT NULL,
    origin_ref JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_rev_doc_ver (document_id, version_no),
    UNIQUE KEY uk_review_rev_task_doc_id (task_id, document_id, id),
    KEY idx_review_rev_doc_time (document_id, created_at, id),
    CONSTRAINT fk_review_rev_task_doc FOREIGN KEY (task_id, document_id) REFERENCES prd_review_document (task_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_rev_creator FOREIGN KEY (created_by) REFERENCES prd_user (id) ON DELETE RESTRICT,
    CONSTRAINT chk_review_rev_change_type CHECK (change_type IN ('INITIAL', 'EDIT', 'RESTORE')),
    CONSTRAINT chk_review_rev_origin_kind CHECK (origin_kind IN ('MANUAL', 'IMPORT_DRAFT', 'RESTORE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE prd_review_document
    ADD CONSTRAINT fk_review_doc_head_rev FOREIGN KEY (task_id, id, head_revision_id) REFERENCES prd_review_revision (task_id, document_id, id) ON DELETE RESTRICT;

CREATE TABLE prd_review_round (
    id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    baseline_revision_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    revision INT NOT NULL DEFAULT 1,
    instruction VARCHAR(2000) NULL,
    due_at DATETIME(3) NULL,
    conclusion VARCHAR(32) NULL,
    summary VARCHAR(2000) NULL,
    close_snapshot JSON NULL,
    created_by VARCHAR(64) NOT NULL,
    closed_by VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    closed_at DATETIME(3) NULL,
    open_document_id VARCHAR(64) GENERATED ALWAYS AS (IF(status = 'OPEN', document_id, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_round_task_doc_id (task_id, document_id, id),
    UNIQUE KEY uk_review_round_open_doc (open_document_id),
    KEY idx_review_round_task_doc_time (task_id, document_id, created_at, id),
    CONSTRAINT fk_review_round_task_doc FOREIGN KEY (task_id, document_id) REFERENCES prd_review_document (task_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_round_baseline FOREIGN KEY (task_id, document_id, baseline_revision_id) REFERENCES prd_review_revision (task_id, document_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_round_creator FOREIGN KEY (created_by) REFERENCES prd_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_round_closer FOREIGN KEY (closed_by) REFERENCES prd_user (id) ON DELETE RESTRICT,
    CONSTRAINT chk_review_round_status CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED')),
    CONSTRAINT chk_review_round_conclusion CHECK (conclusion IS NULL OR conclusion IN ('PASS', 'PASS_WITH_RESIDUAL', 'REJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prd_review_issue (
    id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    display_no INT NOT NULL,
    round_id VARCHAR(64) NULL,
    origin_revision_id VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    type VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    impact VARCHAR(32) NOT NULL,
    reporter_id VARCHAR(64) NOT NULL,
    reporter_name_snapshot VARCHAR(64) NOT NULL,
    reported_at_note DATETIME(3) NULL,
    reporter_note VARCHAR(255) NULL,
    assignee_id VARCHAR(64) NOT NULL,
    response_due_at DATETIME(3) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    revision INT NOT NULL DEFAULT 1,
    last_question_seq INT NOT NULL DEFAULT 1,
    last_answer_seq INT NOT NULL DEFAULT 0,
    first_answer_at DATETIME(3) NULL,
    proposed_resolution JSON NULL,
    resolved_by VARCHAR(64) NULL,
    resolved_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_issue_doc_display (document_id, display_no),
    UNIQUE KEY uk_review_issue_task_doc_id (task_id, document_id, id),
    UNIQUE KEY uk_review_issue_task_id (task_id, id),
    KEY idx_review_issue_task_status (task_id, status, updated_at, id),
    KEY idx_review_issue_assignee (assignee_id, status, response_due_at, id),
    KEY idx_review_issue_reporter (reporter_id, created_at, id),
    KEY idx_review_issue_doc (document_id, created_at, id),
    KEY idx_review_issue_round (round_id, status),
    CONSTRAINT fk_review_issue_task_doc FOREIGN KEY (task_id, document_id) REFERENCES prd_review_document (task_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_issue_origin_rev FOREIGN KEY (task_id, document_id, origin_revision_id) REFERENCES prd_review_revision (task_id, document_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_issue_reporter FOREIGN KEY (reporter_id) REFERENCES prd_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_issue_assignee FOREIGN KEY (assignee_id) REFERENCES prd_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_issue_resolver FOREIGN KEY (resolved_by) REFERENCES prd_user (id) ON DELETE RESTRICT,
    CONSTRAINT chk_review_issue_type CHECK (type IN ('QUESTION', 'MISSING', 'AMBIGUITY', 'SUGGESTION')),
    CONSTRAINT chk_review_issue_source CHECK (source IN ('ONLINE', 'MEETING', 'CHAT', 'DEVELOPMENT', 'TESTING')),
    CONSTRAINT chk_review_issue_impact CHECK (impact IN ('BLOCKING', 'NORMAL')),
    CONSTRAINT chk_review_issue_status CHECK (status IN ('OPEN', 'AWAITING_CONFIRMATION', 'RESOLVED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prd_review_post (
    id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    issue_id VARCHAR(64) NOT NULL,
    issue_seq INT NOT NULL,
    kind VARCHAR(32) NOT NULL,
    body TEXT NOT NULL,
    reply_to_post_id VARCHAR(64) NULL,
    corrects_post_id VARCHAR(64) NULL,
    author_id VARCHAR(64) NOT NULL,
    author_name_snapshot VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_post_issue_seq (issue_id, issue_seq),
    UNIQUE KEY uk_review_post_task_issue_id (task_id, issue_id, id),
    KEY idx_review_post_issue_created (issue_id, created_at, id),
    CONSTRAINT fk_review_post_task_issue FOREIGN KEY (task_id, issue_id) REFERENCES prd_review_issue (task_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_post_author FOREIGN KEY (author_id) REFERENCES prd_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_post_reply FOREIGN KEY (reply_to_post_id) REFERENCES prd_review_post (id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_post_corrects FOREIGN KEY (corrects_post_id) REFERENCES prd_review_post (id) ON DELETE RESTRICT,
    CONSTRAINT chk_review_post_kind CHECK (kind IN ('DISCUSSION', 'ANSWER', 'FOLLOW_UP', 'CORRECTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prd_review_anchor (
    id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    issue_id VARCHAR(64) NOT NULL,
    revision_id VARCHAR(64) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    selector_version VARCHAR(32) NOT NULL,
    renderer_fingerprint VARCHAR(128) NOT NULL,
    rendered_text_hash VARCHAR(64) NOT NULL,
    exact_quote VARCHAR(2000) NOT NULL,
    prefix_text VARCHAR(256) NULL,
    suffix_text VARCHAR(256) NULL,
    start_offset INT NOT NULL,
    end_offset INT NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_review_anchor_issue_rev (issue_id, revision_id, created_at, id),
    CONSTRAINT fk_review_anchor_issue FOREIGN KEY (task_id, issue_id) REFERENCES prd_review_issue (task_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_anchor_rev FOREIGN KEY (task_id, document_id, revision_id) REFERENCES prd_review_revision (task_id, document_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_anchor_creator FOREIGN KEY (created_by) REFERENCES prd_user (id) ON DELETE RESTRICT,
    CONSTRAINT chk_review_anchor_kind CHECK (kind IN ('ORIGINAL', 'RELOCATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prd_review_revision_issue (
    task_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    revision_id VARCHAR(64) NOT NULL,
    issue_id VARCHAR(64) NOT NULL,
    change_note VARCHAR(2000) NULL,
    PRIMARY KEY (revision_id, issue_id),
    KEY idx_rev_issue_issue_rev (issue_id, revision_id),
    CONSTRAINT fk_rev_issue_rev FOREIGN KEY (task_id, document_id, revision_id) REFERENCES prd_review_revision (task_id, document_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_rev_issue_issue FOREIGN KEY (task_id, document_id, issue_id) REFERENCES prd_review_issue (task_id, document_id, id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prd_review_event (
    id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64) NULL,
    issue_id VARCHAR(64) NULL,
    issue_seq INT NULL,
    event_type VARCHAR(64) NOT NULL,
    object_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    actor_name_snapshot VARCHAR(64) NOT NULL,
    from_state VARCHAR(64) NULL,
    to_state VARCHAR(64) NULL,
    metadata JSON NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_review_event_task_time (task_id, created_at, id),
    KEY idx_review_event_issue_seq (issue_id, issue_seq, id),
    CONSTRAINT fk_review_event_task FOREIGN KEY (task_id) REFERENCES prd_task (id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_event_actor FOREIGN KEY (actor_id) REFERENCES prd_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prd_review_notification (
    id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    recipient_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64) NULL,
    issue_id VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    read_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_notify_user_event (recipient_id, event_id),
    KEY idx_review_notify_user_read (recipient_id, read_at, created_at, id),
    CONSTRAINT fk_review_notify_task FOREIGN KEY (task_id) REFERENCES prd_task (id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_notify_recipient FOREIGN KEY (recipient_id) REFERENCES prd_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_notify_event FOREIGN KEY (event_id) REFERENCES prd_review_event (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prd_review_issue_watch (
    task_id VARCHAR(64) NOT NULL,
    issue_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (issue_id, user_id),
    KEY idx_review_watch_user_issue (user_id, issue_id),
    CONSTRAINT fk_review_watch_issue FOREIGN KEY (task_id, issue_id) REFERENCES prd_review_issue (task_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_watch_user FOREIGN KEY (user_id) REFERENCES prd_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
