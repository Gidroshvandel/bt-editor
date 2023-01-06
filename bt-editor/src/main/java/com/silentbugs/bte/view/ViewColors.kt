package com.silentbugs.bte.view

import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.graphics.Color

/**
 * Created by PiotrJ on 31/10/15.
 */
object ViewColors {
    // colors stolen from vis ui, hardcoded so we dont have to count on vis being used
    @JvmField
    val VALID = Color(0.105f, 0.631f, 0.886f, 1f)

    @JvmField
    val INVALID = Color(0.862f, 0f, 0.047f, 1f)
    val SUCCEEDED = Color(Color.GREEN)
    val RUNNING = Color(Color.ORANGE)
    val FAILED = Color(Color.RED)
    val CANCELLED = Color(Color.YELLOW)

    @JvmField
    val FRESH = Color(Color.GRAY)

    @JvmField
    val GUARD = Color(0.85f, 0.85f, 1f, 1f)

    @JvmField
    val GUARDED = Color(0.6f, 0.7f, 1f, 1f)

    @JvmStatic
    fun getColor(status: Task.Status?): Color {
        return if (status == null) Color.GRAY else when (status) {
            Task.Status.SUCCEEDED -> SUCCEEDED
            Task.Status.RUNNING -> RUNNING
            Task.Status.FAILED -> FAILED
            Task.Status.CANCELLED -> CANCELLED
            Task.Status.FRESH -> FRESH
            else -> FRESH
        }
    }
}
