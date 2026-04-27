<script lang="ts">
  import { onMount, onDestroy } from "svelte";

  type Profile = {
    id: number | null;
    name: string;
    schedule_type: string;
    eating_window_start_minutes: number;
    eating_window_end_minutes: number;
    restricted_days_mask: number;
    restricted_kcal_target: number | null;
    active: boolean;
  };

  let profile = $state<Profile | null>(null);
  let loaded = $state(false);
  let now = $state<Date>(new Date());
  let tick: ReturnType<typeof setInterval> | undefined;

  async function load() {
    try {
      const resp = await fetch("/api/v1/fasting/profile", {
        headers: { Authorization: `Bearer ${localStorage.getItem("ultraprocessed.token") ?? ""}` }
      });
      if (resp.ok) profile = (await resp.json()) as Profile;
    } catch {
      /* best effort */
    } finally {
      loaded = true;
    }
  }

  function timeOf(minutes: number): string {
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

  /** Mon=0..Sun=6, matching restricted_days_mask bit positions. */
  function dowMon0(d: Date): number {
    return (d.getDay() + 6) % 7;
  }

  function nextRestrictedDay(mask: number, from: Date): Date | null {
    if (mask === 0) return null;
    for (let i = 1; i <= 7; i++) {
      const cand = new Date(from);
      cand.setDate(cand.getDate() + i);
      cand.setHours(0, 0, 0, 0);
      if (mask & (1 << dowMon0(cand))) return cand;
    }
    return null;
  }

  function endOfDay(d: Date): Date {
    const x = new Date(d);
    x.setHours(23, 59, 59, 999);
    return x;
  }

  const TRE = new Set(["SIXTEEN_EIGHT", "EIGHTEEN_SIX", "TWENTY_FOUR", "OMAD", "CUSTOM"]);

  onMount(() => {
    load();
    tick = setInterval(() => (now = new Date()), 30_000);
  });
  onDestroy(() => {
    if (tick) clearInterval(tick);
  });

  const display = $derived.by(() => {
    if (!profile || !profile.active) return null;

    if (TRE.has(profile.schedule_type)) {
      const start = profile.eating_window_start_minutes;
      const end = profile.eating_window_end_minutes;
      const minutesNow = now.getHours() * 60 + now.getMinutes();
      const inWindow = minutesNow >= start && minutesNow <= end;
      if (inWindow) {
        const closeAt = new Date(now);
        closeAt.setHours(0, 0, 0, 0);
        closeAt.setMinutes(end);
        return {
          title: "Eating window open",
          sub: `Closes at ${timeOf(end)} · ${diffParts(closeAt.getTime(), now.getTime())} left`,
          accent: "#5BC97D"
        };
      }
      const nextOpen = new Date(now);
      nextOpen.setHours(0, 0, 0, 0);
      nextOpen.setMinutes(start);
      if (minutesNow > end) nextOpen.setDate(nextOpen.getDate() + 1);
      return {
        title: "Fasting",
        sub: `Eat next at ${timeOf(start)} · ${diffParts(nextOpen.getTime(), now.getTime())} to go`,
        accent: "#E8A04A"
      };
    }

    // Weekly restricted patterns: 5:2, 4:3, ADF.
    const mask = profile.restricted_days_mask ?? 0;
    const todayBit = 1 << dowMon0(now);
    const todayRestricted = (mask & todayBit) !== 0;
    if (todayRestricted) {
      const cap = profile.restricted_kcal_target ?? 500;
      const reset = endOfDay(now);
      return {
        title: `Restricted day · ${cap} kcal cap`,
        sub: `Resets at midnight · ${diffParts(reset.getTime(), now.getTime())} to go`,
        accent: "#E8A04A"
      };
    }
    const next = nextRestrictedDay(mask, now);
    if (!next) {
      return {
        title: "Active fasting plan",
        sub: "No restricted days configured.",
        accent: "#6E6C66"
      };
    }
    const dayLabel = next.toLocaleDateString(undefined, { weekday: "long" });
    return {
      title: "Normal eating today",
      sub: `Next restricted day: ${dayLabel} · ${diffParts(next.getTime(), now.getTime())}`,
      accent: "#5BC97D"
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
    <a href="/settings" class="text-xs text-ink-lo hover:text-ink-hi">Edit</a>
  </div>
{:else if loaded}
  <a
    href="/settings"
    class="block rounded-lg p-4 bg-surface-1 hover:bg-surface-2 transition-colors text-sm text-ink-mid"
  >
    No fasting plan set. <span class="text-accent">Set one →</span>
  </a>
{/if}
