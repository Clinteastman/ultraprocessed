<script lang="ts">
  import type { FastingProfileDto } from "$lib/api/client";

  let {
    value = $bindable<FastingProfileDto>(),
    onChange = () => {}
  }: {
    value: FastingProfileDto;
    onChange?: () => void;
  } = $props();

  type Family = "tre" | "weekly" | "adf";

  type Schedule = {
    id: FastingProfileDto["schedule_type"];
    label: string;
    family: Family;
    /** Defaults applied when this schedule is picked. */
    apply: (current: FastingProfileDto) => Partial<FastingProfileDto>;
    blurb: string;
  };

  const SCHEDULES: Schedule[] = [
    {
      id: "SIXTEEN_EIGHT",
      label: "16:8",
      family: "tre",
      blurb: "Eat in an 8-hour window each day. The most studied time-restricted pattern.",
      apply: () => ({
        eating_window_start_minutes: 12 * 60,
        eating_window_end_minutes: 20 * 60,
        restricted_days_mask: 0,
        restricted_kcal_target: null
      })
    },
    {
      id: "EIGHTEEN_SIX",
      label: "18:6",
      family: "tre",
      blurb: "6-hour eating window, 18 hours fasting.",
      apply: () => ({
        eating_window_start_minutes: 13 * 60,
        eating_window_end_minutes: 19 * 60,
        restricted_days_mask: 0,
        restricted_kcal_target: null
      })
    },
    {
      id: "TWENTY_FOUR",
      label: "20:4",
      family: "tre",
      blurb: "4-hour eating window (Warrior diet).",
      apply: () => ({
        eating_window_start_minutes: 14 * 60,
        eating_window_end_minutes: 18 * 60,
        restricted_days_mask: 0,
        restricted_kcal_target: null
      })
    },
    {
      id: "OMAD",
      label: "OMAD",
      family: "tre",
      blurb: "One meal a day; ~1 hour eating window.",
      apply: () => ({
        eating_window_start_minutes: 17 * 60,
        eating_window_end_minutes: 18 * 60,
        restricted_days_mask: 0,
        restricted_kcal_target: null
      })
    },
    {
      id: "FIVE_TWO",
      label: "5:2",
      family: "weekly",
      blurb:
        "5 normal-eating days + 2 restricted (~500 kcal). Recent work suggests scheduling the 2 restricted days back-to-back can improve insulin sensitivity vs spacing them out, with similar adherence.",
      apply: () => ({
        // Mon + Tue (consecutive, the back-to-back default)
        restricted_days_mask: 0b0000011,
        restricted_kcal_target: 500,
        eating_window_start_minutes: 0,
        eating_window_end_minutes: 1440
      })
    },
    {
      id: "FOUR_THREE",
      label: "4:3",
      family: "weekly",
      blurb: "4 normal + 3 restricted days. More aggressive variant of 5:2; same kcal cap on fast days.",
      apply: () => ({
        restricted_days_mask: 0b0010101, // Mon, Wed, Fri
        restricted_kcal_target: 500,
        eating_window_start_minutes: 0,
        eating_window_end_minutes: 1440
      })
    },
    {
      id: "ADF",
      label: "ADF",
      family: "adf",
      blurb: "Alternate-day fasting. Restricted (~500 kcal) every other day.",
      apply: () => ({
        restricted_days_mask: 0b0101010, // Tue/Thu/Sat
        restricted_kcal_target: 500,
        eating_window_start_minutes: 0,
        eating_window_end_minutes: 1440
      })
    },
    {
      id: "CUSTOM",
      label: "Custom",
      family: "tre",
      blurb: "Pick your own eating window or restricted-day mask.",
      apply: (cur) => ({
        eating_window_start_minutes: cur.eating_window_start_minutes || 720,
        eating_window_end_minutes: cur.eating_window_end_minutes || 1200
      })
    }
  ];

  const DAY_LABELS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

  function pickSchedule(id: FastingProfileDto["schedule_type"]) {
    const s = SCHEDULES.find((x) => x.id === id);
    if (!s) return;
    value = { ...value, schedule_type: id, name: s.label, ...s.apply(value) };
    onChange();
  }

  function setEdge(which: "start" | "end", time: string) {
    if (!time) return;
    const [h, m] = time.split(":").map(Number);
    const minutes = h * 60 + (m ?? 0);
    value = {
      ...value,
      [which === "start" ? "eating_window_start_minutes" : "eating_window_end_minutes"]: minutes
    };
    onChange();
  }

  function toggleDay(idx: number) {
    const next = value.restricted_days_mask ^ (1 << idx);
    value = { ...value, restricted_days_mask: next };
    onChange();
  }

  function setRestrictedKcal(value_: string) {
    const n = Number(value_);
    value = { ...value, restricted_kcal_target: Number.isFinite(n) && n > 0 ? n : null };
    onChange();
  }

  function setFullFast(checked: boolean) {
    // 0 = full fast (water only). Switch back to a sensible default kcal
    // cap when the toggle is turned off, rather than clearing it entirely.
    value = { ...value, restricted_kcal_target: checked ? 0 : 500 };
    onChange();
  }

  function timeOf(minutes: number): string {
    const h = Math.floor(minutes / 60).toString().padStart(2, "0");
    const m = (minutes % 60).toString().padStart(2, "0");
    return `${h}:${m}`;
  }

  const family = $derived(SCHEDULES.find((s) => s.id === value.schedule_type)?.family ?? "tre");
  const blurb = $derived(SCHEDULES.find((s) => s.id === value.schedule_type)?.blurb ?? "");
</script>

<div class="space-y-4">
  <div class="flex flex-wrap gap-2">
    {#each SCHEDULES as s}
      <button
        type="button"
        onclick={() => pickSchedule(s.id)}
        class="px-3 py-1.5 rounded-sm text-sm transition-colors {value.schedule_type === s.id
          ? 'bg-accent text-ink-inv font-medium'
          : 'bg-surface-2 text-ink-mid hover:text-ink-hi'}"
      >
        {s.label}
      </button>
    {/each}
  </div>

  {#if blurb}
    <p class="text-sm text-ink-mid">{blurb}</p>
  {/if}

  {#if family === "tre"}
    <div class="flex flex-wrap items-center gap-4">
      <label class="flex items-center gap-2">
        <span class="text-sm text-ink-mid">Eat from</span>
        <input
          type="time"
          value={timeOf(value.eating_window_start_minutes)}
          onchange={(e) => setEdge("start", e.currentTarget.value)}
          class="bg-surface-2 text-ink-hi text-sm rounded-sm px-2 py-1 border border-surface-3"
        />
      </label>
      <label class="flex items-center gap-2">
        <span class="text-sm text-ink-mid">to</span>
        <input
          type="time"
          value={timeOf(value.eating_window_end_minutes)}
          onchange={(e) => setEdge("end", e.currentTarget.value)}
          class="bg-surface-2 text-ink-hi text-sm rounded-sm px-2 py-1 border border-surface-3"
        />
      </label>
    </div>
  {:else}
    <div>
      <p class="text-xs uppercase tracking-wider text-ink-mid mb-2">Restricted days</p>
      <div class="flex flex-wrap gap-2">
        {#each DAY_LABELS as d, i}
          {@const on = (value.restricted_days_mask & (1 << i)) !== 0}
          <button
            type="button"
            onclick={() => toggleDay(i)}
            class="w-12 py-1.5 rounded-sm text-sm transition-colors {on
              ? 'bg-nova-3/30 text-ink-hi font-medium'
              : 'bg-surface-2 text-ink-mid hover:text-ink-hi'}"
          >
            {d}
          </button>
        {/each}
      </div>
      <p class="text-xs text-ink-lo mt-2">
        Tap to toggle. Back-to-back days (e.g. Mon + Tue) are slightly easier on insulin sensitivity than spaced ones in the recent literature, with similar adherence.
      </p>
    </div>

    <label class="flex items-center gap-2">
      <input
        type="checkbox"
        checked={value.restricted_kcal_target === 0}
        onchange={(e) => setFullFast(e.currentTarget.checked)}
        class="accent-accent"
      />
      <span class="text-sm text-ink-mid">
        Full fast (no calories) on restricted days. Overrides the kcal cap.
      </span>
    </label>

    {#if value.restricted_kcal_target !== 0}
      <label class="flex items-center gap-2">
        <span class="text-sm text-ink-mid w-40">Restricted-day kcal cap</span>
        <input
          type="number"
          min="0"
          step="50"
          value={value.restricted_kcal_target ?? ""}
          oninput={(e) => setRestrictedKcal(e.currentTarget.value)}
          class="bg-surface-2 text-ink-hi text-sm rounded-sm px-2 py-1 border border-surface-3 w-24"
          placeholder="500"
        />
        <span class="text-sm text-ink-lo">kcal</span>
      </label>
    {/if}

    <div>
      <p class="text-xs uppercase tracking-wider text-ink-mid mb-2">
        Daily eating window for normal (non-restricted) days
      </p>
      <p class="text-xs text-ink-lo mb-2">
        Set both edges to 00:00-23:59 to skip and just eat normally.
      </p>
      <div class="flex flex-wrap items-center gap-4">
        <label class="flex items-center gap-2">
          <span class="text-sm text-ink-mid">Eat from</span>
          <input
            type="time"
            value={timeOf(value.eating_window_start_minutes)}
            onchange={(e) => setEdge("start", e.currentTarget.value)}
            class="bg-surface-2 text-ink-hi text-sm rounded-sm px-2 py-1 border border-surface-3"
          />
        </label>
        <label class="flex items-center gap-2">
          <span class="text-sm text-ink-mid">to</span>
          <input
            type="time"
            value={timeOf(value.eating_window_end_minutes)}
            onchange={(e) => setEdge("end", e.currentTarget.value)}
            class="bg-surface-2 text-ink-hi text-sm rounded-sm px-2 py-1 border border-surface-3"
          />
        </label>
      </div>
    </div>
  {/if}

  <label class="flex items-center gap-2">
    <input
      type="checkbox"
      checked={value.active}
      onchange={(e) => {
        value = { ...value, active: e.currentTarget.checked };
        onChange();
      }}
      class="accent-accent"
    />
    <span class="text-sm text-ink-mid">Active (drives "next eat at" + Home Assistant binary sensor)</span>
  </label>
</div>
