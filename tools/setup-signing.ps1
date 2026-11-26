# Run once on Windows under your normal user account. Requires Windows PowerShell 5.1+.
$ErrorActionPreference = 'Stop'
$repo = 'CHANDRU410/Expense_Tracker'
$folder = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'SpendSenseSigning'
$record = Join-Path $folder 'release-key.xml'
if (!(Test-Path $record)) {
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $password = [Convert]::ToBase64String($bytes)
    $secure = ConvertTo-SecureString $password -AsPlainText -Force
    $cert = New-SelfSignedCertificate -Subject 'CN=SpendSense Release' -Type CodeSigningCert -CertStoreLocation 'Cert:\CurrentUser\My' -KeyAlgorithm RSA -KeyLength 3072 -HashAlgorithm SHA256 -KeyExportPolicy Exportable -NotAfter (Get-Date).AddYears(30)
    $pfx = Join-Path $folder 'spendsense-release.pfx'
    Export-PfxCertificate -Cert $cert -FilePath $pfx -Password $secure -ChainOption EndEntityCertOnly | Out-Null
    [PSCredential]::new('SpendSense', $secure) | Export-Clixml -Path $record
}
$credential = Import-Clixml $record
$bundle = @{password=$credential.GetNetworkCredential().Password; pfx=[Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $folder 'spendsense-release.pfx')))} | ConvertTo-Json -Compress
if (Get-Command gh -ErrorAction SilentlyContinue) {
    gh auth status 2>$null
    if ($LASTEXITCODE -eq 0) {
        $bundle | gh secret set SPENDSENSE_SIGNING_BUNDLE --repo $repo
        if ($LASTEXITCODE -ne 0) { throw 'GitHub could not save the signing secret.' }
        gh workflow run android.yml --ref main --repo $repo
        if ($LASTEXITCODE -ne 0) { throw 'Secret saved. Open Actions and run Build SpendSense APK on main.' }
        Write-Host 'Signing configured. GitHub is building the first permanent APK.'
        return
    }
}
Set-Clipboard -Value $bundle
Start-Process "https://github.com/$repo/settings/secrets/actions/new"
Write-Host 'Name the repository secret SPENDSENSE_SIGNING_BUNDLE. Paste the clipboard into Secret and click Add secret.'
Read-Host 'After saving the secret, press Enter to clear the clipboard and open the build page'
Set-Clipboard -Value ' '
Start-Process "https://github.com/$repo/actions/workflows/android.yml"
Write-Host 'Choose Run workflow on main. Keep your signing files and certificate backed up securely.'
