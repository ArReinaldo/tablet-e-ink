package br.tabletink.cabeceira;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Último livro aberto no ReadEra. O ReadEra não publica isso para outros apps, então lemos
 * o banco dele (/data/data/org.readera/databases/readera.db) com root: uma cópia vai para o
 * cache do relógio e é aberta só para leitura. Sem root (ou sem livro), devolve null.
 */
final class Leitura {

    static final class Livro {
        final String titulo;
        /** 0..100, ou -1 se o ReadEra ainda não sabe. */
        final int porcento;

        Livro(String titulo, int porcento) {
            this.titulo = titulo;
            this.porcento = porcento;
        }
    }

    private static final String TAG = "Cabeceira";
    private static final String BANCO = "/data/data/org.readera/databases/readera.db";

    private Leitura() {}

    static Livro ultimo(Context c) {
        File copia = new File(c.getCacheDir(), "readera.db");
        if (!copiarComRoot(copia)) {
            //noinspection ResultOfMethodCallIgnored
            copia.delete();
            return null;
        }
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(copia.getPath(), null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
             Cursor cur = db.rawQuery(
                     "SELECT user_title, doc_title, doc_file_name_title, doc_position FROM docs"
                             + " WHERE doc_active = 1 AND doc_last_read_time > 0"
                             + " ORDER BY doc_last_read_time DESC LIMIT 1", null)) {
            if (!cur.moveToFirst()) return null;
            String titulo = primeiro(cur.getString(0), cur.getString(1), cur.getString(2));
            if (titulo == null) return null;
            return new Livro(titulo, porcento(cur.getString(3)));
        } catch (RuntimeException e) {
            Log.w(TAG, "banco do ReadEra", e);
            return null;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            copia.delete();
        }
    }

    /** "su --mount-master -c cat banco" para o cache do app (o --mount-master enxerga as pastas
     *  dos outros apps, que o Android 11+ esconde dentro do processo do app). */
    private static boolean copiarComRoot(File destino) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "--mount-master", "-c", "cat " + BANCO)
                    .redirectError(new File("/dev/null")).start();
            p.getOutputStream().close();
            try (InputStream in = p.getInputStream(); OutputStream out = new FileOutputStream(destino)) {
                byte[] b = new byte[16384];
                int n;
                long total = 0;
                while ((n = in.read(b)) > 0) {
                    out.write(b, 0, n);
                    total += n;
                    if (total > 64L * 1024 * 1024) return false;
                }
            }
            if (!p.waitFor(10, TimeUnit.SECONDS)) return false;
            boolean ok = p.exitValue() == 0 && destino.length() > 0;
            if (!ok) Log.w(TAG, "su saiu com " + p.exitValue() + ", " + destino.length() + " bytes");
            return ok;
        } catch (Exception e) {
            Log.w(TAG, "su falhou", e);
            return false;
        } finally {
            if (p != null) p.destroy();
        }
    }

    private static String primeiro(String... s) {
        for (String v : s) if (v != null && !v.trim().isEmpty()) return v.trim();
        return null;
    }

    /** doc_position é um JSON com "ratio" (0..1; -1 = desconhecido). */
    private static int porcento(String posicao) {
        try {
            double r = new JSONObject(posicao).optDouble("ratio", -1);
            if (r < 0 || r > 1) return -1;
            return (int) Math.round(r * 100);
        } catch (Exception e) {
            return -1;
        }
    }
}
