$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$manifest=Get-Content -Raw (Join-Path $PSScriptRoot '..\..\..\src\main\resources\compat\mod-attacks.json')|ConvertFrom-Json -AsHashtable
$out=[Collections.Generic.List[string]]::new()
foreach($row in (Get-Content -Raw "$root/audit/modids.json"|ConvertFrom-Json)) {
 $methods=Get-Content -Raw "$root/audit/mods/$($row.file)/methods.json"|ConvertFrom-Json
 foreach($m in $methods) {
  $calls=@($m.calls|Where-Object {$_ -match '\.(m_21153_\(F\)V|m_6074_|m_6667_|m_146870_|m_146873_|m_38710_|setHealth|kill\(|die\(|remove\(|discard\()'})
  if(!$calls.Count){continue}
  $hooked=$manifest.classes.ContainsKey($m.owner) -and $manifest.classes[$m.owner].methods.ContainsKey($m.name+$m.desc)
  $out.Add(($row.id,$row.file,$m.owner,($m.name+$m.desc),$hooked,($calls -join ' | '),($m.strings -join ' | ')) -join "`t")
 }
}
$out|Set-Content "$root/audit/gap-dangerous.tsv"
$out|Where-Object {($_ -split "`t")[4] -eq 'False' -and $_ -match 'm_21153_|m_6074_|m_6667_'}
