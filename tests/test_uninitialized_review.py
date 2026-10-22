import os
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
TASK_ID = "task_d6070bbc8b8140a2808d"
CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
ARTIFACTS_DIR = os.path.abspath("tests/artifacts/uninitialized_review")

os.makedirs(ARTIFACTS_DIR, exist_ok=True)

def main():
    print(f"Logging in as maintainer to inspect {TASK_ID}...")
    resp = requests.post(f"{API_BASE}/auth/login", json={
        "username": "maintainer",
        "password": "Maintainer2026!"
    })
    assert resp.status_code == 200, f"Login failed: {resp.text}"
    auth_data = resp.json()
    token = auth_data["token"]
    user = auth_data["user"]

    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            executable_path=CHROME_PATH if os.path.exists(CHROME_PATH) else None,
            args=["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage"]
        )
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        # Pre-seed session
        page.goto(f"{BASE_URL}/")
        page.evaluate(f"""() => {{
            localStorage.setItem("prd-workbench-token", "{token}");
            localStorage.setItem("prd-workbench-user", JSON.stringify({user}));
        }}""")

        # Navigate to the task review workspace
        target_url = f"{BASE_URL}/reviews/{TASK_ID}"
        print(f"Navigating to {target_url}...")
        page.goto(target_url, wait_until="networkidle")
        time.sleep(1)

        screenshot_path = os.path.join(ARTIFACTS_DIR, "01_empty_state_prompt.png")
        page.screenshot(path=screenshot_path, full_page=True)
        print(f"Saved empty state screenshot to: {screenshot_path}")

        # Check empty state elements
        title_el = page.locator(".doc-title-text")
        print("Title text:", title_el.inner_text())

        badge_el = page.locator(".badge-pending-init")
        print("Badge text:", badge_el.inner_text() if badge_el.count() > 0 else "None")

        empty_heading = page.locator(".empty-state-title")
        print("Empty heading:", empty_heading.inner_text() if empty_heading.count() > 0 else "None")

        draft_items = page.locator(".draft-item-card")
        draft_count = draft_items.count()
        print(f"Found {draft_count} draft items in empty state card.")
        for i in range(draft_count):
            print(f"  Draft {i+1}:", draft_items.nth(i).locator(".draft-filename").inner_text())

        # Test clicking "一键导入并发起评审" on the first draft!
        if draft_count > 0:
            print("Clicking '一键导入并发起评审'...")
            import_btn = page.locator(".import-action-btn").first
            import_btn.click()
            time.sleep(2)
            page.wait_for_load_state("networkidle")

            screenshot_imported = os.path.join(ARTIFACTS_DIR, "02_after_draft_imported.png")
            page.screenshot(path=screenshot_imported, full_page=True)
            print(f"Saved imported screenshot to: {screenshot_imported}")

            doc_title = page.locator(".doc-title-text").inner_text()
            print("New document title after import:", doc_title)
            article_text = page.locator(".article-rendered-content").inner_text()
            print("Article content length:", len(article_text))
            assert len(article_text) > 100, f"Expected non-empty article, got length {len(article_text)}"
            print("Successfully imported draft and loaded review document workspace!")

        browser.close()

if __name__ == "__main__":
    if requests is None or sync_playwright is None:
        print("requests or playwright not installed, skipping test.")
        import sys
        sys.exit(0)
    main()
