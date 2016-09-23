package com.caseykulm.letscache;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;

import java.io.IOException;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int SYNC_CALL = 10;
    private static final int ASYNC_CALL = 100;
    private static final int POLL_DELAY = 10 * 1000;

    /**
     * has response header max-age=604800
     */
    private static final String CACHEABLE_URL = "http://example.com";

    private Handler syncHandler;
    private Handler asyncHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        HandlerThread handlerThread = new HandlerThread("CacheWork");
        handlerThread.start();
        syncHandler = new SyncHandler(handlerThread.getLooper());
        asyncHandler = new AsyncHandler();

        final Switch syncSwitch = (Switch) findViewById(R.id.poll_sync_button);
        final Switch asyncSwitch = (Switch) findViewById(R.id.poll_async_button);
        final Button clearCacheButton = (Button) findViewById(R.id.clear_cache_button);

        syncSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    syncHandler.removeMessages(SYNC_CALL);
                } else {
                    Message message = syncHandler.obtainMessage();
                    message.what = SYNC_CALL;
                    message.obj = getClient(getApplicationContext());
                    syncHandler.sendMessage(message);
                }
            }
        });

        asyncSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    asyncHandler.removeMessages(ASYNC_CALL);
                } else {
                    Message message = asyncHandler.obtainMessage();
                    message.what = ASYNC_CALL;
                    AsyncStuff asyncStuff = new AsyncStuff();
                    asyncStuff.okHttpClient = getClient(getApplicationContext());
                    asyncStuff.callback = MainActivity.this.callback;
                    message.obj = asyncStuff;
                    asyncHandler.sendMessage(message);
                }
            }
        });

        clearCacheButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    getCache(getApplicationContext()).evictAll();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static class SyncHandler extends Handler {
        public SyncHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SYNC_CALL: {
                    OkHttpClient okHttpClient = (OkHttpClient) msg.obj;
                    Request request = new Request.Builder()
                            .url(CACHEABLE_URL)
                            .build();
                    Log.d(TAG, "calling with " + okHttpClient.hashCode());
                    try {
                        Response response = okHttpClient.newCall(request).execute();
                        Log.d(TAG, response.toString());
                        response.body().close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    Message message = obtainMessage();
                    message.what = SYNC_CALL;
                    message.obj = okHttpClient;
                    sendMessageDelayed(message, POLL_DELAY);
                    break;
                }
            }
        }
    }

    private static class AsyncHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ASYNC_CALL: {
                    AsyncStuff asyncStuff = (AsyncStuff) msg.obj;
                    OkHttpClient okHttpClient = (OkHttpClient) asyncStuff.okHttpClient;
                    okhttp3.Callback callback = (okhttp3.Callback) asyncStuff.callback;
                    Request request = new Request.Builder()
                            .url(CACHEABLE_URL)
                            .build();
                    Log.d(TAG, "calling with " + okHttpClient.hashCode());
                    okHttpClient.newCall(request).enqueue(callback);

                    Message message = obtainMessage();
                    message.what = ASYNC_CALL;
                    message.obj = asyncStuff;
                    sendMessageDelayed(message, POLL_DELAY);
                    break;
                }
            }
        }
    }

    private Callback callback = new okhttp3.Callback() {
        @Override
        public void onFailure(Call call, IOException e) {
            Log.e(TAG, "Failed to get", e);
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            Log.d(TAG, response.toString());
        }
    };

    private static class AsyncStuff {
        public OkHttpClient okHttpClient;
        public Callback callback;
    }

    private static OkHttpClient okHttpClient;
    private static Cache cache;

    public static Cache getCache(Context context) {
        if (cache == null) {
            cache = new Cache(context.getCacheDir(), 10L * 1024 * 1024);
        }
        return cache;
    }

    public static OkHttpClient getClient(Context context) {
        if (okHttpClient == null) {
            okHttpClient = new OkHttpClient.Builder()
                    .cache(getCache(context))
                    .build();
        }
        return okHttpClient;
    }
}
