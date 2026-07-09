package org.smc.inputtest

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.text.InputType.*
import android.util.TypedValue
import android.view.WindowInsets
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

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
        column.addView(header("Rich content", topGap = dp(24)))
        column.addView(richContentCard())

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

    // An EditText advertising image mime types via EditorInfo.contentMimeTypes; images the IME
    // commits via commitContent are shown in a preview below. The button puts a generated PNG on
    // the clipboard through TestImageProvider so the IME's clipboard capture can be exercised
    // without leaving the app.
    private fun richContentCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        elevation = dp(2).toFloat()
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(12).toFloat()
        }
        val preview = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(120))
                .apply { topMargin = dp(8) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.parseColor("#EEEEEE"))
        }
        addView(TextView(context).apply {
            text = "Image paste (accepts image/*)"
            setTextColor(Color.parseColor("#888888"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        })
        addView(imagePasteField(preview))
        addView(preview)
        addView(Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                .apply { topMargin = dp(8) }
            text = "Copy test image to clipboard"
            setOnClickListener { copyTestImageToClipboard() }
        })
        addView(Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            text = "Copy sensitive text to clipboard"
            setOnClickListener { copySensitiveTextToClipboard() }
        })
    }

    private fun imagePasteField(preview: ImageView) = object : EditText(this) {
        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
            val ic = super.onCreateInputConnection(outAttrs) ?: return null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return ic
            outAttrs.contentMimeTypes = arrayOf("image/*")
            return object : InputConnectionWrapper(ic, false) {
                override fun commitContent(info: InputContentInfo, flags: Int,
                        opts: Bundle?): Boolean {
                    return try {
                        if (flags and InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                                != 0) {
                            info.requestPermission()
                        }
                        preview.setImageURI(info.contentUri)
                        info.releasePermission()
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            }
        }
    }.apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        inputType = TYPE_CLASS_TEXT
        hint = "Image paste"
    }

    private fun copyTestImageToClipboard() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.parseColor("#00695C"))
            drawCircle(200f, 150f, 90f, Paint().apply { color = Color.parseColor("#FFD54F") })
        }
        val file = File(cacheDir, "clip/test_image.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = Uri.parse("content://org.smc.inputtest.clip/test_image.png")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newUri(contentResolver, "test image", uri))
    }

    private fun copySensitiveTextToClipboard() {
        val clip = ClipData.newPlainText("password", "s3cr3t-p4ssw0rd")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(clip)
    }
}
