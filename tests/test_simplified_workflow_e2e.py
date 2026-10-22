import os
import time
import requests
from playwright.sync_api import sync_playwright

BASE_URL = "http://127.0.0.1:8180"
API_BASE = f"{BASE_URL}/api/v1"
CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

SCREENSHOTS_DIR = os.path.expanduser(
    "~/.gemini/antigravity/brain/252adf32-3ecf-40da-88b2-556141664e19/screenshots"
)
os.makedirs(SCREENSHOTS_DIR, exist_ok=True)

def run():
    ts = int(time.time())
    owner_user = f"maintainer_{ts}"
    reviewer_user = f"reporter_{ts}"
    password = f"P@ssw0rd_{ts}"

    # 1. Register Owner (Maintainer) and Reviewer (Reporter)
    r_owner = requests.post(f"{API_BASE}/auth/register", json={
        "username": owner_user, "password": password, "displayName": "Maintainer (所有者)"
    }).json()
    owner_token = r_owner["token"]

    r_rev = requests.post(f"{API_BASE}/auth/register", json={
        "username": reviewer_user, "password": password, "displayName": "余祥金 (评审人)"
    }).json()
    rev_token = r_rev["token"]

    # 2. Owner creates task and document
    owner_headers = {"Authorization": f"Bearer {owner_token}"}
    task = requests.post(f"{API_BASE}/tasks", headers=owner_headers, json={
        "title": f"智能采购风控协同工作台PRD_{ts}"
    }).json()
    task_id = task["id"]

    doc_resp = requests.post(f"{API_BASE}/review/tasks/{task_id}/documents", headers=owner_headers, json={
        "title": "智能采购风控协同工作台 PRD 规格 (v1.0)",
        "content": """# 智能采购风控协同工作台

## 1. 业务背景与建设目标
本项目旨在为大中型企业采购供应链部门构建全流程智能风控与审批协同系统。

## 2. 核心功能规划与系统模块
### 2.1 供应商多维资质准入核验
系统对接国家工商信用系统与司法诉讼数据库，在供应商提交入驻申请后 3 秒内完成自动化征信筛查与风险打标。

### 2.2 采购合同条款自动比对与审查
引入自然语言处理与结构化规则引擎，比对供应商回传盖章版本与标准合同范本差异。

### 2.3 异常报价与合谋串标预警模型
基于历史成交均价、近期大宗原材料现货指数及竞标企业 IP/MAC/银行流水关联图谱进行实时研判。

## 3. 非功能性需求与性能指标
- 页面首屏渲染时间（FCP）不大于 1.2 秒。
- 关键业务接口响应时间（P99）在 500ms 以内。
- 系统可用性达 99.99%，支持热备容灾切换。
"""
    }).json()
    doc_id = doc_resp["document"]["id"]

    # 3. Add Reviewer to task space
    requests.post(f"{API_BASE}/review/tasks/{task_id}/members", headers=owner_headers, json={
        "username": reviewer_user,
        "role": "REVIEWER"
    })

    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME_PATH, headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        # Step A: Test Requirement 5 - Owner proposes an issue assigned to self (or default)
        page.goto(f"{BASE_URL}/login")
        page.fill("input[autocomplete='username']", owner_user)
        page.fill("input[autocomplete='current-password']", password)
        page.click("button.login-submit-btn")
        page.wait_for_selector(".workspace")

        page.goto(f"{BASE_URL}/reviews/{task_id}")
        page.wait_for_selector(".review-doc-page")

        # Open modal
        page.click("button.primary-ask-btn")
        page.wait_for_selector(".issue-create-modal")
        time.sleep(0.5)

        # Fill modal without changing assignee (default to maintainer/owner)
        page.fill(".issue-create-modal input[placeholder*='简要概括']", "所有者自提章节优化待办")
        page.fill(".issue-create-modal textarea[placeholder*='详细阐述']", "自查发现第2节关于异常报价模型需补充具体特征工程指标。")
        time.sleep(0.5)

        shot_modal = os.path.join(SCREENSHOTS_DIR, "15_create_issue_modal_scrollable_buttons_visible.png")
        page.screenshot(path=shot_modal)
        print(f"Captured: {shot_modal}")

        # Submit as owner
        page.click(".modal-card button.run-button")
        page.wait_for_selector(".issue-detail-drawer")
        print("Owner self-reported issue successfully created without error!")
        time.sleep(1)

        # Close drawer
        page.click(".issue-detail-drawer .close-btn")
        time.sleep(0.5)

        # Step B: Reviewer in separate context creates an issue, tests Reminder button
        context_rev = browser.new_context(viewport={"width": 1440, "height": 900})
        page_rev = context_rev.new_page()

        page_rev.goto(f"{BASE_URL}/login")
        page_rev.fill("input[autocomplete='username']", reviewer_user)
        page_rev.fill("input[autocomplete='current-password']", password)
        page_rev.click("button.login-submit-btn")
        page_rev.wait_for_selector(".workspace")

        page_rev.goto(f"{BASE_URL}/reviews/{task_id}")
        page_rev.wait_for_selector(".review-doc-page")

        # Propose issue with line range
        page_rev.click("button.primary-ask-btn")
        page_rev.wait_for_selector(".issue-create-modal")
        page_rev.click("button.scope-tab-btn:has-text('关联指定正文行号')")
        page_rev.fill(".line-input-item:first-child input", "7")
        page_rev.fill(".line-input-item:nth-of-type(2) input", "9")
        page_rev.fill(".issue-create-modal input[placeholder*='简要概括']", "征信数据源单点故障兜底处理策略")
        page_rev.fill(".issue-create-modal textarea[placeholder*='详细阐述']", "第7-9行提到3秒内自动化核验，若外部接口网络波动是否有降级方案？")
        time.sleep(0.5)
        page_rev.click(".modal-card button.run-button")
        page_rev.wait_for_selector(".issue-detail-drawer")
        time.sleep(1)

        # Verify Reminder button is visible when issue is OPEN and waiting for reply
        remind_btn = page_rev.locator("button.reminder-btn")
        assert remind_btn.is_visible(), "Reminder button should be visible when issue is OPEN"
        shot_reminder_btn = os.path.join(SCREENSHOTS_DIR, "16_remind_assignee_button_visible.png")
        page_rev.screenshot(path=shot_reminder_btn)
        print(f"Captured: {shot_reminder_btn}")

        # Click reminder button
        remind_btn.click()
        time.sleep(1)
        shot_reminded = os.path.join(SCREENSHOTS_DIR, "17_reminder_toast_and_timeline_event.png")
        page_rev.screenshot(path=shot_reminded)
        print(f"Captured: {shot_reminded}")

        # Step C: Requirement Owner replies -> Issue automatically transitions to 待确认 (AWAITING_CONFIRMATION)
        # Owner replies via API
        rev_headers = {"Authorization": f"Bearer {rev_token}"}
        res_issues = requests.get(f"{API_BASE}/review/documents/{doc_id}/issues", headers=rev_headers).json()
        print("Issues response:", res_issues)
        issues = res_issues["items"]
        target_issue = [i for i in issues if "征信数据源" in i["title"]][0]
        issue_id = target_issue["id"]

        # Owner posts official answer
        r_post = requests.post(f"{API_BASE}/review/issues/{issue_id}/posts", headers=owner_headers, json={
            "expectedRevision": target_issue["revision"], # current revision of the issue
            "kind": "ANSWER",
            "body": "针对外部征信接口，系统已内置本地Redis热点缓存并在超时大于2秒时自动降级转入人工初审流水线。"
        }).json()
        print("Owner answered post response:", r_post)

        # Reload reviewer page to see auto-transition to 待确认 and 【问题已解决】 button
        page_rev.reload()
        page_rev.wait_for_selector(".issue-card")
        page_rev.locator(".issue-card:has-text('征信数据源')").click()
        page_rev.wait_for_selector(".issue-detail-drawer")
        time.sleep(1)

        shot_awaiting = os.path.join(SCREENSHOTS_DIR, "18_awaiting_confirmation_resolved_button.png")
        page_rev.screenshot(path=shot_awaiting)
        print(f"Captured: {shot_awaiting}")

        # Step D: Reporter clicks 【问题已解决】 -> Transitions directly to RESOLVED!
        resolve_btn = page_rev.locator("button.wf-btn.success-btn:has-text('问题已解决')")
        assert resolve_btn.is_visible(), "Resolved button should be visible to reporter"
        resolve_btn.click()
        time.sleep(1)

        shot_resolved = os.path.join(SCREENSHOTS_DIR, "19_issue_closed_resolved_1click.png")
        page_rev.screenshot(path=shot_resolved)
        print(f"Captured: {shot_resolved}")

        context_rev.close()

        browser.close()
        print("ALL SIMPLIFIED WORKFLOW TESTS PASSED!")

if __name__ == "__main__":
    run()
