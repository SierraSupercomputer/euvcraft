<#
.SYNOPSIS
Generates a deterministic recipes.md documentation from all JSON recipe files.
.DESCRIPTION
Scans src/main/resources/data/lithmod/recipe and machine_recipes recursively,
outputs docs/recipes.md with namespace ID, type, ingredients with counts,
result and components, and source file. Includes all files (production + test fixtures).
Marks test fixtures clearly as test-only.
.OUTPUTS
docs/recipes.md
#>

param([string]$RepoRoot=(Split-Path $PSScriptRoot -Parent),[switch]$Check)
Set-StrictMode -Version Latest; $ErrorActionPreference='Stop'

$RecipeDirs=@((Join-Path $RepoRoot "src/main/resources/data/lithmod/recipe"),(Join-Path $RepoRoot "src/main/resources/data/lithmod/machine_recipes"))
$OutputFile=Join-Path $RepoRoot "docs/recipes.md"
foreach($dir in $RecipeDirs){if(-not(Test-Path $dir)){throw"Missing directory: $dir"}}
$AllFiles=@();foreach($dir in $RecipeDirs){$AllFiles+=Get-ChildItem -Path $dir -Filter "*.json" -Recurse|Sort-Object FullName}

function Get-NamespaceId{param([string]$FullPath);$rel=$FullPath.Substring($RepoRoot.Length+1).Replace('\','/');$rel=$rel-replace'^src/main/resources/data/lithmod/(recipe|machine_recipes)/','';$rel-replace'\.json$',''}
function Format-VanillaIngredients{param($json);$type=$json.type
if($type-eq'minecraft:crafting_shaped'){$key=$json.key;$pattern=$json.pattern;if(-not$key-or-not$pattern){return"unknown"};$counts=@{};foreach($row in$pattern){foreach($ch in$row.ToCharArray()){if($ch-ne' '){$sym=[string]$ch;$counts[$sym]=($counts[$sym]+1)}}};$parts=@();foreach($kv in$counts.GetEnumerator()|Sort-Object Key){$sym=$kv.Key;$count=$kv.Value;$ing=$key[$sym];if($ing){if($ing-is[string]){$parts+="$count x $ing"}elseif($ing.item){$parts+="$count x $($ing.item)"}elseif($ing.tag){$parts+="$count x #$($ing.tag)"}}}};$patternStr=($pattern-join' ');$keyStr=($key.GetEnumerator()|Sort-Object Key|ForEach-Object{"$($_.Key)=$($_.Value)"})-join', ';return"Shape: [$patternStr] | Keys: {$keyStr} | Counts: $($parts-join', ')"}
elseif($type-eq'minecraft:crafting_shapeless'){$counts=@{};foreach($ing in$json.ingredients){$id=if($ing-is[string]){$ing}elseif($ing.item){$ing.item}elseif($ing.tag){"#$($ing.tag)"}else{'unknown'};$counts[$id]=($counts[$id]+($ing.count??1))};$parts=@();foreach($kv in$counts.GetEnumerator()|Sort-Object Key){$parts+="$($kv.Value) x $($kv.Key)"};return"Shapeless: $($parts-join', ')"}
elseif($type-in@('minecraft:smelting','minecraft:blasting','minecraft:smoking','minecraft:campfire_cooking')){$ing=$json.ingredient;$time=$json.cookingtime??$json.cookingTime??'?';$exp=$json.experience??'0';$ingStr=if($ing-is[string]){$ing}elseif($ing.item){$ing.item}elseif($ing.tag){"#$($ing.tag)"}else{'unknown'};return"Ingredient: $ingStr | Time: $time ticks | XP: $exp"}else{return"Unknown vanilla type: $type"}}
function Format-MachineIngredients{param($ingredients);$parts=@();$slot=0;foreach($ing in$ingredients){$slot++;$kind=if($ing.tag){"tag"}else{"item"};$id=if($ing.tag){$ing.tag}else{$ing.item};$count=$ing.count??1;$parts+="Slot ${slot}: $count x $kind($id)"};$parts-join'; '}
function Format-Components{param($c);if(-not$c){return''};$p=@();foreach($kv in$c.GetEnumerator()|Sort-Object Key){$v=$kv.Value|ConvertTo-Json -Compress -Depth 10;$v=$v-replace'\|','\\|'-replace'`','\`';$p+="$($kv.Key)=$v"};if($p.Count){return" | Components: {$($p-join', ')}"};return''}
function Format-Result{param($r,$isVanilla);$id=if($isVanilla){$r.id??$r.item??'unknown'}else{$r.item??'unknown'};$count=$r.count??1;$comp=Format-Components $r.components;return"$count x $id$comp"}

$lines=@("# EUVCraft M1-11 Recipe Documentation","","| Namespace ID | Type | Ingredients | Result | Source File | Notes |","|--------------|------|-------------|--------|-------------|-------|")
$rows=@();foreach($file in$AllFiles){$json=Get-Content $file.FullName -Raw|ConvertFrom-Json -AsHashtable;$nsId="lithmod:"+$(Get-NamespaceId $file.FullName);$relPath=$file.FullName.Substring($RepoRoot.Length+1).Replace('\','/');$notes='';$machineId=$json.machine;if($machineId-eq'test_machine'-or$file.Name-like'test_machine_*.json'){$notes='TEST FIXTURE'};$type=$json.type;$machine=$json.machine
if($type){$ingredients=Format-VanillaIngredients $json;$result=Format-Result $json.result $true;$typeDisplay=$type}elseif($machine){$ingredients=Format-MachineIngredients $json.ingredients;$result=Format-Result $json.result $false;$ticks=$json.ticks??'?';$ep=$json.energy_per_tick;$typeDisplay="machine:$machine";if($ep){$typeDisplay+=" | FE/tick override: $ep"};$typeDisplay+=" | Ticks: $ticks"}else{$ingredients=$result='unknown';$typeDisplay='unknown'}
$esc={param($s)$s-replace'\|','\\|'-replace'`','\`'};$rows+=[pscustomobject]@{NsId=$nsId;TypeDisplay=&$esc $typeDisplay;Ingredients=&$esc $ingredients;Result=&$esc $result;RelPath=&$esc $relPath;Notes=&$esc $notes}}
$rows|Sort-Object NsId,TypeDisplay,RelPath|ForEach-Object{$lines+="| $($_.NsId) | $($_.TypeDisplay) | $($_.Ingredients) | $($_.Result) | $($_.RelPath) | $($_.Notes) |"}
$mdContent=($lines-join"`n")+"`n"
if($Check){if(-not(Test-Path$OutputFile)){throw"Output file missing: $OutputFile"};$ex=Get-Content$OutputFile -Raw;if($ex-ne$mdContent){throw"Output file is stale or differs from generation"}}else{$mdContent|Out-File -FilePath$OutputFile -Encoding utf8NoBOM;Write-Host"Generated $OutputFile with $($AllFiles.Count) recipes."}