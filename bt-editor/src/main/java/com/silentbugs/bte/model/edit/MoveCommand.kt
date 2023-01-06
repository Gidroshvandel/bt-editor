package com.silentbugs.bte.model.edit

import com.badlogic.gdx.utils.Pool
import com.silentbugs.bte.model.tasks.TaskModel

/**
 * Created by EvilEntity on 05/02/2016.
 */
class MoveCommand : Command(Type.MOVE) {
    private var add: AddCommand? = null
    private var remove: RemoveCommand? = null
    private fun init(what: TaskModel, where: TaskModel): MoveCommand {
        return init(-1, what, where)
    }

    private fun init(at: Int, what: TaskModel, where: TaskModel): MoveCommand {
        remove = RemoveCommand.obtain(what)
        add = AddCommand.obtain(at, what, where)
        return this
    }

    override fun execute() {
        remove?.execute()
        add?.execute()
    }

    override fun undo() {
        add?.undo()
        remove?.undo()
    }

    override fun reset() {
        add?.free()
        add = null
        remove?.free()
        remove = null
    }

    override fun free() {
        pool.free(this)
    }

    companion object {
        private val pool: Pool<MoveCommand> = object : Pool<MoveCommand>() {
            override fun newObject(): MoveCommand {
                return MoveCommand()
            }
        }

        fun obtain(what: TaskModel, where: TaskModel): MoveCommand {
            return pool.obtain().init(what, where)
        }

        fun obtain(at: Int, what: TaskModel, where: TaskModel): MoveCommand {
            return pool.obtain().init(at, what, where)
        }
    }
}
