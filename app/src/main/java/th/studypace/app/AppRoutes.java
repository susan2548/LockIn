package th.studypace.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.UUID;

/** Launcher intents from other apps may open the planner, but cannot start a task. */
final class AppRoutes {
    private AppRoutes() {}
    private static synchronized String token(Context context) {
        SharedPreferences prefs=context.getSharedPreferences("internal_navigation",Context.MODE_PRIVATE);
        String value=prefs.getString("token",null);
        if(value==null) {
            value=UUID.randomUUID().toString();
            prefs.edit().putString("token",value).commit();
        }
        return value;
    }
    static Intent open(Context context,String route) {
        return new Intent(context,MainActivity.class).putExtra("route",route).putExtra("route_token",token(context));
    }
    static boolean isInternal(Context context,Intent intent) {
        String supplied=intent.getStringExtra("route_token");
        return supplied!=null && supplied.equals(token(context));
    }
}
