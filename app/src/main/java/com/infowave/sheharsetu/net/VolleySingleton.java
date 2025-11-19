package com.infowave.sheharsetu.net;

import android.content.Context;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public class VolleySingleton {
    private static volatile VolleySingleton instance;
    private final RequestQueue queue;

    private VolleySingleton(Context ctx) {
        queue = Volley.newRequestQueue(ctx.getApplicationContext());
    }

    public static VolleySingleton getInstance(Context ctx) {
        if (instance == null) {
            synchronized (VolleySingleton.class) {
                if (instance == null) instance = new VolleySingleton(ctx);
            }
        }
        return instance;
    }

    public <T> void add(Request<T> req) {
        queue.add(req);
    }
}
