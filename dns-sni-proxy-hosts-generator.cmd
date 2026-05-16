@echo off
setlocal enabledelayedexpansion
chcp 1251 >nul

echo =========================================================================
echo     Скрипт на PowerShell, который формирует hosts-файл для SNI Proxy.
echo          Включает дедупликацию, проверку доступности и проверку
echo          перенаправлений с использованием публичных DNS-серверов
echo =========================================================================
echo.
 
rem Проверяем минимальное количество параметров
if "%1"=="" goto :usage
if "%2"=="" goto :usage
if "%3"=="" goto :usage

set PRIMARY_DNS=%1
set CHECK_DNS=%2
set DOMAINS_FILE=%~3
set OUTPUT_FILE=%4
set DEDUP=0

rem Если OUTPUT_FILE не указан, используем по умолчанию
if "%OUTPUT_FILE%"=="" set OUTPUT_FILE=hosts.txt

rem Проверяем пятый параметр на ключ дедупликации (если OUTPUT_FILE указан)
if not "%4"=="" (
    if /i "%5"=="dedup" set DEDUP=1
) else (
    rem Если OUTPUT_FILE не указан, то 4й параметр может быть dedup
    if /i "%4"=="dedup" (
        set DEDUP=1
        set OUTPUT_FILE=hosts.txt
    )
)

rem Проверяем ситуацию, когда OUTPUT_FILE указан, а dedup - нет
if not "%4"=="" if not "%5"=="" (
    if /i "%5"=="dedup" set DEDUP=1
)

echo Основной DNS сервер: %PRIMARY_DNS%
echo Проверочный DNS сервер: %CHECK_DNS%
echo Исходный файл доменов: %DOMAINS_FILE%
echo Выходной файл: %OUTPUT_FILE%
if !DEDUP!==1 echo Дедупликация: ВКЛЮЧЕНА
echo.

if not exist "%DOMAINS_FILE%" (
    echo ОШИБКА: Файл с доменами не найден!
    pause
    goto :eof
)

if not exist "dns-sni-proxy-hosts-generator.ps1" (
    echo ОШИБКА: Файл dns-sni-proxy-hosts-generator.ps1 не найден!
    echo Убедитесь, что он находится в той же папке.
    pause
    goto :eof
)

echo Создание hosts файла...
echo.

powershell -ExecutionPolicy Bypass -File "dns-sni-proxy-hosts-generator.ps1" -primaryDns "%PRIMARY_DNS%" -checkDns "%CHECK_DNS%" -domainsFile "%DOMAINS_FILE%" -outputFile "%OUTPUT_FILE%" -dedup %DEDUP%

if errorlevel 1 (
    echo.
    echo Произошла ошибка при выполнении PowerShell!
)

echo.
pause
goto :eof

:usage
echo Использование:
echo   %~n0 ^<основной_DNS^> ^<проверочный_DNS^> ^<файл_с_доменами^> [выходной_файл] [dedup]
echo.
echo Примеры:
echo   %~n0 dns.comss.one 8.8.8.8 domains.txt
echo   %~n0 dns.comss.one 8.8.8.8 domains.txt dedup
echo   %~n0 176.99.11.77 8.8.8.8 domains.txt myhosts.txt
echo   %~n0 176.99.11.77 8.8.8.8 domains.txt myhosts.txt dedup
echo.
echo Файл с доменами должен содержать список доменов,
echo по одному на строку. Поддерживаются комментарии
echo (строки, начинающиеся с #) и пустые строки.
echo.
echo Логика работы:
echo - Получаем все A-записи (IPv4) от основного DNS
echo - Получаем все A-записи от проверочного DNS
echo - Если есть хотя бы один общий IP, помечаем как #forwarded
echo - Иначе берём первый IP от основного DNS для hosts
echo - Сравнение записей определяет, является ли DNS ответ перенаправленным
echo - на публичный DNS (#forwarded) или содержит необходимый SNI Proxy
echo - В ряде случаев определение #forwarded не срабатывает из-за CDN, 
echo - Round-robin больших пулов IP, заглушек в сторону private networks.
echo - Для тонкой сверки обращать внимание на диапазоны IP и пользоваться Whois
echo.
echo Если указан ключ dedup:
echo - Пропускает дублирующиеся домены
echo - Первое вхождение домена обрабатывается нормально
echo - Последующие вхождения отмечаются как #duplicate
echo.
pause