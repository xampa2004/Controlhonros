package es.controlhornos.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.*;

/** Línea de tiempo: todas las tiradas activas sobre el mismo eje de horas, una fila por horno. */
class TimelineView extends View {
    private final List<Oven> ovens;
    private final List<Piece> pieces;
    private final long t0, t1, now;
    private final float d, pxPerMin;
    private final float axisH, rowH, padX;
    private final Paint grid=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barTxt=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nowLine=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat hm=new SimpleDateFormat("HH:mm",new Locale("es","ES"));

    TimelineView(Context c,List<Oven> ovens,List<Piece> pieces,long now){
        super(c);
        this.ovens=ovens; this.pieces=pieces; this.now=now;
        d=getResources().getDisplayMetrics().density;
        long min=now,max=now;
        for(Piece p:pieces){ min=Math.min(min,p.start); max=Math.max(max,p.end); }
        t0=(min-15*60000L)/3600000L*3600000L;                 // redondeo a la hora hacia abajo
        t1=((max+15*60000L)+3599999L)/3600000L*3600000L;      // y hacia arriba
        pxPerMin=1.6f*d;
        axisH=18*d; rowH=28*d; padX=6*d;
        grid.setStyle(Paint.Style.STROKE); grid.setStrokeWidth(1*d); grid.setColor(0xffe2e8f0);
        txt.setColor(0xff64748b); txt.setTextSize(10*d);
        barTxt.setColor(0xffffffff); barTxt.setTextSize(9.5f*d);
        nowLine.setStyle(Paint.Style.STROKE); nowLine.setStrokeWidth(2*d); nowLine.setColor(0xffb3261e);
    }

    private float x(long t){return padX+(t-t0)/60000f*pxPerMin;}

    @Override protected void onMeasure(int wSpec,int hSpec){
        int needed=(int)((t1-t0)/60000f*pxPerMin)+(int)(12*d);
        int screen=getResources().getDisplayMetrics().widthPixels-(int)(60*d);
        int width=MeasureSpec.getMode(wSpec)==MeasureSpec.UNSPECIFIED?Math.max(needed,screen):Math.max(MeasureSpec.getSize(wSpec),needed);
        int height=(int)(axisH+Math.max(1,ovens.size())*rowH+6*d);
        setMeasuredDimension(width,height);
    }

    @Override protected void onDraw(Canvas c){
        for(long t=t0;t<=t1;t+=3600000L){
            float x=x(t);
            c.drawLine(x,axisH,x,getHeight(),grid);
            c.drawText(hm.format(new Date(t)),x+2*d,11*d,txt);
        }
        for(int i=0;i<ovens.size();i++){
            Oven o=ovens.get(i);
            float y=axisH+i*rowH;
            int color=i%2==0?0xff1f6aa5:0xff3d8fc0;
            for(Piece p:pieces){
                if(p.ovenId!=o.id) continue;
                float x1=x(p.start), x2=Math.max(x(p.end),x1+2*d);
                bar.setColor(p.end<now?0xffb45309:color);
                c.drawRoundRect(x1,y+4*d,x2,y+rowH-4*d,4*d,4*d,bar);
                if(x2-x1>58*d){
                    c.drawText(hm.format(new Date(p.start))+"–"+hm.format(new Date(p.end)),x1+4*d,y+rowH/2+3.5f*d,barTxt);
                }
            }
        }
        float xn=x(now);
        c.drawLine(xn,axisH,xn,getHeight(),nowLine);
    }
}
