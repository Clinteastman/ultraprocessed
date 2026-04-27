<script lang="ts">
  import { onMount } from "svelte";
  import { goto } from "$app/navigation";
  import { page } from "$app/stores";
  import { api } from "$lib/api/client";
  import type { ConsumptionLogDto, FoodEntryDto } from "$lib/api/types";
  import NovaPill from "$lib/components/NovaPill.svelte";

  let food = $state<FoodEntryDto | null>(null);
  let logs = $state<ConsumptionLogDto[]>([]);
  let error = $state<string | null>(null);
  let loading = $state(true);

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

  const UNITS: Record<string, string> = {
    protein_g: "g", fat_g: "g", saturated_fat_g: "g", carbs_g: "g",
    sugar_g: "g", fiber_g: "g", salt_g: "g", omega3_g: "g",
    sodium_mg: "mg", cholesterol_mg: "mg", calcium_mg: "mg", iron_mg: "mg",
    potassium_mg: "mg", magnesium_mg: "mg", zinc_mg: "mg", phosphorus_mg: "mg",
    copper_mg: "mg", manganese_mg: "mg", vitamin_c_mg: "mg", vitamin_e_mg: "mg",
    vitamin_b1_mg: "mg", vitamin_b2_mg: "mg", vitamin_b3_mg: "mg", vitamin_b6_mg: "mg",
    selenium_ug: "μg", iodine_ug: "μg", vitamin_a_ug: "μg", vitamin_d_ug: "μg",
    vitamin_k_ug: "μg", vitamin_b12_ug: "μg", folate_ug: "μg"
  };

  let uuid = $derived($page.params.uuid);

  async function load() {
    loading = true;
    error = null;
    try {
      food = await api.getFood(uuid);
      // Pull all consumption then filter client-side; backend doesn't expose
      // a per-food consumption endpoint yet.
      const all = await api.consumption(undefined, undefined, 5000);
      logs = all.filter((l) => l.food_client_uuid === uuid);
    } catch (e) {
      error = (e as Error).message;
    } finally {
      loading = false;
    }
  }

  onMount(load);

  async function deleteFood() {
    if (!food) return;
    if (!confirm(`Delete "${food.name}" and all ${logs.length} consumption ${logs.length === 1 ? "entry" : "entries"} of it?`)) return;
    try {
      await api.deleteFood(food.client_uuid);
      goto("/");
    } catch (e) {
      error = (e as Error).message;
    }
  }

  async function deleteLog(clientUuid: string) {
    if (!confirm("Delete this entry?")) return;
    try {
      await api.deleteConsumption(clientUuid);
      logs = logs.filter((l) => l.client_uuid !== clientUuid);
    } catch (e) {
      error = (e as Error).message;
    }
  }

  const ingredients = $derived.by(() => {
    if (!food) return [] as string[];
    try {
      const parsed = JSON.parse(food.ingredients_json);
      return Array.isArray(parsed) ? (parsed as string[]) : [];
    } catch {
      return [];
    }
  });

  const nutrients = $derived.by(() => {
    if (!food?.nutrients_json) return [] as { key: string; label: string; amount: number; unit: string }[];
    let parsed: Record<string, number | null> = {};
    try {
      parsed = JSON.parse(food.nutrients_json) as Record<string, number | null>;
    } catch {
      return [];
    }
    return Object.entries(parsed)
      .filter(([, v]) => typeof v === "number" && (v as number) > 0)
      .map(([key, value]) => ({
        key,
        label: NUTRIENT_LABELS[key] ?? key,
        amount: value as number,
        unit: UNITS[key] ?? ""
      }));
  });

  function timeLabel(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleString(undefined, {
      weekday: "short",
      day: "numeric",
      month: "short",
      hour: "2-digit",
      minute: "2-digit"
    });
  }

  const novaTint = (cls: number) => {
    const map: Record<number, string> = { 1: "#5BC97D", 2: "#C7C354", 3: "#E8A04A", 4: "#D8543E" };
    return `color-mix(in srgb, ${map[cls] ?? "#888"} 14%, transparent)`;
  };
</script>

<svelte:head><title>{food?.name ?? "Food"} · Ultraprocessed</title></svelte:head>

<a href="/" class="text-sm text-ink-mid hover:text-ink-hi inline-flex items-center gap-2 mb-4">
  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"
    stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
  Back to intake
</a>

{#if loading}
  <div class="text-ink-mid">Loading...</div>
{:else if error}
  <div class="rounded-md bg-surface-1 border border-surface-3 p-6 text-ink-mid">{error}</div>
{:else if food}
  <div class="space-y-8">
    <!-- Header -->
    <header class="grid grid-cols-1 md:grid-cols-[160px_1fr] gap-6 items-start">
      <div class="rounded-md bg-surface-1 overflow-hidden aspect-square flex items-center justify-center">
        {#if food.image_url}
          <img src={food.image_url} alt="" class="w-full h-full object-cover" />
        {:else}
          <span class="text-ink-lo text-xs">no image</span>
        {/if}
      </div>
      <div class="space-y-2">
        {#if food.brand}
          <p class="text-xs uppercase tracking-wider text-ink-mid">{food.brand}</p>
        {/if}
        <h2 class="font-display text-4xl text-ink-hi">{food.name}</h2>
        <div class="flex flex-wrap items-center gap-3">
          <NovaPill novaClass={food.nova_class} />
          {#if food.barcode}
            <span class="text-xs text-ink-lo font-mono">barcode {food.barcode}</span>
          {/if}
          <span class="text-xs text-ink-lo uppercase tracking-wider">via {food.source}</span>
          {#if food.confidence < 0.9}
            <span class="text-xs text-nova-3">confidence {Math.round(food.confidence * 100)}%</span>
          {/if}
        </div>
      </div>
    </header>

    <!-- Why this NOVA score -->
    <section class="rounded-lg p-6" style="background-color: {novaTint(food.nova_class)};">
      <p class="text-xs uppercase tracking-wider text-ink-mid mb-2">
        Why NOVA {food.nova_class}
      </p>
      <p class="text-ink-hi">{food.nova_rationale}</p>
    </section>

    <!-- Ingredients -->
    {#if ingredients.length > 0}
      <section class="rounded-lg bg-surface-1 p-6 space-y-3">
        <p class="text-xs uppercase tracking-wider text-ink-mid">Ingredients</p>
        <p class="text-sm text-ink-hi leading-relaxed">{ingredients.join(", ")}</p>
      </section>
    {:else}
      <section class="rounded-lg bg-surface-1 p-6 text-sm text-ink-mid">
        No ingredient list captured. Open Food Facts didn't have one for this barcode and the analyzer didn't identify any.
      </section>
    {/if}

    <!-- Nutrients per 100g -->
    {#if nutrients.length > 0}
      <section class="rounded-lg bg-surface-1 p-6 space-y-3">
        <p class="text-xs uppercase tracking-wider text-ink-mid">Per 100g</p>
        {#if food.kcal_per_100g}
          <p class="font-display text-3xl text-ink-hi">{food.kcal_per_100g.toFixed(0)}<span class="text-base text-ink-mid font-sans"> kcal</span></p>
        {/if}
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-1">
          {#each nutrients as n}
            <div class="flex items-center justify-between py-1 border-b border-surface-2 last:border-0">
              <span class="text-sm text-ink-mid">{n.label}</span>
              <span class="text-sm tabular-nums text-ink-hi">{n.amount.toFixed(n.amount < 10 ? 1 : 0)} {n.unit}</span>
            </div>
          {/each}
        </div>
      </section>
    {/if}

    <!-- Consumption history for this food -->
    <section class="rounded-lg bg-surface-1 p-6 space-y-3">
      <p class="text-xs uppercase tracking-wider text-ink-mid">
        You've eaten this {logs.length} {logs.length === 1 ? "time" : "times"}
      </p>
      {#if logs.length === 0}
        <p class="text-ink-mid text-sm">No consumption entries.</p>
      {:else}
        <div class="space-y-2">
          {#each logs as log}
            <div class="flex items-center justify-between gap-3 rounded-md bg-surface-2 px-3 py-2 group">
              <div>
                <p class="text-sm text-ink-hi">{timeLabel(log.eaten_at)}</p>
                <p class="text-xs text-ink-mid">{log.percentage_eaten}% · {(log.kcal_consumed_snapshot ?? 0).toFixed(0)} kcal</p>
              </div>
              <button
                type="button"
                onclick={() => deleteLog(log.client_uuid)}
                class="opacity-0 group-hover:opacity-100 transition-opacity text-ink-lo hover:text-nova-4 p-1"
                title="Delete"
                aria-label="Delete"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                  stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="w-4 h-4">
                  <path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6h14z"/>
                  <path d="M10 11v6M14 11v6"/>
                </svg>
              </button>
            </div>
          {/each}
        </div>
      {/if}
    </section>

    <!-- Danger zone -->
    <section class="rounded-lg border border-nova-4/30 p-6 space-y-3">
      <p class="text-xs uppercase tracking-wider text-nova-4">Danger zone</p>
      <p class="text-sm text-ink-mid">
        Deleting the food removes it and all {logs.length} consumption {logs.length === 1 ? "entry" : "entries"} above. Aggregations and charts will recompute.
      </p>
      <button
        type="button"
        onclick={deleteFood}
        class="rounded-md bg-nova-4/15 text-nova-4 hover:bg-nova-4/25 px-4 py-2 text-sm font-medium transition-colors"
      >
        Delete this food
      </button>
    </section>
  </div>
{/if}
