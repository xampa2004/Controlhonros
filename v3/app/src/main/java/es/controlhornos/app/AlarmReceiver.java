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
        SharedPreferences p=c.getSharedPreferences("ajustes",0);
        String s=p.getString("alarma_uri",null);
        Uri media=mediaSound(c);
        StringBuilder id=new StringBuilder(CHANNEL).append('_').append(vibrate(c)?"v":"nv");
        if(media!=null) id.append("_m").append(Integer.toHexString(media.toString().hashCode()));
        else if(soundFile(c)!=null) id.append("_f");
        else if(s!=null) id.append('_').append(Integer.toHexString(s.hashCode()));
        return id.toString();
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

    /** Copia propia del audio elegido (los MP3 suenan siempre, sin permisos de por medio). */
    static String soundFile(Context c){
        String p=c.getSharedPreferences("ajustes",0).getString("alarma_file",null);
        if(p==null||p.isEmpty()) return null;
        return new java.io.File(p).isFile()?p:null;
    }

    /**
     * Copia del audio en el almacén multimedia (Descargas). Es la que usa el canal de la notificación:
     * así el propio sistema suena el aviso aunque la app no pueda quedarse en segundo plano.
     */
    static Uri mediaSound(Context c){
        String s=c.getSharedPreferences("ajustes",0).getString("alarma_media",null);
        return s==null||s.isEmpty()?null:Uri.parse(s);
    }

    @Override public void onReceive(Context context, Intent intent){
        if(intent!=null && STOP_RING.equals(intent.getAction())){
            // Parada manual: se calla el aviso y se quita la notificación.
            RingService.stop(context);
            long id=intent.getLongExtra("piece_id",0);
            if(id!=0) try{ ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel((int)id); }
            catch(Exception ignored){}
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
        String own=soundFile(context);
        Uri media=mediaSound(context);
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(channelId(context),"Fin de ciclo",NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Avisos cuando termina una tirada");
            // La vibración va en el canal: así vibra aunque el servicio no llegue a arrancar.
            ch.enableVibration(vibrate(context));
            Uri sound=sound(context);
            android.media.AudioAttributes attrs=new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            // Con copia en el almacén multimedia suena el propio sistema; si solo hay copia privada,
            // el canal va en silencio y suena el servicio (así no se duplica el sonido).
            ch.setSound(media!=null?media:(own!=null?null:sound),attrs); // null = sin sonido
            nm.createNotificationChannel(ch);
        }

        Intent open=new Intent(context,MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(context,(int)id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b=Build.VERSION.SDK_INT>=26
            ? new Notification.Builder(context,channelId(context))
            : new Notification.Builder(context).setSound(sound(context));

        String line=(desc==null||desc.trim().isEmpty())?"":(" · "+desc);
        // Parar a mano desde la propia notificación, sin abrir la app.
        PendingIntent stopPi=PendingIntent.getBroadcast(context,(int)id+1000,
            new Intent(context,AlarmReceiver.class).setAction(STOP_RING).putExtra("piece_id",id),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(ovenName+": ciclo terminado")
            .setContentText("La tirada ha llegado a su hora prevista"+line)
            .setStyle(new Notification.BigTextStyle().bigText("La tirada de "+ovenName+" ha llegado a su hora prevista."+line))
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_lock_silent_mode,"PARAR",stopPi).build())
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setDeleteIntent(PendingIntent.getBroadcast(context,(int)id,
                new Intent(context,AlarmReceiver.class).setAction(STOP_RING).putExtra("piece_id",id),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE))
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .build();

        if(repeat(context) || own!=null){
            // El servicio insiste con el sonido (o lo da él cuando el audio es propio) hasta pararlo a mano.
            RingService.start(context,(int)id,n);
        } else {
            try{ nm.notify((int)id,n); } catch(SecurityException ignored){}
        }
    }
}
