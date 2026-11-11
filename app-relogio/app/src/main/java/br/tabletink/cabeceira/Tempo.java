package br.tabletink.cabeceira;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Previsão da Open-Meteo (gratuita, sem chave). A última resposta fica em tempo.json, para
 * continuar mostrando o tempo sem internet.
 */
final class Tempo {

    static final String ARQUIVO = "tempo.json";

    /** O que a tela mostra, já calculado para o momento atual. */
    static final class Resumo {
        int agora, min, max, chuva;
        IconeView.Tipo icone;
        String resto;   // do agora até o fim do dia: "garoa à noite"
        String dia;     // do fim da noite até o fim do dia, para o destaque ao acordar
        IconeView.Tipo iconeDia;  // o que marca o dia (chuva, sol...), para o destaque ao acordar
        int umidade = -1;         // -1 quando a resposta guardada não tem
        final List<Hora> horas = new ArrayList<>();  // 9h, 12h, 15h, 18h e 21h de hoje
    }

    static final class Hora {
        int hora, temp, chuva;
        IconeView.Tipo icone;
    }

    private static final int[] HORAS_FAIXA = {9, 12, 15, 18, 21};

    private Tempo() {}

    static boolean buscar(Context c, Config cfg) {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,relative_humidity_2m,weather_code,is_day"
                        + "&hourly=temperature_2m,relative_humidity_2m,weather_code,precipitation_probability"
                        + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                        + "&timezone=auto&forecast_days=2",
                cfg.latitude, cfg.longitude);
        try {
            JSONObject resposta = new JSONObject(Arquivos.baixar(url));
            if (!resposta.has("hourly") || !resposta.has("daily")) return false;
            JSONObject cache = new JSONObject();
            cache.put("buscadoEm", System.currentTimeMillis());
            cache.put("lat", cfg.latitude);
            cache.put("lon", cfg.longitude);
            cache.put("resposta", resposta);
            Arquivos.gravar(new File(Config.pasta(c), ARQUIVO), cache.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** A última resposta guardada, se for da mesma localização da configuração. */
    static JSONObject carregar(Context c, Config cfg) {
        String s = Arquivos.ler(new File(Config.pasta(c), ARQUIVO));
        if (s == null) return null;
        try {
            JSONObject j = new JSONObject(s);
            if (Math.abs(j.optDouble("lat") - cfg.latitude) > 1e-3
                    || Math.abs(j.optDouble("lon") - cfg.longitude) > 1e-3) return null;
            return j;
        } catch (JSONException e) {
            return null;
        }
    }

    /** null quando a resposta guardada não tem o dia de hoje. */
    static Resumo resumir(JSONObject cache, LocalDateTime agora, int fimNoiteMin) {
        try {
            JSONObject r = cache.getJSONObject("resposta");
            String hoje = agora.toLocalDate().toString();
            JSONObject d = r.getJSONObject("daily");
            int i = indice(d.getJSONArray("time"), hoje);
            if (i < 0) return null;
            JSONObject h = r.getJSONObject("hourly");
            JSONArray horas = h.getJSONArray("time");
            String horaAtual = String.format(Locale.US, "%sT%02d:00", hoje, agora.getHour());
            int ih = indice(horas, horaAtual);
            if (ih < 0) return null;

            Resumo res = new Resumo();
            res.min = (int) Math.round(d.getJSONArray("temperature_2m_min").getDouble(i));
            res.max = (int) Math.round(d.getJSONArray("temperature_2m_max").getDouble(i));
            res.chuva = d.getJSONArray("precipitation_probability_max").optInt(i, 0);

            // "Agora": usa o atual da API se for desta hora; senão, a previsão horária
            JSONObject cur = r.optJSONObject("current");
            int codigo = h.getJSONArray("weather_code").optInt(ih, 0);
            double temp = h.getJSONArray("temperature_2m").optDouble(ih, 0);
            boolean dia = agora.getHour() >= 6 && agora.getHour() < 18;
            JSONArray umid = h.optJSONArray("relative_humidity_2m");
            if (umid != null && !umid.isNull(ih)) res.umidade = (int) Math.round(umid.optDouble(ih));
            if (cur != null && cur.has("relative_humidity_2m")) {
                res.umidade = (int) Math.round(cur.optDouble("relative_humidity_2m"));
            }
            if (cur != null && cur.optString("time").startsWith(horaAtual.substring(0, 13))) {
                codigo = cur.optInt("weather_code", codigo);
                temp = cur.optDouble("temperature_2m", temp);
                dia = cur.optInt("is_day", dia ? 1 : 0) == 1;
            }
            res.agora = (int) Math.round(temp);
            res.icone = icone(codigo, dia);

            JSONArray codigos = h.getJSONArray("weather_code");
            JSONArray probs = h.getJSONArray("precipitation_probability");
            res.resto = descrever(horas, codigos, probs, hoje, agora.getHour());
            res.dia = descrever(horas, codigos, probs, hoje, fimNoiteMin / 60);
            if (res.resto == null) res.resto = res.dia;
            res.iconeDia = iconeDoDia(res.dia, res.icone);

            JSONArray temps = h.getJSONArray("temperature_2m");
            for (int hh : HORAS_FAIXA) {
                int k = indice(horas, String.format(Locale.US, "%sT%02d:00", hoje, hh));
                if (k < 0) continue;
                Hora x = new Hora();
                x.hora = hh;
                x.temp = (int) Math.round(temps.optDouble(k, 0));
                x.chuva = probs.isNull(k) ? 0 : probs.optInt(k, 0);
                x.icone = icone(codigos.optInt(k, 0), hh >= 6 && hh < 18);
                res.horas.add(x);
            }
            return res;
        } catch (JSONException e) {
            return null;
        }
    }

    /** Ícone que resume o dia a partir da descrição; sem nada marcante, o do momento. */
    private static IconeView.Tipo iconeDoDia(String dia, IconeView.Tipo agora) {
        if (dia == null) return agora;
        if (dia.startsWith("trovoadas")) return IconeView.Tipo.TROVOADA;
        if (dia.startsWith("chuva")) return IconeView.Tipo.CHUVA;
        if (dia.startsWith("garoa")) return IconeView.Tipo.GAROA;
        if (dia.equals("nublado")) return IconeView.Tipo.NUVEM;
        if (dia.equals("neblina")) return IconeView.Tipo.NEBLINA;
        if (dia.equals("sol entre nuvens")) return IconeView.Tipo.SOL_NUVEM;
        if (dia.equals("céu limpo")) return IconeView.Tipo.SOL;
        return agora;
    }

    private static int indice(JSONArray a, String valor) {
        for (int i = 0; i < a.length(); i++) if (valor.equals(a.optString(i))) return i;
        return -1;
    }

    private static boolean precipita(int c) {
        return (c >= 51 && c <= 67) || (c >= 71 && c <= 77) || (c >= 80 && c <= 86) || c >= 95;
    }

    static IconeView.Tipo icone(int c, boolean dia) {
        if (c >= 95) return IconeView.Tipo.TROVOADA;
        if (c >= 51 && c <= 57) return IconeView.Tipo.GAROA;
        if (precipita(c)) return IconeView.Tipo.CHUVA;
        if (c == 45 || c == 48) return IconeView.Tipo.NEBLINA;
        if (c == 3) return IconeView.Tipo.NUVEM;
        if (c == 1 || c == 2) return dia ? IconeView.Tipo.SOL_NUVEM : IconeView.Tipo.LUA_NUVEM;
        return dia ? IconeView.Tipo.SOL : IconeView.Tipo.LUA;
    }

    /** Resumo curto das horas de `data` a partir de `deHora`: "chuva à tarde", "céu limpo"... */
    private static String descrever(JSONArray horas, JSONArray codigos, JSONArray probs, String data, int deHora) {
        boolean[] periodo = new boolean[4];
        int[] ceu = new int[4];
        boolean alguma = false, trovoada = false, soGaroa = true;
        int contadas = 0;
        for (int i = 0; i < horas.length(); i++) {
            String t = horas.optString(i);
            if (!t.startsWith(data)) continue;
            int h = Integer.parseInt(t.substring(11, 13));
            if (h < deHora) continue;
            contadas++;
            int c = codigos.optInt(i, 0);
            int p = probs.isNull(i) ? 0 : probs.optInt(i, 0);
            if (precipita(c) || p >= 60) {
                alguma = true;
                periodo[h / 6] = true;
                if (c >= 95) trovoada = true;
                if (!(c >= 51 && c <= 57)) soGaroa = false;
            } else {
                ceu[c == 0 ? 0 : (c == 1 || c == 2) ? 1 : (c == 45 || c == 48) ? 3 : 2]++;
            }
        }
        if (contadas == 0) return null;
        if (alguma) {
            String oQue = trovoada ? "trovoadas" : soGaroa ? "garoa" : "chuva";
            String[] nomes = {"de madrugada", "pela manhã", "à tarde", "à noite"};
            List<String> quando = new ArrayList<>();
            for (int k = 0; k < 4; k++) if (periodo[k]) quando.add(nomes[k]);
            if (quando.size() >= 3) return oQue + " ao longo do dia";
            return oQue + " " + String.join(" e ", quando);
        }
        int melhor = 0;
        for (int k = 1; k < 4; k++) if (ceu[k] > ceu[melhor]) melhor = k;
        return new String[]{"céu limpo", "sol entre nuvens", "nublado", "neblina"}[melhor];
    }
}
