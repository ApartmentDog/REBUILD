# REBUILD — Android Project

Straight talk before you open this in Android Studio: this is a complete source tree covering
the full architecture (data layer, Sync Ratio math, all four zones, PIN lock, notebook, NFC
registration + scanning, geofencing with delayed arrival check, notification actions, settings/
directive editing). It has **not been compiled** — I don't have an Android SDK/Gradle environment
here to build and test it, so treat first build as a debugging pass, not a "should just work"
moment. Below is exactly where I'd expect friction, updated after a follow-up pass that closed
most of the gaps from the first version of this doc.

## What's fully wired and should work as-is
- Room database (Directive, Completion, Streak, NfcTag, NotebookEntry, GeofencePlace, SyncHistory)
- Seed data — your actual 16 items + Posture Protocol + Voice Protocol + gated Fry Scream Basics
- Sync Ratio calculation (70% weighted daily/weekly completion + 30% standing streak health)
- Dashboard UI — four zones, checkboxes, streak rows with HOLD/BREAK + optional note
- Notebook — add/view/delete freeform entries
- PIN lock — set on first use, hashed (not plaintext), full lock on every open
- NERV terminal theming — colors, terminal type reference, segmented Sync Ratio bar
- **NFC tag registration screen** — tap an unregistered tag anywhere in the app, it
  routes you to a dialog to label it and link it to a Daily directive; registered tags
  auto-confirm on future taps without opening the app to a specific screen
- **Geofence place setup screen** — "use my current location" flow to set Home,
  registers the geofence immediately on save
- **Settings screen** — add/edit/delete directives, with links to NFC Tags and Locations
- **Delayed arrival check** — entering the home geofence now enqueues a 5-minute
  WorkManager job that checks for an NFC scan before deciding whether to notify,
  instead of firing instantly (this was the biggest logic gap in the first pass)
- **Notification action buttons** — DONE and REMIND ME LATER on the arrival nudge
  are wired to a real receiver; REMIND ME LATER re-schedules the same check 15
  minutes out via WorkManager
- **Notification icon** — a simple vector asset now exists at
  `res/drawable/ic_notification.xml` (bracket/reticle motif, flat white per Android's
  notification icon requirements)

## What's still genuinely open
- **Monospace font file** — `Theme.kt` now references `R.font.share_tech_mono`
  properly (not silently falling back anymore), but the actual `.ttf` binary isn't
  bundled — I can't fetch font files in this environment. **This will fail to compile
  until you add the file.** Get Share Tech Mono or JetBrains Mono free from Google
  Fonts, save as `app/src/main/res/font/share_tech_mono.ttf` (lowercase, underscores
  only), done. If you want something that compiles today with zero extra steps, swap
  that one line back to `FontFamily.Monospace` as a placeholder.
- **Weekly-due filtering** — seed data uses `Calendar.DAY_OF_WEEK` (Wednesday = 4).
  Double check this matches expectations once running; day-of-week off-by-ones are
  a classic first-run surprise.
- **Arrival check is simplified** — it currently checks "has any NFC scan with
  source='nfc' landed today," not specifically "since arrival" or "for the wallet/keys
  directive specifically." Good enough to test the flow; tighten later if it's
  triggering falsely (e.g. if you scanned wallet at 8am, it won't nudge you at 6pm
  even if you left and came back without rescanning).
- **Everything from the "huge list of suggestions"** (streak heatmap, trend charts,
  export, morning/end-of-day summaries, adjustable weighting, timers in protocol steps,
  photo/audio logs, widget, damage-state visuals) is **still not implemented** — logged
  in the design doc, intentionally out of scope for this pass. The architecture should
  make adding most of them straightforward later.

## First build checklist
1. Open in Android Studio (Koala or newer recommended for compose/kotlin versions used)
2. Add the font file (see above) — this is the one thing guaranteed to block first compile
3. Let Gradle sync, fix remaining compile errors one at a time — likely candidates:
   import mismatches, Compose BOM version drift, `PendingIntent` flag requirements
   if you drop minSdk below 23
4. Run on a device/emulator with NFC if you want to test tag scanning (most emulators
   don't support NFC — you'll likely need a real device for that part)
5. Background location permission on Android 11+ requires the two-step flow already
   built into `PermissionFlowScreen` — Android will reject a direct background
   location request that isn't preceded by foreground grant
6. Test the full loop once running: Settings → Locations → save Home while physically
   there → Settings → NFC Tags → tap a blank tag → label it "Wallet Tray" → link to
   "Wallet + Keys In Place" → leave and re-enter the geofence to see the notification
   timing in action

## Files by concern
- `data/` — Room entities, DAOs, database, repository (Sync Ratio logic lives here), seed data
- `ui/` — Compose screens (Dashboard, Notebook, Settings, NFC registration, Locations,
  PIN lock, permission flow), ViewModel, theme/typography/colors
- `security/` — PIN hashing and storage
- `nfc/` — tag scan handling
- `location/` — geofencing setup and broadcast receiver
- `notification/` — notification channel, content, action receiver, delayed-check worker

Ping me with the actual error output when you hit build failures — that'll be far more useful
than me guessing further from here.
