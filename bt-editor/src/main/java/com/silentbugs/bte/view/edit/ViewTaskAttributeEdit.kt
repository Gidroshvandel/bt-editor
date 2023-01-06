package com.silentbugs.bte.view.edit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Align
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.silentbugs.bte.model.tasks.TaskModel
import com.silentbugs.bte.view.edit.AttrFieldEdit.createEditField
import com.silentbugs.bte.view.edit.AttrFieldEdit.createPathEditField

/**
 * View for attribute editing on tasks
 *
 *
 * Created by PiotrJ on 21/10/15.
 */
class ViewTaskAttributeEdit : VisTable() {

    private var top: VisLabel? = null
    private var name: VisLabel? = null
    private val taskComment: VisLabel

    init {
        add(VisLabel("Edit task").also { top = it }).row()
        add(VisLabel("<?>").also { name = it })
        taskComment = VisLabel("", "small")
        taskComment.color = Color.LIGHT_GRAY
        taskComment.setAlignment(Align.center)
        row()
    }

    fun startEdit(task: TaskModel) {
        stopEdit()
        name?.setText(task.getName())
        val comment = task.comment
        if (comment != null) {
            taskComment.setText(comment)
            add(taskComment).row()
        }
        if (task.isReadOnly()) {
            add(VisLabel("Task is read only")).row()
        } else {
            addTaskFields(task)
        }
    }

    private fun addTaskFields(task: TaskModel) {
        for (field in task.getEditableFields()) {
            val cont = VisTable()
            val comment = field.comment
            if (field.skipName() && comment != null) {
                val tc = VisLabel(comment)
                tc.setAlignment(Align.center)
                cont.add(tc).row()
            } else {
                cont.add(VisLabel(field.name)).row()
                if (comment != null) {
                    val tc = VisLabel(comment, "small")
                    tc.setAlignment(Align.center)
                    tc.color = Color.LIGHT_GRAY
                    cont.add(tc).row()
                }
            }
            if (task.type === TaskModel.Type.INCLUDE && field.name == "subtree") {
                cont.add(createPathEditField(field))
            } else {
                // TODO need to handle area edit for comments
                cont.add(createEditField(field))
            }
            add(cont).row()
        }
    }

    fun stopEdit() {
        clear()
        add(top).row()
        add(name).row()
        name?.setText("<?>")
    }
}
