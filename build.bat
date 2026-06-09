@echo off
set JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1.1.1\jbr
set MVN=C:\Users\Santi\.m2\wrapper\dists\apache-maven-3.8.5-bin\5i5jha092a3i37g0paqnfr15e0\apache-maven-3.8.5\bin\mvn.cmd
"%MVN%" package -q
if %errorlevel% == 0 (
    echo listo. corre: notas.bat
) else (
    echo error al compilar.
)
