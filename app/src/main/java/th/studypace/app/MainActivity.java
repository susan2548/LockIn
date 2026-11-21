package th.studypace.app;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.util.Base64;
import android.view.accessibility.AccessibilityManager;
import android.webkit.*;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private WebView web;
    private final android.util.LruCache<String,byte[]> iconCache = new android.util.LruCache<String,byte[]>(2 * 1024 * 1024) {
        @Override protected int sizeOf(String key, byte[] data) { return data.length; }
    };
    private boolean ready;
    private String pendingRoute="";
    private static final String HOST="pordee.local";
    private static final int NOTIFY=12, LEGACY_IMAGES=13;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if(saved!=null) pendingRoute=saved.getString("pendingRoute","");
        else readRoute(getIntent());
        web=new WebView(this);
        web.setBackgroundColor(Color.rgb(244,246,251));
        WebSettings ws=web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setDefaultTextEncodingName("UTF-8");
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setSupportMultipleWindows(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();
                if("https".equals(uri.getScheme()) && HOST.equals(uri.getHost())) {
                    String path=uri.getPath();
                    if(path!=null && path.matches("/icons/[a-zA-Z0-9_]+(?:[.][a-zA-Z0-9_]+)*[.]png")) {
                        byte[] png = appIcon(path.substring(7, path.length() - 4));
                        if(png != null) return new WebResourceResponse("image/png", null, new ByteArrayInputStream(png));
                    }
                    if(path!=null && path.matches("/[a-zA-Z0-9_.-]+") && !path.contains("..")) {
                        String name=path.substring(1);
                        String mime=name.endsWith(".html")?"text/html":name.endsWith(".css")?"text/css":name.endsWith(".js")?"text/javascript":name.endsWith(".webp")?"image/webp":name.endsWith(".svg")?"image/svg+xml":name.endsWith(".woff2")?"font/woff2":"application/octet-stream";
                        try{return new WebResourceResponse(mime,"UTF-8",getAssets().open(name));}catch(IOException ignored){}
                    }
                }
                return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView view,String url) { ready=true; callResume(); }
        });
        web.addJavascriptInterface(new Bridge(),"Native");
        setContentView(web);
        web.loadUrl("https://"+HOST+"/index.html");
        Alerts.reschedule(this);
    }
    private byte[] appIcon(String pkg) {
        byte[] cached = iconCache.get(pkg);
        if (cached != null) return cached;
        try {
            android.graphics.drawable.Drawable icon = getPackageManager().getApplicationIcon(pkg).mutate();
            int size = Math.min(144, Math.max(64, Math.round(44 * getResources().getDisplayMetrics().density)));
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, size, size);
            icon.draw(new android.graphics.Canvas(bitmap));
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
            bitmap.recycle();
            byte[] data = stream.toByteArray();
            iconCache.put(pkg, data);
            return data;
        } catch (PackageManager.NameNotFoundException | RuntimeException missing) {
            return null;
        }
    }
    private synchronized void readRoute(Intent it) {
        if(it!=null && it.hasExtra("route") && AppRoutes.isInternal(this,it)) {
            String route=it.getStringExtra("route");
            if(route!=null && route.length()<250) pendingRoute=route;
            it.removeExtra("route");
        }
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);readRoute(intent);callResume();}
    @Override protected void onSaveInstanceState(Bundle bundle){super.onSaveInstanceState(bundle);bundle.putString("pendingRoute",pendingRoute);}
    @Override protected void onResume(){super.onResume();callResume();}
    private void callResume(){if(ready && web!=null)web.evaluateJavascript("if(window.onNativeResume)window.onNativeResume()",null);}
    @Override public void onBackPressed(){
        if(!ready){super.onBackPressed();return;}
        web.evaluateJavascript("Boolean(window.onNativeBack && window.onNativeBack())",result->{if(!"true".equals(result))finish();});
    }
    @Override public void onRequestPermissionsResult(int request,String[] perms,int[] results){
        super.onRequestPermissionsResult(request,perms,results);callResume();
        if(request==LEGACY_IMAGES && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)
            Toast.makeText(this,"แตะบันทึกรูปอีกครั้งเพื่อเก็บภาพลงเครื่อง",Toast.LENGTH_LONG).show();
        if(request==NOTIFY) Alerts.reschedule(this);
    }
    @Override protected void onDestroy(){ready=false;if(web!=null){web.removeJavascriptInterface("Native");web.destroy();}super.onDestroy();}
    private class Bridge {
        @JavascriptInterface public String getState(){return Store.read(MainActivity.this).toString();}
        @JavascriptInterface public void put(String key,String value){
            try{Store.put(MainActivity.this,key,value);}catch(JSONException e){throw new IllegalArgumentException("Invalid planner item",e);}
        }
        @JavascriptInterface public void addEvent(String raw){
            try {
                JSONObject e=new JSONObject(raw);
                String type=e.getString("type");
                if(!"habit".equals(type) && !"note".equals(type))return;
                Store.event(MainActivity.this,type,e.optString("id"),e.optString("title"),e.optDouble("value",1),e.optString("note"),"",e.optString("unit"),System.currentTimeMillis(), "habit".equals(type) ? e.optString("icon") : null);
                Alerts.reschedule(MainActivity.this);
            } catch(JSONException ex){throw new IllegalArgumentException(ex);}
        }
        @JavascriptInterface public void startFocus(String id,String title,int minutes){Store.startFocus(MainActivity.this,id,title,minutes);}
        @JavascriptInterface public void finishFocus(){Store.finishFocus(MainActivity.this);}
        @JavascriptInterface public void extendFocus(int minutes){Store.extendFocus(MainActivity.this,minutes);}
        @JavascriptInterface public String takeRoute(){
            synchronized(MainActivity.this){String route=pendingRoute; pendingRoute=""; return route;}
        }
        @JavascriptInterface public boolean guardEnabled(){
            AccessibilityManager manager=getSystemService(AccessibilityManager.class);
            if(manager==null)return false;
            for(AccessibilityServiceInfo info:manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                ComponentName component=ComponentName.unflattenFromString(info.getId());
                if(component!=null && getPackageName().equals(component.getPackageName()) && GuardService.class.getName().equals(component.getClassName()))return true;
            }
            return false;
        }
        @JavascriptInterface public void openGuardSettings(){
            runOnUiThread(()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }
        @JavascriptInterface public boolean notificationsEnabled(){return getSystemService(NotificationManager.class).areNotificationsEnabled();}
        @JavascriptInterface public void requestNotifications(){
            runOnUiThread(()->{
                if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                    boolean asked=getPreferences(MODE_PRIVATE).getBoolean("askedNotify",false);
                    if(!asked || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)){
                        getPreferences(MODE_PRIVATE).edit().putBoolean("askedNotify",true).apply();
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFY);
                        return;
                    }
                }
                if(!notificationsEnabled())startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
            });
        }
        @JavascriptInterface public String getApps(){
            PackageManager pm=getPackageManager();
            Set<String> except=new HashSet<>(Arrays.asList(getPackageName(),"android","com.android.settings","com.android.systemui","com.android.permissioncontroller","com.google.android.permissioncontroller"));
            TelecomManager telecom=getSystemService(TelecomManager.class);
            if(telecom!=null)except.add(telecom.getDefaultDialerPackage());
            for(ResolveInfo home:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0))except.add(home.activityInfo.packageName);
            List<ResolveInfo> infos=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
            infos.sort(new ResolveInfo.DisplayNameComparator(pm));
            JSONArray items=new JSONArray();
            for(ResolveInfo info:infos) {
                String pkg=info.activityInfo.packageName;
                if(!except.add(pkg))continue;
                JSONObject app=new JSONObject();
                try{app.put("pkg",pkg);app.put("name",info.loadLabel(pm).toString());items.put(app);}catch(JSONException ignored){}
            }
            return items.toString();
        }
        @JavascriptInterface public String saveImage(String raw,String requestedName) {
            if(Build.VERSION.SDK_INT<29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
                runOnUiThread(()->requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},LEGACY_IMAGES));
                return "กรุณาอนุญาตให้บันทึกภาพ แล้วแตะบันทึกอีกครั้ง";
            }
            Uri dest=null;
            try {
                String payload=raw.startsWith("data:image/png;base64,")?raw.substring(22):raw;
                if(payload.length()>35_000_000) return "ภาพนี้ใหญ่เกินไป กรุณาลดรายละเอียดที่ส่งออก";
                byte[] bytes=Base64.decode(payload,Base64.DEFAULT);
                if(bytes.length<8 || bytes[0]!=(byte)137 || bytes[1]!=80 || bytes[2]!=78 || bytes[3]!=71) return "ไม่สามารถอ่านข้อมูลภาพ PNG";
                String name=requestedName.replaceAll("[^a-zA-Z0-9._-]","_");
                if(!name.endsWith(".png"))name+=".png";
                ContentValues values=new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,name);values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
                if(Build.VERSION.SDK_INT>=29) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/LockIn");
                    values.put(MediaStore.Images.Media.IS_PENDING,1);
                } else {
                    File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"LockIn");
                    if(!dir.isDirectory() && !dir.mkdirs())throw new IOException("folder");
                    File file=new File(dir,name);
                    if(file.exists())file=new File(dir,System.currentTimeMillis()+"-"+name);
                    values.put(MediaStore.Images.Media.DATA,file.getAbsolutePath());
                }
                dest=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                if(dest==null)throw new IOException("gallery");
                try(OutputStream out=getContentResolver().openOutputStream(dest)){
                    if(out==null)throw new IOException("gallery");out.write(bytes);
                }
                if(Build.VERSION.SDK_INT>=29) {
                    values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);
                    getContentResolver().update(dest,values,null,null);
                }
                return "";
            }catch(Exception problem) {
                if(dest!=null)getContentResolver().delete(dest,null,null);
                return "บันทึกรูปไม่ได้ กรุณาตรวจพื้นที่ว่างแล้วลองอีกครั้ง";
            }
        }
    }
}
