package com.silentbugs.bte.model.tasks

import com.badlogic.gdx.utils.Pool
import com.silentbugs.bte.model.BehaviorTreeModel

/**
 * Model for fake GuardTask
 *
 *
 * TODO add a way to set name in written tree
 *
 *
 * Created by EvilEntity on 04/02/2016.
 */
class GuardModel private constructor() : TaskModel(Type.GUARD), Pool.Poolable {
    // NOTE this shadows guard from ModelTask, but this fake guard cant have a real guard, so we should be fine maybe
    private var guard: TaskModel? = null
    private var guarded: TaskModel? = null

    fun init(guard: TaskModel?, guarded: TaskModel?, model: BehaviorTreeModel?): GuardModel {
        this.model = model
        minChildren = ReflectionUtils.getMinChildren(Guard::class.java)
        maxChildren = ReflectionUtils.getMaxChildren(Guard::class.java)
        children.clear()
        if (guard == null && guarded != null || guard != null && guarded == null) {
            throw AssertionError("Guard and guarded must be either both null or not null")
        }
        if (guard != null) {
            // NOTE we can probably assume that if we have those, we are initializing from working tree
            this.guard = guard
            children.add(guard)
            guard.parent = this
            guard.setIsGuard(guarded)
            this.guarded = guarded
            children.add(guarded)
            guarded?.parent = this
        }
        return this
    }

    protected fun init(other: GuardModel): GuardModel {
        // TODO this doesnt work at all
        other.wrapped?.cloneTask()?.let { super.initTask(it, other.model) }
        return this
    }

    override fun setGuard(newGuard: TaskModel) {
        insertChild(0, newGuard)
    }

    fun setGuarded(newGuarded: TaskModel) {
        insertChild(1, newGuarded)
    }

    /**
     * Due to how guards work, we need to do some garbage in here
     */
    override fun insertChild(at: Int, task: TaskModel) {
        task.parent = this
        val parent = parent
            ?: throw AssertionError("GuardModel requires parent before children can be added to it")
        val idInParent = parent.children.indexOf(this, true)
        // TODO make sure this works when we are a child of root
        // at == 0 -> insert as guard if possible
        if (at == 0) {
            if (children.size == 0) {
                // set task as child of this models parent at this models position
                children.add(task)
                guarded = task
                guarded?.insertInto(parent, idInParent)
                guarded?.parent = this
            } else if (children.size == 1) {
                // we already have a guarded task, set new task as guard and guard the task at pos 1
                children.insert(0, task)
                guard = task
                guard?.parent = this
                guarded?.setGuard(task)
                // 				guarded.insertInto(parent, idInParent);
            } else {
                throw AssertionError("Invalid children count")
            }
        } else if (at == 1) {
            // add guarded task, mark task at 0 as guard and set it as guard of new task
            children.add(task)
            // note we can only be at 1, if we have a child
            guarded?.removeFrom(parent)
            guard = guarded
            guarded = task
            guard?.let { guarded?.setGuard(it) }
            guarded?.insertInto(parent, idInParent)
            guarded?.parent = this
        } else {
            throw AssertionError("Invalid task at $at")
        }
    }

    override fun insertInto(parent: TaskModel, at: Int) {
        val wrapped = guarded?.wrapped
        val parentWrapped = parent.wrapped
        if (wrapped != null && parentWrapped != null) {
            ReflectionUtils.insert(wrapped, at, parentWrapped)
        }
    }

    override fun removeChild(task: TaskModel) {
        val parent = parent
            ?: throw AssertionError("GuardModel requires parent before children can be removed")
        val at = children.indexOf(task, true)
        val size = children.size
        children.removeValue(task, true)
        val idInParent = parent.children.indexOf(this, true)
        if (at == 0) {
            if (size == 2) {
                // remove guard from guarded
                // remove guard
                task.setIsNotGuard()
                guarded?.removeGuard()
                guard = null
            } else if (size == 1) {
                task.removeFrom(parent)
                task.parent = null
                guarded = null
            } else {
                throw AssertionError("Invalid children count")
            }
        } else if (at == 1) {
            // guarded removed
            guarded?.removeGuard()
            guarded?.removeFrom(parent)
            guard?.setIsNotGuard()
            guarded = guard
            guarded?.insertInto(parent, idInParent)
            guard = null
        } else {
            throw AssertionError("Invalid task at $at")
        }
        task.parent = null
    }

    override fun removeFrom(parent: TaskModel) {
        val wrapped = guarded?.wrapped
        val parentWrapped = parent.wrapped
        if (wrapped != null && parentWrapped != null) {
            ReflectionUtils.remove(wrapped, parentWrapped)
        }
    }

    override fun copy(): TaskModel {
        return pool.obtain().init(this)
    }

    override fun free() {
        pool.free(this)
    }

    override fun getName(): String {
        return "Guard"
    }

    override fun toString(): String {
        return "GuardModel{" +
            "guard=" + guard +
            ", guarded=" + guarded +
            (if (valid) ", valid" else ", invalid") +
            '}'
    }

    companion object {
        private val pool: Pool<GuardModel> = object : Pool<GuardModel>() {
            override fun newObject(): GuardModel {
                return GuardModel()
            }
        }

        fun obtain(guard: TaskModel?, guarded: TaskModel?, model: BehaviorTreeModel?): GuardModel {
            return pool.obtain().init(guard, guarded, model)
        }

        fun free(leaf: GuardModel) {
            pool.free(leaf)
        }
    }
}
