package com.ashrdev.aznd.ui.common.share

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * An undo / redo history of values (card specs). The owner keeps the current value and tells the stack what it was
 * before each change ([push]); [undo] and [redo] take the current value and give back the one to go to.
 * Changes that follow each other closely and are marked continuous (a slider being dragged, a handle being pulled)
 * count as one step, so one drag is one Undo, not hundreds.
 */
internal class UndoStack<T>(
    private val maxSteps: Int = 100,
    private val mergeMs: Long = 800L
) {
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val undoStack = ArrayList<T>()
    private val redoStack = ArrayList<T>()
    private var lastAt = 0L
    private var lastContinuous = false

    /** The value was [previous] and has just been changed. */
    fun push(previous: T, continuous: Boolean = false) {
        val now = System.currentTimeMillis()
        val merge = continuous && lastContinuous && undoStack.isNotEmpty() && now - lastAt < mergeMs
        if (!merge) {
            undoStack.add(previous)
            if (undoStack.size > maxSteps) undoStack.removeAt(0)
        }
        lastAt = now
        lastContinuous = continuous
        redoStack.clear()
        sync()
    }

    /** The value before the last change, or null when there is none. [current] can be redone to. */
    fun undo(current: T): T? {
        if (undoStack.isEmpty()) return null
        redoStack.add(current)
        val back = undoStack.removeAt(undoStack.lastIndex)
        lastContinuous = false
        sync()
        return back
    }

    /** The value that was undone, or null when there is none. [current] can be undone to again. */
    fun redo(current: T): T? {
        if (redoStack.isEmpty()) return null
        undoStack.add(current)
        val forward = redoStack.removeAt(redoStack.lastIndex)
        lastContinuous = false
        sync()
        return forward
    }

    private fun sync() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }
}
