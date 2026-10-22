import axios from "axios";
import { clearSession, getToken, type User } from "./session";
import type { QualityCatalog } from "./quality";

export type Source = {
  id: string;
  taskId: string;
  name: string;
  mediaType: string;
  size: number;
  status: string;
  createdAt: string;
};

export type Task = {
  id: string;
  title: string;
  sources: Source[];
  createdAt: string;
};

export type TaskSummary = {
  id: string;
  title: string;
  createdAt: string;
};

export type Mode = "REVIEW" | "GENERATE" | "DEMO" | "POC" | "ESTIMATE" | "TEST_CASE" | "TECH_SPEC";

export type EstimateItem = {
  module: string;
  subModule?: string;
  type: "PAGE" | "API" | "THIRD_PARTY_API" | string;
  name: string;
  detail?: string;
  complexity?: "SIMPLE" | "MEDIUM" | "COMPLEX" | string;
  devDays: number;
  testDays: number;
  totalDays: number;
  description?: string;
  extraFields?: Record<string, string>;
};

export type ModuleSummary = {
  module: string;
  subModule?: string;
  pageCount?: number;
  apiCount?: number;
  devDays: number;
  testDays: number;
  totalDays: number;
};

export type EstimatePayload = {
  mode: "ESTIMATE";
  title: string;
  summary: string;
  scopeNote: string;
  totalDays: number;
  devDays: number;
  testDays: number;
  customHeaders?: string[];
  moduleSummaries?: ModuleSummary[];
  items: EstimateItem[];
};

export type ExportEstimateColumn = {
  key: string;
  title: string;
  visible: boolean;
};

export type ExportEstimateRequest = {
  title?: string;
  dailyRate?: number;
  columns?: ExportEstimateColumn[];
  customHeaders?: string[];
  extraFieldValues?: Record<string, string>;
};

export type TestCaseItem = {
  caseId: string;
  module: string;
  subModule?: string;
  title: string;
  type: "FUNCTION" | "BOUNDARY" | "EXCEPTION" | "PERMISSION" | "CONCURRENCY" | string;
  priority: "P0" | "P1" | "P2" | string;
  precondition?: string;
  steps: string;
  testData?: string;
  expectedResult: string;
  description?: string;
  extraFields?: Record<string, string>;
};

export type TestCaseModuleSummary = {
  module: string;
  subModule?: string;
  totalCount: number;
  p0Count: number;
  p1Count: number;
  p2Count: number;
};

export type TestCasePayload = {
  mode: "TEST_CASE";
  title: string;
  summary: string;
  scopeNote: string;
  totalCount: number;
  p0Count: number;
  p1Count: number;
  p2Count: number;
  boundaryCount?: number;
  exceptionCount?: number;
  customHeaders?: string[];
  moduleSummaries?: TestCaseModuleSummary[];
  items: TestCaseItem[];
};

export type ExportTestCaseColumn = {
  key: string;
  title: string;
  visible: boolean;
};

export type ExportTestCaseRequest = {
  title?: string;
  columns?: ExportTestCaseColumn[];
  customHeaders?: string[];
  extraFieldValues?: Record<string, string>;
};

export type TechSpecTaskItem = {
  taskId: string;
  module: string;
  name: string;
  type: "FRONTEND" | "BACKEND" | "MIDDLEWARE" | string;
  priority: "P0" | "P1" | "P2" | string;
  devDays: number;
  dependencies: string[];
  description: string;
};

export type TechSpecColumn = {
  name: string;
  type: string;
  nullable: boolean;
  comment: string;
};

export type TechSpecDatabaseTable = {
  tableName: string;
  tableComment: string;
  columns: TechSpecColumn[];
  indexes: Array<string | Record<string, unknown>>;
};

export type TechSpecApiEndpoint = {
  method: "GET" | "POST" | "PUT" | "DELETE" | string;
  path: string;
  summary: string;
  requestBody?: string;
  responseBody?: string;
  errorCodes?: string | Array<string | Record<string, unknown>>;
};

export type TechSpecPayload = {
  mode: "TECH_SPEC";
  title: string;
  summary: string;
  architectureMarkdown: string;
  totalTasks: number;
  frontendTasks: number;
  backendTasks: number;
  middlewareTasks: number;
  totalDevDays: number;
  databaseTables?: TechSpecDatabaseTable[];
  apiEndpoints?: TechSpecApiEndpoint[];
  tasks: TechSpecTaskItem[];
};

export type DemoSkill = {
  id: string;
  name: string;
  description: string;
};

export type Completeness = "QUICK" | "STANDARD" | "THOROUGH";

export type Run = {
  id: string;
  taskId: string;
  parentRunId: string | null;
  mode: Mode;
  completeness?: Completeness;
  lenses?: string[];
  status: "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED";
  stage: string | null;
  result: Record<string, unknown> | null;
  error: string | null;
  accepted: boolean;
  createdAt: string;
  finishedAt: string | null;
};

export type RunHistoryItem = {
  id: string;
  taskId: string;
  taskTitle: string;
  mode: Mode;
  status: Run["status"];
  summary: string | null;
  error: string | null;
  accepted: boolean;
  createdAt: string;
  finishedAt: string | null;
};

export type DraftSource = {
  sourceId: string;
  runId: string;
  content: string;
  updatedAt: string;
};

export type ReviewFeedbackAction = "EDITED" | "WONT_DO" | "NEEDS_CUSTOMER";

export type ReviewFeedbackItem = {
  itemPath: string;
  action: ReviewFeedbackAction;
  note?: string;
};

export type ReviewFeedback = {
  runId: string;
  revision: number;
  items: ReviewFeedbackItem[];
};

export type AuthPayload = {
  token: string;
  expiresAt: string;
  user: User;
};

export const client = axios.create({ baseURL: "/api/v1", timeout: 30000 });

client.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
      const blob = error.response.data;
      const type = blob.type || "";
      if (type.includes("application/json") || type.includes("text/") || !type) {
        try {
          const text = await blob.text();
          error.response.data = JSON.parse(text);
        } catch {
          // not valid json, keep as blob
        }
      }
    }
    const status = error.response?.status;
    const url = String(error.config?.url || "");
    if (status === 401 && !url.includes("/auth/login") && !url.includes("/auth/register")) {
      clearSession();
      if (!window.location.pathname.startsWith("/login")) {
        const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`;
        const params = new URLSearchParams();
        if (redirect.startsWith("/") && !redirect.startsWith("//")) {
          params.set("redirect", redirect);
        }
        window.location.assign(params.size > 0 ? `/login?${params.toString()}` : "/login");
      }
    }
    return Promise.reject(error);
  }
);

export function apiErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string } | undefined;
    if (data?.message) {
      return data.message;
    }
    if (error.code === "ECONNABORTED" || error.message?.toLowerCase().includes("timeout")) {
      return "请求超时，请稍后重试";
    }
    if (error.code === "ERR_NETWORK") {
      return "无法连接服务";
    }
    return error.message;
  }
  return "请求失败";
}

export function apiErrorCode(error: unknown): string | undefined {
  if (!axios.isAxiosError(error)) return undefined;
  const data = error.response?.data as { code?: string } | undefined;
  return data?.code;
}

export function apiHttpStatus(error: unknown): number | undefined {
  if (!axios.isAxiosError(error)) return undefined;
  return error.response?.status;
}

export async function getHealth() {
  const { data } = await client.get<{ status: string }>("/health");
  return data;
}

export async function registerAccount(username: string, password: string, displayName: string) {
  const { data } = await client.post<AuthPayload>("/auth/register", { username, password, displayName });
  return data;
}

export async function loginAccount(username: string, password: string) {
  const { data } = await client.post<AuthPayload>("/auth/login", { username, password });
  return data;
}

export async function logoutAccount() {
  try {
    await client.post("/auth/logout");
  } catch {
    // Local session is cleared regardless of server state.
  }
}

export async function getMe() {
  const { data } = await client.get<User>("/auth/me");
  return data;
}

export async function listTasks() {
  const { data } = await client.get<TaskSummary[]>("/tasks");
  return data;
}

export async function createTask(title: string) {
  const { data } = await client.post<Task>("/tasks", { title });
  return data;
}

export async function renameTask(taskId: string, title: string) {
  const { data } = await client.patch<Task>(`/tasks/${taskId}`, { title });
  return data;
}

export async function listDemoSkills() {
  const { data } = await client.get<DemoSkill[]>("/demo-skills");
  return data;
}

export async function listQualityOptions() {
  const { data } = await client.get<QualityCatalog>("/quality-options");
  return data;
}

export async function getTask(taskId: string) {
  const { data } = await client.get<Task>(`/tasks/${taskId}`);
  return data;
}

export async function uploadSource(taskId: string, file: File) {
  const form = new FormData();
  form.append("file", file);
  const { data } = await client.post<Source>(`/tasks/${taskId}/sources`, form);
  return data;
}

export async function deleteSource(taskId: string, sourceId: string) {
  await client.delete(`/tasks/${taskId}/sources/${sourceId}`);
}

export async function getDraftSource(taskId: string, sourceId: string) {
  const { data } = await client.get<DraftSource>(`/tasks/${taskId}/draft-sources/${sourceId}`);
  return data;
}

export async function updateDraftSource(taskId: string, sourceId: string, content: string, expectedUpdatedAt: string) {
  const { data } = await client.put<DraftSource>(`/tasks/${taskId}/draft-sources/${sourceId}`, {
    content,
    expectedUpdatedAt,
  });
  return data;
}

export async function createRun(
  taskId: string,
  mode: Mode,
  instruction: string,
  options?: { skill?: string; completeness?: Completeness; lenses?: string[]; reviseFromRunId?: string }
) {
  const body: Record<string, unknown> = {
    mode,
    instruction,
    completeness: options?.completeness || "STANDARD",
    lenses: options?.lenses || [],
  };
  if (options?.skill) {
    body.skill = options.skill;
  }
  if (options?.reviseFromRunId) {
    body.reviseFromRunId = options.reviseFromRunId;
  }
  const { data } = await client.post<Run>(`/tasks/${taskId}/runs`, body);
  return data;
}

export async function listRuns(mode?: Mode, taskId?: string) {
  const { data } = await client.get<RunHistoryItem[]>("/runs", {
    params: {
      ...(mode ? { mode } : {}),
      ...(taskId ? { taskId } : {}),
    },
  });
  return data;
}

export async function getRun(runId: string) {
  const { data } = await client.get<Run>(`/runs/${runId}`);
  return data;
}

export async function confirmRun(runId: string) {
  const { data } = await client.post<Run>(`/runs/${runId}/confirm`);
  return data;
}

export async function getReviewFeedback(runId: string) {
  const { data } = await client.get<ReviewFeedback>(`/runs/${runId}/review-feedback`);
  return data;
}

export async function updateReviewFeedback(runId: string, expectedRevision: number, items: ReviewFeedbackItem[]) {
  const normalized = items.map((item) => ({
    itemPath: item.itemPath,
    action: item.action,
    ...(item.note?.trim() ? { note: item.note.trim() } : {}),
  }));
  const { data } = await client.put<ReviewFeedback>(`/runs/${runId}/review-feedback`, {
    expectedRevision,
    items: normalized,
  });
  return data;
}

export async function exportEstimateXlsx(runId: string, request?: ExportEstimateRequest) {
  const response = await client.post(`/runs/${runId}/export-estimate`, request || {}, {
    responseType: "blob",
    timeout: 120000,
  });
  const header = (response.headers && response.headers["content-disposition"]) || "";
  let filename = "工作量评估与报价表.xlsx";
  const match = header.match(/filename\*=UTF-8''([^;]+)/i) || header.match(/filename="?([^";]+)"?/i);
  if (match && match[1]) {
    filename = decodeURIComponent(match[1]);
  }
  const blob = new Blob([response.data], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export async function exportTestCaseXlsx(runId: string, request?: ExportTestCaseRequest) {
  const response = await client.post(`/runs/${runId}/export-test-cases`, request || {}, {
    responseType: "blob",
    timeout: 120000,
  });
  const header = (response.headers && response.headers["content-disposition"]) || "";
  let filename = "系统测试用例清单.xlsx";
  const match = header.match(/filename\*=UTF-8''([^;]+)/i) || header.match(/filename="?([^";]+)"?/i);
  if (match && match[1]) {
    filename = decodeURIComponent(match[1]);
  }
  const blob = new Blob([response.data], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export type ExportTechSpecRequest = {
  title?: string;
};

/** Download the full deliverable TECH_SPEC Markdown (architecture + tables + APIs + tasks). */
export async function exportTechSpecMarkdown(runId: string, request?: ExportTechSpecRequest) {
  const response = await client.post(`/runs/${runId}/export-tech-spec`, request || {}, {
    responseType: "blob",
    timeout: 120000,
  });
  const header = (response.headers && response.headers["content-disposition"]) || "";
  let filename = "研发技术方案与任务工单.md";
  const match = header.match(/filename\*=UTF-8''([^;]+)/i) || header.match(/filename="?([^";]+)"?/i);
  if (match && match[1]) {
    filename = decodeURIComponent(match[1]);
  }
  const blob = new Blob([response.data], { type: "text/markdown;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/** Fetch full TECH_SPEC Markdown text for clipboard copy (same content as download). */
export async function fetchTechSpecMarkdownText(runId: string, request?: ExportTechSpecRequest): Promise<string> {
  const response = await client.post(`/runs/${runId}/export-tech-spec`, request || {}, {
    responseType: "blob",
    timeout: 120000,
  });
  const blob = response.data instanceof Blob ? response.data : new Blob([response.data]);
  return await blob.text();
}

export async function downloadTaskBundleZip(taskId: string) {
  const response = await client.get(`/tasks/${taskId}/bundle`, {
    responseType: "blob",
    timeout: 120000,
  });
  const header = (response.headers && response.headers["content-disposition"]) || "";
  let filename = "项目交付大礼包.zip";
  const match = header.match(/filename\*=UTF-8''([^;]+)/i) || header.match(/filename="?([^";]+)"?/i);
  if (match && match[1]) {
    filename = decodeURIComponent(match[1]);
  }
  const blob = new Blob([response.data], {
    type: "application/zip",
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export type AnalyticsOverview = {
  totalUsers: number;
  activeUsersToday: number;
  totalTasks: number;
  totalRuns: number;
  totalRunsSucceeded: number;
  totalRunsFailed: number;
  successRate: number;
  totalErrors: number;
};

export type UserAnalyticsProfile = {
  userId: string;
  username: string;
  displayName: string;
  role: string;
  registeredAt: string;
  lastActiveAt: string | null;
  taskCount: number;
  runCount: number;
  errorCount: number;
};

export type FeatureUsageStat = {
  module: string;
  action: string;
  count: number;
  avgDurationMs: number;
  successCount: number;
  failCount: number;
  successRate: number;
};

export type ErrorStat = {
  module: string;
  errorCode: string;
  count: number;
  lastOccurredAt: string;
};

export type RecentErrorLog = {
  id: string;
  taskId: string;
  taskTitle: string | null;
  userId: string | null;
  username: string | null;
  module: string;
  action: string;
  errorCode: string;
  errorMessage: string | null;
  createdAt: string;
};

export type AdminDashboardData = {
  overview: AnalyticsOverview;
  users: UserAnalyticsProfile[];
  features: FeatureUsageStat[];
  errorStats: ErrorStat[];
  recentErrors: RecentErrorLog[];
};

export async function getAdminDashboard() {
  const { data } = await client.get<AdminDashboardData>("/admin/analytics/dashboard");
  return data;
}

export async function getAdminOverview() {
  const { data } = await client.get<AnalyticsOverview>("/admin/analytics/overview");
  return data;
}

export async function getAdminUsers() {
  const { data } = await client.get<UserAnalyticsProfile[]>("/admin/analytics/users");
  return data;
}

export async function getAdminFeatures(days = 30) {
  const { data } = await client.get<FeatureUsageStat[]>("/admin/analytics/features", { params: { days } });
  return data;
}

export async function getAdminErrors(days = 30) {
  const { data } = await client.get<ErrorStat[]>("/admin/analytics/errors", { params: { days } });
  return data;
}

export async function getAdminRecentErrors(limit = 20) {
  const { data } = await client.get<RecentErrorLog[]>("/admin/analytics/recent-errors", { params: { limit } });
  return data;
}

export async function adminUpdateUserRole(userId: string, role: "ADMIN" | "USER") {
  const { data } = await client.put<User>(`/admin/users/${userId}/role`, { role });
  return data;
}

export async function adminCreateUser(payload: {
  username: string;
  password: string;
  displayName?: string;
  role: "ADMIN" | "USER";
}) {
  const { data } = await client.post<User>("/admin/users", payload);
  return data;
}

export type UpdateLogItem = {
  title: string;
  detail: string;
  type?: "feature" | "improve" | "fix" | "security" | string;
  tone?: "accent" | "blue" | "amber" | string;
};

export type UpdateLogEntry = {
  id?: string;
  version: string;
  releaseDate?: string;
  date?: string;
  title: string;
  summary: string;
  highlights?: string[];
  items: UpdateLogItem[];
  isLatest?: boolean;
  status?: "PUBLISHED" | "DRAFT" | string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type UpdateLogSummaryResponse = {
  latest: UpdateLogEntry | null;
  history: UpdateLogEntry[];
};

export type CreateUpdateLogPayload = {
  version: string;
  releaseDate: string;
  title: string;
  summary: string;
  highlights: string[];
  items: UpdateLogItem[];
  isLatest?: boolean;
  status?: string;
};

export type UpdateUpdateLogPayload = CreateUpdateLogPayload;

export async function getPublishedUpdateLogs(): Promise<UpdateLogSummaryResponse> {
  const { data } = await client.get<UpdateLogSummaryResponse>("/update-logs");
  return data;
}

export async function getAdminUpdateLogs(params?: { keyword?: string; status?: string }): Promise<UpdateLogEntry[]> {
  const { data } = await client.get<UpdateLogEntry[]>("/admin/update-logs", { params });
  return data;
}

export async function createAdminUpdateLog(payload: CreateUpdateLogPayload): Promise<UpdateLogEntry> {
  const { data } = await client.post<UpdateLogEntry>("/admin/update-logs", payload);
  return data;
}

export async function updateAdminUpdateLog(id: string, payload: UpdateUpdateLogPayload): Promise<UpdateLogEntry> {
  const { data } = await client.put<UpdateLogEntry>(`/admin/update-logs/${id}`, payload);
  return data;
}

export async function deleteAdminUpdateLog(id: string): Promise<void> {
  await client.delete(`/admin/update-logs/${id}`);
}

export async function toggleAdminUpdateLogStatus(
  id: string,
  payload: { status?: string; isLatest?: boolean }
): Promise<UpdateLogEntry> {
  const { data } = await client.post<UpdateLogEntry>(`/admin/update-logs/${id}/status`, payload);
  return data;
}
