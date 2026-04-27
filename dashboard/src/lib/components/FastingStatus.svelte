<script lang="ts">
  import { onMount, onDestroy } from "svelte";

  type FastingState = {
    currently_fasting: boolean;
    next_eat_at: string | null;
    last_meal_at: string | null;
    profile: {
      name: string;
      eating_window_start_minutes: number;
      eating_window_end_minutes: number;
    } | null;
  };

  let state = $state<FastingState | null>(null);
  let loaded = $state(false);
  let now = $state<Date>(new Date());
  let tick: ReturnType<typeof setInterval> | undefined;

  async function load() {
    try {
      const resp = await fetch("/api/v1/fasting/state", {
        headers: { Authorization: `Bearer ${localStorage.getItem("ultraprocessed.token") ?? ""}` }
      });
      if (resp.ok) state = (await resp.json()) as FastingState;
    } catch {
      // best effort
    } finally {
      loaded = true;
    }
  }

  function timeOfDay(minutes: number): string {
    const h = Math.floor(minutes / 60).toString().padStart(2, "0");
    const m = (minutes % 60).toString().padStart(2, "0");
    return `${h}:${m}`;
  }

  function diffParts(toMs: number, fromMs: number): string {
    const diff = Math.max(0, toMs - fromMs);
    const totalMin = Math.floor(diff / 60000);
    const h = Math.floor(totalMin / 60);
    const m = totalMin % 60;
    if (h === 0) return `${m}m`;
    return `${h}h ${m.toString().padStart(2, "0")}m`;
  }

  onMount(() => {
    load();
    tick = setInterval(() => (now = new Date()), 30_000);
  });
  onDestroy(() => {
    if (tick) clearInterval(tick);
  });

  const display = $derived.by(() => {
    if (!state || !state.profile) {
      return null;
    }
    const start = state.profile.eating_window_start_minutes;
    const end = state.profile.eating_window_end_minutes;
    const minutesNow = now.getHours() * 60 + now.getMinutes();
    const inWindow = minutesNow >= start && minutesNow <= end;

    if (inWindow) {
      // Eating window open: countdown to window close.
      const closeAt = new Date(now);
      closeAt.setHours(0, 0, 0, 0);
      closeAt.setMinutes(end);
      return {
        kind: "eating" as const,
        title: "Eating window open",
        sub: `Closes at ${timeOfDay(end)} · ${diffParts(closeAt.getTime(), now.getTime())} left`,
        accent: "#5BC97D"
      };
    }
    // Fasting: countdown to next opening.
    let nextOpen: Date;
    if (state.next_eat_at) {
      nextOpen = new Date(state.next_eat_at);
    } else {
      nextOpen = new Date(now);
      nextOpen.setHours(0, 0, 0, 0);
      nextOpen.setMinutes(start);
      if (minutesNow > end) nextOpen.setDate(nextOpen.getDate() + 1);
    }
    return {
      kind: "fasting" as const,
      title: "Fasting",
      sub: `Eat next at ${timeOfDay(start)} · ${diffParts(nextOpen.getTime(), now.getTime())} to go`,
      accent: "#E8A04A"
    };
  });
</script>

{#if loaded && display}
  <div
    class="rounded-lg p-4 flex items-center gap-4"
    style="background-color: color-mix(in srgb, {display.accent} 14%, transparent);"
  >
    <div class="w-2 h-2 rounded-full" style="background-color: {display.accent};"></div>
    <div class="flex-1 min-w-0">
      <p class="text-sm font-medium" style="color: {display.accent};">{display.title}</p>
      <p class="text-sm text-ink-mid">{display.sub}</p>
    </div>
    <a href="/targets" class="text-xs text-ink-lo hover:text-ink-hi">Edit</a>
  </div>
{:else if loaded}
  <a
    href="/targets"
    class="block rounded-lg p-4 bg-surface-1 hover:bg-surface-2 transition-colors text-sm text-ink-mid"
  >
    No fasting window set. <span class="text-accent">Set one →</span>
  </a>
{/if}
