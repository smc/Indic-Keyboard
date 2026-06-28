/*
 * Copyright 2026, Jishnu Mohan <jishnu7@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.smc.inputmethod.indic.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.inputmethod.latin.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import org.smc.inputmethod.indic.varnam.VarnamDownloadManager;
import org.smc.inputmethod.indic.varnam.VarnamDownloadManager.Scheme;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VarnamSettingsFragment extends Fragment implements VarnamDownloadManager.Listener {

    private VarnamDownloadManager downloadManager;
    private ViewGroup listView;
    private View loadingView;
    private View refreshBar;
    private final Map<String, Scheme> schemesByLang = new HashMap<>();
    private final Map<String, View> cards = new HashMap<>();
    private final java.util.Set<String> downloading = new java.util.HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
            @Nullable final ViewGroup container, @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_varnam, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        listView = view.findViewById(R.id.varnam_list);
        loadingView = view.findViewById(R.id.varnam_loading);
        refreshBar = view.findViewById(R.id.varnam_refresh);
        downloadManager = new VarnamDownloadManager(view.getContext());
        downloadManager.setListener(this);

        // Render the last-known languages immediately so the page isn't blank, then refresh.
        final List<Scheme> cached = downloadManager.cachedSchemes();
        if (!cached.isEmpty()) {
            loadingView.setVisibility(View.GONE);
            renderSchemes(cached);
        }
        refreshBar.setVisibility(View.VISIBLE);
        downloadManager.loadIndex();
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.varnam_page_title);
    }

    @Override
    public void onDestroyView() {
        if (downloadManager != null) {
            downloadManager.setListener(null);
        }
        super.onDestroyView();
    }

    // ---- Listener ----

    @Override
    public void onIndexLoaded(final List<Scheme> schemes) {
        if (!isAdded()) return;
        refreshBar.setVisibility(View.GONE);
        loadingView.setVisibility(View.GONE);
        renderSchemes(schemes);
    }

    /** Adds cards for new languages and refreshes existing ones (except those mid-download). */
    private void renderSchemes(final List<Scheme> schemes) {
        final LayoutInflater inflater = LayoutInflater.from(listView.getContext());
        for (final Scheme scheme : schemes) {
            schemesByLang.put(scheme.lang, scheme);
            View card = cards.get(scheme.lang);
            if (card == null) {
                card = inflater.inflate(R.layout.varnam_language_card, listView, false);
                cards.put(scheme.lang, card);
                listView.addView(card);
            }
            if (!downloading.contains(scheme.lang)) {
                bindIdle(card, scheme);
            }
        }
    }

    @Override
    public void onProgress(final String lang, final int percent) {
        downloading.add(lang);
        final View card = cards.get(lang);
        if (card != null) bindProgress(card, percent);
    }

    @Override
    public void onInstalled(final String lang) {
        downloading.remove(lang);
        final View card = cards.get(lang);
        final Scheme scheme = schemesByLang.get(lang);
        if (card != null && scheme != null) bindIdle(card, scheme);
        if (isAdded() && scheme != null && getView() != null) {
            Snackbar.make(getView(), getString(R.string.varnam_installed_ready, scheme.name),
                    Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onError(final String lang, final String message) {
        if (lang != null) {
            downloading.remove(lang);
            final View card = cards.get(lang);
            final Scheme scheme = schemesByLang.get(lang);
            if (card != null && scheme != null) bindIdle(card, scheme);
        } else if (refreshBar != null) {
            // Index refresh failed; stop the top spinner and keep whatever was cached.
            refreshBar.setVisibility(View.GONE);
            if (cards.isEmpty() && loadingView != null) {
                ((TextView) loadingView).setText(R.string.varnam_download_error_generic);
            }
        }
        if (isAdded() && getView() != null) {
            Snackbar.make(getView(),
                    message == null ? getString(R.string.varnam_download_error_generic) : message,
                    Snackbar.LENGTH_LONG).show();
        }
    }

    // ---- Card binding ----

    private void bindIdle(final View card, final Scheme scheme) {
        final TextView name = card.findViewById(R.id.varnam_card_name);
        final TextView status = card.findViewById(R.id.varnam_card_status);
        final MaterialButton action = card.findViewById(R.id.varnam_card_action);
        final LinearProgressIndicator progress = card.findViewById(R.id.varnam_card_progress);

        name.setText(scheme.name);
        progress.setVisibility(View.GONE);
        action.setVisibility(View.VISIBLE);

        final int installed = VarnamDownloadManager.installedVersion(card.getContext(), scheme.lang);
        final String size = formatSize(scheme.size);
        if (installed < 0) {
            status.setText(getString(R.string.varnam_status_available, size, scheme.version));
            action.setText(R.string.varnam_download);
            action.setIconResource(R.drawable.ic_varnam_download);
            action.setOnClickListener(v -> startDownload(scheme));
        } else if (scheme.version > installed) {
            status.setText(getString(R.string.varnam_status_update, size));
            action.setText(R.string.varnam_update);
            action.setIconResource(R.drawable.ic_varnam_update);
            action.setOnClickListener(v -> startDownload(scheme));
        } else {
            status.setText(getString(R.string.varnam_status_installed, installed));
            action.setText(R.string.varnam_delete);
            action.setIconResource(R.drawable.ic_varnam_delete);
            action.setOnClickListener(v -> confirmRemove(scheme));
        }
    }

    private void bindProgress(final View card, final int percent) {
        final TextView status = card.findViewById(R.id.varnam_card_status);
        final MaterialButton action = card.findViewById(R.id.varnam_card_action);
        final LinearProgressIndicator progress = card.findViewById(R.id.varnam_card_progress);

        action.setVisibility(View.GONE);
        final boolean indeterminate = (percent == VarnamDownloadManager.INSTALLING);
        // The indicator can't switch indeterminate mode while visible, so hide it first.
        if (progress.isIndeterminate() != indeterminate) {
            progress.setVisibility(View.GONE);
            progress.setIndeterminate(indeterminate);
        }
        progress.setVisibility(View.VISIBLE);
        if (indeterminate) {
            status.setText(R.string.varnam_installing);
        } else {
            status.setText(getString(R.string.varnam_downloading, percent));
            progress.setProgressCompat(percent, true);
        }
    }

    private void startDownload(final Scheme scheme) {
        downloading.add(scheme.lang);
        final View card = cards.get(scheme.lang);
        if (card != null) bindProgress(card, 0);
        downloadManager.download(scheme);
    }

    private void confirmRemove(final Scheme scheme) {
        if (getContext() == null) return;
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(scheme.name)
                .setMessage(R.string.varnam_delete_confirm)
                .setPositiveButton(R.string.varnam_delete, (d, w) -> {
                    downloadManager.delete(scheme.lang);
                    final View card = cards.get(scheme.lang);
                    if (card != null) bindIdle(card, scheme);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String formatSize(final long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%d KB", Math.max(1, bytes / 1024));
        }
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
