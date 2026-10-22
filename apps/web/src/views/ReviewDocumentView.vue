<template>
  <div class="review-doc-page">
    <!-- Topbar -->
    <header class="topbar review-topbar">
      <div class="topbar-left">
        <router-link to="/reviews" class="topbar-action back-action" title="返回线上评审列表">
          <ArrowLeft :size="15" />
          <span>线上评审</span>
        </router-link>
        <span class="topbar-divider">/</span>
        <div class="doc-title-wrapper">
          <input
            v-if="editingTitle"
            ref="titleInputRef"
            v-model="newTitle"
            class="title-inline-input"
            @blur="saveTitle"
            @keydown.enter="saveTitle"
            @keydown.esc="editingTitle = false"
          />
          <h1
            v-else
            class="doc-title-text"
            :class="{ editable: canEditDocument && !!document }"
            :title="canEditDocument && document ? '点击修改文档标题' : taskDisplayName"
            @click="document ? startEditTitle() : null"
          >
            {{ document?.title || (taskDisplayName !== '线上评审' ? taskDisplayName : (loading ? "加载中..." : "线上评审")) }}
            <Edit3 v-if="canEditDocument && !!document" :size="13" class="edit-icon" />
          </h1>
          <span v-if="!document && !loading" class="badge-pending-init">未创建评审文档</span>
          <span v-if="document?.archivedAt" class="badge-archived">已归档</span>
        </div>
      </div>

      <div class="topbar-center">
        <!-- Revision Selector -->
        <div v-if="document" class="revision-selector">
          <label class="selector-label">版本:</label>
          <select
            :value="currentRevision?.id"
            class="version-select"
            @change="handleSelectRevision(($event.target as HTMLSelectElement).value)"
          >
            <option
              v-for="rev in revisions"
              :key="rev.id"
              :value="rev.id"
            >
              v{{ rev.versionNo }} ({{ rev.summary || rev.changeType }})
            </option>
          </select>
        </div>
        <div v-else class="revision-selector empty-rev-indicator">
          <span class="selector-label">版本:</span>
          <span class="no-rev-text">待创建首个版本</span>
        </div>

        <!-- Round Badge -->
        <div v-if="activeRound" class="round-indicator" :class="'round-' + activeRound.status.toLowerCase()">
          <span class="round-dot"></span>
          <span>{{ roundLabel(activeRound) }} ({{ roundStatusText(activeRound) }})</span>
        </div>
        <div v-else class="round-indicator round-none">
          <span>暂无进行中的评审轮次</span>
        </div>
      </div>

      <div class="topbar-right">
        <button
          class="topbar-action"
          type="button"
          title="发起版本对比或查看版本历史"
          :disabled="!document || revisions.length < 2"
          @click="showRevisionsModal = true"
        >
          <GitCommit :size="15" />
          <span>版本对比</span>
        </button>

        <button
          class="topbar-action"
          type="button"
          title="管理评审轮次与结论"
          :disabled="!document"
          @click="showRoundsModal = true"
        >
          <CheckSquare :size="15" />
          <span>评审轮次</span>
        </button>

        <button
          class="topbar-action"
          type="button"
          title="管理项目成员与发言权限"
          @click="showMembersModal = true"
        >
          <Users :size="15" />
          <span>成员权限</span>
        </button>

        <button
          class="topbar-action export-action"
          type="button"
          title="导出包含正文、问题清单与历史的评审归档包"
          :disabled="!document || exporting"
          @click="handleExport"
        >
          <Download :size="15" />
          <span>{{ exporting ? "导出中..." : "导出评审包" }}</span>
        </button>

        <button
          class="topbar-action primary-ask-btn"
          type="button"
          title="针对当前版本提出新问题或建议"
          :disabled="!document"
          @click="openCreateIssueDialog(null)"
        >
          <PlusCircle :size="15" />
          <span>对本版本提问</span>
        </button>

        <!-- Responsive toggles -->
        <button
          class="topbar-action mobile-only-btn"
          type="button"
          @click="showMobileIssues = !showMobileIssues"
        >
          <MessageSquare :size="15" />
          <span>问题({{ statistics.openIssues }})</span>
        </button>
      </div>
    </header>

    <p v-if="workspaceError" class="error-state workspace-error-banner">{{ workspaceError }}</p>

    <!-- Main Workspace Layout -->
    <div class="workspace-body">
      <!-- Left Panel: TOC -->
      <aside class="left-panel" :class="{ 'mobile-open': showMobileToc }">
        <div class="panel-section-title">
          <BookOpen :size="14" />
          <span>文档目录</span>
        </div>
        <nav class="toc-nav">
          <a
            v-for="item in tocItems"
            :key="item.id"
            :href="'#' + item.id"
            class="toc-link"
            :class="'level-' + item.level"
            @click.prevent="scrollToHeading(item.id)"
          >
            {{ item.text }}
          </a>
          <div v-if="tocItems.length === 0" class="empty-toc">
            <span>正文未检测到标题</span>
          </div>
        </nav>

        <!-- Unlocatable issues banner -->
        <div v-if="unlocatableIssues.length > 0" class="unlocatable-card">
          <div class="unlocatable-header">
            <AlertTriangle :size="14" />
            <span>{{ unlocatableIssues.length }} 个批注因内容修改无法对齐</span>
          </div>
          <div class="unlocatable-list">
            <button
              v-for="u in unlocatableIssues"
              :key="u.id"
              class="unlocatable-item-btn"
              type="button"
              @click="openIssueDetail(u.id)"
            >
              #{{ u.displayNo }} {{ u.title }}
            </button>
          </div>
        </div>
      </aside>

      <!-- Center Panel: Markdown Reader -->
      <main class="center-panel" @mouseup="handleTextSelection">
        <div v-if="loading" class="center-loading">
          <div class="loading-spinner"></div>
          <p>正在载入文档与修订内容...</p>
        </div>

        <!-- Empty state when no review document exists -->
        <div v-else-if="!document" class="no-doc-empty-state">
          <div class="empty-state-card">
            <div class="empty-icon-wrap">
              <FileText :size="48" class="empty-doc-icon" />
            </div>
            <h2 class="empty-state-title">
              《{{ taskDisplayName }}》尚未创建评审文档
            </h2>
            <p class="empty-state-desc">
              线上需求评审基于冻结的文档版本开展协作讨论、行级批注与结论签署。
              您可以将工作台已生成的 PRD 规格材料一键导入为评审基线，或上传/创建新的 Markdown 文档。
            </p>

            <div v-if="myRole === 'OWNER'" class="owner-init-actions">
              <div v-if="availableDrafts.length > 0" class="draft-import-section">
                <div class="section-badge-title">
                  <Sparkles :size="15" />
                  <span>检测到工作台已具备 PRD 规格材料，推荐一键导入：</span>
                </div>
                <div class="draft-cards-list">
                  <div
                    v-for="draft in availableDrafts"
                    :key="draft.id"
                    class="draft-item-card"
                  >
                    <div class="draft-item-info">
                      <FileCode :size="20" class="draft-icon" />
                      <div class="draft-item-texts">
                        <span class="draft-filename" :title="draft.name">{{ draft.name }}</span>
                        <span class="draft-meta">{{ formatFileSize(draft.size) }} · {{ formatTime(draft.createdAt) }}</span>
                      </div>
                    </div>
                    <button
                      class="btn-primary import-action-btn"
                      type="button"
                      :disabled="creatingDoc"
                      @click="handleImportDraft(draft)"
                    >
                      <Upload :size="14" />
                      <span>{{ creatingDoc && importingDraftId === draft.id ? "正在导入..." : "一键导入并发起评审" }}</span>
                    </button>
                  </div>
                </div>
              </div>

              <div class="manual-create-section">
                <span v-if="availableDrafts.length > 0" class="or-separator">或者</span>
                <button
                  class="btn-secondary manual-create-btn"
                  type="button"
                  @click="openCreateDocModal"
                >
                  <Plus :size="15" />
                  <span>新建空白文档 / 上传本地 Markdown 文件</span>
                </button>
              </div>
            </div>

            <div v-else class="non-owner-empty-hint">
              <Clock :size="20" class="hint-clock-icon" />
              <span>当前任务尚未由所有者（OWNER）初始化评审文档。请联系所有者发起评审。</span>
            </div>
          </div>
        </div>

        <article v-else class="markdown-article">
          <!-- Selection Floating Tooltip -->
          <div
            v-if="selectionTooltip.visible"
            class="selection-floating-tooltip"
            :style="{ top: selectionTooltip.y + 'px', left: selectionTooltip.x + 'px' }"
            @mousedown.stop
          >
            <button
              class="tooltip-ask-btn"
              type="button"
              @click="handleCreateIssueFromSelection"
            >
              <MessageSquarePlus :size="14" />
              <span>对此内容提问</span>
            </button>
          </div>

          <div
            ref="articleContentRef"
            class="article-rendered-content"
            v-html="renderedHtml"
            @click="handleArticleClick"
          ></div>
        </article>
      </main>

      <!-- Right Panel: Issues List & Filter -->
      <aside class="right-panel" :class="{ 'mobile-open': showMobileIssues }">
        <div class="panel-header">
          <div class="stats-overview">
            <div class="stat-box">
              <span class="stat-num">{{ statistics.totalIssues }}</span>
              <span class="stat-label">总问题</span>
            </div>
            <div class="stat-box highlight">
              <span class="stat-num">{{ statistics.openIssues }}</span>
              <span class="stat-label">待处理</span>
            </div>
            <div class="stat-box awaiting">
              <span class="stat-num">{{ statistics.awaitingConfirmationIssues }}</span>
              <span class="stat-label">待确认</span>
            </div>
            <div class="stat-box resolved">
              <span class="stat-num">{{ statistics.resolvedIssues }}</span>
              <span class="stat-label">已解决</span>
            </div>
            <div class="stat-box rate">
              <span class="stat-num">{{ statistics.resolutionRate || '0%' }}</span>
              <span class="stat-label">闭环率</span>
            </div>
          </div>

          <!-- Filters -->
          <div class="issues-filter-bar">
            <div class="filter-search-wrap">
              <Search :size="13" class="filter-search-icon" />
              <input
                v-model="issueKeyword"
                type="text"
                placeholder="搜索问题标题..."
                class="filter-search-input"
                @input="loadIssues"
              />
            </div>
            <div class="filter-dropdowns">
              <select v-model="filterStatus" class="mini-select" @change="loadIssues">
                <option value="">全部状态</option>
                <option value="OPEN">待处理</option>
                <option value="AWAITING_CONFIRMATION">待确认</option>
                <option value="RESOLVED">已解决</option>
                <option value="CANCELLED">已撤销</option>
              </select>
              <select v-model="filterType" class="mini-select" @change="loadIssues">
                <option value="">全部类型</option>
                <option value="QUESTION">提问</option>
                <option value="MISSING">缺失</option>
                <option value="AMBIGUITY">歧义</option>
                <option value="SUGGESTION">建议</option>
              </select>
              <select v-model="filterImpact" class="mini-select" @change="loadIssues">
                <option value="">全部影响</option>
                <option value="BLOCKING">阻塞</option>
                <option value="NORMAL">一般</option>
              </select>
            </div>
          </div>
        </div>

        <!-- Issue Cards List -->
        <div class="issues-scroll-list">
          <div v-if="issuesLoading" class="mini-loading">
            <span>正在更新问题列表...</span>
          </div>

          <div v-else-if="!document" class="empty-issues-box">
            <MessageSquare :size="32" class="empty-check" style="color: var(--ink-faint)" />
            <p>尚未创建评审文档</p>
            <span class="empty-sub">创建文档后即可在正文划词提问</span>
          </div>

          <div v-else-if="issues.length === 0" class="empty-issues-box">
            <CheckCircle2 :size="32" class="empty-check" />
            <p>当前筛选条件下无评审问题</p>
          </div>

          <div
            v-for="issue in issues"
            :key="issue.id"
            class="issue-card"
            :class="{
              active: selectedIssueId === issue.id,
              overdue: issue.overdue,
              'awaiting-reply': issue.awaitingResponse
            }"
            @click="openIssueDetail(issue.id)"
          >
            <div class="issue-card-top">
              <span class="issue-no">#{{ issue.displayNo }}</span>
              <span class="issue-title" :title="issue.title">{{ issue.title }}</span>
              <span class="issue-status-badge" :class="'status-' + issue.status.toLowerCase()">
                {{ statusLabels[issue.status] || issue.status }}
              </span>
            </div>

            <div class="issue-card-tags">
              <span class="type-tag" :class="'type-' + issue.type.toLowerCase()">
                {{ typeLabels[issue.type] || issue.type }}
              </span>
              <span class="impact-tag" :class="'impact-' + issue.impact.toLowerCase()">
                {{ impactLabels[issue.impact] || issue.impact }}
              </span>
              <span v-if="getIssueLineRange(issue)" class="issue-line-badge">
                <Crosshair :size="10" />
                {{ getIssueLineRange(issue)?.label }}
              </span>
              <span v-if="issue.awaitingResponse" class="tag-alert">待答复</span>
              <span v-if="issue.overdue" class="tag-danger">已逾期</span>
            </div>

            <div class="issue-card-footer">
              <span class="assignee-text">责任人: {{ issue.assigneeName }}</span>
              <span class="post-count">
                <MessageSquare :size="12" />
                {{ issue.postCount }}
              </span>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- Issue Detail Drawer / Panel -->
    <div v-if="selectedIssue" class="drawer-backdrop" @click="closeIssueDetail">
      <div class="issue-detail-drawer" @click.stop>
        <div class="detail-header">
          <div class="detail-header-top">
            <span class="detail-issue-no">#{{ selectedIssue.displayNo }}</span>
            <h2 class="detail-issue-title">{{ selectedIssue.title }}</h2>
            <button class="close-btn" type="button" title="关闭抽屉" aria-label="关闭抽屉" @click="closeIssueDetail">
              <X :size="16" />
            </button>
          </div>
          <p v-if="actionError" class="error-state">{{ actionError }}</p>

          <div class="detail-badges-row">
            <span class="issue-status-badge" :class="'status-' + selectedIssue.status.toLowerCase()">
              {{ statusLabels[selectedIssue.status] }}
            </span>
            <span class="type-tag" :class="'type-' + selectedIssue.type.toLowerCase()">
              {{ typeLabels[selectedIssue.type] }}
            </span>
            <span class="impact-tag" :class="'impact-' + selectedIssue.impact.toLowerCase()">
              {{ impactLabels[selectedIssue.impact] }}
            </span>
            <span class="meta-tag">提出于 v{{ selectedIssue.originVersionNo }}</span>
            <button
              class="watch-toggle-btn"
              type="button"
              :class="{ watching: selectedIssue.watching }"
              @click="toggleWatchIssue"
            >
              <Eye :size="13" />
              <span>{{ selectedIssue.watching ? "已关注" : "关注" }}</span>
            </button>
          </div>

          <div class="detail-meta-grid">
            <div>提出人: <strong>{{ selectedIssue.reporterName }}</strong></div>
            <div>
              责任人: <strong>{{ selectedIssue.assigneeName }}</strong>
              <button
                v-if="canSpeak"
                class="btn-transfer"
                type="button"
                title="转派给其他成员"
                @click="openAssignDialog"
              >
                <UserCheck :size="12" />
                <span>转派</span>
              </button>
            </div>
            <div v-if="selectedIssue.responseDueAt">答复截止: {{ formatTime(selectedIssue.responseDueAt) }}</div>
          </div>
        </div>

        <div class="detail-body-scroll">
          <div class="chat-conversation-stream">
            <!-- 1. The Opening Question Message (WeChat Bubble) -->
            <div class="timeline-node chat-message-item question-message">
              <div class="chat-avatar reporter-avatar" :title="selectedIssue.reporterName">
                {{ getAvatarInitial(selectedIssue.reporterName) }}
              </div>
              <div class="chat-message-main">
                <div class="chat-sender-header">
                  <span class="chat-author-name">{{ selectedIssue.reporterName }}</span>
                  <span class="chat-role-badge reporter-badge">提出人</span>
                  <span class="chat-time">{{ formatTime(selectedIssue.createdAt) }}</span>
                </div>

                <div class="chat-bubble question-bubble">
                  <!-- Referenced quote from document (if any) -->
                  <div v-if="selectedIssue.latestAnchor || selectedIssue.originalAnchor" class="bubble-quote-box">
                    <div class="bubble-quote-header">
                      <div class="bubble-quote-tag">
                        <Quote :size="12" />
                        <span v-if="selectedIssueLineRange" class="quote-line-tag">
                          {{ selectedIssueLineRange.label }}
                        </span>
                      </div>
                      <button class="locate-btn mini" type="button" title="在正文中高亮定位此片段" @click="locateAndScrollToAnchor">
                        <Crosshair :size="11" />
                        <span>定位至正文</span>
                      </button>
                    </div>
                    <div
                      class="bubble-quote-text markdown"
                      v-html="renderMarkdown((selectedIssue.latestAnchor || selectedIssue.originalAnchor)?.exactQuote || '')"
                    ></div>
                  </div>

                  <!-- Question Description Body -->
                  <div class="bubble-content markdown" v-html="renderMarkdown(selectedIssue.body)"></div>
                </div>
              </div>
            </div>

            <!-- 2. Subsequent Chat Replies (WeChat Bubbles) -->
            <template v-for="post in chatPosts" :key="post.id">
              <div
                class="timeline-node chat-message-item"
                :class="{ 'official-answer-item': post.kind === 'ANSWER' }"
              >
                <div
                  class="chat-avatar"
                  :class="{
                    'answer-avatar': post.kind === 'ANSWER',
                    'reporter-avatar': post.authorId === selectedIssue.reporterId,
                    'other-avatar': post.kind !== 'ANSWER' && post.authorId !== selectedIssue.reporterId
                  }"
                  :title="post.authorName"
                >
                  {{ getAvatarInitial(post.authorName) }}
                </div>
                <div class="chat-message-main">
                  <div class="chat-sender-header">
                    <span class="chat-author-name">{{ post.authorName }}</span>
                    <!-- Role / Kind Tag -->
                    <span v-if="post.kind === 'ANSWER'" class="chat-role-badge answer-badge">
                      <Check :size="11" /> 官方答复
                    </span>
                    <span v-else-if="post.authorId === selectedIssue.reporterId" class="chat-role-badge reporter-badge">
                      提出人
                    </span>
                    <span v-else-if="post.authorId === selectedIssue.assigneeId" class="chat-role-badge assignee-badge">
                      责任人
                    </span>
                    <span v-else-if="post.kind === 'FOLLOW_UP'" class="chat-role-badge followup-badge">
                      追问
                    </span>
                    <span v-else class="chat-role-badge discussion-badge">
                      讨论
                    </span>
                    <span class="chat-time">{{ formatTime(post.createdAt) }}</span>
                  </div>

                  <div
                    class="chat-bubble"
                    :class="{ 'official-answer-bubble': post.kind === 'ANSWER' }"
                  >
                    <div class="bubble-content markdown" v-html="renderMarkdown(post.body)"></div>
                  </div>
                </div>
              </div>
            </template>

            <!-- 3. Final Milestone Resolution Status (WeChat Centered Pill) -->
            <div v-if="selectedIssue.status === 'RESOLVED'" class="chat-system-notice resolved">
              <CheckCircle2 :size="13" />
              <span>问题已解决 · 提出人已验收闭环</span>
            </div>
            <div v-else-if="selectedIssue.status === 'CANCELLED'" class="chat-system-notice cancelled">
              <XCircle :size="13" />
              <span>问题已撤销</span>
            </div>
            <div v-else-if="selectedIssue.status === 'AWAITING_CONFIRMATION'" class="chat-system-notice awaiting">
              <Clock :size="12" />
              <span>责任人已出具答复 · 待提出人确认解决</span>
            </div>
          </div>
        </div>

        <!-- Detail Actions Footer -->
        <div class="detail-footer">
          <!-- Workflow Buttons -->
          <div v-if="hasWorkflowActions" class="workflow-actions-bar">
            <!-- Remind Assignee (Only Reporter when OPEN and awaiting reply) -->
            <button
              v-if="canRemind"
              class="wf-btn reminder-btn"
              type="button"
              :disabled="remindingAssignee"
              title="提醒责任人及时答复并解决该问题"
              @click="handleRemindAssignee"
            >
              <Bell :size="13" />
              <span>{{ remindingAssignee ? "正在提醒..." : "提醒负责人解决" }}</span>
            </button>

            <!-- Confirm (Strictly Only Reporter can see and confirm) -->
            <button
              v-if="canConfirmResolution"
              class="wf-btn success-btn"
              type="button"
              title="确认责任人方案已满足要求，完成问题闭环"
              @click="handleConfirmResolution"
            >
              <CheckCircle2 :size="14" />
              <span>问题已解决</span>
            </button>

            <!-- Reject (Strictly Only Reporter can reject resolution) -->
            <button
              v-if="canReject"
              class="wf-btn danger-btn"
              type="button"
              title="退回并要求责任人重新处理"
              @click="showRejectDialog = true"
            >
              拒绝确认
            </button>

            <!-- Cancel (Strictly Only Reporter can cancel question) -->
            <button
              v-if="canCancel"
              class="wf-btn neutral-btn cancel-btn"
              type="button"
              title="撤销自己提出的该条问题"
              @click="showCancelDialog = true"
            >
              <XCircle :size="13" />
              <span>撤销问题</span>
            </button>

            <!-- Reopen (Resolved -> Open) -->
            <button
              v-if="selectedIssue.status === 'RESOLVED' && canReopen"
              class="wf-btn warning-btn"
              type="button"
              @click="showReopenDialog = true"
            >
              重新打开
            </button>

            <!-- Restore (Cancelled -> Open) -->
            <button
              v-if="selectedIssue.status === 'CANCELLED' && myRole === 'OWNER'"
              class="wf-btn neutral-btn"
              type="button"
              @click="showRestoreDialog = true"
            >
              恢复问题
            </button>
          </div>

          <!-- Reply / Post Input Box -->
          <div v-if="canSpeak && selectedIssue.status !== 'CANCELLED'" class="reply-box">
            <div class="reply-kind-tabs">
              <button
                class="kind-tab"
                :class="{ active: replyKind === 'DISCUSSION' }"
                type="button"
                @click="replyKind = 'DISCUSSION'"
              >
                讨论
              </button>
              <button
                v-if="canOfficialAnswer"
                class="kind-tab answer-tab"
                :class="{ active: replyKind === 'ANSWER' }"
                type="button"
                @click="replyKind = 'ANSWER'"
              >
                官方答复
              </button>
              <button
                class="kind-tab"
                :class="{ active: replyKind === 'FOLLOW_UP' }"
                type="button"
                @click="replyKind = 'FOLLOW_UP'"
              >
                追问
              </button>
            </div>
            <textarea
              v-model="replyBody"
              rows="3"
              class="reply-textarea"
              :placeholder="replyPlaceholder + ' (Ctrl+Enter 快捷发送)'"
              @keydown.ctrl.enter="submitReply"
              @keydown.meta.enter="submitReply"
            ></textarea>
            <div class="reply-footer">
              <span class="reply-hint">
                {{ replyKind === 'ANSWER' ? '官方答复将记录责任人结论并推进解决流程' : '' }}
              </span>
              <button
                class="run-button reply-submit-btn"
                type="button"
                :disabled="!replyBody.trim() || submittingReply"
                @click="submitReply"
              >
                {{ submittingReply ? "发送中..." : "发送" }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Create Issue Modal -->
    <div v-if="showCreateIssueDialog" class="modal-backdrop" @click="showCreateIssueDialog = false">
      <div class="modal-card issue-create-modal" @click.stop>
        <div class="modal-header">
          <h3>
            <PlusCircle :size="16" />
            <span>提出评审问题 / 建议</span>
          </h3>
          <button class="close-btn" type="button" title="关闭" aria-label="关闭" @click="showCreateIssueDialog = false">
            <X :size="16" />
          </button>
        </div>

        <form class="modal-form-layout" @submit.prevent="submitCreateIssue">
          <!-- Business Error Alert at Top of Form -->
          <div v-if="createIssueError" class="modal-alert-box error">
            <AlertCircle :size="16" />
            <div class="alert-content">
              <span class="alert-msg">{{ createIssueError }}</span>
            </div>
            <button class="alert-close-btn" type="button" @click="createIssueError = ''">
              <X :size="14" />
            </button>
          </div>

          <div class="modal-scroll-content">
            <!-- Scope Mode Switcher -->
            <div class="scope-mode-tabs">
              <button
                type="button"
                class="scope-tab-btn"
                :class="{ active: issueScopeMode === 'LINE_RANGE' }"
                @click="setScopeMode('LINE_RANGE')"
              >
                <AlignLeft :size="13" />
                <span>关联指定正文行号</span>
              </button>
              <button
                type="button"
                class="scope-tab-btn"
                :class="{ active: issueScopeMode === 'GLOBAL' }"
                @click="setScopeMode('GLOBAL')"
              >
                <FileText :size="13" />
                <span>全文整体提问</span>
              </button>
            </div>

            <!-- Line Range Picker when LINE_RANGE is selected -->
            <div v-if="issueScopeMode === 'LINE_RANGE'" class="line-range-selector-panel">
              <div class="line-range-inputs-row">
                <div class="line-input-item">
                  <label>起始行:</label>
                  <input
                    v-model.number="lineRangeStart"
                    type="number"
                    :min="1"
                    :max="totalContentLines"
                    class="line-num-input"
                    required
                  />
                </div>
                <span class="line-range-dash">至</span>
                <div class="line-input-item">
                  <label>结束行:</label>
                  <input
                    v-model.number="lineRangeEnd"
                    type="number"
                    :min="lineRangeStart || 1"
                    :max="totalContentLines"
                    class="line-num-input"
                    required
                  />
                </div>
                <span class="total-lines-hint">共 {{ totalContentLines }} 行正文</span>
              </div>

              <!-- Live Excerpt Preview -->
              <div class="line-quote-preview-box">
                <div class="preview-header">
                  <Quote :size="12" />
                  <span class="preview-title">
                    第 {{ lineRangeStart }} - {{ lineRangeEnd }} 行原文片段预览:
                  </span>
                </div>
                <div
                  class="preview-content markdown"
                  v-html="renderMarkdown(slicedLineQuote || '（所选行内容为空）')"
                ></div>
              </div>
            </div>

            <label class="form-field">
              <span>问题概要 / 标题 <span class="required-star">*</span></span>
              <input
                v-model="newIssueTitle"
                type="text"
                maxlength="255"
                placeholder="简要概括问题或修改建议"
                required
              />
            </label>

            <div class="form-row">
              <label class="form-field">
                <span>类型 <span class="required-star">*</span></span>
                <select v-model="newIssueType" required>
                  <option value="QUESTION">提问</option>
                  <option value="MISSING">缺失</option>
                  <option value="AMBIGUITY">歧义</option>
                  <option value="SUGGESTION">建议</option>
                </select>
              </label>

              <label class="form-field">
                <span>影响级别 <span class="required-star">*</span></span>
                <select v-model="newIssueImpact" required>
                  <option value="BLOCKING">阻塞 (BLOCKING)</option>
                  <option value="NORMAL">一般 (NORMAL)</option>
                </select>
              </label>

              <label class="form-field">
                <span>责任人</span>
                <select v-model="newIssueAssigneeId">
                  <option v-if="ownerMember" :value="ownerMember.userId">
                    {{ ownerMember.displayName }} (需求创建人 / PRD生成人员)
                  </option>
                  <option
                    v-for="m in members.filter(m => m.userId !== ownerMember?.userId)"
                    :key="m.userId"
                    :value="m.userId"
                  >
                    {{ m.displayName }} ({{ roleLabels[m.role] }})
                  </option>
                  <option v-if="!ownerMember && !members.length" value="">
                    默认 (需求创建人 / PRD生成人员)
                  </option>
                </select>
              </label>
            </div>

            <label class="form-field">
              <span>详细说明 <span class="required-star">*</span></span>
              <textarea
                v-model="newIssueBody"
                rows="4"
                placeholder="详细阐述为什么存疑、业务影响或改进建议..."
                required
              ></textarea>
            </label>
          </div>

          <div class="modal-actions">
            <button class="secondary-button" type="button" @click="showCreateIssueDialog = false">
              取消
            </button>
            <button class="run-button" type="submit" :disabled="submittingIssue">
              {{ submittingIssue ? "提交中..." : "提交评审问题" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- Rejection Reason Modal -->
    <div v-if="showRejectDialog" class="modal-backdrop" @click="showRejectDialog = false">
      <div class="modal-card" @click.stop>
        <div class="modal-header">
          <h3>拒绝解决确认</h3>
          <button class="close-btn" type="button" @click="showRejectDialog = false"><X :size="16" /></button>
        </div>
        <form @submit.prevent="submitRejectResolution">
          <p class="modal-prompt">问题将退回“待处理”状态，并要求责任人重新答复。</p>
          <label class="form-field">
            <span>拒绝原因 <span class="required-star">*</span></span>
            <textarea v-model="actionReason" rows="3" placeholder="说明为什么方案未达到预期..." required></textarea>
          </label>
          <label v-if="selectedIssue?.canProxyConfirm && !selectedIssue?.canConfirm" class="form-field">
            <span>所有者代办原因 <span class="required-star">*</span></span>
            <input v-model="proxyReason" type="text" placeholder="原提出者已无法操作代办原因..." required />
          </label>
          <div class="modal-actions">
            <button class="secondary-button" type="button" @click="showRejectDialog = false">取消</button>
            <button class="run-button danger-btn" type="submit" :disabled="submittingAction">确认退回</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Reopen Modal -->
    <div v-if="showReopenDialog" class="modal-backdrop" @click="showReopenDialog = false">
      <div class="modal-card" @click.stop>
        <div class="modal-header">
          <h3>重新打开已解决问题</h3>
          <button class="close-btn" type="button" @click="showReopenDialog = false"><X :size="16" /></button>
        </div>
        <form @submit.prevent="submitReopen">
          <label class="form-field">
            <span>重开原因 <span class="required-star">*</span></span>
            <textarea v-model="actionReason" rows="3" placeholder="说明为何需要重新处理..." required></textarea>
          </label>
          <div class="modal-actions">
            <button class="secondary-button" type="button" @click="showReopenDialog = false">取消</button>
            <button class="run-button" type="submit" :disabled="submittingAction">确认重开</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Cancel Modal -->
    <div v-if="showCancelDialog" class="modal-backdrop" @click="showCancelDialog = false">
      <div class="modal-card" @click.stop>
        <div class="modal-header">
          <h3>撤销问题</h3>
          <button class="close-btn" type="button" @click="showCancelDialog = false"><X :size="16" /></button>
        </div>
        <form @submit.prevent="submitCancel">
          <label class="form-field">
            <span>撤销原因 <span class="required-star">*</span></span>
            <textarea v-model="actionReason" rows="3" placeholder="说明撤销原因或处置方式..." required></textarea>
          </label>
          <div class="modal-actions">
            <button class="secondary-button" type="button" @click="showCancelDialog = false">取消</button>
            <button class="run-button" type="submit" :disabled="submittingAction">确认撤销</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Restore Modal -->
    <div v-if="showRestoreDialog" class="modal-backdrop" @click="showRestoreDialog = false">
      <div class="modal-card" @click.stop>
        <div class="modal-header">
          <h3>恢复已撤销问题</h3>
          <button class="close-btn" type="button" @click="showRestoreDialog = false"><X :size="16" /></button>
        </div>
        <form @submit.prevent="submitRestore">
          <label class="form-field">
            <span>恢复原因 <span class="required-star">*</span></span>
            <textarea v-model="actionReason" rows="3" placeholder="说明恢复原因..." required></textarea>
          </label>
          <div class="modal-actions">
            <button class="secondary-button" type="button" @click="showRestoreDialog = false">取消</button>
            <button class="run-button" type="submit" :disabled="submittingAction">确认恢复</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Assign Modal -->
    <div v-if="showAssignDialog" class="modal-backdrop" @click="showAssignDialog = false">
      <div class="modal-card" @click.stop>
        <div class="modal-header">
          <h3>转派责任人</h3>
          <button class="close-btn" type="button" @click="showAssignDialog = false"><X :size="16" /></button>
        </div>
        <form @submit.prevent="submitAssign">
          <label class="form-field">
            <span>新责任人 <span class="required-star">*</span></span>
            <select v-model="assignTargetUserId" required>
              <option v-for="m in members" :key="m.userId" :value="m.userId">
                {{ m.displayName }} ({{ roleLabels[m.role] }})
              </option>
            </select>
          </label>
          <label class="form-field">
            <span>转派理由</span>
            <input v-model="assignReason" type="text" placeholder="可选..." />
          </label>
          <div class="modal-actions">
            <button class="secondary-button" type="button" @click="showAssignDialog = false">取消</button>
            <button class="run-button" type="submit" :disabled="submittingAction">确认转派</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Revisions & Diff Modal -->
    <div v-if="showRevisionsModal" class="modal-backdrop" @click="showRevisionsModal = false">
      <div class="modal-card wide-modal" @click.stop>
        <div class="modal-header">
          <h3><GitCommit :size="16" /> 版本历史与基线对比</h3>
          <button class="close-btn" type="button" @click="showRevisionsModal = false"><X :size="16" /></button>
        </div>

        <div class="diff-control-bar">
          <div class="diff-select-group">
            <label>基准版本:</label>
            <select v-model="diffBaseId" class="mini-select">
              <option v-for="r in revisions" :key="r.id" :value="r.id">v{{ r.versionNo }}</option>
            </select>
            <span class="diff-arrow">→</span>
            <label>对比版本:</label>
            <select v-model="diffTargetId" class="mini-select">
              <option v-for="r in revisions" :key="r.id" :value="r.id">v{{ r.versionNo }}</option>
            </select>
            <button class="secondary-button" type="button" @click="loadDiff">查看差异</button>
          </div>

          <button
            v-if="canEditDocument"
            class="run-button"
            type="button"
            @click="showCreateRevisionForm = !showCreateRevisionForm"
          >
            {{ showCreateRevisionForm ? "收起新版本表单" : "发布新修订版本" }}
          </button>
        </div>

        <!-- New Revision Form -->
        <div v-if="showCreateRevisionForm" class="new-revision-box">
          <h4>发布正文新版本</h4>
          <form @submit.prevent="submitCreateRevision">
            <div class="form-row">
              <label class="form-field">
                <span>修订类型 <span class="required-star">*</span></span>
                <select v-model="newRevChangeType" required>
                  <option value="MINOR">小版本修改 (MINOR)</option>
                  <option value="MAJOR">重大调整 (MAJOR)</option>
                  <option value="HOTFIX">热修复 (HOTFIX)</option>
                </select>
              </label>
              <label class="form-field">
                <span>修改概要 <span class="required-star">*</span></span>
                <input v-model="newRevSummary" type="text" placeholder="简要说明修改内容" required />
              </label>
            </div>
            <label class="form-field">
              <span>正文 Markdown 内容 <span class="required-star">*</span></span>
              <textarea v-model="newRevContent" rows="8" class="code-font" required></textarea>
            </label>
            <div class="modal-actions">
              <button class="secondary-button" type="button" @click="showCreateRevisionForm = false">取消</button>
              <button class="run-button" type="submit" :disabled="submittingRevision">保存并发布</button>
            </div>
          </form>
        </div>

        <!-- Diff Viewer -->
        <div v-if="diffResult" class="diff-result-container">
          <div class="diff-stats-header">
            <span>+{{ diffAddedCount }} 行新增</span>
            <span>-{{ diffDeletedCount }} 行删除</span>
          </div>
          <pre class="diff-pre"><code>{{ formattedDiffText }}</code></pre>
        </div>
      </div>
    </div>

    <!-- Review Rounds Modal -->
    <div v-if="showRoundsModal" class="modal-backdrop" @click="showRoundsModal = false">
      <div class="modal-card" @click.stop>
        <div class="modal-header">
          <h3><CheckSquare :size="16" /> 评审轮次管理</h3>
          <button class="close-btn" type="button" @click="showRoundsModal = false"><X :size="16" /></button>
        </div>

        <div class="rounds-list">
          <div v-for="rnd in rounds" :key="rnd.id" class="round-card">
            <div class="round-card-top">
              <span class="round-title">{{ roundLabel(rnd) }}</span>
              <span class="round-badge" :class="'round-' + rnd.status.toLowerCase()">
                {{ roundStatusText(rnd) }}
              </span>
            </div>
            <div class="round-meta">
              <span>基线版本: v{{ rnd.baselineVersionNo }}</span>
              <span>发起时间: {{ formatTime(rnd.createdAt) }}</span>
              <span v-if="rnd.closedAt">结束时间: {{ formatTime(rnd.closedAt) }}</span>
            </div>
            <div v-if="rnd.conclusion" class="round-conclusion">
              <strong>结论说明：</strong>{{ rnd.conclusion }}
            </div>
            <div v-if="rnd.status === 'OPEN' && myRole === 'OWNER'" class="round-actions">
              <button class="run-button success-btn" type="button" @click="openCloseRoundDialog(rnd)">
                结束本轮评审
              </button>
              <button class="secondary-button" type="button" @click="cancelCurrentRound(rnd)">
                取消本轮
              </button>
            </div>
          </div>
        </div>

        <div v-if="!activeRound && myRole === 'OWNER'" class="start-round-box">
          <button class="run-button" type="button" @click="startNewRound">
            发起新一轮评审
          </button>
        </div>
      </div>
    </div>

    <!-- Close Round Dialog -->
    <div v-if="showCloseRoundModal" class="modal-backdrop" @click="showCloseRoundModal = false">
      <div class="modal-card" @click.stop>
        <div class="modal-header">
          <h3>结束评审轮次</h3>
          <button class="close-btn" type="button" @click="showCloseRoundModal = false"><X :size="16" /></button>
        </div>
        <form @submit.prevent="submitCloseRound">
          <label class="form-field">
            <span>评审结论 <span class="required-star">*</span></span>
            <select v-model="closeConclusion" required>
              <option value="PASS" :disabled="unresolvedIssueTotal > 0">
                通过 (PASS) {{ unresolvedIssueTotal > 0 ? "- 仍有未闭环问题不可选择" : "" }}
              </option>
              <option value="PASS_WITH_RESIDUAL">
                附条件通过 (PASS_WITH_RESIDUAL)
              </option>
              <option value="REJECT">
                暂不通过 (REJECT)
              </option>
            </select>
          </label>

          <label class="form-field">
            <span>评审纪要与总评 <span class="required-star">*</span></span>
            <textarea v-model="closeSummary" rows="3" placeholder="总结本轮评审结论与后续安排..." required></textarea>
          </label>

          <p v-if="closeRoundError" class="error-state">{{ closeRoundError }}</p>
          <div v-if="closeConclusion === 'PASS_WITH_RESIDUAL' || closeConclusion === 'REJECT'" class="residual-list">
            <h4>遗留问题处置说明 (必须逐条填写处置意见):</h4>
            <div
              v-for="unres in issues.filter(i => i.status === 'OPEN' || i.status === 'AWAITING_CONFIRMATION')"
              :key="unres.id"
              class="residual-item"
            >
              <span>#{{ unres.displayNo }} {{ unres.title }}</span>
              <input
                v-model="unresolvedDispositions[unres.id]"
                type="text"
                placeholder="处置方案 (例如：已建立JIRA-120追踪)"
                required
              />
            </div>
          </div>

          <div class="modal-actions">
            <button class="secondary-button" type="button" @click="showCloseRoundModal = false">取消</button>
            <button class="run-button" type="submit">确认结束轮次</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Members Management Modal -->
    <div v-if="showMembersModal" class="modal-backdrop" @click="showMembersModal = false">
      <div class="modal-card" @click.stop>
        <div class="modal-header">
          <h3><Users :size="16" /> 项目成员与权限分配</h3>
          <button class="close-btn" type="button" @click="showMembersModal = false"><X :size="16" /></button>
        </div>

        <div class="members-table-wrap">
          <table class="members-table">
            <thead>
              <tr>
                <th>成员姓名</th>
                <th>用户名</th>
                <th>角色</th>
                <th v-if="myRole === 'OWNER'">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="m in members" :key="m.userId">
                <td><strong>{{ m.displayName }}</strong></td>
                <td>@{{ m.username }}</td>
                <td>
                  <span class="role-badge" :class="'role-' + m.role.toLowerCase()">
                    {{ roleLabels[m.role] }}
                  </span>
                </td>
                <td v-if="myRole === 'OWNER'">
                  <button
                    v-if="m.role !== 'OWNER'"
                    class="mini-btn danger"
                    type="button"
                    @click="handleRemoveMember(m)"
                  >
                    移除
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="myRole === 'OWNER'" class="add-member-form">
          <div class="add-member-header">
            <h4><UserPlus :size="15" /> 添加评审成员</h4>
            <span class="add-member-tip">支持按账号或姓名模糊检索</span>
          </div>

          <div v-if="memberAddError" class="member-add-error">
            <AlertCircle :size="14" />
            <span>{{ memberAddError }}</span>
          </div>

          <form class="member-search-form" @submit.prevent="submitAddMember">
            <div class="user-autocomplete-wrapper">
              <div class="search-input-box">
                <Search :size="14" class="search-icon" />
                <input
                  v-model="userSearchQuery"
                  type="text"
                  class="member-search-input"
                  placeholder="搜索用户名或姓名 (如 admin、余...)"
                  autocomplete="off"
                  @input="handleCandidateInput"
                  @focus="handleCandidateFocus"
                />
                <button
                  v-if="userSearchQuery"
                  type="button"
                  class="clear-candidate-btn"
                  @click="clearCandidateSelection"
                  title="清空"
                >
                  <X :size="13" />
                </button>
              </div>

              <!-- Candidate dropdown popover -->
              <div
                v-if="showCandidateDropdown"
                class="candidate-dropdown"
              >
                <div v-if="candidateLoading" class="candidate-state-msg">
                  <Loader2 :size="14" class="spinning" />
                  <span>正在匹配用户...</span>
                </div>
                <div v-else-if="candidateUsers.length === 0" class="candidate-state-msg empty">
                  <span>未找到匹配的待添加成员</span>
                </div>
                <ul v-else class="candidate-list">
                  <li
                    v-for="c in candidateUsers"
                    :key="c.userId"
                    class="candidate-item"
                    :class="{ active: selectedCandidate?.userId === c.userId }"
                    @click="selectCandidate(c)"
                  >
                    <div class="candidate-avatar">
                      {{ (c.displayName || c.username).slice(0, 1).toUpperCase() }}
                    </div>
                    <div class="candidate-info">
                      <div class="candidate-name">{{ c.displayName }}</div>
                      <div class="candidate-handle">@{{ c.username }}</div>
                    </div>
                    <CheckSquare v-if="selectedCandidate?.userId === c.userId" :size="14" class="selected-mark" />
                  </li>
                </ul>
              </div>
            </div>

            <select v-model="newMemberRole" class="member-role-select">
              <option value="REVIEWER">评审者 (可发言讨论)</option>
              <option value="EDITOR">编辑者 (可修改PRD与答复)</option>
              <option value="VIEWER">观察者 (仅只读)</option>
            </select>

            <button class="run-button add-btn" type="submit" :disabled="submittingMember">
              <Loader2 v-if="submittingMember" :size="14" class="spinning" />
              <UserPlus v-else :size="14" />
              <span>{{ submittingMember ? "添加中..." : "确认添加" }}</span>
            </button>
          </form>
        </div>
      </div>
    </div>

    <!-- Create Review Document Modal -->
    <div v-if="showCreateDocModal" class="modal-backdrop" @click.self="showCreateDocModal = false">
      <div class="modal-card create-doc-modal-card" @click.stop>
        <div class="modal-header">
          <h3><FilePlus :size="16" /> 新建需求评审文档</h3>
          <button class="close-btn" type="button" @click="showCreateDocModal = false"><X :size="16" /></button>
        </div>

        <div class="modal-body-scroll">
          <div class="form-field">
            <label class="field-label required">评审文档标题</label>
            <input
              v-model="newDocTitle"
              type="text"
              class="form-input"
              placeholder="请输入文档标题，如：财务凭证管理系统 PRD 需求文档"
              maxlength="200"
            />
          </div>

          <div class="form-field">
            <div class="form-label-row">
              <label class="field-label required">文档正文 (Markdown 格式)</label>
              <button
                class="btn-text-action"
                type="button"
                @click="triggerFileInput"
              >
                <Upload :size="13" />
                <span>从本地 .md 文件载入</span>
              </button>
              <input
                ref="fileInputRef"
                type="file"
                accept=".md,.markdown,.txt"
                style="display: none"
                @change="handleFileSelected"
              />
            </div>
            <textarea
              v-model="newDocContent"
              class="form-textarea code-font"
              rows="14"
              placeholder="# 1. 业务背景与目标&#10;&#10;详细描述本次评审的需求内容与设计方案..."
            ></textarea>
            <div class="char-count-row">
              <span>已输入 {{ newDocContent.length }} 字符</span>
            </div>
          </div>

          <div v-if="createDocError" class="modal-error-msg">
            <AlertTriangle :size="14" />
            <span>{{ createDocError }}</span>
          </div>
        </div>

        <div class="modal-footer">
          <button
            class="cancel-btn"
            type="button"
            :disabled="creatingDoc"
            @click="showCreateDocModal = false"
          >
            取消
          </button>
          <button
            class="run-button"
            type="button"
            :disabled="creatingDoc || !newDocTitle.trim() || !newDocContent.trim()"
            @click="submitCreateReviewDoc"
          >
            {{ creatingDoc ? "正在创建..." : "立即创建并进入评审" }}
          </button>
        </div>
      </div>
    </div>

    <!-- Reminder Notification Toast -->
    <transition name="toast-fade">
      <div v-if="reminderSuccessToast" class="reminder-toast">
        <CheckCircle2 :size="16" />
        <span>{{ reminderSuccessToast }}</span>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import {
  AlertCircle,
  AlertTriangle,
  AlignLeft,
  ArrowLeft,
  Bell,
  BookOpen,
  CheckCircle2,
  CheckSquare,
  Clock,
  Crosshair,
  Download,
  Edit3,
  Eye,
  FileCode,
  FilePlus,
  FileText,
  GitCommit,
  MessageSquare,
  MessageSquarePlus,
  Plus,
  PlusCircle,
  Quote,
  Search,
  Sparkles,
  Upload,
  UserCheck,
  UserPlus,
  Users,
  Loader2,
  Check,
  X,
  XCircle,
} from "lucide-vue-next";
import {
  addMember,
  searchCandidateUsers,
  type ReviewCandidateUserDetail,
  assignIssue,
  cancelRound,
  closeRound,
  compareRevisions,
  createIssue,
  createPost,
  createReviewDocument,
  createRevision,
  createRound,
  downloadReviewExport,
  executeIssueAction,
  fetchIssue,
  fetchIssues,
  fetchMembers,
  fetchReviewDocument,
  fetchReviewTaskDocuments,
  fetchReviewTasks,
  fetchRevisions,
  fetchRounds,
  fetchTimeline,
  importReviewDocument,
  removeMember,
  updateReviewDocumentTitle,
  watchIssue,
  unwatchIssue,
  type AnchorInput,
  type ReviewAnchorDetail,
  type ReviewDiffResponse,
  type ReviewDocumentDetail,
  type ReviewDocumentSummary,
  type ReviewIssueDetail,
  type ReviewIssueSummary,
  type ReviewMemberDetail,
  type ReviewRevisionDetail,
  type ReviewRoundDetail,
  type ReviewStatistics,
  type ReviewTimelineItem,
} from "../reviewApi";
import { renderMarkdown, renderMarkdownWithAnchors } from "../markdown";
import { renderMermaidInContainer } from "../mermaidRenderer";
import type { TocItem } from "../toc";
import {
  computeFingerprint,
  computeSelectionAnchor,
  createDomRangeFromOffsets,
  findLineRangeForSelection,
  locateAnchorInText,
} from "../reviewAnnotationAdapter";
import { getUser } from "../session";
import { apiErrorMessage, getDraftSource, type Source } from "../api";

const route = useRoute();
const currentUser = ref(getUser());

const taskId = computed(() => String(route.params.taskId || ""));
const docIdParam = computed(() => {
  const raw = String(route.params.documentId || "");
  if (!raw || raw === "null" || raw === "undefined") return "";
  return raw;
});
const currentTaskId = computed(() => document.value?.taskId || taskId.value);

const loading = ref(true);
const document = ref<ReviewDocumentSummary | null>(null);
const currentRevision = ref<ReviewRevisionDetail | null>(null);
const myRole = ref<"OWNER" | "EDITOR" | "REVIEWER" | "VIEWER">("VIEWER");
const activeRound = ref<ReviewRoundDetail | null>(null);
const revisions = ref<ReviewRevisionDetail[]>([]);
const rounds = ref<ReviewRoundDetail[]>([]);
const members = ref<ReviewMemberDetail[]>([]);
const ownerMember = computed(() => members.value.find((m) => m.role === "OWNER"));

// Title inline editing
const editingTitle = ref(false);
const newTitle = ref("");
const titleInputRef = ref<HTMLInputElement | null>(null);
const reviewTaskTitle = ref("");

const taskDisplayName = computed(() => {
  return document.value?.title || reviewTaskTitle.value || "线上评审";
});

// Create / Import Document Modal State
const showCreateDocModal = ref(false);
const newDocTitle = ref("");
const newDocContent = ref("");
const creatingDoc = ref(false);
const importingDraftId = ref("");
const createDocError = ref("");
const fileInputRef = ref<HTMLInputElement | null>(null);

const availableDrafts = computed(() => [] as Source[]);

// Center Reader & Anchors
const articleContentRef = ref<HTMLElement | null>(null);
const renderedHtml = ref("");
const tocItems = ref<TocItem[]>([]);
const selectionTooltip = ref({ visible: false, x: 0, y: 0, quote: "" });
const pendingAnchor = ref<AnchorInput | null>(null);
const unlocatableIssues = ref<ReviewIssueSummary[]>([]);

// Issues List & Filter
const issues = ref<ReviewIssueSummary[]>([]);
const issuesLoading = ref(false);
const issueKeyword = ref("");
const filterStatus = ref("");
const filterType = ref("");
const filterImpact = ref("");
const statistics = ref<ReviewStatistics>({
  totalIssues: 0,
  openIssues: 0,
  awaitingConfirmationIssues: 0,
  resolvedIssues: 0,
  cancelledIssues: 0,
  resolutionRate: "0%",
});

// Selected Issue Detail Drawer
const selectedIssueId = ref<string | null>(null);
const selectedIssue = ref<ReviewIssueDetail | null>(null);
const timelineItems = ref<ReviewTimelineItem[]>([]);
const chatPosts = computed(() => {
  return timelineItems.value
    .filter(item => (item.kind || item.itemType) === "POST" && item.post)
    .map(item => item.post!);
});

function getAvatarInitial(name?: string): string {
  if (!name) return "?";
  const trimmed = name.trim();
  return trimmed.charAt(0).toUpperCase();
}
const replyKind = ref<"DISCUSSION" | "ANSWER" | "FOLLOW_UP">("DISCUSSION");
const replyBody = ref("");
const submittingReply = ref(false);

// Modals
const showMobileToc = ref(false);
const showMobileIssues = ref(false);
const showCreateIssueDialog = ref(false);
const showRevisionsModal = ref(false);
const showRoundsModal = ref(false);
const showCloseRoundModal = ref(false);
const showMembersModal = ref(false);
const showProposeDialog = ref(false);
const showRejectDialog = ref(false);
const showReopenDialog = ref(false);
const showCancelDialog = ref(false);
const showRestoreDialog = ref(false);
const showAssignDialog = ref(false);
const exporting = ref(false);

// Create Issue Form State
const newIssueTitle = ref("");
const newIssueType = ref<"QUESTION" | "MISSING" | "AMBIGUITY" | "SUGGESTION">("QUESTION");
const newIssueImpact = ref<"BLOCKING" | "NORMAL">("NORMAL");
const newIssueAssigneeId = ref("");
const newIssueBody = ref("");
const submittingIssue = ref(false);
const createIssueError = ref("");
const actionError = ref("");
const closeRoundError = ref("");
const workspaceError = ref("");

const issueScopeMode = ref<"GLOBAL" | "LINE_RANGE">("GLOBAL");
const lineRangeStart = ref<number>(1);
const lineRangeEnd = ref<number>(1);

const docLines = computed(() => (currentRevision.value?.content || "").split("\n"));
const totalContentLines = computed(() => Math.max(1, docLines.value.length));

const slicedLineQuote = computed(() => {
  if (!docLines.value.length) return "";
  const s = Math.max(1, Math.min(lineRangeStart.value || 1, totalContentLines.value));
  const e = Math.max(s, Math.min(lineRangeEnd.value || s, totalContentLines.value));
  return docLines.value.slice(s - 1, e).join("\n");
});

const selectedIssueLineRange = computed(() => {
  if (!selectedIssue.value) return null;
  const anc = selectedIssue.value.latestAnchor || selectedIssue.value.originalAnchor;
  if (!anc) return null;
  return getAnchorLineRange(anc);
});

function getAnchorLineRange(anc?: ReviewAnchorDetail | AnchorInput | null): { startLine: number; endLine: number; label: string } | null {
  if (!anc) return null;
  if (anc.selectorVersion && anc.selectorVersion.startsWith("lines:")) {
    const match = anc.selectorVersion.match(/^lines:(\d+)(?:-(\d+))?$/);
    if (match) {
      const s = parseInt(match[1], 10);
      const e = match[2] ? parseInt(match[2], 10) : s;
      return { startLine: s, endLine: e, label: s === e ? `第 ${s} 行` : `第 ${s} - ${e} 行` };
    }
  }
  const quote = anc.exactQuote;
  const content = currentRevision.value?.content || "";
  if (!quote || !content) return null;
  const idx = content.indexOf(quote);
  if (idx !== -1) {
    const startLine = content.slice(0, idx).split("\n").length;
    const endLine = startLine + quote.split("\n").length - 1;
    return {
      startLine,
      endLine,
      label: startLine === endLine ? `第 ${startLine} 行` : `第 ${startLine} - ${endLine} 行`,
    };
  }
  return null;
}

function getIssueLineRange(issue: ReviewIssueSummary): { startLine: number; endLine: number; label: string } | null {
  if (selectedIssue.value?.id === issue.id && selectedIssueLineRange.value) {
    return selectedIssueLineRange.value;
  }
  return null;
}

// Propose Resolution Form State
const proposeKind = ref<"DOC_CHANGED" | "NO_CHANGE">("NO_CHANGE");
const proposeRevisionId = ref("");
const proposeSummary = ref("");
const proposeExplanation = ref("");

// Generic Action Form State
const actionReason = ref("");
const proxyReason = ref("");
const submittingAction = ref(false);
const assignTargetUserId = ref("");
const assignReason = ref("");

// Revisions & Diff Form State
const diffBaseId = ref("");
const diffTargetId = ref("");
const diffResult = ref<ReviewDiffResponse | null>(null);
const showCreateRevisionForm = ref(false);
const newRevChangeType = ref<"MINOR" | "MAJOR" | "HOTFIX">("MINOR");
const newRevSummary = ref("");
const newRevContent = ref("");
const submittingRevision = ref(false);

// Close Round Form State
const closeConclusion = ref<"PASS" | "PASS_WITH_RESIDUAL" | "REJECT">("PASS");
const closeSummary = ref("");
const unresolvedDispositions = ref<Record<string, string>>({});

// Add Member Form State with Autocomplete
const userSearchQuery = ref("");
const candidateUsers = ref<ReviewCandidateUserDetail[]>([]);
const candidateLoading = ref(false);
const showCandidateDropdown = ref(false);
const selectedCandidate = ref<ReviewCandidateUserDetail | null>(null);
const memberAddError = ref("");
const newMemberRole = ref<"EDITOR" | "REVIEWER" | "VIEWER">("REVIEWER");

// Label Mappings
const roleLabels: Record<string, string> = {
  OWNER: "所有者",
  EDITOR: "编辑者",
  REVIEWER: "评审者",
  VIEWER: "观察者",
};

const statusLabels: Record<string, string> = {
  OPEN: "待处理",
  AWAITING_CONFIRMATION: "待确认",
  RESOLVED: "已解决",
  CANCELLED: "已撤销",
};

const typeLabels: Record<string, string> = {
  QUESTION: "提问",
  MISSING: "缺失",
  AMBIGUITY: "歧义",
  SUGGESTION: "建议",
};

const impactLabels: Record<string, string> = {
  BLOCKING: "阻塞",
  NORMAL: "一般",
};

const postKindLabels: Record<string, string> = {
  QUESTION: "提问",
  ANSWER: "官方答复",
  FOLLOW_UP: "追加提问",
  DISCUSSION: "讨论",
};

const resolutionKindLabels: Record<string, string> = {
  DOC_CHANGED: "正文已修订",
  NO_CHANGE: "经确认无需修改",
  DEFERRED: "延后至下阶段",
  OTHER: "其他说明",
};

// Computed Permissions
const canSpeak = computed(() => myRole.value !== "VIEWER");
const canEditDocument = computed(() => myRole.value === "OWNER" || myRole.value === "EDITOR");
const unresolvedIssueTotal = computed(() => {
  return (document.value?.unresolvedIssueCount ?? 0) ||
    statistics.value.openIssues + statistics.value.awaitingConfirmationIssues;
});

const issueResolution = computed(() => {
  return selectedIssue.value?.proposedResolution || selectedIssue.value?.resolution || null;
});

const diffAddedCount = computed(() => (diffResult.value?.lines || []).filter((l) => l.type === "ADD").length);
const diffDeletedCount = computed(() => (diffResult.value?.lines || []).filter((l) => l.type === "DELETE").length);
const formattedDiffText = computed(() => {
  if (!diffResult.value) return "";
  return (diffResult.value.lines || [])
    .map((line) => {
      if (line.type === "ADD") return `+ ${line.content}`;
      if (line.type === "DELETE") return `- ${line.content}`;
      return `  ${line.content}`;
    })
    .join("\n");
});

const isReporter = computed(() => {
  if (!selectedIssue.value || !currentUser.value?.id) return false;
  return currentUser.value.id === selectedIssue.value.reporterId;
});

const canOfficialAnswer = computed(() => {
  if (!selectedIssue.value) return false;
  const uid = currentUser.value?.id;
  if (uid === selectedIssue.value.reporterId) return false;
  return myRole.value === "OWNER" || uid === selectedIssue.value.assigneeId;
});

const canProposeResolution = computed(() => {
  if (!selectedIssue.value) return false;
  if (selectedIssue.value.status !== "OPEN") return false;
  if (!canOfficialAnswer.value) return false;
  return selectedIssue.value.lastAnswerSeq >= selectedIssue.value.lastQuestionSeq;
});

const canConfirmResolution = computed(() => {
  if (!selectedIssue.value || !isReporter.value) return false;
  const status = selectedIssue.value.status;
  if (status === "AWAITING_CONFIRMATION") return true;
  if (status === "OPEN") {
    // If there is any reply from assignee or other members
    return (
      (selectedIssue.value.lastAnswerSeq || 0) > 0 ||
      timelineItems.value.some((item) => item.kind === "POST" && item.post?.authorId !== selectedIssue.value?.reporterId)
    );
  }
  return false;
});

const canCancel = computed(() => {
  if (!selectedIssue.value || !isReporter.value) return false;
  return selectedIssue.value.status === "OPEN";
});

const canRemind = computed(() => {
  if (!selectedIssue.value || !isReporter.value) return false;
  if (selectedIssue.value.status !== "OPEN") return false;
  return !timelineItems.value || timelineItems.value.filter((i) => i.kind === "POST").length === 0 || selectedIssue.value.awaitingResponse;
});

const canReject = computed(() => {
  if (!selectedIssue.value || !isReporter.value) return false;
  return selectedIssue.value.status === "AWAITING_CONFIRMATION";
});

const canReopen = computed(() => {
  if (!selectedIssue.value) return false;
  if (myRole.value === "OWNER") return true;
  const uid = currentUser.value?.id;
  return uid === selectedIssue.value.reporterId && canSpeak.value;
});

const hasWorkflowActions = computed(() => {
  if (!selectedIssue.value) return false;
  if (isReporter.value && (canConfirmResolution.value || canCancel.value || canRemind.value || canReject.value)) {
    return true;
  }
  if (selectedIssue.value.status === "RESOLVED" && canReopen.value) return true;
  if (selectedIssue.value.status === "CANCELLED" && myRole.value === "OWNER") return true;
  return false;
});

const replyPlaceholder = computed(() => {
  if (replyKind.value === "ANSWER") return "输入正式答复内容（将同步至评审记录）...";
  if (replyKind.value === "FOLLOW_UP") return "针对现有答复追加疑问或追问细节...";
  return "发表评审意见或交流讨论...";
});

function roundLabel(r: ReviewRoundDetail): string {
  const idx = rounds.value.findIndex((item) => item.id === r.id);
  const no = idx >= 0 ? rounds.value.length - idx : 1;
  return `第 ${no} 轮`;
}

function roundStatusText(r: ReviewRoundDetail): string {
  if (r.status === "OPEN") return "进行中";
  if (r.status === "CLOSED") {
    if (r.conclusion === "PASS") return "已通过";
    if (r.conclusion === "REJECT") return "暂不通过";
    return "附条件通过";
  }
  return "已取消";
}

function formatTime(isoStr?: string | null): string {
  if (!isoStr) return "";
  try {
    const d = new Date(isoStr);
    return d.toLocaleString("zh-CN", {
      month: "numeric",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return isoStr;
  }
}

function formatEventDescription(ev: { eventType: string; fromState: string | null; toState: string | null }): string {
  if (ev.eventType === "ISSUE_CONFIRMED") return "确认问题已解决";
  if (ev.eventType === "ISSUE_REJECTED") return "退回并拒绝解决确认";
  if (ev.eventType === "ISSUE_REOPENED") return "重新打开了问题";
  if (ev.eventType === "ISSUE_CANCELLED") return "撤销了该问题";
  if (ev.eventType === "ISSUE_RESTORED") return "恢复了已撤销问题";
  if (ev.eventType === "ISSUE_ASSIGNED") return "转派了责任人";
  if (ev.eventType === "RESOLUTION_PROPOSED") return "责任人已正式答复";
  if (ev.eventType === "ISSUE_REMINDED") return "发起了催办提醒，提醒负责人及时答复解决该问题";
  return ev.eventType;
}

// -----------------------------------------------------------------------
// Data Loading & Initialization
// -----------------------------------------------------------------------

function formatFileSize(bytes?: number): string {
  if (!bytes) return "0 B";
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / (1024 * 1024)).toFixed(2) + " MB";
}

function openCreateDocModal() {
  newDocTitle.value = reviewTaskTitle.value ? `${reviewTaskTitle.value} 需求评审文档` : "";
  newDocContent.value = "";
  createDocError.value = "";
  showCreateDocModal.value = true;
}

function triggerFileInput() {
  fileInputRef.value?.click();
}

function handleFileSelected(e: Event) {
  const input = e.target as HTMLInputElement;
  if (!input.files || input.files.length === 0) return;
  const file = input.files[0];
  if (!newDocTitle.value.trim()) {
    newDocTitle.value = file.name.replace(/\.[^/.]+$/, "");
  }
  const reader = new FileReader();
  reader.onload = (event) => {
    newDocContent.value = (event.target?.result as string) || "";
  };
  reader.readAsText(file, "UTF-8");
  input.value = "";
}

async function handleImportDraft(draft: Source) {
  if (creatingDoc.value) return;
  creatingDoc.value = true;
  importingDraftId.value = draft.id;
  try {
    const draftData = await getDraftSource(taskId.value, draft.id);
    const title = reviewTaskTitle.value ? `${reviewTaskTitle.value} 需求评审文档` : draft.name.replace(/\.md$/, "");
    try {
      await importReviewDocument(taskId.value, {
        sourceId: draft.id,
        expectedUpdatedAt: draftData.updatedAt,
        title,
      });
    } catch (importErr) {
      createDocError.value = apiErrorMessage(importErr);
      return;
    }
    await init();
  } catch (err: unknown) {
    createDocError.value = apiErrorMessage(err);
  } finally {
    creatingDoc.value = false;
    importingDraftId.value = "";
  }
}

async function submitCreateReviewDoc() {
  const title = newDocTitle.value.trim();
  const content = newDocContent.value.trim();
  if (!title) {
    createDocError.value = "请输入文档标题";
    return;
  }
  if (!content) {
    createDocError.value = "请输入或导入文档 Markdown 正文";
    return;
  }
  creatingDoc.value = true;
  createDocError.value = "";
  try {
    await createReviewDocument(taskId.value, {
      title,
      content,
    });
    showCreateDocModal.value = false;
    await init();
  } catch (err: unknown) {
    createDocError.value = apiErrorMessage(err);
  } finally {
    creatingDoc.value = false;
  }
}

async function init() {
  loading.value = true;
  try {
    if (taskId.value) {
      try {
        const tList = await fetchReviewTasks();
        const found = tList.items.find((t) => t.taskId === taskId.value);
        if (found) {
          reviewTaskTitle.value = found.title;
        }
      } catch {
        // ignore
      }
      try {
        const mems = await fetchMembers(taskId.value);
        members.value = mems;
        const currentUid = currentUser.value?.id;
        const myMem = mems.find((m) => m.userId === currentUid);
        if (myMem) {
          myRole.value = myMem.role;
        }
      } catch (e) {
        console.warn("Could not load space members", e);
      }
    }

    let docId = docIdParam.value;
    if (!docId) {
      const resp = await fetchReviewTaskDocuments(taskId.value);
      if (resp && resp.items && resp.items.length > 0) {
        docId = resp.items[0].id;
      }
    }
    if (!docId) {
      document.value = null;
      currentRevision.value = null;
      revisions.value = [];
      rounds.value = [];
      renderedHtml.value = "";
      tocItems.value = [];
      loading.value = false;
      return;
    }

    const docDetail = await fetchReviewDocument(docId);
    const resolvedTaskId = docDetail.document?.taskId || taskId.value;

    const [revs, rnds, mems] = await Promise.all([
      fetchRevisions(docId),
      fetchRounds(docId),
      fetchMembers(resolvedTaskId),
    ]);

    document.value = docDetail.document;
    currentRevision.value = docDetail.headRevision;
    myRole.value = (docDetail.currentUserRole || docDetail.myRole || "VIEWER") as any;
    activeRound.value = docDetail.openRound || docDetail.activeRound || null;
    revisions.value = revs;
    rounds.value = rnds;
    members.value = mems;

    if (revs.length >= 2) {
      diffBaseId.value = revs[revs.length - 2].id;
      diffTargetId.value = revs[revs.length - 1].id;
    }

    updateRenderedMarkdown();
    await loadIssues();

    const queryIssueId = String(route.query.issue || route.query.issueId || "");
    if (queryIssueId) {
      await openIssueDetail(queryIssueId);
    }
  } catch (err) {
    workspaceError.value = apiErrorMessage(err);
  } finally {
    loading.value = false;
  }
}

async function triggerMermaidRender() {
  await nextTick();
  if (typeof window === "undefined" || typeof window.document === "undefined") return;
  if (articleContentRef.value) {
    await renderMermaidInContainer(articleContentRef.value);
  }
  const drawerEl = window.document.querySelector<HTMLElement>(".issue-detail-drawer");
  if (drawerEl) {
    await renderMermaidInContainer(drawerEl);
  }
}

watch([renderedHtml, loading], async ([, isLoading]) => {
  if (!isLoading) {
    await triggerMermaidRender();
  }
});

function updateRenderedMarkdown() {
  if (!currentRevision.value) return;
  const res = renderMarkdownWithAnchors(currentRevision.value.content || "");
  renderedHtml.value = res.html;
  tocItems.value = res.toc;

  nextTick(async () => {
    checkAnchorRelocations();
    await triggerMermaidRender();
  });
}

async function loadIssues() {
  if (!document.value) return;
  issuesLoading.value = true;
  try {
    const resp = await fetchIssues(document.value.id, {
      status: filterStatus.value || undefined,
      type: filterType.value || undefined,
      impact: filterImpact.value || undefined,
      keyword: issueKeyword.value.trim() || undefined,
      pageSize: 100,
    });
    issues.value = resp.items;
    statistics.value = resp.statistics;
    checkAnchorRelocations();
  } catch (err) {
    workspaceError.value = apiErrorMessage(err);
  } finally {
    issuesLoading.value = false;
  }
}

function checkAnchorRelocations() {
  if (!articleContentRef.value) return;
  const fullText = articleContentRef.value.textContent || "";
  const unlocatable: ReviewIssueSummary[] = [];

  for (const issue of issues.value) {
    if (issue.status === "RESOLVED" || issue.status === "CANCELLED") continue;
    const detailMatch = selectedIssue.value?.id === issue.id ? selectedIssue.value : null;
    const quote = detailMatch?.latestAnchor?.exactQuote || detailMatch?.originalAnchor?.exactQuote;
    if (!quote) continue;
    const loc = locateAnchorInText(fullText, detailMatch?.latestAnchor || detailMatch?.originalAnchor || {
      revisionId: issue.originRevisionId,
      rendererFingerprint: "",
      renderedTextHash: "",
      exactQuote: quote,
      prefixText: "",
      suffixText: "",
      startOffset: 0,
      endOffset: 0,
    });
    if (!loc.found) {
      unlocatable.push(issue);
    }
  }
  unlocatableIssues.value = unlocatable;
}

// -----------------------------------------------------------------------
// Revision & Title actions
// -----------------------------------------------------------------------

async function handleSelectRevision(revisionId: string) {
  const rev = revisions.value.find((r) => r.id === revisionId);
  if (rev) {
    currentRevision.value = rev;
    updateRenderedMarkdown();
  }
}

function startEditTitle() {
  if (!canEditDocument.value) return;
  newTitle.value = document.value?.title || "";
  editingTitle.value = true;
  nextTick(() => titleInputRef.value?.focus());
}

async function saveTitle() {
  if (!editingTitle.value || !document.value) return;
  const t = newTitle.value.trim();
  if (!t || t === document.value.title) {
    editingTitle.value = false;
    return;
  }
  try {
    const updated = await updateReviewDocumentTitle(document.value.id, {
      expectedRevision: document.value.revision,
      title: t,
      reason: "修改文档标题",
    });
    document.value.title = updated.document.title;
    document.value.revision = updated.document.revision;
  } catch (err) {
    workspaceError.value = apiErrorMessage(err);
  } finally {
    editingTitle.value = false;
  }
}

// -----------------------------------------------------------------------
// Text Selection & Anchoring
// -----------------------------------------------------------------------

async function handleTextSelection() {
  if (!articleContentRef.value || !currentRevision.value) return;
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0 || sel.isCollapsed) {
    selectionTooltip.value.visible = false;
    return;
  }

  const anchor = await computeSelectionAnchor(articleContentRef.value, sel, currentRevision.value.id);
  if (anchor) {
    pendingAnchor.value = anchor;
    const range = sel.getRangeAt(0);
    const rect = range.getBoundingClientRect();
    selectionTooltip.value = {
      visible: true,
      x: Math.max(10, rect.left + rect.width / 2 - 60),
      y: Math.max(10, rect.top - 42),
      quote: anchor.exactQuote,
    };
  } else {
    selectionTooltip.value.visible = false;
  }
}

function setScopeMode(mode: "GLOBAL" | "LINE_RANGE") {
  issueScopeMode.value = mode;
  if (mode === "LINE_RANGE") {
    if (!lineRangeStart.value) lineRangeStart.value = 1;
    if (!lineRangeEnd.value) lineRangeEnd.value = Math.min(5, totalContentLines.value);
  }
}

function handleCreateIssueFromSelection() {
  selectionTooltip.value.visible = false;
  openCreateIssueDialog(pendingAnchor.value);
}

function openCreateIssueDialog(anchor: AnchorInput | null) {
  pendingAnchor.value = anchor;
  newIssueTitle.value = "";
  newIssueType.value = "QUESTION";
  newIssueImpact.value = "NORMAL";
  newIssueAssigneeId.value = ownerMember.value?.userId || "";
  newIssueBody.value = "";
  createIssueError.value = "";

  if (anchor && anchor.exactQuote) {
    issueScopeMode.value = "LINE_RANGE";
    const content = currentRevision.value?.content || "";
    const range = findLineRangeForSelection(content, anchor.exactQuote, anchor.prefixText, anchor.suffixText);
    lineRangeStart.value = range.start;
    lineRangeEnd.value = range.end;
  } else {
    issueScopeMode.value = "GLOBAL";
    lineRangeStart.value = 1;
    lineRangeEnd.value = Math.min(5, totalContentLines.value);
  }

  showCreateIssueDialog.value = true;
}

async function submitCreateIssue() {
  if (!document.value || !newIssueTitle.value.trim() || !newIssueBody.value.trim()) return;
  submittingIssue.value = true;
  createIssueError.value = "";
  try {
    const originRevisionId = currentRevision.value?.id;
    const baselineId = activeRound.value?.baselineRevisionId;
    const attachToRound = Boolean(activeRound.value && originRevisionId && originRevisionId === baselineId);

    let anchorPayload: AnchorInput | null = null;
    if (issueScopeMode.value === "LINE_RANGE" && currentRevision.value) {
      const s = Math.max(1, Math.min(lineRangeStart.value || 1, totalContentLines.value));
      const e = Math.max(s, Math.min(lineRangeEnd.value || s, totalContentLines.value));
      const content = currentRevision.value.content || "";
      const linesBefore = docLines.value.slice(0, s - 1);
      const startOffset = linesBefore.reduce((acc, l) => acc + l.length + 1, 0);
      const quote = slicedLineQuote.value;
      const endOffset = startOffset + quote.length;
      const prefix = content.slice(Math.max(0, startOffset - 32), startOffset);
      const suffix = content.slice(endOffset, Math.min(content.length, endOffset + 32));

      anchorPayload = {
        revisionId: currentRevision.value.id,
        selectorVersion: `lines:${s}-${e}`,
        rendererFingerprint: "markdown-lines-v1",
        renderedTextHash: await computeFingerprint(quote),
        exactQuote: quote,
        prefixText: prefix,
        suffixText: suffix,
        startOffset,
        endOffset,
      };
    } else if (pendingAnchor.value) {
      anchorPayload = pendingAnchor.value;
    }

    const detail = await createIssue(document.value.id, {
      roundId: attachToRound ? activeRound.value?.id : undefined,
      originRevisionId,
      title: newIssueTitle.value.trim(),
      body: newIssueBody.value.trim(),
      type: newIssueType.value,
      impact: newIssueImpact.value,
      source: "ONLINE",
      assigneeId: newIssueAssigneeId.value || ownerMember.value?.userId || undefined,
      anchor: anchorPayload || undefined,
    });
    showCreateIssueDialog.value = false;
    pendingAnchor.value = null;
    await loadIssues();
    await openIssueDetail(detail.id);
  } catch (err) {
    createIssueError.value = apiErrorMessage(err);
  } finally {
    submittingIssue.value = false;
  }
}

// -----------------------------------------------------------------------
// Issue Detail & Actions
// -----------------------------------------------------------------------

async function openIssueDetail(issueId: string) {
  selectedIssueId.value = issueId;
  replyBody.value = "";
  try {
    const [detail, timeline] = await Promise.all([
      fetchIssue(issueId),
      fetchTimeline(issueId),
    ]);
    selectedIssue.value = detail;
    timelineItems.value = timeline.items;
    if (canOfficialAnswer.value) {
      replyKind.value = "ANSWER";
    } else {
      replyKind.value = "DISCUSSION";
    }
    await triggerMermaidRender();
  } catch (err) {
    actionError.value = apiErrorMessage(err);
  }
}

function closeIssueDetail() {
  selectedIssueId.value = null;
  selectedIssue.value = null;
}

async function toggleWatchIssue() {
  if (!selectedIssue.value) return;
  const isWatching = selectedIssue.value.watching;
  try {
    if (isWatching) {
      await unwatchIssue(selectedIssue.value.id);
      selectedIssue.value.watching = false;
    } else {
      await watchIssue(selectedIssue.value.id);
      selectedIssue.value.watching = true;
    }
  } catch (err) {
    actionError.value = apiErrorMessage(err);
  }
}

async function submitReply() {
  if (!selectedIssue.value || !replyBody.value.trim()) return;
  submittingReply.value = true;
  try {
    await createPost(selectedIssue.value.id, {
      expectedRevision: selectedIssue.value.revision,
      kind: replyKind.value,
      body: replyBody.value.trim(),
    });
    replyBody.value = "";
    await openIssueDetail(selectedIssue.value.id);
    await loadIssues();
    nextTick(() => {
      const el = window.document.querySelector(".timeline-list");
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    });
  } catch (err) {
    actionError.value = apiErrorMessage(err);
  } finally {
    submittingReply.value = false;
  }
}

async function submitProposeResolution() {
  if (!selectedIssue.value) return;
  submittingAction.value = true;
  try {
    const kind = proposeKind.value as "DOC_CHANGED" | "NO_CHANGE";
    const conclusion = proposeSummary.value.trim();
    const explanation = proposeExplanation.value.trim() || conclusion;
    await executeIssueAction(selectedIssue.value.id, {
      expectedRevision: selectedIssue.value.revision,
      action: "PROPOSE_RESOLUTION",
      resolution: {
        kind,
        conclusion,
        revisionId: kind === "DOC_CHANGED" ? proposeRevisionId.value : undefined,
        changeNote: kind === "DOC_CHANGED" ? explanation : undefined,
        noChangeReason: kind === "NO_CHANGE" ? explanation : undefined,
      },
    });
    showProposeDialog.value = false;
    await openIssueDetail(selectedIssue.value.id);
    await loadIssues();
  } catch (err) {
    actionError.value = apiErrorMessage(err);
  } finally {
    submittingAction.value = false;
  }
}

const remindingAssignee = ref(false);
const reminderSuccessToast = ref("");

async function handleRemindAssignee() {
  if (!selectedIssue.value) return;
  remindingAssignee.value = true;
  actionError.value = "";
  try {
    const updated = await executeIssueAction(selectedIssue.value.id, {
      expectedRevision: selectedIssue.value.revision,
      action: "REMIND",
    });
    selectedIssue.value = updated;
    const timeline = await fetchTimeline(selectedIssue.value.id);
    timelineItems.value = timeline.items;
    reminderSuccessToast.value = `已向负责人（${selectedIssue.value.assigneeName}）发送催办提醒通知`;
    setTimeout(() => {
      reminderSuccessToast.value = "";
    }, 4000);
  } catch (err: unknown) {
    actionError.value = apiErrorMessage(err);
  } finally {
    remindingAssignee.value = false;
  }
}

async function handleConfirmResolution() {
  if (!selectedIssue.value || !isReporter.value) return;
  try {
    await executeIssueAction(selectedIssue.value.id, {
      expectedRevision: selectedIssue.value.revision,
      action: "CONFIRM",
    });
    await openIssueDetail(selectedIssue.value.id);
    await loadIssues();
  } catch (err) {
    actionError.value = apiErrorMessage(err);
  }
}

async function submitRejectResolution() {
  if (!selectedIssue.value || !actionReason.value.trim()) return;
  submittingAction.value = true;
  try {
    await executeIssueAction(selectedIssue.value.id, {
      expectedRevision: selectedIssue.value.revision,
      action: "REJECT",
      reason: actionReason.value.trim(),
      proxyReason: proxyReason.value.trim() || undefined,
    });
    showRejectDialog.value = false;
    actionReason.value = "";
    await openIssueDetail(selectedIssue.value.id);
    await loadIssues();
  } catch (err) {
    actionError.value = apiErrorMessage(err);
  } finally {
    submittingAction.value = false;
  }
}

async function submitReopen() {
  if (!selectedIssue.value || !actionReason.value.trim()) return;
  submittingAction.value = true;
  try {
    await executeIssueAction(selectedIssue.value.id, {
      expectedRevision: selectedIssue.value.revision,
      action: "REOPEN",
      reason: actionReason.value.trim(),
    });
    showReopenDialog.value = false;
    actionReason.value = "";
    await openIssueDetail(selectedIssue.value.id);
    await loadIssues();
  } catch (err) {
    actionError.value = apiErrorMessage(err);
  } finally {
    submittingAction.value = false;
  }
}

async function submitCancel() {
  if (!selectedIssue.value || !actionReason.value.trim()) return;
  submittingAction.value = true;
  try {
    await executeIssueAction(selectedIssue.value.id, {
      expectedRevision: selectedIssue.value.revision,
      action: "CANCEL",
      reason: actionReason.value.trim(),
    });
    showCancelDialog.value = false;
    actionReason.value = "";
    await openIssueDetail(selectedIssue.value.id);
    await loadIssues();
  } catch (err) {
    actionError.value = apiErrorMessage(err);
  } finally {
    submittingAction.value = false;
  }
}

async function submitRestore() {
  if (!selectedIssue.value || !actionReason.value.trim()) return;
  submittingAction.value = true;
  try {
    await executeIssueAction(selectedIssue.value.id, {
      expectedRevision: selectedIssue.value.revision,
      action: "RESTORE",
      reason: actionReason.value.trim(),
    });
    showRestoreDialog.value = false;
    actionReason.value = "";
    await openIssueDetail(selectedIssue.value.id);
    await loadIssues();
  } catch (err) {
    actionError.value = apiErrorMessage(err);
  } finally {
    submittingAction.value = false;
  }
}

function openAssignDialog() {
  if (!selectedIssue.value) return;
  assignTargetUserId.value = selectedIssue.value.assigneeId;
  assignReason.value = "";
  showAssignDialog.value = true;
}

async function submitAssign() {
  if (!selectedIssue.value || !assignTargetUserId.value) return;
  submittingAction.value = true;
  try {
    await assignIssue(selectedIssue.value.id, {
      expectedRevision: selectedIssue.value.revision,
      assigneeId: assignTargetUserId.value,
      reason: assignReason.value.trim() || undefined,
    });
    showAssignDialog.value = false;
    await openIssueDetail(selectedIssue.value.id);
    await loadIssues();
  } catch (err) {
    actionError.value = apiErrorMessage(err);
  } finally {
    submittingAction.value = false;
  }
}

function locateAndScrollToAnchor() {
  if (!articleContentRef.value || !selectedIssue.value) return;
  const anc = selectedIssue.value.latestAnchor || selectedIssue.value.originalAnchor;
  if (!anc || !anc.exactQuote) return;

  const quote = anc.exactQuote.trim();
  const searchSnippet = quote.slice(0, 40).trim();

  // 1. Try to find existing mark
  let targetEl: HTMLElement | null = articleContentRef.value.querySelector(
    `mark[data-issue-id="${selectedIssue.value.id}"]`
  );

  // 2. If not found by mark, find DOM child element containing snippet
  if (!targetEl) {
    const candidates = articleContentRef.value.querySelectorAll<HTMLElement>(
      "h1, h2, h3, h4, h5, h6, p, li, blockquote, pre, tr, td, th"
    );
    for (const el of Array.from(candidates)) {
      if (el.textContent && el.textContent.includes(searchSnippet)) {
        targetEl = el;
        break;
      }
    }
  }

  // 3. Fallback to fullText range search via TreeWalker
  if (!targetEl) {
    const fullText = articleContentRef.value.textContent || "";
    const loc = locateAnchorInText(fullText, anc);
    if (loc.found) {
      const range = createDomRangeFromOffsets(articleContentRef.value, loc.startOffset, loc.endOffset);
      if (range && range.startContainer.parentElement) {
        targetEl = range.startContainer.parentElement;
      }
    }
  }

  if (targetEl) {
    targetEl.scrollIntoView({ behavior: "smooth", block: "center" });
    targetEl.classList.remove("anchor-pulse-highlight");
    void targetEl.offsetWidth; // force reflow
    targetEl.classList.add("anchor-pulse-highlight");
    setTimeout(() => {
      targetEl?.classList.remove("anchor-pulse-highlight");
    }, 2800);
  } else {
    window.alert("当前正文中未定位到该原文片段（内容可能已在新版本中被修改或移动）。");
  }
}

// -----------------------------------------------------------------------
// Revisions & Diff
// -----------------------------------------------------------------------

async function loadDiff() {
  if (!document.value || !diffBaseId.value || !diffTargetId.value) return;
  try {
    const diff = await compareRevisions(document.value.id, diffBaseId.value, diffTargetId.value);
    diffResult.value = diff;
  } catch (err) {
    workspaceError.value = apiErrorMessage(err);
  }
}

async function submitCreateRevision() {
  if (!document.value || !currentRevision.value || !newRevContent.value.trim()) return;
  submittingRevision.value = true;
  try {
    const rev = await createRevision(document.value.id, {
      expectedRevision: document.value.revision,
      expectedHeadRevisionId: currentRevision.value.id,
      content: newRevContent.value,
      summary: newRevSummary.value.trim() || "版本更新",
    });
    showCreateRevisionForm.value = false;
    newRevSummary.value = "";
    newRevContent.value = "";
    await init();
  } catch (err) {
    workspaceError.value = apiErrorMessage(err);
  } finally {
    submittingRevision.value = false;
  }
}

// -----------------------------------------------------------------------
// Rounds
// -----------------------------------------------------------------------

async function startNewRound() {
  if (!document.value || !currentRevision.value) return;
  try {
    await createRound(document.value.id, { baselineRevisionId: currentRevision.value.id });
    await init();
  } catch (err) {
    workspaceError.value = apiErrorMessage(err);
  }
}

function openCloseRoundDialog(round: ReviewRoundDetail) {
  closeSummary.value = "";
  closeRoundError.value = "";
  unresolvedDispositions.value = {};
  showCloseRoundModal.value = true;
}

async function submitCloseRound() {
  if (!activeRound.value) return;
  closeRoundError.value = "";
  try {
    await closeRound(activeRound.value.id, {
      expectedRevision: activeRound.value.revision,
      conclusion: closeConclusion.value,
      summary: closeSummary.value.trim(),
      unresolvedDispositions:
        closeConclusion.value === "PASS_WITH_RESIDUAL" || closeConclusion.value === "REJECT"
          ? unresolvedDispositions.value
          : undefined,
    });
    showCloseRoundModal.value = false;
    showRoundsModal.value = false;
    await init();
  } catch (err) {
    closeRoundError.value = apiErrorMessage(err);
  }
}

async function cancelCurrentRound(round: ReviewRoundDetail) {
  const reason = window.prompt("请输入取消评审轮次的原因：");
  if (!reason || !reason.trim()) return;
  try {
    await cancelRound(round.id, {
      expectedRevision: round.revision,
      reason: reason.trim(),
    });
    await init();
  } catch (err) {
    workspaceError.value = apiErrorMessage(err);
  }
}

// -----------------------------------------------------------------------
// Members
// -----------------------------------------------------------------------

const submittingMember = ref(false);
let searchDebounceTimer: any = null;

function handleCandidateInput() {
  memberAddError.value = "";
  if (selectedCandidate.value && userSearchQuery.value !== `${selectedCandidate.value.displayName} (@${selectedCandidate.value.username})`) {
    selectedCandidate.value = null;
  }
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer);
  searchDebounceTimer = setTimeout(() => {
    triggerCandidateSearch();
  }, 200);
}

function handleCandidateFocus() {
  if (!showCandidateDropdown.value) {
    triggerCandidateSearch();
  }
}

async function triggerCandidateSearch() {
  candidateLoading.value = true;
  try {
    const list = await searchCandidateUsers(currentTaskId.value, userSearchQuery.value.trim());
    candidateUsers.value = list;
    showCandidateDropdown.value = true;
  } catch (err) {
    console.error("Failed to search candidate users:", err);
  } finally {
    candidateLoading.value = false;
  }
}

function selectCandidate(candidate: ReviewCandidateUserDetail) {
  selectedCandidate.value = candidate;
  userSearchQuery.value = `${candidate.displayName} (@${candidate.username})`;
  showCandidateDropdown.value = false;
  memberAddError.value = "";
}

function clearCandidateSelection() {
  selectedCandidate.value = null;
  userSearchQuery.value = "";
  candidateUsers.value = [];
  showCandidateDropdown.value = false;
}

async function submitAddMember() {
  let targetUsername = "";
  if (selectedCandidate.value) {
    targetUsername = selectedCandidate.value.username;
  } else {
    const raw = userSearchQuery.value.trim();
    const handleMatch = raw.match(/@([a-zA-Z0-9_\-\.]+)/);
    if (handleMatch) {
      targetUsername = handleMatch[1];
    } else {
      targetUsername = raw;
    }
  }

  if (!targetUsername) {
    memberAddError.value = "请先搜索并选择要添加的用户";
    return;
  }

  submittingMember.value = true;
  memberAddError.value = "";
  try {
    await addMember(currentTaskId.value, {
      username: targetUsername,
      role: newMemberRole.value,
    });
    clearCandidateSelection();
    members.value = await fetchMembers(currentTaskId.value);
  } catch (err) {
    memberAddError.value = apiErrorMessage(err);
  } finally {
    submittingMember.value = false;
  }
}

async function handleRemoveMember(member: ReviewMemberDetail) {
  if (!window.confirm(`确定要移除成员 ${member.displayName} 吗？`)) return;
  try {
    await removeMember(currentTaskId.value, member.userId, { expectedRevision: member.revision });
    members.value = await fetchMembers(currentTaskId.value);
  } catch (err) {
    workspaceError.value = apiErrorMessage(err);
  }
}

// -----------------------------------------------------------------------
// Export ZIP
// -----------------------------------------------------------------------

async function handleExport() {
  exporting.value = true;
  try {
    const blob = await downloadReviewExport(currentTaskId.value, {
      documentId: document.value?.id,
      includeAuditJson: true,
    });
    const url = window.URL.createObjectURL(blob);
    const a = window.document.createElement("a");
    a.href = url;
    a.download = `review-export-${currentTaskId.value}.zip`;
    window.document.body.appendChild(a);
    a.click();
    window.document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  } catch (err) {
    workspaceError.value = apiErrorMessage(err);
  } finally {
    exporting.value = false;
  }
}

function scrollToHeading(anchor: string) {
  const el = window.document.getElementById(anchor);
  if (el) {
    el.scrollIntoView({ behavior: "smooth" });
  }
}

function handleArticleClick(e: MouseEvent) {
  const target = e.target as HTMLElement;
  const mark = target.closest("mark.review-anchor-mark");
  if (mark) {
    const issueId = mark.getAttribute("data-issue-id");
    if (issueId) {
      openIssueDetail(issueId);
    }
  }
}

watch(
  [() => route.params.taskId, () => route.params.documentId],
  () => init()
);

watch(
  [() => route.query.issue, () => route.query.issueId],
  ([newIssue, newIssueId]) => {
    const targetId = String(newIssue || newIssueId || "");
    if (targetId && targetId !== selectedIssueId.value) {
      openIssueDetail(targetId);
    }
  }
);

onMounted(() => {
  init();
});
</script>

<style scoped>
.review-doc-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--page);
  overflow: hidden;
}

.review-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
  height: 48px;
  flex-shrink: 0;
  z-index: 10;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.back-action {
  text-decoration: none;
}

.topbar-divider {
  color: var(--line-strong);
}

.doc-title-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.doc-title-text {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: default;
}

.doc-title-text.editable {
  cursor: pointer;
}

.doc-title-text.editable:hover {
  color: var(--accent);
}

.edit-icon {
  color: var(--ink-faint);
}

.title-inline-input {
  font-size: 15px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid var(--accent);
  background: var(--surface);
  color: var(--ink);
}

.badge-archived {
  font-size: 11px;
  background: var(--surface-quiet);
  color: var(--ink-faint);
  padding: 2px 6px;
  border-radius: 4px;
}

.topbar-center {
  display: flex;
  align-items: center;
  gap: 12px;
}

.revision-selector {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.selector-label {
  color: var(--ink-faint);
}

.version-select {
  padding: 3px 8px;
  border-radius: 6px;
  border: 1px solid var(--line);
  background: var(--surface-subtle);
  font-size: 13px;
  font-weight: 500;
}

.round-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 3px 8px;
  border-radius: 12px;
  font-weight: 500;
}

.round-open {
  background: #ecfdf5;
  color: #047857;
}

.round-open .round-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
}

.round-none {
  background: var(--surface-subtle);
  color: var(--ink-faint);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.primary-ask-btn {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}

.primary-ask-btn:hover {
  background: var(--accent-dark);
}

.mobile-only-btn {
  display: none;
}

/* Workspace Layout */
.workspace-body {
  flex: 1;
  display: flex;
  height: calc(100vh - 48px);
  overflow: hidden;
}

/* Left Panel */
.left-panel {
  width: 220px;
  background: var(--surface);
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow-y: auto;
  padding: 16px 12px;
}

.panel-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-soft);
  margin-bottom: 12px;
}

.toc-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.toc-link {
  font-size: 13px;
  color: var(--ink-soft);
  text-decoration: none;
  padding: 4px 8px;
  border-radius: 4px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.toc-link:hover {
  background: var(--surface-subtle);
  color: var(--accent);
}

.toc-link.level-2 {
  padding-left: 16px;
  font-size: 12px;
}

.toc-link.level-3 {
  padding-left: 24px;
  font-size: 12px;
  color: var(--ink-faint);
}

.unlocatable-card {
  margin-top: auto;
  padding: 10px;
  background: #fffbeb;
  border: 1px solid #fef3c7;
  border-radius: 6px;
  font-size: 12px;
}

.unlocatable-header {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #b45309;
  font-weight: 600;
  margin-bottom: 6px;
}

.unlocatable-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.unlocatable-item-btn {
  background: none;
  border: none;
  color: #92400e;
  text-align: left;
  font-size: 11px;
  cursor: pointer;
  padding: 2px 4px;
}

/* Center Panel */
.center-panel {
  flex: 1;
  background: var(--surface);
  overflow-y: auto;
  position: relative;
  padding: 32px 48px;
}

.markdown-article {
  max-width: 820px;
  margin: 0 auto;
}

.article-rendered-content {
  font-size: 15px;
  line-height: 1.7;
  color: var(--ink);
}

.article-rendered-content :deep(h1) {
  font-size: 24px;
  margin-top: 24px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--line);
  padding-bottom: 8px;
}

.article-rendered-content :deep(h2) {
  font-size: 19px;
  margin-top: 20px;
  margin-bottom: 10px;
}

.article-rendered-content :deep(h3) {
  font-size: 16px;
  margin-top: 16px;
  margin-bottom: 8px;
}

.article-rendered-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 16px 0;
}

.article-rendered-content :deep(th),
.article-rendered-content :deep(td) {
  border: 1px solid var(--line);
  padding: 8px 12px;
  text-align: left;
}

.article-rendered-content :deep(th) {
  background: var(--surface-subtle);
  font-weight: 600;
}

/* Floating Tooltip */
.selection-floating-tooltip {
  position: fixed;
  z-index: 100;
  transform: translateY(-100%);
}

.tooltip-ask-btn {
  background: var(--accent);
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  gap: 6px;
}

.tooltip-ask-btn:hover {
  background: var(--accent-dark);
}

/* Right Panel */
.right-panel {
  width: 380px;
  background: var(--surface-subtle);
  border-left: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}

.panel-header {
  padding: 12px 16px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.stats-overview {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.stat-box {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-num {
  font-size: 16px;
  font-weight: 700;
  color: var(--ink);
}

.stat-label {
  font-size: 11px;
  color: var(--ink-faint);
}

.stat-box.highlight .stat-num { color: var(--amber); }
.stat-box.awaiting .stat-num { color: #d97706; }
.stat-box.resolved .stat-num { color: #059669; }
.stat-box.rate .stat-num { color: var(--accent); }

.issues-filter-bar {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-search-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.filter-search-icon {
  position: absolute;
  left: 8px;
  color: var(--ink-faint);
}

.filter-search-input {
  width: 100%;
  height: 28px;
  padding: 0 8px 0 26px;
  border-radius: 6px;
  border: 1px solid var(--line);
  background: var(--surface);
  font-size: 12px;
}

.filter-dropdowns {
  display: flex;
  gap: 4px;
}

.mini-select {
  flex: 1;
  height: 26px;
  font-size: 11px;
  padding: 0 4px;
  border-radius: 4px;
  border: 1px solid var(--line);
  background: var(--surface);
}

.issues-scroll-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.issue-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.15s;
}

.issue-card:hover {
  border-color: var(--accent);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.issue-card.active {
  border-color: var(--accent);
  background: #f0fdfa;
}

.issue-card-top {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.issue-no {
  font-weight: 700;
  font-size: 12px;
  color: var(--ink-faint);
}

.issue-title {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.issue-status-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
}

.status-open { background: var(--amber-soft); color: var(--amber); }
.status-awaiting_confirmation { background: #fef3c7; color: #b45309; }
.status-resolved { background: #d1fae5; color: #047857; }
.status-cancelled { background: var(--surface-quiet); color: var(--ink-faint); }

.issue-card-tags {
  display: flex;
  gap: 4px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.type-tag, .impact-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
}

.type-suggestion { background: var(--blue-soft); color: var(--blue); }
.type-question { background: var(--purple-soft); color: var(--purple); }
.type-blocker { background: var(--danger-soft); color: var(--danger); font-weight: 600; }
.type-defect { background: #fee2e2; color: #dc2626; }

.impact-critical { background: #fecdd3; color: #9f1239; font-weight: 600; }
.impact-high { background: #ffedd5; color: #9a3412; }
.impact-normal { background: var(--surface-quiet); color: var(--ink-soft); }
.impact-low { background: var(--surface-quiet); color: var(--ink-faint); }

.tag-alert {
  font-size: 11px;
  background: #fef3c7;
  color: #b45309;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 600;
}

.tag-danger {
  font-size: 11px;
  background: #fee2e2;
  color: #dc2626;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 600;
}

.issue-card-footer {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--ink-faint);
}

.post-count {
  display: flex;
  align-items: center;
  gap: 3px;
}

/* Detail Drawer */
.issue-detail-drawer {
  width: 520px;
  max-width: 90vw;
  height: 100%;
  background: var(--surface);
  box-shadow: -8px 0 24px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
}

.detail-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--line);
}

.detail-header-top {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
}

.detail-issue-no {
  font-size: 16px;
  font-weight: 700;
  color: var(--ink-faint);
}

.detail-issue-title {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: var(--ink);
  line-height: 1.4;
}

.detail-badges-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.meta-tag {
  font-size: 11px;
  color: var(--ink-faint);
  background: var(--surface-quiet);
  padding: 1px 6px;
  border-radius: 4px;
}

.watch-toggle-btn {
  margin-left: auto;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: 1px solid var(--line);
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;
}

.watch-toggle-btn.watching {
  background: var(--accent-soft);
  color: var(--accent);
  border-color: var(--accent);
}

.detail-meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  font-size: 12px;
  color: var(--ink-faint);
}

.detail-body-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.quote-anchor-box {
  background: #f8fafc;
  border-left: 3px solid var(--accent);
  padding: 10px 12px;
  border-radius: 0 6px 6px 0;
  margin-bottom: 16px;
}

.quote-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
  margin-bottom: 6px;
}

.quote-tag-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.quote-line-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 6px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 4px;
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
}

.issue-line-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--accent);
  background: rgba(30, 94, 76, 0.08);
  border-radius: 3px;
  padding: 1px 6px;
  font-weight: 500;
}

.locate-btn {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
  background: var(--surface);
  border: 1px solid var(--accent);
  color: var(--accent);
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.locate-btn:hover {
  background: var(--accent);
  color: #fff;
}

.close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--ink-soft);
  cursor: pointer;
  transition: all 0.15s ease;
}

.close-btn:hover {
  background: var(--surface-subtle);
  color: var(--ink);
  border-color: var(--line);
}

.btn-transfer {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
  padding: 2px 8px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--surface);
  color: var(--ink);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-transfer:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--surface-subtle);
}

.scope-mode-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  background: var(--surface-subtle);
  padding: 3px;
  border-radius: 6px;
  border: 1px solid var(--line);
}

.scope-tab-btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 6px 12px;
  border: none;
  background: transparent;
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 500;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.scope-tab-btn.active {
  background: var(--surface);
  color: var(--ink);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  font-weight: 600;
}

.line-range-selector-panel {
  background: #f8fafc;
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 14px;
}

.line-range-inputs-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.line-input-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ink);
}

.line-num-input {
  width: 72px;
  padding: 4px 8px;
  border: 1px solid var(--line);
  border-radius: 4px;
  font-size: 13px;
  text-align: center;
  background: #fff;
}

.line-range-dash {
  font-size: 12px;
  color: var(--ink-soft);
}

.total-lines-hint {
  font-size: 11px;
  color: var(--ink-soft);
  margin-left: auto;
}

.line-quote-preview-box {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 4px;
  padding: 8px 10px;
}

.preview-header {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--accent);
  margin-bottom: 4px;
}

.preview-content {
  margin: 0;
  font-size: 12px;
  font-family: inherit;
  color: var(--ink-soft);
  max-height: 140px;
  overflow-y: auto;
  line-height: 1.5;
}

.preview-content :deep(p),
.preview-content :deep(ul),
.preview-content :deep(ol) {
  margin: 0 0 0.35em;
  font-size: 12px;
}

.preview-content :deep(h1),
.preview-content :deep(h2),
.preview-content :deep(h3),
.preview-content :deep(h4) {
  margin: 0.25em 0 0.2em;
  font-size: 13px;
  font-weight: 700;
  border-bottom: none;
  padding: 0;
}

.preview-content :deep(li) {
  margin: 0.15em 0;
}

.preview-content :deep(table) {
  font-size: 11px;
  margin: 0.3em 0;
}

@keyframes anchorPulseHighlight {
  0% {
    background-color: rgba(30, 94, 76, 0.35);
    outline: 2px solid var(--accent);
    border-radius: 4px;
  }
  50% {
    background-color: rgba(30, 94, 76, 0.2);
    outline: 2px solid var(--accent);
  }
  100% {
    background-color: transparent;
    outline: 2px solid transparent;
  }
}

.anchor-pulse-highlight {
  animation: anchorPulseHighlight 2.5s ease-out;
}

.exact-quote-content {
  margin: 0;
  font-size: 13px;
  color: var(--ink-soft);
  line-height: 1.5;
}

.exact-quote-content :deep(p),
.exact-quote-content :deep(ul),
.exact-quote-content :deep(ol),
.exact-quote-content :deep(h1),
.exact-quote-content :deep(h2),
.exact-quote-content :deep(h3) {
  margin: 0 0 0.35em;
  font-size: 13px;
}

.issue-original-body {
  margin-bottom: 20px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-soft);
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.body-text {
  font-size: 14px;
  line-height: 1.6;
  color: var(--ink);
  margin: 0;
}

.body-text :deep(p),
.body-text :deep(ul),
.body-text :deep(ol) {
  margin: 0 0 0.5em;
}

.post-content :deep(p),
.post-content :deep(ul),
.post-content :deep(ol) {
  margin: 0 0 0.5em;
  font-size: 13px;
  line-height: 1.6;
}

.resolution-card {
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 20px;
}

.res-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #047857;
  margin-bottom: 6px;
}

.res-summary, .res-explanation {
  font-size: 13px;
  color: #065f46;
  margin: 4px 0;
}

/* Required Star */
.required-star {
  color: #ef4444;
  font-weight: 700;
  margin-left: 2px;
  display: inline-block;
}

/* WeChat-style Chat Conversation Stream */
.chat-conversation-stream {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px 20px;
}

.chat-message-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.chat-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  color: #ffffff;
  flex-shrink: 0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  user-select: none;
}

.chat-avatar.reporter-avatar {
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
}

.chat-avatar.answer-avatar {
  background: linear-gradient(135deg, #10b981 0%, #047857 100%);
}

.chat-avatar.other-avatar {
  background: linear-gradient(135deg, #6366f1 0%, #4338ca 100%);
}

.chat-message-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.chat-sender-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.chat-author-name {
  font-weight: 600;
  color: #1e293b;
}

.chat-role-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 600;
  line-height: 1.4;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.chat-role-badge.reporter-badge {
  background: #eff6ff;
  color: #2563eb;
  border: 1px solid #bfdbfe;
}

.chat-role-badge.answer-badge {
  background: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
}

.chat-role-badge.assignee-badge {
  background: #f5f3ff;
  color: #7c3aed;
  border: 1px solid #ddd6fe;
}

.chat-role-badge.followup-badge {
  background: #fff7ed;
  color: #ea580c;
  border: 1px solid #fed7aa;
}

.chat-role-badge.discussion-badge {
  background: #f1f5f9;
  color: #64748b;
  border: 1px solid #e2e8f0;
}

.chat-time {
  margin-left: auto;
  color: #94a3b8;
  font-size: 11.5px;
}

/* WeChat Chat Bubble */
.chat-bubble {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 4px 14px 14px 14px;
  padding: 11px 15px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  color: #1e293b;
  font-size: 13.5px;
  line-height: 1.6;
  position: relative;
  word-break: break-word;
}

.chat-bubble.question-bubble {
  background: #ffffff;
  border-color: #cbd5e1;
}

.chat-bubble.official-answer-bubble {
  background: #f0fdf4;
  border: 1px solid #a7f3d0;
  border-left: 3px solid #10b981;
}

.bubble-content.markdown p:last-child {
  margin-bottom: 0;
}

/* In-bubble quotation box */
.bubble-quote-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-left: 3px solid #3b82f6;
  border-radius: 6px;
  padding: 8px 12px;
  margin-bottom: 10px;
}

.bubble-quote-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11.5px;
  color: #475569;
  font-weight: 600;
  margin-bottom: 6px;
}

.bubble-quote-tag {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #2563eb;
}

.bubble-quote-text {
  font-size: 12px;
  color: #475569;
  max-height: 140px;
  overflow-y: auto;
  line-height: 1.5;
}

.locate-btn.mini {
  padding: 2px 7px;
  font-size: 11px;
  height: 22px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #2563eb;
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  transition: all 0.15s ease;
}

.locate-btn.mini:hover {
  background: #dbeafe;
  color: #1d4ed8;
}

/* WeChat-style centered system notices */
.chat-system-notice {
  align-self: center;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 16px;
  border-radius: 20px;
  font-size: 12px;
  margin: 10px 0;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.chat-system-notice.resolved {
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  color: #047857;
  font-weight: 600;
}

.chat-system-notice.cancelled {
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  color: #64748b;
  font-weight: 500;
}

.chat-system-notice.awaiting {
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #b45309;
  font-weight: 500;
}

.detail-footer {
  padding: 16px 20px;
  border-top: 1px solid var(--line);
  background: var(--surface);
}

.workflow-actions-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  margin-bottom: 12px;
  background: var(--surface-subtle, #f8fafc);
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 10px;
  flex-wrap: wrap;
}

.wf-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  padding: 7px 15px;
  border-radius: 8px;
  border: 1px solid transparent;
  cursor: pointer;
  line-height: 1.2;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}

/* Success: 问题已解决 */
.wf-btn.success-btn {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(16, 185, 129, 0.25), inset 0 1px 0 rgba(255, 255, 255, 0.2);
  border-color: rgba(16, 185, 129, 0.4);
}

.wf-btn.success-btn:hover {
  background: linear-gradient(135deg, #059669 0%, #047857 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.wf-btn.success-btn:active {
  transform: translateY(0);
  box-shadow: 0 1px 3px rgba(16, 185, 129, 0.3);
}

/* Neutral / Cancel: 撤销问题 */
.wf-btn.neutral-btn.cancel-btn,
.wf-btn.neutral-btn {
  background: #ffffff;
  color: #64748b;
  border-color: #cbd5e1;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.wf-btn.neutral-btn.cancel-btn:hover,
.wf-btn.neutral-btn:hover {
  background: #fef2f2;
  color: #ef4444;
  border-color: rgba(239, 68, 68, 0.3);
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(239, 68, 68, 0.12);
}

.wf-btn.neutral-btn.cancel-btn:active,
.wf-btn.neutral-btn:active {
  transform: translateY(0);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

/* Reminder: 提醒负责人解决 */
.wf-btn.reminder-btn {
  background: rgba(245, 158, 11, 0.08);
  color: #b45309;
  border-color: rgba(245, 158, 11, 0.25);
  font-weight: 500;
}

.wf-btn.reminder-btn:hover {
  background: rgba(245, 158, 11, 0.16);
  border-color: rgba(245, 158, 11, 0.4);
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(245, 158, 11, 0.15);
}

/* Danger: 拒绝确认 */
.wf-btn.danger-btn {
  background: #fee2e2;
  color: #dc2626;
  border-color: rgba(220, 38, 38, 0.25);
  font-weight: 500;
}

.wf-btn.danger-btn:hover {
  background: #fecaca;
  color: #b91c1c;
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(220, 38, 38, 0.15);
}

/* Warning: 重新打开 */
.wf-btn.warning-btn {
  background: rgba(245, 158, 11, 0.1);
  color: #d97706;
  border-color: rgba(245, 158, 11, 0.3);
  font-weight: 500;
}

.wf-btn.warning-btn:hover {
  background: rgba(245, 158, 11, 0.2);
  transform: translateY(-1px);
}

.reply-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.reply-kind-tabs {
  display: flex;
  gap: 4px;
}

.kind-tab {
  font-size: 12px;
  background: none;
  border: 1px solid var(--line);
  border-radius: 4px;
  padding: 3px 8px;
  cursor: pointer;
}

.kind-tab.active {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}

.kind-tab.answer-tab.active {
  background: #059669;
  border-color: #059669;
}

.reply-textarea {
  width: 100%;
  font-size: 13px;
  padding: 8px;
  border-radius: 6px;
  border: 1px solid var(--line);
  outline: none;
  resize: vertical;
}

.reply-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.reply-hint {
  font-size: 11px;
  color: #059669;
}

.reply-submit-btn {
  padding: 6px 16px;
}

/* Modals */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-card {
  width: 520px;
  max-width: 90vw;
  max-height: 85vh;
  background: var(--surface);
  border-radius: 12px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 24px;
}

.wide-modal {
  width: 800px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.modal-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-soft);
}

.form-field input, .form-field select, .form-field textarea {
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid var(--line);
  font-size: 13px;
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-row .form-field {
  flex: 1;
}

.modal-quote-box {
  background: var(--surface-subtle);
  border-left: 3px solid var(--accent);
  padding: 8px 12px;
  font-size: 12px;
  color: var(--ink-soft);
  margin-bottom: 12px;
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.modal-card.issue-create-modal {
  width: 560px;
  max-width: 92vw;
  max-height: 88vh;
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-card.issue-create-modal .modal-header {
  padding: 18px 24px 14px 24px;
  border-bottom: 1px solid var(--border-color, #e2e8f0);
  margin-bottom: 0;
  flex-shrink: 0;
}

.modal-form-layout {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.modal-alert-box.error {
  margin: 12px 24px 0 24px;
  padding: 10px 14px;
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: 8px;
  color: #b91c1c;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  flex-shrink: 0;
}

.modal-alert-box .alert-content {
  flex: 1;
  line-height: 1.4;
}

.modal-alert-box .alert-close-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #b91c1c;
  display: flex;
  align-items: center;
  padding: 2px;
  border-radius: 4px;
}

.modal-alert-box .alert-close-btn:hover {
  background: rgba(239, 68, 68, 0.15);
}

.modal-scroll-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
  min-height: 0;
}

.modal-card.issue-create-modal .modal-actions {
  padding: 14px 24px;
  border-top: 1px solid var(--border-color, #e2e8f0);
  background: var(--surface, #ffffff);
  flex-shrink: 0;
  margin-top: 0;
}

.wf-btn.reminder-btn {
  background: #f8fafc;
  color: #334155;
  border: 1px solid #cbd5e1;
}

.wf-btn.reminder-btn:hover:not(:disabled) {
  background: #f1f5f9;
  border-color: #94a3b8;
  color: #0f172a;
}

.status-workflow-hint.awaiting {
  background: rgba(16, 185, 129, 0.06);
  border-left: 3px solid #10b981;
}

.status-workflow-hint.awaiting .hint-header {
  color: #059669;
}

.reminder-toast {
  position: fixed;
  bottom: 28px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2000;
  background: #0f172a;
  color: #f8fafc;
  padding: 10px 20px;
  border-radius: 24px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.25);
  pointer-events: none;
}

.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, 10px);
}

/* Diff Viewer */
.diff-control-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
}

.diff-select-group {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.diff-arrow {
  color: var(--ink-faint);
}

.diff-result-container {
  border: 1px solid var(--line);
  border-radius: 6px;
  overflow: hidden;
  background: #f8fafc;
}

.diff-stats-header {
  padding: 8px 12px;
  background: var(--surface-subtle);
  border-bottom: 1px solid var(--line);
  font-size: 12px;
  font-weight: 600;
  display: flex;
  gap: 12px;
}

.diff-stats-header span:first-child { color: #059669; }
.diff-stats-header span:last-child { color: #dc2626; }

.diff-pre {
  margin: 0;
  padding: 12px;
  font-family: monospace;
  font-size: 12px;
  max-height: 400px;
  overflow: auto;
  white-space: pre-wrap;
}

/* Members table */
.members-table-wrap {
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: 16px;
}

.members-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.members-table th, .members-table td {
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
  text-align: left;
}

.members-table th {
  background: var(--surface-subtle);
  font-weight: 600;
}

/* Add member form & autocomplete */
.add-member-form {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
}

.add-member-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.add-member-header h4 {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.add-member-tip {
  font-size: 12px;
  color: var(--text-muted);
}

.member-add-error {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  font-size: 12px;
  margin-bottom: 10px;
}

.member-search-form {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  position: relative;
}

.user-autocomplete-wrapper {
  flex: 1;
  position: relative;
}

.search-input-box {
  display: flex;
  align-items: center;
  position: relative;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 0 8px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.search-input-box:focus-within {
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
}

.search-input-box .search-icon {
  color: var(--text-muted);
  margin-right: 6px;
  flex-shrink: 0;
}

.member-search-input {
  flex: 1;
  border: none;
  background: transparent;
  padding: 7px 0;
  font-size: 13px;
  color: var(--text);
  outline: none;
}

.clear-candidate-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 2px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.clear-candidate-btn:hover {
  background: var(--surface-subtle);
  color: var(--text);
}

.candidate-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  max-height: 220px;
  overflow-y: auto;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 100;
}

.candidate-state-msg {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px;
  font-size: 12px;
  color: var(--text-muted);
}

.candidate-list {
  list-style: none;
  margin: 0;
  padding: 4px;
}

.candidate-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.candidate-item:hover,
.candidate-item.active {
  background: var(--surface-subtle);
}

.candidate-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #eff6ff;
  color: #2563eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.candidate-info {
  flex: 1;
  min-width: 0;
}

.candidate-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.candidate-handle {
  font-size: 11px;
  color: var(--text-muted);
}

.selected-mark {
  color: #2563eb;
  flex-shrink: 0;
}

.member-role-select {
  padding: 7px 10px;
  font-size: 13px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text);
}

.member-search-form .add-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  white-space: nowrap;
}

@media (max-width: 1100px) {
  .left-panel {
    display: none;
  }
}

@media (max-width: 860px) {
  .review-topbar {
    overflow-x: auto;
    overflow-y: hidden;
    gap: 8px;
    padding: 0 8px;
  }
  .review-topbar::-webkit-scrollbar {
    display: none;
  }
  .topbar-center {
    display: none;
  }
  .right-panel {
    display: none;
  }
  .right-panel.mobile-open {
    display: flex;
    position: fixed;
    top: 48px;
    right: 0;
    bottom: 0;
    width: 320px;
    max-width: 90vw;
    background: var(--surface);
    z-index: 100;
    box-shadow: -4px 0 16px rgba(0, 0, 0, 0.15);
  }
  .mobile-only-btn {
    display: flex;
  }
}

/* Empty State & Init Styles */
.badge-pending-init {
  font-size: 11px;
  background: #fef3c7;
  color: #b45309;
  border: 1px solid #fde68a;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.empty-rev-indicator {
  color: var(--ink-faint);
}

.no-rev-text {
  font-size: 12px;
  color: var(--ink-faint);
  font-style: italic;
}

.no-doc-empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  min-height: 100%;
}

.empty-state-card {
  max-width: 640px;
  width: 100%;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 36px 32px;
  text-align: center;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
}

.empty-icon-wrap {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  color: #64748b;
}

.empty-state-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--ink);
  margin: 0 0 12px;
}

.empty-state-desc {
  font-size: 14px;
  color: var(--ink-soft);
  line-height: 1.6;
  margin: 0 0 28px;
}

.owner-init-actions {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.draft-import-section {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
  text-align: left;
}

.section-badge-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #2563eb;
  margin-bottom: 12px;
}

.draft-cards-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.draft-item-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 12px 14px;
  gap: 12px;
}

.draft-item-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.draft-icon {
  color: #3b82f6;
  flex-shrink: 0;
}

.draft-item-texts {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.draft-filename {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.draft-meta {
  font-size: 11px;
  color: var(--ink-faint);
}

.import-action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  font-size: 13px;
  font-weight: 500;
  background: #2563eb;
  color: #ffffff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
}

.import-action-btn:hover:not(:disabled) {
  background: #1d4ed8;
}

.import-action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.manual-create-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.or-separator {
  font-size: 12px;
  color: var(--ink-faint);
}

.manual-create-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  font-size: 13px;
  font-weight: 500;
  background: #ffffff;
  border: 1px solid var(--line-strong);
  color: var(--ink);
  border-radius: 6px;
  cursor: pointer;
}

.manual-create-btn:hover {
  background: var(--surface-subtle);
  border-color: var(--accent);
}

.non-owner-empty-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px;
  background: #f8fafc;
  border-radius: 8px;
  font-size: 13px;
  color: var(--ink-soft);
}

.hint-clock-icon {
  color: #94a3b8;
}

.create-doc-modal-card {
  width: 680px;
}

.modal-body-scroll {
  overflow-y: auto;
  max-height: 60vh;
  padding-right: 4px;
}

.form-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.btn-text-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  color: var(--accent);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}

.btn-text-action:hover {
  text-decoration: underline;
}

.char-count-row {
  display: flex;
  justify-content: flex-end;
  font-size: 11px;
  color: var(--ink-faint);
  margin-top: 4px;
}

.code-font {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.5;
}

.modal-error-msg {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fee2e2;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  margin-top: 10px;
}

.status-workflow-hint {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-left: 3px solid #3b82f6;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 14px;
}

.status-workflow-hint .hint-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #1e40af;
  margin-bottom: 4px;
}

.status-workflow-hint .hint-text {
  margin: 0;
  font-size: 12px;
  color: #334155;
  line-height: 1.5;
}
</style>
