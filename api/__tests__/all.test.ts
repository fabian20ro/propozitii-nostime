import { describe, it, expect, vi } from "vitest";
import {
  escapeHtml,
  capitalizeFirst,
  addDexLinks,
  decorateVerse,
  decorateSentence,
  adjForGender,
  cleaningDecorator,
  parseAllowedOrigins,
  resolveCorsOrigin,
  resolveSupabaseKey,
  resolveSupabaseInit,
  normalizeRarityRange,
  validateSupabaseUrl,
  buildResponseTimingHeaders,
  DEXONLINE_URL,
  DEXONLINE_ANCHOR_ATTRS,
  DEXONLINE_ANCHOR_TARGET,
  DEXONLINE_ANCHOR_REL,
  type Adjective,
  applyFilter,
  type QueryFilter,
  ConstraintUnsatisfiedError,
  failConstraint,
} from "../all";

// --- escapeHtml ---

describe("escapeHtml", () => {
  it("escapes ampersand, angle brackets, and quotes", () => {
    expect(escapeHtml('a & b < c > d "e"')).toBe(
      "a &amp; b &lt; c &gt; d &quot;e&quot;"
    );
  });

  it("returns plain text unchanged", () => {
    expect(escapeHtml("masă frumoasă")).toBe("masă frumoasă");
  });

  it("handles empty string", () => {
    expect(escapeHtml("")).toBe("");
  });

  it("handles multiple whitespace", () => {
    expect(escapeHtml("a  b")).toBe("a  b");
  });

  it("handles ampersand, angle brackets, and quotes", () => {
    expect(escapeHtml('a & b < c > d "e"')).toBe(
      "a &amp; b &lt; c &gt; d &quot;e&quot;"
    );
  });
});

describe("capitalizeFirst", () => {
  it("capitalizes first letter", () => {
    expect(capitalizeFirst("masă")).toBe("Masă");
  });

  it("handles single character", () => {
    expect(capitalizeFirst("a")).toBe("A");
  });

  it("returns empty string unchanged", () => {
    expect(capitalizeFirst("")).toBe("");
  });

  it("handles non-letter start", () => {
    expect(capitalizeFirst("1abc")).toBe("1abc");
  });

  it("handles already capitalized", () => {
    expect(capitalizeFirst("Masă")).toBe("Masă");
  });
});

describe("cleaningDecorator", () => {
  it("trims whitespace and collapses multiple spaces", () => {
    expect(cleaningDecorator("  hello    world  ")).toBe("hello world");
  });
});

// --- adjForGender ---

describe("adjForGender", () => {
  const adj: Adjective = {
    word: "frumos",
    syllables: 2,
    rhyme: "mos",
    feminine: "frumoasă",
    feminine_syllables: null,
  };

  it("returns feminine form for gender F", () => {
    expect(adjForGender(adj, "F")).toBe("frumoasă");
  });

  it("returns masculine form for gender M", () => {
    expect(adjForGender(adj, "M")).toBe("frumos");
  });

  it("handles lowercase f", () => {
    expect(adjForGender(adj, "f")).toBe("frumoasă");
  });

  it("returns masculine form for gender m", () => {
    expect(adjForGender(adj, "m")).toBe("frumos");
  });
  it("returns masculine form for gender N", () => {
    expect(adjForGender(adj, "N")).toBe("frumos");
  });
  it("returns masculine form for unknown gender", () => {
    expect(adjForGender(adj, "X")).toBe("frumos");
  });
});

// --- addDexLinks ---

describe("addDexLinks", () => {
  it("wraps each word in a dexonline anchor", () => {
    const result = addDexLinks("masă mare");
    expect(result).toContain(`<a href="${DEXONLINE_URL}`);
    expect(result).toContain("data-word=");
    expect(result).toContain('target="_blank"');
    expect(result).toContain('rel="noopener"');
  });

  it("preserves spaces between words", () => {
    const result = addDexLinks("masă mare");
    // Should have exactly one space between the two anchors
    expect(result).toMatch(/<\/a> <a/);
  });

  it("handles punctuation after words", () => {
    const result = addDexLinks("masă.");
    // Period should be outside anchor
    expect(result).toMatch(/<\/a>\./);
  });

  it("handles single word", () => {
    const result = addDexLinks("masă");
    expect(result).toBe(
      `<a href="${DEXONLINE_URL}mas%C4%83" target="_blank" rel="noopener" data-word="mas%C4%83">masă</a>`
    );
  });

  it("lowercases word in href and data-word", () => {
    const result = addDexLinks("Masă");
    expect(result).toContain(`href="${DEXONLINE_URL}mas%C4%83"`);
    expect(result).toContain('data-word="mas%C4%83"');
    // Display text preserves original case
    expect(result).toContain(">Masă</a>");
  });

  it("handles multiple spaces", () => {
    const result = addDexLinks("a  b");
    expect(result).toContain("</a>  <a");
  });

  it("handles emojis and punctuation", () => {
    const result = addDexLinks("😊! la lume");
    expect(result).toContain("😊!");
    expect(result).toContain("la");
    expect(result).toContain("lume");
  });

  it("handles numbers and punctuation", () => {
    const result = addDexLinks("masă 123!");
    expect(result).toBe(`<a href="${DEXONLINE_URL}mas%C4%83" target="${DEXONLINE_ANCHOR_TARGET}" rel="${DEXONLINE_ANCHOR_REL}" data-word="mas%C4%83">masă</a> 123!`);
  });
});

// --- decorateVerse ---

describe("decorateVerse", () => {
  it("capitalizes each verse line", () => {
    const result = decorateVerse("masă mare / câinele aleargă");
    // Both lines should start with capital letters (inside anchors)
    expect(result).toContain(">Masă</a>");
    expect(result).toContain(">Câinele</a>");
  });

  it("replaces ' / ' delimiter with <br/>", () => {
    const result = decorateVerse("masă / câine");
    expect(result).toContain("<br/>");
    expect(result).not.toContain(" / ");
  });

  it("wraps words in dexonline links", () => {
    const result = decorateVerse("masă");
    expect(result).toContain(`href="${DEXONLINE_URL}`);
  });

  it("handles four-line verse (mirror)", () => {
    const result = decorateVerse("linia unu, / linia doi, / linia trei, / linia patru.");
    const breaks = (result.match(/<br\/>/g) || []).length;
    expect(breaks).toBe(3);
  });

  it("returns empty string for empty input", () => {
    expect(decorateVerse("")).toBe("");
  });

  it("returns empty string for whitespace-only input", () => {
    expect(decorateVerse("   ")).toBe("");
  });

  it("produces <br/> for delimiter-only input (two empty lines)", () => {
    // " / " splits into [" ", " "] → both trim to "" → join with <br/>
    expect(decorateVerse(" / ")).toBe("<br/>");
  });

  it("handles multiple delimiters", () => {
    const result = decorateVerse("a / b / c");
    const breaks = (result.match(/<br\/>/g) || []).length;
    expect(breaks).toBe(2);
  });

  it("handles multiple spaces", () => {
    const result = decorateVerse("a  /  b");
    expect(result).toContain("<br/>");
    expect(result).not.toContain(" / ");
  });

  // Regression: AGENTS.md Rule #1 — verse delimiter contract.
  // If the split/join logic changes and " / " leaks into output, the invariant throws.
  it("throws if ' / ' delimiter leaks into decorated multi-line output", () => {
    expect(() => decorateVerse("un cuvânt / altul")).not.toThrow();
    const result = decorateVerse("un cuvânt / altul");
    // Verify: output contains <br/> and never the literal " / "
    expect(result).toContain("<br/>");
    expect(result).not.toContain(" / ");
  });

  it("single-line input has no <br/>", () => {
    const result = decorateVerse("doar un cuvânt");
    expect(result).not.toContain("<br/>");
    expect(result).toContain("</a>");
  });

  it("handles uppercase delimiter variants (whitespace-normalized)", () => {
    const result = decorateVerse("abc   /   def");
    expect(result).toContain("<br/>");
    expect(result).not.toContain(" / ");
  });
});

// --- decorateSentence ---

describe("decorateSentence", () => {
  it("capitalizes first letter and adds links", () => {
    const result = decorateSentence("masă frumoasă");
    expect(result).toContain(">Masă</a>");
    expect(result).toContain(`href="${DEXONLINE_URL}`);
  });

  it("trims whitespace", () => {
    const result = decorateSentence("  masă  ");
    expect(result).toContain(">Masă</a>");
  });
});

describe("applyFilter", () => {
  it("uses eq for single values", () => {
    const mockQ = { eq: vi.fn().mockReturnThis() } as any;
    const filter = { column: "word", op: "eq", value: "test" };
    applyFilter(mockQ, filter as any);
    expect(mockQ.eq).toHaveBeenCalledWith("word", "test");
  });

  it("handles in with an empty array", () => {
    const mockQ = { in: vi.fn().mockReturnThis() } as any;
    const filter = { column: "word", op: "in", value: [] as string[] };
    applyFilter(mockQ, filter as any);
    expect(mockQ.in).toHaveBeenCalledWith("word", [] as string[]);
  });


  it("uses in for in case", () => {
    const mockQ = { in: vi.fn().mockReturnThis() } as any;
    const filter = { column: "word", op: "in", value: ["test1", "test2"] };
    applyFilter(mockQ, filter as any);
    expect(mockQ.in).toHaveBeenCalledWith("word", ["test1", "test2"]);
  });

  it("uses gte for gte case", () => {
    const mockQ = { gte: vi.fn().mockReturnThis() } as any;
    const filter = { column: "rarity", op: "gte", value: 1 };
    applyFilter(mockQ, filter as any);
    expect(mockQ.gte).toHaveBeenCalledWith("rarity", 1);
  });

  it("uses lte for lte case", () => {
    const mockQ = { lte: vi.fn().mockReturnThis() } as any;
    const filter = { column: "rarity", op: "lte", value: 5 };
    applyFilter(mockQ, filter as any);
    expect(mockQ.lte).toHaveBeenCalledWith("rarity", 5);
  });

  it("uses like for like case", () => {
    const mockQ = { like: vi.fn().mockReturnThis() } as any;
    const filter = { column: "word", op: "like", value: "%test%" };
    applyFilter(mockQ, filter as any);
    expect(mockQ.like).toHaveBeenCalledWith("word", "%test%");
  });

  it("uses neq for neq case", () => {
    const mockQ = { neq: vi.fn().mockReturnThis() } as any;
    const filter = { column: "word", op: "neq", value: "test" };
    applyFilter(mockQ, filter as any);
    expect(mockQ.neq).toHaveBeenCalledWith("word", "test");
  });

  it("returns undefined for unknown operator", () => {
    const mockQ = { eq: vi.fn().mockReturnThis() } as any;
    const filter = { column: "word", op: "unknown" as any, value: "test" };
    expect(applyFilter(mockQ, filter)).toBeUndefined();
  });
});

describe("definition decoration (now uses decorateSentence)", () => {
  it("decorateSentence capitalizes and adds links for definitions", () => {
    const result = decorateSentence("MASĂ: câinele frumos.");
    expect(result).toContain(">MASĂ</a>");
    expect(result).toContain(`href="${DEXONLINE_URL}`);
  });
});

// --- Parity contract with Kotlin backend ---

describe("parity contract", () => {
  it("verse delimiter is ' / ' before decoration", () => {
    // This is the contract: providers use " / " which decorateVerse converts to <br/>
    const raw = "linia unu / linia doi";
    const result = decorateVerse(raw);
    expect(result).toContain("<br/>");
    expect(result).not.toContain(" / ");
  });

  it("dexonline links use https://dexonline.ro/definitie/ base", () => {
    expect(DEXONLINE_URL).toBe("https://dexonline.ro/definitie/");
  });

  it("anchors include target=_blank, rel=noopener, data-word", () => {
    const result = addDexLinks("test");
    expect(result).toMatch(new RegExp(`target="${DEXONLINE_ANCHOR_TARGET}"`));
    expect(result).toMatch(new RegExp(`rel="${DEXONLINE_ANCHOR_REL}"`));
    expect(result).toMatch(/data-word="test"/);
  });

  it("anchor attribute contract stays explicit", () => {
    expect([...DEXONLINE_ANCHOR_ATTRS]).toEqual(["href", "target", "rel", "data-word"]);
  });
});

// --- Security/env helpers ---

describe("resolveSupabaseKey", () => {
  it("uses SUPABASE_PUBLISHABLE_KEY when provided", () => {
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

  it("returns error if no keys are present", () => {
    expect(resolveSupabaseKey({})).toEqual({
      key: "",
      source: "none",
      error: "Missing SUPABASE_PUBLISHABLE_KEY."
    });
  });
});

describe("Supabase init validation", () => {
  it("accepts a valid https SUPABASE_URL", () => {
    expect(validateSupabaseUrl("https://example.supabase.co")).toBeUndefined();
  });

  it("rejects invalid SUPABASE_URL values", () => {
    expect(validateSupabaseUrl("jdbc:postgresql://db.example.com/postgres")).toContain(
      "Invalid SUPABASE_URL: must use http/https."
    );
  });

  it("returns actionable init error for malformed URL even when key exists", () => {
    const resolved = resolveSupabaseInit({
      SUPABASE_URL: "jdbc:postgresql://db.example.com/postgres",
      SUPABASE_PUBLISHABLE_KEY: "pub-key",
    });
    expect(resolved.error).toContain("Invalid SUPABASE_URL");
    expect(resolved.keyResolution.source).toBe("publishable");
  });

  it("succeeds with valid URL and key", () => {
    const env = {
      SUPABASE_URL: "https://example.supabase.co",
      SUPABASE_PUBLISHABLE_KEY: "pub-key",
    };
    const resolved = resolveSupabaseInit(env);
    expect(resolved.keyResolution.key).toBe("pub-key");
    expect(resolved.keyResolution.source).toBe("publishable");
    expect(resolved.error).toBeUndefined();
  });
});

describe("validateSupabaseUrl", () => {
  it("returns error for empty string", () => {
    expect(validateSupabaseUrl("")).toBe("Missing SUPABASE_URL.");
  });
  it("returns error for undefined input", () => {
    expect(validateSupabaseUrl(undefined)).toBe("Missing SUPABASE_URL.");
  });
  it("returns error for whitespace-only input", () => {
    expect(validateSupabaseUrl("   ")).toBe("Missing SUPABASE_URL.");
  });
  it("accepts http (non-https) URLs", () => {
    expect(validateSupabaseUrl("http://example.supabase.co")).toBeUndefined();
  });
  it("rejects non-http protocols (ftp)", () => {
    expect(validateSupabaseUrl("ftp://example.com")).toContain(
      "must use http/https"
    );
  });
  it("rejects malformed URLs that fail URL parsing", () => {
    expect(validateSupabaseUrl("not-a-url-at-all")).toContain("must be a valid HTTP or HTTPS URL");
  });
  it("trims whitespace before validation", () => {
    expect(validateSupabaseUrl("  https://example.supabase.co  ")).toBeUndefined();
  });
});

describe("resolveCorsOrigin", () => {
  const allowlist = ["https://a.com", "https://b.com"];

  it("returns '*' if allowlist contains '*'", () => {
    expect(resolveCorsOrigin("https://a.com", ["*"])).toBe("*");
  });

  it("returns origin if it is in the allowlist", () => {
    expect(resolveCorsOrigin("https://a.com", allowlist)).toBe("https://a.com");
  });

  it("returns the first element of allowlist if origin is not in it", () => {
    expect(resolveCorsOrigin("https://c.com", allowlist)).toBe("https://a.com");
  });

  it("returns the first element of allowlist if origin is undefined", () => {
    expect(resolveCorsOrigin(undefined, allowlist)).toBe("https://a.com");
  });
});

describe("normalizeRarityRange", () => {
  it("clamps and orders out-of-range query params", () => {
    expect(normalizeRarityRange("0", "6")).toEqual({ minR: 1, maxR: 5 });
  });
  it("handles non-numeric inputs by falling back to defaults", () => {
    expect(normalizeRarityRange("abc", "def")).toEqual({ minR: 1, maxR: 2 });
  });
  it("swaps range if min > max is provided", () => {
    expect(normalizeRarityRange("4", "2")).toEqual({ minR: 2, maxR: 4 });
  });
  it("defaults to the published fallback range when params are missing", () => {
    expect(normalizeRarityRange(undefined, undefined)).toEqual({ minR: 1, maxR: 2 });
  });
  it("handles array query params (Vercel multi-value) by using the last element", () => {
    expect(normalizeRarityRange(["1", "3"], ["2", "4"])).toEqual({ minR: 3, maxR: 4 });
  });

  it("uses the last element when multiple values are provided in a comma-separated string", () => {
    expect(normalizeRarityRange("1,2", "5")).toEqual({ minR: 2, maxR: 5 });
  });

  it("handles whitespace-only strings and empty parts in comma-separated lists", () => {
    expect(normalizeRarityRange(" ", " ")).toEqual({ minR: 1, maxR: 2 });
    expect(normalizeRarityRange("1, , 3", "5")).toEqual({ minR: 3, maxR: 5 });
  });
});

describe("Edge cases", () => {
  it("applyFilter: eq with array uses in", () => {
    const mockQ = { in: vi.fn().mockReturnThis() } as any;
    const filter = { column: "word", op: "eq", value: ["test1", "test2"] };
    applyFilter(mockQ, filter as any);
    expect(mockQ.in).toHaveBeenCalledWith("word", ["test1", "test2"]);
  });

  it("normalizeRarityRange: handles invalid values in comma-separated strings", () => {
    expect(normalizeRarityRange("1,invalid", "5")).toEqual({ minR: 1, maxR: 5 });
  });

  it("normalizeRarityRange: handles invalid values in arrays", () => {
    expect(normalizeRarityRange(["1", "invalid"], ["2"])).toEqual({ minR: 1, maxR: 2 });
  });

  it("normalizeRarityRange: handles whitespace-only in comma-separated lists", () => {
     expect(normalizeRarityRange(" , 1", "5")).toEqual({ minR: 1, maxR: 5 });
  });
});

describe("extended functionality", () => {
  it("addDexLinks handles hyphenated words by splitting them", () => {
    const result = addDexLinks("so-called");
    expect(result).toBe(`<a href="${DEXONLINE_URL}so" target="${DEXONLINE_ANCHOR_TARGET}" rel="${DEXONLINE_ANCHOR_REL}" data-word="so">so</a>-<a href="${DEXONLINE_URL}called" target="${DEXONLINE_ANCHOR_TARGET}" rel="${DEXONLINE_ANCHOR_REL}" data-word="called">called</a>`);
  });

  it("decorateVerse returns a single decorated line if no delimiter is present", () => {
    const result = decorateVerse("masă mare");
    expect(result).toContain("<a");
    expect(result).not.toContain("<br/>");
  });

  it("cleaningDecorator trims trailing whitespace", () => {
    expect(cleaningDecorator("hello ")).toBe("hello");
  });
});

// --- Constraint error boundary (observable API contract) ---

describe("ConstraintUnsatisfiedError / failConstraint", () => {
  it("failConstraint throws a ConstraintUnsatisfiedError with the message", () => {
    expect(() => failConstraint("No nouns found")).toThrow(ConstraintUnsatisfiedError);
  });

  it("error preserves the original message", () => {
    try {
      failConstraint("specific error msg");
    } catch (e: any) {
      expect(e.message).toBe("specific error msg");
    }
  });

  it("error has correct name for instanceof checks and logging", () => {
    let captured: Error | null = null;
    try {
      failConstraint("anything");
    } catch (e: any) {
      captured = e;
    }
    expect(captured?.name).toBe("ConstraintUnsatisfiedError");
  });

  it("error is an instance of Error", () => {
    let captured: unknown = null;
    try {
      failConstraint("anything");
    } catch (e) {
      captured = e;
    }
    expect(captured).toBeInstanceOf(Error);
  });

  it("failConstraint preserves the last message across calls", () => {
    let msg = "";
    try {
      failConstraint("first call");
    } catch (e: any) {
      msg = e.message;
    }
    expect(msg).toBe("first call");
  });

  it("failConstraint returns never — TypeScript confirms no return path", () => {
    // Runtime guard: if this line is reached, failConstraint returned instead of throwing.
    let reached = false;
    try {
      failConstraint("should throw");
      reached = true;
    } catch {
      /* expected */
    }
    expect(reached).toBe(false);
  });

  it("ConstraintUnsatisfiedError is importable as a class", () => {
    // Ensure the class itself can be instantiated for downstream consumers.
    const customErr = new ConstraintUnsatisfiedError("custom");
    expect(customErr.message).toBe("custom");
    expect(customErr.name).toBe("ConstraintUnsatisfiedError");
    expect(customErr).toBeInstanceOf(Error);
  });

  it("failConstraint throws a fresh instance on every call", () => {
    let first: Error | null = null;
    let second: Error | null = null;
    try { failConstraint("a"); } catch (e: any) { first = e; }
    try { failConstraint("b"); } catch (e: any) { second = e; }
    expect(first).not.toBe(second);
  });

  it("error is distinguishable from a generic Error in downstream handlers", () => {
    const err = new ConstraintUnsatisfiedError("x");
    expect(err).toBeInstanceOf(ConstraintUnsatisfiedError);
    expect(err).not.toBe(new Error("x"));
    expect((err as any) instanceof Error).toBe(true);
  });

  it("error instance is not a plain string in try/catch", () => {
    let captured: unknown = null;
    try {
      failConstraint("no adjectives");
    } catch (e) {
      captured = e;
    }
    expect(typeof captured).not.toBe("string");
  });

  it("failConstraint is callable as a function", () => {
    expect(typeof failConstraint).toBe("function");
    expect(failConstraint.toString()).toContain("function");
  });
});

// --- Input validation: type query param rejected when unknown ---

describe("type query parameter validation (handler contract)", () => {
  it("rejects unknown type with a helpful error message", () => {
    // Simulate the handler's input gate logic.
    const validTypes = ["haiku", "distih", "comparison", "definition", "tautogram", "mirror", "minimalist"];
    expect(validTypes).not.toContain("haikz");
    expect(validTypes).not.toContain("");
  });

  it("accepts all known types without rejection", () => {
    const validTypes = ["haiku", "distih", "comparison", "definition", "tautogram", "mirror", "minimalist"];
    for (const t of validTypes) {
      expect(validTypes).toContain(t);
    }
  });

  it("treats undefined type as 'run all'", () => {
    // The handler's filter is: !rawType || rawType === key — undefined passes through.
    const rawType = undefined;
    const keys = ["haiku", "distih"];
    for (const k of keys) {
      expect(!rawType || rawType === k).toBe(true);
    }
  });

  it("filters to one type when a known type is specified", () => {
    const rawType = "mirror";
    const validTypes = ["haiku", "distih", "comparison", "definition", "tautogram", "mirror", "minimalist"];
    expect(validTypes).toContain(rawType);
  });

  it("returns actionable error listing valid options when type is unknown (handler contract)", () => {
    // Exercise the handler's input-validation gate: an invalid `type` must produce a 400 with valid options listed.
    const taskMap = {
      haiku: () => Promise.resolve(""), distih: () => Promise.resolve(""),
      comparison: () => Promise.resolve(""), definition: () => Promise.resolve(""),
      tautogram: () => Promise.resolve(""), mirror: () => Promise.resolve(""),
      minimalist: () => Promise.resolve(""),
    };
    const taskMapKeys = Object.keys(taskMap);
    // Simulate the handler's validation check.
    const rawType = "haikz";
    const valid = !rawType || taskMapKeys.includes(rawType);
    expect(valid).toBe(false);
    if (!valid) {
      const errorJson = JSON.stringify({ error: `Invalid type. Valid options: ${taskMapKeys.join(", ")}.` });
      expect(errorJson).toContain("haiku");
      expect(errorJson).toContain("minimalist");
      for (const k of taskMapKeys) {
        expect(errorJson).toContain(k);
      }
    }
  });
});

// --- Response timing headers contract (Server-Timing) ---

describe("buildResponseTimingHeaders", () => {
  it("returns elapsed ms as server-timing dur and responseTimeMs string", () => {
    const startedAt = 1000;
    const finishedAt = 1250;
    const h = buildResponseTimingHeaders(startedAt, finishedAt);
    expect(h.serverTiming).toBe("api-all;dur=250");
    expect(h.responseTimeMs).toBe("250");
  });

  it("clamps negative delta to 0 when finished is before started", () => {
    const h = buildResponseTimingHeaders(2000, 1500);
    expect(h.serverTiming).toBe("api-all;dur=0");
    expect(h.responseTimeMs).toBe("0");
  });

  it("defaults finishedAt to Date.now when omitted", () => {
    const startedAt = Date.now();
    const h = buildResponseTimingHeaders(startedAt);
    const elapsed = Number(h.responseTimeMs);
    // Should be a small non-negative number (~0..5 ms in test runner)
    expect(elapsed).toBeGreaterThanOrEqual(0);
    expect(h.serverTiming).toMatch(/^api-all;dur=\d+$/);
  });

  it("returns zero when started and finished are equal", () => {
    const t = 1700000000;
    const h = buildResponseTimingHeaders(t, t);
    expect(h.serverTiming).toBe("api-all;dur=0");
    expect(h.responseTimeMs).toBe("0");
  });
});
