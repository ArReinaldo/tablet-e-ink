package br.tabletink.cabeceira;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela do relógio, usada pelo launcher (com botões) e pelo protetor de tela (sem).
 * Dia ("tela papel"): data no canto, hora grande, tempo numa linha e, no rodapé, os apps e o
 * cartão "Continuar lendo".
 * Ao acordar: previsão do dia em destaque, com a faixa de horas. Noite: só hora e data, em âmbar.
 * Retrato e paisagem têm a mesma tela, rearranjada para a largura.
 */
class ClockPanel extends FrameLayout {

    interface Ouvinte {
        void aoMudarFase(float fase);
        void aoMudarCarregando(boolean carregando);
    }

    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final long MINUTO = 60_000L;
    private static final int CELULAS = 5;
    /** Espaço fino no fim do texto em itálico: a última letra passa da largura medida e era cortada. */
    private static final String FOLGA = " ";

    private final boolean comBotoes;
    private final SimpleDateFormat fmtHora = new SimpleDateFormat("HH:mm", PT_BR);
    private final SimpleDateFormat fmtData = new SimpleDateFormat("EEEE, d 'de' MMMM", PT_BR);

    // Estrutura (o palco inteiro é deslocado de tempos em tempos para proteger a tela)
    private final FrameLayout palco;
    private final LinearLayout pilha, principal, rodape, rodapeConteudo, grade;
    private final View fio;

    // Dia e noite
    private final TextView hora, data, dataTopo, tempoTexto, tempoAtualizado;
    private final LinearLayout tempoLinha;
    private final IconeView tempoIcone;
    private final LinearLayout livroNoite;
    private final IconeView livroNoiteIcone;
    private final TextView livroNoiteTexto, apps;
    private final LinearLayout canto;
    private final IconeView bateriaIcone;
    private final TextView bateriaTexto;

    // Cartão do livro
    private final LinearLayout cartao;
    private final IconeView cartaoIcone;
    private final TextView cartaoRotulo, cartaoTitulo;

    // Ao acordar
    private final FrameLayout manha;
    private final LinearLayout manhaColuna, manhaTopo, manhaClima, manhaTextos, manhaFaixa;
    private final TextView manhaHora, manhaSaudacao, manhaTemp, manhaResumo, manhaConselho,
            manhaDetalhes, manhaVolta;
    private final IconeView manhaIcone;
    private final View fioFaixaTopo, fioFaixaBase;
    private final LinearLayout[] celula = new LinearLayout[CELULAS];
    private final TextView[] celulaHora = new TextView[CELULAS], celulaTemp = new TextView[CELULAS];
    private final IconeView[] celulaIcone = new IconeView[CELULAS];
    private final View[] divisoria = new View[CELULAS - 1];

    private Ouvinte ouvinte;
    private Boolean horizontal;
    private Boolean carregando;
    private Intent ultimaBateria;
    private float fase = -1f;
    private List<ResolveInfo> listaApps = new ArrayList<>();
    private long appsLidosEm;
    private int bateriaPct = -1;
    private boolean bateriaPausa;
    private ValueAnimator animacao;
    private boolean livroNaNoite;
    private int corFundo = Tema.DIA_FUNDO, corTinta = Tema.DIA_TINTA, corSuave = Tema.DIA_SUAVE;

    // Dados (carregados em segundo plano)
    private JSONObject tempoCache;
    private boolean tempoOk;
    private long tempoTentativa;

    ClockPanel(Context c, boolean comBotoes) {
        super(c);
        this.comBotoes = comBotoes;
        Typeface serifaLeve = Typeface.create(Typeface.SERIF, 300, false);
        Typeface italico = Typeface.create(Typeface.SERIF, Typeface.ITALIC);

        palco = new FrameLayout(c);
        addView(palco, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        pilha = coluna(c, Gravity.START);
        principal = coluna(c, Gravity.CENTER);
        rodape = coluna(c, Gravity.START);
        rodapeConteudo = new LinearLayout(c);
        grade = coluna(c, Gravity.START);
        fio = new View(c);

        hora = texto(150, serifaLeve);
        hora.setIncludeFontPadding(false);
        data = texto(24, Typeface.SERIF);
        dataTopo = texto(18, Typeface.SERIF);

        tempoIcone = new IconeView(c, IconeView.Tipo.SOL, 1f);
        tempoTexto = texto(16, Typeface.SERIF);
        tempoLinha = new LinearLayout(c);
        tempoLinha.setOrientation(LinearLayout.HORIZONTAL);
        tempoLinha.setGravity(Gravity.CENTER_VERTICAL);
        tempoLinha.addView(tempoIcone, new LinearLayout.LayoutParams(dp(31), dp(31)));
        tempoLinha.addView(tempoTexto, margemEsquerda(dp(11)));
        tempoAtualizado = texto(12, Typeface.SERIF);
        tempoAtualizado.setLetterSpacing(0.04f);

        // À noite o botão do livro aparece por cima, embaixo e centralizado, sem mexer na hora
        livroNoiteIcone = new IconeView(c, IconeView.Tipo.LIVRO, 1f);
        livroNoiteTexto = texto(20, Typeface.SERIF);
        livroNoiteTexto.setText("Meus livros");
        livroNoite = new LinearLayout(c);
        livroNoite.setOrientation(LinearLayout.HORIZONTAL);
        livroNoite.setGravity(Gravity.CENTER_VERTICAL);
        livroNoite.setPadding(dp(29), dp(13), dp(29), dp(13));
        livroNoite.addView(livroNoiteIcone, new LinearLayout.LayoutParams(dp(23), dp(17)));
        livroNoite.addView(livroNoiteTexto, margemEsquerda(dp(11)));

        // De dia: cartão "Continuar lendo"
        cartaoIcone = new IconeView(c, IconeView.Tipo.LIVRO, 1f);
        cartaoRotulo = texto(10, Typeface.SERIF);
        cartaoRotulo.setText("CONTINUAR LENDO");
        cartaoRotulo.setLetterSpacing(0.22f);
        cartaoRotulo.setGravity(Gravity.START);
        cartaoRotulo.setSingleLine(true);
        cartaoTitulo = texto(20, italico);
        cartaoTitulo.setGravity(Gravity.START);
        cartaoTitulo.setSingleLine(true);
        cartaoTitulo.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout cartaoTextos = coluna(c, Gravity.START);
        cartaoTextos.addView(cartaoRotulo, margemTopo(0));
        cartaoTextos.addView(cartaoTitulo, margemTopo(dp(3)));
        cartao = new LinearLayout(c);
        cartao.setOrientation(LinearLayout.HORIZONTAL);
        cartao.setGravity(Gravity.CENTER_VERTICAL);
        cartao.setPadding(dp(18), dp(12), dp(18), dp(12));
        cartao.setMinimumHeight(dp(83));
        cartao.addView(cartaoIcone, new LinearLayout.LayoutParams(dp(19), dp(15)));
        cartao.addView(cartaoTextos, margemEsquerda(dp(13)));

        apps = texto(14, Typeface.SERIF);
        apps.setText("apps");
        apps.setLetterSpacing(0.15f);
        apps.setPadding(dp(20), dp(10), dp(20), dp(10));

        bateriaIcone = new IconeView(c, IconeView.Tipo.BATERIA, 1f);
        bateriaTexto = texto(13, Typeface.SERIF);
        bateriaTexto.setLetterSpacing(0.06f);
        canto = new LinearLayout(c);
        canto.setOrientation(LinearLayout.HORIZONTAL);
        canto.setGravity(Gravity.CENTER_VERTICAL);
        canto.addView(bateriaIcone, new LinearLayout.LayoutParams(dp(23), dp(12)));
        canto.addView(bateriaTexto, margemEsquerda(dp(7)));

        // Ao acordar (as peças são arrumadas em montar(), conforme a orientação)
        manhaHora = texto(80, serifaLeve);
        manhaHora.setIncludeFontPadding(false);
        manhaSaudacao = texto(17, Typeface.SERIF);
        manhaTopo = new LinearLayout(c);
        manhaIcone = new IconeView(c, IconeView.Tipo.SOL, 1.4f);
        manhaTemp = texto(110, serifaLeve);
        manhaTemp.setIncludeFontPadding(false);
        manhaResumo = texto(24, italico);
        manhaConselho = texto(17, Typeface.SERIF);
        manhaDetalhes = texto(13, Typeface.SERIF);
        for (TextView t : new TextView[]{manhaSaudacao, manhaResumo, manhaConselho, manhaDetalhes}) {
            t.setGravity(Gravity.START);
        }
        manhaTextos = coluna(c, Gravity.START);
        manhaTextos.addView(manhaResumo, margemTopo(0));
        manhaTextos.addView(manhaConselho, margemTopo(dp(9)));
        manhaTextos.addView(manhaDetalhes, margemTopo(dp(7)));
        manhaClima = new LinearLayout(c);
        manhaClima.setOrientation(LinearLayout.HORIZONTAL);
        manhaClima.setGravity(Gravity.CENTER_VERTICAL);
        manhaClima.setPadding(dp(12), 0, 0, 0);

        LinearLayout celulas = new LinearLayout(c);
        celulas.setOrientation(LinearLayout.HORIZONTAL);
        for (int k = 0; k < CELULAS; k++) {
            if (k > 0) {
                divisoria[k - 1] = new View(c);
                celulas.addView(divisoria[k - 1], new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            celulaHora[k] = texto(11, Typeface.SERIF);
            celulaIcone[k] = new IconeView(c, IconeView.Tipo.SOL, 1f);
            celulaTemp[k] = texto(16, Typeface.SERIF);
            celula[k] = coluna(c, Gravity.CENTER);
            celula[k].addView(celulaHora[k]);
            LinearLayout.LayoutParams lpIc = new LinearLayout.LayoutParams(dp(20), dp(20));
            lpIc.topMargin = dp(6);
            celula[k].addView(celulaIcone[k], lpIc);
            celula[k].addView(celulaTemp[k], margemTopo(dp(6)));
            celulas.addView(celula[k], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        fioFaixaTopo = new View(c);
        fioFaixaBase = new View(c);
        manhaFaixa = coluna(c, Gravity.START);
        manhaFaixa.addView(fioFaixaTopo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        manhaFaixa.addView(celulas, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));
        manhaFaixa.addView(fioFaixaBase, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        manhaColuna = coluna(c, Gravity.START);
        manhaVolta = texto(11, italico);
        manha = new FrameLayout(c);
        LayoutParams lpVolta = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        lpVolta.rightMargin = dp(48);
        lpVolta.bottomMargin = dp(34);
        manha.addView(manhaVolta, lpVolta);

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
        livroNoite.setOnClickListener(l);
        cartao.setOnClickListener(l);
    }

    void aoTocarApps(View.OnClickListener l) { if (comBotoes) apps.setOnClickListener(l); }

    void aoSegurarHora(View.OnLongClickListener l) {
        hora.setOnLongClickListener(l);
        manhaHora.setOnLongClickListener(l);
    }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        boolean hz = w > h;
        if (horizontal == null || hz != horizontal) post(() -> montar(hz));
    }

    private void montar(boolean hz) {
        horizontal = hz;
        for (View v : new View[]{hora, data, dataTopo, tempoLinha, tempoAtualizado, livroNoite, cartao, grade,
                apps, canto, pilha, principal, rodape, rodapeConteudo, fio, manha, manhaColuna, manhaHora, manhaSaudacao, manhaTopo, manhaIcone,
                manhaTemp, manhaTextos, manhaClima, manhaFaixa}) {
            soltar(v);
        }
        int margem = dp(hz ? 48 : 36);
        dataTopo.setTextSize(TypedValue.COMPLEX_UNIT_SP, hz ? 18 : 16);

        // Dia: hora grande no centro e o tempo numa linha logo abaixo; à noite, hora e data
        principal.addView(hora);
        principal.addView(data, margemTopo(dp(7)));
        principal.addView(tempoLinha, margemTopo(-dp(hz ? 16 : 12)));
        principal.addView(tempoAtualizado, margemTopo(dp(5)));
        // A hora fica centralizada no espaço acima do rodapé; sem rodapé (noite), na tela toda
        pilha.addView(principal, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rodape.setPadding(margem, 0, margem, dp(30));
        rodape.addView(fio, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        LinearLayout.LayoutParams lpConteudo = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpConteudo.topMargin = dp(22);
        rodape.addView(rodapeConteudo, lpConteudo);
        // Paisagem: apps à esquerda e o cartão à direita; retrato: apps em cima e o cartão embaixo
        rodapeConteudo.setOrientation(hz ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        rodapeConteudo.setGravity(hz ? Gravity.CENTER_VERTICAL : Gravity.END);
        if (hz) {
            rodapeConteudo.addView(grade, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams lpCartao = new LinearLayout.LayoutParams(dp(267), ViewGroup.LayoutParams.WRAP_CONTENT);
            lpCartao.leftMargin = dp(28);
            rodapeConteudo.addView(cartao, lpCartao);
        } else {
            rodapeConteudo.addView(grade, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lpCartao = new LinearLayout.LayoutParams(dp(267), ViewGroup.LayoutParams.WRAP_CONTENT);
            lpCartao.topMargin = dp(20);
            rodapeConteudo.addView(cartao, lpCartao);
        }
        pilha.addView(rodape, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        palco.addView(pilha, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        montarGrade();

        LayoutParams lpData = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        lpData.leftMargin = margem;
        lpData.topMargin = dp(32);
        palco.addView(dataTopo, lpData);

        // Ao acordar: em paisagem, hora e saudação numa linha e os textos ao lado da temperatura;
        // em retrato, empilhados
        manhaTopo.setOrientation(hz ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        manhaTopo.addView(manhaHora, margemTopo(0));
        manhaTopo.addView(manhaSaudacao, hz ? margemEsquerda(dp(22)) : margemTopo(dp(10)));
        manhaClima.addView(manhaIcone, new LinearLayout.LayoutParams(dp(82), dp(82)));
        manhaClima.addView(manhaTemp, margemEsquerda(dp(38)));
        manhaColuna.setPadding(margem, hz ? dp(26) : 0, margem, 0);
        manhaColuna.addView(manhaTopo);
        manhaColuna.addView(manhaClima, margemTopo(dp(hz ? 14 : 44)));
        if (hz) {
            manhaClima.addView(manhaTextos, margemEsquerda(dp(28)));
        } else {
            LinearLayout.LayoutParams lpTx = margemTopo(dp(20));
            lpTx.leftMargin = dp(12);
            manhaColuna.addView(manhaTextos, lpTx);
        }
        LinearLayout.LayoutParams lpFaixa = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpFaixa.topMargin = dp(hz ? 28 : 44);
        manhaColuna.addView(manhaFaixa, lpFaixa);
        LayoutParams lpColuna = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                hz ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        if (!hz) lpColuna.bottomMargin = dp(40);
        manha.addView(manhaColuna, 0, lpColuna);
        LayoutParams lpVolta = (LayoutParams) manhaVolta.getLayoutParams();
        lpVolta.rightMargin = margem;
        manhaVolta.setLayoutParams(lpVolta);
        LayoutParams lpApps = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        lpApps.leftMargin = margem - dp(20);
        lpApps.bottomMargin = dp(22);
        manha.addView(apps, lpApps);
        palco.addView(manha, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Margens da bateria: ajustadas em aplicarVisibilidade() (de noite fica onde sempre esteve)
        palco.addView(canto, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END));

        LayoutParams lpNoite = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lpNoite.bottomMargin = dp(hz ? 60 : 110);
        palco.addView(livroNoite, lpNoite);

        renderTempo();
        aplicarVisibilidade();
    }

    // ------------------------------------------------------------------ ciclo

    /** Chamado a cada minuto e ao voltar para a tela. */
    void atualizar() {
        Context c = getContext();
        Config cfg = Config.get(c);
        Date agora = new Date();
        String h = fmtHora.format(agora), d = fmtData.format(agora);
        hora.setText(h);
        manhaHora.setText(h);
        data.setText(d);
        dataTopo.setText(maiuscula(d));
        manhaSaudacao.setText(saudacao() + " · " + d);
        cartaoTitulo.setText((cfg.livro.isEmpty() ? "Meus livros" : cfg.livro) + FOLGA);
        int volta = (cfg.noiteFim + cfg.acordar) % 1440;
        manhaVolta.setText(String.format(PT_BR, "volta ao layout normal às %02d:%02d", volta / 60, volta % 60) + FOLGA);

        if (comBotoes && System.currentTimeMillis() - appsLidosEm > 5 * MINUTO) lerApps();

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
        for (TextView t : new TextView[]{hora, manhaHora, manhaTemp, manhaResumo, livroNoiteTexto, cartaoTitulo}) {
            t.setTextColor(corTinta);
        }
        for (TextView t : new TextView[]{data, dataTopo, tempoAtualizado, apps, bateriaTexto, cartaoRotulo,
                manhaSaudacao, manhaConselho, manhaDetalhes, manhaVolta}) {
            t.setTextColor(corSuave);
        }
        for (TextView t : celulaHora) t.setTextColor(corSuave);
        colorirGrade();
        for (IconeView v : new IconeView[]{tempoIcone, livroNoiteIcone, cartaoIcone, manhaIcone}) {
            v.setCores(corTinta, corFundo);
        }
        for (IconeView v : celulaIcone) v.setCores(corTinta, corFundo);
        bateriaIcone.setCores(corSuave, corFundo);
        livroNoite.setBackground(borda(corTinta));
        cartao.setBackground(borda(Tema.mistura(corTinta, corFundo, 0.25f)));
        // Fios: a cor suave bem clara, sem usar alpha (que é da transição)
        int corFio = Tema.mistura(corSuave, corFundo, 0.6f);
        fio.setBackgroundColor(corFio);
        fioFaixaTopo.setBackgroundColor(corFio);
        fioFaixaBase.setBackgroundColor(corFio);
        for (View v : divisoria) v.setBackgroundColor(Tema.mistura(corSuave, corFundo, 0.75f));
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
        boolean telaManha = acordar && temTempo;

        mostrar(manha, telaManha);
        mostrar(principal, !telaManha);
        mostrar(data, noite);
        mostrar(dataTopo, !noite && !telaManha);
        mostrar(tempoLinha, !noite && temTempo && !acordar);
        mostrar(tempoAtualizado, !noite && temTempo && tempoAntigo(cfg) && !telaManha);
        mostrar(livroNoite, comBotoes && noite && livroNaNoite);
        // O protetor de tela (sem botões) não tem rodapé: só hora, data e tempo
        mostrar(rodape, comBotoes && !noite && !telaManha);
        mostrar(grade, !listaApps.isEmpty());
        // Ao acordar não há rodapé: a palavra "apps" abre a lista completa
        mostrar(apps, comBotoes && telaManha);

        // A noite fica como sempre foi: hora e data centralizadas, bateria no canto de antes
        int cima = noite ? 0 : dp(hz ? 40 : 60);
        int lado = hz ? 0 : dp(12);
        if (principal.getPaddingTop() != cima || principal.getPaddingLeft() != lado) {
            principal.setPadding(lado, cima, lado, 0);
        }
        hora.setTextSize(TypedValue.COMPLEX_UNIT_SP, noite ? (hz ? 170 : 150) : (hz ? 190 : 165));
        LayoutParams lpCanto = (LayoutParams) canto.getLayoutParams();
        int topo = noite ? dp(hz ? 17 : 20) : dp(40);
        int direita = noite ? dp(22) : dp(hz ? 48 : 36);
        if (lpCanto.topMargin != topo || lpCanto.rightMargin != direita) {
            lpCanto.topMargin = topo;
            lpCanto.rightMargin = direita;
            canto.setLayoutParams(lpCanto);
        }

        // Na transição, o que é só do dia some/aparece aos poucos (a hora fica sempre)
        float a = noite ? 1f : 1f - Math.max(0f, fase);
        for (View v : new View[]{dataTopo, tempoLinha, tempoAtualizado, rodape, apps, manhaSaudacao,
                manhaClima, manhaTextos, manhaFaixa, manhaVolta}) {
            v.setAlpha(a);
        }
        canto.setAlpha(noite ? 0.7f : 1f);
        textoBateria();
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

        // 27°   mín 23° · máx 32°   ·   chuva à tarde
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String agora = r.agora + "°";
        sb.append(agora);
        sb.setSpan(new ForegroundColorSpan(corTinta), 0, agora.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(1.35f), 0, agora.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("   mín ").append(String.valueOf(r.min)).append("° · máx ")
                .append(String.valueOf(r.max)).append("°");
        if (r.resto != null) {
            sb.append("   ·   ");
            int ini = sb.length();
            sb.append(r.resto);
            sb.setSpan(new StyleSpan(Typeface.ITALIC), ini, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tempoTexto.setTextColor(corSuave);
        tempoTexto.setText(sb);

        // Ao acordar
        manhaIcone.setTipo(r.iconeDia);
        manhaTemp.setText(agora);
        manhaResumo.setText(r.dia == null ? "" : maiuscula(r.dia) + FOLGA);
        String conselho = conselho(r);
        manhaConselho.setText(conselho);
        mostrar(manhaConselho, !conselho.isEmpty());
        manhaDetalhes.setText("mín " + r.min + "° · máx " + r.max + "°"
                + (r.umidade >= 0 ? " · umidade " + r.umidade + "%" : ""));
        for (int k = 0; k < CELULAS; k++) {
            boolean tem = k < r.horas.size();
            mostrar(celula[k], tem);
            if (k > 0) mostrar(divisoria[k - 1], tem);
            if (!tem) continue;
            Tempo.Hora x = r.horas.get(k);
            celulaHora[k].setText(x.hora + "h");
            celulaIcone[k].setTipo(x.icone);
            SpannableStringBuilder t = new SpannableStringBuilder(x.temp + "°");
            t.setSpan(new ForegroundColorSpan(corTinta), 0, t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (x.chuva >= 30) {
                int ini = t.length();
                t.append(" · ").append(String.valueOf(x.chuva)).append("%");
                t.setSpan(new ForegroundColorSpan(corSuave), ini, t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                t.setSpan(new RelativeSizeSpan(0.62f), ini, t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            celulaTemp[k].setText(t);
        }

        if (tempoCache != null) {
            tempoAtualizado.setText("atualizado às "
                    + fmtHora.format(new Date(tempoCache.optLong("buscadoEm"))));
        }
    }

    /** Uma frase curta para a manhã: guarda-chuva, calor ou frio; vazio quando não há nada a dizer. */
    private static String conselho(Tempo.Resumo r) {
        String d = r.dia == null ? "" : r.dia;
        if (d.startsWith("chuva") || d.startsWith("garoa") || d.startsWith("trovoadas") || r.chuva >= 60) {
            return "Leve o guarda-chuva.";
        }
        if (r.max >= 31) return "Dia quente: beba bastante água.";
        if (r.min <= 14) return "Manhã fria: leve um agasalho.";
        return "";
    }

    // ------------------------------------------------------------------ apps

    /** Apps instalados que aparecem no launcher (menos este), em ordem alfabética. */
    private void lerApps() {
        appsLidosEm = System.currentTimeMillis();
        android.content.pm.PackageManager pm = getContext().getPackageManager();
        Intent consulta = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> novos = new ArrayList<>();
        for (ResolveInfo r : pm.queryIntentActivities(consulta, 0)) {
            if (!r.activityInfo.packageName.equals(getContext().getPackageName())) novos.add(r);
        }
        Collator col = Collator.getInstance(PT_BR);
        novos.sort((a, z) -> col.compare(r(pm, a), r(pm, z)));
        if (mesmos(novos, listaApps)) return;
        listaApps = novos;
        montarGrade();
        aplicarVisibilidade();
    }

    private static String r(android.content.pm.PackageManager pm, ResolveInfo i) {
        return i.loadLabel(pm).toString();
    }

    private static boolean mesmos(List<ResolveInfo> a, List<ResolveInfo> b) {
        if (a.size() != b.size()) return false;
        for (int k = 0; k < a.size(); k++) {
            if (!a.get(k).activityInfo.name.equals(b.get(k).activityInfo.name)) return false;
        }
        return true;
    }

    /** Os apps em 3 colunas, só o nome; um toque abre. */
    private void montarGrade() {
        grade.removeAllViews();
        if (horizontal == null) return;
        android.content.pm.PackageManager pm = getContext().getPackageManager();
        int colunas = 3;
        LinearLayout linhaAtual = null;
        for (int k = 0; k < listaApps.size(); k++) {
            if (k % colunas == 0) {
                linhaAtual = new LinearLayout(getContext());
                linhaAtual.setOrientation(LinearLayout.HORIZONTAL);
                grade.addView(linhaAtual, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            ResolveInfo app = listaApps.get(k);
            TextView t = texto(14, Typeface.SERIF);
            t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setText(r(pm, app));
            t.setPadding(0, dp(5), dp(10), dp(5));
            t.setOnClickListener(v -> abrir(app));
            linhaAtual.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        // Completa a última linha para as colunas ficarem alinhadas
        while (linhaAtual != null && linhaAtual.getChildCount() < colunas) {
            linhaAtual.addView(new View(getContext()), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        colorirGrade();
    }

    private void colorirGrade() {
        for (int i = 0; i < grade.getChildCount(); i++) {
            LinearLayout l = (LinearLayout) grade.getChildAt(i);
            for (int j = 0; j < l.getChildCount(); j++) {
                if (l.getChildAt(j) instanceof TextView) ((TextView) l.getChildAt(j)).setTextColor(corSuave);
            }
        }
    }

    private void abrir(ResolveInfo app) {
        getContext().startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(app.activityInfo.packageName, app.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    // ------------------------------------------------------------------ bateria

    private void atualizarBateria(Intent i) {
        ultimaBateria = i;
        int nivel = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int escala = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = nivel < 0 ? -1 : Math.round(nivel * 100f / escala);
        boolean c = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        boolean pausa = c && cargaPausada();
        bateriaPct = pct;
        bateriaPausa = pausa;
        textoBateria();
        bateriaIcone.setCarga(!c ? IconeView.CARGA_NADA : pausa ? IconeView.CARGA_PAUSA : IconeView.CARGA_RAIO);
        if (carregando == null || carregando != c) {
            carregando = c;
            if (ouvinte != null) ouvinte.aoMudarCarregando(c);
        }
        ControleTomada.aoMudarBateria(pct, c);
    }

    /** "82% · carga em pausa" de dia; à noite, como sempre foi, só a porcentagem. */
    private void textoBateria() {
        boolean dia = fase < 0.999f;
        bateriaTexto.setText(bateriaPct < 0 ? "" : bateriaPct + "%" + (bateriaPausa && dia ? " · carga em pausa" : ""));
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

    private static String saudacao() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 5 && h < 12 ? "Bom dia" : h >= 12 && h < 18 ? "Boa tarde" : "Boa noite";
    }

    private static String maiuscula(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(PT_BR) + s.substring(1);
    }

    private static void soltar(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    private static void mostrar(View v, boolean sim) {
        int vis = sim ? VISIBLE : GONE;
        if (v.getVisibility() != vis) v.setVisibility(vis);
    }

    private GradientDrawable borda(int cor) {
        GradientDrawable b = new GradientDrawable();
        b.setCornerRadius(dp(4));
        b.setStroke(dp(1), cor);
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

    private static LinearLayout.LayoutParams margemEsquerda(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = px;
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
