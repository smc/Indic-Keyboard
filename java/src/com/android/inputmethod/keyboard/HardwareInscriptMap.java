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

package com.android.inputmethod.keyboard;

import android.view.KeyEvent;

import com.android.inputmethod.event.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;

/** Maps hardware qwerty key positions onto an Inscript soft layout's grid, so a physical
 *  keyboard produces the same characters the soft Inscript keyboard would. */
public final class HardwareInscriptMap {
    private static final int[][] PHYSICAL_ROWS = {
            {KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4,
             KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8,
             KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_0},
            {KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R,
             KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I,
             KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P},
            {KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F,
             KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K,
             KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE},
            {KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V,
             KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_COMMA,
             KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SLASH},
    };

    private final Map<Integer, Integer> mNormal;
    private final Map<Integer, Integer> mShifted;

    public HardwareInscriptMap(@Nullable final Keyboard normal, @Nullable final Keyboard shifted) {
        mNormal = build(normal);
        mShifted = build(shifted);
    }

    private static Map<Integer, Integer> build(@Nullable final Keyboard keyboard) {
        final Map<Integer, Integer> map = new HashMap<>();
        if (keyboard == null) {
            return map;
        }
        final Map<Integer, List<Key>> byRow = new TreeMap<>();
        for (final Key key : keyboard.getSortedKeys()) {
            if (key.getCode() <= 0 || key.isModifier()) {
                continue;
            }
            byRow.computeIfAbsent(key.getY(), k -> new ArrayList<>()).add(key);
        }
        final List<List<Key>> rows = new ArrayList<>(byRow.values());
        for (final List<Key> row : rows) {
            Collections.sort(row, (a, b) -> Integer.compare(a.getX(), b.getX()));
        }
        final int rowOffset = PHYSICAL_ROWS.length - Math.min(rows.size(), PHYSICAL_ROWS.length);
        for (int r = 0; r < rows.size() && r + rowOffset < PHYSICAL_ROWS.length; r++) {
            final int[] physical = PHYSICAL_ROWS[r + rowOffset];
            final List<Key> row = rows.get(r);
            if (r + rowOffset == 0) {
                pairDigits(physical, row, map);
                continue;
            }
            for (int c = 0; c < row.size() && c < physical.length; c++) {
                map.put(physical[c], row.get(c).getCode());
            }
        }
        return map;
    }

    // The soft digit row is wider than the ten physical number keys it's paired with (it also
    // carries a leading symbol key and trailing punctuation), so pairing by raw column index
    // shifts every digit by one. Some layouts also put matras rather than digits there. Filter
    // to the row's actual digit-coded keys and pair those in order instead; if none are found,
    // leave the physical row unmapped so the decoder's Latin digits pass through unchanged.
    private static void pairDigits(final int[] physical, final List<Key> row,
            final Map<Integer, Integer> map) {
        final List<Key> digitKeys = new ArrayList<>();
        for (final Key key : row) {
            if (Character.isDigit(key.getCode())) {
                digitKeys.add(key);
            }
        }
        for (int c = 0; c < digitKeys.size() && c < physical.length; c++) {
            map.put(physical[c], digitKeys.get(c).getCode());
        }
    }

    public int map(final int keyEventKeyCode, final boolean shifted) {
        final Integer code = (shifted ? mShifted : mNormal).get(keyEventKeyCode);
        return code != null ? code : Event.NOT_A_CODE_POINT;
    }
}
