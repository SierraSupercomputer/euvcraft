<#
.SYNOPSIS
Generates a deterministic recipes.md documentation from all JSON recipe files.
.DESCRIPTION
Scans src/main/resources/data/lithmod/recipe and machine_recipes recursively,
outputs docs/recipes.md with namespace ID, type, ingredients with counts,
result and components, and source file. Includes all 30 files (28 production + 2 test fixtures).
Marks test fixtures clearly as test-only.
.OUTPUTS
docs/recipes.md
#>

param()

# Script root is the directory containing this script
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
# Default repo root is parent of script root
$RepoRoot = Split-Path -Parent $ScriptRoot

# Recipe directories to scan
$RecipeDirs = @(
    Join-Path $RepoRoot "src/main/resources/data/lithmod/recipe",
    Join-Path $RepoRoot "src/main/resources/data/lithmod/machine_recipes"
)

# Output file
$OutputFile = Join-Path $RepoRoot "docs/recipes.md"

# Collect all JSON files
$AllFiles = @()
foreach ($dir in $RecipeDirs) {
    if (Test-Path $dir) {
        $files = Get-ChildItem -Path $dir -Filter "*.json" -Recurse | Sort-Object FullName
        $AllFiles += $files
    }
}

# Helper: Convert path to namespace ID (relative path without .json, including crafting/)
function Get-NamespaceId {
    param([string]$FullPath)
    $rel = $FullPath.Substring($RepoRoot.Length + 1).Replace('\', '/')
    $rel -replace '\.json$', ''
}

# Helper: Format ingredient for vanilla shaped crafting
function Format-VanillaIngredients {
    param($json)
    $type = $json.type
    if ($type -eq 'minecraft:crafting_shaped') {
        $key = $json.key
        $pattern = $json.pattern
        if ($null -eq $key -or $null -eq $pattern) { return "unknown" }
        $counts = @{}
        foreach ($row in $pattern) {
            foreach ($ch in $row.ToCharArray()) {
                if ($ch -ne ' ') {
                    $counts[$ch] = ($counts[$ch] + 1)
                }
            }
        }
        $parts = @()
        foreach ($kv in $counts.GetEnumerator()) {
            $symbol = $kv.Key
            $count = $kv.Value
            $ingredient = $key[$symbol]
            if ($null -ne $ingredient) {
                if ($ingredient -is [string]) {
                    $parts += "$count x $ingredient"
                } elseif ($ingredient.item) {
                    $parts += "$count x $($ingredient.item)"
                } elseif ($ingredient.tag) {
                    $parts += "$count x #$($ingredient.tag)"
                }
            }
        }
        # Also show pattern and key
        $patternStr = ($pattern -join ' ')
        $keyStr = ($key | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', '
        return "Shape: [$patternStr] | Keys: {$keyStr} | Counts: $($parts -join ', ')"
    } elseif ($type -eq 'minecraft:crafting_shapeless') {
        $ingredients = $json.ingredients
        $parts = @()
        foreach ($ing in $ingredients) {
            if ($ing -is [string]) {
                $parts += "1 x $ing"
            } elseif ($ing.item) {
                $c = if ($ing.count) { $ing.count } else { 1 }
                $parts += "$c x $($ing.item)"
            } elseif ($ing.tag) {
                $c = if ($ing.count) { $ing.count } else { 1 }
                $parts += "$c x #$($ing.tag)"
            }
        }
        return "Shapeless: $($parts -join ', ')"
    } elseif ($type -eq 'minecraft:smelting' -or $type -eq 'minecraft:blasting' -or $type -eq 'minecraft:smoking' -or $type -eq 'minecraft:campfire_cooking') {
        $ing = $json.ingredient
        $time = if ($json.cookingtime) { $json.cookingtime } elseif ($json.cookingTime) { $json.cookingTime } else { '?' }
        $exp = if ($json.experience) { $json.experience } else { '0' }
        $ingStr = if ($ing -is [string]) { $ing } elseif ($ing.item) { $ing.item } elseif ($ing.tag) { "#$($ing.tag)" } else { 'unknown' }
        return "Ingredient: $ingStr | Time: $time ticks | XP: $exp"
    } else {
        return "Unknown vanilla type: $type"
    }
}

# Helper: Format machine recipe ingredients
function Format-MachineIngredients {
    param($ingredients)
    $parts = @()
    $slot = 0
    foreach ($ing in $ingredients) {
        $slot++
        $kind = if ($ing.tag) { "tag" } else { "item" }
        $id = if ($ing.tag) { $ing.tag } else { $ing.item }
        $count = if ($ing.count) { $ing.count } else { 1 }
        $parts += "Slot $slot: $count x $kind($id)"
    }
    return $parts -join '; '
}

# Helper: Format machine recipe result
function Format-MachineResult {
    param($result)
    $item = $result.item
    $count = if ($result.count) { $result.count } else { 1 }
    $compStr = ''
    if ($result.components) {
        $compParts = @()
        foreach ($key in $result.components.Keys) {
            $val = $result.components[$key]
            $compParts += "$key=$val"
        }
        if ($compParts.Count -gt 0) {
            $compStr = " | Components: {$($compParts -join ', ')}"
        }
    }
    return "$count x $item$compStr"
}

# Helper: Format vanilla result
function Format-VanillaResult {
    param($result)
    $id = if ($result.id) { $result.id } elseif ($result.item) { $result.item } else { 'unknown' }
    $count = if ($result.count) { $result.count } else { 1 }
    $compStr = ''
    if ($result.components) {
        $compParts = @()
        foreach ($key in $result.components.Keys) {
            $val = $result.components[$key]
            $compParts += "$key=$val"
        }
        if ($compParts.Count -gt 0) {
            $compStr = " | Components: {$($compParts -join ', ')}"
        }
    }
    return "$count x $id$compStr"
}

# Build markdown lines
$lines = @()
$lines += "# EUVCraft M1-11 Recipe Documentation"
$lines += ""
$lines += "Generated on $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') by generate-m1-recipes.ps1"
$lines += ""
$lines += "| Namespace ID | Type | Ingredients | Result | Source File | Notes |"
$lines += "|--------------|------|-------------|--------|-------------|-------|"

foreach ($file in $AllFiles) {
    $json = Get-Content $file.FullName -Raw | ConvertFrom-Json
    $nsId = Get-NamespaceId $file.FullName
    $relPath = $file.FullName.Substring($RepoRoot.Length + 1).Replace('\', '/')
    $isTest = $false
    $notes = ''

    if ($nsId -like '*test_machine*') {
        $isTest = $true
        $notes = 'TEST FIXTURE'
    }

    $type = $json.type
    $machine = $json.machine

    if ($type) {
        # Vanilla recipe
        $ingredients = Format-VanillaIngredients $json
        $result = Format-VanillaResult $json.result
        $typeDisplay = $type
    } elseif ($machine) {
        # Custom machine recipe
        $ingredients = Format-MachineIngredients $json.ingredients
        $result = Format-MachineResult $json.result
        $ticks = $json.ticks
        $epOverride = $json.energy_per_tick
        $typeDisplay = "machine:$machine"
        if ($epOverride) {
            $typeDisplay += " (FE/tick override: $epOverride)"
        }
        $typeDisplay += " | Ticks: $ticks"
    } else {
        $ingredients = 'unknown'
        $result = 'unknown'
        $typeDisplay = 'unknown'
    }

    $notesEscaped = $notes -replace '\|', '\\|'
    $ingredientsEscaped = $ingredients -replace '\|', '\\|'
    $resultEscaped = $result -replace '\|', '\\|'
    $lines += "| $nsId | $typeDisplay | $ingredientsEscaped | $resultEscaped | $relPath | $notesEscaped |"
}

# Write output
$mdContent = $lines -join "`n"
$mdContent | Out-File -FilePath $OutputFile -Encoding utf8NoBOM

Write-Host "Generated $OutputFile with $($AllFiles.Count) recipes."