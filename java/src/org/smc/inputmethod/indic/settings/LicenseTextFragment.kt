/*
 * Copyright 2026, Jishnu Mohan <jishnu@gmail.com>
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

package org.smc.inputmethod.indic.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

import com.android.inputmethod.latin.R

/** Shows a bundled license's full text (plain monospace) for the project passed in arguments. */
class LicenseTextFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.license_text, container, false)
        val rawRes = when (arguments?.getString(ARG_LICENSE)) {
            "agpl" -> R.raw.license_agpl_3_0
            "gpl" -> R.raw.license_gpl_3_0
            "mit" -> R.raw.license_mit
            "jqueryime" -> R.raw.license_jqueryime
            else -> R.raw.license_apache_2_0
        }
        val text = resources.openRawResource(rawRes).bufferedReader().use { it.readText() }
        root.findViewById<TextView>(R.id.license_text).text = text
        return root
    }

    override fun onResume() {
        super.onResume()
        val title = arguments?.getString(ARG_TITLE)
        if (title != null) {
            (activity as? AppCompatActivity)?.supportActionBar?.title = title
        }
    }

    companion object {
        private const val ARG_LICENSE = "license"
        private const val ARG_TITLE = "title"
    }
}
