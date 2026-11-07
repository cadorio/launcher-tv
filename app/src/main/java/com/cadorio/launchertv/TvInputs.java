package com.cadorio.launchertv;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * As entradas físicas da TV (HDMI, AV…), para trocar de fonte direto da tela inicial.
 * Abrir uma entrada entrega a troca ao app de TV do fabricante (na Sony, o "TV"/com.sony.dtv.tvx).
 */
final class TvInputs {
    /** Uma entrada que o usuário pode abrir. */
    static final class Input {
        final String id;
        final String label;
        final Drawable icon;

        Input(String id, String label, Drawable icon) {
            this.id = id;
            this.label = label;
            this.icon = icon;
        }

        Intent intent() {
            Intent intent = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        }
    }

    private TvInputs() {
    }

    static List<Input> list(Context context) {
        List<Input> out = new ArrayList<>();
        TvInputManager manager;
        try {
            manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        } catch (RuntimeException e) {
            return out;
        }
        if (manager == null) return out; // celular, tablet ou TV sem o framework de entradas

        List<TvInputInfo> all;
        try {
            all = manager.getTvInputList();
        } catch (RuntimeException e) {
            return out;
        }

        // Um aparelho ligado por HDMI-CEC aparece como "filho" da porta HDMI; mostra só o aparelho.
        Set<String> parentsWithChildren = new HashSet<>();
        for (TvInputInfo info : all) {
            if (info.getParentId() != null) parentsWithChildren.add(info.getParentId());
        }

        for (TvInputInfo info : all) {
            if (!info.isPassthroughInput()) continue; // sintonizador: o app de TV já aparece na lista de apps
            if (parentsWithChildren.contains(info.getId())) continue;
            if (Build.VERSION.SDK_INT >= 23 && info.isHidden(context)) continue;

            CharSequence label = null;
            if (Build.VERSION.SDK_INT >= 24) label = info.loadCustomLabel(context);
            if (TextUtils.isEmpty(label)) label = info.loadLabel(context);
            if (TextUtils.isEmpty(label)) continue;

            Drawable icon = null;
            try {
                icon = info.loadIcon(context);
            } catch (RuntimeException ignored) {
                // sem ícone: o launcher usa um genérico
            }
            out.add(new Input(info.getId(), label.toString(), icon));
        }
        return out;
    }
}
