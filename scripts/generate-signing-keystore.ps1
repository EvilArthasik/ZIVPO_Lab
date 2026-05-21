param(
    [string]$Alias = "ticket-signing",
    [string]$StorePath = "certs/local/ticket-signing.p12",
    [string]$CertificatePath = "certs/local/ticket-signing.cer",
    [string]$StorePassword = "changeit",
    [string]$KeyPassword = $StorePassword,
    [string]$DistinguishedName = "CN=Ticket Signing, OU=ZIVPO, O=Demo, L=Moscow, C=RU"
)

$storeDirectory = Split-Path -Parent $StorePath
if ($storeDirectory) {
    New-Item -ItemType Directory -Force $storeDirectory | Out-Null
}

keytool -genkeypair `
    -alias $Alias `
    -keyalg RSA `
    -keysize 2048 `
    -sigalg SHA256withRSA `
    -validity 3650 `
    -storetype PKCS12 `
    -keystore $StorePath `
    -storepass $StorePassword `
    -keypass $KeyPassword `
    -dname $DistinguishedName

keytool -exportcert `
    -rfc `
    -alias $Alias `
    -keystore $StorePath `
    -storetype PKCS12 `
    -storepass $StorePassword `
    -file $CertificatePath

Write-Host "Created signing keystore: $StorePath"
Write-Host "Created public certificate: $CertificatePath"
Write-Host "GitHub secret SIGNATURE_KEYSTORE_BASE64:"
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path $StorePath)))
