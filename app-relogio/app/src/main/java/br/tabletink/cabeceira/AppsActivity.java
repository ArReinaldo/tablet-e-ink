package br.tabletink.cabeceira;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

/** Lista de apps só em texto, no mesmo papel do relógio. */
public class AppsActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        Intent consulta = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> encontrados = getPackageManager().queryIntentActivities(consulta, 0);
        List<ResolveInfo> apps = new ArrayList<>();
        for (ResolveInfo r : encontrados) {
            if (!r.activityInfo.packageName.equals(getPackageName())) apps.add(r);
        }
        Collator col = Collator.getInstance();
        apps.sort((a, z) -> col.compare(rotulo(a), rotulo(z)));

        boolean noite = Tema.ehNoite(this);
        int fundo = noite ? Tema.NOITE_FUNDO : Tema.DIA_FUNDO;
        int tinta = noite ? Tema.NOITE_TINTA : Tema.DIA_TINTA;
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);

        ListView lista = new ListView(this);
        lista.setBackgroundColor(fundo);
        lista.setDivider(null);
        lista.setPadding(pad, pad, pad, pad);
        lista.setClipToPadding(false);
        lista.setAdapter(new ArrayAdapter<ResolveInfo>(this, 0, apps) {
            @Override
            public View getView(int pos, View v, ViewGroup pai) {
                TextView t = (TextView) v;
                if (t == null) {
                    t = new TextView(getContext());
                    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                    t.setTypeface(Typeface.SERIF);
                    t.setTextColor(tinta);
                    t.setPadding(pad, pad / 2, pad, pad / 2);
                }
                t.setText(rotulo(getItem(pos)));
                return t;
            }
        });
        lista.setOnItemClickListener((p, v, pos, id) -> {
            ResolveInfo r = apps.get(pos);
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(new ComponentName(r.activityInfo.packageName, r.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });

        setContentView(lista);
        Tema.aplicarBrilho(getWindow(), noite ? 1f : 0f, this);
    }

    private String rotulo(ResolveInfo r) {
        return r.loadLabel(getPackageManager()).toString();
    }
}
