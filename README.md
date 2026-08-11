# Portico

A real-estate investment portfolio platform for Android, built with Kotlin and
Jetpack Compose against the Milestone 1 blueprint in `Project Details/`.

Portico's argument is that a portfolio is not one number: gross rent falls
through operating costs and tax to whatever actually survives. Every screen is
built around making that subtraction visible, per property and across the
register.

Imtiaz Hossain · 23101137 · CSE489

## What's in it

**Portfolio** — register with search, filter and sort; property detail with
overview, income, expenses, documents and tax tabs; six-step property capture
with live analysis; edit and delete.

**Analysis** — ROI, capital ROI, cash-on-cash, cap rate, gross and net yield,
and monthly/annual cashflow, all computed from your records. Reports for
performance, cashflow, allocation, property comparison and gross-vs-net.

**Tax** — a jurisdiction engine for Uruguay (Montevideo, Canelones, Maldonado)
and Argentina (CABA, Buenos Aires, Córdoba). Every rate is an editable
assumption with its own note, not a filed figure.

**Documents** — private library with a real system file picker, category
filing, viewer, and states for uploading, failed, unavailable and restricted.

**Assistant** — answers questions about your own portfolio on-device, with the
arithmetic shown. Handles extremes, comparisons, tax share, allocation, and
what-if scenarios on rent or costs. No API key ships and none is called.

**Valuation and acquisition** — comparable-based estimate against purchase
price and recorded value; a search-to-decision flow that runs the same engine
on a property you don't own yet.

**Account** — preferences, currency, jurisdiction, appearance, reduce-motion,
notifications, security, privacy, data export and erase, plans, workspace with
roles and permissions, and a six-section admin platform.

## Running it

Open the folder in Android Studio and run the `app` configuration.

From a shell, with Android Studio's bundled JDK:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ANDROID_HOME="$HOME/AppData/Local/Android/Sdk" gradle :app:assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Signing in

Three ways in:

- **Email and password** via Clerk. Note the workspace policy requires a
  **15-character minimum** — the app states this before you type and validates
  against it.
- **Google or GitHub**, both enabled on the Clerk instance.
- **Demo data** — a sample portfolio, no account, works fully offline.

The Clerk publishable key lives in `gradle.properties`. Without it the app
offers the demo path rather than a dead end.

Clerk's development instance also accepts test addresses of the form
`you+clerk_test@example.com` with verification code `424242`, which is the
most reliable way to demonstrate sign-up without a real inbox.

## What is real and what is illustrative

Real: every return, yield, cap rate, cashflow and tax figure is computed by
`domain/Finance.kt` and `domain/Tax.kt` from records in your workspace. Add a
property and its analysis is produced the same way as the seeded ones.
Everything persists across restart via DataStore.

Illustrative, and labelled as such in the app: the three seeded properties,
comparable properties, market signals, acquisition listings, organisation
members, audit entries and admin platform metrics. Pro pricing is marked
"Price not set" rather than invented.

Not connected: any backend. Firebase, cloud document storage, payment
processing, push delivery and external property data are integration seams, not
shipped services. Records never leave the device.

## Architecture

```
domain/     Models, Finance, Tax, Analyst, Seed — no Android dependencies
data/       PorticoStore: single owner of state, persists to DataStore
ui/theme/   Colour roles, semantic extensions, tabular-figure type scale
ui/design/  Panels, rows, charts, icons, logo, the seven state patterns
ui/screens/ One file per product area
```

Entities follow the schema delivered in Milestone 1 — user, portfolio,
financial, document, subscription, enterprise, AI, external data, tax and
system domains.

## Design record

- Product truth: [PRODUCT.md](PRODUCT.md)
- Implemented visual system: [DESIGN.md](DESIGN.md)
- Sidecar: [.impeccable/design.json](.impeccable/design.json)
- Direction contract: the header comment in `ui/PorticoApp.kt`
