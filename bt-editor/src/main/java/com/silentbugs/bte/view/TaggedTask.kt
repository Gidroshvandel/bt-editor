package com.silentbugs.bte.view

import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Tree
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload
import com.badlogic.gdx.utils.Pool
import com.kotcrab.vis.ui.widget.VisCheckBox
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.silentbugs.bte.model.BehaviorTreeModel
import com.silentbugs.bte.model.tasks.TaskModel.Companion.wrap
import com.silentbugs.bte.view.ViewPayload.Companion.free
import com.silentbugs.bte.view.ViewPayload.Companion.obtain

/**
 * Created by EvilEntity on 09/02/2016.
 */
class TaggedTask :
    Tree.Node<Tree.Node<*, *, *>, Any, Actor>(VisTable()),
    Pool.Poolable,
    Comparable<TaggedTask> {

    companion object {
        private const val DEFAULT_TAG = "<INVALID>"

        private val pool: Pool<TaggedTask> = object : Pool<TaggedTask>() {
            override fun newObject(): TaggedTask {
                return TaggedTask()
            }
        }

        @JvmStatic
        fun obtain(
            tag: String,
            cls: Class<out Task<*>?>,
            view: BehaviorTreeView,
            visible: Boolean
        ): TaggedTask {
            return pool.obtain().init(tag, cls, view, visible)
        }

        fun free(task: TaggedTask) {
            pool.free(task)
        }
    }

    private var container: VisTable = actor as VisTable
    private var label: VisLabel = VisLabel()

    var tag: String = DEFAULT_TAG

    var visible = false
    private var cls: Class<out Task<*>>? = null
    private var dad: DragAndDrop? = null
    private var model: BehaviorTreeModel? = null
    private var simpleName: String = DEFAULT_TAG
    private var source: ViewSource
    var parentTag: TaggedRoot? = null
    private var hide: VisCheckBox = VisCheckBox("", "radio")

    init {
        container.add(label)
        hide.isChecked = true
        container.add(hide)?.padLeft(5f)
        hide.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                if (parentTag != null) {
                    parentTag?.toggle(this@TaggedTask, hide.isChecked)
                }
            }
        })
        value = this
        source = object : ViewSource(label) {
            override fun dragStart(event: InputEvent, x: Float, y: Float, pointer: Int): Payload? {
                return cls?.let { obtain(simpleName, wrap(it, model), ViewPayload.Type.ADD) }
            }

            override fun dragStop(
                event: InputEvent,
                x: Float,
                y: Float,
                pointer: Int,
                payload: Payload?,
                target: DragAndDrop.Target?
            ) {
                // TODO do some other stuff if needed
                (payload as? ViewPayload)?.let { free(it) }
            }
        }
        reset()
    }

    private fun init(
        tag: String,
        cls: Class<out Task<*>>,
        view: BehaviorTreeView,
        visible: Boolean
    ): TaggedTask {
        this.tag = tag
        this.cls = cls
        this.visible = visible
        dad = view.dad
        model = view.model
        simpleName = cls.simpleName
        label.setText(simpleName)
        // source for adding task to tree
        dad?.addSource(source)
        hide.isChecked = visible
        return this
    }

    override fun reset() {
        tag = DEFAULT_TAG
        simpleName = DEFAULT_TAG
        cls = null
        label.setText(tag)
        // TODO remove source/target from dad
        if (dad != null) dad?.removeSource(source)
        dad = null
        parentTag = null
    }

    override fun compareTo(other: TaggedTask): Int {
        return if (tag == other.tag) {
            simpleName.compareTo(other.simpleName)
        } else {
            tag.compareTo(other.tag)
        }
    }
}
