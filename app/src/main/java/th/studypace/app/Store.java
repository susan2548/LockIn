package th.studypace.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Date;

/** The planner and journal share a local database; completing a task updates both together. */
public final class Store extends SQLiteOpenHelper {
    private static Store instance;

    private Store(Context context) { super(context.getApplicationContext(), "pordee.db", null, 2); }

    private static synchronized SQLiteDatabase db(Context c) {
        if (instance == null) instance = new Store(c);
        return instance.getWritableDatabase();
    }

    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY,v TEXT NOT NULL)");
        d.execSQL("CREATE TABLE events(seq INTEGER PRIMARY KEY, type TEXT, at INTEGER, item TEXT, title TEXT, value REAL, note TEXT, pkg TEXT, unit TEXT, day TEXT, session TEXT, icon TEXT)");
        d.execSQL("CREATE INDEX by_usage ON events(type,pkg,day)");
        d.execSQL("CREATE INDEX by_item ON events(type,item,seq)");
        save(d, "tasks","[]");
        save(d, "habits","[{\"id\":\"water\",\"title\":\"ดื่มน้ำ\",\"unit\":\"แก้ว\",\"goal\":6,\"period\":\"day\",\"time\":\"\"},{\"id\":\"exercise\",\"title\":\"ออกกำลังกาย\",\"unit\":\"ครั้ง\",\"goal\":2,\"period\":\"week\",\"time\":\"\"}]");
        save(d, "rules","{\"apps\":{},\"breakMinutes\":5,\"dailyTime\":\"22:30\",\"quietStart\":\"\",\"quietEnd\":\"\"}");
        save(d, "focus","null");
    }
    @Override public void onUpgrade(SQLiteDatabase d,int oldVersion,int newVersion) {
        if (oldVersion < 2) d.execSQL("ALTER TABLE events ADD COLUMN icon TEXT");
    }
    private static void save(SQLiteDatabase d,String k,String v) {
        ContentValues row=new ContentValues(); row.put("k",k); row.put("v",v);
        d.insertWithOnConflict("settings",null,row,SQLiteDatabase.CONFLICT_REPLACE);
    }
    private static String get(SQLiteDatabase d,String k) {
        try (Cursor cursor=d.rawQuery("SELECT v FROM settings WHERE k=?",new String[]{k})) {
            return cursor.moveToFirst()?cursor.getString(0):"null";
        }
    }
    public static String section(Context c,String k) { return get(db(c),k); }

    public static JSONObject read(Context c) {
        SQLiteDatabase d=db(c);
        JSONObject result=new JSONObject();
        d.beginTransactionNonExclusive();
        try {
            JSONObject focus=parseFocus(get(d,"focus"));
            if (focus!=null && System.currentTimeMillis()>=focus.optLong("end") && !focus.optBoolean("recorded")) {
                recordFocus(d,focus,System.currentTimeMillis());
                focus.put("recorded",true); save(d,"focus",focus.toString());
            }
            result.put("focus",focus==null?JSONObject.NULL:focus);
            result.put("tasks",new JSONArray(get(d,"tasks")));
            result.put("habits",new JSONArray(get(d,"habits")));
            result.put("rules",new JSONObject(get(d,"rules")));
            JSONArray events=new JSONArray();
            try (Cursor cr=d.rawQuery("SELECT type,at,item,title,value,note,pkg,unit,day,session,icon FROM events ORDER BY at,seq",null)) {
                while(cr.moveToNext()) {
                    JSONObject e=new JSONObject();
                    e.put("type",cr.getString(0)); e.put("at",cr.getLong(1));
                    e.put("id",cr.getString(2)); e.put("title",cr.getString(3)); e.put("value",cr.getDouble(4));
                    e.put("note",cr.getString(5)); e.put("pkg",cr.getString(6)); e.put("unit",cr.getString(7));
                    e.put("day",cr.getString(8)); e.put("session",cr.getString(9));
                    if (!cr.isNull(10)) e.put("icon",cr.getString(10));
                    events.put(e);
                }
            }
            result.put("events",events);
            d.setTransactionSuccessful();
        } catch(JSONException ex) { throw new IllegalStateException(ex); }
        finally { d.endTransaction(); }
        return result;
    }
    public static void put(Context c,String k,String json) throws JSONException {
        SQLiteDatabase d=db(c);
        d.beginTransaction();
        try {
            if ("tasks".equals(k)) {
                JSONArray next=new JSONArray(json), previous=new JSONArray(get(d,k));
                HashMap<String,JSONObject> old=new HashMap<>();
                for(int i=0;i<previous.length();i++) {JSONObject t=previous.getJSONObject(i); old.put(t.getString("id"),t);}
                long at=System.currentTimeMillis();
                for(int i=0;i<next.length();i++) {
                    JSONObject task=next.getJSONObject(i);
                    String id=task.getString("id"), title=task.getString("title"), note=task.optString("note");
                    JSONObject before=old.get(id);
                    boolean wasDone=before!=null && before.optBoolean("done");
                    if(task.optBoolean("done") && !wasDone) append(d,"task",id,title,1,"","","",at,"",null);
                    if(!task.optBoolean("done") && wasDone)
                        d.execSQL("DELETE FROM events WHERE seq=(SELECT MAX(seq) FROM events WHERE type='task' AND item=?)",new String[]{id});
                    if(!note.isEmpty() && (before==null || !note.equals(before.optString("note"))))
                        append(d,"note",id,title,1,note,"","",at,"",null);
                }
            } else if ("habits".equals(k)) { new JSONArray(json); }
            else if ("rules".equals(k)) { new JSONObject(json); }
            else throw new IllegalArgumentException("Unknown section");
            save(d,k,json);
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
        changed(c);
    }
    private static String day(long at) { return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(at)); }
    private static void append(SQLiteDatabase d,String type,String id,String title,double value,String note,String pkg,String unit,long at,String session,String icon) {
        String date=day(at);
        if ("usage".equals(type)) {
            try(Cursor cr=d.rawQuery("SELECT seq FROM events WHERE type='usage' AND pkg=? AND day=? LIMIT 1",new String[]{pkg,date})) {
                if(cr.moveToFirst()) {
                    d.execSQL("UPDATE events SET value=value+? WHERE seq=?",new Object[]{value,cr.getLong(0)});
                    return;
                }
            }
        }
        ContentValues v=new ContentValues();
        v.put("type",type);v.put("at",at);v.put("item",id);v.put("title",title);v.put("value",value);
        v.put("note",note);v.put("pkg",pkg);v.put("unit",unit);v.put("day",date);v.put("session",session);
        if (icon != null && !icon.isEmpty()) v.put("icon",icon);
        d.insertOrThrow("events",null,v);
    }
    public static void event(Context c,String type,String id,String title,double value,String note,String pkg,String unit,long at) {
        event(c,type,id,title,value,note,pkg,unit,at,null);
    }
    public static void event(Context c,String type,String id,String title,double value,String note,String pkg,String unit,long at,String icon) {
        SQLiteDatabase d=db(c);
        d.beginTransaction();
        try { append(d,type,id,title,value,note,pkg,unit,at,"",icon); d.setTransactionSuccessful(); }
        finally { d.endTransaction(); }
    }
    private static JSONObject parseFocus(String text) {
        try { return new JSONObject(text); } catch(JSONException ex) { return null; }
    }
    private static void recordFocus(SQLiteDatabase d,JSONObject focus,long until) {
        if(focus.optBoolean("recorded")) return;
        long start=focus.optLong("start"),end=Math.min(until,focus.optLong("end"));
        for(long cursor=start;cursor<end;) {
            Calendar midnight=Calendar.getInstance(); midnight.setTimeInMillis(cursor);
            midnight.add(Calendar.DAY_OF_MONTH,1); midnight.set(Calendar.HOUR_OF_DAY,0);
            midnight.set(Calendar.MINUTE,0);midnight.set(Calendar.SECOND,0);midnight.set(Calendar.MILLISECOND,0);
            long partEnd=Math.min(end,midnight.getTimeInMillis());
            append(d,"focus",focus.optString("taskId"),focus.optString("title"),(partEnd-cursor)/1000d,"","","",cursor,String.valueOf(start),null);
            cursor=partEnd;
        }
    }
    public static void startFocus(Context c,String id,String title,int minutes) {
        SQLiteDatabase d=db(c); d.beginTransaction();
        try {
            long now=System.currentTimeMillis();
            JSONObject old=parseFocus(get(d,"focus"));
            if(old==null || old.optLong("end")<=now) {
                if(old!=null) recordFocus(d,old,now);
                JSONObject focus=new JSONObject();
                focus.put("taskId",id); focus.put("title",title); focus.put("start",now);
                focus.put("end",now+60000L*Math.max(1,Math.min(1440,minutes)));
                save(d,"focus",focus.toString());
            }
            d.setTransactionSuccessful();
        } catch(JSONException ex){ throw new IllegalStateException(ex); }
        finally {d.endTransaction();}
        changed(c);
    }
    public static void finishFocus(Context c) {
        SQLiteDatabase d=db(c); d.beginTransaction();
        try {
            JSONObject f=parseFocus(get(d,"focus"));
            if(f!=null) recordFocus(d,f,System.currentTimeMillis());
            save(d,"focus","null"); d.setTransactionSuccessful();
        } finally {d.endTransaction();}
        changed(c);
    }
    public static void extendFocus(Context c,int minutes) {
        SQLiteDatabase d=db(c); d.beginTransaction();
        try {
            JSONObject f=parseFocus(get(d,"focus"));
            if(f!=null){
                long now=System.currentTimeMillis(), end=f.optLong("end");
                if(end<=now) {recordFocus(d,f,now); f.put("start",now); f.remove("recorded");}
                f.put("end",Math.max(now,end)+60000L*Math.max(1,Math.min(1440,minutes))); save(d,"focus",f.toString());
            }
            d.setTransactionSuccessful();
        } catch(JSONException ex){throw new IllegalStateException(ex);}
        finally {d.endTransaction();}
        changed(c);
    }
    private static void changed(Context c) {
        Alerts.reschedule(c);
        GuardService.refresh(c);
    }
}
