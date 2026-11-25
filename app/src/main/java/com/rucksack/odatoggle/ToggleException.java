package com.rucksack.odatoggle;

import android.content.Context;

/** Fehler mit uebersetzbarer Meldung (String-Ressource statt festem Text). */
final class ToggleException extends RuntimeException {

    private final int resId;
    private final String arg;

    ToggleException(int resId) {
        this(resId, null);
    }

    ToggleException(int resId, String arg) {
        super(arg);
        this.resId = resId;
        this.arg = arg;
    }

    String message(Context context) {
        return arg == null ? context.getString(resId) : context.getString(resId, arg);
    }
}
