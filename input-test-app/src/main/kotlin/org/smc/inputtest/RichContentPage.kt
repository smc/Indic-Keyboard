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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.text.InputType.TYPE_CLASS_TEXT
import android.view.View
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
import java.io.File

/**
 * An [EditText] advertising image mime types via [EditorInfo.contentMimeTypes]; images the IME
 * commits via commitContent are shown in a preview below. The buttons put content on the clipboard
 * through [TestImageProvider] so the IME's clipboard capture can be exercised without leaving the
 * app.
 */
object RichContentPage : Page {
    override val id = "rich"
    override val title = "Rich content"

    override fun createView(host: Activity): View = host.pageColumn {
        addView(host.sectionHeader("Image paste", topGap = host.dp(8)))
        addView(host.imagePasteCard())
        addView(host.sectionHeader("Clipboard", topGap = host.dp(24)))
        addView(host.clipboardCard())
    }

    private fun Context.imagePasteCard() = card(bottomPadding = 16).apply {
        val preview = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(120))
                .apply { topMargin = dp(8) }
            contentDescription = "Committed image preview"
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.parseColor("#EEEEEE"))
        }
        addView(fieldLabel("Image paste (accepts image/*)"))
        addView(imagePasteField(preview))
        addView(preview)
    }

    private fun Context.clipboardCard() = card(bottomPadding = 16).apply {
        addView(Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            text = "Copy test image to clipboard"
            setOnClickListener { copyTestImageToClipboard() }
        })
        addView(Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            text = "Copy sensitive text to clipboard"
            setOnClickListener { copySensitiveTextToClipboard() }
        })
    }

    private fun Context.imagePasteField(preview: ImageView) = object : EditText(this) {
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

    private fun Context.copyTestImageToClipboard() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(ACCENT)
            drawCircle(200f, 150f, 90f, Paint().apply { color = Color.parseColor("#FFD54F") })
        }
        val file = File(cacheDir, "clip/test_image.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = Uri.parse("content://org.smc.inputtest.clip/test_image.png")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newUri(contentResolver, "test image", uri))
    }

    private fun Context.copySensitiveTextToClipboard() {
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
