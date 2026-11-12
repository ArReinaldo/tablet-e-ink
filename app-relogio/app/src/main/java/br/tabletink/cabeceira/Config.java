package br.tabletink.cabeceira;

import android.content.Context;

import java.io.File;
import java.io.IOException;

/**
 * Configuração num único arquivo de texto (chave = valor), na pasta do app:
 * /sdcard/Android/data/br.tabletink.cabeceira/files/cabeceira.conf
 * O app relê sozinho quando o arquivo muda.
 */
final class Config {

    static final String ARQUIVO = "cabeceira.conf";

    String cidade = "Brasília, DF";
    double latitude = -15.7939;
    double longitude = -47.8828;
    int noiteInicio = 21 * 60;
    int noiteFim = 6 * 60;
    int transicao = 15;
    int acordar = 60;
    int tempoIntervalo = 30;

    private static final String PADRAO =
            "# Relógio de cabeceira — configuração\n"
            + "# Edite e salve; o relógio relê sozinho em até 1 minuto.\n"
            + "\n"
            + "# Cidade (só para referência) e coordenadas usadas na previsão do tempo (Open-Meteo).\n"
            + "# Coordenadas da sua cidade:\n"
            + "#   https://geocoding-api.open-meteo.com/v1/search?name=SUA_CIDADE&language=pt&countryCode=BR\n"
            + "cidade = Brasília, DF\n"
            + "latitude = -15.7939\n"
            + "longitude = -47.8828\n"
            + "\n"
            + "# Modo noite (só a hora e a data, em âmbar) e a transição suave, em minutos\n"
            + "noite_inicio = 21:00\n"
            + "noite_fim = 06:00\n"
            + "transicao_minutos = 15\n"
            + "\n"
            + "# Depois do fim da noite, quanto tempo a previsão do dia fica em destaque\n"
            + "acordar_minutos = 60\n"
            + "\n"
            + "# De quanto em quanto tempo atualizar o tempo\n"
            + "atualizar_minutos = 30\n";

    private static Config atual;
    private static long lidoEm = Long.MIN_VALUE;

    private Config() {}

    static File pasta(Context c) {
        File d = c.getExternalFilesDir(null);
        if (d == null) d = c.getFilesDir();
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static synchronized Config get(Context c) {
        File f = new File(pasta(c), ARQUIVO);
        if (!f.exists()) {
            try {
                Arquivos.gravar(f, PADRAO);
            } catch (IOException ignored) {
            }
        }
        long m = f.lastModified();
        if (atual == null || m != lidoEm) {
            atual = interpretar(Arquivos.ler(f));
            lidoEm = m;
        }
        return atual;
    }

    private static Config interpretar(String texto) {
        Config cfg = new Config();
        if (texto == null) return cfg;
        for (String linha : texto.split("\n")) {
            String l = linha.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            int eq = l.indexOf('=');
            if (eq < 0) continue;
            String k = l.substring(0, eq).trim().toLowerCase();
            String v = l.substring(eq + 1).trim();
            try {
                switch (k) {
                    case "cidade": cfg.cidade = v; break;
                    case "latitude": cfg.latitude = Double.parseDouble(v.replace(',', '.')); break;
                    case "longitude": cfg.longitude = Double.parseDouble(v.replace(',', '.')); break;
                    case "noite_inicio": cfg.noiteInicio = minutos(v, cfg.noiteInicio); break;
                    case "noite_fim": cfg.noiteFim = minutos(v, cfg.noiteFim); break;
                    case "transicao_minutos": cfg.transicao = Math.max(1, Integer.parseInt(v)); break;
                    case "acordar_minutos": cfg.acordar = Math.max(0, Integer.parseInt(v)); break;
                    case "atualizar_minutos": cfg.tempoIntervalo = Math.max(10, Integer.parseInt(v)); break;
                    default: break;
                }
            } catch (NumberFormatException ignored) {
                // valor inválido: mantém o padrão
            }
        }
        return cfg;
    }

    /** "21:00", "21h", "21h30" ou "6" → minutos desde a meia-noite. */
    static int minutos(String v, int padrao) {
        String s = v.trim().toLowerCase().replace('h', ':');
        if (s.endsWith(":")) s = s.substring(0, s.length() - 1);
        try {
            String[] p = s.split(":");
            int h = Integer.parseInt(p[0].trim());
            int m = p.length > 1 && !p[1].trim().isEmpty() ? Integer.parseInt(p[1].trim()) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59) return padrao;
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return padrao;
        }
    }
}
