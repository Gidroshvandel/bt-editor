package com.silentbugs.bte.model.tasks

import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.utils.Pool
import com.silentbugs.bte.model.BehaviorTreeModel

/**
 * Created by EvilEntity on 04/02/2016.
 */
class LeafModel private constructor() : TaskModel(Type.LEAF), Pool.Poolable {

    fun init(task: LeafTask<*>, model: BehaviorTreeModel?): LeafModel {
        super.initTask(task, model)
        return this
    }

    protected fun init(other: LeafModel): LeafModel {
        super.initTask(other.wrapped!!.cloneTask(), other.model)
        return this
    }

    override fun copy(): TaskModel {
        return pool.obtain().init(this)
    }

    override fun free() {
        pool.free(this)
    }

    override fun toString(): String {
        return "LeafModel{" +
            "name='" + getName() + '\'' +
            (if (valid) ", valid" else ", invalid") +
            '}'
    }

    companion object {
        private val pool: Pool<LeafModel> = object : Pool<LeafModel>() {
            override fun newObject(): LeafModel {
                return LeafModel()
            }
        }

        fun obtain(task: LeafTask<*>, model: BehaviorTreeModel?): LeafModel {
            return pool.obtain().init(task, model)
        }

        fun free(leaf: LeafModel) {
            pool.free(leaf)
        }
    }
}
