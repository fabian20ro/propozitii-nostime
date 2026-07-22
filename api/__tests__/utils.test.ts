import { describe, it, expect } from "vitest";
import {
  validateSupabaseUrl,
  resolveSupabaseKey,
  resolveSupabaseInit,
  parseAllowedOrigins,
  resolveCorsOrigin,
  normalizeRarityRange,
  capitalizeFirst,
  cleaningDecorator,
  decorateVerse,
  decorateSentence,
  addDexLinks,
  buildResponseTimingHeaders,
  adjForGender,
  safeTimestamp,
  DEXONLINE_URL,
  escapeHtml
} from "../all";

describe("api/all.ts utilities", () => {
  describe("validateSupabaseUrl", () => {
    it("returns undefined for valid URL", () => {
      expect(validateSupabaseUrl("https://example.com")).toBeUndefined();
    });
    it("returns error for invalid protocol", () => {
      expect(validateSupabaseUrl("ftp://example.com")).toContain("Invalid SUPABASE_URL");
    });
    it("returns error for empty string", () => {
      expect(validateSupabaseUrl("")).toBe("Missing SUPABASE_URL.");
    });
    it("returns error for whitespace", () => {
      expect(validateSupabaseUrl("  ")).toBe("Missing SUPABASE_URL.");
    });
  });

  describe("resolveSupabaseKey", () => {
    it("returns publishable key if present", () => {
      const env = { SUPABASE_PUBLISHABLE_KEY: "pub-key" };
      expect(resolveSupabaseKey(env)).toEqual({
        key: "pub-key",
        source: "publishable"
      });
    });
    it("returns service-role key if fallback is enabled", () => {
      const env = {
        SUPABASE_SERVICE_ROLE_KEY: "ser-key",
        ALLOW_SUPABASE_SERVICE_ROLE_FALLBACK: "true"
      };
      expect(resolveSupabaseKey(env)).toEqual({
        key: "ser-key",
        source: "service-role"
      });
    });
    it("returns error if service-role is set but fallback is disabled", () => {
      const env = {
        SUPABASE_SERVICE_ROLE_KEY: "ser-key",
        ALLOW_SUPABASE_SERVICE_ROLE_FALLBACK: "false"
      };
      const res = resolveSupabaseKey(env);
      expect(res.source).toBe("none");
      expect(res.error).toContain("disabled");
    });
    it("returns error for whitespace-only SUPABASE_PUBLISHABLE_KEY", () => {
      const env = { SUPABASE_PUBLISHABLE_KEY: "  " };
      expect(resolveSupabaseKey(env)).toEqual({
        key: "",
        source: "none",
        error: "Missing SUPABASE_PUBLISHABLE_KEY."
      });
    });
    it("returns error if no keys are present", () => {
      expect(resolveSupabaseKey({})).toEqual({
        key: "",
        source: "none",
        error: "Missing SUPABASE_PUBLISHABLE_KEY."
      });
    });
  });

  describe("parseAllowedOrigins", () => {
    it("returns default origins for empty string", () => {
      expect(parseAllowedOrigins("")).toEqual(["https://fabian20ro.github.io"]);
    });
    it("returns trimmed origins for comma-separated list", () => {
      expect(parseAllowedOrigins(" https://a.com , https://b.com ")).toEqual(["https://a.com", "https://b.com"]);
    });
    it("returns default when env variable is undefined (no ALLOWED_ORIGINS set)", () => {
      expect(parseAllowedOrigins(undefined)).toEqual(["https://fabian20ro.github.io"]);
    });
    it("returns defaults when all comma-split items are empty after trim", () => {
      // Source: .split(",").map(trim).filter(Boolean) → [] → falls back to DEFAULT_ALLOWED_ORIGINS.
      expect(parseAllowedOrigins(", , ")).toEqual(["https://fabian20ro.github.io"]);
    });
  });

  describe("resolveCorsOrigin", () => {
    it("returns * if allowlist contains *", () => {
      expect(resolveCorsOrigin("https://a.com", ["*"])).toBe("*");
    });
    it("returns matching origin", () => {
      expect(resolveCorsOrigin("https://a.com", ["https://a.com", "https://b.com"])).toBe("https://a.com");
    });
    it("returns first origin if no match", () => {
      expect(resolveCorsOrigin("https://c.com", ["https://a.com", "https://b.com"])).toBe("https://a.com");
    });
    it("returns first origin if origin is undefined", () => {
      expect(resolveCorsOrigin(undefined, ["https://a.com", "https://b.com"])).toBe("https://a.com");
    });
  });

  describe("text decorators", () => {
    it("capitalizeFirst handles empty string", () => {
      expect(capitalizeFirst("")).toBe("");
    });
    it("capitalizeFirst capitalizes first character", () => {
      expect(capitalizeFirst("hello")).toBe("Hello");
    });
    it("capitalizeFirst handles non-letter start", () => {
      expect(capitalizeFirst("!hello")).toBe("!hello");
    });
    it("cleaningDecorator trims and collapses whitespace", () => {
      expect(cleaningDecorator("  hello    world  ")).toBe("hello world");
    });
    it("cleaningDecorator handles multiple whitespace types", () => {
      expect(cleaningDecorator("  hello\t\tworld  ")).toBe("hello world");
    });
    it("addDexLinks wraps word in correct anchor tag", () => {
      const result = addDexLinks("hello");
      expect(result).toBe('<a href="https://dexonline.ro/definitie/hello" target="_blank" rel="noopener" data-word="hello">hello</a>');
    });
    it("addDexLinks returns empty string for empty input", () => {
      expect(addDexLinks("")).toBe("");
    });
    it("addDexLinks preserves non-letter-only input without generating links", () => {
      const result = addDexLinks("123 !@#");
      expect(result).toBe("123 !@#");
    });
    it("decorateSentence renders all-caps words preserving case in text but lowercase in data attrs", () => {
      const result = decorateSentence("HELLO world");
      expect(result).toBe('<a href="https://dexonline.ro/definitie/hello" target="_blank" rel="noopener" data-word="hello">HELLO</a> <a href="https://dexonline.ro/definitie/world" target="_blank" rel="noopener" data-word="world">world</a>');
    });
    it("decorateSentence handles single-character input", () => {
      const result = decorateSentence("i");
      expect(result).toBe('<a href="https://dexonline.ro/definitie/i" target="_blank" rel="noopener" data-word="i">I</a>');
    });
    it("handles multiple spaces", () => {
      const result = addDexLinks("a  b");
      expect(result).toContain("</a>  <a");
    });
    it("handles accented characters", () => {
      const result = addDexLinks("masă");
      expect(result).toContain('href="https://dexonline.ro/definitie/mas%C4%83"');
      expect(result).toContain(">masă</a");
    });
    it("handles punctuation and apostrophes", () => {
      const result = addDexLinks("it's a test");
      // Should handle it's -> it and s.
      // Given \p{L}+, it should be <a...>it</a>'<a...>s</a> a <a...>test</a>
      expect(result).toContain("<a href=\"https://dexonline.ro/definitie/it\" target=\"_blank\" rel=\"noopener\" data-word=\"it\">it</a>'<a href=\"https://dexonline.ro/definitie/s\" target=\"_blank\" rel=\"noopener\" data-word=\"s\">s</a> <a href=\"https://dexonline.ro/definitie/a\" target=\"_blank\" rel=\"noopener\" data-word=\"a\">a</a> <a href=\"https://dexonline.ro/definitie/test\" target=\"_blank\" rel=\"noopener\" data-word=\"test\">test</a>");
    });

    it("decorateVerse handles multiple lines and capitalization", () => {
      const verse = "hello / world";
      const result = decorateVerse(verse);
      expect(result).toBe('<a href="https://dexonline.ro/definitie/hello" target="_blank" rel="noopener" data-word="hello">Hello</a><br/><a href="https://dexonline.ro/definitie/world" target="_blank" rel="noopener" data-word="world">World</a>');
    });
    it("decorateVerse works without slashes", () => {
      expect(decorateVerse("hello")).toBe('<a href="https://dexonline.ro/definitie/hello" target="_blank" rel="noopener" data-word="hello">Hello</a>');
    });
    it("decorateVerse works without spaces around slashes", () => {
      expect(decorateVerse("hello/world")).toBe('<a href="https://dexonline.ro/definitie/hello" target="_blank" rel="noopener" data-word="hello">Hello</a><br/><a href="https://dexonline.ro/definitie/world" target="_blank" rel="noopener" data-word="world">World</a>');
    });
    it("decorateVerse handles complex whitespace and multiple lines", () => {
      const verse = "  hello   /   world  /  third  ";
      const result = decorateVerse(verse);
      expect(result).toBe('<a href="https://dexonline.ro/definitie/hello" target="_blank" rel="noopener" data-word="hello">Hello</a><br/><a href="https://dexonline.ro/definitie/world" target="_blank" rel="noopener" data-word="world">World</a><br/><a href="https://dexonline.ro/definitie/third" target="_blank" rel="noopener" data-word="third">Third</a>');
    });

    it("decorateVerse handles empty segments", () => {
      const verse = "hello / / world";
      const result = decorateVerse(verse);
      expect(result).toBe('<a href="https://dexonline.ro/definitie/hello" target="_blank" rel="noopener" data-word="hello">Hello</a><br/><br/><a href="https://dexonline.ro/definitie/world" target="_blank" rel="noopener" data-word="world">World</a>');
    });
    it("escapeHtml handles special characters", () => {
      expect(escapeHtml("<script>alert('x')</script> & \"hello\"")).toBe("&lt;script&gt;alert('x')&lt;/script&gt; &amp; &quot;hello&quot;");
    });

    it("decorateSentence wraps text in links and capitalizes", () => {
      expect(decorateSentence("hello world")).toBe('<a href="https://dexonline.ro/definitie/hello" target="_blank" rel="noopener" data-word="hello">Hello</a> <a href="https://dexonline.ro/definitie/world" target="_blank" rel="noopener" data-word="world">world</a>');
    });
  });

  describe("normalizeRarityRange", () => {
    it("handles simple numeric strings", () => {
      expect(normalizeRarityRange("1", "5")).toEqual({ minR: 1, maxR: 5 });
    });
    it("handles comma-separated values with spaces and multiple items", () => {
      expect(normalizeRarityRange("1, 2, 3", "4, 5, 6")).toEqual({ minR: 3, maxR: 5 });
      expect(normalizeRarityRange("1 , 2", " 4 , 5 ")).toEqual({ minR: 2, maxR: 5 });
    });
    it("handles invalid inputs gracefully", () => {
      expect(normalizeRarityRange("invalid", "5")).toEqual({ minR: 1, maxR: 5 });
      expect(normalizeRarityRange("5", "invalid")).toEqual({ minR: 5, maxR: 5 });
    });
    it("handles reversed ranges by sorting them", () => {
      expect(normalizeRarityRange("5", "1")).toEqual({ minR: 1, maxR: 5 });
    });
    it("clamps values outside [1..5] to the range boundaries", () => {
      expect(normalizeRarityRange("0", "7")).toEqual({ minR: 1, maxR: 5 });
      expect(normalizeRarityRange("-3", "-1")).toEqual({ minR: 1, maxR: 1 });
    });
    it("returns default [1,2] when both values are invalid (both-NaN branch)", () => {
      // Source: if (isNaN(minVal) && isNaN(maxVal)) { [minC, maxC] = [1, 2]; }
      expect(normalizeRarityRange("abc", "xyz")).toEqual({ minR: 1, maxR: 2 });
    });
    it("handles array inputs (multi-value query params)", () => {
      expect(normalizeRarityRange(["2"], ["4"])).toEqual({ minR: 2, maxR: 4 });
      // Last element of multi-value arrays wins (matches firstQueryValue behavior).
      expect(normalizeRarityRange(["1", "3"], ["6", "7"])).toEqual({ minR: 3, maxR: 5 });
    });
    it("clamps array elements outside [1..5] to range boundaries", () => {
      // Source: Math.max(1, Math.min(5, v)) applied per element.
      expect(normalizeRarityRange(["0"], ["7"])).toEqual({ minR: 1, maxR: 5 });
      expect(normalizeRarityRange(["-10"], ["100"])).toEqual({ minR: 1, maxR: 5 });
    });
    it("returns [2,1] clamped to valid range when array values reverse", () => {
      // Source: if (minC > maxC) [minC, maxC] = [maxC, minC]; applied after clamping.
      expect(normalizeRarityRange(["5"], ["1"])).toEqual({ minR: 1, maxR: 5 });
    });
    it("uses last element when arrays have multiple items with mixed validity", () => {
      // Source: getNum takes parsed[last] — last valid/invalid element wins.
      expect(normalizeRarityRange(["abc", "3"], ["4", "xyz"])).toEqual({ minR: 3, maxR: 5 });
    });
    it("handles unequal-length arrays without throwing", () => {
      // Source: each side resolved independently via getNum; lengths are irrelevant.
      expect(normalizeRarityRange(["1", "2", "3"], ["4"])).toEqual({ minR: 3, maxR: 4 });
      expect(normalizeRarityRange(["1"], ["2", "3", "4"])).toEqual({ minR: 1, maxR: 4 });
    });
    it("treats empty-string elements as invalid (falls back per-branch logic)", () => {
      // Source: firstQueryValue filters Boolean; empty strings drop from the array.
      expect(normalizeRarityRange(["", "2"], ["3", ""])).toEqual({ minR: 2, maxR: 3 });
    });
  });

  describe("adjForGender", () => {
    it("returns masculine form", () => {
      const adj = { word: "grand", feminine: "grande", syllables: 1, rhyme: "an", feminine_syllables: 2 } as any;
      expect(adjForGender(adj, "M")).toBe("grand");
    });
    it("returns feminine form", () => {
      const adj = { word: "grand", feminine: "grande", syllables: 1, rhyme: "an", feminine_syllables: 2 } as any;
      expect(adjForGender(adj, "f")).toBe("grande");
    });
    it("returns feminine form with uppercase f", () => {
      const adj = { word: "grand", feminine: "grande", syllables: 1, rhyme: "an", feminine_syllables: 2 } as any;
      expect(adjForGender(adj, "F")).toBe("grande");
    });
    it("returns masculine form for unknown gender", () => {
      const adj = { word: "grand", feminine: "grande", syllables: 1, rhyme: "an", feminine_syllables: 2 } as any;
      expect(adjForGender(adj, "X")).toBe("grand");
    });
  });
});

describe("resolveSupabaseInit", () => {
  it("returns error if Supabase URL is missing", () => {
    const env = { SUPABASE_PUBLISHABLE_KEY: "key" };
    const res = resolveSupabaseInit(env);
    expect(res.error).toBe("Missing SUPABASE_URL.");
  });
  it("returns error if no keys are present", () => {
    expect(resolveSupabaseInit({})).toEqual({
      keyResolution: { key: "", source: "none", error: "Missing SUPABASE_PUBLISHABLE_KEY." },
      error: "Missing SUPABASE_URL."
    });
  });
  it("succeeds when URL is valid and a publishable key is present", () => {
    const env = { SUPABASE_URL: "https://example.supabase.co", SUPABASE_PUBLISHABLE_KEY: "pub-key" };
    const res = resolveSupabaseInit(env);
    expect(res.error).toBeUndefined();
    expect(res.keyResolution.key).toBe("pub-key");
    expect(res.keyResolution.source).toBe("publishable");
  });
  it("propagates key-resolution errors without re-checking the URL", () => {
    const env = {
      SUPABASE_URL: "https://example.supabase.co",
      SUPABASE_SERVICE_ROLE_KEY: "ser-key",
      ALLOW_SUPABASE_SERVICE_ROLE_FALLBACK: "false"
    };
    const res = resolveSupabaseInit(env);
    expect(res.error).toContain("SUPABASE_PUBLISHABLE_KEY");
  });

  // Regression: AGENTS.md — when both URL validation and key resolution fail,
  // the URL error takes precedence (line 151 of all.ts) but keyResolution is
  // still included for operator diagnostics. If a future refactor drops the
  // diagnostic payload or swaps precedence order, deployment operators would
  // lose visibility into which config values to fix first. This test locks that.
  it("returns URL error when both URL and keys are invalid (precedence + diagnostics)", () => {
    const env = {
      SUPABASE_URL: "not-a-valid-url",
      // No publishable or service-role key provided at all.
    };
    const res = resolveSupabaseInit(env);
    expect(res.error).toContain("Invalid SUPABASE_URL");
    expect(res.keyResolution.source).toBe("none");
    expect(res.keyResolution.key).toBe("");
    expect(res.keyResolution.error).toContain("SUPABASE_PUBLISHABLE_KEY");
  });
});

describe("buildResponseTimingHeaders", () => {
  it("returns correct timing headers", () => {
    const start = 1000;
    const end = 1500;
    const res = buildResponseTimingHeaders(start, end);
    expect(res.serverTiming).toBe("api-all;dur=500");
    expect(res.responseTimeMs).toBe("500");
  });
  it("handles negative duration by defaulting to 0", () => {
    const start = 1000;
    const end = 500;
    const res = buildResponseTimingHeaders(start, end);
    expect(res.serverTiming).toBe("api-all;dur=0");
    expect(res.responseTimeMs).toBe("0");
    });
  it("handles zero duration", () => {
    const start = 1000;
    const end = 1000;
    const res = buildResponseTimingHeaders(start, end);
    expect(res.serverTiming).toBe("api-all;dur=0");
    expect(res.responseTimeMs).toBe("0");
  });

  // Regression: AGENTS.md — timing header contract is the only response-time signal.
  it("preserves Server-Timing format 'api-all;dur=<N>' for valid durations", () => {
    const res = buildResponseTimingHeaders(0, 7500);
    expect(res.serverTiming).toMatch(/^api-all;dur=\d+$/);
    expect(Number(res.responseTimeMs)).toBeGreaterThan(0);
  });

  // Regression: maxDuration budget — when handler finishes after GENERATOR_TIMEOUT_MS
  it("reports durations exceeding the 7s generator timeout accurately", () => {
    const res = buildResponseTimingHeaders(0, 10000);
    expect(Number(res.responseTimeMs)).toBeGreaterThanOrEqual(7000);
    expect(res.serverTiming).toMatch(/^api-all;dur=1\d+$/);
  });

  // Regression: the handler uses Date.now() as default finishedAt.
  it("never emits 'api-all;dur=-' when finished < started", () => {
    const res = buildResponseTimingHeaders(10000, 5000);
    expect(res.serverTiming).not.toMatch(/dur=-/);
    expect(Number(res.responseTimeMs)).toBeGreaterThanOrEqual(0);
  });

  // Regression: large-but-valid durations must still produce valid Server-Timing.
  it("produces integer-only duration in Server-Timing (no scientific notation)", () => {
    const res = buildResponseTimingHeaders(0, 999999);
    expect(res.serverTiming).not.toMatch(/e|E/);
    expect(Number(res.responseTimeMs)).toBeGreaterThan(0);
  });

  // Regression: integer inputs must produce integer-only output — no .0 suffix that would break Server-Timing parsing.
  it("produces integer-only duration strings for common medium durations", () => {
    const res = buildResponseTimingHeaders(1000, 2500);
    expect(res.responseTimeMs).toBe("1500");
    expect(res.serverTiming).toBe("api-all;dur=1500");
    // Roundtrip: the string must survive Number() → String() losslessly.
    expect(String(Number(res.responseTimeMs))).toBe(res.responseTimeMs);
  });

  it("produces integer-only duration for zero-start edge case", () => {
    const res = buildResponseTimingHeaders(0, 12345);
    expect(res.responseTimeMs).toBe("12345");
    expect(res.serverTiming).toMatch(/^api-all;dur=\d+$/);
    expect(String(Number(res.responseTimeMs))).toBe(res.responseTimeMs);
  });

  // Regression: the handler's setHeader call passes responseTimeMs as a string.
  it("responseTimeMs is always a plain string (not object/array)", () => {
    const res = buildResponseTimingHeaders(0, 1);
    expect(typeof res.responseTimeMs).toBe("string");
    expect(parseInt(res.responseTimeMs, 10)).toBeGreaterThanOrEqual(0);
    expect(String(Number(res.responseTimeMs))).toBe(res.responseTimeMs);
  });
});

describe("safeTimestamp", () => {
  it("returns an ISO string", () => {
    const ts = safeTimestamp();
    // Must parse as a valid Date.
    expect(new Date(ts).getTime()).not.toBeNaN();
    // Must match ISO format (YYYY-MM-DDTHH:MM:SS.sssZ) within the last 10s to ensure it is current.
    expect(ts).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
    const delta = Math.abs(Date.now() - new Date(ts).getTime());
    expect(delta).toBeLessThan(10_000);
  });

  // Regression: AGENTS.md — safeTimestamp is the only timestamp signal in the handler's JSON envelope.
  it("falls back to a sentinel when Date constructor throws", () => {
    const orig = global.Date;
    delete (global as any).Date;
    try {
      expect(safeTimestamp()).toBe("1970-01-01T00:00:00.000Z");
    } finally {
      (global as any).Date = orig;
    }
  });
});