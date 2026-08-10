# Portico Android

Portico is a responsive Jetpack Compose real-estate investment portfolio tracker. It is built around a surveyor's field-book visual system so portfolio value, net return, cashflow, tax impact, and the next action remain clear on a phone or tablet.

## Included product surface

- Dashboard with portfolio snapshot, value trend, financial summary, holdings, and recent activity.
- Portfolio register with search, filters, property rows, and detailed property views.
- Six-step add-property flow with purchase, income, expense, ROI, cap-rate, yield, cashflow, and review states.
- Reports for performance, cashflow, allocation, comparison, and gross-versus-net analysis.
- Private document library with upload, categories, and PDF viewer states.
- Tax bridge for Uruguay and Argentina assumptions.
- Portico intelligence assistant with property/portfolio/scenario context and demo responses.
- Valuation and acquisition lab flows with explicit synthetic-data labeling.
- Profile, appearance, security, privacy, subscription, and enterprise workspace surfaces.
- Responsive Material navigation bar on compact widths and navigation rail at 600dp+.

## Run

Open this folder in Android Studio and run the `app` configuration on an emulator or Android device.

From PowerShell, with Android Studio's bundled JDK available:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\IHE_OWN\AppData\Local\Android\Sdk"
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.5.0-bin\bvnork1r7n8i6kp5cnkibsc9q\gradle-9.5.0\bin\gradle.bat" :app:assembleDebug
```

The generated APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Real account access with Clerk

The login and sign-up routes use a custom Portico Compose surface backed by Clerk's native Android API. Email/password sign-in, account creation, email verification, password recovery, MFA, and the native session stay inside the app. Portico only receives the active session and gates the protected workspace from it.

Before running the app:

1. Create or open a Clerk application.
2. Enable the Native API and add the Android package `com.portico.android` in Clerk.
3. Put the publishable key in Gradle properties or the environment:

```properties
CLERK_PUBLISHABLE_KEY=pk_test_your_key_here
```

The key is intentionally not committed to this repository. Never place a Clerk secret key in the Android app. If the key is absent, the app shows a setup state instead of pretending that demo credentials are real.

## Product boundary

The app is intentionally usable offline with a local demo repository. Clerk account access is real when configured; portfolio records, documents, tax assumptions, assistant responses, and external property providers remain local illustrative data until their production backends are connected.

## Design record

- Durable product truth: [PRODUCT.md](PRODUCT.md)
- Implemented visual system: [DESIGN.md](DESIGN.md)
- Impeccable sidecar: [.impeccable/design.json](.impeccable/design.json)

Imtiaz Hossain · 23101137
