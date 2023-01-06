package com.silentbugs.bte.model.edit

import com.badlogic.gdx.utils.Pool
import com.silentbugs.bte.model.tasks.TaskModel

/**
 * Created by EvilEntity on 05/02/2016.
 */
class AddCommand protected constructor() : Command(Type.ADD) {

    companion object {

        private val pool: Pool<AddCommand> = object : Pool<AddCommand>() {
            override fun newObject(): AddCommand {
                return AddCommand()
            }
        }

        fun obtain(what: TaskModel, where: TaskModel): AddCommand {
            return pool.obtain().init(what, where)
        }

        fun obtain(at: Int, what: TaskModel, where: TaskModel): AddCommand {
            return pool.obtain().init(at, what, where)
        }
    }

    private var what: TaskModel? = null
    private var target: TaskModel? = null
    private var at = -1

    fun init(what: TaskModel, target: TaskModel): AddCommand {
        return init(-1, what, target)
    }

    fun init(at: Int, what: TaskModel, target: TaskModel): AddCommand {
        this.at = at
        // we can make a copy of what, but cant of target duh
        // do we even want to copy stuff?
        this.what = what // .copy();
        this.target = target
        return this
    }

    override fun execute() {
        val what = this.what
        if (what != null) {
            if (at > -1) {
                target?.insertChild(at, what)
            } else {
                target?.addChild(what)
            }
        } else {
            throw IllegalStateException("Can't execute. AddCommand not init")
        }
    }

    override fun undo() {
        val what = this.what
        if (what != null) {
            target?.removeChild(what)
        } else {
            throw IllegalStateException("Can't execute undo. AddCommand not init")
        }
    }

    override fun free() {
        pool.free(this)
    }

    override fun reset() {
        // TODO free or whatever
        what = null
        target = null
        at = -1
    }
}
