# tablet-e-ink

**Relógio de cabeceira estilo e-ink + leitor tipo Kindle num tablet Android barato.**

Um tablet de entrada vira um relógio de cabeceira elegante — hora grande em serifa, papel de dia,
âmbar à noite — que também mostra o tempo, seus próximos compromissos e o livro que você está lendo,
abrindo o [KOReader](https://koreader.rocks/) no ponto em que parou. Tudo instalado pelo PC, via adb,
**sem root**.

| Dia (papel) | Ao acordar | Noite (âmbar) |
|---|---|---|
| ![Modo dia](docs/img/dia.png) | ![Ao acordar](docs/img/acordar.png) | ![Modo noite](docs/img/noite.png) |

![Modo dia na horizontal](docs/img/dia-horizontal.png)

> Capturas de tela feitas no aparelho. Os lembretes são de exemplo. No tablet de teste as cores
> aparecem mais suaves por causa do filtro de saturação opcional (ver [Root opcional](#root-opcional)).

## O que faz
- **Dia:** hora, data por extenso (“domingo, 1 de novembro”), tempo numa linha (ícone em traço fino,
  temperatura atual, mín/máx e um resumo como “chuva à tarde”), livro atual (“Sapiens · 48%”) que abre
  o KOReader, e os próximos 2–3 lembretes no rodapé.
- **Ao acordar:** na primeira hora depois da noite, a previsão do dia aparece em destaque.
- **Noite:** só a hora e a data, em âmbar e com brilho mínimo. Um toque mostra o botão do livro por 10 s.
- Transição suave entre dia e noite; layout vertical e horizontal; protetor de tela no carregador;
  indicador discreto de bateria; deslocamento periódico do conteúdo para proteger a tela.
- Sem internet, mostra o último tempo baixado com “atualizado às HH:MM”.
- App leve em Java puro (sem bibliotecas), pensado para Android Go com ~2 GB de RAM.

## Aparelho testado
**Multilaser M8 4G** (`ML_SO0V_M8_4G`, MediaTek MT6762, **Android 13 Go**, 2 GB de RAM) — detalhes em
[docs/01-diagnostico.md](docs/01-diagnostico.md).

Deve funcionar em outros tablets e celulares com Android 11 ou mais novo, **sem garantia**. A lista de
apps removidos pelo `etapa2-debloat.ps1` foi feita para o M8; em outro aparelho, revise-a ou use
`-SemDebloat`.

## Requisitos
- PC com **Windows** e PowerShell
- **adb** — [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)
- **Cabo USB** de dados
- No tablet: **Depuração USB** ligada
- Para compilar o app: **JDK 17** e o **Android SDK** (plataforma 34 + build-tools 34)

## Instalação (sem root)
1. **Depuração USB no tablet:** Configurações → Sobre o tablet → toque 7 vezes em “Número da versão”;
   depois Configurações → Sistema → Opções do desenvolvedor → **Depuração USB**.
2. **adb no PC:** extraia o platform-tools e coloque a pasta no `PATH`, ou indique o caminho:
   ```powershell
   $env:ADB = "C:\platform-tools\adb.exe"
   ```
   Conecte o tablet, aceite “Permitir depuração USB” e confira com `adb devices`.
   (Para não repetir isso, crie `scripts\local.ps1` com essa linha — o arquivo fica fora do git.)
3. **Compilar o app:**
   ```powershell
   # uma vez: diga ao Gradle onde está o Android SDK
   Set-Content app-relogio\local.properties "sdk.dir=C\:\\Android\\sdk"
   cd app-relogio; .\gradlew.bat assembleDebug; cd ..
   ```
   O APK sai em `app-relogio\app\build\outputs\apk\debug\app-debug.apk`.
4. **Instalar tudo:**
   ```powershell
   Set-ExecutionPolicy -Scope Process Bypass
   .\scripts\instalar-tudo.ps1            # no Multilaser M8
   .\scripts\instalar-tudo.ps1 -SemDebloat  # em outros aparelhos
   ```
   O script aplica os ajustes de sistema, remove apps desnecessários, baixa e instala o KOReader
   oficial, instala o relógio como tela inicial e protetor de tela, e dá a ele acesso de leitura à
   pasta do KOReader (para mostrar o livro atual).

Cada etapa também pode ser rodada sozinha e desfeita com `-Desfazer`:

| Script | O que faz |
|---|---|
| `scripts\etapa2-config.ps1` | desliga animações; tela ligada no carregador; modo monocromático |
| `scripts\etapa2-debloat.ps1` | remove apps para o usuário 0 (`pm uninstall --user 0`) |
| `scripts\etapa3-ativar.ps1` | instala o relógio; tela inicial; protetor de tela; sem tela de bloqueio de deslizar |
| `scripts\config-relogio.ps1` | baixa/envia a configuração e os lembretes |

## Configuração
Tudo fica num arquivo, `cabeceira.conf`, na pasta do app no tablet
(`/sdcard/Android/data/br.tabletink.cabeceira/files/`). O relógio relê sozinho em até 1 minuto.

```powershell
.\scripts\config-relogio.ps1 -Baixar   # copia para a pasta config\ do projeto (fora do git)
# edite config\cabeceira.conf e config\lembretes.txt
.\scripts\config-relogio.ps1 -Enviar
```
Modelo comentado: [config.example](config.example).

- **Cidade:** troque `latitude` e `longitude`. Para achar as da sua cidade, abra
  `https://geocoding-api.open-meteo.com/v1/search?name=SUA_CIDADE&language=pt&countryCode=BR`.
- **Google Agenda:** no navegador, Google Agenda → Configurações → sua agenda →
  **Endereço secreto no formato iCal** → cole em `ical = ...`. O tablet não precisa de conta Google.
  Esse link dá acesso de leitura à sua agenda: **não o compartilhe nem o publique**.
- **Sem Google Agenda:** deixe `ical =` vazio e escreva os lembretes em `lembretes.txt`, um por linha
  (modelo: [lembretes.example.txt](lembretes.example.txt)):
  ```
  2026-11-03 14:00 Reunião
  2026-11-05 Aniversário da Ana
  ter 9h30 Dentista
  todo-dia 22:00 Remédio
  25/12 Natal
  ```
- **Horários:** `noite_inicio` / `noite_fim` (padrão 21:00–06:00), `transicao_minutos`, `acordar_minutos`.

## Uso
- **Ler:** toque no livro (ou “Continuar lendo”). Livros (EPUB, PDF, MOBI, AZW3, FB2, CBZ…) podem ser
  copiados pelo cabo USB (modo Transferência de arquivos) para uma pasta `Books`, ou baixados pelos
  catálogos OPDS gratuitos do próprio KOReader. Livros com DRM (ex.: comprados na Amazon) não abrem.
- **Trocar o modo à mão:** segure o dedo sobre a hora — alterna automático → dia → noite → automático.
- **apps:** lista simples dos outros apps instalados.

## Limite de carga
O relógio funciona **sem root**. Limitar a bateria em 85% (para um tablet que fica 24 h no carregador)
é **opcional**:
- **Sem root:** use uma **tomada inteligente** ou um **temporizador de tomada** no carregador.
- **Com root (Magisk):** um serviço do Magisk pausa a carga em 85% e retoma em 50%, e o indicador do
  relógio mostra “‖” quando a carga está pausada. Alternativa pronta: o módulo
  [ACC](https://github.com/VR-25/acc).

> ⚠️ Fazer root exige **desbloquear o bootloader, o que APAGA TODOS OS DADOS** do aparelho, pode anular
> a garantia e, se algo der errado, deixar o aparelho sem iniciar. **É por sua conta e risco.**
> O passo a passo do aparelho testado está em [docs/root-opcional/](docs/root-opcional/).

## Root opcional
[docs/root-opcional/](docs/root-opcional/) — backup pelo modo BROM (mtkclient), desbloqueio, Magisk,
limite de carga, cores suaves no sistema inteiro (saturação estilo Kindle Colorsoft) e bloqueio de
atualizações. Nada disso é necessário para o relógio e o leitor.

## Créditos
- [KOReader](https://github.com/koreader/koreader) — o leitor (AGPL-3.0), instalado a partir das releases oficiais.
- [Open-Meteo](https://open-meteo.com/) — previsão do tempo e busca de cidades. *Weather data by
  [Open-Meteo.com](https://open-meteo.com/)* (dados sob [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)).
- [Noto Serif](https://fonts.google.com/noto/specimen/Noto+Serif) — a serifa do relógio, fonte do próprio Android (OFL).
- [Magisk](https://github.com/topjohnwu/Magisk) — root (opcional).
- [ACC — Advanced Charging Controller](https://github.com/VR-25/acc) — alternativa para o limite de carga (opcional).
- [mtkclient](https://github.com/bkerler/mtkclient) — backup pelo modo BROM (opcional).

## Licença
[MIT](LICENSE).
