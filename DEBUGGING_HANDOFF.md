# DASHBOARD RENDER BUG — DEBUGGING HANDOFF

## Symptom (confirmed via screen recording, not just screenshots)
On the Dashboard screen, everything renders correctly UP TO AND INCLUDING
the Sync Ratio panel (header, "PERSONAL OPS" title, the panel itself,
"0%"/"STATUS"/"CRITICAL" text, the yellow/black hazard stripe at the
bottom of that panel). Everything below that point — the zone tabs
(DAILY/WEEKLY/STANDING/MACROS/SESSIONS), all directive cards, the
Mindset panel, the Notebook/Settings buttons, the version footer — is
completely absent. Not invisible-but-present: confirmed via screen
recording that taps in that area produce zero app response (no
navigation, no visual change), only the OS-level "show taps" touch
indicator appears, which is unrelated to the app.

No crash. No ANR dialog observed. No exception in logcat (per user,
though a full adb logcat capture has NOT yet been done — see "Next
steps" below, this is the single most valuable missing piece of data).

## Ruled out, with evidence
1. **ConsoleFrame (beige housing wrapper)** — removed entirely from
   MainActivity's setContent tree. Bug persisted identically. Not the
   cause. (Commit df8e667, reverted back in 4e67c18 group)
2. **weight(1f) constraint propagation** — PermissionFlowScreen uses
   zero weight() calls anywhere and exhibited the same "renders once
   then nothing" pattern in earlier testing before this specific
   Dashboard bug was isolated, which argues against weight() being a
   universal cause, though this wasn't tested as rigorously as the
   ConsoleFrame removal.
3. **Tab content styling (Box-wrapped conditional background vs. bare
   Text)** — reverted to the simpler bare-Text version as a test,
   reverted back after it made no difference. Not the cause.
4. **HazardStripe infinite/non-terminating loop** — the two-tone stripe
   draw loop had no protection against a non-advancing `x` variable.
   Added coerceAtLeast(1f) on stripeW plus a hard iteration cap
   (guard < 2000). This is a real defensive fix and should stay, but
   it did NOT resolve the symptom when tested. Not the (sole) cause.
5. **HazardStripe call removed entirely from EvaPanel** — isolation
   test, temporarily deleted the specific HazardStripe(height = 7.dp)
   call inside EvaPanel's content. Bug persisted. Ruled out that
   specific call as sufldee cause (though see #6, the surrounding
   EvaPanel structure was still suspect).
6. **EvaPanel circular Box sizing (Canvas + content both fillMaxSize
   inside an unbounded-height Box)** — found via fresh close reading
   of EvaPanel.kt. Real bug: outer Box had no explicit height, two
   children (a Canvas drawing the shadow wedge, and content()) both
   demanded fillMaxSize() of a size neither was determining — a
   genuine circular measurement dependency. Fixed properly using
   Modifier.matchParentSize() (BoxScope-specific, verified against
   official Android docs at developer.android.com/develop/ui/compose/
   modifiers — matchParentSize means "measure this child LAST, using
   whatever size the Box already resolved from other children," which
   is the textbook-correct pattern for exactly this situation).
   CONFIRMED TESTED after fixing an unrelated compile error (bad
   import — matchParentSize is a BoxScope member, not a top-level
   importable symbol). Bug persisted even with this fix in place and
   compiling cleanly. This was the most promising theory and it still
   didn't resolve it.

## What this means
Two independent, real, verifiable bugs were found and fixed inside
EvaPanel (HazardStripe's loop, the circular Box sizing), and neither
fixed the actual symptom. This strongly suggests either:
  (a) there's a THIRD bug, still inside EvaPanel or its immediate
      surroundings, not yet found, OR
  (b) the actual cause is NOT inside EvaPanel at all, and the
      isolation testing that pointed there (diagnostic Text markers
      placed at specific points in DashboardScreen.kt) had a flaw
      similar to the first invalid marker test (see git log entry
      "Corrected diagnostic" for an example of a marker being placed
      wrong and giving a false signal).

## Next steps, in order of expected value
1. **Get a real adb logcat capture.** This is the single highest-value
   missing piece of data. Connect the phone to a computer via USB,
   enable USB debugging in Developer Options, run
   `adb logcat | grep -i -E "rebuild|AndroidRuntime|FATAL|ANR"` while
   opening the app. Even a caught-and-suppressed exception, a
   "Skipped frames" warning, or a Compose recomposition warning would
   likely end the guessing entirely. Every fix so far has been
   reasoned from static code reading with zero runtime verification
   available in the environment doing the fixing.
2. **Re-verify the diagnostic marker methodology.** The two marker
   tests done so far (commits 09da837 and 0eb75ef) each had the risk
   of being placed at a point that itself depends on something broken
   upstream. A more rigorous version: add markers at EVERY major
   composable boundary in DashboardScreen.kt in a single build (header
   done / Sync Ratio EvaPanel entered / EvaPanel content started /
   EvaPanel content finished / EvaPanel exited / nfcDisabled check
   done / tab row entered / tab row finished / when-block entered),
   each a distinctly colored/numbered Text, so a single test run
   narrows the exact failure point instead of one boundary per round-trip.
3. **Try an emulator if at all possible**, even a slow one, in
   whatever environment picks this up next — actual step-through
   debugging or even just Android Studio's Layout Inspector would
   likely resolve this in minutes versus the many rounds spent here.
4. **Consider reverting DashboardScreen.kt to the 739b52d version
   wholesale** (last confirmed fully-working state, before any EVA
   theme work) and re-applying ONLY the visual/color changes without
   touching structure, rather than continuing to debug the current
   structure. This trades "keep the exact mockup-matching layout" for
   "definitely working," and could have the new EvaPanel/CelOutlineText
   components re-applied more incrementally, one at a time, with an
   actual working build confirmed between each addition.

## Repo state
Last commit as of this writing: 1b40cbe (matchParentSize import fix).
All work is pushed to origin/main. No uncommitted changes.
