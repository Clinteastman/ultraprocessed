<script lang="ts">
  import { onMount } from "svelte";
  import { api, ApiError } from "$lib/api/client";
  import type { AggregateResponse } from "$lib/api/types";
  import NovaPill from "$lib/components/NovaPill.svelte";
  import NutrientBar from "$lib/components/NutrientBar.svelte";

  let data = $state<AggregateResponse | null>(null);
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

  onMount(async () => {
    try {
      data = await api.today();
    } catch (e) {
      error = e instanceof ApiError ? e.message : (e as Error).message;
    } finally {
      loading = false;
    }
  });

  const novaPercents = $derived.by(() => {
    if (!data) return [];
    const total = Object.values(data.nova_breakdown).reduce((sum, b) => sum + b.calories, 0);
    return [1, 2, 3, 4].map((cls) => {
      const bucket = data!.nova_breakdown[cls.toString()] ?? { meals: 0, calories: 0 };
      return {
        cls,
        meals: bucket.meals,
        calories: bucket.calories,
        pct: total > 0 ? (bucket.calories / total) * 100 : 0
      };
    });
  });

  const macros = $derived.by(() => {
    if (!data) return [];
    return ["protein_g", "fat_g", "saturated_fat_g", "carbs_g", "sugar_g", "fiber_g", "salt_g"]
      .filter((k) => data!.nutrients_adequacy[k])
      .map((k) => ({ name: NUTRIENT_LABELS[k] ?? k, key: k, data: data!.nutrients_adequacy[k] }));
  });

  const micros = $derived.by(() => {
    if (!data) return [];
    return Object.keys(data.nutrients_adequacy)
      .filter((k) => !["protein_g", "fat_g", "saturated_fat_g", "carbs_g", "sugar_g", "fiber_g", "salt_g"].includes(k))
      .map((k) => ({ name: NUTRIENT_LABELS[k] ?? k, key: k, data: data!.nutrients_adequacy[k] }));
  });
</script>

<svelte:head>
  <title>Today · Ultraprocessed</title>
</svelte:head>

{#if loading}
  <div class="text-ink-mid">Loading...</div>
{:else if error}
  <div class="rounded-md bg-surface-1 border border-surface-3 p-6 text-ink-mid">
    {error}
  </div>
{:else if data}
  <section class="grid grid-cols-1 md:grid-cols-3 gap-6">
    <div class="rounded-lg bg-surface-1 p-6 col-span-2">
      <p class="text-xs uppercase tracking-wider text-ink-mid mb-2">Today</p>
      <div class="flex items-baseline gap-3">
        <span class="font-display text-6xl">{data.calories_consumed.toFixed(0)}</span>
        <span class="text-ink-mid">kcal of {data.calorie_reference.toFixed(0)} target</span>
      </div>
      <p class="mt-2 text-sm text-ink-mid">
        {data.meal_count} meal{data.meal_count === 1 ? "" : "s"}
        · NOVA average {data.nova_average?.toFixed(2) ?? "n/a"}
      </p>
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

  {#if macros.length > 0}
    <section class="mt-8 rounded-lg bg-surface-1 p-6">
      <p class="text-xs uppercase tracking-wider text-ink-mid mb-4">Macros</p>
      {#each macros as m}
        <NutrientBar name={m.name} data={m.data} />
      {/each}
    </section>
  {/if}

  {#if micros.length > 0}
    <section class="mt-8 rounded-lg bg-surface-1 p-6">
      <p class="text-xs uppercase tracking-wider text-ink-mid mb-4">Micronutrients</p>
      {#each micros as m}
        <NutrientBar name={m.name} data={m.data} />
      {/each}
    </section>
  {/if}
{/if}
