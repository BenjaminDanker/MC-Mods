param(
    [ValidateSet('Start', 'Stop', 'Test')][string]$Action = 'Start',
    [string]$MariaDbDirectory = '.local/tools/mariadb-11.4.10-winx64'
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$local = Join-Path $repo '.local'
$dbData = Join-Path $local 'mariadb-data'
$credentialFile = Join-Path $local 'mariadb-credential.xml'
$dbTools = [IO.Path]::GetFullPath((Join-Path $repo $MariaDbDirectory))
$dbPort = 33317
if (-not (Test-Path (Join-Path $dbTools 'bin/mariadbd.exe'))) {
    throw 'Unpack the official MariaDB Windows ZIP under .local/tools first; see docs/LOCAL_VERIFICATION.md.'
}
New-Item -ItemType Directory -Path $local -Force | Out-Null
if (-not (Test-Path $credentialFile)) {
    if (Test-Path $dbData) { throw 'Existing data without matching local credentials; refusing to initialize.' }
    $generatedPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $credential = [PSCredential]::new('root', (ConvertTo-SecureString $generatedPassword -AsPlainText -Force))
    $credential | Export-Clixml -LiteralPath $credentialFile
} else {
    $credential = Import-Clixml -LiteralPath $credentialFile
}
if ($Action -eq 'Start') {
    if (-not (Test-Path $dbData)) {
        & (Join-Path $dbTools 'bin/mariadb-install-db.exe') "--datadir=$dbData" "--port=$dbPort" "--password=$($credential.GetNetworkCredential().Password)" --silent
        if ($LASTEXITCODE -ne 0) { throw 'MariaDB initialization failed' }
    }
    $listener = Get-NetTCPConnection -LocalPort $dbPort -State Listen -ErrorAction SilentlyContinue
    if ($listener) { throw "Port $dbPort is already in use; no second server was started." }
    $arguments = @('--no-defaults', "--basedir=`"$dbTools`"", "--datadir=`"$dbData`"",
        '--bind-address=127.0.0.1', "--port=$dbPort", '--innodb-buffer-pool-size=64M',
        '--max-connections=24', '--skip-name-resolve', '--console')
    $process = Start-Process -FilePath (Join-Path $dbTools 'bin/mariadbd.exe') -ArgumentList $arguments `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $local 'mariadb-out.log') `
        -RedirectStandardError (Join-Path $local 'mariadb-error.log')
    Write-Output "Started local MariaDB PID $($process.Id) on 127.0.0.1:$dbPort; data: $dbData"
} elseif ($Action -eq 'Stop') {
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $credential.GetNetworkCredential().Password
        & (Join-Path $dbTools 'bin/mariadb-admin.exe') --no-defaults --host=127.0.0.1 "--port=$dbPort" --user=root shutdown
        if ($LASTEXITCODE -ne 0) { throw 'Local MariaDB shutdown failed' }
    } finally { $env:MYSQL_PWD = $previousPassword }
} else {
    $oldUrl = $env:PET_TEST_DB_URL
    $oldUser = $env:PET_TEST_DB_USER
    $oldPassword = $env:PET_TEST_DB_PASSWORD
    try {
        $env:PET_TEST_DB_URL = "jdbc:mariadb://127.0.0.1:$dbPort/aipets_test"
        $env:PET_TEST_DB_USER = $credential.UserName
        $env:PET_TEST_DB_PASSWORD = $credential.GetNetworkCredential().Password
        Push-Location $repo
        try {
            & .\gradlew.bat :pet-service:test --tests com.silver.aipets.service.persistence.MariaDbAcceptanceTest --offline
            if ($LASTEXITCODE -ne 0) { throw 'Local MariaDB acceptance test failed' }
        } finally { Pop-Location }
    } finally {
        $env:PET_TEST_DB_URL = $oldUrl
        $env:PET_TEST_DB_USER = $oldUser
        $env:PET_TEST_DB_PASSWORD = $oldPassword
    }
}
