/*
 * SPDX-FileCopyrightText: 2025 GameBar
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.collapsingtoolbar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.android.gamebar.R

open class CollapsingToolbarBaseActivity : AppCompatActivity() {

    private var collapsingToolbar: CollapsingToolbarLayout? = null
    private var toolbar: MaterialToolbar? = null
    private var contentFrame: FrameLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(layoutResID: Int) {
        val baseView = layoutInflater.inflate(R.layout.collapsing_toolbar_base_layout, null)
        contentFrame = baseView.findViewById(R.id.content_frame)
        collapsingToolbar = baseView.findViewById(R.id.collapsing_toolbar)
        toolbar = baseView.findViewById(R.id.action_bar)

        layoutInflater.inflate(layoutResID, contentFrame, true)
        super.setContentView(baseView)

        setupToolbar()
    }

    override fun setContentView(view: View?) {
        val baseView = layoutInflater.inflate(R.layout.collapsing_toolbar_base_layout, null)
        contentFrame = baseView.findViewById(R.id.content_frame)
        collapsingToolbar = baseView.findViewById(R.id.collapsing_toolbar)
        toolbar = baseView.findViewById(R.id.action_bar)

        view?.let { contentFrame?.addView(it) }
        super.setContentView(baseView)

        setupToolbar()
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        val baseView = layoutInflater.inflate(R.layout.collapsing_toolbar_base_layout, null)
        contentFrame = baseView.findViewById(R.id.content_frame)
        collapsingToolbar = baseView.findViewById(R.id.collapsing_toolbar)
        toolbar = baseView.findViewById(R.id.action_bar)

        view?.let { contentFrame?.addView(it, params) }
        super.setContentView(baseView)

        setupToolbar()
    }

    private fun setupToolbar() {
        toolbar?.let {
            setSupportActionBar(it)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
            it.setNavigationOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        collapsingToolbar?.title = title
    }

    override fun setTitle(titleId: Int) {
        super.setTitle(titleId)
        collapsingToolbar?.title = getString(titleId)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
