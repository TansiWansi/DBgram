param(
    [ValidateSet('start', 'database', 'adminer', 'frontend', 'build', 'test', 'stop', 'frontend-stop', 'adminer-stop', 'database-stop', 'status', 'env', 'firewall')]
    [string]$Action = 'start',
    [switch]$DatabaseOnly,
    [switch]$Hosted,
    [ValidateRange(1, 65535)][int]$DatabasePort = 55432,
    [ValidateRange(1, 65535)][int]$ApiPort = 8080,
    [ValidateRange(1, 65535)][int]$WebPort = 8787,
    [ValidateRange(1, 65535)][int]$AdminerPort = 8082,
    [string]$ApiBindAddress = '127.0.0.1',
    [string]$WebBindAddress = '0.0.0.0',
    [string]$AppOrigin = '',
    [string]$JavaHome = '',
    [string]$MavenHome = ''
)

$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $project '.runtime'
$configPath = Join-Path $runtime 'local.json'
$processPath = Join-Path $runtime 'processes.json'
$pgBin = Join-Path $runtime 'postgres/pgsql/bin'
$pgData = Join-Path $runtime 'pgdata'
$jar = Join-Path $project 'backend/target/dbgram-backend-0.0.1-SNAPSHOT.jar'
$webExport = Join-Path $project 'frontend/dist'
New-Item -ItemType Directory -Force $runtime | Out-Null

if ($DatabaseOnly) {
    $Action = 'database'
}

if (-not (Test-Path -LiteralPath $configPath)) {
    $initialConfig = @{
        DatabasePassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
        JwtSecret = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
    }
    $initialConfig | ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath $configPath
}
$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
if (-not $config.DatabasePassword -or -not $config.JwtSecret) {
    throw 'The local database password and JWT secret are missing from .runtime/local.json.'
}

$owned = @{}
if (Test-Path -LiteralPath $processPath) {
    $parsed = Get-Content -LiteralPath $processPath -Raw | ConvertFrom-Json
    if ($null -ne $parsed) {
        foreach ($property in $parsed.psobject.properties) {
            $owned[$property.Name] = $property.Value
        }
    }
}

function Save-Processes {
    $owned | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 -LiteralPath $processPath
}

function Get-OwnedProcess([string]$Name) {
    $record = $owned[$Name]
    if (-not $record) {
        return $null
    }
    $running = Get-Process -Id $record.pid -ErrorAction SilentlyContinue
    if (-not $running) {
        return $null
    }
    if (-not $running.StartTime -or -not $running.Path) {
        throw "Cannot verify the recorded $Name process. Run the launcher from the Windows account that started DBgram."
    }
    # A PID can be reused. Check the executable and start time before stopping it.
    if ($running.StartTime.ToUniversalTime().Ticks -ne [long]$record.started -or $running.Path -ne $record.executable) {
        return $null
    }
    return $running
}

function Stop-OwnedProcess([string]$Name) {
    $running = Get-OwnedProcess $Name
    if ($running) {
        Stop-Process -Id $running.Id
        $running.WaitForExit(10000) | Out-Null
        Write-Output "Stopped $Name."
    }
    $owned.Remove($Name)
    if ($Name -eq 'api') {
        $owned.Remove('apiPid')
    }
    Save-Processes
}

function Test-Port([int]$Port) {
    $connection = [Net.Sockets.TcpClient]::new()
    try {
        return $connection.ConnectAsync('127.0.0.1', $Port).Wait(1000) -and $connection.Connected
    } catch {
        return $false
    } finally {
        $connection.Dispose()
    }
}

function Start-OwnedProcess([string]$Name, [string]$Executable, [string[]]$Arguments, [int]$Port) {
    $Executable = (Resolve-Path -LiteralPath $Executable).Path
    $running = Get-OwnedProcess $Name
    if ($running) {
        if ($owned[$Name].port -ne $Port) {
            throw "$Name is already running on port $($owned[$Name].port). Stop it before changing ports."
        }
        Write-Output "$Name is already running (PID $($running.Id))."
        return
    }
    if (Test-Port $Port) {
        throw "Port $Port is occupied by a process outside this launcher. No process was stopped."
    }
    $started = Start-Process -FilePath $Executable -ArgumentList $Arguments `
        -WorkingDirectory $project -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $runtime "$Name.log") `
        -RedirectStandardError (Join-Path $runtime "$Name-error.log")
    $owned[$Name] = @{
        pid = $started.Id
        started = $started.StartTime.ToUniversalTime().Ticks
        executable = $Executable
        port = $Port
    }
    if ($Name -eq 'api') {
        $owned.apiPid = $started.Id
    }
    Save-Processes
}

function Wait-Http([string]$Url, [string]$Name) {
    $lastFailure = 'No successful HTTP response.'
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $running = Get-OwnedProcess $Name
        if (-not $running) {
            throw "$Name exited during startup. Read .runtime/$Name-error.log and .runtime/$Name.log."
        }
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                return
            }
        } catch {
            $lastFailure = $_.Exception.Message
            Start-Sleep -Milliseconds 500
        }
    }
    throw "$Name did not become ready at $Url. $lastFailure Read the logs in .runtime."
}

function Show-WebAddresses {
    Write-Output "Frontend ready: http://localhost:$WebPort (same-origin /api proxy)."
    if ($WebBindAddress -eq '127.0.0.1' -or $WebBindAddress -eq 'localhost') {
        return
    }
    # A gateway excludes disconnected adapters and the host-only WSL interface.
    foreach ($adapter in [Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()) {
        if ($adapter.OperationalStatus -ne 'Up') {
            continue
        }
        $properties = $adapter.GetIPProperties()
        if ($properties.GatewayAddresses.Count -eq 0) {
            continue
        }
        foreach ($unicast in $properties.UnicastAddresses) {
            $address = $unicast.Address
            if ($address.AddressFamily -eq 'InterNetwork' -and -not [Net.IPAddress]::IsLoopback($address)) {
                if ($WebBindAddress -eq '0.0.0.0' -or $WebBindAddress -eq $address.ToString()) {
                    Write-Output "Same Wi-Fi: http://${address}:$WebPort/"
                }
            }
        }
    }
}

function Enable-LanFirewall {
    $principal = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Windows requires Administrator permission to change its firewall. Run make -C frontend firewall in an Administrator terminal.'
    }
    $node = (Get-Command node.exe -ErrorAction Stop).Source
    $ruleName = "DBgram-Web-LAN-$WebPort"
    $rule = Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue
    $settings = @{
        Direction = 'Inbound'
        Action = 'Allow'
        Enabled = 'True'
        Profile = 'Any'
        Protocol = 'TCP'
        LocalPort = $WebPort
        RemoteAddress = 'LocalSubnet'
        Program = $node
        EdgeTraversalPolicy = 'Block'
    }
    # Limit access to Node's website port from this subnet, on either Wi-Fi profile.
    if ($rule) {
        Set-NetFirewallRule -Name $ruleName @settings | Out-Null
    } else {
        New-NetFirewallRule -Name $ruleName -DisplayName "DBgram website (local network, port $WebPort)" @settings | Out-Null
    }
    Write-Output "Windows Firewall allows DBgram on TCP $WebPort from the local subnet."
}

function Start-Database {
    if (-not (Test-Path -LiteralPath (Join-Path $pgBin 'pg_ctl.exe'))) {
        throw 'Extract PostgreSQL 16 Windows binaries into .runtime/postgres/pgsql first. See docs/LOCAL.md.'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $pgData 'PG_VERSION'))) {
        $passwordFile = Join-Path $runtime 'initdb-password'
        [IO.File]::WriteAllText($passwordFile, $config.DatabasePassword)
        try {
            & (Join-Path $pgBin 'initdb.exe') -D $pgData -U dbgram --auth=scram-sha-256 --encoding=UTF8 --locale=C --pwfile=$passwordFile
            if ($LASTEXITCODE -ne 0) {
                throw 'PostgreSQL initialization failed.'
            }
        } finally {
            Remove-Item -LiteralPath $passwordFile -ErrorAction SilentlyContinue
        }
    }
    & (Join-Path $pgBin 'pg_ctl.exe') status -D $pgData *> $null
    if ($LASTEXITCODE -ne 0) {
        & (Join-Path $pgBin 'pg_ctl.exe') start -D $pgData -l (Join-Path $runtime 'postgres.log') -o "-p $DatabasePort -h 127.0.0.1" -w
        if ($LASTEXITCODE -ne 0) {
            throw 'PostgreSQL startup failed. Read .runtime/postgres.log.'
        }
    }
    $env:PGPASSWORD = $config.DatabasePassword
    try {
        $exists = & (Join-Path $pgBin 'psql.exe') -h 127.0.0.1 -p $DatabasePort -U dbgram -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='dbgram'"
        if ($LASTEXITCODE -ne 0) {
            throw "Cannot connect to the local database on port $DatabasePort using .runtime/local.json."
        }
        if ($exists -ne '1') {
            & (Join-Path $pgBin 'createdb.exe') -h 127.0.0.1 -p $DatabasePort -U dbgram dbgram
            if ($LASTEXITCODE -ne 0) {
                throw 'Database creation failed.'
            }
        }
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
    Write-Output "PostgreSQL: localhost:$DatabasePort (database/user: dbgram)."
}

function Set-JavaEnvironment {
    $candidates = @($JavaHome, $env:JAVA_HOME, 'C:/tools/jdk-21.0.5+11')
    foreach ($candidate in $candidates) {
        if (-not $candidate) {
            continue
        }
        $binary = Join-Path $candidate 'bin/java.exe'
        if (-not (Test-Path -LiteralPath $binary)) {
            continue
        }
        $pref = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $version = & $binary -version 2>&1 | Out-String
        $ErrorActionPreference = $pref
        if ($version -match 'version "21[."]') {
            $env:JAVA_HOME = (Resolve-Path -LiteralPath $candidate).Path
            $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
            Write-Output "Java 21: $($env:JAVA_HOME)"
            return
        }
    }
    throw 'Java 21 is required. Set JAVA_HOME or pass -JavaHome with a JDK 21 directory.'
}

function Set-ApiEnvironment {
    $env:JWT_SECRET = $config.JwtSecret
    $env:DATABASE_URL = "jdbc:postgresql://localhost:$DatabasePort/dbgram"
    $env:DATABASE_USER = 'dbgram'
    $env:DATABASE_PASSWORD = $config.DatabasePassword
    # Explicit Spring variables also override any stale values inherited from a terminal.
    $env:SPRING_DATASOURCE_URL = $env:DATABASE_URL
    $env:SPRING_DATASOURCE_USERNAME = $env:DATABASE_USER
    $env:SPRING_DATASOURCE_PASSWORD = $env:DATABASE_PASSWORD
    $env:SPRING_PROFILES_ACTIVE = 'portable'
    $env:COOKIE_SECURE = 'false'
    $env:PORT = $ApiPort
    $env:SERVER_ADDRESS = $ApiBindAddress
    $env:DBGRAM_STORAGE_PATH = Join-Path $runtime 'media'
    $env:STRIPE_WEBHOOK_SECRET = $config.StripeWebhookSecret
    $origin = $AppOrigin
    if (-not $origin) {
        $origin = 'http://localhost:' + $(if ($Hosted) { $ApiPort } else { $WebPort })
    }
    $env:DBGRAM_PUBLIC_URL = $origin
    $env:CORS_ORIGINS = "$origin,http://localhost:$WebPort,http://127.0.0.1:$WebPort,http://localhost:8081"
    if ($Hosted) {
        if (-not (Test-Path -LiteralPath (Join-Path $webExport 'index.html'))) {
            throw 'Build the frontend before using -Hosted: make -C frontend build.'
        }
        $env:SPRING_WEB_RESOURCES_STATIC_LOCATIONS = 'file:' + $webExport.Replace('\', '/') + '/'
    } else {
        Remove-Item Env:SPRING_WEB_RESOURCES_STATIC_LOCATIONS -ErrorAction SilentlyContinue
    }
}

function Start-Adminer {
    $phpDirectory = Join-Path $runtime 'php'
    $php = Join-Path $phpDirectory 'php.exe'
    $adminerRoot = Join-Path $runtime 'adminer'
    if (-not (Test-Path -LiteralPath $php) -or -not (Test-Path -LiteralPath (Join-Path $adminerRoot 'index.php'))) {
        throw 'Portable PHP and Adminer are missing. Follow the downloads in docs/LOCAL.md.'
    }
    $sessions = Join-Path $runtime 'adminer-sessions'
    New-Item -ItemType Directory -Force $sessions | Out-Null
    $env:PATH = $phpDirectory + ';' + $env:PATH
    $arguments = @(
        '-c', ('"' + (Join-Path $project 'deploy/adminer.ini') + '"'),
        '-d', ('"extension_dir=' + (Join-Path $phpDirectory 'ext') + '"'),
        '-d', ('"session.save_path=' + $sessions + '"'),
        '-S', "127.0.0.1:$AdminerPort",
        '-t', ('"' + $adminerRoot + '"')
    )
    Start-OwnedProcess 'adminer' $php $arguments $AdminerPort
    Wait-Http "http://127.0.0.1:$AdminerPort/" 'adminer'
    Write-Output "Adminer: http://localhost:$AdminerPort/?pgsql=127.0.0.1%3A$DatabasePort&username=dbgram&db=dbgram&ns=public"
    Write-Output 'Adminer password: DatabasePassword in .runtime/local.json.'
}

switch ($Action) {
    { $_ -in 'build', 'test' } {
        Set-JavaEnvironment
        if (-not $MavenHome) {
            $MavenHome = if ($env:MAVEN_HOME) { $env:MAVEN_HOME } else { 'C:/tools/maven' }
        }
        $maven = Join-Path $MavenHome 'bin/mvn.cmd'
        if (-not (Test-Path -LiteralPath $maven)) {
            throw 'Set MAVEN_HOME to the installed Maven directory.'
        }
        if ($Action -eq 'build') {
            Stop-OwnedProcess 'api'
        }
        $goal = if ($Action -eq 'build') { 'package' } else { 'test' }
        Push-Location (Join-Path $project 'backend')
        try {
            & $maven "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository" -B $goal
            if ($LASTEXITCODE -ne 0) {
                throw "Maven $goal failed."
            }
        } finally {
            Pop-Location
        }
    }
    'database' {
        Start-Database
    }
    'adminer' {
        Start-Database
        Start-Adminer
    }
    'start' {
        Set-JavaEnvironment
        Set-ApiEnvironment
        if (-not (Test-Path -LiteralPath $jar)) {
            throw 'Build the backend first: make -C backend build.'
        }
        Start-Database
        $java = Join-Path $env:JAVA_HOME 'bin/java.exe'
        Start-OwnedProcess 'api' $java @('-jar', ('"' + $jar + '"')) $ApiPort
        Wait-Http "http://127.0.0.1:$ApiPort/actuator/health" 'api'
        Write-Output "API ready: http://localhost:$ApiPort/actuator/health"
        Start-Adminer
    }
    'frontend' {
        if (-not (Test-Path -LiteralPath (Join-Path $webExport 'index.html'))) {
            throw 'Build the frontend first: make -C frontend build.'
        }
        if (-not (Test-Port $ApiPort)) {
            throw 'Start the backend first: make -C backend up.'
        }
        $node = (Get-Command node.exe -ErrorAction Stop).Source
        $env:WEB_PORT = $WebPort
        $env:WEB_HOST = $WebBindAddress
        $env:BACKEND_URL = "http://localhost:$ApiPort"
        Start-OwnedProcess 'web' $node @(('"' + (Join-Path $PSScriptRoot 'serve-web.mjs') + '"')) $WebPort
        Wait-Http "http://127.0.0.1:$WebPort/" 'web'
        Show-WebAddresses
    }
    'firewall' {
        Enable-LanFirewall
    }
    'frontend-stop' {
        Stop-OwnedProcess 'web'
    }
    'adminer-stop' {
        Stop-OwnedProcess 'adminer'
    }
    'stop' {
        Stop-OwnedProcess 'api'
        Stop-OwnedProcess 'adminer'
    }
    'database-stop' {
        & (Join-Path $pgBin 'pg_ctl.exe') status -D $pgData *> $null
        if ($LASTEXITCODE -eq 0) {
            & (Join-Path $pgBin 'pg_ctl.exe') stop -D $pgData -m fast -w
            if ($LASTEXITCODE -ne 0) {
                throw 'PostgreSQL shutdown failed.'
            }
        }
    }
    'status' {
        foreach ($name in @('api', 'web', 'adminer')) {
            $running = Get-OwnedProcess $name
            if ($running) {
                Write-Output "$name running: PID $($running.Id), port $($owned[$name].port)"
            } else {
                Write-Output "$name stopped"
            }
        }
        & (Join-Path $pgBin 'pg_ctl.exe') status -D $pgData
    }
    'env' {
        Set-JavaEnvironment
        Write-Output "DATABASE_URL=jdbc:postgresql://localhost:$DatabasePort/dbgram"
        Write-Output 'DATABASE_USER=dbgram'
        Write-Output 'SPRING_PROFILES_ACTIVE=portable'
        Write-Output "PORT=$ApiPort; WEB_PORT=$WebPort; ADMINER_PORT=$AdminerPort"
        Write-Output 'COOKIE_SECURE=false; EXPO_PUBLIC_API_URL=/api'
        Write-Output "WEB_HOST=$WebBindAddress"
        Write-Output 'DATABASE_PASSWORD and JWT_SECRET are loaded from .runtime/local.json.'
    }
}
