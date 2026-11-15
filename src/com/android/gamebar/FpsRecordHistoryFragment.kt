/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.gamebar.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * Menampilkan daftar seluruh sesi rekaman FPS.
 * Ketuk sesi → buka FpsRecordDetailActivity.
 * Long-press sesi → hapus.
 */
class FpsRecordHistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: SessionAdapter
    private val sessions = mutableListOf<FpsRecordingSession>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_fps_record_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.rv_fps_sessions)
        emptyView    = view.findViewById(R.id.tv_empty_sessions)

        adapter = SessionAdapter(sessions,
            onClick = { session ->
                startActivity(
                    Intent(requireContext(), FpsRecordDetailActivity::class.java).apply {
                        putExtra(FpsRecordDetailActivity.EXTRA_SESSION_ID, session.id)
                    }
                )
            },
            onLongClick = { session -> confirmDelete(session) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadSessions()
    }

    private fun loadSessions() {
        Thread {
            val loaded = FpsRecordingManager.getInstance(requireContext()).loadAllSessions()
            activity?.runOnUiThread {
                sessions.clear()
                sessions.addAll(loaded)
                adapter.notifyDataSetChanged()
                emptyView.visibility  = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (sessions.isEmpty()) View.GONE  else View.VISIBLE
            }
        }.start()
    }

    private fun confirmDelete(session: FpsRecordingSession) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.fps_delete_session_title))
            .setMessage(getString(R.string.fps_delete_session_message, session.appName))
            .setPositiveButton(getString(R.string.fps_delete_confirm)) { _, _ ->
                FpsRecordingManager.getInstance(requireContext()).deleteSession(session.id)
                Toast.makeText(requireContext(), getString(R.string.fps_session_deleted), Toast.LENGTH_SHORT).show()
                loadSessions()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    private inner class SessionAdapter(
        private val list: List<FpsRecordingSession>,
        private val onClick: (FpsRecordingSession) -> Unit,
        private val onLongClick: (FpsRecordingSession) -> Unit
    ) : RecyclerView.Adapter<SessionAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvApp:      TextView = view.findViewById(R.id.tv_session_app)
            val tvDate:     TextView = view.findViewById(R.id.tv_session_date)
            val tvStats:    TextView = view.findViewById(R.id.tv_session_stats)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_fps_session, parent, false))

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = list[position]
            holder.tvApp.text   = s.appName
            holder.tvDate.text  = formatDate(s.startTime)
            holder.tvStats.text = getString(
                R.string.fps_session_stats,
                s.avgFps.toInt(),
                s.maxFps.toInt(),
                s.minFps.toInt(),
                formatDuration(s.durationMs)
            )
            holder.itemView.setOnClickListener     { onClick(s) }
            holder.itemView.setOnLongClickListener { onLongClick(s); true }
        }
    }

    private fun formatDate(ts: Long): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ts))

    private fun formatDuration(ms: Long): String {
        val sec = ms / 1000
        return if (sec >= 60) "${sec / 60}m ${sec % 60}s" else "${sec}s"
    }
}
