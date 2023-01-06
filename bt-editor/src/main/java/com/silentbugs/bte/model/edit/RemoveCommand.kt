package com.silentbugs.bte.model.edit

import com.badlogic.gdx.utils.Pool
import com.silentbugs.bte.model.tasks.TaskModel

/**
 * Created by EvilEntity on 05/02/2016.
 */
class RemoveCommand protected constructor() : Command(Type.REMOVE), Pool.Poolable {

    companion object {
        private val pool: Pool<RemoveCommand> = object : Pool<RemoveCommand>() {
            override fun newObject(): RemoveCommand {
                return RemoveCommand()
            }
        }

        fun obtain(what: TaskModel): RemoveCommand {
            return pool.obtain().init(what)
        }
    }

    protected var what: TaskModel? = null
    protected var parent: TaskModel? = null
    protected var idInParent = 0
    protected var removeGaurd = false

    fun init(what: TaskModel): RemoveCommand {
        this.what = what
        parent = what.parent
        // note top level guard doesn't have a parent, this could be set to guarded task
        if (parent == null && what.isGuard) {
            removeGaurd = true
            parent = what.guardedTask
        }
        idInParent = parent?.getChildId(what) ?: 0
        return this
    }

    override fun execute() {
        val what = this.what
        if (what != null) {
            if (removeGaurd) {
                parent?.removeGuard()
            } else {
                parent?.removeChild(what)
            }
        } else {
            throw IllegalStateException("Can't execute. RemoveCommand not init")
        }
    }

    override fun undo() {
        val what = this.what
        if (what != null) {
            if (removeGaurd) {
                parent?.setGuard(what)
            } else {
                parent?.insertChild(idInParent, what)
            }
        } else {
            throw IllegalStateException("Can't execute undo. RemoveCommand not init")
        }
    }

    override fun free() {
        pool.free(this)
    }

    override fun reset() {
        // free pools or whatever
        what = null
        parent = null
        idInParent = -1
        removeGaurd = false
    }
}
