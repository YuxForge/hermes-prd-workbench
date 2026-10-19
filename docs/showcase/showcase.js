document.addEventListener("DOMContentLoaded", () => {
  // 1. Step Navigation & Scroll Spy
  const stepTabs = document.querySelectorAll(".step-tab");
  const stageSections = document.querySelectorAll(".stage-section");

  stepTabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      const targetId = tab.getAttribute("data-target");
      const targetEl = document.getElementById(targetId);
      if (targetEl) {
        targetEl.scrollIntoView({ behavior: "smooth", block: "start" });
      }
    });
  });

  const observerOptions = {
    root: null,
    rootMargin: "-20% 0px -70% 0px",
    threshold: 0,
  };

  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        const id = entry.target.id;
        stepTabs.forEach((tab) => {
          if (tab.getAttribute("data-target") === id) {
            tab.classList.add("active");
          } else {
            tab.classList.remove("active");
          }
        });
      }
    });
  }, observerOptions);

  stageSections.forEach((section) => observer.observe(section));

  // 2. Source Materials Tabs
  const sourceTabBtns = document.querySelectorAll(".source-tab-btn");
  const sourceContentBox = document.getElementById("source-content-box");

  const sourceContents = {
    source1: `# 设备采购与审批：客户访谈纪要

> 完全虚构的公开演示材料。组织、人物、单据与金额均为合成数据，不对应任何客户。

## 场景与目标
虚构组织「青岚工坊」有三个部门：设计部、研发部、运营部。办公设备采购目前依赖聊天和表格：申请人发需求，主管回复同意，采购员自行下单，仓管再核对到货。月底常常不知道某笔采购是否已经审批、是否收齐。

一期做一个桌面端设备采购工作台，覆盖四个模块：采购申请、审批待办、采购订单、收货登记。

## 角色与流程
- 申请人只看自己的申请，可保存草稿、编辑草稿、提交，查看驳回理由。
- 部门主管只能审核本部门申请，不能审核自己的申请。
- 采购员只能对已批准申请建采购订单，并录入供应商、预计到货日和明细。
- 仓管只登记已下单订单的实际收货数量，可分多次收货，全部收齐后订单完成。

主管在会上说：“金额不超过 5,000 元的申请，我同意就能买；超过 5,000 元还要财务复核。”财务复核角色的归属和自审替代人还没有确定。

## 用于演示的设备
- 显示器：2 台，单价 1,800.00 元。
- 扩展坞：2 个，单价 450.00 元。
- 合计：4,500.00 元。
- 第二笔申请：设计工作站 1 台，单价 8,600.00 元，用于观察高额审批规则。`,

    source2: `# 设备采购与审批：业务规则补充

> 完全虚构的公开演示材料。本文件故意保留与访谈纪要之间的冲突，供评审发现；不可据此实施真实采购制度。

## 运营部门补充
运营负责人在会后表格中写道：“采购总额小于 10,000 元，部门主管批准即可；达到 10,000 元才需要财务复核。”这与访谈中 5,000 元口径存在差异，尚未形成正式确认结论。

申请单状态暂记为：草稿、待审批、已批准、已驳回。有人建议驳回后新建一张单，有人希望原单修改后再次提交，处理方式待定。

## 采购与收货
- 一个申请只生成一个采购订单；再次点击不得生成第二张订单。
- 订单明细来源于批准的申请，不允许采购员改变物品、单价或数量。
- 采购员填写供应商后下单。未下单订单不能收货。
- 仓管按明细登记本次收货数量；未收、部分收货、已完成三种状态应可区分。

## 验收关注点
验收要能现场走通：新建申请 → 必填校验 → 提交 → 主管审核 → 建单与下单 → 分两次收货 → 查看完成记录。
还需检查金额阈值边界、越权操作、重复审批、重复建单、零数量、负单价、超额收货，以及刷新后的状态表现。`,

    source3: `# 设备采购与审批：现状与交付约束

> 完全虚构的公开演示材料。说明现有线下表格问题、交付范围与静态原型演示边界。

## 现状旧表与痛点
旧版共享表格《设备采购台账2026.xlsx》存在三大痛点：
1. 缺少固定单号与版本，经常被多人覆盖修改。
2. 审批依赖聊天记录留言截图，主管出差时无法代审。
3. 分批到货依靠人工在备注栏手写涂改，多次发生超收或漏收。

## 交付边界
1. 本期只交付四个核心模块：采购申请、审批待办、采购订单、收货登记。
2. 不做企业组织架构系统集成、不接入真实外部网关。
3. 演示静态原型使用内存中的虚构数据，角色切换仅用于交互走查。`,
  };

  sourceTabBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      sourceTabBtns.forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      const key = btn.getAttribute("data-key");
      if (sourceContentBox && sourceContents[key]) {
        sourceContentBox.textContent = sourceContents[key];
      }
    });
  });

  // 3. Conflict Simulator
  const amountBtns = document.querySelectorAll(".amount-btn");
  const resultRule01 = document.getElementById("res-rule-01");
  const resultRule02 = document.getElementById("res-rule-02");
  const conflictAlert = document.getElementById("conflict-alert");

  const simulationData = {
    4500: {
      rule1: "不超过 5,000 元 → 主管批准即可（免财务复核）",
      badge1: "badge-pass",
      rule2: "小于 10,000 元 → 主管批准即可（免财务复核）",
      badge2: "badge-pass",
      alert: false,
      alertText: "",
    },
    8600: {
      rule1: "超过 5,000 元 → 必须财务二级复核",
      badge1: "badge-finance",
      rule2: "小于 10,000 元 → 主管终审即可（无需财务复核）",
      badge2: "badge-conflict",
      alert: true,
      alertText: "⚠️ 规则冲突爆发！8,600 元的设计工作站申请落入分歧区间。若按访谈纪要需要财务复核，按补充规则主管批准即可买。若直接交给研发开发，系统将出现重大越权漏洞或流程卡死！",
    },
    12000: {
      rule1: "超过 5,000 元 → 必须财务二级复核",
      badge1: "badge-finance",
      rule2: "达到 10,000 元 → 必须财务二级复核",
      badge2: "badge-finance",
      alert: false,
      alertText: "",
    },
  };

  amountBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      amountBtns.forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      const val = btn.getAttribute("data-val");
      const data = simulationData[val];
      if (data) {
        if (resultRule01) {
          resultRule01.textContent = data.rule1;
          resultRule01.className = "result-badge " + data.badge1;
        }
        if (resultRule02) {
          resultRule02.textContent = data.rule2;
          resultRule02.className = "result-badge " + data.badge2;
        }
        if (conflictAlert) {
          if (data.alert) {
            conflictAlert.style.display = "block";
            conflictAlert.textContent = data.alertText;
          } else {
            conflictAlert.style.display = "none";
          }
        }
      }
    });
  });

  // 4. Prototype Sandbox Controls
  const resetBtn = document.getElementById("btn-reset-prototype");
  const openNewTabBtn = document.getElementById("btn-open-prototype");
  const prototypeFrame = document.getElementById("prototype-frame");

  if (resetBtn && prototypeFrame) {
    resetBtn.addEventListener("click", () => {
      const currentSrc = prototypeFrame.src;
      prototypeFrame.src = "about:blank";
      setTimeout(() => {
        prototypeFrame.src = currentSrc;
      }, 50);
    });
  }

  if (openNewTabBtn) {
    openNewTabBtn.addEventListener("click", () => {
      window.open("./procurement-demo.html", "_blank", "noopener,noreferrer");
    });
  }

  // 5. Video Modal Handling
  const openVideoBtns = document.querySelectorAll(".btn-open-video");
  const videoDialog = document.getElementById("video-dialog");
  const videoPlayer = document.getElementById("demo-video");
  const closeVideoBtn = document.querySelector(".btn-close-modal");

  openVideoBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      if (videoDialog && typeof videoDialog.showModal === "function") {
        videoDialog.showModal();
        if (videoPlayer) {
          videoPlayer.currentTime = 0;
          videoPlayer.play().catch(() => {});
        }
      }
    });
  });

  if (closeVideoBtn && videoDialog) {
    closeVideoBtn.addEventListener("click", () => {
      videoDialog.close();
      if (videoPlayer) {
        videoPlayer.pause();
      }
    });
  }

  if (videoDialog) {
    videoDialog.addEventListener("click", (event) => {
      if (event.target === videoDialog) {
        videoDialog.close();
        if (videoPlayer) {
          videoPlayer.pause();
        }
      }
    });
    videoDialog.addEventListener("close", () => {
      if (videoPlayer) {
        videoPlayer.pause();
      }
    });
  }

  // 6. Deliverables Explorer Tabs & Filters
  const explorerTabBtns = document.querySelectorAll(".explorer-tab-btn");
  const explorerPanels = document.querySelectorAll(".explorer-panel");

  explorerTabBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      const targetId = btn.getAttribute("data-target") || ("panel-" + btn.getAttribute("data-tab"));
      explorerTabBtns.forEach((b) => b.classList.remove("active"));
      explorerPanels.forEach((p) => p.classList.remove("active"));

      btn.classList.add("active");
      const targetPanel = document.getElementById(targetId);
      if (targetPanel) {
        targetPanel.classList.add("active");
      }
    });
  });

  const techspecSubtabBtns = document.querySelectorAll(".techspec-subtab-btn");
  const techspecSubpanels = document.querySelectorAll(".techspec-subpanel");

  techspecSubtabBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      const targetId = btn.getAttribute("data-sub") || ("subpanel-" + btn.getAttribute("data-subtab"));
      techspecSubtabBtns.forEach((b) => b.classList.remove("active"));
      techspecSubpanels.forEach((p) => p.classList.remove("active"));

      btn.classList.add("active");
      const targetSubpanel = document.getElementById(targetId);
      if (targetSubpanel) {
        targetSubpanel.classList.add("active");
      }
    });
  });

  const tcFilterBtns = document.querySelectorAll(".tc-filter-btn");
  const tcCards = document.querySelectorAll(".tc-card");

  tcFilterBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      const filterVal = btn.getAttribute("data-filter") || btn.getAttribute("data-module");
      tcFilterBtns.forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");

      tcCards.forEach((card) => {
        const cardModule = card.getAttribute("data-module");
        if (filterVal === "all" || cardModule === filterVal) {
          card.style.display = "";
        } else {
          card.style.display = "none";
        }
      });
    });
  });

  // 7. Direct file:// protocol fallback for offline inspection
  if (window.location.protocol === "file:") {
    const zipLinks = document.querySelectorAll('a[href="./deliverables.zip"]');
    zipLinks.forEach((a) => {
      a.href = "../examples/procurement/deliverables.zip";
    });
    if (videoPlayer) {
      videoPlayer.poster = "../examples/procurement/overview.jpg";
      const src = videoPlayer.querySelector("source");
      if (src) {
        src.src = "../examples/procurement/walkthrough.mp4";
      }
    }
  }
});
