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

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.TextAttribute
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

internal const val LOG_TAG = "InputTest"

internal val ACCENT = Color.parseColor("#00695C")
internal val LABEL_GREY = Color.parseColor("#888888")

internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

internal fun Context.sectionHeader(text: String, topGap: Int) = TextView(this).apply {
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        .apply { topMargin = topGap; bottomMargin = dp(8); leftMargin = dp(4) }
    this.text = text.uppercase()
    setTextColor(ACCENT)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    setTypeface(typeface, Typeface.BOLD)
    letterSpacing = 0.06f
}

internal fun Context.card(bottomPadding: Int = 4) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    setPadding(dp(16), dp(16), dp(16), dp(bottomPadding))
    elevation = dp(2).toFloat()
    background = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = dp(12).toFloat()
    }
}

internal fun Context.pageColumn(build: LinearLayout.() -> Unit) = ScrollView(this).apply {
    clipToPadding = false
    isFillViewport = true
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(24))
        build()
    })
}

internal fun Context.fieldLabel(text: String) = TextView(this).apply {
    this.text = text
    setTextColor(LABEL_GREY)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
}

// Logs what the IME commits, including the TextAttribute it attaches. Use it to check that
// picking a suggestion arrives with suggestionSelected=true, which is what screen readers key
// off: adb logcat -s InputTest
internal fun Context.loggingEditText() = object : EditText(this) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val target = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(target, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                Log.i(LOG_TAG, "commitText \"$text\" — no TextAttribute")
                return super.commitText(text, newCursorPosition)
            }

            override fun commitText(text: CharSequence, newCursorPosition: Int,
                    attribute: TextAttribute?): Boolean {
                val selected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                    attribute?.isTextSuggestionSelected.toString()
                } else "unavailable below API 37"
                Log.i(LOG_TAG, "commitText \"$text\" — suggestionSelected=$selected")
                return super.commitText(text, newCursorPosition, attribute)
            }
        }
    }
}
