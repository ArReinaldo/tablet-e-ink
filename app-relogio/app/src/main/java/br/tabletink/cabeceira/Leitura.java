package br.tabletink.cabeceira;

import android.os.Environment;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Livro atual do KOReader: o último arquivo aberto (settings.reader.lua) e o progresso
 * (percent_finished) guardado na pasta .sdr ao lado do livro. Requer acesso a todos os arquivos.
 */
final class Leitura {

    static final class Livro {
        final String titulo;
        final int porcento;

        Livro(String titulo, int porcento) {
            this.titulo = titulo;
            this.porcento = porcento;
        }
    }

    private static final File KOREADER = new File(Environment.getExternalStorageDirectory(), "koreader");
    private static final String TEXTO_LUA = "\"((?:[^\"\\\\]|\\\\.)*)\"";

    private Leitura() {}

    /** null quando não há livro (ou só o guia do KOReader) ou sem permissão de leitura. */
    static Livro atual() {
        if (!Environment.isExternalStorageManager()) return null;
        String cfg = Arquivos.ler(new File(KOREADER, "settings.reader.lua"));
        String ultimo = cfg == null ? null : texto(cfg, "lastfile");
        if (ultimo == null || ultimo.contains("/koreader/help/")) return null;

        File livro = new File(ultimo);
        String nome = livro.getName();
        int ponto = nome.lastIndexOf('.');
        String base = ponto > 0 ? nome.substring(0, ponto) : nome;
        String ext = ponto > 0 ? nome.substring(ponto + 1) : "";
        String sdr = base + ".sdr/metadata." + ext + ".lua";

        String meta = Arquivos.ler(new File(livro.getParentFile(), sdr));
        if (meta == null) meta = Arquivos.ler(new File(KOREADER, "docsettings" + livro.getParent() + "/" + sdr));

        double fracao = 0;
        String titulo = null;
        if (meta != null) {
            Matcher m = Pattern.compile("\\[\"percent_finished\"\\]\\s*=\\s*([0-9.eE+-]+)").matcher(meta);
            if (m.find()) {
                try {
                    fracao = Double.parseDouble(m.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
            titulo = texto(meta, "title");
        }
        if (titulo == null || titulo.trim().isEmpty() || titulo.equals("N/A")) titulo = base;
        titulo = titulo.trim();
        if (titulo.length() > 24) titulo = titulo.substring(0, 23).trim() + "…";
        return new Livro(titulo, (int) Math.round(Math.max(0, Math.min(1, fracao)) * 100));
    }

    private static String texto(String lua, String chave) {
        Matcher m = Pattern.compile("\\[\"" + chave + "\"\\]\\s*=\\s*" + TEXTO_LUA).matcher(lua);
        if (!m.find()) return null;
        return m.group(1).replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\");
    }
}
