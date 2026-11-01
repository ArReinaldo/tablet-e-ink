package br.tabletink.cabeceira;

import android.service.dreams.DreamService;

/** Protetor de tela: o mesmo relógio (com tempo e lembretes), sem botões. Um toque acorda o tablet. */
public class ClockDream extends DreamService {

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);

        ClockPanel painel = new ClockPanel(this, false);
        painel.setOuvinte(new ClockPanel.Ouvinte() {
            @Override public void aoMudarFase(float fase) {
                setScreenBright(fase < 0.5f);
                Tema.aplicarBrilho(getWindow(), fase, ClockDream.this);
            }

            @Override public void aoMudarCarregando(boolean carregando) {}
        });
        setContentView(painel);
    }
}
