package com.silentbugs.bte.model.tasks

/**
 * Created by EvilEntity on 04/02/2016.
 */
class NullModel private constructor() : TaskModel(Type.NULL) {

    override fun free() {}

    override fun copy(): TaskModel {
        return INSTANCE
    }

    override fun toString(): String {
        return "NullModel{}"
    }

    companion object {
        val INSTANCE = NullModel()
    }
}
