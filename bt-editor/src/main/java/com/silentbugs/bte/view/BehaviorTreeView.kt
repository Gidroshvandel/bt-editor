package com.silentbugs.bte.view

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ai.btree.BehaviorTree
import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Tree
import com.badlogic.gdx.scenes.scene2d.ui.Window
import com.badlogic.gdx.scenes.scene2d.utils.*
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.*
import com.kotcrab.vis.ui.widget.file.FileChooser
import com.kotcrab.vis.ui.widget.file.FileChooserAdapter
import com.silentbugs.bte.AIEditor
import com.silentbugs.bte.model.BehaviorTreeModel
import com.silentbugs.bte.model.tasks.TaskModel
import com.silentbugs.bte.view.TaggedTask.Companion.obtain
import com.silentbugs.bte.view.ViewTask.Companion.free
import com.silentbugs.bte.view.ViewTask.Companion.obtain
import com.silentbugs.bte.view.edit.ViewTaskAttributeEdit
import kotlin.math.min

/**
 * Created by EvilEntity on 04/02/2016.
 */
class BehaviorTreeView(editor: AIEditor) : Table(), BehaviorTreeModel.ModelChangeListener {
    var model: BehaviorTreeModel = editor.model
        private set
    private var topMenu: VisTable = VisTable(true)
    private var drawerScrollPane: VisScrollPane
    private var treeScrollPane: VisScrollPane
    private var taskDrawer: VisTree<Tree.Node<*, *, *>, Any>
    private var tree: VisTree<Tree.Node<*, *, *>, Any>
    private var taskEdit: VisTable = VisTable(true)
    var dad: DragAndDrop = DragAndDrop()
        private set
    private var removeTarget: ViewTarget
    var dimImg: SpriteDrawable =
        SpriteDrawable(VisUI.getSkin().getDrawable(DRAWABLE_WHITE) as SpriteDrawable)
        private set
    private val btToggle: VisTextButton = VisTextButton("AutoStep", "toggle")
    private val btStep: VisTextButton = VisTextButton("Step")
    private val btReset: VisTextButton = VisTextButton("Restart")
    private var selectedNode: Tree.Node<*, *, *>? = null
    private var vtEdit: ViewTaskAttributeEdit? = null
    private var saveBtn: VisTextButton = VisTextButton("Save")
    private var saveAsBtn: VisTextButton = VisTextButton("Save As")
    private var loadBtn: VisTextButton = VisTextButton("Load")
    private var saveChooser: FileChooser = FileChooser(FileChooser.Mode.SAVE)
    private var loadChooser: FileChooser = FileChooser(FileChooser.Mode.OPEN)
    private val showGraph: TextButton = VisTextButton("Graph")
    private val graph: ViewGraph =
        ViewGraph((VisUI.getSkin().getDrawable("white") as TextureRegionDrawable))
    private val graphWindow: Window = VisWindow("Graph view").apply {
        isResizable = true
        add(graph).expand().fill()
        pack()
    }
    private var graphPosSet = false
    private var lastSave: FileHandle? = null

    companion object {
        var DRAWABLE_WHITE = "dialogDim"
        private val TAG = BehaviorTreeView::class.java.simpleName
    }

    init {
        dimImg.sprite.color = Color.WHITE
        // create label style with background used by ViewPayloads
        val btnStyle = VisUI.getSkin().get(ButtonStyle::class.java)
        val labelStyle = LabelStyle(VisUI.getSkin().get(LabelStyle::class.java))
        labelStyle.background = btnStyle.up
        VisUI.getSkin().add("label-background", labelStyle)
        add(topMenu).colspan(3)
        val undoBtn = VisTextButton("Undo")
        undoBtn.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                model.undo()
            }
        })
        val redoBtn = VisTextButton("Redo")
        redoBtn.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                model.redo()
            }
        })
        topMenu.add(undoBtn)
        topMenu.add(redoBtn).padRight(20f)
        addSaveLoad(topMenu)
        val btControls = VisTable(true)
        topMenu.add(btControls).padLeft(20f)
        btToggle.isChecked = true
        btControls.add(btToggle)
        btToggle.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                if (btToggle.isDisabled) return
                editor.setAutoStepBehaviorTree(btToggle.isChecked)
            }
        })
        btStep.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                if (btStep.isDisabled) return
                editor.forceStepBehaviorTree()
            }
        })
        btControls.add(btStep)
        btReset.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                if (btReset.isDisabled) return
                editor.restartBehaviorTree()
            }
        })
        btControls.add(btReset)
        row()
        taskDrawer = VisTree<Tree.Node<*, *, *>, Any>()
        taskDrawer.ySpacing = -2f
        taskDrawer.setFillParent(true)
        val treeView = VisTable(true)
        tree = object : VisTree<Tree.Node<*, *, *>, Any>() {
            override fun setOverNode(overNode: Tree.Node<*, *, *>?) {
                val old = this.overNode
                if (old != overNode) {
                    onOverNodeChanged(old, overNode)
                }
                super.setOverNode(overNode)
            }
        }
        tree.selection.multiple = false
        tree.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                val newNode = tree.selection.lastSelected as Tree.Node<*, *, *>
                onSelectionChanged(selectedNode, newNode)
                selectedNode = newNode
            }
        })
        tree.ySpacing = 0f
        // add dim to tree so its in same coordinates as nodes
        treeView.add(tree).fill().expand()
        treeScrollPane = VisScrollPane(treeView)
        treeScrollPane.addListener(FocusOnEnterListener())
        val taskView = VisTable(true)
        taskView.add(taskDrawer).fill().expand()
        drawerScrollPane = VisScrollPane(taskView)
        drawerScrollPane.addListener(FocusOnEnterListener())
        taskEdit.add(ViewTaskAttributeEdit().also { vtEdit = it }).expand().top()
        val drawerTreeSP = VisSplitPane(drawerScrollPane, treeScrollPane, false)
        drawerTreeSP.setSplitAmount(.33f)
        val dtEditSP = VisSplitPane(drawerTreeSP, taskEdit, false)
        dtEditSP.setSplitAmount(.75f)
        add(dtEditSP).grow().pad(5f)
        removeTarget = object : ViewTarget(drawerScrollPane) {
            override fun onDrag(
                source: ViewSource,
                payload: ViewPayload,
                x: Float,
                y: Float
            ): Boolean {
                return payload.type == ViewPayload.Type.MOVE
            }

            override fun onDrop(source: ViewSource, payload: ViewPayload, x: Float, y: Float) {
                model.remove(payload.task)
            }
        }
        dad.addTarget(removeTarget)
        showGraph.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                val stage = stage
                // center only if new add
                if (graphWindow.stage == null) {
                    graphWindow.pack()
                    stage.addActor(graphWindow)
                    graphWindow.setSize(
                        min(graphWindow.prefWidth, stage.width),
                        min(graphWindow.prefHeight, stage.height)
                    )
                    graphWindow.validate()
                    if (!graphPosSet) {
                        graphPosSet = true
                        graphWindow.setPosition(
                            stage.width / 2 - graphWindow.width / 2,
                            stage.height / 2 - graphWindow.height / 2
                        )
                    }
                } else {
                    stage.addActor(graphWindow)
                }
            }
        })
        btControls.add(showGraph)
        val graphClose: TextButton = VisTextButton("X")
        graphWindow.titleTable.add(graphClose).padRight(-padRight + 0.7f)
        graphClose.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                graphWindow.remove()
            }
        })
    }

    private fun addSaveLoad(menu: VisTable) {
        // TODO figure out proper save/load overwrite strategy
        // TODO some error handling maybe
        FileChooser.setDefaultPrefsName("com.silentgames.bte")
        // NOTE disabled at start, we need an initialized model to save/load stuff
        saveBtn.isDisabled = true
        saveAsBtn.isDisabled = true
        loadBtn.isDisabled = true
        menu.add(saveBtn)
        menu.add(saveAsBtn)
        menu.add(loadBtn)
        saveChooser.selectionMode = FileChooser.SelectionMode.FILES
        saveChooser.isMultiSelectionEnabled = false
        // TODO filter maybe
        saveChooser.setListener(object : FileChooserAdapter() {
            override fun selected(file: Array<FileHandle>) {
                // we dont allow multiple files
                lastSave = file.first()
                saveTree()
                Gdx.app.log(TAG, "Saved tree to " + lastSave?.path())
            }
        })
        saveBtn.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                if (saveAsBtn.isDisabled) return
                if (lastSave == null) {
                    stage.addActor(saveChooser.fadeIn())
                } else {
                    saveTree()
                    Gdx.app.log(TAG, "Saved tree to " + lastSave?.path())
                }
            }
        })
        saveAsBtn.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                if (saveAsBtn.isDisabled) return
                stage.addActor(saveChooser.fadeIn())
            }
        })
        loadChooser.selectionMode = FileChooser.SelectionMode.FILES
        loadChooser.isMultiSelectionEnabled = false
        // TODO filter maybe
        loadChooser.setListener(object : FileChooserAdapter() {
            override fun selected(file: Array<FileHandle>) {
                // null? new one?
                lastSave = null
                // we dont allow multiple files
                val fh = file.first()
                model.loadTree(fh)
                Gdx.app.log(TAG, "Loaded tree from " + fh.path())
            }
        })
        loadBtn.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                if (loadBtn.isDisabled) return
                stage.addActor(loadChooser.fadeIn())
            }
        })
    }

    private fun saveTree() {
        val e = lastSave?.let { model.saveTree(it) }
        if (e != null) {
            showMessageDialog("Save operation Failed", e.message)
        }
    }

    private fun showMessageDialog(title: String, text: String?) {
        val dialog = VisDialog(title)
        dialog.addCloseButton()
        dialog.text(text)
        dialog.pack()
        dialog.show(stage)
    }

    private fun onSelectionChanged(oldNode: Tree.Node<*, *, *>?, newNode: Tree.Node<*, *, *>) {
// 		Gdx.app.log(TAG, "selection changed from " + oldNode + " to " + newNode);
        // add stuff to taskEdit
        if (newNode is ViewTask) {
            val task = newNode.task
            if (task?.wrapped != null) {
                vtEdit?.startEdit(task)
            } else {
                Gdx.app.error(TAG, "Error for $task")
            }
        }
    }

    private fun onOverNodeChanged(oldNode: Tree.Node<*, *, *>?, newNode: Tree.Node<*, *, *>?) {
    }

    override fun onInit(model: BehaviorTreeModel) {
        this.model = model
        rebuildTree()
        saveBtn.isDisabled = false
        saveAsBtn.isDisabled = false
        loadBtn.isDisabled = false
    }

    private fun clearTree() {
        for (node in tree.rootNodes) {
            free((node as ViewTask))
        }
        tree.clearChildren()
    }

    private fun rebuildTree() {
        graph.init(model)
        clearTree()
        if (model.isInitialized) {
            model.getRoot()?.let { fillTree(null, it) }
            tree.expandAll()
        }
    }

    private fun fillTree(
        parent: Tree.Node<Tree.Node<*, *, *>, Tree.Node<*, *, *>, Actor>?,
        task: TaskModel
    ) {
        val node = obtain(task, this)
        if (parent == null) {
            tree.add(node)
        } else {
            parent.add(node)
        }
        for (i in 0 until task.childCount) {
            fillTree(node, task.getChild(i))
        }
    }

    private val taggedTasks = Array<TaggedTask>()
    private val tagToNode = ObjectMap<String, TaggedRoot?>()

    fun addSrcTask(tag: String, cls: Class<out Task<*>?>, visible: Boolean) {
        val taggedTask = obtain(tag, cls, this, visible)
        taggedTasks.add(taggedTask)
        taggedTasks.sort()

        // TODO ability to toggle visibility of each node, so it is easier to reduce clutter by hiding rarely used tasks
        for (task in taggedTasks) {
            var categoryNode = tagToNode[task.tag, null]
            if (categoryNode == null) {
                // TODO do we want a custom class for those?
                categoryNode = TaggedRoot.obtain(task.tag, this)
                tagToNode.put(tag, categoryNode)
                taskDrawer.add(categoryNode)
            }
            if (!categoryNode.has(task)) {
                categoryNode.add(task)
            }
        }
        taskDrawer.expandAll()
    }

    override fun onChange(model: BehaviorTreeModel) {
        rebuildTree()
        if (model.isValid()) {
            btToggle.isDisabled = false
            btStep.isDisabled = false
            btReset.isDisabled = false
        } else {
            btToggle.isDisabled = true
            btStep.isDisabled = true
            btReset.isDisabled = true
        }
    }

    override fun onLoad(tree: BehaviorTree<*>, file: FileHandle, model: BehaviorTreeModel) {}

    override fun onLoadError(ex: Exception, file: FileHandle, model: BehaviorTreeModel) {
        Gdx.app.error(TAG, "Tree load failed", ex)
        val stage = stage
        if (stage != null) {
            val dialog = VisDialog("Load failed!")
            dialog.text("Loading " + file.path() + " failed with exception:")
            dialog.contentTable.row()
            val area = VisTextArea(ex.message)
            area.setPrefRows(5f)
            dialog.contentTable.add(area).expand().fill()
            dialog.button("Ok")
            dialog.show(stage)
        }
    }

    override fun onSave(tree: BehaviorTree<*>, file: FileHandle, model: BehaviorTreeModel) {}

    override fun onStepError(ex: Exception, model: BehaviorTreeModel) {
        Gdx.app.error(TAG, "Tree step failed", ex)
        btToggle.isChecked = false
        val stage = stage
        if (stage != null) {
            val dialog = VisDialog("Tree step failed!")
            dialog.text("Tree step failed with exception:")
            dialog.contentTable.row()
            val area = VisTextArea(ex.message)
            area.setPrefRows(5f)
            dialog.contentTable.add(area).expand().fill()
            dialog.button("Ok")
            dialog.show(stage)
        }
    }

    override fun onReset(model: BehaviorTreeModel) {
        clearTree()
        saveBtn.isDisabled = true
        saveAsBtn.isDisabled = true
        loadBtn.isDisabled = true
    }

    fun onShow() {
        model.addChangeListener(this)
        // force update
        onChange(model)
        saveBtn.isDisabled = !model.isInitialized
        saveAsBtn.isDisabled = !model.isInitialized
        loadBtn.isDisabled = !model.isInitialized
    }

    fun onHide() {
        model.removeChangeListener(this)
    }

    fun setSaveLoadDirectory(directory: FileHandle?) {
        saveChooser.setDirectory(directory)
        loadChooser.setDirectory(directory)
    }

    private class FocusOnEnterListener : InputListener() {
        override fun exit(event: InputEvent, x: Float, y: Float, pointer: Int, toActor: Actor?) {
            val stage = event.target.stage
            if (stage != null) {
                stage.scrollFocus = null
            }
        }

        override fun enter(event: InputEvent, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
            val stage = event.target.stage
            if (stage != null) {
                stage.scrollFocus = event.target
            }
        }
    }
}
