package org.eclipse.pde.internal.editor.feature;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.jface.action.*;
import org.eclipse.jface.resource.*;
import org.eclipse.pde.internal.*;
import org.eclipse.pde.internal.base.model.feature.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.internal.base.model.plugin.*;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.*;
import org.eclipse.core.resources.*;
import org.eclipse.swt.events.*;
import org.eclipse.pde.internal.base.model.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.update.ui.forms.internal.*;
import org.eclipse.pde.internal.editor.*;
import org.eclipse.swt.*;
import org.eclipse.ui.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.pde.internal.elements.*;
import org.eclipse.pde.internal.util.*;
import org.eclipse.pde.internal.wizards.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.pde.internal.editor.PropertiesAction;
import org.eclipse.pde.internal.model.feature.FeatureImport;
import org.eclipse.swt.custom.BusyIndicator;

public class RequiresSection
	extends PDEFormSection
	implements IModelProviderListener {
	private static final String KEY_TITLE = "FeatureEditor.RequiresSection.title";
	private static final String KEY_DESC = "FeatureEditor.RequiresSection.desc";
	private static final String KEY_SYNC_BUTTON =
		"FeatureEditor.RequiresSection.syncButton";
	private static final String KEY_COMPUTE =
		"FeatureEditor.RequiresSection.compute";
	private boolean updateNeeded;
	private Button syncButton;
	private TableViewer pluginViewer;
	private Image pluginImage;
	private Image warningPluginImage;

	class ImportContentProvider
		extends DefaultContentProvider
		implements IStructuredContentProvider {
		public Object[] getElements(Object parent) {
			if (parent instanceof IFeature)
				return ((IFeature) parent).getImports();
			return new Object[0];
		}
	}

	class ImportLabelProvider
		extends LabelProvider
		implements ITableLabelProvider {
		public String getColumnText(Object obj, int index) {
			return getReferenceText(obj);
		}
		public Image getColumnImage(Object obj, int index) {
			return getReferenceImage(obj);
		}
	}

	public RequiresSection(FeatureReferencePage page) {
		super(page);
		setHeaderText(PDEPlugin.getResourceString(KEY_TITLE));
		setDescription(PDEPlugin.getResourceString(KEY_DESC));
		pluginImage = PDEPluginImages.DESC_REQ_PLUGIN_OBJ.createImage();
		warningPluginImage =
			createWarningImage(
				PDEPluginImages.DESC_PLUGIN_OBJ,
				PDEPluginImages.DESC_ERROR_CO);
	}

	public void commitChanges(boolean onSave) {
	}

	public Composite createClient(Composite parent, FormWidgetFactory factory) {
		Composite container = factory.createComposite(parent);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		container.setLayout(layout);

		syncButton =
			factory.createButton(
				container,
				PDEPlugin.getResourceString(KEY_SYNC_BUTTON),
				SWT.CHECK);
		syncButton.setSelection(true);
		GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalSpan = 2;
		syncButton.setLayoutData(gd);

		pluginViewer =
			new TableViewer(container, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		pluginViewer.setContentProvider(new ImportContentProvider());
		pluginViewer.setLabelProvider(new ImportLabelProvider());
		pluginViewer.setSorter(ListUtil.NAME_SORTER);

		pluginViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent e) {
				handleSelectionChanged(e);
			}
		});
		MenuManager popupMenuManager = new MenuManager();
		IMenuListener listener = new IMenuListener() {
			public void menuAboutToShow(IMenuManager mng) {
				fillContextMenu(mng);
			}
		};
		pluginViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				//openAction.run();
			}
		});
		Table table = pluginViewer.getTable();
		popupMenuManager.setRemoveAllWhenShown(true);
		popupMenuManager.addMenuListener(listener);
		Menu menu = popupMenuManager.createContextMenu(table);
		table.setMenu(menu);
		gd = new GridData(GridData.FILL_BOTH);
		table.setLayoutData(gd);

		Composite buttonContainer = factory.createComposite(container);
		layout = new GridLayout();
		buttonContainer.setLayout(layout);
		layout.marginWidth = layout.marginHeight = 0;
		gd = new GridData(GridData.FILL_VERTICAL);
		buttonContainer.setLayoutData(gd);

		Button computeButton =
			factory.createButton(
				buttonContainer,
				PDEPlugin.getResourceString(KEY_COMPUTE),
				SWT.PUSH);
		computeButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				BusyIndicator.showWhile(syncButton.getDisplay(), new Runnable() {
					public void run() {
						recomputeImports();
					}
				});
			}
		});
		gd = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
		computeButton.setLayoutData(gd);

		factory.paintBordersFor(container);
		//openAction = new OpenReferenceAction(pluginViewer);
		return container;
	}

	private Image createWarningImage(
		ImageDescriptor baseDescriptor,
		ImageDescriptor overlayDescriptor) {
		ImageDescriptor desc =
			new OverlayIcon(baseDescriptor, new ImageDescriptor[][] { {
			}, {
			}, {
				overlayDescriptor }
		});
		return desc.createImage();
	}
	public void dispose() {
		IFeatureModel model = (IFeatureModel) getFormPage().getModel();
		model.removeModelChangedListener(this);
		WorkspaceModelManager mng = PDEPlugin.getDefault().getWorkspaceModelManager();
		mng.removeModelProviderListener(this);
		if (warningPluginImage != null)
			warningPluginImage.dispose();
		pluginImage.dispose();
		super.dispose();
	}
	public void expandTo(Object object) {
		if (object instanceof IFeatureImport) {
			StructuredSelection ssel = new StructuredSelection(object);
			pluginViewer.setSelection(ssel);
		}
	}
	private void fillContextMenu(IMenuManager manager) {
		/*
		manager.add(openAction);
		manager.add(propertiesAction);
		manager.add(new Separator());
		*/
		getFormPage().getEditor().getContributor().contextMenuAboutToShow(manager);
	}

	private String getReferenceText(Object obj) {
		FeatureImport iimport = (FeatureImport) obj;
		IPlugin plugin = iimport.getPlugin();
		if (plugin != null) {
			return plugin.getTranslatedName();
		}
		return iimport.getId();
	}

	private Image getReferenceImage(Object obj) {
		if (!(obj instanceof FeatureImport))
			return null;
		FeatureImport iimport = (FeatureImport) obj;
		IPlugin plugin = iimport.getPlugin();
		if (plugin != null)
			return pluginImage;
		else
			return warningPluginImage;
	}
	private void handleSelectionChanged(SelectionChangedEvent e) {
		IFeatureImport iimport =
			(IFeatureImport) ((IStructuredSelection) e.getSelection()).getFirstElement();

		StructuredSelection selection = null;

		if (iimport != null) {
			selection = new StructuredSelection(iimport);
		} else
			selection = new StructuredSelection();
		getFormPage().setSelection(selection);
		fireSelectionNotification(iimport);
	}
	public void initialize(Object input) {
		IFeatureModel model = (IFeatureModel) input;
		update(input);
		if (model.isEditable() == false) {
			pluginViewer.getTable().setEnabled(false);
			syncButton.setEnabled(false);
		}
		model.addModelChangedListener(this);
		WorkspaceModelManager mng = PDEPlugin.getDefault().getWorkspaceModelManager();
		mng.addModelProviderListener(this);
	}
	private void initializeOverlays() {

	}

	public void modelChanged(IModelChangedEvent e) {
		if (e.getChangeType() == IModelChangedEvent.WORLD_CHANGED) {
			updateNeeded = true;
			if (getFormPage().isVisible()) {
				update();
			}
		} else if (e.getChangeType() == IModelChangedEvent.CHANGE) {
			Object obj = e.getChangedObjects()[0];
			if (obj instanceof IFeatureImport) {
				pluginViewer.refresh(obj);
			}
		} else {
			Object obj = e.getChangedObjects()[0];
			if (obj instanceof IFeatureImport) {
				IFeatureImport iimport = (IFeatureImport) obj;
				if (e.getChangeType() == IModelChangedEvent.INSERT)
					pluginViewer.add(e.getChangedObjects());
				else
					pluginViewer.remove(e.getChangedObjects());
			} else if (obj instanceof IFeaturePlugin) {
				if (syncButton.getSelection()) {
					recomputeImports();
				}
			}
		}
	}

	private void recomputeImports() {
		IFeatureModel model = (IFeatureModel) getFormPage().getModel();
		IFeature feature = model.getFeature();
		try {
			feature.computeImports();
		} catch (CoreException e) {
			PDEPlugin.logException(e);
		}
	}

	public void modelsChanged(IModelProviderEvent event) {
		updateNeeded = true;
		update();
	}

	public void setFocus() {
		if (pluginViewer != null)
			pluginViewer.getTable().setFocus();
	}

	public void update() {
		if (updateNeeded) {
			this.update(getFormPage().getModel());
		}
	}

	public void update(Object input) {
		IFeatureModel model = (IFeatureModel) input;
		IFeature feature = model.getFeature();
		pluginViewer.setInput(feature);
		updateNeeded = false;
	}
}