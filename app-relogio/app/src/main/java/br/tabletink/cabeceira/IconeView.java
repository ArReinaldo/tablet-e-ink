package br.tabletink.cabeceira;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Ícones em traço fino, desenhados à mão no estilo de ilustração de livro. */
final class IconeView extends View {

    enum Tipo { SOL, LUA, SOL_NUVEM, LUA_NUVEM, NUVEM, GAROA, CHUVA, TROVOADA, NEBLINA, LIVRO, BATERIA }

    static final int CARGA_NADA = 0, CARGA_RAIO = 1, CARGA_PAUSA = 2;

    private final Paint traco = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint preenchimento = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float tracoPx;
    private Tipo tipo;
    private int carga = CARGA_NADA;

    IconeView(Context c, Tipo tipo, float tracoDp) {
        super(c);
        this.tipo = tipo;
        this.tracoPx = tracoDp * c.getResources().getDisplayMetrics().density;
        traco.setStyle(Paint.Style.STROKE);
        traco.setStrokeCap(Paint.Cap.ROUND);
        traco.setStrokeJoin(Paint.Join.ROUND);
        preenchimento.setStyle(Paint.Style.FILL);
    }

    void setTipo(Tipo t) {
        if (t != tipo) { tipo = t; invalidate(); }
    }

    void setCarga(int c) {
        if (c != carga) { carga = c; invalidate(); }
    }

    /** cor = traço; fundo = cor que a nuvem usa para cobrir o sol/lua atrás dela. */
    void setCores(int cor, int fundo) {
        traco.setColor(cor);
        preenchimento.setColor(fundo);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float vbW = 48, vbH = 48;
        if (tipo == Tipo.LIVRO) { vbW = 34; vbH = 26; }
        if (tipo == Tipo.BATERIA) { vbW = 34; vbH = 18; }
        float s = Math.min(getWidth() / vbW, getHeight() / vbH);
        if (s <= 0) return;
        cv.save();
        cv.translate((getWidth() - vbW * s) / 2f, (getHeight() - vbH * s) / 2f);
        cv.scale(s, s);
        traco.setStrokeWidth(tracoPx / s);

        switch (tipo) {
            case SOL: sol(cv, 24, 24, 8); break;
            case LUA: lua(cv, 24, 24, 12); break;
            case SOL_NUVEM: sol(cv, 19, 17, 6); nuvem(cv, 0, 0); break;
            case LUA_NUVEM: lua(cv, 19, 17, 8); nuvem(cv, 0, 0); break;
            case NUVEM: nuvem(cv, -3, -4); break;
            case GAROA:
                nuvem(cv, -3, -6);
                linha(cv, 19, 38, 18.4f, 40); linha(cv, 26, 38, 25.4f, 40); linha(cv, 33, 38, 32.4f, 40);
                linha(cv, 22, 43, 21.4f, 45); linha(cv, 29, 43, 28.4f, 45);
                break;
            case CHUVA:
                nuvem(cv, -3, -6);
                linha(cv, 19, 38, 17, 44); linha(cv, 26, 38, 24, 44); linha(cv, 33, 38, 31, 44);
                break;
            case TROVOADA: {
                nuvem(cv, -3, -6);
                Path p = new Path();
                p.moveTo(27, 37); p.rLineTo(-4, 5.5f); p.rLineTo(5, 0); p.rLineTo(-4, 5.5f);
                cv.drawPath(p, traco);
                break;
            }
            case NEBLINA:
                nuvem(cv, -3, -9);
                linha(cv, 10, 37, 36, 37); linha(cv, 14, 42, 40, 42);
                break;
            case LIVRO: livro(cv); break;
            case BATERIA: bateria(cv); break;
        }
        cv.restore();
    }

    private void linha(Canvas cv, float x1, float y1, float x2, float y2) {
        cv.drawLine(x1, y1, x2, y2, traco);
    }

    private void sol(Canvas cv, float cx, float cy, float r) {
        cv.drawCircle(cx, cy, r, traco);
        float a = r + 3, b = r + 6;
        for (int i = 0; i < 8; i++) {
            double ang = Math.PI / 4 * i;
            float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
            cv.drawLine(cx + a * cs, cy + a * sn, cx + b * cs, cy + b * sn, traco);
        }
    }

    private void lua(Canvas cv, float cx, float cy, float r) {
        Path cheia = new Path();
        cheia.addCircle(cx, cy, r, Path.Direction.CW);
        Path corte = new Path();
        corte.addCircle(cx + r * 0.5f, cy - r * 0.42f, r * 0.85f, Path.Direction.CW);
        cheia.op(corte, Path.Op.DIFFERENCE);
        cv.drawPath(cheia, traco);
    }

    /** Nuvem como união de três círculos e uma base reta; preenchida para cobrir o que está atrás. */
    private void nuvem(Canvas cv, float dx, float dy) {
        Path n = new Path();
        n.addCircle(23 + dx, 33 + dy, 7, Path.Direction.CW);
        Path topo = new Path();
        topo.addCircle(29.5f + dx, 27.5f + dy, 9, Path.Direction.CW);
        Path direita = new Path();
        direita.addCircle(37 + dx, 34 + dy, 6, Path.Direction.CW);
        Path base = new Path();
        base.addRect(23 + dx, 33 + dy, 37 + dx, 40 + dy, Path.Direction.CW);
        n.op(topo, Path.Op.UNION);
        n.op(direita, Path.Op.UNION);
        n.op(base, Path.Op.UNION);
        cv.drawPath(n, preenchimento);
        cv.drawPath(n, traco);
    }

    private void livro(Canvas cv) {
        Path p = new Path();
        p.moveTo(17, 5);
        p.rCubicTo(-4, -3, -9, -3.5f, -15, -2.5f);
        p.rLineTo(0, 19);
        p.rCubicTo(6, -1, 11, -0.5f, 15, 2.5f);
        p.rCubicTo(4, -3, 9, -3.5f, 15, -2.5f);
        p.rLineTo(0, -19);
        p.rCubicTo(-6, -1, -11, -0.5f, -15, 2.5f);
        p.close();
        cv.drawPath(p, traco);
        linha(cv, 17, 5, 17, 24);
    }

    private void bateria(Canvas cv) {
        cv.drawRoundRect(1, 1, 29, 17, 3, 3, traco);
        linha(cv, 31.5f, 6.5f, 31.5f, 11.5f);
        if (carga == CARGA_RAIO) {
            Path p = new Path();
            p.moveTo(16.5f, 4); p.rLineTo(-4, 5.5f); p.rLineTo(5, 0); p.rLineTo(-4, 5);
            cv.drawPath(p, traco);
        } else if (carga == CARGA_PAUSA) {
            linha(cv, 12, 5.5f, 12, 12.5f);
            linha(cv, 17, 5.5f, 17, 12.5f);
        }
    }
}
