# Скрипт для объединения hosts файлов
Write-Host "Объединение hosts файлов..." -ForegroundColor Cyan

# Определяем файлы
$files = @(
    "host_out_dns.astracat.ru.txt",
    "host_out_dns.comss.one.txt", 
    "host_out_dns.geohide.ru.txt",
    "host_out_dns.mafioznik.xyz.txt",
    "host_out_dns.malw.link.txt",
    "host_out_free.shecan.ir.txt",
    "host_out_pro.shecan.ir.txt",
    "host_out_xbox-dns.ru.txt"
)

# Проверка
foreach ($file in $files) {
    if (-not (Test-Path $file)) {
        Write-Host "ОШИБКА: Файл $file не найден!" -ForegroundColor Red
        pause
        exit 1
    }
}

# Читаем все файлы
$contents = @()
foreach ($file in $files) {
    $contents += ,@(Get-Content $file)
}

# Определяем количество строк (минимум из всех файлов)
$rows = ($contents | ForEach-Object { $_.Count } | Measure-Object -Minimum).Minimum

# Создаем CSV
$output = "combined_hosts.csv"

# Создаем заголовок с разделителем-точкой с запятой
$header = ($files | ForEach-Object { 
    $_.Replace("host_out_", "").Replace(".txt", "") 
}) -join ";"
$header | Out-File $output -Encoding UTF8

# Записываем данные с разделителем-точкой с запятой
for ($i = 0; $i -lt $rows; $i++) {
    $row = @()
    for ($j = 0; $j -lt $files.Count; $j++) {
        $val = $contents[$j][$i]
        # Экранируем кавычки и точки с запятой
        if ($val -match '[;"]') {
            $val = '"' + $val.Replace('"', '""') + '"'
        }
        $row += $val
    }
    ($row -join ";") | Out-File $output -Encoding UTF8 -Append
}

Write-Host "Готово! Создан файл: $output" -ForegroundColor Green
Write-Host "Строк: $rows, Столбцов: $($files.Count)" -ForegroundColor Green
Write-Host "Разделитель: точка с запятой (;)" -ForegroundColor Green
pause