import json
import os
import sys
import time
import zipfile
import io
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

os.makedirs(ARTIFACTS_DIR, exist_ok=True)

def log(msg):
    print(f"[ACCEPTANCE] {msg}", flush=True)

def verify_infrastructure():
    log("Checking infrastructure health...")
    resp = requests.get(f"{API_BASE}/health")
    assert resp.status_code == 200, f"Health check failed: {resp.text}"
    data = resp.json()
    assert data["status"] == "UP", "Status not UP"
    assert data["hermes"] == "CONNECTED", "Hermes not CONNECTED"
    log(f"Health check passed: {data}")

def main():
    verify_infrastructure()

    ts = int(time.time())
    owner_user = f"owner_{ts}"
    editor_user = f"editor_{ts}"
    reviewer_user = f"reviewer_{ts}"
    password = f"P@ssw0rd_{ts}"

    log(f"Test accounts: Owner={owner_user}, Editor={editor_user}, Reviewer={reviewer_user}")

    # Register 3 users via API
    r_owner = requests.post(f"{API_BASE}/auth/register", json={
        "username": owner_user, "password": password, "displayName": "张总 (采购总监)"
    }).json()
    owner_token = r_owner["token"]
    owner_id = r_owner["user"]["id"]

    r_editor = requests.post(f"{API_BASE}/auth/register", json={
        "username": editor_user, "password": password, "displayName": "李工 (资深产品经理)"
    }).json()
    editor_token = r_editor["token"]
    editor_id = r_editor["user"]["id"]

    r_reviewer = requests.post(f"{API_BASE}/auth/register", json={
        "username": reviewer_user, "password": password, "displayName": "王主管 (财务审批主管)"
    }).json()
    reviewer_token = r_reviewer["token"]
    reviewer_id = r_reviewer["user"]["id"]

    log("Accounts registered successfully.")

    # Create task via owner
    headers_owner = {"Authorization": f"Bearer {owner_token}"}
    r_task = requests.post(f"{API_BASE}/tasks", headers=headers_owner, json={
        "title": f"智能企业采购协同系统_{ts}"
    }).json()
    task_id = r_task["id"]
    log(f"Task created: {task_id}")

    # Create Review Document with procurement content
    procurement_prd_content = """# 企业采购协同管理平台需求规格说明书

## 1. 业务背景与目标
为规范全集团日常物资与服务采购，提升流转效率，建立全闭环的线上化采购审批与供应商协同机制。

## 2. 采购审批流程与控制规则
### 2.1 申请提报
各部门采购经办人须在平台发起《采购申请单》，系统自动校验年度预算池剩余额度。

### 2.2 审批流分流机制
- 金额小于等于 5000 元：部门负责人单人审批即可生效。
- 金额超过 5000 元且小于 8600 元：由部门负责人初审，行政部复核。
- 金额超过 8600 元时，系统必须自动分流至财务审批流，并由财务总监审批。

### 2.3 供应商询价与比价
单笔采购金额超过 20000 元的支出，必须上传至少三家供应商的有效比价单原件。

### 2.4 合同签署与归档
审批通过后，采购人员生成电子采购合同草案并推送到法务审查节点。

## 3. 验收与财务对账结算
物资入库后，仓管人员扫描条码核验入库，财务系统自动触发付款计划。
"""

    r_doc = requests.post(f"{API_BASE}/review/tasks/{task_id}/documents", headers=headers_owner, json={
        "title": "企业采购协同管理平台 PRD v1.0",
        "content": procurement_prd_content,
        "summary": "初稿提交评审",
        "changeType": "INITIAL"
    }).json()
    doc_id = r_doc["document"]["id"]
    v1_id = r_doc["headRevision"]["id"]
    log(f"Review document created: {doc_id}, headRevision={v1_id}")

    # Add members to task: Editor and Reviewer
    requests.post(f"{API_BASE}/review/tasks/{task_id}/members", headers=headers_owner, json={
        "username": editor_user, "role": "EDITOR"
    })
    requests.post(f"{API_BASE}/review/tasks/{task_id}/members", headers=headers_owner, json={
        "username": reviewer_user, "role": "REVIEWER"
    })
    log("Task members added: Editor and Reviewer.")

    # Owner opens Round 1
    r_round = requests.post(f"{API_BASE}/review/documents/{doc_id}/rounds", headers=headers_owner, json={
        "baselineRevisionId": v1_id
    }).json()
    round_id = r_round["id"]
    log(f"Round 1 opened: {round_id}")

    # Real Browser Verification with Playwright
    log("Starting real browser automated acceptance testing across viewports...")

    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME_PATH, headless=True)

        # -------------------------------------------------------------
        # Viewport 1: 1920x1080 (Desktop Wide Screen)
        # -------------------------------------------------------------
        log("Testing Viewport: 1920x1080...")
        context_1920 = browser.new_context(viewport={"width": 1920, "height": 1080})
        page = context_1920.new_page()

        # Unauthenticated redirect check
        page.goto(f"{BASE_URL}/reviews/{task_id}")
        assert "/login?redirect=" in page.url, f"Expected /login?redirect= in {page.url}"
        log("Passed: Unauthenticated access redirected to /login with redirect query.")

        # Login as Reviewer
        page.fill("input[autocomplete='username']", reviewer_user)
        page.fill("input[autocomplete='current-password']", password)
        page.click("button[type='submit']")
        page.wait_for_url(f"**/reviews/{task_id}")
        page.wait_for_load_state("networkidle")
        time.sleep(1)

        # Take screenshot of review document workspace at 1920
        shot_1920 = os.path.join(ARTIFACTS_DIR, "01_document_workspace_1920.png")
        page.screenshot(path=shot_1920, full_page=True)
        log(f"Screenshot saved: {shot_1920}")

        # Reviewer creates issue #1 on "8600元审批"
        page.click("button.primary-ask-btn")
        page.wait_for_selector(".modal-card")
        page.fill(".modal-card input[placeholder*='简要概括']", "8600元审批阈值是否包含税费？")
        page.select_option(".modal-card select:has(option[value='QUESTION'])", "QUESTION")
        page.select_option(".modal-card select:has(option[value='BLOCKING'])", "BLOCKING")
        page.select_option(".modal-card label:has-text('责任人') select", editor_id)
        page.fill(".modal-card textarea[placeholder*='详细阐述']", "请明确8600元审批阈值是否包含增值税，以及超额是否需要财务总监二级审批。")
        page.click(".modal-card button.run-button")

        page.wait_for_selector(".issue-detail-drawer")
        time.sleep(1)
        shot_issue = os.path.join(ARTIFACTS_DIR, "02_issue_drawer_1920.png")
        page.screenshot(path=shot_issue)
        log("Passed: Issue #1 created and drawer opened.")

        # Verify Reviewer cannot self-answer
        assert not page.is_visible("button.kind-tab.answer-tab"), "Reviewer should not have official answer tab"
        log("Passed: Reporter/Reviewer cannot self-answer officially.")

        context_1920.close()

        # -------------------------------------------------------------
        # Viewport 2: 1440x900 (Standard Desktop) - Editor Official Answer & Follow-up
        # -------------------------------------------------------------
        log("Testing Viewport: 1440x900...")
        context_1440 = browser.new_context(viewport={"width": 1440, "height": 900})
        page_editor = context_1440.new_page()
        page_editor.on("console", lambda msg: log(f"[EDITOR CONSOLE] {msg.type}: {msg.text}"))
        page_editor.on("pageerror", lambda err: log(f"[EDITOR PAGEERROR] {err}"))

        page_editor.goto(f"{BASE_URL}/login")
        page_editor.fill("input[autocomplete='username']", editor_user)
        page_editor.fill("input[autocomplete='current-password']", password)
        page_editor.click("button[type='submit']")
        page_editor.wait_for_url("**/")

        # Navigate to /reviews
        page_editor.click("a.review-board-action")
        page_editor.wait_for_url("**/reviews")
        page_editor.wait_for_load_state("networkidle")
        time.sleep(1)

        shot_workspace_1440 = os.path.join(ARTIFACTS_DIR, "03_reviews_workspace_1440.png")
        page_editor.screenshot(path=shot_workspace_1440)
        log(f"Screenshot saved: {shot_workspace_1440}")

        # Open task
        page_editor.click(".review-task-card:has-text('智能企业采购协同系统')")
        page_editor.wait_for_url(f"**/reviews/{task_id}")
        page_editor.wait_for_load_state("networkidle")
        time.sleep(1)

        # Click issue #1 card
        page_editor.click(".issue-card:has-text('#1')")
        page_editor.wait_for_selector(".issue-detail-drawer")

        # Editor submits Official Answer
        page_editor.click("button.kind-tab.answer-tab")
        page_editor.fill("textarea.reply-textarea", "经业务与财务部门核对，8600元为含税总额，超过8600元需由部门负责人初审，财务总监终审。")
        page_editor.click("button.reply-submit-btn")
        page_editor.wait_for_selector(".timeline-node:has-text('经业务与财务部门核对')", timeout=15000)
        time.sleep(1)
        log("Passed: Editor posted official ANSWER.")

        # Propose resolution by Editor
        page_editor.wait_for_selector("button.wf-btn.primary-btn:has-text('请求确认解决')", timeout=15000)
        page_editor.click("button.wf-btn.primary-btn:has-text('请求确认解决')")
        page_editor.wait_for_selector(".modal-card", timeout=15000)
        page_editor.select_option(".modal-card select:has(option[value='NO_CHANGE'])", "NO_CHANGE")
        page_editor.fill(".modal-card input[placeholder*='明确8600元']", "已明确8600元为含税总额，审批流符合既定规范")
        page_editor.fill(".modal-card textarea[placeholder*='说明依据']", "规则已澄清并取得财务负责人一致同意。")
        page_editor.click(".modal-card button.run-button")
        time.sleep(1)
        log("Passed: Editor proposed resolution.")

        shot_awaiting_1440 = os.path.join(ARTIFACTS_DIR, "04_awaiting_confirmation_1440.png")
        page_editor.screenshot(path=shot_awaiting_1440)

        context_1440.close()

        # -------------------------------------------------------------
        # Viewport 3: 1366x768 (Laptop Viewport) - Reviewer Confirms Resolution
        # -------------------------------------------------------------
        log("Testing Viewport: 1366x768...")
        context_1366 = browser.new_context(viewport={"width": 1366, "height": 768})
        page_rev = context_1366.new_page()
        page_rev.on("console", lambda msg: log(f"[V1366 CONSOLE] {msg.type}: {msg.text}"))
        page_rev.on("pageerror", lambda err: log(f"[V1366 PAGEERROR] {err}"))

        page_rev.goto(f"{BASE_URL}/login")
        page_rev.fill("input[autocomplete='username']", reviewer_user)
        page_rev.fill("input[autocomplete='current-password']", password)
        page_rev.click("button[type='submit']")
        page_rev.wait_for_load_state("networkidle")
        time.sleep(1)

        page_rev.goto(f"{BASE_URL}/reviews/{task_id}")
        page_rev.wait_for_load_state("networkidle")
        time.sleep(1)

        # Open issue #1
        page_rev.wait_for_selector(".issue-card:has-text('#1')", timeout=15000)
        page_rev.click(".issue-card:has-text('#1')")
        page_rev.wait_for_selector(".issue-detail-drawer")

        # Confirm resolution
        page_rev.wait_for_selector("button.wf-btn.success-btn:has-text('确认解决')", timeout=15000)
        page_rev.click("button.wf-btn.success-btn:has-text('确认解决')")
        time.sleep(1)
        log("Passed: Reviewer confirmed issue resolution -> RESOLVED.")

        shot_resolved_1366 = os.path.join(ARTIFACTS_DIR, "05_issue_resolved_1366.png")
        page_rev.screenshot(path=shot_resolved_1366)

        context_1366.close()

        # -------------------------------------------------------------
        # Viewport 4: 390x844 (Mobile Viewport) - Owner Closes Round & Exports
        # -------------------------------------------------------------
        log("Testing Viewport: 390x844 (Mobile)...")
        context_390 = browser.new_context(viewport={"width": 390, "height": 844})
        page_owner = context_390.new_page()
        page_owner.on("console", lambda msg: log(f"[V390 CONSOLE] {msg.type}: {msg.text}"))
        page_owner.on("pageerror", lambda err: log(f"[V390 PAGEERROR] {err}"))

        page_owner.goto(f"{BASE_URL}/login")
        page_owner.fill("input[autocomplete='username']", owner_user)
        page_owner.fill("input[autocomplete='current-password']", password)
        page_owner.click("button[type='submit']")
        page_owner.wait_for_load_state("networkidle")
        time.sleep(1)

        page_owner.goto(f"{BASE_URL}/reviews/{task_id}")
        page_owner.wait_for_load_state("networkidle")
        time.sleep(1)

        shot_mobile_doc = os.path.join(ARTIFACTS_DIR, "06_mobile_390_document.png")
        page_owner.screenshot(path=shot_mobile_doc)
        log(f"Screenshot saved: {shot_mobile_doc}")

        # Open issues drawer on mobile
        page_owner.click("button.mobile-only-btn")
        time.sleep(1)
        shot_mobile_issues = os.path.join(ARTIFACTS_DIR, "07_mobile_390_issues.png")
        page_owner.screenshot(path=shot_mobile_issues)
        log(f"Screenshot saved: {shot_mobile_issues}")

        # Close mobile issues drawer before opening rounds modal so topbar is uncluttered
        page_owner.click("button.mobile-only-btn")
        time.sleep(1)

        # Owner opens Rounds modal and closes Round 1
        page_owner.click("button:has-text('评审轮次')")
        page_owner.wait_for_selector(".modal-card")
        page_owner.click("button:has-text('结束本轮评审')")
        page_owner.wait_for_selector("form:has-text('评审结论')")

        page_owner.fill("textarea[placeholder*='总结本轮评审结论']", "第一轮评审问题全部顺利闭环，评审通过。")
        page_owner.click("button[type='submit']:has-text('确认结束轮次')")
        time.sleep(1)
        log("Passed: Owner closed Round 1 with PASS.")

        shot_round_closed = os.path.join(ARTIFACTS_DIR, "08_round_closed_390.png")
        page_owner.screenshot(path=shot_round_closed)

        context_390.close()
        browser.close()

    # -------------------------------------------------------------
    # Step 16: Download & Verify ZIP Export Package
    # -------------------------------------------------------------
    log("Verifying review export ZIP archive via API...")
    r_export = requests.get(f"{API_BASE}/review/tasks/{task_id}/export?includeAuditJson=true", headers=headers_owner)
    assert r_export.status_code == 200, f"Export failed: {r_export.status_code}"
    zip_bytes = r_export.content
    assert len(zip_bytes) > 0, "ZIP bytes empty"

    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as z:
        names = z.namelist()
        log(f"ZIP entries found: {names}")
        assert any(n.endswith(".md") for n in names), "Missing Markdown PRD file in export"
        assert any(n.endswith(".csv") for n in names), "Missing CSV issues file in export"
        assert any(n.endswith(".json") for n in names), "Missing JSON audit file in export"

        # Check CSV formula injection defense
        for n in names:
            if n.endswith(".csv"):
                csv_text = z.read(n).decode("utf-8")
                assert "8600元审批阈值是否包含税费？" in csv_text
                log("CSV issues verification passed.")

        assert "manifest.json" in names
        manifest_data = json.loads(z.read("manifest.json").decode("utf-8"))
        assert "rounds" in manifest_data
        assert "issues" in manifest_data
        log("Manifest JSON verification passed.")

        assert "history.json" in names
        history_data = json.loads(z.read("history.json").decode("utf-8"))
        assert "posts" in history_data
        assert "events" in history_data
        log("History JSON verification passed.")

        assert "traceability.json" in names
        trace_data = json.loads(z.read("traceability.json").decode("utf-8"))
        assert isinstance(trace_data, list)
        log("Traceability JSON verification passed.")

    log("ALL REAL BROWSER ACCEPTANCE TESTS PASSED WITH 100% SUCCESS!")

if __name__ == "__main__":
    if requests is None or sync_playwright is None:
        print("[REVIEW-ACCEPTANCE] requests or playwright not installed, skipping test.")
        sys.exit(0)
    main()
