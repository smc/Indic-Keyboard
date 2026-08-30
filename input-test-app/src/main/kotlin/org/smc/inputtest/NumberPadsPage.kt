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

/**
 * The fields that bring up a number pad instead of the alphabet layout. The dialpad group is the
 * one to reach for when checking how the ABC/DEF letter hints sit next to the digits: those hints
 * render only on the phone and number-password layouts.
 */
object NumberPadsPage : Page {
    override val id = "numpad"
    override val title = "Number pads"

    private val groups = listOf(
        "Dialpad (letter hints)" to listOf(
            field("Phone", TYPE_CLASS_PHONE),
            field("Number password", TYPE_CLASS_NUMBER or TYPE_NUMBER_VARIATION_PASSWORD),
        ),
        "Numbers" to listOf(
            field("Number", TYPE_CLASS_NUMBER),
            field("Number signed", TYPE_CLASS_NUMBER or TYPE_NUMBER_FLAG_SIGNED),
            field("Number decimal", TYPE_CLASS_NUMBER or TYPE_NUMBER_FLAG_DECIMAL),
        ),
        "Date & time" to listOf(
            field("Datetime", TYPE_CLASS_DATETIME or TYPE_DATETIME_VARIATION_NORMAL),
            field("Date", TYPE_CLASS_DATETIME or TYPE_DATETIME_VARIATION_DATE),
            field("Time", TYPE_CLASS_DATETIME or TYPE_DATETIME_VARIATION_TIME),
        ),
    )

    override fun createView(host: Activity): View = host.groupedFieldPage(groups)
}
