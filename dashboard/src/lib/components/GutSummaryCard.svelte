<script lang="ts">
  import { onMount } from "svelte";
  import type { HaSnapshot } from "$lib/api/types";
  import { api } from "$lib/api/client";

  // A compact "how was your gut today?" card for the main dashboard.
  // Pulls from the HA snapshot endpoint so we don't double up queries.

  let snapshot = $state<HaSnapshot | null>(null);

  async function load() {
    try {
      snapshot = await api.haSnapshot();
    } catch {
      /* card just hides if backend isn't reachable */
    }
  }
  onMount(() => {
    load();
    const t = setInterval(load, 60_000);
    return () => clearInterval(t);
  });

  function describeBowel(snap: HaSnapshot): string {
    if (snap.bowel_count_today === 0) return "No visits yet today";
    const avg = snap.bristol_average_today;
    const flares = snap.bowel_flares_today;
    const parts = [`${snap.bowel_count_today} visit${snap.bowel_count_today === 1 ? "" : "s"}`];
    if (avg != null) parts.push(`avg Bristol ${avg.toFixed(1)}`);
    if (flares > 0) parts.push(`${flares} flare${flares === 1 ? "" : "s"}`);
    return parts.join(" · ");
  }

  function describeSymptoms(snap: HaSnapshot): string {
    if (snap.symptom_count_today === 0 && snap.bloating_today === 0 && snap.heartburn_today === 0) {
      return "Nothing flagged";
    }
    const parts: string[] = [];
    if (snap.bloating_today > 0) parts.push(`bloating ${snap.bloating_today}/3`);
    if (snap.heartburn_today > 0) parts.push(`heartburn ${snap.heartburn_today}/3`);
    if (snap.symptom_count_today > 0) parts.push(`${snap.symptom_count_today} event${snap.symptom_count_today === 1 ? "" : "s"}`);
    return parts.join(" · ");
  }

  function gutTone(score: number | null): string {
    if (score == null) return "#6E6C66";
    if (score <= 3) return "#D8543E";
    if (score <= 6) return "#E8A04A";
    return "#5BC97D";
  }
</script>

{#if snapshot}
  <a href="/ibs" class="block rounded-lg bg-surface-1 p-6 hover:bg-surface-2 transition-colors">
    <div class="flex items-baseline justify-between mb-2">
      <p class="text-xs uppercase tracking-wider text-ink-mid">Gut today</p>
      <p class="text-[11px] text-ink-lo">→ tracker</p>
    </div>

    <div class="flex items-baseline gap-3">
      {#if snapshot.gut_score_today != null}
        <span class="font-display text-5xl leading-none" style="color: {gutTone(snapshot.gut_score_today)};">
          {snapshot.gut_score_today}
        </span>
        <span class="text-ink-lo text-sm">/ 10</span>
      {:else}
        <span class="font-display text-3xl text-ink-mid leading-none">—</span>
        <span class="text-ink-lo text-sm">no check-in yet</span>
      {/if}
    </div>

    <div class="mt-4 grid grid-cols-2 gap-3 text-sm">
      <div>
        <p class="text-[10px] uppercase tracking-wider text-ink-lo">Bowel</p>
        <p class="text-ink-hi mt-1">{describeBowel(snapshot)}</p>
      </div>
      <div>
        <p class="text-[10px] uppercase tracking-wider text-ink-lo">Symptoms</p>
        <p class="text-ink-hi mt-1">{describeSymptoms(snapshot)}</p>
      </div>
    </div>

    {#if snapshot.minutes_since_last_bowel != null && snapshot.minutes_since_last_bowel > 60 * 36}
      <p class="text-xs mt-3 text-nova-3">
        {Math.round(snapshot.minutes_since_last_bowel / 60)} hours since last bowel — watch for constipation.
      </p>
    {/if}
  </a>
{/if}
