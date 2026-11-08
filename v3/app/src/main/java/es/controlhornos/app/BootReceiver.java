package es.controlhornos.app;

import android.content.*;

/** Reprograma todas las alarmas pendientes tras un reinicio del teléfono o una actualización de la app (req. 3). */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i){
        AlarmUtil.rescheduleAll(c);
    }
}
