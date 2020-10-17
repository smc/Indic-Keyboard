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

public class VarnamIndicKeyboard {
    /**
     * Make a varnam handle according to Indic Keyboard
     * @param scheme  The scheme/varnam symbol table identifier
     * @param context Android context
     * @return Varnam object (this is not the same object as this class)
     */
    public static Varnam makeVarnam(String scheme, Context context) throws Exception {
        Varnam varnam;

        // More about storage: https://stackoverflow.com/a/29404440/1372424
        File varnamFolder = new File(context.getExternalFilesDir(null).getPath() + "/varnam", scheme);

        if (!varnamFolder.exists()) {
            varnamFolder.mkdirs();
        }

        File vstFile = new File(varnamFolder.getAbsolutePath(), scheme + ".vst");
        String learningsFile = varnamFolder.getAbsolutePath() + "/" + scheme + ".vst.learnings";

        if (!vstFile.exists()) {
            throw new Exception(context.getString(R.string.varnam_vst_missing, scheme));
        }

        varnam = new Varnam(vstFile.getAbsolutePath(), learningsFile);
        return varnam;
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
        put("varnam-ml", new Scheme("ml", "ml", "Malayalam", "", "0.1", "Navaneeth KN"));
        put("varnam-ta", new Scheme("ta", "ta", "Tamil", "", "0.1", "Navaneeth KN/Kumaran Venkataraman"));
    }};
}
