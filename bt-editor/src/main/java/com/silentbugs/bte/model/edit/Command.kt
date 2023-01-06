package com.silentbugs.bte.model.edit

import com.badlogic.gdx.utils.Pool

abstract class Command(protected var type: Type) : Pool.Poolable {
    enum class Type {
        ADD, REMOVE, MOVE, COPY
    }

    abstract fun execute()
    abstract fun undo()
    abstract fun free()
}
