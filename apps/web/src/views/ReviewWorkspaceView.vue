<template>
  <div class="review-workspace-page">
    <header class="topbar">
      <a class="brand" href="/" aria-label="PRD 工作台">
        <span class="brand-badge-icon"><Layers :size="16" /></span>
        <strong>PRD 工作台</strong>
        <span class="brand-sub-badge">需求线上评审</span>
      </a>

      <!-- Primary Module Navigation -->
      <nav class="primary-module-nav" aria-label="工作台主功能模块导航">
        <router-link to="/" class="primary-nav-tab">
          <Layers :size="14" />
          <span>PRD 工作台</span>
        </router-link>
        <router-link to="/reviews" class="primary-nav-tab review-tab active">
          <CheckSquare :size="14" />
          <span>线上评审</span>
          <span class="nav-badge-pill">协同</span>
        </router-link>
      </nav>

      <div class="topbar-spacer"></div>

      <button
        class="topbar-action update-log-action"
        type="button"
        title="查看更新日志"
        aria-label="查看更新日志"
        @click="updateLogOpen = true"
      >
        <History :size="15" />
        <span>更新日志</span>
        <span v-if="!updateLogSeen" class="update-log-dot" aria-label="有新更新"></span>
      </button>

      <button
        class="topbar-action notification-action"
        type="button"
        title="查看评审通知"
        aria-label="查看评审通知"
        @click="toggleNotificationDrawer"
      >
        <Bell :size="15" />
        <span>通知</span>
        <span v-if="unreadCount > 0" class="notification-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
      </button>

      <div class="user-profile-menu-wrapper">
        <button
          class="user-avatar-btn"
          type="button"
          :title="currentUser?.displayName || currentUser?.username"
          @click.stop="userMenuOpen = !userMenuOpen"
        >
          <span class="user-avatar-text">{{ userInitial }}</span>
          <span class="user-name-display">{{ currentUser?.displayName || currentUser?.username }}</span>
        </button>
        <div v-if="userMenuOpen" class="user-dropdown-card" role="menu" @click.stop>
          <div class="user-dropdown-header">
            <div class="user-dropdown-avatar">{{ userInitial }}</div>
            <div class="user-dropdown-info">
              <span class="user-dropdown-name">{{ currentUser?.displayName || currentUser?.username }}</span>
              <span class="user-dropdown-role">@{{ currentUser?.username }} ({{ currentUser?.role }})</span>
            </div>
          </div>
          <div class="user-dropdown-footer">
            <button class="logout-menu-btn" type="button" @click="handleLogout">
              <LogOut :size="14" />
              <span>退出登录</span>
            </button>
          </div>
        </div>
      </div>
    </header>

    <main class="review-workspace-main">
      <div class="workspace-hero">
        <div class="hero-left">
          <h1>线上评审</h1>
          <p class="hero-subtitle">跨角色在线协同、逐条闭环评审、版本基线比对与导出归档</p>
        </div>
        <div class="hero-actions">
          <div class="search-input-box">
            <Search :size="15" class="search-icon" />
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索需求任务标题..."
              class="task-search-input"
            />
          </div>
        </div>
      </div>

      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>正在加载线上评审任务...</p>
      </div>

      <div v-else-if="filteredTasks.length === 0" class="empty-state">
        <FileText :size="48" class="empty-icon" />
        <h2>暂无可评审的需求任务</h2>
        <p>当您在工作台中生成需求或被加入需求评审团队时，相关任务将显示在此处。</p>
        <router-link to="/" class="run-button return-btn">前往主工作台生成 PRD</router-link>
      </div>

      <div v-else class="task-grid">
        <div
          v-for="task in filteredTasks"
          :key="task.taskId"
          class="review-task-card"
          @click="openTask(task.taskId)"
        >
          <div class="card-header">
            <div class="card-title-group">
              <h2 class="card-title" :title="task.title">{{ task.title }}</h2>
              <span class="role-badge" :class="'role-' + task.role.toLowerCase()">
                {{ roleLabels[task.role] || task.role }}
              </span>
            </div>
          </div>

          <div class="card-meta">
            <div class="meta-item">
              <Clock :size="14" />
              <span>更新于 {{ formatTime(task.updatedAt || task.createdAt) }}</span>
            </div>
          </div>

          <div class="card-stats">
            <div class="stat-pill" :class="{ highlight: (task.todoCounts?.assignedToMe || 0) > 0 }">
              <MessageSquare :size="14" />
              <span>待我解决: {{ task.todoCounts?.assignedToMe || 0 }}</span>
            </div>
            <div v-if="(task.todoCounts?.awaitingMyConfirm || 0) > 0" class="stat-pill action-required">
              <AlertCircle :size="14" />
              <span>待我确认: {{ task.todoCounts.awaitingMyConfirm }}</span>
            </div>
            <div v-if="(task.todoCounts?.awaitingProxyConfirm || 0) > 0" class="stat-pill subtle">
              <AlertCircle :size="14" />
              <span>待代确认: {{ task.todoCounts.awaitingProxyConfirm }}</span>
            </div>
            <div v-if="(task.todoCounts?.unreadNotifications || 0) > 0" class="stat-pill unread-pill">
              <Bell :size="14" />
              <span>未读: {{ task.todoCounts.unreadNotifications }}</span>
            </div>
          </div>

          <div class="card-footer">
            <span class="enter-link">
              进入评审工作区
              <ArrowRight :size="14" />
            </span>
          </div>
        </div>
      </div>
    </main>

    <!-- Notification Drawer -->
    <div v-if="notificationDrawerOpen" class="drawer-backdrop" @click="notificationDrawerOpen = false">
      <div class="notification-drawer" @click.stop>
        <div class="drawer-header">
          <h3>
            <Bell :size="16" />
            评审通知中心
          </h3>
          <div class="drawer-actions">
            <button
              v-if="unreadCount > 0"
              class="mark-all-read-btn"
              type="button"
              @click="handleMarkAllRead"
            >
              全部标为已读
            </button>
            <button
              class="close-drawer-btn"
              type="button"
              @click="notificationDrawerOpen = false"
            >
              <X :size="16" />
            </button>
          </div>
        </div>

        <div v-if="notifications.length === 0" class="empty-notifications">
          <CheckCircle2 :size="32" />
          <p>暂无任何评审通知</p>
        </div>

        <div v-else class="notification-list">
          <div
            v-for="item in notifications"
            :key="item.id"
            class="notification-item"
            :class="{ unread: !item.readAt }"
            @click="openNotificationItem(item)"
          >
            <div class="notif-top">
              <span class="notif-actor">{{ item.actorName }}</span>
              <span class="notif-type">{{ formatEventType(item.eventType) }}</span>
              <span class="notif-time">{{ formatTime(item.createdAt) }}</span>
            </div>
            <div class="notif-body">
              <span class="notif-issue">{{ item.targetTitle }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Release Changelog Modal -->
    <ChangelogModal
      v-model="updateLogOpen"
      :history="UPDATE_LOG_HISTORY"
      :current="CURRENT_UPDATE_LOG"
      :user-id="currentUser?.id || currentUser?.username"
      @seen="updateLogSeen = true"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Bell,
  CheckCircle2,
  CheckSquare,
  Clock,
  File,
  FileText,
  History,
  Layers,
  LogOut,
  MessageSquare,
  Search,
  User,
  X,
} from "lucide-vue-next";
import ChangelogModal from "../components/ChangelogModal.vue";
import { CURRENT_UPDATE_LOG, UPDATE_LOG_HISTORY, updateLogSeenKey } from "../updateLog";
import {
  fetchNotifications,
  fetchReviewTasks,
  getUnreadNotificationsCount,
  markAllNotificationsAsRead,
  markNotificationAsRead,
  type ReviewNotificationItem,
  type ReviewTaskSummary,
} from "../reviewApi";
import { clearSession, getUser } from "../session";

const router = useRouter();
const currentUser = ref(getUser());
const userMenuOpen = ref(false);
const loading = ref(true);
const tasks = ref<ReviewTaskSummary[]>([]);
const searchQuery = ref("");
const unreadCount = ref(0);
const notificationDrawerOpen = ref(false);
const notifications = ref<ReviewNotificationItem[]>([]);
const updateLogOpen = ref(false);
const updateLogSeen = ref(false);

const roleLabels: Record<string, string> = {
  OWNER: "所有者",
  EDITOR: "编辑者",
  REVIEWER: "评审者",
  VIEWER: "观察者",
};

const roundStatusLabels: Record<string, string> = {
  OPEN: "本轮进行中",
  CLOSED: "本轮已结束",
  CANCELLED: "已取消",
};

const userInitial = computed(() => {
  const name = currentUser.value?.displayName || currentUser.value?.username || "U";
  return name.charAt(0).toUpperCase();
});

const filteredTasks = computed(() => {
  const q = searchQuery.value.trim().toLowerCase();
  if (!q) return tasks.value;
  return tasks.value.filter((t) => t.title.toLowerCase().includes(q));
});

function formatTime(isoStr: string): string {
  if (!isoStr) return "";
  try {
    const date = new Date(isoStr);
    return date.toLocaleString("zh-CN", {
      month: "numeric",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return isoStr;
  }
}

function formatEventType(type: string): string {
  const map: Record<string, string> = {
    ISSUE_CREATED: "提出了新问题",
    ISSUE_ANSWERED: "提交了正式答复",
    ISSUE_FOLLOW_UP: "追加了提问",
    ISSUE_COMMENTED: "发表了讨论",
    RESOLUTION_PROPOSED: "请求确认解决",
    ISSUE_CONFIRMED: "确认问题已解决",
    ISSUE_REJECTED: "拒绝解决确认",
    ISSUE_REOPENED: "重新打开了问题",
    ISSUE_CANCELLED: "撤销了问题",
    ISSUE_RESTORED: "恢复了已撤销问题",
    ISSUE_ASSIGNED: "转派了问题",
    ROUND_OPENED: "发起了新一轮评审",
    ROUND_CLOSED: "结束了评审轮次",
  };
  return map[type] || type;
}

async function loadData() {
  loading.value = true;
  try {
    const [taskListResp, count] = await Promise.all([
      fetchReviewTasks(),
      getUnreadNotificationsCount().catch(() => 0),
    ]);
    tasks.value = taskListResp.items || [];
    unreadCount.value = count;
  } catch (err) {
    console.error("Failed to load review workspace tasks", err);
  } finally {
    loading.value = false;
  }
}

async function toggleNotificationDrawer() {
  notificationDrawerOpen.value = !notificationDrawerOpen.value;
  if (notificationDrawerOpen.value) {
    try {
      const resp = await fetchNotifications(1, 30);
      notifications.value = resp.items;
      unreadCount.value = resp.unreadCount;
    } catch (err) {
      console.error("Failed to load notifications", err);
    }
  }
}

async function handleMarkAllRead() {
  try {
    await markAllNotificationsAsRead([]);
    unreadCount.value = 0;
    notifications.value.forEach((n) => (n.readAt = new Date().toISOString()));
    await loadData();
  } catch (err) {
    console.error("Failed to mark all as read", err);
  }
}

async function openNotificationItem(item: ReviewNotificationItem) {
  if (!item.readAt) {
    markNotificationAsRead(item.id).catch(() => {});
    item.readAt = new Date().toISOString();
    unreadCount.value = Math.max(0, unreadCount.value - 1);
  }
  notificationDrawerOpen.value = false;
  const targetPath = item.documentId && item.documentId !== "null" && item.documentId !== "undefined"
    ? `/reviews/${item.taskId}/documents/${item.documentId}`
    : `/reviews/${item.taskId}`;
  await router.push({
    path: targetPath,
    query: item.issueId ? { issue: item.issueId } : {},
  });
}

function openTask(taskId: string) {
  router.push(`/reviews/${taskId}`);
}

function handleLogout() {
  clearSession();
  router.replace("/login");
}

onMounted(() => {
  updateLogSeen.value = localStorage.getItem(updateLogSeenKey(currentUser.value?.id || currentUser.value?.username)) === CURRENT_UPDATE_LOG.version;
  loadData();
});
</script>

<style scoped>
.review-workspace-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--page);
  overflow-y: auto;
}

.review-workspace-main {
  flex: 1;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 32px 24px;
}

.workspace-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 28px;
  gap: 16px;
  flex-wrap: wrap;
}

.hero-left h1 {
  font-size: 24px;
  font-weight: 700;
  color: var(--ink);
  margin: 0 0 6px 0;
}

.hero-subtitle {
  font-size: 14px;
  color: var(--ink-faint);
  margin: 0;
}

.search-input-box {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 12px;
  color: var(--ink-faint);
}

.task-search-input {
  width: 280px;
  height: 38px;
  padding: 0 12px 0 34px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink);
  font-size: 14px;
  outline: none;
}

.task-search-input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent-soft);
}

.notification-badge {
  background: var(--danger);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 10px;
  line-height: 1.2;
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 20px;
}

.review-task-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
}

.review-task-card:hover {
  border-color: var(--accent);
  transform: translateY(-2px);
  box-shadow: var(--shadow);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.card-title-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ink);
  margin: 0;
  line-height: 1.4;
}

.role-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
}

.role-owner {
  background: var(--accent-soft);
  color: var(--accent);
}

.role-editor {
  background: var(--blue-soft);
  color: var(--blue);
}

.role-reviewer {
  background: var(--purple-soft);
  color: var(--purple);
}

.role-viewer {
  background: var(--surface-quiet);
  color: var(--ink-faint);
}

.round-badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.round-open {
  background: #ecfdf5;
  color: #047857;
  border: 1px solid #a7f3d0;
}

.round-closed {
  background: #f1f5f9;
  color: #475569;
}

.card-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--ink-faint);
  margin-bottom: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.card-stats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.stat-pill {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  background: var(--surface-subtle);
  color: var(--ink-soft);
  border: 1px solid var(--line);
}

.stat-pill.highlight {
  background: var(--amber-soft);
  color: var(--amber);
  border-color: #fde68a;
  font-weight: 600;
}

.stat-pill.action-required {
  background: var(--danger-soft);
  color: var(--danger);
  border-color: #fecdd3;
  font-weight: 600;
}

.stat-pill.unread-pill {
  background: #eef2ff;
  color: #4338ca;
  font-weight: 600;
}

.card-footer {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  display: flex;
  justify-content: flex-end;
}

.enter-link {
  font-size: 13px;
  color: var(--accent);
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 16px;
  text-align: center;
  color: var(--ink-faint);
}

.empty-icon {
  color: var(--ink-faint);
  margin-bottom: 16px;
}

.empty-state h2 {
  font-size: 18px;
  color: var(--ink);
  margin: 0 0 8px 0;
}

.empty-state p {
  font-size: 14px;
  max-width: 420px;
  margin: 0 0 20px 0;
}

.return-btn {
  text-decoration: none;
}

/* Drawer */
.drawer-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}

.notification-drawer {
  width: 420px;
  max-width: 90vw;
  height: 100%;
  background: var(--surface);
  box-shadow: -8px 0 24px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
}

.drawer-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--line);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.drawer-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.drawer-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mark-all-read-btn {
  background: none;
  border: none;
  color: var(--accent);
  font-size: 13px;
  cursor: pointer;
  padding: 4px 8px;
}

.close-drawer-btn {
  background: none;
  border: none;
  color: var(--ink-faint);
  cursor: pointer;
  padding: 4px;
}

.notification-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
}

.notification-item {
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--line);
  margin-bottom: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.notification-item:hover {
  background: var(--surface-subtle);
}

.notification-item.unread {
  background: var(--accent-soft);
  border-color: #99f6e4;
}

.notif-top {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  margin-bottom: 6px;
}

.notif-actor {
  font-weight: 600;
  color: var(--ink);
}

.notif-type {
  color: var(--accent);
  background: #ccfbf1;
  padding: 1px 6px;
  border-radius: 4px;
}

.notif-time {
  margin-left: auto;
  color: var(--ink-faint);
}

.notif-body {
  font-size: 13px;
  color: var(--ink-soft);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.notif-issue {
  font-weight: 500;
  color: var(--ink);
}

.notif-doc {
  font-size: 12px;
  color: var(--ink-faint);
}

.empty-notifications {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 16px;
  color: var(--ink-faint);
}

@media (max-width: 640px) {
  .task-grid {
    grid-template-columns: 1fr;
  }
}
</style>
