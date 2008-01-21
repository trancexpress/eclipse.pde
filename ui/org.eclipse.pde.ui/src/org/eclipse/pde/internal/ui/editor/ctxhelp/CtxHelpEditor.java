/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.editor.ctxhelp;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.viewers.*;
import org.eclipse.pde.internal.core.text.ctxhelp.*;
import org.eclipse.pde.internal.ui.IPDEUIConstants;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.editor.*;
import org.eclipse.pde.internal.ui.editor.context.InputContext;
import org.eclipse.pde.internal.ui.editor.context.InputContextManager;
import org.eclipse.ui.*;
import org.eclipse.ui.forms.editor.IFormPage;
import org.eclipse.ui.part.*;

/**
 * Main UI class for the context help editor.  This editor provides a convenient way to
 * explore and edit the xml files containing context help entries.  The editor will
 * have two pages, one displaying the xml source, the other displaying a form editor.
 * @since 3.4
 * @see CtxHelpSourcePage
 * @see CtxHelpPage
 */
public class CtxHelpEditor extends MultiSourceEditor {

	public CtxHelpEditor() {
		super();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEFormEditor#getEditorID()
	 */
	protected String getEditorID() {
		return IPDEUIConstants.CONTEXT_HELP_EDITOR_ID;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEFormEditor#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class adapter) {
		if (isShowInApplicable()) {
			if (adapter == IShowInSource.class) {
				return getShowInSource();
			} else if (adapter == IShowInTargetList.class) {
				return getShowInTargetList();
			}
		}
		return super.getAdapter(adapter);
	}

	/**
	 * @return whether there is a selection that requires the "Show In" menu to be available
	 */
	private boolean isShowInApplicable() {
		if (getSelection().isEmpty()) {
			return false;
		}
		if (getSelection() instanceof IStructuredSelection) {
			IStructuredSelection selection = (IStructuredSelection) getSelection();
			for (Iterator iter = selection.iterator(); iter.hasNext();) {
				Object obj = iter.next();
				if (obj instanceof CtxHelpTopic && ((CtxHelpTopic) obj).getLocation() != null) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns the <code>IShowInSource</code> for this editor.
	 * @return the <code>IShowInSource</code> 
	 */
	private IShowInSource getShowInSource() {
		return new IShowInSource() {
			public ShowInContext getShowInContext() {
				ArrayList resourceList = new ArrayList();
				IStructuredSelection selection = (IStructuredSelection) getSelection();
				IStructuredSelection resources;
				if (selection.isEmpty()) {
					resources = null;
				} else {
					IWorkspaceRoot root = PDEPlugin.getWorkspace().getRoot();
					for (Iterator iter = selection.iterator(); iter.hasNext();) {
						Object obj = iter.next();
						if (obj instanceof CtxHelpTopic) {
							IPath path = ((CtxHelpTopic) obj).getLocation();
							if (path != null && !path.isEmpty()) {
								CtxHelpModel model = (CtxHelpModel) getAggregateModel();
								IPath pluginPath = model.getUnderlyingResource().getProject().getFullPath();
								IResource resource = root.findMember(pluginPath.append(path));
								if (resource != null) {
									resourceList.add(resource);
								}
							}
						}
					}
					resources = new StructuredSelection(resourceList);
				}
				return new ShowInContext(null, resources);
			}
		};
	}

	/**
	 * Returns the <code>IShowInTargetList</code> for this editor.
	 * @return the <code>IShowInTargetList</code> 
	 */
	private IShowInTargetList getShowInTargetList() {
		return new IShowInTargetList() {
			public String[] getShowInTargetIds() {
				return new String[] {JavaUI.ID_PACKAGES, IPageLayout.ID_RES_NAV};
			}
		};
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEFormEditor#isSaveAsAllowed()
	 */
	public boolean isSaveAsAllowed() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEFormEditor#getContextIDForSaveAs()
	 */
	public String getContextIDForSaveAs() {
		return CtxHelpInputContext.CONTEXT_ID;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEFormEditor#addEditorPages()
	 */
	protected void addEditorPages() {
		try {
			addPage(new CtxHelpPage(this));
		} catch (PartInitException e) {
			PDEPlugin.logException(e);
		}
		addSourcePage(CtxHelpInputContext.CONTEXT_ID);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEFormEditor#createContentOutline()
	 */
	protected ISortableContentOutlinePage createContentOutline() {
		return new CtxHelpFormOutlinePage(this);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEFormEditor#createInputContextManager()
	 */
	protected InputContextManager createInputContextManager() {
		return new CtxHelpInputContextManager(this);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEFormEditor#createResourceContexts(org.eclipse.pde.internal.ui.editor.context.InputContextManager, org.eclipse.ui.IFileEditorInput)
	 */
	protected void createResourceContexts(InputContextManager contexts, IFileEditorInput input) {
		contexts.putContext(input, new CtxHelpInputContext(this, input, true));
		contexts.monitorFile(input.getFile());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEFormEditor#createStorageContexts(org.eclipse.pde.internal.ui.editor.context.InputContextManager, org.eclipse.ui.IStorageEditorInput)
	 */
	protected void createStorageContexts(InputContextManager contexts, IStorageEditorInput input) {
		contexts.putContext(input, new CtxHelpInputContext(this, input, true));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEFormEditor#createSystemFileContexts(org.eclipse.pde.internal.ui.editor.context.InputContextManager, org.eclipse.pde.internal.ui.editor.SystemFileEditorInput)
	 */
	protected void createSystemFileContexts(InputContextManager contexts, SystemFileEditorInput input) {
		File file = (File) input.getAdapter(File.class);
		if (file != null) {
			IEditorInput in = new SystemFileEditorInput(file);
			contexts.putContext(in, new CtxHelpInputContext(this, in, true));
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEFormEditor#editorContextAdded(org.eclipse.pde.internal.ui.editor.context.InputContext)
	 */
	public void editorContextAdded(InputContext context) {
		// Add the source page
		addSourcePage(context.getId());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEFormEditor#getInputContext(java.lang.Object)
	 */
	protected InputContext getInputContext(Object object) {
		return fInputContextManager.findContext(CtxHelpInputContext.CONTEXT_ID);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.context.IInputContextListener#contextRemoved(org.eclipse.pde.internal.ui.editor.context.InputContext)
	 */
	public void contextRemoved(InputContext context) {
		close(false);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.context.IInputContextListener#monitoredFileAdded(org.eclipse.core.resources.IFile)
	 */
	public void monitoredFileAdded(IFile monitoredFile) {
		// NO-OP
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.context.IInputContextListener#monitoredFileRemoved(org.eclipse.core.resources.IFile)
	 */
	public boolean monitoredFileRemoved(IFile monitoredFile) {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEFormEditor#getSelection()
	 */
	public ISelection getSelection() {
		IFormPage formPage = getActivePageInstance();
		if ((formPage != null) && (formPage instanceof CtxHelpPage)) {
			// Synchronizes the selection made in the master tree view with the
			// selection in the outline view when the link with editor button
			// is toggled on
			return ((CtxHelpPage) formPage).getSelection();
		}
		return super.getSelection();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEFormEditor#canCut(org.eclipse.jface.viewers.ISelection)
	 */
	public boolean canCut(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection sel = (IStructuredSelection) selection;
			for (Iterator iter = sel.iterator(); iter.hasNext();) {
				Object obj = iter.next();
				if (obj instanceof CtxHelpObject && ((CtxHelpObject) obj).canBeRemoved()) {
					return canCopy(selection);
				}
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.MultiSourceEditor#createSourcePage(org.eclipse.pde.internal.ui.editor.PDEFormEditor, java.lang.String, java.lang.String, java.lang.String)
	 */
	protected PDESourcePage createSourcePage(PDEFormEditor editor, String title, String name, String contextId) {
		return new CtxHelpSourcePage(editor, title, name);
	}

}
