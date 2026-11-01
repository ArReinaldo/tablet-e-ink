package br.tabletink.cabeceira;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

/** Launcher: relógio + livro atual / "Continuar lendo" (KOReader) + lista de apps. */
public class MainActivity extends Activity {

    static final String KOREADER = "org.koreader.launcher";

    private ClockPanel painel;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        painel = new ClockPanel(this, true);
        setContentView(painel);

        painel.setOuvinte(new ClockPanel.Ouvinte() {
            @Override public void aoMudarFase(float fase) {
                Tema.aplicarBrilho(getWindow(), fase, MainActivity.this);
            }

            @Override public void aoMudarCarregando(boolean carregando) {
                if (carregando) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });

        painel.aoTocarLer(v -> abrirKoreader());
        painel.aoTocarApps(v -> startActivity(new Intent(this, AppsActivity.class)));
        painel.aoSegurarHora(v -> {
            String modo = Tema.proximoModo(this);
            Toast.makeText(this, "Modo: " + modo, Toast.LENGTH_SHORT).show();
            painel.atualizar();
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        esconderBarras();
        painel.atualizar();
    }

    @Override
    public void onWindowFocusChanged(boolean foco) {
        super.onWindowFocusChanged(foco);
        if (foco) esconderBarras();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Tela inicial: voltar não sai daqui.
    }

    private void abrirKoreader() {
        Intent i = getPackageManager().getLaunchIntentForPackage(KOREADER);
        if (i == null) {
            Toast.makeText(this, "KOReader não está instalado", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(i);
    }

    private void esconderBarras() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c == null) return;
        c.hide(WindowInsets.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }
}
