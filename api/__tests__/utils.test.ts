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
  addDexLinks,
  buildResponseTimingHeaders
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
  });

  describe("buildResponseTimingHeaders", () => {
    it("returns correct timing headers", () => {
      const start = 1000;
      const end = 1500;
      const res = buildResponseTimingHeaders(start, end);
      expect(res.serverTiming).toBe("api-all;dur=500");
      expect(res.responseTimeMs).toBe("500");
    });
  });
