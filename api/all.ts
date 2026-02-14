import type { VercelRequest, VercelResponse } from "@vercel/node";
import { createClient, SupabaseClient } from "@supabase/supabase-js";

// --- Environment validation (deferred for testability) ---

const supabaseUrl = process.env.SUPABASE_URL ?? "";
export const DEFAULT_ALLOWED_ORIGINS = ["https://fabian20ro.github.io"];

export interface SupabaseKeyResolution {
  key: string;
  source: "anon" | "read" | "service-role" | "none";
  error?: string;
}

export function resolveSupabaseKey(
  env: Record<string, string | undefined>
): SupabaseKeyResolution {
  const anon = (env.SUPABASE_ANON_KEY ?? "").trim();
  if (anon) return { key: anon, source: "anon" };

  const readOnly = (env.SUPABASE_READ_KEY ?? "").trim();
  if (readOnly) return { key: readOnly, source: "read" };

  const serviceRole = (env.SUPABASE_SERVICE_ROLE_KEY ?? "").trim();
  const allowServiceFallback =
    (env.ALLOW_SUPABASE_SERVICE_ROLE_FALLBACK ?? "").toLowerCase() === "true";
  if (serviceRole && allowServiceFallback) {
    return { key: serviceRole, source: "service-role" };
  }
  if (serviceRole) {
    return {
      key: "",
      source: "none",
      error:
        "SUPABASE_SERVICE_ROLE_KEY is set but disabled for this public endpoint. " +
        "Use SUPABASE_ANON_KEY (preferred) or SUPABASE_READ_KEY.",
    };
  }
  return {
    key: "",
    source: "none",
    error: "Missing SUPABASE_ANON_KEY (preferred) or SUPABASE_READ_KEY.",
  };
}

export function parseAllowedOrigins(raw: string | undefined): string[] {
  if (!raw || raw.trim().length === 0) return DEFAULT_ALLOWED_ORIGINS;
  const origins = raw
    .split(",")
    .map((v) => v.trim())
    .filter(Boolean);
  return origins.length > 0 ? origins : DEFAULT_ALLOWED_ORIGINS;
}

export function resolveCorsOrigin(origin: string | undefined, allowlist: string[]): string {
  if (allowlist.includes("*")) return "*";
  if (origin && allowlist.includes(origin)) return origin;
  return allowlist[0];
}

const keyResolution = resolveSupabaseKey(process.env);
const allowedOrigins = parseAllowedOrigins(process.env.ALLOWED_ORIGINS);

const supabase: SupabaseClient = supabaseUrl && keyResolution.key
  ? createClient(supabaseUrl, keyResolution.key)
  : (null as unknown as SupabaseClient); // null when imported in test environment

if (keyResolution.source === "service-role") {
  console.warn(
    "[security] api/all.ts uses SUPABASE_SERVICE_ROLE_KEY fallback; prefer SUPABASE_ANON_KEY."
  );
}

export const DEXONLINE_URL = "https://dexonline.ro/definitie/";
const UNSATISFIABLE =
  "Nu există suficiente cuvinte pentru nivelul de raritate ales.";

// --- Types ---

export interface Noun {
  word: string;
  gender: string;
  syllables: number;
  rhyme: string;
  articulated: string;
}

export interface Adjective {
  word: string;
  syllables: number;
  rhyme: string;
  feminine: string;
}

export interface Verb {
  word: string;
  syllables: number;
  rhyme: string;
}

type WordRow = Noun | Adjective | Verb;

interface QueryFilter {
  column: string;
  op: "eq" | "gte" | "lte" | "like" | "neq";
  value: string | number;
}

export function adjForGender(adj: Adjective, gender: string): string {
  return gender === "F" ? adj.feminine : adj.word;
}

// --- Generic random-row helper (single count + single fetch) ---

async function randomRow<T extends WordRow>(
  select: string,
  filters: QueryFilter[]
): Promise<T | null> {
  // 1) Count
  let countQ = supabase.from("words").select("word", { count: "exact", head: true });
  for (const f of filters) countQ = applyFilter(countQ, f);
  const { count } = await countQ;
  if (!count) return null;

  // 2) Fetch one at random offset
  const offset = Math.floor(Math.random() * count);
  let dataQ = supabase.from("words").select(select).range(offset, offset).limit(1);
  for (const f of filters) dataQ = applyFilter(dataQ, f);
  const { data } = await dataQ;
  if (!data || data.length === 0) return null;
  return data[0] as unknown as T;
}

function applyFilter(q: any, f: QueryFilter): any {
  switch (f.op) {
    case "eq": return q.eq(f.column, f.value);
    case "gte": return q.gte(f.column, f.value);
    case "lte": return q.lte(f.column, f.value);
    case "like": return q.like(f.column, f.value);
    case "neq": return q.neq(f.column, f.value);
  }
}

function rarityFilters(minR: number, maxR: number): QueryFilter[] {
  return [
    { column: "rarity_level", op: "gte", value: minR },
    { column: "rarity_level", op: "lte", value: maxR },
  ];
}

function excludeFilters(exclude: string[]): QueryFilter[] {
  return exclude.map((w) => ({ column: "word", op: "neq" as const, value: w }));
}

// --- Word query functions ---

const NOUN_SELECT = "word, gender, syllables, rhyme, articulated";
const ADJ_SELECT = "word, syllables, rhyme, feminine";
const VERB_SELECT = "word, syllables, rhyme";

async function randomNoun(
  minR: number, maxR: number, exclude: string[] = []
): Promise<Noun> {
  const row = await randomRow<Noun>(NOUN_SELECT, [
    { column: "type", op: "eq", value: "N" },
    ...rarityFilters(minR, maxR),
    ...excludeFilters(exclude),
  ]);
  if (!row) throw new Error("No nouns found");
  return row;
}

async function randomNounByArticulatedSyllables(
  syllables: number, minR: number, maxR: number, exclude: string[] = []
): Promise<Noun | null> {
  return randomRow<Noun>(NOUN_SELECT, [
    { column: "type", op: "eq", value: "N" },
    { column: "articulated_syllables", op: "eq", value: syllables },
    ...rarityFilters(minR, maxR),
    ...excludeFilters(exclude),
  ]);
}

async function randomNounByPrefix(
  prefix: string, minR: number, maxR: number, exclude: string[] = []
): Promise<Noun | null> {
  return randomRow<Noun>(NOUN_SELECT, [
    { column: "type", op: "eq", value: "N" },
    { column: "word", op: "like", value: `${prefix}%` },
    ...rarityFilters(minR, maxR),
    ...excludeFilters(exclude),
  ]);
}

async function randomAdj(
  minR: number, maxR: number, exclude: string[] = []
): Promise<Adjective> {
  const row = await randomRow<Adjective>(ADJ_SELECT, [
    { column: "type", op: "eq", value: "A" },
    ...rarityFilters(minR, maxR),
    ...excludeFilters(exclude),
  ]);
  if (!row) throw new Error("No adjectives found");
  return row;
}

async function randomAdjBySyllables(
  syllables: number, minR: number, maxR: number
): Promise<Adjective | null> {
  return randomRow<Adjective>(ADJ_SELECT, [
    { column: "type", op: "eq", value: "A" },
    { column: "syllables", op: "eq", value: syllables },
    ...rarityFilters(minR, maxR),
  ]);
}

async function randomAdjByPrefix(
  prefix: string, minR: number, maxR: number
): Promise<Adjective | null> {
  return randomRow<Adjective>(ADJ_SELECT, [
    { column: "type", op: "eq", value: "A" },
    { column: "word", op: "like", value: `${prefix}%` },
    ...rarityFilters(minR, maxR),
  ]);
}

async function randomVerb(
  minR: number, maxR: number, exclude: string[] = []
): Promise<Verb> {
  const row = await randomRow<Verb>(VERB_SELECT, [
    { column: "type", op: "eq", value: "V" },
    ...rarityFilters(minR, maxR),
    ...excludeFilters(exclude),
  ]);
  if (!row) throw new Error("No verbs found");
  return row;
}

async function randomVerbBySyllables(
  syllables: number, minR: number, maxR: number
): Promise<Verb | null> {
  return randomRow<Verb>(VERB_SELECT, [
    { column: "type", op: "eq", value: "V" },
    { column: "syllables", op: "eq", value: syllables },
    ...rarityFilters(minR, maxR),
  ]);
}

async function randomVerbByRhyme(
  rhyme: string, minR: number, maxR: number, exclude: string[] = []
): Promise<Verb | null> {
  return randomRow<Verb>(VERB_SELECT, [
    { column: "type", op: "eq", value: "V" },
    { column: "rhyme", op: "eq", value: rhyme },
    ...rarityFilters(minR, maxR),
    ...excludeFilters(exclude),
  ]);
}

async function randomVerbByPrefix(
  prefix: string, minR: number, maxR: number
): Promise<Verb | null> {
  return randomRow<Verb>(VERB_SELECT, [
    { column: "type", op: "eq", value: "V" },
    { column: "word", op: "like", value: `${prefix}%` },
    ...rarityFilters(minR, maxR),
  ]);
}

// --- Tautogram: batch prefix probing (3 queries total) ---

const PREFIX_SAMPLE_SIZE = 5;

async function randomPrefixWithAllTypes(
  minR: number, maxR: number
): Promise<string | null> {
  // 1) Pick a few random nouns to get candidate two-letter prefixes
  const { count: nounTotal } = await supabase
    .from("words").select("word", { count: "exact", head: true })
    .eq("type", "N").gte("rarity_level", minR).lte("rarity_level", maxR);
  if (!nounTotal) return null;

  const offset = Math.floor(Math.random() * Math.max(1, nounTotal - PREFIX_SAMPLE_SIZE));
  const { data: sampleNouns } = await supabase
    .from("words").select("word")
    .eq("type", "N").gte("rarity_level", minR).lte("rarity_level", maxR)
    .range(offset, offset + PREFIX_SAMPLE_SIZE - 1).limit(PREFIX_SAMPLE_SIZE);
  if (!sampleNouns || sampleNouns.length === 0) return null;

  const prefixes = [...new Set(
    sampleNouns.map((n) => n.word.substring(0, 2)).filter((p) => p.length === 2)
  )];
  if (prefixes.length === 0) return null;

  // 2) Single query: fetch type + prefix for all words matching any candidate prefix
  const orFilter = prefixes.map((p) => `word.like.${p}%`).join(",");
  const { data: candidates } = await supabase
    .from("words").select("word, type")
    .gte("rarity_level", minR).lte("rarity_level", maxR)
    .or(orFilter);
  if (!candidates) return null;

  // 3) Count types per prefix client-side
  const stats = new Map<string, { types: Set<string>; nounCount: number }>();
  for (const c of candidates) {
    if (c.word.length < 2) continue;
    const p = c.word.substring(0, 2);
    if (!stats.has(p)) stats.set(p, { types: new Set(), nounCount: 0 });
    const s = stats.get(p)!;
    s.types.add(c.type);
    if (c.type === "N") s.nounCount++;
  }

  const valid = prefixes.filter((p) => {
    const s = stats.get(p);
    return s && s.types.size === 3 && s.nounCount >= 2;
  });
  if (valid.length === 0) return null;
  return valid[Math.floor(Math.random() * valid.length)];
}

// --- Mirror: iterative rhyme group probing (no bulk fetch) ---

const MAX_RHYME_ATTEMPTS = 15;

async function findTwoVerbRhymeGroups(
  minR: number, maxR: number
): Promise<[string, string] | null> {
  // Strategy: pick random verbs, check if their rhyme has 2+ members.
  // Collect two distinct rhyme groups.
  const foundRhymes: string[] = [];

  for (let i = 0; i < MAX_RHYME_ATTEMPTS && foundRhymes.length < 2; i++) {
    const verb = await randomVerb(minR, maxR);
    const rhyme = verb.rhyme;

    if (foundRhymes.includes(rhyme)) continue;

    // Check if at least 2 verbs share this rhyme
    const { count } = await supabase
      .from("words")
      .select("word", { count: "exact", head: true })
      .eq("type", "V")
      .eq("rhyme", rhyme)
      .gte("rarity_level", minR)
      .lte("rarity_level", maxR);

    if (count && count >= 2) {
      foundRhymes.push(rhyme);
    }
  }

  if (foundRhymes.length < 2) return null;
  return [foundRhymes[0], foundRhymes[1]];
}

// --- Decorators ---

export function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

export function addDexLinks(sentence: string): string {
  const words = sentence.match(/\p{L}+/gu) || [];
  const spaces = sentence.split(/\p{L}+/u);
  let result = "";
  let wi = 0;
  let si = 0;
  if (spaces.length > words.length) result += spaces[si++];
  while (wi < words.length && si < spaces.length) {
    const w = words[wi++];
    const encoded = encodeURIComponent(w.toLowerCase());
    result += `<a href="${DEXONLINE_URL}${encoded}" target="_blank" rel="noopener" data-word="${encoded}">${escapeHtml(w)}</a>`;
    result += spaces[si++];
  }
  if (wi < words.length) {
    const w = words[wi];
    const encoded = encodeURIComponent(w.toLowerCase());
    result += `<a href="${DEXONLINE_URL}${encoded}" target="_blank" rel="noopener" data-word="${encoded}">${escapeHtml(w)}</a>`;
  }
  return result;
}

export function capitalizeFirst(s: string): string {
  if (!s) return s;
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export function decorateVerse(sentence: string): string {
  const capitalized = sentence
    .split(" / ")
    .map((line) => capitalizeFirst(line.trim()))
    .join(" / ");
  const linked = addDexLinks(capitalized);
  return linked.replace(/ \/ /g, "<br/>");
}

export function decorateSentence(sentence: string): string {
  return addDexLinks(capitalizeFirst(sentence.trim()));
}

export function decorateDefinition(sentence: string): string {
  return addDexLinks(sentence);
}

// --- Sentence providers ---

async function genComparison(minR: number, maxR: number): Promise<string> {
  const n1 = await randomNoun(minR, maxR);
  const adj = await randomAdj(minR, maxR);
  const n2 = await randomNoun(minR, maxR, [n1.word]);
  const raw = `${n1.articulated} e mai ${adjForGender(adj, n1.gender)} dec\u00e2t ${n2.articulated}.`;
  return decorateSentence(raw);
}

async function genDefinition(minR: number, maxR: number): Promise<string> {
  const defined = await randomNoun(minR, maxR);
  const noun = await randomNoun(minR, maxR, [defined.word]);
  const adj = await randomAdj(minR, maxR);
  const verb = await randomVerb(minR, maxR);
  const obj = await randomNoun(minR, maxR, [defined.word, noun.word]);
  const raw = `${defined.word.toUpperCase()}: ${noun.articulated} ${adjForGender(adj, noun.gender)} care ${verb.word} ${obj.articulated}.`;
  return decorateDefinition(raw);
}

async function genDistih(minR: number, maxR: number): Promise<string> {
  const usedN: string[] = [];
  const usedA: string[] = [];
  const usedV: string[] = [];

  async function buildLine() {
    const n1 = await randomNoun(minR, maxR, usedN);
    usedN.push(n1.word);
    const a1 = await randomAdj(minR, maxR, usedA);
    usedA.push(a1.word);
    const v = await randomVerb(minR, maxR, usedV);
    usedV.push(v.word);
    const n2 = await randomNoun(minR, maxR, usedN);
    usedN.push(n2.word);
    const a2 = await randomAdj(minR, maxR, usedA);
    usedA.push(a2.word);
    return `${n1.articulated} ${adjForGender(a1, n1.gender)} ${v.word} ${n2.articulated} ${adjForGender(a2, n2.gender)}.`;
  }

  const l1 = await buildLine();
  const l2 = await buildLine();
  return decorateVerse(`${l1} / ${l2}`);
}

async function genHaiku(minR: number, maxR: number): Promise<string> {
  const noun =
    (await randomNounByArticulatedSyllables(5, minR, maxR)) ||
    (await randomNoun(minR, maxR));
  const adjSyl = noun.gender === "F" ? 3 : 4;
  const adj = await randomAdjBySyllables(adjSyl, minR, maxR);
  if (!adj) throw new Error("No adj with required syllables");
  const verb = await randomVerbBySyllables(3, minR, maxR);
  if (!verb) throw new Error("No verb with 3 syllables");
  const noun2 =
    (await randomNounByArticulatedSyllables(5, minR, maxR, [noun.word])) ||
    (await randomNoun(minR, maxR, [noun.word]));
  const raw = `${noun.articulated} / ${adjForGender(adj, noun.gender)} ${verb.word} / ${noun2.articulated}.`;
  return decorateVerse(raw);
}

async function genMirror(minR: number, maxR: number): Promise<string> {
  const rhymes = await findTwoVerbRhymeGroups(minR, maxR);
  if (!rhymes) throw new Error("No rhyme groups");
  const [rhymeA, rhymeB] = rhymes;

  const usedN: string[] = [];
  const usedA: string[] = [];
  const usedV: string[] = [];

  async function buildLine(rhyme: string, punct: string): Promise<string> {
    const n = await randomNoun(minR, maxR, usedN);
    usedN.push(n.word);
    const a = await randomAdj(minR, maxR, usedA);
    usedA.push(a.word);
    const v = await randomVerbByRhyme(rhyme, minR, maxR, usedV);
    if (!v) throw new Error(`No verb for rhyme ${rhyme}`);
    usedV.push(v.word);
    return `${n.articulated} ${adjForGender(a, n.gender)} ${v.word}${punct}`;
  }

  const l1 = await buildLine(rhymeA, ",");
  const l2 = await buildLine(rhymeB, ",");
  const l3 = await buildLine(rhymeB, ",");
  const l4 = await buildLine(rhymeA, ".");
  return decorateVerse(`${l1} / ${l2} / ${l3} / ${l4}`);
}

async function genTautogram(minR: number, maxR: number): Promise<string> {
  const prefix = await randomPrefixWithAllTypes(minR, maxR);
  if (!prefix) throw new Error("No valid prefix");
  const n1 = await randomNounByPrefix(prefix, minR, maxR);
  if (!n1) throw new Error("No noun for prefix");
  const adj = await randomAdjByPrefix(prefix, minR, maxR);
  if (!adj) throw new Error("No adj for prefix");
  const verb = await randomVerbByPrefix(prefix, minR, maxR);
  if (!verb) throw new Error("No verb for prefix");
  const n2 = await randomNounByPrefix(prefix, minR, maxR, [n1.word]);
  if (!n2) throw new Error("No 2nd noun for prefix");
  const raw = `${n1.articulated} ${adjForGender(adj, n1.gender)} ${verb.word} ${n2.articulated}.`;
  return decorateSentence(raw);
}

// --- Main handler ---

export default async function handler(req: VercelRequest, res: VercelResponse) {
  const reqOrigin = Array.isArray(req.headers.origin)
    ? req.headers.origin[0]
    : req.headers.origin;
  res.setHeader("Access-Control-Allow-Origin", resolveCorsOrigin(reqOrigin, allowedOrigins));
  res.setHeader("Vary", "Origin");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  res.setHeader("Access-Control-Max-Age", "86400");

  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "GET") return res.status(405).json({ error: "Method Not Allowed" });

  if (!supabase) {
    return res.status(500).json({
      error: keyResolution.error ?? "Missing SUPABASE_URL or Supabase API key",
    });
  }

  const minCandidate = Math.max(1, Math.min(5, Number(req.query.minRarity) || 1));
  const maxCandidate = Math.max(1, Math.min(5, Number(req.query.rarity) || 2));
  const minR = Math.min(minCandidate, maxCandidate);
  const maxR = Math.max(minCandidate, maxCandidate);

  async function safe(fn: () => Promise<string>): Promise<string> {
    try {
      return await fn();
    } catch (err) {
      console.error("Sentence generation failed:", err);
      return UNSATISFIABLE;
    }
  }

  const [haiku, distih, comparison, definition, tautogram, mirror] =
    await Promise.all([
      safe(() => genHaiku(minR, maxR)),
      safe(() => genDistih(minR, maxR)),
      safe(() => genComparison(minR, maxR)),
      safe(() => genDefinition(minR, maxR)),
      safe(() => genTautogram(minR, maxR)),
      safe(() => genMirror(minR, maxR)),
    ]);

  return res.status(200).json({
    haiku,
    distih,
    comparison,
    definition,
    tautogram,
    mirror,
  });
}
