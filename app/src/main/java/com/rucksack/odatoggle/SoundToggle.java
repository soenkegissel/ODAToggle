package com.rucksack.odatoggle;

import android.content.Context;
import android.content.SharedPreferences;

import com.fujitsu_ten.displayaudio.whitelist.common.ProcessControl;

/**
 * Schaltet die Audio-Rechte von OpenDroidAuto (Prozess "it.smg.hu") um:
 *
 *  EXKLUSIV: Sound Out an, Sound Interrupt aus, Interrupt Mute aus
 *            -> Medien auf beiden Lautsprechern, Radio pausiert.
 *  MIX:      die Werte, die vor dem Umschalten auf EXKLUSIV gesetzt waren
 *            (Standard: Sound Out an, Sound Interrupt an, Mute aus)
 *            -> laeuft parallel zum Radio, aber nur links.
 */
final class SoundToggle {

    static final String TARGET_PROCESS = "it.smg.hu";

    static final int STATE_UNKNOWN = -1;
    static final int STATE_MIX = 0;
    static final int STATE_EXCLUSIVE = 1;

    private static final String PREFS = "sound_toggle";
    private static final String KEY_MIX_OUT = "mix_sound_out";
    private static final String KEY_MIX_INT = "mix_sound_interrupt";
    private static final String KEY_MIX_MUTE = "mix_sound_interrupt_mute";
    private static final String KEY_LAST_STATE = "last_state";

    private SoundToggle() { }

    /** Aktuellen Zustand aus dem Radio lesen. */
    static int readState(Context context) {
        try {
            ProcessControl pc = requireTarget(WhiteList.load());
            int state = isExclusive(pc) ? STATE_EXCLUSIVE : STATE_MIX;
            prefs(context).edit().putInt(KEY_LAST_STATE, state).commit();
            return state;
        } catch (Throwable t) {
            return prefs(context).getInt(KEY_LAST_STATE, STATE_UNKNOWN);
        }
    }

    /** Umschalten. Gibt den neuen Zustand zurueck, wirft bei Fehlern. */
    static int toggle(Context context) throws Exception {
        WhiteList wl = WhiteList.load();
        ProcessControl pc = requireTarget(wl);
        SharedPreferences p = prefs(context);
        int newState;

        if (isExclusive(pc)) {
            pc.soundOut = p.getInt(KEY_MIX_OUT, 1);
            pc.soundInterrupt = p.getInt(KEY_MIX_INT, 1);
            pc.soundInterruptMute = p.getInt(KEY_MIX_MUTE, 0);
            newState = STATE_MIX;
        } else {
            p.edit()
                    .putInt(KEY_MIX_OUT, pc.soundOut)
                    .putInt(KEY_MIX_INT, pc.soundInterrupt)
                    .putInt(KEY_MIX_MUTE, pc.soundInterruptMute)
                    .commit();
            pc.soundOut = 1;
            pc.soundInterrupt = 0;
            pc.soundInterruptMute = 0;
            newState = STATE_EXCLUSIVE;
        }
        pc.result = 0;

        wl.save(context);
        p.edit().putInt(KEY_LAST_STATE, newState).commit();
        return newState;
    }

    private static boolean isExclusive(ProcessControl pc) {
        return pc.soundOut == 1 && pc.soundInterrupt == 0 && pc.soundInterruptMute == 0;
    }

    private static ProcessControl requireTarget(WhiteList wl) {
        ProcessControl pc = wl.find(TARGET_PROCESS);
        if (pc == null) {
            throw new ToggleException(R.string.error_not_in_whitelist, TARGET_PROCESS);
        }
        return pc;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
