<template>
  <div class="login-page">
    <section class="login-card">
      <div class="login-brand">
        <span class="login-brand-icon">
          <Layers :size="20" />
        </span>
        <div class="login-brand-text">
          <strong>Hermes PRD 工作台</strong>
          <span class="login-brand-badge">Hermes Lab</span>
        </div>
      </div>
      <h1>{{ mode === "login" ? "欢迎登录" : "创建账号" }}</h1>
      <p class="login-lead">PRD 生成、智能评审与可交互 Demo 全流程工作台。</p>

      <form @submit.prevent="submit">
        <label class="login-field">
          <span>用户名</span>
          <input
            v-model="username"
            autocomplete="username"
            maxlength="32"
            placeholder="字母、数字或下划线"
            required
          />
        </label>
        <label v-if="mode === 'register'" class="login-field">
          <span>显示名</span>
          <input v-model="displayName" maxlength="64" placeholder="例如：产品负责人" />
        </label>
        <label class="login-field">
          <span>密码</span>
          <div class="login-password">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              maxlength="4096"
              title="密码至少 8 个字符，UTF-8 编码不得超过 72 字节"
              placeholder="至少 8 位"
              required
            />
            <button
              class="password-toggle-btn"
              type="button"
              :title="showPassword ? '隐藏密码' : '显示密码'"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              <EyeOff v-if="showPassword" :size="16" />
              <Eye v-else :size="16" />
            </button>
          </div>
        </label>

        <p v-if="error" class="error-state login-error">{{ error }}</p>

        <button class="run-button login-submit-btn" type="submit" :disabled="submitting">
          {{ submitting ? "正在进入..." : mode === "login" ? "登录工作台" : "注册并立即进入" }}
        </button>
      </form>

      <div class="login-card-footer">
        <button class="switch-mode-btn" type="button" @click="toggle">
          {{ mode === "login" ? "还没有账号？立即免费注册" : "已有账号？直接登录" }}
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { Eye, EyeOff, Layers } from "lucide-vue-next";
import { apiErrorMessage, loginAccount, registerAccount } from "../api";
import { saveSession } from "../session";

const router = useRouter();
const mode = ref<"login" | "register">("login");
const username = ref("");
const displayName = ref("");
const password = ref("");
const showPassword = ref(false);
const error = ref("");
const submitting = ref(false);

function toggle() {
  mode.value = mode.value === "login" ? "register" : "login";
  error.value = "";
}

async function submit() {
  error.value = "";
  submitting.value = true;
  try {
    const payload =
      mode.value === "login"
        ? await loginAccount(username.value.trim(), password.value)
        : await registerAccount(username.value.trim(), password.value, displayName.value.trim());
    saveSession(payload.token, payload.user);
    const rawRedirect = String(router.currentRoute.value.query.redirect || "");
    const safeRedirect =
      rawRedirect.startsWith("/") &&
      !rawRedirect.startsWith("//") &&
      !rawRedirect.startsWith("/login")
        ? rawRedirect
        : "/";
    await router.replace(safeRedirect);
  } catch (err) {
    error.value = apiErrorMessage(err);
  } finally {
    submitting.value = false;
  }
}
</script>
