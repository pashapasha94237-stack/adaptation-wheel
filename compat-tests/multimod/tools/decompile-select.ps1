param([Parameter(Mandatory)][string]$Name,[Parameter(Mandatory)][string]$Pattern)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$wheel=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$target="$root/audit/$Name-input.jar"
Add-Type -AssemblyName System.IO.Compression
$output=[IO.Compression.ZipFile]::Open($target,[IO.Compression.ZipArchiveMode]::Create)
$seen=@{}
try {
 $list=if(Test-Path "$root/audit/all24-with-nested.txt"){"$root/audit/all24-with-nested.txt"}else{"$root/audit/jars.txt"}
 foreach($path in (Get-Content $list)) {
  $zip=[IO.Compression.ZipFile]::OpenRead($path)
  try {foreach($e in $zip.Entries){if($e.FullName -match $Pattern -and $e.FullName.EndsWith('.class') -and !$seen.ContainsKey($e.FullName)) {
   $seen[$e.FullName]=$true;$dst=$output.CreateEntry($e.FullName);$src=$e.Open();$stream=$dst.Open()
   try{$src.CopyTo($stream)}finally{$stream.Dispose();$src.Dispose()}
  }}}finally{$zip.Dispose()}
 }
}finally{$output.Dispose()}
New-Item -ItemType Directory -Force "$root/audit/decompile-$Name"|Out-Null
& "$wheel/.tools/java/jdk-17.0.20.1+1/bin/java.exe" -Xmx2G -jar "$wheel/.gradle-user-home/caches/forge_gradle/maven_downloader/net/minecraftforge/forgeflower/2.0.629.0/forgeflower-2.0.629.0.jar" -log=WARN -dgs=1 $target "$root/audit/decompile-$Name" *> "$root/audit/decompile-$Name.log"
[IO.Compression.ZipFile]::ExtractToDirectory("$root/audit/decompile-$Name/$Name-input.jar","$root/audit/source-$Name",$true)
"Decompiled $($seen.Count) classes into audit/source-$Name"
