package br.tabletink.cabeceira;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lembretes dos próximos 7 dias: do link iCal secreto do Google Agenda (cache em agenda.ics) ou,
 * sem link, de lembretes.txt na pasta do app.
 */
final class Agenda {

    static final String ICS = "agenda.ics";
    static final String LOCAL = "lembretes.txt";

    private static final String[] DIAS = {"Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"};

    private static final String EXEMPLO =
            "# Lembretes do relógio de cabeceira (usados quando \"ical\" está vazio em cabeceira.conf).\n"
            + "# Um por linha; linhas começando com # são ignoradas. Formatos:\n"
            + "#   2026-11-03 14:00 Reunião        data e hora\n"
            + "#   2026-11-05 Aniversário da Ana   dia inteiro\n"
            + "#   ter 9h30 Dentista               toda terça (seg ter qua qui sex sab dom)\n"
            + "#   todo-dia 22:00 Remédio          todos os dias\n"
            + "#   25/12 Natal                     todo ano\n"
            + "# Tire o # do começo de uma linha acima para testar.\n";

    static final class Evento {
        final LocalDate data;
        final LocalTime hora;   // null = dia inteiro
        final String texto;

        Evento(LocalDate data, LocalTime hora, String texto) {
            this.data = data;
            this.hora = hora;
            this.texto = texto;
        }
    }

    private Agenda() {}

    static boolean buscar(Context c, Config cfg) {
        if (cfg.ical.isEmpty()) return false;
        try {
            String url = cfg.ical.replaceFirst("^webcal://", "https://");
            String s = Arquivos.baixar(url);
            if (!s.contains("BEGIN:VCALENDAR")) return false;
            Arquivos.gravar(new File(Config.pasta(c), ICS), s);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Até `n` rótulos prontos: "Amanhã · Reunião 14h". */
    static List<String> rotulos(Context c, Config cfg, int n) {
        LocalDateTime agora = LocalDateTime.now();
        LocalDate hoje = agora.toLocalDate();
        LocalDate fim = hoje.plusDays(7);
        File pasta = Config.pasta(c);

        List<Evento> eventos;
        if (!cfg.ical.isEmpty()) {
            String s = Arquivos.ler(new File(pasta, ICS));
            eventos = s == null ? Collections.emptyList() : Ics.eventos(s, hoje, fim);
        } else {
            File f = new File(pasta, LOCAL);
            if (!f.exists()) {
                try {
                    Arquivos.gravar(f, EXEMPLO);
                } catch (IOException ignored) {
                }
            }
            String s = Arquivos.ler(f);
            eventos = s == null ? Collections.emptyList() : local(s, hoje, fim);
        }

        List<Evento> futuros = new ArrayList<>();
        for (Evento e : eventos) {
            if (e.data.equals(hoje) && e.hora != null && e.hora.isBefore(agora.toLocalTime())) continue;
            futuros.add(e);
        }
        futuros.sort(Comparator.comparing((Evento e) -> e.data)
                .thenComparing(e -> e.hora == null ? LocalTime.MIN : e.hora));

        // Um lembrete que se repete (ex.: diário) aparece só na próxima ocorrência
        Set<String> vistos = new HashSet<>();
        List<String> saida = new ArrayList<>();
        for (Evento e : futuros) {
            if (saida.size() >= n) break;
            if (vistos.add(e.texto)) saida.add(rotulo(e, hoje));
        }
        return saida;
    }

    static String rotulo(Evento e, LocalDate hoje) {
        String dia = e.data.equals(hoje) ? "Hoje"
                : e.data.equals(hoje.plusDays(1)) ? "Amanhã"
                : DIAS[e.data.getDayOfWeek().getValue() - 1];
        String texto = e.texto.length() > 34 ? e.texto.substring(0, 33).trim() + "…" : e.texto;
        String hora = "";
        if (e.hora != null) {
            hora = " " + e.hora.getHour() + "h"
                    + (e.hora.getMinute() == 0 ? "" : String.format(Locale.US, "%02d", e.hora.getMinute()));
        }
        return dia + " · " + texto + hora;
    }

    // ---- lembretes.txt ----

    private static List<Evento> local(String s, LocalDate de, LocalDate ate) {
        List<Evento> out = new ArrayList<>();
        for (String linha : s.split("\n")) {
            String l = linha.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] t = l.split("\\s+");
            int i = 1;
            LocalTime hora = null;
            if (t.length > 1) {
                hora = hora(t[1]);
                if (hora != null) i = 2;
            }
            StringBuilder sb = new StringBuilder();
            for (; i < t.length; i++) sb.append(sb.length() == 0 ? "" : " ").append(t[i]);
            String texto = sb.toString();
            if (texto.isEmpty()) continue;

            String quando = t[0].toLowerCase(Locale.ROOT);
            DayOfWeek semana = diaDaSemana(quando);
            if (quando.matches("\\d{4}-\\d{2}-\\d{2}")) {
                try {
                    LocalDate d = LocalDate.parse(quando);
                    if (!d.isBefore(de) && d.isBefore(ate)) out.add(new Evento(d, hora, texto));
                } catch (DateTimeParseException ignored) {
                }
            } else if (quando.matches("\\d{1,2}/\\d{1,2}")) {
                String[] p = quando.split("/");
                try {
                    MonthDay md = MonthDay.of(Integer.parseInt(p[1]), Integer.parseInt(p[0]));
                    for (LocalDate d = de; d.isBefore(ate); d = d.plusDays(1)) {
                        if (MonthDay.from(d).equals(md)) out.add(new Evento(d, hora, texto));
                    }
                } catch (RuntimeException ignored) {
                }
            } else if (semana != null || quando.equals("todo-dia") || quando.startsWith("diari")
                    || quando.startsWith("diári")) {
                for (LocalDate d = de; d.isBefore(ate); d = d.plusDays(1)) {
                    if (semana == null || d.getDayOfWeek() == semana) out.add(new Evento(d, hora, texto));
                }
            }
        }
        return out;
    }

    private static DayOfWeek diaDaSemana(String s) {
        String p = s.length() >= 3 ? s.substring(0, 3) : s;
        switch (p) {
            case "seg": return DayOfWeek.MONDAY;
            case "ter": return DayOfWeek.TUESDAY;
            case "qua": return DayOfWeek.WEDNESDAY;
            case "qui": return DayOfWeek.THURSDAY;
            case "sex": return DayOfWeek.FRIDAY;
            case "sab": case "sáb": return DayOfWeek.SATURDAY;
            case "dom": return DayOfWeek.SUNDAY;
            default: return null;
        }
    }

    /** "14:00", "14h", "14h30", "9h" → hora; qualquer outra coisa → null. */
    static LocalTime hora(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        if (!t.matches("\\d{1,2}(:\\d{2}|h\\d{0,2})")) return null;
        String[] p = t.split("[:h]");
        int h = Integer.parseInt(p[0]);
        int m = p.length > 1 && !p[1].isEmpty() ? Integer.parseInt(p[1]) : 0;
        if (h > 23 || m > 59) return null;
        return LocalTime.of(h, m);
    }
}
