<p align="center">
  <img src="docs/brand/portico-readme-banner.png" width="100%" alt="Portico architectural mark merging into a property-value curve" />
</p>

<h1 align="center">Portico</h1>

<p align="center">
  <strong>Real-estate portfolio intelligence for Android.</strong><br />
  Turn property records into a defensible view of value, yield, cashflow, tax and return.
</p>

<p align="center">
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-E8A33D?style=flat-square&logo=android&logoColor=white&labelColor=141416" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-E8A33D?style=flat-square&logo=kotlin&logoColor=white&labelColor=141416" />
  <img alt="Firebase" src="https://img.shields.io/badge/Data-Firebase-E8A33D?style=flat-square&logo=firebase&logoColor=white&labelColor=141416" />
  <img alt="Tests" src="https://img.shields.io/badge/Tests-42%20passing-E8A33D?style=flat-square&labelColor=141416" />
  <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/License-MIT-E8A33D?style=flat-square&labelColor=141416" /></a>
</p>

<p align="center">
  <a href="#product-story">Product</a> ·
  <a href="#product-tour">Screens</a> ·
  <a href="#capabilities">Capabilities</a> ·
  <a href="#architecture">Architecture</a> ·
  <a href="#getting-started">Get started</a> ·
  <a href="#tests">Tests</a>
</p>

<p align="center"><sub>Designed and built by Imtiaz Hossain · CSE489</sub></p>

---

## Product story

A property portfolio is not one number. It is gross rent falling through
operating costs and tax to whatever actually survives.

Portico makes that subtraction visible on every property and across the whole
register. The app pairs owner-scoped records with a financial engine that
recomputes return, yield, cap rate and cashflow from source transactions rather
than storing impressive-looking percentages.

| Financial truth | Private workspace | Local intelligence | Adaptive interface |
| --- | --- | --- | --- |
| Gross-to-net calculations reconcile to the underlying records. | Clerk sessions and owner-scoped Firestore collections isolate each account. | Portfolio questions are answered on-device; no conversation or financial context reaches a cloud model. | Phone navigation becomes a rail and wider working surface on tablets. |

<div align="center">
<img src="docs/screenshots/02-dashboard.png" width="300" alt="Dashboard showing portfolio value and performance chart" />
<img src="docs/screenshots/03-waterfall.png" width="300" alt="Gross-to-net waterfall breaking income down to net" />
</div>

The gross-to-net waterfall on the right is the product's whole argument, and it
is the same component at four scales: dashboard summary, property detail, tax
module, gross-vs-net report. Each deduction shows a bar proportional to gross
and its share as a percentage, closing under a double rule on the net figure.

**Every figure is computed.** Nothing stores a return, a yield or a cap rate;
`domain/Finance.kt` derives them from the records in your workspace. A property
you add is analysed by exactly the same rules as the seeded ones.

---

## Product tour

### Register and property

| Portfolio register | Property detail |
| --- | --- |
| <img src="docs/screenshots/04-portfolio.png" width="290" alt="Portfolio register with search, filter and sort" /> | <img src="docs/screenshots/05-property.png" width="290" alt="Property detail with value chart and return metrics" /> |
| Search, filter by country, region, type and performance, and sort, all operating on computed financials, so *sort by net yield* ranks by the same number the property page shows. | Value over time with a scrub interaction, then return and yield, the monthly chain, acquisition facts, income, expenses, documents and tax. |

### Analysis

| Allocation reports | Assistant |
| --- | --- |
| <img src="docs/screenshots/07-reports.png" width="290" alt="Allocation donuts by country, region and property type" /> | <img src="docs/screenshots/09-assistant-answer.png" width="290" alt="Assistant answering a question with the arithmetic shown" /> |
| Five reports: performance, cashflow, allocation, property comparison and gross-vs-net. Allocation converts currencies before comparing shares. | Answers questions about your own portfolio **on-device**, and shows the arithmetic. No API key ships and none is called. |

### Tax and payments

| Bangladesh tax rules | Sandbox checkout |
| --- | --- |
| <img src="docs/screenshots/06-tax-bangladesh.png" width="290" alt="Editable Bangladesh tax rates with their working shown" /> | <img src="docs/screenshots/11-checkout.png" width="290" alt="Sandbox checkout with order summary and test cards" /> |
| Every rate is a draggable assumption carrying its own working. `0.75 × 12% = 9% of gross rent` makes the 25% maintenance allowance auditable. | Card validation, processing, decline paths, receipts and billing history. Nothing is charged and no provider is contacted. |

### Entry and theme

| Onboarding | Light theme |
| --- | --- |
| <img src="docs/screenshots/01-onboarding.png" width="290" alt="Onboarding explaining the product" /> | <img src="docs/screenshots/10-dashboard-light.png" width="290" alt="Dashboard in the light theme" /> |

### Tablet and desktop-style layouts

Navigation is structural: a bottom bar on a phone, a rail from 600dp, and a
wider working area with three-column metrics from 840dp.

<div align="center">
<img src="docs/screenshots/12-tablet-dashboard.png" width="420" alt="Tablet dashboard with navigation rail" />
<img src="docs/screenshots/13-tablet-admin.png" width="420" alt="Admin platform with section rail and multi-column user table" />
</div>

The admin platform gets a desk layout, with its own section rail and real
multi-column tables, and it is reachable on a phone too, so it is demonstrable
on any device.

---

## Capabilities

| Area | What works end to end |
| --- | --- |
| **Portfolio** | Search, filter and sort; property detail; six-step property capture; edit and delete; server-enforced Free-plan limit. |
| **Financial analysis** | ROI, capital ROI, cash-on-cash, cap rate, gross/net yield, monthly/annual cashflow, value history and gross-to-net waterfall. |
| **Reports** | Performance, cashflow, allocation, property comparison and gross-versus-net analysis with cross-currency normalization. |
| **Tax** | Editable assumptions for nine jurisdictions across Bangladesh, Uruguay and Argentina, with the arithmetic attached to every rate. |
| **Documents** | Private library, Android system picker, upload/download/delete, categories, viewer and recovery states. |
| **Portico Intelligence** | On-device portfolio and property analysis, comparisons and what-if scenarios with the working shown. |
| **Valuation and acquisition** | Comparable-based estimates and a search-to-decision flow using the same finance engine as owned properties. |
| **Plans and payments** | Free/Pro rules, server-owned sandbox checkout, receipts, billing history, cancellation and resume—without charging a card. |
| **Account and workspace** | Clerk email/OAuth authentication, session management, preferences, appearance, notifications, privacy, roles and admin surfaces. |
| **Portability** | CSV export/import with duplicate detection and line-level rejection reporting. |

---

## Currency

A portfolio spanning Dhaka, Montevideo and Buenos Aires holds three currencies,
and adding those figures together without converting produces a number that
means nothing.

Each property carries its own currency. Conversion happens **once**, at the
boundary of `Finance.analyse`, so everything downstream works in a single
currency without knowing conversion exists.

Rates are expressed as units per 1 USD and are **your assumptions, not a feed**,
the same philosophy as the tax rates. A stale rate you set and can see beats a
plausible-looking one you cannot check.

> The property worth knowing: **switching display currency moves every amount
> and leaves every rate untouched.** ROI, cap rate and net yield are ratios, so
> they come out identical in taka or dollars. There is a test asserting exactly
> that.

---

## Sandbox payments

Checkout is a working server-verified sandbox flow: Luhn validation, expiry and
CVC checks, a processing state, distinct decline paths, a receipt, persisted
billing history, and cancel-at-period-end with resume. The authenticated bridge,
not the Android client, writes subscription and payment state.

**No money moves and no payment provider is contacted.** Outcomes are driven by
the published, non-functional test card numbers, tappable in the app:

| Card | Outcome |
| --- | --- |
| `4242 4242 4242 4242` | Succeeds |
| `4000 0000 0000 0002` | Card declined |
| `4000 0000 0000 9995` | Insufficient funds |
| `4000 0000 0000 0069` | Expired card |
| `4000 0000 0000 0119` | Processing error |

Any future expiry and any 3-digit code work. A typed number is matched locally
to one of these published test outcomes; only a non-sensitive outcome token is
sent to the bridge. A full number is never transmitted or persisted. Pro is
priced at $12.00/month as **sandbox pricing**, labelled as such in the UI.
Production billing replaces that outcome token with a verified Play Billing or
processor purchase token.

---

## Signing in

Three ways in:

- **Email and password** via Clerk. The workspace policy requires a
  **15-character minimum**. The app states this before you type, with a live
  character counter, and validates against it.
- **Google or GitHub**, both enabled on the Clerk instance.
- **Demo data.** A sample portfolio, no account, works fully offline.

The Clerk publishable key lives in `gradle.properties`. Without it the app
offers the demo path rather than a dead end.

Clerk's development instance also accepts test addresses of the form
`you+clerk_test@example.com` with verification code `424242`, which is the most
reliable way to demonstrate sign-up without a real inbox.

---

## Getting started

### Requirements

- Android Studio with Android SDK 36
- JDK 17 or Android Studio's bundled runtime
- Android 8.0/API 26 or newer device or emulator

### Build

```powershell
git clone https://github.com/ImtiazHossain-Eshan/Portico_Android.git
cd Portico_Android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. You can also
open the repository in Android Studio and run the `app` configuration.

The complete demo cockpit works without an account. Authenticated cloud flows
use the Clerk publishable key and Firebase bridge URL resolved from Gradle
properties or environment variables:

```properties
CLERK_PUBLISHABLE_KEY=pk_test_or_pk_live_...
FIREBASE_TOKEN_BRIDGE_URL=https://your-bridge.vercel.app/api/firebase-token
PORTICO_FILE_API_URL=https://your-bridge.vercel.app/api/files
```

`app/google-services.json` enables Firebase Android services when present. Keep
service-account credentials and private backend keys out of the APK and Git.

---

## Tests

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" gradle :app:testDebugUnitTest
```

42 JVM tests cover the four things most worth locking down:

| Suite | Covers |
| --- | --- |
| `FinanceTest` | The gross-to-net chain reconciling, cap rate excluding tax where net yield does not, negative cashflow surviving unclamped, recorded tax replacing the jurisdiction assumption rather than stacking, portfolio rates recomputed from totals rather than averaged |
| `ExchangeTest` | Round trips, cross-currency routing through the base, mixed-currency aggregation, and the invariance property above |
| `SandboxPaymentTest` | Mod-10 validation, expiry and CVC rules, every decline branch, and that the full card number never reaches a persisted record |
| `PorticoImportTest` | Quoted commas, header-order independence, duplicate skipping, and rejected rows reported with their line number |

---

## Architecture

```
domain/     Models, Finance, Tax, Exchange, Payments, Analyst, Seed
            pure Kotlin, no Android dependencies, fully unit-tested
data/       PorticoStore: single owner of state, offline cache + cloud sync
            FirestoreWorkspaceRepository: normalized owner-scoped records
            PorticoBackend: authenticated quota, billing and push APIs
            PorticoFiles: authenticated private document transfer
            PorticoExport / PorticoImport: CSV round-trip
ui/theme/   Colour roles, semantic extensions, tabular-figure type scale
ui/design/  Panels, rows, charts, icons, logo, the seven state patterns
ui/screens/ One file per product area
```

Entities follow the schema delivered in Milestone 1: user, portfolio,
financial, document, subscription, enterprise, AI, external data, tax and system
domains.

---

## What is real and what is illustrative

**Real.** Every return, yield, cap rate, cashflow and tax figure is computed by
`domain/Finance.kt` and `domain/Tax.kt` from records in your workspace.
Everything persists across restart through an account-scoped DataStore cache and
normalized Firestore collections. Clerk authentication, realtime cloud sync,
private document upload/download/delete, server-enforced property quotas,
server-owned sandbox subscriptions, FCM Installation-ID registration and delivery, CSV
import/export, and session revocation all hit real systems. Existing schema-v1
workspace snapshots migrate to the normalized schema on authenticated sync.

**Illustrative, and labelled as such in the app.** The three seeded properties,
comparable properties, market signals, acquisition listings, organisation
members, audit entries and admin platform metrics. Every tax rate and every
exchange rate is an assumption you can edit.

**Not connected.** Real payment processing, cloud AI and external
property data remain integration seams. Checkout is intentionally labelled
sandbox and never charges a card. Portico Intelligence is intentionally
on-device: no backend AI endpoint exists, and portfolio context, questions and
conversation history are not sent to an external model.

### Cloud data layout

Every member record is isolated below `users/{clerkUserId}`. Domain entities use
independent collections (`properties`, `income`, `expenses`, `valuations`,
`documents`, `activity`, `notifications`, `conversations`, and `payments`) and
profile/preferences/tax/exchange/subscription records live in `settings`.
Firestore's root user document carries schema version, revision and counts only.
Subscription, receipts, quota counters and FCM installation records are
server-owned; Firestore rules deny Android writes to those paths and deny direct
property creation so the bridge can enforce the Free limit atomically.

Document metadata is stored in Firestore; file bytes are stored in a private
Vercel Blob store. Android never receives Firebase service credentials or a Blob
token. Both APIs verify the current Clerk JWT and derive the owner path on the
server.

---

## Language

The app ships in English only.

There was a language selector offering Spanish and Portuguese; it changed a
preference and nothing else, which is precisely the kind of control that only
reports rather than does. It has been removed rather than left as decoration.

Doing it properly means 628 distinct strings (421 labels, 207 sentences)
extracted and translated, and financial terminology is not somewhere to guess.
The seam is clean if you want it: strings are inline in the composables, so
extraction to `strings.xml` plus a `values-bn` or `values-es` set is mechanical.

---

## Design record

- Durable product truth: [PRODUCT.md](PRODUCT.md)
- Implemented visual system: [DESIGN.md](DESIGN.md)
- Direction contract: the header comment in `ui/PorticoApp.kt`
- Milestone 1 wireframes and schema: `diagrams/`

---

## Project and license

Portico was created by **Imtiaz Hossain** (`23101137`) for **CSE489**. The source
is available under the [MIT License](LICENSE).
