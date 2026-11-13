$ErrorActionPreference='Stop'
$wheel=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$path="$wheel/src/main/resources/compat/mod-attacks.json"
$manifest=Get-Content -Raw $path|ConvertFrom-Json -AsHashtable
$dispatch=@{
 'de/teamlapen/vampirism/util/DamageHandler'=@('hurtModded','hurtVanilla')
 'net/tslat/aoa3/util/DamageUtil'=@('safelyDealDamage')
 'stepsword/mahoutsukai/util/EffectUtil'=@('genericAttack','magicAttack')
 'com/bobmowzie/mowziesmobs/server/damage/DamageUtil'=@('dealMixedDamage')
 'twilightforest/util/EntityUtil'=@('properlyApplyCustomDamageSource')
}
$entries=@{};foreach($e in $manifest.entries){$entries[$e.key]=$e}
foreach($e in $entries.Values){if($dispatch.ContainsKey($e.owner)){$e.shared=$true}}
$ids=Get-Content -Raw "$PSScriptRoot/audit/modids.json"|ConvertFrom-Json
$count=0
foreach($row in $ids){
 $methods=Get-Content -Raw "$PSScriptRoot/audit/mods/$($row.file)/methods.json"|ConvertFrom-Json
 foreach($method in $methods){
  if($dispatch.ContainsKey($method.owner)){continue}
  $delegates=$false
  foreach($call in $method.calls){foreach($owner in $dispatch.Keys){if($call.StartsWith($owner+'.')){foreach($name in $dispatch[$owner]){if($call.StartsWith($owner+'.'+$name+'(')){$delegates=$true;break}}}}}
  if(!$delegates){continue}
  $semantic=$method.name -replace '^lambda\$([^$]+)\$\d+$','$1'
  $hash=[Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($method.owner+'#'+$semantic))
  $suffix=[Convert]::ToHexString($hash).Substring(0,8).ToLowerInvariant()
  $short=($method.owner -split '/')[-1] -replace '\$\d+$',''
  $token=($short+'/'+$semantic) -creplace '([a-z0-9])([A-Z])','$1_$2'
  $token=$token.ToLowerInvariant() -replace '[^a-z0-9_/.-]','_'
  if($token.Length -gt 160){$token=$token.Substring(0,160)}
  $key='attack:'+$row.id+':'+$token+'_'+$suffix
  if(!$entries.ContainsKey($key)){$entries[$key]=@{key=$key;mod=$row.id;label=$short+' / '+$semantic;owner=$method.owner;method=$method.name;delegate=$true};$count++}
 }
}
$manifest.entries=@($entries.Values|Sort-Object key)
[IO.File]::WriteAllText($path,($manifest|ConvertTo-Json -Depth 15 -Compress),[Text.UTF8Encoding]::new($false))
Write-Output ('Added '+$count+' real callers of shared damage dispatchers')
