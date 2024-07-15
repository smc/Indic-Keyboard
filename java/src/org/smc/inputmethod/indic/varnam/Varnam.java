package org.smc.inputmethod.indic.varnam;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

import com.varnamproject.govarnam.Suggestion;

public class Varnam {
    public final static int MSG_SETUP = 0;
    public final static int MSG_INIT = 1;
    public final static int MSG_TRANSLITERATE = 2;
    public final static int MSG_CANCEL = 3;
    public final static int MSG_LEARN = 4;
    public final static int MSG_UNLEARN = 5;
    public final static int MSG_SET_DICTIONARY_SUGGESTIONS_LIMIT = 6;
    public final static int MSG_SET_TOKENIZER_SUGGESTIONS_LIMIT = 7;

    public final static String ERROR_VST_MISSING = "vst-missing";

    private String schemeID;
    private Messenger messenger;
    private boolean isBound;
    private VarnamCallback onConnectCallback;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            messenger = new Messenger(service);
            isBound = true;

            Log.d("varnam", "connected to service");
            if (onConnectCallback != null) {
                Bundle data = new Bundle();
                data.putString("schemeID", schemeID);
                try {
                    Message msg = Message.obtain(null, MSG_INIT, data);
                    msg.replyTo = new Messenger(new Handler(Looper.getMainLooper()) {
                        @Override
                        public void handleMessage(Message msg) {
                            Bundle data = (Bundle) msg.obj;
                            String error = data.getString("error");
                            if (error != null) {
                                Log.d("varnam", error);
                                onConnectCallback.onError(error);
                            } else {
                                Log.d("varnam", "scheme inited");
                                onConnectCallback.onResult(data.getBoolean("setting_learn"));
                            }
                        }
                    });
                    messenger.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    // For getting errors from varnam app, need a default replyTo
    public Messenger getDefaultReplyTo() {
        return new Messenger(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Bundle data = (Bundle) msg.obj;
                data.setClassLoader(Suggestion.class.getClassLoader());
                String error = data.getString("error");
                if (error != null) {
                    new VarnamCallback().onError(error);
                }
            }
        });
    }

    public Varnam(String schemeIDx, Context context, VarnamCallback cb) {
        schemeID = schemeIDx;
        onConnectCallback = cb;
        // Bind to the remote service
        Intent intent = new Intent();
        intent.setClassName("org.smc.inputmethod.indic.varnam", "org.smc.inputmethod.indic.varnam.VarnamService");
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    public void close(Context context) {
        context.unbindService(connection);
    }

    // Setup a scheme
    public void setupScheme(String schemeID) {
        try {
            Bundle data = new Bundle();
            data.putString("schemeID", schemeID);
            Message msg = Message.obtain(null, MSG_SETUP, data);
            msg.replyTo = getDefaultReplyTo();
            this.messenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setDictionarySuggestionsLimit(int limit) {
        try {
            Bundle data = new Bundle();
            data.putInt("limit", limit);
            Message msg = Message.obtain(null, MSG_SET_DICTIONARY_SUGGESTIONS_LIMIT, data);
            msg.replyTo = getDefaultReplyTo();
            this.messenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setTokenizerSuggestionsLimit(int limit) {
        try {
            Bundle data = new Bundle();
            data.putInt("limit", limit);
            Message msg = Message.obtain(null, MSG_SET_TOKENIZER_SUGGESTIONS_LIMIT, data);
            msg.replyTo = getDefaultReplyTo();
            this.messenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void transliterate(int id, String input, VarnamCallback cb) {
        try {
            Bundle data = new Bundle();
            data.putInt("id", id);
            data.putString("input", input);

            Message msg = Message.obtain(null, MSG_TRANSLITERATE, data);
            msg.replyTo = new Messenger(new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    Bundle data = (Bundle) msg.obj;
                    data.setClassLoader(Suggestion.class.getClassLoader());
                    String error = data.getString("error");
                    if (error != null) {
                        cb.onError(error);
                    } else {
                        Parcelable[] result = data.getParcelableArray("result");
                        Suggestion[] sugs = new Suggestion[result.length];
                        for (int i = 0; i < result.length; i++) {
                            sugs[i] = (Suggestion) result[i];
                        }
                        cb.onResult(input, sugs);
                    }
                }
            });
            this.messenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void cancel(int id) {
        try {
            Bundle data = new Bundle();
            data.putInt("id", id);

            Message msg = Message.obtain(null, MSG_CANCEL, data);
            msg.replyTo = getDefaultReplyTo();
            this.messenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void learn(String input) {
        try {
            Bundle data = new Bundle();
            data.putString("input", input);

            Message msg = Message.obtain(null, MSG_LEARN, data);
            msg.replyTo = getDefaultReplyTo();
            this.messenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
