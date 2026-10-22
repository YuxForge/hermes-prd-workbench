import { createRouter, createWebHistory } from "vue-router";
import { getToken, getUser } from "./session";
import LoginView from "./views/LoginView.vue";
import WorkbenchView from "./views/WorkbenchView.vue";
import AdminAnalyticsView from "./views/AdminAnalyticsView.vue";
import ReviewWorkspaceView from "./views/ReviewWorkspaceView.vue";
import ReviewDocumentView from "./views/ReviewDocumentView.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", name: "login", component: LoginView },
    { path: "/", name: "workbench", component: WorkbenchView, meta: { auth: true } },
    { path: "/admin", name: "admin", component: AdminAnalyticsView, meta: { auth: true, requireAdmin: true } },
    { path: "/reviews", name: "reviews", component: ReviewWorkspaceView, meta: { auth: true } },
    { path: "/reviews/:taskId", name: "review-task", component: ReviewDocumentView, meta: { auth: true } },
    { path: "/reviews/:taskId/documents/:documentId", name: "review-doc", component: ReviewDocumentView, meta: { auth: true } },
  ],
});

router.beforeEach((to) => {
  if (to.meta.auth && !getToken()) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }
  if (to.path === "/login" && getToken()) {
    return { path: "/" };
  }
  if (to.meta.requireAdmin) {
    const user = getUser();
    if (user?.role !== "ADMIN") {
      return { path: "/" };
    }
  }
  return true;
});

export default router;

