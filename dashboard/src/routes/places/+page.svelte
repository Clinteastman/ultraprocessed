<script lang="ts">
  import { onMount, onDestroy } from "svelte";
  import { api, ApiError } from "$lib/api/client";
  import type { ConsumptionLogDto, FoodEntryDto } from "$lib/api/types";
  import DateRangePicker, { type DateRange } from "$lib/components/DateRangePicker.svelte";
  import NovaPill from "$lib/components/NovaPill.svelte";
  import type { Map as LMap, CircleMarker as LCircleMarker } from "leaflet";

  // "Places" - shows where you've eaten on an OpenStreetMap, plus a list
  // of items grouped by location label. Tapping a list row pans + zooms
  // the map to that item.

  let logs = $state<ConsumptionLogDto[]>([]);
  let foods = $state<Map<string, FoodEntryDto>>(new Map());
  let loading = $state(true);
  let error = $state<string | null>(null);
  let selectedUuid = $state<string | null>(null);

  let range = $state<DateRange>({
    preset: "last7",
    from: startOfDaysAgo(6),
    to: endOfToday(),
    label: "Last 7 days"
  });

  function startOfDaysAgo(n: number): Date {
    const d = new Date();
    d.setDate(d.getDate() - n);
    d.setHours(0, 0, 0, 0);
    return d;
  }
  function endOfToday(): Date {
    const d = new Date();
    d.setHours(23, 59, 59, 999);
    return d;
  }

  const NOVA_COLOR: Record<number, string> = {
    1: "#5BC97D",
    2: "#C7C354",
    3: "#E8A04A",
    4: "#D8543E"
  };

  let mapEl: HTMLDivElement | null = null;
  let map: LMap | null = null;
  let markersByUuid = new Map<string, LCircleMarker>();

  async function load(silent = false) {
    if (!silent) loading = true;
    error = null;
    try {
      const [logsRes, foodsRes] = await Promise.all([
        api.consumption(range.from.toISOString(), range.to.toISOString(), 5000),
        api.recentFoods(2000)
      ]);
      logs = logsRes;
      foods = new Map(foodsRes.map((f) => [f.client_uuid, f]));
    } catch (e) {
      error = e instanceof ApiError ? e.message : (e as Error).message;
    } finally {
      if (!silent) loading = false;
    }
  }

  const located = $derived(logs.filter((l) => l.lat != null && l.lng != null));

  type Group = {
    label: string;
    itemCount: number;
    totalKcal: number;
    novaAverage: number;
    upfShare: number;
  };

  const groups = $derived.by((): Group[] => {
    if (located.length === 0) return [];
    const buckets = new Map<string, ConsumptionLogDto[]>();
    for (const l of located) {
      const key =
        l.location_label && l.location_label.trim() !== ""
          ? l.location_label
          : `${l.lat!.toFixed(3)}, ${l.lng!.toFixed(3)}`;
      const arr = buckets.get(key);
      if (arr) arr.push(l);
      else buckets.set(key, [l]);
    }
    const out: Group[] = [];
    for (const [label, list] of buckets) {
      let totalKcal = 0;
      let weighted = 0;
      let nova4Cal = 0;
      for (const l of list) {
        const food = foods.get(l.food_client_uuid);
        const nova = food?.nova_class ?? 3;
        const kcal = l.kcal_consumed_snapshot ?? 0;
        totalKcal += kcal;
        weighted += kcal * nova;
        if (nova === 4) nova4Cal += kcal;
      }
      const novaAverage =
        totalKcal > 0
          ? weighted / totalKcal
          : list.reduce((s, l) => s + (foods.get(l.food_client_uuid)?.nova_class ?? 3), 0) / list.length;
      const upfShare = totalKcal > 0 ? Math.round((nova4Cal / totalKcal) * 100) : 0;
      out.push({
        label,
        itemCount: list.length,
        totalKcal,
        novaAverage,
        upfShare
      });
    }
    return out.sort((a, b) => b.itemCount - a.itemCount);
  });

  async function ensureMap() {
    if (!mapEl || map) return;
    const L = (await import("leaflet")).default;
    map = L.map(mapEl, { attributionControl: true });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "&copy; OpenStreetMap"
    }).addTo(map);
    map.setView([51.5, -0.12], 5);
  }

  async function refreshMarkers() {
    if (!map) return;
    const L = (await import("leaflet")).default;
    for (const m of markersByUuid.values()) m.remove();
    markersByUuid = new Map();
    if (located.length === 0) return;
    for (const log of located) {
      const food = foods.get(log.food_client_uuid);
      const nova = food?.nova_class ?? 3;
      const color = NOVA_COLOR[nova] ?? "#888";
      const marker = L.circleMarker([log.lat!, log.lng!], {
        radius: 7,
        fillColor: color,
        color: "rgba(0,0,0,0.4)",
        weight: 1.2,
        fillOpacity: 1
      }).addTo(map);
      const tip = `${food?.name ?? "Unknown"} · NOVA ${nova} · ${log.location_label ?? ""}`;
      marker.bindTooltip(tip, { direction: "top" });
      marker.on("click", () => (selectedUuid = log.client_uuid));
      markersByUuid.set(log.client_uuid, marker);
    }
    const lats = located.map((l) => l.lat!);
    const lngs = located.map((l) => l.lng!);
    const span = Math.max(
      Math.max(...lats) - Math.min(...lats),
      Math.max(...lngs) - Math.min(...lngs)
    );
    if (located.length === 1 || span < 0.0005) {
      const cLat = lats.reduce((a, b) => a + b, 0) / lats.length;
      const cLng = lngs.reduce((a, b) => a + b, 0) / lngs.length;
      map.setView([cLat, cLng], 16);
    } else {
      map.fitBounds(
        [
          [Math.min(...lats), Math.min(...lngs)],
          [Math.max(...lats), Math.max(...lngs)]
        ],
        { padding: [40, 40], maxZoom: 18 }
      );
    }
  }

  function focusItem(log: ConsumptionLogDto) {
    selectedUuid = log.client_uuid;
    if (!map || log.lat == null || log.lng == null) return;
    map.setView([log.lat, log.lng], 17, { animate: true });
  }

  onMount(async () => {
    await load();
    await ensureMap();
    await refreshMarkers();
  });
  onDestroy(() => {
    map?.remove();
    map = null;
  });

  $effect(() => {
    void located;
    refreshMarkers();
  });

  function timeLabel(iso: string): string {
    return new Date(iso).toLocaleString(undefined, {
      day: "numeric",
      month: "short",
      hour: "2-digit",
      minute: "2-digit"
    });
  }
</script>

<svelte:head>
  <title>Places · Ultraprocessed</title>
  <link
    rel="stylesheet"
    href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
    crossorigin=""
  />
</svelte:head>

<div class="space-y-6">
  <div class="flex flex-wrap items-end justify-between gap-4">
    <div>
      <a href="/" class="text-xs text-ink-lo hover:text-ink-hi">← Intake</a>
      <h2 class="font-display text-3xl text-ink-hi mt-1">Places</h2>
    </div>
    <DateRangePicker bind:value={range} onChange={() => load()} />
  </div>

  {#if loading}
    <div class="text-ink-mid">Loading...</div>
  {:else if error}
    <div class="rounded-md bg-surface-1 border border-surface-3 p-6 text-ink-mid">{error}</div>
  {:else}
    <section class="rounded-lg bg-surface-1 overflow-hidden">
      <div bind:this={mapEl} class="h-[420px] w-full"></div>
      {#if located.length === 0}
        <div class="p-4 text-sm text-ink-mid">
          Nothing located in this range yet. Once items have a lat/lng (live GPS or Home fallback) they'll show up here.
        </div>
      {/if}
    </section>

    <section class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <div class="rounded-lg bg-surface-1 p-6">
        <p class="text-xs uppercase tracking-wider text-ink-mid mb-3">
          {located.length} item{located.length === 1 ? "" : "s"} in range
        </p>
        {#if located.length === 0}
          <p class="text-sm text-ink-mid">Nothing here yet.</p>
        {:else}
          <div class="space-y-2 max-h-[420px] overflow-auto pr-1">
            {#each located as log}
              {@const food = foods.get(log.food_client_uuid)}
              {@const nova = food?.nova_class ?? 3}
              {@const isSel = log.client_uuid === selectedUuid}
              <button
                type="button"
                onclick={() => focusItem(log)}
                class="w-full text-left flex items-center gap-3 rounded-md p-3 transition-colors"
                style="background-color: {isSel
                  ? `color-mix(in srgb, ${NOVA_COLOR[nova]} 30%, transparent)`
                  : `color-mix(in srgb, ${NOVA_COLOR[nova]} 12%, transparent)`};"
              >
                <div
                  class="w-9 h-9 rounded-sm flex items-center justify-center text-ink-inv font-bold text-sm shrink-0"
                  style="background-color: {NOVA_COLOR[nova]};"
                >
                  {nova}
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-ink-hi font-medium truncate">{food?.name ?? "Unknown"}</p>
                  <p class="text-xs text-ink-mid">
                    {log.location_label ?? "Unknown"} · {timeLabel(log.eaten_at)}
                  </p>
                </div>
                <NovaPill novaClass={nova} />
              </button>
            {/each}
          </div>
        {/if}
      </div>

      <div class="rounded-lg bg-surface-1 p-6">
        <p class="text-xs uppercase tracking-wider text-ink-mid mb-3">By place</p>
        {#if groups.length === 0}
          <p class="text-sm text-ink-mid">
            Once you've logged items in a few different places, this is where you'll see how your processing score compares between them.
          </p>
        {:else}
          <div class="space-y-2">
            {#each groups as g}
              {@const novaInt = Math.max(1, Math.min(4, Math.round(g.novaAverage)))}
              <div
                class="rounded-md p-3 flex items-center gap-3"
                style="background-color: color-mix(in srgb, {NOVA_COLOR[novaInt]} 12%, transparent);"
              >
                <div
                  class="w-10 h-10 rounded-sm flex items-center justify-center text-ink-inv font-bold shrink-0"
                  style="background-color: {NOVA_COLOR[novaInt]};"
                >
                  {g.novaAverage.toFixed(1)}
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-ink-hi font-medium truncate">{g.label}</p>
                  <p class="text-xs text-ink-mid">
                    {g.itemCount} item{g.itemCount === 1 ? "" : "s"} · {g.upfShare}% UPF · {Math.round(
                      g.totalKcal
                    )} kcal
                  </p>
                </div>
              </div>
            {/each}
          </div>
        {/if}
      </div>
    </section>
  {/if}
</div>
