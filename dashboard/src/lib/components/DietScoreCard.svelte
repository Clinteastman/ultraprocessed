<script lang="ts">
  let {
    novaAverage,
    mealCount
  }: { novaAverage: number | null; mealCount: number } = $props();

  const NOVA_COLORS = ["#5BC97D", "#5BC97D", "#C7C354", "#E8A04A", "#D8543E"];

  function colorFor(avg: number): string {
    if (avg < 1.5) return NOVA_COLORS[1];
    if (avg < 2.5) return NOVA_COLORS[2];
    if (avg < 3.5) return NOVA_COLORS[3];
    return NOVA_COLORS[4];
  }

  function verdictFor(avg: number): string {
    if (avg < 1.4) return "Mostly whole foods.";
    if (avg < 1.8) return "Lots of whole foods.";
    if (avg < 2.4) return "Some processing.";
    if (avg < 2.8) return "Mainly processed.";
    if (avg < 3.4) return "Heavy on processed.";
    if (avg < 3.7) return "Mostly ultra-processed.";
    return "Ultra-processed dominated.";
  }

  // Marker position on a 1-4 scale, normalised 0-100 for CSS.
  function markerPercent(avg: number): number {
    const clamped = Math.max(1, Math.min(4, avg));
    return ((clamped - 1) / 3) * 100;
  }

  const accent = $derived(novaAverage != null ? colorFor(novaAverage) : "#6E6C66");
  const verdict = $derived(novaAverage != null ? verdictFor(novaAverage) : "");
  const markerPct = $derived(novaAverage != null ? markerPercent(novaAverage) : 0);
</script>

<div class="rounded-lg bg-surface-1 p-6 flex flex-col h-full">
  <p class="text-xs uppercase tracking-wider text-ink-mid">Diet NOVA score</p>

  {#if novaAverage == null || mealCount === 0}
    <div class="flex-1 flex items-center text-ink-mid text-sm mt-3">
      Nothing tracked in this period.
    </div>
  {:else}
    <div class="flex items-baseline gap-2 mt-2">
      <span class="font-display text-7xl leading-none" style="color: {accent};">
        {novaAverage.toFixed(1)}
      </span>
      <span class="text-ink-lo text-sm">/ 4</span>
    </div>

    <p class="text-base mt-2" style="color: {accent};">{verdict}</p>

    <!-- 1-4 visual scale with marker -->
    <div class="mt-4">
      <div class="relative h-2 rounded-xs"
        style="background: linear-gradient(to right, #5BC97D 0%, #C7C354 33%, #E8A04A 66%, #D8543E 100%);">
        <div
          class="absolute top-1/2 -translate-y-1/2 w-3 h-3 rounded-full bg-ink-hi border-2"
          style="left: calc({markerPct}% - 6px); border-color: {accent};"
        ></div>
      </div>
      <div class="flex justify-between text-[10px] uppercase tracking-wider text-ink-lo mt-1">
        <span>1 · whole</span>
        <span>2 · ingredient</span>
        <span>3 · processed</span>
        <span>4 · ultra</span>
      </div>
    </div>

    <p class="text-xs text-ink-lo mt-4">
      Weighted by calories across {mealCount} item{mealCount === 1 ? "" : "s"}.
    </p>
  {/if}
</div>
