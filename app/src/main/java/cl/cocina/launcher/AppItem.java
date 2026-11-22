package cl.cocina.launcher;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** Un mosaico de la caja de aplicaciones. */
public class AppItem {
    public static final int APP = 0;     // app instalada elegida por el usuario
    public static final int FIXED = 1;   // YouTube / Netflix (fijas)
    public static final int ADD = 2;     // botón "+"
    public static final int ALL = 3;     // botón "Todas"

    public final int type;
    public final String key;      // "paquete/actividad" para apps
    public final String label;
    public final Drawable icon;
    public final Intent intent;

    public AppItem(int type, String key, String label, Drawable icon, Intent intent) {
        this.type = type;
        this.key = key;
        this.label = label;
        this.icon = icon;
        this.intent = intent;
    }
}
