$ErrorActionPreference='Stop'
$wheel=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$root=$PSScriptRoot
$manifest=Get-Content -Raw "$wheel/src/main/resources/compat/mod-attacks.json"|ConvertFrom-Json -AsHashtable
$modIds=Get-Content -Raw "$root/audit/modids.json"|ConvertFrom-Json
$dictionaries=@{}
foreach($row in $modIds){
 $resources=Get-Content -Raw "$root/audit/mods/$($row.file)/resources.json"|ConvertFrom-Json -AsHashtable
 if(!$dictionaries.ContainsKey($row.id)){$dictionaries[$row.id]=@{en_us=@{};ru_ru=@{}}}
 foreach($file in $resources.Keys){
  if($file -notmatch 'assets/[^/]+/lang/(en_us|ru_ru)\.json$'){continue}
  $locale=$Matches[1]
  foreach($key in $resources[$file].Keys){$dictionaries[$row.id][$locale][$key]=$resources[$file][$key]}
 }
}
$manual=@{
 'attack:iceandfire:dragon_breath/fire'=@('Fire dragon breath','Огненное дыхание дракона')
 'attack:iceandfire:dragon_breath/ice'=@('Ice dragon breath','Ледяное дыхание дракона')
 'attack:iceandfire:dragon_breath/lightning'=@('Lightning dragon breath','Громовое дыхание дракона')
 'attack:iceandfire:dragon_charge/fire'=@('Fire dragon charge','Огненный заряд дракона')
 'attack:iceandfire:dragon_charge/ice'=@('Ice dragon charge','Ледяной заряд дракона')
 'attack:iceandfire:dragon_charge/lightning'=@('Lightning dragon charge','Громовой заряд дракона')
 'attack:ad_astra:oxygen_api_impl/entity_tick_27362741'=@('Space vacuum','Космический вакуум')
 'attack:ad_astra:temperature_api_impl/burn_entity_d174e659'=@('Extreme planetary heat','Экстремальная жара на планете')
 'attack:ad_astra:temperature_api_impl/freeze_entity_8445ab38'=@('Extreme planetary cold','Экстремальный холод на планете')
 'attack:adpother:entity_events/kill_weak_living_dd918636'=@('Polluted atmosphere','Загрязнённая атмосфера')
 'attack:alexsmobs:grizzly_bear_aiapril_fools/m_8037__2b1a13bf'=@('Grizzly bear frightening grab','Пугающий захват медведя')
 'attack:anomaly_rephased:alook_tick_procedure/execute_4d949fb0'=@('Anomalous gaze: health depletion','Аномальный взгляд: истощение здоровья')
 'attack:aoa3:supremacy_sword/do_melee_effect_490c2ef2'=@('Supremacy Sword execution','Меч превосходства: добивание')
 'attack:aoa3:ultimatum_staff_task/run_794db302'=@('Ultimatum Staff: exchange of life','Посох ультиматума: обмен жизнью')
 'attack:born_in_chaos_v1:lifesteal_pri_nalozhienii_effiekta_procedure/execute_952e5452'=@('Life drain','Похищение жизни')
 'attack:born_in_chaos_v1:shell_mace_pri_udarie_po_sushchnosti_instrumientom_procedure/execute_4b03b13b'=@('Shell mace: piercing blow','Пробивающий удар ракушечной булавы')
}
function Human([string]$s){
 $s=$s -replace '^(Entity|Item|Effect)','' -replace '(Entity|MobEffect|Procedure|Potion|ApiImpl|BlockEntity|Impl|Manager|Overrides)$',''
 return ($s -creplace '([a-z0-9])([A-Z])','$1 $2') -replace '[_$]',' '
}
function Action([string]$s,[string]$locale){
 $ru=$locale -eq 'ru_ru'
 $rules=@('lightning|thunder|electric','fire|flame|burn','ice|frost|freez','laser|beam|sunstrike','explos|deton|bomb','poison|venom|acid|toxic','radiat|irradiat','blood|bleed','soul|magic|spell|curse','oxygen|suffocat|vacuum','projectile|spit|arrow|m_5790_|m_6532_','bite|melee|hurtEnemy|m_6129_')
 $en=@('Lightning attack','Fire attack','Freezing attack','Beam attack','Explosion','Toxic attack','Radiation','Blood attack','Magical attack','Suffocation','Projectile impact','Melee attack')
 $names=@('Электрическая атака','Огненная атака','Замораживающая атака','Лучевая атака','Взрыв','Токсичная атака','Радиация','Атака кровью','Магическая атака','Удушье','Попадание снаряда','Ближняя атака')
 for($i=0;$i -lt $rules.Count;$i++){if($s -match $rules[$i]){if($ru){return $names[$i]}else{return $en[$i]}}}
 if($ru){return 'Особое воздействие'}else{return 'Special attack'}
}
$utf=[Text.UTF8Encoding]::new($false)
foreach($locale in @('en_us','ru_ru')){
 $path="$wheel/src/main/resources/assets/adaptationwheel/lang/$locale.json"
 $lang=Get-Content -Raw $path|ConvertFrom-Json -AsHashtable
 foreach($entry in $manifest.entries){
  if($entry.key -in @('attack:iceandfire:dragon_breath','attack:iceandfire:dragon_charge')){continue}
  $short=($entry.owner -split '/')[-1] -replace '\$.*',''
  $human=Human $short
  $normalized=($human -replace '[^a-zA-Z0-9]','').ToLowerInvariant()
  $native=$null
  foreach($key in $dictionaries[$entry.mod].en_us.Keys){
   if($key -notmatch '^(entity|item|block|effect)\.[^.]+\.([^.]*)$'){continue}
   if(($Matches[2] -replace '[^a-zA-Z0-9]','').ToLowerInvariant() -ne $normalized){continue}
   if($dictionaries[$entry.mod][$locale].ContainsKey($key)){$native=$dictionaries[$entry.mod][$locale][$key]}
   else{$native=$dictionaries[$entry.mod].en_us[$key]}
   break
  }
  if(!$native){$native=$human}
  if($native -match '^m \d+' -or $native.Length -lt 2){$native=$entry.mod}
  $action=Action ($entry.owner+'/'+$entry.method) $locale
  $translation='attack.adaptationwheel.'+$entry.key.Substring(7).Replace(':','.').Replace('/','.')
  $lang[$translation]=$native+' — '+$action+' ['+$entry.mod+']'
 }
 foreach($key in $manual.Keys){
  $translation='attack.adaptationwheel.'+$key.Substring(7).Replace(':','.').Replace('/','.')
  $lang[$translation]=$manual[$key][[int]($locale -eq 'ru_ru')]
 }
 if($manifest.ContainsKey('projectiles')){foreach($id in $manifest.projectiles.Keys){
  $projectile=$manifest.projectiles[$id];$ns=($id -split ':')[0];$part=($id -split ':')[1]
  $nativeKey='entity.'+$ns+'.'+$part.Replace('/','.')
  $label=if($dictionaries[$ns][$locale].ContainsKey($nativeKey)){$dictionaries[$ns][$locale][$nativeKey]}elseif($dictionaries[$ns].en_us.ContainsKey($nativeKey)){$dictionaries[$ns].en_us[$nativeKey]}else{Human $part}
  $translation='attack.adaptationwheel.'+$projectile.key.Substring(7).Replace(':','.').Replace('/','.')
  $lang[$translation]=$label+' — '+$(if($locale -eq 'ru_ru'){'Попадание снаряда'}else{'Projectile impact'})+' ['+$ns+']'
 }}
 $lang['condition.adaptationwheel.iceandfire_frozen']=if($locale -eq 'ru_ru'){'Драконья заморозка'}else{'Dragon freezing'}
 $lang['condition.adaptationwheel.weather2_tornado']=if($locale -eq 'ru_ru'){'Затягивание торнадо'}else{'Tornado suction'}
 $lang['condition.adaptationwheel.iceandfire_siren_charm']=if($locale -eq 'ru_ru'){'Чары песни сирены'}else{'Siren song charm'}
 $lang['condition.adaptationwheel.anomaly_execution']=if($locale -eq 'ru_ru'){'Смертельный захват Аномалии'}else{'Anomaly execution'}
 [IO.File]::WriteAllText($path,($lang|ConvertTo-Json -Depth 8),$utf)
}
