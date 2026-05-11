<script lang="ts">
  // 4-button row for severity 0..3 (none/mild/moderate/severe).
  // Designed for one-tap logging.
  let {
    value = $bindable<number>(0),
    label = "Severity",
    showNone = true
  }: { value?: number; label?: string; showNone?: boolean } = $props();

  const ENTRIES: { v: number; label: string; tone: string }[] = [
    { v: 0, label: "None",     tone: "bg-surface-2 text-ink-mid" },
    { v: 1, label: "Mild",     tone: "bg-nova-1/40 text-ink-hi" },
    { v: 2, label: "Moderate", tone: "bg-nova-3/40 text-ink-hi" },
    { v: 3, label: "Severe",   tone: "bg-nova-4/40 text-ink-hi" }
  ];
</script>

<div class="flex flex-col gap-2">
  <p class="text-xs uppercase tracking-wider text-ink-mid">{label}</p>
  <div class="grid grid-cols-4 gap-1.5">
    {#each ENTRIES as e}
      {#if showNone || e.v > 0}
        <button
          type="button"
          onclick={() => (value = e.v)}
          class="rounded-md py-2 text-sm font-medium transition-all
                 {value === e.v ? 'ring-2 ring-ink-hi' : ''}
                 {e.tone}"
        >
          {e.label}
        </button>
      {/if}
    {/each}
  </div>
</div>
