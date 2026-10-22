-- Flyway migration V017: Clean up unread notifications for already replied/confirmed/closed issues
-- When an issue has already transitioned to AWAITING_CONFIRMATION, RESOLVED, or CANCELLED,
-- any unread notification for the assignee is considered handled/read.
UPDATE prd_review_notification n
JOIN prd_review_issue i ON n.issue_id = i.id
SET n.read_at = CURRENT_TIMESTAMP(3)
WHERE n.read_at IS NULL
  AND (
    (i.status IN ('AWAITING_CONFIRMATION', 'RESOLVED', 'CANCELLED') AND n.recipient_id = i.assignee_id)
    OR (i.status IN ('RESOLVED', 'CANCELLED') AND n.recipient_id = i.reporter_id)
  );
