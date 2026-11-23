# LedgerCalc

A calculator that keeps its working.

Every value you enter is written on its own line with its operator, applied strictly top to bottom,
and re-totalled under a rule — like a paper tape from a till. The point is not to save keystrokes;
it is to make the arithmetic checkable afterwards, by you or by whoever you send it to.

```
  200.00
+ 300.00
−  50.00
```
`----------`
`= 450.00`

## What it does

- **Tape arithmetic** — rows apply in order, no hidden precedence. `200 + 300 × 2` is `1,000.00`,
  because that is what you see on the tape.
- **Exact money** — amounts are `BigDecimal`, every committed row is quantised to two decimals, so
  the rows you read always add up to the total you read.
- **Correctable** — tap any row to change it; the tail re-totals. Undo goes all the way back past
  `=`, and past a clear.
- **Brackets and percent** — `(30 × 2)` keeps its working beside the amount; `+ 10%` applies to the
  running total, while `× 10%` is a plain fraction.
- **History** — every finished tape is kept, newest first, and can be reopened, continued or
  deleted (deletion is undoable).
- **Export** — share the tape as aligned plain text, or as a paginated PDF.

## Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | 25 | `gradle/gradle-daemon-jvm.properties` pins the toolchain, so Gradle downloads it on first run — the first build needs network access to `api.foojay.io`. |
| Android SDK | platform matching `compileSdk 37` | Installed by Android Studio, or via `sdkmanager`. |
| Gradle | 9.8.2 | Provided by the wrapper; do not install it yourself. |
| AGP | 9.5.0 | Declared in `gradle/libs.versions.toml`. |

Open the project root in Android Studio (Ladybug or newer) and let it sync, or build from the
command line.

## Build and test

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:lintDebug              # Android lint
```

There are no instrumentation tests yet; `testInstrumentationRunner` is declared and the
`androidTest` source set is empty. If you add one, `androidx.test` dependencies are still needed.

## How the model works

The calculation model is deliberately separate from Android so it can be tested without a device.
Everything the app knows about arithmetic is in `domain/` and is pure:

- `TapeEngine` — every key press as a function `(Tape) -> Tape`. No mutable state, no callbacks.
- `Tape` / `TapeRow` — an immutable tape and its rows; the total is derived, never stored.
- `Amounts` — rounding and formatting rules, in one place.
- `Expression` — a recursive-descent parser used only inside a bracket group.
- `HistoryBook` — when a tape becomes a history entry, and when it stops being one.

`ui/TapeViewModel` is the only bridge: it turns key presses into engine calls and persists the
result through `TapeRepository`.

## Where things live

```
app/src/main/java/com/deva/ledgercalc/
├── domain/      # arithmetic, tape state machine, history rules — no Android types
├── data/        # SharedPreferences codec, text/PDF export, version info
├── ui/          # Compose screens, keypad, theme
└── MainActivity.kt
app/src/test/java/com/deva/ledgercalc/
├── domain/      # Amounts, Expression, TapeEngine, HistoryBook (reconcile)
└── data/        # TapeText, TapeCodec round trips
```

## Your data

The tape and its history live only in this app's private storage. There is no account, no network
permission, and no analytics.

- **Cloud backup is off** (`app/src/main/res/xml/data_extraction_rules.xml`): a ledger is a
  financial record and the app never asked permission to upload it.
- **Device-to-device transfer is on**, so moving to a new phone keeps your history.
- Text and PDF exports go wherever the share sheet sends them — that is under your control, not the
  app's.

If you need a portable copy, use *Share as text* or *Share as PDF*.

## Contributing

- Keep `domain/` free of Android types. If a rule needs testing, it belongs there.
- Add a unit test with each behaviour change; the arithmetic rules are cheap to test and expensive
  to get wrong. `./gradlew :app:testDebugUnitTest` should pass before a PR.
- Formatting follows the official Kotlin style (`kotlin.code.style=official`); `.editorconfig` holds
  the indent settings.
- Commit messages: a short imperative subject line, and a body explaining *why* when the change is
  not obvious.

## License

MIT — see [LICENSE](LICENSE).
