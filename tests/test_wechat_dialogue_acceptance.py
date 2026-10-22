import os
import sys
import time
import requests
from playwright.sync_api import sync_playwright

BASE_URL = "http://127.0.0.1:8180"
API_BASE = f"{BASE_URL}/api/v1"
CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
ARTIFACTS_DIR = os.path.abspath("tests/artifacts/review_e2e_screenshots")
CONVERSATION_SCREENSHOTS_DIR = os.environ.get("CONVERSATION_SCREENSHOTS_DIR")

os.makedirs(ARTIFACTS_DIR, exist_ok=True)
if CONVERSATION_SCREENSHOTS_DIR:
    os.makedirs(CONVERSATION_SCREENSHOTS_DIR, exist_ok=True)

def log(msg):
    print(f"[WECHAT-UI-ACCEPTANCE] {msg}", flush=True)

def save_screenshot(page, filename):
    p1 = os.path.join(ARTIFACTS_DIR, filename)
    page.screenshot(path=p1, full_page=True)
    if CONVERSATION_SCREENSHOTS_DIR:
        p2 = os.path.join(CONVERSATION_SCREENSHOTS_DIR, filename)
        page.screenshot(path=p2, full_page=True)
    log(f"Saved screenshot: {filename}")

def main():
    log("Starting WeChat-Style Dialogue & Red Asterisk Acceptance Test...")
    ts = int(time.time())
    owner_user = f"owner_wc_{ts}"
    reporter_user = f"reporter_wc_{ts}"
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
        "title": f"对话流与必填项体验验收需求 {ts}"
    }).json()
    task_id = task_res["id"]

    requests.post(f"{API_BASE}/review/tasks/{task_id}/members", headers={"Authorization": f"Bearer {owner_token}"}, json={
        "username": reporter_user, "role": "REVIEWER"
    })

    doc_res = requests.post(f"{API_BASE}/review/tasks/{task_id}/documents", headers={"Authorization": f"Bearer {owner_token}"}, json={
        "title": "财务凭证与账簿协同 PRD",
        "content": """# 财务凭证与账簿设计

## 1. 业务定义
### 1.1 凭证录入
凭证录入需校验借贷平衡与科目存在性。

### 1.2 冲销规则
红字冲销须在次月结账日前完成并记录操作人快照。
""",
        "summary": "初稿提交",
        "changeType": "INITIAL"
    }).json()
    doc_id = doc_res["document"]["id"]
    head_rev_id = doc_res["headRevision"]["id"]

    # Reporter creates issue
    issue_res = requests.post(f"{API_BASE}/review/documents/{doc_id}/issues", headers={"Authorization": f"Bearer {reporter_token}"}, json={
        "title": "关于红字冲销时限的严谨性确认",
        "body": "请问如果遇到跨年关账，次月结账日规则是否需要顺延到审计完成日之后？",
        "type": "QUESTION",
        "impact": "NORMAL",
        "source": "ONLINE",
        "originRevisionId": head_rev_id,
        "assigneeId": owner_id
    }).json()
    issue_id = issue_res["id"]

    with sync_playwright() as p:
        browser = p.chromium.launch(
            executable_path=CHROME_PATH if os.path.exists(CHROME_PATH) else None,
            headless=True
        )
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        # =====================================================================
        # Step 1: Login as Reporter and check Red Asterisks in Create Issue Modal
        # =====================================================================
        log("Logging in as Reporter...")
        page.goto(f"{BASE_URL}/login")
        page.wait_for_selector("input[autocomplete='username']")
        page.fill("input[autocomplete='username']", reporter_user)
        page.fill("input[autocomplete='current-password']", password)
        page.click("button[type='submit']")
        page.wait_for_url(f"{BASE_URL}/")

        page.goto(f"{BASE_URL}/reviews/{task_id}/documents/{doc_id}")
        page.wait_for_selector(".review-doc-page")
        page.wait_for_timeout(1000)

        # Open Create Issue modal to verify red asterisks
        log("Opening Create Issue modal to verify red asterisks...")
        page.click("button.primary-ask-btn")
        page.wait_for_selector(".issue-create-modal")
        page.wait_for_timeout(500)

        # Check .required-star styling
        required_stars = page.locator(".issue-create-modal .required-star")
        star_count = required_stars.count()
        assert star_count >= 4, f"Expected at least 4 required stars, found {star_count}"

        # Evaluate the computed color of the first required star
        star_color = required_stars.first.evaluate("el => window.getComputedStyle(el).color")
        log(f"Computed color of required star: {star_color}")
        # RGB of #ef4444 is rgb(239, 68, 68)
        assert "239" in star_color and "68" in star_color, f"Star color should be red, got: {star_color}"

        save_screenshot(page, "30_required_fields_red_asterisk.png")
        page.click(".issue-create-modal .close-btn")
        page.wait_for_timeout(500)

        # =====================================================================
        # Step 2: Maintainer answers the issue
        # =====================================================================
        log("Maintainer replying with official answer via API...")
        r_post = requests.post(f"{API_BASE}/review/issues/{issue_id}/posts", headers={"Authorization": f"Bearer {owner_token}"}, json={
            "expectedRevision": issue_res["revision"],
            "kind": "ANSWER",
            "body": "跨年关账已在第 3.2 节补充说明，以次年首个工作日及年度审计报告签署日为准。"
        })
        assert r_post.status_code in (200, 201), f"Maintainer post reply failed: {r_post.text}"

        # =====================================================================
        # Step 3: Reporter views the WeChat-style dialogue drawer
        # =====================================================================
        log("Reporter opening issue drawer to inspect WeChat-style dialogue...")
        page.goto(f"{BASE_URL}/reviews/{task_id}/documents/{doc_id}?issue={issue_id}")
        page.wait_for_selector(".issue-detail-drawer")
        page.wait_for_timeout(1000)

        # 1. Assert noisy events like ISSUE_CREATED and ISSUE_ANSWERED are NOT present in drawer!
        drawer_text = page.locator(".issue-detail-drawer").inner_text()
        assert "ISSUE_CREATED" not in drawer_text, "ISSUE_CREATED event string should NOT be shown!"
        assert "ISSUE_ANSWERED" not in drawer_text, "ISSUE_ANSWERED event string should NOT be shown!"
        assert "流程状态：待确认" not in drawer_text, "Bulky redundant status hint card should NOT be shown!"
        assert "解决方案建议" not in drawer_text, "Bulky redundant resolution card should NOT be shown!"
        log("Verified: Noisy raw event tags and redundant green cards are eliminated!")

        # 2. Assert WeChat chat bubbles are present
        chat_stream = page.locator(".chat-conversation-stream")
        assert chat_stream.is_visible(), "Chat conversation stream MUST be visible!"

        # Question bubble
        question_bubble = page.locator(".chat-message-item.question-message")
        assert question_bubble.is_visible(), "Question chat bubble MUST be visible!"
        assert "提出人" in question_bubble.inner_text()
        assert "余祥金" in question_bubble.inner_text()
        assert "跨年关账" in question_bubble.inner_text()

        # Official answer bubble
        answer_bubble = page.locator(".chat-message-item.official-answer-item")
        assert answer_bubble.is_visible(), "Official answer chat bubble MUST be visible!"
        assert "官方答复" in answer_bubble.inner_text()
        assert "Maintainer" in answer_bubble.inner_text()
        assert "跨年关账已在第 3.2 节补充说明" in answer_bubble.inner_text()

        # Awaiting confirmation notice
        awaiting_notice = page.locator(".chat-system-notice.awaiting")
        assert awaiting_notice.is_visible(), "Awaiting notice MUST be visible!"

        log("Verified: WeChat-style chat bubbles and avatars look great!")
        save_screenshot(page, "31_issue_drawer_wechat_dialogue_bubbles.png")

        # =====================================================================
        # Step 4: Reporter clicks "问题已解决", verify resolution milestone notice
        # =====================================================================
        log("Reporter clicking '问题已解决'...")
        resolve_btn = page.locator(".workflow-actions-bar .success-btn")
        assert resolve_btn.is_visible()
        resolve_btn.click()
        page.wait_for_timeout(1500)

        # Verify status is RESOLVED and resolved notice is visible
        resolved_badge = page.locator(".issue-detail-drawer .issue-status-badge")
        assert "已解决" in resolved_badge.inner_text()

        resolved_notice = page.locator(".chat-system-notice.resolved")
        assert resolved_notice.is_visible(), "Resolved system notice MUST be visible!"
        assert "问题已解决" in resolved_notice.inner_text()
        log("Verified: Issue resolved milestone notice is visible!")

        save_screenshot(page, "32_issue_drawer_resolved_milestone_notice.png")

        browser.close()
        log("ALL WECHAT DIALOGUE & RED ASTERISK CHECKS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    main()
