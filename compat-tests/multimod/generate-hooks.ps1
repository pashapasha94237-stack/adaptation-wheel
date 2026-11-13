$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$wheel=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ids=Get-Content -Raw "$root/audit/modids.json"|ConvertFrom-Json
$dependencies=@('botarium','brandonscore','citadel','codechickenlib','coroutil','forgeendertech','resourcefulconfig','resourcefullib','zeta')
$classes=@{};$entries=@{};$evidence=@();$versions=@{}
function Token([string]$s){
 $s=$s -creplace '([a-z0-9])([A-Z])','$1_$2'
 return $s.ToLowerInvariant() -replace '[^a-z0-9_/.-]','_'
}
function Key([string]$mod,[string]$owner,[string]$name){
 $semantic=$name -replace '^lambda\$([^$]+)\$\d+$','$1'
 $short=($owner -split '/')[-1] -replace '\$\d+$',''
 if($owner -like '*IafDragonDestructionManager*' -and $semantic -match 'destroyArea(Breath|Charge)'){
  return 'attack:iceandfire:dragon_'+$Matches[1].ToLowerInvariant()
 }
 $hash=[Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($owner+'#'+$semantic))
 $suffix=[Convert]::ToHexString($hash).Substring(0,8).ToLowerInvariant()
 $path=(Token($short+'/'+$semantic))
 if($path.Length -gt 160){$path=$path.Substring(0,160)}
 return 'attack:'+$mod+':'+$path+'_'+$suffix
}
foreach($row in $ids){
 if($dependencies -contains $row.id){continue}
 $dir="$root/audit/mods/$($row.file)"
 $methods=Get-Content -Raw "$dir/methods.json"|ConvertFrom-Json
 $versions[$row.file]=$row.id
 foreach($m in $methods){
  $calls=@($m.calls | Where-Object {$_ -match '\.m_6469_\(Lnet/minecraft/world/damagesource/DamageSource;F\)Z'})
  if($calls.Count -eq 0){continue}
  # Overrides calling super.hurt are verified/filtered by the transformer, not intercepted recursively.
  if(!$classes.ContainsKey($m.owner)){$classes[$m.owner]=@{mod=$row.id;methods=@{}}}
  $key=Key $row.id $m.owner $m.name
  $classes[$m.owner].methods[$m.name+$m.desc]=@{key=$key;kind='hurt'}
  $entry=@{mod=$row.id;key=$key;owner=$m.owner;method=$m.name;label=(($m.owner -split '/')[-1]+' / '+($m.name -replace '^lambda\$([^$]+)\$\d+$','$1'))}
  $entries[$key]=$entry
  $evidence+=@{file=$row.file;owner=$m.owner;method=$m.name;descriptor=$m.desc;key=$key;calls=$calls.Count}
 }
}
# Explicit audited direct health attacks. Constructors, healing, revival, max-health
# synchronization and boss-only phase setters are intentionally not damage hooks.
$direct=@{
 'anomaly_rephased'=@('ALookTickProcedure')
 'alexsmobs'=@('GrizzlyBearAIAprilFools')
 'aoa3'=@('SupremacySword','UltimatumStaffTask')
 'born_in_chaos_v1'=@('LifestealPriNalozhieniiEffiektaProcedure','ShellMacePriUdariePoSushchnostiInstrumientomProcedure')
 'botania'=@('BottledManaItem')
 'draconicevolution'=@('LaserBeamPhase')
 'eternal_tales'=@('BloodsuckerProjectileHitsLivingEntityProcedure','BloodyTalwarLivingEntityIsHitWithToolProcedure','DamagedAstralLivingEntityIsHitWithToolProcedure','DynastySwordLivingEntityIsHitWithToolProcedure','InfectionEffectExpiresProcedure','RestoredAstralLivingEntityIsHitWithToolProcedure','SigilLivingEntityIsHitWithToolProcedure')
 'mahoutsukai'=@('RetributionSpellEffect','ReversionEyesPotion')
}
foreach($row in $ids){
 if(!$direct.ContainsKey($row.id)){continue}
 $methods=Get-Content -Raw "$root/audit/mods/$($row.file)/methods.json"|ConvertFrom-Json
 foreach($m in $methods){
  if(($m.calls -match '\.m_21153_\(F\)V').Count -eq 0){continue}
  if($direct[$row.id] -notcontains ($m.owner -split '/')[-1]){continue}
  if(!$classes.ContainsKey($m.owner)){$classes[$m.owner]=@{mod=$row.id;methods=@{}}}
  $key=Key $row.id $m.owner $m.name
  $signature=$m.name+$m.desc
  if($classes[$m.owner].methods.ContainsKey($signature)){$classes[$m.owner].methods[$signature].kind='hurt_health'}
  else{$classes[$m.owner].methods[$signature]=@{key=$key;kind='health'}}
  $entries[$key]=@{mod=$row.id;key=$key;owner=$m.owner;method=$m.name;label=(($m.owner -split '/')[-1]+' / '+$m.name)}
 }
}
$manifest=@{schema=1;versions=$versions;classes=$classes;entries=@($entries.Values|Sort-Object key)}
$utf=[Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText("$root/audit/hook-evidence.json",($evidence|ConvertTo-Json -Depth 10),$utf)
[IO.Directory]::CreateDirectory("$wheel/src/main/resources/compat")|Out-Null
[IO.File]::WriteAllText("$wheel/src/main/resources/compat/mod-attacks.json",($manifest|ConvertTo-Json -Depth 12 -Compress),$utf)
Write-Output ('Audited methods: '+$evidence.Count+'; transformer classes: '+$classes.Count+'; attack families: '+$entries.Count)
