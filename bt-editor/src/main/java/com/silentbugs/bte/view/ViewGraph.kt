package com.silentbugs.bte.view

import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Array
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTextButton
import com.silentbugs.bte.model.BehaviorTreeModel
import com.silentbugs.bte.model.tasks.TaskModel
import com.silentbugs.bte.view.ViewColors.getColor

/**
 * Created by PiotrJ on 30/10/15.
 */
class ViewGraph(private var line: TextureRegionDrawable) :
    ScrollPane(Table()),
    BehaviorTreeModel.TreeStatusListener {

    private var root: Node? = null
    private var model: BehaviorTreeModel? = null
    private var container: Table = actor as Table

    fun init(model: BehaviorTreeModel) {
        rebuild(model)
    }

    fun reset() {
        container.reset()
        model?.removeListener(this)
    }

    private fun rebuild(model: BehaviorTreeModel) {
        if (root != null) clear()
        this.model = model
        model.addListener(this)
        val rootTask = model.getRoot()
        val node = Node(createTaskActor(rootTask), rootTask)
        root = node
        container.add(root).expand().fillX().top()
        if (rootTask != null) {
            for (i in 0 until rootTask.childCount) {
                val child = rootTask.getChild(i)
                createNodes(node, child)
            }
        }
    }

    private fun createNodes(parent: Node, task: TaskModel) {
        val node = Node(createTaskActor(task), task)
        parent.addNode(node)
        for (i in 0 until task.childCount) {
            val child = task.getChild(i)
            createNodes(node, child)
        }
    }

    private fun createTaskActor(task: TaskModel?): Actor {
        return VisLabel(task?.getName())
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        root?.let { drawConnections(batch, it) }
    }

    private val start = Vector2()
    private val end = Vector2()
    private val tmp = Vector2()
    private fun drawConnections(batch: Batch, root: Node) {
        if (!root.childrenVisible()) return
        val rootActor = root.actor
        start[rootActor.x + rootActor.width / 2] = rootActor.y
        rootActor.localToAscendantCoordinates(this, start)
        val sx = start.x
        val sy = start.y
        for (node in root.childrenNodes) {
            val nodeActor = node.actor
            end[nodeActor.x + nodeActor.width / 2] = nodeActor.y + nodeActor.height
            nodeActor.localToAscendantCoordinates(this, end)
            val len = tmp.set(end).sub(sx, sy).len()
            val angle = tmp.angleDeg()
            batch.color = node.actor.color
            line.draw(batch, sx, sy, 0f, 1.5f, len, 3f, 1f, 1f, angle)
            drawConnections(batch, node)
        }
    }

    private fun findNode(task: Task<*>?): Node? {
        return task?.let { this.root?.findNode(it) }
    }

    override fun statusChanged(task: TaskModel, previousStatus: Task.Status) {
        val node: Node? = findNode(task.wrapped)
        if (node != null) {
            val actor = node.actor
            actor.clearActions()
            actor.color = getColor(task.wrapped?.status)
            actor.addAction(Actions.color(Color.GRAY, 1.5f, Interpolation.pow3In))
        }
    }

    override fun rebuild() {
        clear()
        model?.let { rebuild(it) }
        invalidateHierarchy()
    }

    override fun clear() {
        container.clear()
        root = null
        model?.removeListener(this)
    }

    class Node(var actor: Actor, private var task: TaskModel?) : Table() {
        var childrenNodes = Array<Node>()
        private var top: Table
        private var hide: TextButton
        private var children: Table
        fun childrenVisible(): Boolean {
            return children.parent != null
        }

        fun addNode(node: Node) {
            childrenNodes.add(node)
            children.add(node).expand().fillX().top().pad(5f)
            if (hide.parent == null) top.add(hide).padLeft(5f)
        }

        fun findNode(task: Task<*>): Node? {
            if (this.task?.wrapped == task) return this
            for (node in childrenNodes) {
                val found = node.findNode(task)
                if (found != null) return found
            }
            return null
        }

        init {
            actor.color = getColor(task?.wrapped?.status)
            top = Table()
            add(top).row()
            top.add(actor)
            // NOTE would be lovely if we could make this work, depth is messed up like that.
            add().pad(15f).row() // .fill().expand().row();
            children = Table()
            add(children).expand().fill()
            hide = VisTextButton("<")
            hide.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent, x: Float, y: Float) {
                    if (children.parent == null) {
                        add(children).expand().fill()
                        hide.setText("<")
                    } else {
                        clear()
                        add(top).row()
                        add().pad(15f).row()
                        hide.setText(">")
                    }
                }
            })
        }
    }
}
