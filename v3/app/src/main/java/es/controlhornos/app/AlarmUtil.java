package es.controlhornos.app;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.util.Map;

class AlarmUtil {

    /** true si se pudo programar como alarma EXACTA; false si se usó el respaldo inexacto. */
    static boolean schedule(Context c, Piece p, String ovenName){
        if(p.end<=System.currentTimeMillis()) return false; // ya ha pasado: no se reprograma (req. 10)
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Intent i=new Intent(c,AlarmReceiver.class);
        i.putExtra("piece_id",p.id);
        i.putExtra("oven_id",p.ovenId);
        i.putExtra("oven_name",ovenName);
        i.putExtra("description",p.description);
        // request code = id de la pieza: una pieza = una única alarma pendiente (evita duplicados, req. 3).
        PendingIntent pi=PendingIntent.getBroadcast(c,(int)p.id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        boolean exact=true;
        try{
            // setAlarmClock es lo más fiable que ofrece Android: el sistema lo trata como la alarma del usuario
            // (sobrevive al ahorro de batería y a que el fabricante mate la app en segundo plano).
            PendingIntent showPi=PendingIntent.getActivity(c,(int)p.id,new Intent(c,MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(p.end,showPi),pi);
        } catch(Exception e){
            // Respaldo si el móvil no permite alarmas de reloj: alarma exacta y, si tampoco, aproximada.
            try{
                if(Build.VERSION.SDK_INT>=31){
                    if(am.canScheduleExactAlarms()){
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,p.end,pi);
                    } else {
                        exact=false;
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,p.end,pi);
                    }
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,p.end,pi);
                }
            } catch(SecurityException e2){
                exact=false;
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,p.end,pi);
            }
        }
        return exact;
    }

    static void cancel(Context c,long id){
        Intent i=new Intent(c,AlarmReceiver.class);
        PendingIntent pi=PendingIntent.getBroadcast(c,(int)id,i,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        if(pi!=null){
            ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pi);
            pi.cancel();
        }
    }

    /** Reprograma todas las alarmas pendientes. Se llama al arrancar el teléfono y al abrir la app (req. 3). */
    static void rescheduleAll(Context c){
        Db db=new Db(c);
        Map<Long,String> names=new java.util.HashMap<>();
        for(Oven o:db.ovens()) names.put(o.id,o.name);
        for(Piece p:db.all()){
            String name=names.get(p.ovenId);
            if(name==null) name="Horno";
            schedule(c,p,name);
        }
        db.close();
    }
}
