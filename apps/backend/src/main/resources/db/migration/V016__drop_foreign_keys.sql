-- Migration V016: Drop all physical foreign key constraints across the schema.
-- In accordance with enterprise table design best practices, relationship integrity
-- and cascading logic are maintained at the application service layer, avoiding
-- database-level row locks, deadlock risks, and tightly-coupled constraints.

-- 1. Baseline and core task/auth tables
ALTER TABLE agent_result DROP FOREIGN KEY fk_agent_result_run;
ALTER TABLE agent_run DROP FOREIGN KEY fk_agent_run_parent;
ALTER TABLE agent_run DROP FOREIGN KEY fk_agent_run_task;
ALTER TABLE prd_session DROP FOREIGN KEY fk_prd_session_user;
ALTER TABLE prd_source DROP FOREIGN KEY fk_prd_source_task;
ALTER TABLE prd_task DROP FOREIGN KEY fk_prd_task_user;

-- 2. Online review collaborative tables
ALTER TABLE prd_review_member DROP FOREIGN KEY fk_review_member_task;
ALTER TABLE prd_review_member DROP FOREIGN KEY fk_review_member_user;
ALTER TABLE prd_review_member DROP FOREIGN KEY fk_review_member_creator;

ALTER TABLE prd_review_document DROP FOREIGN KEY fk_review_doc_head_rev;
ALTER TABLE prd_review_document DROP FOREIGN KEY fk_review_doc_task;
ALTER TABLE prd_review_document DROP FOREIGN KEY fk_review_doc_creator;

ALTER TABLE prd_review_revision DROP FOREIGN KEY fk_review_rev_task_doc;
ALTER TABLE prd_review_revision DROP FOREIGN KEY fk_review_rev_creator;

ALTER TABLE prd_review_round DROP FOREIGN KEY fk_review_round_task_doc;
ALTER TABLE prd_review_round DROP FOREIGN KEY fk_review_round_baseline;
ALTER TABLE prd_review_round DROP FOREIGN KEY fk_review_round_creator;
ALTER TABLE prd_review_round DROP FOREIGN KEY fk_review_round_closer;

ALTER TABLE prd_review_issue DROP FOREIGN KEY fk_review_issue_task_doc;
ALTER TABLE prd_review_issue DROP FOREIGN KEY fk_review_issue_origin_rev;
ALTER TABLE prd_review_issue DROP FOREIGN KEY fk_review_issue_reporter;
ALTER TABLE prd_review_issue DROP FOREIGN KEY fk_review_issue_assignee;
ALTER TABLE prd_review_issue DROP FOREIGN KEY fk_review_issue_resolver;

ALTER TABLE prd_review_post DROP FOREIGN KEY fk_review_post_task_issue;
ALTER TABLE prd_review_post DROP FOREIGN KEY fk_review_post_author;
ALTER TABLE prd_review_post DROP FOREIGN KEY fk_review_post_reply;
ALTER TABLE prd_review_post DROP FOREIGN KEY fk_review_post_corrects;

ALTER TABLE prd_review_anchor DROP FOREIGN KEY fk_review_anchor_issue;
ALTER TABLE prd_review_anchor DROP FOREIGN KEY fk_review_anchor_rev;
ALTER TABLE prd_review_anchor DROP FOREIGN KEY fk_review_anchor_creator;

ALTER TABLE prd_review_revision_issue DROP FOREIGN KEY fk_rev_issue_rev;
ALTER TABLE prd_review_revision_issue DROP FOREIGN KEY fk_rev_issue_issue;

ALTER TABLE prd_review_event DROP FOREIGN KEY fk_review_event_task;
ALTER TABLE prd_review_event DROP FOREIGN KEY fk_review_event_actor;

ALTER TABLE prd_review_notification DROP FOREIGN KEY fk_review_notify_task;
ALTER TABLE prd_review_notification DROP FOREIGN KEY fk_review_notify_recipient;
ALTER TABLE prd_review_notification DROP FOREIGN KEY fk_review_notify_event;

ALTER TABLE prd_review_issue_watch DROP FOREIGN KEY fk_review_watch_issue;
ALTER TABLE prd_review_issue_watch DROP FOREIGN KEY fk_review_watch_user;
