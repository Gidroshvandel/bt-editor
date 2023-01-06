package com.silentbugs.bte.model.tasks

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ai.btree.BranchTask
import com.badlogic.gdx.ai.btree.Decorator
import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.ai.btree.decorator.Include
import com.badlogic.gdx.ai.btree.decorator.Repeat
import com.badlogic.gdx.ai.utils.random.ConstantIntegerDistribution
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool
import com.badlogic.gdx.utils.reflect.ClassReflection
import com.badlogic.gdx.utils.reflect.ReflectionException
import com.silentbugs.bte.TaskComment
import com.silentbugs.bte.TaskInjector
import com.silentbugs.bte.model.BehaviorTreeModel
import com.silentbugs.bte.model.tasks.fields.EditableFields
import kotlin.math.min

/**
 * Created by EvilEntity on 04/02/2016.
 */
abstract class TaskModel protected constructor(val type: Type) : Pool.Poolable {

    enum class Type {
        INCLUDE, LEAF, BRANCH, DECORATOR, ROOT, NULL, GUARD
    }

    var parent: TaskModel? = null

    private var guard: TaskModel? = null

    var wrapped: Task<*>? = null
        protected set

    // NOTE there aren't that many children per task, 4 is a decent start
    var children = Array<TaskModel>(4)
    protected var init = false

    protected var valid = false
    protected var readOnly = false
        private set
    var isGuard = false
        protected set
    var guardedTask: TaskModel? = null
        protected set

    protected var minChildren = 0

    protected var maxChildren = 0

    protected var model: BehaviorTreeModel? = null

    // comment from wrapped task class
    var comment: String? = null
        protected set

    // user comment from loaded tree
    var userComment: String = ""
        set(value) {
            field = value.trim { it <= ' ' }
        }

    protected var fields = Array<EditableFields.EditableField>()

    protected fun initTask(task: Task<*>, model: BehaviorTreeModel?) {
        this.model = model
        init = true
        wrapped = task
        minChildren = ReflectionUtils.getMinChildren(task)
        maxChildren = ReflectionUtils.getMaxChildren(task)
        for (i in 0 until task.childCount) {
            val child = wrap(task.getChild(i), model)
            child.parent = this
            children.add(child)
        }
        val guard = task.guard
        if (guard != null) {
            this.guard = wrap(guard, model)
            this.guard?.setIsGuard(this)
        }
        EditableFields.get(this, fields)
        val aClass: Class<out Task<*>> = task.javaClass
        val a = ClassReflection.getDeclaredAnnotation(aClass, TaskComment::class.java)
        if (a != null) {
            val tc = a.getAnnotation(TaskComment::class.java)
            val value: String = tc.value
            if (value.isNotEmpty()) {
                comment = value
            }
        }
    }

    fun setIsGuard(guarded: TaskModel?) {
        isGuard = true
        guardedTask = guarded
        for (child in children) {
            child.setIsGuard(guarded)
        }
    }

    fun setIsNotGuard() {
        isGuard = false
        guardedTask = null
        for (child in children) {
            child.setIsNotGuard()
        }
    }

    fun canAdd(task: TaskModel?): Boolean {
        // TODO special handling for some things maybe
        return !readOnly && children.size < maxChildren
    }

    open fun isValid(): Boolean {
        valid = !(children.size < minChildren || children.size > maxChildren)
        if (guard != null) {
// 			guard.validate();
            valid = valid and (guard?.isValid() ?: false)
        }
        for (child in children) {
// 			child.validate();
            valid = valid and child.isValid()
        }
        return valid
    }

    // 	public void validate () {
    // 		valid = !(children.size < minChildren || children.size > maxChildren);
    // 		if (guard != null) {
    // 			guard.validate();
    // 			valid &= guard.isValid();
    // 		}
    // 		for (TaskModel child : children) {
    // 			child.validate();
    // 			valid &= child.isValid();
    // 		}
    // 	}
    val childCount: Int
        get() = children.size

    fun setReadOnly(readOnly: Boolean) {
        this.readOnly = readOnly
        for (i in 0 until children.size) {
            children[i].setReadOnly(readOnly)
        }
    }

    fun getChild(id: Int): TaskModel {
        return children[id]
    }

    /**
     * Check if given task is in this task
     */
    fun hasChild(task: TaskModel): Boolean {
        if (this === task) return true
        for (child in children) {
            if (child === task || child.hasChild(task)) {
                return true
            }
        }
        return false
    }

    fun addChild(task: TaskModel) {
        // can we do this? or do we need some specific code in here
        insertChild(children.size, task)
    }

    open fun insertChild(at: Int, task: TaskModel) {
        // if at is larger then size, we will insert as last
        val minAt = min(at, children.size)
        children.insert(minAt, task)
        task.parent = this
        task.insertInto(this, minAt)
    }

    open fun insertInto(parent: TaskModel, at: Int) {
        val wrapped = wrapped
        val parentWrapped = parent.wrapped
        if (wrapped != null && parentWrapped != null) {
            ReflectionUtils.insert(wrapped, at, parentWrapped)
        }
    }

    open fun removeChild(task: TaskModel) {
        children.removeValue(task, true)
        task.removeFrom(this)
        task.parent = null
    }

    open fun removeFrom(parent: TaskModel) {
        val wrapped = wrapped
        val parentWrapped = parent.wrapped
        if (wrapped != null && parentWrapped != null) {
            ReflectionUtils.remove(wrapped, parentWrapped)
        }
    }

    fun getChildId(what: TaskModel): Int {
        return children.indexOf(what, true)
    }

    open fun setGuard(newGuard: TaskModel) {
        removeGuard()
        guard = newGuard
        wrapped?.guard = newGuard.wrapped
        newGuard.setIsGuard(this)
    }

    fun removeGuard() {
        guard = null
        wrapped?.guard = null
    }

    fun isGuarded(): Boolean {
        return guard != null
    }

    private var name: String = ""

    fun setName(name: String) {
        this.name = name
    }

    open fun getName(): String {
        val nullText = "<!null!>"
        if (name.isEmpty()) {
            name = if (wrapped != null) wrapped?.javaClass?.simpleName ?: nullText else nullText
        }
        return name
    }

    override fun toString(): String {
        return "TaskModel{" +
            "name='" + name + '\'' +
            (if (valid) ", valid" else ", invalid") +
            ", type=" + type +
            ", parent=" + parent +
            '}'
    }

    override fun reset() {
        for (child in children) {
            free(child)
        }
        children.clear()
        if (guard != null) {
            free(guard)
        }
        guard = null
        guardedTask = null
        isGuard = false
        wrapped = null
        parent = null
        init = false
        name = ""
        readOnly = false
        comment = null
        userComment = ""
        listeners.clear()
        EditableFields.release(fields)
    }

    fun hasUserComment(): Boolean {
        return userComment.isNotEmpty()
    }

    fun getEditableFields(): Array<EditableFields.EditableField> {
        return fields
    }

    fun isReadOnly(): Boolean {
        return readOnly
    }

    abstract fun free()
    abstract fun copy(): TaskModel
    fun getModelTask(task: Task<*>): TaskModel? {
        if (wrapped === task) return this
        // TODO use a map for this garbage?
        if (guard != null) {
            val found = guard?.getModelTask(task)
            if (found != null) return found
        }
        for (child in children) {
            val found = child.getModelTask(task)
            if (found != null) return found
        }
        return null
    }

    private val listeners = Array<ChangeListener>(2)
    fun addListener(listener: ChangeListener) {
        if (!listeners.contains(listener, true)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: ChangeListener) {
        listeners.removeValue(listener, true)
    }

    fun wrappedUpdated(from: Task.Status?, to: Task.Status?) {
        for (listener in listeners) {
            listener.statusChanged(from, to)
        }
    }

    interface ChangeListener {
        fun statusChanged(from: Task.Status?, to: Task.Status?)
    }

    companion object {
        private val TAG = TaskModel::class.java.simpleName

        @JvmField
        var injector: TaskInjector? = null

        /**
         * Injects dependencies into [Task] and its children, if it was set via [com.silentbugs.bte.AIEditor.setTaskInjector]
         *
         * @param task task to inject dependencies into
         */
        fun inject(task: Task<*>?) {
            if (injector != null && task != null) {
                injector?.inject(task)
            }
        }

        fun wrap(task: Task<*>, model: BehaviorTreeModel?): TaskModel {
            var taskModel: TaskModel = NullModel.INSTANCE
            when (task) {
                is Include<*> -> {
                    taskModel = IncludeModel.obtain(task, model)
                }
                is LeafTask<*> -> {
                    taskModel = LeafModel.obtain(task, model)
                }
                is Guard -> {
                    taskModel = GuardModel.obtain(null, null, model)
                }
                is BranchTask<*> -> {
                    taskModel = BranchModel.obtain(task, model)
                }
                is Decorator<*> -> {
                    taskModel = DecoratorModel.obtain(task, model)
                }
                else -> {
                    Gdx.app.error(TAG, "Invalid task class! $task")
                }
            }
            if (task.guard != null) {
                val guard = wrap(task.guard, model)
                return GuardModel.obtain(guard, taskModel, model)
            }
            return taskModel
        }

        @JvmStatic
        fun wrap(cls: Class<out Task<*>>, model: BehaviorTreeModel?): TaskModel {
            // note we dont actual instance of this
            if (cls == Guard::class.java) {
                return GuardModel.obtain(null, null, model)
            }
            // TODO how do we want to make an instance of this?
            try {
                val task = ClassReflection.newInstance(cls)
                if (task != null) {
                    if (cls == Repeat::class.java) {
                        val repeat = task as Repeat<*>
                        repeat.times = ConstantIntegerDistribution.ONE
                    }
                    inject(task)
                    return wrap(task, model)
                }
            } catch (e: ReflectionException) {
                e.printStackTrace()
            }
            return NullModel.INSTANCE
        }

        fun free(task: TaskModel?) {
            task?.free()
        }
    }
}
