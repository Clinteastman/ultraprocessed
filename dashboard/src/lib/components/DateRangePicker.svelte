<script lang="ts">
  export type RangePreset = "today" | "yesterday" | "last7" | "last30" | "custom";
  export interface DateRange {
    preset: RangePreset;
    from: Date;
    to: Date;
    label: string;
  }

  let {
    value = $bindable<DateRange>(),
    onChange = (_: DateRange) => {}
  }: {
    value: DateRange;
    onChange?: (r: DateRange) => void;
  } = $props();

  const presets: { id: RangePreset; label: string }[] = [
    { id: "today", label: "Today" },
    { id: "yesterday", label: "Yesterday" },
    { id: "last7", label: "Last 7 days" },
    { id: "last30", label: "Last 30 days" },
    { id: "custom", label: "Custom" }
  ];

  function setPreset(id: RangePreset) {
    if (id === "custom") {
      value = { ...value, preset: "custom" };
      onChange(value);
      return;
    }
    value = computeRange(id);
    onChange(value);
  }

  export function computeRange(id: RangePreset, customFrom?: Date, customTo?: Date): DateRange {
    const now = new Date();
    const startOfDay = (d: Date) => {
      const x = new Date(d);
      x.setHours(0, 0, 0, 0);
      return x;
    };
    const endOfDay = (d: Date) => {
      const x = new Date(d);
      x.setHours(23, 59, 59, 999);
      return x;
    };

    switch (id) {
      case "today": {
        return { preset: id, from: startOfDay(now), to: endOfDay(now), label: "Today" };
      }
      case "yesterday": {
        const y = new Date(now);
        y.setDate(y.getDate() - 1);
        return { preset: id, from: startOfDay(y), to: endOfDay(y), label: "Yesterday" };
      }
      case "last7": {
        const start = new Date(now);
        start.setDate(start.getDate() - 6);
        return { preset: id, from: startOfDay(start), to: endOfDay(now), label: "Last 7 days" };
      }
      case "last30": {
        const start = new Date(now);
        start.setDate(start.getDate() - 29);
        return { preset: id, from: startOfDay(start), to: endOfDay(now), label: "Last 30 days" };
      }
      case "custom": {
        const from = startOfDay(customFrom ?? now);
        const to = endOfDay(customTo ?? now);
        return { preset: id, from, to, label: `${fmt(from)} – ${fmt(to)}` };
      }
    }
  }

  function fmt(d: Date): string {
    return d.toLocaleDateString(undefined, { day: "numeric", month: "short" });
  }

  function toInputDate(d: Date): string {
    return d.toISOString().slice(0, 10);
  }

  function onCustomChange(which: "from" | "to", iso: string) {
    if (!iso) return;
    const [y, m, d] = iso.split("-").map(Number);
    const next = new Date(value[which]);
    next.setFullYear(y, m - 1, d);
    if (which === "from") next.setHours(0, 0, 0, 0);
    else next.setHours(23, 59, 59, 999);
    value = computeRange("custom", which === "from" ? next : value.from, which === "to" ? next : value.to);
    onChange(value);
  }
</script>

<div class="flex flex-wrap items-center gap-2">
  {#each presets as p}
    <button
      type="button"
      onclick={() => setPreset(p.id)}
      class="px-3 py-1.5 rounded-sm text-sm transition-colors {value.preset === p.id
        ? 'bg-accent text-ink-inv font-medium'
        : 'bg-surface-1 text-ink-mid hover:text-ink-hi'}"
    >
      {p.label}
    </button>
  {/each}
  {#if value.preset === "custom"}
    <input
      type="date"
      value={toInputDate(value.from)}
      onchange={(e) => onCustomChange("from", e.currentTarget.value)}
      class="bg-surface-1 text-ink-hi text-sm rounded-sm px-2 py-1 border border-surface-3"
    />
    <span class="text-ink-mid text-sm">to</span>
    <input
      type="date"
      value={toInputDate(value.to)}
      onchange={(e) => onCustomChange("to", e.currentTarget.value)}
      class="bg-surface-1 text-ink-hi text-sm rounded-sm px-2 py-1 border border-surface-3"
    />
  {/if}
</div>
