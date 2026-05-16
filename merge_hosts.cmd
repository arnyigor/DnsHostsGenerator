@echo off
chcp 1251 >nul
echo =========================================================================
echo      Скрипт для удобного сравнения и фильтрации всех hosts в Excel
echo =========================================================================
echo.
echo Объединение hosts файлов в CSV...
echo.

REM Проверяем наличие PowerShell
where powershell >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Ошибка: PowerShell не установлен или не найден.
    pause
    exit /b 1
)

REM Запускаем PowerShell скрипт
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0merge_hosts.ps1"
if %ERRORLEVEL% EQU 0 (
    echo Скрипт выполнен успешно!
    echo Создан файл: hosts_comparison.csv
) else (
    echo Произошла ошибка при выполнении скрипта.
)

pause