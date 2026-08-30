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

package org.smc.inputtest

import android.app.Activity
import android.text.InputType.*
import android.view.View
import android.view.inputmethod.EditorInfo

/**
 * An [android.widget.EditText] for every text-ish [android.text.InputType] variant (and a few
 * IME-action variants) Indic Keyboard cares about, grouped by kind. To add a field, drop a [field]
 * entry into the relevant group below. The number pads live on [NumberPadsPage].
 */
object TextFieldsPage : Page {
    override val id = "text"
    override val title = "Text inputs"

    private val groups = listOf(
        "Text" to listOf(
            field("Text", TYPE_CLASS_TEXT),
            field("Cap sentences", TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_CAP_SENTENCES),
            field("Cap words", TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_CAP_WORDS),
            field("Cap characters", TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_CAP_CHARACTERS),
            field("Auto-correct", TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_AUTO_CORRECT),
            field("No suggestions", TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_NO_SUGGESTIONS),
            field("Multi-line", TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_MULTI_LINE),
            field("Auto-complete", TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_AUTO_COMPLETE),
        ),
        "Text variations" to listOf(
            field("Person name", TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PERSON_NAME),
            field("Postal address", TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_POSTAL_ADDRESS),
            field("Email address", TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            field("Email subject", TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_EMAIL_SUBJECT),
            field("Short message", TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_SHORT_MESSAGE),
            field("Long message", TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_LONG_MESSAGE),
            field("URI", TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_URI),
            field("Filter", TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_FILTER),
            field("Phonetic", TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PHONETIC),
            field("Web text", TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_WEB_EDIT_TEXT),
            field("Web email", TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS),
        ),
        "Passwords" to listOf(
            field("Password", TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PASSWORD),
            field("Visible password", TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
            field("Web password", TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_WEB_PASSWORD),
        ),
        "Other" to listOf(
            field("Null (raw key events)", TYPE_NULL),
            field("Incognito (no personalized learning)", TYPE_CLASS_TEXT,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING),
        ),
        "IME actions" to listOf(
            field("Go", TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_GO),
            field("Search", TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH),
            field("Send", TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEND),
            field("Next", TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_NEXT),
            field("Done", TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_DONE),
        ),
    )

    override fun createView(host: Activity): View = host.groupedFieldPage(groups)
}
