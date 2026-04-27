<script lang="ts">
  import { onMount } from "svelte";
  import { api, type FastingProfileDto } from "$lib/api/client";
  import FastingPicker from "$lib/components/FastingPicker.svelte";

  // ----- Phone pairing -----
  let qrSvg = $state<string | null>(null);
  let pairing = $state(false);
  let pairError = $state<string | null>(null);
  let copied = $state(false);
  let payload = $state<string | null>(null);
  let originWarning = $state<string | null>(null);

  // ----- Calorie target -----
  let calorieTarget = $state<number>(2000);
  let calorieDirty = $state(false);
  let calorieSaving = $state(false);
  let calorieError = $state<string | null>(null);
  let calorieSaved = $state(false);

  // ----- Fasting profile -----
  let fasting = $state<FastingProfileDto>({
    id: null,
    name: "16:8",
    schedule_type: "SIXTEEN_EIGHT",
    eating_window_start_minutes: 12 * 60,
    eating_window_end_minutes: 20 * 60,
    restricted_days_mask: 0,
    restricted_kcal_target: null,
    active: true
  });
  let fastingDirty = $state(false);
  let fastingSaving = $state(false);
  let fastingError = $state<string | null>(null);
  let fastingSaved = $state(false);
  let fastingLoaded = $state(false);

  onMount(async () => {
    try {
      const t = await api.getTargets();
      calorieTarget = t.calorie_target_kcal;
    } catch (e) {
      calorieError = (e as Error).message;
    }
    try {
      const f = await api.getFastingProfile();
      if (f) fasting = { ...fasting, ...f };
    } catch {
      /* fresh user */
    } finally {
      fastingLoaded = true;
    }
  });

  async function saveCalorie() {
    calorieSaving = true;
    calorieError = null;
    calorieSaved = false;
    try {
      const r = await api.putTargets({ calorie_target_kcal: calorieTarget });
      calorieTarget = r.calorie_target_kcal;
      calorieDirty = false;
      calorieSaved = true;
      setTimeout(() => (calorieSaved = false), 2500);
    } catch (e) {
      calorieError = (e as Error).message;
    } finally {
      calorieSaving = false;
    }
  }

  async function saveFasting() {
    fastingSaving = true;
    fastingError = null;
    fastingSaved = false;
    try {
      await api.putFastingProfile(fasting);
      fastingDirty = false;
      fastingSaved = true;
      setTimeout(() => (fastingSaved = false), 2500);
    } catch (e) {
      fastingError = (e as Error).message;
    } finally {
      fastingSaving = false;
    }
  }

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
          "your phone to use that URL, which the phone can't reach. " +
          "Access this dashboard via the public hostname before generating a QR for a phone.";
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
      /* noop */
    }
  }
</script>

<svelte:head><title>Settings · Ultraprocessed</title></svelte:head>

<div class="space-y-10 max-w-2xl">
  <div>
    <h2 class="font-display text-3xl">Settings</h2>
    <p class="text-ink-mid text-sm mt-2">
      Daily targets, fasting plan, and phone pairing. The dashboard runs in the same container as the API; nothing else to point anywhere.
    </p>
  </div>

  <!-- Calorie target -->
  <section class="rounded-lg bg-surface-1 p-6 space-y-4">
    <p class="text-xs uppercase tracking-wider text-ink-mid">Calorie target</p>
    <div class="flex items-end gap-3">
      <input
        type="number"
        min="500"
        max="10000"
        step="50"
        value={calorieTarget}
        oninput={(e) => {
          calorieTarget = Number(e.currentTarget.value);
          calorieDirty = true;
          calorieSaved = false;
        }}
        class="font-display text-4xl bg-transparent text-ink-hi w-40 focus:outline-none border-b border-surface-3 focus:border-accent"
      />
      <span class="text-ink-mid pb-2">kcal / day</span>
    </div>
    <p class="text-sm text-ink-lo">
      EU NRV reference is 2,000 kcal for an average adult. Tune to your basal + activity level.
    </p>
    {#if calorieError}<p class="text-sm text-nova-4">{calorieError}</p>{/if}
    <div class="flex items-center gap-3">
      <button
        type="button"
        onclick={saveCalorie}
        disabled={!calorieDirty || calorieSaving}
        class="rounded-md bg-accent text-ink-inv font-semibold px-4 py-2 hover:bg-accent-press transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {calorieSaving ? "Saving..." : "Save"}
      </button>
      {#if calorieSaved}<span class="text-sm text-nova-1">Saved.</span>{/if}
    </div>
  </section>

  <!-- Fasting -->
  <section class="rounded-lg bg-surface-1 p-6 space-y-4">
    <div>
      <p class="text-xs uppercase tracking-wider text-ink-mid">Fasting plan</p>
      <p class="text-sm text-ink-mid mt-1">
        Time-restricted (TRE) eating windows or weekly restricted-day patterns (5:2, 4:3, ADF). The dashboard's fasting status strip and Home Assistant sensors read from this.
      </p>
    </div>
    <FastingPicker bind:value={fasting} onChange={() => { fastingDirty = true; fastingSaved = false; }} />
    {#if fastingError}<p class="text-sm text-nova-4">{fastingError}</p>{/if}
    <div class="flex items-center gap-3">
      <button
        type="button"
        onclick={saveFasting}
        disabled={!fastingDirty || fastingSaving || !fastingLoaded}
        class="rounded-md bg-accent text-ink-inv font-semibold px-4 py-2 hover:bg-accent-press transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {fastingSaving ? "Saving..." : "Save"}
      </button>
      {#if fastingSaved}<span class="text-sm text-nova-1">Saved.</span>{/if}
    </div>
  </section>

  <!-- Phone pairing -->
  <section class="rounded-lg bg-surface-1 p-6 space-y-5">
    <div>
      <p class="text-xs uppercase tracking-wider text-ink-mid">Pair a device</p>
      <p class="text-sm text-ink-mid mt-1">
        Point your phone at the QR code; the app sets the backend URL and token in one go.
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
    {#if pairError}<p class="text-sm text-nova-4">{pairError}</p>{/if}
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
