<script lang="ts">
  // Plain-language reference for what NOVA scores mean, how the fasting
  // schedules work, what the restricted-day kcal cap is, and why we lead
  // with processing rather than calories. Mirrors the phone Help screen
  // so both surfaces tell the same story.
  const novaRows = [
    {
      n: 1,
      color: "#5BC97D",
      label: "Unprocessed / minimally processed",
      blurb:
        "Whole foods, fresh fruit, vegetables, eggs, plain meat, fish, milk."
    },
    {
      n: 2,
      color: "#C7C354",
      label: "Processed culinary ingredients",
      blurb:
        "Oils, butter, sugar, salt, vinegar. Used to cook, not eaten alone."
    },
    {
      n: 3,
      color: "#E8A04A",
      label: "Processed foods",
      blurb:
        "Bread, cheese, tinned veg in salt, smoked meat, fresh pasta. Recognisable food + a few ingredients."
    },
    {
      n: 4,
      color: "#D8543E",
      label: "Ultra-processed foods (UPF)",
      blurb:
        "Long ingredient lists with industrial additives, emulsifiers, sweeteners. Soft drinks, mass-market biscuits, shaped meat, ready meals."
    }
  ];
</script>

<svelte:head>
  <title>How this app works · Ultraprocessed</title>
</svelte:head>

<div class="max-w-3xl mx-auto px-6 py-10 space-y-10">
  <header>
    <a href="/" class="text-xs text-ink-lo hover:text-ink-hi">← Back</a>
    <h1 class="text-3xl font-display mt-2">How this app works</h1>
  </header>

  <section class="space-y-3">
    <h2 class="text-xs uppercase tracking-wider text-ink-mid">Why we lead with processing</h2>
    <p class="text-ink-mid">
      Calories are necessary but not sufficient. Two days of 2000 kcal can be very different for your body if one is whole foods and the other is ultra-processed. Hero metrics across this app put NOVA score and UPF share first; calories are a supporting stat, not the headline.
    </p>
  </section>

  <section class="space-y-3">
    <h2 class="text-xs uppercase tracking-wider text-ink-mid">NOVA classes</h2>
    <div class="space-y-2">
      {#each novaRows as row}
        <div
          class="rounded-lg p-4 flex items-center gap-4"
          style="background-color: color-mix(in srgb, {row.color} 14%, transparent);"
        >
          <div
            class="w-10 h-10 rounded-sm flex items-center justify-center text-ink-inv font-bold text-lg"
            style="background-color: {row.color};"
          >
            {row.n}
          </div>
          <div class="min-w-0">
            <p class="font-medium text-ink-hi">{row.label}</p>
            <p class="text-sm text-ink-mid">{row.blurb}</p>
          </div>
        </div>
      {/each}
    </div>
  </section>

  <section class="space-y-3">
    <h2 class="text-xs uppercase tracking-wider text-ink-mid">How is the daily NOVA score worked out?</h2>
    <p class="text-ink-mid">
      It's a kcal-weighted average of every item logged that day. A 200 kcal slice of toast (NOVA 3) and a 50 kcal apple (NOVA 1) average to ~2.6, not 2.0 - the toast is doing more of the dietary work that day. Lower is better.
    </p>
  </section>

  <section class="space-y-3">
    <h2 class="text-xs uppercase tracking-wider text-ink-mid">UPF share %</h2>
    <p class="text-ink-mid">
      What fraction of today's calories came from NOVA 4 items. Often more actionable than the average score - cutting one biscuit can move this by 10 percentage points even if your average barely shifts.
    </p>
  </section>

  <section class="space-y-3">
    <h2 class="text-xs uppercase tracking-wider text-ink-mid">Fasting schedules</h2>
    <ul class="text-ink-mid space-y-2 list-disc pl-5">
      <li>
        <span class="text-ink-hi font-medium">Time-restricted (TRE)</span> - 16:8, 18:6, 20:4, OMAD: you pick a daily eating window. Outside it, you fast.
      </li>
      <li>
        <span class="text-ink-hi font-medium">Multi-day patterns</span> - 5:2, 4:3, ADF: you pick which weekdays are restricted. On a restricted day, you cap calories (or fast fully); the other days are normal eating.
      </li>
      <li>
        <span class="text-ink-hi font-medium">Custom</span> - either kind, set by hand.
      </li>
    </ul>
  </section>

  <section class="space-y-3">
    <h2 class="text-xs uppercase tracking-wider text-ink-mid">What's the "500 kcal cap" about?</h2>
    <p class="text-ink-mid">
      On a 5:2-style restricted day, the convention is to eat ~500 kcal (women) / ~600 kcal (men) - roughly 25% of typical maintenance. The home strip shows that cap so you know your target on a restricted day. Toggle "Full fast" in Settings if you want a water-only day instead.
    </p>
  </section>

  <section class="space-y-3">
    <h2 class="text-xs uppercase tracking-wider text-ink-mid">Eating window for restricted-day schedules</h2>
    <p class="text-ink-mid">
      On normal (non-restricted) days for a 5:2/4:3/ADF schedule, the app applies whatever eating window you've set as a TRE-style routine. So you can run "5:2 + 16:8 on the other days" out of one profile.
    </p>
  </section>

  <section class="space-y-3">
    <h2 class="text-xs uppercase tracking-wider text-ink-mid">Where data lives</h2>
    <p class="text-ink-mid">
      The phone holds a local SQLite database and works fully offline. The backend stores the canonical copy and serves the dashboard, the Home Assistant integration, and pull-based sync from the phone. Everything you see here is the same data the phone has, just from a different angle.
    </p>
  </section>
</div>
