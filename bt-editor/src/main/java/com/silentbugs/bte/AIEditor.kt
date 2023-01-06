package com.silentbugs.bte

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ai.btree.BehaviorTree
import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.ai.btree.branch.*
import com.badlogic.gdx.ai.btree.decorator.*
import com.badlogic.gdx.ai.btree.leaf.Failure
import com.badlogic.gdx.ai.btree.leaf.Success
import com.badlogic.gdx.ai.btree.leaf.Wait
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Window
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.GdxRuntimeException
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisWindow
import com.silentbugs.bte.model.BehaviorTreeModel
import com.silentbugs.bte.model.tasks.Guard
import com.silentbugs.bte.model.tasks.TaskModel
import com.silentbugs.bte.view.BehaviorTreeView

/**
 * Main editor class
 *
 *
 * NOTE
 * we must support undo/redo
 * to do that, we will clone all tasks all the time, at least at the beginning
 * when we add something, we store a clone of task with its children in the add node and add another clone to the tree
 * same for remove/move actions
 * we also could store a some sort of history of data changes in each task
 *
 *
 * TODO add Subtree fake task, ala guard so we can split stuff
 *
 *
 * Created by EvilEntity on 04/02/2016.
 */
class AIEditor @JvmOverloads constructor(skin: Skin? = null) : Disposable {
    private var ownsSkin = false

    /* Current tree we are editing */
    private var tree: BehaviorTree<out Any>? = null
    val model: BehaviorTreeModel
    val view: BehaviorTreeView
    private var stepStrategy: BehaviorTreeStepStrategy? = null
    private var simpleStepStrategy: BehaviorTreeStepStrategy? = null
    private var window: VisWindow? = null
    private var autoStep = true

    companion object {
        private val TAG = AIEditor::class.java.simpleName
    }
    /**
     * Create AIEditor with external VisUI skin
     *
     * @param skin Skin to use
     */
    /**
     * Create AIEditor with internal VisUI skin
     * AIEditor must be disposed in this case
     */
    init {
        var localSkin = skin
        if (localSkin == null) {
            ownsSkin = true
            localSkin = Skin(VisUI.SkinScale.X1.skinFile)
            try {
                VisUI.load(localSkin)
            } catch (e: GdxRuntimeException) {
                Gdx.app.error(TAG, "VisUI already loaded?", e)
                localSkin.dispose()
            }
        }
        model = BehaviorTreeModel()
        view = BehaviorTreeView(this)
        setUpdateStrategy(null)
    }

    /**
     * Initialize the editor with new tree
     *
     * @param tree tree to use
     * @param copy if we should work on a copy
     */
    fun initialize(tree: BehaviorTree<*>, copy: Boolean = false) {
        reset()
        val behaviorTree = if (copy) {
            tree.cloneTask() as BehaviorTree<*>
        } else {
            tree
        }
        this.tree = behaviorTree
        model.init(behaviorTree)
    }

    fun reset() {
        if (tree == null) return
        // TODO prompt for save or whatever
        model.reset()
        tree = null
    }

    /**
     * Update the editor, call this each frame
     */
    fun update(delta: Float) {
        if (model.isValid() && stepStrategy?.shouldStep(tree, delta) == true && autoStep) {
            // TODO figure out a way to break stepping if there is an infinite loop in the tree
            // TODO or more practically, if we run some excessive amount of tasks
            model.step()
        }
    }

    /**
     * Step the behavior tree immediately if model is valid
     */
    fun forceStepBehaviorTree() {
        if (model.isValid()) {
            tree?.step()
        }
    }

    /**
     * BTUpdateStrategy for the BehaviorTree that will be called if it is in valid state
     * Pass in null to use default stepStrategy, step() each update
     *
     * @param strategy stepStrategy to use for BehaviorTree updates
     */
    fun setUpdateStrategy(strategy: BehaviorTreeStepStrategy?) {
        if (strategy == null) {
            if (simpleStepStrategy == null) {
                simpleStepStrategy = object : BehaviorTreeStepStrategy {
                    override fun shouldStep(tree: BehaviorTree<*>?, delta: Float): Boolean {
                        return true
                    }
                }
            }
            stepStrategy = simpleStepStrategy
        } else {
            stepStrategy = strategy
        }
    }

    @JvmOverloads
    fun prepareWindow(closeable: Boolean = true) {
        if (window == null || isWindowCloseable != closeable) {
            if (window != null) {
                window?.clear()
                window?.remove()
            }
            window = object : VisWindow("AIEditor") {
                override fun setParent(parent: Group?) {
                    super.setParent(parent)
                    if (parent != null) {
                        view.onShow()
                    } else {
                        view.onHide()
                    }
                }
            }
            window?.isResizable = true
            window?.add(view)?.fill()?.expand()
            isWindowCloseable = closeable
            if (closeable) {
                window?.addCloseButton()
                window?.closeOnEscape()
            }
        }
        window?.clearActions()
        window?.centerWindow()
    }

    fun getWindow(): Window? {
        if (window == null) {
            prepareWindow()
        }
        return window
    }

    private var isWindowCloseable = false
    private var fadingOut = false
    val isWindowVisible: Boolean get() = window != null && window?.parent != null && !fadingOut

    fun showWindow(group: Group) {
        val needResize = window == null
        group.addActor(getWindow())
        if (needResize) {
            window?.let { window ->
                window.setSize(
                    Math.min(window.prefWidth, window.stage.width),
                    Math.min(window.prefHeight, window.stage.height)
                )
                window.validate()
            }
        }
        window?.clearActions()
        window?.fadeIn()
        fadingOut = false
    }

    fun hideWindow() {
        window?.clearActions()
        window?.fadeOut()
        fadingOut = true
    }

    fun toggleWindow(group: Group) {
        if (isWindowVisible) {
            hideWindow()
        } else {
            showWindow(group)
        }
    }

    fun addTaskClass(tag: String, cls: Class<out Task<*>>, visible: Boolean = true) {
        view.addSrcTask(tag, cls, visible)
    }

    fun addDefaultTaskClasses() {
        addTaskClass("branch", Sequence::class.java)
        addTaskClass("branch", Selector::class.java)
        addTaskClass("branch", Parallel::class.java)
        addTaskClass("branch", DynamicGuardSelector::class.java)
        addTaskClass("branch", RandomSelector::class.java)
        addTaskClass("branch", RandomSequence::class.java)
        addTaskClass("branch", Guard::class.java)
        addTaskClass("decorator", AlwaysFail::class.java)
        addTaskClass("decorator", AlwaysSucceed::class.java)
        addTaskClass("decorator", Include::class.java)
        addTaskClass("decorator", Invert::class.java)
        addTaskClass("decorator", Random::class.java)
        addTaskClass("decorator", Repeat::class.java)
        addTaskClass("decorator", SemaphoreGuard::class.java)
        addTaskClass("decorator", UntilFail::class.java)
        addTaskClass("decorator", UntilSuccess::class.java)
        addTaskClass("decorator", Wait::class.java)
        addTaskClass("leaf", Success::class.java)
        addTaskClass("leaf", Failure::class.java)
    }

    fun setAutoStepBehaviorTree(autoStep: Boolean) {
        this.autoStep = autoStep
    }

    fun restartBehaviorTree() {
        tree?.resetTask()
    }

    interface BehaviorTreeStepStrategy {
        fun shouldStep(tree: BehaviorTree<*>?, delta: Float): Boolean
    }

    fun setTaskInjector(injector: TaskInjector?) {
        TaskModel.injector = injector
    }

    /**
     * Set directory used for backups, backups are saved when tree is modified and is valid
     *
     * @param fh file handle to an existing directory where backups will be saved
     */
    fun setBackupDirectory(fh: FileHandle?) {
        model.setBackupDirectory(fh)
    }

    /**
     * Set initial directory that will be used by save/load dialogs
     * The default is user home directory
     *
     * @param fh file handle to an existing directory
     */
    fun setSaveLoadDirectory(fh: FileHandle?) {
        view.setSaveLoadDirectory(fh)
    }

    override fun dispose() {
        if (ownsSkin) {
            VisUI.dispose()
        }
    }
}
