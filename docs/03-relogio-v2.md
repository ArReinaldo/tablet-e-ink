# Relógio de cabeceira v0.2 — detalhes

## O que mostra
| Modo | Quando (padrão) | Conteúdo |
|---|---|---|
| Dia (papel) | 06:00–21:00 | hora, data por extenso, tempo (ícone · agora · mín/máx · resumo), livro atual ("Título · NN%") ou "Continuar lendo", "apps", até 3 lembretes |
| Ao acordar | 1ª hora após o fim da noite | igual ao dia, com a previsão do dia em destaque |
| Noite (âmbar) | 21:00–06:00 | só hora e data; um toque mostra o botão do livro por 10 s |

- Transição suave de cores e brilho (15 min) na entrada e na saída da noite.
- Bateria discreta no canto: raio = carregando, ‖ = corrente parada (limite de carga 50/85).
- Proteção da tela: o conteúdo se desloca ±8 dp a cada 3 min.
- Vertical e horizontal; o layout troca sozinho ao girar.
- Segurar a hora alterna auto → dia → noite → auto (como antes).
- Cores do tema iguais às da v0.1; no tablet o filtro Colorsoft (saturação 0,45) deixa o âmbar amarelo-claro.

## Configuração (um arquivo só)
Tablet: `/sdcard/Android/data/br.tabletink.cabeceira/files/cabeceira.conf` (relido sozinho em até 1 min).
Pelo PC: `scripts\config-relogio.ps1 -Baixar`, editar em `config\` (pasta do projeto, fora do git), `-Enviar`.

| Chave | Padrão |
|---|---|
| `latitude` / `longitude` | Brasília (DF): -15.7939 / -47.8828 — troque pela sua cidade |
| `ical` | vazio → usa `lembretes.txt` |
| `noite_inicio` / `noite_fim` | 21:00 / 06:00 |
| `transicao_minutos` | 15 |
| `acordar_minutos` | 60 |
| `atualizar_minutos` | 30 (tempo e agenda) |
| `lembretes` | 3 |

**Google Agenda:** Agenda no navegador → Configurações → sua agenda → *Endereço secreto no formato iCal* →
copie para `ical = https://calendar.google.com/calendar/ical/.../basic.ics`. Não compartilhe esse link.

**lembretes.txt** (quando `ical` está vazio), um por linha:
```
2026-11-03 14:00 Reunião
2026-11-05 Aniversário da Ana
ter 9h30 Dentista
todo-dia 22:00 Remédio
25/12 Natal
```

## Dados e permissões
- Tempo: Open-Meteo (sem chave); última resposta em `tempo.json` — sem internet mostra "atualizado às HH:MM".
- Agenda: cache em `agenda.ics`. Suporta repetições (diária/semanal/mensal/anual, BYDAY, COUNT, UNTIL),
  exceções e ocorrências remarcadas/canceladas. Lembrete repetido aparece só na próxima ocorrência.
- Livro: lê `/sdcard/koreader/settings.reader.lua` e `<livro>.sdr/metadata.*.lua`; precisa de
  `appops set br.tabletink.cabeceira MANAGE_EXTERNAL_STORAGE allow` (já no `etapa3-ativar.ps1`).

## Observações dos testes
- O indicador de carga pode mostrar ‖ com carregador fraco (porta USB do PC) e a tela acesa, porque a
  corrente fica negativa mesmo sem o limite atuar. No carregador de parede mostra o raio.
- Abrir o protetor pelo atalho de teste `com.android.systemui/.Somnambulator` derruba o SystemUI
  (bug do Android 13 na tela de abertura). O protetor normal (1 min parado no carregador) não tem o problema.
