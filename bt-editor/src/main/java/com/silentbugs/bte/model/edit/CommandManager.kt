package com.silentbugs.bte.model.edit

import com.badlogic.gdx.utils.Array

/**
 * Menages commands, allows for undo/redo etc
 *
 *
 * Created by EvilEntity on 05/02/2016.
 */
class CommandManager {

    private val commands = Array<Command>()
    private var current = 0

    val canRedo: Boolean get() = current < commands.size - 1 && current >= -1

    fun redo(): Boolean {
        return if (canRedo) {
            val command = commands[++current]
            command.execute()
            true
        } else {
            false
        }
    }

    val canUndo: Boolean get() = commands.size > 0 && current >= 0

    fun undo(): Boolean {
        return if (canUndo) {
            val command = commands[current--]
            command.undo()
            true
        } else {
            false
        }
    }

    fun execute(command: Command) {
        if (current < commands.size - 1 && commands.size > 0) {
            for (i in current + 1 until commands.size - 1) {
                commands[i].free()
            }
            commands.removeRange(current + 1, commands.size - 1)
        }
        commands.add(command)
        command.execute()
        current = commands.size - 1
    }

    fun reset() {
        for (command in commands) {
            command.free()
        }
        commands.clear()
    }
}
