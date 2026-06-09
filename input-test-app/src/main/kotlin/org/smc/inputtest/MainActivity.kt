package org.smc.inputtest

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType.*
import android.util.TypedValue
import android.view.WindowInsets
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * One screen with an [EditText] for every [android.text.InputType] variant (and a few IME-action
 * variants) Indic Keyboard cares about, grouped by kind. Use it to manually exercise the keyboard
 * against each field type — suggestions, auto-caps, password masking, the raw-key TYPE_NULL path,
 * IME actions. To add a field, drop a [field] entry into the relevant group below.
 */
class MainActivity : Activity() {
    private fun field(label: String, type: Int, ime: Int = EditorInfo.IME_ACTION_UNSPECIFIED) =
        Triple(label, type, ime)

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
            field("Number password", TYPE_CLASS_NUMBER or TYPE_NUMBER_VARIATION_PASSWORD),
        ),
        "Numbers, phone & date-time" to listOf(
            field("Number", TYPE_CLASS_NUMBER),
            field("Number signed", TYPE_CLASS_NUMBER or TYPE_NUMBER_FLAG_SIGNED),
            field("Number decimal", TYPE_CLASS_NUMBER or TYPE_NUMBER_FLAG_DECIMAL),
            field("Phone", TYPE_CLASS_PHONE),
            field("Datetime", TYPE_CLASS_DATETIME or TYPE_DATETIME_VARIATION_NORMAL),
            field("Date", TYPE_CLASS_DATETIME or TYPE_DATETIME_VARIATION_DATE),
            field("Time", TYPE_CLASS_DATETIME or TYPE_DATETIME_VARIATION_TIME),
        ),
        "Other" to listOf(
            field("Null (raw key events)", TYPE_NULL),
        ),
        "IME actions" to listOf(
            field("Go", TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_GO),
            field("Search", TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH),
            field("Send", TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEND),
            field("Next", TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_NEXT),
            field("Done", TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_DONE),
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        groups.forEachIndexed { index, (title, fields) ->
            column.addView(header(title, topGap = if (index == 0) dp(8) else dp(24)))
            column.addView(card(fields))
        }

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            addView(column)
        }
        setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(i.left, i.top, i.right, i.bottom)
                if (insets.getInsets(WindowInsets.Type.ime()).bottom > 0) {
                    v.post { scrollFocusedAboveKeyboard(scroll) }
                }
            }
            insets
        }
    }

    private fun scrollFocusedAboveKeyboard(scroll: ScrollView) {
        val focused = currentFocus ?: return
        val scrollLoc = IntArray(2).also { scroll.getLocationInWindow(it) }
        val focusedLoc = IntArray(2).also { focused.getLocationInWindow(it) }
        val viewportBottom = scrollLoc[1] + scroll.height - scroll.paddingBottom
        val overlap = focusedLoc[1] + focused.height - viewportBottom + dp(16)
        if (overlap > 0) scroll.smoothScrollBy(0, overlap)
    }

    private fun header(text: String, topGap: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            .apply { topMargin = topGap; bottomMargin = dp(8); leftMargin = dp(4) }
        this.text = text.uppercase()
        setTextColor(Color.parseColor("#00695C"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTypeface(typeface, Typeface.BOLD)
        letterSpacing = 0.06f
    }

    private fun card(fields: List<Triple<String, Int, Int>>) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        setPadding(dp(16), dp(16), dp(16), dp(4))
        elevation = dp(2).toFloat()
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(12).toFloat()
        }
        fields.forEach { (label, type, ime) -> addView(fieldView(label, type, ime)) }
    }

    private fun fieldView(label: String, type: Int, ime: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            .apply { bottomMargin = dp(12) }
        addView(TextView(context).apply {
            text = label
            setTextColor(Color.parseColor("#888888"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        })
        addView(EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            inputType = type
            imeOptions = ime
            hint = label
        })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
