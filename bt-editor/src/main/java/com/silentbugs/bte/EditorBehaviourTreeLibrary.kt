package com.silentbugs.bte

import com.badlogic.gdx.Files
import com.badlogic.gdx.ai.GdxAI
import com.badlogic.gdx.ai.btree.BehaviorTree
import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.ai.btree.utils.BehaviorTreeLibrary
import com.badlogic.gdx.ai.btree.utils.BehaviorTreeParser
import com.badlogic.gdx.ai.btree.utils.BehaviorTreeParser.DefaultBehaviorTreeReader
import com.badlogic.gdx.ai.btree.utils.DistributionAdapters
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.utils.ObjectMap
import com.silentbugs.bte.model.BehaviorTreeModel
import com.silentbugs.bte.model.tasks.TaskModel

/**
 * A `BehaviorTreeLibrary` is a repository of behavior tree archetypes. Behavior tree archetypes never run. Indeed, they are
 * only cloned to create behavior tree instances that can run. Has extra functionality useful for AIEditor
 * If useEditorBehaviourTree is `true`, a subclass of BehaviourTree will be returned, with some extra functionality, default is `false`
 *
 *
 * Created by PiotrJ on 16/02/16.
 */
class EditorBehaviourTreeLibrary(
    resolver: FileHandleResolver?,
    parseDebugLevel: Int,
    useEditorBehaviourTree: Boolean = false
) : BehaviorTreeLibrary(resolver, parseDebugLevel) {

    constructor(parseDebugLevel: Int = BehaviorTreeParser.DEBUG_NONE) : this(
        GdxAI.getFileSystem().newResolver(Files.FileType.Internal),
        parseDebugLevel
    )

    private val reader: EditorBehaviourTreeReader<Any> = EditorBehaviourTreeReader()
    private val editorParser: EditorParser<Any> =
        EditorParser(DistributionAdapters(), parseDebugLevel, reader)
    private val taskToComment = ObjectMap<Task<*>, String?>()

    init {
        parser = editorParser
        editorParser.useEditorBehaviourTree = useEditorBehaviourTree
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> createBehaviorTree(treeReference: String, blackboard: T): BehaviorTree<T> {
        val bt = retrieveArchetypeTree(treeReference)
        val cbt = bt.cloneTask() as BehaviorTree<T>
        cloneCommentMap(bt.getChild(0), cbt.getChild(0))
        cbt.setObject(blackboard)
        return cbt
    }

    private fun cloneCommentMap(src: Task<*>, dst: Task<*>) {
        val comment = getComment(src)
        if (comment != null) {
            taskToComment.put(dst, comment)
        }
        for (i in 0 until src.childCount) {
            cloneCommentMap(src.getChild(i), dst.getChild(i))
        }
    }

    fun updateComments(model: BehaviorTreeModel) {
        val root = model.getRoot()
        updateComments(root)
    }

    private fun updateComments(task: TaskModel?) {
        val wrapped = task?.wrapped
        // wrapped can be null if TaskModel is a Guard
        if (wrapped != null) {
            task.userComment = getComment(wrapped) ?: ""
        }
        for (i in 0 until (task?.childCount ?: 0)) {
            updateComments(task?.getChild(i))
        }
    }

    private fun getComment(task: Task<*>): String? {
        return taskToComment[task, null]
    }

    /**
     * If set to `true` newly parsed trees will be [EditorBehaviourTree]s with extra capabilities for the editor
     * The tree will ignore [BehaviorTree.step] if it is being edited
     *
     * @param useEditorBehaviourTree if true, newly parsed trees will be [EditorBehaviourTree]s
     */
    fun setUseEditorBehaviourTree(useEditorBehaviourTree: Boolean) {
        editorParser.useEditorBehaviourTree = useEditorBehaviourTree
    }

    fun isUseEditorBehaviourTree(): Boolean {
        return editorParser.useEditorBehaviourTree
    }

    private inner class EditorBehaviourTreeReader<E> : DefaultBehaviorTreeReader<E>(true) {
        private var lastComment: String? = null

        override fun comment(text: String) {
            super.comment(text)
            lastComment = text.trim { it <= ' ' }
        }

        override fun endStatement() {
            super.endStatement()
            if (prevTask != null && lastComment != null) {
                taskToComment.put(prevTask.task, lastComment)
                lastComment = null
            }
        }
    }

    private inner class EditorParser<E>(
        distributionAdapters: DistributionAdapters,
        debugLevel: Int,
        reader: DefaultBehaviorTreeReader<E>
    ) : BehaviorTreeParser<E>(distributionAdapters, debugLevel, reader) {
        var useEditorBehaviourTree = false

        public override fun createBehaviorTree(root: Task<E>, `object`: E): BehaviorTree<E> {
            if (debugLevel > DEBUG_LOW) printTree(root, 0)
            return if (useEditorBehaviourTree) EditorBehaviourTree(
                root,
                `object`
            ) else BehaviorTree(root, `object`)
        }
    }

    /**
     * Creates a behavior tree with a root task and a blackboard object. Both the root task and the blackboard object must be set
     * before running this behavior tree, see [addChild()][.addChild] and [setObject()][.setObject]
     * respectively.
     *
     * @param rootTask the root task of this tree. It can be `null`.
     * @param object   the blackboard. It can be `null`.
     */
    class EditorBehaviourTree<E>(rootTask: Task<E>? = null, `object`: E? = null) :
        BehaviorTree<E>(rootTask, `object`) {
        var isEdited = false

        /**
         * Creates a `BehaviorTree` with no root task and no blackboard object. Both the root task and the blackboard object must
         * be set before running this behavior tree, see [addChild()][.addChild] and [setObject()][.setObject]
         * respectively.
         */
        constructor() : this(null, null)

        /**
         * Creates a behavior tree with a root task and no blackboard object. Both the root task and the blackboard object must be set
         * before running this behavior tree, see [addChild()][.addChild] and [setObject()][.setObject]
         * respectively.
         *
         * @param rootTask the root task of this tree. It can be `null`.
         */
        constructor(rootTask: Task<E>) : this(rootTask, null)

        override fun step() {
            if (!isEdited) {
                super.step()
            }
        }

        fun forceStep() {
            super.step()
        }
    }
}
