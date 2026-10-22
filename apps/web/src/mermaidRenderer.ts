import mermaid from "mermaid";
import { mermaidRenderConfig } from "./mermaidConfig";
import { safeDiagramCode, sanitizeDiagramSvg } from "./mermaidSecurity";

let initialized = false;

export function initMermaid(): void {
  if (initialized || typeof window === "undefined") {
    return;
  }
  mermaid.initialize(mermaidRenderConfig());
  initialized = true;
}

let diagramCounter = 0;

export async function renderMermaidInContainer(container: HTMLElement | null): Promise<void> {
  if (!container || typeof window === "undefined") {
    return;
  }
  initMermaid();

  const allCodes = Array.from(container.querySelectorAll<HTMLElement>("pre > code"));
  const codeBlocks = allCodes.filter((codeEl) => {
    if (codeEl.classList.contains("language-mermaid") || codeEl.classList.contains("lang-mermaid")) {
      return true;
    }
    const text = (codeEl.textContent || "").trim();
    return /^(?:graph\s|flowchart\s|sequenceDiagram|stateDiagram|classDiagram|erDiagram|gantt|pie\s|journey|gitGraph|mindmap|quadrantChart)/m.test(text);
  });
  if (!codeBlocks || codeBlocks.length === 0) {
    return;
  }

  for (const codeEl of Array.from(codeBlocks)) {
    const preEl = codeEl.closest("pre");
    if (!preEl || preEl.classList.contains("mermaid-rendered")) {
      continue;
    }

    const code = (codeEl.textContent || "").trim();
    if (!code) {
      continue;
    }

    const diagramId = `mermaid-chart-${Date.now()}-${++diagramCounter}`;
    try {
      const { svg: rawSvg } = await mermaid.render(diagramId, safeDiagramCode(code));
      const svg = sanitizeDiagramSvg(rawSvg);

      const wrapper = document.createElement("div");
      wrapper.className = "mermaid-diagram-card";
      wrapper.innerHTML = `
        <div class="mermaid-toolbar">
          <span class="mermaid-tag">📊 业务流转 / 架构图</span>
        </div>
        <div class="mermaid-viewport">${svg}</div>
      `;

      preEl.classList.add("mermaid-rendered");
      preEl.parentNode?.replaceChild(wrapper, preEl);
    } catch (err) {
      const errEl = document.getElementById("d" + diagramId) || document.getElementById(diagramId);
      if (errEl) {
        errEl.remove();
      }
      preEl.classList.add("mermaid-rendered", "mermaid-error");
      const notice = document.createElement("div");
      notice.className = "mermaid-error-notice";
      notice.textContent = "⚠️ Mermaid 图表语法未完全闭合，已保留源码视图";
      preEl.parentNode?.insertBefore(notice, preEl);
    }
  }
}
