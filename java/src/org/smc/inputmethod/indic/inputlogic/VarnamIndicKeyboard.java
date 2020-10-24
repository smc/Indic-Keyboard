/*
 * Copyright (C) 2020, Subin Siby <mail@subinsb.com>
 * Licensed under the Apache License, Version 2.0
 */

package org.smc.inputmethod.indic.inputlogic;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.android.inputmethod.latin.R;
import com.varnamproject.varnam.Varnam;
import com.varnamproject.varnam.VarnamException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VarnamIndicKeyboard {
    /**
     * Make a varnam handle according to Indic Keyboard
     * @param scheme  The scheme/varnam symbol table identifier
     * @param context Android context
     * @return Varnam object (this is not the same object as this class)
     */
    public static Varnam makeVarnam(String scheme, Context context) throws Exception {
        Varnam varnam;

        File varnamFolder = getVarnamFolder(scheme, context);

        File vstFile = new File(varnamFolder.getAbsolutePath(), scheme + ".vst");
        String learningsFile = varnamFolder.getAbsolutePath() + "/" + scheme + ".vst.learnings";

        if (!vstFile.exists()) {
            throw new Exception(context.getString(R.string.varnam_vst_missing, scheme));
        }

        varnam = new Varnam(vstFile.getAbsolutePath(), learningsFile);
        return varnam;
    }

    public static File getVarnamFolder(String scheme, Context context) {
        // More about storage: https://stackoverflow.com/a/29404440/1372424
        File varnamFolder = new File(context.getExternalFilesDir(null).getPath() + "/varnam", scheme);

        if (!varnamFolder.exists()) {
            varnamFolder.mkdirs();
        }
        return varnamFolder;
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
        // TODO get author, version value from VST file if available
        put("varnam-bn", new Scheme("bn", "bn", "Bangla", "", "0.1", ""));
        put("varnam-ka", new Scheme("kn", "kn", "Kannada", "", "0.1", ""));
        put("varnam-gu", new Scheme("gu", "gu", "Gujarati", "", "0.1", ""));
        put("varnam-hi", new Scheme("hi", "hi", "Hindi", "", "0.1", ""));
        put("varnam-ml", new Scheme("ml", "ml", "Malayalam", "", "0.1", ""));
        put("varnam-ta", new Scheme("ta", "ta", "Tamil", "", "0.1", ""));
        put("varnam-te", new Scheme("te", "te", "Telugu", "", "0.1", ""));
    }};

    public static HashMap<String, Scheme> getInstalledSchemes(Context context) {
        HashMap<String, Scheme> installedSchemes = new HashMap<String, Scheme>();
        for (Map.Entry<String, Scheme> entry : schemes.entrySet()) {
            String keyboardID = entry.getKey();
            Scheme scheme = entry.getValue();

            try {
                makeVarnam(scheme.id, context);
                installedSchemes.put(keyboardID, scheme);
            } catch (Exception e) {
                Log.d("VarnamIndicKeyboard", keyboardID + " is not installed.");
            }
        }
        return installedSchemes;
    }

    public static boolean isSchemeInstalled(String keyboardID, Context context) {
        return getInstalledSchemes(context).containsKey(keyboardID);
    }
}
