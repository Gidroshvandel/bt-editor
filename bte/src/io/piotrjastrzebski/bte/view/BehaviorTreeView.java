package io.piotrjastrzebski.bte.view;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Tree;
import com.badlogic.gdx.scenes.scene2d.utils.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.file.FileChooser;
import com.kotcrab.vis.ui.widget.file.FileChooserAdapter;
import io.piotrjastrzebski.bte.AIEditor;
import io.piotrjastrzebski.bte.model.BehaviorTreeModel;
import io.piotrjastrzebski.bte.model.tasks.TaskModel;
import io.piotrjastrzebski.bte.view.edit.ViewTaskAttributeEdit;

/**
 * Created by EvilEntity on 04/02/2016.
 */
@SuppressWarnings("rawtypes")
public class BehaviorTreeView extends Table implements BehaviorTreeModel.ModelChangeListener {
	public static String DRAWABLE_WHITE = "dialogDim";
	private static final String TAG = BehaviorTreeView.class.getSimpleName();
	protected BehaviorTreeModel model;
	protected VisTable topMenu;
	protected VisScrollPane drawerScrollPane;
	protected VisScrollPane treeScrollPane;
	protected VisTree taskDrawer;
	protected VisTree tree;
	protected VisTable taskEdit;
	protected DragAndDrop dad;
	protected ViewTarget removeTarget;
	protected SpriteDrawable dimImg;
	protected final VisTextButton btToggle;
	protected final VisTextButton btStep;
	protected final VisTextButton btReset;
	private Tree.Node selectedNode;
	private final ViewTaskAttributeEdit vtEdit;
	private VisTextButton saveBtn;
	private VisTextButton saveAsBtn;
	private VisTextButton loadBtn;
	private FileChooser saveChooser;
	private FileChooser loadChooser;

	public BehaviorTreeView (final AIEditor editor) {
		this.model = editor.getModel();
		dimImg = new SpriteDrawable((SpriteDrawable)VisUI.getSkin().getDrawable(DRAWABLE_WHITE));
		dimImg.getSprite().setColor(Color.WHITE);
		// create label style with background used by ViewPayloads
		VisTextButton.ButtonStyle btnStyle = VisUI.getSkin().get(VisTextButton.ButtonStyle.class);
		VisLabel.LabelStyle labelStyle = new Label.LabelStyle(VisUI.getSkin().get(VisLabel.LabelStyle.class));
		labelStyle.background = btnStyle.up;
		VisUI.getSkin().add("label-background", labelStyle);

		dad = new DragAndDrop();
		topMenu = new VisTable(true);
		add(topMenu).colspan(3);

		VisTextButton undoBtn = new VisTextButton("Undo");
		undoBtn.addListener(new ClickListener(){
			@Override public void clicked (InputEvent event, float x, float y) {
				model.undo();
			}
		});
		VisTextButton redoBtn = new VisTextButton("Redo");
		redoBtn.addListener(new ClickListener(){
			@Override public void clicked (InputEvent event, float x, float y) {
				model.redo();
			}
		});
		topMenu.add(undoBtn);
		topMenu.add(redoBtn).padRight(20);

		addSaveLoad(topMenu);

		VisTable btControls = new VisTable(true);
		topMenu.add(btControls).padLeft(20);
		btToggle = new VisTextButton("AutoStep", "toggle");
		btToggle.setChecked(true);
		btControls.add(btToggle);
		btToggle.addListener(new ChangeListener() {
			@Override public void changed (ChangeEvent event, Actor actor) {
				if (btToggle.isDisabled()) return;
				editor.setAutoStepBehaviorTree(btToggle.isChecked());
			}
		});
		btStep = new VisTextButton("Step");
		btStep.addListener(new ClickListener(){
			@Override public void clicked (InputEvent event, float x, float y) {
				if (btStep.isDisabled()) return;
				editor.forceStepBehaviorTree();
			}
		});
		btControls.add(btStep);
		btReset = new VisTextButton("Restart");
		btReset.addListener(new ClickListener(){
			@Override public void clicked (InputEvent event, float x, float y) {
				if (btReset.isDisabled()) return;
				editor.restartBehaviorTree();
			}
		});
		btControls.add(btReset);

		row();
		taskDrawer = new VisTree();
		taskDrawer.setYSpacing(-2);
		taskDrawer.setFillParent(true);
		VisTable treeView = new VisTable(true);
		tree = new VisTree() {
			@Override public void setOverNode (Node overNode) {
				Node old = tree.getOverNode();
				if (old != overNode) {
					onOverNodeChanged(old, overNode);
				}
				super.setOverNode(overNode);
			}
		};
		tree.getSelection().setMultiple(false);
		tree.addListener(new ChangeListener() {
			@Override public void changed (ChangeEvent event, Actor actor) {
				Tree.Node newNode = (Tree.Node) tree.getSelection().getLastSelected();
				onSelectionChanged(selectedNode, newNode);
				selectedNode = newNode;
			}
		});
		tree.setYSpacing(0);
		// add dim to tree so its in same coordinates as nodes
		treeView.add(tree).fill().expand();
		treeScrollPane = new VisScrollPane(treeView);
		treeScrollPane.addListener(new FocusOnEnterListener());

		VisTable taskView =  new VisTable(true);
		taskView.add(taskDrawer).fill().expand();
		drawerScrollPane = new VisScrollPane(taskView);
		drawerScrollPane.addListener(new FocusOnEnterListener());

		taskEdit = new VisTable(true);
		taskEdit.add(vtEdit = new ViewTaskAttributeEdit()).expand().top();
		VisSplitPane drawerTreeSP = new VisSplitPane(drawerScrollPane, treeScrollPane, false);
		drawerTreeSP.setSplitAmount(.33f);
		VisSplitPane dtEditSP = new VisSplitPane(drawerTreeSP, taskEdit, false);
		dtEditSP.setSplitAmount(.75f);
		add(dtEditSP).grow().pad(5);

		removeTarget = new ViewTarget(drawerScrollPane) {
			@Override public boolean onDrag (ViewSource source, ViewPayload payload, float x, float y) {
				return payload.getType() == ViewPayload.Type.MOVE;
			}

			@Override public void onDrop (ViewSource source, ViewPayload payload, float x, float y) {
				model.remove(payload.task);
			}
		};
		dad.addTarget(removeTarget);
	}

	private FileHandle lastSave = null;
	private void addSaveLoad (VisTable menu) {
		// TODO figure out proper save/load overwrite strategy
		// TODO some error handling maybe
		FileChooser.setFavoritesPrefsName("io.piotrjastrzebski.bte");
		saveBtn = new VisTextButton("Save");
		saveAsBtn = new VisTextButton("Save As");
		loadBtn = new VisTextButton("Load");
		// NOTE disabled at start, we need an initialized model to save/load stuff
		saveBtn.setDisabled(true);
		saveAsBtn.setDisabled(true);
		loadBtn.setDisabled(true);
		menu.add(saveBtn);
		menu.add(saveAsBtn);
		menu.add(loadBtn);
		saveChooser = new FileChooser(FileChooser.Mode.SAVE);
		saveChooser.setSelectionMode(FileChooser.SelectionMode.FILES);
		saveChooser.setMultiSelectionEnabled(false);
		// TODO filter maybe
		saveChooser.setListener(new FileChooserAdapter() {
			@Override
			public void selected (Array<FileHandle> file) {
				// we dont allow multiple files
				lastSave = file.first();
				model.saveTree(lastSave);
				Gdx.app.log(TAG, "Saved tree to " + lastSave.path());
			}
		});

		saveBtn.addListener(new ClickListener(){
			@Override public void clicked (InputEvent event, float x, float y) {
				if (saveAsBtn.isDisabled()) return;
				if (lastSave == null) {
					getStage().addActor(saveChooser.fadeIn());
				} else {
					model.saveTree(lastSave);
					Gdx.app.log(TAG, "Saved tree to " + lastSave.path());
				}
			}
		});
		saveAsBtn.addListener(new ClickListener(){
			@Override public void clicked (InputEvent event, float x, float y) {
				if (saveAsBtn.isDisabled()) return;
				getStage().addActor(saveChooser.fadeIn());
			}
		});

		loadChooser = new FileChooser(FileChooser.Mode.OPEN);
		loadChooser.setSelectionMode(FileChooser.SelectionMode.FILES);
		loadChooser.setMultiSelectionEnabled(false);
		// TODO filter maybe
		loadChooser.setListener(new FileChooserAdapter() {
			@Override
			public void selected (Array<FileHandle> file) {
				// null? new one?
				lastSave = null;
				// we dont allow multiple files
				FileHandle fh = file.first();
				model.loadTree(fh);
				Gdx.app.log(TAG, "Loaded tree from " + fh.path());
			}
		});
		loadBtn.addListener(new ClickListener(){
			@Override public void clicked (InputEvent event, float x, float y) {
				if (loadBtn.isDisabled()) return;
				getStage().addActor(loadChooser.fadeIn());
			}
		});
	}

	private void onSelectionChanged (Tree.Node oldNode, Tree.Node newNode) {
//		Gdx.app.log(TAG, "selection changed from " + oldNode + " to " + newNode);
		// add stuff to taskEdit
		if (newNode instanceof ViewTask) {
			TaskModel task = ((ViewTask)newNode).task;
			if (task != null && task.getWrapped() != null) {
				vtEdit.startEdit(task);
			} else {
				Gdx.app.error(TAG, "Error for " + task);
			}
		}
	}

	private void onOverNodeChanged (Tree.Node oldNode, Tree.Node newNode) {

	}

	@Override public void onInit (BehaviorTreeModel model) {
		this.model = model;

		rebuildTree();
		saveBtn.setDisabled(false);
		saveAsBtn.setDisabled(false);
		loadBtn.setDisabled(false);
	}

	private void clearTree () {
		for (Object node : tree.getNodes()) {
			ViewTask.free((ViewTask)node);
		}
		tree.clearChildren();
	}

	private void rebuildTree () {
		clearTree();
		if (model.isInitialized()) {
			fillTree(null, model.getRoot());
			tree.expandAll();
		}
	}

	private void fillTree (Tree.Node parent, TaskModel task) {
		Tree.Node node = ViewTask.obtain(task, this);
		if (parent == null) {
			tree.add(node);
		} else {
			parent.add(node);
		}
		for (int i = 0; i < task.getChildCount(); i++) {
			fillTree(node, task.getChild(i));
		}
	}

	private Array<TaggedTask> taggedTasks = new Array<>();
	private ObjectMap<String, TaggedRoot> tagToNode = new ObjectMap<>();
	public void addSrcTask (String tag, Class<? extends Task> cls, boolean visible) {
		TaggedTask taggedTask = TaggedTask.obtain(tag, cls, this, visible);
		taggedTasks.add(taggedTask);
		taggedTasks.sort();

		// TODO ability to toggle visibility of each node, so it is easier to reduce clutter by hiding rarely used tasks
		for (TaggedTask task : taggedTasks) {
			TaggedRoot categoryNode = tagToNode.get(task.tag, null);
			if (categoryNode == null) {
				// TODO do we want a custom class for those?
				categoryNode = TaggedRoot.obtain(task.tag, this);
				tagToNode.put(tag, categoryNode);
				taskDrawer.add(categoryNode);
			}
			if (!categoryNode.has(task)){
				categoryNode.add(task);
			}
		}
		taskDrawer.expandAll();
	}

	@Override public void onChange (BehaviorTreeModel model) {
		rebuildTree();
		if (model.isValid()) {
			btToggle.setDisabled(false);
			btStep.setDisabled(false);
			btReset.setDisabled(false);
		} else {
			btToggle.setDisabled(true);
			btStep.setDisabled(true);
			btReset.setDisabled(true);
		}
	}

	@Override public void onLoad (BehaviorTree tree, FileHandle file, BehaviorTreeModel model) {

	}

	@Override public void onLoadError (Exception ex, FileHandle file, BehaviorTreeModel model) {
		Gdx.app.error(TAG, "Tree load failed", ex);
		Stage stage = getStage();
		if (stage != null) {
			VisDialog dialog = new VisDialog("Load failed!");
			dialog.text("Loading " + file.path()+" failed with exception:");
			dialog.getContentTable().row();
			VisTextArea area = new VisTextArea(ex.getMessage());
			area.setPrefRows(5);
			dialog.getContentTable().add(area).expand().fill();
			dialog.button("Ok");
			dialog.show(stage);
		}
	}

	@Override public void onSave (BehaviorTree tree, FileHandle file, BehaviorTreeModel model) {

	}

	@Override public void onStepError (Exception ex, BehaviorTreeModel model) {
		Gdx.app.error(TAG, "Tree step failed", ex);
		btToggle.setChecked(false);
		Stage stage = getStage();
		if (stage != null) {
			VisDialog dialog = new VisDialog("Tree step failed!");
			dialog.text("Tree step failed with exception:");
			dialog.getContentTable().row();
			VisTextArea area = new VisTextArea(ex.getMessage());;
			area.setPrefRows(5);
			dialog.getContentTable().add(area).expand().fill();
			dialog.button("Ok");
			dialog.show(stage);
		}
	}

	@Override public void onReset (BehaviorTreeModel model) {
		clearTree();
		saveBtn.setDisabled(true);
		saveAsBtn.setDisabled(true);
		loadBtn.setDisabled(true);
	}

	public void onShow () {
		model.addChangeListener(this);
		// force update
		onChange(model);
		saveBtn.setDisabled(!model.isInitialized());
		saveAsBtn.setDisabled(!model.isInitialized());
		loadBtn.setDisabled(!model.isInitialized());
	}

	public void onHide () {
		model.removeChangeListener(this);
	}

	public void setSaveLoadDirectory (FileHandle directory) {
		saveChooser.setDirectory(directory);
		loadChooser.setDirectory(directory);
	}

	private static class FocusOnEnterListener extends InputListener {
		@Override public void exit (InputEvent event, float x, float y, int pointer, Actor toActor) {
			Stage stage = event.getTarget().getStage();
			if (stage != null) {
				stage.setScrollFocus(null);
			}
		}

		@Override public void enter (InputEvent event, float x, float y, int pointer, Actor fromActor) {
			Stage stage = event.getTarget().getStage();
			if (stage != null) {
				stage.setScrollFocus(event.getTarget());
			}
		}
	}

}
