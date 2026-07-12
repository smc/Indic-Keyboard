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

import android.app.Activity
import android.app.Fragment
import android.os.Bundle

/**
 * Test activity to use when testing preference fragments.
 *
 * Create an `ActivityInstrumentationTestCase2` for this activity and call `setIntent()` with an
 * intent that names the fragment to load ([EXTRA_SHOW_FRAGMENT]). The fragment can then be obtained
 * from this activity for testing/verification.
 */
@Suppress("DEPRECATION")
class TestFragmentActivity : Activity() {
    lateinit var fragment: Fragment
        private set

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        val fragmentName = intent.getStringExtra(EXTRA_SHOW_FRAGMENT)
            ?: throw IllegalArgumentException("No fragment name specified for testing")
        fragment = Fragment.instantiate(this, fragmentName)
        fragmentManager.beginTransaction().add(fragment, fragmentName).commit()
    }

    companion object {
        /** The name of the fragment to load; required when starting this activity. */
        const val EXTRA_SHOW_FRAGMENT = "show_fragment"
    }
}
