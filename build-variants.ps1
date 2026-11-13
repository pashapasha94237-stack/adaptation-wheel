param([switch]$Offline,[switch]$SkipAssets)
$ErrorActionPreference='Stop'
$projectRoot=$PSScriptRoot
$bundledJava=Join-Path $projectRoot '.tools/java/jdk-17.0.20.1+1'
if(Test-Path -LiteralPath $bundledJava){$env:JAVA_HOME=$bundledJava}
$javaExecutable=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'java.exe'}
if(!$env:GRADLE_USER_HOME){$env:GRADLE_USER_HOME=Join-Path $projectRoot '.gradle-user-home'}
foreach($variant in @('offline','online')){
    $arguments=@('-Xmx2G','-Dfile.encoding=UTF-8','-Dorg.gradle.internal.instrumentation.agent=false','-classpath',(Join-Path $projectRoot 'gradle/wrapper/gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain','-p',$projectRoot,'build','--no-daemon','-Dorg.gradle.jvmargs=','--console=plain')
    if($variant -eq 'online'){$arguments+='-PneuralOnline=true'}
    if($Offline){$arguments+='--offline'}
    if($SkipAssets){$arguments+=@('-x','downloadMCMeta','-x','downloadAssets')}
    $log=Join-Path $projectRoot ('build-4.16.0-'+$variant+'.log')
    # Windows PowerShell 5.1 turns compiler warnings on stderr into terminating errors under Stop.
    $ErrorActionPreference='Continue'
    & $javaExecutable @arguments *> $log
    $exitCode=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    if($exitCode -ne 0){Get-Content -LiteralPath $log -Tail 35;throw "Build failed: $variant"}
    Write-Output ("Built and checked: $variant")
}
& (Join-Path $projectRoot 'package-release.ps1')
