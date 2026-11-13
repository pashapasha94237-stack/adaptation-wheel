param([string[]]$Tasks = @('build'))
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$portableJava = Get-ChildItem -Path '.tools\java' -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($portableJava) { $env:JAVA_HOME = $portableJava.FullName }
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    throw 'Java 17 JDK required. Set JAVA_HOME to a Java 17 JDK and run this script again.'
}
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-user-home'
& .\gradlew.bat @Tasks --console=plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
