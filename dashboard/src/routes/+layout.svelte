<script lang="ts">
  import "../app.css";
  import { onMount } from "svelte";
  import { ensurePaired } from "$lib/api/client";

  let { children } = $props();

  let pairing = $state(true);
  let pairError = $state<string | null>(null);

  onMount(async () => {
    try {
      await ensurePaired();
    } catch (e) {
      pairError = (e as Error).message;
    } finally {
      pairing = false;
    }
  });
</script>

<div class="min-h-screen flex flex-col">
  <header class="border-b border-surface-3">
    <div class="max-w-6xl mx-auto px-6 py-5 flex items-center justify-between">
      <a href="/" class="font-display text-2xl tracking-tight text-ink-hi hover:text-ink-hi">
        Ultraprocessed
      </a>
      <div class="flex items-center gap-1">
        <a
          href="/help"
          title="How this app works"
          aria-label="Help"
          class="rounded-sm p-2 text-ink-mid hover:text-ink-hi hover:bg-surface-1 transition-colors"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
            stroke-linecap="round" stroke-linejoin="round" class="w-5 h-5">
            <circle cx="12" cy="12" r="10" />
            <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
        </a>
      <a
        href="/settings"
        title="Settings"
        aria-label="Settings"
        class="rounded-sm p-2 text-ink-mid hover:text-ink-hi hover:bg-surface-1 transition-colors"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
          stroke-linecap="round" stroke-linejoin="round" class="w-5 h-5">
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h.01a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h.01a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v.01a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
          <circle cx="12" cy="12" r="3"/>
        </svg>
      </a>
      </div>
    </div>
  </header>

  <main class="flex-1 max-w-6xl w-full mx-auto px-6 py-8">
    {#if pairing}
      <div class="text-ink-mid">Connecting...</div>
    {:else if pairError}
      <div class="rounded-md bg-surface-1 border border-surface-3 p-6 text-ink-mid">
        Couldn't reach the backend: {pairError}
      </div>
    {:else}
      {@render children()}
    {/if}
  </main>

  <footer class="text-xs text-ink-lo text-center py-6">
    Self-hosted · AGPL-3.0
  </footer>
</div>
