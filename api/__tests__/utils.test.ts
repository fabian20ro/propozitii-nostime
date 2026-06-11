import { describe, it, expect } from "vitest";
import {
  validateSupabaseUrl,
  resolveSupabaseKey,
  parseAllowedOrigins,
  resolveCorsOrigin,
  normalizeRarityRange
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

  describe("normalizeRarityRange", () => {
    it("handles valid range", () => {
      expect(normalizeRarityRange("1", "4")).toEqual({ minR: 1, maxR: 4 });
    });
    it("clamps to [1, 5]", () => {
      expect(normalizeRarityRange("0", "6")).toEqual({ minR: 1, maxR: 5 });
    });
    it("handles array inputs", () => {
      expect(normalizeRarityRange(["2"], ["5"])).toEqual({ minR: 2, maxR: 5 });
    });
  });
});
