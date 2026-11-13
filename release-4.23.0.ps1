$ErrorActionPreference = 'Stop'
$wheel = $PSScriptRoot
$boost = $env:ADAPT_BOOST_DIR  # folder of the Adapt Boost source project
$status = Join-Path $wheel 'release-4.23.0-status.txt'
Set-Content -LiteralPath $status -Value 'RUNNING' -Encoding UTF8
Start-Transcript -LiteralPath (Join-Path $wheel 'release-4.23.0.log') -Force | Out-Null

# Windows PowerShell 5.1 turns any stderr line of a redirected native program (even a
# compiler warning) into a terminating error under 'Stop'. Run Java with 'Continue' and
# judge success only by its exit code.
function Invoke-Java([string]$java,[string[]]$arguments,[string]$log) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $java @arguments *> $log; return $LASTEXITCODE }
    finally { $ErrorActionPreference = $previous }
}

try {
    $java = Join-Path $wheel '.tools\java\jdk-17.0.20.1+1\bin\java.exe'
    $env:JAVA_HOME = Join-Path $wheel '.tools\java\jdk-17.0.20.1+1'
    $env:GRADLE_USER_HOME = Join-Path $wheel '.gradle-user-home'

    Write-Output '=== Adapt Boost 1.6.1: clean build + core tests ==='
    $boostLog = Join-Path $wheel 'build-adapt-boost-1.6.1.log'
    $code = Invoke-Java $java @('-Xmx3G','-Dfile.encoding=UTF-8','-Dorg.gradle.internal.instrumentation.agent=false','-classpath',(Join-Path $boost 'gradle\wrapper\gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain','-p',$boost,'clean','build','--offline','--no-daemon','-Dorg.gradle.jvmargs=','--console=plain') $boostLog
    if ($code -ne 0) { Get-Content -LiteralPath $boostLog -Tail 40; throw "Adapt Boost build failed ($code)" }
    & (Join-Path $boost 'package-release.ps1')
    Copy-Item -LiteralPath (Join-Path $boost 'dist\adapt-boost-1.20.1-forge-1.6.1.jar') -Destination (Join-Path $wheel 'embedded') -Force
    Copy-Item -LiteralPath (Join-Path $boost 'dist\adapt-boost-1.20.1-forge-1.6.1-sources.zip') -Destination (Join-Path $wheel 'embedded-src') -Force

    foreach ($variant in @('offline','online')) {
        Write-Output "=== Adaptation Wheel 4.23.0-$($variant): build + tests ==="
        $arguments = @('-Xmx2G','-Dfile.encoding=UTF-8','-Dorg.gradle.internal.instrumentation.agent=false','-classpath',(Join-Path $wheel 'gradle\wrapper\gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain','-p',$wheel,'build','--offline','--no-daemon','-Dorg.gradle.jvmargs=','--console=plain')
        if ($variant -eq 'online') { $arguments += '-PneuralOnline=true' }
        $log = Join-Path $wheel ("build-4.23.0-$variant.log")
        $code = Invoke-Java $java $arguments $log
        if ($code -ne 0) { Get-Content -LiteralPath $log -Tail 40; throw "Wheel build failed: $variant ($code)" }
        Write-Output "Built and checked: $variant"
    }
    & (Join-Path $wheel 'package-release.ps1')
    Set-Content -LiteralPath $status -Value 'OK' -Encoding UTF8
}
catch {
    Write-Output ('ERROR: ' + $_)
    Set-Content -LiteralPath $status -Value ('FAILED: ' + $_) -Encoding UTF8
}
finally {
    Stop-Transcript | Out-Null
}
