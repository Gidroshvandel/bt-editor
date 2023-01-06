package com.silentbugs.bte.view.edit

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.ai.utils.random.*
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.reflect.ClassReflection
import com.badlogic.gdx.utils.reflect.Field
import com.badlogic.gdx.utils.reflect.ReflectionException
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisSelectBox
import com.kotcrab.vis.ui.widget.VisTextArea
import com.kotcrab.vis.ui.widget.VisTextField
import com.silentbugs.bte.model.tasks.fields.EditableFields
import kotlin.reflect.KClass

/**
 * Created by PiotrJ on 06/10/15.
 */
internal object AttrFieldEdit {
    private val TAG = AttrFieldEdit::class.java.simpleName
    private val digitFieldFilter: VisTextField.TextFieldFilter =
        VisTextField.TextFieldFilter.DigitsOnlyFilter()
    private val digitPeriodFieldFilter =
        VisTextField.TextFieldFilter { _, c -> Character.isDigit(c) || c == '.' }

    @Throws(ReflectionException::class)
    internal fun stringAreaEditField(`object`: Any?, field: Field): Actor {
        val value = field[`object`] as String
        val ta = VisTextArea(value)
        ta.setPrefRows(3.25f)
        ta.maxLength = 80
        ta.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                val text = ta.text
                try {
                    field[`object`] = text
                } catch (e: ReflectionException) {
                    Gdx.app.error("String validator", "Failed to set field $field to $text", e)
                }
            }
        })
        addCancelOnESC(ta)
        return ta
    }

    @JvmStatic
    fun createEditField(field: EditableFields.EditableField): Actor? {
        val fType = field.type
        return when {
            fType == Float::class.javaPrimitiveType -> {
                floatEditField(field)
            }
            fType == Double::class.javaPrimitiveType -> {
                doubleEditField(field)
            }
            fType == Int::class.javaPrimitiveType -> {
                integerEditField(field)
            }
            fType == Long::class.javaPrimitiveType -> {
                longEditField(field)
            }
            fType == String::class.java -> {
                stringEditField(field)
            }
            fType == Boolean::class.javaPrimitiveType -> {
                booleanEditField(field)
            }
            fType != null && fType.isEnum -> {
                enumEditField(field)
            }
            fType != null && Distribution::class.java.isAssignableFrom(fType) -> {
                distEditField(field)
            }
            else -> {
                Gdx.app.error(TAG, "Not supported field type $fType in $field")
                null
            }
        }
    }

    private fun integerEditField(editableField: EditableFields.EditableField): Actor {
        return valueEditField(object : IntField() {
            override var int: Int
                get() = editableField.get() as Int
                set(value) {
                    editableField.set(value)
                }
        })
    }

    private fun longEditField(editableField: EditableFields.EditableField): Actor {
        return valueEditField(object : LongField() {
            override var long: Long
                get() = editableField.get() as Long
                set(value) {
                    editableField.set(value)
                }
        })
    }

    private fun floatEditField(editableField: EditableFields.EditableField): Actor {
        return valueEditField(object : FloatField() {
            override var float: Float
                get() = editableField.get() as Float
                set(value) {
                    editableField.set(value)
                }
        })
    }

    private fun doubleEditField(editableField: EditableFields.EditableField): Actor {
        return valueEditField(object : DoubleField() {
            override var double: Double
                get() = editableField.get() as Double
                set(value) {
                    editableField.set(value)
                }
        })
    }

    private fun stringEditField(field: EditableFields.EditableField): Actor {
        val value = field.get() as String?
        val tf = VisTextField(value)
        tf.setAlignment(Align.center)
        if (field.isRequired) {
            if (value == null || value.isEmpty()) tf.color = Color.RED
        }
        tf.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                val text = tf.text
                if (text.isEmpty() && field.isRequired) {
                    tf.color = Color.RED
                } else {
                    tf.color = Color.WHITE
                    field.set(text)
                }
            }
        })
        addCancelOnESC(tf)
        return tf
    }

    private fun enumEditField(field: EditableFields.EditableField): Actor {
        val values: Array<out Any> = field.type?.enumConstants ?: arrayOf()
        val sb = VisSelectBox<Any?>()
        sb.setItems(*values)
        sb.selected = field.get()
        sb.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                val selected = sb.selection.lastSelected
                field.set(selected)
            }
        })
        return sb
    }

    private fun booleanEditField(field: EditableFields.EditableField): Actor {
        val sb = VisSelectBox<Any?>()
        sb.setItems(true, false)
        sb.selected = field.get()
        sb.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                val selected = sb.selection.lastSelected
                field.set(selected)
            }
        })
        return sb
    }

    private fun createDistEditField(
        text: String,
        field: EditableFields.EditableField,
        dist: Distribution?,
        cont: Table,
        classes: Array<KClass<out DWrapper>>
    ) {
        val fields = Table()
        val sb = VisSelectBox<DWrapper>()
        cont.add(VisLabel(text)).row()
        cont.add(sb).row()
        cont.add(fields)
        var actual: DWrapper? = null
        val wrappers = arrayOfNulls<DWrapper>(classes.size)
        for (i in classes.indices) {
            val aClass = classes[i]
            try {
                val constructor = ClassReflection.getDeclaredConstructor(aClass::class.java)
                constructor.isAccessible = true
                val wrapper = constructor.newInstance() as DWrapper
                wrapper.init(field)
                wrappers[i] = wrapper
                if (wrapper.isWrapperFor(dist)) {
                    actual = wrapper
                }
            } catch (e: ReflectionException) {
                e.printStackTrace()
            }
        }
        if (actual == null) {
            Gdx.app.error(text, "Wrapper missing for $dist")
            return
        }
        actual.set(dist)
        actual.createEditFields(fields)
        sb.setItems(*wrappers)
        sb.selected = actual
        sb.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                val selected = sb.selection.lastSelected
                field.set(selected.create())
                fields.clear()
                selected.createEditFields(fields)
            }
        })
    }

    private fun createSimpleDistEditField(
        text: String,
        field: EditableFields.EditableField,
        dist: Distribution?,
        cont: Table,
        aClass: Class<out DWrapper>
    ) {
        val fields = Table()
        cont.add(VisLabel(text)).row()
        cont.add(fields)
        var wrapper: DWrapper? = null
        try {
            val constructor = ClassReflection.getDeclaredConstructor(aClass)
            constructor.isAccessible = true
            wrapper = constructor.newInstance() as DWrapper
            wrapper.init(field)
        } catch (e: ReflectionException) {
            e.printStackTrace()
        }
        if (wrapper == null) {
            Gdx.app.error(text, "Wrapper missing for $dist")
            return
        }
        wrapper.set(dist)
        wrapper.createEditFields(fields)
    }

    private fun distEditField(field: EditableFields.EditableField): Actor {
        // how do implement this crap? multiple inputs probably for each value in the thing
        val dist = field.get() as Distribution?
        val cont = Table()
        val type = field.type

        // add new edit fields, per type of distribution
        // if field type is one of the abstract classes, we want to be able to pick dist we want
        if (type == IntegerDistribution::class.java) {
            createDistEditField(
                "Integer distribution",
                field,
                dist,
                cont,
                arrayOf(CIDWrapper::class, TIDWrapper::class, UIDWrapper::class)
            )
            return cont
        }
        if (type == LongDistribution::class.java) {
            createDistEditField(
                "Long distribution",
                field,
                dist,
                cont,
                arrayOf(CLDWrapper::class, TLDWrapper::class, ULDWrapper::class)
            )
            return cont
        }
        if (type == FloatDistribution::class.java) {
            createDistEditField(
                "Float distribution",
                field,
                dist,
                cont,
                arrayOf(CFDWrapper::class, TFDWrapper::class, UFDWrapper::class, GFDWrapper::class)
            )
            return cont
        }
        if (type == DoubleDistribution::class.java) {
            createDistEditField(
                "Double distribution",
                field,
                dist,
                cont,
                arrayOf(CDDWrapper::class, TDDWrapper::class, UDDWrapper::class, GDDWrapper::class)
            )
            return cont
        }
        // if not we cant pick the type, just edit existing distribution
        val wrapper = getWrapperFor(type)
        if (wrapper == null) {
            Gdx.app.error(TAG, "Wrapper for $type not found!")
            return cont
        }
        createSimpleDistEditField(type!!.simpleName, field, dist, cont, wrapper)
        return cont
    }

    private fun getWrapperFor(type: Class<*>?): Class<out DWrapper>? {
        if (type == ConstantIntegerDistribution::class.java) {
            return CIDWrapper::class.java
        }
        if (type == ConstantLongDistribution::class.java) {
            return CLDWrapper::class.java
        }
        if (type == ConstantFloatDistribution::class.java) {
            return CFDWrapper::class.java
        }
        if (type == ConstantDoubleDistribution::class.java) {
            return CDDWrapper::class.java
        }
        if (type == GaussianFloatDistribution::class.java) {
            return GFDWrapper::class.java
        }
        if (type == GaussianDoubleDistribution::class.java) {
            return GDDWrapper::class.java
        }
        if (type == TriangularIntegerDistribution::class.java) {
            return TIDWrapper::class.java
        }
        if (type == TriangularLongDistribution::class.java) {
            return TLDWrapper::class.java
        }
        if (type == TriangularFloatDistribution::class.java) {
            return TFDWrapper::class.java
        }
        if (type == TriangularDoubleDistribution::class.java) {
            return TDDWrapper::class.java
        }
        if (type == UniformIntegerDistribution::class.java) {
            return UIDWrapper::class.java
        }
        if (type == UniformLongDistribution::class.java) {
            return ULDWrapper::class.java
        }
        if (type == UniformFloatDistribution::class.java) {
            return UFDWrapper::class.java
        }
        return if (type == UniformDoubleDistribution::class.java) {
            UDDWrapper::class.java
        } else null
    }

    private fun valueEditField(valueField: ValueField): Actor {
        val vtf = VisTextField("")
        vtf.text = valueField.get()
        vtf.setAlignment(Align.center)
        vtf.textFieldFilter = valueField.getFilter()
        vtf.setTextFieldListener { _, _ ->
            val text = vtf.text
            if (valueField.isValid(text)) {
                vtf.color = Color.WHITE
                valueField.set(text)
            } else {
                vtf.color = Color.RED
            }
        }
        addCancelOnESC(vtf)
        return vtf
    }

    @JvmStatic
    fun createPathEditField(field: EditableFields.EditableField): Actor {
        val value = field.get() as String?
        val tf = VisTextField(value)
        tf.setAlignment(Align.center)
        tf.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                val text = tf.text
                if (text.isEmpty()) {
                    tf.color = Color.RED
                    return
                }
                // TODO what else? we could try to parse it...
                val fh = Gdx.files.internal(text)
                if (fh.isDirectory || !fh.exists()) {
                    tf.color = Color.RED
                } else {
                    tf.color = Color.WHITE
                    field.set(text)
                }
            }
        })
        addCancelOnESC(tf)
        return tf
    }

    private fun addCancelOnESC(actor: Actor) {
        actor.addListener(object : InputListener() {
            override fun keyDown(event: InputEvent, keycode: Int): Boolean {
                if (keycode == Input.Keys.ESCAPE) {
                    actor.stage.keyboardFocus = null
                }
                return false
            }
        })
    }

    private abstract class ValueField {
        abstract fun get(): String?
        abstract fun isValid(value: String): Boolean
        abstract fun set(value: String)
        abstract fun getFilter(): VisTextField.TextFieldFilter
    }

    private abstract class IntField : ValueField() {

        override fun getFilter(): VisTextField.TextFieldFilter {
            return digitFieldFilter
        }

        override fun isValid(value: String): Boolean {
            return value.toIntOrNull() != null
        }

        override fun get(): String? {
            return int.toString()
        }

        override fun set(value: String) {
            int = value.toInt()
        }

        abstract var int: Int
    }

    private abstract class LongField : ValueField() {
        override fun isValid(value: String): Boolean {
            return value.toLongOrNull() != null
        }

        override fun getFilter(): VisTextField.TextFieldFilter {
            return digitFieldFilter
        }

        override fun get(): String? {
            return long.toString()
        }

        override fun set(value: String) {
            long = value.toLong()
        }

        abstract var long: Long
    }

    private abstract class FloatField : ValueField() {
        override fun isValid(value: String): Boolean {
            return value.toFloatOrNull() != null
        }

        override fun get(): String? {
            return float.toString()
        }

        override fun set(value: String) {
            float = value.toFloat()
        }

        override fun getFilter(): VisTextField.TextFieldFilter {
            return digitPeriodFieldFilter
        }

        abstract var float: Float
    }

    private abstract class DoubleField : ValueField() {
        override fun isValid(value: String): Boolean {
            return value.toDoubleOrNull() != null
        }

        override fun getFilter(): VisTextField.TextFieldFilter {
            return digitPeriodFieldFilter
        }

        override fun get(): String {
            return double.toString()
        }

        override fun set(value: String) {
            double = value.toDouble()
        }

        abstract var double: Double
    }

    abstract class DWrapper {
        private var field: EditableFields.EditableField? = null
        fun updateOwner() {
            field?.set(create())
        }

        abstract fun create(): Distribution?
        abstract fun set(dist: Distribution?)
        abstract fun createEditFields(fields: Table)
        abstract fun isWrapperFor(distribution: Distribution?): Boolean
        fun init(field: EditableFields.EditableField?): DWrapper {
            this.field = field
            return this
        }
    }

    class CIDWrapper : DWrapper() {
        var value = 0
        override fun createEditFields(fields: Table) {
            fields.add(VisLabel("value")).padRight(10f).row()
            fields.add(
                valueEditField(object : IntField() {
                    override var int: Int
                        get() = value
                        set(value) {
                            this@CIDWrapper.value = value
                            updateOwner()
                        }
                })
            )
        }

        override fun isWrapperFor(distribution: Distribution?): Boolean {
            return distribution is ConstantIntegerDistribution
        }

        override fun create(): IntegerDistribution {
            return ConstantIntegerDistribution(value)
        }

        override fun set(dist: Distribution?) {
            if (dist is ConstantIntegerDistribution) {
                value = dist.value
            }
        }

        override fun toString(): String {
            return "Constant"
        }
    }

    class TIDWrapper : DWrapper() {
        var low = 0
        var high = 0
        var mode = 0f
        override fun createEditFields(fields: Table) {
            fields.add(VisLabel("low")).padRight(10f).row()
            fields.add(
                valueEditField(object : IntField() {
                    override var int: Int
                        get() = low
                        set(value) {
                            low = value
                            updateOwner()
                        }
                })
            ).row()
            fields.add(VisLabel("high")).padRight(10f).row()
            fields.add(
                valueEditField(object : IntField() {
                    override var int: Int
                        get() = high
                        set(value) {
                            high = value
                            updateOwner()
                        }
                })
            ).row()
            fields.add(VisLabel("mode")).padRight(10f).row()
            fields.add(
                valueEditField(object : FloatField() {
                    override var float: Float
                        get() = mode
                        set(value) {
                            mode = value
                            updateOwner()
                        }
                })
            )
        }

        override fun create(): IntegerDistribution {
            return TriangularIntegerDistribution(low, high, mode)
        }

        override fun isWrapperFor(distribution: Distribution?): Boolean {
            return distribution is TriangularIntegerDistribution
        }

        override fun set(dist: Distribution?) {
            if (dist is TriangularIntegerDistribution) {
                low = dist.low
                high = dist.high
                mode = dist.mode
            }
        }

        override fun toString(): String {
            return "Triangular"
        }
    }

    class UIDWrapper : DWrapper() {
        var low = 0
        var high = 0
        override fun createEditFields(fields: Table) {
            fields.add(VisLabel("low")).padRight(10f).row()
            fields.add(
                valueEditField(object : IntField() {
                    override var int: Int
                        get() = low
                        set(value) {
                            low = value
                            updateOwner()
                        }
                })
            ).row()
            fields.add(VisLabel("high")).padRight(10f).row()
            fields.add(
                valueEditField(object : IntField() {
                    override var int: Int
                        get() = high
                        set(value) {
                            high = value
                            updateOwner()
                        }
                })
            )
        }

        override fun create(): IntegerDistribution {
            return UniformIntegerDistribution(low, high)
        }

        override fun isWrapperFor(distribution: Distribution?): Boolean {
            return distribution is UniformIntegerDistribution
        }

        override fun set(dist: Distribution?) {
            if (dist is UniformIntegerDistribution) {
                low = dist.low
                high = dist.high
            }
        }

        override fun toString(): String {
            return "Uniform"
        }
    }

    class CLDWrapper : DWrapper() {
        var value: Long = 0
        override fun createEditFields(fields: Table) {
            fields.add(VisLabel("value")).padRight(10f).row()
            fields.add(
                valueEditField(object : LongField() {
                    override var long: Long
                        get() = value
                        set(value) {
                            this@CLDWrapper.value = value
                            updateOwner()
                        }
                })
            )
        }

        override fun create(): Distribution {
            return ConstantLongDistribution(value)
        }

        override fun isWrapperFor(distribution: Distribution?): Boolean {
            return distribution is ConstantLongDistribution
        }

        override fun set(dist: Distribution?) {
            if (dist is ConstantLongDistribution) {
                value = dist.value
            }
        }

        override fun toString(): String {
            return "Constant"
        }
    }

    class TLDWrapper : DWrapper() {
        var low: Long = 0
        var high: Long = 0
        var mode = 0.0
        override fun createEditFields(fields: Table) {
            fields.add(VisLabel("low")).padRight(10f).row()
            fields.add(
                valueEditField(object : LongField() {
                    override var long: Long
                        get() = low
                        set(value) {
                            low = value
                            updateOwner()
                        }
                })
            ).row()
            fields.add(VisLabel("high")).padRight(10f).row()
            fields.add(
                valueEditField(object : LongField() {
                    override var long: Long
                        get() = high
                        set(value) {
                            high = value
                            updateOwner()
                        }
                })
            ).row()
            fields.add(VisLabel("mode")).padRight(10f).row()
            fields.add(
                valueEditField(object : DoubleField() {
                    override var double: Double
                        get() = mode
                        set(value) {
                            mode = value
                            updateOwner()
                        }
                })
            )
        }

        override fun create(): Distribution {
            return TriangularLongDistribution(low, high, mode)
        }

        override fun isWrapperFor(distribution: Distribution?): Boolean {
            return distribution is TriangularLongDistribution
        }

        override fun set(dist: Distribution?) {
            if (dist is TriangularLongDistribution) {
                low = dist.low
                high = dist.high
                mode = dist.mode
            }
        }

        override fun toString(): String {
            return "Triangular"
        }
    }

    class ULDWrapper : DWrapper() {
        var low: Long = 0
        var high: Long = 0
        override fun createEditFields(fields: Table) {
            fields.add(VisLabel("low")).padRight(10f).row()
            fields.add(
                valueEditField(object : LongField() {
                    override var long: Long
                        get() = low
                        set(value) {
                            low = value
                            updateOwner()
                        }
                })
            ).row()
            fields.add(VisLabel("high")).padRight(10f).row()
            fields.add(
                valueEditField(object : LongField() {
                    override var long: Long
                        get() = high
                        set(value) {
                            high = value
                            updateOwner()
                        }
                })
            ).row()
        }

        override fun create(): Distribution {
            return UniformLongDistribution(low, high)
        }

        override fun isWrapperFor(distribution: Distribution?): Boolean {
            return distribution is UniformLongDistribution
        }

        override fun set(dist: Distribution?) {
            if (dist is UniformLongDistribution) {
                low = dist.low
                high = dist.high
            }
        }

        override fun toString(): String {
            return "Uniform"
        }
    }

    class CFDWrapper : DWrapper() {
        var value = 0f
        override fun createEditFields(fields: Table) {
            fields.add(VisLabel("value")).padRight(10f).row()
            fields.add(
                valueEditField(object : FloatField() {
                    override var float: Float
                        get() = value
                        set(value) {
                            this@CFDWrapper.value = value
                            updateOwner()
                        }
                })
            )
        }

        override fun create(): Distribution {
            return ConstantFloatDistribution(value)
        }

        override fun isWrapperFor(distribution: Distribution?): Boolean {
            return distribution is ConstantFloatDistribution
        }

        override fun set(dist: Distribution?) {
            if (dist is ConstantFloatDistribution) {
                value = dist.value
            }
        }

        override fun toString(): String {
            return "Constant"
        }
    }

    class TFDWrapper : DWrapper() {
        var low = 0f
        var high = 0f
        var mode = 0f
        override fun createEditFields(fields: Table) {
            fields.add(VisLabel("low")).padRight(10f).row()
            fields.add(
                valueEditField(object : FloatField() {
                    override var float: Float
                        get() = low
                        set(value) {
                            low = value
                            updateOwner()
                        }
                })
            ).row()
            fields.add(VisLabel("high")).padRight(10f).row()
            fields.add(
                valueEditField(object : FloatField() {
                    override var float: Float
                        get() = high
                        set(value) {
                            high = value
                            updateOwner()
                        }
                })
            ).row()
            fields.add(VisLabel("mode")).padRight(10f).row()
            fields.add(
                valueEditField(object : FloatField() {
                    override var float: Float
                        get() = mode
                        set(value) {
                            mode = value
                            updateOwner()
                        }
                })
            )
        }

        override fun create(): Distribution {
            return TriangularFloatDistribution(low, high, mode)
        }

        override fun isWrapperFor(distribution: Distribution?): Boolean {
            return distribution is TriangularFloatDistribution
        }

        override fun set(dist: Distribution?) {
            if (dist is TriangularFloatDistribution) {
                low = dist.low
                high = dist.high
                mode = dist.mode
            }
        }

        override fun toString(): String {
            return "Triangular"
        }
    }

    class UFDWrapper : DWrapper() {
        var low = 0f
        var high = 0f
        override fun createEditFields(fields: Table) {
            fields.add(VisLabel("low")).padRight(10f).row()
            fields.add(
                valueEditField(object : FloatField() {
                    override var float: Float
                        get() = low
                        set(value) {
                            low = value
                            updateOwner()
                        }
                })
            ).row()
            fields.add(VisLabel("high")).padRight(10f).row()
            fields.add(
                valueEditField(object : FloatField() {
                    override var float: Float
                        get() = high
                        set(value) {
                            high = value
                            updateOwner()
                        }
                })
            ).row()
        }

        override fun create(): Distribution {
            return UniformFloatDistribution(low, high)
        }

        override fun isWrapperFor(distribution: Distribution?): Boolean {
            return distribution is UniformFloatDistribution
        }

        override fun set(dist: Distribution?) {
            if (dist is UniformFloatDistribution) {
                low = dist.low
                high = dist.high
            }
        }

        override fun toString(): String {
            return "Uniform"
        }
    }

    class GFDWrapper : DWrapper() {
        var mean = 0f
        var std = 0f
        override fun createEditFields(fields: Table) {
            fields.add(VisLabel("mean")).padRight(10f).row()
            fields.add(
                valueEditField(object : FloatField() {
                    override var float: Float
                        get() = mean
                        set(value) {
                            mean = value
                            updateOwner()
                        }
                })
            ).row()
            fields.add(VisLabel("STD")).padRight(10f).row()
            fields.add(
                valueEditField(object : FloatField() {
                    override var float: Float
                        get() = std
                        set(value) {
                            std = value
                            updateOwner()
                        }
                })
            )
        }

        override fun create(): Distribution {
            return GaussianFloatDistribution(mean, std)
        }

        override fun isWrapperFor(distribution: Distribution?): Boolean {
            return distribution is GaussianFloatDistribution
        }

        override fun set(dist: Distribution?) {
            if (dist is GaussianFloatDistribution) {
                mean = dist.mean
                std = dist.standardDeviation
            }
        }

        override fun toString(): String {
            return "Gaussian"
        }
    }

    class CDDWrapper : DWrapper() {
        var value = 0.0
        override fun createEditFields(fields: Table) {
            fields.add(VisLabel("value")).padRight(10f).row()
            fields.add(
                valueEditField(object : DoubleField() {
                    override var double: Double
                        get() = value
                        set(value) {
                            this@CDDWrapper.value = value
                            updateOwner()
                        }
                })
            )
        }

        override fun create(): Distribution {
            return ConstantDoubleDistribution(value)
        }

        override fun isWrapperFor(distribution: Distribution?): Boolean {
            return distribution is ConstantDoubleDistribution
        }

        override fun set(dist: Distribution?) {
            if (dist is ConstantDoubleDistribution) {
                value = dist.value
            }
        }

        override fun toString(): String {
            return "Constant"
        }
    }

    class TDDWrapper : DWrapper() {
        var low = 0.0
        var high = 0.0
        var mode = 0.0
        override fun createEditFields(fields: Table) {
            fields.add(VisLabel("low")).padRight(10f).row()
            fields.add(
                valueEditField(object : DoubleField() {
                    override var double: Double
                        get() = low
                        set(value) {
                            low = value
                            updateOwner()
                        }
                })
            ).row()
            fields.add(VisLabel("high")).padRight(10f).row()
            fields.add(
                valueEditField(object : DoubleField() {
                    override var double: Double
                        get() = high
                        set(value) {
                            high = value
                            updateOwner()
                        }
                })
            ).row()
            fields.add(VisLabel("mode")).padRight(10f).row()
            fields.add(
                valueEditField(object : DoubleField() {
                    override var double: Double
                        get() = mode
                        set(value) {
                            mode = value
                            updateOwner()
                        }
                })
            )
        }

        override fun create(): Distribution {
            return TriangularDoubleDistribution(low, high, mode)
        }

        override fun isWrapperFor(distribution: Distribution?): Boolean {
            return distribution is TriangularDoubleDistribution
        }

        override fun set(dist: Distribution?) {
            if (dist is TriangularDoubleDistribution) {
                low = dist.low
                high = dist.high
                mode = dist.mode
            }
        }

        override fun toString(): String {
            return "Triangular"
        }
    }

    class UDDWrapper : DWrapper() {
        var low = 0.0
        var high = 0.0
        override fun createEditFields(fields: Table) {
            fields.add(VisLabel("low")).padRight(10f).row()
            fields.add(
                valueEditField(object : DoubleField() {
                    override var double: Double
                        get() = low
                        set(value) {
                            low = value
                            updateOwner()
                        }
                })
            ).row()
            fields.add(VisLabel("high")).padRight(10f).row()
            fields.add(
                valueEditField(object : DoubleField() {
                    override var double: Double
                        get() = high
                        set(value) {
                            high = value
                            updateOwner()
                        }
                })
            )
        }

        override fun create(): Distribution {
            return UniformDoubleDistribution(low, high)
        }

        override fun isWrapperFor(distribution: Distribution?): Boolean {
            return distribution is UniformDoubleDistribution
        }

        override fun set(dist: Distribution?) {
            if (dist is UniformDoubleDistribution) {
                low = dist.low
                high = dist.high
            }
        }

        override fun toString(): String {
            return "Uniform"
        }
    }

    class GDDWrapper : DWrapper() {
        var mean = 0.0
        var std = 0.0
        override fun createEditFields(fields: Table) {
            fields.add(VisLabel("mean")).padRight(10f).row()
            fields.add(
                valueEditField(object : DoubleField() {
                    override var double: Double
                        get() = mean
                        set(value) {
                            mean = value
                            updateOwner()
                        }
                })
            ).row()
            fields.add(VisLabel("STD")).padRight(10f).row()
            fields.add(
                valueEditField(object : DoubleField() {
                    override var double: Double
                        get() = std
                        set(value) {
                            std = value
                            updateOwner()
                        }
                })
            )
        }

        override fun create(): Distribution {
            return GaussianDoubleDistribution(mean, std)
        }

        override fun isWrapperFor(distribution: Distribution?): Boolean {
            return distribution is GaussianDoubleDistribution
        }

        override fun set(dist: Distribution?) {
            if (dist is GaussianDoubleDistribution) {
                mean = dist.mean
                std = dist.standardDeviation
            }
        }

        override fun toString(): String {
            return "Gaussian"
        }
    }
}
