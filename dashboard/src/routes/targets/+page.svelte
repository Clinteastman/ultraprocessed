<script lang="ts">
  import { onMount } from "svelte";
  import { api } from "$lib/api/client";

  type FastingProfile = {
    id: number | null;
    name: string;
    schedule_type: string;
    eating_window_start_minutes: number;
    eating_window_end_minutes: number;
    active: boolean;
  };

  let calorieTarget = $state<number>(2000);
  let calorieDirty = $state(false);
  let calorieSaving = $state(false);
  let calorieError = $state<string | null>(null);
  let calorieSaved = $state(false);

  let fasting = $state<FastingProfile>({
    id: null,
    name: "16:8",
    schedule_type: "SIXTEEN_EIGHT",
    eating_window_start_minutes: 12 * 60,
    eating_window_end_minutes: 20 * 60,
    active: true
  });
  let fastingDirty = $state(false);
  let fastingSaving = $state(false);
  let fastingError = $state<string | null>(null);
  let fastingSaved = $state(false);
  let fastingLoaded = $state(false);

  const SCHEDULES = [
    { id: "SIXTEEN_EIGHT", label: "16:8", start: 12, end: 20 },
    { id: "EIGHTEEN_SIX", label: "18:6", start: 13, end: 19 },
    { id: "TWENTY_FOUR", label: "20:4", start: 14, end: 18 },
    { id: "OMAD", label: "OMAD", start: 17, end: 18 },
    { id: "CUSTOM", label: "Custom", start: 12, end: 20 }
  ];

  onMount(async () => {
    try {
      const t = await api.getTargets();
      calorieTarget = t.calorie_target_kcal;
    } catch (e) {
      calorieError = (e as Error).message;
    }
    try {
      const f = await api.getFastingProfile();
      if (f) {
        fasting = f as FastingProfile;
      }
    } catch {
      // 200/null is fine; first-time users haven't set one.
    } finally {
      fastingLoaded = true;
    }
  });

  function setSchedule(id: string) {
    const sched = SCHEDULES.find((s) => s.id === id) ?? SCHEDULES[0];
    fasting = {
      ...fasting,
      schedule_type: sched.id,
      name: sched.label,
      eating_window_start_minutes: sched.start * 60,
      eating_window_end_minutes: sched.end * 60
    };
    fastingDirty = true;
    fastingSaved = false;
  }

  function setEdge(which: "start" | "end", value: string) {
    if (!value) return;
    const [h, m] = value.split(":").map(Number);
    const minutes = h * 60 + (m ?? 0);
    fasting = {
      ...fasting,
      [which === "start" ? "eating_window_start_minutes" : "eating_window_end_minutes"]: minutes,
      schedule_type: "CUSTOM",
      name: fasting.name === "Custom" ? fasting.name : "Custom"
    };
    fastingDirty = true;
    fastingSaved = false;
  }

  function toTime(minutes: number): string {
    const h = Math.floor(minutes / 60).toString().padStart(2, "0");
    const m = (minutes % 60).toString().padStart(2, "0");
    return `${h}:${m}`;
  }

  async function saveCalorie() {
    calorieSaving = true;
    calorieError = null;
    calorieSaved = false;
    try {
      const r = await api.putTargets({ calorie_target_kcal: calorieTarget });
      calorieTarget = r.calorie_target_kcal;
      calorieDirty = false;
      calorieSaved = true;
      setTimeout(() => (calorieSaved = false), 2500);
    } catch (e) {
      calorieError = (e as Error).message;
    } finally {
      calorieSaving = false;
    }
  }

  async function saveFasting() {
    fastingSaving = true;
    fastingError = null;
    fastingSaved = false;
    try {
      await api.putFastingProfile({
        name: fasting.name,
        schedule_type: fasting.schedule_type,
        eating_window_start_minutes: fasting.eating_window_start_minutes,
        eating_window_end_minutes: fasting.eating_window_end_minutes,
        active: fasting.active
      });
      fastingDirty = false;
      fastingSaved = true;
      setTimeout(() => (fastingSaved = false), 2500);
    } catch (e) {
      fastingError = (e as Error).message;
    } finally {
      fastingSaving = false;
    }
  }
</script>

<svelte:head><title>Targets · Ultraprocessed</title></svelte:head>

<div class="space-y-10 max-w-2xl">
  <div>
    <h2 class="font-display text-3xl text-ink-hi">Targets</h2>
    <p class="text-ink-mid text-sm mt-2">
      Daily calorie target plus your fasting window. Used by the dashboard's "vs target" line and (later) by Home Assistant sensors.
    </p>
  </div>

  <section class="rounded-lg bg-surface-1 p-6 space-y-4">
    <p class="text-xs uppercase tracking-wider text-ink-mid">Calorie target</p>
    <div class="flex items-end gap-3">
      <input
        type="number"
        min="500"
        max="10000"
        step="50"
        value={calorieTarget}
        oninput={(e) => {
          calorieTarget = Number(e.currentTarget.value);
          calorieDirty = true;
          calorieSaved = false;
        }}
        class="font-display text-4xl bg-transparent text-ink-hi w-40 focus:outline-none border-b border-surface-3 focus:border-accent"
      />
      <span class="text-ink-mid pb-2">kcal / day</span>
    </div>
    <p class="text-sm text-ink-lo">
      EU NRV reference is 2,000 kcal for an average adult. Tune it to your own basal + activity level.
    </p>
    {#if calorieError}<p class="text-sm text-nova-4">{calorieError}</p>{/if}
    <div class="flex items-center gap-3">
      <button
        type="button"
        onclick={saveCalorie}
        disabled={!calorieDirty || calorieSaving}
        class="rounded-md bg-accent text-ink-inv font-semibold px-4 py-2 hover:bg-accent-press transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {calorieSaving ? "Saving..." : "Save"}
      </button>
      {#if calorieSaved}<span class="text-sm text-nova-1">Saved.</span>{/if}
    </div>
  </section>

  <section class="rounded-lg bg-surface-1 p-6 space-y-4">
    <p class="text-xs uppercase tracking-wider text-ink-mid">Fasting window</p>
    <div class="flex flex-wrap gap-2">
      {#each SCHEDULES as s}
        <button
          type="button"
          onclick={() => setSchedule(s.id)}
          class="px-3 py-1.5 rounded-sm text-sm transition-colors {fasting.schedule_type === s.id
            ? 'bg-accent text-ink-inv font-medium'
            : 'bg-surface-2 text-ink-mid hover:text-ink-hi'}"
        >
          {s.label}
        </button>
      {/each}
    </div>
    <div class="flex flex-wrap items-center gap-4 mt-2">
      <label class="flex items-center gap-2">
        <span class="text-sm text-ink-mid">Eat from</span>
        <input
          type="time"
          value={toTime(fasting.eating_window_start_minutes)}
          onchange={(e) => setEdge("start", e.currentTarget.value)}
          class="bg-surface-2 text-ink-hi text-sm rounded-sm px-2 py-1 border border-surface-3"
        />
      </label>
      <label class="flex items-center gap-2">
        <span class="text-sm text-ink-mid">to</span>
        <input
          type="time"
          value={toTime(fasting.eating_window_end_minutes)}
          onchange={(e) => setEdge("end", e.currentTarget.value)}
          class="bg-surface-2 text-ink-hi text-sm rounded-sm px-2 py-1 border border-surface-3"
        />
      </label>
    </div>
    <label class="flex items-center gap-2 mt-2">
      <input
        type="checkbox"
        checked={fasting.active}
        onchange={(e) => {
          fasting = { ...fasting, active: e.currentTarget.checked };
          fastingDirty = true;
          fastingSaved = false;
        }}
        class="accent-accent"
      />
      <span class="text-sm text-ink-mid">Active (drives "next eat at" + Home Assistant binary sensor)</span>
    </label>
    {#if fastingError}<p class="text-sm text-nova-4">{fastingError}</p>{/if}
    <div class="flex items-center gap-3">
      <button
        type="button"
        onclick={saveFasting}
        disabled={!fastingDirty || fastingSaving || !fastingLoaded}
        class="rounded-md bg-accent text-ink-inv font-semibold px-4 py-2 hover:bg-accent-press transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {fastingSaving ? "Saving..." : "Save"}
      </button>
      {#if fastingSaved}<span class="text-sm text-nova-1">Saved.</span>{/if}
    </div>
  </section>
</div>
