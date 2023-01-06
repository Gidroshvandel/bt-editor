package com.silentbugs.bte.model.tasks

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ai.btree.*
import com.badlogic.gdx.ai.btree.annotation.TaskConstraint
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectIntMap
import com.badlogic.gdx.utils.reflect.Annotation
import com.badlogic.gdx.utils.reflect.ClassReflection
import com.badlogic.gdx.utils.reflect.ReflectionException

/**
 * Created by PiotrJ on 10/02/16.
 */
object ReflectionUtils {
    private val TAG = ReflectionUtils::class.java.simpleName
    private val minChildrenCache = ObjectIntMap<Class<*>>()
    private val maxChildrenCache = ObjectIntMap<Class<*>>()
    fun getMinChildren(task: Task<*>): Int {
        return getMinChildren(task.javaClass)
    }

    fun getMinChildren(cls: Class<out Task<*>?>): Int {
        // Constraint can only have >= 0 value
        var min = minChildrenCache[cls, -1]
        if (min < 0) {
            findConstraints(cls)
            // if its still -1, we failed
            min = minChildrenCache[cls, -1]
        }
        return min
    }

    fun getMaxChildren(task: Task<*>): Int {
        return getMaxChildren(task.javaClass)
    }

    fun getMaxChildren(cls: Class<out Task<*>?>): Int {
        // Constraint can only have >= 0 value
        var max = maxChildrenCache[cls, -1]
        if (max < 0) {
            findConstraints(cls)
            // if its still -1, we failed
            max = maxChildrenCache[cls, -1]
        }
        return max
    }

    private fun findConstraints(clazz: Class<*>) {
        var cls = clazz
        var annotation: Annotation? = null
        val tCls = cls
        // walk the class hierarchy till we get the annotation
        while (annotation == null && cls != Any::class.java) {
            annotation = ClassReflection.getDeclaredAnnotation(cls, TaskConstraint::class.java)
            if (annotation == null) {
                cls = cls.superclass
            }
        }
        if (annotation == null) {
            Gdx.app.error(TAG, "TaskConstraint annotation not found on class $tCls")
            return
        }
        val constraint = annotation.getAnnotation(TaskConstraint::class.java)
        minChildrenCache.put(tCls, constraint.minChildren)
        maxChildrenCache.put(tCls, constraint.maxChildren)
    }

    fun clearReflectionCache() {
        minChildrenCache.clear()
        maxChildrenCache.clear()
    }

    @Suppress("UNCHECKED_CAST")
    fun insert(what: Task<*>, at: Int, into: Task<*>): Boolean {
        try {
            // we need to check it task is in target before we add, as that will happen on init
            if (into is BranchTask<*>) {
                val field = ClassReflection.getDeclaredField(BranchTask::class.java, "children")
                field.isAccessible = true
                val children = field[into] as Array<Task<*>>
                // disallow if out of bounds,  allow to insert if empty
                if (at > children.size && at > 0) {
                    Gdx.app.error(
                        "INSERT",
                        "cannot insert $what to $into at $at as its out of range"
                    )
                    return false
                }
                if (!children.contains(what, true)) {
                    children.insert(at, what)
                    // note in this class there are some more children that we need to deal with
                    if (into is SingleRunningChildBranch<*>) {
                        // set the field to null so it is recreated with correct size
                        val randomChildren = ClassReflection.getDeclaredField(
                            SingleRunningChildBranch::class.java,
                            "randomChildren"
                        )
                        randomChildren.isAccessible = true
                        randomChildren[into] = null
                    }
                } else {
                    Gdx.app.error(
                        "INSERT",
                        "cannot insert $what to $into at $at, target already contains task"
                    )
                    return false
                }
                return true
            } else if (into is Decorator<*>) {
                // can insert if decorator is empty
                val field = ClassReflection.getDeclaredField(Decorator::class.java, "child")
                field.isAccessible = true
                val old = field[into]
                // ignore at, just replace
                if (old == null || old !== what) {
                    field[into] = what
                    return true
                } else {
                    Gdx.app.error("INSERT", "cannot insert $what to $into as its a decorator")
                }
            } else {
                Gdx.app.error("INSERT", "cannot insert $what to $into as its a leaf")
            }
        } catch (e: ReflectionException) {
            Gdx.app.error("REMOVE", "ReflectionException error", e)
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    fun remove(what: Task<*>, from: Task<*>): Boolean {
        try {
            // we need to check it task is in target before we add, as that will happen on init
            when (from) {
                is BranchTask<*> -> {
                    val field = ClassReflection.getDeclaredField(BranchTask::class.java, "children")
                    field.isAccessible = true
                    val children = field[from] as Array<Task<*>>
                    if (children.removeValue(what, true)) {
                        // note in this class there are some more children that we need to deal with
                        if (from is SingleRunningChildBranch<*>) {
                            // set the field to null so it is recreated with correct size
                            val randomChildren = ClassReflection.getDeclaredField(
                                SingleRunningChildBranch::class.java,
                                "randomChildren"
                            )
                            randomChildren.isAccessible = true
                            randomChildren[from] = null
                        }
                    }
                    return false
                }
                is Decorator<*> -> {
                    val field = ClassReflection.getDeclaredField(Decorator::class.java, "child")
                    field.isAccessible = true
                    val old = field[from]
                    if (old === what || old == null) {
                        field[from] = null
                    } else {
                        return false
                    }
                    return old != null
                }
                else -> {
                    Gdx.app.error("REMOVE", "cannot remove $what from $from as its a leaf")
                }
            }
        } catch (e: ReflectionException) {
            Gdx.app.error("REMOVE", "ReflectionException error", e)
        }
        return false
    }

    /**
     * Replace root of tree with root of with
     *
     * @param tree to replace root of
     * @param with donor tree
     * @return if replacement was successful
     */
    fun replaceRoot(tree: BehaviorTree<*>, with: BehaviorTree<*>): Boolean {
        tree.resetTask()
        with.resetTask()
        try {
            val field = ClassReflection.getDeclaredField(BehaviorTree::class.java, "rootTask")
            field.isAccessible = true
            field[tree] = field[with]
            return true
        } catch (e: ReflectionException) {
            e.printStackTrace()
        }
        return false
    }
}
