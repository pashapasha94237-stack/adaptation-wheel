$ErrorActionPreference = 'Stop'
$wheel = $PSScriptRoot
$status = Join-Path $wheel 'release-4.24.0-status.txt'
Set-Content -LiteralPath $status -Value 'RUNNING' -Encoding UTF8
Start-Transcript -LiteralPath (Join-Path $wheel 'release-4.24.0.log') -Force | Out-Null
function Invoke-Java([string]$java,[string[]]$arguments,[string]$log) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $java @arguments *> $log; return $LASTEXITCODE }
    finally { $ErrorActionPreference = $previous }
}
try {
    $java = Join-Path $wheel '.tools/java/jdk-17.0.20.1+1/bin/java.exe'
    $env:JAVA_HOME = Join-Path $wheel '.tools/java/jdk-17.0.20.1+1'
    $env:GRADLE_USER_HOME = Join-Path $wheel '.gradle-user-home'
    $payloads = @('embedded/adapt-boost-1.20.1-forge-1.6.1.jar','embedded/distant-horizons-3.3.3-1.20.1.jar')
    $before = @{}; foreach ($name in $payloads) { $before[$name] = (Get-FileHash -LiteralPath (Join-Path $wheel $name)).Hash }
    # Rebuild the wheel only: preserve the exact optimizer and DH from 4.23.
    $staging = Join-Path $wheel 'artifacts/release-4.24.0'
    New-Item -ItemType Directory -Force -Path $staging | Out-Null
    foreach ($variant in @('offline','online')) {
        $arguments = @('-Xmx2G','-Dfile.encoding=UTF-8','-Dorg.gradle.internal.instrumentation.agent=false','-classpath',(Join-Path $wheel 'gradle/wrapper/gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain','-p',$wheel,'clean','build','--offline','--no-daemon','-Dorg.gradle.jvmargs=','--console=plain','-x','downloadMCMeta','-x','downloadAssets')
        if ($variant -eq 'online') { $arguments += '-PneuralOnline=true' }
        $log = Join-Path $wheel ("build-4.24.0-$variant.log")
        $code = Invoke-Java $java $arguments $log
        if ($code -ne 0) { Get-Content -LiteralPath $log -Tail 40; throw "Wheel build failed: $variant ($code)" }
        Copy-Item -LiteralPath (Join-Path $wheel "build/libs/adaptation-wheel-1.20.1-4.24.0-$variant.jar") -Destination $staging -Force
        Write-Output "Built and checked: $variant"
    }
    Copy-Item -Path (Join-Path $staging '*.jar') -Destination (Join-Path $wheel 'build/libs') -Force
    foreach ($name in $payloads) { if ((Get-FileHash -LiteralPath (Join-Path $wheel $name)).Hash -ne $before[$name]) { throw "Embedded payload changed: $name" } }
    & (Join-Path $wheel 'package-release.ps1')
    Set-Content -LiteralPath $status -Value 'OK' -Encoding UTF8
}
catch {
    Write-Output ('ERROR: ' + $_)
    Set-Content -LiteralPath $status -Value ('FAILED: ' + $_) -Encoding UTF8
    throw
}
finally { Stop-Transcript | Out-Null }
