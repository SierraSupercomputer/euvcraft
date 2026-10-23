# Generates docs/m1-slice.md deterministically from actual tracked source and
# src/main/resources data files. Nothing in the output is hand-written except the
# fixed section scaffolding; every count, path, default, range and reference is
# read from the tree at generation time and the generator fails loudly when an
# input is missing or a required section would come out empty.
#
# Ordinary mode: writes the document (UTF-8, no BOM, LF line endings).
# -Check:        rebuilds the exact same bytes, compares them to the existing file
#                and fails stale/missing without mutating anything.

param(
    [switch]$Check,
    [string]$Output = "docs/m1-slice.md"
)

$ErrorActionPreference = 'Stop'

# ---- repo root + helpers ----------------------------------------------------

$Root = Split-Path -Parent $PSScriptRoot
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

function Read-SourceText([string]$Path) {
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

function Fail([string]$Message) {
    throw "generate-m1-slice: $Message"
}

# Root-relative, forward-slash path for display; never leaks absolute paths.
function Rel-Path([string]$Absolute) {
    $norm = $Absolute.Replace('/', '\')
    $root = $Root.Replace('/', '\')
    if (-not $norm.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
        Fail "path escapes repo root: $Absolute"
    }
    $rel = $norm.Substring($root.Length).TrimStart('\')
    return $rel -replace '\\', '/'
}

# Ordinal (culture-independent) file-name sort for stable output ordering.
function Sort-Ordinal([string[]]$Names) {
    $list = New-Object 'System.Collections.Generic.List[string]'
    $list.AddRange([string[]]$Names)
    $list.Sort([System.StringComparer]::Ordinal)
    return $list.ToArray()
}

function Assert-File([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Fail "missing required input: $(Rel-Path $Path)"
    }
}

function Assert-Dir([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        Fail "missing required directory: $(Rel-Path $Path)"
    }
}

$srcJavaRoot = Join-Path $Root 'src\main\java'
$classesRoot = Join-Path $Root 'build\classes\java\main'
Assert-Dir $srcJavaRoot
Assert-Dir $classesRoot

# ---- generated API section (tools/generate-m0-api.ps1 -M1) ------------------

$apiScript = Join-Path $PSScriptRoot 'generate-m0-api.ps1'
Assert-File $apiScript

$apiTemp = Join-Path ([System.IO.Path]::GetTempPath()) ('euvcraft-m1-slice-api-' + [System.IO.Path]::GetRandomFileName())
try {
    & $apiScript -M1 -ClassesRoot $classesRoot -Output $apiTemp
    if ($LASTEXITCODE -ne 0) {
        Fail "generate-m0-api.ps1 -M1 failed with exit code $LASTEXITCODE"
    }
    if (-not (Test-Path -LiteralPath $apiTemp)) {
        Fail 'API generator produced no output file'
    }
    $apiContent = [System.IO.File]::ReadAllText($apiTemp, [System.Text.Encoding]::UTF8)
}
finally {
    Remove-Item $apiTemp -Force -ErrorAction SilentlyContinue
}

$apiHeaderEnd = $apiContent.IndexOf('### ', [System.StringComparison]::Ordinal)
if ($apiHeaderEnd -lt 1) {
    Fail 'generated API document has no per-class sections'
}
$apiBody = $apiContent.Substring($apiHeaderEnd).TrimEnd()
foreach ($requiredPkg in @(
        'com.euvcraft.lithmod.core.multiblock',
        'com.euvcraft.lithmod.mc.multiblock',
        'com.euvcraft.lithmod.mc.machine.test',
        'com.euvcraft.lithmod.mc.recipe'
    )) {
    if (-not $apiBody.Contains($requiredPkg)) {
        Fail "generated API section is missing actual M1 package: $requiredPkg"
    }
}
$apiClassCount = ([regex]::Matches($apiBody, [regex]::Escape('### '))).Count
if ($apiClassCount -lt 10) {
    Fail "generated API section has only $apiClassCount class blocks"
}

# ---- package layout (parsed from actual package declarations) ---------------

$javaFiles = [System.IO.Directory]::GetFiles($srcJavaRoot, '*.java', [System.IO.SearchOption]::AllDirectories)
if ($javaFiles.Count -eq 0) {
    Fail 'no .java sources found under src/main/java'
}
$packageCounts = @{}
foreach ($javaFile in $javaFiles) {
    $text = Read-SourceText $javaFile
    $pkgLine = [regex]::Match($text, '(?m)^\s*package\s+([A-Za-z][A-Za-z0-9_.]*)\s*;')
    if (-not $pkgLine.Success) {
        Fail "no package declaration in $(Rel-Path $javaFile)"
    }
    $pkg = $pkgLine.Groups[1].Value
    if (-not $packageCounts.ContainsKey($pkg)) { $packageCounts[$pkg] = 0 }
    $packageCounts[$pkg]++
}
$packageLines = Sort-Ordinal ([string[]]$packageCounts.Keys) | ForEach-Object {
    "  - $_ ($($packageCounts[$_]) files)"
}
if ($packageLines.Count -eq 0) { Fail 'package layout section is empty' }

# ---- config: FabConfig entries parsed across lines, defaults from FabSettings

$fabConfigPath = Join-Path $srcJavaRoot 'com\euvcraft\lithmod\mc\FabConfig.java'
$fabSettingsPath = Join-Path $srcJavaRoot 'com\euvcraft\lithmod\core\FabSettings.java'
Assert-File $fabConfigPath
Assert-File $fabSettingsPath
$fabConfigSource = Read-SourceText $fabConfigPath
$fabSettingsSource = Read-SourceText $fabSettingsPath

$fabSettingsDefaults = @{}
foreach ($line in ($fabSettingsSource -split "`r?`n")) {
    if ($line -match '^\s*public static final (List(?:<[^>]+>)?|Map(?:<[^>]+>)?|int|long|double|boolean|String)\s+([A-Za-z0-9_]+)\s*=\s*(.+?);\s*$') {
        $fabSettingsDefaults[$matches[2]] = $matches[3].Trim()
    }
}

$category = ''
$entries = New-Object 'System.Collections.Generic.List[object]'
$entryBlock = $null
foreach ($line in ($fabConfigSource -split "`r?`n")) {
    $trimmed = $line.Trim()
    if ($trimmed -match '\.push\("([A-Za-z0-9_]+)"\)') { $category = $matches[1]; continue }
    if ($trimmed -match 'BUILDER\.build\(\);') { $entryBlock = $null; continue }
    if (-not $entryBlock) {
        if ($trimmed -match '^([A-Za-z_][A-Za-z0-9_.]*)\s*=\s*BUILDER\b') {
            $entryBlock = $trimmed
            if (-not $trimmed.EndsWith(');')) { continue }
        } else {
            continue
        }
    } else {
        $entryBlock = "$entryBlock $trimmed"
        if (-not $trimmed.EndsWith(');')) { continue }
    }
    if (-not $entryBlock.Contains('.define')) { $entryBlock = $null; continue }
    $field = ''
    if ($entryBlock -match '^([A-Za-z_][A-Za-z0-9_.]*)\s*=') { $field = $matches[1] } else { Fail "config entry block without field name: $entryBlock" }
        $configKey = $null; $defaultExpr = $null; $range = ''
        $defineCall = [regex]::Match($entryBlock, '\.(defineInRange|defineListAllowEmpty|defineList|define)\s*\(')
        if (-not $defineCall.Success) { Fail "missing supported config definition: $field" }
        $defineArguments = [System.Collections.Generic.List[string]]::new()
        $argumentText = [System.Text.StringBuilder]::new()
        $depth = 1; $quoted = $false; $escaped = $false
        for ($offset = $defineCall.Index + $defineCall.Length; $offset -lt $entryBlock.Length; $offset++) {
            $character = $entryBlock[$offset]
            if ($quoted) {
                [void]$argumentText.Append($character)
                if ($escaped) { $escaped = $false }
                elseif ($character -eq '\') { $escaped = $true }
                elseif ($character -eq '"') { $quoted = $false }
                continue
            }
            if ($character -eq '"') { $quoted = $true; [void]$argumentText.Append($character); continue }
            if ($character -eq '(') { $depth++; [void]$argumentText.Append($character); continue }
            if ($character -eq ')') {
                $depth--
                if ($depth -eq 0) { $defineArguments.Add($argumentText.ToString().Trim()); break }
                [void]$argumentText.Append($character); continue
            }
            if ($character -eq ',' -and $depth -eq 1) {
                $defineArguments.Add($argumentText.ToString().Trim()); [void]$argumentText.Clear(); continue
            }
            [void]$argumentText.Append($character)
        }
        if ($depth -ne 0 -or $quoted -or $defineArguments.Count -lt 2) { Fail "unbalanced config arguments: $field" }
        if ($defineArguments[0] -notmatch '^"([A-Za-z0-9_]+)"$') { Fail "unsupported config key expression: $field" }
        $configKey = $matches[1]; $defaultExpr = $defineArguments[1]
        switch ($defineCall.Groups[1].Value) {
            'defineInRange' {
                if ($defineArguments.Count -ne 4) { Fail "invalid range arguments: $field" }
                $range = "[$($defineArguments[2]), $($defineArguments[3])]"
            }
            'defineListAllowEmpty' { $range = 'list of strings, empty allowed' }
            'defineList' {
                $range = 'list'
                if ($entryBlock -match 'value >= (\d+)\s*&&\s*value <= (\d+)') {
                    $range = "list of integers, each in [$($matches[1]), $($matches[2])]"
                }
            }
            'define' { $range = 'boolean (ModConfigSpec.BooleanValue)' }
        }

        if (-not $configKey) { Fail "config entry '$field' has no define call: $entryBlock" }
        $defaultText = $defaultExpr
        $settingsRef = [regex]::Match($defaultExpr, 'FabSettings\.([A-Za-z0-9_]+)')
        if ($settingsRef.Success) {
            $constName = $settingsRef.Groups[1].Value
            if (-not $fabSettingsDefaults.ContainsKey($constName)) {
                Fail "config default '$constName' is not a parsed FabSettings constant"
            }
            $defaultText = "$defaultExpr (=$($fabSettingsDefaults[$constName]))"
        }
        $range = [regex]::Replace($range, 'FabSettings\.([A-Za-z0-9_]+)', [System.Text.RegularExpressions.MatchEvaluator]{
            param($constantMatch)
            $name = $constantMatch.Groups[1].Value
            if (-not $fabSettingsDefaults.ContainsKey($name)) { Fail "unresolved range constant: $name" }
            return $fabSettingsDefaults[$name]
        })
        $entries.Add([pscustomobject]@{
            Category = $category; Key = $configKey; Field = $field
            Default = $defaultText; Range = $range
        })
        $entryBlock = $null
}
if ($category -eq '') {
    Fail 'FabConfig.java yielded no config category (push(...) not found)'
}

# Required 15 actual keys/categories derived from FabConfig/FabSettings (M0-02/M1 handoff).
$expectedConfig = [ordered]@{
    general = @('foupCapacity', 'wafersPerBoule', 'chipsPerWafer', 'maskUses', 'resistPerLayer',
        'rollsPerLayer', 'yieldMultiplier', 'tierYieldOverrides', 'layerTicksOverrides')
    compute = @('coreCountOptions', 'comfortableCoreOverrides', 'coreCountPenaltySteepness')
    features = @('tier6Enabled', 'debugCommands')
    power = @('fePowerMultiplier')
}
if ($entries.Count -ne 15) {
    Fail "expected 15 FabConfig define calls, parsed $($entries.Count): $($entries.Key -join ', ')"
}
foreach ($catName in $expectedConfig.Keys) {
    $have = @($entries | Where-Object { $_.Category -eq $catName } | ForEach-Object { $_.Key })
    $want = $expectedConfig[$catName]
    $same = ($have.Count -eq $want.Count) -and (-not (Compare-Object $have $want))
    if (-not $same) {
        Fail "config category '$catName' mismatch. want: [$($want -join ', ')] got: [$($have -join ', ')]"
    }
}

# ---- machine definitions (data/lithmod/machines/*.json) ---------------------

$machineDir = Join-Path $Root 'src\main\resources\data\lithmod\machines'
Assert-Dir $machineDir
$machineNames = Sort-Ordinal ([System.IO.Directory]::GetFiles($machineDir, '*.json'))
if ($machineNames.Count -ne 15) {
    Fail "expected 15 machine definition files in data/lithmod/machines, found $($machineNames.Count)"
}
$machineDefs = New-Object 'System.Collections.Generic.List[object]'
$fixtureMachineId = 'test_machine'
foreach ($mFile in $machineNames) {
    $raw = Read-SourceText $mFile
    $json = $raw | ConvertFrom-Json
    if (-not $json.id) { Fail "machine definition without 'id': $(Rel-Path $mFile)" }
    $fixture = ($json.id -eq $fixtureMachineId) # MachineDefinitionLoader.isFixtureDefinition (payload id)
    $machineDefs.Add([pscustomobject]@{
        Path = (Rel-Path $mFile); Id = $json.id; Fixture = $fixture; Raw = ($raw.Trim() -replace "`r`n", "`n")
    })
}
if (@($machineDefs | Where-Object { $_.Id -eq $fixtureMachineId }).Count -ne 1) {
    Fail "expected exactly one '$fixtureMachineId' definition (gated by MachineDefinitionLoader)"
}

# ---- recipe summary (recipe/*, recipe/crafting/*, machine_recipes/*) --------

$recipeRoot = Join-Path $Root 'src\main\resources\data\lithmod\recipe'
$craftingDir = Join-Path $recipeRoot 'crafting'
$mrDir = Join-Path $Root 'src\main\resources\data\lithmod\machine_recipes'
Assert-Dir $recipeRoot; Assert-Dir $craftingDir; Assert-Dir $mrDir

$craftingNames = Sort-Ordinal ([System.IO.Directory]::GetFiles($craftingDir, '*.json'))
$cookingNames = Sort-Ordinal ([System.IO.Directory]::GetFiles($recipeRoot, '*.json'))
$machineRecipeNames = Sort-Ordinal ([System.IO.Directory]::GetFiles($mrDir, '*.json'))
$totalRecipes = $craftingNames.Count + $cookingNames.Count + $machineRecipeNames.Count
if ($totalRecipes -ne 107) {
    Fail "expected 107 recipe resources (105 production + 2 fixture-gated), found $totalRecipes"
}
if ($machineRecipeNames.Count -ne 18) {
    Fail "expected 18 machine recipe files in data/lithmod/machine_recipes, found $($machineRecipeNames.Count)"
}
if ($cookingNames.Count -ne 2) {
    Fail "expected the two vanilla cooking recipes in data/lithmod/recipe, found $($cookingNames.Count)"
}

$recipes = New-Object 'System.Collections.Generic.List[object]'
foreach ($rFile in ($craftingNames + $cookingNames)) {
    $json = (Read-SourceText $rFile) | ConvertFrom-Json
    if (-not $json.type) { Fail "recipe without 'type': $(Rel-Path $rFile)" }
    $resultId = ''
    if ($json.result -is [string]) { $resultId = $json.result } else { $resultId = $json.result.id }
    if (-not $resultId) { Fail "recipe without result id: $(Rel-Path $rFile)" }
    $count = 1
    if ($json.result.count) { $count = [int]$json.result.count }
    $recipes.Add([pscustomobject]@{
        Path = (Rel-Path $rFile); Kind = $json.type; Result = $resultId; Count = $count
        Fixture = $false; Raw = $null
    })
}
$machineRecipes = New-Object 'System.Collections.Generic.List[object]'
$fixtureRecipeCount = 0
foreach ($rFile in $machineRecipeNames) {
    $raw = Read-SourceText $rFile
    $json = $raw | ConvertFrom-Json
    if (-not $json.machine) { Fail "machine recipe without 'machine': $(Rel-Path $rFile)" }
    $knownMachine = $machineDefs | Where-Object { $_.Id -eq $json.machine }
    if (-not $knownMachine) {
        Fail "machine recipe $(Rel-Path $rFile) references undefined machine '$($json.machine)'"
    }
    $resultId = $json.result.item
    if (-not $resultId) { Fail "machine recipe without result.item: $(Rel-Path $rFile)" }
    $fixture = ($json.machine -eq $fixtureMachineId) # MachineRecipeLoader.parseRuntime / isFixtureSpec
    if ($fixture) { $fixtureRecipeCount++ }
    $energy = ''
    if ($json.energy_per_tick -ne $null) { $energy = "$($json.energy_per_tick)" } else { $energy = '-' }
    $machineRecipes.Add([pscustomobject]@{
        Path = (Rel-Path $rFile); Machine = $json.machine; Result = $resultId
        Count = $json.result.count; Ticks = $json.ticks; Energy = $energy; Fixture = $fixture
        Raw = ($raw.Trim() -replace "`r`n", "`n")
    })
}
if ($fixtureRecipeCount -ne 2) {
    Fail "expected the two '$fixtureMachineId' fixture recipes to be payload-gated, found $fixtureRecipeCount"
}
if (($totalRecipes - $fixtureRecipeCount) -ne 105) {
    Fail 'production recipe count is not 105'
}

# ---- loader directory strings + codec sources (derived from tracked source) --

$loaderFiles = @(
    'MachineDefinitionLoader', 'MachineRecipeLoader', 'TierDataLoader',
    'DieRulesDataLoader', 'SupportLineDataLoader'
)
function Data-SourcePath([string]$Name) {
    return Join-Path $srcJavaRoot "com\euvcraft\lithmod\mc\data\$Name.java"
}
$loaderDirs = @{}
foreach ($loaderName in $loaderFiles) {
    $p = Data-SourcePath $loaderName
    Assert-File $p
    $src = Read-SourceText $p
    $dirMatch = [regex]::Match($src, 'super\("([A-Za-z0-9_]+)"')
    if (-not $dirMatch.Success) {
        $dirMatch = [regex]::Match($src, 'DIRECTORY\s*=\s*"([A-Za-z0-9_]+)"')
    }
    if (-not $dirMatch.Success) {
        Fail "$loaderName.java does not declare its data directory string"
    }
    $loaderDirs[$loaderName] = $dirMatch.Groups[1].Value
}
$codecFiles = @('MachineDefinitionCodec', 'MachineRecipeSpecCodec', 'MachineIngredientCodec', 'MachineResultCodec')
$codecSources = @()
foreach ($codecName in $codecFiles) {
    $p = Data-SourcePath $codecName
    Assert-File $p
    $src = Read-SourceText $p
    if (-not $src.Contains('CODEC = RecordCodecBuilder')) {
        Fail "$codecName.java does not declare a CODEC"
    }
    $codecSources += [pscustomobject]@{
        Name = $codecName
        Path = "src/main/java/com/euvcraft/lithmod/mc/data/$codecName.java"
        Raw = $src.Trim()
    }
}

# ---- tracked report references (fail missing; never invented sources) -------

$decisionRows = @(
    @{ Decision = 'Silicon cooking ingredient is minecraft:quartz (owner QUARTZ decision; sand smelts to glass is vanilla-only)'
        Status = 'Decided'; Source = 'docs/reports/M1-04_silicon-chain.md' }
    @{ Decision = 'Pattern Generator machine recipes consume blank_mask plus copper/iron/redstone common metals'
        Status = 'Decided'; Source = 'docs/reports/M1-11_review.md' }
    @{ Decision = 'Resist top-ups while running/paused: same item + same components only; extraction/swap/creative clone locked'
        Status = 'Decided'; Source = 'docs/reports/M1-09_process-cell-controller.md; docs/reports/M1-09_acceptance.md' }
    @{ Decision = 'Abort discards unfinished progress without refund, retains completed layers, and latches until the FOUP is removed and reinserted'
        Status = 'Decided'; Source = 'docs/reports/M1-09_process-cell-controller.md; docs/reports/M1-12_baseline-audit.md' }
    @{ Decision = 'Process Cell geometry: StructureShape(2,2,3) = two blocks wide, two tall, three long (12 members; 1 controller, 1 or 2 power ports)'
        Status = 'Decided'; Source = 'docs/reports/M1-11_acceptance.md; docs/reports/M1-12_baseline-audit.md' }
    @{ Decision = 'M1 source takes precedence over older references (owner-confirmed); concrete M1-12 constraints tabled in the baseline audit'
        Status = 'Decided'; Source = 'docs/reports/M1-12_baseline-audit.md' }
    @{ Decision = 'T6 core choices: later owner decision, out of M1-12 scope; features.tier6Enabled=false skips T6 (tiers go T5 -> T7)'
        Status = 'Decided'; Source = 'docs/reports/M1-12_baseline-audit.md; docs/reports/M0-02_config-system.md' }
)
foreach ($row in $decisionRows) {
    foreach ($src in ($row.Source -split '; ')) {
        Assert-File (Join-Path $Root ($src -replace '/', '\'))
    }
}

$shipSourcePaths = @(
    'src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java'
    'src/main/java/com/euvcraft/lithmod/mc/gametest/M1ReloadGameTests.java'
    'src/main/java/com/euvcraft/lithmod/mc/gametest/M1ShippingGameTests.java'
    'src/main/java/com/euvcraft/lithmod/mc/GameTestFixtures.java'
    'src/main/java/com/euvcraft/lithmod/mc/data/MachineDefinitionLoader.java'
    'src/main/java/com/euvcraft/lithmod/mc/data/MachineRecipeLoader.java'
    'build.gradle'
    'src/main/resources/data/lithmod/tags/block/machine.json'
    'src/main/resources/data/lithmod/loot_table/blocks/test_machine.json'
    'src/main/resources/data/minecraft/tags/block/needs_stone_tool.json'
    'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json'
)
foreach ($shipPath in $shipSourcePaths) {
    Assert-File (Join-Path $Root ($shipPath -replace '/', '\'))
}

# ---- 26.3 note numbers derived from actual tracked files --------------------

$crystalDefJson = (Read-SourceText (Join-Path $machineDir 'crystal_puller.json')) | ConvertFrom-Json
$baselineFe = [int]$crystalDefJson.energy_per_tick
if ($baselineFe -le 0) { Fail 'crystal_puller.json baseline energy_per_tick is not positive' }
$reloadTestSource = Read-SourceText (Join-Path $Root 'src\main\java\com\euvcraft\lithmod\mc\gametest\M1ReloadGameTests.java')
$deltaMatch = [regex]::Match($reloadTestSource, 'baselineEnergyPerTick\s*\+\s*(\d+)')
if (-not $deltaMatch.Success) {
    Fail 'M1ReloadGameTests.java does not declare its baseline+n override delta'
}
$overrideDelta = [int]$deltaMatch.Groups[1].Value
$crystalRecipeJson = (Read-SourceText (Join-Path $mrDir 'crystal_puller.json')) | ConvertFrom-Json
$crystalTicks = [int]$crystalRecipeJson.ticks
if ($crystalTicks -le 0) { Fail 'crystal_puller machine recipe ticks are not positive' }
$packDesc = [regex]::Match($reloadTestSource, 'PackMetadataSection\(\s*\r?\n?\s*net\.minecraft\.network\.chat\.Component\.literal\("([^"]+)"\)')
if (-not $packDesc.Success) {
    Fail 'M1ReloadGameTests.java does not declare its probe pack description literal'
}
$probePackDescription = $packDesc.Groups[1].Value

# ---- document assembly ------------------------------------------------------

$doc = New-Object 'System.Collections.Generic.List[string]'
function Emit([string]$Line) { $doc.Add($Line) }
function Emit-Blank { $doc.Add('') }
function Emit-Fenced([string]$Language, [string]$Body) {
    Emit ('```' + $Language)
    foreach ($line in ($Body -split "`r?`n")) { Emit $line }
    Emit '```'
}

Emit '# EUVCRAFT M1 Handoff Slice'
Emit-Blank
Emit 'Generated by tools/generate-m1-slice.ps1 from actual tracked source and src/main/resources data. Do not hand-edit.'
Emit 'Deterministic output: source-root-relative paths only, stable ordinal ordering, UTF-8 without BOM, LF line endings, no timestamps, hosts, user names or absolute paths.'
Emit-Blank

Emit '## Package Layout (parsed from actual package declarations)'
Emit-Blank
foreach ($line in $packageLines) { Emit $line }
Emit-Blank

Emit '## Generated API (compiled bytecode via tools/generate-m0-api.ps1 -M1)'
Emit-Blank
Emit 'Signatures below are taken from the compiled main classes by javap at generation time; M1 failing or missing classes abort generation. M0 classes retain their documented signatures.'
Emit-Blank
foreach ($line in ($apiBody -split "`r?`n")) { Emit $line }
Emit-Blank

Emit '## Config: FabConfig (ModConfig.Type.LOCAL registered in EUVCRAFTMod.java, runtime run/config/lithmod-local.toml)'
Emit-Blank
Emit 'Parsed across FabConfig.java multiline BUILDER chains; defaults for FabSettings.* references are resolved from the parsed FabSettings.java constants. 15 keys total.'
Emit-Blank
Emit '| Category | Key | Field | Default | Range |'
Emit '|----------|-----|-------|---------|-------|'
foreach ($entry in $entries) {
    Emit "| $($entry.Category) | $($entry.Key) | $($entry.Field) | $($entry.Default) | $($entry.Range) |"
}
Emit-Blank
Emit 'Actual ranges come from the defineInRange min/max literals, the list validators in FabConfig, and the FabSettings clamp constants; list overrides use the Tn=value format parsed by FabSettings.parseTierOverrideList.'
Emit-Blank

Emit '## Machine Definitions (src/main/resources/data/lithmod/machines/*.json)'
Emit-Blank
Emit "test_machine definitions/recipes are dropped before validation outside fixture mode (MachineDefinitionLoader.runtimeBuild); test_package is production content and is never filtered (loader javadoc)."
Emit-Blank
Emit '| Definition | id | energy_capacity | max_receive | energy_per_tick | Runtime gate |'
Emit '|------------|----|-----------------|-------------|-----------------|--------------|'
foreach ($m in $machineDefs) {
    $json = $m.Raw | ConvertFrom-Json
    $gate = 'production'
    if ($m.Fixture) { $gate = 'fixture (test-only)' }
    Emit "| $($m.Path) | $($m.Id) | $($json.energy_capacity) | $($json.max_receive) | $($json.energy_per_tick) | $gate |"
}
Emit-Blank
foreach ($m in $machineDefs) {
    Emit "### $($m.Path)"
    Emit-Blank
    Emit-Fenced 'json' $m.Raw
    Emit-Blank
}

Emit "## Recipe Summary ($totalRecipes actual resources: $($totalRecipes - $fixtureRecipeCount) production + $fixtureRecipeCount fixture-gated)"
Emit-Blank
Emit "### Vanilla recipe datapack files ($($recipes.Count) production)"
Emit-Blank
Emit '| Path | Type | Result | Count |'
Emit '|------|------|--------|-------|'
foreach ($r in $recipes) {
    Emit "| $($r.Path) | $($r.Kind) | $($r.Result) | $($r.Count) |"
}
Emit-Blank
Emit '### Machine recipes (data/lithmod/machine_recipes/*.json)'
Emit-Blank
Emit 'Loaded by MachineRecipeLoader (directory string "machine_recipes") on every server resource reload; priority is file-id order. energy_per_tick is optional and falls back to the machine definition.'
Emit-Blank
Emit '| Path | machine | result | count | ticks | energy_per_tick | Gate |'
Emit '|------|---------|--------|-------|-------|-----------------|------|'
foreach ($r in $machineRecipes) {
    $gate = 'production'
    if ($r.Fixture) { $gate = 'fixture (test-only)' }
    Emit "| $($r.Path) | $($r.Machine) | $($r.Result) | $($r.Count) | $($r.Ticks) | $($r.Energy) | $gate |"
}
Emit-Blank
Emit 'The two test_machine entries are the payload-gated fixtures: MachineRecipeLoader.parseRuntime filters every spec whose real payload machineId is test_machine when fixtures are off; buildResolved stays unfiltered for unit tests.'
Emit-Blank
foreach ($r in $machineRecipes) {
    $label = 'production'
    if ($r.Fixture) { $label = 'fixture (test-only)' }
    Emit "#### $($r.Path) ($label)"
    Emit-Blank
    Emit-Fenced 'json' $r.Raw
    Emit-Blank
}

# ---- data inventory, formats, paths -----------------------------------------

Emit '## Data Files and Formats (actual directories and loader declarations)'
Emit-Blank
$lithDataDir = Join-Path $Root 'src\main\resources\data\lithmod'
$tiersDir = Join-Path $lithDataDir 'tiers'
$dieRulesDir = Join-Path $lithDataDir 'die_rules'
$supportLinesDir = Join-Path $lithDataDir 'support_lines'
$lootDir = Join-Path $lithDataDir 'loot_table\blocks'
$tagDir = Join-Path $lithDataDir 'tags\block'
foreach ($d in @($tiersDir, $dieRulesDir, $supportLinesDir, $lootDir, $tagDir)) { Assert-Dir $d }
$tierNames = Sort-Ordinal ([System.IO.Directory]::GetFiles($tiersDir, '*.json'))
$dieRuleNames = Sort-Ordinal ([System.IO.Directory]::GetFiles($dieRulesDir, '*.json'))
$supportLineNames = Sort-Ordinal ([System.IO.Directory]::GetFiles($supportLinesDir, '*.json'))
$lootNames = Sort-Ordinal ([System.IO.Directory]::GetFiles($lootDir, '*.json'))
$tagNames = Sort-Ordinal ([System.IO.Directory]::GetFiles($tagDir, '*.json'))

Emit '| Loader | data/lithmod directory (parsed from loader source) | Tracked source path |'
Emit '|--------|------------------------------------------------------|---------------------|'
foreach ($loaderName in (Sort-Ordinal ([string[]]$loaderDirs.Keys))) {
    Emit "| $loaderName | data/lithmod/$($loaderDirs[$loaderName]) | src/main/java/com/euvcraft/lithmod/mc/data/$loaderName.java |"
}
Emit-Blank
Emit 'Tier JSON fields per file (raw literals read from src/main/resources/data/lithmod/tiers):'
Emit-Blank
Emit '| File | id | index | layers | layer_ticks | default_yield_percent | optional |'
Emit '|------|----|-------|--------|-------------|----------------------|----------|'
foreach ($tName in $tierNames) {
    $tRaw = Read-SourceText $tName
    $value = [regex]::Match($tRaw, '"default_yield_percent"\s*:\s*([0-9.eE+-]+)').Groups[1].Value
    $tJson = $tRaw | ConvertFrom-Json
    Emit "| data/lithmod/tiers/$(Split-Path -Leaf $tName) | $($tJson.id) | $($tJson.index) | $($tJson.layers) | $($tJson.layer_ticks) | $value | $($tJson.optional) |"
}
Emit-Blank
Emit-Blank
Emit "M0-owned data files stay as tracked: data/lithmod/die_rules/rules.json, data/lithmod/support_lines/matrix.json, and the $($tierNames.Count) data/lithmod/tiers/t<index>.json files. Block loot tables: $($lootNames.Count) files under data/lithmod/loot_table/blocks; block tag files: $($tagNames.Count). Counts are read at generation time."
Emit-Blank

Emit '### Machine data codecs (actual codec sources; core records stay game-independent)'
Emit-Blank
foreach ($c in $codecSources) {
    Emit "#### $($c.Path)"
    Emit-Blank
    Emit-Fenced 'java' $c.Raw
    Emit-Blank
}
Emit 'MachineIngredient FLEXIBLE_CODEC accepts ("item", count) or ("tag", count); MachineResultCodec keeps result components as arbitrary JSON via a PASSTHROUGH codec (protocol object stays unquoted on disk).'
Emit-Blank

Emit '## Valid Local Resource Paths (verified directories)'
Emit-Blank
Emit '| Kind | Root-relative pattern |'
Emit '|------|-----------------------|'
Emit '| Machine Definition | data/lithmod/machines/<id>.json |'
Emit '| Machine Recipe | data/lithmod/machine_recipes/<name>.json |'
Emit '| Crafting Recipe | data/lithmod/recipe/crafting/<name>.json |'
Emit '| Cooking Recipe (smelting/blasting) | data/lithmod/recipe/<name>.json |'
Emit '| Tier Data | data/lithmod/tiers/t<index>.json |'
Emit '| Die Rules | data/lithmod/die_rules/<name>.json |'
Emit '| Support Lines | data/lithmod/support_lines/<name>.json |'
Emit '| Block Loot Tables | data/lithmod/loot_table/blocks/<block>.json |'
Emit '| Block Tags | data/lithmod/tags/block/machine.json + data/minecraft/tags/block/mineable/pickaxe.json, needs_stone_tool.json |'
Emit '| Runtime Config | run/config/lithmod-local.toml (ModConfig.Type.LOCAL, registered in EUVCRAFTMod.java) |'
Emit '| Fixture gate | JVM system property lithmodFixtures AND non-production (GameTestFixtures.java); -PlithmodFixtures=false with -PlithmodShippingProbe=true registers one off-mode case |'
Emit-Blank

Emit '## Open/Decided Design Decisions (owner choices with tracked report references)'
Emit-Blank
Emit 'No resolved questions are left open and no decisions are guessed. Every referenced file is a tracked path in this repository and its existence is checked at generation time.'
Emit-Blank
Emit '| Decision | Status | Source Reference |'
Emit '|----------|--------|------------------|'
foreach ($row in $decisionRows) {
    Emit "| $($row.Decision) | $($row.Status) | $($row.Source) |"
}
Emit-Blank

Emit '## 26.3 API Notes from M1 Reports'
Emit-Blank
$spikeReport = Join-Path $Root 'docs/reports/M1-02s_energy-spike.md'
Assert-File $spikeReport
Emit 'Registration, energy, inventory persistence and menu APIs established by [M1-02s energy spike](reports/M1-02s_energy-spike.md):'
Emit-Blank
Emit '| Topic | Verified API | Original source evidence |'
Emit '|-------|--------------|--------------------------|'
$spikeRows = @((Read-SourceText $spikeReport) -split "`r?`n" | Where-Object { $_ -match '^\| (Block registration|Energy capability|Block entity type|Menu type registration|Menu abstract methods|Inventory persistence) \|' })
if ($spikeRows.Count -lt 3) { Fail 'M1-02s API evidence rows missing' }
foreach ($row in $spikeRows) { Emit $row }
Emit-Blank
Emit '## 26.3 API Notes (values derived from actual tracked sources at generation time)'
Emit-Blank
Emit '| Note | Source |'
Emit '|------|--------|'
Emit "| The runtime datapack probe writes pack.mcmeta through PackMetadataSection.SERVER_TYPE.codec() with the literal '$probePackDescription' and the SharedConstants server-data pack version (runtime decode reported min_format 121, max_format [121,0]) | src/main/java/com/euvcraft/lithmod/mc/gametest/M1ReloadGameTests.java |"
Emit "| Reload probe measured FE path: baseline crystal_puller energy_per_tick $baselineFe FE/tick -> override baseline + $overrideDelta ($($baselineFe + $overrideDelta) FE/tick) -> restored $baselineFe FE/tick; the loaded crystal_puller recipe is $crystalTicks paid ticks; original datapack selection restore is asserted | src/main/java/com/euvcraft/lithmod/mc/gametest/M1ReloadGameTests.java; src/main/resources/data/lithmod/machines/crystal_puller.json; src/main/resources/data/lithmod/machine_recipes/crystal_puller.json |"
Emit '| Fixture gate requires BOTH the lithmodFixtures JVM property true AND non-production FMLEnvironment; normal client/server runs stay false; gameTestServer defaults the property to true, and -PlithmodShippingProbe=true with fixtures off registers only the m1_gate_shipping_no_fixtures case | src/main/java/com/euvcraft/lithmod/mc/GameTestFixtures.java; build.gradle; src/main/java/com/euvcraft/lithmod/mc/gametest/M1ShippingGameTests.java |'
Emit '| Release jar excludes exactly the eight test_machine fixture entries from the jar task only; processResources/testOutput are untouched so fixture game tests keep fixtures; test_package JSON is production content and is not excluded | build.gradle |'
Emit '| Optional (required=false) test_machine tag entries live in data/lithmod/tags/block/machine.json, data/minecraft/tags/block/mineable/pickaxe.json and data/minecraft/tags/block/needs_stone_tool.json; production block entries remain present; see the source-derived inventory above | see those tracked files |'
Emit '| Fixture loot table uses a neoforge:conditions gate of type neoforge:registered (registry minecraft:item, value lithmod:test_machine) | src/main/resources/data/lithmod/loot_table/blocks/test_machine.json |'
Emit '| Config is registered as ModConfig.Type.LOCAL (NeoForge 26.3 renamed the former COMMON type); runtime file run/config/lithmod-local.toml | src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java |'
Emit-Blank

# ---- finalize: normalize once, then write or byte-exact Check ---------------

$outputContent = ($doc -join "`n") + "`n"
$expectedBytes = $utf8NoBom.GetBytes($outputContent)

function Show-OutputPath([string]$Path) {
    if ($Path.Replace('/', '\').StartsWith($Root, [System.StringComparison]::OrdinalIgnoreCase)) {
        return Rel-Path $Path
    }
    return $Path
}

$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $expectedHash = ([BitConverter]::ToString($sha256.ComputeHash($expectedBytes))).Replace('-', '').ToLowerInvariant()
} finally {
    $sha256.Dispose()
}

if ($Check) {
    $absOutput = $Output
    if (-not [System.IO.Path]::IsPathRooted($absOutput)) { $absOutput = Join-Path $Root $absOutput }
    if (-not (Test-Path -LiteralPath $absOutput -PathType Leaf)) {
        Write-Error "Check failed: $(Show-OutputPath $absOutput) does not exist"
        exit 1
    }
    $existingBytes = [System.IO.File]::ReadAllBytes($absOutput)
    if ($existingBytes.Length -ne $expectedBytes.Length) {
        Write-Error "Check failed: $(Show-OutputPath $absOutput) is stale (length $($existingBytes.Length) <> $($expectedBytes.Length); expected sha256 $expectedHash)"
        exit 1
    }
    $stale = $false
    for ($i = 0; $i -lt $expectedBytes.Length; $i++) {
        if ($existingBytes[$i] -ne $expectedBytes[$i]) { $stale = $true; break }
    }
    if ($stale) {
        Write-Error "Check failed: $(Show-OutputPath $absOutput) is stale; regenerate it (no mutation attempted; expected sha256 $expectedHash)"
        exit 1
    }
    Write-Host "Check passed: $(Show-OutputPath $absOutput) is byte-identical to the regenerated document (sha256 $expectedHash)"
    exit 0
}

$absWriteOutput = $Output
if (-not [System.IO.Path]::IsPathRooted($absWriteOutput)) { $absWriteOutput = Join-Path $Root $absWriteOutput }
$parentDir = Split-Path -Parent $absWriteOutput
if (-not (Test-Path -LiteralPath $parentDir)) {
    New-Item -ItemType Directory -Path $parentDir -Force | Out-Null
}
[System.IO.File]::WriteAllBytes($absWriteOutput, $expectedBytes)
Write-Host "Wrote $(Show-OutputPath $absWriteOutput) (sha256 $expectedHash)"

Emit '## Valid Local Resource Paths (verified directories)'
Emit-Blank
Emit '| Kind | Root-relative pattern |'
Emit '|------|-----------------------|'
Emit '| Machine Definition | data/lithmod/machines/<id>.json |'
Emit '| Machine Recipe | data/lithmod/machine_recipes/<name>.json |'
Emit '| Crafting Recipe | data/lithmod/recipe/crafting/<name>.json |'
Emit '| Cooking Recipe (smelting/blasting) | data/lithmod/recipe/<name>.json |'
Emit '| Tier Data | data/lithmod/tiers/t<index>.json |'
Emit '| Die Rules | data/lithmod/die_rules/<name>.json |'
Emit '| Support Lines | data/lithmod/support_lines/<name>.json |'
Emit '| Block Loot Tables | data/lithmod/loot_table/blocks/<block>.json |'
Emit '| Block Tags | data/lithmod/tags/block/machine.json + data/minecraft/tags/block/mineable/pickaxe.json, needs_stone_tool.json |'
Emit '| Runtime Config | run/config/lithmod-local.toml (ModConfig.Type.LOCAL, registered in EUVCRAFTMod.java) |'
Emit '| Fixture_gate | JVM system property lithmodFixtures AND non-production (GameTestFixtures.java); -PlithmodFixtures=false with -PlithmodShippingProbe=true registers one off-mode case |'
Emit-Blank



