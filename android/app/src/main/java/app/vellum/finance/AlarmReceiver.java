package app.vellum.finance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Notifier.show(c, i.getStringExtra("id"), i.getStringExtra("title"), i.getStringExtra("body"), i.getStringExtra("route"));
    }
}
