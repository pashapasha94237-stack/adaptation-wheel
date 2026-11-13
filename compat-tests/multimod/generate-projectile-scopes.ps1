$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$wheel=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifest=Get-Content -Raw "$wheel/src/main/resources/compat/mod-attacks.json"|ConvertFrom-Json -AsHashtable
$hierarchy=@{}
foreach($folder in @('mods','extra-mods','nested-mods')){
 if(!(Test-Path "$root/audit/$folder")){continue}
 foreach($file in (Get-ChildItem "$root/audit/$folder" -Filter classes.json -Recurse -File)){
  foreach($cls in (Get-Content -Raw -LiteralPath $file.FullName|ConvertFrom-Json)){$hierarchy[$cls.name]=$cls.super}
 }
}
$owners=[Collections.Generic.HashSet[string]]::new()
foreach($definition in $manifest.projectiles.Values){
 $owner=$definition.entity
 while($owner -and $owner -ne 'net/minecraft/world/entity/Entity' -and $owner -ne 'java/lang/Object'){
  if(!$owners.Add($owner)){break}
  $owner=$hierarchy[$owner]
 }
}
$classes=@($owners|Sort-Object)
[IO.File]::WriteAllText("$wheel/src/main/resources/compat/projectile-scopes.json",(@{classes=$classes}|ConvertTo-Json -Depth 4 -Compress),[Text.UTF8Encoding]::new($false))
Write-Output "Projectile classes and audited ancestors: $($classes.Count)"
