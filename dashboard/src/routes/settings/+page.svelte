<script lang="ts">
  import { api } from "$lib/api/client";

  let qrSvg = $state<string | null>(null);
  let pairing = $state(false);
  let pairError = $state<string | null>(null);
  let copied = $state(false);
  let payload = $state<string | null>(null);
  let originWarning = $state<string | null>(null);

  async function pairDevice() {
    pairing = true;
    pairError = null;
    copied = false;
    qrSvg = null;
    payload = null;
    originWarning = null;
    try {
      const origin = window.location.origin;
      const host = window.location.hostname;
      if (host === "localhost" || host === "127.0.0.1" || host === "0.0.0.0") {
        originWarning =
          `Heads-up: this dashboard is loaded via ${origin}. The QR will tell ` +
          "your phone to use that URL, which the phone can't reach (localhost on the phone is the phone, not this machine). " +
          "Access this dashboard via your LAN IP or public hostname before generating a QR for a phone.";
      }

      const result = await api.pair("phone");
      const blob = JSON.stringify({
        v: 1,
        url: origin,
        token: result.token,
        device_id: result.device_id,
        user_id: result.user_id
      });
      payload = blob;
      const QRCode = (await import("qrcode")).default;
      qrSvg = await QRCode.toString(blob, {
        type: "svg",
        margin: 2,
        width: 320,
        errorCorrectionLevel: "M",
        color: { dark: "#0B0B0C", light: "#F5F4EF" }
      });
    } catch (e) {
      pairError = (e as Error).message;
    } finally {
      pairing = false;
    }
  }

  async function copyPayload() {
    if (!payload) return;
    try {
      await navigator.clipboard.writeText(payload);
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
      The dashboard runs in the same container as the API. This page is for connecting phones and pointing at related screens.
    </p>
  </div>

  <section class="rounded-lg bg-surface-1 p-6 space-y-3">
    <p class="text-xs uppercase tracking-wider text-ink-mid">Daily targets</p>
    <p class="text-sm text-ink-mid">
      Edit your calorie target and fasting window on the
      <a href="/targets" class="text-accent hover:underline">Targets page</a>.
    </p>
  </section>

  <section class="rounded-lg bg-surface-1 p-6 space-y-5">
    <div>
      <p class="text-xs uppercase tracking-wider text-ink-mid">Pair a device</p>
      <p class="text-sm text-ink-mid mt-1">
        Tap the button, point your phone at the QR code, and the app sets the backend URL and token in one go. No typing.
      </p>
    </div>
    <button
      type="button"
      onclick={pairDevice}
      disabled={pairing}
      class="rounded-md bg-accent text-ink-inv font-semibold px-4 py-2 hover:bg-accent-press transition-colors disabled:opacity-50"
    >
      {pairing ? "Generating..." : qrSvg ? "Generate another" : "Pair a device"}
    </button>
    {#if pairError}
      <p class="text-sm text-nova-4">{pairError}</p>
    {/if}
    {#if originWarning}
      <div class="rounded-md bg-nova-3/15 border border-nova-3/30 p-3">
        <p class="text-sm text-ink-hi">{originWarning}</p>
      </div>
    {/if}
    {#if qrSvg}
      <div class="space-y-3">
        <div class="rounded-md p-4 bg-ink-hi inline-block">
          {@html qrSvg}
        </div>
        <p class="text-xs text-ink-mid">
          In the phone app open Settings &rarr; Scan pairing QR.
        </p>
        <details class="text-sm text-ink-mid">
          <summary class="cursor-pointer text-ink-mid hover:text-ink-hi">
            Show payload (manual copy)
          </summary>
          <div class="mt-2 rounded-md bg-surface-2 p-4 space-y-2">
            <code class="block break-all font-mono text-xs text-ink-hi">{payload}</code>
            <button
              type="button"
              onclick={copyPayload}
              class="text-xs rounded-md bg-surface-3 text-ink-hi px-3 py-1 hover:bg-surface-3/70 transition-colors"
            >
              {copied ? "Copied" : "Copy"}
            </button>
          </div>
        </details>
      </div>
    {/if}
  </section>
</div>
