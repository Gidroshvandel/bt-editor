package com.silentbugs.bte.view

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Tree
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.BooleanArray
import com.badlogic.gdx.utils.Pool
import com.kotcrab.vis.ui.widget.VisCheckBox
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable

/**
 * Created by EvilEntity on 10/02/2016.
 */
class TaggedRoot private constructor() :
    Tree.Node<Tree.Node<*, *, *>, Any, Actor>(VisTable()),
    Pool.Poolable {

    companion object {
        private const val DEFAULT_TAG = "<RESET>"

        private val pool: Pool<TaggedRoot> = object : Pool<TaggedRoot>() {
            override fun newObject(): TaggedRoot {
                return TaggedRoot()
            }
        }

        @JvmStatic
        fun obtain(tag: String, view: BehaviorTreeView): TaggedRoot {
            return pool.obtain().init(tag, view)
        }

        fun free(task: TaggedRoot) {
            pool.free(task)
        }

        private val TAG = TaggedTask::class.java.simpleName
    }

    private var view: BehaviorTreeView? = null
    private var container: VisTable = actor as VisTable
    private var tagLabel: VisLabel = VisLabel()
    private var toggleHidden: VisCheckBox = VisCheckBox("", "radio")
    private var tag: String = DEFAULT_TAG
    private var tasks: Array<TaggedTask>
    private var visibleTasks: BooleanArray
    private var hiddenRevealed = false

    init {
        container.add(tagLabel)
        container.add(toggleHidden).padLeft(5f)
        toggleHidden.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                toggleHiddenTasks(toggleHidden.isChecked)
            }
        })
        tasks = Array()
        visibleTasks = BooleanArray()
    }

    private fun init(tag: String, view: BehaviorTreeView): TaggedRoot {
        this.tag = tag
        this.view = view
        tagLabel.setText(tag)
        return this
    }

    override fun reset() {
        tag = DEFAULT_TAG
        tagLabel.setText(tag)
        tasks.clear()
        visibleTasks.clear()
    }

    override fun add(node: Tree.Node<*, *, *>) {
        if (node is TaggedTask) {
            node.parentTag = this
            tasks.add(node)
            visibleTasks.add(node.visible)
            if (node.visible) {
                super.add(node)
            }
        } else {
            Gdx.app.error(TAG, "Node added to TaggedRoot should be TaggedTask, was $node")
        }
    }

    fun toggle(task: TaggedTask, show: Boolean) {
        for (i in 0 until tasks.size) {
            val other = tasks[i]
            if (other == task) {
                val isVisible = visibleTasks[i]
                if (show && !isVisible) {
                    visibleTasks[i] = true
                    if (task.parent == null) insert(i, task)
                } else if (!show && isVisible) {
                    visibleTasks[i] = false
                    if (!hiddenRevealed) {
                        task.remove()
                    }
                }
                break
            }
        }
    }

    private fun toggleHiddenTasks(checked: Boolean) {
        hiddenRevealed = checked
        if (hiddenRevealed) {
            for (i in 0 until tasks.size) {
                val task = tasks[i]
                if (!visibleTasks[i]) {
                    insert(i, task)
                }
            }
            expandAll()
        } else {
            for (i in 0 until tasks.size) {
                val task = tasks[i]
                if (!visibleTasks[i]) {
                    task.remove()
                }
            }
        }
    }

    fun free() {
        pool.free(this)
    }

    fun has(task: TaggedTask): Boolean {
        return tasks.contains(task, true)
    }
}
