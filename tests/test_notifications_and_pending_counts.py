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

BASE_URL = os.environ.get("BASE_URL", "http://127.0.0.1:8180")
API_BASE = f"{BASE_URL}/api/v1"
CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
ARTIFACTS_DIR = os.path.abspath("tests/artifacts/review_e2e_screenshots")
CONVERSATION_SCREENSHOTS_DIR = os.environ.get("CONVERSATION_SCREENSHOTS_DIR")

os.makedirs(ARTIFACTS_DIR, exist_ok=True)
if CONVERSATION_SCREENSHOTS_DIR:
    os.makedirs(CONVERSATION_SCREENSHOTS_DIR, exist_ok=True)

def save_screenshot(page, filename):
    p1 = os.path.join(ARTIFACTS_DIR, filename)
    page.screenshot(path=p1, full_page=True)
    if CONVERSATION_SCREENSHOTS_DIR:
        p2 = os.path.join(CONVERSATION_SCREENSHOTS_DIR, filename)
        page.screenshot(path=p2, full_page=True)
    print(f"[E2E-NOTIFICATIONS] Saved screenshot: {filename}")

def get_admin_password():
    import subprocess
    cmd = "docker exec hermes-prd-workbench-backend-1 cat /run/secrets/admin-password"
    out = subprocess.check_output(cmd, shell=True).decode().strip()
    return out

def run():
    print("[E2E-NOTIFICATIONS] Starting notifications and pending counts acceptance test...")
    admin_pwd = get_admin_password()
    print("[E2E-NOTIFICATIONS] Admin password retrieved.")

    # Maintainer login via API
    m_login_resp = requests.post(f"{API_BASE}/auth/login", json={
        "username": "maintainer",
        "password": admin_pwd
    })
    assert m_login_resp.status_code == 200, f"Maintainer login failed: {m_login_resp.text}"
    maintainer_token = m_login_resp.json()["token"]
    maintainer_headers = {"Authorization": f"Bearer {maintainer_token}"}

    with sync_playwright() as p:
        browser = p.chromium.launch(
            executable_path=CHROME_PATH if os.path.exists(CHROME_PATH) else None,
            headless=True
        )
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        # Step 1: Login as Maintainer in Browser
        print("[E2E-NOTIFICATIONS] Logging in as Maintainer in browser...")
        page.goto(f"{BASE_URL}/login")
        page.wait_for_selector("input[autocomplete='username']")
        page.fill("input[autocomplete='username']", "maintainer")
        page.fill("input[autocomplete='current-password']", admin_pwd)
        page.click("button[type='submit']")
        page.wait_for_timeout(1000)

        # Step 2: Navigate to 线上评审 workspace
        print("[E2E-NOTIFICATIONS] Navigating to 线上评审 workspace...")
        page.goto(f"{BASE_URL}/reviews")
        page.wait_for_selector('.review-task-card')
        page.wait_for_timeout(1000)

        # Locate "公开演示：财务凭证与账簿需求" card
        finance_card = page.locator('.review-task-card:has-text("公开演示：财务凭证与账簿需求")')
        assert finance_card.count() > 0, "Finance task card not found!"

        # Verify "待我解决: 0"
        todo_text = finance_card.locator('.stat-pill:has-text("待我解决")').inner_text()
        print(f"[E2E-NOTIFICATIONS] Finance card todo stat: '{todo_text}'")
        assert "待我解决: 0" in todo_text, f"Expected '待我解决: 0', got '{todo_text}'"

        # Verify no unread badge on finance card
        unread_pill = finance_card.locator('.unread-pill')
        assert unread_pill.count() == 0, f"Expected 0 unread pills on card, got {unread_pill.count()}"

        # Verify top notification bell has NO unread badge
        nav_bell_badge = page.locator('.notification-action .notification-badge')
        badge_count = nav_bell_badge.count()
        print(f"[E2E-NOTIFICATIONS] Top nav bell badge count: {badge_count}")
        assert badge_count == 0, f"Expected 0 top nav bell badges, got {badge_count}"

        # Capture screenshot 33
        save_screenshot(page, "33_workspace_maintainer_0_unread_0_pending.png")

        # Step 3: Test Dynamic Flow:
        # Register a reporter, create an issue assigned to maintainer on finance task
        ts = int(time.time())
        reporter_username = f"user_ntf_{ts}"
        reporter_pwd = "password123"
        reg_resp = requests.post(f"{API_BASE}/auth/register", json={
            "username": reporter_username,
            "password": reporter_pwd,
            "displayName": "通知验收测试员"
        })
        assert reg_resp.status_code == 201, f"Failed to register reporter: {reg_resp.text}"
        reporter_token = reg_resp.json()["token"]
        reporter_headers = {"Authorization": f"Bearer {reporter_token}"}

        task_id = "task_d6070bbc8b8140a2808d"

        # Add member
        add_resp = requests.post(f"{API_BASE}/review/tasks/{task_id}/members", headers=maintainer_headers, json={
            "username": reporter_username,
            "role": "REVIEWER"
        })
        assert add_resp.status_code in (200, 201), f"Failed to add member: {add_resp.text}"

        # Get document id
        doc_resp = requests.get(f"{API_BASE}/review/tasks/{task_id}/documents", headers=reporter_headers)
        assert doc_resp.status_code == 200
        doc_item = doc_resp.json()["items"][0]
        doc_id = doc_item["id"]
        head_rev_id = doc_item.get("headRevisionId")

        # Reporter creates an issue assigned to maintainer
        print("[E2E-NOTIFICATIONS] Reporter creating a new issue assigned to Maintainer...")
        iss_resp = requests.post(f"{API_BASE}/review/documents/{doc_id}/issues", headers=reporter_headers, json={
            "title": f"实时通知测试问题_{ts}",
            "body": "测试责任人回复后未读消息与待我解决是否自动清零闭环",
            "type": "QUESTION",
            "impact": "NORMAL",
            "source": "ONLINE",
            "originRevisionId": head_rev_id,
            "assigneeId": "user_maintainer_001",
            "anchor": None
        })
        assert iss_resp.status_code == 201, f"Failed to create issue: {iss_resp.text}"
        new_issue_id = iss_resp.json()["id"]
        print(f"[E2E-NOTIFICATIONS] Created issue: {new_issue_id}")

        # Maintainer refreshes workspace
        page.reload()
        page.wait_for_selector('.review-task-card')
        finance_card = page.locator('.review-task-card:has-text("公开演示：财务凭证与账簿需求")')
        todo_text = finance_card.locator('.stat-pill:has-text("待我解决")').inner_text()
        print(f"[E2E-NOTIFICATIONS] After issue created, todo stat: '{todo_text}'")
        assert "待我解决: 1" in todo_text, f"Expected '待我解决: 1', got '{todo_text}'"

        unread_pill = finance_card.locator('.unread-pill')
        assert unread_pill.count() > 0, "Expected unread pill on card!"
        print(f"[E2E-NOTIFICATIONS] Card unread pill text: '{unread_pill.inner_text()}'")
        assert "未读: 1" in unread_pill.inner_text()

        nav_bell_badge = page.locator('.notification-action .notification-badge')
        assert nav_bell_badge.count() > 0, "Expected nav bell badge!"
        print(f"[E2E-NOTIFICATIONS] Top nav bell badge: '{nav_bell_badge.inner_text()}'")
        assert "1" in nav_bell_badge.inner_text()

        # Step 4: Maintainer enters document and replies to the issue
        print("[E2E-NOTIFICATIONS] Maintainer entering document...")
        page.goto(f"{BASE_URL}/reviews/{task_id}/documents/{doc_id}")
        page.wait_for_selector('.issue-card')
        page.wait_for_timeout(1000)

        # Click on the new issue card to open drawer
        print("[E2E-NOTIFICATIONS] Clicking on newly created issue...")
        new_issue_card = page.locator(f'.issue-card:has-text("实时通知测试问题_{ts}")')
        new_issue_card.click()
        page.wait_for_selector('.issue-detail-drawer')

        # Submit official answer
        print("[E2E-NOTIFICATIONS] Submitting official answer...")
        page.fill('.reply-textarea', '这是责任人的官方答复，问题已澄清，请验收。')
        page.click('.reply-submit-btn')
        page.wait_for_timeout(1500)

        # Step 5: Maintainer returns to 线上评审 workspace
        print("[E2E-NOTIFICATIONS] Returning to 线上评审 workspace...")
        page.goto(f"{BASE_URL}/reviews")
        page.wait_for_selector('.review-task-card')
        page.wait_for_timeout(1000)

        # Verify that:
        # 1. 待我解决 is 0!
        # 2. 未读 pill is 0 / hidden!
        # 3. Top nav bell badge is 0 / hidden!
        finance_card = page.locator('.review-task-card:has-text("公开演示：财务凭证与账簿需求")')
        todo_text = finance_card.locator('.stat-pill:has-text("待我解决")').inner_text()
        print(f"[E2E-NOTIFICATIONS] After replying, todo stat: '{todo_text}'")
        assert "待我解决: 0" in todo_text, f"Expected '待我解决: 0' after replying, got '{todo_text}'"

        unread_pill = finance_card.locator('.unread-pill')
        assert unread_pill.count() == 0, f"Expected 0 unread pills after replying, got {unread_pill.count()}"

        nav_bell_badge = page.locator('.notification-action .notification-badge')
        assert nav_bell_badge.count() == 0, f"Expected 0 nav bell badge after replying, got {nav_bell_badge.count()}"

        # Capture screenshot 34
        save_screenshot(page, "34_maintainer_replied_auto_clears_unread_and_pending.png")

        context.close()
        browser.close()

    print("[E2E-NOTIFICATIONS] ALL NOTIFICATIONS & PENDING COUNTS CHECKS PASSED PERFECTLY!")

if __name__ == "__main__":
    if requests is None or sync_playwright is None:
        print("[E2E-NOTIFICATIONS] requests or playwright not installed, skipping test.")
        sys.exit(0)
    run()
