package com.silentbugs.bte.model.tasks

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ai.btree.decorator.Include
import com.badlogic.gdx.ai.btree.utils.BehaviorTreeLibraryManager
import com.badlogic.gdx.utils.Pool
import com.silentbugs.bte.model.BehaviorTreeModel

/**
 * Wraps Include task
 *
 *
 * For path to be considered valid it must be present in BehaviorTreeLibraryManager.getInstance().getLibrary()
 *
 *
 * Created by EvilEntity on 04/02/2016.
 */
class IncludeModel private constructor() : TaskModel(Type.INCLUDE), Pool.Poolable {
    fun init(include: Include<*>, model: BehaviorTreeModel?): IncludeModel {
        super.initTask(include, model)
        return this
    }

    protected fun init(other: IncludeModel): IncludeModel {
        // NOTE we can't clone this task as that will attempt to create subtree
        super.initTask(other.wrapped!!, other.model)
        return this
    }

    override fun isValid(): Boolean {
        // TODO check that we have a proper tree at specified subtree
        // TODO if it is valid, we want to add the sub tree as child of this task
        // TODO that will probably require custom include task that accepts children or something
        // TODO perhaps delegate path check to external thing, so it is possible to change it
        val include = wrapped as Include<in Any>
        // TODO test with dog.other
        include.subtree = "dog.other"
        include.lazy = true
        val library = BehaviorTreeLibraryManager.getInstance().library
        var includeChanged = false
        if (include.subtree != null) {
            // TODO use this from snapshot eventually
// 			if (library.hasBehaviorTree(include.subtree))
            try {
                val rootTask = library.createRootTask<Any>(include.subtree)
                if (include.childCount > 0) {
                    ReflectionUtils.remove(include.getChild(0), include)
                }
                include.addChild(rootTask)
                includeChanged = true
                valid = true
            } catch (e: RuntimeException) {
                // TODO proper handling, with type of error reported
                Gdx.app.error(TAG, "Subtree not found " + include.subtree, e)
            }
        }
        if (includeChanged) {
            for (i in 0 until children.size) {
                free(children[i])
            }
            children.clear()
            for (i in 0 until include.childCount) {
                val child = wrap(include.getChild(i), model)
                child.parent = this
                child.setReadOnly(true)
                children.add(child)
            }
        }
        return valid
    }

    override fun copy(): TaskModel {
        return pool.obtain().init(this)
    }

    override fun free() {
        pool.free(this)
    }

    override fun toString(): String {
        return "IncludeModel{" +
            "name='" + getName() + '\'' +
            ", subtree='" + (if (wrapped != null) (wrapped as Include<*>).subtree else "null") + '\'' +
            (if (valid) ", valid" else ", invalid") +
            '}'
    }

    class FanycInclude
    companion object {
        private val pool: Pool<IncludeModel> = object : Pool<IncludeModel>() {
            override fun newObject(): IncludeModel {
                return IncludeModel()
            }
        }

        fun obtain(include: Include<*>, model: BehaviorTreeModel?): IncludeModel {
            return pool.obtain().init(include, model)
        }

        fun free(leaf: IncludeModel) {
            pool.free(leaf)
        }

        private val TAG = IncludeModel::class.java.simpleName
    }
}
