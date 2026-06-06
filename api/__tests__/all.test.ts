import { describe, it, expect } from "vitest";
import {
  escapeHtml,
  capitalizeFirst,
  addDexLinks,
  decorateVerse,
  decorateSentence,
  adjForGender,
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

  it("does not escape single quotes", () => {
    expect(escapeHtml("it's")).toBe("it's");
  });
});

// --- capitalizeFirst ---

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

  it("handles already capitalized", () => {
    expect(capitalizeFirst("Masă")).toBe("Masă");
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

  it("returns masculine form for gender N", () => {
    expect(adjForGender(adj, "N")).toBe("frumos");
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

  it("returns empty string for empty input", () => {
    expect(decorateSentence("")).toBe("");
  });

  it("returns empty string for whitespace-only input", () => {
    expect(decorateSentence("   ")).toBe("");
  });
});

// --- definition decoration (now uses decorateSentence) ---

describe("definition decoration", () => {
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
    const resolved = resolveSupabaseKey({
      SUPABASE_PUBLISHABLE_KEY: "publishable",
      SUPABASE_SERVICE_ROLE_KEY: "abc",
    });
    expect(resolved.source).toBe("publishable");
    expect(resolved.key).toBe("publishable");
  });

  it("treats whitespace-only keys as empty", () => {
    const resolved = resolveSupabaseKey({
      SUPABASE_PUBLISHABLE_KEY: "   ",
    });
    expect(resolved.source).toBe("none");
    expect(resolved.key).toBe("");
  });

  it("requires publishable key when service-role fallback is not enabled", () => {
    const resolved = resolveSupabaseKey({});
    expect(resolved.source).toBe("none");
    expect(resolved.key).toBe("");
    expect(resolved.error).toContain("Missing SUPABASE_PUBLISHABLE_KEY");
  });

  it("rejects service-role fallback by default", () => {
    const resolved = resolveSupabaseKey({
      SUPABASE_SERVICE_ROLE_KEY: "service",
    });
    expect(resolved.source).toBe("none");
    expect(resolved.key).toBe("");
    expect(resolved.error).toContain("SUPABASE_SERVICE_ROLE_KEY is set but disabled");
  });

  it("allows service-role only when explicitly enabled", () => {
    const resolved = resolveSupabaseKey({
      SUPABASE_SERVICE_ROLE_KEY: "service",
      ALLOW_SUPABASE_SERVICE_ROLE_FALLBACK: "true",
    });
    expect(resolved.source).toBe("service-role");
    expect(resolved.key).toBe("service");
  });
});

describe("Supabase init validation", () => {
  it("accepts a valid https SUPABASE_URL", () => {
    expect(validateSupabaseUrl("https://example.supabase.co")).toBeUndefined();
  });

  it("rejects invalid SUPABASE_URL values", () => {
    expect(validateSupabaseUrl("jdbc:postgresql://db.example.com/postgres")).toContain(
      "Invalid SUPABASE_URL"
    );
  });

  it("returns actionable init error for malformed URL even when key exists", () => {
    const resolved = resolveSupabaseInit({
      SUPABASE_URL: "jdbc:postgresql://db.example.com/postgres",
      SUPABASE_PUBLISHABLE_KEY: "publishable",
    });
    expect(resolved.error).toContain("Invalid SUPABASE_URL");
    expect(resolved.keyResolution.source).toBe("publishable");
  });
});

describe("validateSupabaseUrl", () => {
  it("returns error for empty string", () => {
    expect(validateSupabaseUrl("")).toContain("Missing SUPABASE_URL");
  });

  it("returns error for undefined input", () => {
    expect(validateSupabaseUrl(undefined)).toContain("Missing SUPABASE_URL");
  });

  it("returns error for whitespace-only input", () => {
    expect(validateSupabaseUrl("   ")).toContain("Missing SUPABASE_URL");
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
    expect(validateSupabaseUrl("not-a-url-at-all")).toContain(
      "must be a valid HTTP or HTTPS URL"
    );
  });

  it("trims whitespace before validation", () => {
    expect(validateSupabaseUrl("  https://example.supabase.co  ")).toBeUndefined();
  });
});

describe("CORS origin helpers", () => {
  it("uses default allowlist when env value is empty", () => {
    expect(parseAllowedOrigins(undefined)).toEqual(["https://fabian20ro.github.io"]);
  });

  it("builds response timing headers from elapsed milliseconds", () => {
    expect(buildResponseTimingHeaders(1000, 1123)).toEqual({
      serverTiming: "api-all;dur=123",
      responseTimeMs: "123",
    });
  });

  it("clamps negative elapsed time to zero", () => {
    expect(buildResponseTimingHeaders(1123, 1000)).toEqual({
      serverTiming: "api-all;dur=0",
      responseTimeMs: "0",
    });
  });

  it("parses comma-separated allowlist", () => {
    expect(
      parseAllowedOrigins("https://a.example, https://b.example")
    ).toEqual(["https://a.example", "https://b.example"]);
  });

  it("reflects request origin when it is allowed", () => {
    const allowed = ["https://a.example", "https://b.example"];
    expect(resolveCorsOrigin("https://b.example", allowed)).toBe("https://b.example");
  });

  it("falls back to first allowlist origin when request origin is not allowed", () => {
    const allowed = ["https://a.example", "https://b.example"];
    expect(resolveCorsOrigin("https://evil.example", allowed)).toBe("https://a.example");
  });

  it("handles comma-only or whitespace-only strings by returning default", () => {
    expect(parseAllowedOrigins(", , ")).toEqual(["https://fabian20ro.github.io"]);
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

  it("handles array query params (Vercel multi-value) by using first element", () => {
    expect(normalizeRarityRange(["3"], ["4"])).toEqual({ minR: 3, maxR: 4 });
  });

  it("clamps and orders array query params", () => {
    // "6" clamps to 5; "0" is falsy so || 2 fallback → maxCandidate=2
    // then min/max swap: { minR: 2, maxR: 5 }
    expect(normalizeRarityRange(["6"], ["0"])).toEqual({ minR: 2, maxR: 5 });
  });
});
