<script lang="ts">
  import { onMount } from "svelte";
  import { api, getToken } from "$lib/api/client";
  import type { ConsumptionLogDto, FoodEntryDto } from "$lib/api/types";
  import NovaPill from "$lib/components/NovaPill.svelte";

  let logs = $state<ConsumptionLogDto[]>([]);
  let foods = $state<Map<string, FoodEntryDto>>(new Map());
  let loading = $state(true);
  let error = $state<string | null>(null);

  function dayLabel(iso: string): string {
    const d = new Date(iso);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    const dayStart = new Date(d);
    dayStart.setHours(0, 0, 0, 0);
    if (dayStart.getTime() === today.getTime()) return "Today";
    if (dayStart.getTime() === yesterday.getTime()) return "Yesterday";
    return d.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" });
  }

  function timeLabel(iso: string): string {
    return new Date(iso).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
  }

  const novaTint = (cls: number) => {
    const map: Record<number, string> = { 1: "#5BC97D", 2: "#C7C354", 3: "#E8A04A", 4: "#D8543E" };
    return `color-mix(in srgb, ${map[cls] ?? "#888"} 14%, transparent)`;
  };

  onMount(async () => {
    if (!getToken()) {
      error = "Not configured. Add backend URL and token in Settings.";
      loading = false;
      return;
    }
    try {
      const [logRes, foodRes] = await Promise.all([
        api.consumption(),
        api.recentFoods(500)
      ]);
      logs = logRes;
      foods = new Map(foodRes.map((f) => [f.client_uuid, f]));
    } catch (e) {
      error = (e as Error).message;
    } finally {
      loading = false;
    }
  });

  const grouped = $derived.by(() => {
    const groups: { label: string; entries: ConsumptionLogDto[] }[] = [];
    for (const log of logs) {
      const label = dayLabel(log.eaten_at);
      const last = groups[groups.length - 1];
      if (last && last.label === label) last.entries.push(log);
      else groups.push({ label, entries: [log] });
    }
    return groups;
  });
</script>

<svelte:head><title>History · Ultraprocessed</title></svelte:head>

{#if loading}
  <div class="text-ink-mid">Loading...</div>
{:else if error}
  <div class="rounded-md bg-surface-1 border border-surface-3 p-6 text-ink-mid">{error}</div>
{:else if logs.length === 0}
  <div class="text-ink-mid">No meals logged yet.</div>
{:else}
  <div class="space-y-8">
    {#each grouped as group}
      {@const totalKcal = group.entries.reduce((sum, l) => sum + (l.kcal_consumed_snapshot ?? 0), 0)}
      <section>
        <div class="flex items-center justify-between text-xs uppercase tracking-wider text-ink-mid mb-3">
          <span>{group.label}</span>
          <span>{group.entries.length} meals · {totalKcal.toFixed(0)} kcal</span>
        </div>
        <div class="space-y-2">
          {#each group.entries as log}
            {@const food = foods.get(log.food_client_uuid)}
            {@const novaClass = food?.nova_class ?? 3}
            <div
              class="flex items-center gap-4 rounded-md p-4"
              style="background-color: {novaTint(novaClass)};"
            >
              <div class="flex-1 min-w-0">
                <p class="font-medium text-ink-hi truncate">{food?.name ?? "Unknown food"}</p>
                <p class="text-xs text-ink-mid">
                  {timeLabel(log.eaten_at)} · {log.percentage_eaten}% · {(log.kcal_consumed_snapshot ?? 0).toFixed(0)} kcal
                </p>
              </div>
              <NovaPill {novaClass} />
            </div>
          {/each}
        </div>
      </section>
    {/each}
  </div>
{/if}
