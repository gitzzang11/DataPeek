# Releasing DataPeek

Release signing material is kept outside this repository:

- Key: `%USERPROFILE%\.datapeek\signing\datapeek-release.jks`
- Properties: `%USERPROFILE%\.datapeek\signing\signing.properties`

The same key must be retained for every future version. Never commit either file.

Create a release from a clean `main` branch with:

```powershell
.\scripts\release.ps1 -Version 1.0.1 -DryRun
.\scripts\release.ps1 -Version 1.0.1
```

The script validates semantic version ordering, increments `versionCode`, runs tests,
lint, and the signed release build, verifies the APK with `apksigner`, creates an
annotated tag, pushes it, and creates a GitHub Release without replacing an existing
tag or release. Use `-NotesFile` for custom notes. `-SkipPush` and
`-SkipGithubRelease` are available for local validation.

The public CI workflow intentionally builds only the debug APK. It never receives the
release keystore or signing passwords.
