$ErrorActionPreference='Stop'
$wheel=$PSScriptRoot
$env:JAVA_HOME=Join-Path $wheel '.tools/java/jdk-17.0.20.1+1'
$env:GRADLE_USER_HOME=Join-Path $wheel '.gradle-user-home'
$java=Join-Path $env:JAVA_HOME 'bin/java.exe'
$staging=Join-Path $wheel 'artifacts/release-4.42.0'
New-Item -ItemType Directory -Force -Path $staging | Out-Null
$payloads=@('embedded/adapt-boost-1.20.1-forge-1.7.0.jar','embedded/distant-horizons-3.3.3-1.20.1.jar')
$hashes=@{};foreach($name in $payloads){$hashes[$name]=(Get-FileHash -LiteralPath (Join-Path $wheel $name)).Hash}
foreach($variant in @('trained','untrained','trained-online','untrained-online')){
    $arguments=@('-Xmx2G','-Dfile.encoding=UTF-8','-Dorg.gradle.internal.instrumentation.agent=false','-classpath',(Join-Path $wheel 'gradle/wrapper/gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain','-p',$wheel,'clean','build','--offline','--no-daemon','-Dorg.gradle.jvmargs=','--console=plain','-x','downloadMCMeta','-x','downloadAssets')
    if($variant.StartsWith('untrained')){$arguments+='-PpretrainedKnowledge=false'}
    if($variant.EndsWith('-online')){$arguments+='-PneuralOnline=true'}
    $prior=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try{& $java @arguments *> (Join-Path $wheel ('build-4.42.0-'+$variant+'.log'));$code=$LASTEXITCODE}finally{$ErrorActionPreference=$prior}
    if($code -ne 0){throw "Build failed: $variant ($code)"}
    Copy-Item -LiteralPath (Join-Path $wheel ('build/libs/adaptation-wheel-1.20.1-4.42.0-'+$variant+'.jar')) -Destination $staging -Force
    Write-Output ('Built and tested '+$variant)
}
Copy-Item -Path (Join-Path $staging '*.jar') -Destination (Join-Path $wheel 'build/libs') -Force
foreach($name in $payloads){if((Get-FileHash -LiteralPath (Join-Path $wheel $name)).Hash -ne $hashes[$name]){throw "Embedded payload changed: $name"}}
& (Join-Path $wheel 'package-release.ps1')
