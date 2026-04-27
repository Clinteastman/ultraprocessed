<script lang="ts">
  import "../app.css";
  import { page } from "$app/stores";

  let { children } = $props();

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
    {@render children()}
  </main>

  <footer class="text-xs text-ink-lo text-center py-6">
    Self-hosted at this URL · AGPL-3.0
  </footer>
</div>
