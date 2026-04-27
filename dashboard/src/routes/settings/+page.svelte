<script lang="ts">
  import { api } from "$lib/api/client";

  let phoneToken = $state<string | null>(null);
  let issuingPhoneToken = $state(false);
  let phoneTokenError = $state<string | null>(null);
  let copied = $state(false);

  async function issuePhoneToken() {
    issuingPhoneToken = true;
    phoneTokenError = null;
    copied = false;
    try {
      const result = await api.pair("phone");
      phoneToken = result.token;
    } catch (e) {
      phoneTokenError = (e as Error).message;
    } finally {
      issuingPhoneToken = false;
    }
  }

  async function copyPhoneToken() {
    if (!phoneToken) return;
    try {
      await navigator.clipboard.writeText(phoneToken);
      copied = true;
      setTimeout(() => (copied = false), 2000);
    } catch {
      /* user can copy manually */
    }
  }
</script>

<svelte:head><title>Settings · Ultraprocessed</title></svelte:head>

<div class="space-y-10 max-w-2xl">
  <div>
    <h2 class="font-display text-3xl">Settings</h2>
    <p class="text-ink-mid text-sm mt-2">
      The dashboard runs in the same container as the API. Nothing to point anywhere - this page is just for connecting phones.
    </p>
  </div>

  <section class="rounded-lg bg-surface-1 p-6 space-y-4">
    <div>
      <p class="text-xs uppercase tracking-wider text-ink-mid">Pair a phone</p>
      <p class="text-sm text-ink-mid mt-1">
        In the Android app, open Settings, set <span class="text-ink-hi">Backend URL</span> to wherever this dashboard is hosted, then either tap <span class="text-ink-hi">Pair</span> in the app, or generate a token here and paste it as the <span class="text-ink-hi">Device token</span>.
      </p>
    </div>
    <button
      type="button"
      onclick={issuePhoneToken}
      disabled={issuingPhoneToken}
      class="rounded-md bg-accent text-ink-inv font-semibold px-4 py-2 hover:bg-accent-press transition-colors disabled:opacity-50"
    >
      {issuingPhoneToken ? "Generating..." : "Generate phone token"}
    </button>
    {#if phoneTokenError}
      <p class="text-sm text-nova-4">{phoneTokenError}</p>
    {/if}
    {#if phoneToken}
      <div class="rounded-md bg-surface-2 p-4 space-y-3">
        <p class="text-xs uppercase tracking-wider text-ink-mid">Phone token (one-time view)</p>
        <code class="block break-all font-mono text-sm text-ink-hi">{phoneToken}</code>
        <button
          type="button"
          onclick={copyPhoneToken}
          class="text-sm rounded-md bg-surface-3 text-ink-hi px-3 py-1 hover:bg-surface-3/70 transition-colors"
        >
          {copied ? "Copied" : "Copy"}
        </button>
      </div>
    {/if}
  </section>
</div>
