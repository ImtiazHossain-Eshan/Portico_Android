---
name: Portico Android
description: A real-estate portfolio read as a financial instrument, not a brochure.
visual-world: Neutral graphite and bone grounds / one amber accent / hairline-ruled panels
platform: Android native / Kotlin / Jetpack Compose / Material 3
---

# Portico design system

Recorded from the built application, not from intention. Every value below is
in `ui/theme/Theme.kt` or `ui/design/`.

## North star

A property portfolio is not one number. It is gross rent falling through
operating costs and tax to whatever survives, per property and across the
whole register. Portico puts that subtraction on screen and lets the investor
open any figure to the records underneath it.

The category default (a card per metric, a donut, an accent gradient) hides
that chain. This build refuses it.

## Visual world

Grounds are neutral: graphite in dark, bone in light. Both are deliberately
achromatic so the only saturated colour on screen is carrying information.

One accent, amber, at two temperatures: `#E8A33D` on graphite and `#9A6212`
bronze on bone. It marks action and selection only, never decoration, which
is what frees green and red to mean exactly one thing each.

- **No blue anywhere.** The neutral used for gross totals and comparison marks
  is a warm grey (`#9A948B` / `#6B665E`); a cool grey there picks up a blue
  cast next to the amber and drags the surface toward generic fintech.
- **No gradients, no glass, no glow, no drop shadows.** Depth is one 1dp
  hairline plus a tonal step. That is the entire elevation system.

### Palette

| Role | Dark | Light |
| --- | --- | --- |
| Ground | `#0B0B0C` | `#F6F5F3` |
| Panel | `#141416` | `#FFFFFF` |
| Sunk (fields, table headers) | `#1C1C1F` | `#EDEBE8` |
| Hairline | `#2A2A2E` | `#DDDAD5` |
| Rule (totals, borders) | `#3A3A40` | `#B8B4AE` |
| Text | `#F5F4F2` | `#1A1917` |
| Secondary text | `#A8A5A0` | `#5E5B56` |
| Tertiary text | `#726F6A` | `#8A867F` |
| Accent | `#E8A33D` | `#9A6212` |
| Gain | `#5FB981` | `#1F7A4D` |
| Loss | `#E5675C` | `#B3392E` |
| Neutral | `#9A948B` | `#6B665E` |

Gain and loss never carry meaning alone. Every delta pairs the colour with a
direction arrow and a sign, so the reading survives colour-blindness.

## Typography

System sans (Roboto) throughout: one family carries headings, data, labels and
prose. The decisive choice is `fontFeatureSettings = "tnum"` on **every style
except `bodyLarge`**: tabular figures are what let a column of money be compared
by eye. Without them `1,240,500` and `184,200` set to different widths and the
ledger stops aligning.

`bodyLarge` stays proportional. It is the one style reserved for running prose
(assistant replies, explanations).

Scale: display 46/36/28 · headline 26/22/19 · title 18/15/13 · body 16/14/12 ·
label 14/12/11. Tracking runs from −1.6sp on display to +0.6sp on the smallest
label; labels are set in caps by `SectionLabel`.

## Composition

The unit is the **panel**, not the card: a hairline-bounded region with a
tracked-caps header strip and rows inside it. Panels butt against one another,
so a phone carries roughly a dozen figures where card-per-metric carried four.

- Panel radius 12dp; control radius 10dp. Pills are for small controls only.
- Spacing scale 4 / 8 / 12 / 16 / 24 / 32dp. Gutters 16dp compact, 24dp medium,
  32dp expanded.
- Rows are 44dp minimum, 48dp when tappable, hairline-separated and inset to
  the text column.
- `TotalRule` is a double rule, the ledger convention for a closed total.

### The waterfall

The signature composition. Gross income, then each deduction as an indented
negative row with a bar proportional to gross and a "% of gross" caption,
closing under a double rule on a net figure marked in the accent.

One component (`WaterfallLedger`) renders at four scales: dashboard summary,
property detail, tax module, and the gross-vs-net report. One idea, four
places, never re-drawn.

## Charts

Drawn geometry, never pictures. Gridlines are hairlines at the same weight as
panel rules; the series is a single 2dp stroke; the only fill is a flat 10%
wash under the line.

- `ValueChart` draws value over time with a **scrub**: press or drag to read the
  value at any point, release to return to latest. Range selector 1M/6M/1Y/5Y/All.
  Bottom corners are labelled "Low"/"High" so they don't misread as a time axis.
- `AllocationDonut` shows share by country, region and type.
- `CashflowColumns` draws monthly columns with a real zero baseline, so negative
  months render below it.
- `MagnitudeBar` takes a `format` lambda, because the same bar carries money
  in one panel and a percentage in the next.

Every chart carries a spoken summary via `contentDescription`; a screen-reader
user gets the trend, not the pixels.

## Icons and logo

`Glyph` is a single drawn set: 24-unit grid, 1.75dp stroke, butt caps, mitre
joins. No emoji, no characters standing in for pictures, no second icon family.

The **Portico mark** is a lintel and stylobate framing three bays that rise to
different heights, the middle one in the accent. Read one way it is the
architectural portico the product is named for; read the other the bays are a
bar chart of holdings. The gap under the lintel is what makes the second
reading work. It runs from 24dp in a top bar to the adaptive launcher icon
without a separate drawing.

## Motion

One authored moment: `rememberTickFlash`. A value that changes tints its own
row for 640ms, then settles. Nothing else animates on its own.

Supporting motion is functional only: 180ms fade-through between destinations,
a single chart draw-in per data change, and the waterfall bars settling once.
The Profile "Reduce motion" preference disables reveals; the system
"Remove animations" setting is honoured before any motion runs.

## Responsive behaviour

Structural, never fluid type. Text follows the system font scale instead.

| Width | Navigation | Content |
| --- | --- | --- |
| < 600dp | Navigation bar | Single column, 2-up metric grid |
| ≥ 600dp | Navigation rail | Single column, 3-up metric grid |
| ≥ 840dp | Navigation rail | Wider working area; Admin gets its own section rail and multi-column tables |

Width is measured with `BoxWithConstraints` from the actual container, not from
screen metrics, so split-screen and freeform windows get the layout that fits
the space they were given.

Admin is reachable on a phone (single column) as well as expanded, so §27 is
demonstrable on any device.

## States

Seven reusable patterns in `ui/design/States.kt`, applied rather than
improvised per screen: loading skeleton, empty, error, success, offline,
permission denied, session expired. Plus three product-specific: plan limit
reached, assistant offline, document unavailable.

Two rules hold across all of them. **Empty states teach the next action** rather
than announcing absence. **Error states name both the problem and the way out**.
A message with no recovery is a dead end.

## Honesty

Illustrative figures are labelled where a user could mistake them for their
own: seeded properties, comparables, market signals, listings, platform
metrics, every tax rate, and every exchange rate. `TAX_DISCLAIMER` appears on every tax surface.
Pro pricing is real but sandbox-only, and every payment surface says SANDBOX.

## Quality bar

The build is finished when:

1. `assembleDebug` and `lintDebug` both pass with zero errors.
2. Cold launch shows the Portico mark before Compose content, in both themes.
3. Onboarding, sign-in, registration, verification, recovery, OAuth, demo entry
   and sign-out are all reachable.
4. Every blueprint section has a working surface, and no control is a dead-end
   snackbar.
5. Every displayed rate is computed by `Finance.kt` from records in the store,
   never stored as a constant.
6. Phone and expanded layouts preserve the same information hierarchy.
7. Both themes clear 4.5:1 on body text, touch targets are 48dp, and charts
   carry spoken summaries.
