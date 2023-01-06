package com.silentbugs.bte.model

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ai.btree.BehaviorTree
import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.ai.btree.utils.BehaviorTreeLibraryManager
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectIntMap
import com.silentbugs.bte.BehaviorTreeWriter
import com.silentbugs.bte.EditorBehaviourTreeLibrary
import com.silentbugs.bte.model.edit.AddCommand
import com.silentbugs.bte.model.edit.CommandManager
import com.silentbugs.bte.model.edit.MoveCommand
import com.silentbugs.bte.model.edit.RemoveCommand.Companion.obtain
import com.silentbugs.bte.model.tasks.FakeRootModel
import com.silentbugs.bte.model.tasks.ReflectionUtils
import com.silentbugs.bte.model.tasks.TaskModel
import com.silentbugs.bte.model.tasks.TaskModel.Companion.free
import com.silentbugs.bte.model.tasks.TaskModel.Companion.inject
import com.silentbugs.bte.model.tasks.TaskModel.Companion.wrap
import kotlin.math.abs

/**
 * Created by EvilEntity on 04/02/2016.
 */
class BehaviorTreeModel : BehaviorTree.Listener<Any> {
    var tree: BehaviorTree<Any>? = null
        private set
    private var eTree: EditorBehaviourTreeLibrary.EditorBehaviourTree<*>? = null
    private val fakeRoot: FakeRootModel = FakeRootModel()
    private var root: TaskModel? = null
    private val commands: CommandManager = CommandManager()
    private var isDirty = false
    private var valid = false
    var isInitialized = false
        private set
    private var rebuild = false
    private var treeStatusListener: MutableList<TreeStatusListener> = mutableListOf()

    fun addListener(treeStatusListener: TreeStatusListener) {
        this.treeStatusListener.add(treeStatusListener)
    }

    fun removeListener(treeStatusListener: TreeStatusListener) {
        this.treeStatusListener.remove(treeStatusListener)
    }

    @Suppress("UNCHECKED_CAST")
    fun init(tree: BehaviorTree<*>) {
        reset()
        this.isInitialized = true
        isDirty = false
        this.tree = tree as BehaviorTree<Any>
        if (tree is EditorBehaviourTreeLibrary.EditorBehaviourTree<*>) {
            eTree = tree
            eTree?.isEdited = true
        }
        this.tree?.addListener(this)
        root = wrap(tree.getChild(0), this)
        root?.let {
            fakeRoot.init(it, this)
            valid = it.isValid()
        }
        // notify last so we are setup
        for (listener in listeners) {
            listener.onInit(this)
        }
    }

    fun reset() {
        this.isInitialized = false
        commands.reset()
        if (tree != null) {
            tree?.listeners?.removeValue(this, true)
        }
        // notify first, so listeners have a chance to do stuff
        for (listener in listeners) {
            listener.onReset(this)
        }
        free(fakeRoot)
        tree = null
        if (eTree != null) {
            eTree?.isEdited = false
        }
        eTree = null
        root = null
        isDirty = false
        valid = false
    }

    fun getRoot(): TaskModel? {
        return if (!this.isInitialized) null else fakeRoot
    }

    fun canAddBefore(what: TaskModel?, target: TaskModel): Boolean {
        // check if can insert what before target
        val parent = target.parent ?: return false
        return parent.canAdd(what)
    }

    fun addBefore(what: TaskModel, target: TaskModel) {
        if (!canAddBefore(what, target)) return
        val parent = target.parent
        if (parent != null) {
            val id = parent.getChildId(target)
            commands.execute(AddCommand.obtain(id, what, parent))
            tree?.resetTask()
            isDirty = true
            notifyChanged()
        }
    }

    fun canAddAfter(what: TaskModel, target: TaskModel): Boolean {
        // check if can insert what after target
        val parent = target.parent ?: return false
        return parent.canAdd(what)
    }

    fun addAfter(what: TaskModel, target: TaskModel) {
        if (!canAddAfter(what, target)) return
        val parent = target.parent
        if (parent != null) {
            val id = parent.getChildId(target)
            commands.execute(AddCommand.obtain(id + 1, what, parent))
            tree?.resetTask()
            isDirty = true
            notifyChanged()
        }
    }

    /**
     * Check if given task can be added to target task
     */
    fun canAdd(what: TaskModel?, target: TaskModel): Boolean {
        return target.canAdd(what)
    }

    /**
     * Add task that is not in the tree
     * It is not possible to add tasks to leaf tasks
     */
    fun add(what: TaskModel, target: TaskModel) {
        if (!canAdd(what, target)) return
        isDirty = true
        commands.execute(AddCommand.obtain(what, target))
        tree?.resetTask()
        notifyChanged()
    }

    fun canMoveBefore(what: TaskModel, target: TaskModel): Boolean {
        // we can move stuff within same parent
        if (what.parent === target.parent) return true
        // if we cant add to target, we cant move
        return if (!canAddBefore(what, target)) false else what === target || !what.hasChild(target)
        // cant add into itself or into own children
    }

    /**
     * Check if given task can be moved to target
     * It is not possible to move task into its own children for example
     */
    fun canMove(what: TaskModel, target: TaskModel): Boolean {
        return if (!canAdd(what, target)) false else !what.hasChild(target)
    }

    fun canMoveAfter(what: TaskModel, target: TaskModel): Boolean {
        // we can move stuff within same parent
        if (what.parent === target.parent) return true
        // if we cant add to target, we cant move
        return if (!canAddAfter(what, target)) false else what === target || !what.hasChild(target)
        // cant add into itself or into own children
    }

    fun moveBefore(what: TaskModel, target: TaskModel) {
        if (!canMoveBefore(what, target)) return
        val parent = target.parent
        if (parent != null) {
            val id = parent.getChildId(target)
            commands.execute(MoveCommand.obtain(id, what, parent))
            isDirty = true
            notifyChanged()
        }
    }

    /**
     * Move task that is in the tree to another position
     */
    fun move(what: TaskModel, target: TaskModel) {
        if (!canMove(what, target)) return
        commands.execute(MoveCommand.obtain(what, target))
        tree?.resetTask()
        isDirty = true
        notifyChanged()
    }

    fun moveAfter(what: TaskModel, target: TaskModel) {
        if (!canMoveAfter(what, target)) return
        val parent = target.parent
        if (parent != null) {
            val id = parent.getChildId(target)
            commands.execute(MoveCommand.obtain(id + 1, what, parent))
            isDirty = true
            notifyChanged()
        }
    }

    /**
     * Remove task from the tree
     */
    fun remove(what: TaskModel) {
        isDirty = true
        commands.execute(obtain(what))
        tree?.resetTask()
        notifyChanged()
    }

    fun undo() {
        isDirty = true
        if (commands.undo()) {
            tree?.resetTask()
            notifyChanged()
        }
    }

    fun redo() {
        isDirty = true
        if (commands.redo()) {
            tree?.resetTask()
            notifyChanged()
        }
    }

    private fun notifyChanged() {
        for (i in 0 until listeners.size) {
            listeners[i].onChange(this)
        }
    }

    private val listeners = Array<ModelChangeListener>()
    fun addChangeListener(listener: ModelChangeListener) {
        if (!listeners.contains(listener, true)) {
            listeners.add(listener)
        }
    }

    fun removeChangeListener(listener: ModelChangeListener) {
        listeners.removeValue(listener, true)
    }

    private val modelTasks = ObjectIntMap<TaskModel>()
    private val tasks = ObjectIntMap<Task<*>>()
    fun isValid(): Boolean {
        if (this.isInitialized && isDirty) {
            val root = root
            val newValid = root?.isValid() ?: false
            isDirty = false
            if (newValid != valid && newValid) {
                Gdx.app.log(TAG, "Reset tree")
                tree?.resetTask()
            }
            valid = newValid
            if (valid && root != null) {
                saveBackup()
                // TODO remove this probably
                modelTasks.clear()
                tasks.clear()
                Gdx.app.log(TAG, "doubleCheck")
                doubleCheck(modelTasks, tasks, root)
                modelTasks.entries().forEach { entry ->
                    if (entry.value > 1) {
                        Gdx.app.error(TAG, "Duped model task " + entry.key)
                    }
                }
                tasks.forEach { entry ->
                    if (entry.value > 1) {
                        Gdx.app.error(TAG, "Duped task " + entry.key)
                    }
                }
            }
        }
        return valid
    }

    private fun doubleCheck(
        modelTasks: ObjectIntMap<TaskModel>,
        tasks: ObjectIntMap<Task<*>>,
        task: TaskModel
    ) {
        modelTasks.put(task, modelTasks[task, 0] + 1)
        val wrapped: Task<*>? = task.wrapped
        if (wrapped != null) {
            tasks.put(wrapped, tasks[wrapped, 0] + 1)
        } else {
            Gdx.app.error(TAG, "Wrapped task of $task is null!")
        }
        for (i in 0 until task.childCount) {
            doubleCheck(modelTasks, tasks, task.getChild(i))
        }
    }

    override fun statusUpdated(task: Task<in Any>, previousStatus: Task.Status) {
        if (task is BehaviorTree<*>) {
            root?.wrappedUpdated(previousStatus, task.getStatus())
            return
        }
        val taskModel = root?.getModelTask(task)
        if (taskModel == null) {
            // TODO we are adding some tasks dynamically in our tree,
            Gdx.app.error(TAG, "Mddel task for $task not found, wtf?")
            rebuild = true
            return
        }
        taskModel.wrappedUpdated(previousStatus, task.status)
        treeStatusListener.forEach {
            it.statusChanged(taskModel, previousStatus)
        }
    }

    override fun childAdded(task: Task<in Any>, index: Int) {
    }

    // TODO move this save/load garbage to other class
    private val defaultBackupDir = Gdx.files.external("bte2/backups/")
    private var backupDir: FileHandle? = null
    private var treeName: String? = null

    /**
     * Set backup folder
     *
     * @param backup folder in which automatic backups will be saved, null to use default
     */
    fun setBackupDirectory(backup: FileHandle?) {
        if (backup != null && !backup.exists() && !backup.isDirectory) {
            Gdx.app.error(TAG, "Backup folder must be a directory and exist!")
        }
        backupDir = backup
    }

    private fun saveBackup() {
        // TODO do we want to limit number of backups?
        val dir = this.backupDir ?: defaultBackupDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val serialize = tree?.let { BehaviorTreeWriter.serialize(it) } ?: ""
        val name: String
        // we don't want - in name
        val hc = abs(serialize.hashCode())
        name = if (treeName != null) {
            treeName + "_" + hc + ".tree"
        } else {
            "tree_$hc.tree"
        }
        val child = dir.child(name)
        child.writeString(serialize, false)
        // 		Gdx.app.log(TAG, "Created backup: " + child.file().getAbsolutePath());
    }

    fun saveTree(fh: FileHandle): Exception? {
        return try {
            val serialize = tree?.let { BehaviorTreeWriter.serialize(it) } ?: ""
            fh.writeString(serialize, false)
            treeName = fh.nameWithoutExtension()
            null
        } catch (e: Exception) {
            Gdx.app.log(TAG, "Error saveTree", e)
            e
        }
    }

    fun loadTree(fh: FileHandle) {
        try {
            // TODO we probably want an option to load/save a tree without current one set
            val library = BehaviorTreeLibraryManager.getInstance().library
            val loadedTree: BehaviorTree<*> = library.createBehaviorTree<Any>(fh.path())
            // 				model.btLoaded(loadedTree);
            val old = tree
            reset()
            // we do this so whatever is holding original tree is updated
            // TODO maybe a callback instead of this garbage
            // 				if (old != null) {
            old?.let {
                ReflectionUtils.replaceRoot(it, loadedTree)
                init(it)
            }
            // 				} else {
            // 					model.init(loadedTree);
            // 				}
            if (library is EditorBehaviourTreeLibrary) {
                // TODO this is super garbage
                library.updateComments(this)
            }
            inject(old)
            treeName = fh.nameWithoutExtension()
            for (listener in listeners) {
                listener.onLoad(loadedTree, fh, this)
            }
        } catch (ex: Exception) {
            for (listener in listeners) {
                listener.onLoadError(ex, fh, this)
            }
        }
    }

    fun step() {
        if (rebuild) {
            rebuild()
        }
        if (this.isInitialized && isValid()) {
            try {
                if (eTree != null) {
                    eTree?.forceStep()
                } else {
                    tree?.step()
                }
            } catch (ex: IllegalStateException) {
                valid = false
                for (listener in listeners) {
                    listener.onStepError(ex, this)
                }
                notifyChanged()
            }
        }
    }

    /**
     * Rebuild current tree representation
     * This can be used if tree structure changed for some reason
     */
    private fun rebuild() {
        if (this.isInitialized) {
            tree?.let { init(it) }
            rebuild = false
            treeStatusListener.forEach {
                it.rebuild()
            }
        }
    }

    interface ModelChangeListener {
        /**
         * Called when model was reset
         */
        fun onReset(model: BehaviorTreeModel)

        /**
         * called when model was initialized with new behavior tree
         */
        fun onInit(model: BehaviorTreeModel)

        /**
         * called when model was initialized with new behavior tree
         */
        fun onChange(model: BehaviorTreeModel)

        /**
         * called when model loaded a tree from file
         */
        fun onLoad(tree: BehaviorTree<*>, file: FileHandle, model: BehaviorTreeModel)

        /**
         * called when model loaded a tree from file
         */
        fun onLoadError(ex: Exception, file: FileHandle, model: BehaviorTreeModel)

        /**
         * called when model saved a tree from file
         */
        fun onSave(tree: BehaviorTree<*>, file: FileHandle, model: BehaviorTreeModel)
        fun onStepError(ex: Exception, model: BehaviorTreeModel)
    }

    interface TreeStatusListener {
        fun statusChanged(task: TaskModel, previousStatus: Task.Status)
        fun rebuild()
    }

    companion object {
        private val TAG = BehaviorTreeModel::class.java.simpleName
    }
}
