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

package org.smc.inputmethod.indic.setup

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView

import com.android.inputmethod.latin.R

import org.smc.inputmethod.indic.languagepack.LanguagePackDownloadManager
import org.smc.inputmethod.indic.languagepack.LanguagePackDownloadManager.Pack

/** Drives the wizard's download step: kicks off language-pack downloads for the selected
 *  languages and keeps the status rows current. [onStateChanged] fires on every status change
 *  so the host can refresh its primary action. */
internal class PackDownloadController(
    private val context: Context,
    private val selection: LanguageSelection,
    private val onStateChanged: () -> Unit
) : LanguagePackDownloadManager.Listener {

    private var manager: LanguagePackDownloadManager? = null
    private val langs = ArrayList<String>()              // language codes to fetch
    private val pending = HashSet<String>()              // still downloading
    private val status = HashMap<String, CharSequence>()
    private val ready = HashSet<String>()
    private val rows = HashMap<String, View>()

    var started = false
        private set
    private var indexLoaded = false

    fun start() {
        if (started) {
            return
        }
        started = true
        langs.clear()
        for (language in selection.languages) {
            if (!selection.selectedLocales.contains(language.mLocale)) {
                continue
            }
            val code = selection.languageCode(language)
            // English ships with the app and has no downloadable pack — don't list it.
            if ("en" == code || langs.contains(code)) {
                continue
            }
            langs.add(code)
            status[code] = context.getString(R.string.su_pack_waiting)
        }
        if (langs.isEmpty()) {
            indexLoaded = true
            return
        }
        manager = LanguagePackDownloadManager(context).also {
            it.setListener(this)
            it.loadIndex()  // schemes arrive in onIndexLoaded, then we download
        }
    }

    fun buildList(list: SetupSelectionList) {
        list.clear()
        rows.clear()
        for (code in langs) {
            rows[code] = list.addRow(rowText(code), showSwitch = false)
            updateRow(code)
        }
    }

    fun allDone(): Boolean = indexLoaded && pending.isEmpty()

    fun detach() {
        manager?.setListener(null)  // downloads continue in the system DownloadManager
    }

    private fun rowText(code: String): CharSequence =
        if (ready.contains(code)) selection.displayName(code)
        else "${selection.displayName(code)} · ${status[code] ?: ""}"

    private fun updateRow(code: String) {
        val row = rows[code] ?: return
        row.findViewById<TextView>(R.id.selection_title).text = rowText(code)
        row.findViewById<ImageView>(R.id.selection_status_icon).visibility =
            if (ready.contains(code)) View.VISIBLE else View.GONE
    }

    private fun setStatus(code: String, text: CharSequence, isReady: Boolean = false) {
        status[code] = text
        if (isReady) ready.add(code)
        updateRow(code)
    }

    override fun onIndexLoaded(schemes: List<Pack>) {
        indexLoaded = true
        for (code in langs) {
            val pack = schemes.firstOrNull { code == it.lang }
            when {
                pack == null -> setStatus(code, context.getString(R.string.su_pack_unavailable))
                manager!!.ensureDownloaded(pack) -> {
                    pending.add(code)
                    setStatus(code, context.getString(R.string.su_pack_waiting))
                }
                else -> setStatus(code, context.getString(R.string.su_pack_ready), isReady = true)
            }
        }
        onStateChanged()
    }

    override fun onProgress(lang: String, percent: Int) {
        if (!langs.contains(lang)) return
        pending.add(lang)
        if (percent == LanguagePackDownloadManager.INSTALLING) {
            setStatus(lang, context.getString(R.string.language_pack_installing))
        } else {
            setStatus(lang, context.getString(R.string.language_pack_downloading, percent))
        }
        onStateChanged()
    }

    override fun onInstalled(lang: String) {
        if (!langs.contains(lang)) return
        pending.remove(lang)
        setStatus(lang, context.getString(R.string.su_pack_ready), isReady = true)
        onStateChanged()
    }

    override fun onError(lang: String?, message: String?) {
        if (lang == null || !langs.contains(lang)) return
        pending.remove(lang)
        setStatus(lang, context.getString(R.string.su_pack_failed))
        onStateChanged()
    }
}
