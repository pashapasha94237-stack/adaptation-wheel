$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$target="$root/inputs/nested-audit"
New-Item -ItemType Directory -Force $target|Out-Null
Add-Type -AssemblyName System.IO.Compression
$queue=[Collections.Generic.Queue[string]]::new()
$seen=@{};$nested=[Collections.Generic.List[string]]::new();$inventory=[Collections.Generic.List[object]]::new()
foreach($path in (Get-Content "$root/audit/all24-jars.txt")){$queue.Enqueue($path)}
while($queue.Count -gt 0) {
 $parent=$queue.Dequeue();$archive=[IO.Compression.ZipFile]::OpenRead($parent)
 try {foreach($entry in $archive.Entries){
  if(!$entry.FullName.EndsWith('.jar')){continue}
  $memory=[IO.MemoryStream]::new();$stream=$entry.Open()
  try{$stream.CopyTo($memory)}finally{$stream.Dispose()}
  $bytes=$memory.ToArray();$memory.Dispose();$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
  if($seen.ContainsKey($hash)){continue}
  $file=[IO.Path]::GetFileNameWithoutExtension($entry.FullName)+'--'+$hash.Substring(0,8).ToLowerInvariant()+'.jar'
  $path=Join-Path $target $file;[IO.File]::WriteAllBytes($path,$bytes)
  $seen[$hash]=$true;$nested.Add($path);$queue.Enqueue($path)
  $inventory.Add(@{parent=[IO.Path]::GetFileName($parent);entry=$entry.FullName;path=$path;sha256=$hash;bytes=$bytes.Length})
 }}finally{$archive.Dispose()}
}
$nested|Set-Content "$root/audit/nested-jars.txt"
$inventory|ConvertTo-Json -Depth 6|Set-Content "$root/audit/nested-inventory.json"
Get-Content "$root/audit/all24-jars.txt","$root/audit/nested-jars.txt"|Set-Content "$root/audit/all24-with-nested.txt"
"Extracted $($nested.Count) distinct nested libraries for private bytecode analysis"
