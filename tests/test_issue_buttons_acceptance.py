import json
import os
import sys
import time
try:
    import requests
except ImportError:
    requests = None

try:
    from playwright.sync_api import sync_playwright
except ImportError:
    sync_playwright = None

BASE_URL = "http://127.0.0.1:8180"
API_BASE = f"{BASE_URL}/api/v1"
CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
ARTIFACTS_DIR = os.path.abspath("tests/artifacts/review_e2e_screenshots")
CONVERSATION_SCREENSHOTS_DIR = os.environ.get("CONVERSATION_SCREENSHOTS_DIR")

os.makedirs(ARTIFACTS_DIR, exist_ok=True)
if CONVERSATION_SCREENSHOTS_DIR:
    os.makedirs(CONVERSATION_SCREENSHOTS_DIR, exist_ok=True)

def log(msg):
    print(f"[BUTTONS-ACCEPTANCE] {msg}", flush=True)

def save_screenshot(page, filename):
    p1 = os.path.join(ARTIFACTS_DIR, filename)
    page.screenshot(path=p1, full_page=True)
    if CONVERSATION_SCREENSHOTS_DIR:
        p2 = os.path.join(CONVERSATION_SCREENSHOTS_DIR, filename)
        page.screenshot(path=p2, full_page=True)
    log(f"Saved screenshot: {filename}")

def main():
    log("Starting Issue Action Buttons Acceptance Test...")
    ts = int(time.time())
    owner_user = f"owner_btn_{ts}"
    reporter_user = f"reporter_btn_{ts}"
    password = f"P@ss_{ts}"

    log("Registering test users...")
    r_owner = requests.post(f"{API_BASE}/auth/register", json={
        "username": owner_user, "password": password, "displayName": "Maintainer (责任人/所有者)"
    }).json()
    owner_token = r_owner["token"]
    owner_id = r_owner["user"]["id"]

    r_reporter = requests.post(f"{API_BASE}/auth/register", json={
        "username": reporter_user, "password": password, "displayName": "余祥金 (问题提出人)"
    }).json()
    reporter_token = r_reporter["token"]
    reporter_id = r_reporter["user"]["id"]

    # Owner creates task and document
    task_res = requests.post(f"{API_BASE}/tasks", headers={"Authorization": f"Bearer {owner_token}"}, json={
        "title": f"提出人专享按钮与流转体验验收需求 {ts}"
    }).json()
    task_id = task_res["id"]

    # Owner adds reporter as REVIEWER
    requests.post(f"{API_BASE}/review/tasks/{task_id}/members", headers={"Authorization": f"Bearer {owner_token}"}, json={
        "username": reporter_user, "role": "REVIEWER"
    })

    doc_res = requests.post(f"{API_BASE}/review/tasks/{task_id}/documents", headers={"Authorization": f"Bearer {owner_token}"}, json={
        "title": "权限与状态流转体验验收 PRD",
        "content": """# 权限与状态流转 PRD

## 1. 业务流程定义
### 1.1 责任划分
问题确认闭环与撤销操作属于提出人专有权责，责任人负责方案出具与答复。
### 1.2 撤销规范
提出人若发现误提问题或已自行查明，可在待处理阶段主动撤销。
""",
        "summary": "初稿提交",
        "changeType": "INITIAL"
    }).json()
    doc_id = doc_res["document"]["id"]
    head_rev_id = doc_res["headRevision"]["id"]

    # Reporter creates Issue 1 (will be answered by Owner, then confirmed resolved by Reporter)
    issue_res_1 = requests.post(f"{API_BASE}/review/documents/{doc_id}/issues", headers={"Authorization": f"Bearer {reporter_token}"}, json={
        "title": "关于责任划分条款的合规性确认",
        "body": "请责任人明确在提出人休假期间，是否支持委托授权人闭环问题？",
        "type": "QUESTION",
        "impact": "NORMAL",
        "source": "ONLINE",
        "originRevisionId": head_rev_id,
        "assigneeId": owner_id
    }).json()
    assert "id" in issue_res_1, f"Create issue 1 failed: {issue_res_1}"
    issue_id_1 = issue_res_1["id"]
    log(f"Reporter created Issue 1: {issue_id_1}")

    # Reporter creates Issue 2 (will test cancellation by Reporter)
    issue_res_2 = requests.post(f"{API_BASE}/review/documents/{doc_id}/issues", headers={"Authorization": f"Bearer {reporter_token}"}, json={
        "title": "误提问题测试：撤销按钮验证",
        "body": "此条为测试提出人主动撤销功能的问题记录。",
        "type": "QUESTION",
        "impact": "NORMAL",
        "source": "ONLINE",
        "originRevisionId": head_rev_id,
        "assigneeId": owner_id
    }).json()
    assert "id" in issue_res_2, f"Create issue 2 failed: {issue_res_2}"
    issue_id_2 = issue_res_2["id"]
    log(f"Reporter created Issue 2: {issue_id_2}")

    with sync_playwright() as p:
        browser = p.chromium.launch(
            executable_path=CHROME_PATH if os.path.exists(CHROME_PATH) else None,
            headless=True
        )

        # =====================================================================
        # Step 1: Login as Owner/Maintainer (NON-REPORTER)
        # =====================================================================
        log("Testing Owner/Assignee view: must NOT see '问题已解决' or '撤销问题'...")
        context_owner = browser.new_context(viewport={"width": 1440, "height": 900})
        page_owner = context_owner.new_page()

        page_owner.goto(f"{BASE_URL}/login")
        page_owner.wait_for_selector("input[autocomplete='username']")
        page_owner.fill("input[autocomplete='username']", owner_user)
        page_owner.fill("input[autocomplete='current-password']", password)
        page_owner.click("button[type='submit']")
        page_owner.wait_for_url(f"{BASE_URL}/")

        # Directly navigate to Issue 1
        page_owner.goto(f"{BASE_URL}/reviews/{task_id}/documents/{doc_id}?issue={issue_id_1}")
        page_owner.wait_for_selector(".issue-detail-drawer")
        page_owner.wait_for_timeout(1000)

        # Check buttons in Owner view: CANNOT see "问题已解决" or "撤销问题"
        resolve_btn_owner = page_owner.locator(".workflow-actions-bar .success-btn")
        cancel_btn_owner = page_owner.locator(".workflow-actions-bar .cancel-btn")
        assert resolve_btn_owner.count() == 0, "Owner should NOT see '问题已解决' button!"
        assert cancel_btn_owner.count() == 0, "Owner should NOT see '撤销问题' button!"
        log("Verified: Owner/Assignee DOES NOT see '问题已解决' or '撤销问题'!")

        # Owner replies with official answer
        log("Owner submitting official answer to Issue 1...")
        page_owner.click(".kind-tab.answer-tab")
        page_owner.fill(".reply-textarea", "二期将提供严格的委派授权代理链，当前版本以提出人本人验收为准。")
        page_owner.click("button.reply-submit-btn")
        page_owner.wait_for_timeout(1500)

        # After reply, verify Owner STILL does NOT see "问题已解决" or "撤销问题"
        assert page_owner.locator(".workflow-actions-bar .success-btn").count() == 0, "Owner should NOT see '问题已解决' after replying!"
        assert page_owner.locator(".workflow-actions-bar .cancel-btn").count() == 0, "Owner should NOT see '撤销问题' after replying!"
        log("Verified: Even after replying, Owner STILL cannot see '问题已解决' or '撤销问题'!")

        save_screenshot(page_owner, "26_assignee_view_no_confirm_or_cancel_buttons.png")
        context_owner.close()

        # =====================================================================
        # Step 2: Login as Reporter (余祥金)
        # =====================================================================
        log("Testing Reporter view: must see beautified and smooth '问题已解决' and '撤销问题'...")
        context_reporter = browser.new_context(viewport={"width": 1440, "height": 900})
        page_reporter = context_reporter.new_page()

        page_reporter.goto(f"{BASE_URL}/login")
        page_reporter.wait_for_selector("input[autocomplete='username']")
        page_reporter.fill("input[autocomplete='username']", reporter_user)
        page_reporter.fill("input[autocomplete='current-password']", password)
        page_reporter.click("button[type='submit']")
        page_reporter.wait_for_url(f"{BASE_URL}/")

        # Open Issue 2 as Reporter (OPEN, verify '撤销问题' is visible and can be cancelled)
        log("Reporter checking Issue 2 (OPEN state)...")
        page_reporter.goto(f"{BASE_URL}/reviews/{task_id}/documents/{doc_id}?issue={issue_id_2}")
        page_reporter.wait_for_selector(".issue-detail-drawer")
        page_reporter.wait_for_timeout(1000)

        cancel_btn = page_reporter.locator(".workflow-actions-bar .cancel-btn")
        assert cancel_btn.is_visible(), "Reporter MUST see '撤销问题' button when issue is OPEN!"
        log("Verified: Reporter sees '撤销问题' button!")
        save_screenshot(page_reporter, "29_reporter_view_cancel_issue_button_smooth.png")

        # Open Issue 1 as Reporter (AWAITING_CONFIRMATION, verify '问题已解决' is visible)
        log("Reporter checking Issue 1 (AWAITING_CONFIRMATION state)...")
        page_reporter.goto(f"{BASE_URL}/reviews/{task_id}/documents/{doc_id}?issue={issue_id_1}")
        page_reporter.wait_for_selector(".issue-detail-drawer")
        page_reporter.wait_for_timeout(1000)

        # Verify Reporter sees the beautified "问题已解决" button
        resolve_btn_reporter = page_reporter.locator(".workflow-actions-bar .success-btn")
        assert resolve_btn_reporter.is_visible(), "Reporter MUST see '问题已解决' button!"
        assert "问题已解决" in resolve_btn_reporter.inner_text(), "Button text should be '问题已解决'"

        log("Verified: Reporter sees beautified '问题已解决' button!")
        save_screenshot(page_reporter, "27_reporter_view_beautified_smooth_buttons.png")

        # Reporter clicks "问题已解决"
        log("Reporter clicking '问题已解决'...")
        resolve_btn_reporter.click()
        page_reporter.wait_for_timeout(1500)

        # Verify status is now RESOLVED
        status_tag = page_reporter.locator(".issue-detail-drawer .issue-status-badge")
        assert "已解决" in status_tag.inner_text(), f"Issue status should be '已解决', got {status_tag.inner_text()}!"
        log("Issue 1 successfully resolved by reporter in 1 click!")

        save_screenshot(page_reporter, "28_issue_resolved_by_reporter.png")

        browser.close()
        log("ALL BUTTON ACCEPTANCE CHECKS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    if requests is None or sync_playwright is None:
        print("[BUTTONS-ACCEPTANCE] requests or playwright not installed, skipping test.")
        sys.exit(0)
    main()
