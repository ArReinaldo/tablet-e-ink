package br.tabletink.cabeceira;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import java.util.Calendar;

/**
 * Modo dia (papel) / noite (âmbar): cores, horário, transição e brilho.
 * A "fase" vai de 0 (dia) a 1 (noite); valores intermediários são a transição suave.
 */
final class Tema {

    static final String AUTO = "auto", DIA = "dia", NOITE = "noite";

    // Papel
    static final int DIA_FUNDO = 0xFFF2EFE6;
    static final int DIA_TINTA = 0xFF1E1E1C;
    static final int DIA_SUAVE = 0xFF77736A;
    // Âmbar
    static final int NOITE_FUNDO = 0xFF000000;
    static final int NOITE_TINTA = 0xFFE8962E;
    static final int NOITE_SUAVE = 0xFF7A4E18;

    static final float BRILHO_NOITE = 0.02f;

    private Tema() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("cabeceira", Context.MODE_PRIVATE);
    }

    static String modo(Context c) {
        return prefs(c).getString("modo", AUTO);
    }

    /** Alterna auto → dia → noite → auto e devolve o novo modo. */
    static String proximoModo(Context c) {
        String atual = modo(c);
        String novo = AUTO.equals(atual) ? DIA : DIA.equals(atual) ? NOITE : AUTO;
        prefs(c).edit().putString("modo", novo).apply();
        return novo;
    }

    /** 0 = dia, 1 = noite; sobe ao longo de `transicao` minutos após o início da noite e desce após o fim. */
    static float fase(Context c, Config cfg) {
        String m = modo(c);
        if (DIA.equals(m)) return 0f;
        if (NOITE.equals(m)) return 1f;
        float agora = minutoAtual();
        float t = Math.max(1, cfg.transicao);
        float desdeFim = desde(agora, cfg.noiteFim);
        if (desdeFim < t) return 1f - desdeFim / t;
        int duracao = (cfg.noiteFim - cfg.noiteInicio + 1440) % 1440;
        float desdeInicio = desde(agora, cfg.noiteInicio);
        if (desdeInicio < duracao) return Math.min(1f, desdeInicio / t);
        return 0f;
    }

    /** Primeira hora (configurável) depois do fim da noite: previsão do dia em destaque. */
    static boolean acordando(Context c, Config cfg) {
        return AUTO.equals(modo(c)) && desde(minutoAtual(), cfg.noiteFim) < cfg.acordar;
    }

    static boolean ehNoite(Context c) {
        return fase(c, Config.get(c)) >= 0.5f;
    }

    static int mistura(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 0xFF) + ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t));
        int g = (int) (((a >> 8) & 0xFF) + ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t));
        int bl = (int) ((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * t));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** De dia segue o brilho do sistema; à noite cai até 2%, gradualmente durante a transição. */
    static void aplicarBrilho(Window w, float fase, Context c) {
        WindowManager.LayoutParams lp = w.getAttributes();
        if (fase <= 0.001f) {
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        } else {
            float sistema = Settings.System.getInt(c.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 128) / 255f;
            lp.screenBrightness = sistema + (BRILHO_NOITE - sistema) * Math.min(1f, fase);
        }
        w.setAttributes(lp);
    }

    private static float minutoAtual() {
        Calendar a = Calendar.getInstance();
        return a.get(Calendar.HOUR_OF_DAY) * 60 + a.get(Calendar.MINUTE) + a.get(Calendar.SECOND) / 60f;
    }

    private static float desde(float agora, int marco) {
        float d = agora - marco;
        return d < 0 ? d + 1440 : d;
    }
}
