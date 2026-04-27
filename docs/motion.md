# Motion + haptics

Motion is choreographed, not decorative. Every transition has a reason; durations and curves are tokenised so the app feels coherent, not a collage of defaults.

## Easing curves

```
emphasized        cubic-bezier(0.2, 0.0, 0.0, 1.0)    primary navigations
standard          cubic-bezier(0.4, 0.0, 0.2, 1.0)    everyday motion
decelerate        cubic-bezier(0.0, 0.0, 0.2, 1.0)    things entering
accelerate        cubic-bezier(0.4, 0.0, 1.0, 1.0)    things leaving
spring-snappy     stiffness 600, damping 40            taps, pressed states
spring-soft       stiffness 250, damping 30            slider, sheet
```

Compose mapping: use `MotionScheme.expressive()` as a base, override the spring tokens above where called for. Avoid `tween` defaults.

## Durations

```
fast      120ms     micro feedback (tap ripple, chip toggle)
base      220ms     navigation between screens
slow      360ms     hero transitions (scan -> result)
hold      720ms     status confirmations (success states)
```

Anything longer than `slow` must be earned (e.g., the result screen's reveal).

## Choreographed transitions

### scan -> result

Trigger: barcode locked, OCR resolved, or analyzer returned.

Sequence:
1. Reticle does a single pulse (140ms scale 1.0 -> 1.04 -> 1.0, spring-snappy). Haptic: light tick.
2. Camera preview dims to surface-1 (220ms standard).
3. Result screen slides up from the bottom edge as a sheet (360ms emphasized) while the captured frame animates from the reticle to the small thumbnail position on the result page (shared element).
4. NOVA digit fades + scales from 0.85 -> 1.0 (220ms decelerate, 80ms after sheet settles).

### result -> log success

Trigger: tap "I ate it".

Sequence:
1. The CTA pill compresses 6dp inwards (80ms accelerate). Haptic: light tick on press.
2. The pill expands to fill width and morphs into a confirmation strip showing percentage + "logged" (220ms spring-soft).
3. Brief 720ms hold; haptic: success notification.
4. Auto-dismiss back to scan screen (sheet slides down, 360ms emphasized).

### history scroll

Items use a subtle stagger on initial load: each row delayed by 30ms after the previous (max 6 rows staggered). After load, no entrance animation on subsequent scrolls.

### percentage slider

`spring-soft`. Snap to 25/50/75/100 with a quiet detent (haptic: light tick at each detent). Outside snap zones, the slider tracks the gesture exactly.

## Haptics

```
tick           HapticFeedbackType.TextHandleMove   (Compose) - subtle nudge
confirm        HapticFeedbackConstants.CONFIRM      - success
reject         HapticFeedbackConstants.REJECT       - error / no-match
gesture        HapticFeedbackConstants.GESTURE_END  - scroll detent
```

Rules:
- Never haptic-spam a list. Detents only at meaningful boundaries.
- Always pair haptics with a visual change. Haptic-only feedback is a bug.
- Respect system reduced-motion / disabled-haptics settings.

## Reduced motion

When the user has reduced-motion enabled (system setting), we:
- Replace shared-element transitions with simple cross-fades (180ms standard).
- Disable the result-screen NOVA scale-in.
- Keep functional haptics; drop decorative ones.

## Compose implementation notes

- Define `MotionTokens` object with the constants above. All animations reference tokens, not literals.
- Use `AnimatedContent` with `SizeTransform` for the CTA -> confirmation morph.
- Use `LookaheadScope` for the shared-element camera-frame transition.
- Wrap haptic calls in a `LocalHapticController` composition local; tests can swap it for a no-op.
