# PKY AI Assistant - Android Build Automation Script
# This script restores the Gradle wrapper and builds the APK.

$ErrorActionPreference = "Stop"

Write-Host "--- PKY AI Assistant Android Build ---" -ForegroundColor Cyan

# 1. Force use of discovered JDK 17
$env:JAVA_HOME = "C:\Users\Ashok\.antigravity\extensions\redhat.java-1.12.0-win32-x64\jre\17.0.4.1-win32-x86_64"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

if (!(Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "Java not found! Please install JDK 17+ and add to PATH."
    exit 1
}
Write-Host "[OK] Using Discovered JDK 17: $env:JAVA_HOME" -ForegroundColor Green

# 2. Restore Gradle Wrapper if missing
if (!(Test-Path "gradlew.bat")) {
    Write-Host "[!] Gradle wrapper missing. Attempting to restore..." -ForegroundColor Yellow
    
    # Create gradle/wrapper structure
    New-Item -ItemType Directory -Path "gradle/wrapper" -Force | Out-Null
    
    # Download gradle-wrapper.jar
    $jarUrl = "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar"
    $propsUrl = "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.properties"
    $batUrl = "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradlew.bat"
    $shUrl = "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradlew"

    Write-Host "[-] Downloading wrapper components..."
    Invoke-WebRequest -Uri $jarUrl -OutFile "gradle/wrapper/gradle-wrapper.jar"
    Invoke-WebRequest -Uri $propsUrl -OutFile "gradle/wrapper/gradle-wrapper.properties"
    Invoke-WebRequest -Uri $batUrl -OutFile "gradlew.bat"
    Invoke-WebRequest -Uri $shUrl -OutFile "gradlew"
    
    # Fix properties for Android project (default to 8.0)
    "distributionBase=GRADLE_USER_HOME`ndistributionPath=wrapper/dists`ndistributionUrl=https\://services.gradle.org/distributions/gradle-8.0-bin.zip`nzipStoreBase=GRADLE_USER_HOME`nzipStorePath=wrapper/dists" | Set-Content "gradle/wrapper/gradle-wrapper.properties"

    Write-Host "[OK] Wrapper restored." -ForegroundColor Green
}

# 3. Build APK
Write-Host "[-] Compiling PKY AI Assistant (Debug APK)..." -ForegroundColor Cyan
./gradlew.bat assembleDebug

if ($LASTEXITCODE -eq 0) {
    $apkPath = Get-ChildItem -Path "app/build/outputs/apk/debug/*.apk" | Select-Object -First 1 -ExpandProperty FullName
    Write-Host "`n[SUCCESS] APK created at: $apkPath" -ForegroundColor Green
} else {
    Write-Host "`n[FAILED] Build failed with exit code $LASTEXITCODE" -ForegroundColor Red
}
