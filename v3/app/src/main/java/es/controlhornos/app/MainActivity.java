package es.controlhornos.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private Db db;
    private LinearLayout root, content;
    private TextView permissionBanner;
    private final Handler handler=new Handler();
    private final SimpleDateFormat fmt=new SimpleDateFormat("dd/MM/yyyy HH:mm",new Locale("es","ES"));
    private final Runnable ticker=()->{render();handler.postDelayed(this.ticker,1000);};
    private final int blue=0xff164e78, dark=0xff17324d, grey=0xff64748b, red=0xffb3261e;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        db=new Db(this);
        getWindow().setStatusBarColor(dark);
        getWindow().setNavigationBarColor(dark);
        buildShell();
        // En Android 15 (targetSdk 35) las barras son transparentes sobre fondo claro: iconos oscuros.
        // Debe ir despues de buildShell(): sin decor creado, getInsetsController() lanza NullPointerException.
        if(Build.VERSION.SDK_INT>=35){
            WindowInsetsController ic=getWindow().getDecorView().getWindowInsetsController();
            if(ic!=null){
                int light=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                ic.setSystemBarsAppearance(light,light);
            }
        }
        requestNotificationPermissionIfNeeded();
        // Requisito: al abrir la app se comprueba que las alarmas pendientes siguen programadas.
        AlarmUtil.rescheduleAll(this);
        render();
    }
    @Override protected void onResume(){
        super.onResume();
        render();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker,1000);
    }
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(ticker);}
    @Override protected void onDestroy(){super.onDestroy();if(db!=null)db.close();}
    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] results){
        super.onRequestPermissionsResult(code,perms,results);
        render();
    }

    private void buildShell(){
        ScrollView scroll=new ScrollView(this);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(18),dp(16),dp(28));root.setBackgroundColor(0xfff3f6fa);
        scroll.setBackgroundColor(0xfff3f6fa);
        // Android 15 (targetSdk 35) pinta a pantalla completa: hay que apartar el contenido de las barras.
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            int top, bottom;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                top=bars.top; bottom=bars.bottom;
            } else {
                top=insets.getSystemWindowInsetTop(); bottom=insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0,top,0,bottom);
            return insets;
        });
        scroll.addView(root);setContentView(scroll);
        scroll.requestApplyInsets();

        TextView title=txt("CONTROL DE HORNOS",24,dark,true);root.addView(title);
        TextView sub=txt("Control de tiradas y avisos de fin de ciclo",14,grey,false);
        sub.setPadding(0,dp(4),0,dp(10));root.addView(sub);

        permissionBanner=txt("",12,0xff7a4a00,true);
        permissionBanner.setPadding(dp(10),dp(8),dp(10),dp(8));
        root.addView(permissionBanner,matchWrap());

        Button permissions=button("CONFIGURAR AVISOS DEL MÓVIL",0xffe3edf7,dark);
        permissions.setOnClickListener(v->openAlarmSettings());
        LinearLayout.LayoutParams pbp=matchWrap();pbp.topMargin=dp(8);
        root.addView(permissions,pbp);

        Button addOven=button("+ AÑADIR HORNO",0xffe3edf7,dark);
        LinearLayout.LayoutParams aop=matchWrap();aop.topMargin=dp(8);
        addOven.setOnClickListener(v->addOvenDialog());
        root.addView(addOven,aop);

        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content);

        TextView note=txt("Mantén la aplicación instalada y permite las notificaciones y alarmas. En OPPO, desactiva también la restricción de batería para esta app.",12,grey,false);
        note.setPadding(dp(2),dp(14),dp(2),0);root.addView(note);
    }

    private void requestNotificationPermissionIfNeeded(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
    }

    private boolean notifGranted(){
        return Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
    }
    private boolean exactGranted(){
        return Build.VERSION.SDK_INT<31 || ((AlarmManager)getSystemService(ALARM_SERVICE)).canScheduleExactAlarms();
    }
    private boolean batteryOk(){
        return ((android.os.PowerManager)getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());
    }

    /** Comprueba de verdad el estado de los permisos; nunca afirma que están activos sin comprobarlo. */
    private void updatePermissionBanner(){
        boolean notif=notifGranted(), exact=exactGranted(), battery=batteryOk();
        if(notif && exact && battery){
            permissionBanner.setVisibility(View.GONE);
        } else {
            permissionBanner.setVisibility(View.VISIBLE);
            permissionBanner.setBackground(round(0xfffdecc8,dp(8)));
            StringBuilder sb=new StringBuilder("⚠ Avisos incompletos: ");
            if(!notif) sb.append("faltan permisos de notificaciones. ");
            if(!exact) sb.append("faltan permisos de alarma exacta (las horas podrían no ser precisas). ");
            if(!battery) sb.append("el móvil puede retrasar los avisos con la app cerrada. ");
            sb.append("Pulsa \"Configurar avisos del móvil\".");
            permissionBanner.setText(sb.toString());
        }
    }

    private void openAlarmSettings(){
        if(!exactGranted()){
            try{ startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+getPackageName()))); }
            catch(Exception e){ openAppDetails(); }
            return;
        }
        if(!notifGranted()){
            // Solo se puede pedir el permiso si el sistema aún permite mostrarlo; si lo denegó para siempre, hay que ir a Ajustes.
            if(Build.VERSION.SDK_INT>=33 && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)){
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
            } else {
                openNotificationSettings();
            }
            return;
        }
        if(!batteryOk()){
            try{ startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:"+getPackageName()))); }
            catch(Exception e){ openAppDetails(); }
            return;
        }
        new AlertDialog.Builder(this).setTitle("Avisos configurados")
            .setMessage("Los permisos necesarios están concedidos. Si sigues sin oír el aviso con el móvil en reposo, revisa en OPPO/ColorOS: Ajustes > Aplicaciones > Control de hornos > Batería, y permite el inicio automático y la actividad en segundo plano.")
            .setPositiveButton("Entendido",null).show();
    }

    private void openNotificationSettings(){
        try{
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch(Exception e){ openAppDetails(); }
    }
    private void openAppDetails(){
        try{ startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName()))); }
        catch(Exception ignored){ toast("Abre los ajustes del móvil > Aplicaciones > Control de hornos."); }
    }

    private void render(){
        if(content==null) return;
        updatePermissionBanner();
        content.removeAllViews();
        List<Oven> ovens=db.ovens();
        View tl=timelinePanel(ovens);
        if(tl!=null) content.addView(tl);
        if(ovens.isEmpty()){
            TextView empty=txt("No hay hornos configurados. Pulsa \"+ AÑADIR HORNO\".",14,grey,false);
            content.addView(empty);
            return;
        }
        for(Oven oven:ovens) content.addView(ovenPanel(oven));
        View hist=historyPanel(ovens);
        if(hist!=null) content.addView(hist);
    }

    /** Todas las tiradas activas sobre el mismo eje de horas, aparte de las tarjetas de cada horno. */
    private View timelinePanel(List<Oven> ovens){
        List<Piece> pieces=db.all();
        if(pieces.isEmpty()) return null;
        LinearLayout panel=card();
        panel.addView(txt("LÍNEA DE TIEMPO",15,dark,true));
        TextView sub=txt("Todas las tiradas activas, una fila por horno. Desliza en horizontal.",12,grey,false);
        sub.setPadding(0,dp(3),0,dp(8));panel.addView(sub);
        HorizontalScrollView hs=new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(new TimelineView(this,ovens,pieces,System.currentTimeMillis()));
        panel.addView(hs);
        return panel;
    }

    /** Tiradas ya recogidas, con la foto del ciclo que tenían al crearse. */
    private View historyPanel(List<Oven> ovens){
        List<Piece> hist=db.history();
        if(hist.isEmpty()) return null;
        Map<Long,String> names=new HashMap<>();
        for(Oven o:ovens) names.put(o.id,o.name);
        LinearLayout panel=card();
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);panel.addView(head);
        head.addView(txt("HISTORIAL",15,dark,true),new LinearLayout.LayoutParams(0,-2,1));
        Button clear=button("VACIAR",0xfffbe3e0,red);
        clear.setOnClickListener(v-> new AlertDialog.Builder(this).setTitle("¿Vaciar el historial?")
            .setMessage("Se borrarán las "+hist.size()+" tiradas recogidas. Los hornos y las tiradas activas no se tocan.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Vaciar",(d,w)->{db.clearHistory();render();}).show());
        head.addView(clear);
        int shown=0;
        for(Piece p:hist){
            if(shown++>=30) break;
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0,dp(9),0,dp(2));
            String label=(p.description==null||p.description.trim().isEmpty())?"Tirada #"+p.id:p.description;
            row.addView(txt(label,14,dark,true));
            String ovenName=names.containsKey(p.ovenId)?names.get(p.ovenId):"Horno eliminado";
            row.addView(txt(ovenName+" · "+fmt.format(new Date(p.start)),12,grey,false));
            row.addView(txt(p.details(),12,blue,false));
            panel.addView(row);
        }
        return panel;
    }

    private LinearLayout card(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14),dp(14),dp(14),dp(14));panel.setBackground(round(0xffffffff,dp(14)));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(12);panel.setLayoutParams(pp);
        return panel;
    }

    private View ovenPanel(Oven oven){
        LinearLayout panel=card();

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(head);
        TextView h=txt(oven.name.toUpperCase(Locale.getDefault()),18,dark,true);
        h.setOnClickListener(v->editOvenDialog(oven));
        head.addView(h,new LinearLayout.LayoutParams(0,-2,1));
        Button cycle=button("CICLO: "+Db.dur(oven.minutes),0xffe8eef5,dark);
        head.addView(cycle);cycle.setOnClickListener(v->editCycle(oven));

        Button details=button(ovenDetails(oven),0xffeef2f7,dark);
        LinearLayout.LayoutParams detp=new LinearLayout.LayoutParams(-1,dp(38));detp.topMargin=dp(8);
        panel.addView(details,detp);
        details.setOnClickListener(v->editOvenDialog(oven));

        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(8);panel.addView(row,rp);
        Button add=button("+ TIRAR",blue,0xffffffff);
        row.addView(add,new LinearLayout.LayoutParams(0,dp(48),1));
        add.setOnClickListener(v->addPiece(oven));
        Button running=button("YA EN MARCHA",0xffe3edf7,dark);
        LinearLayout.LayoutParams runp=new LinearLayout.LayoutParams(0,dp(48),1);runp.leftMargin=dp(8);
        row.addView(running,runp);
        running.setOnClickListener(v->addRunningDialog(oven));

        Button del=button("ELIMINAR HORNO",0xfffbe3e0,red);
        LinearLayout.LayoutParams delp=new LinearLayout.LayoutParams(-1,dp(36));delp.topMargin=dp(8);
        panel.addView(del,delp);del.setOnClickListener(v->deleteOvenDialog(oven));

        List<Piece> pieces=db.forOven(oven.id);
        if(pieces.isEmpty()){
            TextView empty=txt("Todavía no hay tiradas en este horno.",13,grey,false);
            empty.setPadding(0,dp(12),0,dp(2));panel.addView(empty);
        } else {
            for(Piece p:pieces) panel.addView(pieceCard(p,oven));
        }
        return panel;
    }

    /** "180 ºC · Curva Bizcocho · 20 cm", o el texto que invita a rellenarlo. */
    private String ovenDetails(Oven o){
        StringBuilder sb=new StringBuilder();
        if(!o.temp.isEmpty()) sb.append(o.temp);
        if(!o.curve.isEmpty()) sb.append(sb.length()>0?" · ":"").append("Curva ").append(o.curve);
        if(!o.format.isEmpty()) sb.append(sb.length()>0?" · ":"").append(o.format);
        return sb.length()==0?"TEMPERATURA, CURVA Y FORMATO (pulsa para definir)":sb.toString();
    }

    private View pieceCard(Piece p,Oven oven){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(round(0xfff5f8fc,dp(10)));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(10);card.setLayoutParams(cp);

        String label=(p.description==null||p.description.trim().isEmpty())?"Tirada #"+p.id:p.description;
        TextView name=txt(label,16,dark,true);card.addView(name);
        TextView times=txt("Entrada: "+fmt.format(new Date(p.start))+"\nSalida prevista: "+fmt.format(new Date(p.end)),12,grey,false);
        times.setPadding(0,dp(5),0,dp(3));card.addView(times);
        TextView det=txt(p.details(),12,blue,false);
        det.setPadding(0,0,0,dp(7));card.addView(det);

        long now=System.currentTimeMillis();
        long total=Math.max(1,p.end-p.start), elapsed=Math.max(0,Math.min(total,now-p.start));
        int percent=(int)(elapsed*100/total);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);bar.setProgress(percent);card.addView(bar,new LinearLayout.LayoutParams(-1,dp(8)));

        boolean finished = now>=p.end;
        boolean soon = !finished && (p.end-now)<=5*60000L; // últimos 5 minutos: destacar
        String status = finished ? "CICLO TERMINADO · pendiente de recogida" : "Progreso: "+percent+"% · Restante: "+remaining(p.end-now);
        TextView st=txt(status,13, finished?0xffb45309:(soon?red:blue), true);
        st.setPadding(0,dp(6),0,dp(8));
        if(finished||soon) card.setBackground(round(0xfffdeeea,dp(10)));
        card.addView(st);

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);card.addView(actions);
        Button edit=button("EDITAR",0xffe4ebf3,dark);
        actions.addView(edit,new LinearLayout.LayoutParams(0,dp(42),1));
        edit.setOnClickListener(v->editPiece(p,oven));
        Button done=button("RECOGIDA",0xffd9eee2,0xff17633a);
        LinearLayout.LayoutParams donep=new LinearLayout.LayoutParams(0,dp(42),1);donep.leftMargin=dp(8);
        actions.addView(done,donep);
        done.setOnClickListener(v-> new AlertDialog.Builder(this).setTitle("¿Retirar esta tirada?")
            .setMessage("Pasará al historial con su ciclo, temperatura, curva y formato, y se cancelará su alarma.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Sí, recogida",(d,w)->{
                AlarmUtil.cancel(this,p.id);
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel((int)p.id);
                db.markDone(p.id); render();
            })
            .show());
        return card;
    }

    private void addOvenDialog(){
        EditText name=new EditText(this);name.setHint("Nombre del horno (ej. Horno 3)");
        LinearLayout wrap=new LinearLayout(this);wrap.setPadding(dp(18),dp(8),dp(18),0);wrap.addView(name);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Nuevo horno").setView(wrap)
            .setNegativeButton("Cancelar",null).setPositiveButton("Añadir",null).create();
        // El botón no cierra el diálogo si falta el nombre: así no se pierde lo escrito.
        d.setOnShowListener(s->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String n=name.getText().toString().trim();
            if(n.isEmpty()){toast("Escribe un nombre para el horno.");return;}
            if(db.addOven(n,60)<0){toast("No se pudo guardar el horno. Inténtalo de nuevo.");return;}
            d.dismiss();render();
        }));
        d.show();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }
    /** Nombre, temperatura, curva y formato del horno. */
    private void editOvenDialog(Oven oven){
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(18),dp(8),dp(18),0);
        EditText name=new EditText(this);name.setText(oven.name);name.setHint("Nombre del horno");
        EditText temp=new EditText(this);temp.setText(oven.temp);temp.setHint("Temperatura (ej. 180 ºC)");
        EditText curve=new EditText(this);curve.setText(oven.curve);curve.setHint("Nombre de la curva (ej. Bizcocho)");
        EditText format=new EditText(this);format.setText(oven.format);format.setHint("Formato (ej. 20 cm)");
        wrap.addView(name);wrap.addView(temp);wrap.addView(curve);wrap.addView(format);

        AlertDialog d=new AlertDialog.Builder(this).setTitle("Editar "+oven.name).setView(wrap)
            .setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        d.setOnShowListener(s->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String n=name.getText().toString().trim();
            if(n.isEmpty()){toast("El nombre no puede estar vacío.");return;}
            db.updateOven(oven.id,n,oven.minutes,
                temp.getText().toString().trim(),curve.getText().toString().trim(),format.getText().toString().trim());
            AlarmUtil.rescheduleAll(this); // para que la próxima notificación muestre el nombre nuevo
            d.dismiss();render();
        }));
        d.show();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }
    private void deleteOvenDialog(Oven oven){
        boolean hasPieces=db.ovenHasPieces(oven.id);
        String msg = hasPieces
            ? "Este horno tiene tiradas guardadas. Si lo eliminas, se eliminarán también esas tiradas y sus alarmas. Esta acción no se puede deshacer."
            : "¿Eliminar este horno? Esta acción no se puede deshacer.";
        new AlertDialog.Builder(this).setTitle("Eliminar "+oven.name).setMessage(msg)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Eliminar",(d,w)->{
                for(Piece p:db.forOven(oven.id)){
                    AlarmUtil.cancel(this,p.id);
                    ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel((int)p.id);
                }
                db.deletePiecesOfOven(oven.id); // tiradas activas e historial de este horno
                db.deleteOven(oven.id);
                render();
            }).show();
    }

    private void editCycle(Oven oven){
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(18),dp(8),dp(18),0);
        EditText[] f=addDurationInputs(wrap,oven.minutes);
        TextView warn=txt("Esto solo afecta a las tiradas nuevas. Las ya registradas conservan su hora de salida.",12,grey,false);
        warn.setPadding(0,dp(10),0,0);wrap.addView(warn);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Duración del ciclo · "+oven.name).setView(wrap)
            .setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        d.setOnShowListener(s->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            long minutes=durationMinutes(f);
            if(minutes<=0){toast("El ciclo debe ser superior a cero.");return;}
            if(minutes>MAX_MINUTES){toast("Como máximo un año (365 días).");return;}
            db.setOvenMinutes(oven.id,(int)minutes);
            d.dismiss();render();
        }));
        d.show();
    }

    /** TIRAR guarda la tirada en el acto, sin pedir nada: editar los detalles es un paso posterior. */
    private void addPiece(Oven oven){
        if(oven.minutes<=0){toast("Configura primero la duración del ciclo del horno.");editCycle(oven);return;}
        long now=System.currentTimeMillis(), end=now+oven.minutes*60000L;
        long id=db.add(oven.id,now,end,"",oven.minutes,oven.temp,oven.curve,oven.format);
        if(id<0){toast("No se pudo guardar la tirada. Inténtalo de nuevo.");return;}
        boolean exact=AlarmUtil.schedule(this,
            new Piece(id,oven.id,now,end,"",oven.minutes,oven.temp,oven.curve,oven.format,false),oven.name);
        render();
        toast(exact?"Tirada guardada. Aviso a las "+fmt.format(new Date(end))+"."
                   :"Tirada guardada. El aviso será aproximado (faltan alarmas exactas).");
    }

    /** Si se olvidó pulsar TIRAR: se indica lo que le queda y la entrada se calcula hacia atrás. */
    private void addRunningDialog(Oven oven){
        if(oven.minutes<=0){toast("Configura primero la duración del ciclo del horno.");editCycle(oven);return;}
        EditText desc=new EditText(this);desc.setHint("Descripción opcional (ej. bizcocho de la abuela)");
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);wrap.setPadding(dp(18),dp(8),dp(18),0);
        wrap.addView(txt("Tiempo que le queda en el horno",13,grey,true));
        EditText[] f=addDurationInputs(wrap,Math.min(60,oven.minutes-1));
        wrap.addView(desc);
        TextView note=txt("La hora de entrada se calcula hacia atrás con el ciclo de "+Db.dur(oven.minutes)+".",12,grey,false);
        note.setPadding(0,dp(8),0,0);wrap.addView(note);

        AlertDialog d=new AlertDialog.Builder(this).setTitle("Tirada ya en marcha · "+oven.name).setView(wrap)
            .setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        d.setOnShowListener(s->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            long remain=durationMinutes(f);
            if(remain<=0){toast("Indica cuánto tiempo le queda (más de cero).");return;}
            if(remain>=oven.minutes){toast("No puede quedar más tiempo que el ciclo completo ("+Db.dur(oven.minutes)+").");return;}
            long now=System.currentTimeMillis();
            long end=now+remain*60000L;
            long start=end-oven.minutes*60000L;
            String text=desc.getText().toString().trim();
            long id=db.add(oven.id,start,end,text,oven.minutes,oven.temp,oven.curve,oven.format);
            if(id<0){toast("No se pudo guardar la tirada. Inténtalo de nuevo.");return;}
            AlarmUtil.schedule(this,
                new Piece(id,oven.id,start,end,text,oven.minutes,oven.temp,oven.curve,oven.format,false),oven.name);
            d.dismiss();render();
            toast("Tirada guardada. Sale a las "+fmt.format(new Date(end))+".");
        }));
        d.show();
    }

    private static final long MAX_MINUTES=365*1440;

    /** Tres campos numéricos Días / Horas / Min en una fila; devuelve [días, horas, minutos]. */
    private EditText[] addDurationInputs(LinearLayout parent,int total){
        EditText days=numField(total/1440), hours=numField((total%1440)/60), mins=numField(total%60);
        LinearLayout row=new LinearLayout(this);
        row.addView(fieldColumn("Días",days),new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(8);
        row.addView(fieldColumn("Horas",hours),hp);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,-2,1);mp.leftMargin=dp(8);
        row.addView(fieldColumn("Min",mins),mp);
        parent.addView(row);
        return new EditText[]{days,hours,mins};
    }
    private LinearLayout fieldColumn(String label,EditText field){
        LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);
        TextView t=txt(label,12,grey,true);t.setPadding(dp(4),0,0,0);col.addView(t);col.addView(field);
        return col;
    }
    private EditText numField(int value){
        EditText e=new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setSelectAllOnFocus(true);
        e.setText(String.valueOf(value));
        return e;
    }
    /** Suma los tres campos en minutos; un valor absurdo cae en MAX_MINUTES+1 y no desborda. */
    private long durationMinutes(EditText[] f){
        try{
            return Long.parseLong(f[0].getText().toString().trim().isEmpty()?"0":f[0].getText().toString().trim())*1440L
                 + Long.parseLong(f[1].getText().toString().trim().isEmpty()?"0":f[1].getText().toString().trim())*60L
                 + Long.parseLong(f[2].getText().toString().trim().isEmpty()?"0":f[2].getText().toString().trim());
        } catch(NumberFormatException ex){
            return MAX_MINUTES+1;
        }
    }

    private void editPiece(Piece p,Oven oven){
        final int duration=p.duration>0?p.duration:oven.minutes; // filas antiguas sin duración guardada
        Calendar cal=Calendar.getInstance();cal.setTimeInMillis(p.start);
        EditText desc=new EditText(this);desc.setSingleLine(false);
        desc.setHint("Descripción opcional de la pieza o prueba");
        desc.setText(p.description==null?"":p.description);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(18),dp(8),dp(18),0);
        layout.addView(txt("Fecha y hora de entrada",13,grey,true));
        layout.addView(desc);
        final Calendar selected=(Calendar)cal.clone();
        Button date=button(fmt.format(cal.getTime()),0xffe8eef5,dark);
        LinearLayout.LayoutParams datep=new LinearLayout.LayoutParams(-1,dp(46));datep.topMargin=dp(8);
        layout.addView(date,datep);
        date.setOnClickListener(v-> new DatePickerDialog(this,(view,y,mo,day)->{
            selected.set(Calendar.YEAR,y);selected.set(Calendar.MONTH,mo);selected.set(Calendar.DAY_OF_MONTH,day);
            date.setText(fmt.format(selected.getTime()));
            new TimePickerDialog(this,(tv,hh,mm)->{
                selected.set(Calendar.HOUR_OF_DAY,hh);selected.set(Calendar.MINUTE,mm);selected.set(Calendar.SECOND,0);
                date.setText(fmt.format(selected.getTime()));
            },selected.get(Calendar.HOUR_OF_DAY),selected.get(Calendar.MINUTE),true).show();
        },selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),selected.get(Calendar.DAY_OF_MONTH)).show());
        TextView note=txt("Datos de la tirada: "+p.details()+". La salida se recalcula con esa misma duración.",12,grey,false);
        note.setPadding(0,dp(10),0,0);layout.addView(note);

        AlertDialog d=new AlertDialog.Builder(this).setTitle("Editar tirada · "+oven.name).setView(layout)
            .setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        d.setOnShowListener(s->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            long start=selected.getTimeInMillis();
            if(start>System.currentTimeMillis()+60000){toast("La fecha de entrada no puede estar en el futuro.");return;}
            long end=start+duration*60000L;
            String text=desc.getText().toString().trim();
            db.updatePiece(p.id,start,end,text);
            AlarmUtil.cancel(this,p.id);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel((int)p.id);
            if(end>System.currentTimeMillis())
                AlarmUtil.schedule(this,
                    new Piece(p.id,oven.id,start,end,text,duration,p.temp,p.curve,p.format,false),oven.name);
            d.dismiss();render();
        }));
        d.show();
    }

    private String remaining(long ms){if(ms<=0)return "00:00:00";long s=ms/1000;return String.format(Locale.getDefault(),"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60);}
    private TextView txt(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,1);return t;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setTextColor(fg);b.setAllCaps(false);b.setBackground(round(bg,dp(9)));b.setPadding(dp(8),0,dp(8),0);return b;}
    private android.graphics.drawable.GradientDrawable round(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(-1,-2);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
