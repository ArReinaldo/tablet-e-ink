package br.tabletink.cabeceira;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitor mínimo de iCalendar (.ics) para os eventos de uma janela de dias: DTSTART com data,
 * UTC ou TZID; RRULE DAILY/WEEKLY/MONTHLY/YEARLY com INTERVAL, COUNT, UNTIL e BYDAY;
 * EXDATE; ocorrências alteradas (RECURRENCE-ID) e canceladas.
 */
final class Ics {

    private static final DateTimeFormatter DATA = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final Pattern BYDAY = Pattern.compile("([+-]?\\d*)(MO|TU|WE|TH|FR|SA|SU)");

    private static final class Prop {
        final String nome;
        final Map<String, String> params;
        final String valor;

        Prop(String nome, Map<String, String> params, String valor) {
            this.nome = nome;
            this.params = params;
            this.valor = valor;
        }
    }

    private static final class Quando {
        final LocalDateTime t;
        final boolean diaInteiro;

        Quando(LocalDateTime t, boolean diaInteiro) {
            this.t = t;
            this.diaInteiro = diaInteiro;
        }
    }

    private Ics() {}

    /** Ocorrências com data em [de, ate). */
    static List<Agenda.Evento> eventos(String texto, LocalDate de, LocalDate ate) {
        String t = texto.replace("\r\n", "\n").replace('\r', '\n')
                .replace("\n ", "").replace("\n\t", "");
        List<List<Prop>> vevents = new ArrayList<>();
        List<Prop> atual = null;
        for (String linha : t.split("\n")) {
            if (linha.equals("BEGIN:VEVENT")) { atual = new ArrayList<>(); continue; }
            if (linha.equals("END:VEVENT")) {
                if (atual != null) vevents.add(atual);
                atual = null;
                continue;
            }
            if (atual == null) continue;
            Prop p = prop(linha);
            if (p != null) atual.add(p);
        }

        ZoneId local = ZoneId.systemDefault();

        // Ocorrências de uma série que foram alteradas ou canceladas individualmente
        Map<String, Set<LocalDateTime>> substituidas = new HashMap<>();
        for (List<Prop> ev : vevents) {
            Prop rid = get(ev, "RECURRENCE-ID"), uid = get(ev, "UID");
            if (rid == null || uid == null) continue;
            Quando q = quando(rid.params, rid.valor, local);
            if (q != null) substituidas.computeIfAbsent(uid.valor, k -> new HashSet<>()).add(q.t);
        }

        List<Agenda.Evento> out = new ArrayList<>();
        for (List<Prop> ev : vevents) {
            Prop status = get(ev, "STATUS");
            if (status != null && "CANCELLED".equalsIgnoreCase(status.valor)) continue;
            Prop ds = get(ev, "DTSTART");
            if (ds == null) continue;
            Quando ini = quando(ds.params, ds.valor, local);
            if (ini == null) continue;
            String resumo = texto(get(ev, "SUMMARY"));
            if (resumo.isEmpty()) resumo = "(sem título)";
            LocalTime hora = ini.diaInteiro ? null : ini.t.toLocalTime();

            Prop rr = get(ev, "RRULE");
            if (rr == null || get(ev, "RECURRENCE-ID") != null) {
                LocalDate d = ini.t.toLocalDate();
                if (!d.isBefore(de) && d.isBefore(ate)) out.add(new Agenda.Evento(d, hora, resumo));
                continue;
            }

            Set<LocalDateTime> excluir = new HashSet<>();
            for (Prop p : ev) {
                if (!p.nome.equals("EXDATE")) continue;
                for (String v : p.valor.split(",")) {
                    Quando q = quando(p.params, v, local);
                    if (q != null) excluir.add(q.t);
                }
            }
            Prop uid = get(ev, "UID");
            if (uid != null) excluir.addAll(substituidas.getOrDefault(uid.valor, Collections.emptySet()));

            expandir(ini, regra(rr.valor), local, excluir, de, ate, resumo, hora, out);
        }
        return out;
    }

    private static void expandir(Quando ini, Map<String, String> r, ZoneId local, Set<LocalDateTime> excluir,
                                 LocalDate de, LocalDate ate, String resumo, LocalTime hora,
                                 List<Agenda.Evento> out) {
        String freq = r.getOrDefault("FREQ", "");
        int intervalo = Math.max(1, inteiro(r.get("INTERVAL"), 1));
        int limite = r.containsKey("COUNT") ? inteiro(r.get("COUNT"), Integer.MAX_VALUE) : Integer.MAX_VALUE;
        LocalDateTime ateQuando = null;
        if (r.containsKey("UNTIL")) {
            Quando u = quando(Collections.emptyMap(), r.get("UNTIL"), local);
            if (u != null) ateQuando = u.diaInteiro ? u.t.toLocalDate().atTime(LocalTime.MAX) : u.t;
        }
        List<String[]> dias = new ArrayList<>();   // {ordinal, dia}
        if (r.containsKey("BYDAY")) {
            for (String s : r.get("BYDAY").split(",")) {
                Matcher m = BYDAY.matcher(s.trim());
                if (m.matches()) dias.add(new String[]{m.group(1), m.group(2)});
            }
        }

        LocalDate d0 = ini.t.toLocalDate();
        LocalTime t0 = ini.t.toLocalTime();
        int geradas = 0;
        for (int k = 0; k < 20_000 && geradas < limite; k++) {
            List<LocalDate> candidatas = new ArrayList<>();
            switch (freq) {
                case "DAILY":
                    candidatas.add(d0.plusDays((long) k * intervalo));
                    break;
                case "WEEKLY": {
                    if (dias.isEmpty()) {
                        candidatas.add(d0.plusWeeks((long) k * intervalo));
                    } else {
                        LocalDate semana = d0.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                .plusWeeks((long) k * intervalo);
                        for (String[] dia : dias) {
                            LocalDate x = semana.with(TemporalAdjusters.nextOrSame(dow(dia[1])));
                            if (!x.isBefore(d0)) candidatas.add(x);
                        }
                        Collections.sort(candidatas);
                    }
                    break;
                }
                case "MONTHLY": {
                    YearMonth ym = YearMonth.from(d0).plusMonths((long) k * intervalo);
                    if (!dias.isEmpty() && !dias.get(0)[0].isEmpty()) {
                        for (String[] dia : dias) {
                            LocalDate x = enesimo(ym, inteiro(dia[0], 1), dow(dia[1]));
                            if (x != null && !x.isBefore(d0)) candidatas.add(x);
                        }
                        Collections.sort(candidatas);
                    } else if (d0.getDayOfMonth() <= ym.lengthOfMonth()) {
                        candidatas.add(ym.atDay(d0.getDayOfMonth()));
                    }
                    break;
                }
                case "YEARLY": {
                    LocalDate x = d0.plusYears((long) k * intervalo);
                    if (x.getDayOfMonth() == d0.getDayOfMonth()) candidatas.add(x);   // pula 29/02 em ano comum
                    break;
                }
                default:
                    return;
            }

            boolean acabou = false;
            for (LocalDate x : candidatas) {
                if (geradas >= limite) break;
                LocalDateTime quando = x.atTime(t0);
                if (ateQuando != null && quando.isAfter(ateQuando)) { acabou = true; break; }
                if (!x.isBefore(ate)) { acabou = true; break; }
                geradas++;
                if (x.isBefore(de) || excluir.contains(quando)) continue;
                out.add(new Agenda.Evento(x, hora, resumo));
            }
            if (acabou) break;
        }
    }

    private static LocalDate enesimo(YearMonth ym, int n, DayOfWeek dia) {
        LocalDate x;
        if (n > 0) {
            x = ym.atDay(1).with(TemporalAdjusters.nextOrSame(dia)).plusWeeks(n - 1L);
        } else {
            x = ym.atEndOfMonth().with(TemporalAdjusters.previousOrSame(dia)).minusWeeks(-n - 1L);
        }
        return YearMonth.from(x).equals(ym) ? x : null;
    }

    private static DayOfWeek dow(String s) {
        switch (s) {
            case "MO": return DayOfWeek.MONDAY;
            case "TU": return DayOfWeek.TUESDAY;
            case "WE": return DayOfWeek.WEDNESDAY;
            case "TH": return DayOfWeek.THURSDAY;
            case "FR": return DayOfWeek.FRIDAY;
            case "SA": return DayOfWeek.SATURDAY;
            default: return DayOfWeek.SUNDAY;
        }
    }

    private static Quando quando(Map<String, String> params, String valor, ZoneId local) {
        String v = valor.trim();
        try {
            if (v.length() == 8 || "DATE".equalsIgnoreCase(params.get("VALUE"))) {
                return new Quando(LocalDate.parse(v.substring(0, 8), DATA).atStartOfDay(), true);
            }
            LocalDateTime t = LocalDateTime.parse(v.substring(0, 15), DATA_HORA);
            ZoneId z = null;
            if (v.endsWith("Z")) {
                z = ZoneOffset.UTC;
            } else if (params.containsKey("TZID")) {
                try {
                    z = ZoneId.of(params.get("TZID"));
                } catch (RuntimeException e) {
                    z = local;
                }
            }
            if (z != null) t = t.atZone(z).withZoneSameInstant(local).toLocalDateTime();
            return new Quando(t, false);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Map<String, String> regra(String s) {
        Map<String, String> m = new HashMap<>();
        for (String par : s.split(";")) {
            int eq = par.indexOf('=');
            if (eq > 0) m.put(par.substring(0, eq).toUpperCase(Locale.ROOT), par.substring(eq + 1));
        }
        return m;
    }

    private static int inteiro(String s, int padrao) {
        if (s == null || s.isEmpty() || s.equals("+") || s.equals("-")) return padrao;
        try {
            return Integer.parseInt(s.startsWith("+") ? s.substring(1) : s);
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private static Prop get(List<Prop> ev, String nome) {
        for (Prop p : ev) if (p.nome.equals(nome)) return p;
        return null;
    }

    private static String texto(Prop p) {
        if (p == null) return "";
        return p.valor.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",")
                .replace("\\;", ";").replace("\\\\", "\\").trim();
    }

    /** "NOME;P1=V1;P2=\"V:2\":valor" → Prop. */
    private static Prop prop(String linha) {
        int dois = -1;
        boolean aspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char ch = linha.charAt(i);
            if (ch == '"') aspas = !aspas;
            else if (ch == ':' && !aspas) { dois = i; break; }
        }
        if (dois <= 0) return null;
        String[] partes = linha.substring(0, dois).split(";");
        Map<String, String> params = new HashMap<>();
        for (int i = 1; i < partes.length; i++) {
            int eq = partes[i].indexOf('=');
            if (eq > 0) {
                params.put(partes[i].substring(0, eq).toUpperCase(Locale.ROOT),
                        partes[i].substring(eq + 1).replace("\"", ""));
            }
        }
        return new Prop(partes[0].toUpperCase(Locale.ROOT), params, linha.substring(dois + 1));
    }
}
