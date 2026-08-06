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
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Slide-in page switcher, built from platform views so the test app keeps no navigation
 * dependency.
 *
 * Everything a test needs to steer is addressable without pixel hunting: the menu button, every
 * page row and the close row carry a stable `contentDescription` ([NAV_BUTTON_DESC], [rowDesc],
 * [CLOSE_DESC]), the title bar reports the visible page as "Page: <id>", and each switch is logged
 * to `adb logcat -s InputTest`. Tapping the scrim also dismisses, but its centre sits under the
 * panel, so drive [CLOSE_DESC] or the back key instead. Tests that just want a page should skip the
 * drawer entirely and launch with `--es page <id>` — see [Page].
 */
class NavDrawer(
    private val host: Activity,
    private val pages: List<Page>,
    private val onSelect: (Page) -> Unit,
) {
    companion object {
        const val NAV_BUTTON_DESC = "Open navigation"
        const val CLOSE_DESC = "Close navigation"
        const val PANEL_DESC = "Navigation"
        const val ANIMATION_MILLIS = 120L

        fun rowDesc(page: Page) = "Nav: ${page.id}"
    }

    private val panelWidth = host.dp(280)

    private val titleView = TextView(host).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
    }

    private val menuButton = TextView(host).apply {
        layoutParams = LinearLayout.LayoutParams(host.dp(48), host.dp(48))
        text = "☰"
        contentDescription = NAV_BUTTON_DESC
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        isClickable = true
        setOnClickListener { open() }
    }

    val topBar = LinearLayout(host).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(ACCENT)
        setPadding(host.dp(4), host.dp(4), host.dp(16), host.dp(4))
        addView(menuButton)
        addView(titleView)
    }

    private val scrim = View(host).apply {
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        setBackgroundColor(Color.parseColor("#80000000"))
        visibility = View.GONE
        setOnClickListener { close() }
    }

    private val panel = ScrollView(host).apply {
        layoutParams = FrameLayout.LayoutParams(panelWidth, MATCH_PARENT)
        contentDescription = PANEL_DESC
        setBackgroundColor(Color.WHITE)
        elevation = host.dp(16).toFloat()
        translationX = -panelWidth.toFloat()
        addView(LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, host.dp(24), 0, host.dp(24))
            addView(host.sectionHeader("Pages", topGap = host.dp(8)).apply {
                (layoutParams as LinearLayout.LayoutParams).leftMargin = host.dp(20)
            })
            pages.forEach { addView(row(it)) }
            addView(closeRow())
        })
        setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val i = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(i.left, i.top, 0, i.bottom)
            }
            insets
        }
    }

    /** Notified whenever the panel opens or closes, so the host can wire up back handling. */
    var onOpenStateChanged: ((Boolean) -> Unit)? = null

    val isOpen get() = scrim.visibility == View.VISIBLE

    /** Adds the scrim and the panel over [content]; returns the view to hand to setContentView. */
    fun wrap(content: View) = FrameLayout(host).apply {
        addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(scrim)
        addView(panel)
    }

    fun onPageShown(page: Page) {
        titleView.text = page.title
        titleView.contentDescription = "Page: ${page.id}"
    }

    fun open() {
        scrim.visibility = View.VISIBLE
        onOpenStateChanged?.invoke(true)
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(ANIMATION_MILLIS).start()
        panel.animate().translationX(0f).setDuration(ANIMATION_MILLIS).start()
    }

    fun close() {
        onOpenStateChanged?.invoke(false)
        scrim.animate().alpha(0f).setDuration(ANIMATION_MILLIS)
            .withEndAction { scrim.visibility = View.GONE }.start()
        panel.animate().translationX(-panelWidth.toFloat()).setDuration(ANIMATION_MILLIS).start()
    }

    private fun closeRow() = TextView(host).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            .apply { topMargin = host.dp(16) }
        text = "Close"
        contentDescription = CLOSE_DESC
        setPadding(host.dp(20), host.dp(16), host.dp(20), host.dp(16))
        setTextColor(LABEL_GREY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        isClickable = true
        setOnClickListener { close() }
    }

    private fun row(page: Page) = TextView(host).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        text = page.title
        contentDescription = rowDesc(page)
        setPadding(host.dp(20), host.dp(16), host.dp(20), host.dp(16))
        setTextColor(Color.parseColor("#111111"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        isClickable = true
        setOnClickListener {
            close()
            onSelect(page)
        }
    }
}
