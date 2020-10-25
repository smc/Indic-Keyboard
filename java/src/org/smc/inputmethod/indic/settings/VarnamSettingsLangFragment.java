/*
 * Copyright (C) 2020, Subin Siby <mail@subinsb.com>
 * Licensed under the Apache License, Version 2.0
 */

package org.smc.inputmethod.indic.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import com.android.inputmethod.latin.R;
import com.varnamproject.varnam.Varnam;
import com.varnamproject.varnam.VarnamLibrary;

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
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

/**
 * "Varnam" language settings sub screen.
 *
 * This settings sub screen handles the following varnam preferences.
 * - TODO Language download
 * - Import words
 */
public final class VarnamSettingsLangFragment extends PreferenceFragmentCompat {
    private static String TAG = "VarnamSettings";

    private static int PICK_VARNAM_PACK_APP = 1;
    private static int PICK_VARNAM_CORPUS_FILE = 2;
    private static int PICK_VARNAM_TRAINED_FILE = 3;
    private static int PICK_VARNAM_TRAINED_EXPORT_DIR = 4;

    private VarnamIndicKeyboard.Scheme scheme;
    private boolean installed = false; // Whether VST is available to use

    private LinearLayout status;
    private ProgressBar progressBar;
    private TextView logTextView;

    private Thread learnThread;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.prefs_screen_varnam_lang);

        status = getActivity().findViewById(R.id.varnam_import_file_status);
        progressBar = getActivity().findViewById(R.id.varnam_import_file_progress);
        logTextView = getActivity().findViewById(R.id.varnam_import_file_log);

        String id = getArguments().getString("id");
        scheme = VarnamIndicKeyboard.schemes.get(id);
        installed = VarnamIndicKeyboard.isSchemeInstalled(id, getContext());

        PreferenceCategory packCategory = findPreference("pref_varnam_category_pack");
        packCategory.setTitle(getString(R.string.pref_varnam_category_pack, scheme.name));

        setups();

        if (savedInstanceState != null) {
            int statusVisibility = savedInstanceState.getInt("statusVisibility");
            if (status != null) {
                status.setVisibility(statusVisibility);
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        savedInstanceState.putInt("statusVisibility", status.getVisibility());
    }

    @Override
    public void onStop() {
        super.onStop();
        if (learnThread != null) {
            learnThread.interrupt();
            learnThread = null;
            logTextView.setText("");
            status.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == PICK_VARNAM_PACK_APP && resultCode == Activity.RESULT_OK) {
            // TODO allow user to pick individual packs to import
            // get array list
            ArrayList<Uri> uriArrayList = resultData.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            final ArrayList<Uri> packURIs = new ArrayList<>();

            if (uriArrayList != null) {
                for (int i = 0; i < uriArrayList.size(); i++) {
                    // Get the file's content URI
                    Uri uri = uriArrayList.get(i);
                    String fileName = uri.getLastPathSegment();
                    try {
                        Log.i(TAG, "URI: " + uri.toString() + " fileName: " + fileName);

                        if (fileName.equals(scheme.id + ".vst")) {
                            logTextView.setText("");
                            log(getString(R.string.varnam_import_vst, scheme.name));

                            AssetFileDescriptor afd = getContext().getContentResolver().openAssetFileDescriptor(uri, "r");

                            // Create dest filename and copy
                            File varnamFolder = VarnamIndicKeyboard.getVarnamFolder(scheme.id, getContext());
                            File vstFile = new File(varnamFolder.getAbsolutePath(), scheme.id + ".vst");

                            FileChannel sourceChannel = new FileInputStream(afd.getFileDescriptor()).getChannel();
                            FileChannel destinationChannel = new FileOutputStream(vstFile).getChannel();

                            sourceChannel.transferTo(afd.getStartOffset(), afd.getLength(), destinationChannel);
                        } else if (!fileName.equals("packs.json")) {
                            packURIs.add(uri);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, Log.getStackTraceString(e));
                        // Break loop at first exception
                        break;
                    }
                }
                status.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < packURIs.size(); i++) {
                            if (Thread.currentThread().isInterrupted()) {
                                return;
                            }
                            // Get the file's content URI
                            Uri uri = packURIs.get(i);
                            importFromFile(uri, i + 1, packURIs.size());
                        }
                        installed = true;
                        progressBar.post(new Runnable() {
                            @Override
                            public void run() {
                                setups();
                            }
                        });
                    }
                };
                learnThread = new Thread(runnable);
                learnThread.start();
            }
        } else if (requestCode == PICK_VARNAM_CORPUS_FILE && resultCode == Activity.RESULT_OK) {
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
        } else if (requestCode == PICK_VARNAM_TRAINED_FILE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                final Uri uri = resultData.getData();

                status.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
                logTextView.setText("");
                log(getString(R.string.varnam_import_copy, uri.getPath()));

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        importFromFile(uri, 1, 1);
                    }
                };
                learnThread = new Thread(runnable);
                learnThread.start();
            }
        } else if (requestCode == PICK_VARNAM_TRAINED_EXPORT_DIR && resultCode == Activity.RESULT_OK) {
            if (resultData != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                final Uri uri = resultData.getData();
                final String path = FileUtil.getFullPathFromTreeUri(uri, getContext());

                status.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
                logTextView.setText("");
                log(getString(R.string.varnam_export_begin, path));

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        exportTrained(path);
                    }
                };
                learnThread = new Thread(runnable);
                learnThread.start();
            }
        } else {
            Log.e(TAG, "Activity returned fail");
        }
    }

    private void log(String msg) {
        logTextView.append(msg + "\n");
    }

    private void learnFromFile(Uri uri) {
        try {
            // We're copying file to avoid asking external storage permission to read file
            // TODO better/alternate way to do this ?
            final String path = copyUriAndGetPath(uri);
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
                    log("Removing cached file.");
                    new File(path).delete();

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
    private void importFromFile(Uri uri, final int index, final int total) {
        try {
            // We're copying file to avoid asking external storage permission to read file
            // TODO better/alternate way to do this ?
            final String path = copyUriAndGetPath(uri);
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
                    log(getString(R.string.varnam_import_running, index, total));
                }
            });

            Log.d("Varnam", "Trained file : " + path);
            varnam.importFromFile(path);

            progressBar.post(new Runnable() {
                @Override
                public void run() {
                    log("Removing cached file.");
                    new File(path).delete();

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

    private void exportTrained(String path) {
        try {
            Varnam varnam = VarnamIndicKeyboard.makeVarnam(scheme.id, getContext());

            progressBar.post(new Runnable() {
                @Override
                public void run() {
                    progressBar.setIndeterminate(true);
                    log(getString(R.string.varnam_export_running));
                }
            });

            Log.d("Varnam", "Export directory: " + path);
            Log.d("Varnam", varnam.transliterate("aaaa").get(0).getText());
            varnam.exportFull(path, new VarnamLibrary.ExportCallback() {
                @Override
                public void invoke(int total_words, final int total_processed, String current_word) {
                    final int progress = (int) ((float) total_processed / total_words * 100);
                    progressBar.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setIndeterminate(false);
                            progressBar.setProgress(progress);
                        }
                    });
                }
            });

            log(getString(R.string.varnam_export_completed));
        } catch (Exception e) {
            Log.e("VarnamExport", e.getMessage());

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

    private void setups() {
        setupVarnamPackAppInstall();
        setupVarnamImportFile();
        setupVarnamImportTrained();
        setupVarnamExportTrained();
    }

    /**
     * Shows a Install Language pack button at the top if lang not available
     * Otherwise, shows an update button
     * Thanks user207064 https://stackoverflow.com/a/31586010/1372424
     * Licensed under CC-BY-SA 3.0
     */
    private void setupVarnamPackAppInstall() {
        Preference packInstallButton = findPreference("pref_varnam_import_pack");

        packInstallButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent mRequestFileIntent = new Intent(Intent.ACTION_PICK);
                mRequestFileIntent.setPackage("com.varnamproject.pack." + scheme.id);
                mRequestFileIntent.setType("application/octet-stream");
                try {
                    startActivityForResult(mRequestFileIntent, PICK_VARNAM_PACK_APP);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getContext(), getString(R.string.varnam_pack_app_unavailable, scheme.name), Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
    }

    private void setupVarnamImportFile() {
        Preference filePicker = findPreference("pref_varnam_import_file");

        filePicker.setEnabled(installed);
        if (!installed) {
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

    private void setupVarnamImportTrained() {
        Preference filePicker = findPreference("pref_varnam_import_trained");

        filePicker.setEnabled(installed);
        if (!installed) {
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

                startActivityForResult(intent, PICK_VARNAM_TRAINED_FILE);
                return true;
            }
        });
    }

    private void setupVarnamExportTrained() {
        Preference dirPicker = findPreference("pref_varnam_export_trained");

        // Directory picker is not available in older Android version
        if (!installed || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            dirPicker.setEnabled(false);
            return;
        }
        dirPicker.setEnabled(true);

        dirPicker.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = null;
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

                startActivityForResult(intent, PICK_VARNAM_TRAINED_EXPORT_DIR);
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
