import type { VercelRequest, VercelResponse } from "@vercel/node";
import { createClient } from "@supabase/supabase-js";

const supabaseUrl = process.env.SUPABASE_URL!;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY!;
const supabase = createClient(supabaseUrl, supabaseKey);

const DEXONLINE_URL = "https://dexonline.ro/definitie/";
const UNSATISFIABLE =
  "Nu există suficiente cuvinte pentru nivelul de raritate ales.";

// --- Types ---

interface Noun {
  word: string;
  gender: string;
  syllables: number;
  rhyme: string;
  articulated: string;
}

interface Adjective {
  word: string;
  syllables: number;
  rhyme: string;
  feminine: string;
}

interface Verb {
  word: string;
  syllables: number;
  rhyme: string;
}

function adjForGender(adj: Adjective, gender: string): string {
  return gender === "F" ? adj.feminine : adj.word;
}

// --- Random word queries (Supabase) ---

async function randomNoun(
  minR: number,
  maxR: number,
  exclude: string[] = []
): Promise<Noun> {
  // Use Postgres function random() via RPC is not available on free tier,
  // so fetch a count, pick random offset.
  let query = supabase
    .from("words")
    .select("word, gender, syllables, rhyme, articulated")
    .eq("type", "N")
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  for (const w of exclude) query = query.neq("word", w);
  const { count } = await query.select("word", { count: "exact", head: true });
  if (!count) throw new Error("No nouns");
  const offset = Math.floor(Math.random() * count);
  // Re-build query with actual select
  let q2 = supabase
    .from("words")
    .select("word, gender, syllables, rhyme, articulated")
    .eq("type", "N")
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  for (const w of exclude) q2 = q2.neq("word", w);
  const { data } = await q2.range(offset, offset).limit(1);
  return data![0] as Noun;
}

async function randomNounByArticulatedSyllables(
  syllables: number,
  minR: number,
  maxR: number,
  exclude: string[] = []
): Promise<Noun | null> {
  let query = supabase
    .from("words")
    .select("word, gender, syllables, rhyme, articulated")
    .eq("type", "N")
    .eq("articulated_syllables", syllables)
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  for (const w of exclude) query = query.neq("word", w);
  const { count } = await query.select("word", { count: "exact", head: true });
  if (!count) return null;
  const offset = Math.floor(Math.random() * count);
  let q2 = supabase
    .from("words")
    .select("word, gender, syllables, rhyme, articulated")
    .eq("type", "N")
    .eq("articulated_syllables", syllables)
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  for (const w of exclude) q2 = q2.neq("word", w);
  const { data } = await q2.range(offset, offset).limit(1);
  return data?.[0] as Noun | null;
}

async function randomNounByPrefix(
  prefix: string,
  minR: number,
  maxR: number,
  exclude: string[] = []
): Promise<Noun | null> {
  let query = supabase
    .from("words")
    .select("word, gender, syllables, rhyme, articulated")
    .eq("type", "N")
    .like("word", `${prefix}%`)
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  for (const w of exclude) query = query.neq("word", w);
  const { count } = await query.select("word", { count: "exact", head: true });
  if (!count) return null;
  const offset = Math.floor(Math.random() * count);
  let q2 = supabase
    .from("words")
    .select("word, gender, syllables, rhyme, articulated")
    .eq("type", "N")
    .like("word", `${prefix}%`)
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  for (const w of exclude) q2 = q2.neq("word", w);
  const { data } = await q2.range(offset, offset).limit(1);
  return data?.[0] as Noun | null;
}

async function randomAdj(
  minR: number,
  maxR: number,
  exclude: string[] = []
): Promise<Adjective> {
  let query = supabase
    .from("words")
    .select("word, syllables, rhyme, feminine")
    .eq("type", "A")
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  for (const w of exclude) query = query.neq("word", w);
  const { count } = await query.select("word", { count: "exact", head: true });
  if (!count) throw new Error("No adjectives");
  const offset = Math.floor(Math.random() * count);
  let q2 = supabase
    .from("words")
    .select("word, syllables, rhyme, feminine")
    .eq("type", "A")
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  for (const w of exclude) q2 = q2.neq("word", w);
  const { data } = await q2.range(offset, offset).limit(1);
  return data![0] as Adjective;
}

async function randomAdjBySyllables(
  syllables: number,
  minR: number,
  maxR: number
): Promise<Adjective | null> {
  const query = supabase
    .from("words")
    .select("word, syllables, rhyme, feminine")
    .eq("type", "A")
    .eq("syllables", syllables)
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  const { count } = await query.select("word", { count: "exact", head: true });
  if (!count) return null;
  const offset = Math.floor(Math.random() * count);
  const { data } = await supabase
    .from("words")
    .select("word, syllables, rhyme, feminine")
    .eq("type", "A")
    .eq("syllables", syllables)
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR)
    .range(offset, offset)
    .limit(1);
  return data?.[0] as Adjective | null;
}

async function randomAdjByPrefix(
  prefix: string,
  minR: number,
  maxR: number
): Promise<Adjective | null> {
  const query = supabase
    .from("words")
    .select("word, syllables, rhyme, feminine")
    .eq("type", "A")
    .like("word", `${prefix}%`)
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  const { count } = await query.select("word", { count: "exact", head: true });
  if (!count) return null;
  const offset = Math.floor(Math.random() * count);
  const { data } = await supabase
    .from("words")
    .select("word, syllables, rhyme, feminine")
    .eq("type", "A")
    .like("word", `${prefix}%`)
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR)
    .range(offset, offset)
    .limit(1);
  return data?.[0] as Adjective | null;
}

async function randomVerb(
  minR: number,
  maxR: number,
  exclude: string[] = []
): Promise<Verb> {
  let query = supabase
    .from("words")
    .select("word, syllables, rhyme")
    .eq("type", "V")
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  for (const w of exclude) query = query.neq("word", w);
  const { count } = await query.select("word", { count: "exact", head: true });
  if (!count) throw new Error("No verbs");
  const offset = Math.floor(Math.random() * count);
  let q2 = supabase
    .from("words")
    .select("word, syllables, rhyme")
    .eq("type", "V")
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  for (const w of exclude) q2 = q2.neq("word", w);
  const { data } = await q2.range(offset, offset).limit(1);
  return data![0] as Verb;
}

async function randomVerbBySyllables(
  syllables: number,
  minR: number,
  maxR: number
): Promise<Verb | null> {
  const query = supabase
    .from("words")
    .select("word, syllables, rhyme")
    .eq("type", "V")
    .eq("syllables", syllables)
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  const { count } = await query.select("word", { count: "exact", head: true });
  if (!count) return null;
  const offset = Math.floor(Math.random() * count);
  const { data } = await supabase
    .from("words")
    .select("word, syllables, rhyme")
    .eq("type", "V")
    .eq("syllables", syllables)
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR)
    .range(offset, offset)
    .limit(1);
  return data?.[0] as Verb | null;
}

async function randomVerbByRhyme(
  rhyme: string,
  minR: number,
  maxR: number,
  exclude: string[] = []
): Promise<Verb | null> {
  let query = supabase
    .from("words")
    .select("word, syllables, rhyme")
    .eq("type", "V")
    .eq("rhyme", rhyme)
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  for (const w of exclude) query = query.neq("word", w);
  const { count } = await query.select("word", { count: "exact", head: true });
  if (!count) return null;
  const offset = Math.floor(Math.random() * count);
  let q2 = supabase
    .from("words")
    .select("word, syllables, rhyme")
    .eq("type", "V")
    .eq("rhyme", rhyme)
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  for (const w of exclude) q2 = q2.neq("word", w);
  const { data } = await q2.range(offset, offset).limit(1);
  return data?.[0] as Verb | null;
}

async function randomVerbByPrefix(
  prefix: string,
  minR: number,
  maxR: number
): Promise<Verb | null> {
  const query = supabase
    .from("words")
    .select("word, syllables, rhyme")
    .eq("type", "V")
    .like("word", `${prefix}%`)
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR);
  const { count } = await query.select("word", { count: "exact", head: true });
  if (!count) return null;
  const offset = Math.floor(Math.random() * count);
  const { data } = await supabase
    .from("words")
    .select("word, syllables, rhyme")
    .eq("type", "V")
    .like("word", `${prefix}%`)
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR)
    .range(offset, offset)
    .limit(1);
  return data?.[0] as Verb | null;
}

async function findTwoVerbRhymeGroups(
  minR: number,
  maxR: number
): Promise<[string, string] | null> {
  // Get all verb rhymes with 2+ members
  const { data } = await supabase.rpc("find_rhyme_groups", {
    word_type: "V",
    min_count: 2,
    min_rarity: minR,
    max_rarity: maxR,
  });
  // If RPC not available, fall back to fetching all verb rhymes
  if (!data || data.length < 2) {
    // Fallback: query distinct rhymes and filter client-side
    const { data: allVerbs } = await supabase
      .from("words")
      .select("rhyme")
      .eq("type", "V")
      .gte("rarity_level", minR)
      .lte("rarity_level", maxR);
    if (!allVerbs) return null;
    const counts = new Map<string, number>();
    for (const v of allVerbs) {
      counts.set(v.rhyme, (counts.get(v.rhyme) || 0) + 1);
    }
    const valid = [...counts.entries()]
      .filter(([, c]) => c >= 2)
      .map(([r]) => r);
    if (valid.length < 2) return null;
    // Shuffle and pick two
    for (let i = valid.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [valid[i], valid[j]] = [valid[j], valid[i]];
    }
    return [valid[0], valid[1]];
  }
  // Shuffle RPC results
  const rhymes = data.map((r: { rhyme: string }) => r.rhyme);
  for (let i = rhymes.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [rhymes[i], rhymes[j]] = [rhymes[j], rhymes[i]];
  }
  return [rhymes[0], rhymes[1]];
}

async function randomPrefixWithAllTypes(
  minR: number,
  maxR: number
): Promise<string | null> {
  // Fetch first_letter values that have all 3 types
  const { data: allWords } = await supabase
    .from("words")
    .select("word, type")
    .gte("rarity_level", minR)
    .lte("rarity_level", maxR)
    .gte("word", "aa") // at least 2 chars
    .limit(50000);
  if (!allWords) return null;
  const prefixTypes = new Map<string, Set<string>>();
  for (const w of allWords) {
    if (w.word.length >= 2) {
      const p = w.word.substring(0, 2);
      if (!prefixTypes.has(p)) prefixTypes.set(p, new Set());
      prefixTypes.get(p)!.add(w.type);
    }
  }
  const valid = [...prefixTypes.entries()]
    .filter(([, types]) => types.size === 3)
    .map(([p]) => p);
  if (valid.length === 0) return null;
  return valid[Math.floor(Math.random() * valid.length)];
}

// --- Decorators ---

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function addDexLinks(sentence: string): string {
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

function capitalizeFirst(s: string): string {
  if (!s) return s;
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function decorateVerse(sentence: string): string {
  const capitalized = sentence
    .split(" / ")
    .map((line) => capitalizeFirst(line.trim()))
    .join(" / ");
  const linked = addDexLinks(capitalized);
  return linked.replace(/ \/ /g, "<br/>");
}

function decorateSentence(sentence: string): string {
  return addDexLinks(capitalizeFirst(sentence.trim()));
}

function decorateDefinition(sentence: string): string {
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

  async function buildLine(
    rhyme: string,
    punct: string
  ): Promise<string> {
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
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") return res.status(200).end();

  const minR = Math.max(1, Math.min(5, Number(req.query.minRarity) || 1));
  const maxR = Math.max(1, Math.min(5, Number(req.query.rarity) || 2));

  async function safe(fn: () => Promise<string>): Promise<string> {
    try {
      return await fn();
    } catch {
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
