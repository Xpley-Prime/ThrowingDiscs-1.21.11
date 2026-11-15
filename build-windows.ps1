$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$gradleVersion = '9.2.1'
$root = Join-Path $PSScriptRoot '.gradle-distribution'
$gradle = Join-Path $root "gradle-$gradleVersion\bin\gradle.bat"
if (!(Test-Path $gradle)) {
    New-Item -ItemType Directory -Force -Path $root | Out-Null
    $zip = Join-Path $root "gradle-$gradleVersion-bin.zip"
    Write-Host 'Fetching Gradle from services.gradle.org ...'
    Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $root -Force
    Remove-Item $zip
}
& $gradle build --stacktrace
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host 'Success. Find your mod in build\libs\palimpsest-0.1.0.jar'
