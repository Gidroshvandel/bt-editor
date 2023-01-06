package com.silentbugs.bte.model.tasks

import com.badlogic.gdx.ai.btree.branch.Selector
import com.silentbugs.bte.model.BehaviorTreeModel

/**
 * Created by EvilEntity on 04/02/2016.
 */
class FakeRootModel : TaskModel(Type.ROOT) {
    fun init(root: TaskModel, model: BehaviorTreeModel?) {
        this.model = model
        children.clear()
        children.add(root)
        // need some wrapped task so remove command works
        val selector: Selector<in Any> = Selector<Any>()
        selector.addChild(root.wrapped)
        wrapped = selector
        root.parent = this
    }

    override fun free() {
        super.reset()
    }

    override fun copy(): TaskModel {
        return FakeRootModel()
    }

    override fun toString(): String {
        return "FakeRootModel{}"
    }

    override fun getName(): String {
        return "ROOT"
    }

    override fun isValid(): Boolean {
        return model?.isValid() ?: false
    }

    init {
        minChildren = 1
        maxChildren = 1
    }
}
