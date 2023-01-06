package com.silentbugs.bte.model.tasks

import com.badlogic.gdx.ai.btree.Decorator
import com.badlogic.gdx.utils.Pool
import com.silentbugs.bte.model.BehaviorTreeModel

/**
 * TODO this would be nice, but is a bit of pain to implement
 * Possible situations, if we allow more than 1 task:
 * - 0 children, invalid state
 * - add - 1 child, valid state
 * - 1 child, valid state
 * - add - 2 children, invalid state
 * - remove - 0 children, invalid state
 * - 2 children, invalid state
 * - remove - 1 child, valid state
 *
 *
 * Created by EvilEntity on 04/02/2016.
 */
class DecoratorModel private constructor() : TaskModel(Type.DECORATOR), Pool.Poolable {
    fun init(task: Decorator<*>, model: BehaviorTreeModel?): DecoratorModel {
        super.initTask(task, model)
        return this
    }

    protected fun init(other: DecoratorModel): DecoratorModel {
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
        return "DecoratorModel{" +
            "name='" + getName() + '\'' +
            (if (valid) ", valid" else ", invalid") +
            '}'
    }

    companion object {
        private val pool: Pool<DecoratorModel> = object : Pool<DecoratorModel>() {
            override fun newObject(): DecoratorModel {
                return DecoratorModel()
            }
        }

        fun obtain(task: Decorator<*>, model: BehaviorTreeModel?): DecoratorModel {
            return pool.obtain().init(task, model)
        }

        fun free(leaf: DecoratorModel) {
            pool.free(leaf)
        }
    }
}
