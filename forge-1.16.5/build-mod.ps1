param([string[]]$Tasks = @('build'))
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$portJava = Get-ChildItem -LiteralPath '../.tools/java8' -Directory | Select-Object -First 1
$env:JAVA_HOME = $portJava.FullName
$env:GRADLE_USER_HOME = Join-Path (Split-Path $PSScriptRoot -Parent) '.gradle-user-home'
& (Join-Path $env:JAVA_HOME 'bin/java.exe') '-Xmx3G' '-Dfile.encoding=UTF-8' '-classpath' 'gradle/wrapper/gradle-wrapper.jar' org.gradle.wrapper.GradleWrapperMain @Tasks --no-daemon '-Dorg.gradle.jvmargs=' --console=plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
