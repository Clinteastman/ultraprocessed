<script lang="ts">
  import { onMount, onDestroy } from "svelte";
  import type { ConsumptionLogDto, FoodEntryDto } from "$lib/api/types";

  // 24h horizontal pattern view of *today*. Shows the eating window (for
  // TRE schedules) shaded; meal markers as small dots colored by NOVA;
  // a vertical "now" line so progress through the day is at a glance.
  // Hidden when the selected range isn't a single day.

  type Profile = {
    schedule_type: string;
    eating_window_start_minutes: number;
    eating_window_end_minutes: number;
    active: boolean;
  };

  let {
    logs,
    foods,
    fromMs,
    toMs
  }: {
    logs: ConsumptionLogDto[];
    foods: Map<string, FoodEntryDto>;
    fromMs: number;
    toMs: number;
  } = $props();

  let fasting = $state<Profile | null>(null);
  let now = $state<Date>(new Date());
  let tick: ReturnType<typeof setInterval> | undefined;

  async function loadFasting() {
    try {
      const resp = await fetch("/api/v1/fasting/profile", {
        headers: { Authorization: `Bearer ${localStorage.getItem("ultraprocessed.token") ?? ""}` }
      });
      if (resp.ok) fasting = (await resp.json()) as Profile;
    } catch {
      /* best effort - timeline still draws without the band */
    }
  }

  onMount(() => {
    loadFasting();
    tick = setInterval(() => (now = new Date()), 60_000);
  });
  onDestroy(() => {
    if (tick) clearInterval(tick);
  });

  const TRE = new Set(["SIXTEEN_EIGHT", "EIGHTEEN_SIX", "TWENTY_FOUR", "OMAD", "CUSTOM"]);
  const NOVA_COLOR: Record<number, string> = {
    1: "#5BC97D",
    2: "#C7C354",
    3: "#E8A04A",
    4: "#D8543E"
  };

  // Use the *selected day's* midnight as the baseline. fromMs is start of
  // day for "today" / "yesterday"; we don't render for multi-day ranges.
  const dayStartMs = $derived.by(() => {
    const d = new Date(fromMs);
    d.setHours(0, 0, 0, 0);
    return d.getTime();
  });
  const dayEndMs = $derived(dayStartMs + 24 * 60 * 60 * 1000);

  const singleDay = $derived(toMs - fromMs <= 24 * 60 * 60 * 1000 + 1000);

  const eatingBand = $derived.by(() => {
    if (!fasting || !fasting.active) return null;
    if (!TRE.has(fasting.schedule_type)) return null;
    const s = fasting.eating_window_start_minutes;
    const e = fasting.eating_window_end_minutes;
    if (e <= s) return null;
    return { startPct: (s / 1440) * 100, endPct: (e / 1440) * 100 };
  });

  const markers = $derived.by(() => {
    return logs
      .map((log) => {
        const ms = new Date(log.eaten_at).getTime();
        if (ms < dayStartMs || ms > dayEndMs) return null;
        const food = foods.get(log.food_client_uuid);
        const nova = food?.nova_class ?? 3;
        const pct = ((ms - dayStartMs) / (dayEndMs - dayStartMs)) * 100;
        return { pct, color: NOVA_COLOR[nova], name: food?.name ?? "Unknown" };
      })
      .filter((m): m is { pct: number; color: string; name: string } => m !== null);
  });

  const nowPct = $derived.by(() => {
    const t = now.getTime();
    if (t < dayStartMs || t > dayEndMs) return null;
    return ((t - dayStartMs) / (dayEndMs - dayStartMs)) * 100;
  });
</script>

{#if singleDay}
  <section class="rounded-lg bg-surface-1 p-6">
    <p class="text-xs uppercase tracking-wider text-ink-mid mb-3">Today's pattern</p>
    <div class="relative h-7 rounded-sm bg-surface-2 overflow-hidden">
      {#if eatingBand}
        <div
          class="absolute top-1 bottom-1 rounded-xs"
          style="left: {eatingBand.startPct}%; width: {eatingBand.endPct - eatingBand.startPct}%; background-color: color-mix(in srgb, #5BC97D 22%, transparent);"
        ></div>
      {/if}
      {#each [6, 12, 18] as h}
        <div
          class="absolute bottom-0 h-1 w-px bg-ink-lo opacity-40"
          style="left: {(h * 60 / 1440) * 100}%;"
        ></div>
      {/each}
      {#each markers as m}
        <div
          class="absolute top-1/2 -translate-x-1/2 -translate-y-1/2 w-2.5 h-2.5 rounded-full ring-1 ring-black/30"
          style="left: {m.pct}%; background-color: {m.color};"
          title={m.name}
        ></div>
      {/each}
      {#if nowPct != null}
        <div
          class="absolute top-0.5 bottom-0.5 w-0.5 bg-ink-hi"
          style="left: calc({nowPct}% - 1px);"
        ></div>
      {/if}
    </div>
    <div class="flex justify-between text-[10px] uppercase tracking-wider text-ink-lo mt-1">
      <span>00:00</span>
      <span>12:00</span>
      <span>23:59</span>
    </div>
  </section>
{/if}
