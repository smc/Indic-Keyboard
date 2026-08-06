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

/**
 * One screenful of fields. [id] is the stable handle automated tests use to jump straight here:
 *
 *     adb shell am start -n org.smc.inputtest/.MainActivity --es page rich
 */
interface Page {
    val id: String
    val title: String
    fun createView(host: Activity): View
}

val PAGES: List<Page> = listOf(TextFieldsPage, RichContentPage, ComposeFieldsPage)
