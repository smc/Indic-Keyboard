/*
 * Copyright (C) 2020, Subin Siby <mail@subinsb.com>
 * Licensed under the Apache License, Version 2.0
 */

package org.smc.inputmethod.indic.inputlogic;

import android.content.Context;
import android.util.Log;

import com.varnamproject.varnam.Varnam;
import com.varnamproject.varnam.VarnamException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class VarnamIndicKeyboard {
    /**
     * Make a varnam handle according to Indic Keyboard
     * @param scheme  The scheme/varnam symbol table identifier
     * @param context Android context
     * @return Varnam object (this is not the same object as this class)
     */
    public static Varnam makeVarnam(String scheme, Context context) {
        // TODO: make it a user accessible folder
        String varnamFolder = context.getFilesDir().getPath() + "/varnam";
        String vstFile = varnamFolder + "/" + scheme + ".vst";
        String learningsFile = varnamFolder + "/" + scheme + ".vst.learnings";

        Varnam varnam;
        try {
            varnam = new Varnam(vstFile, learningsFile);
            return varnam;
        } catch (VarnamException e) {
            Log.e("VarnamException", e.toString());
        }
        return null;
    }

    public static class Scheme {
        public String id; // Scheme ID doesn't necessarily be the same as language
        public String name;
        public String lang;

        public Scheme(String id, String name, String lang) {
            this.id = id;
            this.name = name;
            this.lang = lang;
        }
    }

    public static final HashMap<String, Scheme> schemes = new HashMap<String, Scheme>() {{
        put("varnam-ml", new Scheme("ml", "Malayalam", "ml"));
    }};
}
