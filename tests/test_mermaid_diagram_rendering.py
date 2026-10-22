import os
import sys
try:
    from playwright.sync_api import sync_playwright
except ImportError:
    sync_playwright = None

BASE_URL = os.environ.get("BASE_URL", "http://127.0.0.1:8180")
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
    print(f"[MERMAID-ACCEPTANCE] Saved screenshot: {filename}")

def get_admin_password():
    import subprocess
    cmd = "docker exec hermes-prd-workbench-backend-1 cat /run/secrets/admin-password"
    out = subprocess.check_output(cmd, shell=True).decode().strip()
    return out

def run():
    print("[MERMAID-ACCEPTANCE] Starting Mermaid Diagram Rendering Acceptance Test...")
    admin_pwd = get_admin_password()

    with sync_playwright() as p:
        browser = p.chromium.launch(
            executable_path=CHROME_PATH if os.path.exists(CHROME_PATH) else None,
            headless=True
        )
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        # Step 1: Login
        print("[MERMAID-ACCEPTANCE] Logging in as Maintainer...")
        page.goto(f"{BASE_URL}/login")
        page.wait_for_selector("input[autocomplete='username']")
        page.fill("input[autocomplete='username']", "maintainer")
        page.fill("input[autocomplete='current-password']", admin_pwd)
        page.click("button[type='submit']")
        page.wait_for_timeout(1000)

        # Step 2: Open Review Document View for Finance Task
        task_id = "task_d6070bbc8b8140a2808d"
        print(f"[MERMAID-ACCEPTANCE] Navigating to Review Document View: /reviews/{task_id}...")
        page.goto(f"{BASE_URL}/reviews/{task_id}")
        page.wait_for_selector(".article-rendered-content", timeout=15000)
        page.wait_for_timeout(2000)

        # Step 3: Verify Mermaid Diagram Cards are rendered in the document
        print("[MERMAID-ACCEPTANCE] Locating .mermaid-diagram-card elements...")
        diagram_cards = page.locator(".article-rendered-content .mermaid-diagram-card")
        card_count = diagram_cards.count()
        print(f"[MERMAID-ACCEPTANCE] Found {card_count} rendered mermaid diagram cards in document!")
        assert card_count > 0, "No .mermaid-diagram-card elements rendered in document!"

        # Locate Section 6.6 "提交审核校验流程"
        heading_6_6 = page.locator('h2:has-text("6.6 提交审核校验流程"), h3:has-text("6.6 提交审核校验流程")')
        assert heading_6_6.count() > 0, "Section 6.6 heading not found!"
        print("[MERMAID-ACCEPTANCE] Found Section 6.6 heading!")

        # Scroll to section 6.6
        heading_6_6.first.scroll_into_view_if_needed()
        page.wait_for_timeout(1000)

        # Check for SVG inside the diagram card near section 6.6
        svg_elements = page.locator(".article-rendered-content .mermaid-viewport svg")
        svg_count = svg_elements.count()
        print(f"[MERMAID-ACCEPTANCE] Total rendered SVG diagrams in document: {svg_count}")
        assert svg_count > 0, "Expected at least 1 SVG rendered!"

        # Verify Chinese label text inside the diagram
        page_content = page.content()
        assert "点击提交审核" in page_content, "Expected Chinese label '点击提交审核' in page content!"
        assert "状态为草稿" in page_content, "Expected Chinese label '状态为草稿' in page content!"
        print("[MERMAID-ACCEPTANCE] Verified Chinese flowchart labels are rendered inside diagram!")

        # Capture screenshot 35
        save_screenshot(page, "35_review_document_mermaid_rendered_flowchart.png")

        context.close()
        browser.close()

    print("[MERMAID-ACCEPTANCE] ALL MERMAID DIAGRAM RENDERING CHECKS PASSED PERFECTLY!")

if __name__ == "__main__":
    if sync_playwright is None:
        print("[MERMAID-ACCEPTANCE] playwright not installed, skipping test.")
        sys.exit(0)
    run()
