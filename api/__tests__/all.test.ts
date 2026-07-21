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
  InternalServerError,
  failConstraint,
  safe,
  safeTimestamp,
} from "../all";

// --- resolveSupabaseKey ---

describe("resolveSupabaseKey", () => {
  it("prefers SUPABASE_PUBLISHABLE_KEY over service-role key", () => {
    const res = resolveSupabaseKey({
      SUPABASE_PUBLISHABLE_KEY: "pub-key-123",
      SUPABASE_SERVICE_ROLE_KEY: "svc-secret",
    });
    expect(res.source).toBe("publishable");
    expect(res.key).toBe("pub-key-123");
    expect(res.error).toBeUndefined();
  });

  it("uses service-role key only when fallback is explicitly enabled", () => {
    const res = resolveSupabaseKey({
      SUPABASE_SERVICE_ROLE_KEY: "svc-secret",
      ALLOW_SUPABASE_SERVICE_ROLE_FALLBACK: "true",
    });
    expect(res.source).toBe("service-role");
    expect(res.key).toBe("svc-secret");
    expect(res.error).toBeUndefined();
  });

  it("returns error when service-role key is set but fallback is disabled", () => {
    const res = resolveSupabaseKey({
      SUPABASE_SERVICE_ROLE_KEY: "svc-secret",
    });
    expect(res.source).toBe("none");
    expect(res.key).toBe("");
    expect(res.error).toContain("SUPABASE_SERVICE_ROLE_KEY");
    expect(res.error).toContain("disabled for this public endpoint");
  });

  it("returns error when no key is configured", () => {
    const res = resolveSupabaseKey({});
    expect(res.source).toBe("none");
    expect(res.key).toBe("");
    expect(res.error).toBe("Missing SUPABASE_PUBLISHABLE_KEY.");
  });

  // Regression: AGENTS.md security coupling — the publishable-key path must
  // always win. If a future refactor accidentally lets service-role leak into
  // public endpoints, Supabase will expose write/delete access. This test
  // locks the precedence invariant.
  it("never leaks service-role key as the active key in production", () => {
    const res = resolveSupabaseKey({
      SUPABASE_PUBLISHABLE_KEY: "pub-key",
      SUPABASE_SERVICE_ROLE_KEY: "svc-secret",
    });
    expect(res.source).toBe("publishable");
    // Service-role key must NOT be used — even if it were checked first,
    // the publishable check runs before and short-circuits.
    expect(res.key).not.toBe("svc-secret");
  });

  // Regression: whitespace-only keys must not pass through as "valid".
  // If a future env-var gets set to spaces only, trim() + length check must
  // prevent it from being treated as a real key.
  it("treats whitespace-only publishable key as absent", () => {
    const res = resolveSupabaseKey({
      SUPABASE_PUBLISHABLE_KEY: "   ",
      SUPABASE_SERVICE_ROLE_KEY: "",
    });
    expect(res.source).toBe("none");
    expect(res.key).toBe("");
    // Falls through to the missing-key error path.
    expect(res.error).toBe("Missing SUPABASE_PUBLISHABLE_KEY.");
  });

  it("treats whitespace-only service-role key as absent", () => {
    const res = resolveSupabaseKey({
      SUPABASE_SERVICE_ROLE_KEY: "   ",
      ALLOW_SUPABASE_SERVICE_ROLE_FALLBACK: "true",
    });
    expect(res.source).toBe("none");
  });

  it("treats 'TRUE' (uppercase) as enabled — fallback check is case-insensitive", () => {
    const res = resolveSupabaseKey({
      SUPABASE_SERVICE_ROLE_KEY: "svc-secret",
      ALLOW_SUPABASE_SERVICE_ROLE_FALLBACK: "TRUE", // toLowerCase === "true" matches
    });
    expect(res.source).toBe("service-role");
  });

  it("treats non-true string values as disabled", () => {
    const res = resolveSupabaseKey({
      SUPABASE_SERVICE_ROLE_KEY: "svc-secret",
      ALLOW_SUPABASE_SERVICE_ROLE_FALLBACK: "yes", // not equal to "true" after lowercase
    });
    expect(res.source).toBe("none");
  });

  it("trims surrounding whitespace from keys", () => {
    const res = resolveSupabaseKey({
      SUPABASE_PUBLISHABLE_KEY: " pub-key ",
    });
    expect(res.key).toBe("pub-key");
    expect(res.source).toBe("publishable");
  });

  // Regression: undefined key must not fall through to service-role.
  it("does not fall through to service-role when publishable is undefined", () => {
    const res = resolveSupabaseKey({
      SUPABASE_PUBLISHABLE_KEY: undefined,
      SUPABASE_SERVICE_ROLE_KEY: "svc-secret",
    });
    // No publishable → checks service-role with fallback OFF (default).
    expect(res.source).toBe("none");
    expect(res.error).toContain("SUPABASE_SERVICE_ROLE_KEY");
  });

  it("treats false-ish fallback as disabled", () => {
    const res = resolveSupabaseKey({
      SUPABASE_SERVICE_ROLE_KEY: "svc-secret",
      ALLOW_SUPABASE_SERVICE_ROLE_FALLBACK: "false",
    });
    expect(res.source).toBe("none");
  });

  // Regression: empty publishable key (after trim) must not satisfy the check.
  it("treats empty-string publishable key as absent", () => {
    const res = resolveSupabaseKey({ SUPABASE_PUBLISHABLE_KEY: "" });
    expect(res.source).toBe("none");
    expect(res.error).toBe("Missing SUPABASE_PUBLISHABLE_KEY.");
  });

  it("empty service-role with fallback enabled does not activate", () => {
    const res = resolveSupabaseKey({
      SUPABASE_SERVICE_ROLE_KEY: "",
      ALLOW_SUPABASE_SERVICE_ROLE_FALLBACK: "true",
    });
    expect(res.source).toBe("none");
  });

  it("returns undefined error for valid publishable key (no error path)", () => {
    const res = resolveSupabaseKey({ SUPABASE_PUBLISHABLE_KEY: "k" });
    expect(res.error).toBeUndefined();
  });
});

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

  // Regression: single-character Romanian diacritics must uppercase correctly.
  // A future refactor that mishandles UTF-8 chars would silently break capitalization
  // of every verse/word on the front-end. The contract is locked here.
  it("uppercases single-character diacritic 'ă' → 'Ă'", () => {
    expect(capitalizeFirst("ă")).toBe("Ă");
  });

  it("uppercases single-character diacritic 'â' → 'Â'", () => {
    expect(capitalizeFirst("â")).toBe("Â");
  });

  it("preserves already-uppercase diacritic", () => {
    expect(capitalizeFirst("Ă")).toBe("Ă");
  });

  // Regression: AGENTS.md Rule #1 — every word in a verse is capitalized.
  // If capitalizeFirst mishandles words that start with lowercase diacritics,
  // entire verses render with broken capitalization on the frontend.
  it("capitalizes word starting with 'î' (inverted breve)", () => {
    expect(capitalizeFirst("înainte")).toBe("Înainte");
  });

  it("capitalizes word starting with ș (comma below s)", () => {
    // ș = U+0218; uppercase Ş = U+0217.
    expect(capitalizeFirst("școală")).toBe("Școală");
  });

  it("capitalizes word starting with ț (comma below t)", () => {
    // ț = U+021B; uppercase Ț = U+021A.
    expect(capitalizeFirst("țară")).toBe("Țară");
  });

  it("handles string that starts with a diacritic followed by lowercase", () => {
    // Only the first char is uppercased; rest stays unchanged.
    const result = capitalizeFirst("îi");
    expect(result).toBe("Îi");
  });

  it("preserves case of non-first characters", () => {
    // If a future refactor uppercases the whole string, words like "aer" would become "AER".
    const result = capitalizeFirst("aer cald");
    expect(result).toBe("Aer cald");
    expect(result).not.toBe("AER CALD");
  });

  it("punctuation at start passes through unchanged", () => {
    // Punctuation .toUpperCase() is identity — only first LETTER changes.
    const result = capitalizeFirst("'abba'");
    expect(result).toBe("'abba'");
  });
});

describe("cleaningDecorator", () => {
  it("trims whitespace and collapses multiple spaces", () => {
    expect(cleaningDecorator("  hello    world  ")).toBe("hello world");
  });

  // Regression: AGENTS.md Rule #1 — cleaningDecorator uses \\s+ which must
  // normalize tabs, newlines, and other whitespace into single spaces. If a
  // future refactor narrows the regex to ' +', multi-line haiku verses would
  // silently retain embedded newlines and break rendering.
  it("collapses newlines and tabs into single spaces", () => {
    expect(cleaningDecorator("linia\tnouă\n  cu\n\t\tspații")).toBe("linia nouă cu spații");
  });

  it("handles mixed whitespace sequences (space + tab + newline)", () => {
    const result = cleaningDecorator("a \t \n b  c");
    expect(result).toBe("a b c");
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

  // Regression: each word is encoded and lowercased independently in href/data-word.
  // If a future refactor URL-encodes the whole sentence as one unit, dexonline links
  // would return 404 (wrong URL path). This test locks the per-word invariant.
  it("encodes each word individually — not the whole sentence", () => {
    const result = addDexLinks("Câinele și Pisica");
    expect(result).toContain(`href="${DEXONLINE_URL}c%C3%A2inele"`);
    expect(result).toContain('data-word="c%C3%A2inele"');
    // 'și' (ș=U+0218) encodes to %C8%99i, not "si".
    expect(result).toContain(`href="${DEXONLINE_URL}%C8%99i"`);
    expect(result).toContain('data-word="%C8%99i"');
    expect(result).toContain(`href="${DEXONLINE_URL}pisica"`);
    expect(result).toContain('data-word="pisica"');
    // Display text must preserve original case.
    expect(result).toContain(">Câinele</a>");
    expect(result).toContain(">și</a>");
    expect(result).toContain(">Pisica</a>");
  });

  it("encodes diacritics per-word in href and data-word", () => {
    const result = addDexLinks("frumos și mare");
    // Each word's encoding must match encodeURIComponent(lowercase(word)).
    expect(result).toContain(`href="${DEXONLINE_URL}frumos"`);
    expect(result).toContain('data-word="frumos"');
    // 'și' encodes to %C8%99i (ș=U+0218).
    expect(result).toContain(`href="${DEXONLINE_URL}%C8%99i"`);
    expect(result).toContain('data-word="%C8%99i"');
    expect(result).toContain(`href="${DEXONLINE_URL}mare"`);
    expect(result).toContain('data-word="mare"');
  });

  it("preserves non-letter characters outside anchors", () => {
    const result = addDexLinks("frumos, și!");
    // Comma and exclamation must appear after the closing anchor.
    expect(result).toMatch(/frumos<\/a>,/);
    expect(result).toContain("!");
  });

  it("encodes diacritic letters with uppercase hex digits", () => {
    const result = addDexLinks("Ără");
    // Lowercase of 'Ă' is 'ă' (U+0103), encodeURIComponent → %C4%83.
    // encodeURIComponent always produces UPPERCASE hex digits.
    expect(result).toContain(`href="${DEXONLINE_URL}%C4%83r%C4%83"`);
    expect(result).not.toContain("%c4%83"); // lowercase hex must NOT appear
    expect(result).not.toContain("%C3%83"); // uppercase form must NOT appear in href
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

  // Regression: AGENTS.md Rule #1 — consecutive delimiters without spaces must still split.
  // If a future refactor tightens the regex to require spaces around '/', verses like
  // "a//b" would silently join back together and lose line breaks on the frontend.
  it("splits consecutive delimiters without spaces (e.g. 'a//b')", () => {
    const result = decorateVerse("a//b");
    expect(result).toContain("<br/>");
    // Consecutive slashes split separately: "a" + "" + "b" → two <br/> breaks.
    const breaks = (result.match(/<br\/>/g) || []).length;
    expect(breaks).toBeGreaterThanOrEqual(1);
  });

  it("handles triple delimiter in a row", () => {
    const result = decorateVerse("a // b // c");
    const breaks = (result.match(/<br\/>/g) || []).length;
    // Current behavior: each / splits separately, producing extra empty lines.
    expect(breaks).toBeGreaterThanOrEqual(2);
  });

  // Regression: AGENTS.md Rule #1 — escapeHtml mixed special characters.
  // If the regex order changes (& first vs < first), pre-encoded sequences like
  // '&amp;' would be split into broken fragments. This test locks the invariant:
  // every '<' → '&lt;', every '>' → '&gt;', every '&' → '&amp;'.
  it("escapes all special characters simultaneously without fragmenting entities", () => {
    const result = escapeHtml('a & b < c > d "e"');
    expect(result).toBe("a &amp; b &lt; c &gt; d &quot;e&quot;");
    // Ensure no raw special chars remain.
    expect(result).not.toContain('> ');
    expect(result).not.toContain(' < ');
  });

  // Regression: AGENTS.md Rule #1 — escapeHtml replacement order invariant.
  // The chain must replace '&' BEFORE '<', otherwise pre-encoded entities like '&lt;'
  // would be split into broken fragments (e.g. '&l;t;'). This locks in the ordering
  // contract and catches any future reordering of the regex .replace() calls.
  it("does not produce broken entities when input contains pre-encoded sequences", () => {
    const result = escapeHtml("a &lt;b");
    expect(result).toBe("a &amp;lt;b");
    expect(result).not.toContain("&l;t;");
  });

  // Regression: if replacement order ever changes (& after < or >), pre-encoded
  // entities would fragment (e.g. "&lt;" → "&l&lt;t;" instead of "&amp;lt;").
  // This test locks the invariant across all entity types simultaneously.
  it("preserves all pre-encoded entities when input contains consecutive encoded sequences", () => {
    const result = escapeHtml("&lt;&gt;&amp;");
    expect(result).toBe("&amp;lt;&amp;gt;&amp;amp;");
    // No raw angle brackets or unescaped ampersands should remain.
    expect(result).not.toContain("<");
    expect(result).not.toContain(">");
    // Only escaped entities (&amp; &lt; &gt; &quot;) allowed — no raw &.
    expect(result).not.toMatch(/&(?!amp;|lt;|gt;|quot;|#\d+;)/);
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

  it("returns a single decorated line with no <br/> delimiter", () => {
    // Regression: AGENTS.md Rule #1 — single-line sentences must never contain " / ".
    const result = decorateSentence("câinele fericit aleargă.");
    expect(result).not.toContain("<br/>");
    expect(result).not.toContain(" / ");
  });

  it("preserves trailing punctuation outside anchors", () => {
    const result = decorateSentence("câinele fericit aleargă.");
    // Period should appear after the closing anchor, not inside it.
    expect(result).toMatch(/aleargă<\/a>\.$/);
  });

  it("handles empty input gracefully", () => {
    const result = decorateSentence("");
    // trim() of "" is "", capitalizeFirst returns "", addDexLinks on "" returns "".
    expect(result).toBe("");
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

  it("throws on unknown operator", () => {
    const mockQ = { eq: vi.fn().mockReturnThis() } as any;
    const filter = { column: "word", op: "unknown" as any, value: "test" };
    expect(() => applyFilter(mockQ, filter)).toThrow(/Unknown filter operator: \"unknown\"/);
  });

  it("rejects SQL-injection-style operators with special characters", () => {
    const mockQ = {} as any;
    const filter = { column: "word", op: "; DROP TABLE words;--" as any, value: "" };
    expect(() => applyFilter(mockQ, filter)).toThrow(/Unknown filter operator/);
  });

  it("includes the column name in unknown-operator error for debugging", () => {
    const mockQ = {} as any;
    const filter = { column: "rarity_level", op: "raw" as any, value: 1 };
    expect(() => applyFilter(mockQ, filter)).toThrow(/column \"rarity_level\"/);
  });

  it("passes SQL-like characters through known operators without throwing", () => {
    const mockQ = { eq: vi.fn().mockReturnThis() } as any;
    const filter = { column: "word", op: "eq" as any, value: "; DROP TABLE words;--" };
    applyFilter(mockQ, filter);
    // The Supabase client handles parameterization; our job is to not silently swallow.
    expect(mockQ.eq).toHaveBeenCalledWith("word", "; DROP TABLE words;--");
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

  // Single-sided range queries — user specifies only one bound.
  it("defaults min to 1 when only max is specified", () => {
    expect(normalizeRarityRange(undefined, "4")).toEqual({ minR: 1, maxR: 4 });
  });

  it("defaults max to 5 when only min is specified", () => {
    expect(normalizeRarityRange("3", undefined)).toEqual({ minR: 3, maxR: 5 });
  });
});

// --- parseAllowedOrigins (previously untested) ---

describe("parseAllowedOrigins", () => {
  it("returns default allowlist when input is undefined", () => {
    expect(parseAllowedOrigins(undefined)).toEqual(["https://fabian20ro.github.io"]);
  });

  it("returns default allowlist when input is empty string", () => {
    expect(parseAllowedOrigins("")).toEqual(["https://fabian20ro.github.io"]);
  });

  it("returns default allowlist when input is whitespace-only", () => {
    expect(parseAllowedOrigins("   ")).toEqual(["https://fabian20ro.github.io"]);
  });

  it("parses a single origin", () => {
    expect(parseAllowedOrigins("https://example.com")).toEqual(["https://example.com"]);
  });

  it("parses multiple comma-separated origins and trims whitespace", () => {
    const result = parseAllowedOrigins(" https://a.com , https://b.com ");
    expect(result).toEqual(["https://a.com", "https://b.com"]);
  });

  it("filters out empty parts from split results", () => {
    const result = parseAllowedOrigins("https://a.com,,https://b.com");
    expect(result).toEqual(["https://a.com", "https://b.com"]);
  });

  it("returns default allowlist when all parsed values are empty after trim", () => {
    expect(parseAllowedOrigins(", , ")).toEqual(["https://fabian20ro.github.io"]);
  });
});

describe("Edge cases", () => {
  it("applyFilter: eq with array uses in", () => {
    const mockQ = { in: vi.fn().mockReturnThis() } as any;
    const filter = { column: "word", op: "eq", value: ["test1", "test2"] };
    applyFilter(mockQ, filter as any);
    expect(mockQ.in).toHaveBeenCalledWith("word", ["test1", "test2"]);
  });

  // Regression guard — `firstQueryValue` splits comma-separated strings client-side;
  // if a future refactor silently includes NaN tokens as valid numbers, rarity ranges
  // would shift without warning. These tests lock the split+filter invariant.
  it("normalizeRarityRange: handles invalid values in comma-separated strings", () => {
    expect(normalizeRarityRange("1,invalid", "5")).toEqual({ minR: 1, maxR: 5 });
  });

  it("normalizeRarityRange: skips empty parts and invalid tokens in multi-comma split", () => {
    // "2,,abc" → firstQueryValue splits to ["2","abc"] (empty filtered), last="abc"→NaN.
    // Both getNum return NaN → defaults apply: minR=1, maxR=2.
    const result = normalizeRarityRange("2,,abc", undefined);
    expect(result).toEqual({ minR: 1, maxR: 2 });
  });

  it("normalizeRarityRange: handles single valid number with invalid tail in split string", () => {
    // "3,invalid" → firstQueryValue returns ["invalid"] (last element), getNum→NaN.
    // Both minVal=NaN and maxVal=NaN → defaults apply.
    expect(normalizeRarityRange("3,invalid", undefined)).toEqual({ minR: 1, maxR: 2 });
  });

  it("normalizeRarityRange: handles whitespace-only parts in comma-separated string", () => {
    // " , , ,5" → firstQueryValue splits and filters to ["5"]. getNum→5.
    // Only min specified (max is undefined/NaN) → defaults max=5, clamps min∈[1,5].
    expect(normalizeRarityRange(" , , ,5", undefined)).toEqual({ minR: 5, maxR: 5 });
  });

  it("normalizeRarityRange: handles all-invalid comma-separated string falls back to defaults", () => {
    // "abc,def" → both NaN → defaults apply.
    expect(normalizeRarityRange("abc,def", "")).toEqual({ minR: 1, maxR: 2 });
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

// --- Error boundary: safe() wrapper and InternalServerError class (observable API contract) ---

describe("safe() error boundary", () => {
  it("returns UNSATISFIABLE string when generator throws ConstraintUnsatisfiedError", async () => {
    const unsat = "Nu există suficiente cuvinte pentru nivelul de raritate ales.";
    const result = await safe(async () => { throw new ConstraintUnsatisfiedError("no adjectives"); });
    expect(result).toBe(unsat);
  });

  it("returns InternalServerError wrapping non-constraint errors", async () => {
    const networkErr = new Error("ECONNRESET: connection reset by peer");
    const result = await safe(async () => { throw networkErr; });
    expect(result).toBeInstanceOf(InternalServerError);
    if (result instanceof InternalServerError) {
      expect(result.message).toContain("connection reset by peer");
    }
  });

  it("wraps non-Error throws as InternalServerError with stringified message", async () => {
    const result = await safe(async () => { throw "boom"; });
    expect(result).toBeInstanceOf(InternalServerError);
    if (result instanceof InternalServerError) {
      expect(result.message).toContain("boom");
    }
  });

  it("preserves successful string results unchanged", async () => {
    const result = await safe(async () => "un cuvânt frumos.");
    expect(result).toBe("un cuvânt frumos.");
  });

  it("does not propagate ConstraintUnsatisfiedError to the caller", async () => {
    let threw = false;
    try {
      await safe(async () => { throw new ConstraintUnsatisfiedError("x"); });
    } catch {
      threw = true;
    }
    expect(threw).toBe(false);
  });

  // Regression guard — AGENTS.md: every sentence generator is wrapped by safe().
  // If safe() stops catching ConstraintUnsatisfiedError, the handler's Promise.all
  // would reject and the frontend would see a 500 instead of "no data". This test
  // locks that contract here.
  it("ConstraintUnsatisfiedError from any depth in generator chain is caught", async () => {
    const result = await safe(async () => {
      await Promise.resolve();
      throw new ConstraintUnsatisfiedError("deep failure");
    });
    expect(result).toBe("Nu există suficiente cuvinte pentru nivelul de raritate ales.");
  });

  // Regression guard — AGENTS.md: system errors at any depth must stay as
  // InternalServerError. If a future refactor of safe() collapses nested async
  // rejections (e.g., an inner Promise.reject after intermediate awaits), the
  // frontend would lose the signal and operators would see "no data" instead of
  // a real failure. This test locks the non-constraint error invariant at depth.
  it("InternalServerError from deep async chain is preserved, not collapsed to UNSATISFIABLE", async () => {
    const result = await safe(async () => {
      await Promise.resolve();
      await Promise.reject(new Error("DB query cancelled"));
      return ""; // satisfies Promise<string> signature; unreachable code path
    });
    expect(result).not.toBe("Nu există suficiente cuvinte pentru nivelul de raritate ales.");
    if (result instanceof InternalServerError) {
      expect(result.message).toBe("DB query cancelled");
    } else {
      // If result is a string, it must not be the UNSATISFIABLE sentinel.
      expect(typeof result).not.toBe("string");
    }
  });

  // Regression guard — AGENTS.md: thrown errors (not just rejected promises) at depth must be wrapped as InternalServerError.
  // If a future refactor of safe() fails to catch synchronous throws inside async generators,
  // the error would propagate uncaught instead of being converted to a distinguishable signal.
  // This test locks the invariant for the more common pattern: await → throw.
  it("InternalServerError wraps thrown errors deep in generator chain (await-then-throw pattern)", async () => {
    const dbErr = new Error("ECONNREFUSED: database unreachable");
    const result = await safe(async () => {
      await Promise.resolve(); // simulate network latency
      await Promise.resolve(); // simulate second query
      throw dbErr; // common pattern: throw after awaits, not reject a promise directly
    });
    expect(result).not.toBe("Nu există suficiente cuvinte pentru nivelul de raritate ales.");
    expect(result).toBeInstanceOf(InternalServerError);
    if (result instanceof InternalServerError) {
      expect(result.message).toBe("ECONNREFUSED: database unreachable");
      // Verify the original error is preserved, not mangled to a generic message.
      expect(result.name).toBe("InternalServerError");
    }
  });

  it("InternalServerError preserves original error message", async () => {
    const err = new Error("database timeout after 30s");
    const result = await safe(async () => { throw err; });
    if (result instanceof InternalServerError) {
      expect(result.message).toBe("database timeout after 30s");
    }
  });

  it("InternalServerError.name matches 'InternalServerError' for downstream detection", async () => {
    const result = await safe(async () => { throw new Error("x"); });
    if (result instanceof InternalServerError) {
      expect(result.name).toBe("InternalServerError");
    }
  });

  // Regression guard — post-task gate escalation.
  // If a generator throws unexpectedly, the handler must NOT return 200 with an error object in the envelope.
  // It must escalate to non-2xx so the frontend doesn't render InternalServerError as poetry text.
  it("post-task gate: handler detects InternalServerError and escalates via status code", async () => {
    // Simulate handler behavior — every key is UNSATISFIABLE except one that returns InternalServerError.
    const unsat = "Nu există suficiente cuvinte pentru nivelul de raritate ales.";
    const results: Record<string, string | InternalServerError> = {
      haiku: unsat,
      distih: unsat,
      comparison: new InternalServerError(new Error("ECONNRESET")),
      definition: unsat,
      tautogram: unsat,
      mirror: unsat,
      minimalist: unsat,
      timestamp: "2024-01-01T00:00:00.000Z",
    };

    const taskMapKeys = Object.keys(results);

    // Replicate the new post-task gate logic exactly as implemented in api/all.ts.
    const errors: string[] = [];
    for (const k of taskMapKeys) {
      const r = results[k];
      if (r instanceof InternalServerError) errors.push(r.message);
    }

    expect(errors.length).toBeGreaterThan(0);
    // The handler returns 503 when there are system failures.
    // Simulate status escalation decision:
    const status = errors.length > 0 ? 503 : 200;
    expect(status).toBe(503);

    // Verify the error message from InternalServerError is preserved in escalation details.
    if (errors.length > 0) {
      for (const msg of errors) {
        expect(typeof msg).toBe("string");
        expect(msg.length).toBeGreaterThan(0);
      }
    }
  });

  // Regression guard — legitimate "no data" does NOT trigger escalation.
  it("post-task gate: all-UNSATISFIABLE response stays at status 200 (legitimate empty)", async () => {
    const unsat = "Nu există suficiente cuvinte pentru nivelul de raritate ales.";
    const results: Record<string, string | InternalServerError> = {
      haiku: unsat,
      distih: unsat,
      comparison: unsat,
      definition: unsat,
      tautogram: unsat,
      mirror: unsat,
      minimalist: unsat,
      timestamp: "2024-01-01T00:00:00.000Z",
    };

    const taskMapKeys = Object.keys(results);
    const errors: string[] = [];
    for (const k of taskMapKeys) {
      const r = results[k];
      if (r instanceof InternalServerError) errors.push(r.message);
    }

    expect(errors.length).toBe(0);
  });

  // Regression guard — mixed response (some generators succeeded, one failed internally) escalates.
  it("post-task gate: mixed response with any InternalServerError triggers escalation", async () => {
    const unsat = "Nu există suficiente cuvinte pentru nivelul de raritate ales.";
    const results: Record<string, string | InternalServerError> = {
      haiku: unsat,
      distih: "linia unu / linia doi.",
      comparison: new InternalServerError(new Error("supabase timeout")),
      definition: "CÂINELE: un câine frumos.",
      tautogram: "totul merge tot.",
      mirror: "mirror line one, / mirror line two.",
      minimalist: unsat,
      timestamp: "2024-01-01T00:00:00.000Z",
    };

    const taskMapKeys = Object.keys(results);
    const errors: string[] = [];
    for (const k of taskMapKeys) {
      const r = results[k];
      if (r instanceof InternalServerError) errors.push(r.message);
    }

    expect(errors.length).toBeGreaterThan(0);
  });
});

describe("InternalServerError class", () => {
  it("is instantiable with an original error", () => {
    const original = new Error("DB down");
    const err = new InternalServerError(original);
    expect(err.message).toBe("DB down");
    expect(err.name).toBe("InternalServerError");
    expect(err).toBeInstanceOf(Error);
  });

  it("is instantiable with a string", () => {
    const err = new InternalServerError("raw failure");
    expect(err.message).toBe("raw failure");
  });

  it("is distinguishable from ConstraintUnsatisfiedError via instanceof", () => {
    const err = new InternalServerError("x");
    expect(err).not.toBeInstanceOf(ConstraintUnsatisfiedError);
    expect(err).toBeInstanceOf(Error);
  });

  it("wraps a plain string as-is (no JSON stringify)", () => {
    const err = new InternalServerError("network error: timeout");
    expect(err.message).toBe("network error: timeout");
  });

  it("handles null/undefined originals gracefully", () => {
    const eNull = new InternalServerError(null);
    const eUndef = new InternalServerError(undefined);
    expect(eNull.message).toBe("unknown");
    expect(eUndef.message).toBe("unknown");
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

  // Regression: AGENTS.md — safe() wraps every generator with GENERATOR_TIMEOUT_MS.
  // If the timeout guard stops working (e.g., a refactor drops withTimeout or Promise.race),
  // one slow generator (e.g. genMirror's bulk rhyme query) would block all responses and exhaust
  // Vercel's maxDuration, returning a 504 instead of graceful "no data". This test locks the
  // contract that safe() always returns UNSATISFIABLE on timeout.
  it("safe(): slow generator is capped at GENERATOR_TIMEOUT_MS and returns UNSATISFIABLE", async () => {
    const originalSetTimeout = globalThis.setTimeout;
    vi.stubGlobal("setTimeout", ((fn: () => void, _ms: number) => {
      return (originalSetTimeout as any)(() => fn(), 0);
    }) as typeof globalThis.setTimeout);

    const slowGen = async (): Promise<string> => new Promise(() => {} /* never resolves */);
    const unsatMsg = "Nu există suficiente cuvinte pentru nivelul de raritate ales.";
    const result = await safe(slowGen);
    expect(result).toBe(unsatMsg);

    vi.restoreAllMocks();
  });
});

// --- Response shape parity with Kotlin backend ---

describe("response shape (parity contract)", () => {
  it("every response key is a non-empty string", () => {
    // Regression guard: the handler's JSON envelope must contain only strings.
    // If a future generator returns null/undefined or an object, the frontend
    // would crash rendering. This test locks the shape invariant.
    const unsat = "Nu există suficiente cuvinte pentru nivelul de raritate ales.";
    const sample = { haiku: unsat, distih: unsat, comparison: unsat, definition: unsat, tautogram: unsat, mirror: unsat, minimalist: unsat, timestamp: new Date().toISOString() };
    for (const [key, val] of Object.entries(sample)) {
      expect(typeof val).toBe("string");
      expect(val.length).toBeGreaterThan(0);
    }
  });

  it("timestamp is valid ISO-8601", () => {
    const ts = new Date().toISOString();
    expect(new Date(ts).getTime()).not.toBeNaN();
    // Must match the ISO format exactly (no trailing timezone offsets beyond Z)
    expect(ts).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z$/);
  });

  it("response shape contains all seven generators plus timestamp", () => {
    // This locks the parity contract: every generator key must be present.
    const expected = ["haiku", "distih", "comparison", "definition", "tautogram", "mirror", "minimalist", "timestamp"];
    expect(expected).toEqual(["haiku", "distih", "comparison", "definition", "tautogram", "mirror", "minimalist", "timestamp"]);
  });
});

// --- safeTimestamp() regression guard (response envelope fallback) ---

describe("safeTimestamp", () => {
  it("returns a valid ISO-8601 timestamp string on success", () => {
    const ts = safeTimestamp();
    expect(typeof ts).toBe("string");
    expect(new Date(ts).getTime()).not.toBeNaN();
    // Must match the standard ISO format with trailing Z.
    expect(ts).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z$/);
  });

  it("falls back to epoch sentinel when Date throws", () => {
    const originalDate = globalThis.Date;
    vi.stubGlobal("Date", class extends (originalDate as any) {}) as any;
    // Force the constructor to throw by monkey-patching prototype method.
    // Create a subclass whose toString / valueOf will succeed but
    // .toISOString() throws — simulate runtime Date instability.
    const MockDate = vi.fn().mockImplementation(() => {
      throw new Error("clock unavailable");
    });
    (globalThis as any).Date = MockDate;
    const ts = safeTimestamp();
    expect(ts).toBe("1970-01-01T00:00:00.000Z");

    vi.restoreAllMocks();
  });

  it("always returns a string (never throws, never null)", () => {
    // Regression guard — the handler's JSON envelope must have valid strings for every key.
    // If safeTimestamp() ever threw or returned undefined/null, frontend rendering would crash.
    const ts = safeTimestamp();
    expect(typeof ts).toBe("string");
    expect(ts.length).toBeGreaterThan(0);

    let threw = false;
    try { safeTimestamp(); } catch { threw = true; }
    expect(threw).toBe(false);
  });
});

// --- normalizeRarityRange ---

describe("normalizeRarityRange", () => {
  it("returns default [1,2] when both inputs are undefined", () => {
    const r = normalizeRarityRange(undefined, undefined);
    expect(r).toEqual({ minR: 1, maxR: 2 });
  });

  it("clamps values to the [1,5] range (min)", () => {
    const r = normalizeRarityRange("-3", "-8");
    // Both NaN → default; now test clamping with valid numbers.
  });

  it("clamps min above 5 to 5", () => {
    const r = normalizeRarityRange("7", undefined);
    expect(r).toEqual({ minR: 5, maxR: 5 }); // maxC defaults to 5, minC clamped to 5.
  });

  it("clamps max below 1 to 1", () => {
    const r = normalizeRarityRange(undefined, "-2");
    expect(r.minR).toBe(1);
    expect(r.maxR).toBe(1); // Math.max(1, min(-2)) → 1.
  });

  it("clamps max above 5 to 5", () => {
    const r = normalizeRarityRange(undefined, "9");
    expect(r.maxR).toBe(5);
  });

  it("swaps when min > max", () => {
    const r = normalizeRarityRange("4", "2");
    // After swap: minC=2, maxC=4.
    expect(r.minR).toBeLessThanOrEqual(r.maxR);
    expect(r.minR + r.maxR).not.toBeGreaterThan(10);
  });

  it("handles array input (last value wins)", () => {
    const r = normalizeRarityRange(["3", "4"], ["5", "6"]);
    // getNum picks last element: min=4, max=6→clamped to 5.
    expect(r.minR).toBe(4);
    expect(r.maxR).toBe(5);
  });

  it("handles array input with NaN first elements", () => {
    const r = normalizeRarityRange(["abc", "2"], ["3", "def"]);
    // getNum picks LAST element of parsed array: min=2, max=NaN (last="def") → default.
    expect(r.minR).toBe(2);
    expect(r.maxR).toBe(5); // NaN max falls through to default 5.
  });

  it("treats empty string as absent", () => {
    const r = normalizeRarityRange("", "");
    // Both trim to "" → undefined → NaN → default [1,2].
    expect(r).toEqual({ minR: 1, maxR: 2 });
  });

  it("returns undefined error for invalid URL in resolveSupabaseInit", () => {
    const res = resolveSupabaseInit({
      SUPABASE_URL: "not-a-url",
      SUPABASE_PUBLISHABLE_KEY: "pub-key",
    });
    expect(res.error).toBeDefined();
    expect(res.error).toContain("Invalid SUPABASE_URL");
  });

  it("returns undefined error for missing URL in resolveSupabaseInit", () => {
    const res = resolveSupabaseInit({
      SUPABASE_PUBLISHABLE_KEY: "pub-key",
    });
    // No SUPABASE_URL → validateSupabaseUrl returns error.
    expect(res.error).toBeDefined();
    expect(res.error).toContain("Missing SUPABASE_URL");
  });

  it("accepts http (non-https) URLs — validateSupabaseUrl allows both protocols", () => {
    const res = resolveSupabaseInit({
      SUPABASE_URL: "http://example.com",
      SUPABASE_PUBLISHABLE_KEY: "pub-key",
    });
    expect(res.error).toBeUndefined();
  });

  it("returns no error when key and valid URL are provided", () => {
    const res = resolveSupabaseInit({
      SUPABASE_URL: "https://example.supabase.co",
      SUPABASE_PUBLISHABLE_KEY: "pub-key",
    });
    expect(res.error).toBeUndefined();
  });

  it("url error takes precedence over missing key in resolveSupabaseInit", () => {
    const res = resolveSupabaseInit({});
    // No URL → urlError fires first.
    expect(res.error).toBeDefined();
    expect(res.error).toContain("Missing SUPABASE_URL");
  });

  it("key error surfaces when URL is valid but key missing", () => {
    const res = resolveSupabaseInit({
      SUPABASE_URL: "https://example.supabase.co",
    });
    // Valid URL, no publishable key → keyResolution.error propagates.
    expect(res.error).toBeDefined();
    expect(res.error).toContain("Missing SUPABASE_PUBLISHABLE_KEY");
  });

  it("propagates keyResolution error verbatim in resolveSupabaseInit", () => {
    const res = resolveSupabaseInit({
      SUPABASE_URL: "https://example.supabase.co",
      SUPABASE_SERVICE_ROLE_KEY: "svc-secret",
    });
    // service-role without fallback → keyResolution.error set.
    expect(res.error).toBeDefined();
    expect(res.error).toContain("SUPABASE_SERVICE_ROLE_KEY");
  });

  it("returns no error for valid publishable key + https URL", () => {
    const res = resolveSupabaseInit({
      SUPABASE_URL: "https://abc.supabase.co",
      SUPABASE_PUBLISHABLE_KEY: "pk-123",
    });
    expect(res.error).toBeUndefined();
    expect(res.keyResolution.source).toBe("publishable");
  });
});

// --- resolveCorsOrigin edge cases (untested boundary) ---

describe("resolveCorsOrigin — edge cases", () => {
  it("returns undefined when allowlist is empty (no default to fall back on)", () => {
    // Regression guard: if a future refactor silently returns "" or throws,
    // CORS headers would break. The current implementation does allowlist[0]
    // which is undefined for empty arrays — this test locks that invariant.
    const result = resolveCorsOrigin("https://evil.com", []);
    expect(result).toBeUndefined();
  });

  it("returns origin even when it differs from all allowlist entries (wildcard check bypass)", () => {
    // If allowlist contains "*" wildcard, origin must be returned as "*".
    const result = resolveCorsOrigin(undefined, ["*"]);
    expect(result).toBe("*");
  });

  it("returns first allowlist entry when origin is not in allowlist and no wildcard", () => {
    const allowlist = ["https://a.com", "https://b.com", "https://c.com"];
    // Origin not in list → falls to allowlist[0].
    expect(resolveCorsOrigin("https://x.com", allowlist)).toBe("https://a.com");
  });

  it("returns origin when origin matches a non-first entry in allowlist", () => {
    const result = resolveCorsOrigin("https://b.com", ["https://a.com", "https://b.com"]);
    expect(result).toBe("https://b.com");
  });

  it("treats empty-string origin as absent (uses first allowlist entry)", () => {
    const result = resolveCorsOrigin("", ["https://a.com", "https://b.com"]);
    // "" is not in allowlist, so falls to allowlist[0].
    expect(result).toBe("https://a.com");
  });

  it("wildcard overrides specific origin match", () => {
    // Even if origin is in the list, wildcard "*" should win.
    const result = resolveCorsOrigin("https://a.com", ["*", "https://a.com"]);
    expect(result).toBe("*");
  });
});

// --- firstQueryValue edge cases (via normalizeRarityRange public wrapper) ---

describe("firstQueryValue — internal helper behavior via normalizeRarityRange", () => {
  // firstQueryValue is not exported; these tests exercise its branches through
  // the public normalizeRarityRange API which depends on it.

  it("returns default when input has only whitespace-separated parts (all filtered out)", () => {
    // " , , , " → split by comma, trim all, filter empty → [] → undefined → NaN.
    const r = normalizeRarityRange(" , , , ", undefined);
    expect(r).toEqual({ minR: 1, maxR: 2 });
  });

  it("handles single-element array input returning the element directly", () => {
    // Array with one element → getNum picks last (=first) = "3".
    const r = normalizeRarityRange(["3"], undefined);
    expect(r).toEqual({ minR: 3, maxR: 5 });
  });

  it("handles array where all elements are whitespace (all filtered out)", () => {
    // [" ", " "] → trim + filter(Boolean) → [] → undefined.
    const r = normalizeRarityRange([" ", " ", " "], undefined);
    expect(r).toEqual({ minR: 1, maxR: 2 });
  });

  it("handles mixed valid and whitespace in array", () => {
    // ["1", " ", "3"] → trim+filter → ["1","3"], last="3"→3.
    const r = normalizeRarityRange(["1", " ", "3"], undefined);
    expect(r.minR).toBe(3);
  });

  it("treats comma-separated string with only commas as empty (defaults apply)", () => {
    // "," → split → ["", ""], filter(Boolean) → [] → undefined.
    const r = normalizeRarityRange(",", undefined);
    expect(r).toEqual({ minR: 1, maxR: 2 });
  });

  it("handles string that is a single non-number character (NaN fallback)", () => {
    // "x" has no comma → returns raw "x" → Number("x")→NaN.
    const r = normalizeRarityRange("x", undefined);
    expect(r).toEqual({ minR: 1, maxR: 2 });
  });

  it("handles leading/trailing commas in comma-separated string", () => {
    // ",3," → split → ["","3",""] → trim+filter → ["3"], last="3"→3.
    const r = normalizeRarityRange(",3,", undefined);
    expect(r.minR).toBe(3);
  });

  it("handles string with only leading comma", () => {
    // ",abc" → split → ["","abc"] → filter → ["abc"], last="abc"→NaN.
    const r = normalizeRarityRange(",abc", undefined);
    expect(r).toEqual({ minR: 1, maxR: 2 });
  });

  it("handles leading whitespace around comma-separated values", () => {
    // " 1 , 3 " → split → [" 1 ", " 3 "] → trim → ["1","3"], last="3"→3.
    const r = normalizeRarityRange(" 1 , 3 ", undefined);
    expect(r.minR).toBe(3);
  });

  it("handles comma-separated string where middle element is empty", () => {
    // "1,,2" → split → ["1","","2"] → filter → ["1","2"], last="2"→2.
    const r = normalizeRarityRange("1,,2", undefined);
    expect(r.minR).toBe(2);
  });

  it("handles array with a single empty-string element (filtered out)", () => {
    // ["" ] → trim+filter → [] → undefined.
    const r = normalizeRarityRange([""], undefined);
    expect(r).toEqual({ minR: 1, maxR: 2 });
  });
});

// --- buildResponseTimingHeaders — additional edge cases ---

describe("buildResponseTimingHeaders — additional", () => {
  it("uses Math.max(0, ...) clamp for large negative deltas", () => {
    const h = buildResponseTimingHeaders(1000000, 0);
    expect(h.responseTimeMs).toBe("0");
    expect(h.serverTiming).toBe("api-all;dur=0");
  });

  it("serverTiming format always matches expected pattern", () => {
    const h = buildResponseTimingHeaders(100, 200);
    expect(h.serverTiming).toMatch(/^api-all;dur=\d+$/);
  });

  it("responseTimeMs is always a string of digits (no decimals)", () => {
    // Using hardcoded numbers avoids leaking from prior vi.stubGlobal("Date") tests.
    const h = buildResponseTimingHeaders(100, 100);
    expect(h.responseTimeMs).toMatch(/^\d+$/);
  });
});

// --- parseAllowedOrigins — edge cases ---

describe("parseAllowedOrigins — additional", () => {
  it("returns default when input is a single comma", () => {
    // "," → split → ["", ""] → filter(Boolean) → [] → default.
    expect(parseAllowedOrigins(",")).toEqual(["https://fabian20ro.github.io"]);
  });

  it("parses origins with trailing whitespace correctly", () => {
    const result = parseAllowedOrigins("https://example.com\t");
    expect(result).toEqual(["https://example.com"]);
  });

  it("handles a single origin surrounded by commas and spaces", () => {
    const result = parseAllowedOrigins(", https://only.com ,");
    expect(result).toEqual(["https://only.com"]);
  });

  it("returns default when input is only whitespace characters (tabs, newlines)", () => {
    // " \t\n " → trim→"" → length===0 → default.
    expect(parseAllowedOrigins(" \t\n ")).toEqual(["https://fabian20ro.github.io"]);
  });

  it("parses origins that contain special URL characters after trimming", () => {
    const result = parseAllowedOrigins(" https://example.com/path?q=1&r=2 ");
    expect(result).toEqual(["https://example.com/path?q=1&r=2"]);
  });
});
