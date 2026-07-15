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

import android.graphics.LinearGradient
import android.graphics.Shader
import android.widget.TextView

/** Cross-fades through the localized greetings on the welcome step, painting each with a
 *  horizontal gradient sized to the rendered text. */
internal class GreetingAnimator(
    private val word: TextView, private val greetings: Array<String>
) {
    private var index = 0
    private val cycler = object : Runnable {
        override fun run() {
            index = (index + 1) % greetings.size
            crossFadeTo(index)
            word.postDelayed(this, GREETING_HOLD_MS + GREETING_FADE_MS * 2)
        }
    }

    init {
        word.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyGradient() }
    }

    fun start() {
        index = 0
        word.animate().cancel()
        word.alpha = 1f
        word.text = greetings[0]
        applyGradient()
        word.removeCallbacks(cycler)
        if (greetings.size > 1) {
            word.postDelayed(cycler, GREETING_HOLD_MS + GREETING_FADE_MS)
        }
    }

    fun stop() {
        word.removeCallbacks(cycler)
        word.animate().cancel()
    }

    private fun crossFadeTo(i: Int) {
        word.animate().alpha(0f).setDuration(GREETING_FADE_MS).withEndAction {
            word.text = greetings[i]
            applyGradient()
            word.animate().alpha(1f).setDuration(GREETING_FADE_MS).start()
        }.start()
    }

    private fun applyGradient() {
        val text = word.text
        if (text.isNullOrEmpty() || word.width == 0) {
            return
        }
        val textWidth = word.paint.measureText(text.toString())
        if (textWidth <= 0f) {
            return
        }
        val available = word.width - word.paddingLeft - word.paddingRight
        val start = word.paddingLeft + ((available - textWidth) / 2f).coerceAtLeast(0f)
        word.paint.shader = LinearGradient(
            start, 0f, start + textWidth, 0f, GREETING_GRADIENT, null, Shader.TileMode.CLAMP
        )
        word.invalidate()
    }

    companion object {
        private val GREETING_GRADIENT =
            intArrayOf(0xFF00BFA5.toInt(), 0xFF2979FF.toInt(), 0xFF7C4DFF.toInt())
        private const val GREETING_HOLD_MS = 1500L
        private const val GREETING_FADE_MS = 450L
    }
}
