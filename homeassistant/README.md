# Ultraprocessed - Home Assistant integration

A HACS-installable integration that exposes your Ultraprocessed
backend's data as Home Assistant entities so you can build dashboards
that combine food intake with whatever you already track in HA
(activity calories, sleep, weight, etc.).

## Install (HACS)

1. In HACS, go to **Integrations -> three-dot menu -> Custom repositories**.
2. Add `https://github.com/Clinteastman/ultraprocessed` as type **Integration**.
3. Search for "Ultraprocessed" and install.
4. Restart Home Assistant.
5. **Settings -> Devices & Services -> Add Integration -> Ultraprocessed**.
6. Enter your backend URL (e.g. `https://ultraprocessed.example.com`) and
   a device token (pair one from the dashboard's Settings page or the
   phone app).

## What you get

A single `Ultraprocessed` device in HA grouping ~50 entities. The most
useful ones are enabled by default; the rest (B-vitamins, trace
minerals) are registered but disabled - turn them on per dashboard need
to keep your entity list tidy.

### Headline sensors

- `sensor.ultraprocessed_calories_today` - kcal in today
- `sensor.ultraprocessed_calorie_target` - your daily target
- `sensor.ultraprocessed_calories_remaining` - target minus intake
- `sensor.ultraprocessed_nova_average` - calorie-weighted NOVA score (1-4)
- `sensor.ultraprocessed_ultra_processed_calories` - NOVA-4 kcal today
- `sensor.ultraprocessed_ultra_processed_share` - UPF share as a %
- `sensor.ultraprocessed_meals_today` - count
- `sensor.ultraprocessed_minutes_since_last_meal`
- `sensor.ultraprocessed_last_meal` - last food name (state); attrs hold
  NOVA class, kcal, percentage eaten, eaten-at
- `sensor.ultraprocessed_next_eat_at` - timestamp of when the eating
  window opens next (only meaningful while currently fasting)
- `sensor.ultraprocessed_eating_window_closes_at` - timestamp of when
  today's window ends (only meaningful while in the window)
- `sensor.ultraprocessed_active_fasting_profile` - profile name (e.g.
  "16:8"); attrs carry the schedule details

### Per-NOVA-class breakdown

- `sensor.ultraprocessed_nova_1_calories` ... `nova_4_calories`
- `sensor.ultraprocessed_nova_1_meals` ... `nova_4_meals` *(disabled by default)*

### Per-nutrient

One sensor per macro/micro from the backend's RDV table. State is
amount consumed; attributes carry `reference_daily_value` and
`percent_rdv` so you can template e.g. "alert if sodium > 100% RDV".

Enabled by default: protein, fat, saturated fat, carbs, sugar, fiber,
salt, sodium.

Disabled by default (registered, ready to enable): cholesterol, omega-3,
all minerals (calcium, iron, potassium, magnesium, zinc, phosphorus,
selenium, iodine, copper, manganese), all vitamins (A, C, D, E, K, B1,
B2, B3, B6, B12, folate).

### Binary sensors

- `binary_sensor.ultraprocessed_fasting` - on while outside the eating
  window. Use as the trigger for "notify me when I can eat next" automations.

## Combining with Samsung Health / activity data

The whole point: HA owns the cross-source view. Once your activity
calories are in HA (via the Companion app, Withings, Garmin, whatever),
build a Lovelace card that subtracts them:

```yaml
type: custom:mini-graph-card
name: Energy balance
entities:
  - entity: sensor.ultraprocessed_calories_today
    name: In
    color: '#5BC97D'
  - entity: sensor.samsung_health_active_calories
    name: Out
    color: '#E8A04A'
```

Or template a net-calorie sensor:

```yaml
template:
  - sensor:
      - name: Net calories today
        unit_of_measurement: kcal
        state: >
          {{ states('sensor.ultraprocessed_calories_today') | float(0)
             - states('sensor.samsung_health_active_calories') | float(0) }}
```

## Updating

The integration polls `/api/v1/ha/snapshot` every 5 minutes by default
(set in `const.py`). The backend computes everything in one pass so
this is a single cheap request - safe to drop the interval to 1 minute
if you want fresher countdowns.
