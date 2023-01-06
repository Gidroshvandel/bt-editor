package com.silentbugs.bte.model.tasks.fields

import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool
import com.badlogic.gdx.utils.reflect.Annotation
import com.badlogic.gdx.utils.reflect.ClassReflection
import com.badlogic.gdx.utils.reflect.Field
import com.badlogic.gdx.utils.reflect.ReflectionException
import com.silentbugs.bte.TaskComment
import com.silentbugs.bte.model.tasks.TaskModel

/**
 * Wraps a field and allows for easy setting and getting values
 *
 *
 * Created by EvilEntity on 15/02/2016.
 */
object EditableFields {
    /**
     * Get all editable fields for this TaskModel wrapped task + userComment
     */
    fun get(modelTask: TaskModel, out: Array<EditableField>): Array<EditableField> {
        out.add(CommentEditableField.obtain(modelTask))
        modelTask.wrapped?.let { get(it, out) }
        return out
    }

    private operator fun get(task: Task<*>, out: Array<EditableField>): Array<EditableField> {
        val aClass: Class<*> = task.javaClass
        val fields = ClassReflection.getFields(aClass)
        for (f in fields) {
            var a: Annotation? = f.getDeclaredAnnotation(TaskAttribute::class.java) ?: continue
            val annotation = a?.getAnnotation(TaskAttribute::class.java)
            a = f.getDeclaredAnnotation(TaskComment::class.java)
            var tc: TaskComment? = null
            if (a != null) {
                tc = a.getAnnotation(TaskComment::class.java)
            }
            annotation?.let { addField(task, it, f, tc, out) }
        }
        return out
    }

    fun release(fields: Array<EditableField>) {
        for (field in fields) {
            field.free()
        }
        fields.clear()
    }

    private fun addField(
        task: Task<*>,
        ann: TaskAttribute,
        field: Field,
        tc: TaskComment?,
        out: Array<EditableField>
    ) {
        var name: String = ann.name
        if (name.isEmpty()) {
            name = field.name
        }
        if (tc != null) {
            val comment: String = tc.value.trim { it <= ' ' }
            val skipName: Boolean = tc.skipFieldName
            out.add(BaseEditableField.obtain(name, task, field, ann.required, comment, skipName))
        } else {
            out.add(BaseEditableField.obtain(name, task, field, ann.required, null, false))
        }
    }

    interface EditableField {
        /**
         * @return values of this field for assigned instance
         */
        fun get(): Any?

        /**
         * @param value value of the field, must be of correct type
         */
        fun set(value: Any?)

        /**
         * @return owner instance of this field
         */
        val owner: Any?

        /**
         * @return name of the field
         */
        val name: String?

        /**
         * @return comment fpr this field or null
         */
        val comment: String?

        /**
         * @return type of this field
         */
        val type: Class<*>?

        /**
         * @return if this field is annotated as required
         */
        val isRequired: Boolean

        /**
         * @return if the field name should be skipped
         */
        fun skipName(): Boolean
        fun free()
    }

    private class BaseEditableField : EditableField, Pool.Poolable {

        override var name: String? = null
            private set

        private var task: Task<*>? = null

        private var field: Field? = null

        override var isRequired = false
            private set
        override var comment: String? = null
            private set

        private var skipName = false

        private fun init(
            name: String?,
            task: Task<*>,
            field: Field,
            required: Boolean,
            comment: String?,
            skipName: Boolean
        ): EditableField {
            this.name = name
            this.task = task
            this.field = field
            isRequired = required
            this.comment = comment
            this.skipName = skipName
            return this
        }

        override fun get(): Any? {
            try {
                return field?.get(task)
            } catch (e: ReflectionException) {
                e.printStackTrace()
            }
            return null
        }

        override fun set(value: Any?) {
            if (isRequired && value == null) throw AssertionError("Field " + name + " in " + task?.javaClass?.simpleName + " is required!")
            // TOOD proper check, this fails for float.class Float.class etc
// 			if (value != null && !field.getType().isAssignableFrom(value.getClass()))
// 				throw new AssertionError("Invalid value type for field " + name + ", got " + value.getClass() + ", expected " + field.getType());
            try {
                field?.set(task, value)
            } catch (e: ReflectionException) {
                e.printStackTrace()
            }
        }

        override val owner: Any?
            get() = task

        override val type: Class<*>?
            get() = this.field?.type

        override fun skipName(): Boolean {
            return skipName
        }

        override fun free() {
            pool.free(this)
        }

        override fun reset() {
            name = null
            task = null
            field = null
        }

        companion object {
            private val pool: Pool<BaseEditableField> = object : Pool<BaseEditableField>() {
                override fun newObject(): BaseEditableField {
                    return BaseEditableField()
                }
            }

            fun obtain(
                name: String?,
                task: Task<*>,
                field: Field,
                required: Boolean,
                comment: String?,
                skipName: Boolean
            ): EditableField {
                return pool.obtain().init(name, task, field, required, comment, skipName)
            }
        }
    }

    private class CommentEditableField : EditableField, Pool.Poolable {

        override var owner: TaskModel? = null
            private set

        private fun init(task: TaskModel): EditableField {
            this.owner = task
            return this
        }

        override fun get(): Any? {
            return this.owner?.userComment
        }

        override fun set(value: Any?) {
            if (value?.javaClass != String::class.java) throw AssertionError("Invalid value type for field " + name + ", got " + value?.javaClass + ", expected String.class")
            this.owner?.userComment = value as String? ?: ""
        }

        override val name: String
            get() = "# Comment"
        override val comment: String?
            get() = null

        override val type: Class<*>
            get() = String::class.java

        override val isRequired: Boolean
            get() = false

        override fun skipName(): Boolean {
            return false
        }

        override fun free() {
            pool.free(this)
        }

        override fun reset() {
            this.owner = null
        }

        companion object {
            private val pool: Pool<CommentEditableField> = object : Pool<CommentEditableField>() {
                override fun newObject(): CommentEditableField {
                    return CommentEditableField()
                }
            }

            fun obtain(task: TaskModel): EditableField {
                return pool.obtain().init(task)
            }
        }
    }
}
