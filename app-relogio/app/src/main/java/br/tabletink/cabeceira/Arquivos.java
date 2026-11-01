package br.tabletink.cabeceira;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Leitura/gravação de arquivos pequenos e download HTTP, sem bibliotecas. */
final class Arquivos {

    private static final int LIMITE = 4 * 1024 * 1024;

    private Arquivos() {}

    /** Conteúdo do arquivo, ou null se não existir ou não puder ser lido. */
    static String ler(File f) {
        if (f == null || !f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return lerTudo(in);
        } catch (IOException e) {
            return null;
        }
    }

    /** Grava num temporário e renomeia, para nunca deixar um arquivo pela metade. */
    static void gravar(File f, String conteudo) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(conteudo.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(f)) throw new IOException("rename " + f);
    }

    static String baixar(String endereco) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(endereco).openConnection();
        con.setConnectTimeout(15_000);
        con.setReadTimeout(20_000);
        con.setRequestProperty("User-Agent", "Cabeceira/0.2 (tablet-e-ink)");
        try {
            if (con.getResponseCode() != 200) throw new IOException("HTTP " + con.getResponseCode());
            try (InputStream in = con.getInputStream()) {
                return lerTudo(in);
            }
        } finally {
            con.disconnect();
        }
    }

    private static String lerTudo(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) {
            buf.write(b, 0, n);
            if (buf.size() > LIMITE) throw new IOException("arquivo grande demais");
        }
        return buf.toString(StandardCharsets.UTF_8.name());
    }
}
