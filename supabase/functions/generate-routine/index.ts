// Edge Function: picks today's exercises with an LLM via OpenRouter.
// The OpenRouter key lives only in Supabase secrets (OPENROUTER_API_KEY), never in the APK.
// The app decides sets / seconds / rest locally; this function only chooses and orders exercises.
import { createClient } from "jsr:@supabase/supabase-js@2";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const GOALS = ["fuerza", "cardio", "tonificar", "movilidad"] as const;
const FOCUS = ["cuerpo_completo", "tren_superior", "tren_inferior", "core"] as const;
const DEFAULT_MODEL = "meta-llama/llama-3.3-70b-instruct:free";

type Exercise = {
  slug: string;
  name: string;
  muscle_groups: string[];
  equipment: string[];
  type: string;
  difficulty: number;
  unilateral: boolean;
};

type Body = {
  goal: string;
  duration_min: number;
  focus?: string;
  level?: string;
  available_slugs?: string[];
  recent_slugs?: string[];
};

const PREFERRED_TYPES: Record<string, string[]> = {
  fuerza: ["fuerza", "core"],
  cardio: ["cardio", "fuerza", "core"],
  tonificar: ["fuerza", "core", "cardio"],
  movilidad: ["movilidad", "recuperacion"],
};

const FOCUS_GROUPS: Record<string, string[]> = {
  tren_superior: ["pecho", "espalda", "hombros", "brazos", "biceps", "triceps"],
  tren_inferior: ["piernas", "cuadriceps", "isquios", "gluteos", "gemelos", "aductores"],
  core: ["core", "abdominales", "oblicuos", "lumbar"],
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });
}

/** ~3.5 min per exercise once sets, rest and transitions are included. */
export function targetCount(durationMin: number) {
  return Math.min(15, Math.max(2, Math.round(durationMin / 3.5)));
}

export function candidates(all: Exercise[], owned: Set<string>, body: Body): Exercise[] {
  owned.add("ninguno");
  let pool = all.filter((e) => e.equipment.every((q) => owned.has(q)));
  if (body.available_slugs?.length) {
    const allowed = new Set(body.available_slugs);
    pool = pool.filter((e) => allowed.has(e.slug));
  }
  const types = PREFERRED_TYPES[body.goal] ?? PREFERRED_TYPES.tonificar;
  const byType = pool.filter((e) => types.includes(e.type));
  if (byType.length) pool = byType;
  const groups = FOCUS_GROUPS[body.focus ?? ""];
  if (groups) {
    const focused = pool.filter((e) => e.muscle_groups.some((m) => groups.includes(m)));
    if (focused.length) pool = focused;
  }
  return pool;
}

/** Balanced random pick: round-robin over primary muscle groups, avoiding the last workout. */
export function fallbackPick(pool: Exercise[], count: number, recent: string[] = []): string[] {
  const shuffled = [...pool].sort(() => Math.random() - 0.5);
  const fresh = shuffled.filter((e) => !recent.includes(e.slug));
  const ordered = [...fresh, ...shuffled.filter((e) => recent.includes(e.slug))];
  const buckets = new Map<string, Exercise[]>();
  for (const e of ordered) {
    const k = e.muscle_groups[0] ?? "otros";
    buckets.set(k, [...(buckets.get(k) ?? []), e]);
  }
  const out: string[] = [];
  while (out.length < count && [...buckets.values()].some((b) => b.length)) {
    for (const b of buckets.values()) {
      const e = b.shift();
      if (e && out.length < count) out.push(e.slug);
    }
  }
  return out;
}

async function askModel(pool: Exercise[], body: Body, count: number): Promise<{ title: string; slugs: string[] }> {
  const key = Deno.env.get("OPENROUTER_API_KEY");
  if (!key) throw new Error("OPENROUTER_API_KEY not set");
  const model = Deno.env.get("OPENROUTER_MODEL") || DEFAULT_MODEL;
  const catalog = pool.map((e) => ({ slug: e.slug, nombre: e.name, musculos: e.muscle_groups, tipo: e.type, dificultad: e.difficulty }));
  const system =
    "Eres un entrenador personal. Diseñas la sesión de hoy para entrenar en casa eligiendo SOLO ejercicios del catálogo dado. " +
    "Responde únicamente con JSON válido con la forma {\"title\": string, \"exercise_slugs\": string[]}.";
  const user = JSON.stringify({
    objetivo: body.goal,
    zona: body.focus ?? "cuerpo_completo",
    nivel: body.level ?? "intermedio",
    duracion_min: body.duration_min,
    numero_de_ejercicios: count,
    evita_si_puedes: body.recent_slugs ?? [],
    reglas: [
      `Elige exactamente ${count} slugs distintos del catálogo.`,
      "Ordénalos: primero los más exigentes o multiarticulares, al final core o movilidad.",
      "Equilibra grupos musculares y no repitas dos seguidos del mismo grupo.",
      "El título es corto (máx. 5 palabras), en español y motivador.",
    ],
    catalogo: catalog,
  });

  const res = await fetch("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json", "X-Title": "Gym en Casa" },
    body: JSON.stringify({
      model,
      temperature: 0.9,
      messages: [{ role: "system", content: system }, { role: "user", content: user }],
      response_format: { type: "json_object" },
    }),
    signal: AbortSignal.timeout(30_000),
  });
  if (!res.ok) throw new Error(`OpenRouter ${res.status}: ${(await res.text()).slice(0, 200)}`);
  const data = await res.json();
  const text: string = data?.choices?.[0]?.message?.content ?? "";
  const match = text.match(/\{[\s\S]*\}/);
  if (!match) throw new Error("model returned no JSON");
  const parsed = JSON.parse(match[0]);
  return { title: String(parsed.title ?? "").slice(0, 60), slugs: Array.isArray(parsed.exercise_slugs) ? parsed.exercise_slugs.map(String) : [] };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "method not allowed" }, 405);

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return json({ error: "unauthorized" }, 401);
  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: authHeader } },
  });
  const { data: userData, error: userError } = await supabase.auth.getUser(authHeader.replace(/^Bearer\s+/i, ""));
  if (userError || !userData?.user) return json({ error: "unauthorized" }, 401);

  let body: Body;
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid JSON body" }, 400);
  }
  if (!GOALS.includes(body.goal as (typeof GOALS)[number])) return json({ error: "invalid goal" }, 400);
  if (body.focus && !FOCUS.includes(body.focus as (typeof FOCUS)[number])) return json({ error: "invalid focus" }, 400);
  const duration = Number(body.duration_min);
  if (!Number.isFinite(duration) || duration < 5 || duration > 120) return json({ error: "invalid duration_min" }, 400);

  const [{ data: profile }, { data: exercises, error: exError }] = await Promise.all([
    supabase.from("profiles").select("equipment").eq("id", userData.user.id).maybeSingle(),
    supabase.from("exercises").select("slug,name,muscle_groups,equipment,type,difficulty,unilateral"),
  ]);
  if (exError) return json({ error: "catalog unavailable" }, 500);

  const owned = new Set<string>(profile?.equipment ?? []);
  // The app already filtered by equipment; if the profile has not synced yet, trust its list.
  if (owned.size === 0 && body.available_slugs?.length) {
    for (const e of exercises ?? []) if (body.available_slugs.includes(e.slug)) e.equipment.forEach((q: string) => owned.add(q));
  }
  const pool = candidates((exercises ?? []) as Exercise[], owned, body);
  if (!pool.length) return json({ title: "", exercise_slugs: [], source: "empty" });
  const count = Math.min(targetCount(duration), pool.length);

  try {
    const { title, slugs } = await askModel(pool, body, count);
    const valid = new Set(pool.map((e) => e.slug));
    const picked = [...new Set(slugs.filter((s) => valid.has(s)))].slice(0, count);
    // Top up if the model returned too few valid slugs.
    if (picked.length < count) {
      for (const s of fallbackPick(pool.filter((e) => !picked.includes(e.slug)), count - picked.length, body.recent_slugs)) picked.push(s);
    }
    return json({ title, exercise_slugs: picked, source: "ai" });
  } catch (e) {
    console.error("AI failed, using fallback:", (e as Error).message);
    return json({ title: "", exercise_slugs: fallbackPick(pool, count, body.recent_slugs), source: "fallback" });
  }
});
