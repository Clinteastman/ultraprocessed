<script lang="ts">
  import "../app.css";
  import { onMount } from "svelte";
  import { page } from "$app/stores";
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

  const navItems = [
    { href: "/", label: "Today" },
    { href: "/history", label: "History" },
    { href: "/settings", label: "Settings" }
  ];
</script>

<div class="min-h-screen flex flex-col">
  <header class="border-b border-surface-3">
    <div class="max-w-5xl mx-auto px-6 py-5 flex items-center gap-8">
      <h1 class="font-display text-2xl tracking-tight">Ultraprocessed</h1>
      <nav class="flex gap-6 text-sm">
        {#each navItems as item}
          {@const active = $page.url.pathname === item.href || ($page.url.pathname.startsWith(item.href) && item.href !== "/")}
          <a
            href={item.href}
            class="transition-colors {active ? 'text-accent' : 'text-ink-mid hover:text-ink-hi'}"
          >
            {item.label}
          </a>
        {/each}
      </nav>
    </div>
  </header>

  <main class="flex-1 max-w-5xl w-full mx-auto px-6 py-8">
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
