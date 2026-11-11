# Relógio de cabeceira v0.4 — detalhes

## O que mostra
| Modo | Quando (padrão) | Conteúdo |
|---|---|---|
| Dia (papel) | 06:00–21:00 | data no canto, hora grande, tempo numa linha (ícone · agora · mín/máx · resumo); no rodapé, a lista de apps (um toque abre) e o cartão "Continuar lendo" |
| Ao acordar | 1ª hora após o fim da noite | hora e saudação, previsão do dia em destaque (resumo, conselho, mín/máx, umidade), faixa 9h–21h com chance de chuva, "apps" para a lista completa |
| Noite (âmbar) | 21:00–06:00 | só hora e data; um toque mostra o botão do livro por 10 s |

- Transição suave de cores e brilho (15 min) na entrada e na saída da noite.
- Bateria discreta no canto: raio = carregando, ‖ = corrente parada (limite de carga 50/85).
- Proteção da tela: o conteúdo se desloca ±8 dp a cada 3 min.
- Vertical e horizontal, com a mesma tela rearranjada; o layout troca sozinho ao girar.
- Bateria: de dia mostra também "carga em pausa" quando o limite de carga atua.
- Segurar a hora alterna auto → dia → noite → auto (como antes).
- Cores do tema iguais às da v0.1; no tablet o filtro Colorsoft (saturação 0,45) deixa o âmbar amarelo-claro.

## Configuração (um arquivo só)
Tablet: `/sdcard/Android/data/br.tabletink.cabeceira/files/cabeceira.conf` (relido sozinho em até 1 min).
Pelo PC: `scripts\config-relogio.ps1 -Baixar`, editar em `config\` (pasta do projeto, fora do git), `-Enviar`.

| Chave | Padrão |
|---|---|
| `latitude` / `longitude` | Brasília (DF): -15.7939 / -47.8828 — troque pela sua cidade |
| `noite_inicio` / `noite_fim` | 21:00 / 06:00 |
| `transicao_minutos` | 15 |
| `acordar_minutos` | 60 |
| `atualizar_minutos` | 30 (tempo) |
| `livro` | vazio → "Meus livros" no cartão; escreva o título do livro atual |

## Dados e permissões
- Tempo: Open-Meteo (sem chave); última resposta em `tempo.json` — sem internet mostra "atualizado às HH:MM".
- Agenda: desde a v0.4 o relógio não mostra compromissos nem baixa a agenda (as chaves `ical` e
  `lembretes` são ignoradas).
- Livro: o cartão "Continuar lendo" (e, à noite, "Meus livros") abre o ReadEra (`org.readera`), ou o KOReader se ele não estiver instalado.
  O ReadEra não informa livro nem progresso a outros apps, então o cartão mostra o título da chave
  `livro` (ou "Meus livros") e não mostra %. O
  relógio não precisa de acesso aos arquivos do aparelho.

## Observações dos testes
- O indicador de carga pode mostrar ‖ com carregador fraco (porta USB do PC) e a tela acesa, porque a
  corrente fica negativa mesmo sem o limite atuar. No carregador de parede mostra o raio.
- Abrir o protetor pelo atalho de teste `com.android.systemui/.Somnambulator` derruba o SystemUI
  (bug do Android 13 na tela de abertura). O protetor normal (1 min parado no carregador) não tem o problema.
