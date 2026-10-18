<# Generate the recipe guide from actual JSON. PowerShell 7; no external tools. #>
param([string]$RepoRoot = (Split-Path $PSScriptRoot -Parent), [switch]$Check)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$RepoRoot = [System.IO.Path]::GetFullPath($RepoRoot)
$outputPath = Join-Path $RepoRoot 'docs/recipes.md'

function Value-OrDefault($Map, [string]$Key, $Default) {
    if ($Map.Contains($Key)) { return $Map[$Key] }
    return $Default
}
function Scalar-Text($Value) {
    if ($Value -is [System.IFormattable]) {
        return $Value.ToString($null, [System.Globalization.CultureInfo]::InvariantCulture)
    }
    return [string]$Value
}
function Ingredient-Text($Ingredient) {
    if ($Ingredient -is [string]) { return $Ingredient }
    if ($Ingredient -is [System.Collections.IDictionary]) {
        if ($Ingredient.Contains('item')) { return $Ingredient.item }
        if ($Ingredient.Contains('tag')) { return '#' + $Ingredient.tag }
    }
    if ($Ingredient -is [array]) {
        return '(' + (($Ingredient | ForEach-Object { Ingredient-Text $_ }) -join ' or ') + ')'
    }
    throw 'Unsupported ingredient JSON; update the generator for the new schema.'
}
function Result-Text($Result) {
    if ($Result -is [string]) { return '1 x ' + $Result }
    $id = if ($Result.Contains('id')) { $Result.id } else { $Result.item }
    $text = (Scalar-Text (Value-OrDefault $Result 'count' 1)) + ' x ' + $id
    if ($Result.Contains('components')) {
        $parts = foreach ($entry in ($Result.components.GetEnumerator() | Sort-Object Key)) {
            # Component values may be structured JSON or scalar text.
            $value = if ($entry.Value -is [string]) { $entry.Value } else {
                ConvertTo-Json -InputObject $entry.Value -Depth 100 -Compress
            }
            $entry.Key + '=' + $value
        }
        $text += '; components: ' + ($parts -join '; ')
    }
    return $text
}
function Ingredients-Text($Data, [string]$Kind) {
    if ($Kind -eq 'machine') {
        $parts = for ($slot = 0; $slot -lt $Data.ingredients.Count; $slot++) {
            $ingredient = $Data.ingredients[$slot]
            'slot ' + $slot + ': ' + (Scalar-Text (Value-OrDefault $ingredient 'count' 1)) +
                ' x ' + (Ingredient-Text $ingredient)
        }
        return $parts -join '; '
    }
    if ($Data.type -eq 'minecraft:crafting_shaped') {
        $counts = @{}
        foreach ($row in $Data.pattern) {
            foreach ($character in $row.ToCharArray()) {
                if ($character -eq ' ') { continue }
                $symbol = [string]$character
                if (-not $Data.key.Contains($symbol)) { throw "Missing shape key: $symbol" }
                $ingredient = Ingredient-Text $Data.key[$symbol]
                if (-not $counts.ContainsKey($ingredient)) { $counts[$ingredient] = 0 }
                $counts[$ingredient]++
            }
        }
        $parts = foreach ($entry in ($counts.GetEnumerator() | Sort-Object Key)) {
            (Scalar-Text $entry.Value) + ' x ' + $entry.Key
        }
        $keys = foreach ($entry in ($Data.key.GetEnumerator() | Sort-Object Key)) {
            $entry.Key + '=' + (Ingredient-Text $entry.Value)
        }
        return 'shape [' + ($Data.pattern -join ' / ') + ']; keys [' + ($keys -join '; ') +
            ']; totals [' + ($parts -join '; ') + ']'
    }
    if ($Data.type -eq 'minecraft:crafting_shapeless') {
        $counts = @{}
        foreach ($value in $Data.ingredients) {
            $ingredient = Ingredient-Text $value
            if (-not $counts.ContainsKey($ingredient)) { $counts[$ingredient] = 0 }
            $counts[$ingredient]++
        }
        return (($counts.GetEnumerator() | Sort-Object Key | ForEach-Object {
            (Scalar-Text $_.Value) + ' x ' + $_.Key
        }) -join '; ')
    }
    if ($Data.type -in @('minecraft:smelting', 'minecraft:blasting', 'minecraft:smoking', 'minecraft:campfire_cooking')) {
        return '1 x ' + (Ingredient-Text $Data.ingredient)
    }
    throw "Unsupported vanilla recipe type: $($Data.type)"
}
function Cell-Text($Value) {
    return ([string]$Value).Replace('|', '\|').Replace('`', '\`').Replace("`r", '').Replace("`n", ' ')
}

$rows = @()
foreach ($kind in @('vanilla', 'machine')) {
    $folderName = if ($kind -eq 'vanilla') { 'recipe' } else { 'machine_recipes' }
    $folder = Join-Path $RepoRoot ('src/main/resources/data/lithmod/' + $folderName)
    if (-not (Test-Path -LiteralPath $folder -PathType Container)) { throw "Missing directory: $folderName" }
    foreach ($file in (Get-ChildItem -LiteralPath $folder -Filter '*.json' -File -Recurse)) {
        $data = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json -AsHashtable -Depth 100
        $relativeId = [System.IO.Path]::GetRelativePath($folder, $file.FullName).Replace('\', '/')
        $relativeId = $relativeId.Substring(0, $relativeId.Length - '.json'.Length)
        $source = [System.IO.Path]::GetRelativePath($RepoRoot, $file.FullName).Replace('\', '/')
        $fixture = $kind -eq 'machine' -and $data.machine -eq 'test_machine'
        $type = if ($kind -eq 'machine') { 'machine:' + $data.machine } else { $data.type }
        $timing = @()
        if ($data.Contains('ticks')) { $timing += (Scalar-Text $data.ticks) + ' ticks' }
        if ($data.Contains('cookingtime')) { $timing += (Scalar-Text $data.cookingtime) + ' cooking ticks' }
        if ($data.Contains('experience')) { $timing += (Scalar-Text $data.experience) + ' XP' }
        if ($data.Contains('energy_per_tick')) { $timing += (Scalar-Text $data.energy_per_tick) + ' FE/tick override' }
        $rows += [pscustomobject]@{
            Id = 'lithmod:' + $relativeId; Type = $type
            Ingredients = Ingredients-Text $data $kind; Result = Result-Text $data.result
            Timing = $timing -join '; '; Source = $source
            Notes = if ($fixture) { 'TEST-ONLY FIXTURE; not M1 progression' } else { '' }
        }
    }
}
$fixtureCount = @($rows | Where-Object Notes).Count
$lines = @(
    '# M1 recipe reference', '',
    'Generated by `tools/generate-m1-recipes.ps1` from the actual recipe JSON. Do not edit this guide by hand.', '',
    ('Recipes: ' + $rows.Count + ' total; ' + ($rows.Count - $fixtureCount) + ' production; ' + $fixtureCount + ' test-only fixtures.'), '',
    'Machine ingredient slot numbers are zero based. Ingredients and timings are editable placeholders. Mask templates specify the public design; the Pattern Generator supplies the configured use count on completion.', '',
    '| Recipe ID | Type | Ingredients | Result | Timing / energy | Source | Notes |',
    '| --- | --- | --- | --- | --- | --- | --- |'
)
foreach ($row in ($rows | Sort-Object Id, Type, Source)) {
    $cells = @($row.Id, $row.Type, $row.Ingredients, $row.Result, $row.Timing, $row.Source, $row.Notes)
    $lines += '| ' + (($cells | ForEach-Object { Cell-Text $_ }) -join ' | ') + ' |'
}
$content = ($lines -join "`n") + "`n"
$encoding = [System.Text.UTF8Encoding]::new($false)
$bytes = $encoding.GetBytes($content)
if ($Check) {
    if (-not (Test-Path -LiteralPath $outputPath -PathType Leaf)) { throw 'docs/recipes.md is missing; regenerate it.' }
    $actual = [System.IO.File]::ReadAllBytes($outputPath)
    if ([Convert]::ToBase64String($actual) -cne [Convert]::ToBase64String($bytes)) {
        throw 'docs/recipes.md is stale; run tools/generate-m1-recipes.ps1.'
    }
    Write-Host "Recipe guide matches $($rows.Count) JSON recipes."
} else {
    [void][System.IO.Directory]::CreateDirectory((Split-Path $outputPath -Parent))
    [System.IO.File]::WriteAllBytes($outputPath, $bytes)
    Write-Host "Generated docs/recipes.md from $($rows.Count) JSON recipes ($fixtureCount test-only fixtures)."
}

