param(
    [string] $StudentId = "1BIB23219",
    [string] $OutputDir = "certs/local",
    [string] $ServiceDnsName = "localhost"
)

$ErrorActionPreference = "Stop"

function Read-PlainPassword {
    param([string] $Prompt)

    $secure = Read-Host $Prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

function Invoke-Keytool {
    param([string[]] $Arguments)

    & keytool @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "keytool failed: $($Arguments -join ' ')"
    }
}

$password = $env:TLS_KEYSTORE_PASSWORD
if ([string]::IsNullOrWhiteSpace($password)) {
    $password = Read-PlainPassword "TLS keystore password"
}

if ([string]::IsNullOrWhiteSpace($password) -or $password.Length -lt 6) {
    throw "Password must contain at least 6 characters because keytool requires it."
}

$outputPath = (New-Item -ItemType Directory -Force -Path $OutputDir).FullName

$rootStore = Join-Path $outputPath "rbpo-root-ca-$StudentId.p12"
$intermediateStore = Join-Path $outputPath "rbpo-intermediate-ca-$StudentId.p12"
$serviceStore = Join-Path $outputPath "rbpo-service-$StudentId.p12"
$rootCert = Join-Path $outputPath "rbpo-root-ca-$StudentId.crt"
$intermediateCert = Join-Path $outputPath "rbpo-intermediate-ca-$StudentId.crt"
$chainCert = Join-Path $outputPath "rbpo-service-chain-$StudentId.crt"
$intermediateCsr = Join-Path $outputPath "rbpo-intermediate-ca-$StudentId.csr"
$serviceCsr = Join-Path $outputPath "rbpo-service-$StudentId.csr"

$rootAlias = "rbpo-root-ca-$($StudentId.ToLowerInvariant())"
$intermediateAlias = "rbpo-intermediate-ca-$($StudentId.ToLowerInvariant())"
$serviceAlias = "rbpo-service-$($StudentId.ToLowerInvariant())"

Invoke-Keytool @(
    "-genkeypair", "-alias", $rootAlias, "-keyalg", "RSA", "-keysize", "4096",
    "-validity", "3650", "-storetype", "PKCS12", "-keystore", $rootStore,
    "-storepass", $password, "-keypass", $password,
    "-dname", "CN=RBPO Root CA $StudentId, OU=Student-$StudentId, O=RBPO Labs, L=Moscow, C=RU",
    "-ext", "bc=ca:true,pathlen:1", "-ext", "ku=keyCertSign,cRLSign"
)

Invoke-Keytool @(
    "-exportcert", "-rfc", "-alias", $rootAlias, "-keystore", $rootStore,
    "-storepass", $password, "-file", $rootCert
)

Invoke-Keytool @(
    "-genkeypair", "-alias", $intermediateAlias, "-keyalg", "RSA", "-keysize", "4096",
    "-validity", "1825", "-storetype", "PKCS12", "-keystore", $intermediateStore,
    "-storepass", $password, "-keypass", $password,
    "-dname", "CN=RBPO Intermediate CA $StudentId, OU=Student-$StudentId, O=RBPO Labs, L=Moscow, C=RU",
    "-ext", "bc=ca:true,pathlen:0", "-ext", "ku=keyCertSign,cRLSign"
)

Invoke-Keytool @(
    "-certreq", "-alias", $intermediateAlias, "-keystore", $intermediateStore,
    "-storepass", $password, "-file", $intermediateCsr
)

Invoke-Keytool @(
    "-gencert", "-alias", $rootAlias, "-keystore", $rootStore, "-storepass", $password,
    "-infile", $intermediateCsr, "-outfile", $intermediateCert, "-rfc", "-validity", "1825",
    "-ext", "bc=ca:true,pathlen:0", "-ext", "ku=keyCertSign,cRLSign"
)

Invoke-Keytool @(
    "-importcert", "-noprompt", "-alias", $rootAlias, "-keystore", $intermediateStore,
    "-storepass", $password, "-file", $rootCert
)

Invoke-Keytool @(
    "-importcert", "-noprompt", "-alias", $intermediateAlias, "-keystore", $intermediateStore,
    "-storepass", $password, "-file", $intermediateCert
)

Invoke-Keytool @(
    "-genkeypair", "-alias", $serviceAlias, "-keyalg", "RSA", "-keysize", "2048",
    "-validity", "825", "-storetype", "PKCS12", "-keystore", $serviceStore,
    "-storepass", $password, "-keypass", $password,
    "-dname", "CN=$ServiceDnsName, OU=Student-$StudentId, O=RBPO Labs, L=Moscow, C=RU",
    "-ext", "san=dns:$ServiceDnsName,ip:127.0.0.1", "-ext", "ku=digitalSignature,keyEncipherment",
    "-ext", "eku=serverAuth"
)

Invoke-Keytool @(
    "-certreq", "-alias", $serviceAlias, "-keystore", $serviceStore,
    "-storepass", $password, "-file", $serviceCsr
)

Invoke-Keytool @(
    "-gencert", "-alias", $intermediateAlias, "-keystore", $intermediateStore, "-storepass", $password,
    "-infile", $serviceCsr, "-outfile", $chainCert, "-rfc", "-validity", "825",
    "-ext", "san=dns:$ServiceDnsName,ip:127.0.0.1", "-ext", "ku=digitalSignature,keyEncipherment",
    "-ext", "eku=serverAuth"
)

Invoke-Keytool @(
    "-importcert", "-noprompt", "-alias", $rootAlias, "-keystore", $serviceStore,
    "-storepass", $password, "-file", $rootCert
)

Invoke-Keytool @(
    "-importcert", "-noprompt", "-alias", $intermediateAlias, "-keystore", $serviceStore,
    "-storepass", $password, "-file", $intermediateCert
)

Invoke-Keytool @(
    "-importcert", "-noprompt", "-alias", $serviceAlias, "-keystore", $serviceStore,
    "-storepass", $password, "-file", $chainCert
)

Remove-Item -LiteralPath $intermediateCsr, $serviceCsr -Force

Write-Host "Generated TLS chain for student $StudentId in $outputPath"
Write-Host "Root certificate to trust locally: $rootCert"
Write-Host "Spring Boot keystore: $serviceStore"
Write-Host "Spring alias: $serviceAlias"
