/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.api.tools.internal.model;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.api.tools.internal.provisional.ApiPlugin;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiComponent;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiElement;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiType;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiTypeRoot;
import org.eclipse.pde.api.tools.internal.util.Util;

/**
 * Common implementation for {@link IApiTypeRoot}
 * 
 * @since 1.0.0
 */
public abstract class AbstractApiTypeRoot extends ApiElement implements IApiTypeRoot {

	/**
	 * Constructor
	 * @param parent the parent {@link IApiElement} or <code>null</code> if none
	 * @param name the name of the type root
	 */
	protected AbstractApiTypeRoot(IApiElement parent, String name) {
		super(parent, IApiElement.API_TYPE_ROOT, name);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.provisional.IApiTypeRoot#getContents()
	 */
	public byte[] getContents() throws CoreException {
		InputStream inputStream = getInputStream();
		try {
			return Util.getInputStreamAsByteArray(inputStream, -1);
		} catch (IOException e) {
			abort("Unable to read class file: " + getTypeName(), e); //$NON-NLS-1$
			return null; // never gets here
		} finally {
			try {
				inputStream.close();
			} catch(IOException e) {
				ApiPlugin.log(e);
			}
		}
	}
	
	/**
	 * Returns an input stream for reading this {@link IApiTypeRoot}. Clients are responsible
	 * for closing the input stream.
	 * 
	 * @return input stream
	 */
	public abstract InputStream getInputStream() throws CoreException;	
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.provisional.IApiTypeRoot#getStructure()
	 */
	public IApiType getStructure() throws CoreException {
		return TypeStructureBuilder.buildTypeStructure(getContents(), getApiComponent(), this);
	}
	
	/**
	 * @see org.eclipse.pde.api.tools.internal.provisional.IApiTypeRoot#getApiComponent()
	 */
	public IApiComponent getApiComponent() {
		return (IApiComponent) getAncestor(IApiElement.COMPONENT);
	}
}
