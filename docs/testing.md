# Building and testing EUVCRAFT

Use Java 25 and the checked-in Gradle wrapper. The development dependencies are pinned in the build and supplied in libs/. The Arcforge API JAR is for compilation. For gameplay, install the full Arcforge mod.

The commands below use the Windows wrapper. On Linux or macOS, use ./gradlew with the same arguments.

## Build and unit tests

~~~powershell
.\gradlew.bat build checkCoreImports
~~~

Find the JAR in build/libs/ and JUnit reports in build/reports/tests/test/. The first build needs network access to resolve Gradle and NeoForge dependencies.

## Game tests

~~~powershell
.\gradlew.bat runGameTestServer
.\gradlew.bat runGameTestServer -PlithmodFixtures=false -PlithmodShippingProbe=true
~~~

The first command runs the in-game suite with fixtures. The second runs the production registry smoke test with fixtures disabled.

## Playtesting and resource changes

Start the development client with .\gradlew.bat runClient. Follow the [playtest checklist](playtest-progression.md#playtest-checklist) in a test world.

After editing recipes, run the PowerShell 7 generator and documentation checker from the repo root.

~~~powershell
pwsh -File tools/generate-m1-recipes.ps1
node tools/check-progression-guide.mjs
~~~

For art changes, follow the workflows for [starter textures](starter-textures.md), [joined panels](textures.md), [T4 panels](t4-textures.md), and the [beamline model](beamline-blockbench-model.md). See [GitHub commit attribution](github-attribution.md) for the repository's contribution checks.
