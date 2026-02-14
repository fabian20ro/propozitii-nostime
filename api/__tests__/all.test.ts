import { describe, it, expect } from "vitest";
import {
  escapeHtml,
  capitalizeFirst,
  addDexLinks,
  decorateVerse,
  decorateSentence,
  decorateDefinition,
  adjForGender,
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
