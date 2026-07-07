<#
 .SYNOPSIS
 Initialize the PostgreSQL database for the hbzy project.

 .DESCRIPTION
 File: init-postgresql.ps1
 Purpose: Create the hbzy database, execute the PostgreSQL schema script,
          and import the formal business data extracted from hbzy.sql.
 Responsibilities:
 1. Check whether psql.exe, the schema file, and the source SQL file exist.
 2. Create the hbzy database when it does not exist.
 3. Import data for dim_process_metric, ads_batch_metric_stat,
    and fact_process_metric_value.
 Developer: czd
 #>

param(
    [string]$DbHost = "localhost",
    [int]$Port = 5432,
    [string]$Database = "hbzy",
    [string]$Username = "postgres",
    [string]$Password = "123456",
    [string]$SourceSql = "",
    [string]$SchemaSql = ""
)

$ErrorActionPreference = "Stop"

function Resolve-PsqlPath {
    $candidates = @(
        "C:\Program Files\PostgreSQL\17\bin\psql.exe",
        "C:\Program Files\PostgreSQL\16\bin\psql.exe",
        "C:\Program Files\PostgreSQL\15\bin\psql.exe"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    $command = Get-Command psql.exe -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }

    throw "psql.exe was not found. Please install the PostgreSQL client tools first."
}

function Invoke-PsqlCommand {
    param(
        [string]$PsqlPath,
        [string]$TargetDatabase,
        [string]$Sql
    )

    & $PsqlPath -h $DbHost -p $Port -U $Username -d $TargetDatabase -v ON_ERROR_STOP=1 -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to execute PostgreSQL command: $Sql"
    }
}

if ([string]::IsNullOrWhiteSpace($SourceSql)) {
    $SourceSql = Join-Path $PSScriptRoot "..\hbzy.sql"
}
if ([string]::IsNullOrWhiteSpace($SchemaSql)) {
    $SchemaSql = Join-Path $PSScriptRoot "..\backend\src\main\resources\sql\postgresql\hbzy_schema.sql"
}

$SourceSql = [System.IO.Path]::GetFullPath($SourceSql)
$SchemaSql = [System.IO.Path]::GetFullPath($SchemaSql)

if (-not (Test-Path -LiteralPath $SourceSql)) {
    throw "Source SQL file was not found: $SourceSql"
}
if (-not (Test-Path -LiteralPath $SchemaSql)) {
    throw "PostgreSQL schema file was not found: $SchemaSql"
}

$psqlPath = Resolve-PsqlPath
$env:PGPASSWORD = $Password

$dbExists = & $psqlPath -h $DbHost -p $Port -U $Username -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$Database';"
if ($LASTEXITCODE -ne 0) {
    throw "Unable to connect to PostgreSQL. Please verify host, port, username, and password."
}

$dbExistsText = ""
if ($null -ne $dbExists) {
    $dbExistsText = $dbExists.ToString().Trim()
}

if ($dbExistsText -ne "1") {
    Invoke-PsqlCommand -PsqlPath $psqlPath -TargetDatabase "postgres" -Sql "CREATE DATABASE $Database ENCODING 'UTF8';"
}

& $psqlPath -h $DbHost -p $Port -U $Username -d $Database -v ON_ERROR_STOP=1 -f $SchemaSql
if ($LASTEXITCODE -ne 0) {
    throw "Failed to execute the schema script: $SchemaSql"
}

$targetTables = @(
    "dim_process_metric",
    "ads_batch_metric_stat",
    "fact_process_metric_value"
)
$targetTableLookup = @{}
foreach ($tableName in $targetTables) {
    $targetTableLookup[$tableName] = $true
}
$backtickChar = [string][char]96

$insertLines = Get-Content -LiteralPath $SourceSql -Encoding UTF8 | ForEach-Object {
    $line = $_
    if ($line -match '^INSERT INTO `(.+?)` VALUES') {
        $tableName = $Matches[1]
        if ($targetTableLookup.ContainsKey($tableName)) {
            $line.Replace($backtickChar, "")
        }
    }
}

if ($insertLines.Count -eq 0) {
    throw "No import data for the target business tables was found in $SourceSql"
}

$tempSql = Join-Path $env:TEMP "hbzy-postgresql-import.sql"
$importContent = @(
    "SET client_encoding = 'UTF8';",
    "BEGIN;"
)
$importContent += $insertLines
$importContent += "COMMIT;"

Set-Content -LiteralPath $tempSql -Value $importContent -Encoding UTF8

try {
    & $psqlPath -h $DbHost -p $Port -U $Username -d $Database -v ON_ERROR_STOP=1 -f $tempSql
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to import business data: $tempSql"
    }

    $summarySql = "SELECT 'dim_process_metric' AS table_name, COUNT(*) AS row_count FROM dim_process_metric UNION ALL SELECT 'ads_batch_metric_stat', COUNT(*) FROM ads_batch_metric_stat UNION ALL SELECT 'fact_process_metric_value', COUNT(*) FROM fact_process_metric_value;"
    Invoke-PsqlCommand -PsqlPath $psqlPath -TargetDatabase $Database -Sql $summarySql
}
finally {
    if (Test-Path -LiteralPath $tempSql) {
        Remove-Item -LiteralPath $tempSql -Force
    }
}

Write-Host "PostgreSQL initialization finished for database: $Database"
