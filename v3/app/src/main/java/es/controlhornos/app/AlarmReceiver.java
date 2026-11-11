package es.controlhornos.app;

import android.app.*;
import android.content.*;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    static final String CHANNEL="fin_ciclo_hornos";
    /** Acción que llega al descartar la notificación: calla el sonido repetido. */
    static final String STOP_RING="es.controlhornos.app.STOP_RING";

    static boolean vibrate(Context c){
        return !"0".equals(c.getSharedPreferences("ajustes",0).getString("vibrar","1"));
    }
    static boolean repeat(Context c){
        return !"0".equals(c.getSharedPreferences("ajustes",0).getString("repetir","1"));
    }

    /**
     * Canal propio por sonido y por vibración: Android no permite cambiar el sonido ni la vibración
     * de un canal ya creado, así que cada combinación usa el suyo.
     */
    static String channelId(Context c){
        String s=c.getSharedPreferences("ajustes",0).getString("alarma_uri",null);
        String vib=vibrate(c)?"v":"nv";
        return s==null?CHANNEL+"_"+vib:CHANNEL+"_"+vib+"_"+Integer.toHexString(s.hashCode());
    }
    /** Sonido elegido: null (preferencia ausente) = alarma del sistema; vacío = sin sonido. */
    static Uri sound(Context c){
        String s=c.getSharedPreferences("ajustes",0).getString("alarma_uri",null);
        if(s==null){
            Uri u=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            return u!=null?u:RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        if(s.isEmpty()) return null;
        return Uri.parse(s);
    }

    @Override public void onReceive(Context context, Intent intent){
        if(intent!=null && STOP_RING.equals(intent.getAction())){
            RingService.stop(context);
            return;
        }
        // Una alarma nunca debe tumbar la app: si algo falla, ese aviso se pierde y ya está.
        try{ avisar(context,intent); }catch(Exception ignored){}
    }

    private void avisar(Context context,Intent intent){
        long id=intent.getLongExtra("piece_id",0);
        String ovenName=intent.getStringExtra("oven_name");
        if(ovenName==null||ovenName.trim().isEmpty()) ovenName="Horno";
        String desc=intent.getStringExtra("description");

        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(channelId(context),"Fin de ciclo",NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Avisos cuando termina una tirada");
            ch.enableVibration(vibrate(context));
            Uri sound=sound(context);
            android.media.AudioAttributes attrs=new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            ch.setSound(sound,attrs); // null = sin sonido
            nm.createNotificationChannel(ch);
        }

        Intent open=new Intent(context,MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(context,(int)id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b=Build.VERSION.SDK_INT>=26
            ? new Notification.Builder(context,channelId(context))
            : new Notification.Builder(context).setSound(sound(context));

        String line=(desc==null||desc.trim().isEmpty())?"":(" · "+desc);
        Notification n=b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(ovenName+": ciclo terminado")
            .setContentText("La tirada ha llegado a su hora prevista"+line)
            .setStyle(new Notification.BigTextStyle().bigText("La tirada de "+ovenName+" ha llegado a su hora prevista."+line))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setDeleteIntent(PendingIntent.getBroadcast(context,(int)id,
                new Intent(context,AlarmReceiver.class).setAction(STOP_RING),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE))
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .build();

        if(repeat(context)){
            // El servicio insiste con el sonido hasta que se atiende la notificación o se abre la app.
            RingService.start(context,(int)id,n);
        } else {
            try{ nm.notify((int)id,n); } catch(SecurityException ignored){}
        }
    }
}
