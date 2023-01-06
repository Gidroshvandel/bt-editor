package com.silentbugs.bte.view

import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload
import com.badlogic.gdx.utils.Pool
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisLabel
import com.silentbugs.bte.model.tasks.TaskModel

/**
 * Created by EvilEntity on 05/02/2016.
 */
open class ViewPayload protected constructor() : Payload() {

    enum class Type {
        ADD, COPY, MOVE
    }

    companion object {
        private val pool: Pool<ViewPayload> = object : Pool<ViewPayload>() {
            override fun newObject(): ViewPayload {
                return ViewPayload()
            }
        }

        @JvmStatic
        fun obtain(text: String, payload: TaskModel, type: Type): ViewPayload {
            return pool.obtain().init(text, payload, type)
        }

        @JvmStatic
        fun free(task: ViewPayload) {
            pool.free(task)
        }
    }

    private val labelStyle = VisUI.getSkin().get("label-background", LabelStyle::class.java)
    protected var drag: VisLabel = VisLabel("", labelStyle)
    protected var valid: VisLabel = VisLabel("", labelStyle).apply {
        color = ViewColors.VALID
    }
    protected var invalid: VisLabel = VisLabel("", labelStyle).apply {
        color = ViewColors.INVALID
    }

    lateinit var task: TaskModel
    lateinit var type: Type
        protected set

    init {
        dragActor = drag
        validDragActor = valid
        invalidDragActor = invalid
    }

    private fun init(text: String, payload: TaskModel, type: Type): ViewPayload {
        task = payload
        this.type = type
        setDragText(text)
        return this
    }

    protected fun setDragText(text: String?) {
        // NOTE got to pack so background is sized correctly
        drag.setText(text)
        drag.pack()
        valid.setText(text)
        valid.pack()
        invalid.setText(text)
        invalid.pack()
    }
}
