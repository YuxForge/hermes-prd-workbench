export type UpdateLogItem = {
  title: string;
  detail: string;
  tone: "accent" | "blue" | "amber" | string;
  type?: "feature" | "improve" | "fix" | "security" | string;
};

export type UpdateLogEntry = {
  id?: string;
  version: string;
  date: string;
  releaseDate?: string;
  title: string;
  summary: string;
  highlights: string[];
  items: UpdateLogItem[];
  isLatest?: boolean;
  status?: string;
};

/** Public release history only; internal customer notes are excluded. */
export const CURRENT_UPDATE_LOG: UpdateLogEntry = {
  version: "V0.5.0-beta.1",
  status: "PREVIEW",
  date: "2026-10-22",
  title: "线上需求评审协同与体验重构重要更新",
  summary: "重大功能发布：全面上线独立“线上评审”协同工作台与快捷入口，支持4级成员角色权限、正文划词精准行号定位、全量 Markdown 富文本与 Mermaid 矢量流程图渲染、微信气泡式沟通流、未读消息与待办自动清零、一键确认极简解决闭环；人员权限新增用户账号与名称模糊搜索联想。",
  highlights: ["线上评审独立工作台", "精准划词行号定位", "微信气泡交流流", "Mermaid流程图渲染", "一键确认极简闭环", "未读与待办自动清零", "人员权限模糊搜索"],
  items: [
    {title:"线上评审独立入口与协同工作台", detail:"顶栏与工作台均提供独立直观的“线上评审”中心与任务快捷入口，支持4级团队权限、多轮次审查基线比对与审计归档导出。", tone:"accent", type:"feature"},
    {title:"划词选区与正文行号精准映射", detail:"重构选区行号计算算法，智能剔除 Markdown 语法标记，基于选区首尾语义片段精确定位正文行号，提问弹窗支持实时 Markdown 富文本片段预览。", tone:"blue", type:"improve"},
    {title:"全量 Markdown 富文本与 Mermaid 流程图渲染", detail:"提问预览、批注抽屉与交流答复全面接入 Markdown 渲染；集成 Mermaid 矢量引擎，时序图与流程图自适应呈现高清 SVG 且支持横向滚动。", tone:"accent", type:"feature"},
    {title:"微信气泡式交流流与极简解决闭环", detail:"问题详情采用沉浸式左右对话气泡，剔除事件噪声；移除繁琐解决申请，责任人答复后提出人一键即可直接确认解决闭环。", tone:"amber", type:"improve"},
    {title:"人员权限账号/名称模糊搜索加人", detail:"项目成员与权限分配弹窗新增支持用户账号、名称的模糊搜索与实时下拉联想选择，彻底告别手动输入用户ID。", tone:"blue", type:"feature"},
    {title:"通知与待办自动清零无感闭环", detail:"查看详情、答复与确认解决时自动标记通知已读，“待我解决”与“待确认”指标精准收敛，提供存量历史通知清洗迁移。", tone:"accent", type:"improve"},
    {title:"数据库物理外键解耦", detail:"遵循高并发数据库规范，全面解耦 Review 模块表间外键依赖，由应用层保障数据生命周期与业务一致性。", tone:"blue", type:"improve"},
  ],
};
export const UPDATE_LOG_HISTORY: UpdateLogEntry[] = [
  {
    version: "V0.4.0-beta.2",
    status: "PREVIEW",
    date: "2026-10-13",
    title: "跨平台与图表修复预览版",
    summary: "预览版仍仅支持全新安装；新增 Windows 11 WSL2 验收，修复 Compose 同源请求的 CORS 403，并恢复流程图、时序图及 Word 导出中的文字。",
    highlights: ["WSL2 验收", "CORS 修复", "Mermaid 文字恢复", "英文开源首页"],
    items: [
      {title:"Compose 同源访问", detail:"Nginx 保留浏览器 Host 端口，WSL2 登录和写操作不再被错误识别为跨域。", tone:"blue", type:"fix"},
      {title:"图表文字", detail:"流程图与时序图使用原生 SVG 文字，严格清理后仍保留节点、角色、消息和备注。", tone:"accent", type:"fix"},
      {title:"导出与展示", detail:"Word 图表导出完成视觉验收，并增加英文 README、合成数据截图和演示 GIF。", tone:"amber", type:"improve"},
    ],
  },
  {
    version: "V0.4.0-beta.1",
    status: "PREVIEW",
    date: "2026-10-13",
    title: "首个开源预览版",
    summary: "全新安装预览版，提供通用模板、可配置模型服务、隔离预览和七种业务模式。",
    highlights: ["通用 HTML 原型", "自托管部署", "隔离预览", "默认关闭行为采集"],
    items: [
      {title:"通用模板", detail:"默认生成自包含 HTML，不依赖私有宿主。", tone:"blue", type:"improve"},
      {title:"隔离预览", detail:"新标签也使用 sandbox iframe，原始 HTML 可下载。", tone:"accent", type:"security"},
      {title:"静态诊断", detail:"生成代码仅做结构和语法检查；完整业务行为仍需浏览器验收。", tone:"amber", type:"security"},
    ],
  },
];
export const UPDATE_LOG_FOOTER = "查看记录仅保存在当前浏览器，不会影响工作内容。";
export function updateLogSeenKey(userId: string | null | undefined) {
  return `prd-workbench-updates-seen:${userId || "anonymous"}`;
}
