package com.silentbugs.bte.view

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload

/**
 * Created by EvilEntity on 05/02/2016.
 */
abstract class ViewTarget(actor: Actor?) : DragAndDrop.Target(actor) {
    override fun drag(
        source: DragAndDrop.Source,
        payload: Payload,
        x: Float,
        y: Float,
        pointer: Int
    ): Boolean {
        return onDrag(source as ViewSource, payload as ViewPayload, x, y)
    }

    override fun drop(
        source: DragAndDrop.Source,
        payload: Payload,
        x: Float,
        y: Float,
        pointer: Int
    ) {
        onDrop(source as ViewSource, payload as ViewPayload, x, y)
    }

    abstract fun onDrag(source: ViewSource, payload: ViewPayload, x: Float, y: Float): Boolean
    abstract fun onDrop(source: ViewSource, payload: ViewPayload, x: Float, y: Float)
}
