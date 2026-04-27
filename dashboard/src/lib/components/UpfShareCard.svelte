<script lang="ts">
  import type { NovaBucket } from "$lib/api/types";

  let {
    novaBreakdown,
    totalKcal
  }: { novaBreakdown: Record<string, NovaBucket>; totalKcal: number } = $props();

  const upfKcal = $derived(novaBreakdown["4"]?.calories ?? 0);
  const wholeKcal = $derived(novaBreakdown["1"]?.calories ?? 0);
  const upfPct = $derived(totalKcal > 0 ? (upfKcal / totalKcal) * 100 : null);
  const wholePct = $derived(totalKcal > 0 ? (wholeKcal / totalKcal) * 100 : null);

  function band(pct: number): { color: string; verdict: string } {
    if (pct < 10) return { color: "#5BC97D", verdict: "Hardly any UPF." };
    if (pct < 25) return { color: "#C7C354", verdict: "Low ultra-processed." };
    if (pct < 50) return { color: "#E8A04A", verdict: "Moderate UPF share." };
    if (pct < 70) return { color: "#D8543E", verdict: "High ultra-processed." };
    return { color: "#D8543E", verdict: "Mostly ultra-processed." };
  }

  const status = $derived(upfPct != null ? band(upfPct) : null);
</script>

<div class="rounded-lg bg-surface-1 p-6 flex flex-col h-full">
  <p class="text-xs uppercase tracking-wider text-ink-mid">Ultra-processed share</p>

  {#if upfPct == null}
    <div class="flex-1 flex items-center text-ink-mid text-sm mt-3">
      Nothing tracked in this period.
    </div>
  {:else}
    <div class="flex items-baseline gap-2 mt-2">
      <span class="font-display text-7xl leading-none" style="color: {status?.color};">
        {upfPct.toFixed(0)}%
      </span>
      <span class="text-ink-lo text-sm">of calories</span>
    </div>

    {#if status}
      <p class="text-base mt-2" style="color: {status.color};">{status.verdict}</p>
    {/if}

    <!-- Stacked horizontal bar showing all four NOVA tiers' calorie share -->
    <div class="mt-4 flex h-3 rounded-xs overflow-hidden bg-surface-3">
      {#each [1, 2, 3, 4] as cls}
        {@const cKcal = novaBreakdown[cls.toString()]?.calories ?? 0}
        {@const pct = totalKcal > 0 ? (cKcal / totalKcal) * 100 : 0}
        {@const color = ["#5BC97D", "#C7C354", "#E8A04A", "#D8543E"][cls - 1]}
        {#if pct > 0}
          <div style="width: {pct}%; background-color: {color};" title="NOVA {cls}: {pct.toFixed(0)}%"></div>
        {/if}
      {/each}
    </div>

    <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs mt-4">
      <div class="flex items-center gap-2">
        <div class="w-2 h-2 rounded-full" style="background-color: #5BC97D;"></div>
        <span class="text-ink-mid">Whole</span>
        <span class="text-ink-hi tabular-nums ml-auto">
          {wholePct != null ? `${wholePct.toFixed(0)}%` : "—"}
        </span>
      </div>
      <div class="flex items-center gap-2">
        <div class="w-2 h-2 rounded-full" style="background-color: #D8543E;"></div>
        <span class="text-ink-mid">Ultra-processed</span>
        <span class="text-ink-hi tabular-nums ml-auto">{upfPct.toFixed(0)}%</span>
      </div>
    </div>

    <p class="text-xs text-ink-lo mt-4">
      {Math.round(upfKcal)} kcal of {Math.round(totalKcal)} from NOVA 4 in this period.
    </p>
  {/if}
</div>
