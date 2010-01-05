/*******************************************************************************
 *  Copyright (c) 2008, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *      IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.shared.target;

import java.util.*;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.dialogs.*;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.progress.WorkbenchJob;

/**
 * FilteredTree extension that creates a ContainerCheckedTreeViewer, manages the
 * check state across filtering (working around bugs in ContainerCheckedTreeViewer),
 * and preloads all metadata repositories before allowing filtering, in order to 
 * coordinate background fetch and filtering.  It also manages a cache of expanded
 * elements that can survive a change of input.
 * 
 * @since 3.4
 *
 */
public class FilteredCheckboxTree extends FilteredTree {

	private static final long FILTER_DELAY = 400;

	FormToolkit fToolkit;
	ITreeContentProvider fContentProvider;
	ArrayList checkState = new ArrayList();
	FilteredContainerCheckedTreeViewer checkboxViewer;

	/**
	 * Extension of the tree viewer to allow firing of check state change events
	 */
	class FilteredContainerCheckedTreeViewer extends ContainerCheckedTreeViewer {
		public FilteredContainerCheckedTreeViewer(Tree tree) {
			super(tree);
		}

		// This method is public so that we can programmatically change check state and have listeners be updated
		public void fireCheckStateChanged(Object element, boolean state) {
			fireCheckStateChanged(new CheckStateChangedEvent(this, element, state));
		}
	}

	/**
	 * @param parent
	 * @param contentProvider Used to determine which elements are leaf nodes
	 * @param toolkit
	 */
	public FilteredCheckboxTree(Composite parent, ITreeContentProvider contentProvider, FormToolkit toolkit) {
		super(parent, true);
		fContentProvider = contentProvider;
		fToolkit = toolkit;
		init(SWT.NONE, new PatternFilter());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.dialogs.FilteredTree#doCreateTreeViewer(org.eclipse.swt.widgets.Composite, int)
	 */
	protected TreeViewer doCreateTreeViewer(Composite parent, int style) {
		Tree tree = null;

		if (fToolkit != null) {
			tree = fToolkit.createTree(parent, SWT.CHECK | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		} else {
			tree = new Tree(parent, SWT.CHECK | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		}

		checkboxViewer = new FilteredContainerCheckedTreeViewer(tree);
		checkboxViewer.addCheckStateListener(new ICheckStateListener() {
			public void checkStateChanged(CheckStateChangedEvent event) {
				updateCheckState(event.getElement(), event.getChecked());
			}
		});
		return checkboxViewer;
	}

	void updateCheckState(Object element, boolean state) {
		// We use an additive check state cache so we need to remove
		// previously checked items if the user unchecked them.
		if (!state && checkState != null) {
			ArrayList toRemove = new ArrayList(1);
			if (fContentProvider.hasChildren(element)) {
				Set unchecked = new HashSet();
				Object[] children = fContentProvider.getChildren(element);
				for (int i = 0; i < children.length; i++) {
					unchecked.add(children[i]);
				}
				Iterator iter = checkState.iterator();
				while (iter.hasNext()) {
					Object current = iter.next();
					if (current != null && unchecked.contains(current)) {
						toRemove.add(current);
					}
				}
			} else {
				Iterator iter = checkState.iterator();
				while (iter.hasNext()) {
					Object current = iter.next();
					if (current != null && current.equals(element)) {
						toRemove.add(current);
						// Do not break out of the loop.  We may have duplicate equal
						// elements in the cache.  Since the cache is additive, we want
						// to be sure we've gotten everything.
					}
				}
			}
			checkState.removeAll(toRemove);
		} else if (state) {
			rememberLeafCheckState();
		}
	}

	/*
	 * Overridden to hook a listener on the job and set the deferred content provider
	 * to synchronous mode before a filter is done.
	 * @see org.eclipse.ui.dialogs.FilteredTree#doCreateRefreshJob()
	 */
	protected WorkbenchJob doCreateRefreshJob() {
		WorkbenchJob filterJob = super.doCreateRefreshJob();
		filterJob.addJobChangeListener(new JobChangeAdapter() {
			public void running(IJobChangeEvent event) {
				getDisplay().syncExec(new Runnable() {
					public void run() {
						rememberLeafCheckState();
					}
				});
			}

			public void done(IJobChangeEvent event) {
				if (event.getResult().isOK()) {
					getDisplay().asyncExec(new Runnable() {
						public void run() {
							if (checkboxViewer.getTree().isDisposed())
								return;
							checkboxViewer.getTree().setRedraw(false);
							restoreLeafCheckState();
							checkboxViewer.getTree().setRedraw(true);
						}
					});
				}
			}
		});
		return filterJob;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.dialogs.FilteredTree#doCreateFilterText(org.eclipse.swt.widgets.Composite)
	 */
	protected Text doCreateFilterText(Composite parent) {
		// Overridden so the text gets create using the toolkit if we have one
		Text parentText = super.doCreateFilterText(parent);
		if (fToolkit != null) {
			int style = parentText.getStyle();
			parentText.dispose();
			return fToolkit.createText(parent, null, style);
		}
		return parentText;
	}

	void rememberLeafCheckState() {
		ContainerCheckedTreeViewer v = (ContainerCheckedTreeViewer) getViewer();
		Object[] checked = v.getCheckedElements();
		if (checkState == null)
			checkState = new ArrayList(checked.length);
		for (int i = 0; i < checked.length; i++)
			if (!v.getGrayed(checked[i]) && fContentProvider.getChildren(checked[i]).length == 0)
				if (!checkState.contains(checked[i]))
					checkState.add(checked[i]);
	}

	void restoreLeafCheckState() {
		if (checkboxViewer == null || checkboxViewer.getTree().isDisposed())
			return;
		if (checkState == null)
			return;

		checkboxViewer.setCheckedElements(new Object[0]);
		checkboxViewer.setGrayedElements(new Object[0]);
		// Now we are only going to set the check state of the leaf nodes
		// and rely on our container checked code to update the parents properly.
		Iterator iter = checkState.iterator();
		Object element = null;
		if (iter.hasNext())
			checkboxViewer.expandAll();
		while (iter.hasNext()) {
			element = iter.next();
			checkboxViewer.setChecked(element, true);
		}
	}

	public ContainerCheckedTreeViewer getCheckboxTreeViewer() {
		return checkboxViewer;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.dialogs.FilteredTree#getRefreshJobDelay()
	 */
	protected long getRefreshJobDelay() {
		return FILTER_DELAY;
	}

	/**
	 * Sets the given object as checked or unchecked depending on the given state.
	 * Fires a check state changed event to notify listeners.  This method should
	 * always be used when programmatically changing the check state of elements.
	 * The check state listeners need to be notified so that check state can be
	 * correctly restored after filtering.
	 * 
	 * @param element element with changing check state
	 * @param state <code>true</code> to check the item, <code>false</code> to uncheck
	 */
	public void setChecked(Object element, boolean state) {
		// TODO Should the event only be fired if the state has actually changed?
		checkboxViewer.setChecked(element, state);
		updateCheckState(element, state);
	}

	public void setCheckedElements(Object[] elements) {
		checkboxViewer.setCheckedElements(elements);
		rememberLeafCheckState();
	}

}