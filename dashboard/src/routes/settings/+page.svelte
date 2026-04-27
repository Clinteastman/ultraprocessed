<script lang="ts">
  import { onMount } from "svelte";
  import { api, getBaseUrl, getToken, setBaseUrl, setToken } from "$lib/api/client";

  let baseUrl = $state("");
  let token = $state("");
  let deviceName = $state("dashboard");
  let pairing = $state(false);
  let testStatus = $state<string | null>(null);
  let pairError = $state<string | null>(null);

  onMount(() => {
    baseUrl = getBaseUrl();
    token = getToken();
  });

  function save() {
    setBaseUrl(baseUrl);
    setToken(token);
    testStatus = "Saved.";
  }

  async function pair() {
    pairing = true;
    pairError = null;
    try {
      setBaseUrl(baseUrl);
      const result = await api.pair(deviceName);
      token = result.token;
      setToken(token);
      testStatus = "Paired. Token saved.";
    } catch (e) {
      pairError = (e as Error).message;
    } finally {
      pairing = false;
    }
  }

  async function test() {
    testStatus = "Checking...";
    try {
      setBaseUrl(baseUrl);
      setToken(token);
      const me = await api.whoami();
      testStatus = `OK. Logged in as device #${me.device_id} (${me.device_name}) for user #${me.user_id}.`;
    } catch (e) {
      testStatus = `Failed: ${(e as Error).message}`;
    }
  }
</script>

<svelte:head><title>Settings · Ultraprocessed</title></svelte:head>

<div class="space-y-6 max-w-xl">
  <div>
    <h2 class="font-display text-3xl">Settings</h2>
    <p class="text-ink-mid text-sm mt-1">
      Stored in your browser's localStorage. Only this device sees the token.
    </p>
  </div>

  <div class="space-y-4">
    <label class="block">
      <span class="text-xs uppercase tracking-wider text-ink-mid">Backend URL</span>
      <input
        type="url"
        bind:value={baseUrl}
        placeholder="https://ultraprocessed.example.com"
        class="mt-1 w-full rounded-sm bg-surface-1 border border-surface-3 px-3 py-2 text-ink-hi focus:border-accent focus:outline-none"
      />
      <p class="text-xs text-ink-lo mt-1">
        Leave blank if the dashboard is served from the backend itself.
      </p>
    </label>

    <label class="block">
      <span class="text-xs uppercase tracking-wider text-ink-mid">Device token</span>
      <input
        type="password"
        bind:value={token}
        class="mt-1 w-full rounded-sm bg-surface-1 border border-surface-3 px-3 py-2 text-ink-hi focus:border-accent focus:outline-none"
      />
    </label>

    <div class="flex gap-3">
      <button
        type="button"
        onclick={save}
        class="rounded-md bg-accent text-ink-inv font-semibold px-4 py-2 hover:bg-accent-press transition-colors"
      >
        Save
      </button>
      <button
        type="button"
        onclick={test}
        class="rounded-md bg-surface-2 text-ink-hi font-medium px-4 py-2 hover:bg-surface-3 transition-colors"
      >
        Test connection
      </button>
    </div>
    {#if testStatus}
      <p class="text-sm text-ink-mid">{testStatus}</p>
    {/if}
  </div>

  <div class="border-t border-surface-3 pt-6 space-y-3">
    <p class="text-xs uppercase tracking-wider text-ink-mid">Or pair fresh</p>
    <p class="text-sm text-ink-mid">
      Issues a brand-new device token (single-user mode adds a new device for the same user).
    </p>
    <label class="block">
      <span class="text-xs uppercase tracking-wider text-ink-mid">Device name</span>
      <input
        type="text"
        bind:value={deviceName}
        class="mt-1 w-full rounded-sm bg-surface-1 border border-surface-3 px-3 py-2 text-ink-hi focus:border-accent focus:outline-none"
      />
    </label>
    <button
      type="button"
      onclick={pair}
      disabled={pairing || !baseUrl}
      class="rounded-md bg-surface-2 text-ink-hi font-medium px-4 py-2 hover:bg-surface-3 transition-colors disabled:opacity-50"
    >
      {pairing ? "Pairing..." : "Pair this browser"}
    </button>
    {#if pairError}
      <p class="text-sm text-nova-4">{pairError}</p>
    {/if}
  </div>
</div>
