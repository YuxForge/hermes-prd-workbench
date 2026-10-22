import { describe, expect, it } from "vitest";
import {
  computeFingerprint,
  locateAnchorInText,
  findLineRangeForSelection,
  type LocatedAnchorResult,
} from "./reviewAnnotationAdapter";

describe("reviewAnnotationAdapter", () => {
  it("computes reproducible hash fingerprint", async () => {
    const hash1 = await computeFingerprint("采购审批必须经过财务总监确认");
    const hash2 = await computeFingerprint("采购审批必须经过财务总监确认");
    expect(hash1).toBe(hash2);
    expect(typeof hash1).toBe("string");
    expect(hash1.length).toBeGreaterThanOrEqual(8);
  });

  it("locates exact match when text and offsets are unchanged", () => {
    const text = "第一条：金额超过8600元时，系统必须自动分流至财务审批流。";
    const quote = "金额超过8600元时";
    const start = text.indexOf(quote);
    const end = start + quote.length;

    const res: LocatedAnchorResult = locateAnchorInText(text, {
      revisionId: "rev_1",
      rendererFingerprint: "fp",
      renderedTextHash: "hash",
      exactQuote: quote,
      prefixText: "第一条：",
      suffixText: "，系统必须",
      startOffset: start,
      endOffset: end,
    });

    expect(res.found).toBe(true);
    expect(res.exactMatch).toBe(true);
    expect(res.startOffset).toBe(start);
    expect(res.endOffset).toBe(end);
  });

  it("relocates correctly via prefix and suffix context when offset shifted", () => {
    const originalText = "第1节 金额超过8600元需要财务审批。";
    const newText = "【新增前言说明】第1节 金额超过8600元需要财务审批。";
    const quote = "金额超过8600元";

    const res = locateAnchorInText(newText, {
      revisionId: "rev_1",
      rendererFingerprint: "fp",
      renderedTextHash: "hash",
      exactQuote: quote,
      prefixText: "第1节 ",
      suffixText: "需要财务审批",
      startOffset: 4, // old offset before shifting
      endOffset: 4 + quote.length,
    });

    expect(res.found).toBe(true);
    expect(res.exactMatch).toBe(false);
    expect(newText.slice(res.startOffset, res.endOffset)).toBe(quote);
  });

  it("relocates to closest occurrence when context shifted but quote exists", () => {
    const text = "提示：8600元需要审批。另一处：金额8600元需要审批。";
    const quote = "8600元";

    const res = locateAnchorInText(text, {
      revisionId: "rev_1",
      rendererFingerprint: "fp",
      renderedTextHash: "hash",
      exactQuote: quote,
      prefixText: "",
      suffixText: "",
      startOffset: 3,
      endOffset: 3 + quote.length,
    });

    expect(res.found).toBe(true);
    expect(res.startOffset).toBe(3);
  });

  it("returns found false when quote was deleted from text", () => {
    const text = "第1节 采购流程全面精简，无需特殊审批。";
    const quote = "金额超过8600元";

    const res = locateAnchorInText(text, {
      revisionId: "rev_1",
      rendererFingerprint: "fp",
      renderedTextHash: "hash",
      exactQuote: quote,
      prefixText: "第1节 ",
      suffixText: "需要",
      startOffset: 4,
      endOffset: 15,
    });

    expect(res.found).toBe(false);
    expect(res.startOffset).toBe(-1);
  });

  it("findLineRangeForSelection accurately maps user selection to markdown lines", () => {
    const md = `# 智能财务PRD

| G5 | 状态留存 | 详情与操作日志记录每一次状态变更 |
| G6 | 借贷平衡 | 借贷不等时提交被拦截并给出差异提示 |

## 1.4 范围

本期纳入（与 data/catalog.json 一致）：

- 凭证管理 (voucher): 凭证列表、录入编辑、详情与记账。
- 账簿查询 (ledger): 明细账查询、总账查询。
- 凭证操作日志 (voucher-log): 操作日志列表。

## 1.5 本期不做（非目标）

- 税务申报。`;

    // Multi-line selection under 1.4 Scope
    const sel = `1.4 范围
本期纳入（与 data/catalog.json 一致）：
凭证管理 (voucher): 凭证列表、录入编辑、详情与记账。
账簿查询 (ledger): 明细账查询、总账查询。
凭证操作日志 (voucher-log): 操作日志列表。`;

    const r1 = findLineRangeForSelection(md, sel);
    expect(r1.start).toBe(6); // line 6 is ## 1.4 范围
    expect(r1.end).toBe(12); // line 12 is - 凭证操作日志

    // Single item in table
    const r2 = findLineRangeForSelection(md, "借贷不等时提交被拦截");
    expect(r2.start).toBe(4);
    expect(r2.end).toBe(4);

    // Single list item
    const r3 = findLineRangeForSelection(md, "账簿查询 (ledger): 明细账查询");
    expect(r3.start).toBe(11);
    expect(r3.end).toBe(11);
  });
});

