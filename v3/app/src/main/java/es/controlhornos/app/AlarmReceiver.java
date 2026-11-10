package es.controlhornos.app;

import android.app.*;
import android.content.*;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    static final String CHANNEL="fin_ciclo_hornos";

    /** Canal propio por sonido: Android no permite cambiar el sonido de un canal ya creado. */
    static String channelId(Context c){
        String s=c.getSharedPreferences("ajustes",0).getString("alarma_uri",null);
        return s==null?CHANNEL:CHANNEL+"_"+Integer.toHexString(s.hashCode());
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
            ch.enableVibration(true);
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
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .build();
        try{ nm.notify((int)id,n); } catch(SecurityException ignored){}
    }
}
