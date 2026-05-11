<script lang="ts">
  import type {
    BowelEventDto,
    ConsumptionLogDto,
    FoodEntryDto,
    SymptomEventDto
  } from "$lib/api/types";

  // Three-row 24h timeline: meals on top (NOVA-colored, FODMAP-haloed),
  // symptoms in the middle, bowel events on the bottom. Lets you see at
  // a glance which meals preceded which flares.

  let {
    logs,
    foods,
    symptoms,
    bowels,
    fromMs,
    toMs
  }: {
    logs: ConsumptionLogDto[];
    foods: Map<string, FoodEntryDto>;
    symptoms: SymptomEventDto[];
    bowels: BowelEventDto[];
    fromMs: number;
    toMs: number;
  } = $props();

  const NOVA_COLOR: Record<number, string> = {
    1: "#5BC97D",
    2: "#C7C354",
    3: "#E8A04A",
    4: "#D8543E"
  };
  const FODMAP_RING: Record<string, string> = {
    HIGH: "#D8543E",
    MODERATE: "#E8A04A",
    LOW: "#5BC97D",
    UNKNOWN: "transparent"
  };

  const dayStartMs = $derived.by(() => {
    const d = new Date(fromMs);
    d.setHours(0, 0, 0, 0);
    return d.getTime();
  });
  const dayEndMs = $derived(dayStartMs + 24 * 60 * 60 * 1000);
  const singleDay = $derived(toMs - fromMs <= 24 * 60 * 60 * 1000 + 1000);

  function pct(ms: number): number {
    return ((ms - dayStartMs) / (dayEndMs - dayStartMs)) * 100;
  }
  function inDay(ms: number): boolean {
    return ms >= dayStartMs && ms <= dayEndMs;
  }

  const mealMarkers = $derived.by(() =>
    logs
      .map((log) => {
        const ms = new Date(log.eaten_at).getTime();
        if (!inDay(ms)) return null;
        const food = foods.get(log.food_client_uuid);
        const nova = food?.nova_class ?? 3;
        const fodmap = food?.fodmap_level ?? "UNKNOWN";
        return {
          pct: pct(ms),
          color: NOVA_COLOR[nova],
          ring: FODMAP_RING[fodmap],
          name: food?.name ?? "Unknown",
          fodmap
        };
      })
      .filter((m): m is NonNullable<typeof m> => m !== null)
  );

  const symptomMarkers = $derived.by(() =>
    symptoms
      .map((s) => {
        const ms = new Date(s.occurred_at).getTime();
        if (!inDay(ms)) return null;
        const intensity = 0.4 + (s.severity / 3) * 0.6; // 0.4..1.0 opacity
        return {
          pct: pct(ms),
          kind: s.kind,
          severity: s.severity,
          intensity,
          label: `${s.kind.toLowerCase()} (${s.severity}/3)`
        };
      })
      .filter((m): m is NonNullable<typeof m> => m !== null)
  );

  const bowelMarkers = $derived.by(() =>
    bowels
      .map((b) => {
        const ms = new Date(b.occurred_at).getTime();
        if (!inDay(ms)) return null;
        // Color the dot by Bristol distance from "normal" (4).
        const distance = Math.abs(4 - b.bristol);
        const color = distance >= 2 ? "#D8543E" : distance >= 1 ? "#E8A04A" : "#5BC97D";
        return {
          pct: pct(ms),
          color,
          label: `Bristol ${b.bristol}${b.urgency >= 2 ? " · urgent" : ""}`,
          flare: b.bristol in { 1: 1, 2: 1, 6: 1, 7: 1 } || b.urgency >= 2 || b.pain >= 2
        };
      })
      .filter((m): m is NonNullable<typeof m> => m !== null)
  );

  let now = $state<Date>(new Date());
  $effect(() => {
    const t = setInterval(() => (now = new Date()), 60_000);
    return () => clearInterval(t);
  });
  const nowPct = $derived.by(() => {
    const t = now.getTime();
    if (t < dayStartMs || t > dayEndMs) return null;
    return pct(t);
  });
</script>

{#if singleDay}
  <section class="rounded-lg bg-surface-1 p-6">
    <div class="flex justify-between items-baseline mb-3">
      <p class="text-xs uppercase tracking-wider text-ink-mid">Gut timeline</p>
      <p class="text-[10px] text-ink-lo">
        {mealMarkers.length} meals · {symptomMarkers.length} symptoms · {bowelMarkers.length} bowel
      </p>
    </div>

    <div class="space-y-2">
      <!-- Meals row -->
      <div class="relative h-7 rounded-sm bg-surface-2 overflow-hidden">
        <div class="absolute left-2 top-1.5 text-[10px] uppercase text-ink-lo">Meals</div>
        {#each mealMarkers as m}
          <div
            class="absolute top-1/2 -translate-x-1/2 -translate-y-1/2 w-2.5 h-2.5 rounded-full"
            style="left: {m.pct}%; background-color: {m.color}; box-shadow: 0 0 0 2px {m.ring};"
            title="{m.name} · FODMAP {m.fodmap.toLowerCase()}"
          ></div>
        {/each}
        {#if nowPct != null}
          <div class="absolute top-0 bottom-0 w-0.5 bg-ink-hi" style="left: calc({nowPct}% - 1px);"></div>
        {/if}
      </div>

      <!-- Symptoms row -->
      <div class="relative h-7 rounded-sm bg-surface-2 overflow-hidden">
        <div class="absolute left-2 top-1.5 text-[10px] uppercase text-ink-lo">Symptoms</div>
        {#each symptomMarkers as s}
          <div
            class="absolute top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-sm"
            style="left: {s.pct}%; width: {6 + s.severity * 2}px; height: {6 + s.severity * 2}px; background-color: rgb(216 84 62 / {s.intensity});"
            title={s.label}
          ></div>
        {/each}
        {#if nowPct != null}
          <div class="absolute top-0 bottom-0 w-0.5 bg-ink-hi" style="left: calc({nowPct}% - 1px);"></div>
        {/if}
      </div>

      <!-- Bowel row -->
      <div class="relative h-7 rounded-sm bg-surface-2 overflow-hidden">
        <div class="absolute left-2 top-1.5 text-[10px] uppercase text-ink-lo">Bowel</div>
        {#each bowelMarkers as b}
          <div
            class="absolute top-1/2 -translate-x-1/2 -translate-y-1/2 w-2.5 h-2.5 rotate-45"
            style="left: {b.pct}%; background-color: {b.color}; {b.flare ? 'box-shadow: 0 0 0 2px #D8543E;' : ''}"
            title={b.label}
          ></div>
        {/each}
        {#if nowPct != null}
          <div class="absolute top-0 bottom-0 w-0.5 bg-ink-hi" style="left: calc({nowPct}% - 1px);"></div>
        {/if}
      </div>
    </div>

    <div class="flex justify-between text-[10px] uppercase tracking-wider text-ink-lo mt-2">
      <span>00:00</span>
      <span>06:00</span>
      <span>12:00</span>
      <span>18:00</span>
      <span>23:59</span>
    </div>
  </section>
{/if}
