<script lang="ts">
  import { onMount } from "svelte";
  import { api, ApiError } from "$lib/api/client";
  import type { AggregateResponse, ConsumptionLogDto, FoodEntryDto } from "$lib/api/types";
  import NovaPill from "$lib/components/NovaPill.svelte";
  import NutrientBar from "$lib/components/NutrientBar.svelte";
  import CalorieChart from "$lib/components/CalorieChart.svelte";
  import DateRangePicker, { type DateRange } from "$lib/components/DateRangePicker.svelte";
  import FastingStatus from "$lib/components/FastingStatus.svelte";

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

  async function load() {
    loading = true;
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
      loading = false;
    }
  }

  onMount(load);

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

  // Bucket logs by day for the calorie chart.
  const calorieBuckets = $derived.by(() => {
    const days: Map<string, { label: string; kcal: number; iso: string }> = new Map();
    const dayMs = 24 * 60 * 60 * 1000;

    // Pre-fill empty days so we get a continuous bar chart.
    const start = new Date(range.from);
    start.setHours(0, 0, 0, 0);
    const end = new Date(range.to);
    end.setHours(0, 0, 0, 0);
    const dayCount = Math.max(1, Math.round((end.getTime() - start.getTime()) / dayMs) + 1);

    for (let i = 0; i < dayCount; i++) {
      const d = new Date(start.getTime() + i * dayMs);
      const iso = d.toISOString().slice(0, 10);
      const label = dayCount <= 1
        ? d.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })
        : d.toLocaleDateString(undefined, { day: "numeric", month: "short" });
      days.set(iso, { label, kcal: 0, iso });
    }
    for (const log of logs) {
      const iso = log.eaten_at.slice(0, 10);
      const bucket = days.get(iso);
      if (bucket) bucket.kcal += log.kcal_consumed_snapshot ?? 0;
    }
    return Array.from(days.values());
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
      else label = new Date(log.eaten_at).toLocaleDateString(undefined, {
        weekday: "short", day: "numeric", month: "short"
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

  const novaPercents = $derived.by(() => {
    if (!aggregate) return [];
    const total = Object.values(aggregate.nova_breakdown).reduce((sum, b) => sum + b.calories, 0);
    return [1, 2, 3, 4].map((cls) => {
      const bucket = aggregate!.nova_breakdown[cls.toString()] ?? { meals: 0, calories: 0 };
      return {
        cls,
        meals: bucket.meals,
        calories: bucket.calories,
        pct: total > 0 ? (bucket.calories / total) * 100 : 0
      };
    });
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
    <section class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <div class="rounded-lg bg-surface-1 p-6 col-span-2">
        <p class="text-xs uppercase tracking-wider text-ink-mid mb-2">Calories</p>
        <div class="flex items-baseline gap-3">
          <span class="font-display text-6xl">{aggregate.calories_consumed.toFixed(0)}</span>
          <span class="text-ink-mid">
            kcal of {aggregate.calorie_reference.toFixed(0)} target
            {#if range.preset !== "today" && range.preset !== "yesterday"}
              <span class="text-ink-lo">(period total)</span>
            {/if}
          </span>
        </div>
        <p class="mt-2 text-sm text-ink-mid">
          {aggregate.meal_count} meal{aggregate.meal_count === 1 ? "" : "s"}
          · NOVA average {aggregate.nova_average?.toFixed(2) ?? "n/a"}
        </p>
        <div class="mt-5">
          <CalorieChart buckets={calorieBuckets} target={aggregate.calorie_reference} />
        </div>
      </div>

      <div class="rounded-lg bg-surface-1 p-6">
        <p class="text-xs uppercase tracking-wider text-ink-mid mb-3">NOVA breakdown</p>
        {#each novaPercents as bucket}
          <div class="flex items-center justify-between py-1">
            <NovaPill novaClass={bucket.cls} />
            <span class="text-sm tabular-nums text-ink-mid">
              {bucket.calories.toFixed(0)} kcal
              <span class="text-ink-lo">({bucket.pct.toFixed(0)}%)</span>
            </span>
          </div>
        {/each}
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
                          {timeLabel(log.eaten_at)} · {log.percentage_eaten}% ·
                          {(log.kcal_consumed_snapshot ?? 0).toFixed(0)} kcal
                        </p>
                      </div>
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
