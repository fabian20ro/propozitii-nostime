import { createClient, SupabaseClient } from "@supabase/supabase-js";

interface VercelRequestLike {
  method?: string;
  query: Record<string, string | string[] | undefined>;
  headers: Record<string, string | string[] | undefined>;
}

interface VercelResponseLike {
  setHeader(name: string, value: string): void;
  status(code: number): {
    end(): unknown;
    json(body: unknown): unknown;
  };
}

// --- Environment validation (deferred for testability) ---

const DEFAULT_ALLOWED_ORIGINS = ["https://fabian20ro.github.io"];

interface SupabaseKeyResolution {
  key: string;
  source: "publishable" | "service-role" | "none";
  error?: string;
}

export function resolveSupabaseKey(
  env: Record<string, string | undefined>
): SupabaseKeyResolution {
  const publishable = (env.SUPABASE_PUBLISHABLE_KEY ?? "").trim();
  if (publishable) return { key: publishable, source: "publishable" };

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
        "Use SUPABASE_PUBLISHABLE_KEY (preferred).",
    };
  }
  return {
    key: "",
    source: "none",
    error: "Missing SUPABASE_PUBLISHABLE_KEY.",
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

export function validateSupabaseUrl(raw: string | undefined): string | undefined {
  const supabaseUrl = (raw ?? "").trim();
  if (!supabaseUrl) return "Missing SUPABASE_URL.";
  try {
    const parsed = new URL(supabaseUrl);
    if (parsed.protocol !== "https:" && parsed.protocol !== "http:") {
      return "Invalid SUPABASE_URL: must use http/https.";
    }
  } catch {
    return "Invalid SUPABASE_URL: must be a valid HTTP or HTTPS URL.";
  }
  return undefined;
}

interface SupabaseInitResolution {
  keyResolution: SupabaseKeyResolution;
  error?: string;
}

export function resolveSupabaseInit(
  env: Record<string, string | undefined>
): SupabaseInitResolution {
  const keyResolution = resolveSupabaseKey(env);
  const urlError = validateSupabaseUrl(env.SUPABASE_URL);
  if (urlError) return { keyResolution, error: urlError };
  if (!keyResolution.key) {
    return {
      keyResolution,
      error: keyResolution.error ?? "Missing Supabase API key.",
    };
  }
  return { keyResolution };
}

const initResolution = resolveSupabaseInit(process.env);
const allowedOrigins = parseAllowedOrigins(process.env.ALLOWED_ORIGINS);

let supabase: SupabaseClient | null = null;
let supabaseError = initResolution.error;
let supabaseInitAttempted = false;

function getSupabaseClient(): SupabaseClient | null {
  if (supabaseInitAttempted) return supabase;
  supabaseInitAttempted = true;

  if (initResolution.keyResolution.source === "service-role") {
    console.warn(
      "[security] api/all.ts uses SUPABASE_SERVICE_ROLE_KEY fallback; prefer SUPABASE_PUBLISHABLE_KEY."
    );
  }

  if (supabaseError) return null;

  try {
    const supabaseUrl = (process.env.SUPABASE_URL ?? "").trim();
    supabase = createClient(supabaseUrl, initResolution.keyResolution.key, {
      auth: {
        autoRefreshToken: false,
        persistSession: false,
        detectSessionInUrl: false,
      },
    });
    return supabase;
  } catch (err) {
    const msg = err instanceof Error ? err.message : "unknown error";
    supabaseError = `Supabase client initialization failed: ${msg}`;
    return null;
  }
}

// --- Per-generator timeout (prevents a single slow generator from exhausting maxDuration) ---

const GENERATOR_TIMEOUT_MS = 7000;

function withTimeout<T>(promise: Promise<T>, fallback: T, ms: number): Promise<T> {
  let timer: ReturnType<typeof setTimeout>;
  const timeout = new Promise<T>((resolve) => {
    timer = setTimeout(() => resolve(fallback), ms);
  });
  return Promise.race([promise.finally(() => clearTimeout(timer)), timeout]);
}

export const DEXONLINE_URL = "https://dexonline.ro/definitie/";
const UNSATISFIABLE =
  "Nu există suficiente cuvinte pentru nivelul de raritate ales.";

class ConstraintUnsatisfiedError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ConstraintUnsatisfiedError";
  }
}

function failConstraint(message: string): never {
  throw new ConstraintUnsatisfiedError(message);
}

// --- Types ---

interface Noun {
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
  feminine_syllables: number;
}

interface Verb {
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

// --- Per-request count cache (avoids redundant count queries for same filter combos) ---
// Cache key intentionally excludes `neq` (exclude) filters because exclude sets
// are small (≤6 words) relative to the pool. If a cached count causes an offset
// miss (data query returns null), randomRow retries once with a fresh count.

type CountCache = Map<string, number>;

function countCacheKey(filters: QueryFilter[]): string {
  return filters
    .filter((f) => f.op !== "neq")
    .map((f) => `${f.column}:${f.op}:${f.value}`)
    .sort()
    .join("|");
}

// --- Generic random-row helper (single count + single fetch) ---

async function randomRow<T extends WordRow>(
  select: string,
  filters: QueryFilter[],
  cache?: CountCache
): Promise<T | null> {
  const client = getSupabaseClient();
  if (!client) throw new Error(supabaseError ?? "Supabase client unavailable.");

  const cKey = countCacheKey(filters);
  const usedCachedCount = cache?.has(cKey) ?? false;
  let count: number | null | undefined = cache?.get(cKey);

  if (count === undefined) {
    let countQ = client.from("words").select("word", { count: "exact", head: true });
    for (const f of filters) countQ = applyFilter(countQ, f);
    const result = await countQ;
    count = result.count;
    if (count != null && cache) cache.set(cKey, count);
  }
  if (!count) return null;

  // Fetch one at random offset
  const offset = Math.floor(Math.random() * count);
  let dataQ = client.from("words").select(select).range(offset, offset).limit(1);
  for (const f of filters) dataQ = applyFilter(dataQ, f);
  const { data } = await dataQ;

  // If data miss and we used a cached count (possibly stale due to exclude
  // filters), retry once with a fresh count to handle small-pool edge cases.
  if ((!data || data.length === 0) && usedCachedCount) {
    cache?.delete(cKey);
    let freshQ = client.from("words").select("word", { count: "exact", head: true });
    for (const f of filters) freshQ = applyFilter(freshQ, f);
    const freshResult = await freshQ;
    const freshCount = freshResult.count;
    if (!freshCount) return null;
    if (cache) cache.set(cKey, freshCount);
    const retryOffset = Math.floor(Math.random() * freshCount);
    let retryQ = client.from("words").select(select).range(retryOffset, retryOffset).limit(1);
    for (const f of filters) retryQ = applyFilter(retryQ, f);
    const { data: retryData } = await retryQ;
    if (!retryData || retryData.length === 0) return null;
    return retryData[0] as unknown as T;
  }

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
const ADJ_SELECT = "word, syllables, rhyme, feminine, feminine_syllables";
const VERB_SELECT = "word, syllables, rhyme";

async function randomNoun(
  minR: number, maxR: number, exclude: string[] = [], cache?: CountCache
): Promise<Noun> {
  const row = await randomRow<Noun>(NOUN_SELECT, [
    { column: "type", op: "eq", value: "N" },
    ...rarityFilters(minR, maxR),
    ...excludeFilters(exclude),
  ], cache);
  if (!row) failConstraint("No nouns found");
  return row;
}

async function randomNounByArticulatedSyllables(
  syllables: number, minR: number, maxR: number, exclude: string[] = [], cache?: CountCache
): Promise<Noun | null> {
  return randomRow<Noun>(NOUN_SELECT, [
    { column: "type", op: "eq", value: "N" },
    { column: "articulated_syllables", op: "eq", value: syllables },
    ...rarityFilters(minR, maxR),
    ...excludeFilters(exclude),
  ], cache);
}

async function randomNounByPrefix(
  prefix: string, minR: number, maxR: number, exclude: string[] = [], cache?: CountCache
): Promise<Noun | null> {
  return randomRow<Noun>(NOUN_SELECT, [
    { column: "type", op: "eq", value: "N" },
    { column: "word", op: "like", value: `${prefix}%` },
    ...rarityFilters(minR, maxR),
    ...excludeFilters(exclude),
  ], cache);
}

async function randomAdj(
  minR: number, maxR: number, exclude: string[] = [], cache?: CountCache
): Promise<Adjective> {
  const row = await randomRow<Adjective>(ADJ_SELECT, [
    { column: "type", op: "eq", value: "A" },
    ...rarityFilters(minR, maxR),
    ...excludeFilters(exclude),
  ], cache);
  if (!row) failConstraint("No adjectives found");
  return row;
}

async function randomAdjBySyllables(
  syllables: number, minR: number, maxR: number, cache?: CountCache
): Promise<Adjective | null> {
  return randomRow<Adjective>(ADJ_SELECT, [
    { column: "type", op: "eq", value: "A" },
    { column: "syllables", op: "eq", value: syllables },
    ...rarityFilters(minR, maxR),
  ], cache);
}

async function randomAdjByFeminineSyllables(
  feminineSyllables: number, minR: number, maxR: number, cache?: CountCache
): Promise<Adjective | null> {
  return randomRow<Adjective>(ADJ_SELECT, [
    { column: "type", op: "eq", value: "A" },
    { column: "feminine_syllables", op: "eq", value: feminineSyllables },
    ...rarityFilters(minR, maxR),
  ], cache);
}

async function randomAdjByPrefix(
  prefix: string, minR: number, maxR: number, cache?: CountCache
): Promise<Adjective | null> {
  return randomRow<Adjective>(ADJ_SELECT, [
    { column: "type", op: "eq", value: "A" },
    { column: "word", op: "like", value: `${prefix}%` },
    ...rarityFilters(minR, maxR),
  ], cache);
}

async function randomVerb(
  minR: number, maxR: number, exclude: string[] = [], cache?: CountCache
): Promise<Verb> {
  const row = await randomRow<Verb>(VERB_SELECT, [
    { column: "type", op: "eq", value: "V" },
    ...rarityFilters(minR, maxR),
    ...excludeFilters(exclude),
  ], cache);
  if (!row) failConstraint("No verbs found");
  return row;
}

async function randomVerbBySyllables(
  syllables: number, minR: number, maxR: number, cache?: CountCache
): Promise<Verb | null> {
  return randomRow<Verb>(VERB_SELECT, [
    { column: "type", op: "eq", value: "V" },
    { column: "syllables", op: "eq", value: syllables },
    ...rarityFilters(minR, maxR),
  ], cache);
}

async function randomVerbByRhyme(
  rhyme: string, minR: number, maxR: number, exclude: string[] = [], cache?: CountCache
): Promise<Verb | null> {
  return randomRow<Verb>(VERB_SELECT, [
    { column: "type", op: "eq", value: "V" },
    { column: "rhyme", op: "eq", value: rhyme },
    ...rarityFilters(minR, maxR),
    ...excludeFilters(exclude),
  ], cache);
}

async function randomVerbByPrefix(
  prefix: string, minR: number, maxR: number, cache?: CountCache
): Promise<Verb | null> {
  return randomRow<Verb>(VERB_SELECT, [
    { column: "type", op: "eq", value: "V" },
    { column: "word", op: "like", value: `${prefix}%` },
    ...rarityFilters(minR, maxR),
  ], cache);
}

// --- Tautogram: batch prefix probing (3 queries total) ---

const PREFIX_SAMPLE_SIZE = 5;

async function randomPrefixWithAllTypes(
  minR: number, maxR: number
): Promise<string | null> {
  const client = getSupabaseClient();
  if (!client) throw new Error(supabaseError ?? "Supabase client unavailable.");

  // 1) Pick a few random nouns to get candidate two-letter prefixes
  const { count: nounTotal } = await client
    .from("words").select("word", { count: "exact", head: true })
    .eq("type", "N").gte("rarity_level", minR).lte("rarity_level", maxR);
  if (!nounTotal) return null;

  const offset = Math.floor(Math.random() * Math.max(1, nounTotal - PREFIX_SAMPLE_SIZE));
  const { data: sampleNouns } = await client
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
  const { data: candidates } = await client
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

// --- Mirror: bulk rhyme group discovery (1-2 queries instead of up to 45) ---

async function findTwoVerbRhymeGroups(
  minR: number, maxR: number
): Promise<[string, string] | null> {
  const client = getSupabaseClient();
  if (!client) throw new Error(supabaseError ?? "Supabase client unavailable.");

  // Fetch all verb rhymes in rarity range in a single bulk query,
  // then group client-side. Paginate to handle Supabase's 1000-row default.
  const allRhymes: string[] = [];
  let from = 0;
  const pageSize = 1000;
  while (true) {
    const { data, error } = await client
      .from("words")
      .select("rhyme")
      .eq("type", "V")
      .gte("rarity_level", minR)
      .lte("rarity_level", maxR)
      .range(from, from + pageSize - 1);

    if (error || !data || data.length === 0) break;
    for (const row of data) allRhymes.push(row.rhyme);
    if (data.length < pageSize) break;
    from += pageSize;
  }

  if (allRhymes.length === 0) return null;

  // Count occurrences per rhyme
  const rhymeCounts = new Map<string, number>();
  for (const r of allRhymes) {
    rhymeCounts.set(r, (rhymeCounts.get(r) ?? 0) + 1);
  }

  // Filter to rhymes with 2+ verbs
  const validRhymes = [...rhymeCounts.entries()]
    .filter(([, count]) => count >= 2)
    .map(([rhyme]) => rhyme);

  if (validRhymes.length < 2) return null;

  // Pick two distinct rhymes at random
  const i1 = Math.floor(Math.random() * validRhymes.length);
  [validRhymes[i1], validRhymes[validRhymes.length - 1]] =
    [validRhymes[validRhymes.length - 1], validRhymes[i1]];
  const pick1 = validRhymes[validRhymes.length - 1];
  const i2 = Math.floor(Math.random() * (validRhymes.length - 1));
  const pick2 = validRhymes[i2];

  return [pick1, pick2];
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
  return sentence.replace(/\p{L}+/gu, (w) => {
    const encoded = encodeURIComponent(w.toLowerCase());
    return `<a href="${DEXONLINE_URL}${encoded}" target="_blank" rel="noopener" data-word="${encoded}">${escapeHtml(w)}</a>`;
  });
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

async function genComparison(minR: number, maxR: number, cache?: CountCache): Promise<string> {
  const [n1, adj] = await Promise.all([
    randomNoun(minR, maxR, [], cache),
    randomAdj(minR, maxR, [], cache),
  ]);
  const n2 = await randomNoun(minR, maxR, [n1.word], cache);
  const raw = `${n1.articulated} e mai ${adjForGender(adj, n1.gender)} dec\u00e2t ${n2.articulated}.`;
  return decorateSentence(raw);
}

async function genDefinition(minR: number, maxR: number, cache?: CountCache): Promise<string> {
  const [defined, adj, verb] = await Promise.all([
    randomNoun(minR, maxR, [], cache),
    randomAdj(minR, maxR, [], cache),
    randomVerb(minR, maxR, [], cache),
  ]);
  const noun = await randomNoun(minR, maxR, [defined.word], cache);
  const obj = await randomNoun(minR, maxR, [defined.word, noun.word], cache);
  const raw = `${defined.word.toUpperCase()}: ${noun.articulated} ${adjForGender(adj, noun.gender)} care ${verb.word} ${obj.articulated}.`;
  return decorateDefinition(raw);
}

async function genDistih(minR: number, maxR: number, cache?: CountCache): Promise<string> {
  const usedN: string[] = [];
  const usedA: string[] = [];
  const usedV: string[] = [];

  async function buildLine() {
    const [n1, a1, v] = await Promise.all([
      randomNoun(minR, maxR, usedN, cache),
      randomAdj(minR, maxR, usedA, cache),
      randomVerb(minR, maxR, usedV, cache),
    ]);
    usedN.push(n1.word);
    usedA.push(a1.word);
    usedV.push(v.word);
    const [n2, a2] = await Promise.all([
      randomNoun(minR, maxR, usedN, cache),
      randomAdj(minR, maxR, usedA, cache),
    ]);
    usedN.push(n2.word);
    usedA.push(a2.word);
    return `${n1.articulated} ${adjForGender(a1, n1.gender)} ${v.word} ${n2.articulated} ${adjForGender(a2, n2.gender)}.`;
  }

  const l1 = await buildLine();
  const l2 = await buildLine();
  return decorateVerse(`${l1} / ${l2}`);
}

async function genHaiku(minR: number, maxR: number, cache?: CountCache): Promise<string> {
  const noun =
    (await randomNounByArticulatedSyllables(5, minR, maxR, [], cache)) ||
    (await randomNoun(minR, maxR, [], cache));
  // Adjective form must be 4 syllables; query by feminine_syllables for feminine nouns
  const adjPromise = noun.gender === "F"
    ? randomAdjByFeminineSyllables(4, minR, maxR, cache)
    : randomAdjBySyllables(4, minR, maxR, cache);
  const [adj, verb, noun2] = await Promise.all([
    adjPromise,
    randomVerbBySyllables(3, minR, maxR, cache),
    randomNounByArticulatedSyllables(5, minR, maxR, [noun.word], cache).then(
      (n2) => n2 || randomNoun(minR, maxR, [noun.word], cache)
    ),
  ]);
  if (!adj) failConstraint("No adj with required syllables");
  if (!verb) failConstraint("No verb with 3 syllables");
  const raw = `${noun.articulated} / ${adjForGender(adj, noun.gender)} ${verb.word} / ${noun2.articulated}.`;
  return decorateVerse(raw);
}

async function genMirror(minR: number, maxR: number, cache?: CountCache): Promise<string> {
  const rhymes = await findTwoVerbRhymeGroups(minR, maxR);
  if (!rhymes) failConstraint("No rhyme groups");
  const [rhymeA, rhymeB] = rhymes;

  const usedN: string[] = [];
  const usedA: string[] = [];
  const usedV: string[] = [];

  async function buildLine(rhyme: string, punct: string): Promise<string> {
    const [n, a, v] = await Promise.all([
      randomNoun(minR, maxR, usedN, cache),
      randomAdj(minR, maxR, usedA, cache),
      randomVerbByRhyme(rhyme, minR, maxR, usedV, cache),
    ]);
    if (!v) failConstraint(`No verb for rhyme ${rhyme}`);
    usedN.push(n.word);
    usedA.push(a.word);
    usedV.push(v.word);
    return `${n.articulated} ${adjForGender(a, n.gender)} ${v.word}${punct}`;
  }

  const l1 = await buildLine(rhymeA, ",");
  const l2 = await buildLine(rhymeB, ",");
  const l3 = await buildLine(rhymeB, ",");
  const l4 = await buildLine(rhymeA, ".");
  return decorateVerse(`${l1} / ${l2} / ${l3} / ${l4}`);
}

async function genTautogram(minR: number, maxR: number, cache?: CountCache): Promise<string> {
  const prefix = await randomPrefixWithAllTypes(minR, maxR);
  if (!prefix) failConstraint("No valid prefix");
  const [n1, adj, verb] = await Promise.all([
    randomNounByPrefix(prefix, minR, maxR, [], cache),
    randomAdjByPrefix(prefix, minR, maxR, cache),
    randomVerbByPrefix(prefix, minR, maxR, cache),
  ]);
  if (!n1) failConstraint("No noun for prefix");
  if (!adj) failConstraint("No adj for prefix");
  if (!verb) failConstraint("No verb for prefix");
  const n2 = await randomNounByPrefix(prefix, minR, maxR, [n1.word], cache);
  if (!n2) failConstraint("No 2nd noun for prefix");
  const raw = `${n1.articulated} ${adjForGender(adj, n1.gender)} ${verb.word} ${n2.articulated}.`;
  return decorateSentence(raw);
}

// --- Main handler ---

export default async function handler(req: VercelRequestLike, res: VercelResponseLike) {
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

  if (!getSupabaseClient()) {
    return res.status(500).json({
      error: supabaseError ?? "Missing SUPABASE_URL or Supabase API key",
    });
  }

  const minCandidate = Math.max(1, Math.min(5, Number(req.query.minRarity) || 1));
  const maxCandidate = Math.max(1, Math.min(5, Number(req.query.rarity) || 2));
  const minR = Math.min(minCandidate, maxCandidate);
  const maxR = Math.max(minCandidate, maxCandidate);

  async function safe(fn: () => Promise<string>): Promise<string> {
    try {
      return await withTimeout(fn(), UNSATISFIABLE, GENERATOR_TIMEOUT_MS);
    } catch (err) {
      if (err instanceof ConstraintUnsatisfiedError) return UNSATISFIABLE;
      console.error("Sentence generation failed:", err);
      return UNSATISFIABLE;
    }
  }

  const cache: CountCache = new Map();

  const [haiku, distih, comparison, definition, tautogram, mirror] =
    await Promise.all([
      safe(() => genHaiku(minR, maxR, cache)),
      safe(() => genDistih(minR, maxR, cache)),
      safe(() => genComparison(minR, maxR, cache)),
      safe(() => genDefinition(minR, maxR, cache)),
      safe(() => genTautogram(minR, maxR, cache)),
      safe(() => genMirror(minR, maxR, cache)),
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
