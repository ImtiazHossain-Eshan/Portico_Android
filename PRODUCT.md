# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

delegated: native Android with Kotlin and Jetpack Compose; a local demo repository provides complete offline UX while keeping a clean seam for Firebase-backed data later.

## Users

Inferred from the supplied blueprint: individual real-estate investors and small investment teams who need to understand portfolio value, property performance, cashflow, yield, taxes, documents, and acquisition opportunities from a phone.

## Product Purpose

Portico is a real-estate investment portfolio tracker. It gives an investor one place to monitor holdings, enter and analyze properties, track income and expenses, understand net returns, manage private documents, review reports, and explore AI-assisted investment questions. Success means a user can move from “how is my portfolio doing?” to a defensible next action in minutes.

## Positioning

Portico connects property operations to investor-grade financial interpretation: it makes gross income, expenses, taxes, net income, yield, ROI, and cashflow legible together at portfolio and property level instead of treating properties as a list of addresses.

## Operating Context

The primary use scene is mobile, often between property decisions or during a financial review. Users may switch between dashboard scanning, property entry, income/expense recording, document lookup, reporting, tax review, AI scenario analysis, valuation, and acquisition research. Mobile-first layouts must also adapt gracefully to larger phones and tablets.

## Capabilities and Constraints

- Dashboard: portfolio value, invested capital, total return, ROI, performance trend, financial summary, properties, and recent activity.
- Portfolio and property management: search, filter, sort, property details, performance, income, expenses, taxes, documents, valuation, and editing.
- Add-property flow: property, purchase, income, expenses, analysis, and review steps with calculations.
- Reports: performance, cashflow, allocation, property comparison, and gross-versus-net analysis.
- Documents: private library, upload flow, categories, viewer states, and recovery states.
- Tax: gross income → expenses → taxes/fees → net income → net yield → net ROI; country support covers Bangladesh, Uruguay and Argentina, and the rules table takes more without touching the property model.
- AI assistant: portfolio/property/scenario context, suggested questions, financial analysis, conversation history, loading, error, offline, and disclaimer states.
- Valuation and acquisition: property inputs, estimate/comparable views, market signals, search-to-decision flow.
- Profile/settings: currency, tax jurisdiction, language, notifications, appearance, security, privacy, subscription, logout, and account deletion.
- Subscription and enterprise: Free/Pro plan comparison and future organization, roles, permissions, and data-export surfaces.
- The current build is an offline-capable product demonstration. Firebase Authentication, Firestore, Storage, Functions, Messaging, Analytics, Crashlytics, and external data providers remain integration seams rather than shipped services.
- Financial examples in the UI are clearly marked as sample/demo data and are not financial advice.

## Brand Commitments

The product name is Portico. The supplied brief requires a premium, financial, intelligent, minimal, trustworthy, modern experience with original visual identity. The provided Figma design is optional reference, not a binding implementation source.

## Evidence on Hand

- Product blueprint: `Project Details/Real Estate Investment Portfolio Platform Blueprint.docx` and `.pdf`.
- Wireframe and schema references: `diagrams/Wireframe_diagram_portico_Imtiaz Hossain.pdf` and `diagrams/Schema_diagram_portico_Imtiaz Hossain.pdf`.
- Original Figma reference link is recorded in `diagrams/Links_diagram (Optional)/wireframe_diagram_figma_link.txt`.
- No production API, Firebase project, real user data, commercial pricing, or verified market-data provider credentials were supplied; do not fabricate those claims.

## Product Principles

- Make financial truth scannable before making it beautiful.
- Separate gross performance from net performance at every meaningful level.
- Keep the next action obvious and reversible.
- Treat documents and financial data as private, permissioned records.
- Make future integrations replaceable without coupling the UI to a provider.

## Accessibility & Inclusion

The app must use accessible labels and logical focus order, preserve at least 48dp touch targets, support readable type scaling, maintain sufficient contrast, avoid color-only meaning, provide chart summaries, honor system light/dark preferences, and expose recovery for loading, empty, error, offline, permission-denied, and expired-session states.
