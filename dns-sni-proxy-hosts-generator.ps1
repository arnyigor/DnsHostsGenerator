param(
    [string]$primaryDns,
    [string]$checkDns,
    [string]$domainsFile,
    [string]$outputFile,
    [int]$dedup = 0
)

# Сохраняем стандартные цвета для последующего восстановления
$originalForeground = $Host.UI.RawUI.ForegroundColor
$originalBackground = $Host.UI.RawUI.BackgroundColor

$lines = Get-Content $domainsFile -Encoding UTF8
$outputLines = @()
$lineCount = 0
$resolvedCount = 0
$failedCount = 0
$forwardedCount = 0
$duplicateCount = 0
$processedDomains = @{}

foreach ($line in $lines) {
    $lineCount++
    Write-Host ("Обработка строки " + $lineCount + ": ") -NoNewline
    Write-Host $line -ForegroundColor Cyan
    
    if ([string]::IsNullOrWhiteSpace($line)) {
        $outputLines += ''
        Write-Host '[Пустая строка]' -ForegroundColor DarkGray
        Write-Host ''
        continue
    }
    
    if ($line.TrimStart().StartsWith('#')) {
        $outputLines += $line
        Write-Host '[Комментарий]' -ForegroundColor DarkGray
        Write-Host ''
        continue
    }
    
    $domain = $line.Trim()
    
    if ($dedup -eq 1 -and $processedDomains.ContainsKey($domain)) {
        $duplicateCount++
        $outputLines += ('#duplicate ' + $domain)
        Write-Host '[DUPLICATE] ' -ForegroundColor Yellow -NoNewline
        Write-Host ('Домен уже обработан ранее: ' + $domain)
        Write-Host ''
        continue
    }
    
    # Основной DNS запрос
    try {
        $primaryResult = Resolve-DnsName -Name $domain -Server $primaryDns -DnsOnly -ErrorAction Stop
        $primaryRecords = @($primaryResult | Where-Object { $_.Type -eq 'A' })
        
        if ($primaryRecords.Count -eq 0) {
            $outputLines += ('#unresolvedDomain ' + $domain)
            $failedCount++
            Write-Host '[ОШИБКА] ' -ForegroundColor Red -NoNewline
            Write-Host ('Не удалось разрешить домен основным DNS: ' + $domain)
            Write-Host ''
            continue
        }
        
        # Получаем первый IP
        $firstIp = [string]$primaryRecords[0].IPAddress
        
        # Получаем все IP от основного DNS
        $primaryIps = @()
        foreach ($record in $primaryRecords) {
            $primaryIps += [string]$record.IPAddress
        }
        
        $processedDomains[$domain] = $true
        
        # Проверочный DNS запрос
        try {
            $checkResult = Resolve-DnsName -Name $domain -Server $checkDns -DnsOnly -ErrorAction Stop
            $checkRecords = @($checkResult | Where-Object { $_.Type -eq 'A' })
            
            if ($checkRecords.Count -gt 0) {
                # Получаем все IP от проверочного DNS
                $checkIps = @()
                foreach ($record in $checkRecords) {
                    $checkIps += [string]$record.IPAddress
                }
                
                # Проверяем на общие IP
                $hasCommon = $false
                foreach ($pIp in $primaryIps) {
                    foreach ($cIp in $checkIps) {
                        if ($pIp -eq $cIp) {
                            $hasCommon = $true
                            break
                        }
                    }
                    if ($hasCommon) { break }
                }
                
                if ($hasCommon) {
                    $outputLines += ('#forwarded ' + $domain)
                    $forwardedCount++
                    $primaryIpsStr = $primaryIps -join ', '
                    $checkIpsStr = $checkIps -join ', '
                    Write-Host '[FORWARDED] ' -ForegroundColor Yellow -NoNewline
                    Write-Host ($domain + ' -> есть общий IP')
                    Write-Host ('       Основной DNS: ') -NoNewline -ForegroundColor Gray
                    Write-Host $primaryIpsStr -ForegroundColor Green
                    Write-Host ('       Проверочный DNS: ') -NoNewline -ForegroundColor Gray
                    Write-Host $checkIpsStr -ForegroundColor Blue
                } else {
                    $outputLines += ($firstIp + ' ' + $domain)
                    $resolvedCount++
                    Write-Host '[УСПЕХ] ' -ForegroundColor Green -NoNewline
                    Write-Host ($domain + ' -> ') -NoNewline
                    Write-Host $firstIp -ForegroundColor Green
                    
                    if ($primaryIps.Count -gt 1) {
                        Write-Host ('       Все IP от основного DNS: ') -NoNewline -ForegroundColor Gray
                        Write-Host ($primaryIps -join ', ') -ForegroundColor Green
                    }
                    
                    $checkIpsStr = $checkIps -join ', '
                    Write-Host ('       Проверочный DNS: ') -NoNewline -ForegroundColor Gray
                    Write-Host $checkIpsStr -ForegroundColor Blue
                }
            } else {
                # Проверочный DNS не вернул A-записей
                $outputLines += ($firstIp + ' ' + $domain)
                $resolvedCount++
                Write-Host '[УСПЕХ] ' -ForegroundColor Green -NoNewline
                Write-Host ($domain + ' -> ') -NoNewline
                Write-Host $firstIp -ForegroundColor Green
                
                if ($primaryIps.Count -gt 1) {
                    Write-Host ('       Все IP от основного DNS: ') -NoNewline -ForegroundColor Gray
                    Write-Host ($primaryIps -join ', ') -ForegroundColor Green
                }
                
                Write-Host ('       Проверочный DNS: ') -NoNewline -ForegroundColor Gray
                Write-Host 'не ответил' -ForegroundColor DarkYellow
            }
        } catch {
            # Проверочный DNS не ответил
            $outputLines += ($firstIp + ' ' + $domain)
            $resolvedCount++
            Write-Host '[УСПЕХ] ' -ForegroundColor Green -NoNewline
            Write-Host ($domain + ' -> ') -NoNewline
            Write-Host $firstIp -ForegroundColor Green
            
            if ($primaryIps.Count -gt 1) {
                Write-Host ('       Все IP от основного DNS: ') -NoNewline -ForegroundColor Gray
                Write-Host ($primaryIps -join ', ') -ForegroundColor Green
            }
            
            Write-Host ('       Проверочный DNS: ') -NoNewline -ForegroundColor Gray
            Write-Host 'не ответил' -ForegroundColor DarkYellow
        }
    } catch {
        # Основной DNS не ответил
        $outputLines += ('#unresolvedDomain ' + $domain)
        $failedCount++
        Write-Host '[ОШИБКА] ' -ForegroundColor Red -NoNewline
        Write-Host ('Не удалось разрешить домен основным DNS: ' + $domain)
    }
    
    Write-Host ''
}

$outputLines | Out-File -FilePath $outputFile -Encoding UTF8

# Восстанавливаем оригинальные цвета
$Host.UI.RawUI.ForegroundColor = $originalForeground
$Host.UI.RawUI.BackgroundColor = $originalBackground

Write-Host '===============================================' -ForegroundColor Cyan
Write-Host 'РЕЗУЛЬТАТЫ:' -ForegroundColor White
Write-Host ('Всего строк обработано: ') -NoNewline -ForegroundColor Gray
Write-Host $lineCount -ForegroundColor White
Write-Host ('Доменов успешно разрешено: ') -NoNewline -ForegroundColor Gray
Write-Host $resolvedCount -ForegroundColor Green
Write-Host ('Доменов исключено (#forwarded): ') -NoNewline -ForegroundColor Gray
Write-Host $forwardedCount -ForegroundColor Yellow
Write-Host ('Доменов не разрешено (#unresolvedDomain): ') -NoNewline -ForegroundColor Gray
Write-Host $failedCount -ForegroundColor Red
if ($dedup -eq 1) {
    Write-Host ('Дублей пропущено (#duplicate): ') -NoNewline -ForegroundColor Gray
    Write-Host $duplicateCount -ForegroundColor Magenta
}
Write-Host ''
Write-Host ('Hosts файл создан: ') -NoNewline -ForegroundColor Gray
Write-Host $outputFile -ForegroundColor Cyan
Write-Host '===============================================' -ForegroundColor Cyan