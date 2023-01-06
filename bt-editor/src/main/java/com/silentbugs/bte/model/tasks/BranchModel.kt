package com.silentbugs.bte.model.tasks

import com.badlogic.gdx.ai.btree.BranchTask
import com.badlogic.gdx.utils.Pool
import com.silentbugs.bte.model.BehaviorTreeModel

/**
 * Created by EvilEntity on 04/02/2016.
 */
class BranchModel private constructor() : TaskModel(Type.BRANCH), Pool.Poolable {
    fun init(task: BranchTask<*>, model: BehaviorTreeModel?): BranchModel {
        super.initTask(task, model)
        return this
    }

    protected fun init(other: BranchModel): BranchModel {
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
        return "BranchModel{" +
            "name='" + getName() + '\'' +
            (if (valid) ", valid" else ", invalid") +
            ", childrenCount=" + childCount +
            '}'
    }

    companion object {
        private val pool: Pool<BranchModel> = object : Pool<BranchModel>() {
            override fun newObject(): BranchModel {
                return BranchModel()
            }
        }

        fun obtain(task: BranchTask<*>, model: BehaviorTreeModel?): BranchModel {
            return pool.obtain().init(task, model)
        }

        fun free(leaf: BranchModel) {
            pool.free(leaf)
        }
    }
}
