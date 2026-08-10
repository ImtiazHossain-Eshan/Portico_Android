---
name: Portico Android
description: A responsive warm editorial desk for real-estate portfolio decisions.
visual-world: Off-white paper / orange signals / brown dark mode
platform: Android native / Kotlin / Jetpack Compose / Material 3
---

# Portico design system

## North star

Portico is a flight deck for property capital. The investor should be able to scan the portfolio orbit, understand the instruments, and move from a number to the property, document, report, tax view, or scenario that explains it.

This is a full visual replacement of the former field-book direction. The product truth stays the same: property register, six-step capture, return and cashflow calculations, reports, documents, Uruguay/Argentina tax context, valuation, acquisition, AI assistant, subscription, enterprise, and profile controls remain reachable.

## Visual world

- Off-white paper surfaces with terracotta/orange decisions, not generic fintech gradients.
- Faceted property/portfolio geometry, orbital rings, instrument arcs, and measured grid lines provide the 3D vocabulary.
- Orange marks primary actions, positive movement, and completed state.
- Warm brown marks comparative or analytical state.
- Dark mode uses coffee-brown surfaces with lighter orange action contrast.
- Critical red is reserved for destructive/error states.
- Material 3 supplies Android-native navigation, fields, buttons, chips, dialogs, safe insets, and accessibility semantics.

The signature interaction is the portfolio scan: the faceted orbit rotates slowly while the portfolio value and three instrument gauges remain readable. Motion is spatial explanation, not decoration.

## Tokens

### Warm palette

| Role | Token | Value | Use |
| --- | --- | --- | --- |
| Background | `PorticoPaper` | `#F8F1E7` | Light canvas and launch surface |
| Surface | `surface` | `#FFFCF7` | Opaque cards and auth surfaces |
| Surface variant | `PorticoMist` | `#EDE2D4` | Cards, gauges, chips, navigation |
| Primary | `PorticoOrange` | `#C85A20` | Primary action, selected state, positive value |
| Secondary | `PorticoLilac` | `#8B5E45` | Analysis, comparisons, range markers |
| Tertiary | warm ochre | `#A86F32` | Cost, caution, demo/local markers |
| Text | `PorticoInk` | `#2A1D17` | High-priority readable text |
| Muted | `PorticoMuted` | `#78685D` | Supporting information and timestamps |
| Rule | `PorticoLine` | `#D7C6B5` | Geometry, separators, grid lines |
| Critical | `PorticoCritical` | `#B33B2E` | Destructive/error states only |

Light mode is the default: it is calm, paper-like, and orange-led. Dark mode is a brown reading environment rather than a blue-black inversion.

## Typography

Android `SansSerif` is used with a tight hierarchy and strong weight contrast. Financial values are bold and large; labels are compact and tracked; supporting copy remains readable at Dynamic Type sizes. No monospace costume or gradient text is used.

- Display: 42sp / 46sp, semibold, for dominant portfolio values.
- Display medium: 34sp / 40sp, semibold, for secondary anchors.
- Headline: 24–30sp, semibold, for screen theses.
- Title: 20sp / 26sp, semibold, for sections and property names.
- Body: 14–16sp, regular, for explanation and decision context.
- Label: 11–14sp, medium, for controls, instruments, and navigation.

## Geometry and depth

- Page gutters: 20dp on compact width; wider content breathes inside the expanded layout.
- Panel radius: 20–24dp for primary cockpit surfaces; 16–20dp for secondary surfaces.
- Instrument arcs and plot previews use crisp Canvas geometry with semantic descriptions.
- Depth is expressed through tonal planes, restrained 1dp borders, and small native shadow elevation on emphasized surfaces.
- Buttons use clean, opaque Material 3 fills and outlines with restrained press elevation; large content surfaces remain legible and opaque.

## Navigation and responsive behavior

- Compact width uses a five-destination Material NavigationBar: Overview, Portfolio, Reports, Assistant, Profile.
- Expanded width at 600dp and above uses the same destinations as a NavigationRail and gives the content a wider working area.
- Child surfaces use a contextual top app bar and a system-compatible back action.
- Horizontal action rows scroll instead of forcing fragile grids.
- Safe drawing and navigation-bar insets are respected; controls use Material minimum touch targets.
- The six-step Add Property workflow remains a vertical, keyboard-safe sequence on phone and a wider working surface on expanded layouts.

## Icon and logo system

The Portico mark is a faceted architectural portico/facade core crossed by two orbital rings and finished with a single signal node. It appears in the Android launcher, launch splash, splash screen, top bar, assistant, and profile surfaces.

`PorticoGlyph` is the custom canvas icon language used for primary navigation, controls, commands, and system actions. Glyphs are geometric, high-contrast, and semantic: orbit, portfolio, reports, assistant, profile, add, documents, tax, valuation, back, search, forward, income, expense, location, check, security, and more.

## Motion

- Logo orbit: slow, continuous rotation during entry and key cockpit surfaces.
- Portfolio orbit: bounded 3D `graphicsLayer` tilt plus slow Canvas rings and property nodes.
- Trend chart: a one-time line reveal on report entry, ending on the latest point so the motion explains chronology.
- Button press: native Material elevation and touch feedback keep actions tactile without decorative stripes or overlays.
- Route change: Material-style crossfade, 320ms.
- Entry flow: splash handoff, onboarding fade-through, and explicit state transitions.
- Assistant: short loading interval so the state change is legible; local answer appears with a clear offline label.
- System animation scale and the Profile reduced-motion preference disable continuous orbit and shorten transitions.

Motion is kept on transforms/alpha/Canvas geometry and does not animate long lists. The first viewport remains usable if all animation is disabled.

## States and accessibility

Every major surface carries its required state language: local/demo, empty, loading, error/recovery, success, permission/privacy, and offline. Financial examples are labeled illustrative.

Interactive controls expose content descriptions, use Material components where appropriate, and retain 48dp targets. Charts and orbital views have semantic descriptions. Password fields use secure transformations. The assistant explicitly says when it is offline and keeps demo responses on-device.

## Quality bar

The app is not considered finished until:

1. Cold launch shows Portico branding before Compose content.
2. Onboarding, sign-in, registration, verification, demo entry, and sign-out are reachable.
3. Dashboard, portfolio, property detail, add-property steps, reports, documents, tax, assistant, valuation, acquisition, subscription, enterprise, and profile routes are reachable.
4. Build and lint pass with no errors.
5. Phone and expanded layouts preserve the same information hierarchy.
6. Reduced motion, offline, demo-data, and sensitive-data copy remain explicit.
