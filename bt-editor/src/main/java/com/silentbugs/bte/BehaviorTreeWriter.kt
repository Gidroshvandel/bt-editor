package com.silentbugs.bte

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ai.btree.BehaviorTree
import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute
import com.badlogic.gdx.ai.btree.decorator.Include
import com.badlogic.gdx.ai.btree.utils.DistributionAdapters
import com.badlogic.gdx.ai.utils.random.Distribution
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.reflect.ClassReflection
import com.badlogic.gdx.utils.reflect.Field
import com.badlogic.gdx.utils.reflect.ReflectionException
import com.silentbugs.bte.model.tasks.TaskModel

/**
 * Utility class for serialization of [BehaviorTree]s in a format readable by [com.badlogic.gdx.ai.btree.utils.BehaviorTreeParser]
 *
 *
 * TODO implement guards, inline single task guards, stick complex guards into subtrees, add name to GuardModel?
 *
 *
 * Created by PiotrJ on 21/10/15.
 */
object BehaviorTreeWriter {

    private val TAG = BehaviorTreeWriter::class.java.simpleName

    /**
     * Save the tree in parsable format
     *
     * @param tree behavior tree to save
     * @param path external file path to save to, can't be a folder
     */
    fun save(tree: BehaviorTree<*>, path: String?) {
        val savePath = Gdx.files.external(path)
        if (savePath.isDirectory) {
            Gdx.app.error("BehaviorTreeSaver", "save path cannot be a directory!")
            return
        }
        savePath.writeString(serialize(tree), false)
    }

    /**
     * Serialize the tree to parser readable format
     *
     * @param tree BehaviorTree to serialize
     * @return serialized tree
     */
    fun serialize(tree: BehaviorTree<out Any>): String {
        return serialize(tree.getChild(0))
    }

    /**
     * Serialize the tree to parser readable format
     *
     * @param task task to serialize
     * @return serialized tree
     */
    fun serialize(task: Task<*>): String {
        val classes = Array<Class<out Task<*>>>()
        findClasses(task, classes)
        val taskToGuard = ObjectMap<Task<*>, GuardHolder>()
        findGuards(task, taskToGuard, 0)
        Gdx.app.log(TAG, "Found guards: $taskToGuard")
        classes.sort { o1, o2 -> o1.simpleName.compareTo(o2.simpleName) }
        val sb = StringBuilder("# Alias definitions\n")
        for (aClass in classes) {
            sb.append("import ").append(getAlias(aClass)).append(":\"").append(aClass.canonicalName)
                .append("\"\n")
        }
        writeGuards(sb, taskToGuard)
        sb.append("\nroot\n")
        writeTask(sb, task, 1, taskToGuard)
        return sb.toString()
    }

    private fun writeGuards(sb: StringBuilder, taskToGuard: ObjectMap<Task<*>, GuardHolder>) {
        val sorted = Array<GuardHolder>()
        val values = taskToGuard.values()
        for (value in values) {
            sorted.add(value)
        }
        sorted.sort()
        for (guard in sorted) {
            sb.append("\nsubtree name:\"")
            sb.append(guard.name)
            sb.append("\"\n")
            writeTask(sb, guard.guard, 1, taskToGuard)
        }
    }

    private fun findGuards(task: Task<*>, guards: ObjectMap<Task<*>, GuardHolder>, depth: Int) {
        val guard = task.guard
        if (guard != null) {
            guards.put(task, GuardHolder("guard" + guards.size, depth, guard, task))
            findGuards(guard, guards, depth + 1)
        }
        for (i in 0 until task.childCount) {
            findGuards(task.getChild(i), guards, depth + 1)
        }
    }

    private fun writeTask(
        sb: StringBuilder,
        task: Task<*>,
        depth: Int,
        taskToGuard: ObjectMap<Task<*>, GuardHolder>
    ) {
        for (i in 0 until depth) {
            sb.append("  ")
        }
        val guard = taskToGuard.get(task)
        if (guard != null) {
            sb.append("($")
            sb.append(guard.name)
            sb.append(") ")
        }
        sb.append(getAlias(task.javaClass))
        getTaskAttributes(sb, task)
        sb.append("\n")
        // include may have a whole tree as child, ignore it
        if (task is Include<*>) return
        for (i in 0 until task.childCount) {
            writeTask(sb, task.getChild(i), depth + 1, taskToGuard)
        }
    }

    /**
     * Serialize the tree to parser readable format
     *
     * @param task task to serialize
     * @return serialized tree
     */
    fun serialize(task: TaskModel): String {
        val classes = Array<Class<out Task<*>>>()
        findClasses(task.wrapped, classes)
        classes.sort { o1, o2 -> o1.simpleName.compareTo(o2.simpleName) }
        val sb = StringBuilder("# Alias definitions\n")
        for (aClass in classes) {
            sb.append("import ").append(getAlias(aClass)).append(":\"").append(aClass.canonicalName)
                .append("\"\n")
        }
        sb.append("\nroot\n")
        writeTask(sb, task, 1)
        return sb.toString()
    }

    private fun writeTask(sb: StringBuilder, modelTask: TaskModel, depth: Int) {
        if (modelTask.hasUserComment()) {
            for (i in 0 until depth) {
                sb.append("  ")
            }
            sb.append("# ")
            sb.append(modelTask.userComment)
            sb.append('\n')
        }
        for (i in 0 until depth) {
            sb.append("  ")
        }
        val task = modelTask.wrapped
        sb.append(getAlias(task!!.javaClass))
        getTaskAttributes(sb, task)
        sb.append('\n')
        // include may have a whole tree as child, ignore it
        if (task is Include<*>) return
        for (i in 0 until modelTask.childCount) {
            writeTask(sb, modelTask.getChild(i), depth + 1)
        }
    }

    private fun getTaskAttributes(sb: StringBuilder, task: Task<*>?) {
        val aClass: Class<*> = task!!.javaClass
        val fields = ClassReflection.getFields(aClass)
        for (f in fields) {
            val a = f.getDeclaredAnnotation(TaskAttribute::class.java) ?: continue
            val annotation = a.getAnnotation(TaskAttribute::class.java)
            sb.append(" ")
            getFieldString(sb, task, annotation, f)
        }
    }

    private fun getFieldString(
        sb: StringBuilder,
        task: Task<*>?,
        ann: TaskAttribute,
        field: Field
    ) {
        // prefer name from annotation if there is one
        var name: String? = ann.name
        if (name == null || name.isEmpty()) {
            name = field.name
        }
        sb.append(name)
        val o: Any
        try {
            field.isAccessible = true
            o = field[task]
        } catch (e: ReflectionException) {
            Gdx.app.error("", "Failed to get field", e)
            return
        }
        if (field.type.isEnum || field.type == String::class.java) {
            sb.append(":\"").append(o).append("\"")
        } else if (Distribution::class.java.isAssignableFrom(field.type)) {
            sb.append(":\"").append(toParsableString(o as Distribution)).append("\"")
        } else {
            sb.append(":").append(o)
        }
    }

    private var adapters: DistributionAdapters? = null

    /**
     * Attempts to create a parsable string for given distribution
     *
     * @param distribution distribution to create parsable string for
     * @return string that can be parsed by distribution classes
     */
    private fun toParsableString(distribution: Distribution?): String {
        requireNotNull(distribution) { "Distribution cannot be null" }
        if (adapters == null) adapters = DistributionAdapters()
        return adapters!!.toString(distribution)
    }

    private fun findClasses(task: Task<*>?, classes: Array<Class<out Task<*>>>) {
        val aClass: Class<out Task<*>> = task!!.javaClass
        val cName = aClass.canonicalName
        // ignore task classes from gdx-ai, as they are already accessible by the parser
        if (!cName.startsWith("com.badlogic.gdx.ai.btree.") && !classes.contains(aClass, true)) {
            classes.add(aClass)
        }
        val guard = task.guard
        if (guard != null) {
            findClasses(guard, classes)
        }
        for (i in 0 until task.childCount) {
            findClasses(task.getChild(i), classes)
        }
    }

    private val taskToAlias = ObjectMap<Class<out Task<*>>, String?>()

    /**
     * Get alias for given [Task] generated from its class name
     *
     * @param aClass class of task
     * @return valid alias for the class
     */
    private fun getAlias(aClass: Class<out Task<*>>?): String {
        requireNotNull(aClass) { "Class cannot be null" }
        var alias = taskToAlias[aClass, null]
        if (alias == null) {
            val name = aClass.simpleName
            alias = Character.toLowerCase(name[0]).toString() + if (name.length > 1) name.substring(
                1
            ) else ""
            taskToAlias.put(aClass, alias)
        }
        return alias
    }

    /**
     * Override default alias of a [Task] generated by [BehaviorTreeWriter.getAlias]
     * Passing in null alias will revert it to the default
     *
     * @param aClass class of task
     * @param alias  alias that will be used in saved tree
     */
    fun setAlias(aClass: Class<out Task<*>>?, alias: String?) {
        requireNotNull(aClass) { "Class cannot be null" }
        taskToAlias.put(aClass, alias)
    }

    private class GuardHolder(
        val name: String,
        val depth: Int,
        val guard: Task<*>,
        val guarded: Task<*>
    ) : Comparable<GuardHolder> {
        override fun toString(): String {
            return "GuardHolder{" +
                "name='" + name + '\'' +
                ", guard=" + guard.javaClass.simpleName +
                ", guarded=" + guarded.javaClass.simpleName +
                '}'
        }

        override fun compareTo(other: GuardHolder): Int {
            return if (depth == other.depth) name.compareTo(other.name) else other.depth - depth
            // we want stuff that is deeper first
        }
    }
}
