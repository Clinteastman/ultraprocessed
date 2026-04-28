<script lang="ts">
  import { onMount, onDestroy } from "svelte";
  import type { ConsumptionLogDto, FoodEntryDto } from "$lib/api/types";
  import type { Map as LMap, Marker as LMarker } from "leaflet";

  // Compact non-interactive map preview for the dashboard home. Shows
  // where the [logs] in the current date window were logged, auto-fitted
  // to their bounding box. Clicking the map jumps to /places.

  let {
    logs,
    foods
  }: {
    logs: ConsumptionLogDto[];
    foods: Map<string, FoodEntryDto>;
  } = $props();

  let container: HTMLDivElement | null = null;
  let map: LMap | null = null;
  let markers: LMarker[] = [];

  const NOVA_COLOR: Record<number, string> = {
    1: "#5BC97D",
    2: "#C7C354",
    3: "#E8A04A",
    4: "#D8543E"
  };

  const located = $derived(logs.filter((l) => l.lat != null && l.lng != null));

  async function ensureMap() {
    if (!container || map) return;
    const L = (await import("leaflet")).default;
    map = L.map(container, {
      attributionControl: false,
      zoomControl: false,
      dragging: false,
      scrollWheelZoom: false,
      doubleClickZoom: false,
      boxZoom: false,
      keyboard: false,
      touchZoom: false
    });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: ""
    }).addTo(map);
  }

  async function refreshMarkers() {
    if (!map) return;
    const L = (await import("leaflet")).default;
    markers.forEach((m) => m.remove());
    markers = [];
    if (located.length === 0) return;
    for (const log of located) {
      const food = foods.get(log.food_client_uuid);
      const nova = food?.nova_class ?? 3;
      const color = NOVA_COLOR[nova] ?? "#888";
      const m = L.circleMarker([log.lat!, log.lng!], {
        radius: 6,
        fillColor: color,
        color: "rgba(0,0,0,0.4)",
        weight: 1,
        fillOpacity: 1
      }).addTo(map);
      markers.push(m as unknown as LMarker);
    }
    // Tight-cluster fallback: if all markers are within ~50m, pin to the
    // centroid at zoom 16.5 - fitBounds with a degenerate box ends up
    // showing a confusing wide view.
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
        { padding: [24, 24], maxZoom: 17 }
      );
    }
  }

  onMount(async () => {
    await ensureMap();
    await refreshMarkers();
  });
  onDestroy(() => {
    map?.remove();
    map = null;
  });

  $effect(() => {
    // Re-render markers whenever located changes.
    void located;
    refreshMarkers();
  });
</script>

<svelte:head>
  <link
    rel="stylesheet"
    href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
    crossorigin=""
  />
</svelte:head>

<a
  href="/places"
  class="block rounded-lg bg-surface-1 overflow-hidden hover:opacity-95 transition-opacity"
  title="Open the full places map"
>
  <div class="px-6 pt-4">
    <p class="text-xs uppercase tracking-wider text-ink-mid">Where</p>
  </div>
  {#if located.length === 0}
    <div class="px-6 pb-6 pt-2 text-sm text-ink-mid">
      Nothing located in this period yet.
    </div>
  {:else}
    <div bind:this={container} class="h-44 w-full mt-3"></div>
  {/if}
</a>

<style>
  /* Leaflet's default body cursor doesn't fit the read-only preview. */
  :global(.leaflet-container) {
    cursor: pointer;
    background: #1f2630;
  }
</style>
