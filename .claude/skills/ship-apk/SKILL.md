---
name: ship-apk
description: Get an Action Tracker change to Sona as an installable APK - draft PR, green "Build APK" run, artifact link, and whether it is safe to install. Use when finishing any app change.
---

# Ship a change as an APK

Sona tests every change by sideloading the CI-built APK on the S25 Ultra, installing it over the
current app. A build the phone can't open (lower DB version) or that signs with the wrong key
(broken Google sign-in) is the main thing to avoid.

1. **Branch** from the latest `origin/main`; target `main`. Don't base a PR on another PR's branch.
2. **Check locally what you can.** Cloud sessions have no Android SDK, so Gradle can't build here.
   Re-read the diff for compile errors, missing imports and unescaped apostrophes in `strings.xml`.
3. **Open a draft PR** and watch the "Build APK" workflow (`.github/workflows/build.yml`): it runs
   `gradle :app:testDebugUnitTest` then `gradle :app:assembleDebug`. Fix failures from the job log
   and push again until green.
4. **Find the APK**: on the green run, the artifact is `action-tracker-debug-apk` (a zip holding
   `app-debug.apk`). Link the workflow run page; artifacts need a GitHub sign-in to download.
5. **Say whether it is safe to install**:
   - Safe when the branch's `DB_VERSION` is greater than or equal to the newest version Sona may
     already have installed (normally `main`'s), and the run used the signing secrets (the log
     must not say "No signing secrets found").
   - Not safe if `main` has moved to a higher `DB_VERSION` since the branch was cut: merge `main`
     in first, then rebuild.
   - If the schema changed, say that after installing, older APKs can't be installed over it.
6. **Reply** with the PR link, the APK link, one line on what to try on the phone, and the
   safe-to-install verdict. Update `README.md` in the same PR when the change is user-visible.
