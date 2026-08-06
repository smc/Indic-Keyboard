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
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.LinearLayout

/**
 * The two Compose `BasicTextField` generations, which drive Compose's own InputConnection rather
 * than the platform [android.widget.EditText] one: [TextFieldState] goes through
 * `StatelessInputConnection`, [TextFieldValue] through the older `RecordingInputConnection`.
 *
 * They answer `getTextBeforeCursor` from state that can lag an edit the keyboard just sent, which
 * the platform fields never do — so reach for this page when a bug reproduces "only in some apps".
 * This is the stack most modern app composers are built on.
 */
object ComposeFieldsPage : Page {
    override val id = "compose"
    override val title = "Compose fields"

    override fun createView(host: Activity): View = host.pageColumn {
        addView(host.sectionHeader("Compose text fields", topGap = host.dp(8)))
        addView(host.card(bottomPadding = 16).apply {
            addView(ComposeView(host).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                setContent { Fields() }
            })
        })
    }
}

private val LABEL = TextStyle(fontSize = 13.sp, color = Color(0xFF888888))
private val BODY = TextStyle(fontSize = 20.sp, color = Color(0xFF111111))

private val fieldModifier = Modifier
    .fillMaxWidth()
    .height(72.dp)
    .padding(top = 6.dp, bottom = 6.dp)
    .background(Color(0xFFEEEEEE))
    .padding(10.dp)

@androidx.compose.runtime.Composable
private fun Fields() {
    Column {
        BasicText("State-based (StatelessInputConnection)", style = LABEL)
        val state = remember { TextFieldState() }
        BasicTextField(
            state = state,
            modifier = fieldModifier.semantics { contentDescription = "Compose state-based" },
            textStyle = BODY,
        )

        BasicText("Value-based (RecordingInputConnection)", style = LABEL)
        var value by remember { mutableStateOf(TextFieldValue()) }
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            modifier = fieldModifier.semantics { contentDescription = "Compose value-based" },
            textStyle = BODY,
        )
    }
}
