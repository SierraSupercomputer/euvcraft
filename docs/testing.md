# Testing EUVCRAFT

## Unit tests (JUnit 5, game classes on classpath)

```powershell
$env:JAVA_HOME="C:\Users\ethan\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2"
.\gradlew.bat test --offline
```

ModDevGradle unit test support is enabled in `build.gradle`:

```groovy
neoForge {
    unitTest {
        enable()
    }
}
```

The `FabConfig` mc test verifies that the NeoForge config spec builds and its
defaults equal `FabSettings.defaults()`.

Per-class counts are in `build/test-results/test/*.xml`.

## Game tests

```powershell
$env:JAVA_HOME="C:\Users\ethan\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2"
.\gradlew.bat runGameTestServer --offline
```

A trivial game test is registered as `lithmod:trivial` and must report as passed.

## Reloading tier data (manual)

1. Start the dev server.
2. Create a data pack with `data/lithmod/tiers/t5.json` containing an override.
3. Run `/reload`.
4. Check the server log for the eight loaded tiers and the override value.
5. Use an invalid override; the server log names the file and field and keeps
   the previous registry.