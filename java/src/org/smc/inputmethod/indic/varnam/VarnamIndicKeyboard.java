/*
 * Copyright (C) 2020, Subin Siby <mail@subinsb.com>
 * Licensed under the Apache License, Version 2.0
 */

package org.smc.inputmethod.indic.varnam;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class VarnamIndicKeyboard {
    /**
     * Make a varnam handle according to Indic Keyboard
     * @param schemeID The scheme/varnam symbol table identifier
     * @param context  Android context
     * @return Varnam object (this is not the same object as this class)
     */
    public static Varnam makeVarnam(String schemeID, Context context, VarnamCallback cb) {
        return new Varnam(schemeID, context, cb);
    }

    public static class Scheme {
        public String id; // Scheme ID doesn't necessarily be the same as language
        public String lang;
        public String name;
        public String description;
        public String version;
        public String author;

        public Scheme(String id, String lang, String name, String description, String version, String author) {
            this.id = id;
            this.lang = lang;
            this.name = name;
            this.description = description;
            this.version = version;
            this.author = author;
        }
    }

    public static final HashMap<String, Scheme> schemes = new HashMap<String, Scheme>() {{
//        put("varnam-bn", new Scheme("bn", "bn", "Bangla", "", "0.1", ""));
//        put("varnam-kn", new Scheme("kn", "kn", "Kannada", "", "0.1", ""));
//        put("varnam-gu", new Scheme("gu", "gu", "Gujarati", "", "0.1", ""));
//        put("varnam-hi", new Scheme("hi", "hi", "Hindi", "", "0.1", ""));
        put("varnam-ml", new Scheme("ml", "ml", "Malayalam", "", "0.1", ""));
//        put("varnam-ta", new Scheme("ta", "ta", "Tamil", "", "0.1", ""));
//        put("varnam-te", new Scheme("te", "te", "Telugu", "", "0.1", ""));
    }};
}
