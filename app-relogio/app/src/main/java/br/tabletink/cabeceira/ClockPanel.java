package br.tabletink.cabeceira;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela do relógio, usada pelo launcher (com botões) e pelo protetor de tela (sem).
 * Dia: hora, data, tempo, livro atual e lembretes. Noite: só hora e data, em âmbar.
 * Ao acordar: previsão do dia em destaque. Monta um layout para retrato e outro para paisagem.
 */
class ClockPanel extends FrameLayout {

    interface Ouvinte {
        void aoMudarFase(float fase);
        void aoMudarCarregando(boolean carregando);
    }

    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final long MINUTO = 60_000L;

    private final boolean comBotoes;
    private final SimpleDateFormat fmtHora = new SimpleDateFormat("HH:mm", PT_BR);
    private final SimpleDateFormat fmtData = new SimpleDateFormat("EEEE, d 'de' MMMM", PT_BR);

    // Estrutura (o palco inteiro é deslocado de tempos em tempos para proteger a tela)
    private final FrameLayout palco;
    private final LinearLayout linha, principal, lateral, rodape;
    private final View fioVertical, fioRodape;

    // Conteúdo
    private final TextView hora, data, tempoTexto, tempoResumo, tempoAtualizado;
    private final LinearLayout tempoLinha;
    private final IconeView tempoIcone;
    private final LinearLayout destaque, destaqueTextos;
    private final IconeView destaqueIcone;
    private final TextView destaqueResumo, destaqueTemps, destaqueChuva;
    private final LinearLayout livro, livroNoite;
    private final IconeView livroIcone, livroNoiteIcone;
    private final TextView livroTexto, livroNoiteTexto, apps, lembretesLinha;
    private final LinearLayout lembretes, canto;
    private final IconeView bateriaIcone;
    private final TextView bateriaTexto;

    private Ouvinte ouvinte;
    private Boolean horizontal;
    private Boolean carregando;
    private Intent ultimaBateria;
    private float fase = -1f;
    private ValueAnimator animacao;
    private boolean livroNaNoite;
    private int corFundo = Tema.DIA_FUNDO, corTinta = Tema.DIA_TINTA, corSuave = Tema.DIA_SUAVE;

    // Dados (carregados em segundo plano)
    private JSONObject tempoCache;
    private boolean tempoOk;
    private long tempoTentativa, agendaTentativa;
    private List<String> rotulos = Collections.emptyList();

    ClockPanel(Context c, boolean comBotoes) {
        super(c);
        this.comBotoes = comBotoes;
        Typeface serifaLeve = Typeface.create(Typeface.SERIF, 300, false);
        Typeface italico = Typeface.create(Typeface.SERIF, Typeface.ITALIC);

        palco = new FrameLayout(c);
        addView(palco, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        linha = new LinearLayout(c);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        principal = coluna(c, Gravity.CENTER);
        lateral = coluna(c, Gravity.START | Gravity.CENTER_VERTICAL);
        rodape = coluna(c, Gravity.CENTER_HORIZONTAL);
        fioVertical = new View(c);
        fioRodape = new View(c);

        hora = texto(150, serifaLeve);
        hora.setIncludeFontPadding(false);
        data = texto(24, Typeface.SERIF);

        tempoIcone = new IconeView(c, IconeView.Tipo.SOL, 1f);
        tempoTexto = texto(19, Typeface.SERIF);
        tempoLinha = new LinearLayout(c);
        tempoLinha.setOrientation(LinearLayout.HORIZONTAL);
        tempoLinha.setGravity(Gravity.CENTER_VERTICAL);
        tempoLinha.addView(tempoIcone, new LinearLayout.LayoutParams(dp(31), dp(31)));
        LinearLayout.LayoutParams lpTxt = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpTxt.leftMargin = dp(11);
        tempoLinha.addView(tempoTexto, lpTxt);
        tempoResumo = texto(18, italico);
        tempoAtualizado = texto(12, Typeface.SERIF);
        tempoAtualizado.setLetterSpacing(0.04f);

        destaqueIcone = new IconeView(c, IconeView.Tipo.SOL, 1.3f);
        destaqueResumo = texto(33, italico);
        destaqueTemps = texto(19, Typeface.SERIF);
        destaqueChuva = texto(16, Typeface.SERIF);
        for (TextView t : new TextView[]{tempoResumo, destaqueResumo, destaqueTemps, destaqueChuva}) {
            t.setGravity(Gravity.START);
        }
        destaqueTextos = coluna(c, Gravity.START);
        destaqueTextos.addView(destaqueResumo, margemTopo(0));
        destaqueTextos.addView(destaqueTemps, margemTopo(dp(5)));
        destaqueTextos.addView(destaqueChuva, margemTopo(dp(3)));
        destaque = new LinearLayout(c);

        livroIcone = new IconeView(c, IconeView.Tipo.LIVRO, 1f);
        livroTexto = texto(20, Typeface.SERIF);
        livro = botaoLivro(c, livroIcone, livroTexto);
        // À noite o botão aparece por cima, embaixo e centralizado, sem mexer na hora
        livroNoiteIcone = new IconeView(c, IconeView.Tipo.LIVRO, 1f);
        livroNoiteTexto = texto(20, Typeface.SERIF);
        livroNoite = botaoLivro(c, livroNoiteIcone, livroNoiteTexto);

        apps = texto(14, Typeface.SERIF);
        apps.setText("apps");
        apps.setLetterSpacing(0.15f);
        apps.setPadding(dp(20), dp(10), dp(20), dp(10));

        lembretes = coluna(c, Gravity.CENTER_HORIZONTAL);
        lembretesLinha = texto(15, Typeface.SERIF);

        bateriaIcone = new IconeView(c, IconeView.Tipo.BATERIA, 1f);
        bateriaTexto = texto(13, Typeface.SERIF);
        bateriaTexto.setLetterSpacing(0.06f);
        canto = new LinearLayout(c);
        canto.setOrientation(LinearLayout.HORIZONTAL);
        canto.setGravity(Gravity.CENTER_VERTICAL);
        canto.addView(bateriaIcone, new LinearLayout.LayoutParams(dp(23), dp(12)));
        LinearLayout.LayoutParams lpBat = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpBat.leftMargin = dp(7);
        canto.addView(bateriaTexto, lpBat);

        if (comBotoes) {
            // À noite só há hora e data; um toque mostra o livro por 10 s
            setOnClickListener(v -> toqueNoite());
            hora.setOnClickListener(v -> toqueNoite());
            data.setOnClickListener(v -> toqueNoite());
        }
    }

    void setOuvinte(Ouvinte o) { ouvinte = o; }

    void aoTocarLer(View.OnClickListener l) {
        if (!comBotoes) return;
        livro.setOnClickListener(l);
        livroNoite.setOnClickListener(l);
    }

    void aoTocarApps(View.OnClickListener l) { if (comBotoes) apps.setOnClickListener(l); }

    void aoSegurarHora(View.OnLongClickListener l) { hora.setOnLongClickListener(l); }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        boolean hz = w > h;
        if (horizontal == null || hz != horizontal) post(() -> montar(hz, w));
    }

    private void montar(boolean hz, int largura) {
        horizontal = hz;
        for (View v : new View[]{hora, data, tempoLinha, tempoResumo, tempoAtualizado, destaque, livro, livroNoite,
                apps, lembretes, lembretesLinha, canto, principal, lateral, rodape, linha, fioVertical, fioRodape}) {
            soltar(v);
        }
        destaque.removeAllViews();
        destaque.addView(destaqueIcone);
        destaque.addView(destaqueTextos);

        principal.addView(hora);
        principal.addView(data, margemTopo(dp(7)));

        if (!hz) {
            principal.addView(tempoLinha, margemTopo(dp(32)));
            principal.addView(tempoAtualizado, margemTopo(dp(5)));
            destaque.setOrientation(LinearLayout.HORIZONTAL);
            destaque.setGravity(Gravity.CENTER_VERTICAL);
            destaqueIcone.setLayoutParams(new LinearLayout.LayoutParams(dp(88), dp(88)));
            LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpT.leftMargin = dp(24);
            destaqueTextos.setLayoutParams(lpT);
            principal.addView(destaque, margemTopo(dp(43)));
            principal.addView(livro, margemTopo(dp(43)));
            principal.addView(apps, margemTopo(dp(12)));
            palco.addView(principal, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            rodape.addView(fioRodape, new LinearLayout.LayoutParams(dp(37), 1));
            rodape.addView(lembretes, margemTopo(dp(13)));
            LayoutParams lpR = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM);
            lpR.bottomMargin = dp(47);
            palco.addView(rodape, lpR);
            lembretes.setGravity(Gravity.CENTER_HORIZONTAL);
        } else {
            principal.setPadding(0, 0, 0, 0);
            linha.addView(principal, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 61f));
            LinearLayout.LayoutParams lpFio = new LinearLayout.LayoutParams(1, dp(227));
            lpFio.gravity = Gravity.CENTER_VERTICAL;
            linha.addView(fioVertical, lpFio);

            lateral.setPadding(dp(43), 0, dp(20), 0);
            lateral.addView(tempoLinha);
            LinearLayout.LayoutParams lpRes = margemTopo(dp(4));
            lpRes.leftMargin = dp(42);
            lateral.addView(tempoResumo, lpRes);
            lateral.addView(tempoAtualizado, margemTopo(dp(5)));
            destaque.setOrientation(LinearLayout.VERTICAL);
            destaque.setGravity(Gravity.START);
            destaqueIcone.setLayoutParams(new LinearLayout.LayoutParams(dp(75), dp(75)));
            destaqueTextos.setLayoutParams(margemTopo(dp(9)));
            lateral.addView(destaque);
            lateral.addView(livro, margemTopo(dp(29)));
            lateral.addView(lembretes, margemTopo(dp(29)));
            lembretes.setGravity(Gravity.START);
            linha.addView(lateral, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 39f));
            palco.addView(linha, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            int esquerda = largura * 61 / 100;
            LayoutParams lpApps = new LayoutParams(esquerda, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
            lpApps.bottomMargin = dp(14);
            palco.addView(apps, lpApps);
            LayoutParams lpLinha = new LayoutParams(esquerda, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
            lpLinha.bottomMargin = dp(58);
            palco.addView(lembretesLinha, lpLinha);
        }

        LayoutParams lpCanto = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        lpCanto.topMargin = dp(hz ? 17 : 20);
        lpCanto.rightMargin = dp(22);
        palco.addView(canto, lpCanto);

        LayoutParams lpNoite = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lpNoite.bottomMargin = dp(hz ? 60 : 110);
        palco.addView(livroNoite, lpNoite);

        renderTempo();
        renderLembretes();
        aplicarVisibilidade();
    }

    // ------------------------------------------------------------------ ciclo

    /** Chamado a cada minuto e ao voltar para a tela. */
    void atualizar() {
        Context c = getContext();
        Config cfg = Config.get(c);
        Date agora = new Date();
        hora.setText(fmtHora.format(agora));
        data.setText(fmtData.format(agora));

        // A pausa da carga não gera broadcast de bateria; reavalia a corrente a cada minuto
        if (ultimaBateria != null) atualizarBateria(ultimaBateria);

        // Proteção da tela: 9 posições, troca a cada 3 minutos
        int i = (int) ((agora.getTime() / (3 * MINUTO)) % 9);
        palco.setTranslationX((i % 3 - 1) * dp(8));
        palco.setTranslationY((i / 3 - 1) * dp(8));

        irParaFase(Tema.fase(c, cfg));
        carregarDados(cfg);
        renderTempo();
        aplicarVisibilidade();
    }

    private void irParaFase(float alvo) {
        if (fase < 0f) {
            mostrarFase(alvo);
            return;
        }
        if (Math.abs(alvo - fase) < 0.001f) return;
        if (animacao != null) animacao.cancel();
        animacao = ValueAnimator.ofFloat(fase, alvo);
        animacao.setDuration(2500);
        animacao.addUpdateListener(a -> mostrarFase((float) a.getAnimatedValue()));
        animacao.start();
    }

    private void mostrarFase(float f) {
        fase = f;
        corFundo = Tema.mistura(Tema.DIA_FUNDO, Tema.NOITE_FUNDO, f);
        corTinta = Tema.mistura(Tema.DIA_TINTA, Tema.NOITE_TINTA, f);
        corSuave = Tema.mistura(Tema.DIA_SUAVE, Tema.NOITE_SUAVE, f);
        setBackgroundColor(corFundo);
        hora.setTextColor(corTinta);
        for (TextView t : new TextView[]{data, tempoResumo, tempoAtualizado, destaqueTemps, destaqueChuva,
                apps, lembretesLinha, bateriaTexto}) {
            t.setTextColor(corSuave);
        }
        destaqueResumo.setTextColor(corTinta);
        livroTexto.setTextColor(corTinta);
        livroNoiteTexto.setTextColor(corTinta);
        for (int k = 0; k < lembretes.getChildCount(); k++) {
            ((TextView) lembretes.getChildAt(k)).setTextColor(corSuave);
        }
        tempoIcone.setCores(corTinta, corFundo);
        destaqueIcone.setCores(corTinta, corFundo);
        livroIcone.setCores(corTinta, corFundo);
        livroNoiteIcone.setCores(corTinta, corFundo);
        bateriaIcone.setCores(corSuave, corFundo);
        for (LinearLayout b : new LinearLayout[]{livro, livroNoite}) {
            GradientDrawable borda = new GradientDrawable();
            borda.setCornerRadius(dp(4));
            borda.setStroke(dp(1), corTinta);
            b.setBackground(borda);
        }
        fioVertical.setBackgroundColor(corSuave);
        fioVertical.setAlpha(0.45f);
        fioRodape.setBackgroundColor(corSuave);
        renderTempo();
        aplicarVisibilidade();
        if (ouvinte != null) ouvinte.aoMudarFase(f);
    }

    private void aplicarVisibilidade() {
        if (horizontal == null || fase < 0f) return;
        Config cfg = Config.get(getContext());
        boolean hz = horizontal;
        boolean noite = fase >= 0.999f;
        boolean acordar = !noite && Tema.acordando(getContext(), cfg);
        boolean temTempo = resumoTempo() != null;
        boolean temLembretes = !rotulos.isEmpty();

        mostrar(tempoLinha, !noite && temTempo && !acordar);
        mostrar(tempoResumo, hz && !noite && temTempo && !acordar);
        mostrar(tempoAtualizado, !noite && temTempo && tempoAntigo(cfg));
        mostrar(destaque, !noite && temTempo && acordar);
        mostrar(livro, comBotoes && !noite);
        mostrar(livroNoite, comBotoes && noite && livroNaNoite);
        mostrar(apps, comBotoes && !noite);
        mostrar(lembretes, !noite && temLembretes && !(hz && acordar));
        mostrar(lembretesLinha, hz && !noite && temLembretes && acordar);
        mostrar(fioRodape, !hz && !noite && temLembretes);
        mostrar(fioVertical, hz && !noite);
        mostrar(lateral, hz && !noite);
        // Em retrato, só reserva o espaço do rodapé quando há lembretes; senão centraliza de verdade
        int baixo = !hz && !noite && temLembretes ? dp(127) : 0;
        if (!hz && principal.getPaddingBottom() != baixo) principal.setPadding(dp(12), 0, dp(12), baixo);
        hora.setTextSize(TypedValue.COMPLEX_UNIT_SP, hz && noite ? 170 : 150);

        // Na transição, o que é só do dia some/aparece aos poucos
        float a = noite ? 1f : 1f - Math.max(0f, fase);
        for (View v : new View[]{tempoLinha, tempoResumo, tempoAtualizado, destaque, apps, lembretes,
                lembretesLinha, fioRodape}) {
            v.setAlpha(a);
        }
        livro.setAlpha(a);
        fioVertical.setAlpha(0.45f * a);
        canto.setAlpha(noite ? 0.7f : 1f);
    }

    private void toqueNoite() {
        if (!comBotoes || fase < 0.999f) return;
        livroNaNoite = true;
        aplicarVisibilidade();
        removeCallbacks(esconderLivro);
        postDelayed(esconderLivro, 10_000);
    }

    private final Runnable esconderLivro = () -> {
        livroNaNoite = false;
        aplicarVisibilidade();
    };

    // ------------------------------------------------------------------ dados

    private void carregarDados(Config cfg) {
        Context c = getContext().getApplicationContext();
        long agora = System.currentTimeMillis();
        boolean primeira = tempoTentativa == 0;
        long espera = (tempoOk ? cfg.tempoIntervalo : 5) * MINUTO;
        if (agora - tempoTentativa >= espera) {
            tempoTentativa = agora;
            IO.execute(() -> {
                if (primeira) entregarTempo(Tempo.carregar(c, cfg), false);
                boolean ok = Tempo.buscar(c, cfg);
                entregarTempo(Tempo.carregar(c, cfg), ok);
            });
        }
        if (!cfg.ical.isEmpty() && agora - agendaTentativa >= cfg.tempoIntervalo * MINUTO) {
            agendaTentativa = agora;
            IO.execute(() -> Agenda.buscar(c, cfg));
        }
        IO.execute(() -> {
            List<String> r = Agenda.rotulos(c, cfg, cfg.lembretesQuantos);
            post(() -> {
                rotulos = r;
                renderLembretes();
                aplicarVisibilidade();
            });
        });
    }

    private void entregarTempo(JSONObject cache, boolean ok) {
        post(() -> {
            if (ok) tempoOk = true;
            if (cache != null) tempoCache = cache;
            renderTempo();
            aplicarVisibilidade();
        });
    }

    private Tempo.Resumo resumoTempo() {
        if (tempoCache == null) return null;
        return Tempo.resumir(tempoCache, LocalDateTime.now(), Config.get(getContext()).noiteFim);
    }

    private boolean tempoAntigo(Config cfg) {
        if (tempoCache == null) return false;
        long buscado = tempoCache.optLong("buscadoEm");
        return System.currentTimeMillis() - buscado > (cfg.tempoIntervalo + 15) * MINUTO;
    }

    private void renderTempo() {
        Tempo.Resumo r = resumoTempo();
        if (r == null || horizontal == null) return;
        tempoIcone.setTipo(r.icone);
        destaqueIcone.setTipo(r.icone);

        SpannableStringBuilder sb = new SpannableStringBuilder();
        String agora = r.agora + "°";
        sb.append(agora);
        sb.setSpan(new ForegroundColorSpan(corTinta), 0, agora.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("  ·  ").append(String.valueOf(r.min)).append("° / ").append(String.valueOf(r.max)).append("°");
        if (!horizontal && r.resto != null) sb.append("  ·  ").append(r.resto);
        tempoTexto.setTextColor(corSuave);
        tempoTexto.setText(sb);
        tempoResumo.setText(r.resto == null ? "" : r.resto);

        destaqueResumo.setText(r.dia == null ? "" : r.dia);
        destaqueTemps.setText(r.agora + "° agora · mín " + r.min + "° · máx " + r.max + "°");
        destaqueChuva.setText("chance de chuva " + r.chuva + "%");

        if (tempoCache != null) {
            tempoAtualizado.setText("atualizado às "
                    + fmtHora.format(new Date(tempoCache.optLong("buscadoEm"))));
        }
    }

    private void renderLembretes() {
        lembretes.removeAllViews();
        boolean hz = Boolean.TRUE.equals(horizontal);
        for (int k = 0; k < rotulos.size(); k++) {
            TextView t = texto(hz ? 15 : 16, Typeface.SERIF);
            t.setGravity(hz ? Gravity.START : Gravity.CENTER_HORIZONTAL);
            t.setText(rotulos.get(k));
            t.setTextColor(corSuave);
            lembretes.addView(t, margemTopo(k == 0 ? 0 : dp(7)));
        }
        lembretesLinha.setText(String.join("   ·   ", rotulos.subList(0, Math.min(2, rotulos.size()))));
    }

    // ------------------------------------------------------------------ bateria

    private void atualizarBateria(Intent i) {
        ultimaBateria = i;
        int nivel = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int escala = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = nivel < 0 ? -1 : Math.round(nivel * 100f / escala);
        boolean c = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        bateriaTexto.setText(pct < 0 ? "" : pct + "%");
        bateriaIcone.setCarga(!c ? IconeView.CARGA_NADA
                : cargaPausada() ? IconeView.CARGA_PAUSA : IconeView.CARGA_RAIO);
        if (carregando == null || carregando != c) {
            carregando = c;
            if (ouvinte != null) ouvinte.aoMudarCarregando(c);
        }
        ControleTomada.aoMudarBateria(pct, c);
    }

    /**
     * Com o cabo ligado, o limitador de carga (service.d/limite-carga.sh) corta a corrente de
     * entrada e o Android continua dizendo "carregando"; o sinal real é a corrente não positiva.
     */
    private boolean cargaPausada() {
        BatteryManager bm = getContext().getSystemService(BatteryManager.class);
        if (bm == null) return false;
        int corrente = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        return corrente != Integer.MIN_VALUE && corrente <= 0;
    }

    private final BroadcastReceiver relogio = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { atualizar(); }
    };

    private final BroadcastReceiver receptorBateria = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { atualizarBateria(i); }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getContext().registerReceiver(relogio, f);
        Intent b = getContext().registerReceiver(receptorBateria,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) atualizarBateria(b);
        atualizar();
    }

    @Override
    protected void onDetachedFromWindow() {
        getContext().unregisterReceiver(relogio);
        getContext().unregisterReceiver(receptorBateria);
        if (animacao != null) animacao.cancel();
        removeCallbacks(esconderLivro);
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------------ utilidades

    private static void soltar(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    private static void mostrar(View v, boolean sim) {
        int vis = sim ? VISIBLE : GONE;
        if (v.getVisibility() != vis) v.setVisibility(vis);
    }

    private LinearLayout botaoLivro(Context c, IconeView icone, TextView texto) {
        texto.setText("Meus livros");
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(29), dp(13), dp(29), dp(13));
        b.addView(icone, new LinearLayout.LayoutParams(dp(23), dp(17)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(11);
        b.addView(texto, lp);
        return b;
    }

    private LinearLayout coluna(Context c, int gravidade) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(gravidade);
        return l;
    }

    private static LinearLayout.LayoutParams margemTopo(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px;
        return lp;
    }

    private TextView texto(float sp, Typeface tf) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(tf);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
