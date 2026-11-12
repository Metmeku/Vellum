package app.vellum.finance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms are cleared on reboot or app update; re-arm the saved reminder list. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) { Notifier.arm(c); }
}
