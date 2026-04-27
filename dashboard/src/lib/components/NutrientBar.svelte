<script lang="ts">
  import type { NutrientAdequacyDto } from "../api/types";

  let { name, data }: { name: string; data: NutrientAdequacyDto } = $props();

  const directionColor = $derived.by(() => {
    if (data.direction === "low") return "#E8A04A";
    if (data.direction === "high") return "#D8543E";
    return "#5BC97D";
  });

  const widthPct = $derived(Math.min(100, Math.round(data.pct * 100)));
</script>

<div class="flex items-center gap-3 py-1">
  <div class="w-32 truncate text-sm text-ink-mid">{name}</div>
  <div class="flex-1 h-2 rounded-xs bg-surface-3 overflow-hidden">
    <div class="h-full rounded-xs" style="width: {widthPct}%; background-color: {directionColor};"></div>
  </div>
  <div class="w-24 text-right text-sm tabular-nums text-ink-hi">
    {data.consumed.toFixed(1)} <span class="text-ink-lo">/ {data.reference}</span>
  </div>
</div>
