/*
 * Copyright (C) 2020, Subin Siby <mail@subinsb.com>
 * Licensed under the Apache License, Version 2.0
 */

package org.smc.inputmethod.indic.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.inputmethod.latin.R;
import com.varnamproject.varnam.Varnam;

import org.smc.inputmethod.indic.inputlogic.VarnamIndicKeyboard;
import org.xml.sax.InputSource;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;

/**
 * "Varnam" language settings sub screen.
 *
 * This settings sub screen handles the following varnam preferences.
 * - TODO Language download
 * - Import words
 */
public final class VarnamSettingsLangFragment extends PreferenceFragmentCompat {
    private static int PICK_VARNAM_CORPUS_FILE = 1;
    private static int PICK_VARNAM_PACK_FILE = 2;

    private VarnamIndicKeyboard.Scheme scheme;
    private boolean installed = false; // Whether VST is available to use

    private LinearLayout status;
    private ProgressBar progressBar;
    private TextView logTextView;

    private Thread learnThread;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.prefs_screen_varnam_lang);

        status = getActivity().findViewById(R.id.varnam_import_file_status);
        progressBar = getActivity().findViewById(R.id.varnam_import_file_progress);
        logTextView = getActivity().findViewById(R.id.varnam_import_file_log);

        String id = getArguments().getString("id");
        scheme = VarnamIndicKeyboard.schemes.get(id);
        installed = VarnamIndicKeyboard.isSchemeInstalled(id, getContext());

        setupVarnamImportFile();
        setupVarnamImportPack();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (learnThread != null) {
            learnThread.interrupt();
        }
        status.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == PICK_VARNAM_CORPUS_FILE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                final Uri uri = resultData.getData();

                status.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
                logTextView.setText("");
                log(getString(R.string.varnam_import_copy, uri.getPath()));

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        learnFromFile(uri);
                    }
                };
                learnThread = new Thread(runnable);
                learnThread.start();
            }
        } else if (requestCode == PICK_VARNAM_PACK_FILE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                final Uri uri = resultData.getData();

                status.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
                logTextView.setText("");
                log(getString(R.string.varnam_import_copy, uri.getPath()));

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        importFromFile(uri);
                    }
                };
                learnThread = new Thread(runnable);
                learnThread.start();
            }
        }
    }

    private void log(String msg) {
        logTextView.append(msg + "\n");
    }

    private void learnFromFile(Uri uri) {
        try {
            // We're copying file to avoid asking external storage permission to read file
            // TODO better/alternate way to do this ?
            String path = copyUriAndGetPath(uri);
            Varnam varnam = VarnamIndicKeyboard.makeVarnam(scheme.id, getContext());

            progressBar.post(new Runnable() {
                @Override
                public void run() {
                    progressBar.setIndeterminate(true);
                    log(getString(R.string.varnam_import_running));
                }
            });

            Log.d("Varnam", "Learn file : " + path);
            final Varnam.LearnStatus status = varnam.learnFromFile(path, null);

            progressBar.post(new Runnable() {
                @Override
                public void run() {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(100);
                    log(getString(R.string.varnam_import_completed));
                    log(getString(R.string.varnam_import_status, status.total_words, status.failed));
                }
            });
        } catch (Exception e) {
            Log.e("VarnamLearn", e.getMessage());

            final Exception err = e;
            progressBar.post(new Runnable() {
                @Override
                public void run() {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(50);
                    log(err.getMessage());
                }
            });
        }
    }

    /**
     * Import a pack/trained/varnam exported file
     * @param uri
     */
    private void importFromFile(Uri uri) {
        try {
            // We're copying file to avoid asking external storage permission to read file
            // TODO better/alternate way to do this ?
            String path = copyUriAndGetPath(uri);
            Varnam varnam = VarnamIndicKeyboard.makeVarnam(scheme.id, getContext());

            if (isGZipFile(path)) {
                // uncompress to the same file
                InputStream in = getActivity().getContentResolver().openInputStream(uri);
                unzip(in, path);
            }

            progressBar.post(new Runnable() {
                @Override
                public void run() {
                    progressBar.setIndeterminate(true);
                    log(getString(R.string.varnam_import_running));
                }
            });

            Log.d("Varnam", "Pack file : " + path);
            varnam.importFromFile(path);

            progressBar.post(new Runnable() {
                @Override
                public void run() {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(100);
                    log(getString(R.string.varnam_import_completed));
                }
            });
        } catch (Exception e) {
            Log.e("VarnamLearn", e.getMessage());

            final Exception err = e;
            progressBar.post(new Runnable() {
                @Override
                public void run() {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(50);
                    log(err.getMessage());
                }
            });
        }
    }

    private void setupVarnamImportFile() {
        Preference filePicker = findPreference("pref_varnam_import_file");

        if (!installed) {
            filePicker.setEnabled(false);
            return;
        }

        filePicker.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.setType("*/*");
                } else {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    // TODO might not work in some file explorers ?
                    // https://stackoverflow.com/questions/8945531/pick-any-kind-of-file-via-an-intent-in-android
                    intent.putExtra("CONTENT_TYPE", "*/*");
                }

                startActivityForResult(intent, PICK_VARNAM_CORPUS_FILE);
                return true;
            }
        });
    }

    private void setupVarnamImportPack() {
        Preference filePicker = findPreference("pref_varnam_import_pack");

        if (!installed) {
            filePicker.setEnabled(false);
            return;
        }

        filePicker.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.setType("*/*");
                } else {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    // TODO might not work in some file explorers ?
                    // https://stackoverflow.com/questions/8945531/pick-any-kind-of-file-via-an-intent-in-android
                    intent.putExtra("CONTENT_TYPE", "*/*");
                }

                startActivityForResult(intent, PICK_VARNAM_PACK_FILE);
                return true;
            }
        });
    }

    private String copyUriAndGetPath(Uri uri) throws Exception {
        InputStream in = getActivity().getContentResolver().openInputStream(uri);

        File outFile = File.createTempFile("varnam", "", getActivity().getCacheDir());
        OutputStream out = new FileOutputStream(outFile);

        copyFile(in, out);

        in.close();
        out.close();

        return outFile.getAbsolutePath();
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    /**
     * Check if a file is gzip format
     * @param path
     * @return
     */
    public static boolean isGZipFile(String path) {
        int magic = 0;
        try {
            RandomAccessFile raf = new RandomAccessFile(path, "r");
            magic = raf.read() & 0xff | ((raf.read() << 8) & 0xff00);
            raf.close();
        } catch (Exception e) {
            Log.e("gzip", e.getMessage());
        }
        return magic == GZIPInputStream.GZIP_MAGIC;
    }

    /**
     * Unzip a file
     * CC-BY-SA 3.0 https://stackoverflow.com/a/10633905/1372424
     * @param stream
     * @param path
     * @return
     */
    private boolean unzip(InputStream stream, String path) {
        try {
            InputStream compressedInputStream = new GZIPInputStream(stream);
            InputSource inputSource = new InputSource(compressedInputStream);
            InputStream inputStream = new BufferedInputStream(inputSource.getByteStream());

            File outputFile = new File(path);
            OutputStream outputStream = new FileOutputStream(outputFile.getAbsoluteFile());

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.flush();
            outputStream.close();
            inputStream.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
