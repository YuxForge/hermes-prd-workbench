<template>
    <header class="topbar">
      <a class="brand" href="/" aria-label="PRD 工作台">
        <span class="brand-badge-icon"><Layers :size="16" /></span>
        <strong>PRD 工作台</strong>
        <span class="brand-sub-badge">{{ CURRENT_UPDATE_LOG.version.toLowerCase() }}</span>
      </a>

      <!-- Primary Module Navigation -->
      <nav class="primary-module-nav" aria-label="工作台主功能模块导航">
        <router-link to="/" class="primary-nav-tab active">
          <Layers :size="14" />
          <span>PRD 工作台</span>
        </router-link>
        <router-link to="/reviews" class="primary-nav-tab review-tab">
          <CheckSquare :size="14" />
          <span>线上评审</span>
          <span class="nav-badge-pill">协同</span>
        </router-link>
      </nav>

      <div class="topbar-spacer"></div>
      <div
        class="system-health-badge"
        :class="{ offline: health !== 'UP' }"
        :title="health === 'UP' ? 'Hermes Agent 联通正常，控制面在线' : '控制面或 Hermes 服务未就绪'"
      >
        <span class="status-pulse-dot"></span>
        <span class="status-badge-text">{{ health === "UP" ? "系统就绪" : "服务异常" }}</span>
      </div>
      <button
        class="topbar-action"
        type="button"
        :title="historyOpen ? '收起需求栏' : '打开需求栏'"
        :aria-label="historyOpen ? '收起需求栏' : '打开需求栏'"
        @click="historyOpen = !historyOpen"
      >
        <PanelLeftClose v-if="historyOpen" :size="15" />
        <PanelLeftOpen v-else :size="15" />
        <span>{{ historyOpen ? "收起需求" : "需求" }}</span>
      </button>
      <button
        class="topbar-action"
        type="button"
        :title="contextOpen ? '收起证据' : '打开证据'"
        :aria-label="contextOpen ? '收起证据' : '打开证据'"
        @click="contextOpen = !contextOpen"
      >
        <PanelRight :size="15" />
        <span>{{ contextOpen ? "收起证据" : "证据" }}</span>
      </button>
      <button class="topbar-action update-log-action" type="button" title="查看更新日志" aria-label="查看更新日志" @click="openUpdateLog">
        <History :size="15" />
        <span>更新日志</span>
        <span v-if="!updateLogSeen" class="update-log-dot" aria-label="有新更新"></span>
      </button>
      <button
        v-if="user?.role === 'ADMIN'"
        class="topbar-action admin-board-action"
        type="button"
        title="运营监控看板 (管理员专属)"
        aria-label="运营监控看板"
        @click="openAdminDashboard"
      >
        <Shield :size="15" />
        <span>管理看板</span>
      </button>
      <div class="user-profile-menu-wrapper">
        <button
          class="user-avatar-btn"
          type="button"
          :title="user?.displayName || user?.username"
          aria-label="用户账号菜单"
          @click.stop="userMenuOpen = !userMenuOpen"
        >
          <span class="user-avatar-text">{{ userInitial }}</span>
          <span class="user-name-display">{{ user?.displayName || user?.username }}</span>
          <ChevronDown :size="12" class="user-menu-arrow" :class="{ rotated: userMenuOpen }" />
        </button>
        <div v-if="userMenuOpen" class="user-dropdown-card" role="menu" @click.stop>
          <div class="user-dropdown-header">
            <div class="user-dropdown-avatar">{{ userInitial }}</div>
            <div class="user-dropdown-info">
              <div style="display: flex; align-items: center; gap: 6px;">
                <strong>{{ user?.displayName || user?.username }}</strong>
                <span v-if="user?.role === 'ADMIN'" class="admin-badge-mini">ADMIN</span>
              </div>
              <small>@{{ user?.username }}</small>
            </div>
          </div>
          <div class="user-dropdown-divider"></div>
          <button v-if="user?.role === 'ADMIN'" class="user-dropdown-item admin-menu-item" type="button" @click="openAdminDashboard">
            <Shield :size="14" />
            <span>管理监控看板</span>
          </button>
          <div v-if="user?.role === 'ADMIN'" class="user-dropdown-divider"></div>
          <button class="user-dropdown-item logout-item" type="button" @click="logout">
            <LogOut :size="14" />
            <span>退出登录</span>
          </button>
        </div>
      </div>
    </header>

    <main class="workspace workspace-with-history" :class="{ 'history-collapsed': !historyOpen }">
      <aside v-if="historyOpen" class="history-panel" @scroll="onHistoryScroll">
        <div class="task-workspace-section">
          <div class="task-section-header">
            <span class="task-section-title">我的需求 ({{ tasks.length }})</span>
            <div class="task-header-actions">
              <button class="secondary-button compact-ghost" type="button" :disabled="creatingTask" @click="openNewTask">
                <Plus :size="13" />
                新建
              </button>
              <button
                class="icon-button collapse-sidebar-btn"
                type="button"
                title="收起需求栏"
                aria-label="收起需求栏"
                @click="historyOpen = false"
              >
                <PanelLeftClose :size="14" />
              </button>
            </div>
          </div>
          <div v-if="tasks.length > 1" class="task-filter-input-wrapper">
            <Search :size="13" aria-hidden="true" />
            <input
              v-model="taskFilterKeyword"
              class="task-filter-input"
              placeholder="快速过滤需求..."
              aria-label="快速过滤需求"
            />
          </div>
          <p v-if="!tasks.length" class="history-empty">还没有需求，点新建开始。</p>
          <div v-else class="task-card-list">
            <div
              v-for="item in displayedTasks"
              :key="item.id"
              class="task-card-item-wrap"
              :class="{ active: item.id === taskId }"
            >
              <button
                type="button"
                class="task-card-item"
                :title="item.title"
                @click="(item as any).isSharedReview ? openOnlineReview(item.id) : pickTask(item.id)"
              >
                <span class="task-card-name">{{ item.title }}</span>
                <div class="task-card-meta-row">
                  <span class="task-card-meta">{{ formatTime(item.createdAt) }}</span>
                  <span v-if="taskRunningMap[item.id]" class="task-card-status-badge">
                    <Loader2 :size="10" class="spinning" />
                    {{ runningModeLabel(taskRunningMap[item.id].mode) }}中
                  </span>
                  <span v-else-if="(item as any).isSharedReview" class="shared-review-tag">
                    受邀协作
                  </span>
                </div>
              </button>
              <button
                class="task-review-badge-btn"
                type="button"
                title="进入该需求的线上评审工作区"
                aria-label="进入线上评审"
                @click.stop="openOnlineReview(item.id)"
              >
                <CheckSquare :size="11" />
                <span>线上评审</span>
              </button>
            </div>
          </div>
          <!-- 隐式兼容 taskPickerEl 和 taskSearchInput -->
          <div v-show="false" ref="taskPickerEl">
            <input ref="taskSearchInput" v-model="taskQuery" />
          </div>
        </div>

        <div class="history-list-heading">
          <span>当前需求演进</span>
          <select v-model="historyFilter" class="history-filter-select" aria-label="筛选运行记录">
            <option value="ALL">全部记录</option>
            <option value="GENERATE">仅生成</option>
            <option value="REVIEW">仅评审</option>
            <option value="DEMO">仅 Demo</option>
            <option value="POC">仅 POC</option>
            <option value="ESTIMATE">仅评估</option>
            <option value="TEST_CASE">仅用例</option>
            <option value="TECH_SPEC">仅方案</option>
          </select>
        </div>
        <p v-if="!taskId" class="history-empty">先选择或新建一个需求。</p>
        <p v-else-if="!filteredHistory.length" class="history-empty">该需求还没有 {{ historyEmptyLabel }}。</p>
        <button
          v-for="item in filteredHistory"
          :key="item.id"
          type="button"
          class="history-item"
          :class="{ active: run?.id === item.id }"
          @click="openHistory(item)"
        >
          <span class="history-track" aria-hidden="true"></span>
          <span class="history-content">
            <span class="history-mode">{{ modeLabel(item.mode) }}</span>
            <strong>{{ item.summary || item.taskTitle }}</strong>
            <small>
              <span class="history-status" :class="item.status.toLowerCase()">{{ statusLabel(item.status) }}</span>
              · {{ formatTime(item.createdAt) }}
            </small>
          </span>
          <ChevronRight class="history-arrow" :size="15" aria-hidden="true" />
        </button>
      </aside>

      <div class="workspace-main" :style="{ '--context-size': `${contextWidth}px` }">
        <div class="workspace-chrome">
          <section class="task-heading">
            <div class="task-title">
              <button
                v-if="!historyOpen"
                class="icon-button sidebar-expand-trigger"
                type="button"
                title="展开需求列表"
                aria-label="展开需求列表"
                @click="historyOpen = true"
              >
                <PanelLeftOpen :size="15" />
              </button>
              <input
                v-model="title"
                class="title-input"
                aria-label="需求名称"
                title="点击修改需求名称"
                maxlength="200"
                placeholder="未命名需求"
                @blur="persistTitle"
                @keydown.enter.prevent="persistTitle"
              />
              <span v-if="titleSavedHint" class="title-saved"><Check :size="13" />已保存</span>
            </div>
            <div class="mode-switch" role="tablist" aria-label="工作模式">
              <button type="button" class="mode-button" :class="{ active: mode === 'GENERATE' }" @click="setMode('GENERATE')">生成 PRD</button>
              <button type="button" class="mode-button" :class="{ active: mode === 'REVIEW' }" @click="setMode('REVIEW')">评审</button>
              <button type="button" class="mode-button" :class="{ active: mode === 'DEMO' }" @click="setMode('DEMO')">生成 Demo</button>
              <button type="button" class="mode-button" :class="{ active: mode === 'POC' }" @click="setMode('POC')">生成 POC</button>
              <button type="button" class="mode-button" :class="{ active: mode === 'ESTIMATE' }" @click="setMode('ESTIMATE')">工作量评估</button>
              <button type="button" class="mode-button" :class="{ active: mode === 'TEST_CASE' }" @click="setMode('TEST_CASE')">测试用例</button>
              <button type="button" class="mode-button" :class="{ active: mode === 'TECH_SPEC' }" @click="setMode('TECH_SPEC')">技术方案</button>
            </div>
          </section>

          <!-- 全生命周期资产协同矩阵 (Asset Matrix) -->
          <section class="asset-matrix-bar">
            <div class="matrix-label-box">
              <span class="matrix-main-badge"><Workflow :size="13" /> 全景资产协同</span>
              <span class="matrix-sub-text">一核双翼闭环</span>
            </div>
            <div class="asset-chips-scroll">
              <button
                type="button"
                class="asset-chip-btn"
                :class="{ active: mode === 'GENERATE', ready: assetMatrix.GENERATE.ready }"
                :title="assetMatrix.GENERATE.tooltip"
                @click="setMode('GENERATE')"
              >
                <span class="chip-dot"></span>
                <span class="chip-name">PRD</span>
                <span class="chip-tag">{{ assetMatrix.GENERATE.statusText }}</span>
              </button>
              <button
                type="button"
                class="asset-chip-btn"
                :class="{ active: mode === 'REVIEW', ready: assetMatrix.REVIEW.ready }"
                :title="assetMatrix.REVIEW.tooltip"
                @click="setMode('REVIEW')"
              >
                <span class="chip-dot"></span>
                <span class="chip-name">评审</span>
                <span class="chip-tag">{{ assetMatrix.REVIEW.statusText }}</span>
              </button>
              <button
                type="button"
                class="asset-chip-btn"
                :class="{ active: mode === 'DEMO', ready: assetMatrix.DEMO.ready }"
                :title="assetMatrix.DEMO.tooltip"
                @click="setMode('DEMO')"
              >
                <span class="chip-dot"></span>
                <span class="chip-name">原型</span>
                <span class="chip-tag">{{ assetMatrix.DEMO.statusText }}</span>
              </button>
              <button
                type="button"
                class="asset-chip-btn"
                :class="{ active: mode === 'POC', ready: assetMatrix.POC.ready }"
                :title="assetMatrix.POC.tooltip"
                @click="setMode('POC')"
              >
                <span class="chip-dot"></span>
                <span class="chip-name">POC</span>
                <span class="chip-tag">{{ assetMatrix.POC.statusText }}</span>
              </button>
              <button
                type="button"
                class="asset-chip-btn"
                :class="{ active: mode === 'ESTIMATE', ready: assetMatrix.ESTIMATE.ready }"
                :title="assetMatrix.ESTIMATE.tooltip"
                @click="setMode('ESTIMATE')"
              >
                <span class="chip-dot"></span>
                <span class="chip-name">评估</span>
                <span class="chip-tag">{{ assetMatrix.ESTIMATE.statusText }}</span>
              </button>
              <button
                type="button"
                class="asset-chip-btn"
                :class="{ active: mode === 'TEST_CASE', ready: assetMatrix.TEST_CASE.ready }"
                :title="assetMatrix.TEST_CASE.tooltip"
                @click="setMode('TEST_CASE')"
              >
                <span class="chip-dot"></span>
                <span class="chip-name">用例</span>
                <span class="chip-tag">{{ assetMatrix.TEST_CASE.statusText }}</span>
              </button>
              <button
                type="button"
                class="asset-chip-btn"
                :class="{ active: mode === 'TECH_SPEC', ready: assetMatrix.TECH_SPEC.ready }"
                :title="assetMatrix.TECH_SPEC.tooltip"
                @click="setMode('TECH_SPEC')"
              >
                <span class="chip-dot"></span>
                <span class="chip-name">方案</span>
                <span class="chip-tag">{{ assetMatrix.TECH_SPEC.statusText }}</span>
              </button>
            </div>
            <div class="matrix-export-wrap">
              <button
                type="button"
                class="bundle-download-btn"
                :disabled="busy || !hasAnySucceededRun || exportingBundle"
                title="一键流式打包下载当前需求的全量交付资产压缩包 (.zip)"
                @click="handleExportBundle"
              >
                <Loader2 v-if="exportingBundle" :size="13" class="spinning" />
                <Archive v-else :size="13" />
                <span>{{ exportingBundle ? "正在打包…" : "导出交付大礼包 (.zip)" }}</span>
              </button>
            </div>
          </section>

          <section class="run-setup" :class="{ expanded: settingsExpanded }">
            <div class="setup-primary">
            <div class="setup-field materials-field">
              <div class="setup-label">材料 <strong>{{ visibleSourceList.length }}</strong></div>
              <div class="setup-control materials-control">
                <ul v-if="visibleSourceList.length" class="source-list">
                  <li
                    v-for="source in visibleSourceList"
                    :key="source.id"
                    class="source-row"
                    :class="{ fresh: freshSourceIds.includes(source.id) }"
                  >
                    <span class="source-role">{{ sourceRoleLabel(source.name) }}</span>
                    <button
                      type="button"
                      class="source-name"
                      :title="sourceTooltip(source)"
                      @click="selectSource(source)"
                    >
                      {{ displayName(source.name) }}
                    </button>
                    <button
                      v-if="!isDraftSource(source.name)"
                      type="button"
                      class="source-remove"
                      :disabled="busy"
                      :aria-label="`删除 ${sourceTooltip(source)}`"
                      @click="removeSource(source.id)"
                    >
                      <Trash2 :size="13" />
                    </button>
                    <button
                      v-else-if="usesExistingPrd && availablePrdRuns.length > 1"
                      type="button"
                      class="source-change"
                      :disabled="busy"
                      @click="openPrdPicker"
                    >
                      更换
                    </button>
                    <span
                      v-else-if="isDraftSource(source.name)"
                      class="source-auto"
                      :title="usesExistingPrd ? '已自动带入当前 PRD' : '由最近一次成功生成自动带入'"
                    >
                      已带入
                    </span>
                  </li>
                </ul>
                <p v-else class="source-empty">尚未添加材料</p>
                <p v-if="hasActivePrd" class="materials-hint">
                  <template v-if="mode === 'DEMO'">已带入当前 PRD，可直接生成 Demo；也可添加补充材料。</template>
                  <template v-else-if="mode === 'POC'">已带入当前 PRD，可生成可演示的多文件 POC 系统；耗时取决于模型和材料规模。</template>
                  <template v-else-if="mode === 'REVIEW'">已带入当前 PRD，可直接开始评审；也可添加补充材料。</template>
                  <template v-else-if="mode === 'TEST_CASE'">已带入当前 PRD，将以最新 PRD 规格为基准设计测试用例；也可添加补充材料。</template>
                  <template v-else-if="mode === 'TECH_SPEC'">已带入当前 PRD，将以最新 PRD 为基准生成可交付前后端的详细技术方案；也可添加补充材料。</template>
                  <template v-else-if="mode === 'ESTIMATE'">已带入当前 PRD，将基于 PRD 规格拆解功能点并评估工时报价；也可添加补充材料。</template>
                  <template v-else>已有 PRD，可直接开始生成；也可添加补充材料。</template>
                </p>
                <p v-else-if="mode === 'REVIEW'" class="materials-hint">
                  可直接上传已有 PRD 文档（.docx / .md / .txt 等）进行专业评审，无需先行生成 PRD。
                </p>
                <p v-else-if="mode === 'DEMO'" class="materials-hint">
                  可直接上传已有 PRD 文档（.docx / .md / .txt 等）生成通用中后台 风格高保真交互 Demo 原型，无需先行生成 PRD。
                </p>
                <p v-else-if="mode === 'POC'" class="materials-hint">
                  可基于当前 PRD 生成静态多文件 POC 演示系统（HTML/CSS/JS），工作台内预览，ZIP 可本地点击。生成可能需要十几到几十分钟。
                </p>
                <p v-else-if="mode === 'ESTIMATE'" class="materials-hint">
                  可基于需求清单或已有 PRD 拆解页面与接口，按材料与显式假设估算开发与自测人天。
                </p>
                <p v-else-if="mode === 'TEST_CASE'" class="materials-hint">
                  根据需求规格或 PRD 生成稿，智能梳理并生成覆盖功能正向流、边界值极限、逆向异常与权限的标准测试用例集。
                </p>
                <p v-else-if="mode === 'TECH_SPEC'" class="materials-hint">
                  基于需求规格或 PRD，生成可直接交给前后端实施的详细技术方案：分层架构、数据表设计与 DDL 依据、核心 API 契约及分模块设计补充。
                </p>
                <p v-if="usesExistingPrd && pendingPrdCandidate" class="materials-hint warn">
                  有新生成稿待选择；本次仍使用「{{ activePrdTitle }}」，{{ mode === "POC" ? "POC" : mode === "DEMO" ? "Demo" : mode === "REVIEW" ? "评审" : mode === "TEST_CASE" ? "测试用例" : mode === "TECH_SPEC" ? "技术方案" : "评估" }}尚未使用新稿。
                </p>
                <p v-else-if="generateInputsDirty && mode === 'GENERATE'" class="materials-hint warn">
                  生成材料已更新，请重新生成 PRD 后再修订；评审和 Demo 仍可使用当前 PRD。
                </p>
                <button class="secondary-button compact-ghost materials-add" type="button" :disabled="busy" @click="openUpload">
                  <Plus :size="14" />
                  添加材料
                </button>
              </div>
            </div>
            <label class="setup-field goal-field">
              <span class="setup-label">本次目标</span>
              <input v-model="instruction" class="setup-input" :disabled="busy" :placeholder="instructionPlaceholder" />
            </label>
            <div class="setup-action-col" :class="{ 'with-revise': showReviseToggle }">
              <button class="run-button setup-action" type="button" :class="{ busy }" :disabled="busy" @click="startRun()">
                <Play :size="15" />
                {{ runButtonLabel }}
              </button>
              <label v-if="showReviseToggle" class="revise-toggle">
                <input v-model="revising" type="checkbox" :disabled="busy" />
                基于当前稿修订
              </label>
            </div>
            </div>
            <div class="settings-summary-bar">
              <button
                class="settings-toggle"
                type="button"
                :aria-expanded="settingsExpanded"
                aria-controls="advanced-run-settings"
                @click="settingsExpanded = !settingsExpanded"
              >
                <SlidersHorizontal :size="14" />
                <span>{{ settingsSummary }}</span>
                <ChevronDown :size="14" :class="{ rotated: settingsExpanded }" />
              </button>
              <span v-if="qualityHint" class="quality-hint">{{ qualityHint }}</span>
            </div>
            <div v-if="settingsExpanded" id="advanced-run-settings" class="advanced-settings">
              <div class="advanced-field completeness-field" title="默认标准。赶时间选快速，写细选详尽。">
                <span class="setup-label">完整度</span>
                <div class="completeness-switch" role="radiogroup" aria-label="产出完整度，默认标准">
                  <button
                    v-for="item in qualityCatalog.completeness"
                    :key="item.id"
                    type="button"
                    class="completeness-button"
                    :class="[completenessTone(item.id), { active: completeness === item.id }]"
                    :disabled="busy"
                    role="radio"
                    :aria-checked="completeness === item.id"
                    :title="item.description"
                    @click="completeness = item.id"
                  >
                    {{ item.name }}
                  </button>
                </div>
              </div>
              <label v-if="mode === 'DEMO'" class="advanced-field skill-field">
                <span class="setup-label">页面风格</span>
                <select v-model="demoSkill" class="setup-input" :disabled="busy" aria-label="Demo 风格 skill">
                  <option v-for="skill in demoSkills" :key="skill.id" :value="skill.id" :title="skill.description">{{ skill.name }}</option>
                </select>
              </label>
              <div class="advanced-field lens-field">
                <span class="setup-label">{{ mode === "GENERATE" ? "文档拆法" : mode === "ESTIMATE" ? "评估重点" : mode === "TEST_CASE" ? "测试重点" : mode === "TECH_SPEC" ? "技术重点" : "评审重点" }}</span>
                <div class="lens-chips" role="group" :aria-label="lensGroupLabel">
                  <button
                    v-for="item in (qualityCatalog.lenses[mode] || [])"
                    :key="item.id"
                    type="button"
                    class="lens-chip"
                    :class="{ active: isLensActive(item.id, item.default) }"
                    :disabled="busy"
                    :title="item.description"
                    @click="handleLensSelect(item.id, item.default)"
                  >
                    <Check v-if="isLensActive(item.id, item.default)" :size="12" class="lens-check-icon" />
                    {{ item.name }}
                  </button>
                </div>
              </div>
            </div>
          </section>
        </div>

        <section class="work-area" :class="{ 'with-context': contextOpen }">
          <article ref="resultCanvasEl" class="result-canvas" :class="{ 'is-fullscreen': resultFullscreen }">
            <header v-if="resultToolbar" class="result-toolbar">
              <div class="result-toolbar-heading">
                <span>当前结果</span>
                <h2 class="result-toolbar-title">{{ resultToolbar.title }}</h2>
                <small v-if="resultToolbar.kind === 'GENERATE' && showingCurrentDraft" class="draft-state" :class="currentDraftState?.toLowerCase()">
                  {{ draftStateLabel }}
                </small>
              </div>
              <div class="export-actions">
                <template v-if="resultToolbar.kind === 'GENERATE'">
                  <button v-if="showingCurrentDraft && !draftEditing" class="secondary-button compact-ghost" type="button" :disabled="busy" @click="beginDraftEdit">
                    编辑
                  </button>
                  <template v-if="showingCurrentDraft && draftEditing">
                    <button class="secondary-button compact-ghost" type="button" :disabled="draftSaving" @click="cancelDraftEdit">取消</button>
                    <button class="run-button" type="button" :disabled="draftSaving || !draftDirty" @click="saveDraft">
                      {{ draftSaving ? "保存中" : "保存当前稿" }}
                    </button>
                  </template>
                  <button class="secondary-button compact-ghost" type="button" :disabled="busy" @click="startDemoFromPrd">
                    <Play :size="14" />
                    生成 Demo
                  </button>
                  <button class="secondary-button compact-ghost" type="button" :disabled="busy" @click="startPocFromPrd">
                    <Code :size="14" />
                    生成 POC
                  </button>
                  <button class="run-button" type="button" :disabled="busy || run?.accepted" @click="confirmCurrent">
                    <Check :size="15" />
                    {{ run?.accepted ? "已采用" : "采用本次生成" }}
                  </button>
                  <details class="action-menu">
                    <summary class="icon-button" title="更多操作" aria-label="更多操作"><MoreHorizontal :size="17" /></summary>
                    <div class="action-menu-popover">
                      <button type="button" @click="exportMarkdown"><FileText :size="14" />导出 Markdown</button>
                      <button
                        type="button"
                        :disabled="!prdPackFiles.length || exportingPackage"
                        @click="exportPrdPack"
                      >
                        <Archive :size="14" />{{ exportingPackage ? "打包中" : "导出文档包 ZIP" }}
                      </button>
                      <button type="button" :disabled="exporting" @click="exportDocx">
                        <Download :size="14" />{{ exporting ? "导出中" : "导出 Word" }}
                      </button>
                    </div>
                  </details>
                </template>
                <template v-else-if="resultToolbar.kind === 'DEMO'">
                  <button class="secondary-button compact-ghost" type="button" title="全屏沉浸演示 (按ESC退出)" @click="isPresentationMode = true">
                    <Maximize2 :size="14" />全屏演示
                  </button>
                  <button class="secondary-button compact-ghost" type="button" title="一键定制拜访客户名称" @click="customizerModalOpen = true">
                    <Sparkles :size="14" />客户定制
                  </button>
                  <button
                    class="secondary-button compact-ghost"
                    type="button"
                    :disabled="!demoPackageFiles.length || exportingPackage"
                    :title="mode === 'POC' ? '在浏览器本地打包 poc_files 原始多文件，不上传到服务端' : '在浏览器本地打包 package_files，不上传到服务端'"
                    @click="exportDemoPackage"
                  >
                    <Loader2 v-if="exportingPackage" :size="14" class="spinning" />
                    <Archive v-else :size="14" />{{ exportingPackage ? "打包中" : mode === "POC" ? "导出 POC ZIP" : "导出定制包 ZIP" }}
                  </button>
                  <button class="run-button" type="button" @click="openLocalHtml"><ExternalLink :size="15" />本地打开</button>
                  <details class="action-menu">
                    <summary class="icon-button" title="更多操作" aria-label="更多操作"><MoreHorizontal :size="17" /></summary>
                    <div class="action-menu-popover">
                      <button type="button" @click="exportHtml"><Download :size="14" />导出 HTML</button>
                      <button type="button" @click="exportDemoScript"><FileText :size="14" />导出讲解词</button>
                    </div>
                  </details>
                </template>
                <template v-else-if="resultToolbar.kind === 'ESTIMATE'">
                  <div class="quick-rate-box">
                    <span class="rate-label">基准单价:</span>
                    <input
                      v-model.number="estimateDailyRate"
                      type="number"
                      min="0"
                      step="100"
                      placeholder="元/人天"
                      class="quick-rate-input"
                      title="输入人天单价，动态实时测算报价金额"
                    />
                    <span v-if="estimateDailyRate && estimateDailyRate > 0" class="rate-total">
                      预估总额: <strong>¥{{ formatPrice(estimateTotalQuote) }}</strong>
                    </span>
                  </div>
                  <button
                    class="secondary-button compact-ghost"
                    type="button"
                    title="定制显示列与扩展字段"
                    @click="estimateColumnsDrawerOpen = true"
                  >
                    <SlidersHorizontal :size="14" />表头定制
                  </button>
                  <button
                    class="run-button"
                    type="button"
                    :disabled="exporting"
                    @click="handleExportEstimateXlsx"
                  >
                    <Download :size="15" />{{ exporting ? "导出中…" : "导出 Excel (.xlsx)" }}
                  </button>
                </template>
                <template v-else-if="resultToolbar.kind === 'TEST_CASE'">
                  <button
                    class="secondary-button compact-ghost"
                    type="button"
                    title="定制测试用例显示列与扩展字段"
                    @click="testCaseColumnsDrawerOpen = true"
                  >
                    <SlidersHorizontal :size="14" />表头定制
                  </button>
                  <button
                    class="run-button"
                    type="button"
                    :disabled="exporting"
                    @click="handleExportTestCaseXlsx"
                  >
                    <Download :size="15" />{{ exporting ? "导出中…" : "导出 Excel (.xlsx)" }}
                  </button>
                </template>
                <template v-else-if="resultToolbar.kind === 'TECH_SPEC'">
                  <button
                    class="secondary-button compact-ghost"
                    type="button"
                    title="复制完整技术方案 Markdown（架构+表+API）"
                    :disabled="exporting"
                    @click="copyTechSpecMarkdown"
                  >
                    <Copy :size="14" />{{ copiedTechSpec ? "已复制！" : "复制完整 Markdown" }}
                  </button>
                  <button
                    class="run-button"
                    type="button"
                    title="下载完整技术方案 Markdown 文件"
                    :disabled="exporting"
                    @click="handleExportTechSpecMarkdown"
                  >
                    <Download :size="15" />{{ exporting ? "导出中…" : "导出 Markdown (.md)" }}
                  </button>
                </template>
                <button
                  class="icon-button result-fullscreen-button"
                  type="button"
                  :title="resultFullscreen ? '退出全屏' : '全屏查看'"
                  :aria-label="resultFullscreen ? '退出全屏' : '全屏查看'"
                  @click="toggleResultFullscreen"
                >
                  <Minimize2 v-if="resultFullscreen" :size="17" />
                  <Maximize2 v-else :size="17" />
                </button>
              </div>
            </header>
            <div class="result-scroll">
              <p v-if="errorMessage && !modeBusy" class="error-state">{{ errorMessage }}</p>
              <div v-if="otherBusy && !modeBusy && showResult" class="run-banner">{{ otherBusyHint }}</div>
              <div v-if="modeBusy" class="run-progress-panel">
                <p class="result-kicker">
                  {{ progressTitle }}
                  <span class="completeness-badge" :class="runCompletenessTone">{{ runCompletenessName }}</span>
                </p>
                <div class="run-progress" aria-hidden="true">
                  <span :style="{ width: progressWidth }"></span>
                </div>
                <div class="running-stepper-card">
                  <div class="stepper-header">
                    <span>Hermes 智能推演阶段</span>
                    <span>已耗时 {{ elapsedLabel }}</span>
                  </div>
                  <div class="stepper-timeline">
                    <div
                      v-for="(st, sIdx) in stepperStages"
                      :key="st.key"
                      class="stepper-node"
                      :class="{ active: stepperCurrentIndex === sIdx, done: stepperCurrentIndex > sIdx }"
                    >
                      <span class="stepper-node-dot">{{ sIdx + 1 }}</span>
                      <span class="stepper-node-label">{{ st.label }}</span>
                    </div>
                  </div>
                </div>
                <small style="margin-top: 8px; display: block;">当前阶段：{{ stageLabel(run?.stage) }} · {{ elapsedLabel }} · 质量标准：{{ runCompletenessName }}</small>
              </div>
              <div v-else-if="run?.status === 'FAILED'" class="error-state">
                <p>{{ run.error || errorMessage }}</p>
                <button class="run-button" type="button" :disabled="busy" @click="startRun()">重新运行</button>
              </div>
              <div v-else-if="showResult && result && mode === 'GENERATE'" class="generate-result" :class="{ 'editing-layout': showingCurrentDraft && draftEditing }">
                <div v-if="pendingPrdCandidate && pendingPrdCandidate.id === run?.id && run.id !== activePrdRunId" class="candidate-banner">
                  <div>
                    <strong>新生成稿待选择</strong>
                    <p>评审与 Demo 仍基于当前稿，尚未使用本次生成结果。</p>
                  </div>
                  <div class="candidate-actions">
                    <button class="secondary-button compact-ghost" type="button" @click="showActiveDraft">查看当前稿</button>
                    <button class="run-button compact" type="button" @click="selectActivePrd(run.id)">替换当前稿</button>
                  </div>
                </div>
                <p class="summary">{{ String(result.summary || "") }}</p>

                <div v-if="prdPackFiles.length && !(showingCurrentDraft && draftEditing)" class="demo-result-tabs" role="tablist">
                  <button
                    type="button"
                    role="tab"
                    class="demo-result-tab"
                    :class="{ active: prdResultTab === 'ASSEMBLED' }"
                    :aria-selected="prdResultTab === 'ASSEMBLED'"
                    @click="prdResultTab = 'ASSEMBLED'"
                  >
                    合并稿
                  </button>
                  <button
                    type="button"
                    role="tab"
                    class="demo-result-tab"
                    :class="{ active: prdResultTab === 'PACK' }"
                    :aria-selected="prdResultTab === 'PACK'"
                    @click="prdResultTab = 'PACK'"
                  >
                    文档包
                    <span class="demo-package-count">{{ prdPackFiles.length }}</span>
                  </button>
                </div>

                <!-- 编辑态：带快捷排版工具条与分屏实时对照 -->
                <div v-if="showingCurrentDraft && draftEditing" class="editor-advanced-shell">
                  <div class="editor-toolbar">
                    <div class="editor-toolbar-actions">
                      <button type="button" class="toolbar-button" title="插入二级标题" @click="insertH2">H2</button>
                      <button type="button" class="toolbar-button" title="插入三级标题" @click="insertH3">H3</button>
                      <span class="toolbar-separator"></span>
                      <button type="button" class="toolbar-button" title="加粗 (Ctrl+B)" @click="insertBold"><Bold :size="13" /></button>
                      <button type="button" class="toolbar-button" title="无序列表" @click="insertList"><List :size="13" /></button>
                      <button type="button" class="toolbar-button" title="插入表格" @click="insertTable"><Table :size="13" /></button>
                      <button type="button" class="toolbar-button" title="插入代码块" @click="insertCode"><Code :size="13" /></button>
                      <button type="button" class="toolbar-button" title="插入引用" @click="insertQuote"><Quote :size="13" /></button>
                    </div>
                    <div class="editor-toolbar-meta">
                      <span>{{ draftEditor.length.toLocaleString() }} 字</span>
                      <button
                        type="button"
                        class="split-toggle-btn"
                        :class="{ active: editorSplitMode === 'split' }"
                        @click="editorSplitMode = editorSplitMode === 'split' ? 'single' : 'split'"
                      >
                        <Columns :size="13" />
                        {{ editorSplitMode === 'split' ? '分屏对照' : '单栏编辑' }}
                      </button>
                    </div>
                  </div>
                  <nav v-if="editorTocList.length" class="editor-outline-bar" aria-label="章节定位">
                    <span class="editor-outline-label"><BookOpen :size="13" />章节定位</span>
                    <div class="editor-outline-items">
                      <button
                        v-for="heading in editorTocList"
                        :key="heading.id"
                        type="button"
                        class="editor-outline-item"
                        :class="`level-${heading.level}`"
                        :title="`定位并选中：${heading.text}`"
                        @click="locateEditorHeading(heading.id)"
                      >
                        {{ heading.text }}
                      </button>
                    </div>
                  </nav>
                  <div class="editor-split-grid" :class="{ 'split-mode': editorSplitMode === 'split' }">
                    <div class="editor-pane">
                      <textarea
                        ref="editorTextareaEl"
                        v-model="draftEditor"
                        class="draft-editor"
                        maxlength="500000"
                        aria-label="编辑当前 PRD Markdown"
                        placeholder="在此直接编辑 PRD..."
                        @scroll="syncEditorScroll('editor', $event)"
                        @keydown="onEditorKeydown"
                      ></textarea>
                    </div>
                    <div v-if="editorSplitMode === 'split'" ref="editorPreviewEl" class="editor-preview-card" @scroll="syncEditorScroll('preview', $event)">
                      <div class="markdown" v-html="editorPreviewHtml"></div>
                    </div>
                  </div>
                </div>

                <section v-else-if="prdResultTab === 'PACK' && prdPackFiles.length" class="demo-package-panel">
                  <div class="demo-package-shell">
                    <aside class="demo-package-files" aria-label="PRD 文档包文件列表">
                      <div class="demo-package-files-header">
                        <strong>文件树</strong>
                        <span>{{ prdPackFiles.length }} 个文件</span>
                      </div>
                      <button
                        v-for="file in prdPackFiles"
                        :key="file.path"
                        type="button"
                        class="demo-package-file"
                        :class="{ active: selectedPrdFile?.path === file.path }"
                        :title="file.path"
                        @click="selectedPrdPath = file.path"
                      >
                        <FileText :size="13" />
                        <span>{{ file.path }}</span>
                      </button>
                    </aside>
                    <div class="demo-package-code-panel">
                      <header class="demo-package-code-header">
                        <code>{{ selectedPrdFile?.path || "请选择文件" }}</code>
                      </header>
                      <pre v-if="selectedPrdFile" class="demo-package-code"><code>{{ selectedPrdFile.content }}</code></pre>
                    </div>
                  </div>
                  <p class="hint demo-package-root-hint">浏览器本地打包为 {{ prdPackRoot }}/，不上传服务端。历史结果若无 prd_files 只显示合并稿。</p>
                </section>

                <!-- 阅读态：TOC 大纲导航（默认置左） + 正文 -->
                <div v-else class="prd-content-layout" :class="{ 'toc-right': tocPosition === 'right', 'toc-hidden': tocCollapsed }">
                  <aside v-if="tocList.length && !tocCollapsed" class="toc-sidebar" :class="`pos-${tocPosition}`" aria-label="文档目录大纲">
                    <header class="toc-header">
                      <span class="toc-title">
                        <BookOpen :size="13" /> 目录大纲 ({{ displayedTocList.length }})
                      </span>
                      <div class="toc-header-tools">
                        <div v-if="hasSubHeadings" class="toc-depth-tabs" role="radiogroup" aria-label="大纲深度">
                          <button
                            type="button"
                            class="toc-depth-btn"
                            :class="{ active: tocDepthFilter === 'ALL' }"
                            title="显示全部层级标题"
                            @click="tocDepthFilter = 'ALL'"
                          >全部</button>
                          <button
                            type="button"
                            class="toc-depth-btn"
                            :class="{ active: tocDepthFilter === 'H2' }"
                            title="仅显示一级与二级主章节"
                            @click="tocDepthFilter = 'H2'"
                          >主章</button>
                        </div>
                        <button
                          type="button"
                          class="toc-icon-btn"
                          :title="tocPosition === 'left' ? '切换目录至右侧' : '切换目录至左侧'"
                          aria-label="切换目录位置"
                          @click="tocPosition = tocPosition === 'left' ? 'right' : 'left'"
                        >
                          <ArrowLeftRight :size="12" />
                        </button>
                        <button
                          type="button"
                          class="toc-icon-btn"
                          title="收起目录大纲"
                          aria-label="收起目录大纲"
                          @click="tocCollapsed = true"
                        >
                          <PanelLeftClose v-if="tocPosition === 'left'" :size="12" />
                          <PanelRightClose v-else :size="12" />
                        </button>
                      </div>
                    </header>

                    <!-- 当标题超过 12 个时显示快速过滤搜索框 -->
                    <div v-if="tocList.length >= 12" class="toc-filter-box">
                      <Search :size="12" class="toc-filter-icon" aria-hidden="true" />
                      <input
                        v-model="tocKeyword"
                        class="toc-filter-input"
                        placeholder="过滤大纲章节..."
                        aria-label="过滤大纲章节"
                      />
                      <button
                        v-if="tocKeyword"
                        class="toc-filter-clear-btn"
                        type="button"
                        title="清空"
                        aria-label="清空过滤"
                        @click="tocKeyword = ''"
                      >
                        <X :size="11" />
                      </button>
                    </div>

                    <div class="toc-items-container">
                      <p v-if="!displayedTocList.length" class="toc-empty-hint">无匹配章节</p>
                      <button
                        v-for="heading in displayedTocList"
                        :key="heading.id"
                        type="button"
                        class="toc-link-item"
                        :class="`level-${heading.level}`"
                        :title="heading.text"
                        @click="scrollToHeading(heading.id)"
                      >
                        <span class="toc-link-text">{{ heading.text }}</span>
                      </button>
                    </div>
                  </aside>

                  <article class="prd-article-body">
                    <button
                      v-if="tocList.length && tocCollapsed"
                      type="button"
                      class="toc-expand-float-btn"
                      title="展开目录大纲"
                      aria-label="展开目录大纲"
                      @click="tocCollapsed = false"
                    >
                      <BookOpen :size="13" />
                      <span>展开目录大纲</span>
                    </button>
                    <div class="markdown" v-html="markdownHtml"></div>
                  </article>
                </div>
                <div v-if="draftConflict && showingCurrentDraft" class="draft-conflict" role="alert">
                  当前稿已变化，你的输入仍保留。
                  <button class="secondary-button compact-ghost" type="button" @click="reloadDraftAfterConflict">重新加载</button>
                </div>
                <section v-if="openQuestions.length">
                  <h3>待确认</h3>
                  <label v-for="(item, index) in openQuestions" :key="index" class="question-row">
                    <input
                      type="checkbox"
                      :checked="isQuestionSelected(item)"
                      :disabled="busy"
                      @change="toggleQuestion(item)"
                    />
                    <span>{{ questionText(item) }}</span>
                  </label>
                  <p class="hint">勾选后点「修订当前稿」，会带入下一轮；修订会保留目录和结论方向，不保证逐句不改。</p>
                </section>
                <section v-if="references.length">
                  <h3>引用</h3>
                  <button v-for="(item, index) in references" :key="index" type="button" class="issue-row" @click="openReference(item)">
                    {{ String(item.sourceName || item.id || "引用") }}
                  </button>
                </section>
                <button class="secondary-button result-rerun" type="button" :disabled="busy" @click="startRun()"><RotateCcw :size="14" />重新生成</button>
                <button
                  v-if="hasGenerateDraft"
                  class="secondary-button result-rerun"
                  type="button"
                  :disabled="busy"
                  @click="startRevise"
                >
                  修订当前稿
                </button>
              </div>
              <div v-else-if="showResult && result && mode === 'REVIEW'" class="review-result">
                <div class="review-prd-reference-bar">
                  <div class="review-ref-info">
                    <span class="review-ref-label">评审依据底稿</span>
                    <strong class="review-ref-name">{{ activePrdTitle }}</strong>
                  </div>
                  <button type="button" class="secondary-button compact-ghost" title="返回 PRD 正文对照查验" @click="setMode('GENERATE')">
                    <BookOpen :size="13" /> 对照查看 PRD 正文
                  </button>
                </div>
                <p class="result-kicker">评审完成</p>
                <h2>{{ String(result.title || "评审结果") }}</h2>
                <p class="summary">{{ String(result.summary || "") }}</p>
                <div v-if="feedbackConflict" class="draft-conflict" role="alert">
                  评审处理记录已变化，你当前选择仍保留。
                  <button class="secondary-button compact-ghost" type="button" @click="reloadFeedbackAfterConflict">重新加载</button>
                </div>
                <div v-else-if="feedbackLoadError" class="draft-conflict" role="alert">
                  {{ feedbackLoadError }}
                  <button class="secondary-button compact-ghost" type="button" @click="retryLoadFeedback">重试加载</button>
                </div>
                <section>
                  <h3>关键问题 {{ blockingIssues.length }}</h3>
                  <article
                    v-for="(item, index) in blockingIssues"
                    :key="index"
                    class="issue-row blocking"
                    :class="{ active: isContextItem(item) }"
                  >
                    <button type="button" class="issue-evidence" @click="openItem(item)">
                    <span class="issue-copy">
                      <strong>{{ issueTitle(item) }}</strong>
                      <span v-if="issueDetail(item)">{{ issueDetail(item) }}</span>
                    </span>
                    <ChevronRight class="issue-arrow" :size="16" aria-hidden="true" />
                    </button>
                    <div class="feedback-row">
                      <select :value="feedbackAction(`blockingIssues/${index}`)" :disabled="busy || !feedback" aria-label="处理决定" @change="onFeedbackAction(`blockingIssues/${index}`, $event)">
                        <option value="" disabled>选择处理</option>
                        <option value="EDITED">已改</option>
                        <option value="WONT_DO">不做</option>
                        <option value="NEEDS_CUSTOMER">待问客户</option>
                      </select>
                      <input :value="feedbackNote(`blockingIssues/${index}`)" :disabled="busy || !feedback || !feedbackAction(`blockingIssues/${index}`)" maxlength="2000" placeholder="说明或待确认内容" @input="onFeedbackNote(`blockingIssues/${index}`, $event)" />
                      <button class="secondary-button compact-ghost" type="button" :disabled="busy || !feedback || feedbackSavingPath === `blockingIssues/${index}`" @click="saveFeedbackItem(`blockingIssues/${index}`)">保存</button>
                      <small v-if="feedbackAction(`blockingIssues/${index}`) === 'EDITED'" class="feedback-hint">请先在当前稿中完成并保存修改。</small>
                    </div>
                    <div class="review-item-actions">
                      <button type="button" class="link-action-btn" title="在 PRD 正文中定位对应章节" @click="locateIssueInPrd(item)">
                        <Compass :size="12" /> 定位正文
                      </button>
                      <button type="button" class="link-action-btn" title="将此问题带入下轮修改目标" @click="applyFeedbackToObjective(item)">
                        <Sparkles :size="12" /> 带入下轮目标
                      </button>
                    </div>
                  </article>
                  <p v-if="!blockingIssues.length">本次未提出阻断问题。</p>
                </section>
                <details v-if="otherSuggestions.length" class="review-details">
                  <summary>其他建议 {{ otherSuggestions.length }}</summary>
                  <article
                    v-for="(item, index) in otherSuggestions"
                    :key="index"
                    class="issue-row suggestion"
                    :class="{ active: isContextItem(item) }"
                  >
                    <button type="button" class="issue-evidence" @click="openItem(item)">
                    <span class="issue-copy">
                      <strong>{{ issueTitle(item) }}</strong>
                      <span v-if="issueDetail(item)">{{ issueDetail(item) }}</span>
                    </span>
                    <ChevronRight class="issue-arrow" :size="16" aria-hidden="true" />
                    </button>
                    <div class="feedback-row">
                      <select :value="feedbackAction(`otherSuggestions/${index}`)" :disabled="busy || !feedback" aria-label="处理决定" @change="onFeedbackAction(`otherSuggestions/${index}`, $event)">
                        <option value="" disabled>选择处理</option>
                        <option value="EDITED">已改</option>
                        <option value="WONT_DO">不做</option>
                        <option value="NEEDS_CUSTOMER">待问客户</option>
                      </select>
                      <input :value="feedbackNote(`otherSuggestions/${index}`)" :disabled="busy || !feedback || !feedbackAction(`otherSuggestions/${index}`)" maxlength="2000" placeholder="说明或待确认内容" @input="onFeedbackNote(`otherSuggestions/${index}`, $event)" />
                      <button class="secondary-button compact-ghost" type="button" :disabled="busy || !feedback || feedbackSavingPath === `otherSuggestions/${index}`" @click="saveFeedbackItem(`otherSuggestions/${index}`)">保存</button>
                      <small v-if="feedbackAction(`otherSuggestions/${index}`) === 'EDITED'" class="feedback-hint">请先在当前稿中完成并保存修改。</small>
                    </div>
                    <div class="review-item-actions">
                      <button type="button" class="link-action-btn" title="在 PRD 正文中定位对应章节" @click="locateIssueInPrd(item)">
                        <Compass :size="12" /> 定位正文
                      </button>
                      <button type="button" class="link-action-btn" title="将此建议带入下轮修改目标" @click="applyFeedbackToObjective(item)">
                        <Sparkles :size="12" /> 带入下轮目标
                      </button>
                    </div>
                  </article>
                </details>
                <details v-if="readyItems.length" class="review-details">
                  <summary>可沿用 {{ readyItems.length }}</summary>
                  <div v-for="(item, index) in readyItems" :key="index" class="ready-item">
                    <strong>{{ issueTitle(item) }}</strong>
                    <p v-if="issueDetail(item)">{{ issueDetail(item) }}</p>
                  </div>
                </details>
                <button class="secondary-button result-rerun" type="button" :disabled="busy" @click="startRun()"><RotateCcw :size="14" />重新运行</button>
              </div>
              <div v-else-if="showResult && result && (mode === 'DEMO' || mode === 'POC')" class="generate-result demo-result">
                <p class="summary">{{ String(result.summary || "") }}</p>
                <div class="demo-result-tabs" role="tablist" aria-label="Demo 结果视图">
                  <button
                    type="button"
                    role="tab"
                    class="demo-result-tab"
                    :class="{ active: demoResultTab === 'PREVIEW' }"
                    :aria-selected="demoResultTab === 'PREVIEW'"
                    @click="demoResultTab = 'PREVIEW'"
                  >
                    <Play :size="14" />交互演示
                  </button>
                  <button
                    type="button"
                    role="tab"
                    class="demo-result-tab"
                    :class="{ active: demoResultTab === 'PACKAGE' }"
                    :aria-selected="demoResultTab === 'PACKAGE'"
                    @click="demoResultTab = 'PACKAGE'"
                  >
                    <Code :size="14" />{{ mode === 'POC' ? '源码文件' : '工程源码' }}
                    <span class="demo-package-count">{{ demoPackageFiles.length }}</span>
                  </button>
                </div>
                <section v-if="demoResultTab === 'PREVIEW'" class="demo-result-panel">
                  <div v-if="demoPreviewHtml" class="demo-preview-shell">
                    <div v-if="demoPreviewState === 'loading'" class="demo-preview-status" role="status">
                      <span class="loading-spinner" aria-hidden="true"></span>
                      正在加载预览
                    </div>
                    <div v-else-if="demoPreviewState === 'failed'" class="demo-preview-status failed" role="alert">
                      预览加载失败，可重新生成或本地打开。
                    </div>
                    <iframe
                      class="demo-preview-frame"
                      :class="{ ready: demoPreviewState === 'ready' }"
                      :srcdoc="demoPreviewHtml"
                      :sandbox="demoPreviewSandbox"
                      referrerpolicy="no-referrer"
                      title="Demo 预览"
                      @load="markDemoPreviewReady"
                      @error="markDemoPreviewFailed"
                    ></iframe>
                  </div>
                  <p v-else class="hint">当前结果没有可预览的 HTML。可导出后本地打开。</p>
                  <section v-if="demoPreviewHtml" class="demo-patch-tool">
                    <h3>调整可见文案</h3>
                    <div class="demo-patch-controls">
                      <input v-model="demoPatchFrom" maxlength="200" placeholder="唯一原文" aria-label="Demo 原文" />
                      <input v-model="demoPatchTo" maxlength="200" placeholder="新文案" aria-label="Demo 新文案" />
                      <button class="secondary-button compact-ghost" type="button" @click="applyDemoTextPatch">应用</button>
                      <button class="secondary-button compact-ghost" type="button" :disabled="!patchedDemoHtml" @click="resetDemoPatch">恢复原始</button>
                    </div>
                    <p v-if="demoPatchMessage" class="hint">{{ demoPatchMessage }}</p>
                  </section>
                  <section class="demo-script">
                    <div class="demo-script-header">
                      <h3>演示讲解词</h3>
                      <span v-if="isFallbackDemoScript" class="demo-script-badge">基于概要自动生成</span>
                    </div>
                    <div v-if="demoScriptMarkdown" class="markdown" v-html="demoScriptHtml"></div>
                    <p v-else class="hint">本次未生成讲解词。</p>
                  </section>
                </section>
                <section v-else class="demo-package-panel">
                  <div v-if="demoPackageFiles.length" class="demo-package-shell">
                    <aside class="demo-package-files" aria-label="定制包文件列表">
                      <div class="demo-package-files-header">
                        <strong>文件树</strong>
                        <span>{{ demoPackageFiles.length }} 个文件</span>
                      </div>
                      <button
                        v-for="file in demoPackageFiles"
                        :key="file.path"
                        type="button"
                        class="demo-package-file"
                        :class="{ active: selectedPackageFile?.path === file.path }"
                        :title="file.path"
                        @click="selectedPackagePath = file.path"
                      >
                        <FileText :size="13" />
                        <span>{{ file.path }}</span>
                      </button>
                    </aside>
                    <div class="demo-package-code-panel">
                      <header class="demo-package-code-header">
                        <code>{{ selectedPackageFile?.path || "请选择文件" }}</code>
                        <button
                          v-if="selectedPackageFile"
                          type="button"
                          class="secondary-button compact-ghost"
                          @click="copyPackageFile"
                        >
                          <Copy :size="13" />{{ packageCopyMessage || "复制代码" }}
                        </button>
                      </header>
                      <pre v-if="selectedPackageFile" class="demo-package-code"><code>{{ selectedPackageFile.content }}</code></pre>
                      <p v-else class="hint">请选择左侧文件查看源码。</p>
                    </div>
                  </div>
                  <div v-else class="demo-package-empty">
                    <Code :size="22" />
                    <strong>{{ mode === "POC" ? "本次结果未提供源码文件" : "本次结果未提供工程源码" }}</strong>
                    <p>{{ mode === "POC" ? "当前仍可使用交互演示和 HTML 导出。POC 应提交沙箱多文件，由控制面组装 poc_files。" : "当前仍可使用交互演示和 HTML 导出。通用模板输出单文件 HTML，可选文本附件不保证可编译。" }}</p>
                  </div>
                  <p v-if="demoPackage.rejected.length" class="demo-package-warning" role="alert">
                    已忽略 {{ demoPackage.rejected.length }} 个不安全或非文本文件；凭据、环境文件和路径穿越内容不会进入 ZIP。
                  </p>
                  <p v-if="demoPackageFiles.length" class="hint demo-package-root-hint">
                    ZIP 根目录：<code>{{ demoPackageRoot }}/</code>，文件仅在浏览器本地生成。
                  </p>
                </section>
                <button class="secondary-button result-rerun" type="button" :disabled="busy" @click="startRun()"><RotateCcw :size="14" />{{ mode === "POC" ? "重新生成 POC" : "重新生成 Demo" }}</button>
              </div>
              <div v-else-if="showResult && result && mode === 'ESTIMATE'" class="generate-result estimate-result">
                <!-- 顶部报价免责声明卡片 -->
                <div class="estimate-scope-alert" role="alert">
                  <div class="alert-icon"><AlertCircle :size="18" /></div>
                  <div class="alert-body">
                    <strong>报价范围铁律声明</strong>
                    <p>{{ estimateScopeNote }}</p>
                  </div>
                </div>

                <!-- KPI 卡片区域 -->
                <div class="estimate-kpi-grid">
                  <div class="kpi-card highlight">
                    <span class="kpi-label">总预估工作量</span>
                    <strong class="kpi-value">{{ estimateTotalDays }} <small>人天</small></strong>
                    <span class="kpi-hint">基于功能拆分与评估假设</span>
                  </div>
                  <div class="kpi-card">
                    <span class="kpi-label">研发开发工时</span>
                    <strong class="kpi-value">{{ estimateDevDays }} <small>人天</small></strong>
                    <span class="kpi-hint">全栈 AI 协同开发</span>
                  </div>
                  <div class="kpi-card">
                    <span class="kpi-label">研发自测工时</span>
                    <strong class="kpi-value">{{ estimateTestDays }} <small>人天</small></strong>
                    <span class="kpi-hint">研发单元与集成自测</span>
                  </div>
                  <div class="kpi-card">
                    <span class="kpi-label">拆解功能规模</span>
                    <strong class="kpi-value">{{ estimatePageCount }} <small>页面</small> / {{ estimateApiCount }} <small>接口</small></strong>
                    <span class="kpi-hint">{{ estimateThirdPartyCount > 0 ? `含 ${estimateThirdPartyCount} 个第三方对接` : '颗粒度细化到项' }}</span>
                  </div>
                  <div v-if="estimateDailyRate && estimateDailyRate > 0" class="kpi-card price-highlight">
                    <span class="kpi-label">预估报价金额</span>
                    <strong class="kpi-value">¥{{ formatPrice(estimateTotalQuote) }}</strong>
                    <span class="kpi-hint">基准单价 ¥{{ estimateDailyRate }}/天</span>
                  </div>
                </div>

                <!-- 结果综述 -->
                <div class="estimate-summary-box">
                  <h4>评估概述与依据</h4>
                  <p class="summary-text">{{ String(result.summary || "") }}</p>
                </div>

                <!-- Excel 风格单 Sheet 交互表格 -->
                <div class="estimate-sheet-container">
                  <div class="estimate-sheet-toolbar">
                    <div class="sheet-title-info">
                      <span class="sheet-badge"><FileSpreadsheet :size="14" /> 单 Sheet 汇总表</span>
                      <strong>{{ result.title || "工作量评估与报价明细" }}</strong>
                      <span class="sheet-count">(共 {{ filteredEstimateItems.length }} 项)</span>
                    </div>
                    <div class="sheet-filter-group">
                      <input
                        v-model="estimateSearchKeyword"
                        type="text"
                        placeholder="搜索模块/页面/接口..."
                        class="sheet-search-input"
                      />
                      <select v-model="estimateTypeFilter" class="sheet-type-select">
                        <option value="ALL">全部类别</option>
                        <option value="PAGE">仅页面</option>
                        <option value="API">仅内部接口</option>
                        <option value="THIRD_PARTY_API">仅第三方接口</option>
                      </select>
                    </div>
                  </div>

                  <div class="excel-table-wrapper">
                    <table class="excel-grid-table">
                      <thead>
                        <tr>
                          <th
                            v-for="col in visibleEstimateColumns"
                            :key="col.key"
                            :class="['col-' + col.key, col.align]"
                            :style="{ width: col.width ? col.width + 'px' : 'auto' }"
                          >
                            {{ col.title }}
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        <template v-for="group in groupedEstimateItems" :key="group.module">
                          <!-- 模块小计汇总行 (Subtotal Banner) -->
                          <tr class="module-group-row">
                            <td :colspan="visibleEstimateColumns.length">
                              <div class="module-group-header">
                                <span class="group-name">📦 {{ group.module }}</span>
                                <span class="group-stats">
                                  包含 {{ group.pageCount }} 个页面，{{ group.apiCount }} 个接口
                                  <template v-if="group.thirdPartyCount > 0">（含 {{ group.thirdPartyCount }} 个第三方对接）</template>
                                  | 模块小计：开发 {{ group.devDays }} 天 + 自测 {{ group.testDays }} 天 = <strong>{{ group.totalDays }} 天</strong>
                                  <template v-if="estimateDailyRate && estimateDailyRate > 0">
                                    （¥{{ formatPrice(group.totalDays * estimateDailyRate) }}）
                                  </template>
                                </span>
                              </div>
                            </td>
                          </tr>
                          <!-- 模块明细数据行 -->
                          <tr
                            v-for="(item, iIdx) in group.items"
                            :key="group.module + '-' + iIdx"
                            class="data-row"
                          >
                            <td
                              v-for="col in visibleEstimateColumns"
                              :key="col.key"
                              :class="[col.align, 'cell-' + col.key]"
                            >
                              <template v-if="col.key === 'index'">{{ getRowIndex(item) }}</template>
                              <template v-else-if="col.key === 'module'">{{ item.module }}</template>
                              <template v-else-if="col.key === 'subModule'">{{ item.subModule || '-' }}</template>
                              <template v-else-if="col.key === 'type'">
                                <span class="type-pill" :class="typeTagClass(item.type)">
                                  {{ typeLabel(item.type) }}
                                </span>
                              </template>
                              <template v-else-if="col.key === 'name'">
                                <strong>{{ item.name }}</strong>
                              </template>
                              <template v-else-if="col.key === 'detail'">
                                <span class="cell-detail-text">{{ item.detail || '-' }}</span>
                              </template>
                              <template v-else-if="col.key === 'complexity'">
                                <span class="complexity-pill" :class="complexityTagClass(item.complexity)">
                                  {{ complexityLabel(item.complexity) }}
                                </span>
                              </template>
                              <template v-else-if="col.key === 'devDays'">{{ item.devDays?.toFixed(1) }}</template>
                              <template v-else-if="col.key === 'testDays'">{{ item.testDays?.toFixed(1) }}</template>
                              <template v-else-if="col.key === 'totalDays'">
                                <strong>{{ item.totalDays?.toFixed(1) }}</strong>
                              </template>
                              <template v-else-if="col.key === 'unitPrice'">
                                ¥{{ estimateDailyRate || 0 }}
                              </template>
                              <template v-else-if="col.key === 'totalPrice'">
                                <strong>¥{{ formatPrice((item.totalDays || 0) * (estimateDailyRate || 0)) }}</strong>
                              </template>
                              <template v-else-if="col.key === 'description'">
                                <span class="cell-desc-text">{{ item.description || '-' }}</span>
                              </template>
                              <template v-else>
                                <!-- 自定义扩展列支持行内输入/展示 -->
                                <input
                                  v-if="col.isCustom"
                                  v-model="customFieldStore[item.name + '_' + col.key]"
                                  class="custom-cell-input"
                                  placeholder="录入内容"
                                />
                                <span v-else>{{ getExtraValue(item, col.key) }}</span>
                              </template>
                            </td>
                          </tr>
                        </template>

                        <!-- 表格底部总计行 (Grand Total Row) -->
                        <tr class="grand-total-row">
                          <td
                            v-for="col in visibleEstimateColumns"
                            :key="'total-' + col.key"
                            :class="[col.align, 'cell-total-' + col.key]"
                          >
                            <template v-if="col.key === 'index' || col.key === 'module'">
                              【全项目总计】
                            </template>
                            <template v-else-if="col.key === 'devDays'">
                              {{ estimateDevDays }} 天
                            </template>
                            <template v-else-if="col.key === 'testDays'">
                              {{ estimateTestDays }} 天
                            </template>
                            <template v-else-if="col.key === 'totalDays'">
                              <strong>{{ estimateTotalDays }} 天</strong>
                            </template>
                            <template v-else-if="col.key === 'totalPrice'">
                              <strong>¥{{ formatPrice(estimateTotalQuote) }}</strong>
                            </template>
                            <template v-else-if="col.key === 'description'">
                              =SUM(开发人天 + 自测人天)
                            </template>
                            <template v-else></template>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <div class="estimate-footer-actions">
                  <button class="secondary-button" type="button" :disabled="busy" @click="startRun()">
                    <RotateCcw :size="14" />重新评估
                  </button>
                  <button
                    v-if="run && !run.accepted"
                    class="secondary-button confirm-button"
                    type="button"
                    :disabled="busy"
                    @click="confirmCurrent"
                  >
                    <Check :size="14" />采纳该报价评估
                  </button>
                </div>

                <!-- 表头定制抽屉 (侧滑抽屉) -->
                <div
                  v-if="estimateColumnsDrawerOpen"
                  class="drawer-backdrop"
                  @click.self="estimateColumnsDrawerOpen = false"
                >
                  <section class="drawer-panel" role="dialog" aria-modal="true" aria-label="定制 Excel 表头与列">
                    <header class="drawer-header">
                      <h3>定制 Excel 表头与列</h3>
                      <button
                        type="button"
                        class="icon-button"
                        title="关闭"
                        aria-label="关闭抽屉"
                        @click="estimateColumnsDrawerOpen = false"
                      >
                        <X :size="16" />
                      </button>
                    </header>
                    <div class="column-customizer-content">
                      <p class="customizer-tip">
                        勾选需要在 Excel 预览与导出中显示的字段，也可添加自定义列（如负责人、备注等）。
                      </p>
                      <div class="column-checkbox-list">
                        <div
                          v-for="col in estimateColumns"
                          :key="col.key"
                          class="column-checkbox-item"
                        >
                          <label class="col-item-label">
                            <input
                              v-model="col.visible"
                              type="checkbox"
                              :disabled="col.key === 'module' || col.key === 'totalDays'"
                            />
                            <span>{{ col.title }}</span>
                          </label>
                          <button
                            v-if="col.isCustom"
                            type="button"
                            class="col-remove-btn"
                            @click="removeCustomColumn(col.key)"
                          >
                            删除
                          </button>
                        </div>
                      </div>

                      <div class="add-custom-column-section">
                        <h4>添加自定义扩展列</h4>
                        <div class="custom-col-form">
                          <input
                            v-model="newCustomColTitle"
                            type="text"
                            placeholder="例如: 责任工程师 / 备注"
                            maxlength="20"
                          />
                          <button
                            type="button"
                            class="secondary-button compact"
                            :disabled="!newCustomColTitle.trim()"
                            @click="addCustomColumn"
                          >
                            + 添加列
                          </button>
                        </div>
                      </div>
                    </div>
                  </section>
                </div>
              </div>
              <div v-else-if="showResult && result && mode === 'TEST_CASE'" class="generate-result test-case-result">
                <!-- 顶部测试范围与策略说明卡片 -->
                <div class="test-case-scope-alert" role="alert">
                  <div class="alert-icon"><AlertCircle :size="18" /></div>
                  <div class="alert-body">
                    <strong>测试覆盖策略与范围说明</strong>
                    <p>{{ testCaseScopeNote }}</p>
                  </div>
                </div>

                <!-- KPI 指标卡片区域 -->
                <div class="test-case-kpi-grid">
                  <div class="kpi-card highlight">
                    <span class="kpi-label">测试用例总数</span>
                    <strong class="kpi-value">{{ testCaseTotalCount }} <small>条</small></strong>
                    <span class="kpi-hint">覆盖全部核心模块</span>
                  </div>
                  <div class="kpi-card p0-card">
                    <span class="kpi-label">P0 核心冒烟用例</span>
                    <strong class="kpi-value">{{ testCaseP0Count }} <small>条</small></strong>
                    <span class="kpi-hint">提测准入阻断项</span>
                  </div>
                  <div class="kpi-card">
                    <span class="kpi-label">P1 关键功能用例</span>
                    <strong class="kpi-value">{{ testCaseP1Count }} <small>条</small></strong>
                    <span class="kpi-hint">核心业务逻辑验证</span>
                  </div>
                  <div class="kpi-card">
                    <span class="kpi-label">P2 次要场景用例</span>
                    <strong class="kpi-value">{{ testCaseP2Count }} <small>条</small></strong>
                    <span class="kpi-hint">边缘与UI展示</span>
                  </div>
                  <div class="kpi-card">
                    <span class="kpi-label">边界值极限测试</span>
                    <strong class="kpi-value">{{ testCaseBoundaryCount }} <small>条</small></strong>
                    <span class="kpi-hint">极限数值与字段极值</span>
                  </div>
                  <div class="kpi-card">
                    <span class="kpi-label">逆向异常流拦截</span>
                    <strong class="kpi-value">{{ testCaseExceptionCount }} <small>条</small></strong>
                    <span class="kpi-hint">校验拦截与容错兜底</span>
                  </div>
                </div>

                <!-- 结果综述 -->
                <div class="estimate-summary-box">
                  <h4>测试用例集概述与测试策略</h4>
                  <p class="summary-text">{{ String(result.summary || "") }}</p>
                </div>

                <!-- 测试用例 Excel 风格单 Sheet 表格 -->
                <div class="estimate-sheet-container">
                  <div class="estimate-sheet-toolbar">
                    <div class="sheet-title-info">
                      <span class="sheet-badge"><FileSpreadsheet :size="14" /> 测试用例清单</span>
                      <strong>{{ result.title || "系统测试用例集" }}</strong>
                      <span class="sheet-count">(共 {{ filteredTestCaseItems.length }} 条)</span>
                    </div>
                    <div class="sheet-filter-group">
                      <input
                        v-model="testCaseSearchKeyword"
                        type="text"
                        placeholder="搜索模块/标题/步骤/预期结果..."
                        class="sheet-search-input"
                      />
                      <select v-model="testCaseModuleFilter" class="sheet-type-select">
                        <option value="ALL">全部模块</option>
                        <option v-for="m in testCaseModuleOptions" :key="m" :value="m">{{ m }}</option>
                      </select>
                      <select v-model="testCasePriorityFilter" class="sheet-type-select">
                        <option value="ALL">全部等级</option>
                        <option value="P0">仅 P0 冒烟</option>
                        <option value="P1">仅 P1 关键</option>
                        <option value="P2">仅 P2 次要</option>
                      </select>
                      <select v-model="testCaseTypeFilter" class="sheet-type-select">
                        <option value="ALL">全部类型</option>
                        <option value="FUNCTION">功能测试</option>
                        <option value="BOUNDARY">边界值</option>
                        <option value="EXCEPTION">异常流</option>
                        <option value="PERMISSION">权限安全</option>
                        <option value="CONCURRENCY">并发测试</option>
                      </select>
                    </div>
                  </div>

                  <div class="excel-table-wrapper">
                    <table class="excel-grid-table">
                      <thead>
                        <tr>
                          <th
                            v-for="col in visibleTestCaseColumns"
                            :key="col.key"
                            :class="['col-' + col.key, col.align]"
                            :style="{ width: col.width ? col.width + 'px' : 'auto' }"
                          >
                            {{ col.title }}
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        <template v-for="group in groupedTestCaseItems" :key="group.module">
                          <!-- 模块小计汇总行 (Subtotal Banner) -->
                          <tr class="module-group-row">
                            <td :colspan="visibleTestCaseColumns.length">
                              <div class="module-group-header">
                                <span class="group-name">📦 {{ group.module }}</span>
                                <span class="group-stats">
                                  共 {{ group.totalCount }} 条用例（P0: {{ group.p0Count }} 条，P1: {{ group.p1Count }} 条，P2: {{ group.p2Count }} 条
                                  <template v-if="group.boundaryCount > 0">，边界值: {{ group.boundaryCount }}</template>
                                  <template v-if="group.exceptionCount > 0">，异常流: {{ group.exceptionCount }}</template>）
                                </span>
                              </div>
                            </td>
                          </tr>
                          <!-- 用例明细数据行 -->
                          <tr
                            v-for="(item, tIdx) in group.items"
                            :key="group.module + '-' + tIdx"
                            class="data-row"
                          >
                            <td
                              v-for="col in visibleTestCaseColumns"
                              :key="col.key"
                              :class="[col.align, 'cell-' + col.key]"
                            >
                              <template v-if="col.key === 'index'">{{ getTestCaseRowIndex(item) }}</template>
                              <template v-else-if="col.key === 'caseId'">
                                <span class="case-id-badge">{{ item.caseId }}</span>
                              </template>
                              <template v-else-if="col.key === 'module'">{{ item.module }}</template>
                              <template v-else-if="col.key === 'subModule'">{{ item.subModule || '-' }}</template>
                              <template v-else-if="col.key === 'title'">
                                <strong>{{ item.title }}</strong>
                              </template>
                              <template v-else-if="col.key === 'type'">
                                <span :class="testCaseTypeTagClass(item.type)">
                                  {{ testCaseTypeLabel(item.type) }}
                                </span>
                              </template>
                              <template v-else-if="col.key === 'priority'">
                                <span :class="testCasePriorityTagClass(item.priority)">
                                  {{ testCasePriorityLabel(item.priority) }}
                                </span>
                              </template>
                              <template v-else-if="col.key === 'precondition'">
                                <span>{{ item.precondition || '-' }}</span>
                              </template>
                              <template v-else-if="col.key === 'steps'">
                                <div class="cell-steps-text">{{ item.steps || '-' }}</div>
                              </template>
                              <template v-else-if="col.key === 'testData'">
                                <span>{{ item.testData || '-' }}</span>
                              </template>
                              <template v-else-if="col.key === 'expectedResult'">
                                <div class="cell-expected-text">{{ item.expectedResult || '-' }}</div>
                              </template>
                              <template v-else-if="col.key === 'description'">
                                <span class="cell-desc-text">{{ item.description || '-' }}</span>
                              </template>
                              <template v-else>
                                <input
                                  v-if="col.isCustom"
                                  v-model="customTestCaseFieldStore[item.caseId + '_' + col.key]"
                                  class="custom-cell-input"
                                  placeholder="录入内容"
                                />
                                <span v-else>{{ getTestCaseExtraValue(item, col.key) }}</span>
                              </template>
                            </td>
                          </tr>
                        </template>

                        <!-- 表格底部总计行 (Grand Total Row) -->
                        <tr class="grand-total-row">
                          <td
                            v-for="col in visibleTestCaseColumns"
                            :key="'total-' + col.key"
                            :class="[col.align, 'cell-total-' + col.key]"
                          >
                            <template v-if="col.key === 'index' || col.key === 'caseId' || col.key === 'module'">
                              【全用例总计】
                            </template>
                            <template v-else-if="col.key === 'title'">
                              共计 <strong>{{ testCaseTotalCount }}</strong> 条测试用例
                            </template>
                            <template v-else-if="col.key === 'priority'">
                              P0:{{ testCaseP0Count }} / P1:{{ testCaseP1Count }} / P2:{{ testCaseP2Count }}
                            </template>
                            <template v-else></template>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <div class="estimate-footer-actions">
                  <button class="secondary-button" type="button" :disabled="busy" @click="startRun()">
                    <RotateCcw :size="14" />重新生成用例
                  </button>
                  <button
                    v-if="run && !run.accepted"
                    class="secondary-button confirm-button"
                    type="button"
                    :disabled="busy"
                    @click="confirmCurrent"
                  >
                    <Check :size="14" />采纳该测试用例集
                  </button>
                </div>

                <!-- 测试用列表头定制抽屉 (侧滑抽屉) -->
                <div
                  v-if="testCaseColumnsDrawerOpen"
                  class="drawer-backdrop"
                  @click.self="testCaseColumnsDrawerOpen = false"
                >
                  <section class="drawer-panel" role="dialog" aria-modal="true" aria-label="定制测试用例表头与列">
                    <header class="drawer-header">
                      <h3>定制用例表头与显示列</h3>
                      <button
                        type="button"
                        class="icon-button"
                        title="关闭"
                        aria-label="关闭抽屉"
                        @click="testCaseColumnsDrawerOpen = false"
                      >
                        <X :size="16" />
                      </button>
                    </header>
                    <div class="drawer-body">
                      <p class="drawer-desc">勾选控制列表可见列与导出列，支持动态添加自定义扩展列（如执行人、测试结果等）。</p>

                      <div class="column-toggle-list">
                        <div
                          v-for="col in testCaseColumns"
                          :key="col.key"
                          class="column-toggle-item"
                        >
                          <label class="column-checkbox-label">
                            <input
                              v-model="col.visible"
                              type="checkbox"
                              :disabled="col.key === 'title' || col.key === 'caseId'"
                            />
                            <span class="col-title">{{ col.title }}</span>
                            <span v-if="col.isCustom" class="custom-badge">自定义</span>
                          </label>
                          <button
                            v-if="col.isCustom"
                            type="button"
                            class="icon-button delete-col-btn"
                            title="删除该列"
                            @click="removeCustomTestCaseColumn(col.key)"
                          >
                            <Trash2 :size="13" />
                          </button>
                        </div>
                      </div>

                      <div class="add-custom-column-section">
                        <h4>添加自定义扩展列</h4>
                        <div class="custom-col-form">
                          <input
                            v-model="newCustomTestCaseColTitle"
                            type="text"
                            placeholder="例如: 执行人 / 测试结果 / Bug编号"
                            maxlength="20"
                          />
                          <button
                            type="button"
                            class="secondary-button compact"
                            :disabled="!newCustomTestCaseColTitle.trim()"
                            @click="addCustomTestCaseColumn"
                          >
                            + 添加列
                          </button>
                        </div>
                      </div>
                    </div>
                  </section>
                </div>
              </div>
              <!-- 研发技术方案 (TECH_SPEC) 结果视图 -->
              <div v-else-if="showResult && result && mode === 'TECH_SPEC'" class="generate-result tech-spec-result">
                <!-- 顶部技术方案 KPI 指标卡片 -->
                <div class="tech-spec-kpi-grid">
                  <div class="kpi-card highlight">
                    <span class="kpi-label">分层架构</span>
                    <strong class="kpi-value">{{ techSpecHasArchitecture ? "已就绪" : "待生成" }}</strong>
                    <span class="kpi-hint">选型、分层与流转</span>
                  </div>
                  <div class="kpi-card">
                    <span class="kpi-label">数据库表结构</span>
                    <strong class="kpi-value">{{ techSpecDatabaseTables.length }} <small>张</small></strong>
                    <span class="kpi-hint">含字段与索引</span>
                  </div>
                  <div class="kpi-card">
                    <span class="kpi-label">核心 API 契约</span>
                    <strong class="kpi-value">{{ techSpecApiEndpoints.length }} <small>个</small></strong>
                    <span class="kpi-hint">请求/响应与错误码</span>
                  </div>
                  <div class="kpi-card">
                    <span class="kpi-label">分模块补充</span>
                    <strong class="kpi-value">{{ techSpecModuleDocCount }} <small>篇</small></strong>
                    <span class="kpi-hint">modules/*.md</span>
                  </div>
                </div>

                <!-- 技术方案子导航：架构 / 数据模型 / API（历史工单仅在有数据时显示） -->
                <div class="tech-spec-subnav" role="tablist" aria-label="技术方案子视图">
                  <button
                    type="button"
                    class="tech-subnav-btn"
                    :class="{ active: techSpecActiveTab === 'ARCH' }"
                    @click="techSpecActiveTab = 'ARCH'"
                  >
                    <Layers :size="14" />
                    <span>分层架构与设计</span>
                  </button>
                  <button
                    type="button"
                    class="tech-subnav-btn"
                    :class="{ active: techSpecActiveTab === 'DB' }"
                    @click="techSpecActiveTab = 'DB'"
                  >
                    <Database :size="14" />
                    <span>数据库模型与表结构 ({{ techSpecDatabaseTables.length }})</span>
                  </button>
                  <button
                    type="button"
                    class="tech-subnav-btn"
                    :class="{ active: techSpecActiveTab === 'API' }"
                    @click="techSpecActiveTab = 'API'"
                  >
                    <Code :size="14" />
                    <span>核心 API 契约设计 ({{ techSpecApiEndpoints.length }})</span>
                  </button>
                  <button
                    v-if="techSpecTotalTasks > 0"
                    type="button"
                    class="tech-subnav-btn"
                    :class="{ active: techSpecActiveTab === 'TASKS' }"
                    @click="techSpecActiveTab = 'TASKS'"
                  >
                    <Kanban :size="14" />
                    <span>历史工单 ({{ techSpecTotalTasks }})</span>
                  </button>
                </div>

                <!-- Tab 1: 研发工单看板与多维筛选 -->
                <section v-if="techSpecActiveTab === 'TASKS'" class="tech-tab-content tech-tasks-section">
                  <div class="tech-tasks-toolbar">
                    <div class="sheet-title-info">
                      <span class="sheet-badge"><Kanban :size="14" /> 历史结果</span>
                      <strong>历史工单清单</strong>
                      <span class="sheet-count">(共 {{ filteredTechTasks.length }} 项)</span>
                    </div>
                    <div class="sheet-filter-group">
                      <input
                        v-model="techSpecSearchKeyword"
                        type="text"
                        placeholder="搜索工单编号/名称/模块/说明..."
                        class="sheet-search-input"
                      />
                      <select v-model="techSpecModuleFilter" class="sheet-type-select">
                        <option value="ALL">全部模块</option>
                        <option v-for="m in techSpecModuleOptions" :key="m" :value="m">{{ m }}</option>
                      </select>
                      <select v-model="techSpecTypeFilter" class="sheet-type-select">
                        <option value="ALL">全部端别</option>
                        <option value="FRONTEND">前端开发</option>
                        <option value="BACKEND">后端开发</option>
                        <option value="MIDDLEWARE">中间件/DB</option>
                      </select>
                      <select v-model="techSpecPriorityFilter" class="sheet-type-select">
                        <option value="ALL">全部优先级</option>
                        <option value="P0">P0 紧急核心</option>
                        <option value="P1">P1 关键业务</option>
                        <option value="P2">P2 次要支持</option>
                      </select>
                    </div>
                  </div>

                  <div class="task-kanban-grid">
                    <article
                      v-for="task in filteredTechTasks"
                      :key="task.taskId"
                      class="tech-task-card"
                    >
                      <header class="tech-task-header">
                        <span class="tech-task-id">{{ task.taskId }}</span>
                        <span class="tech-task-type" :class="techSpecTypeBadgeClass(task.type)">
                          {{ techSpecTypeLabel(task.type) }}
                        </span>
                        <span class="tech-task-priority" :class="techSpecPriorityBadgeClass(task.priority)">
                          {{ task.priority }}
                        </span>
                        <span class="tech-task-module">{{ task.module }}</span>
                      </header>
                      <h4 class="tech-task-name">{{ task.name }}</h4>
                      <p class="tech-task-desc">{{ task.description }}</p>
                      <div v-if="task.dependencies && task.dependencies.length" class="tech-task-deps">
                        <span>前置依赖:</span>
                        <code>{{ task.dependencies.join(', ') }}</code>
                      </div>
                      <footer class="tech-task-footer">
                        <span class="tech-task-days">
                          <Clock :size="13" /> <strong>{{ task.devDays }}</strong> 人天 (约 {{ (task.devDays * 8).toFixed(0) }}h)
                        </span>
                      </footer>
                    </article>
                  </div>
                </section>

                <!-- Tab 2: 系统分层架构与推演设计 (含 Mermaid 原生渲染) -->
                <section v-else-if="techSpecActiveTab === 'ARCH'" class="tech-tab-content tech-arch-section">
                  <div class="arch-pattern-banner">
                    <Layers :size="16" />
                    <strong>系统架构蓝图与分层推演</strong>
                    <span>已与 Mermaid 引擎原生联动渲染</span>
                  </div>
                  <div class="markdown arch-markdown-box" v-html="techSpecArchitectureHtml"></div>
                </section>

                <!-- Tab 3: 数据库模型与表结构设计 -->
                <section v-else-if="techSpecActiveTab === 'DB'" class="tech-tab-content tech-db-section">
                  <div v-if="!techSpecDatabaseTables.length" class="empty-sub-content">
                    暂无结构化数据库表设计
                  </div>
                  <div v-else class="db-tables-list">
                    <article v-for="table in techSpecDatabaseTables" :key="table.tableName" class="db-table-card">
                      <header class="db-table-header">
                        <div class="table-name-wrap">
                          <Database :size="16" />
                          <strong class="table-name">{{ table.tableName }}</strong>
                          <span class="table-comment">{{ table.tableComment ? `(${table.tableComment})` : '' }}</span>
                        </div>
                      </header>
                      <div class="db-table-scroll">
                        <table class="db-columns-table">
                          <thead>
                            <tr>
                              <th style="width: 25%">字段名称</th>
                              <th style="width: 20%">数据类型</th>
                              <th style="width: 15%">是否可空</th>
                              <th style="width: 40%">字段说明与业务语义</th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr v-for="col in table.columns" :key="col.name">
                              <td><code>{{ col.name }}</code></td>
                              <td><span class="db-type-badge">{{ col.type }}</span></td>
                              <td>
                                <span :class="col.nullable ? 'db-null-tag' : 'db-notnull-tag'">
                                  {{ col.nullable ? 'NULL' : 'NOT NULL' }}
                                </span>
                              </td>
                              <td>{{ col.comment || '-' }}</td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                      <footer v-if="table.indexes && table.indexes.length" class="db-table-footer">
                        <strong>索引定义:</strong>
                        <code>{{ formatTechnicalDetail(table.indexes) }}</code>
                      </footer>
                    </article>
                  </div>
                </section>

                <!-- Tab 4: 核心 API 契约规约 -->
                <section v-else-if="techSpecActiveTab === 'API'" class="tech-tab-content tech-api-section">
                  <div v-if="!techSpecApiEndpoints.length" class="empty-sub-content">
                    暂无核心 API 契约设计
                  </div>
                  <div v-else class="api-endpoints-list">
                    <article v-for="(api, aIdx) in techSpecApiEndpoints" :key="aIdx" class="api-endpoint-card">
                      <header class="api-card-header">
                        <span class="http-method-badge" :class="'method-' + api.method.toLowerCase()">
                          {{ api.method }}
                        </span>
                        <code class="api-path">{{ api.path }}</code>
                        <strong class="api-summary">{{ api.summary }}</strong>
                      </header>
                      <div class="api-card-body">
                        <div v-if="api.requestBody" class="api-snippet-block">
                          <h5>Request Payload</h5>
                          <pre class="api-json-pre"><code>{{ formatJsonSnippet(api.requestBody) }}</code></pre>
                        </div>
                        <div v-if="api.responseBody" class="api-snippet-block">
                          <h5>Response Payload</h5>
                          <pre class="api-json-pre"><code>{{ formatJsonSnippet(api.responseBody) }}</code></pre>
                        </div>
                        <div v-if="api.errorCodes && api.errorCodes.length" class="api-errors-wrap">
                          <span class="sub-label">错误码定义:</span>
                          <code>{{ formatTechnicalDetail(api.errorCodes) }}</code>
                        </div>
                      </div>
                    </article>
                  </div>
                </section>

                <!-- 底部操作栏 -->
                <div class="estimate-footer-actions">
                  <button class="secondary-button" type="button" :disabled="busy" @click="startRun()">
                    <RotateCcw :size="14" />重新生成技术方案
                  </button>
                  <button
                    v-if="run && !run.accepted"
                    class="secondary-button confirm-button"
                    type="button"
                    :disabled="busy"
                    @click="confirmCurrent"
                  >
                    <Check :size="14" />采纳该技术方案
                  </button>
                </div>
              </div>
              <div v-else-if="mode === 'TEST_CASE' && hasActivePrd" class="review-prd-baseline">
                <div class="review-baseline-banner">
                  <div class="review-baseline-info">
                    <span class="baseline-tag"><CheckSquare :size="13" /> 测试用例底稿已就绪</span>
                    <h3>{{ activePrdTitle }}</h3>
                    <p>Hermes 将以该 PRD 的功能规格、边界条件与业务流转规则为基准，全覆盖梳理功能正向流、边界值、逆向异常与权限测试用例集。</p>
                  </div>
                  <div class="review-baseline-actions">
                    <button v-if="availablePrdRuns.length > 1" class="secondary-button compact-ghost" type="button" :disabled="busy" @click="openPrdPicker">
                      更换 PRD
                    </button>
                    <button class="run-button compact" type="button" :disabled="busy" @click="startRun()">
                      <Play :size="14" />
                      开始生成用例
                    </button>
                  </div>
                </div>
                <div v-if="activePrdMarkdownHtml" class="review-baseline-preview markdown" v-html="activePrdMarkdownHtml"></div>
              </div>
              <div v-else-if="mode === 'TECH_SPEC' && hasActivePrd" class="review-prd-baseline">
                <div class="review-baseline-banner">
                  <div class="review-baseline-info">
                    <span class="baseline-tag"><Layers :size="13" /> 技术方案底稿已就绪</span>
                    <h3>{{ activePrdTitle }}</h3>
                    <p>Hermes 将基于该 PRD 的功能规格、数据流与验收准则，自动推演系统架构设计、数据表结构、API 契约及分模块设计补充，形成可交付前后端的详细技术方案。</p>
                  </div>
                  <div class="review-baseline-actions">
                    <button v-if="availablePrdRuns.length > 1" class="secondary-button compact-ghost" type="button" :disabled="busy" @click="openPrdPicker">
                      更换 PRD
                    </button>
                    <button class="run-button compact" type="button" :disabled="busy" @click="startRun()">
                      <Play :size="14" />
                      开始生成技术方案
                    </button>
                  </div>
                </div>
                <div v-if="activePrdMarkdownHtml" class="review-baseline-preview markdown" v-html="activePrdMarkdownHtml"></div>
              </div>
              <div v-else-if="mode === 'REVIEW' && hasActivePrd" class="review-prd-baseline">
                <div class="review-baseline-banner">
                  <div class="review-baseline-info">
                    <span class="baseline-tag"><FileText :size="13" /> 评审底稿已就绪</span>
                    <h3>{{ activePrdTitle }}</h3>
                    <p>Hermes 将基于该 PRD 逐项评审业务规则、数据边界与验收标准。你可以直接在上方点击「开始评审」，也可以在此提前核阅底稿内容。</p>
                  </div>
                  <div class="review-baseline-actions">
                    <button v-if="availablePrdRuns.length > 1" class="secondary-button compact-ghost" type="button" :disabled="busy" @click="openPrdPicker">
                      更换 PRD
                    </button>
                    <button class="run-button compact" type="button" :disabled="busy" @click="startRun()">
                      <Play :size="14" />
                      开始评审
                    </button>
                  </div>
                </div>
                <div v-if="activePrdMarkdownHtml" class="review-baseline-preview markdown" v-html="activePrdMarkdownHtml"></div>
              </div>
              <div v-else-if="(mode === 'DEMO' || mode === 'POC') && hasActivePrd" class="demo-prd-baseline">
                <div class="review-baseline-banner">
                  <div class="review-baseline-info">
                    <span class="baseline-tag"><Sparkles :size="13" /> {{ mode === 'POC' ? 'POC 演示底稿已就绪' : '原型生成底稿已就绪' }}</span>
                    <h3>{{ activePrdTitle }}</h3>
                    <p v-if="mode === 'POC'">将基于当前 PRD 生成静态多文件 POC 演示系统，耗时取决于模型和材料规模。预览在工作台沙箱中进行，源码可打包 ZIP。</p>
                    <p v-else>将基于当前 PRD 生成高保真交互原型 Demo。可在上方选择通用 HTML 或业务领域预设，点击「开始生成 Demo」。</p>
                  </div>
                  <div class="review-baseline-actions">
                    <button v-if="availablePrdRuns.length > 1" class="secondary-button compact-ghost" type="button" :disabled="busy" @click="openPrdPicker">
                      更换 PRD
                    </button>
                    <button class="run-button compact" type="button" :disabled="busy" @click="startRun()">
                      <Play :size="14" />
                      {{ mode === 'POC' ? '开始生成 POC' : '开始生成 Demo' }}
                    </button>
                  </div>
                </div>
                <div v-if="activePrdMarkdownHtml" class="review-baseline-preview markdown" v-html="activePrdMarkdownHtml"></div>
              </div>
              <div v-else-if="(mode === 'REVIEW' || mode === 'DEMO' || mode === 'POC') && !hasActivePrd && visibleSourceList.length" class="review-prd-baseline uploaded-prd-baseline">
                <div class="review-baseline-banner">
                  <div class="review-baseline-info">
                    <span class="baseline-tag"><FileText :size="13" /> 已上传 PRD 材料（{{ visibleSourceList.length }} 份）</span>
                    <h3>{{ visibleSourceList.map((s) => displayName(s.name)).join("、") }}</h3>
                    <p v-if="mode === 'REVIEW'">Hermes 将直接基于你上传的已有 PRD 材料进行专业评审，检查业务逻辑、边界条件与阻断性问题。无需先生成 PRD，可直接点击「开始评审」。</p>
                    <p v-else>Hermes 将直接基于你上传的已有 PRD 材料生成通用中后台 风格高保真交互 Demo 原型。无需先生成 PRD，可选择风格后直接点击「开始生成 Demo」。</p>
                  </div>
                  <div class="review-baseline-actions">
                    <button class="secondary-button compact-ghost" type="button" :disabled="busy" @click="openUpload">
                      补充材料
                    </button>
                    <button class="run-button compact" type="button" :disabled="busy" @click="startRun()">
                      <Play :size="14" />
                      {{ mode === "POC" ? "开始生成 POC" : mode === "DEMO" ? "开始生成 Demo" : "开始评审" }}
                    </button>
                  </div>
                </div>
              </div>
              <div v-else class="empty-guide">
                <p>{{ emptyHint }}</p>
                <div class="empty-actions">
                  <button v-if="mode === 'GENERATE' && !visibleSourceList.length" class="run-button" type="button" :disabled="busy" @click="openUpload">
                    添加材料
                  </button>
                  <button v-else-if="mode === 'GENERATE'" class="run-button" type="button" :disabled="busy" @click="startRun()">开始生成</button>
                  <button v-else-if="mode === 'REVIEW' && !visibleSourceList.length" class="run-button" type="button" :disabled="busy" @click="openUpload">
                    添加材料
                  </button>
                  <button v-else-if="mode === 'REVIEW'" class="run-button" type="button" :disabled="busy" @click="startRun()">
                    开始评审
                  </button>
                  <button v-else-if="mode === 'DEMO' && !visibleSourceList.length" class="run-button" type="button" :disabled="busy" @click="openUpload">
                    添加材料
                  </button>
                  <button v-else-if="mode === 'DEMO'" class="run-button" type="button" :disabled="busy" @click="startRun()">
                    开始生成 Demo
                  </button>
                  <button v-else-if="mode === 'POC' && !visibleSourceList.length" class="run-button" type="button" :disabled="busy" @click="openUpload">
                    添加材料
                  </button>
                  <button v-else-if="mode === 'POC'" class="run-button" type="button" :disabled="busy" @click="startRun()">
                    开始生成 POC
                  </button>
                  <button v-else-if="mode === 'ESTIMATE' && !visibleSourceList.length" class="run-button" type="button" :disabled="busy" @click="openUpload">
                    添加材料
                  </button>
                  <button v-else-if="mode === 'ESTIMATE'" class="run-button" type="button" :disabled="busy" @click="startRun()">
                    开始工作量评估
                  </button>
                  <button v-else-if="mode === 'TEST_CASE' && !visibleSourceList.length" class="run-button" type="button" :disabled="busy" @click="openUpload">
                    添加材料
                  </button>
                  <button v-else-if="mode === 'TEST_CASE'" class="run-button" type="button" :disabled="busy" @click="startRun()">
                    开始生成用例
                  </button>
                  <button v-else-if="mode === 'TECH_SPEC' && !visibleSourceList.length" class="run-button" type="button" :disabled="busy" @click="openUpload">
                    添加材料
                  </button>
                  <button v-else-if="mode === 'TECH_SPEC'" class="run-button" type="button" :disabled="busy" @click="startRun()">
                    开始生成技术方案
                  </button>
                </div>
              </div>
            </div>
          </article>

          <button
            v-if="contextOpen"
            type="button"
            class="split-handle col"
            aria-orientation="vertical"
            aria-label="拖动调整右侧栏宽度"
            @pointerdown.prevent="startDragContext"
          ></button>
          <aside v-if="contextOpen" class="context-panel open">
            <header class="context-header">
              <h3>证据详情</h3>
              <button class="icon-button" type="button" title="关闭证据" aria-label="关闭证据" @click="closeContext"><X :size="16" /></button>
            </header>
            <div class="context-body">
              <h4>{{ contextTitle }}</h4>
              <p v-if="contextBodyVisible">{{ contextBody }}</p>
              <p v-if="showMissingQuote" class="hint">此判断综合材料得出，未附具体引用。</p>
              <label class="focus-input">
                <span>下一轮要求</span>
                <input v-model="followUp" :disabled="busy" placeholder="下次运行时一并提交" />
              </label>
              <span v-if="followUp.trim()" class="next-run-status"><Check :size="13" />已加入下一次运行</span>
            </div>
          </aside>
        </section>
      </div>
    </main>

    <div v-if="newTaskOpen" class="modal-backdrop" @click.self="closeNewTask">
      <section class="modal" role="dialog" aria-modal="true" aria-labelledby="new-task-title">
        <header class="modal-header">
          <div><span class="eyebrow">我的需求</span><h2 id="new-task-title">新建需求</h2></div>
          <button class="icon-button" type="button" title="关闭" aria-label="关闭" @click="closeNewTask"><X :size="16" /></button>
        </header>
        <label class="modal-field">
          <span>需求名称</span>
          <input
            ref="newTaskInput"
            v-model="newTaskTitle"
            class="setup-input"
            maxlength="200"
            placeholder="例如：智慧园区能耗看板"
            @keydown.enter.prevent="confirmNewTask"
          />
        </label>
        <p v-if="newTaskError" class="hint">{{ newTaskError }}</p>
        <footer class="modal-actions">
          <button class="secondary-button" type="button" @click="closeNewTask">取消</button>
          <button class="run-button compact" type="button" :disabled="creatingTask" @click="confirmNewTask">
            {{ creatingTask ? "创建中" : "创建" }}
          </button>
        </footer>
      </section>
    </div>

    <div v-if="uploadOpen" class="modal-backdrop" @click.self="closeUpload">
      <section class="modal" role="dialog" aria-modal="true" aria-labelledby="upload-title">
        <header class="modal-header">
          <div><span class="eyebrow">任务材料</span><h2 id="upload-title">添加材料</h2></div>
          <button class="icon-button" type="button" title="关闭" aria-label="关闭" @click="closeUpload"><X :size="16" /></button>
        </header>
        <label class="drop-zone">
          <strong>选择文件</strong>
          <span>当前支持 MD、TXT、DOCX、XLSX</span>
          <input
            type="file"
            multiple
            accept=".md,.txt,.docx,.xlsx,text/plain,text/markdown,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            @change="onPickFiles"
          />
        </label>
        <label class="modal-field paste-material-field">
          <span>或粘贴文本</span>
          <textarea v-model="pastedMaterial" maxlength="200000" placeholder="粘贴会议纪要、聊天记录或业务规则"></textarea>
        </label>
        <div class="selected-files">
          <p v-if="!pendingFiles.length && !visibleSourceList.length" class="file-empty">尚未选择文件</p>
          <ul v-else class="file-list">
            <li v-for="file in pendingFiles" :key="`pending-${file.name}`" class="file-row pending">
              <span class="file-status">待添加</span>
              <span class="file-name">{{ file.name }}</span>
            </li>
            <li v-for="source in visibleSourceList" :key="source.id" class="file-row added source-file-row">
              <span class="source-role">{{ sourceRoleLabel(source.name) }}</span>
              <span class="file-name" :title="sourceTooltip(source)">{{ displayName(source.name) }}</span>
              <button
                v-if="!isDraftSource(source.name)"
                type="button"
                class="source-remove"
                :disabled="busy"
                @click="removeSource(source.id)"
              >
                删除
              </button>
              <span v-else class="source-auto">自动</span>
            </li>
          </ul>
        </div>
        <p v-if="pendingFiles.length" class="upload-hint">选择文件后还需点「添加」，成功后会关闭弹窗并提示。</p>
        <p v-if="uploadError" class="hint">{{ uploadError }}</p>
        <footer class="modal-actions">
          <button class="secondary-button" type="button" @click="closeUpload">取消</button>
          <button class="run-button compact" type="button" :disabled="(!pendingFiles.length && !pastedMaterial.trim()) || uploading" @click="confirmUpload">
            {{ uploading ? "添加中" : "添加" }}
          </button>
        </footer>
      </section>
    </div>

    <div v-if="runConfirmOpen" class="modal-backdrop" @click.self="closeRunConfirm">
      <section class="modal run-confirm-modal" role="dialog" aria-modal="true" aria-labelledby="run-confirm-title">
        <header class="modal-header">
          <div>
            <span class="eyebrow">运行前确认</span>
            <h2 id="run-confirm-title">{{ runConfirmTitle }}</h2>
          </div>
          <button class="icon-button" type="button" title="关闭" aria-label="关闭" @click="closeRunConfirm"><X :size="16" /></button>
        </header>
        <p class="run-confirm-lead">请核对本次将读取的材料，确认无误后再开始。</p>
        <ul class="run-confirm-list">
          <li v-for="source in visibleSourceList" :key="source.id">
            <span class="source-role">{{ sourceRoleLabel(source.name) }}</span>
            <span class="file-name" :title="sourceTooltip(source)">{{ displayName(source.name) }}</span>
          </li>
        </ul>
        <p v-if="generateInputsDirty && usesExistingPrd" class="hint warn">
          生成材料已更新但尚未重新生成；本次仍使用「{{ activePrdTitle }}」。
        </p>
        <p v-if="usesExistingPrd && pendingPrdCandidate" class="hint warn">
          有新生成稿待选择；本次确认仍读取「{{ activePrdTitle }}」，不会使用候选稿。
        </p>
        <p v-if="revising && mode === 'GENERATE'" class="hint">将基于当前稿修订，保留目录和结论方向，不保证逐句不改。</p>
        <footer class="modal-actions">
          <button class="secondary-button" type="button" :disabled="starting" @click="closeRunConfirm">返回修改</button>
          <button class="run-button compact" type="button" :disabled="starting" @click="confirmStartRun">
            {{ starting ? "启动中" : runConfirmAction }}
          </button>
        </footer>
      </section>
    </div>

    <div v-if="prdPickerOpen" class="modal-backdrop" @click.self="closePrdPicker">
      <section class="modal run-confirm-modal" role="dialog" aria-modal="true" aria-labelledby="prd-picker-title">
        <header class="modal-header">
          <div>
            <span class="eyebrow">选择 PRD</span>
            <h2 id="prd-picker-title">更换当前 PRD</h2>
          </div>
          <button class="icon-button" type="button" title="关闭" aria-label="关闭" @click="closePrdPicker"><X :size="16" /></button>
        </header>
        <p class="run-confirm-lead">评审和 Demo 将读取所选 PRD，不影响生成页原始材料。</p>
        <ul class="run-confirm-list prd-picker-list">
          <li v-for="item in availablePrdRuns" :key="item.id">
            <button
              type="button"
              class="prd-picker-item"
              :class="{ active: item.id === activePrdRunId }"
              @click="selectActivePrd(item.id)"
            >
              <strong>{{ item.summary || item.taskTitle || "未命名 PRD" }}</strong>
              <small>{{ formatTime(item.createdAt) }}</small>
            </button>
          </li>
        </ul>
        <footer class="modal-actions">
          <button class="secondary-button" type="button" @click="closePrdPicker">取消</button>
        </footer>
      </section>
    </div>

    <div v-if="replacementOpen" class="modal-backdrop" @click.self="closeReplacement">
      <section class="modal run-confirm-modal" role="dialog" aria-modal="true" aria-labelledby="replacement-title">
        <header class="modal-header">
          <div>
            <span class="eyebrow">替换保护</span>
            <h2 id="replacement-title">替换当前 PRD？</h2>
          </div>
          <button class="icon-button" type="button" title="关闭" aria-label="关闭" :disabled="replacing" @click="closeReplacement"><X :size="16" /></button>
        </header>
        <p v-if="!replacementFinalConfirm" class="run-confirm-lead">替换会移除当前工作稿，且不能在工作台内恢复。可先导出当前内容留存。</p>
        <p v-else class="run-confirm-lead hint warn"><strong>最后确认：</strong>继续后将不保留当前工作稿，并立即换成候选稿。</p>
        <p v-if="draftDirty" class="hint warn">导出内容将包含编辑器中尚未保存的修改。</p>
        <footer class="modal-actions replacement-actions">
          <button class="secondary-button" type="button" :disabled="replacing" @click="exportReplacementDraft">
            <Download :size="14" />导出当前内容
          </button>
          <button class="secondary-button" type="button" :disabled="replacing" @click="closeReplacement">取消</button>
          <button class="run-button compact" type="button" :disabled="replacing" @click="confirmReplacement">
            {{ replacing ? "替换中" : replacementFinalConfirm ? "确认替换" : "不保留并替换" }}
          </button>
        </footer>
      </section>
    </div>

    <ChangelogModal
      v-model="updateLogOpen"
      :user-id="user?.id || user?.username"
      @seen="updateLogSeen = true"
    />

    <Teleport to="body">
      <ul
        v-if="taskMenuOpen"
        class="task-menu"
        :style="taskMenuStyle"
        role="listbox"
        aria-label="需求列表"
      >
        <li v-if="!filteredTasks.length" class="task-menu-empty">没有匹配的需求</li>
        <li v-for="item in filteredTasks" :key="item.id">
          <button
            type="button"
            class="task-menu-item"
            :class="{ active: item.id === taskId }"
            role="option"
            :aria-selected="item.id === taskId"
            @click="pickTask(item.id)"
          >
            <span class="task-option-title">{{ item.title }}</span>
            <span class="task-option-time">{{ formatTime(item.createdAt) }}</span>
          </button>
        </li>
      </ul>
    </Teleport>
    <div class="toast" :class="{ visible: Boolean(toastMessage) }" role="status">{{ toastMessage }}</div>

    <!-- 售前 Demo 现场客户客制化弹窗 -->
    <div v-if="customizerModalOpen" class="modal-backdrop" @click.self="customizerModalOpen = false">
      <section class="modal" role="dialog" aria-modal="true" aria-labelledby="customizer-title">
        <header class="modal-header">
          <h2 id="customizer-title">Demo 现场客户客制化</h2>
          <button class="icon-button" type="button" title="关闭" @click="customizerModalOpen = false"><X :size="16" /></button>
        </header>
        <div class="modal-body">
          <p class="hint">拜访客户前快速将原型里的机构/系统名称替换为客户名称（现场生效）。</p>
          <label class="modal-field">
            <span>原机构/系统名称</span>
            <input v-model="customizerFrom" maxlength="200" placeholder="如：衡正律师事务所" />
          </label>
          <label class="modal-field">
            <span>目标客户名称</span>
            <input v-model="customizerTo" maxlength="200" placeholder="如：君合律师事务所" />
          </label>
        </div>
        <footer class="modal-footer">
          <button class="secondary-button" type="button" @click="customizerModalOpen = false">取消</button>
          <button class="run-button" type="button" :disabled="!customizerFrom.trim() || !customizerTo.trim()" @click="applyCustomerCustomizer">一键应用定制</button>
        </footer>
      </section>
    </div>

    <!-- 售前 Demo 全屏沉浸演示模式 -->
    <div v-if="isPresentationMode" class="presentation-container">
      <header class="presentation-bar">
        <div class="presentation-title">
          <span>{{ resultToolbar?.title || "Demo 原型沉浸演示" }}</span>
          <span class="presentation-badge">售前全屏演示</span>
        </div>
        <button class="secondary-button compact" type="button" @click="isPresentationMode = false">
          退出全屏 (ESC)
        </button>
      </header>
      <div class="presentation-iframe-shell">
        <iframe
          class="demo-preview-frame ready"
          :srcdoc="demoPreviewHtml"
          :sandbox="demoPreviewSandbox"
          referrerpolicy="no-referrer"
          title="Demo 全屏演示"
        ></iframe>
      </div>
    </div>
  </template>

<script setup lang="ts">
import { formatTechnicalDetail } from "../techSpecDisplay";
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import {
  AlertCircle,
  Archive,
  ArrowLeftRight,
  BookOpen,
  Bold,
  Check,
  CheckSquare,
  ChevronDown,
  ChevronRight,
  Clock,
  Code,
  Columns,
  Compass,
  Copy,
  Cpu,
  Database,
  Download,
  ExternalLink,
  FileSpreadsheet,
  FileText,
  History,
  Kanban,
  Layers,
  List,
  Loader2,
  LogOut,
  Maximize2,
  Minimize2,
  MoreHorizontal,
  PanelLeftClose,
  PanelLeftOpen,
  PanelRight,
  PanelRightClose,
  Play,
  Plus,
  Quote,
  RotateCcw,
  Search,
  Server,
  Shield,
  SlidersHorizontal,
  Sparkles,
  Table,
  Trash2,
  Workflow,
  X,
} from "lucide-vue-next";
import { trackFeature } from "../telemetry";
import {
  apiErrorMessage,
  apiErrorCode,
  confirmRun,
  createRun,
  createTask,
  deleteSource,
  downloadTaskBundleZip,
  exportEstimateXlsx,
  exportTestCaseXlsx,
  exportTechSpecMarkdown,
  fetchTechSpecMarkdownText,
  getDraftSource,
  getHealth,
  getReviewFeedback,
  getRun,
  getTask,
  listDemoSkills,
  listQualityOptions,
  listRuns,
  listTasks,
  logoutAccount,
  renameTask,
  type Completeness,
  type DemoSkill,
  type DraftSource,
  type EstimateItem,
  type EstimatePayload,
  type TestCaseItem,
  type TestCasePayload,
  type TechSpecApiEndpoint,
  type TechSpecDatabaseTable,
  type TechSpecPayload,
  type TechSpecTaskItem,
  type Mode,
  type ReviewFeedback,
  type ReviewFeedbackAction,
  type ReviewFeedbackItem,
  type Run,
  type RunHistoryItem,
  type Source,
  type TaskSummary,
  uploadSource,
  updateDraftSource,
  updateReviewFeedback,
} from "../api";
import { fetchReviewTasks } from "../reviewApi";
import { itemList, issueDetail, issueTitle, renderMarkdown, renderMarkdownWithAnchors, stripDuplicateLeadingHeading } from "../markdown";
import { downloadBlob, downloadDocx, downloadHtml, downloadMarkdown, DEMO_PREVIEW_SANDBOX, createDemoPreviewUrl, createSandboxedDemoHtml, inlineStaticPocHtml, buildFallbackDemoScript } from "../exportDoc";
import { buildDemoPackageZip, normalizeDemoPackageFiles, packageRootName, type DemoPackageFile } from "../demoPackage";
import { clearSession, getUser, storageKey, type User } from "../session";
import { formatElapsed } from "../formatElapsed";
import { filterTasks } from "../taskSearch";
import { actionCompleteness, applyExtraLens, completenessName, completenessOption, completenessTone, defaultLenses, extraLenses, FALLBACK_QUALITY, toggleLens, type QualityCatalog } from "../quality";
import { DRAFT_PREFIX, demoUploadName, displaySourceName, draftRunId, draftSourceName, draftTitlesFromRuns, estimateUploadName, isDraftSource, isGenerateSource, pocUploadName, reviewUploadName, techSpecUploadName, testCaseUploadName, visibleSources } from "../sources";
import { buildSettingsSummary, hasDistinctContextBody, initialDemoPreviewState, type DemoPreviewState } from "../workbenchUi";
import { draftContentState, hasUnsavedDraft, latestDraftCandidate, recoverUniqueDraftRunId } from "../draftEditing";
import { MAX_DEMO_TEXT_PATCHES, patchUniqueVisibleText, replayDemoTextPatches, type DemoTextPatch } from "../demoPatches";
import { findHeadingRange } from "../editorNavigation";
import { computeScrollAnchors, interpolateScroll, type ScrollAnchor } from "../editorScrollSync";
import { RUN_STAGES, runStageIndex } from "../runStepper";
import { CURRENT_UPDATE_LOG, UPDATE_LOG_FOOTER, UPDATE_LOG_HISTORY, updateLogSeenKey } from "../updateLog";
import ChangelogModal from "../components/ChangelogModal.vue";
import {
  runningModeLabel,
  updateTaskRunning,
  removeTaskRunning,
  type TaskRunningMap,
  shouldStopPollingImmediately,
  shouldResumeWatchersAfterHealthRecovery,
  MAX_CONSECUTIVE_POLL_FAILURES,
} from "../taskRunningState";
import {
  DEFAULT_ESTIMATE_COLUMNS,
  calculateEstimateGroups,
  formatCurrency,
  getEstimateComplexityBadgeClass,
  getEstimateTypeBadgeClass,
  type EstimateColumnConfig,
} from "../estimateExport";
import {
  DEFAULT_TEST_CASE_COLUMNS,
  buildTestCaseModuleGroups,
  typeLabel as testCaseTypeLabelFn,
  typeTagClass as testCaseTypeTagClassFn,
  priorityLabel as testCasePriorityLabelFn,
  priorityTagClass as testCasePriorityTagClassFn,
  type TestCaseColumnConfig,
  type TestCaseModuleGroup,
} from "../testCaseExport";
import { renderMermaidInContainer } from "../mermaidRenderer";

const router = useRouter();
const user = ref<User | null>(getUser());
const userMenuOpen = ref(false);
const userInitial = computed(() => {
  const name = user.value?.displayName || user.value?.username || "用";
  return name.slice(0, 1).toUpperCase();
});
const title = ref("未命名需求");
const savedTitle = ref("");
const mode = ref<Mode>("GENERATE");
const instruction = ref("");
const followUp = ref("");
const health = ref("DOWN");
const taskId = ref("");
const tasks = ref<TaskSummary[]>([]);
const sources = ref<Source[]>([]);
const emptyRuns = (): Record<Mode, Run | null> => ({ REVIEW: null, GENERATE: null, DEMO: null, POC: null, ESTIMATE: null, TEST_CASE: null, TECH_SPEC: null });
const runsByMode = ref<Record<Mode, Run | null>>(emptyRuns());
const history = ref<RunHistoryItem[]>([]);
const historyFilter = ref<"ALL" | Mode>("ALL");
const demoSkills = ref<DemoSkill[]>([{ id: "generic-admin-html", name: "通用 HTML 交互原型", description: "自包含 HTML 原型，不依赖私有宿主或构建工具" }]);
const demoSkill = ref("generic-admin-html");
const qualityCatalog = ref<QualityCatalog>(FALLBACK_QUALITY);
const completeness = ref<Completeness>("STANDARD");
const lensesByMode = ref<Record<Mode, string[]>>({
  GENERATE: defaultLenses(FALLBACK_QUALITY, "GENERATE"),
  REVIEW: defaultLenses(FALLBACK_QUALITY, "REVIEW"),
  DEMO: defaultLenses(FALLBACK_QUALITY, "DEMO"),
  POC: defaultLenses(FALLBACK_QUALITY, "POC"),
  ESTIMATE: defaultLenses(FALLBACK_QUALITY, "ESTIMATE"),
  TEST_CASE: defaultLenses(FALLBACK_QUALITY, "TEST_CASE"),
  TECH_SPEC: defaultLenses(FALLBACK_QUALITY, "TECH_SPEC"),
});
const revising = ref(false);
const selectedQuestions = ref<string[]>([]);
const errorMessage = ref("");
const uploadError = ref("");
const uploadOpen = ref(false);
const runConfirmOpen = ref(false);
const prdPickerOpen = ref(false);
const replacementOpen = ref(false);
const replacementTargetRunId = ref("");
const replacementFinalConfirm = ref(false);
const replacing = ref(false);
const generateInputsDirty = ref(false);
const activePrdRunId = ref<string | null>(null);
const activePrdRun = ref<Run | null>(null);
const newTaskOpen = ref(false);
const newTaskTitle = ref("");
const newTaskError = ref("");
const creatingTask = ref(false);
const newTaskInput = ref<HTMLInputElement | null>(null);
const taskPickerEl = ref<HTMLElement | null>(null);
const taskSearchInput = ref<HTMLInputElement | null>(null);
const taskQuery = ref("");
const taskMenuOpen = ref(false);
const taskMenuStyle = ref<Record<string, string>>({});
const HISTORY_OPEN_KEY = "prd_workbench_history_open";
const historyOpen = ref(typeof window !== "undefined" && !window.matchMedia("(max-width: 768px)").matches && localStorage.getItem(HISTORY_OPEN_KEY) !== "false");
watch(historyOpen, (val) => {
  try {
    localStorage.setItem(HISTORY_OPEN_KEY, String(val));
  } catch {}
});
const titleSavedHint = ref(false);
const toastMessage = ref("");
const nowTick = ref(Date.now());
const uploading = ref(false);
const starting = ref(false);
const pendingFiles = ref<File[]>([]);
const pastedMaterial = ref("");
const freshSourceIds = ref<string[]>([]);
const demoPreviewUrl = ref("");
const demoPreviewState = ref<DemoPreviewState>("empty");
const patchedDemoHtml = ref("");
const demoPatchFrom = ref("");
const demoPatchTo = ref("");
const demoPatchMessage = ref("");
const demoPatches = ref<DemoTextPatch[]>([]);
const demoResultTab = ref<"PREVIEW" | "PACKAGE">("PREVIEW");
const selectedPackagePath = ref("");
const packageCopyMessage = ref("");
const exportingPackage = ref(false);
const demoPreviewSandbox = DEMO_PREVIEW_SANDBOX;
const contextOpen = ref(false);
const updateLogOpen = ref(false);
const updateLogSeen = ref(false);
const taskFilterKeyword = ref("");
const displayedTasks = computed(() => {
  const q = taskFilterKeyword.value.trim().toLowerCase();
  if (!q) return tasks.value;
  return tasks.value.filter((t) => t.title.toLowerCase().includes(q));
});
const editorSplitMode = ref<"single" | "split">("split");
const editorTextareaEl = ref<HTMLTextAreaElement | null>(null);
const editorPreviewEl = ref<HTMLElement | null>(null);
const editorScrollSyncing = ref(false);
const editorScrollAnchors = ref<ScrollAnchor[]>([]);
let activeScrollSource: "editor" | "preview" | null = null;
let scrollLockTimer: number | null = null;
let anchorDirty = true;
const isPresentationMode = ref(false);
const customizerModalOpen = ref(false);
const customizerFrom = ref("衡正律师事务所");
const customizerTo = ref("");
const settingsExpanded = ref(false);
const contextWidth = ref(readSize("context-w", 340));
const resultCanvasEl = ref<HTMLElement | null>(null);
const resultFullscreen = ref(false);
const exporting = ref(false);
const draft = ref<DraftSource | null>(null);
const draftEditor = ref("");
const draftEditing = ref(false);
const draftSaving = ref(false);
const draftConflict = ref(false);
const feedback = ref<ReviewFeedback | null>(null);
const feedbackDrafts = ref<Record<string, ReviewFeedbackItem>>({});
const feedbackSavingPath = ref("");
const feedbackConflict = ref(false);
const feedbackLoadError = ref("");
const contextTitle = ref("引用与上下文");
const contextBody = ref("点击问题或引用后，这里显示材料原文。");
const contextHasQuote = ref(true);
const showMissingQuote = ref(false);
const taskRunningMap = ref<TaskRunningMap>({});
const runWatchers = new Map<string, number>();
let healthTimer: number | undefined;
let renameTimer: number | undefined;
let toastTimer: number | undefined;
let elapsedTimer: number | undefined;
let freshTimer: number | undefined;
let htmlPreviewUrl: string | undefined;
let syncingDraft = false;
let renaming = false;

const modes: Mode[] = ["REVIEW", "GENERATE", "DEMO", "POC", "ESTIMATE", "TEST_CASE", "TECH_SPEC"];
const run = computed(() => runsByMode.value[mode.value]);
const otherBusy = computed(() => modes.some((item) => item !== mode.value && isActive(runsByMode.value[item])));
const modeBusy = computed(() => starting.value || isActive(run.value));
const busy = computed(() => modeBusy.value || otherBusy.value);
const result = computed(() => (run.value?.result as Record<string, unknown> | null) || null);
const showResult = computed(() => Boolean(result.value && run.value?.status === "SUCCEEDED"));
const originalDemoHtml = computed(() => {
  if (!showResult.value || !result.value) return "";
  if (mode.value === "DEMO") {
    return String(result.value.html || "");
  }
  if (mode.value === "POC") {
    const html = String(result.value.html || "");
    if (html.trim()) return html;
    const files = (result.value.poc_files || {}) as Record<string, string>;
    const index = typeof files["index.html"] === "string" ? files["index.html"] : "";
    return index ? inlineStaticPocHtml(index, files) : "";
  }
  return "";
});
const demoPreviewSourceHtml = computed(() => patchedDemoHtml.value || originalDemoHtml.value);
const demoPreviewHtml = computed(() => createSandboxedDemoHtml(demoPreviewSourceHtml.value));
const demoPackage = computed(() =>
  normalizeDemoPackageFiles(mode.value === "POC" ? result.value?.poc_files : result.value?.package_files)
);
const demoPackageFiles = computed(() => demoPackage.value.files);
const selectedPackageFile = computed<DemoPackageFile | null>(() => {
  return demoPackageFiles.value.find((file) => file.path === selectedPackagePath.value) || demoPackageFiles.value[0] || null;
});
const demoPackageRoot = computed(() =>
  packageRootName(String(result.value?.title || title.value || "package"), mode.value === "POC" ? "hermes-poc" : "hermes-demo")
);
const prdResultTab = ref<"ASSEMBLED" | "PACK">("ASSEMBLED");
const selectedPrdPath = ref("");
const prdPack = computed(() => normalizeDemoPackageFiles(mode.value === "GENERATE" ? result.value?.prd_files : undefined));
const prdPackFiles = computed(() => prdPack.value.files);
const selectedPrdFile = computed<DemoPackageFile | null>(() => {
  return prdPackFiles.value.find((file) => file.path === selectedPrdPath.value) || prdPackFiles.value[0] || null;
});
const prdPackRoot = computed(() =>
  packageRootName(String(result.value?.title || title.value || "PRD"), "hermes-prd")
);
const isFallbackDemoScript = computed(() => {
  if ((mode.value !== "DEMO" && mode.value !== "POC") || !showResult.value || !result.value) {
    return false;
  }
  const res = result.value as Record<string, any>;
  const rawScript =
    res.scriptMarkdown ||
    res.script_markdown ||
    res.script ||
    res.demoScript ||
    res.presentationScript ||
    res.explanation ||
    (res.payload && typeof res.payload === "object"
      ? res.payload.scriptMarkdown || res.payload.script_markdown || res.payload.script
      : null);
  return !(typeof rawScript === "string" && rawScript.trim().length > 0);
});
const demoScriptMarkdown = computed(() => {
  if ((mode.value !== "DEMO" && mode.value !== "POC") || !showResult.value || !result.value) {
    return "";
  }
  const res = result.value as Record<string, any>;
  const rawScript =
    res.scriptMarkdown ||
    res.script_markdown ||
    res.script ||
    res.demoScript ||
    res.presentationScript ||
    res.explanation ||
    (res.payload && typeof res.payload === "object"
      ? res.payload.scriptMarkdown || res.payload.script_markdown || res.payload.script
      : null);
  if (typeof rawScript === "string" && rawScript.trim()) {
    return rawScript.trim();
  }
  const demoTitle = String(res.title || title.value || "系统原型");
  const summary = String(res.summary || "");
  return buildFallbackDemoScript(demoTitle, summary);
});
const demoScriptHtml = computed(() => renderMarkdown(demoScriptMarkdown.value));
const resultToolbar = computed(() => {
  if (!showResult.value || !result.value) {
    return null;
  }
  if (mode.value === "GENERATE") {
    return { kind: "GENERATE" as const, title: String(result.value.title || "生成稿") };
  }
  if (mode.value === "DEMO") {
    return { kind: "DEMO" as const, title: String(result.value.title || "Demo") };
  }
  if (mode.value === "POC") {
    return { kind: "DEMO" as const, title: String(result.value.title || "POC 演示系统") };
  }
  if (mode.value === "ESTIMATE") {
    return { kind: "ESTIMATE" as const, title: String(result.value.title || "工作量评估与报价") };
  }
  if (mode.value === "TEST_CASE") {
    return { kind: "TEST_CASE" as const, title: String(result.value.title || "系统测试用例集") };
  }
  if (mode.value === "TECH_SPEC") {
    return { kind: "TECH_SPEC" as const, title: String(result.value.title || "研发技术方案") };
  }
  return { kind: "REVIEW" as const, title: String(result.value.title || "评审") };
});

// --- 工作量评估 (ESTIMATE) 状态与方法 ---
const getInitialEstimateRate = (): number | null => {
  const saved = typeof window !== "undefined" ? localStorage.getItem(storageKey("estimate-rate")) : null;
  const num = Number(saved);
  return Number.isFinite(num) && num > 0 ? num : null;
};
const estimateDailyRate = ref<number | null>(getInitialEstimateRate());
watch(estimateDailyRate, (val) => {
  if (val && val > 0) {
    localStorage.setItem(storageKey("estimate-rate"), String(val));
  } else {
    localStorage.removeItem(storageKey("estimate-rate"));
  }
});

const estimateColumnsDrawerOpen = ref(false);
const estimateSearchKeyword = ref("");
const estimateTypeFilter = ref<"ALL" | "PAGE" | "API" | "THIRD_PARTY_API">("ALL");
const estimateColumns = ref<EstimateColumnConfig[]>(DEFAULT_ESTIMATE_COLUMNS.map((c: EstimateColumnConfig) => ({ ...c })));
const newCustomColTitle = ref("");
const customFieldStore = ref<Record<string, string>>({});

function addCustomColumn() {
  const t = newCustomColTitle.value.trim();
  if (!t) return;
  const key = "custom_" + Date.now();
  estimateColumns.value.push({
    key,
    title: t,
    visible: true,
    isCustom: true,
    align: "left",
  });
  newCustomColTitle.value = "";
}

function removeCustomColumn(key: string) {
  estimateColumns.value = estimateColumns.value.filter((col) => col.key !== key);
}

const estimateScopeNote = computed(() => {
  if (mode.value !== "ESTIMATE" || !result.value) return "";
  const res = result.value as Record<string, any>;
  return String(
    res.scopeNote ||
    res.scope_note ||
    "本次报价仅包含开发和测试的工作量，不含产品设计、运维、项目管理的工作量。"
  );
});

const estimateItems = computed<EstimateItem[]>(() => {
  if (mode.value !== "ESTIMATE" || !result.value) return [];
  const res = result.value as Record<string, any>;
  const items = res.items;
  return Array.isArray(items) ? (items as EstimateItem[]) : [];
});

const estimateDevDays = computed(() => {
  if (mode.value !== "ESTIMATE" || !result.value) return 0;
  const res = result.value as Record<string, any>;
  if (typeof res.devDays === "number") return Math.round(res.devDays * 10) / 10;
  if (typeof res.dev_days === "number") return Math.round(res.dev_days * 10) / 10;
  return Math.round(estimateItems.value.reduce((sum, item) => sum + (Number(item.devDays) || 0), 0) * 10) / 10;
});

const estimateTestDays = computed(() => {
  if (mode.value !== "ESTIMATE" || !result.value) return 0;
  const res = result.value as Record<string, any>;
  if (typeof res.testDays === "number") return Math.round(res.testDays * 10) / 10;
  if (typeof res.test_days === "number") return Math.round(res.test_days * 10) / 10;
  return Math.round(estimateItems.value.reduce((sum, item) => sum + (Number(item.testDays) || 0), 0) * 10) / 10;
});

const estimateTotalDays = computed(() => {
  if (mode.value !== "ESTIMATE" || !result.value) return 0;
  const res = result.value as Record<string, any>;
  if (estimateDevDays.value > 0 || estimateTestDays.value > 0) {
    return Math.round((estimateDevDays.value + estimateTestDays.value) * 10) / 10;
  }
  if (typeof res.totalDays === "number") return Math.round(res.totalDays * 10) / 10;
  if (typeof res.total_days === "number") return Math.round(res.total_days * 10) / 10;
  return Math.round(estimateItems.value.reduce((sum, item) => sum + (Number(item.totalDays) || 0), 0) * 10) / 10;
});

const estimatePageCount = computed(() => {
  return estimateItems.value.filter((i) => i.type === "PAGE").length;
});

const estimateApiCount = computed(() => {
  return estimateItems.value.filter((i) => i.type === "API" || i.type === "THIRD_PARTY_API").length;
});

const estimateThirdPartyCount = computed(() => {
  return estimateItems.value.filter((i) => i.type === "THIRD_PARTY_API").length;
});

const estimateTotalQuote = computed(() => {
  return estimateTotalDays.value * (estimateDailyRate.value || 0);
});

const filteredEstimateItems = computed(() => {
  let list = estimateItems.value;
  if (estimateTypeFilter.value !== "ALL") {
    list = list.filter((i) => i.type === estimateTypeFilter.value);
  }
  const kw = estimateSearchKeyword.value.trim().toLowerCase();
  if (kw) {
    list = list.filter(
      (i) =>
        (i.module && i.module.toLowerCase().includes(kw)) ||
        (i.subModule && i.subModule.toLowerCase().includes(kw)) ||
        (i.name && i.name.toLowerCase().includes(kw)) ||
        (i.detail && i.detail.toLowerCase().includes(kw)) ||
        (i.description && i.description.toLowerCase().includes(kw))
    );
  }
  return list;
});

const groupedEstimateItems = computed(() => {
  return calculateEstimateGroups(filteredEstimateItems.value);
});

const visibleEstimateColumns = computed(() => {
  return estimateColumns.value.filter((col) => col.visible);
});

function formatPrice(val: number) {
  return formatCurrency(val);
}

function getRowIndex(item: EstimateItem) {
  const idx = filteredEstimateItems.value.indexOf(item);
  return idx >= 0 ? idx + 1 : 1;
}

function typeTagClass(type: string) {
  return getEstimateTypeBadgeClass(type);
}

function typeLabel(type: string) {
  if (type === "PAGE") return "页面";
  if (type === "API") return "内部接口";
  if (type === "THIRD_PARTY_API") return "第三方接口";
  return type;
}

function complexityTagClass(complexity?: string) {
  return getEstimateComplexityBadgeClass(complexity || "");
}

function complexityLabel(complexity?: string) {
  if (complexity === "SIMPLE") return "简单";
  if (complexity === "MEDIUM") return "中等";
  if (complexity === "COMPLEX") return "复杂";
  return "中等";
}

function getExtraValue(item: EstimateItem, key: string) {
  return (item as any)[key] ?? "";
}

async function handleExportEstimateXlsx() {
  if (exporting.value || !run.value) return;
  exporting.value = true;
  try {
    const customHeaders = estimateColumns.value.filter((c) => c.isCustom).map((c) => c.title);
    const extraFieldValues: Record<string, string> = {};
    for (const [k, v] of Object.entries(customFieldStore.value)) {
      if (v) extraFieldValues[k] = v;
    }
    await exportEstimateXlsx(run.value.id, {
      columns: estimateColumns.value.map((c) => ({ key: c.key, title: c.title, visible: c.visible })),
      dailyRate: estimateDailyRate.value || undefined,
      customHeaders: customHeaders.length ? customHeaders : undefined,
      extraFieldValues: Object.keys(extraFieldValues).length ? extraFieldValues : undefined,
    });
    showToast("工作量评估 Excel 导出成功");
  } catch (err) {
    errorMessage.value = apiErrorMessage(err);
  } finally {
    exporting.value = false;
  }
}

// --- 测试用例 (TEST_CASE) 状态与方法 ---
const testCaseColumnsDrawerOpen = ref(false);
const testCaseSearchKeyword = ref("");
const testCaseModuleFilter = ref("ALL");
const testCasePriorityFilter = ref("ALL");
const testCaseTypeFilter = ref("ALL");
const testCaseColumns = ref<TestCaseColumnConfig[]>(DEFAULT_TEST_CASE_COLUMNS.map((c) => ({ ...c })));
const newCustomTestCaseColTitle = ref("");
const customTestCaseFieldStore = ref<Record<string, string>>({});

function addCustomTestCaseColumn() {
  const t = newCustomTestCaseColTitle.value.trim();
  if (!t) return;
  const key = "custom_" + Date.now();
  testCaseColumns.value.push({
    key,
    title: t,
    visible: true,
    isCustom: true,
    align: "left",
  });
  newCustomTestCaseColTitle.value = "";
}

function removeCustomTestCaseColumn(key: string) {
  testCaseColumns.value = testCaseColumns.value.filter((col) => col.key !== key);
}

const testCaseScopeNote = computed(() => {
  if (mode.value !== "TEST_CASE" || !result.value) return "";
  const res = result.value as Record<string, any>;
  return String(
    res.scopeNote ||
    res.scope_note ||
    "本测试用例集覆盖核心业务流程、边界极值、逆向异常流与权限安全校验，可作为提测冒烟与全量回归基准。"
  );
});

const testCaseItems = computed<TestCaseItem[]>(() => {
  if (mode.value !== "TEST_CASE" || !result.value) return [];
  const res = result.value as Record<string, any>;
  const items = res.items;
  return Array.isArray(items) ? (items as TestCaseItem[]) : [];
});

const testCaseTotalCount = computed(() => {
  if (mode.value !== "TEST_CASE" || !result.value) return 0;
  const res = result.value as Record<string, any>;
  if (typeof res.totalCount === "number") return res.totalCount;
  if (typeof res.total_count === "number") return res.total_count;
  return testCaseItems.value.length;
});

const testCaseP0Count = computed(() => {
  if (mode.value !== "TEST_CASE" || !result.value) return 0;
  const res = result.value as Record<string, any>;
  if (typeof res.p0Count === "number") return res.p0Count;
  if (typeof res.p0_count === "number") return res.p0_count;
  return testCaseItems.value.filter((i) => (i.priority || "").toUpperCase() === "P0").length;
});

const testCaseP1Count = computed(() => {
  if (mode.value !== "TEST_CASE" || !result.value) return 0;
  const res = result.value as Record<string, any>;
  if (typeof res.p1Count === "number") return res.p1Count;
  if (typeof res.p1_count === "number") return res.p1_count;
  return testCaseItems.value.filter((i) => (i.priority || "").toUpperCase() === "P1").length;
});

const testCaseP2Count = computed(() => {
  if (mode.value !== "TEST_CASE" || !result.value) return 0;
  const res = result.value as Record<string, any>;
  if (typeof res.p2Count === "number") return res.p2Count;
  if (typeof res.p2_count === "number") return res.p2_count;
  return testCaseItems.value.filter((i) => (i.priority || "").toUpperCase() === "P2").length;
});

const testCaseBoundaryCount = computed(() => {
  if (mode.value !== "TEST_CASE" || !result.value) return 0;
  const res = result.value as Record<string, any>;
  if (typeof res.boundaryCount === "number") return res.boundaryCount;
  if (typeof res.boundary_count === "number") return res.boundary_count;
  return testCaseItems.value.filter((i) => (i.type || "").toUpperCase() === "BOUNDARY").length;
});

const testCaseExceptionCount = computed(() => {
  if (mode.value !== "TEST_CASE" || !result.value) return 0;
  const res = result.value as Record<string, any>;
  if (typeof res.exceptionCount === "number") return res.exceptionCount;
  if (typeof res.exception_count === "number") return res.exception_count;
  return testCaseItems.value.filter((i) => (i.type || "").toUpperCase() === "EXCEPTION").length;
});

const testCaseModuleOptions = computed<string[]>(() => {
  const set = new Set<string>();
  for (const item of testCaseItems.value) {
    if (item.module) set.add(item.module);
  }
  return Array.from(set);
});

const filteredTestCaseItems = computed(() => {
  let list = testCaseItems.value;
  if (testCaseModuleFilter.value !== "ALL") {
    list = list.filter((i) => i.module === testCaseModuleFilter.value);
  }
  if (testCasePriorityFilter.value !== "ALL") {
    list = list.filter((i) => (i.priority || "").toUpperCase() === testCasePriorityFilter.value);
  }
  if (testCaseTypeFilter.value !== "ALL") {
    list = list.filter((i) => (i.type || "").toUpperCase() === testCaseTypeFilter.value);
  }
  const kw = testCaseSearchKeyword.value.trim().toLowerCase();
  if (kw) {
    list = list.filter(
      (i) =>
        (i.module && i.module.toLowerCase().includes(kw)) ||
        (i.subModule && i.subModule.toLowerCase().includes(kw)) ||
        (i.title && i.title.toLowerCase().includes(kw)) ||
        (i.steps && i.steps.toLowerCase().includes(kw)) ||
        (i.expectedResult && i.expectedResult.toLowerCase().includes(kw)) ||
        (i.caseId && i.caseId.toLowerCase().includes(kw))
    );
  }
  return list;
});

const groupedTestCaseItems = computed(() => {
  return buildTestCaseModuleGroups(filteredTestCaseItems.value);
});

const visibleTestCaseColumns = computed(() => {
  return testCaseColumns.value.filter((col) => col.visible);
});

function getTestCaseRowIndex(item: TestCaseItem) {
  const idx = filteredTestCaseItems.value.indexOf(item);
  return idx >= 0 ? idx + 1 : 1;
}

function testCaseTypeLabel(type: string) {
  return testCaseTypeLabelFn(type);
}

function testCaseTypeTagClass(type: string) {
  return testCaseTypeTagClassFn(type);
}

function testCasePriorityLabel(priority: string) {
  return testCasePriorityLabelFn(priority);
}

function testCasePriorityTagClass(priority: string) {
  return testCasePriorityTagClassFn(priority);
}

function getTestCaseExtraValue(item: TestCaseItem, key: string) {
  return (item as any)[key] ?? (item.extraFields ? item.extraFields[key] : "") ?? "";
}

async function handleExportTestCaseXlsx() {
  if (exporting.value || !run.value) return;
  exporting.value = true;
  try {
    const customHeaders = testCaseColumns.value.filter((c) => c.isCustom).map((c) => c.title);
    const extraFieldValues: Record<string, string> = {};
    for (const [k, v] of Object.entries(customTestCaseFieldStore.value)) {
      if (v) extraFieldValues[k] = v;
    }
    await exportTestCaseXlsx(run.value.id, {
      title: (result.value as any)?.title || title.value || undefined,
      columns: testCaseColumns.value.map((c) => ({ key: c.key, title: c.title, visible: c.visible })),
      customHeaders: customHeaders.length ? customHeaders : undefined,
      extraFieldValues: Object.keys(extraFieldValues).length ? extraFieldValues : undefined,
    });
    showToast("测试用例清单 Excel 导出成功");
  } catch (err) {
    errorMessage.value = apiErrorMessage(err);
  } finally {
    exporting.value = false;
  }
}

// --- 研发技术方案 (TECH_SPEC) 状态与方法 ---
const techSpecActiveTab = ref<"TASKS" | "ARCH" | "DB" | "API">("ARCH");
const techSpecSearchKeyword = ref("");
const techSpecModuleFilter = ref("ALL");
const techSpecTypeFilter = ref("ALL");
const techSpecPriorityFilter = ref("ALL");
const copiedTechSpec = ref(false);

const techSpecPayload = computed(() =>
  mode.value === "TECH_SPEC" ? (result.value as unknown as TechSpecPayload | null) : null
);

const techSpecTotalTasks = computed(() => techSpecPayload.value?.totalTasks || techSpecPayload.value?.tasks?.length || 0);
const techSpecTotalDevDays = computed(() => techSpecPayload.value?.totalDevDays || 0);
const techSpecFrontendTasks = computed(() => techSpecPayload.value?.frontendTasks || 0);
const techSpecBackendTasks = computed(() => techSpecPayload.value?.backendTasks || 0);
const techSpecMiddlewareTasks = computed(() => techSpecPayload.value?.middlewareTasks || 0);
const techSpecDatabaseTables = computed(() => techSpecPayload.value?.databaseTables || []);
const techSpecApiEndpoints = computed(() => techSpecPayload.value?.apiEndpoints || []);
const techSpecHasArchitecture = computed(() => !!(techSpecPayload.value?.architectureMarkdown || "").trim());
const techSpecModuleDocCount = computed(() => {
  const files = (techSpecPayload.value as any)?.tech_spec_files;
  if (!files || typeof files !== "object") return 0;
  return Object.keys(files).filter((p) => p.startsWith("modules/") && p.endsWith(".md")).length;
});

const techSpecArchitectureHtml = computed(() =>
  renderMarkdown(techSpecPayload.value?.architectureMarkdown || "")
);

const techSpecModuleOptions = computed(() => {
  const tasks = techSpecPayload.value?.tasks || [];
  const set = new Set<string>();
  tasks.forEach((t) => {
    if (t.module?.trim()) set.add(t.module.trim());
  });
  return Array.from(set);
});

const filteredTechTasks = computed(() => {
  let list = techSpecPayload.value?.tasks || [];
  if (techSpecModuleFilter.value !== "ALL") {
    list = list.filter((t) => t.module === techSpecModuleFilter.value);
  }
  if (techSpecTypeFilter.value !== "ALL") {
    list = list.filter((t) => t.type === techSpecTypeFilter.value);
  }
  if (techSpecPriorityFilter.value !== "ALL") {
    list = list.filter((t) => t.priority === techSpecPriorityFilter.value);
  }
  const kw = techSpecSearchKeyword.value.trim().toLowerCase();
  if (kw) {
    list = list.filter(
      (t) =>
        (t.taskId && t.taskId.toLowerCase().includes(kw)) ||
        (t.name && t.name.toLowerCase().includes(kw)) ||
        (t.module && t.module.toLowerCase().includes(kw)) ||
        (t.description && t.description.toLowerCase().includes(kw))
    );
  }
  return list;
});

async function handleExportTechSpecMarkdown() {
  if (exporting.value || !run.value) return;
  exporting.value = true;
  try {
    const title = techSpecPayload.value?.title || "研发技术方案";
    await exportTechSpecMarkdown(run.value.id, { title });
    trackFeature("TECH_SPEC", "EXPORT_MARKDOWN", { taskId: taskId.value });
    showToast("完整技术方案 Markdown 已开始下载");
  } catch (error) {
    showToast(apiErrorMessage(error) || "导出技术方案失败");
  } finally {
    exporting.value = false;
  }
}

async function copyTechSpecMarkdown() {
  if (!run.value || exporting.value) return;
  exporting.value = true;
  try {
    const title = techSpecPayload.value?.title || "研发技术方案";
    const md = await fetchTechSpecMarkdownText(run.value.id, { title });
    await navigator.clipboard.writeText(md);
    copiedTechSpec.value = true;
    trackFeature("TECH_SPEC", "COPY_MARKDOWN", { taskId: taskId.value });
    showToast("完整技术方案 Markdown 已复制到剪贴板");
    setTimeout(() => { copiedTechSpec.value = false; }, 2000);
  } catch {
    showToast("复制失败，请改用「导出 Markdown」下载");
  } finally {
    exporting.value = false;
  }
}

function techSpecTypeLabel(type: string) {
  if (type === "FRONTEND") return "前端开发";
  if (type === "BACKEND") return "后端开发";
  if (type === "MIDDLEWARE") return "中间件/DB";
  return type || "研发任务";
}

function techSpecTypeBadgeClass(type: string) {
  if (type === "FRONTEND") return "tech-type-fe";
  if (type === "BACKEND") return "tech-type-be";
  if (type === "MIDDLEWARE") return "tech-type-mw";
  return "tech-type-default";
}

function techSpecPriorityBadgeClass(priority: string) {
  if (priority === "P0") return "priority-badge p0";
  if (priority === "P1") return "priority-badge p1";
  if (priority === "P2") return "priority-badge p2";
  return "priority-badge";
}

function formatJsonSnippet(raw: unknown): string {
  if (!raw) return "";
  if (typeof raw === "string") {
    try {
      const parsed = JSON.parse(raw);
      return JSON.stringify(parsed, null, 2);
    } catch {
      return raw;
    }
  }
  try {
    return JSON.stringify(raw, null, 2);
  } catch {
    return String(raw);
  }
}

// --- 资产全景协同矩阵 (Asset Matrix) 状态与导出大礼包 ---
const hasAnySucceededRun = computed(() =>
  modes.some((m) => runsByMode.value[m]?.status === "SUCCEEDED")
);

const exportingBundle = ref(false);

async function handleExportBundle() {
  if (!taskId.value || exportingBundle.value) return;
  exportingBundle.value = true;
  try {
    await downloadTaskBundleZip(taskId.value);
    showToast("交付大礼包 (.zip) 下载成功");
  } catch (err) {
    errorMessage.value = "导出交付大礼包失败: " + apiErrorMessage(err);
  } finally {
    exportingBundle.value = false;
  }
}

const assetMatrix = computed(() => {
  const gen = runsByMode.value.GENERATE;
  const rev = runsByMode.value.REVIEW;
  const demo = runsByMode.value.DEMO;
  const poc = runsByMode.value.POC;
  const est = runsByMode.value.ESTIMATE;
  const tc = runsByMode.value.TEST_CASE;
  const ts = runsByMode.value.TECH_SPEC;

  return {
    GENERATE: {
      ready: gen?.status === "SUCCEEDED",
      statusText: gen?.status === "SUCCEEDED" ? "PRD 已就绪" : "待生成",
      tooltip: gen?.status === "SUCCEEDED" ? `PRD 规格说明书 (v${availablePrdRuns.value.length || 1})，点击查看` : "尚未生成 PRD",
    },
    REVIEW: {
      ready: rev?.status === "SUCCEEDED",
      statusText: rev?.status === "SUCCEEDED" ? `${(rev.result as any)?.blockingIssues?.length || 0}项阻断` : "待评审",
      tooltip: rev?.status === "SUCCEEDED" ? `专家评审结论与整改确认，点击查看` : "尚未进行评审",
    },
    DEMO: {
      ready: demo?.status === "SUCCEEDED",
      statusText: demo?.status === "SUCCEEDED" ? "原型可演示" : "待生成",
      tooltip: demo?.status === "SUCCEEDED" ? `交互原型已生成，支持单页独立演示，点击查看` : "尚未生成原型",
    },
    POC: {
      ready: poc?.status === "SUCCEEDED",
      statusText: poc?.status === "SUCCEEDED" ? "POC 可演示" : "待生成",
      tooltip: poc?.status === "SUCCEEDED" ? `静态多文件 POC 已生成，工作台沙箱预览，ZIP 可本地点击` : "尚未生成 POC 演示系统",
    },
    ESTIMATE: {
      ready: est?.status === "SUCCEEDED",
      statusText: est?.status === "SUCCEEDED" ? `${(est.result as any)?.totalDays || 0}人天` : "待评估",
      tooltip: est?.status === "SUCCEEDED" ? `工作量评估表 (${(est.result as any)?.totalDays || 0} 人天)，点击查看` : "尚未进行工作量评估",
    },
    TEST_CASE: {
      ready: tc?.status === "SUCCEEDED",
      statusText: tc?.status === "SUCCEEDED" ? `${(tc.result as any)?.totalCount || 0}条用例` : "待生成",
      tooltip: tc?.status === "SUCCEEDED" ? `系统测试用例集 (${(tc.result as any)?.totalCount || 0} 条，P0: ${(tc.result as any)?.p0Count || 0})，点击查看` : "尚未生成测试用例",
    },
    TECH_SPEC: {
      ready: ts?.status === "SUCCEEDED",
      statusText: ts?.status === "SUCCEEDED" ? `${((ts.result as any)?.databaseTables || []).length || 0}表/${((ts.result as any)?.apiEndpoints || []).length || 0}接口` : "待生成",
      tooltip: ts?.status === "SUCCEEDED" ? `详细技术方案（架构/表/API），点击查看` : "尚未生成技术方案",
    },
  };
});

const filteredTasks = computed(() => filterTasks(tasks.value, taskQuery.value, title.value));
const blockingIssues = computed(() => itemList(result.value?.blockingIssues));
const otherSuggestions = computed(() => itemList(result.value?.otherSuggestions));
const readyItems = computed(() => itemList(result.value?.readyItems));
const openQuestions = computed(() => itemList(result.value?.openQuestions));
const references = computed(() => itemList(result.value?.references));
const currentDraftSource = computed(() =>
  activePrdRunId.value ? sources.value.find((source) => source.name === draftSourceName(activePrdRunId.value!)) || null : null
);
const showingCurrentDraft = computed(
  () => mode.value === "GENERATE" && Boolean(run.value?.id && run.value.id === activePrdRunId.value && draft.value)
);
const displayedMarkdown = computed(() =>
  showingCurrentDraft.value ? draftEditor.value : String(result.value?.markdown || "")
);
const cleanedMarkdown = computed(() =>
  stripDuplicateLeadingHeading(displayedMarkdown.value, String(result.value?.title || ""))
);
const markdownAnalysis = computed(() => renderMarkdownWithAnchors(cleanedMarkdown.value));
const markdownHtml = computed(() => markdownAnalysis.value.html);
const tocList = computed(() => markdownAnalysis.value.toc);
const tocDepthFilter = ref<"ALL" | "H2">("ALL");
const tocKeyword = ref("");
const TOC_POS_KEY = "prd_workbench_toc_position";
const tocPosition = ref<"left" | "right">(
  typeof window !== "undefined" && localStorage.getItem(TOC_POS_KEY) === "right" ? "right" : "left"
);
watch(tocPosition, (val) => {
  try {
    localStorage.setItem(TOC_POS_KEY, val);
  } catch {}
});
const tocCollapsed = ref(false);

const hasSubHeadings = computed(() => {
  return tocList.value.some((h) => h.level > 2);
});

const displayedTocList = computed(() => {
  let list = tocList.value;
  if (tocDepthFilter.value === "H2") {
    list = list.filter((h) => h.level <= 2);
  }
  const kw = tocKeyword.value.trim().toLowerCase();
  if (kw) {
    list = list.filter((h) => h.text.toLowerCase().includes(kw));
  }
  return list;
});
const editorMarkdownAnalysis = computed(() => renderMarkdownWithAnchors(draftEditor.value));
const editorPreviewHtml = computed(() => editorMarkdownAnalysis.value.html);
const editorTocList = computed(() => editorMarkdownAnalysis.value.toc);
const activePrdMarkdownHtml = computed(() => {
  const md = String(activePrdRun.value?.result?.markdown || draft.value?.content || "");
  if (!md) return "";
  return renderMarkdownWithAnchors(md).html;
});
const triggerMermaidRender = () => {
  nextTick(() => {
    if (typeof document === "undefined") return;
    const targets = document.querySelectorAll<HTMLElement>(".markdown");
    targets.forEach((el) => {
      renderMermaidInContainer(el);
    });
  });
};
watch([markdownHtml, editorPreviewHtml, activePrdMarkdownHtml, showResult, mode, techSpecActiveTab], () => {
  triggerMermaidRender();
}, { flush: "post" });
const currentDraftState = computed(() => {
  if (!draft.value || !activePrdRun.value) return null;
  return draftContentState(
    draft.value.content,
    String(activePrdRun.value.result?.markdown || ""),
    draftEditor.value
  );
});
const draftDirty = computed(() => Boolean(draft.value && hasUnsavedDraft(draft.value.content, draftEditor.value)));
const draftStateLabel = computed(() => {
  if (currentDraftState.value === "UNSAVED") return "未保存";
  if (currentDraftState.value === "EDITED") return "当前稿 · 已编辑";
  return "当前稿";
});
const pendingPrdCandidate = computed(() => latestDraftCandidate(history.value, activePrdRunId.value));
const visibleSourceList = computed(() =>
  visibleSources(sources.value, mode.value, revising.value ? activePrdRunId.value : null)
);
const extraModeLenses = computed(() => extraLenses(qualityCatalog.value, mode.value));
const selectedLenses = computed(() => lensesByMode.value[mode.value] || []);
const selectedExtraCount = computed(() =>
  extraModeLenses.value.filter((item) => selectedLenses.value.includes(item.id)).length
);
const defaultLensHint = computed(() => {
  const extrasOn = extraModeLenses.value.some((item) => selectedLenses.value.includes(item.id));
  const name = (qualityCatalog.value.lenses[mode.value] || []).find((item) => item.default)?.name;
  if (mode.value === "GENERATE" && extrasOn) return "已改拆法";
  if (mode.value === "REVIEW") return name ? `默认${name}` : "默认看开发能否开工";
  if (mode.value === "DEMO") return name ? `默认${name}` : "默认主路径可点";
  if (mode.value === "POC") return name ? `默认${name}` : "默认核心域做深";
  if (mode.value === "ESTIMATE") return name ? `默认${name}` : "默认页面与接口拆分";
  if (mode.value === "TEST_CASE") return name ? `默认${name}` : "默认全覆盖 (功能/边界/异常)";
  return name ? `默认${name}` : "默认按模块拆";
});
const selectedCompleteness = computed(() => completenessOption(qualityCatalog.value, completeness.value));
const runCompleteness = computed(() => run.value?.completeness || completeness.value);
const runCompletenessName = computed(() => completenessName(runCompleteness.value));
const runCompletenessTone = computed(() => completenessTone(runCompleteness.value));
const qualityHint = computed(() => {
  const cost = selectedCompleteness.value?.costHint || "";
  if (revising.value && mode.value === "GENERATE") {
    return ["会以当前稿为底稿再打一轮，保留目录和结论方向，不保证逐句不改。", cost].filter(Boolean).join(" ");
  }
  if (completeness.value === "STANDARD" && !cost) {
    return "";
  }
  return completeness.value === "STANDARD" ? "" : cost;
});
const lensGroupLabel = computed(() => {
  if (mode.value === "REVIEW") return "补充评审角度";
  if (mode.value === "DEMO") return "补充 Demo 重点";
  if (mode.value === "POC") return "补充 POC 范围";
  if (mode.value === "TEST_CASE") return "补充测试视角";
  return "换拆法";
});
const settingsSummary = computed(() =>
  buildSettingsSummary({
    completeness: completenessName(completeness.value),
    defaultFocus: defaultLensHint.value,
    selectedExtraCount: selectedExtraCount.value,
    skill:
      mode.value === "DEMO"
        ? demoSkills.value.find((item) => item.id === demoSkill.value)?.name || demoSkill.value
        : undefined,
  })
);
const contextBodyVisible = computed(() => hasDistinctContextBody(contextTitle.value, contextBody.value));
const draftTitles = computed(() => draftTitlesFromRuns([...Object.values(runsByMode.value), activePrdRun.value]));
const latestPrdTitle = computed(() => {
  const generateRun = runsByMode.value.GENERATE;
  const title = generateRun?.result?.title;
  return title ? String(title) : "已生成的 PRD";
});
const hasGenerateDraft = computed(() => {
  const generateRun = activePrdRun.value;
  return generateRun?.status === "SUCCEEDED" && Boolean(generateRun?.result);
});
const usesExistingPrd = computed(
  () =>
    mode.value === "REVIEW" ||
    mode.value === "DEMO" ||
    mode.value === "POC" ||
    mode.value === "TEST_CASE" ||
    mode.value === "TECH_SPEC" ||
    mode.value === "ESTIMATE"
);
const availablePrdRuns = computed(() =>
  history.value.filter(
    (item) => item.taskId === taskId.value && item.mode === "GENERATE" && item.status === "SUCCEEDED"
  )
);
const hasActivePrd = computed(() => Boolean(activePrdRunId.value && availablePrdRuns.value.some((item) => item.id === activePrdRunId.value)));
const activePrdTitle = computed(() => {
  const loadedTitle = activePrdRun.value?.id === activePrdRunId.value ? activePrdRun.value.result?.title : null;
  if (typeof loadedTitle === "string" && loadedTitle.trim()) return loadedTitle.trim();
  const item = availablePrdRuns.value.find((entry) => entry.id === activePrdRunId.value);
  return item?.summary || item?.taskTitle || latestPrdTitle.value;
});
const showReviseToggle = computed(() => mode.value === "GENERATE" && hasGenerateDraft.value && !generateInputsDirty.value);
const instructionPlaceholder = computed(() => {
  if (mode.value === "REVIEW") return "可填写关注点，也可留空";
  if (mode.value === "DEMO") return "客户/场景、主流程、最多 3 个必须可点动作";
  if (mode.value === "POC") return "核心域、必须可点的一级菜单、售前主路径";
  if (mode.value === "ESTIMATE") return "可填写特定的评估规则、单价假设或工时约束，留空使用示例开发与自测范围";
  if (mode.value === "TEST_CASE") return "可填写特定的测试重点、异常拦截重点或自动化覆盖要求，留空自动全覆盖";
  if (mode.value === "TECH_SPEC") return "可填写特定的架构选型、接口规范或模块设计重点，留空按标准详细方案生成";
  return "可填写生成要求";
});
const emptyHint = computed(() => {
  if (mode.value === "GENERATE") {
    if (generateInputsDirty.value) {
      return "生成材料已更新，请重新生成 PRD；评审和 Demo 仍可使用当前 PRD。";
    }
    return "添加材料后即可开始生成 PRD，也可切换到评审、Demo 或从左侧打开需求。";
  }
  if (mode.value === "REVIEW") {
    if (hasActivePrd.value) {
      return `已带入「${activePrdTitle.value}」，添加补充材料后即可开始评审。`;
    }
    if (visibleSourceList.value.length) {
      return "已准备 PRD 评审材料，点击开始评审；也可添加补充材料。";
    }
    return "添加已有 PRD 文档（.docx / .md / .txt 等）后即可直接开始评审，无需先生成 PRD。";
  }
  if (mode.value === "DEMO") {
    if (hasActivePrd.value) {
      return `已带入「${activePrdTitle.value}」，选择风格后即可生成 Demo。`;
    }
    if (visibleSourceList.value.length) {
      return "已准备 PRD 材料，选择风格后即可开始生成 Demo；也可添加补充材料。";
    }
    return "添加已有 PRD 文档（.docx / .md / .txt 等）后即可直接生成 Demo 原型，无需先生成 PRD。";
  }
  if (mode.value === "POC") {
    if (hasActivePrd.value) {
      return `已带入「${activePrdTitle.value}」，可生成静态多文件 POC，耗时取决于模型和材料规模。`;
    }
    if (visibleSourceList.value.length) {
      return "已准备 PRD 材料，可开始生成 POC 演示系统；也可添加补充材料。";
    }
    return "添加已有 PRD 文档后即可生成静态多文件 POC 演示系统，无需先生成 PRD。";
  }
  if (mode.value === "ESTIMATE") {
    if (visibleSourceList.value.length) {
      return "已准备评估材料（需求清单或 PRD），点击开始工作量评估。";
    }
    return "添加需求清单或 PRD 材料后即可开始工作量评估，支持单 Sheet Excel 预览与导出。";
  }
  if (mode.value === "TEST_CASE") {
    if (visibleSourceList.value.length) {
      return "已准备测试材料（PRD 或需求文档），点击开始生成测试用例。";
    }
    return "添加 PRD 或需求文档材料后即可开始生成测试用例，支持多维筛选与专业 Excel 导出。";
  }
  if (mode.value === "TECH_SPEC") {
    if (visibleSourceList.value.length) {
      return "已准备技术方案材料（PRD 或需求文档），点击开始生成可交付前后端的详细技术方案。";
    }
    return "添加 PRD 或需求文档后即可开始生成技术方案，包含分层架构、表结构与 API 契约。";
  }
  return "添加材料后即可开始运行。";
});
const runConfirmTitle = computed(() => {
  if (mode.value === "GENERATE") return revising.value ? "确认修订当前稿？" : "确认开始生成 PRD？";
  if (mode.value === "DEMO") return "确认开始生成 Demo？";
  if (mode.value === "POC") return "确认开始生成 POC 演示系统？耗时取决于模型和材料规模。";
  if (mode.value === "ESTIMATE") return "确认开始工作量评估？";
  if (mode.value === "TEST_CASE") return "确认开始生成测试用例？";
  if (mode.value === "TECH_SPEC") return "确认开始生成详细技术方案？";
  return "确认开始评审？";
});
const runConfirmAction = computed(() => {
  if (mode.value === "GENERATE") return revising.value ? "确认修订" : "确认生成";
  if (mode.value === "DEMO") return "确认生成 Demo";
  if (mode.value === "POC") return "确认生成 POC";
  if (mode.value === "ESTIMATE") return "确认评估";
  if (mode.value === "TEST_CASE") return "确认生成用例";
  if (mode.value === "TECH_SPEC") return "确认生成方案";
  return "确认评审";
});
const filteredHistory = computed(() =>
  historyFilter.value === "ALL" ? history.value : history.value.filter((item) => item.mode === historyFilter.value)
);
const historyEmptyLabel = computed(() => {
  if (historyFilter.value === "REVIEW") return "评审记录";
  if (historyFilter.value === "DEMO") return "Demo 记录";
  if (historyFilter.value === "POC") return "POC 记录";
  if (historyFilter.value === "GENERATE") return "生成记录";
  if (historyFilter.value === "ESTIMATE") return "评估记录";
  if (historyFilter.value === "TEST_CASE") return "用例记录";
  if (historyFilter.value === "TECH_SPEC") return "方案记录";
  return "记录";
});
const progressTitle = computed(() => {
  if (mode.value === "GENERATE") return "正在生成 PRD";
  if (mode.value === "DEMO") return "正在生成 Demo";
  if (mode.value === "POC") return "正在生成 POC 演示系统";
  if (mode.value === "ESTIMATE") return "正在进行工作量评估";
  if (mode.value === "TEST_CASE") return "正在生成测试用例";
  if (mode.value === "TECH_SPEC") return "正在生成详细技术方案";
  return "正在评审";
});
const analyzingLabel = computed(() => {
  if (mode.value === "GENERATE") return "组织文档";
  if (mode.value === "DEMO") return "设计页面";
  if (mode.value === "POC") return "仍在写文件";
  if (mode.value === "ESTIMATE") return "拆解功能点";
  if (mode.value === "TEST_CASE") return "设计用例集合";
  if (mode.value === "TECH_SPEC") return "规划分层架构与契约";
  return "分析问题";
});
const otherBusyHint = computed(() => {
  if (isActive(runsByMode.value.GENERATE)) return "生成正在进行，完成后可在此查看或发起评审、Demo、工作量评估、测试用例。";
  if (isActive(runsByMode.value.REVIEW)) return "评审正在进行，完成后可在此查看或发起其他操作。";
  if (isActive(runsByMode.value.DEMO)) return "Demo 正在生成，完成后可在此查看或发起其他操作。";
  if (isActive(runsByMode.value.POC)) return "POC 演示系统仍在写文件，可能需要十几到几十分钟，完成后可在此预览或导出 ZIP。";
  if (isActive(runsByMode.value.ESTIMATE)) return "工作量评估正在进行，完成后可在此查看或导出。";
  if (isActive(runsByMode.value.TEST_CASE)) return "测试用例生成正在进行，完成后可在此查看或导出。";
  return "技术方案生成正在进行，完成后可在此查看。";
});
const stageIndex = computed(() => {
  return runStageIndex(run.value?.stage);
});
const progressWidth = computed(() => `${Math.min(92, 18 + stageIndex.value * 34)}%`);
const elapsedLabel = computed(() => {
  nowTick.value;
  const started = Date.parse(String(run.value?.createdAt || ""));
  return formatElapsed(started, Date.now());
});
const runButtonLabel = computed(() => {
  const spec = completenessName(
    actionCompleteness(completeness.value, run.value?.completeness, isActive(run.value))
  );
  if (starting.value || isActive(run.value)) {
    return `${runningVerb()}中 · ${spec} · ${stageLabel(run.value?.stage)}`;
  }
  if (otherBusy.value) {
    if (isActive(runsByMode.value.GENERATE)) return "生成进行中";
    if (isActive(runsByMode.value.REVIEW)) return "评审进行中";
    if (isActive(runsByMode.value.DEMO)) return "Demo 进行中";
    if (isActive(runsByMode.value.POC)) return "POC 进行中";
    if (isActive(runsByMode.value.ESTIMATE)) return "评估进行中";
    if (isActive(runsByMode.value.TEST_CASE)) return "用例生成进行中";
    return "方案生成进行中";
  }
  if (mode.value === "GENERATE") return revising.value ? `修订当前稿 · ${spec}` : `开始生成 · ${spec}`;
  if (mode.value === "DEMO") return `开始生成 Demo · ${spec}`;
  if (mode.value === "POC") return `开始生成 POC · ${spec}`;
  if (mode.value === "ESTIMATE") return `开始工作量评估 · ${spec}`;
  if (mode.value === "TEST_CASE") return `开始生成测试用例 · ${spec}`;
  if (mode.value === "TECH_SPEC") return `开始生成技术方案 · ${spec}`;
  return `开始评审 · ${spec}`;
});

onMounted(async () => {
  updateLogSeen.value = localStorage.getItem(updateLogSeenKey(user.value?.id || user.value?.username)) === CURRENT_UPDATE_LOG.version;
  if (!updateLogSeen.value) {
    updateLogOpen.value = true;
  }
  const savedMode = localStorage.getItem(storageKey("mode"));
  mode.value = savedMode === "REVIEW" || savedMode === "DEMO" || savedMode === "POC" || savedMode === "ESTIMATE" || savedMode === "TEST_CASE" || savedMode === "TECH_SPEC" ? savedMode : "GENERATE";
  historyFilter.value = "ALL";
  window.addEventListener("keydown", onGlobalKeydown);
  window.addEventListener("pointerdown", onDocPointerDown);
  window.addEventListener("resize", onTaskMenuReposition);
  window.addEventListener("resize", onEditorResize);
  window.addEventListener("beforeunload", onBeforeUnload);
  document.addEventListener("fullscreenchange", onFullscreenChange);
  document.addEventListener("webkitfullscreenchange", onFullscreenChange);
  await refreshHealth();
  healthTimer = window.setInterval(() => {
    void refreshHealth();
  }, 10000);
  await loadDemoSkills();
  await loadQualityOptions();
  await refreshTasks();
  const saved = localStorage.getItem(storageKey("task-id"));
  if (saved) {
    try {
      await loadTask(saved);
    } catch {
      localStorage.removeItem(storageKey("task-id"));
    }
  } else if (tasks.value.length) {
    await loadTask(tasks.value[0].id);
  }
  await refreshHistory();
  await hydrateRuns();
  await ensureActivePrdRunId();
  await ensureReviewMaterial();
  await loadCurrentDraft();
  if (runsByMode.value.REVIEW?.status === "SUCCEEDED") {
    await loadFeedback(runsByMode.value.REVIEW.id);
  }
  void discoverActiveRuns();
});

async function discoverActiveRuns() {
  try {
    const allRecentRuns = await listRuns();
    for (const item of allRecentRuns) {
      if (item.status === "PENDING" || item.status === "RUNNING") {
        taskRunningMap.value = updateTaskRunning(taskRunningMap.value, item.taskId, {
          runId: item.id,
          mode: item.mode,
          status: item.status,
          taskTitle: item.taskTitle,
        });
        watchRun(item.id, item.taskId);
      }
    }
  } catch {
    // Non-blocking background discovery
  }
}

onUnmounted(() => {
  window.removeEventListener("keydown", onGlobalKeydown);
  window.removeEventListener("pointerdown", onDocPointerDown);
  window.removeEventListener("resize", onTaskMenuReposition);
  window.removeEventListener("resize", onEditorResize);
  window.removeEventListener("beforeunload", onBeforeUnload);
  document.removeEventListener("fullscreenchange", onFullscreenChange);
  document.removeEventListener("webkitfullscreenchange", onFullscreenChange);
  document.body.classList.remove("modal-open");
  runWatchers.forEach((intervalId) => window.clearInterval(intervalId));
  runWatchers.clear();
  if (healthTimer) window.clearInterval(healthTimer);
  if (renameTimer) window.clearTimeout(renameTimer);
  if (toastTimer) window.clearTimeout(toastTimer);
  if (elapsedTimer) window.clearInterval(elapsedTimer);
  if (freshTimer) window.clearTimeout(freshTimer);
  revokeHtmlPreview();
});

function onEditorResize() {
  anchorDirty = true;
}

watch(mode, (next) => {
  if (next !== "GENERATE") {
    revising.value = false;
  }
});

watch(modeBusy, (active) => {
  if (elapsedTimer) {
    window.clearInterval(elapsedTimer);
    elapsedTimer = undefined;
  }
  if (active) {
    nowTick.value = Date.now();
    elapsedTimer = window.setInterval(() => {
      nowTick.value = Date.now();
    }, 1000);
  }
});

watch(draftEditor, () => {
  anchorDirty = true;
  void nextTick(() => {
    refreshEditorScrollAnchors();
    syncEditorScrollElements(editorTextareaEl.value, editorPreviewEl.value);
  });
});

watch(editorSplitMode, (mode) => {
  if (mode === "split") {
    anchorDirty = true;
    void nextTick(() => {
      refreshEditorScrollAnchors();
      syncEditorScrollElements(editorTextareaEl.value, editorPreviewEl.value);
    });
  }
});

watch(title, () => {
  if (renameTimer) window.clearTimeout(renameTimer);
  renameTimer = window.setTimeout(() => {
    void persistTitle();
  }, 800);
  if (!taskMenuOpen.value) {
    taskQuery.value = title.value;
  }
});

watch(taskId, () => {
  taskQuery.value = title.value;
});

watch(
  [() => run.value?.id || "", originalDemoHtml],
  ([runId, html]) => {
    patchedDemoHtml.value = "";
    demoPatchFrom.value = "";
    demoPatchTo.value = "";
    demoPatchMessage.value = "";
    demoPatches.value = [];
    demoResultTab.value = "PREVIEW";
    selectedPackagePath.value = "";
    packageCopyMessage.value = "";
    if ((mode.value !== "DEMO" && mode.value !== "POC") || !runId || !html) return;
    const stored = localStorage.getItem(demoPatchStorageKey(runId));
    if (!stored) return;
    try {
      const parsed = JSON.parse(stored);
      if (!Array.isArray(parsed) || parsed.length > 50) throw new Error("invalid patches");
      const patches = parsed.filter(
        (item): item is DemoTextPatch =>
          Boolean(item && typeof item.from === "string" && item.from && typeof item.to === "string" && item.to)
      );
      if (patches.length !== parsed.length) throw new Error("invalid patch item");
      const replayed = replayDemoTextPatches(html, patches);
      if (!replayed.valid) throw new Error("stale patches");
      demoPatches.value = patches;
      patchedDemoHtml.value = replayed.html;
      if (patches.length) demoPatchMessage.value = `已恢复 ${patches.length} 项本地文案调整。`;
    } catch {
      localStorage.removeItem(demoPatchStorageKey(runId));
    }
  },
  { immediate: true }
);

watch(
  demoPreviewSourceHtml,
  (html) => {
    revokeHtmlPreview();
    demoPreviewState.value = initialDemoPreviewState(html);
    demoPreviewUrl.value = createDemoPreviewUrl(html);
    htmlPreviewUrl = demoPreviewUrl.value || undefined;
  }
);

watch(demoPackageFiles, (files) => {
  if (!files.some((file) => file.path === selectedPackagePath.value)) {
    selectedPackagePath.value = files[0]?.path || "";
  }
  packageCopyMessage.value = "";
});

watch(prdPackFiles, (files) => {
  if (!files.length) {
    prdResultTab.value = "ASSEMBLED";
  }
  if (!files.some((file) => file.path === selectedPrdPath.value)) {
    selectedPrdPath.value = files[0]?.path || "";
  }
});

function showToast(message: string, duration = 2800) {
  toastMessage.value = message;
  if (toastTimer) window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => {
    toastMessage.value = "";
  }, duration);
}

function openUpdateLog() {
  updateLogOpen.value = true;
  updateLogSeen.value = true;
  localStorage.setItem(updateLogSeenKey(user.value?.id || user.value?.username), CURRENT_UPDATE_LOG.version);
  document.body.classList.add("modal-open");
}

function closeUpdateLog() {
  updateLogOpen.value = false;
  document.body.classList.remove("modal-open");
}

function openAdminDashboard() {
  userMenuOpen.value = false;
  router.push("/admin");
}

function demoPatchStorageKey(runId: string) {
  return storageKey(`demo-patches:${runId}`);
}

function persistDemoPatches() {
  const runId = run.value?.mode === "DEMO" || run.value?.mode === "POC" ? run.value.id : "";
  if (!runId) return;
  try {
    localStorage.setItem(demoPatchStorageKey(runId), JSON.stringify(demoPatches.value));
  } catch {
    demoPatchMessage.value = "本地保存失败，请减少调整内容后重试。";
  }
}

function clearDraftState() {
  draft.value = null;
  draftEditor.value = "";
  draftEditing.value = false;
  draftConflict.value = false;
}

function clearFeedbackState() {
  feedback.value = null;
  feedbackDrafts.value = {};
  feedbackSavingPath.value = "";
  feedbackConflict.value = false;
  feedbackLoadError.value = "";
}

async function loadCurrentDraft() {
  const source = currentDraftSource.value;
  if (!taskId.value || !source || !activePrdRunId.value) {
    clearDraftState();
    return;
  }
  try {
    const loaded = await getDraftSource(taskId.value, source.id);
    draft.value = loaded;
    draftEditor.value = loaded.content;
    draftConflict.value = false;
    await resolveActivePrdRun();
  } catch (error) {
    clearDraftState();
    errorMessage.value = apiErrorMessage(error);
  }
}

function beginDraftEdit() {
  if (!showingCurrentDraft.value || busy.value) return;
  draftEditing.value = true;
}

function cancelDraftEdit() {
  if (!draft.value) return;
  if (draftDirty.value && !window.confirm("放弃尚未保存的当前稿修改？")) return;
  draftEditor.value = draft.value.content;
  draftEditing.value = false;
  draftConflict.value = false;
}

async function saveDraft() {
  if (!draft.value || !taskId.value || draftSaving.value) return false;
  errorMessage.value = "";
  if (!draftEditor.value.trim()) {
    errorMessage.value = "当前稿不能为空";
    return false;
  }
  draftSaving.value = true;
  draftConflict.value = false;
  try {
    const saved = await updateDraftSource(
      taskId.value,
      draft.value.sourceId,
      draftEditor.value,
      draft.value.updatedAt
    );
    draft.value = saved;
    draftEditor.value = saved.content;
    draftEditing.value = false;
    errorMessage.value = "";
    showToast("当前稿已保存");
    return true;
  } catch (error) {
    if (apiErrorCode(error) === "SOURCE_CHANGED") {
      draftConflict.value = true;
      errorMessage.value = "当前稿已在别处变化。你的输入仍保留，请重新加载后再修改。";
    } else {
      errorMessage.value = apiErrorMessage(error);
    }
    return false;
  } finally {
    draftSaving.value = false;
  }
}

async function reloadDraftAfterConflict() {
  if (draftDirty.value && !window.confirm("重新加载会放弃当前输入，是否继续？")) return;
  await loadCurrentDraft();
}

async function guardUnsavedDraft() {
  if (!draftDirty.value) return true;
  if (window.confirm("当前稿有未保存修改。确定将先保存并继续；取消可选择放弃或返回。")) {
    return saveDraft();
  }
  if (!window.confirm("确定放弃未保存修改并继续？取消将留在当前页面。")) return false;
  if (draft.value) draftEditor.value = draft.value.content;
  draftEditing.value = false;
  draftConflict.value = false;
  return true;
}

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (!draftDirty.value) return;
  event.preventDefault();
  event.returnValue = "";
}

async function loadFeedback(runId: string) {
  try {
    const loaded = await getReviewFeedback(runId);
    feedback.value = loaded;
    feedbackDrafts.value = Object.fromEntries(loaded.items.map((item) => [item.itemPath, { ...item }]));
    feedbackConflict.value = false;
    feedbackLoadError.value = "";
  } catch (error) {
    clearFeedbackState();
    feedbackLoadError.value = apiErrorMessage(error);
  }
}

function feedbackAction(path: string): ReviewFeedbackAction | "" {
  return feedbackDrafts.value[path]?.action || "";
}

function feedbackNote(path: string) {
  return feedbackDrafts.value[path]?.note || "";
}

function setFeedbackAction(path: string, action: string) {
  if (!action) return;
  feedbackDrafts.value = {
    ...feedbackDrafts.value,
    [path]: { itemPath: path, action: action as ReviewFeedbackAction, note: feedbackNote(path) || undefined },
  };
}

function setFeedbackNote(path: string, note: string) {
  const action = feedbackAction(path);
  if (!action) return;
  feedbackDrafts.value = {
    ...feedbackDrafts.value,
    [path]: { itemPath: path, action, ...(note ? { note } : {}) },
  };
}

function onFeedbackAction(path: string, event: Event) {
  setFeedbackAction(path, (event.target as HTMLSelectElement).value);
}

function onFeedbackNote(path: string, event: Event) {
  setFeedbackNote(path, (event.target as HTMLInputElement).value);
}

async function saveFeedbackItem(path: string) {
  if (!run.value || !feedback.value || feedbackSavingPath.value) return;
  const item = feedbackDrafts.value[path];
  if (!item) {
    errorMessage.value = "请先选择处理决定";
    return;
  }
  const note = item.note?.trim() || "";
  if ((item.action === "WONT_DO" || item.action === "NEEDS_CUSTOMER") && !note) {
    errorMessage.value = item.action === "WONT_DO" ? "请填写不做原因" : "请填写待确认内容";
    return;
  }
  const next = [
    ...feedback.value.items.filter((saved) => saved.itemPath !== path),
    { itemPath: path, action: item.action, ...(note ? { note } : {}) },
  ];
  feedbackSavingPath.value = path;
  try {
    const saved = await updateReviewFeedback(run.value.id, feedback.value.revision, next);
    feedback.value = saved;
    feedbackConflict.value = false;
    feedbackDrafts.value = {
      ...feedbackDrafts.value,
      [path]: { ...saved.items.find((entry) => entry.itemPath === path)! },
    };
    showToast("处理决定已保存");
  } catch (error) {
    if (apiErrorCode(error) === "FEEDBACK_CHANGED") {
      errorMessage.value = "评审处理记录已变化，请重新加载后再保存。";
      feedbackConflict.value = true;
    } else {
      errorMessage.value = apiErrorMessage(error);
    }
  } finally {
    feedbackSavingPath.value = "";
  }
}

async function reloadFeedbackAfterConflict() {
  if (!run.value || run.value.mode !== "REVIEW") return;
  if (!window.confirm("重新加载会覆盖当前未保存的处理选择，是否继续？")) return;
  await loadFeedback(run.value.id);
  errorMessage.value = "";
}

async function retryLoadFeedback() {
  if (!run.value || run.value.mode !== "REVIEW") return;
  await loadFeedback(run.value.id);
}

function closeActionMenus() {
  document.querySelectorAll("details.action-menu[open]").forEach((el) => {
    (el as HTMLDetailsElement).open = false;
  });
}

function onGlobalKeydown(event: KeyboardEvent) {
  if (event.key !== "Escape") {
    return;
  }
  const openActionMenus = document.querySelectorAll("details.action-menu[open]");
  if (openActionMenus.length > 0) {
    closeActionMenus();
    return;
  }
  if (userMenuOpen.value) {
    userMenuOpen.value = false;
    return;
  }
  if (isPresentationMode.value) {
    isPresentationMode.value = false;
    return;
  }
  if (customizerModalOpen.value) {
    customizerModalOpen.value = false;
    return;
  }
  if (estimateColumnsDrawerOpen.value) {
    estimateColumnsDrawerOpen.value = false;
    return;
  }
  if (testCaseColumnsDrawerOpen.value) {
    testCaseColumnsDrawerOpen.value = false;
    return;
  }
  if (taskMenuOpen.value) {
    closeTaskMenu();
    return;
  }
  if (runConfirmOpen.value) {
    closeRunConfirm();
    return;
  }
  if (prdPickerOpen.value) {
    closePrdPicker();
    return;
  }
  if (replacementOpen.value) {
    closeReplacement();
    return;
  }
  if (uploadOpen.value) {
    closeUpload();
    return;
  }
  if (newTaskOpen.value) {
    closeNewTask();
    return;
  }
  if (updateLogOpen.value) {
    closeUpdateLog();
    return;
  }
  if (contextOpen.value) {
    closeContext();
  }
}

function onDocPointerDown(event: PointerEvent) {
  const target = event.target as Element | null;
  if (!target?.closest("details.action-menu")) {
    closeActionMenus();
  }
  if (userMenuOpen.value && !target?.closest(".user-profile-menu-wrapper")) {
    userMenuOpen.value = false;
  }
  if (!taskMenuOpen.value) {
    return;
  }
  if (taskPickerEl.value?.contains(target)) {
    return;
  }
  const menu = document.querySelector(".task-menu");
  if (menu?.contains(target)) {
    return;
  }
  closeTaskMenu();
}

function updateTaskMenuPosition() {
  const el = taskSearchInput.value;
  if (!el) {
    return;
  }
  const rect = el.getBoundingClientRect();
  taskMenuStyle.value = {
    position: "fixed",
    top: `${Math.round(rect.bottom + 4)}px`,
    left: `${Math.round(rect.left)}px`,
    width: `${Math.round(rect.width)}px`,
    zIndex: "80",
  };
}

function onTaskMenuReposition() {
  if (taskMenuOpen.value) {
    updateTaskMenuPosition();
  }
}

function openTaskMenu() {
  if (busy.value) {
    return;
  }
  taskMenuOpen.value = true;
  nextTick(updateTaskMenuPosition);
}

function onTaskSearchFocus() {
  openTaskMenu();
  nextTick(() => taskSearchInput.value?.select());
}

function closeTaskMenu() {
  taskMenuOpen.value = false;
  taskQuery.value = title.value;
}

function onHistoryScroll() {
  if (taskMenuOpen.value) {
    closeTaskMenu();
  }
}

async function pickTask(id: string) {
  closeTaskMenu();
  await selectTask(id);
}

function pickFirstTask() {
  const q = taskQuery.value.trim();
  if (!q || q === title.value.trim()) {
    closeTaskMenu();
    return;
  }
  const first = filteredTasks.value[0];
  if (first) {
    void pickTask(first.id);
  }
}

function isActive(value: Run | null | undefined) {
  return value?.status === "PENDING" || value?.status === "RUNNING";
}

function runningVerb() {
  if (mode.value === "GENERATE") return "生成";
  if (mode.value === "DEMO") return "生成 Demo";
  if (mode.value === "POC") return "生成 POC";
  if (mode.value === "ESTIMATE") return "评估";
  if (mode.value === "TEST_CASE") return "生成用例";
  if (mode.value === "TECH_SPEC") return "生成方案";
  return "评审";
}

function modeLabel(value: Mode) {
  if (value === "REVIEW") return "评审";
  if (value === "DEMO") return "Demo";
  if (value === "POC") return "POC";
  if (value === "ESTIMATE") return "评估";
  if (value === "TEST_CASE") return "用例";
  if (value === "TECH_SPEC") return "方案";
  return "生成";
}

function readSize(key: string, fallback: number) {
  const raw = Number(localStorage.getItem(storageKey(key)));
  return Number.isFinite(raw) && raw > 0 ? raw : fallback;
}

function sourceRoleLabel(name: string) {
  if (isDraftSource(name)) {
    return usesExistingPrd.value ? "当前 PRD" : "生成稿";
  }
  if (name.startsWith("演示补充-")) return "演示补充";
  if (name.startsWith("POC补充-")) return "POC 补充";
  if (name.startsWith("评审补充-")) return "评审补充";
  if (name.startsWith("评估补充-")) return "评估补充";
  if (name.startsWith("用例补充-")) return "用例补充";
  if (name.startsWith("方案补充-")) return "方案补充";
  return "材料";
}

function sourceTooltip(source: Source) {
  const runId = draftRunId(source.name);
  if (runId && draftTitles.value[runId]) {
    return `生成稿 · ${draftTitles.value[runId]}`;
  }
  return source.name;
}

function displayName(name: string) {
  return displaySourceName(name, draftTitles.value);
}

function closeContext() {
  contextOpen.value = false;
}

function onFullscreenChange() {
  const fsEl = document.fullscreenElement || (document as any).webkitFullscreenElement;
  resultFullscreen.value = fsEl === resultCanvasEl.value;
}

async function toggleResultFullscreen() {
  const canvas = resultCanvasEl.value;
  if (!canvas) return;
  try {
    const fsEl = document.fullscreenElement || (document as any).webkitFullscreenElement;
    if (fsEl) {
      if (document.exitFullscreen) {
        await document.exitFullscreen();
      } else if ((document as any).webkitExitFullscreen) {
        await (document as any).webkitExitFullscreen();
      }
    } else {
      if (canvas.requestFullscreen) {
        await canvas.requestFullscreen();
      } else if ((canvas as any).webkitRequestFullscreen) {
        await (canvas as any).webkitRequestFullscreen();
      }
    }
  } catch {
    errorMessage.value = "当前浏览器不支持全屏查看";
  }
}

function startDragContext(event: PointerEvent) {
  const startX = event.clientX;
  const startW = contextWidth.value;
  const move = (ev: PointerEvent) => {
    contextWidth.value = Math.min(520, Math.max(300, startW - (ev.clientX - startX)));
  };
  const up = () => {
    window.removeEventListener("pointermove", move);
    window.removeEventListener("pointerup", up);
    localStorage.setItem(storageKey("context-w"), String(contextWidth.value));
  };
  window.addEventListener("pointermove", move);
  window.addEventListener("pointerup", up);
}

async function setMode(next: Mode): Promise<boolean> {
  if (next === mode.value) return true;
  if (!(await guardUnsavedDraft())) return false;
  mode.value = next;
  errorMessage.value = "";
  localStorage.setItem(storageKey("mode"), next);
  trackFeature(next, "SWITCH_MODE", { taskId: taskId.value });
  if (
    next === "REVIEW" ||
    next === "DEMO" ||
    next === "POC" ||
    next === "ESTIMATE" ||
    next === "TEST_CASE" ||
    next === "TECH_SPEC"
  ) {
    await ensureActivePrdRunId();
    await ensureReviewMaterial();
    await resolveActivePrdRun();
  }
  return true;
}

function toggleModeLens(id: string) {
  lensesByMode.value = {
    ...lensesByMode.value,
    [mode.value]: applyExtraLens(qualityCatalog.value, mode.value, selectedLenses.value, id),
  };
}

function isLensActive(id: string, isDefault?: boolean) {
  const current = selectedLenses.value;
  if (mode.value === "GENERATE") {
    if (isDefault) {
      return !extraModeLenses.value.some((extra) => current.includes(extra.id));
    }
    return current.includes(id);
  }
  if (isDefault) {
    return true;
  }
  return current.includes(id);
}

function handleLensSelect(id: string, isDefault?: boolean) {
  if (busy.value) return;
  if (mode.value === "GENERATE") {
    if (isDefault) {
      lensesByMode.value = {
        ...lensesByMode.value,
        [mode.value]: defaultLenses(qualityCatalog.value, mode.value),
      };
    } else {
      toggleModeLens(id);
    }
  } else {
    if (!isDefault) {
      toggleModeLens(id);
    }
  }
}

function isQuestionSelected(item: Record<string, unknown>) {
  const text = questionText(item);
  return selectedQuestions.value.includes(text);
}

function toggleQuestion(item: Record<string, unknown>) {
  const text = questionText(item);
  if (!text) return;
  selectedQuestions.value = toggleLens(selectedQuestions.value, text);
}

function selectedQuestionPrompt() {
  const selected = selectedQuestions.value.filter(Boolean);
  if (!selected.length) {
    return "";
  }
  return `请回应以下待确认问题（能根据原材料拍板的写成明确假设，不能拍板的保留待确认）：\n- ${selected.join("\n- ")}`;
}

async function refreshHealth() {
  const previous = health.value;
  try {
    health.value = (await getHealth()).status;
    if (shouldResumeWatchersAfterHealthRecovery(previous, health.value)) {
      void discoverActiveRuns();
      void refreshDisplayedActiveRuns();
    }
  } catch {
    health.value = "DOWN";
  }
}

async function refreshDisplayedActiveRuns() {
  const entries = Object.entries(runsByMode.value) as Array<[Mode, Run | null]>;
  for (const [slot, item] of entries) {
    if (!item || (item.status !== "PENDING" && item.status !== "RUNNING")) {
      continue;
    }
    try {
      const latest = await getRun(item.id);
      runsByMode.value = { ...runsByMode.value, [slot]: latest };
      if (latest.status === "PENDING" || latest.status === "RUNNING") {
        watchRun(latest.id, taskId.value);
      } else {
        taskRunningMap.value = removeTaskRunning(taskRunningMap.value, taskId.value);
        starting.value = false;
      }
    } catch {
      if (!runWatchers.has(item.id)) {
        watchRun(item.id, taskId.value);
      }
    }
  }
}

async function loadDemoSkills() {
  try {
    demoSkills.value = await listDemoSkills();
    if (!demoSkills.value.some((item) => item.id === demoSkill.value)) {
      demoSkill.value = demoSkills.value[0]?.id || "generic-admin-html";
    }
  } catch {
    demoSkills.value = [{ id: "generic-admin-html", name: "通用 HTML 交互原型", description: "自包含 HTML 原型，不依赖私有宿主或构建工具" }];
  }
}

async function loadQualityOptions() {
  try {
    qualityCatalog.value = await listQualityOptions();
  } catch {
    qualityCatalog.value = FALLBACK_QUALITY;
  }
  lensesByMode.value = {
    GENERATE: defaultLenses(qualityCatalog.value, "GENERATE"),
    REVIEW: defaultLenses(qualityCatalog.value, "REVIEW"),
    DEMO: defaultLenses(qualityCatalog.value, "DEMO"),
    POC: defaultLenses(qualityCatalog.value, "POC"),
    ESTIMATE: defaultLenses(qualityCatalog.value, "ESTIMATE"),
    TEST_CASE: defaultLenses(qualityCatalog.value, "TEST_CASE"),
    TECH_SPEC: defaultLenses(qualityCatalog.value, "TECH_SPEC"),
  };
}

async function refreshTasks() {
  try {
    const myTasks = await listTasks();
    const merged: (TaskSummary & { isSharedReview?: boolean })[] = [...myTasks];
    try {
      const reviewResp = await fetchReviewTasks();
      if (reviewResp && reviewResp.items) {
        for (const item of reviewResp.items) {
          if (!merged.some((t) => t.id === item.taskId)) {
            merged.push({
              id: item.taskId,
              title: item.title,
              createdAt: item.createdAt,
              isSharedReview: true,
            });
          }
        }
      }
    } catch {
      // ignore review tasks fetch failure
    }
    tasks.value = merged;
  } catch {
    tasks.value = [];
  }
}

function openOnlineReview(targetTaskId: string) {
  router.push(`/reviews/${targetTaskId}`);
}

async function refreshHistory() {
  if (!taskId.value) {
    history.value = [];
    return;
  }
  try {
    history.value = await listRuns(undefined, taskId.value);
  } catch {
    history.value = [];
  }
}

async function hydrateRuns() {
  runsByMode.value = emptyRuns();
  for (const item of modes) {
    const latest = history.value.find((entry) => entry.mode === item);
    if (latest) {
      await loadRun(latest.id, latest.mode, false);
    }
  }
  const savedRun = localStorage.getItem(storageKey("run-id"));
  if (savedRun) {
    try {
      const latest = await getRun(savedRun);
      if (!taskId.value || latest.taskId === taskId.value) {
        await loadRun(savedRun, latest.mode, false);
      }
    } catch {
      localStorage.removeItem(storageKey("run-id"));
    }
  }
}

async function loadTask(id: string) {
  const task = await getTask(id);
  taskId.value = task.id;
  title.value = task.title;
  savedTitle.value = task.title;
  sources.value = task.sources;
  loadGenerateInputsDirty(task.id);
  loadActivePrdRunId(task.id);
  clearDraftState();
  clearFeedbackState();
  localStorage.setItem(storageKey("task-id"), task.id);
}

async function selectTask(id: string) {
  if (window.matchMedia("(max-width: 768px)").matches) historyOpen.value = false;
  if (id === taskId.value) {
    return;
  }
  if (!(await guardUnsavedDraft())) return;
  errorMessage.value = "";
  await loadTask(id);
  localStorage.removeItem(storageKey("run-id"));
  await refreshHistory();
  await hydrateRuns();
  await loadCurrentDraft();
  if (
    mode.value === "REVIEW" ||
    mode.value === "DEMO" ||
    mode.value === "POC" ||
    mode.value === "ESTIMATE" ||
    mode.value === "TEST_CASE" ||
    mode.value === "TECH_SPEC"
  ) {
    await ensureActivePrdRunId();
    await ensureReviewMaterial();
  }
}

async function openNewTask() {
  if (creatingTask.value) {
    return;
  }
  if (!(await guardUnsavedDraft())) return;
  newTaskTitle.value = "";
  newTaskError.value = "";
  newTaskOpen.value = true;
  document.body.classList.add("modal-open");
  void nextTick(() => newTaskInput.value?.focus());
}

function closeNewTask() {
  newTaskOpen.value = false;
  newTaskError.value = "";
  document.body.classList.remove("modal-open");
}

async function confirmNewTask() {
  const name = newTaskTitle.value.trim();
  if (!name) {
    newTaskError.value = "请输入需求名称。";
    newTaskInput.value?.focus();
    return;
  }
  creatingTask.value = true;
  newTaskError.value = "";
  try {
    const task = await createTask(name);
    closeNewTask();
    taskId.value = task.id;
    title.value = task.title;
    savedTitle.value = task.title;
    sources.value = [];
    runsByMode.value = emptyRuns();
    history.value = [];
    instruction.value = "";
    followUp.value = "";
    errorMessage.value = "";
    generateInputsDirty.value = false;
    activePrdRunId.value = null;
    clearDraftState();
    clearFeedbackState();
    revising.value = false;
    localStorage.setItem(storageKey("task-id"), task.id);
    localStorage.removeItem(storageKey("run-id"));
    localStorage.removeItem(generateDirtyKey(task.id));
    localStorage.removeItem(activePrdKey(task.id));
    await refreshTasks();
    await refreshHistory();
  } catch (error) {
    newTaskError.value = apiErrorMessage(error);
  } finally {
    creatingTask.value = false;
  }
}

async function loadRun(id: string, expectedMode?: Mode, persist = true) {
  const latest = await getRun(id);
  const slot = (expectedMode || latest.mode) as Mode;
  runsByMode.value = { ...runsByMode.value, [slot]: latest };
  if (persist) {
    localStorage.setItem(storageKey("run-id"), latest.id);
  }
  if (isActive(latest)) {
    taskRunningMap.value = updateTaskRunning(taskRunningMap.value, latest.taskId, {
      runId: latest.id,
      mode: latest.mode,
      status: latest.status,
    });
    watchRun(latest.id, latest.taskId);
  }
  if (latest.mode === "REVIEW" && latest.status === "SUCCEEDED") {
    await loadFeedback(latest.id);
  }
}

async function persistTitle() {
  const next = title.value.trim();
  if (!next) {
    title.value = savedTitle.value || "未命名需求";
    return;
  }
  if (renaming || (taskId.value && next === savedTitle.value)) {
    return;
  }
  renaming = true;
  try {
    await ensureTask();
    if (next === savedTitle.value) {
      return;
    }
    const updated = await renameTask(taskId.value, next);
    savedTitle.value = updated.title;
    title.value = updated.title;
    titleSavedHint.value = true;
    window.setTimeout(() => {
      titleSavedHint.value = false;
    }, 1600);
    await refreshTasks();
    await refreshHistory();
  } catch (error) {
    errorMessage.value = apiErrorMessage(error);
  } finally {
    renaming = false;
  }
}

async function ensureTask() {
  if (taskId.value) {
    return;
  }
  const task = await createTask(title.value.trim() || "未命名需求");
  taskId.value = task.id;
  title.value = task.title;
  savedTitle.value = task.title;
  localStorage.setItem(storageKey("task-id"), task.id);
  sources.value = task.sources;
  await refreshTasks();
}

async function openHistory(item: RunHistoryItem) {
  if (!(await guardUnsavedDraft())) return;
  errorMessage.value = "";
  if (item.taskId !== taskId.value) {
    await loadTask(item.taskId);
  }
  await loadRun(item.id, item.mode);
  if (item.mode === "REVIEW" || item.mode === "DEMO" || item.mode === "POC" || item.mode === "ESTIMATE" || item.mode === "TEST_CASE" || item.mode === "TECH_SPEC") {
    const latestGenerate = history.value.find(
      (entry) => entry.mode === "GENERATE" && entry.taskId === item.taskId && entry.status === "SUCCEEDED"
    );
    if (latestGenerate) {
      await loadRun(latestGenerate.id, "GENERATE", false);
    }
  }
  mode.value = item.mode;
  localStorage.setItem(storageKey("mode"), item.mode);
  localStorage.setItem(storageKey("run-id"), item.id);
  if (
    item.mode === "REVIEW" ||
    item.mode === "DEMO" ||
    item.mode === "POC" ||
    item.mode === "ESTIMATE" ||
    item.mode === "TEST_CASE" ||
    item.mode === "TECH_SPEC"
  ) {
    await ensureActivePrdRunId();
    await ensureReviewMaterial();
  }
  await loadCurrentDraft();
}

function openUpload() {
  uploadError.value = "";
  pendingFiles.value = [];
  pastedMaterial.value = "";
  uploadOpen.value = true;
  document.body.classList.add("modal-open");
}

function closeUpload() {
  uploadOpen.value = false;
  pendingFiles.value = [];
  pastedMaterial.value = "";
  uploadError.value = "";
  document.body.classList.remove("modal-open");
}

function onPickFiles(event: Event) {
  const input = event.target as HTMLInputElement;
  const rawFiles = Array.from(input.files || []);
  input.value = "";

  const allowedExts = new Set(["md", "txt", "docx", "xlsx"]);
  const maxSizeBytes = 30 * 1024 * 1024; // 30MB
  const validFiles: File[] = [];
  const errors: string[] = [];

  for (const file of rawFiles) {
    const ext = file.name.split(".").pop()?.toLowerCase() || "";
    if (!allowedExts.has(ext)) {
      errors.push(`文件「${file.name}」格式不支持，仅支持 .md, .txt, .docx, .xlsx`);
      continue;
    }
    if (file.size <= 0) {
      errors.push(`文件「${file.name}」是空文件`);
      continue;
    }
    if (file.size > maxSizeBytes) {
      errors.push(`文件「${file.name}」超过 30MB 大小限制`);
      continue;
    }
    validFiles.push(file);
  }

  if (errors.length) {
    uploadError.value = errors.join("；");
  } else {
    uploadError.value = "";
  }

  pendingFiles.value = [...pendingFiles.value, ...validFiles];
}

async function confirmUpload() {
  uploadError.value = "";
  uploading.value = true;
  const added: Source[] = [];
  try {
    await ensureTask();
    const selected = [...pendingFiles.value];
    const hasDraft = Boolean(activePrdRunId.value || sources.value.some((s) => isDraftSource(s.name)));
    for (const file of selected) {
      const named =
        mode.value === "REVIEW" && hasDraft
          ? new File([file], reviewUploadName(file.name), { type: file.type })
          : mode.value === "DEMO" && hasDraft
            ? new File([file], demoUploadName(file.name), { type: file.type })
            : mode.value === "POC" && hasDraft
              ? new File([file], pocUploadName(file.name), { type: file.type })
            : mode.value === "ESTIMATE" && hasDraft
              ? new File([file], estimateUploadName(file.name), { type: file.type })
              : mode.value === "TEST_CASE" && hasDraft
                ? new File([file], testCaseUploadName(file.name), { type: file.type })
                : mode.value === "TECH_SPEC" && hasDraft
                  ? new File([file], techSpecUploadName(file.name), { type: file.type })
                  : file;
      const source = await uploadSource(taskId.value, named);
      sources.value = [...sources.value, source];
      added.push(source);
      // 成功一个出队一个，避免重试时把已成功的文件重复上传
      pendingFiles.value = pendingFiles.value.filter((f) => f !== file);
    }
    if (pastedMaterial.value.trim()) {
      const textFile = new File([pastedMaterial.value], `粘贴材料-${Date.now()}.txt`, { type: "text/plain" });
      const named =
        mode.value === "REVIEW" && hasDraft
          ? new File([textFile], reviewUploadName(textFile.name), { type: textFile.type })
          : mode.value === "DEMO" && hasDraft
            ? new File([textFile], demoUploadName(textFile.name), { type: textFile.type })
            : mode.value === "POC" && hasDraft
              ? new File([textFile], pocUploadName(textFile.name), { type: textFile.type })
            : textFile;
      const source = await uploadSource(taskId.value, named);
      sources.value = [...sources.value, source];
      added.push(source);
      pastedMaterial.value = "";
    }
    if (mode.value === "GENERATE" && added.length) {
      await invalidateGeneratedDrafts();
    }
    closeUpload();
    markFreshSources(added.map((item) => item.id));
    if (added.length === 1) {
      showToast(`已添加材料：${displayName(added[0].name)}`);
    } else {
      showToast(`已添加 ${added.length} 份材料`);
    }
  } catch (error) {
    uploadError.value = apiErrorMessage(error);
  } finally {
    uploading.value = false;
  }
}

function markFreshSources(ids: string[]) {
  freshSourceIds.value = ids;
  if (freshTimer) window.clearTimeout(freshTimer);
  freshTimer = window.setTimeout(() => {
    freshSourceIds.value = [];
  }, 3200);
}

function generateDirtyKey(id = taskId.value) {
  return storageKey(`gen-dirty:${id}`);
}

function activePrdKey(id = taskId.value) {
  return storageKey(`active-prd:${id}`);
}

function loadGenerateInputsDirty(id = taskId.value) {
  generateInputsDirty.value = Boolean(id && localStorage.getItem(generateDirtyKey(id)) === "1");
}

function persistGenerateInputsDirty(dirty: boolean) {
  generateInputsDirty.value = dirty;
  if (!taskId.value) return;
  if (dirty) {
    localStorage.setItem(generateDirtyKey(), "1");
  } else {
    localStorage.removeItem(generateDirtyKey());
  }
}

function loadActivePrdRunId(id = taskId.value) {
  activePrdRunId.value = id ? localStorage.getItem(activePrdKey(id)) : null;
  activePrdRun.value = null;
}

function persistActivePrdRunId(runId: string | null) {
  if (activePrdRunId.value !== runId) {
    activePrdRun.value = null;
  }
  activePrdRunId.value = runId;
  if (!taskId.value) return;
  if (runId) {
    localStorage.setItem(activePrdKey(), runId);
  } else {
    localStorage.removeItem(activePrdKey());
  }
}

async function ensureActivePrdRunId() {
  if (!taskId.value) return;
  const availableIds = availablePrdRuns.value.map((item) => item.id);
  const draftSources = sources.value.filter((source) => isDraftSource(source.name));
  if (draftSources.length) {
    const recovered = recoverUniqueDraftRunId(
      draftSources.map((source) => draftRunId(source.name)),
      availableIds
    );
    if (recovered) {
      persistActivePrdRunId(recovered);
      return;
    }
    persistActivePrdRunId(null);
    errorMessage.value = "当前材料中的生成稿不唯一或没有成功 Run 支撑，请先清理材料后再选择 PRD。";
    return;
  }
  if (activePrdRunId.value && availableIds.includes(activePrdRunId.value)) return;
  const latest = availablePrdRuns.value[0];
  if (latest) {
    persistActivePrdRunId(latest.id);
  }
}

async function resolveActivePrdRun(): Promise<Run | null> {
  if (!taskId.value) return null;
  await ensureActivePrdRunId();
  const runId = activePrdRunId.value;
  if (!runId) return null;
  if (runsByMode.value.GENERATE?.id === runId && runsByMode.value.GENERATE.status === "SUCCEEDED") {
    activePrdRun.value = runsByMode.value.GENERATE;
    return activePrdRun.value;
  }
  try {
    const loaded = await getRun(runId);
    if (loaded.status === "SUCCEEDED" && loaded.mode === "GENERATE") {
      activePrdRun.value = loaded;
      return loaded;
    }
  } catch {
    return null;
  }
  return null;
}

function openPrdPicker() {
  if (busy.value || availablePrdRuns.value.length < 2) return;
  prdPickerOpen.value = true;
  document.body.classList.add("modal-open");
}

function closePrdPicker() {
  prdPickerOpen.value = false;
  if (!uploadOpen.value && !newTaskOpen.value && !runConfirmOpen.value) {
    document.body.classList.remove("modal-open");
  }
}

async function selectActivePrd(runId: string) {
  if (runId === activePrdRunId.value) {
    closePrdPicker();
    return;
  }
  replacementTargetRunId.value = runId;
  replacementFinalConfirm.value = false;
  replacementOpen.value = true;
  prdPickerOpen.value = false;
  document.body.classList.add("modal-open");
}

function closeReplacement() {
  if (replacing.value) return;
  replacementOpen.value = false;
  replacementTargetRunId.value = "";
  replacementFinalConfirm.value = false;
  if (!uploadOpen.value && !newTaskOpen.value && !runConfirmOpen.value && !prdPickerOpen.value) {
    document.body.classList.remove("modal-open");
  }
}

function exportReplacementDraft() {
  const content = draftDirty.value ? draftEditor.value : String(draft.value?.content || displayedMarkdown.value);
  downloadMarkdown(`${title.value || "PRD"}-当前稿`, content);
  showToast("当前内容已导出");
}

async function confirmReplacement() {
  const runId = replacementTargetRunId.value;
  if (!runId || replacing.value) return;
  if (!replacementFinalConfirm.value) {
    replacementFinalConfirm.value = true;
    return;
  }
  replacing.value = true;
  try {
    await replaceActivePrd(runId);
    replacementOpen.value = false;
    replacementTargetRunId.value = "";
    replacementFinalConfirm.value = false;
    document.body.classList.remove("modal-open");
    showToast("已替换当前 PRD");
  } catch (error) {
    errorMessage.value = apiErrorMessage(error);
  } finally {
    replacing.value = false;
  }
}

async function invalidateGeneratedDrafts(toast?: string) {
  if (!taskId.value) return;
  persistGenerateInputsDirty(true);
  revising.value = false;
  if (toast) {
    showToast(toast);
  }
}

async function removeSource(sourceId: string) {
  const target = sources.value.find((source) => source.id === sourceId);
  if (!target || isDraftSource(target.name)) {
    return;
  }
  try {
    await ensureTask();
    await deleteSource(taskId.value, sourceId);
    sources.value = sources.value.filter((source) => source.id !== sourceId);
    if (mode.value === "GENERATE" && isGenerateSource(target.name)) {
      await invalidateGeneratedDrafts("生成材料已变更，请重新生成 PRD；评审和 Demo 仍可使用当前 PRD。");
    }
  } catch (error) {
    const message = apiErrorMessage(error);
    uploadError.value = message;
    errorMessage.value = message;
  }
}

function watchRun(runId: string, forTaskId?: string) {
  const targetTaskId = forTaskId || taskId.value;
  if (!runId || !targetTaskId) return;
  if (runWatchers.has(runId)) return;

  let consecutiveFailures = 0;

  const stopWatching = (errMsg?: string) => {
    window.clearInterval(intervalId);
    runWatchers.delete(runId);
    taskRunningMap.value = removeTaskRunning(taskRunningMap.value, targetTaskId);
    if (taskId.value === targetTaskId) {
      starting.value = false;
      if (errMsg) {
        errorMessage.value = errMsg;
      }
      const current = Object.values(runsByMode.value).find((item) => item?.id === runId);
      if (current && (current.status === "PENDING" || current.status === "RUNNING")) {
        runsByMode.value = {
          ...runsByMode.value,
          [current.mode]: {
            ...current,
            status: "FAILED",
            error: errMsg || "任务状态获取失败",
            finishedAt: current.finishedAt || new Date().toISOString(),
          },
        };
      }
    } else if (errMsg) {
      const targetTask = tasks.value.find((t) => t.id === targetTaskId);
      const taskName = targetTask?.title || "需求";
      showToast(`需求「${taskName}」${errMsg}`);
    }
  };

  const intervalId = window.setInterval(async () => {
    try {
      const latest = await getRun(runId);
      consecutiveFailures = 0;
      const slot = latest.mode as Mode;

      if (latest.status === "PENDING" || latest.status === "RUNNING") {
        taskRunningMap.value = updateTaskRunning(taskRunningMap.value, targetTaskId, {
          runId: latest.id,
          mode: slot,
          status: latest.status,
        });
      } else {
        taskRunningMap.value = removeTaskRunning(taskRunningMap.value, targetTaskId);
      }

      if (taskId.value === targetTaskId) {
        const currentDisplayedRun = runsByMode.value[slot];
        if (!currentDisplayedRun || currentDisplayedRun.id === latest.id) {
          runsByMode.value = { ...runsByMode.value, [slot]: latest };
        }
      }

      if (latest.status === "SUCCEEDED" || latest.status === "FAILED") {
        window.clearInterval(intervalId);
        runWatchers.delete(runId);

        if (taskId.value === targetTaskId) {
          starting.value = false;
          await refreshHistory();
          if (latest.status === "SUCCEEDED" && latest.mode === "GENERATE") {
            revising.value = false;
            persistGenerateInputsDirty(false);
            if (!currentDraftSource.value || (currentDraftState.value === "ORIGINAL" && !draftDirty.value)) {
              await replaceActivePrd(latest.id);
            } else {
              showToast("新生成稿已完成，当前稿未被替换");
            }
          } else if (latest.status === "SUCCEEDED" && latest.mode === "REVIEW") {
            await loadFeedback(latest.id);
          }
        } else {
          const targetTask = tasks.value.find((t) => t.id === targetTaskId);
          const taskName = targetTask?.title || "需求";
          const verb = runningModeLabel(slot);
          const statusText = latest.status === "SUCCEEDED" ? "已完成" : "运行失败";
          showToast(`需求「${taskName}」的 ${verb} ${statusText}`);
        }
      }
    } catch (error) {
      consecutiveFailures++;
      const message = apiErrorMessage(error);
      if (shouldStopPollingImmediately(error)) {
        stopWatching(`任务状态获取终止：${message}`);
        return;
      }
      if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
        stopWatching(`任务状态获取连续失败，已停止轮询：${message}`);
        return;
      }
      if (taskId.value === targetTaskId) {
        errorMessage.value = message;
      }
    }
  }, 1500);

  runWatchers.set(runId, intervalId);
}

async function startRun() {
  if (busy.value || runConfirmOpen.value) {
    return;
  }
  if (!(await guardUnsavedDraft())) return;
  if (
    mode.value === "REVIEW" ||
    mode.value === "DEMO" ||
    mode.value === "POC" ||
    (mode.value === "GENERATE" && revising.value) ||
    mode.value === "ESTIMATE" ||
    mode.value === "TEST_CASE" ||
    mode.value === "TECH_SPEC"
  ) {
    await ensureActivePrdRunId();
    await ensureReviewMaterial();
  }
  if (!visibleSourceList.value.length) {
    uploadError.value =
      mode.value === "GENERATE"
        ? "请先添加材料，再开始生成。"
        : mode.value === "REVIEW"
          ? "请先添加 PRD 材料，再开始评审。"
          : mode.value === "DEMO"
            ? "请先添加 PRD 材料，再开始生成 Demo。"
            : mode.value === "POC"
              ? "请先添加 PRD 材料，再开始生成 POC。"
            : mode.value === "ESTIMATE"
              ? "请先添加需求清单或 PRD 材料，再开始工作量评估。"
              : mode.value === "TEST_CASE"
                ? "请先添加需求规格或 PRD 材料，再开始生成测试用例。"
                : "请先添加需求规格或 PRD 材料，再开始生成详细技术方案。";
    if (!revising.value) {
      openUpload();
    } else {
      errorMessage.value = uploadError.value;
    }
    return;
  }
  runConfirmOpen.value = true;
  document.body.classList.add("modal-open");
}

function closeRunConfirm() {
  if (starting.value) return;
  runConfirmOpen.value = false;
  if (!uploadOpen.value && !newTaskOpen.value && !prdPickerOpen.value) {
    document.body.classList.remove("modal-open");
  }
}

async function confirmStartRun() {
  if (starting.value || !runConfirmOpen.value) {
    return;
  }
  starting.value = true;
  errorMessage.value = "";
  try {
    await persistTitle();
    await ensureTask();
    const extra = followUp.value.trim();
    const questionNote = revising.value ? selectedQuestionPrompt() : "";
    const prompt = [instruction.value.trim(), extra, questionNote].filter(Boolean).join("\n").slice(0, 4000);
    const created = await createRun(taskId.value, mode.value, prompt, {
      skill: mode.value === "DEMO" ? demoSkill.value : undefined,
      completeness: completeness.value,
      lenses: selectedLenses.value,
      reviseFromRunId: revising.value && mode.value === "GENERATE" ? activePrdRunId.value || undefined : undefined,
    });
    runConfirmOpen.value = false;
    if (!uploadOpen.value && !newTaskOpen.value) {
      document.body.classList.remove("modal-open");
    }
    runsByMode.value = { ...runsByMode.value, [mode.value]: created };
    localStorage.setItem(storageKey("run-id"), created.id);
    taskRunningMap.value = updateTaskRunning(taskRunningMap.value, created.taskId, {
      runId: created.id,
      mode: created.mode,
      status: created.status,
      taskTitle: title.value,
    });
    watchRun(created.id, created.taskId);
    await refreshHistory();
    await refreshTasks();
  } catch (error) {
    errorMessage.value = apiErrorMessage(error);
  } finally {
    starting.value = false;
  }
}

async function startRevise() {
  if (busy.value || !hasGenerateDraft.value) return;
  revising.value = true;
  if (!selectedQuestions.value.length) {
    selectedQuestions.value = openQuestions.value.map((item) => questionText(item)).filter(Boolean);
  }
  await startRun();
}

async function startDemoFromPrd() {
  if (busy.value) return;
  if (!(await setMode("DEMO"))) return;
  await startRun();
}

async function startPocFromPrd() {
  if (busy.value) return;
  if (!(await setMode("POC"))) return;
  await startRun();
}

async function ensureReviewMaterial() {
  if (syncingDraft || !taskId.value) {
    return;
  }
  if (isActive(runsByMode.value.GENERATE) || isActive(runsByMode.value.REVIEW) || isActive(runsByMode.value.DEMO) || isActive(runsByMode.value.POC) || isActive(runsByMode.value.ESTIMATE) || isActive(runsByMode.value.TEST_CASE) || isActive(runsByMode.value.TECH_SPEC)) {
    return;
  }
  await syncDraftFromActivePrd();
}

async function syncDraftFromActivePrd() {
  if (syncingDraft || !taskId.value) {
    return;
  }
  const generateRun = await resolveActivePrdRun();
  if (!generateRun || generateRun.taskId !== taskId.value || generateRun.status !== "SUCCEEDED") {
    return;
  }
  const existing = sources.value.find((source) => source.name === draftSourceName(generateRun.id));
  if (existing) {
    await loadCurrentDraft();
    return;
  }
  try {
    await replaceActivePrd(generateRun.id);
  } catch (error) {
    errorMessage.value = apiErrorMessage(error);
  }
}

async function showActiveDraft() {
  if (!activePrdRunId.value) return;
  await loadRun(activePrdRunId.value, "GENERATE");
  await loadCurrentDraft();
}

async function replaceActivePrd(runId: string) {
  if (syncingDraft || !taskId.value) return;
  const target = await getRun(runId);
  if (target.taskId !== taskId.value || target.mode !== "GENERATE" || target.status !== "SUCCEEDED") {
    throw new Error("所选生成稿不可用");
  }
  const markdown = String(target.result?.markdown || "");
  if (!markdown.trim()) throw new Error("所选生成稿没有正文");
  const name = draftSourceName(target.id);
  const oldDrafts = sources.value.filter((source) => isDraftSource(source.name) && source.name !== name);
  let nextSource = sources.value.find((source) => source.name === name) || null;
  let uploaded: Source | null = null;
  syncingDraft = true;
  try {
    if (!nextSource) {
      uploaded = await uploadSource(taskId.value, new File([markdown], name, { type: "text/markdown" }));
      nextSource = uploaded;
    }
    for (const item of oldDrafts) {
      await deleteSource(taskId.value, item.id);
    }
    sources.value = [
      ...sources.value.filter((source) => !oldDrafts.some((old) => old.id === source.id) && source.id !== nextSource!.id),
      nextSource,
    ];
    persistActivePrdRunId(target.id);
    activePrdRun.value = target;
    draft.value = await getDraftSource(taskId.value, nextSource.id);
    draftEditor.value = draft.value.content;
    draftEditing.value = false;
    draftConflict.value = false;
  } catch (error) {
    if (uploaded) {
      try {
        await deleteSource(taskId.value, uploaded.id);
      } catch {
        // The newly uploaded draft remains visible for manual recovery if rollback fails.
      }
    }
    throw error;
  } finally {
    syncingDraft = false;
  }
}

function exportMarkdown() {
  closeActionMenus();
  if (!displayedMarkdown.value.trim()) {
    showToast("当前暂无 PRD 可导出");
    return;
  }
  downloadMarkdown(String(result.value?.title || title.value || "PRD"), displayedMarkdown.value);
  showToast("PRD Markdown 已导出");
}

function exportHtml() {
  closeActionMenus();
  if (!demoPreviewSourceHtml.value.trim()) {
    showToast(mode.value === "POC" ? "当前暂无 POC HTML 可导出" : "当前暂无 Demo HTML 可导出");
    return;
  }
  downloadHtml(String(result.value?.title || title.value || (mode.value === "POC" ? "POC" : "Demo")), demoPreviewSourceHtml.value);
  showToast(mode.value === "POC" ? "POC HTML 已导出" : "Demo HTML 已导出");
}

async function exportDemoPackage() {
  closeActionMenus();
  if (exportingPackage.value) return;
  if (!demoPackageFiles.value.length) {
    showToast(mode.value === "POC" ? "当前结果没有可导出的源码文件" : "当前结果没有可导出的工程源码");
    return;
  }
  exportingPackage.value = true;
  try {
    const blob = await buildDemoPackageZip(
      String(result.value?.title || title.value || "package"),
      demoPackageFiles.value,
      mode.value === "POC" ? "hermes-poc" : "hermes-demo"
    );
    downloadBlob(`${demoPackageRoot.value}.zip`, blob);
    showToast(mode.value === "POC"
      ? `POC ZIP 已生成（${demoPackageFiles.value.length} 个原始文件）`
      : `定制包 ZIP 已生成（${demoPackageFiles.value.length} 个文件）`);
  } catch (error) {
    errorMessage.value = apiErrorMessage(error);
    showToast(mode.value === "POC" ? "POC ZIP 生成失败" : "定制包 ZIP 生成失败");
  } finally {
    exportingPackage.value = false;
  }
}

async function exportPrdPack() {
  closeActionMenus();
  if (exportingPackage.value) return;
  if (!prdPackFiles.value.length) {
    showToast("当前结果没有可导出的文档包文件");
    return;
  }
  exportingPackage.value = true;
  try {
    const blob = await buildDemoPackageZip(
      String(result.value?.title || title.value || "PRD"),
      prdPackFiles.value,
      "hermes-prd"
    );
    downloadBlob(`${prdPackRoot.value}.zip`, blob);
    showToast(`PRD 文档包 ZIP 已生成（${prdPackFiles.value.length} 个原始文件）`);
  } catch (error) {
    errorMessage.value = apiErrorMessage(error);
    showToast("PRD 文档包 ZIP 生成失败");
  } finally {
    exportingPackage.value = false;
  }
}

async function copyPackageFile() {
  const file = selectedPackageFile.value;
  if (!file) return;
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(file.content);
    } else {
      const textarea = document.createElement("textarea");
      textarea.value = file.content;
      textarea.setAttribute("readonly", "true");
      textarea.style.position = "fixed";
      textarea.style.opacity = "0";
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand("copy");
      textarea.remove();
    }
    packageCopyMessage.value = "已复制";
    window.setTimeout(() => {
      packageCopyMessage.value = "";
    }, 1800);
  } catch {
    packageCopyMessage.value = "复制失败";
  }
}

function applyDemoTextPatch() {
  if (demoPatches.value.length >= MAX_DEMO_TEXT_PATCHES) {
    demoPatchMessage.value = `每个 Demo 最多保留 ${MAX_DEMO_TEXT_PATCHES} 项本地文案调整。`;
    return;
  }
  const result = patchUniqueVisibleText(demoPreviewSourceHtml.value, demoPatchFrom.value, demoPatchTo.value);
  if (result.status === "APPLIED") {
    patchedDemoHtml.value = result.html;
    demoPatches.value = [...demoPatches.value, { from: demoPatchFrom.value, to: demoPatchTo.value }];
    persistDemoPatches();
    demoPatchMessage.value = "已在本地预览中替换，原始结果未改变。";
    return;
  }
  if (result.status === "MULTIPLE") {
    demoPatchMessage.value = `找到 ${result.matches} 处相同文案，请输入唯一文本。`;
  } else if (result.status === "NO_MATCH") {
    demoPatchMessage.value = "未找到唯一可见文本。";
  } else {
    demoPatchMessage.value = "请输入不同的原文和新文案。";
  }
}

function resetDemoPatch() {
  patchedDemoHtml.value = "";
  demoPatches.value = [];
  const runId = run.value?.mode === "DEMO" || run.value?.mode === "POC" ? run.value.id : "";
  if (runId) localStorage.removeItem(demoPatchStorageKey(runId));
  demoPatchMessage.value = mode.value === "POC" ? "已恢复原始 POC。" : "已恢复原始 Demo。";
}

function exportDemoScript() {
  closeActionMenus();
  const script = demoScriptMarkdown.value;
  if (!script.trim()) {
    showToast("当前暂无演示讲解词可导出");
    return;
  }
  downloadMarkdown(`${String(result.value?.title || title.value || "Demo")}-讲解词`, script);
  showToast("讲解词已导出");
}

function revokeHtmlPreview() {
  if (htmlPreviewUrl) {
    URL.revokeObjectURL(htmlPreviewUrl);
    htmlPreviewUrl = undefined;
  }
  demoPreviewUrl.value = "";
}

function markDemoPreviewReady() {
  if (demoPreviewSourceHtml.value.trim()) {
    demoPreviewState.value = "ready";
  }
}

function markDemoPreviewFailed() {
  demoPreviewState.value = "failed";
}

function openLocalHtml() {
  if (demoPreviewUrl.value) {
    window.open(demoPreviewUrl.value, "_blank", "noopener");
    return;
  }
  const url = createDemoPreviewUrl(demoPreviewSourceHtml.value);
  if (!url) {
    errorMessage.value = "当前结果没有可打开的 HTML";
    return;
  }
  htmlPreviewUrl = url;
  demoPreviewUrl.value = url;
  window.open(url, "_blank", "noopener");
}

async function exportDocx() {
  closeActionMenus();
  if (exporting.value) return;
  if (!displayedMarkdown.value.trim()) {
    showToast("当前暂无 PRD 可导出");
    return;
  }
  exporting.value = true;
  try {
    await downloadDocx(String(result.value?.title || title.value || "PRD"), displayedMarkdown.value);
    showToast("PRD Word 文档已导出");
  } catch (error) {
    errorMessage.value = apiErrorMessage(error);
  } finally {
    exporting.value = false;
  }
}

async function confirmCurrent() {
  if (!run.value || busy.value) return;
  try {
    const latest = await confirmRun(run.value.id);
    runsByMode.value = { ...runsByMode.value, [latest.mode as Mode]: latest };
    await refreshHistory();
  } catch (error) {
    errorMessage.value = apiErrorMessage(error);
  }
}

async function logout() {
  if (!(await guardUnsavedDraft())) return;
  await logoutAccount();
  clearSession();
  await router.replace("/login");
}

function openItem(item: Record<string, unknown>) {
  const ids = Array.isArray(item.referenceIds) ? item.referenceIds.map((id) => String(id)) : [];
  const matched = references.value.filter((ref) => ids.includes(String(ref.id || "")));
  contextTitle.value = issueTitle(item);
  if (matched.length) {
    contextBody.value = matched
      .map((ref) => [ref.sourceName, ref.quote, ref.location].filter(Boolean).join("\n"))
      .join("\n\n");
    contextHasQuote.value = true;
    showMissingQuote.value = false;
  } else {
    contextBody.value = String(item.detail || item.suggestion || item.impact || "");
    contextHasQuote.value = false;
    showMissingQuote.value = true;
  }
  contextOpen.value = true;
}

function isContextItem(item: Record<string, unknown>) {
  return contextOpen.value && contextTitle.value === issueTitle(item);
}

function openReference(item: Record<string, unknown>) {
  contextTitle.value = String(item.sourceName || item.id || "引用");
  contextBody.value = String(item.quote || item.location || "");
  contextHasQuote.value = Boolean(item.quote);
  showMissingQuote.value = !contextHasQuote.value;
  contextOpen.value = true;
}

function selectSource(source: Source) {
  contextTitle.value = source.name;
  contextBody.value = "材料已上传，运行时会读取全文。";
  contextHasQuote.value = true;
  showMissingQuote.value = false;
  contextOpen.value = true;
}

function questionText(item: Record<string, unknown>) {
  return String(item.question || item.title || "");
}

function statusLabel(status: string) {
  if (status === "SUCCEEDED") return "已完成";
  if (status === "FAILED") return "失败";
  if (status === "RUNNING") return "进行中";
  return "排队中";
}

function stageLabel(stage: string | null | undefined) {
  if (stage === "ANALYZING") return analyzingLabel.value;
  if (stage === "SUBMITTING") return "返回结果";
  return "读取材料";
}

function stageClass(index: number) {
  if (stageIndex.value > index) return "done";
  if (stageIndex.value === index) return "active";
  return "";
}

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

// 1. TOC 目录大纲平滑定位
function scrollToHeading(id: string) {
  const el = document.getElementById(id);
  if (el) {
    el.scrollIntoView({ behavior: "smooth", block: "start" });
    el.classList.add("toc-flash-highlight");
    window.setTimeout(() => el.classList.remove("toc-flash-highlight"), 1600);
  }
}

function refreshEditorScrollAnchors() {
  if (editorSplitMode.value !== "split") return;
  const textarea = editorTextareaEl.value;
  const preview = editorPreviewEl.value;
  if (!textarea || !preview) return;
  editorScrollAnchors.value = computeScrollAnchors({
    textarea,
    previewEl: preview,
    headings: editorTocList.value,
    markdown: draftEditor.value,
  });
  anchorDirty = false;
}

function syncEditorScroll(source: "editor" | "preview", event: Event) {
  if (activeScrollSource !== null && activeScrollSource !== source) {
    return;
  }
  activeScrollSource = source;
  if (scrollLockTimer) {
    window.clearTimeout(scrollLockTimer);
  }
  scrollLockTimer = window.setTimeout(() => {
    activeScrollSource = null;
  }, 100);

  const sourceEl = event.currentTarget as HTMLElement | null;
  const targetEl = source === "editor" ? editorPreviewEl.value : editorTextareaEl.value;
  if (!sourceEl || !targetEl) return;

  if (anchorDirty || editorScrollAnchors.value.length < 2) {
    refreshEditorScrollAnchors();
  }

  const sourceMax = Math.max(0, sourceEl.scrollHeight - sourceEl.clientHeight);
  const targetMax = Math.max(0, targetEl.scrollHeight - targetEl.clientHeight);
  if (!sourceMax || !targetMax) return;

  const direction = source === "editor" ? "editorToPreview" : "previewToEditor";
  const newScrollTop = interpolateScroll(
    sourceEl.scrollTop,
    editorScrollAnchors.value,
    direction,
    sourceMax,
    targetMax
  );

  editorScrollSyncing.value = true;
  targetEl.scrollTop = newScrollTop;
  requestAnimationFrame(() => {
    editorScrollSyncing.value = false;
  });
}

function syncEditorScrollElements(sourceEl: HTMLElement | null, targetEl: HTMLElement | null) {
  if (editorScrollSyncing.value || !sourceEl || !targetEl) return;
  if (editorSplitMode.value !== "split") return;

  if (anchorDirty || editorScrollAnchors.value.length < 2) {
    refreshEditorScrollAnchors();
  }

  const sourceMax = Math.max(0, sourceEl.scrollHeight - sourceEl.clientHeight);
  const targetMax = Math.max(0, targetEl.scrollHeight - targetEl.clientHeight);
  if (!sourceMax || !targetMax) return;

  const newScrollTop = interpolateScroll(
    sourceEl.scrollTop,
    editorScrollAnchors.value,
    "editorToPreview",
    sourceMax,
    targetMax
  );

  editorScrollSyncing.value = true;
  targetEl.scrollTop = newScrollTop;
  requestAnimationFrame(() => {
    editorScrollSyncing.value = false;
  });
}

function locateEditorHeading(id: string) {
  const heading = editorTocList.value.find((item) => item.id === id);
  const textarea = editorTextareaEl.value;
  if (!heading || !textarea) return;
  const headingIndex = editorTocList.value.findIndex((item) => item.id === id);
  const headingOrdinal = editorTocList.value
    .slice(0, headingIndex)
    .filter((item) => item.level === heading.level && item.raw.trim() === heading.raw.trim()).length;
  const range = findHeadingRange(draftEditor.value, heading, headingOrdinal);
  if (!range) return;

  activeScrollSource = "editor";
  if (scrollLockTimer) window.clearTimeout(scrollLockTimer);
  scrollLockTimer = window.setTimeout(() => {
    activeScrollSource = null;
  }, 250);

  textarea.focus();
  textarea.setSelectionRange(range.start, range.end);
  const maxScroll = Math.max(0, textarea.scrollHeight - textarea.clientHeight);
  const lineHeight = Number.parseFloat(getComputedStyle(textarea).lineHeight) || 24;
  textarea.scrollTop = Math.min(maxScroll, Math.max(0, range.lineIndex * lineHeight - textarea.clientHeight * 0.25));

  const previewHeading = editorPreviewEl.value?.querySelector<HTMLElement>(`#${CSS.escape(id)}`);
  if (previewHeading) {
    previewHeading.scrollIntoView({ behavior: "smooth", block: "start" });
    previewHeading.classList.add("toc-flash-highlight");
    window.setTimeout(() => previewHeading.classList.remove("toc-flash-highlight"), 1600);
  }
}

// 2. 双栏 Markdown 编辑与排版工具条
function insertMarkdown(prefix: string, suffix = "", placeholder = "") {
  const textarea = document.querySelector<HTMLTextAreaElement>(".draft-editor");
  if (!textarea) return;
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;
  const text = textarea.value;
  const selected = text.slice(start, end) || placeholder;
  const replacement = `${prefix}${selected}${suffix}`;
  draftEditor.value = text.slice(0, start) + replacement + text.slice(end);
  nextTick(() => {
    textarea.focus();
    textarea.setSelectionRange(start + prefix.length, start + prefix.length + selected.length);
  });
}

function insertH2() {
  insertMarkdown("\n## ", "\n", "二级标题");
}
function insertH3() {
  insertMarkdown("\n### ", "\n", "三级标题");
}
function insertBold() {
  insertMarkdown("**", "**", "加粗文本");
}
function insertList() {
  insertMarkdown("\n- ", "\n", "列表项");
}
function insertTable() {
  insertMarkdown("\n| 功能模块 | 业务规则 | 验收标准 |\n| --- | --- | --- |\n| 示例模块 | 规则描述 | 验收口径 |\n");
}
function insertCode() {
  insertMarkdown("\n```\n", "\n```\n", "代码或逻辑描述");
}
function insertQuote() {
  insertMarkdown("\n> ", "\n", "引用说明");
}

function onEditorKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "s") {
    e.preventDefault();
    if (draftDirty.value && !draftSaving.value) {
      void saveDraft();
    }
  } else if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "b") {
    e.preventDefault();
    insertBold();
  }
}

// 3. 评审意见与 PRD 正文联动
async function locateIssueInPrd(item: Record<string, unknown>) {
  const titleText = issueTitle(item);
  const keywords = titleText
    .replace(/[^\u4e00-\u9fa5\w]/g, " ")
    .split(/\s+/)
    .filter((w) => w.length >= 2);

  if (!(await setMode("GENERATE"))) return;
  await nextTick();
  if (showingCurrentDraft.value) {
    if (!draftEditing.value) beginDraftEdit();
    await nextTick();
    for (const kw of keywords) {
      const matchHeading = editorTocList.value.find((item) => item.text.includes(kw));
      if (matchHeading) {
        locateEditorHeading(matchHeading.id);
        showToast(`已定位并选中相关标题：${matchHeading.text}`);
        return;
      }
    }
  }
  for (const kw of keywords) {
    const matchHeading = tocList.value.find((t) => t.text.includes(kw));
    if (matchHeading) {
      scrollToHeading(matchHeading.id);
      showToast(`已定位至相关章节：${matchHeading.text}`);
      return;
    }
  }
  showToast(`已切回当前 PRD，可对照修改：${titleText}`);
}

function applyFeedbackToObjective(item: Record<string, unknown>) {
  const titleText = issueTitle(item);
  const prompt = `【针对评审意见修改】${titleText}：请完善具体业务规则与验收标准。`;
  instruction.value = instruction.value ? `${instruction.value}\n${prompt}` : prompt;
  showToast("已将修改要求带入「本次目标」输入框");
}

// 4. 售前 Demo 全屏沉浸演示与现场客制化
function applyCustomerCustomizer() {
  const from = customizerFrom.value.trim();
  const to = customizerTo.value.trim();
  if (!from || !to || from === to) return;

  demoPatchFrom.value = from;
  demoPatchTo.value = to;
  applyDemoTextPatch();
  customizerModalOpen.value = false;
  showToast(`已为「${to}」生成专属客制化文案`);
}

// 5. Stage Stepper 流式推进指示
const stepperStages = RUN_STAGES;
const stepperCurrentIndex = computed(() => runStageIndex(run.value?.stage));
</script>
