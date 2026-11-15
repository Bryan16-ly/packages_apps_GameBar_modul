/*
 * SPDX-FileCopyrightText: 2025 GameBar
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.widget

import android.content.Context
import android.util.AttributeSet
import androidx.preference.SwitchPreferenceCompat

open class MainSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.switchPreferenceCompatStyle,
    defStyleRes: Int = 0
) : SwitchPreferenceCompat(context, attrs, defStyleAttr, defStyleRes) {

    init {
        isIconSpaceReserved = false
    }
}
