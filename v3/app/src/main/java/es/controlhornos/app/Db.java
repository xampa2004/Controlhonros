package es.controlhornos.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

class Oven {
    long id; String name; int minutes; String temp, curve, format;
    Oven(long id, String name, int minutes, String temp, String curve, String format){
        this.id=id; this.name=name; this.minutes=minutes;
        this.temp=temp==null?"":temp; this.curve=curve==null?"":curve; this.format=format==null?"":format;
    }
}

class Piece {
    long id, ovenId, start, end; String description;
    int duration; String temp, curve, format; boolean done;
    /** Número visible de la tirada dentro de su horno (se reinicia por horno). */
    int numero;
    Piece(long id, long ovenId, long start, long end, String description,
          int duration, String temp, String curve, String format, boolean done, int numero){
        this.id=id; this.ovenId=ovenId; this.start=start; this.end=end; this.description=description;
        this.duration=duration;
        this.temp=temp==null?"":temp; this.curve=curve==null?"":curve; this.format=format==null?"":format;
        this.done=done; this.numero=numero;
    }
    /** "Tirada #3", o el número antiguo (id) si la fila es anterior al contador. */
    String label(){
        return "Tirada #"+(numero>0?numero:id);
    }
    /** "60 min · 180 ºC · Bizcocho · 20 cm" sin los datos vacíos. */
    String details(){
        StringBuilder sb=new StringBuilder(Db.dur(duration));
        if(!temp.isEmpty()) sb.append(" · ").append(temp);
        if(!curve.isEmpty()) sb.append(" · Curva ").append(curve);
        if(!format.isEmpty()) sb.append(" · ").append(format);
        return sb.toString();
    }
}

class Db extends SQLiteOpenHelper {
    private static final int VERSION = 4;
    Db(Context c){super(c,"control_hornos.db",null,VERSION);}

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE ovens(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, minutes INTEGER NOT NULL, temp TEXT, curve TEXT, format TEXT, counter INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE pieces(id INTEGER PRIMARY KEY AUTOINCREMENT, oven_id INTEGER NOT NULL, start INTEGER NOT NULL, end INTEGER NOT NULL, description TEXT, duration INTEGER, temp TEXT, curve TEXT, format TEXT, done INTEGER NOT NULL DEFAULT 0, numero INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("INSERT INTO ovens(id,name,minutes,temp,curve,format) VALUES (1,'Horno 1',60,'','','')");
        db.execSQL("INSERT INTO ovens(id,name,minutes,temp,curve,format) VALUES (2,'Horno 2',60,'','','')");
    }

    // Migración desde la versión original (1 horno=int fijo 1/2, sin tabla ovens).
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){
        if(oldV<2){
            db.execSQL("CREATE TABLE IF NOT EXISTS ovens(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, minutes INTEGER NOT NULL, temp TEXT, curve TEXT, format TEXT)");
            db.execSQL("INSERT OR IGNORE INTO ovens(id,name,minutes,temp,curve,format) VALUES (1,'Horno 1',60,'','','')");
            db.execSQL("INSERT OR IGNORE INTO ovens(id,name,minutes,temp,curve,format) VALUES (2,'Horno 2',60,'','','')");
            // La tabla pieces original usaba la columna 'oven' (1 o 2). La copiamos a oven_id sin perder datos.
            Cursor probe = db.rawQuery("PRAGMA table_info(pieces)", null);
            boolean hasOldCol=false, hasNewCol=false;
            while(probe.moveToNext()){
                String colName = probe.getString(1);
                if("oven".equals(colName)) hasOldCol=true;
                if("oven_id".equals(colName)) hasNewCol=true;
            }
            probe.close();
            if(hasOldCol && !hasNewCol){
                db.execSQL("ALTER TABLE pieces ADD COLUMN oven_id INTEGER");
                db.execSQL("UPDATE pieces SET oven_id = oven");
            } else if(!hasNewCol){
                db.execSQL("ALTER TABLE pieces ADD COLUMN oven_id INTEGER NOT NULL DEFAULT 1");
            }
        }
        if(oldV<3){
            addColumn(db,"ovens","temp TEXT");
            addColumn(db,"ovens","curve TEXT");
            addColumn(db,"ovens","format TEXT");
            addColumn(db,"pieces","duration INTEGER");
            addColumn(db,"pieces","temp TEXT");
            addColumn(db,"pieces","curve TEXT");
            addColumn(db,"pieces","format TEXT");
            addColumn(db,"pieces","done INTEGER NOT NULL DEFAULT 0");
            // Las tiradas antiguas no guardaron la duración: se deduce de sus horas.
            db.execSQL("UPDATE pieces SET duration = (end-start)/60000 WHERE duration IS NULL");
        }
        if(oldV<4){
            // Contador de tiradas por horno: se puede reiniciar sin tocar el historial.
            addColumn(db,"ovens","counter INTEGER NOT NULL DEFAULT 0");
            addColumn(db,"pieces","numero INTEGER NOT NULL DEFAULT 0");
            // Las filas que ya existían conservan el número que mostraban (el id).
            db.execSQL("UPDATE pieces SET numero = id WHERE numero = 0");
            db.execSQL("UPDATE ovens SET counter = COALESCE((SELECT MAX(numero) FROM pieces WHERE oven_id = ovens.id), 0)");
        }
    }
    /** ALTER TABLE falla si la columna ya existe; en ese caso no hay nada que hacer. */
    private void addColumn(SQLiteDatabase db,String table,String col){
        try{ db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+col); }catch(Exception ignored){}
    }

    // ---- Hornos ----
    ArrayList<Oven> ovens(){
        ArrayList<Oven> a=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,minutes,temp,curve,format FROM ovens ORDER BY id ASC",null)){
            while(c.moveToNext()) a.add(new Oven(c.getLong(0),c.getString(1),c.getInt(2),c.getString(3),c.getString(4),c.getString(5)));
        }
        return a;
    }
    long addOven(String name,int minutes){
        ContentValues v=new ContentValues();
        v.put("name",name); v.put("minutes",minutes); v.put("temp",""); v.put("curve",""); v.put("format","");
        return getWritableDatabase().insert("ovens",null,v);
    }
    void updateOven(long id,String name,int minutes,String temp,String curve,String format){
        ContentValues v=new ContentValues();
        v.put("name",name); v.put("minutes",minutes); v.put("temp",temp); v.put("curve",curve); v.put("format",format);
        getWritableDatabase().update("ovens",v,"id=?",new String[]{String.valueOf(id)});
    }
    void setOvenMinutes(long id,int minutes){
        ContentValues v=new ContentValues();v.put("minutes",minutes);
        getWritableDatabase().update("ovens",v,"id=?",new String[]{String.valueOf(id)});
    }
    void deleteOven(long id){
        getWritableDatabase().delete("ovens","id=?",new String[]{String.valueOf(id)});
    }
    boolean ovenHasPieces(long ovenId){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM pieces WHERE oven_id=?",new String[]{String.valueOf(ovenId)})){
            return c.moveToFirst() && c.getInt(0)>0;
        }
    }

    // ---- Tiradas ----
    /** Duración legible: "2 d 5 h 30 min", "5 h", "90 min". */
    static String dur(int minutes){
        if(minutes<=0) return "0 min";
        int d=minutes/1440, h=(minutes%1440)/60, m=minutes%60;
        StringBuilder sb=new StringBuilder();
        if(d>0) sb.append(d).append(" d");
        if(h>0) sb.append(sb.length()>0?" ":"").append(h).append(" h");
        if(m>0) sb.append(sb.length()>0?" ":"").append(m).append(" min");
        return sb.toString();
    }
    long add(long ovenId,long start,long end,String desc,int duration,String temp,String curve,String format){
        SQLiteDatabase d=getWritableDatabase();
        int numero=0;
        try(Cursor c=d.rawQuery("SELECT counter FROM ovens WHERE id=?",new String[]{String.valueOf(ovenId)})){
            if(c.moveToFirst()) numero=c.getInt(0)+1;
        }
        ContentValues v=new ContentValues();
        v.put("oven_id",ovenId); v.put("start",start); v.put("end",end); v.put("description",desc);
        v.put("duration",duration); v.put("temp",temp); v.put("curve",curve); v.put("format",format);
        v.put("done",0); v.put("numero",numero);
        long id=d.insert("pieces",null,v);
        if(id>0) d.execSQL("UPDATE ovens SET counter=? WHERE id=?",new Object[]{numero,ovenId});
        return id;
    }
    /** Vuelve a 1 el contador de tiradas de un horno. No borra nada: ni historial ni tiradas en curso. */
    void resetCounter(long ovenId){
        getWritableDatabase().execSQL("UPDATE ovens SET counter=0 WHERE id=?",new Object[]{ovenId});
    }
    void updatePiece(long id,long start,long end,String desc){
        ContentValues v=new ContentValues();
        v.put("start",start); v.put("end",end); v.put("description",desc);
        getWritableDatabase().update("pieces",v,"id=?",new String[]{String.valueOf(id)});
    }
    /** La tirada pasa al historial conservando la foto del ciclo, temperatura, curva y formato. */
    void markDone(long id){
        ContentValues v=new ContentValues();v.put("done",1);
        getWritableDatabase().update("pieces",v,"id=?",new String[]{String.valueOf(id)});
    }
    ArrayList<Piece> all(){
        return query("SELECT id,oven_id,start,end,description,duration,temp,curve,format,done,numero FROM pieces WHERE done=0 ORDER BY end ASC",null);
    }
    ArrayList<Piece> forOven(long ovenId){
        return query("SELECT id,oven_id,start,end,description,duration,temp,curve,format,done,numero FROM pieces WHERE done=0 AND oven_id=? ORDER BY end ASC",
            new String[]{String.valueOf(ovenId)});
    }
    ArrayList<Piece> history(){
        return query("SELECT id,oven_id,start,end,description,duration,temp,curve,format,done,numero FROM pieces WHERE done=1 ORDER BY end DESC",null);
    }
    private ArrayList<Piece> query(String sql,String[] args){
        ArrayList<Piece> a=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(sql,args)){
            while(c.moveToNext())
                a.add(new Piece(c.getLong(0),c.getLong(1),c.getLong(2),c.getLong(3),c.getString(4),
                    c.getInt(5),c.getString(6),c.getString(7),c.getString(8),c.getInt(9)==1,c.getInt(10)));
        }
        return a;
    }
    void delete(long id){
        getWritableDatabase().delete("pieces","id=?",new String[]{String.valueOf(id)});
    }
    void deletePiecesOfOven(long ovenId){
        getWritableDatabase().delete("pieces","oven_id=?",new String[]{String.valueOf(ovenId)});
    }
    void clearHistory(){
        getWritableDatabase().delete("pieces","done=1",null);
    }
}
