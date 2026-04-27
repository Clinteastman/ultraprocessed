# Design system

> The brief: it must look so good it doesn't look like an app a developer made on a weekend, and especially not like a generic AI-generated UI.

## Vibe

- **Calm and confident, not clinical.** Food is sensory, not a spreadsheet. Lean into photography, type, and breathing room. No data-grid energy, no pastel medical app.
- **Editorial, not enterprise.** Think: a magazine spread crossed with a precise dashboard. Big numbers, generous margins, restrained chrome.
- **Dark-mode first.** Food photography reads better against deep neutrals; we own this aesthetic and offer light mode as a polite alternative.
- **Earned color.** Color is reserved for meaning (NOVA semantic, success, alerts). The rest is greys and a single accent.
- **Anti-cliche checklist.** No glassmorphism, no neon gradients on white, no chips on every surface, no AI-mascot illustrations, no rounded-everything plastic look. Sharp where it should be sharp.

## Tokens

### Color palette (dark mode is primary)

```
# Surface
bg            #0B0B0C   near-black with a hint of warmth
surface-1     #141416   elevated cards
surface-2     #1C1C1F   modals, sheets
surface-3     #26262A   pressed / selected

# Ink
ink-hi        #F5F4EF   primary text (warm off-white, never pure)
ink-mid       #B5B3AC   secondary text
ink-lo        #6E6C66   tertiary, hints, dividers
ink-inv       #0B0B0C   text on bright surfaces

# Accent (single brand color)
accent        #C7F25C   "fresh leaf" - distinctive but edible-looking
accent-press  #B0D944

# NOVA semantic (used only on the score itself)
nova-1        #5BC97D   green
nova-2        #C7C354   yellow-green
nova-3        #E8A04A   amber
nova-4        #D8543E   red

# Status
success       #5BC97D
warning       #E8A04A
error         #D8543E
```

Light mode (secondary):
```
bg            #FAF9F4
surface-1     #FFFFFF
surface-2     #F1EFE8
ink-hi        #14140F
ink-mid       #555048
ink-lo        #92897C
accent        #6BAA1E   (darkened for contrast on light)
```

### Typography

Two families, both freely licensed and on Google Fonts so we ship without negotiation:

- **Display:** [Instrument Serif](https://fonts.google.com/specimen/Instrument+Serif) - editorial, distinctive, and crucially not the default sans every dev reaches for. Used for the NOVA digit, screen titles, and hero numbers.
- **Body / UI:** [Geist](https://fonts.google.com/specimen/Geist) - precise, modern, excellent at small sizes; also has Geist Mono if we want it for ingredient lists.

Type scale (sp on Android, rem on web):

```
display-xl   72sp / 1.0   Instrument Serif italic   NOVA digit
display-lg   48sp / 1.05  Instrument Serif          screen titles
display-md   32sp / 1.1   Instrument Serif          hero numbers
title        20sp / 1.2   Geist 600                 section headers
body         16sp / 1.4   Geist 400                 paragraphs
body-sm      14sp / 1.4   Geist 400                 captions
mono         14sp / 1.4   Geist Mono 400            ingredient lists
overline     11sp / 1.2   Geist 500 +tracking 0.08  micro labels (NOVA, KCAL)
```

Tracking: a touch tighter on display sizes (-0.5%), normal on body, looser on overlines (+8%). Set proper baseline alignment on Android (`includeFontPadding = false`).

### Spacing

A 4dp grid, but most layouts breathe at 8 / 16 / 24 / 32. Avoid 12 unless aligning to text baselines.

```
space-1     4
space-2     8
space-3     12
space-4     16
space-5     24
space-6     32
space-7     48
space-8     72
space-9     96
```

Default screen edge padding is `space-5` (24); hero screens get `space-6` (32) and content can extend full-bleed where it earns it.

### Radii

```
radius-xs   4    inputs
radius-sm   8    chips, pills
radius-md   12   cards
radius-lg   20   sheets
radius-xl   32   bottom-sheet handle area, hero containers
```

Avoid using a single radius everywhere; vary by surface importance.

### Elevation

Dark-mode elevation is achieved with surface tinting, not shadows. Shadows are reserved for the camera shutter (one place where physicality reads as an affordance).

### Iconography

Stroke-based, 1.5px on a 24px grid. Lucide as the base set; replace any default Material icons. Custom icons for: NOVA grades (4 distinct marks), barcode, scan reticle, fast/eat states.

## Screens

### Scan screen (camera)

```
+----------------------------------------+
|                                        |
|   ULTRAPROCESSED                       |
|                            ⚙           |
|                                        |
|                                        |
|         +------------------+           |
|         |                  |           |
|         |   live camera    |           |
|         |   reticle here   |           |
|         |                  |           |
|         +------------------+           |
|                                        |
|         scanning for barcode...         |
|                                        |
|                                        |
|              ( SHUTTER )                |
|                                        |
|         barcode | label | photo        |
+----------------------------------------+
```

Notes:
- Wordmark top-left in `overline` (small, confident, not a logo soup).
- The reticle is a thin keyline rectangle with corner accents that brighten when a barcode locks. Subtle.
- Status line below the reticle gives live, quiet feedback ("scanning for barcode" -> "found <ean>" -> "looking up...").
- Mode strip at the bottom is purely informational (we auto-route); user can long-press to force a mode if they want.
- Shutter is large, circular, the only place we use a soft shadow.

### Result screen

```
+----------------------------------------+
|  <- back                                |
|                                        |
|  PRODUCT NAME                          |
|  Brand                                 |
|                                        |
|     +--------+                         |
|     |   4    |   NOVA          (red)   |
|     +--------+                         |
|                                        |
|  ultra-processed                       |
|                                        |
|  ingredients you might not             |
|  expect: emulsifier (E471),            |
|  flavour enhancer, ...                 |
|                                        |
|  ----------------------------          |
|                                        |
|  238 KCAL  /  100g  (≈ 1 bar)          |
|                                        |
|  +----------------------------------+  |
|  |  I ATE IT             100% [==] |  |
|  +----------------------------------+  |
|                                        |
|  didn't eat it                          |
+----------------------------------------+
```

Notes:
- Large NOVA digit in Instrument Serif italic, 72sp, in the NOVA semantic color.
- The "ingredients you might not expect" is a curated short list (the LLM/heuristic surfaces 1-3 specifically processing-revealing items, not the full label).
- Calorie line uses display-md numerals, then `body-sm` units in `ink-mid`.
- Primary CTA pill has a percentage slider built in - drag to set, tap the label area to log. Haptic on log.

### History screen

```
+----------------------------------------+
|  HISTORY                                |
|                                        |
|  TODAY  ·  3 meals  ·  812 kcal       |
|                                        |
|  +-------+  Apple                     |
|  | photo |  10:14  ·  100%            |
|  +-------+  NOVA 1   95 kcal          |
|                                        |
|  +-------+  Tesco Granola              |
|  | photo |  09:02  ·  60%             |
|  +-------+  NOVA 4   180 kcal         |
|                                        |
|  YESTERDAY  ·  4 meals  ·  1,940 kcal  |
|                                        |
|  ...                                   |
+----------------------------------------+
```

Notes:
- Day headers in `overline` style with totals inline; section feels like a magazine masthead.
- Each entry is a 64x64 photo + two-line text + NOVA pill on the right.
- Photo-led: even a barcode scan attaches the OFF product photo, so the timeline always has visual anchor.

### Settings screen

Standard form layout with grouped sections. Sections: `Provider`, `Backend (optional)`, `Fasting`, `About`. Inputs use `radius-xs`, ample padding, helper text in `ink-mid`. Sensitive fields show "stored on device, encrypted" hint under the input.

## NOVA classification at a glance

NOVA is the official scoring system this app uses; designs make it the hero.

| Class | Label                          | Color    |
|-------|--------------------------------|----------|
| 1     | Unprocessed / minimally        | nova-1   |
| 2     | Processed culinary ingredients | nova-2   |
| 3     | Processed                      | nova-3   |
| 4     | Ultra-processed                | nova-4   |

When a result is uncertain (low confidence), we render the NOVA digit with a striped border and the rationale text in italic - signalling "best guess".

## Don't-do list

- No emojis in the UI (we use real icons).
- No corner-rounding everything to the same radius.
- No info-density spreadsheets - this app is opinionated and curated.
- No marketing tone in the strings; voice is dry, useful, slightly editorial.
