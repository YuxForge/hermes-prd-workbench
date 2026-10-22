import { describe, expect, it } from "vitest";
import { CURRENT_UPDATE_LOG, UPDATE_LOG_HISTORY, updateLogSeenKey } from "./updateLog";
describe("public update log", () => {
  it("uses only the public release history", () => {
    expect(CURRENT_UPDATE_LOG.version).toBe("V0.5.0-beta.1");
    expect(CURRENT_UPDATE_LOG.status).toBe("PREVIEW");
    expect(CURRENT_UPDATE_LOG.summary).toContain("重大功能发布");
    expect(UPDATE_LOG_HISTORY.map((entry) => entry.version)).toEqual(["V0.4.0-beta.2", "V0.4.0-beta.1"]);
    expect(CURRENT_UPDATE_LOG.items.length).toBeGreaterThan(0);
  });
  it("scopes viewed state by user", () => {
    expect(updateLogSeenKey("user_1")).toBe("prd-workbench-updates-seen:user_1");
    expect(updateLogSeenKey(null)).toBe("prd-workbench-updates-seen:anonymous");
  });
});
