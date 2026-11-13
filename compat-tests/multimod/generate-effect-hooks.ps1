$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$wheel=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$parents=@{};$definitions=@{}
$directories=@(Get-ChildItem "$root/audit/mods" -Directory)+@(Get-ChildItem "$root/audit/extra-mods" -Directory)+@(Get-ChildItem "$root/audit/nested-mods" -Directory)
foreach($dir in $directories){foreach($cls in (Get-Content -Raw "$($dir.FullName)/classes.json"|ConvertFrom-Json)){$parents[$cls.name]=$cls.super}}
function IsEffect([string]$owner) {
 for($n=0;$n -lt 16 -and $owner;$n++) {
  if($owner -in @('net/minecraft/world/effect/MobEffect','net/minecraft/world/effect/InstantenousMobEffect')){return $true}
  $owner=$parents[$owner]
 }
 return $false
}
foreach($dir in $directories){
 foreach($method in (Get-Content -Raw "$($dir.FullName)/methods.json"|ConvertFrom-Json)) {
  if(!(IsEffect $method.owner)){continue}
  if($method.name -eq 'm_6742_' -and $method.desc -eq '(Lnet/minecraft/world/entity/LivingEntity;I)V') {$kind='tick'}
  elseif($method.name -eq 'm_19461_' -and $method.desc -eq '(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V') {$kind='instant'}
  elseif($method.name -eq 'm_6386_' -and $method.owner -eq 'net/mcreator/borninchaosv/potion/CursedMarkMobEffect') {$kind='punitive_expiry'}
  elseif($method.name -eq 'tick' -and $method.desc -eq '(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/effect/MobEffectInstance;I)V') {$kind='extended_tick'}
  elseif($method.name -eq 'onApplication' -and $method.desc -eq '(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;I)V') {$kind='extended_application'}
  elseif($method.name -in @('beforeIncomingAttack','canApplyOther') -and $method.desc.EndsWith(')Z')) {$kind='extended_allow'}
  elseif($method.name -eq 'modifyIncomingAttackDamage' -and $method.desc -eq '(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/damagesource/DamageSource;F)F') {$kind='extended_incoming'}
  elseif($method.name -eq 'modifyOutgoingAttackDamage' -and $method.desc -eq '(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/damagesource/DamageSource;F)F') {$kind='extended_outgoing'}
  elseif($method.name -in @('afterIncomingAttack','afterOutgoingAttack') -and $method.desc.EndsWith(')V')) {$kind='extended_tick'}
  else{continue}
  if(!$definitions.ContainsKey($method.owner)){$definitions[$method.owner]=@{}}
  $definitions[$method.owner][$method.name+$method.desc]=$kind
 }
}
$json=@{schema=1;classes=$definitions}|ConvertTo-Json -Depth 10 -Compress
[IO.File]::WriteAllText("$wheel/src/main/resources/compat/mod-effects.json",$json,[Text.UTF8Encoding]::new($false))
"Native effect classes: $($definitions.Count)"
