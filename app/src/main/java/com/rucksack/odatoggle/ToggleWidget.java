package com.rucksack.odatoggle;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.Toast;

/** Home-Screen-Widget: ein Tipp schaltet zwischen EXKLUSIV und MIX um. */
public class ToggleWidget extends AppWidgetProvider {

    static final String ACTION_TOGGLE = "com.rucksack.odatoggle.ACTION_TOGGLE";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        render(context, manager, ids, SoundToggle.readState(context));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            doToggle(context);
        } else {
            super.onReceive(context, intent);
        }
    }

    /** Wird vom Widget und vom App-Icon benutzt. */
    static void doToggle(Context context) {
        int state;
        try {
            state = SoundToggle.toggle(context);
            String msg = context.getString(state == SoundToggle.STATE_EXCLUSIVE
                    ? R.string.toast_exclusive : R.string.toast_mix);
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            state = SoundToggle.STATE_UNKNOWN;
            String detail = t instanceof ToggleException
                    ? ((ToggleException) t).message(context)
                    : String.valueOf(t.getMessage());
            Toast.makeText(context,
                    context.getString(R.string.toast_error, detail),
                    Toast.LENGTH_LONG).show();
        }
        refreshAll(context, state);
    }

    static void refreshAll(Context context, int state) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ToggleWidget.class));
        if (ids != null && ids.length > 0) {
            render(context, manager, ids, state);
        }
    }

    private static void render(Context context, AppWidgetManager manager, int[] ids, int state) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_toggle);

        int text;
        int bg;
        switch (state) {
            case SoundToggle.STATE_EXCLUSIVE:
                text = R.string.state_exclusive;
                bg = R.drawable.bg_exclusive;
                break;
            case SoundToggle.STATE_MIX:
                text = R.string.state_mix;
                bg = R.drawable.bg_mix;
                break;
            default:
                text = R.string.state_unknown;
                bg = R.drawable.bg_unknown;
        }
        views.setTextViewText(R.id.widget_state, context.getString(text));
        views.setInt(R.id.widget_root, "setBackgroundResource", bg);

        Intent i = new Intent(context, ToggleWidget.class).setAction(ACTION_TOGGLE);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widget_root, pi);

        manager.updateAppWidget(ids, views);
    }
}
