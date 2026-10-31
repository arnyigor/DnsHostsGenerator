@echo off
:: Заменяет адрес подмены OpenAI/Anthropic в hosts на проверенный быстрый и сбрасывает кэш DNS.
:: Запускать двойным кликом: скрипт сам запросит права администратора.
setlocal
net session >nul 2>&1
if %errorlevel% neq 0 (
  echo Требуются права администратора, перезапускаю...
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)

set NEW=83.217.211.149
set HOSTS=%SystemRoot%\System32\drivers\etc\hosts
set BACKUP=%SystemRoot%\System32\drivers\etc\hosts.backup-%RANDOM%

copy /Y "%HOSTS%" "%BACKUP%" >nul
echo Резервная копия: %BACKUP%

powershell -NoProfile -Command ^
  "$domains='chatgpt.com','ab.chatgpt.com','operator.chatgpt.com','sora.chatgpt.com','webrtc.chatgpt.com','sora.com','cdn.oaistatic.com','files.oaiusercontent.com','android.chat.openai.com','api.openai.com','auth.openai.com','auth0.openai.com','chat.openai.com','ios.chat.openai.com','openai.com','platform.openai.com','tcr9i.chat.openai.com','videos.openai.com','a-api.anthropic.com','a-cdn.anthropic.com','api.anthropic.com','auth.anthropic.com','console.anthropic.com','s-cdn.anthropic.com','claude.ai';" ^
  "$p='%HOSTS%'; $lines=Get-Content $p; $out=foreach($l in $lines){ if($l -match '^\s*\d+\.\d+\.\d+\.\d+\s+(\S+)'){ $h=$Matches[1]; if($domains -contains $h){ '%NEW%' + "`t" + $h } else { $l } } else { $l } }; Set-Content -Path $p -Value $out -Encoding ASCII"

ipconfig /flushdns >nul
echo.
echo Готово: домены OpenAI/Anthropic указывают на %NEW%, кэш DNS сброшен.
echo Замер (должно быть меньше секунды):
powershell -NoProfile -Command "(Measure-Command { try { (Invoke-WebRequest -Uri 'https://chatgpt.com/' -UseBasicParsing -TimeoutSec 25) | Out-Null } catch {} }).TotalSeconds"
echo.
pause
