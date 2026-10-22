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
    print(f"[V050-ACCEPTANCE] {msg}", flush=True)

def save_screenshot(page, filename):
    p1 = os.path.join(ARTIFACTS_DIR, filename)
    page.screenshot(path=p1, full_page=True)
    if CONVERSATION_SCREENSHOTS_DIR:
        p2 = os.path.join(CONVERSATION_SCREENSHOTS_DIR, filename)
        page.screenshot(path=p2, full_page=True)
    log(f"Saved screenshot: {filename}")

def main():
    log("Starting V0.5.0 Release Acceptance Verification...")
    ts = int(time.time())
    owner_user = f"owner_v5_{ts}"
    candidate_user1 = f"candidate_pm_{ts}"
    candidate_user2 = f"candidate_dev_{ts}"
    password = f"P@ss_{ts}"

    log("Registering test users...")
    r_owner = requests.post(f"{API_BASE}/auth/register", json={
        "username": owner_user, "password": password, "displayName": "余总 (产研负责人)"
    }).json()
    owner_token = r_owner["token"]
    owner_id = r_owner["user"]["id"]

    r_c1 = requests.post(f"{API_BASE}/auth/register", json={
        "username": candidate_user1, "password": password, "displayName": "李敏 (资深产品设计)"
    }).json()

    r_c2 = requests.post(f"{API_BASE}/auth/register", json={
        "username": candidate_user2, "password": password, "displayName": "王强 (前端架构师)"
    }).json()

    # Create a task and review document
    task_res = requests.post(f"{API_BASE}/tasks", headers={"Authorization": f"Bearer {owner_token}"}, json={
        "title": f"智能合同协同审批系统 PRD评审 {ts}"
    }).json()
    task_id = task_res["id"]
    log(f"Created task: {task_id}")

    doc_res = requests.post(f"{API_BASE}/review/tasks/{task_id}/documents", headers={"Authorization": f"Bearer {owner_token}"}, json={
        "title": "智能合同协同审批系统 PRD 需求文档",
        "content": """# 智能合同协同审批系统 PRD

## 1. 业务目标
实现企业合同从拟定、合规审查到电子签署的全生命周期线上闭环。

## 2. 核心业务流程
### 2.1 合同拟定与模板套用
支持标准模板选择，自动抽取关键条款与责任主体。

### 2.2 风险条款自动识别
集成大模型语义检测引擎，针对高风险免责条款进行高亮标注。
""",
        "summary": "初稿提交评审",
        "changeType": "INITIAL"
    }).json()
    doc_id = doc_res["document"]["id"]
    log(f"Created review doc: {doc_id}")

    # Launch browser
    with sync_playwright() as p:
        browser = p.chromium.launch(
            executable_path=CHROME_PATH if os.path.exists(CHROME_PATH) else None,
            headless=True
        )
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        # Step 1: Login
        log("Logging in via web UI...")
        page.goto(f"{BASE_URL}/login")
        page.wait_for_selector("input[autocomplete='username']")
        page.fill("input[autocomplete='username']", owner_user)
        page.fill("input[autocomplete='current-password']", password)
        page.click("button[type='submit']")
        page.wait_for_url(f"{BASE_URL}/")
        page.wait_for_timeout(1000)

        # Step 2: Test Version Update Log Modal
        log("Testing Version Update Log Modal (V0.5.0-beta.1)...")
        update_log_btn = page.locator(".topbar-action.update-log-action")
        update_log_btn.click()
        page.wait_for_selector(".official-changelog-dialog", state="visible")
        page.wait_for_timeout(600)

        card_text = page.locator(".official-changelog-dialog").inner_text()
        log(f"Dialog inner_text length: {len(card_text)}")
        log(f"Dialog snippet: {card_text[:500]}")
        assert "V0.5.0-beta.1" in card_text, "V0.5.0-beta.1 version string missing in modal"
        assert "线上需求评审协同与体验重构重要更新" in card_text, "Release title missing in modal"
        assert "线上评审独立入口与协同工作台" in card_text, "Feature item missing in modal"
        assert "模糊搜索" in card_text, "Search feature missing in modal"

        save_screenshot(page, "22_update_log_v050_modal.png")

        # Close update log modal
        page.keyboard.press("Escape")
        page.wait_for_timeout(500)

        # Step 3: Test Topbar Primary Navigation
        log("Testing Primary Module Navigation...")
        primary_nav = page.locator(".primary-module-nav")
        assert primary_nav.is_visible(), "Primary module nav missing"
        assert page.locator(".primary-nav-tab.active").inner_text().strip() == "PRD 工作台"

        # Click Online Review tab
        review_tab = page.locator(".primary-nav-tab.review-tab")
        assert review_tab.is_visible(), "Review tab missing"
        review_tab.click()
        page.wait_for_url(f"{BASE_URL}/reviews")
        page.wait_for_timeout(1000)

        # Verify on /reviews page that the review tab is now active
        assert page.locator(".primary-nav-tab.review-tab.active").is_visible(), "Review tab not active on /reviews"
        save_screenshot(page, "23_workbench_primary_nav_online_review.png")

        # Step 4: Open the document review page
        log("Navigating to document review page...")
        page.goto(f"{BASE_URL}/reviews/{task_id}/documents/{doc_id}")
        page.wait_for_selector(".review-doc-page")
        page.wait_for_timeout(1000)

        # Step 5: Test Member Management & Fuzzy Search Autocomplete
        log("Testing Member Management Modal and Fuzzy Search...")
        members_btn = page.locator("button:has-text('成员权限')")
        assert members_btn.is_visible(), "Members button missing"
        members_btn.click()
        page.wait_for_selector(".modal-card:has-text('项目成员与权限分配')")
        page.wait_for_timeout(500)

        # Verify search input is present
        search_input = page.locator(".member-search-input")
        assert search_input.is_visible(), "Member search input missing"

        # Type keyword to search for candidate_pm
        log(f"Searching candidate with keyword '李敏'...")
        search_input.click()
        search_input.fill("李敏")
        page.wait_for_timeout(600)

        # Wait for candidate dropdown
        page.wait_for_selector(".candidate-dropdown")
        page.wait_for_timeout(400)

        dropdown_text = page.locator(".candidate-dropdown").inner_text()
        assert "李敏" in dropdown_text, f"Candidate 李敏 not in dropdown: {dropdown_text}"
        assert f"@{candidate_user1}" in dropdown_text, f"Username @{candidate_user1} not in dropdown"

        save_screenshot(page, "24_member_fuzzy_search_autocomplete_dropdown.png")

        # Click candidate item to select
        log("Selecting candidate from dropdown...")
        candidate_item = page.locator(f".candidate-item:has-text('{candidate_user1}')")
        candidate_item.click()
        page.wait_for_timeout(400)

        # Select role 'EDITOR'
        role_select = page.locator(".member-role-select")
        role_select.select_option("EDITOR")

        # Submit add member
        log("Submitting add member...")
        add_btn = page.locator(".member-search-form .add-btn")
        add_btn.click()
        page.wait_for_timeout(1000)

        # Verify member table now includes 李敏
        table_text = page.locator(".members-table").inner_text()
        assert "李敏" in table_text, "Newly added member not in members table"
        assert f"@{candidate_user1}" in table_text, "Newly added member username not in table"
        assert "编辑者" in table_text, "Newly added member role not in table"

        # Now test searching by username handle for candidate_dev
        log(f"Searching candidate with handle '{candidate_user2}'...")
        search_input.fill(candidate_user2)
        page.wait_for_timeout(600)
        page.wait_for_selector(".candidate-dropdown")
        assert "王强" in page.locator(".candidate-dropdown").inner_text()
        page.locator(f".candidate-item:has-text('{candidate_user2}')").click()
        add_btn.click()
        page.wait_for_timeout(1000)

        # Verify both members exist
        table_text_final = page.locator(".members-table").inner_text()
        assert "王强" in table_text_final
        log("Successfully added both candidate users via fuzzy search autocomplete!")

        save_screenshot(page, "25_member_added_successfully.png")

        browser.close()
        log("ALL V0.5.0 ACCEPTANCE CHECKS PASSED!")

if __name__ == "__main__":
    if requests is None or sync_playwright is None:
        print("[V0.5.0-ACCEPTANCE] requests or playwright not installed, skipping test.")
        sys.exit(0)
    main()
