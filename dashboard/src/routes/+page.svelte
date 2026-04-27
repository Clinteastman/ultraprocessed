<script lang="ts">
  import { onMount } from "svelte";
  import { api, ApiError } from "$lib/api/client";
  import type { AggregateResponse, ConsumptionLogDto, FoodEntryDto } from "$lib/api/types";
  import NovaPill from "$lib/components/NovaPill.svelte";
  import NutrientBar from "$lib/components/NutrientBar.svelte";
  import CalorieChart from "$lib/components/CalorieChart.svelte";
  import DateRangePicker, { type DateRange } from "$lib/components/DateRangePicker.svelte";
  import DietScoreCard from "$lib/components/DietScoreCard.svelte";
  import FastingStatus from "$lib/components/FastingStatus.svelte";
  import NovaTrendChart from "$lib/components/NovaTrendChart.svelte";
  import UpfShareCard from "$lib/components/UpfShareCard.svelte";

  let aggregate = $state<AggregateResponse | null>(null);
  let logs = $state<ConsumptionLogDto[]>([]);
  let foods = $state<Map<string, FoodEntryDto>>(new Map());
  let loading = $state(true);
  let error = $state<string | null>(null);

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

  const NUTRIENT_LABELS: Record<string, string> = {
    protein_g: "Protein", fat_g: "Fat", saturated_fat_g: "Sat fat",
    carbs_g: "Carbs", sugar_g: "Sugar", fiber_g: "Fiber",
    salt_g: "Salt", sodium_mg: "Sodium", cholesterol_mg: "Cholesterol",
    omega3_g: "Omega-3", calcium_mg: "Calcium", iron_mg: "Iron",
    potassium_mg: "Potassium", magnesium_mg: "Magnesium", zinc_mg: "Zinc",
    phosphorus_mg: "Phosphorus", selenium_ug: "Selenium", iodine_ug: "Iodine",
    copper_mg: "Copper", manganese_mg: "Manganese", vitamin_a_ug: "Vitamin A",
    vitamin_c_mg: "Vitamin C", vitamin_d_ug: "Vitamin D", vitamin_e_mg: "Vitamin E",
    vitamin_k_ug: "Vitamin K", vitamin_b1_mg: "Vitamin B1",
    vitamin_b2_mg: "Vitamin B2", vitamin_b3_mg: "Vitamin B3",
    vitamin_b6_mg: "Vitamin B6", vitamin_b12_ug: "Vitamin B12",
    folate_ug: "Folate"
  };

  const MACRO_KEYS = ["protein_g", "fat_g", "saturated_fat_g", "carbs_g", "sugar_g", "fiber_g", "salt_g"];

  async function load(silent = false) {
    if (!silent) loading = true;
    error = null;
    try {
      const fromIso = range.from.toISOString();
      const toIso = range.to.toISOString();
      const [agg, logsRes, foodsRes] = await Promise.all([
        api.range(fromIso, toIso),
        api.consumption(fromIso, toIso, 5000),
        api.recentFoods(2000)
      ]);
      aggregate = agg;
      logs = logsRes;
      foods = new Map(foodsRes.map((f) => [f.client_uuid, f]));
    } catch (e) {
      error = e instanceof ApiError ? e.message : (e as Error).message;
    } finally {
      if (!silent) loading = false;
    }
  }

  // Background poll: re-fetch every 20s while the tab is visible so the
  // dashboard reflects new phone activity without a manual refresh. Pauses
  // when hidden to avoid useless requests, and refreshes on visibility
  // return so coming back to the tab gives current data immediately.
  const POLL_MS = 20_000;
  onMount(() => {
    load();
    let timer: ReturnType<typeof setInterval> | null = null;
    const start = () => {
      if (timer) return;
      timer = setInterval(() => load(true), POLL_MS);
    };
    const stop = () => {
      if (timer) clearInterval(timer);
      timer = null;
    };
    const onVis = () => {
      if (document.visibilityState === "visible") {
        load(true);
        start();
      } else {
        stop();
      }
    };
    document.addEventListener("visibilitychange", onVis);
    start();
    return () => {
      document.removeEventListener("visibilitychange", onVis);
      stop();
    };
  });

  async function deleteLog(clientUuid: string) {
    if (!confirm("Delete this entry?")) return;
    try {
      await api.deleteConsumption(clientUuid);
      logs = logs.filter((l) => l.client_uuid !== clientUuid);
      await load();
    } catch (e) {
      error = (e as Error).message;
    }
  }

  // Bucket logs hourly for single-day ranges, daily for multi-day.
  const calorieBuckets = $derived.by(() => {
    const hourMs = 60 * 60 * 1000;
    const dayMs = 24 * hourMs;
    const spanMs = range.to.getTime() - range.from.getTime();

    if (spanMs < dayMs * 1.5) {
      const buckets: { label: string; kcal: number; iso: string }[] = [];
      const start = new Date(range.from);
      start.setHours(0, 0, 0, 0);
      for (let h = 0; h < 24; h++) {
        const d = new Date(start.getTime() + h * hourMs);
        buckets.push({
          label: `${h.toString().padStart(2, "0")}:00`,
          kcal: 0,
          iso: d.toISOString()
        });
      }
      for (const log of logs) {
        const eaten = new Date(log.eaten_at);
        // Only include logs that fall on the start day (handles tz weirdness).
        if (
          eaten.getFullYear() === start.getFullYear() &&
          eaten.getMonth() === start.getMonth() &&
          eaten.getDate() === start.getDate()
        ) {
          const h = eaten.getHours();
          buckets[h].kcal += log.kcal_consumed_snapshot ?? 0;
        }
      }
      return buckets;
    }

    const days: Map<string, { label: string; kcal: number; iso: string }> = new Map();
    const start = new Date(range.from);
    start.setHours(0, 0, 0, 0);
    const end = new Date(range.to);
    end.setHours(0, 0, 0, 0);
    const dayCount = Math.max(1, Math.round((end.getTime() - start.getTime()) / dayMs) + 1);
    for (let i = 0; i < dayCount; i++) {
      const d = new Date(start.getTime() + i * dayMs);
      const iso = d.toISOString().slice(0, 10);
      const label = d.toLocaleDateString(undefined, { day: "numeric", month: "short" });
      days.set(iso, { label, kcal: 0, iso });
    }
    for (const log of logs) {
      const iso = log.eaten_at.slice(0, 10);
      const bucket = days.get(iso);
      if (bucket) bucket.kcal += log.kcal_consumed_snapshot ?? 0;
    }
    return Array.from(days.values());
  });

  // Daily kcal-weighted NOVA average for the trend chart. Uses the same
  // daily / hourly bucketing as the calorie chart so the two read together.
  const novaTrend = $derived.by(() => {
    const hourMs = 60 * 60 * 1000;
    const dayMs = 24 * hourMs;
    const spanMs = range.to.getTime() - range.from.getTime();
    const buckets: { label: string; novaAverage: number | null; kcal: number }[] = [];

    if (spanMs < dayMs * 1.5) {
      // Single day: hourly bucketing
      const start = new Date(range.from);
      start.setHours(0, 0, 0, 0);
      const sums = Array.from({ length: 24 }, () => ({ weighted: 0, kcal: 0 }));
      for (const log of logs) {
        const eaten = new Date(log.eaten_at);
        if (
          eaten.getFullYear() !== start.getFullYear() ||
          eaten.getMonth() !== start.getMonth() ||
          eaten.getDate() !== start.getDate()
        ) continue;
        const food = foods.get(log.food_client_uuid);
        if (!food) continue;
        const kcal = log.kcal_consumed_snapshot ?? 0;
        if (kcal <= 0) continue;
        const h = eaten.getHours();
        sums[h].weighted += food.nova_class * kcal;
        sums[h].kcal += kcal;
      }
      for (let h = 0; h < 24; h++) {
        const s = sums[h];
        buckets.push({
          label: `${h.toString().padStart(2, "0")}:00`,
          novaAverage: s.kcal > 0 ? s.weighted / s.kcal : null,
          kcal: s.kcal
        });
      }
      return buckets;
    }

    // Multi-day: daily bucketing
    const start = new Date(range.from);
    start.setHours(0, 0, 0, 0);
    const end = new Date(range.to);
    end.setHours(0, 0, 0, 0);
    const dayCount = Math.max(1, Math.round((end.getTime() - start.getTime()) / dayMs) + 1);
    const days = new Map<string, { label: string; weighted: number; kcal: number }>();
    for (let i = 0; i < dayCount; i++) {
      const d = new Date(start.getTime() + i * dayMs);
      const iso = d.toISOString().slice(0, 10);
      const label = d.toLocaleDateString(undefined, { day: "numeric", month: "short" });
      days.set(iso, { label, weighted: 0, kcal: 0 });
    }
    for (const log of logs) {
      const iso = log.eaten_at.slice(0, 10);
      const bucket = days.get(iso);
      if (!bucket) continue;
      const food = foods.get(log.food_client_uuid);
      if (!food) continue;
      const kcal = log.kcal_consumed_snapshot ?? 0;
      if (kcal <= 0) continue;
      bucket.weighted += food.nova_class * kcal;
      bucket.kcal += kcal;
    }
    for (const v of days.values()) {
      buckets.push({
        label: v.label,
        novaAverage: v.kcal > 0 ? v.weighted / v.kcal : null,
        kcal: v.kcal
      });
    }
    return buckets;
  });

  // Group consumption logs by day for the right column.
  const groupedLogs = $derived.by(() => {
    const groups: { label: string; entries: ConsumptionLogDto[] }[] = [];
    const todayKey = new Date().toISOString().slice(0, 10);
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const yesterdayKey = yesterday.toISOString().slice(0, 10);

    for (const log of logs) {
      const dayIso = log.eaten_at.slice(0, 10);
      let label: string;
      if (dayIso === todayKey) label = "Today";
      else if (dayIso === yesterdayKey) label = "Yesterday";
      else
        label = new Date(log.eaten_at).toLocaleDateString(undefined, {
          weekday: "short",
          day: "numeric",
          month: "short"
        });
      const last = groups[groups.length - 1];
      if (last && last.label === label) last.entries.push(log);
      else groups.push({ label, entries: [log] });
    }
    return groups;
  });

  const macros = $derived.by(() => {
    if (!aggregate) return [];
    return MACRO_KEYS
      .filter((k) => aggregate!.nutrients_adequacy[k])
      .map((k) => ({ name: NUTRIENT_LABELS[k] ?? k, key: k, data: aggregate!.nutrients_adequacy[k] }));
  });

  const micros = $derived.by(() => {
    if (!aggregate) return [];
    return Object.keys(aggregate.nutrients_adequacy)
      .filter((k) => !MACRO_KEYS.includes(k))
      .map((k) => ({ name: NUTRIENT_LABELS[k] ?? k, key: k, data: aggregate!.nutrients_adequacy[k] }));
  });

  function timeLabel(iso: string): string {
    return new Date(iso).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
  }

  const novaTint = (cls: number) => {
    const map: Record<number, string> = { 1: "#5BC97D", 2: "#C7C354", 3: "#E8A04A", 4: "#D8543E" };
    return `color-mix(in srgb, ${map[cls] ?? "#888"} 12%, transparent)`;
  };
</script>

<svelte:head><title>Intake · Ultraprocessed</title></svelte:head>

<div class="space-y-6">
  <div class="flex flex-wrap items-end justify-between gap-4">
    <div>
      <p class="text-xs uppercase tracking-wider text-ink-mid">Intake</p>
      <h2 class="font-display text-3xl text-ink-hi">{range.label}</h2>
    </div>
    <DateRangePicker bind:value={range} onChange={() => load()} />
  </div>

  <FastingStatus />

  {#if loading}
    <div class="text-ink-mid">Loading...</div>
  {:else if error}
    <div class="rounded-md bg-surface-1 border border-surface-3 p-6 text-ink-mid">
      {error}
    </div>
  {:else if aggregate}
    <!-- Twin hero cards: how processed your diet is, and the UPF share. -->
    <section class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <DietScoreCard
        novaAverage={aggregate.nova_average}
        mealCount={aggregate.meal_count}
      />
      <UpfShareCard
        novaBreakdown={aggregate.nova_breakdown}
        totalKcal={aggregate.calories_consumed}
      />
    </section>

    <section class="rounded-lg bg-surface-1 p-6">
      <div class="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <p class="text-xs uppercase tracking-wider text-ink-mid">Calories</p>
          <div class="flex items-baseline gap-3 mt-1">
            <span class="font-display text-5xl">{aggregate.calories_consumed.toFixed(0)}</span>
            <span class="text-ink-mid">
              of {aggregate.calorie_reference.toFixed(0)} kcal target
              {#if range.preset !== "today" && range.preset !== "yesterday"}
                <span class="text-ink-lo">(period total)</span>
              {/if}
            </span>
          </div>
        </div>
        <p class="text-sm text-ink-mid">
          {aggregate.meal_count} meal{aggregate.meal_count === 1 ? "" : "s"}
        </p>
      </div>
      <div class="mt-5">
        <CalorieChart buckets={calorieBuckets} target={aggregate.calorie_reference} />
      </div>
    </section>

    <section class="rounded-lg bg-surface-1 p-6">
      <div class="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <p class="text-xs uppercase tracking-wider text-ink-mid">NOVA score trend</p>
          <p class="text-sm text-ink-mid mt-1">
            Calorie-weighted average per
            {range.to.getTime() - range.from.getTime() < 1.5 * 24 * 60 * 60 * 1000 ? "hour" : "day"}.
            Lower is better.
          </p>
        </div>
        {#if aggregate.nova_average != null}
          <p class="text-sm text-ink-mid">
            Period average <span class="text-ink-hi font-medium">{aggregate.nova_average.toFixed(2)}</span>
          </p>
        {/if}
      </div>
      <div class="mt-5">
        <NovaTrendChart buckets={novaTrend} />
      </div>
    </section>

    <section class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <div class="space-y-6">
        {#if macros.length > 0}
          <div class="rounded-lg bg-surface-1 p-6">
            <p class="text-xs uppercase tracking-wider text-ink-mid mb-4">Macros</p>
            {#each macros as m}
              <NutrientBar name={m.name} data={m.data} />
            {/each}
          </div>
        {/if}

        {#if micros.length > 0}
          <div class="rounded-lg bg-surface-1 p-6">
            <p class="text-xs uppercase tracking-wider text-ink-mid mb-4">Micronutrients</p>
            {#each micros as m}
              <NutrientBar name={m.name} data={m.data} />
            {/each}
          </div>
        {/if}
      </div>

      <div class="rounded-lg bg-surface-1 p-6">
        <p class="text-xs uppercase tracking-wider text-ink-mid mb-4">
          Meals ({logs.length})
        </p>
        {#if logs.length === 0}
          <p class="text-ink-mid text-sm">Nothing logged in this period.</p>
        {:else}
          <div class="space-y-5">
            {#each groupedLogs as group}
              <div>
                <p class="text-[11px] uppercase tracking-wider text-ink-mid mb-2">
                  {group.label}
                </p>
                <div class="space-y-2">
                  {#each group.entries as log}
                    {@const food = foods.get(log.food_client_uuid)}
                    {@const novaClass = food?.nova_class ?? 3}
                    <div
                      class="flex items-center gap-3 rounded-md p-3 group"
                      style="background-color: {novaTint(novaClass)};"
                    >
                      <a
                        href={food ? `/foods/${food.client_uuid}` : "#"}
                        class="flex items-center gap-3 flex-1 min-w-0 hover:opacity-90"
                      >
                        {#if food?.image_url}
                          <img
                            src={food.image_url}
                            alt=""
                            class="w-12 h-12 rounded-sm object-cover bg-surface-2"
                            loading="lazy"
                          />
                        {:else}
                          <div class="w-12 h-12 rounded-sm bg-surface-2 flex items-center justify-center text-ink-lo text-xs">
                            —
                          </div>
                        {/if}
                        <div class="flex-1 min-w-0">
                          <p class="text-ink-hi font-medium truncate">
                            {food?.name ?? "Unknown food"}
                          </p>
                          <p class="text-xs text-ink-mid">
                            {timeLabel(log.eaten_at)} ·
                            {log.grams_eaten != null
                              ? `${Math.round(log.grams_eaten)} g`
                              : `${log.percentage_eaten}%`}
                            · {(log.kcal_consumed_snapshot ?? 0).toFixed(0)} kcal
                          </p>
                        </div>
                      </a>
                      <NovaPill {novaClass} />
                      <button
                        type="button"
                        onclick={() => deleteLog(log.client_uuid)}
                        class="opacity-0 group-hover:opacity-100 transition-opacity text-ink-lo hover:text-nova-4 p-1"
                        title="Delete this entry"
                        aria-label="Delete this entry"
                      >
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                          stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"
                          class="w-4 h-4">
                          <path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6h14z"/>
                          <path d="M10 11v6M14 11v6"/>
                        </svg>
                      </button>
                    </div>
                  {/each}
                </div>
              </div>
            {/each}
          </div>
        {/if}
      </div>
    </section>
  {/if}
</div>
