<script lang="ts">
  // 7-button row for the Bristol Stool Scale. Each button has an icon
  // hint and a short label so users learn the scale over time.
  let { value = $bindable<number | null>(null) }: { value?: number | null } = $props();

  const ENTRIES: { v: number; label: string; tip: string; tone: string }[] = [
    { v: 1, label: "1", tip: "Hard pellets",      tone: "bg-nova-4/30 text-ink-hi" },
    { v: 2, label: "2", tip: "Lumpy sausage",     tone: "bg-nova-3/40 text-ink-hi" },
    { v: 3, label: "3", tip: "Cracked sausage",   tone: "bg-nova-2/40 text-ink-hi" },
    { v: 4, label: "4", tip: "Smooth sausage",    tone: "bg-nova-1/40 text-ink-hi" },
    { v: 5, label: "5", tip: "Soft blobs",        tone: "bg-nova-2/40 text-ink-hi" },
    { v: 6, label: "6", tip: "Mushy",             tone: "bg-nova-3/40 text-ink-hi" },
    { v: 7, label: "7", tip: "Liquid",            tone: "bg-nova-4/30 text-ink-hi" }
  ];
</script>

<div class="flex flex-col gap-2">
  <div class="flex items-center justify-between">
    <p class="text-xs uppercase tracking-wider text-ink-mid">Bristol stool scale</p>
    {#if value != null}
      <p class="text-xs text-ink-mid">
        {ENTRIES[value - 1]?.tip}
      </p>
    {/if}
  </div>
  <div class="grid grid-cols-7 gap-1.5">
    {#each ENTRIES as e}
      <button
        type="button"
        onclick={() => (value = e.v)}
        class="aspect-square rounded-md text-sm font-medium transition-all
               {value === e.v ? 'ring-2 ring-ink-hi scale-105' : 'hover:scale-105'}
               {e.tone}"
        title={e.tip}
        aria-label="Bristol {e.v}: {e.tip}"
      >
        {e.label}
      </button>
    {/each}
  </div>
</div>
