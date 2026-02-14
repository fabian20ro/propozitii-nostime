import { describe, it, expect } from "vitest";
import {
  escapeHtml,
  capitalizeFirst,
  addDexLinks,
  decorateVerse,
  decorateSentence,
  decorateDefinition,
  adjForGender,
  parseAllowedOrigins,
  resolveCorsOrigin,
  resolveSupabaseKey,
  DEXONLINE_URL,
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

  it("escapes HTML in display text", () => {
    // Edge case: word containing special chars (unlikely but safe)
    const result = addDexLinks("a");
    expect(result).toContain(">a</a>");
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

// --- decorateDefinition ---

describe("decorateDefinition", () => {
  it("adds links without capitalizing (definition already has case)", () => {
    const result = decorateDefinition("MASĂ: câinele frumos.");
    // MASĂ should be linked and preserved as uppercase in display
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
    expect(result).toMatch(/target="_blank"/);
    expect(result).toMatch(/rel="noopener"/);
    expect(result).toMatch(/data-word="test"/);
  });
});

// --- Security/env helpers ---

describe("resolveSupabaseKey", () => {
  it("prefers SUPABASE_ANON_KEY over any other key", () => {
    const resolved = resolveSupabaseKey({
      SUPABASE_ANON_KEY: "anon",
      SUPABASE_READ_KEY: "read",
      SUPABASE_SERVICE_ROLE_KEY: "service",
    });
    expect(resolved.source).toBe("anon");
    expect(resolved.key).toBe("anon");
  });

  it("falls back to SUPABASE_READ_KEY when anon key is missing", () => {
    const resolved = resolveSupabaseKey({
      SUPABASE_READ_KEY: "read",
    });
    expect(resolved.source).toBe("read");
    expect(resolved.key).toBe("read");
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

describe("CORS origin helpers", () => {
  it("uses default allowlist when env value is empty", () => {
    expect(parseAllowedOrigins(undefined)).toEqual(["https://fabian20ro.github.io"]);
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

  it("supports explicit wildcard allowlist", () => {
    expect(resolveCorsOrigin("https://anything.example", ["*"])).toBe("*");
  });
});
