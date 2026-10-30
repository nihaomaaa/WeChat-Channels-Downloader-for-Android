param([Parameter(Mandatory=$true)][string]$SigningConfig)
$ErrorActionPreference = 'Stop'
$configPath = (Resolve-Path -LiteralPath $SigningConfig).Path
$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$key = Join-Path (Split-Path $configPath -Parent) $config.keystore
if (!(Test-Path -LiteralPath $key)) { throw 'Signing keystore is missing' }
$names = @('STREAMSAVE_KEYSTORE','STREAMSAVE_STORE_PASSWORD','STREAMSAVE_KEY_ALIAS','STREAMSAVE_KEY_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:STREAMSAVE_KEYSTORE = (Resolve-Path -LiteralPath $key).Path
    $env:STREAMSAVE_STORE_PASSWORD = $config.storePassword
    $env:STREAMSAVE_KEY_ALIAS = $config.alias
    $env:STREAMSAVE_KEY_PASSWORD = $config.storePassword
    Push-Location (Split-Path $PSScriptRoot -Parent)
    try {
        & .\gradlew.bat assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed' }
    } finally { Pop-Location }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
