param(
    [Parameter(Mandatory=$true)][string]$TranslatedMod,
    [Parameter(Mandatory=$true)][string]$WheelJar,
    [string]$MinecraftDirectory = (Join-Path $env:APPDATA '.minecraft'),
    [string]$ForgeVersionName = 'Forge 1.20.1',
    [string]$RunName = 'run-russian-qa',
    [switch]$Quick
)
$ErrorActionPreference='Stop'
$projectDirectory=$PSScriptRoot
$smokeDirectory=Join-Path $projectDirectory ($RunName+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $smokeDirectory,(Join-Path $smokeDirectory 'mods'),(Join-Path $smokeDirectory 'native') | Out-Null
$mod=Get-Item -LiteralPath $TranslatedMod
$copiedMod=Join-Path $smokeDirectory ('mods/'+$mod.Name)
Copy-Item -LiteralPath $mod.FullName -Destination $copiedMod
Copy-Item -LiteralPath (Join-Path $projectDirectory 'build/libs/t2s-russian-qa-1.0.0.jar') -Destination (Join-Path $smokeDirectory 'mods')
Copy-Item -LiteralPath $WheelJar -Destination (Join-Path $smokeDirectory 'mods')
foreach($dependency in @('geckolib-forge-1.20.1-4.8.4.jar','player-animation-lib-forge-1.0.2-rc1-1.20.jar')){
 $dependencyPath=Join-Path $MinecraftDirectory ('mods/'+$dependency)
 if(!(Test-Path -LiteralPath $dependencyPath)){
  $cached=Join-Path (Split-Path (Split-Path $projectDirectory)) ('multi-mod-compat-1.20.1/probe/run-all24-411-skyproof-20261021-224957/mods/'+$dependency)
  if(!(Test-Path -LiteralPath $cached)){throw "Missing test dependency: $dependency"}
  $dependencyPath=$cached
 }
 Copy-Item -LiteralPath $dependencyPath -Destination (Join-Path $smokeDirectory 'mods')
}
$versionDirectory = Join-Path $MinecraftDirectory ('versions/' + $ForgeVersionName)
$metadata = Get-Content -Raw -LiteralPath (Join-Path $versionDirectory ($ForgeVersionName + '.json')) | ConvertFrom-Json
function Test-Allowed($rules) {
    if (!$rules -or $rules.Count -eq 0) { return $true }
    $isAllowed = $false
    foreach ($rule in $rules) {
        $matchesRule = $true
        if ($rule.os.name -and $rule.os.name -ne 'windows') { $matchesRule = $false }
        if ($rule.os.arch -and $rule.os.arch -notin @('x86_64','amd64')) { $matchesRule = $false }
        if ($rule.features) { $matchesRule = $false }
        if ($matchesRule) { $isAllowed = $rule.action -eq 'allow' }
    }
    return $isAllowed
}
$classPaths = [System.Collections.Generic.List[string]]::new()
foreach ($library in $metadata.libraries) {
    if (!(Test-Allowed $library.rules)) { continue }
    $relativePath = $library.artifact.path
    if (!$relativePath) { $relativePath = 'libraries/' + $library.downloads.artifact.path }
    if (!$relativePath -or $relativePath -eq 'libraries/') { continue }
    if (!$relativePath.StartsWith('libraries/')) { $relativePath = 'libraries/' + $relativePath }
    $absolutePath = Join-Path $MinecraftDirectory $relativePath
    if (!(Test-Path -LiteralPath $absolutePath)) { throw "Missing installed library: $relativePath" }
    $classPaths.Add($absolutePath)
}
$classPaths.Add((Join-Path $versionDirectory ($ForgeVersionName + '.jar')))
$replacements = @{
    '${natives_directory}' = (Join-Path $smokeDirectory 'native')
    '${launcher_name}' = 'JJCRussianQA'
    '${launcher_version}' = '1'
    '${classpath}' = ($classPaths -join ';')
    '${library_directory}' = (Join-Path $MinecraftDirectory 'libraries')
    '${version_name}' = $ForgeVersionName
    '${classpath_separator}' = ';'
    '${auth_player_name}' = 'JJCRussianQA'
    '${game_directory}' = $smokeDirectory
    '${assets_root}' = (Join-Path $MinecraftDirectory 'assets')
    '${assets_index_name}' = $metadata.assetIndex.id
    '${auth_uuid}' = '00000000000030008000000000000001'
    '${auth_access_token}' = '0'
    '${clientid}' = '0'
    '${auth_xuid}' = '0'
    '${user_type}' = 'legacy'
    '${version_type}' = 'release'
}
function Expand-LaunchArgument([string]$argument) {
    foreach ($entry in $replacements.GetEnumerator()) { $argument = $argument.Replace($entry.Key,[string]$entry.Value) }
    if ($argument.Contains('${')) { throw "Unresolved launch argument: $argument" }
    return $argument
}
$launchArguments = [System.Collections.Generic.List[string]]::new()
$launchArguments.Add('-Xmx4G')
$launchArguments.Add('-Dt2sqa.mod=' + $copiedMod)
if($Quick){$launchArguments.Add('-Dt2sqa.quick=true')}
foreach ($argumentGroup in $metadata.arguments.jvm) {
    if ($argumentGroup -is [string]) { $launchArguments.Add((Expand-LaunchArgument $argumentGroup)); continue }
    if (Test-Allowed $argumentGroup.rules) { foreach ($argument in $argumentGroup.values) { $launchArguments.Add((Expand-LaunchArgument $argument)) } }
}
# Reuse installed native DLLs read-only; any newly extracted files go to the isolated test directory.
$launchArguments.Add('-Djava.library.path=' + (Join-Path $versionDirectory 'natives'))
$launchArguments.Add($metadata.mainClass)
foreach ($argumentGroup in $metadata.arguments.game) {
    if ($argumentGroup -is [string]) { $launchArguments.Add((Expand-LaunchArgument $argumentGroup)); continue }
    if (Test-Allowed $argumentGroup.rules) { foreach ($argument in $argumentGroup.values) { $launchArguments.Add((Expand-LaunchArgument $argument)) } }
}
$launchArguments.Add('--width'); $launchArguments.Add('960'); $launchArguments.Add('--height'); $launchArguments.Add('640')
$argFile = Join-Path $smokeDirectory 'java-arguments.txt'
$encodedArguments = $launchArguments | ForEach-Object { '"' + $_.Replace('\','/').Replace('"','\"') + '"' }
[IO.File]::WriteAllLines($argFile,$encodedArguments,[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllLines((Join-Path $smokeDirectory 'options.txt'),@('lang:ru_ru','onboardAccessibility:false','maxFps:60','enableVsync:false','fullscreen:false','pauseOnLostFocus:false','renderDistance:6','simulationDistance:5'),[Text.UTF8Encoding]::new($false))

$javaPath=Join-Path $PSScriptRoot '..\..\.tools\java\jdk-17.0.20.1+1\bin\java.exe'
$process=Start-Process -FilePath $javaPath -ArgumentList ('@"'+$argFile+'"') -WorkingDirectory $smokeDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $smokeDirectory 'stdout.log') -RedirectStandardError (Join-Path $smokeDirectory 'stderr.log')
Write-Output ('JJC QA directory: '+$smokeDirectory)
Write-Output ('JJC QA PID: '+$process.Id)
$process.WaitForExit()
Write-Output ('JJC QA exit: '+$process.ExitCode)
if($process.ExitCode -ne 0){throw 'JJC QA client crashed; read isolated logs/latest.log'}
$log=Get-Content -LiteralPath (Join-Path $smokeDirectory 'logs/latest.log') -Raw
if($log -notmatch 'JJC_WHEEL_QA PASS:'){throw 'JJC translation runtime checks did not complete.'}
Write-Output 'JJC Russian translation runtime QA completed.'

