param(
    [Parameter(Mandatory=$true)][string]$GroupFile,
    [Parameter(Mandatory=$true)][string]$WheelJar,
    [string]$MinecraftDirectory = (Join-Path $env:APPDATA '.minecraft'),
    [string]$ForgeVersionName = 'Forge 1.20.1',
    [string]$RunName = 'run-russian-qa',
    [switch]$Quick,
    [string]$ResumeFrom = '',
    [string[]]$ExtraJvm = @()
)
$ErrorActionPreference='Stop'
$projectDirectory=$PSScriptRoot
$smokeDirectory=Join-Path $projectDirectory ($RunName+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $smokeDirectory,(Join-Path $smokeDirectory 'mods'),(Join-Path $smokeDirectory 'native') | Out-Null
if($ResumeFrom){
    $fixtureSource=(Resolve-Path -LiteralPath $ResumeFrom).Path
    $fixtureRoot=(Resolve-Path -LiteralPath $projectDirectory).Path.TrimEnd('\')+'\'
    if(!$fixtureSource.StartsWith($fixtureRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Resume is restricted to isolated probe fixtures.'}
    if(!(Test-Path -LiteralPath (Join-Path $fixtureSource 'test-group.json'))){throw 'Missing isolated fixture marker.'}
    Copy-Item -LiteralPath (Join-Path $fixtureSource 'saves') -Destination $smokeDirectory -Recurse
    # Facts the wheel keeps outside worlds (mod list, version, crash marker) live in the game directory.
    if(Test-Path -LiteralPath (Join-Path $fixtureSource 'adaptationwheel')){Copy-Item -LiteralPath (Join-Path $fixtureSource 'adaptationwheel') -Destination $smokeDirectory -Recurse}
}
$group=Get-Content -Raw -LiteralPath $GroupFile | ConvertFrom-Json
foreach($path in $group.jars){Copy-Item -LiteralPath $path -Destination (Join-Path $smokeDirectory 'mods')}
# Optional pre-seeded files, e.g. a mod's config: "files": {"config/x.toml": "text"}.
if($group.files){foreach($file in $group.files.PSObject.Properties){
    $target=Join-Path $smokeDirectory $file.Name
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    [IO.File]::WriteAllText($target,$file.Value,(New-Object Text.UTF8Encoding($false)))
}}
Copy-Item -LiteralPath (Join-Path $projectDirectory 'build/libs/multimod-compat-qa-1.0.0.jar') -Destination (Join-Path $smokeDirectory 'mods')
Copy-Item -LiteralPath $WheelJar -Destination (Join-Path $smokeDirectory 'mods')
Copy-Item -LiteralPath $GroupFile -Destination (Join-Path $smokeDirectory 'test-group.json')
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
    '${launcher_name}' = 'ModCompatibilityQA'
    '${launcher_version}' = '1'
    '${classpath}' = ($classPaths -join ';')
    '${library_directory}' = (Join-Path $MinecraftDirectory 'libraries')
    '${version_name}' = $ForgeVersionName
    '${classpath_separator}' = ';'
    '${auth_player_name}' = 'ModCompatibilityQA'
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
$launchArguments.Add('-Xmx8G')
foreach($extra in $ExtraJvm){$launchArguments.Add($extra.Replace('{run}',$smokeDirectory))}
if($group.neuralMock){$launchArguments.Add('--add-modules');$launchArguments.Add('jdk.httpserver')}
$launchArguments.Add('-Dmultimodqa.group=' + (Join-Path $smokeDirectory 'test-group.json'))
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
# Optional "window": [width, height]; the QA window stays hidden, so it cannot be resized once open.
$windowSize=if($group.window){$group.window}else{@(960,640)}
$launchArguments.Add('--width'); $launchArguments.Add([string]$windowSize[0]); $launchArguments.Add('--height'); $launchArguments.Add([string]$windowSize[1])
$argFile = Join-Path $smokeDirectory 'java-arguments.txt'
$encodedArguments = $launchArguments | ForEach-Object { '"' + $_.Replace('\','/').Replace('"','\"') + '"' }
[IO.File]::WriteAllLines($argFile,$encodedArguments,[Text.UTF8Encoding]::new($false))
# Optional "options": extra options.txt lines, e.g. "guiScale:2".
$options=@('lang:ru_ru','onboardAccessibility:false','maxFps:60','enableVsync:false','fullscreen:false','pauseOnLostFocus:false','renderDistance:6','simulationDistance:5','tutorialStep:none')+@($group.options|Where-Object{$_})
[IO.File]::WriteAllLines((Join-Path $smokeDirectory 'options.txt'),[string[]]$options,[Text.UTF8Encoding]::new($false))

$javaPath=Join-Path $PSScriptRoot '..\..\..\.tools\java\jdk-17.0.20.1+1\bin\java.exe'
# Mods such as Essential write shared files under %APPDATA% (.minecraft\essential, gg.essential.mod) whatever
# --gameDir says; the client gets its own APPDATA so the player's files stay untouched.
$realAppData=$env:APPDATA
$env:APPDATA=(New-Item -ItemType Directory -Force -Path (Join-Path $smokeDirectory 'appdata')).FullName
try{$process=Start-Process -FilePath $javaPath -ArgumentList ('@"'+$argFile+'"') -WorkingDirectory $smokeDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $smokeDirectory 'stdout.log') -RedirectStandardError (Join-Path $smokeDirectory 'stderr.log')}
finally{$env:APPDATA=$realAppData}
# Windows PowerShell 5.1 reports no ExitCode unless the process handle was read while it ran.
$null=$process.Handle
Write-Output ('Mod QA directory: '+$smokeDirectory)
Write-Output ('Mod QA PID: '+$process.Id)
$process.WaitForExit()
Write-Output ('Mod QA exit: '+$process.ExitCode)
if($process.ExitCode -ne 0){throw 'Mod QA client crashed; read isolated logs/latest.log'}
$log=Get-Content -LiteralPath (Join-Path $smokeDirectory 'logs/latest.log') -Raw
if($log -notmatch 'MULTIMOD_QA PASS:'){throw 'JJC translation runtime checks did not complete.'}
Write-Output 'Mod compatibility runtime QA completed.'

