package com.silentbugs.bte.view

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Tree
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload
import com.badlogic.gdx.utils.Pool
import com.kotcrab.vis.ui.widget.VisImage
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.silentbugs.bte.model.BehaviorTreeModel
import com.silentbugs.bte.model.tasks.TaskModel
import com.silentbugs.bte.view.ViewColors.getColor
import com.silentbugs.bte.view.ViewPayload.Companion.free

/**
 * Created by EvilEntity on 09/02/2016.
 */
class ViewTask :
    Tree.Node<Tree.Node<*, *, *>, Tree.Node<*, *, *>, Actor>(VisTable()),
    Pool.Poolable,
    TaskModel.ChangeListener {
    internal enum class DropPoint {
        ABOVE, MIDDLE, BELOW
    }

    companion object {
        private val pool: Pool<ViewTask?> = object : Pool<ViewTask?>() {
            override fun newObject(): ViewTask {
                return ViewTask()
            }
        }

        @JvmStatic
        fun obtain(task: TaskModel, view: BehaviorTreeView): ViewTask? {
            return pool.obtain()?.init(task, view)
        }

        @JvmStatic
        fun free(task: ViewTask) {
            pool.free(task)
        }
    }

    private var dad: DragAndDrop? = null
    private var model: BehaviorTreeModel? = null
    var task: TaskModel? = null
        private set
    private var container: VisTable = actor as VisTable
    private var prefix: VisLabel = VisLabel()
    private var label: VisLabel
    private var status: VisLabel
    private var target: ViewTarget
    private var source: ViewSource
    private var separator: VisImage = VisImage()
    private var isMoving = false
    private var sourceAdded = false
    private var isMarkedAsGuarded = false

    init {
        container.add(prefix)
        label = VisLabel()
        container.add(label)
        status = VisLabel("")
        status.color = ViewColors.FRESH
        container.add(status).padLeft(5f)
        container.touchable = Touchable.enabled
        value = this

        target = TaskViewTarget(
            container,
            { task },
            { model },
            onDrag = { dropPoint, isValid ->
                updateSeparator(dropPoint, isValid)
            },
            onReset = {
                resetSeparator()
            }
        )
        source = object : ViewSource(label) {

            override fun dragStart(event: InputEvent, x: Float, y: Float, pointer: Int): Payload? {
                isMoving = true
                updateNameColor()
                return task?.let { ViewPayload.obtain(it.getName(), it, ViewPayload.Type.MOVE) }
            }

            override fun dragStop(
                event: InputEvent,
                x: Float,
                y: Float,
                pointer: Int,
                payload: Payload?,
                target: DragAndDrop.Target?
            ) {
                isMoving = false
                updateNameColor()
                (payload as? ViewPayload)?.let { free(it) }
            }
        }
        reset()
    }

    private fun init(task: TaskModel, view: BehaviorTreeView): ViewTask {
        // TODO add * after root/include when tree/subtree is not saved
        this.task = task
        dad = view.dad
        model = view.model
        separator.drawable = view.dimImg
        separator.isVisible = false
        container.addActor(separator)
        label.setText(task.getName())
        if (task.type !== TaskModel.Type.ROOT && !task.isReadOnly()) {
            dad?.addSource(source)
            sourceAdded = true
        }
        if (task.type !== TaskModel.Type.GUARD) {
            task.addListener(this)
            setStatus(task.wrapped?.status)
        }
        updateNameColor()
        dad?.addTarget(target)
        return this
    }

    private fun updateNameColor() {
        // NOTE it is possible that the task is freed before this is called from target callback
        // this can happen when we drop stuff back to drawer, it gets removed, tree is updated but the callback didn't yet fire
        val task = task ?: return
        label.color = when {
            task.isReadOnly() -> Color.GRAY
            task.isValid() -> task.getValidTaskColor()
            else -> ViewColors.INVALID
        }
        prefix.color = label.color
    }

    private fun TaskModel.getValidTaskColor() =
        when {
            isMoving -> Color.CYAN
            this.isGuard -> ViewColors.GUARD
            isMarkedAsGuarded -> ViewColors.GUARDED
            else -> Color.WHITE
        }

    private fun updateSeparator(dropPoint: DropPoint, isValid: Boolean) {
        resetSeparator()
        val color = if (isValid) ViewColors.VALID else ViewColors.INVALID
        separator.color = color
        separator.width = container.width
        separator.height = container.height / 4f
        when (dropPoint) {
            DropPoint.ABOVE -> {
                separator.isVisible = true
                separator.setPosition(0f, container.height - separator.height / 2)
            }
            DropPoint.MIDDLE -> label.color = color
            DropPoint.BELOW -> {
                separator.isVisible = true
                separator.setPosition(0f, -separator.height / 2)
            }
        }
    }

    private fun resetSeparator() {
        separator.isVisible = false
        updateNameColor()
    }

    override fun statusChanged(from: Task.Status?, to: Task.Status?) {
        setStatus(to)
    }

    private fun setStatus(taskStatus: Task.Status?) {
        status.setText(taskStatus.toString())
        status.color = getColor(taskStatus)
        status.clearActions()
        status.addAction(Actions.color(Color.GRAY, 1.5f, Interpolation.pow3In))
    }

    override fun reset() {
        if (task != null) task?.removeListener(this)
        task = null
        label.setText("<INVALID>")
        status.setText("")
        if (dad != null) {
            if (sourceAdded) {
                dad?.removeSource(source)
            }
            dad?.removeTarget(target)
        }
        sourceAdded = false
        separator.isVisible = false
        model = null
        for (node in children) {
            (node as? ViewTask)?.let { free(it) }
        }
        children.clear()
    }

    override fun toString(): String {
        return if (task == null) {
            "ViewNode{'null'}"
        } else {
            "ViewNode{'$task'}"
        }
    }
}

private class TaskViewTarget(
    visTable: VisTable,
    private val getTask: () -> TaskModel?,
    private val getModel: () -> BehaviorTreeModel?,
    private val onDrag: ((ViewTask.DropPoint, isValid: Boolean) -> Unit)? = null,
    private val onDrop: ((ViewTask.DropPoint) -> Unit)? = null,
    private val onReset: (() -> Unit)? = null
) : ViewTarget(visTable) {

    companion object {
        private const val DROP_MARGIN = 0.25f
    }

    private var copy = false

    override fun onDrag(source: ViewSource, payload: ViewPayload, x: Float, y: Float): Boolean {
        val task = getTask() ?: return false
        val model = getModel() ?: return false
        copy =
            Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)
        val dropPoint = getDropPoint(actor, y)
        var isValid = !task.isReadOnly() && payload.task != task
        if (isValid) {
            isValid = when (dropPoint) {
                ViewTask.DropPoint.ABOVE -> model.onDrop(
                    payload.type,
                    {
                        canMoveBefore(payload.task, task)
                    },
                    {
                        canAddBefore(payload.task, task)
                    }
                )
                ViewTask.DropPoint.MIDDLE ->
                    model.onDrop(
                        payload.type,
                        {
                            canMove(payload.task, task)
                        },
                        {
                            canAdd(payload.task, task)
                        }
                    )
                ViewTask.DropPoint.BELOW -> model.onDrop(
                    payload.type,
                    {
                        canMoveAfter(payload.task, task)
                    },
                    {
                        canAddAfter(payload.task, task)
                    }
                )
            }
        }
        onDrag?.invoke(dropPoint, isValid)
        return isValid
    }

    override fun onDrop(source: ViewSource, payload: ViewPayload, x: Float, y: Float) {
        val task = getTask() ?: return
        val model = getModel() ?: return
        val dropPoint = getDropPoint(actor, y)
        when (dropPoint) {
            ViewTask.DropPoint.ABOVE -> model.onDrop(
                payload.type,
                {
                    moveBefore(payload.task, task)
                },
                {
                    addBefore(payload.task.copyIfNeeded(), task)
                }
            )
            ViewTask.DropPoint.MIDDLE -> model.onDrop(
                payload.type,
                {
                    move(payload.task, task)
                },
                {
                    add(payload.task.copyIfNeeded(), task)
                }
            )
            ViewTask.DropPoint.BELOW -> model.onDrop(
                payload.type,
                {
                    moveAfter(payload.task, task)
                },
                {
                    addAfter(payload.task.copyIfNeeded(), task)
                }
            )
        }
        onDrop?.invoke(dropPoint)
    }

    override fun reset(source: DragAndDrop.Source, payload: Payload) {
        onReset?.invoke()
    }

    private fun TaskModel.copyIfNeeded() = if (copy) this.copy() else this

    private fun <R> BehaviorTreeModel.onDrop(
        type: ViewPayload.Type,
        canMove: BehaviorTreeModel.() -> R,
        canAdd: BehaviorTreeModel.() -> R
    ): R =
        if (!copy && type == ViewPayload.Type.MOVE) {
            canMove.invoke(this)
        } else {
            canAdd.invoke(this)
        }

    private fun getDropPoint(actor: Actor, y: Float): ViewTask.DropPoint {
        val a = y / actor.height
        if (a < DROP_MARGIN) {
            return ViewTask.DropPoint.BELOW
        } else if (a > 1 - DROP_MARGIN) {
            return ViewTask.DropPoint.ABOVE
        }
        return ViewTask.DropPoint.MIDDLE
    }
}
