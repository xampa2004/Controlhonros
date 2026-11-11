package es.controlhornos.app;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.util.Log;

/**
 * Insiste con el aviso hasta que se atiende: suena una vez y vuelve a sonar un minuto después,
 * así hasta una hora. Se para antes si se abre la app o se descarta la notificación.
 */
public class RingService extends Service {
    private static final long GAP_MS=60000L;       // espera entre un aviso y el siguiente
    private static final long MAX_MS=60*60000L;    // deja de insistir tras una hora
    private MediaPlayer player;
    private Vibrator vibrator;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ring=()->play();
    private final Runnable timeout=()->{
        try{ stopForeground(STOP_FOREGROUND_DETACH); }catch(Exception ignored){}
        stopSelf();
    };

    /** Arranca en primer plano con la notificación del aviso ya construida. */
    static void start(Context c,int notifId,Notification n){
        Intent i=new Intent(c,RingService.class).putExtra("notif_id",notifId).putExtra("notif",n);
        try{
            if(Build.VERSION.SDK_INT>=26) c.startForegroundService(i);
            else c.startService(i);
        }catch(Exception e){
            // Si el móvil no deja arrancar el servicio, al menos suena una vez (el canal ya lo hace).
            try{ ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(notifId,n); }
            catch(Exception ignored){}
        }
    }

    static void stop(Context c){
        try{ c.stopService(new Intent(c,RingService.class)); }catch(Exception ignored){}
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && AlarmReceiver.STOP_RING.equals(intent.getAction())){
            stopSelf(); return START_NOT_STICKY;
        }
        int notifId=intent==null?1:intent.getIntExtra("notif_id",1);
        Notification n=null;
        if(intent!=null){
            Parcelable extra=intent.getParcelableExtra("notif");
            if(extra instanceof Notification) n=(Notification)extra;
        }
        try{
            if(n!=null) startForeground(notifId,n);
        }catch(Exception e){
            Log.w("RingService","sin primer plano",e);
        }
        // El primer aviso ya lo da la notificación (su canal trae sonido y vibración):
        // aquí solo se prepara la repetición, para no sonar dos veces a la vez.
        handler.removeCallbacks(ring);
        handler.postDelayed(ring,GAP_MS);
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout,MAX_MS);
        return START_NOT_STICKY;
    }

    /** Un aviso: el tono entero una sola vez (sin bucle) y la vibración de ese aviso. */
    private void play(){
        stopPlaying();
        handler.removeCallbacks(ring);
        Uri u=AlarmReceiver.sound(this);
        if(u!=null){
            try{
                player=new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
                player.setDataSource(this,u);
                player.setLooping(false);
                player.prepare();
                player.start();
                // Siguiente aviso un minuto después de que empiece éste (y nunca encima del actual).
                handler.postDelayed(ring,Math.max(GAP_MS,player.getDuration()+1000L));
            }catch(Exception e){
                Log.w("RingService","no se pudo reproducir "+u,e);
                releasePlayer();
            }
        }
        if(player==null) handler.postDelayed(ring,GAP_MS); // silencio o error: se reintenta igual
        if(AlarmReceiver.vibrate(this)){
            try{
                vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,700,500},-1));
            }catch(Exception ignored){}
        }
    }

    private void releasePlayer(){
        if(player!=null){ try{player.release();}catch(Exception ignored){} player=null; }
    }
    private void stopPlaying(){
        if(player!=null){ try{player.stop();}catch(Exception ignored){} releasePlayer(); }
        if(vibrator!=null){ try{vibrator.cancel();}catch(Exception ignored){} vibrator=null; }
    }

    @Override public void onDestroy(){
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(ring);
        stopPlaying();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){ return null; }
}
