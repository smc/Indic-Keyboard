/*
 * Copyright 2026, Jishnu Mohan <jishnu7@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.smc.inputtest

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.v1.InlineSuggestionUi

/**
 * Offers a fixed set of fake credentials for whichever field is focused, so the keyboard's
 * inline-suggestion strip can be exercised without a real password manager. Enable with:
 *
 *   adb shell settings put secure autofill_service org.smc.inputtest/.TestAutofillService
 */
@RequiresApi(Build.VERSION_CODES.O)
class TestAutofillService : AutofillService() {

    // The field's hint is baked into every suggestion so a chip leaking onto another field is
    // immediately attributable.
    private fun valuesFor(fieldTag: String) = listOf(
        "$fieldTag user@example.com",
        "$fieldTag second.user@example.com",
        "9876543210",
        "$fieldTag fourth",
        "$fieldTag fifth",
    )

    override fun onFillRequest(
        request: FillRequest,
        signal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.last().structure
        val focused = findFocusedEditText(structure)
        if (focused == null) {
            callback.onSuccess(null)
            return
        }
        val focusedId = focused.autofillId!!
        val values = valuesFor(focused.hint ?: "?")
        // Password fields get a single suggestion, so the keyboard's centered
        // one-chip layout can be exercised too.
        val fieldValues = if (focused.hint?.contains("password", ignoreCase = true) == true) {
            values.take(1)
        } else {
            values
        }
        val inline = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && request.inlineSuggestionsRequest != null
        val response = FillResponse.Builder()
        fieldValues.forEachIndexed { index, value ->
            val menu = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, value)
            }
            val dataset = Dataset.Builder()
            if (inline) {
                val specs = request.inlineSuggestionsRequest!!.inlinePresentationSpecs
                setInlineValue(
                    dataset, focusedId, value, menu,
                    specs[minOf(index, specs.size - 1)], pinned = false,
                )
            } else {
                dataset.setValue(focusedId, AutofillValue.forText(value), menu)
            }
            response.addDataset(dataset.build())
        }
        if (inline) {
            // Mirrors a real service's pinned "open me" entry, which the keyboard docks at
            // the strip's edge.
            val specs = request.inlineSuggestionsRequest!!.inlinePresentationSpecs
            val menu = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "pinned@example.com")
            }
            val dataset = Dataset.Builder()
            setInlineValue(
                dataset, focusedId, "pinned@example.com", menu, specs.last(), pinned = true,
            )
            response.addDataset(dataset.build())
        }
        callback.onSuccess(response.build())
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun setInlineValue(
        dataset: Dataset.Builder,
        id: AutofillId,
        value: String,
        menu: RemoteViews,
        spec: InlinePresentationSpec,
        pinned: Boolean,
    ) {
        val attribution = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val content = InlineSuggestionUi.newContentBuilder(attribution).apply {
            if (pinned) {
                setStartIcon(android.graphics.drawable.Icon.createWithResource(
                    this@TestAutofillService, android.R.drawable.ic_menu_more))
            } else {
                setTitle(value)
            }
        }.build()
        dataset.setValue(
            id, AutofillValue.forText(value), menu,
            InlinePresentation(content.slice, spec, pinned),
        )
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private fun findFocusedEditText(structure: AssistStructure): AssistStructure.ViewNode? {
        for (i in 0 until structure.windowNodeCount) {
            val found = findFocused(structure.getWindowNodeAt(i).rootViewNode)
            if (found != null) return found
        }
        return null
    }

    private fun findFocused(node: AssistStructure.ViewNode): AssistStructure.ViewNode? {
        if (node.isFocused && node.autofillId != null
            && node.className?.contains("EditText") == true
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findFocused(node.getChildAt(i))
            if (found != null) return found
        }
        return null
    }
}
