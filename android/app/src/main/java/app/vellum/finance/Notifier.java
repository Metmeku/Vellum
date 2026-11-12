package app.vellum.finance;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

/** Schedules the reminders the web app computes. Survives reboots via BootReceiver. */
public final class Notifier {
    static final String CHANNEL = "vellum_reminders";
    private static final String PREFS = "vellum_notifications";

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Daily nudges, gold, iqub, bills, insurance and stock reminders");
            c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    /** Replace all scheduled reminders with this list: [{id, at (epoch ms), title, body, route}]. */
    static void schedule(Context c, String json) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try {
            JSONArray old = new JSONArray(p.getString("list", "[]"));
            for (int i = 0; i < old.length(); i++) am.cancel(pending(c, old.getJSONObject(i)));
        } catch (Exception ignored) {}
        p.edit().putString("list", json == null ? "[]" : json).apply();
        arm(c);
    }

    static void arm(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try {
            JSONArray list = new JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "[]"));
            long now = System.currentTimeMillis();
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                long at = o.getLong("at");
                if (at > now) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c, o));
            }
        } catch (Exception ignored) {}
    }

    private static PendingIntent pending(Context c, JSONObject o) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.putExtra("id", o.optString("id"));
        i.putExtra("title", o.optString("title", "Vellum"));
        i.putExtra("body", o.optString("body"));
        i.putExtra("route", o.optString("route", "#home"));
        return PendingIntent.getBroadcast(c, o.optString("id").hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void show(Context c, String id, String title, String body, String route) {
        ensureChannel(c);
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra("route", route);
        PendingIntent tap = PendingIntent.getActivity(c, (id + "_tap").hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_vellum)
                .setColor(0xFFA9780F)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(tap)
                .setAutoCancel(true);
        try { NotificationManagerCompat.from(c).notify(id.hashCode(), b.build()); }
        catch (SecurityException ignored) { /* permission not granted */ }
    }
}
