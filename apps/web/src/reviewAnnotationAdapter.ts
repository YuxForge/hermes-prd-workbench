import type { AnchorInput, ReviewAnchorDetail } from "./reviewApi";

export type LocatedAnchorResult = {
  found: boolean;
  startOffset: number;
  endOffset: number;
  exactMatch: boolean;
};

/**
 * Returns the plain text of the DOM element across text nodes,
 * preserving natural layout text.
 */
export function getElementPlainText(element: HTMLElement): string {
  return element.textContent || "";
}

/**
 * Compute the plain text offset from a DOM Node and inner offset within that node.
 */
export function getNodeCharacterOffset(root: HTMLElement, targetNode: Node, nodeOffset: number): number {
  let charCount = 0;
  const treeWalker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);

  let current = treeWalker.nextNode();
  while (current) {
    if (current === targetNode) {
      return charCount + nodeOffset;
    }
    charCount += current.textContent ? current.textContent.length : 0;
    current = treeWalker.nextNode();
  }

  return charCount;
}

function toUtf8Bytes(str: string): Uint8Array {
  return new TextEncoder().encode(str);
}

function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

/**
 * SHA-256 hex digest of UTF-8 bytes. Falls back to a 32-bit mix only when
 * SubtleCrypto is unavailable (non-secure contexts / unit tests without crypto).
 */
export async function computeFingerprint(str: string): Promise<string> {
  const bytes = toUtf8Bytes(str);
  if (globalThis.crypto?.subtle) {
    const digest = await globalThis.crypto.subtle.digest("SHA-256", bytes.buffer as ArrayBuffer);
    return bytesToHex(new Uint8Array(digest));
  }
  let hash = 0;
  for (let i = 0; i < bytes.length; i++) {
    hash = (hash << 5) - hash + bytes[i];
    hash |= 0;
  }
  return (hash >>> 0).toString(16).padStart(8, "0");
}

/**
 * Computes an AnchorInput from a browser Selection within a container.
 */
export async function computeSelectionAnchor(
  container: HTMLElement,
  selection: Selection,
  revisionId: string
): Promise<AnchorInput | null> {
  if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
    return null;
  }

  const range = selection.getRangeAt(0);
  if (!container.contains(range.startContainer) || !container.contains(range.endContainer)) {
    return null;
  }

  const exactQuote = selection.toString();
  if (!exactQuote || exactQuote.length === 0) {
    return null;
  }

  const startOffset = getNodeCharacterOffset(container, range.startContainer, range.startOffset);
  const endOffset = getNodeCharacterOffset(container, range.endContainer, range.endOffset);

  const fullText = getElementPlainText(container);
  const prefixStart = Math.max(0, startOffset - 32);
  const prefixText = fullText.slice(prefixStart, startOffset);
  const suffixEnd = Math.min(fullText.length, endOffset + 32);
  const suffixText = fullText.slice(endOffset, suffixEnd);

  return {
    revisionId,
    selectorVersion: "v1",
    rendererFingerprint: "browser-dom-v1",
    renderedTextHash: await computeFingerprint(exactQuote),
    exactQuote,
    prefixText,
    suffixText,
    startOffset,
    endOffset,
  };
}

/**
 * Locates an anchor within the plain text of the given container.
 * 1. Checks exact offset range.
 * 2. Checks prefix + exactQuote + suffix.
 * 3. Checks nearest exactQuote substring.
 * 4. Fallback: returns not found.
 */
export function locateAnchorInText(
  fullText: string,
  anchor: ReviewAnchorDetail | AnchorInput
): LocatedAnchorResult {
  const quote = anchor.exactQuote;
  if (!quote) {
    return { found: false, startOffset: -1, endOffset: -1, exactMatch: false };
  }

  const quoteLen = quote.length;

  // 1. Exact match at original offsets
  if (
    anchor.startOffset >= 0 &&
    anchor.endOffset <= fullText.length &&
    fullText.slice(anchor.startOffset, anchor.endOffset) === quote
  ) {
    return {
      found: true,
      startOffset: anchor.startOffset,
      endOffset: anchor.endOffset,
      exactMatch: true,
    };
  }

  // 2. Try prefix + quote + suffix
  if (anchor.prefixText || anchor.suffixText) {
    const combined = (anchor.prefixText || "") + quote + (anchor.suffixText || "");
    const combinedIdx = fullText.indexOf(combined);
    if (combinedIdx !== -1) {
      const matchStart = combinedIdx + (anchor.prefixText ? anchor.prefixText.length : 0);
      return {
        found: true,
        startOffset: matchStart,
        endOffset: matchStart + quoteLen,
        exactMatch: false,
      };
    }
  }

  // 3. Find closest occurrence of exactQuote to startOffset
  let closestStart = -1;
  let minDistance = Infinity;
  let searchIdx = fullText.indexOf(quote);
  while (searchIdx !== -1) {
    const dist = Math.abs(searchIdx - anchor.startOffset);
    if (dist < minDistance) {
      minDistance = dist;
      closestStart = searchIdx;
    }
    searchIdx = fullText.indexOf(quote, searchIdx + 1);
  }

  if (closestStart !== -1) {
    return {
      found: true,
      startOffset: closestStart,
      endOffset: closestStart + quoteLen,
      exactMatch: false,
    };
  }

  return { found: false, startOffset: -1, endOffset: -1, exactMatch: false };
}

/**
 * Creates a DOM Range covering the given character offsets in the container.
 */
export function createDomRangeFromOffsets(
  container: HTMLElement,
  startOffset: number,
  endOffset: number
): Range | null {
  const treeWalker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, null);
  let charCount = 0;
  let startNode: Node | null = null;
  let startInnerOffset = 0;
  let endNode: Node | null = null;
  let endInnerOffset = 0;

  let current = treeWalker.nextNode();
  while (current) {
    const len = current.textContent ? current.textContent.length : 0;
    if (!startNode && charCount + len >= startOffset) {
      startNode = current;
      startInnerOffset = startOffset - charCount;
    }
    if (charCount + len >= endOffset) {
      endNode = current;
      endInnerOffset = endOffset - charCount;
      break;
    }
    charCount += len;
    current = treeWalker.nextNode();
  }

  if (startNode && endNode) {
    try {
      const range = document.createRange();
      range.setStart(startNode, startInnerOffset);
      range.setEnd(endNode, endInnerOffset);
      return range;
    } catch {
      return null;
    }
  }

  return null;
}

/**
 * Strips leading/inline markdown markup syntax to facilitate matching plain text selections.
 */
export function stripMarkdownSyntax(str: string): string {
  return (str || "")
    .replace(/^[\s>#*\-+|\d.]+/g, "")
    .replace(/[#*`_~\[\]()|]+/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

export type LineRangeResult = {
  start: number;
  end: number;
};

/**
 * Accurately determines the 1-based start and end line numbers in markdown source content
 * corresponding to a user's selection quote, using fuzzy syntax stripping and prefix/suffix disambiguation.
 */
export function findLineRangeForSelection(
  content: string,
  quote: string,
  prefix: string = "",
  suffix: string = ""
): LineRangeResult {
  if (!content || !quote) return { start: 1, end: 1 };
  const lines = content.split(/\r?\n/);
  const total = lines.length;

  const quoteLines = quote
    .split(/\r?\n/)
    .map((s) => stripMarkdownSyntax(s))
    .filter((s) => s.length >= 2);

  if (!quoteLines.length) return { start: 1, end: 1 };

  const firstQuote = quoteLines[0];
  const lastQuote = quoteLines[quoteLines.length - 1];

  // Collect candidate start lines
  const candidateStarts: number[] = [];
  for (let i = 0; i < total; i++) {
    const stripped = stripMarkdownSyntax(lines[i]);
    if (!stripped) continue;
    if (stripped.includes(firstQuote) || firstQuote.includes(stripped)) {
      candidateStarts.push(i + 1);
    }
  }

  // If none found with full firstQuote, try key of 6 chars
  if (candidateStarts.length === 0) {
    const key = firstQuote.slice(0, 6);
    for (let i = 0; i < total; i++) {
      const stripped = stripMarkdownSyntax(lines[i]);
      if (stripped.includes(key)) {
        candidateStarts.push(i + 1);
      }
    }
  }

  if (candidateStarts.length === 0) {
    candidateStarts.push(1);
  }

  // If multiple candidates, score using prefix / suffix
  let bestStart = candidateStarts[0];
  let bestScore = -1;

  const cleanPrefix = stripMarkdownSyntax(prefix);
  const cleanSuffix = stripMarkdownSyntax(suffix);

  for (const cand of candidateStarts) {
    let score = 0;
    if (cleanPrefix) {
      const beforeLines = lines
        .slice(Math.max(0, cand - 6), cand - 1)
        .map(stripMarkdownSyntax)
        .join(" ");
      if (beforeLines && cleanPrefix.slice(-8) && beforeLines.includes(cleanPrefix.slice(-8))) {
        score += 10;
      }
    }
    if (cleanSuffix) {
      const afterLines = lines
        .slice(cand, Math.min(total, cand + quoteLines.length + 5))
        .map(stripMarkdownSyntax)
        .join(" ");
      if (afterLines && cleanSuffix.slice(0, 8) && afterLines.includes(cleanSuffix.slice(0, 8))) {
        score += 10;
      }
    }
    if (score > bestScore) {
      bestScore = score;
      bestStart = cand;
    }
  }

  // If single line selected
  if (quoteLines.length === 1) {
    return { start: bestStart, end: bestStart };
  }

  // For multi-line selection, find best endLine starting from bestStart
  let bestEnd = -1;
  const maxSearchLine = Math.min(total, bestStart + quoteLines.length * 2 + 5);
  for (let i = bestStart; i < maxSearchLine; i++) {
    const stripped = stripMarkdownSyntax(lines[i]);
    if (!stripped) continue;
    if (stripped.includes(lastQuote) || lastQuote.includes(stripped)) {
      bestEnd = i + 1;
      break;
    }
  }

  if (bestEnd === -1 || bestEnd < bestStart) {
    const key = lastQuote.slice(-6);
    for (let i = bestStart; i < maxSearchLine; i++) {
      const stripped = stripMarkdownSyntax(lines[i]);
      if (stripped.includes(key)) {
        bestEnd = i + 1;
        break;
      }
    }
  }

  if (bestEnd === -1 || bestEnd < bestStart) {
    bestEnd = Math.min(total, bestStart + quoteLines.length - 1);
  }

  return { start: bestStart, end: bestEnd };
}

