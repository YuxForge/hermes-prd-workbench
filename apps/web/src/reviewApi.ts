import { client } from "./api";

export type ReviewTodoCounts = {
  assignedToMe: number;
  awaitingMyConfirm: number;
  awaitingProxyConfirm: number;
  unreadNotifications: number;
};

export type ReviewTaskSummary = {
  taskId: string;
  title: string;
  role: "OWNER" | "EDITOR" | "REVIEWER" | "VIEWER";
  todoCounts: ReviewTodoCounts;
  createdAt: string;
  updatedAt: string;
};

export type ReviewTaskListResponse = {
  items: ReviewTaskSummary[];
  total: number;
  page: number;
  pageSize: number;
  asOf: string;
};

export type ReviewDocumentSummary = {
  id: string;
  taskId: string;
  title: string;
  revision: number;
  headRevisionId: string;
  headVersionNo: number;
  nextIssueNo: number;
  unresolvedIssueCount: number;
  archivedAt: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  openRoundId: string | null;
};

export type ReviewRevisionDetail = {
  id: string;
  taskId: string;
  documentId: string;
  versionNo: number;
  parentRevisionId: string | null;
  content: string;
  contentHash: string;
  summary: string;
  changeType: "INITIAL" | "EDIT" | "RESTORE";
  originKind: "MANUAL" | "IMPORT_DRAFT" | "RESTORE";
  originRef: unknown;
  createdBy: string;
  authorNameSnapshot: string;
  createdAt: string;
  relatedIssues?: string[];
};

export type ReviewRevisionSummary = {
  id: string;
  versionNo: number;
  summary: string;
  changeType: string;
  createdBy: string;
  authorNameSnapshot: string;
  createdAt: string;
  relatedIssueCount: number;
};

export type ReviewHistoryItem = {
  kind: "REVISION" | "TITLE_CHANGED" | string;
  revision?: ReviewRevisionSummary | null;
  titleChange?: {
    fromTitle: string;
    toTitle: string;
    reason: string;
    actorId: string;
    actorName: string;
    createdAt: string;
  } | null;
  createdAt: string;
};

export type ReviewHistoryResponse = {
  items: ReviewHistoryItem[];
  total: number;
  page: number;
  pageSize: number;
  asOf: string;
};

export type ReviewRoundDetail = {
  id: string;
  taskId: string;
  documentId: string;
  baselineRevisionId: string;
  baselineVersionNo: number;
  status: "OPEN" | "CLOSED" | "CANCELLED";
  revision: number;
  instruction: string | null;
  dueAt: string | null;
  conclusion: "PASS" | "PASS_WITH_RESIDUAL" | "REJECT" | string | null;
  summary: string | null;
  closeSnapshot: Record<string, unknown> | null;
  createdBy: string;
  closedBy: string | null;
  createdAt: string;
  closedAt: string | null;
  totalIssues: number;
  unresolvedIssues: number;
};

export type ReviewDocumentDetail = {
  document: ReviewDocumentSummary;
  headRevision: ReviewRevisionDetail;
  currentUserRole: "OWNER" | "EDITOR" | "REVIEWER" | "VIEWER";
  openRound?: ReviewRoundDetail | null;
  myRole?: "OWNER" | "EDITOR" | "REVIEWER" | "VIEWER";
  activeRound?: ReviewRoundDetail | null;
};

export type ReviewAnchorDetail = {
  id: string;
  revisionId: string;
  kind: "ORIGINAL" | "RELOCATED";
  selectorVersion: string;
  rendererFingerprint: string;
  renderedTextHash: string;
  exactQuote: string;
  prefixText: string;
  suffixText: string;
  startOffset: number;
  endOffset: number;
  createdAt: string;
};

export type ReviewResolutionDetail = {
  kind: "DOC_CHANGED" | "NO_CHANGE" | "DEFERRED" | "OTHER";
  conclusion: string;
  revisionId: string | null;
  changeNote?: string | null;
  noChangeReason?: string | null;
  summary?: string;
  explanation?: string;
};

export type ReviewIssueSummary = {
  id: string;
  taskId: string;
  documentId: string;
  displayNo: number;
  roundId: string | null;
  originRevisionId: string;
  originVersionNo: number;
  title: string;
  type: "QUESTION" | "MISSING" | "AMBIGUITY" | "SUGGESTION";
  source: "ONLINE" | "MEETING" | "CHAT" | "DEVELOPMENT" | "TESTING";
  impact: "BLOCKING" | "NORMAL";
  reporterId: string;
  reporterName: string;
  assigneeId: string;
  assigneeName: string;
  responseDueAt: string | null;
  status: "OPEN" | "AWAITING_CONFIRMATION" | "RESOLVED" | "CANCELLED";
  revision: number;
  awaitingResponse: boolean;
  overdue: boolean;
  createdAt: string;
  updatedAt: string;
  postCount: number;
};

export type ReviewIssueDetail = ReviewIssueSummary & {
  body: string;
  reportedAtNote: string | null;
  reporterNote: string | null;
  lastQuestionSeq: number;
  lastAnswerSeq: number;
  firstAnswerAt: string | null;
  proposedResolution: ReviewResolutionDetail | null;
  resolution?: ReviewResolutionDetail | null;
  resolvedBy: string | null;
  resolvedByName: string | null;
  resolvedAt: string | null;
  originalAnchor: ReviewAnchorDetail | null;
  latestAnchor: ReviewAnchorDetail | null;
  watching: boolean;
  canConfirm: boolean;
  canProxyConfirm: boolean;
};

export type ReviewStatistics = {
  totalIssues: number;
  openIssues: number;
  awaitingConfirmationIssues: number;
  resolvedIssues: number;
  cancelledIssues: number;
  resolutionRate: string | null;
};

export type ReviewIssueListResponse = {
  items: ReviewIssueSummary[];
  total: number;
  page: number;
  pageSize: number;
  asOf: string;
  statistics: ReviewStatistics;
};

export type ReviewPostResponse = {
  id: string;
  issueId: string;
  issueSeq: number;
  kind: "QUESTION" | "ANSWER" | "FOLLOW_UP" | "DISCUSSION" | "CORRECTION";
  body: string;
  replyToPostId?: string | null;
  correctsPostId?: string | null;
  authorId: string;
  authorName: string;
  createdAt: string;
};

export type ReviewEventResponse = {
  id: string;
  eventType: string;
  actorId: string;
  actorName: string;
  fromState: string | null;
  toState: string | null;
  metadata: Record<string, unknown> | null;
  createdAt: string;
};

export type ReviewTimelineItem = {
  kind: "POST" | "EVENT";
  itemType?: "POST" | "EVENT";
  issueSeq: number;
  post: ReviewPostResponse | null;
  event: ReviewEventResponse | null;
  createdAt: string;
};

export type ReviewTimelineResponse = {
  items: ReviewTimelineItem[];
  total: number;
  page: number;
  pageSize: number;
  asOf: string;
};

export type ReviewMemberDetail = {
  userId: string;
  username: string;
  displayName: string;
  role: "OWNER" | "EDITOR" | "REVIEWER" | "VIEWER";
  revision: number;
  createdBy: string;
  createdAt: string;
};

export type ReviewDiffLine = {
  type: "EQUAL" | "ADD" | "DELETE" | string;
  oldLineNo: number | null;
  newLineNo: number | null;
  content: string;
};

export type ReviewDiffResponse = {
  fromRevisionId: string;
  fromVersionNo: number;
  toRevisionId: string;
  toVersionNo: number;
  lines: ReviewDiffLine[];
};

export type ReviewNotificationItem = {
  id: string;
  taskId: string;
  documentId: string | null;
  issueId: string | null;
  eventId: string | null;
  eventType: string;
  actorName: string;
  targetTitle: string;
  createdAt: string;
  readAt: string | null;
};

export type AnchorInput = {
  revisionId: string;
  selectorVersion?: string;
  rendererFingerprint: string;
  renderedTextHash: string;
  exactQuote: string;
  prefixText: string;
  suffixText: string;
  startOffset: number;
  endOffset: number;
};

// -----------------------------------------------------------------------
// API client methods
// -----------------------------------------------------------------------

export async function fetchReviewTasks(): Promise<ReviewTaskListResponse> {
  const { data } = await client.get<ReviewTaskListResponse>("/review/tasks");
  return data;
}

export type ReviewDocumentListResponse = {
  items: ReviewDocumentSummary[];
  total: number;
  page: number;
  pageSize: number;
  asOf: string;
};

export async function fetchReviewTaskDocuments(taskId: string): Promise<ReviewDocumentListResponse> {
  const { data } = await client.get<ReviewDocumentListResponse>(`/review/tasks/${taskId}/documents`);
  return data;
}

export async function fetchReviewDocument(documentId: string): Promise<ReviewDocumentDetail> {
  const { data } = await client.get<ReviewDocumentDetail>(`/review/documents/${documentId}`);
  return data;
}

export async function createReviewDocument(
  taskId: string,
  payload: { title: string; content: string; summary?: string; changeType?: string }
): Promise<ReviewDocumentDetail> {
  const { data } = await client.post<ReviewDocumentDetail>(`/review/tasks/${taskId}/documents`, payload);
  return data;
}

export async function importReviewDocument(
  taskId: string,
  payload: { sourceId: string; expectedUpdatedAt: string; title: string }
): Promise<ReviewDocumentDetail> {
  const { data } = await client.post<ReviewDocumentDetail>(`/review/tasks/${taskId}/documents/import-draft`, payload);
  return data;
}

export async function updateReviewDocumentTitle(
  documentId: string,
  payload: { expectedRevision: number; title: string; reason?: string }
): Promise<ReviewDocumentDetail> {
  const { data } = await client.patch<ReviewDocumentDetail>(`/review/documents/${documentId}`, {
    expectedRevision: payload.expectedRevision,
    title: payload.title,
    reason: payload.reason || "修改文档标题",
  });
  return data;
}

export async function archiveReviewDocument(
  documentId: string,
  payload: { expectedRevision: number; reason: string }
): Promise<ReviewDocumentSummary> {
  const { data } = await client.post<ReviewDocumentSummary>(`/review/documents/${documentId}/archive`, payload);
  return data;
}

export async function unarchiveReviewDocument(
  documentId: string,
  payload: { expectedRevision: number; reason: string }
): Promise<ReviewDocumentSummary> {
  const { data } = await client.post<ReviewDocumentSummary>(`/review/documents/${documentId}/unarchive`, payload);
  return data;
}

export async function fetchRevisions(documentId: string): Promise<ReviewRevisionDetail[]> {
  const { data } = await client.get<ReviewHistoryResponse>(`/review/documents/${documentId}/revisions`, {
    params: { page: 1, pageSize: 100 },
  });
  const summaries = (data.items || [])
    .filter((item) => item.kind === "REVISION" && item.revision)
    .map((item) => item.revision as ReviewRevisionSummary)
    .sort((a, b) => a.versionNo - b.versionNo);
  const details: ReviewRevisionDetail[] = [];
  for (const summary of summaries) {
    details.push(await fetchRevision(documentId, summary.id));
  }
  return details;
}

export async function fetchRevision(documentId: string, revisionId: string): Promise<ReviewRevisionDetail> {
  const { data } = await client.get<ReviewRevisionDetail>(
    `/review/documents/${documentId}/revisions/${revisionId}`
  );
  return data;
}

export async function createRevision(
  documentId: string,
  payload: {
    expectedRevision: number;
    expectedHeadRevisionId: string;
    content: string;
    summary: string;
    relatedIssues?: string[];
    restoredFromRevisionId?: string;
  }
): Promise<ReviewRevisionDetail> {
  const { data } = await client.post<ReviewRevisionDetail>(`/review/documents/${documentId}/revisions`, payload);
  return data;
}

export async function compareRevisions(
  documentId: string,
  baseRevisionId: string,
  targetRevisionId: string
): Promise<ReviewDiffResponse> {
  const { data } = await client.get<ReviewDiffResponse>(`/review/documents/${documentId}/diff`, {
    params: { fromRevisionId: baseRevisionId, toRevisionId: targetRevisionId },
  });
  return data;
}

export async function fetchRounds(documentId: string): Promise<ReviewRoundDetail[]> {
  const { data } = await client.get<ReviewRoundDetail[]>(`/review/documents/${documentId}/rounds`);
  return data;
}

export async function createRound(
  documentId: string,
  payload: { baselineRevisionId?: string }
): Promise<ReviewRoundDetail> {
  const { data } = await client.post<ReviewRoundDetail>(`/review/documents/${documentId}/rounds`, payload);
  return data;
}

export async function closeRound(
  roundId: string,
  payload: {
    expectedRevision: number;
    conclusion: "PASS" | "PASS_WITH_RESIDUAL" | "REJECT";
    summary: string;
    unresolvedDispositions?: Record<string, string>;
  }
): Promise<ReviewRoundDetail> {
  const { data } = await client.post<ReviewRoundDetail>(`/review/rounds/${roundId}/close`, payload);
  return data;
}

export async function cancelRound(
  roundId: string,
  payload: { expectedRevision: number; reason: string }
): Promise<ReviewRoundDetail> {
  const { data } = await client.post<ReviewRoundDetail>(`/review/rounds/${roundId}/cancel`, payload);
  return data;
}

export async function fetchIssues(
  documentId: string,
  params?: {
    roundId?: string;
    status?: string;
    type?: string;
    impact?: string;
    assigneeId?: string;
    keyword?: string;
    page?: number;
    pageSize?: number;
  }
): Promise<ReviewIssueListResponse> {
  const { data } = await client.get<ReviewIssueListResponse>(`/review/documents/${documentId}/issues`, { params });
  return data;
}

export async function fetchIssue(issueId: string): Promise<ReviewIssueDetail> {
  const { data } = await client.get<ReviewIssueDetail>(`/review/issues/${issueId}`);
  return data;
}

export async function createIssue(
  documentId: string,
  payload: {
    roundId?: string;
    originRevisionId?: string;
    title: string;
    body: string;
    type: "QUESTION" | "MISSING" | "AMBIGUITY" | "SUGGESTION";
    source?: "ONLINE" | "MEETING" | "CHAT" | "DEVELOPMENT" | "TESTING";
    impact: "BLOCKING" | "NORMAL";
    assigneeId?: string;
    responseDueAt?: string;
    anchor?: AnchorInput;
    reportedAtNote?: string;
    reporterNote?: string;
  }
): Promise<ReviewIssueDetail> {
  const { data } = await client.post<ReviewIssueDetail>(`/review/documents/${documentId}/issues`, payload);
  return data;
}

export async function updateIssue(
  issueId: string,
  payload: {
    expectedRevision: number;
    title?: string;
    type?: string;
    impact?: string;
    responseDueAt?: string;
  }
): Promise<ReviewIssueDetail> {
  const { data } = await client.patch<ReviewIssueDetail>(`/review/issues/${issueId}`, payload);
  return data;
}

export type ResolutionInput = {
  kind: "DOC_CHANGED" | "NO_CHANGE";
  conclusion: string;
  revisionId?: string;
  changeNote?: string;
  noChangeReason?: string;
};

export async function executeIssueAction(
  issueId: string,
  payload: {
    expectedRevision: number;
    action: "PROPOSE_RESOLUTION" | "CONFIRM" | "REJECT" | "REOPEN" | "CANCEL" | "RESTORE" | "REMIND";
    reason?: string;
    proxyReason?: string;
    resolution?: ResolutionInput;
    duplicateIssueId?: string;
  }
): Promise<ReviewIssueDetail> {
  const { data } = await client.post<ReviewIssueDetail>(`/review/issues/${issueId}/actions`, payload);
  return data;
}

export async function assignIssue(
  issueId: string,
  payload: { expectedRevision: number; assigneeId: string; reason?: string }
): Promise<ReviewIssueDetail> {
  const { data } = await client.post<ReviewIssueDetail>(`/review/issues/${issueId}/assign`, payload);
  return data;
}

export async function createPost(
  issueId: string,
  payload: {
    expectedRevision: number;
    kind: "QUESTION" | "ANSWER" | "FOLLOW_UP" | "DISCUSSION";
    body: string;
    resolutionProposal?: {
      kind: "DOC_CHANGED" | "NO_CHANGE" | "DEFERRED" | "OTHER";
      summary: string;
      revisionId?: string;
      revisionVersionNo?: number;
      explanation: string;
    };
    unresolvedDisposition?: string;
  }
): Promise<ReviewPostResponse> {
  const { data } = await client.post<ReviewPostResponse>(`/review/issues/${issueId}/posts`, payload);
  return data;
}

export async function fetchTimeline(
  issueId: string,
  page = 1,
  pageSize = 50
): Promise<ReviewTimelineResponse> {
  const { data } = await client.get<ReviewTimelineResponse>(`/review/issues/${issueId}/timeline`, {
    params: { page, pageSize },
  });
  return data;
}

export async function watchIssue(issueId: string): Promise<void> {
  await client.post(`/review/issues/${issueId}/watch`);
}

export async function unwatchIssue(issueId: string): Promise<void> {
  await client.delete(`/review/issues/${issueId}/watch`);
}

export async function relocateAnchor(
  issueId: string,
  payload: { expectedRevision: number; revisionId: string; anchor: AnchorInput }
): Promise<ReviewAnchorDetail> {
  const { data } = await client.post<ReviewAnchorDetail>(`/review/issues/${issueId}/anchors`, payload);
  return data;
}

export type ReviewCandidateUserDetail = {
  userId: string;
  username: string;
  displayName: string;
};

export async function searchCandidateUsers(
  taskId: string,
  query?: string
): Promise<ReviewCandidateUserDetail[]> {
  const { data } = await client.get<ReviewCandidateUserDetail[]>(
    `/review/tasks/${taskId}/candidate-users`,
    { params: { query: query || undefined } }
  );
  return data;
}

export async function fetchMembers(taskId: string): Promise<ReviewMemberDetail[]> {
  const { data } = await client.get<ReviewMemberDetail[]>(`/review/tasks/${taskId}/members`);
  return data;
}

export async function addMember(
  taskId: string,
  payload: { username: string; role: "OWNER" | "EDITOR" | "REVIEWER" | "VIEWER" }
): Promise<ReviewMemberDetail> {
  const { data } = await client.post<ReviewMemberDetail>(`/review/tasks/${taskId}/members`, payload);
  return data;
}

export async function updateMemberRole(
  taskId: string,
  userId: string,
  payload: {
    expectedRevision: number;
    role: "OWNER" | "EDITOR" | "REVIEWER" | "VIEWER";
    reassignment?: { issueId: string; newAssigneeId: string }[];
  }
): Promise<ReviewMemberDetail> {
  const { data } = await client.patch<ReviewMemberDetail>(`/review/tasks/${taskId}/members/${userId}`, payload);
  return data;
}

export async function removeMember(
  taskId: string,
  userId: string,
  payload?: {
    expectedRevision?: number;
    reassignment?: { issueId: string; newAssigneeId: string }[];
  }
): Promise<void> {
  await client.post(`/review/tasks/${taskId}/members/${userId}/remove`, payload || {});
}

export type ReviewNotificationListResponse = {
  items: ReviewNotificationItem[];
  total: number;
  unreadCount: number;
};

export async function fetchNotifications(page = 1, pageSize = 20): Promise<ReviewNotificationListResponse> {
  const { data } = await client.get<ReviewNotificationListResponse>("/review/notifications", { params: { page, pageSize } });
  return data;
}

export async function getUnreadNotificationsCount(): Promise<number> {
  const { data } = await client.get<ReviewNotificationListResponse>("/review/notifications", {
    params: { page: 1, pageSize: 1 },
  });
  return data.unreadCount || 0;
}

export async function markNotificationAsRead(notificationId: string): Promise<void> {
  await client.post("/review/notifications/read", { notificationIds: [notificationId] });
}

export async function markAllNotificationsAsRead(notificationIds: string[]): Promise<void> {
  if (notificationIds.length === 0) return;
  await client.post("/review/notifications/read", { notificationIds });
}

export async function downloadReviewExport(
  taskId: string,
  options?: {
    documentId?: string;
    revisionId?: string;
    roundId?: string;
    includeAuditJson?: boolean;
  }
): Promise<Blob> {
  const { data } = await client.get(`/review/tasks/${taskId}/export`, {
    params: options,
    responseType: "blob",
  });
  return data;
}
