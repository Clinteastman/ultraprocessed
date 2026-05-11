<script lang="ts">
  import { onMount } from "svelte";
  import { api, ApiError } from "$lib/api/client";
  import type {
    BowelEventDto,
    ConsumptionLogDto,
    DailyCheckinDto,
    FoodEntryDto,
    SymptomEventDto,
    SymptomKind,
    TriggerReportDto
  } from "$lib/api/types";
  import BristolPicker from "$lib/components/BristolPicker.svelte";
  import SeverityPicker from "$lib/components/SeverityPicker.svelte";
  import GutTimeline from "$lib/components/GutTimeline.svelte";
  import DateRangePicker, { type DateRange } from "$lib/components/DateRangePicker.svelte";

  let loading = $state(true);
  let error = $state<string | null>(null);

  let bowels = $state<BowelEventDto[]>([]);
  let symptoms = $state<SymptomEventDto[]>([]);
  let logs = $state<ConsumptionLogDto[]>([]);
  let foods = $state<Map<string, FoodEntryDto>>(new Map());
  let checkins = $state<DailyCheckinDto[]>([]);
  let triggers = $state<TriggerReportDto | null>(null);

  let range = $state<DateRange>({
    preset: "today",
    from: startOfToday(),
    to: endOfToday(),
    label: "Today"
  });

  function startOfToday(): Date {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    return d;
  }
  function endOfToday(): Date {
    const d = new Date();
    d.setHours(23, 59, 59, 999);
    return d;
  }
  function todayIso(): string {
    return new Date().toISOString().slice(0, 10);
  }

  async function load(silent = false) {
    if (!silent) loading = true;
    error = null;
    try {
      const fromIso = range.from.toISOString();
      const toIso = range.to.toISOString();
      // Trigger report uses its own days window — show last 14 days by default.
      const [bowelsRes, symptomsRes, logsRes, foodsRes, checkinsRes, triggersRes] = await Promise.all([
        api.bowels(fromIso, toIso, 1000),
        api.symptoms(fromIso, toIso, undefined, 1000),
        api.consumption(fromIso, toIso, 5000),
        api.recentFoods(2000),
        api.dailyCheckins(undefined, undefined, 60),
        api.triggerReport({ days: 14 }).catch(() => null)
      ]);
      bowels = bowelsRes;
      symptoms = symptomsRes;
      logs = logsRes;
      foods = new Map(foodsRes.map((f) => [f.client_uuid, f]));
      checkins = checkinsRes;
      triggers = triggersRes;
    } catch (e) {
      error = e instanceof ApiError ? e.message : (e as Error).message;
    } finally {
      if (!silent) loading = false;
    }
  }

  onMount(() => {
    load();
    const t = setInterval(() => load(true), 30_000);
    return () => clearInterval(t);
  });

  // ---- Quick-log: bowel event ----
  let bowelDraft = $state<{
    bristol: number | null;
    urgency: number;
    pain: number;
    completeness: number;
    blood: boolean;
    mucus: boolean;
    notes: string;
  }>({ bristol: null, urgency: 0, pain: 0, completeness: 2, blood: false, mucus: false, notes: "" });
  let savingBowel = $state(false);

  async function logBowel() {
    if (bowelDraft.bristol == null) return;
    savingBowel = true;
    error = null;
    try {
      const now = new Date().toISOString();
      const evt: BowelEventDto = {
        client_uuid: crypto.randomUUID(),
        occurred_at: now,
        bristol: bowelDraft.bristol,
        urgency: bowelDraft.urgency,
        pain: bowelDraft.pain,
        completeness: bowelDraft.completeness,
        blood: bowelDraft.blood,
        mucus: bowelDraft.mucus,
        notes: bowelDraft.notes || null,
        created_at: now
      };
      await api.upsertBowels([evt]);
      bowelDraft = {
        bristol: null,
        urgency: 0,
        pain: 0,
        completeness: 2,
        blood: false,
        mucus: false,
        notes: ""
      };
      await load(true);
    } catch (e) {
      error = (e as Error).message;
    } finally {
      savingBowel = false;
    }
  }

  // ---- Quick-log: symptom ----
  let symptomDraft = $state<{
    kind: SymptomKind;
    severity: number;
    location: string;
    notes: string;
  }>({ kind: "BLOATING", severity: 1, location: "", notes: "" });
  let savingSymptom = $state(false);

  const SYMPTOM_OPTIONS: { value: SymptomKind; label: string; icon: string }[] = [
    { value: "BLOATING",  label: "Bloating",  icon: "balloon" },
    { value: "HEARTBURN", label: "Heartburn", icon: "flame" },
    { value: "CRAMPING",  label: "Cramping",  icon: "bolt" },
    { value: "GAS",       label: "Gas",       icon: "wind" },
    { value: "PAIN",      label: "Pain",      icon: "alert" },
    { value: "NAUSEA",    label: "Nausea",    icon: "wave" },
    { value: "URGENCY",   label: "Urgency",   icon: "run" },
    { value: "FATIGUE",   label: "Fatigue",   icon: "moon" }
  ];

  async function logSymptom() {
    savingSymptom = true;
    error = null;
    try {
      const now = new Date().toISOString();
      const evt: SymptomEventDto = {
        client_uuid: crypto.randomUUID(),
        occurred_at: now,
        kind: symptomDraft.kind,
        severity: symptomDraft.severity,
        duration_minutes: null,
        location: symptomDraft.location || null,
        notes: symptomDraft.notes || null,
        created_at: now
      };
      await api.upsertSymptoms([evt]);
      symptomDraft = { kind: symptomDraft.kind, severity: 1, location: "", notes: "" };
      await load(true);
    } catch (e) {
      error = (e as Error).message;
    } finally {
      savingSymptom = false;
    }
  }

  // ---- Daily check-in ----
  const todayCheckin = $derived(checkins.find((c) => c.day === todayIso()));
  let checkinDraft = $state<{
    bloating_overall: number;
    heartburn_overall: number;
    gut_score: number | null;
    stress: number | null;
    period: boolean;
    notes: string;
  }>({ bloating_overall: 0, heartburn_overall: 0, gut_score: null, stress: 0, period: false, notes: "" });
  let savingCheckin = $state(false);

  $effect(() => {
    if (todayCheckin) {
      checkinDraft = {
        bloating_overall: todayCheckin.bloating_overall,
        heartburn_overall: todayCheckin.heartburn_overall,
        gut_score: todayCheckin.gut_score,
        stress: todayCheckin.stress,
        period: todayCheckin.period,
        notes: todayCheckin.notes ?? ""
      };
    }
  });

  async function saveCheckin() {
    savingCheckin = true;
    error = null;
    try {
      const now = new Date().toISOString();
      const dto: DailyCheckinDto = {
        client_uuid: todayCheckin?.client_uuid ?? crypto.randomUUID(),
        day: todayIso(),
        bloating_overall: checkinDraft.bloating_overall,
        heartburn_overall: checkinDraft.heartburn_overall,
        gut_score: checkinDraft.gut_score,
        mood: null,
        stress: checkinDraft.stress,
        period: checkinDraft.period,
        notes: checkinDraft.notes || null,
        created_at: todayCheckin?.created_at ?? now,
        updated_at: now
      };
      await api.upsertDaily([dto]);
      await load(true);
    } catch (e) {
      error = (e as Error).message;
    } finally {
      savingCheckin = false;
    }
  }

  function formatTime(iso: string): string {
    return new Date(iso).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
  }

  function formatDay(iso: string): string {
    const d = new Date(iso);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (d.getTime() >= today.getTime()) return "Today";
    const yesterday = new Date(today.getTime() - 86_400_000);
    if (d.getTime() >= yesterday.getTime()) return "Yesterday";
    return d.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" });
  }

  function fodmapTone(level: string): string {
    if (level === "HIGH") return "bg-nova-4/40 text-nova-4";
    if (level === "MODERATE") return "bg-nova-3/40 text-nova-3";
    if (level === "LOW") return "bg-nova-1/40 text-nova-1";
    return "bg-surface-2 text-ink-mid";
  }

  async function deleteBowelEvt(uuid: string) {
    if (!confirm("Delete this bowel event?")) return;
    await api.deleteBowel(uuid);
    await load(true);
  }
  async function deleteSymptomEvt(uuid: string) {
    if (!confirm("Delete this symptom?")) return;
    await api.deleteSymptom(uuid);
    await load(true);
  }
</script>

<svelte:head><title>IBS · Ultraprocessed</title></svelte:head>

<div class="space-y-6">
  <div class="flex flex-wrap items-end justify-between gap-4">
    <div>
      <p class="text-xs uppercase tracking-wider text-ink-mid">Gut tracker</p>
      <h2 class="font-display text-3xl text-ink-hi">{range.label}</h2>
    </div>
    <DateRangePicker bind:value={range} onChange={() => load()} />
  </div>

  {#if error}
    <div class="rounded-md bg-surface-1 border border-nova-4/40 p-4 text-ink-mid text-sm">
      {error}
    </div>
  {/if}

  <GutTimeline {logs} {foods} {symptoms} {bowels}
               fromMs={range.from.getTime()} toMs={range.to.getTime()} />

  <!-- Quick-log row: bowel + symptom + daily -->
  <section class="grid grid-cols-1 lg:grid-cols-2 gap-6">
    <!-- Bowel quick-log -->
    <div class="rounded-lg bg-surface-1 p-6 space-y-4">
      <div class="flex items-baseline justify-between">
        <p class="text-xs uppercase tracking-wider text-ink-mid">Log a bathroom visit</p>
        <p class="text-[11px] text-ink-lo">Right now</p>
      </div>

      <BristolPicker bind:value={bowelDraft.bristol} />

      <div class="grid grid-cols-2 gap-4">
        <SeverityPicker bind:value={bowelDraft.urgency} label="Urgency" />
        <SeverityPicker bind:value={bowelDraft.pain} label="Pain" />
      </div>

      <div class="flex flex-wrap gap-3 text-sm">
        <label class="flex items-center gap-2 cursor-pointer">
          <input type="checkbox" bind:checked={bowelDraft.blood} class="accent-nova-4" />
          <span class="text-ink-mid">Blood</span>
        </label>
        <label class="flex items-center gap-2 cursor-pointer">
          <input type="checkbox" bind:checked={bowelDraft.mucus} class="accent-nova-3" />
          <span class="text-ink-mid">Mucus</span>
        </label>
        <label class="flex items-center gap-2 cursor-pointer ml-auto">
          <span class="text-ink-mid text-xs">Feeling</span>
          <select bind:value={bowelDraft.completeness} class="bg-surface-2 border border-surface-3 rounded-sm text-sm px-2 py-1">
            <option value={0}>Incomplete</option>
            <option value={1}>Partial</option>
            <option value={2}>Complete</option>
          </select>
        </label>
      </div>

      <input
        type="text"
        bind:value={bowelDraft.notes}
        placeholder="Notes (optional)"
        class="w-full bg-surface-2 border border-surface-3 rounded-md px-3 py-2 text-sm placeholder:text-ink-lo"
      />

      <button
        type="button"
        onclick={logBowel}
        disabled={bowelDraft.bristol == null || savingBowel}
        class="w-full rounded-md bg-ink-hi text-surface-0 font-medium py-2.5 disabled:opacity-40 disabled:cursor-not-allowed"
      >
        {savingBowel ? "Saving..." : bowelDraft.bristol == null ? "Pick a Bristol score" : "Log bowel event"}
      </button>
    </div>

    <!-- Symptom quick-log -->
    <div class="rounded-lg bg-surface-1 p-6 space-y-4">
      <div class="flex items-baseline justify-between">
        <p class="text-xs uppercase tracking-wider text-ink-mid">Log a symptom</p>
        <p class="text-[11px] text-ink-lo">Right now</p>
      </div>

      <div class="grid grid-cols-4 gap-1.5">
        {#each SYMPTOM_OPTIONS as opt}
          <button
            type="button"
            onclick={() => (symptomDraft.kind = opt.value)}
            class="rounded-md py-2 px-1 text-xs font-medium transition-all
                   {symptomDraft.kind === opt.value ? 'bg-ink-hi/15 ring-2 ring-ink-hi text-ink-hi' : 'bg-surface-2 text-ink-mid hover:bg-surface-3'}"
          >
            {opt.label}
          </button>
        {/each}
      </div>

      <SeverityPicker bind:value={symptomDraft.severity} label="Severity" showNone={false} />

      <input
        type="text"
        bind:value={symptomDraft.location}
        placeholder="Where? (e.g. upper abdomen) — optional"
        class="w-full bg-surface-2 border border-surface-3 rounded-md px-3 py-2 text-sm placeholder:text-ink-lo"
      />

      <input
        type="text"
        bind:value={symptomDraft.notes}
        placeholder="Notes (optional)"
        class="w-full bg-surface-2 border border-surface-3 rounded-md px-3 py-2 text-sm placeholder:text-ink-lo"
      />

      <button
        type="button"
        onclick={logSymptom}
        disabled={savingSymptom}
        class="w-full rounded-md bg-ink-hi text-surface-0 font-medium py-2.5 disabled:opacity-40"
      >
        {savingSymptom ? "Saving..." : `Log ${symptomDraft.kind.toLowerCase()}`}
      </button>
    </div>
  </section>

  <!-- Daily check-in -->
  <section class="rounded-lg bg-surface-1 p-6 space-y-4">
    <div class="flex items-baseline justify-between">
      <div>
        <p class="text-xs uppercase tracking-wider text-ink-mid">Daily check-in</p>
        <p class="text-sm text-ink-mid mt-0.5">
          {todayCheckin ? "Updates today's entry." : "End-of-day rollup. Pick the worst level you felt today."}
        </p>
      </div>
      <span class="text-xs text-ink-lo">{todayIso()}</span>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <SeverityPicker bind:value={checkinDraft.bloating_overall} label="Bloating today" />
      <SeverityPicker bind:value={checkinDraft.heartburn_overall} label="Heartburn today" />
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div>
        <p class="text-xs uppercase tracking-wider text-ink-mid mb-2">Stress today</p>
        <SeverityPicker bind:value={checkinDraft.stress as number} label="" />
      </div>
      <div>
        <p class="text-xs uppercase tracking-wider text-ink-mid mb-2">
          Gut score (1 worst, 10 best)
        </p>
        <div class="grid grid-cols-10 gap-1">
          {#each Array.from({ length: 10 }, (_, i) => i + 1) as n}
            <button
              type="button"
              onclick={() => (checkinDraft.gut_score = n)}
              class="aspect-square rounded text-xs font-medium transition-all
                     {checkinDraft.gut_score === n ? 'ring-2 ring-ink-hi' : ''}
                     {n <= 3 ? 'bg-nova-4/30' : n <= 6 ? 'bg-nova-3/40' : 'bg-nova-1/40'}"
            >
              {n}
            </button>
          {/each}
        </div>
      </div>
    </div>

    <div class="flex flex-wrap items-center gap-4">
      <label class="flex items-center gap-2 cursor-pointer text-sm text-ink-mid">
        <input type="checkbox" bind:checked={checkinDraft.period} class="accent-nova-4" />
        Period today
      </label>
      <input
        type="text"
        bind:value={checkinDraft.notes}
        placeholder="Notes for today (stress, sleep, travel...)"
        class="flex-1 min-w-0 bg-surface-2 border border-surface-3 rounded-md px-3 py-2 text-sm placeholder:text-ink-lo"
      />
      <button
        type="button"
        onclick={saveCheckin}
        disabled={savingCheckin}
        class="rounded-md bg-ink-hi text-surface-0 font-medium px-4 py-2 disabled:opacity-40"
      >
        {savingCheckin ? "Saving..." : todayCheckin ? "Update" : "Save check-in"}
      </button>
    </div>
  </section>

  <!-- Triggers panel -->
  <section class="rounded-lg bg-surface-1 p-6 space-y-4">
    <div>
      <p class="text-xs uppercase tracking-wider text-ink-mid">Likely triggers (last 14 days)</p>
      <p class="text-sm text-ink-mid mt-0.5">
        Foods ranked by their estimated implication in your flares. Higher = more
        likely. Tagged-FODMAP foods rank above unknown.
        {#if triggers}
          <span class="text-ink-lo text-xs">
            · {triggers.flares_considered} flare{triggers.flares_considered === 1 ? "" : "s"} considered
          </span>
        {/if}
      </p>
    </div>

    {#if !triggers || triggers.foods.length === 0}
      <p class="text-ink-mid text-sm">
        No triggers ranked yet — keep logging meals and symptoms. The engine
        needs at least one flare (severe symptom, Bristol 1-2, 6-7, or
        urgency/pain) and food eaten in the prior 48h.
      </p>
    {:else}
      <div class="space-y-1.5">
        {#each triggers.foods as t, i}
          <div class="flex items-center gap-3 rounded-md p-3 bg-surface-2">
            <span class="text-ink-lo font-mono text-xs w-6 text-right">{i + 1}.</span>
            <a href={`/foods/${t.food_client_uuid}`} class="flex-1 min-w-0 hover:opacity-80">
              <p class="text-ink-hi font-medium truncate">{t.food_name}</p>
              <p class="text-xs text-ink-mid truncate">
                {t.food_brand ?? ""}
                {#if t.food_brand}·{/if}
                appeared before {t.flare_count} flare{t.flare_count === 1 ? "" : "s"}
                · {t.baseline_count} non-flare logs
                {#if t.sample_flares.length > 0}
                  · e.g. {t.sample_flares.join(", ")}
                {/if}
              </p>
            </a>
            <span class="text-xs px-2 py-0.5 rounded {fodmapTone(t.fodmap_level)}">
              {t.fodmap_level}
            </span>
            {#if t.fodmap_tags.length > 0}
              <span class="text-[10px] text-ink-lo hidden md:inline">
                {t.fodmap_tags.join(", ")}
              </span>
            {/if}
            <span class="font-display text-lg text-ink-hi tabular-nums w-14 text-right">
              {t.score.toFixed(2)}
            </span>
          </div>
        {/each}
      </div>
    {/if}
  </section>

  <!-- Recent events lists -->
  <section class="grid grid-cols-1 lg:grid-cols-2 gap-6">
    <div class="rounded-lg bg-surface-1 p-6">
      <p class="text-xs uppercase tracking-wider text-ink-mid mb-3">
        Bowel events ({bowels.length})
      </p>
      {#if bowels.length === 0}
        <p class="text-ink-mid text-sm">Nothing logged in this period.</p>
      {:else}
        <div class="space-y-2">
          {#each bowels as b}
            <div class="flex items-center gap-3 rounded-md p-3 bg-surface-2 group">
              <div class="w-9 h-9 rounded-md flex items-center justify-center font-mono text-sm
                          {b.bristol === 4 ? 'bg-nova-1/40' : b.bristol >= 6 || b.bristol <= 2 ? 'bg-nova-4/40' : 'bg-nova-3/40'}">
                {b.bristol}
              </div>
              <div class="flex-1 min-w-0">
                <p class="text-ink-hi text-sm">
                  Bristol {b.bristol}
                  {#if b.urgency >= 2}· urgent{/if}
                  {#if b.pain >= 2}· painful{/if}
                  {#if b.blood}· blood{/if}
                  {#if b.mucus}· mucus{/if}
                </p>
                <p class="text-xs text-ink-mid">
                  {formatDay(b.occurred_at)} {formatTime(b.occurred_at)}
                  {#if b.notes}· {b.notes}{/if}
                </p>
              </div>
              <button
                type="button"
                onclick={() => deleteBowelEvt(b.client_uuid)}
                class="opacity-0 group-hover:opacity-100 text-ink-lo hover:text-nova-4 p-1 transition-opacity"
                aria-label="Delete"
              >×</button>
            </div>
          {/each}
        </div>
      {/if}
    </div>

    <div class="rounded-lg bg-surface-1 p-6">
      <p class="text-xs uppercase tracking-wider text-ink-mid mb-3">
        Symptoms ({symptoms.length})
      </p>
      {#if symptoms.length === 0}
        <p class="text-ink-mid text-sm">Nothing logged in this period.</p>
      {:else}
        <div class="space-y-2">
          {#each symptoms as s}
            <div class="flex items-center gap-3 rounded-md p-3 bg-surface-2 group">
              <div class="w-9 h-9 rounded-md flex items-center justify-center text-xs uppercase font-medium
                          {s.severity >= 3 ? 'bg-nova-4/40 text-nova-4' :
                           s.severity === 2 ? 'bg-nova-3/40 text-nova-3' :
                           'bg-nova-2/40 text-ink-hi'}">
                {s.kind.slice(0, 3)}
              </div>
              <div class="flex-1 min-w-0">
                <p class="text-ink-hi text-sm">
                  {s.kind.toLowerCase().replace(/^./, c => c.toUpperCase())}
                  · {["none", "mild", "moderate", "severe"][s.severity]}
                  {#if s.location}· {s.location}{/if}
                </p>
                <p class="text-xs text-ink-mid">
                  {formatDay(s.occurred_at)} {formatTime(s.occurred_at)}
                  {#if s.notes}· {s.notes}{/if}
                </p>
              </div>
              <button
                type="button"
                onclick={() => deleteSymptomEvt(s.client_uuid)}
                class="opacity-0 group-hover:opacity-100 text-ink-lo hover:text-nova-4 p-1 transition-opacity"
                aria-label="Delete"
              >×</button>
            </div>
          {/each}
        </div>
      {/if}
    </div>
  </section>
</div>
