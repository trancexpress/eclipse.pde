/*******************************************************************************
 * Copyright (c) 2008, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.core.target;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.provisional.p2.metadata.MetadataFactory.InstallableUnitDescription;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.InstallableUnitQuery;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.query.IQueryResult;
import org.eclipse.equinox.p2.publisher.Publisher;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.target.provisional.IBundleContainer;

/**
 * A bundle container representing an installed profile.
 * 
 * @since 3.5 
 */
public class ProfileBundleContainer extends AbstractLocalBundleContainer {

	/**
	 * Path to home/root install location. May contain string variables.
	 */
	private String fHome;

	/**
	 * Alternate configuration location or <code>null</code> if default.
	 * May contain string variables.
	 */
	private String fConfiguration;

	/**
	 * Cached, loaded metadata repository holding metadata for this container
	 */
	private IMetadataRepository fRepo;

	/**
	 * Creates a new bundle container for the profile at the specified location.
	 * 
	 * @param home path in local file system, may contain string variables
	 * @param configurationLocation alternate configuration location or <code>null</code> for default,
	 *  may contain string variables
	 */
	public ProfileBundleContainer(String home, String configurationLocation) {
		fHome = home;
		fConfiguration = configurationLocation;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.core.target.provisional.IBundleContainer#generateRepositories(org.eclipse.equinox.p2.core.IProvisioningAgent, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public IMetadataRepository[] generateRepositories(IProvisioningAgent agent, IProgressMonitor monitor) throws CoreException {
		// TODO Use the progress monitor

		String home = resolveHomeLocation().toOSString();
		if (!new File(home).isDirectory()) {
			throw new CoreException(new Status(IStatus.ERROR, PDECore.PLUGIN_ID, NLS.bind(Messages.ProfileBundleContainer_0, home)));
		}

		URL configUrl = getConfigurationArea();
		if (configUrl != null) {
			if (!new File(configUrl.getFile()).isDirectory()) {
				throw new CoreException(new Status(IStatus.ERROR, PDECore.PLUGIN_ID, NLS.bind(Messages.ProfileBundleContainer_2, home)));
			}
		}

		// TODO Hopefully this logic can be moved into ProfileMetadataRepository, see bug 294511
		// Use hard coded directories for now
		File profileArea = new File(configUrl.getFile(), "p2/org.eclipse.equinox.p2.engine/profileRegistry/SDKProfile.profile");
		if (!profileArea.isDirectory()) {
			profileArea = new File(getLocation(true), "p2/org.eclipse.equinox.p2.engine/profileRegistry/SDKProfile.profile");
			if (!profileArea.isDirectory()) {
				throw new CoreException(new Status(IStatus.ERROR, PDECore.PLUGIN_ID, "Could not find profile for installation: " + getLocation(false)));
			}
		}

		// TODO Have to handle cases where p2 is not present (platform.xml and directory)
//		BundleInfo[] infos = P2Utils.readBundles(home, configUrl);
//		if (infos == null) {
//			IResolvedBundle[] platformXML = resolvePlatformXML(definition, home, monitor);
//			if (platformXML != null) {
//				return platformXML;
//			}
//			infos = new BundleInfo[0];
//		}
//
//		if (monitor.isCanceled()) {
//			return new IResolvedBundle[0];
//		}

		fRepo = Publisher.loadMetadataRepository(agent, profileArea.toURI(), false, false);
		return new IMetadataRepository[] {fRepo};
	}

	public InstallableUnitDescription[] getRootIUs() throws CoreException {
		if (fRepo == null) {
			return null;
		}

		// Collect all installable units in the repository
		IQueryResult result = fRepo.query(InstallableUnitQuery.ANY, null);

		InstallableUnitDescription[] descriptions = new InstallableUnitDescription[result.unmodifiableSet().size()];
		int i = 0;
		for (Iterator iterator = result.iterator(); iterator.hasNext();) {
			IInstallableUnit unit = (IInstallableUnit) iterator.next();
			descriptions[i] = new InstallableUnitDescription();
			descriptions[i].setId(unit.getId());
			descriptions[i].setVersion(unit.getVersion());
			i++;
		}

		return descriptions;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.core.target.provisional.IBundleContainer#isContentEqual(org.eclipse.pde.internal.core.target.provisional.IBundleContainer)
	 */
	public boolean isContentEqual(IBundleContainer container) {
		if (container instanceof ProfileBundleContainer) {
			ProfileBundleContainer pbc = (ProfileBundleContainer) container;
			return fHome.equals(pbc.fHome) && isNullOrEqual(fConfiguration, fConfiguration);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.core.target.AbstractLocalBundleContainer#getLocation(boolean)
	 */
	public String getLocation(boolean resolve) throws CoreException {
		if (resolve) {
			return resolveHomeLocation().toOSString();
		}
		return fHome;
	}

	/**
	 * Returns the configuration area for this container if one was specified during creation.
	 * 
	 * @return string path to configuration location or <code>null</code>
	 */
	public String getConfigurationLocation() {
		return fConfiguration;
	}

//	/**
//	 * Resolves installed bundles based on update manager's platform XML.
//	 * 
//	 * @param definition
//	 * @param home
//	 * @param monitor
//	 * @return resolved bundles or <code>null</code> if none
//	 * @throws CoreException
//	 */
//	protected IResolvedBundle[] resolvePlatformXML(ITargetDefinition definition, String home, IProgressMonitor monitor) throws CoreException {
//		File[] files = PluginPathFinder.getPaths(home, false, false);
//		if (files.length > 0) {
//			List all = new ArrayList(files.length);
//			SubMonitor localMonitor = SubMonitor.convert(monitor, Messages.DirectoryBundleContainer_0, files.length);
//			for (int i = 0; i < files.length; i++) {
//				if (localMonitor.isCanceled()) {
//					throw new OperationCanceledException();
//				}
//				try {
//					IResolvedBundle rb = generateBundle(files[i]);
//					if (rb != null) {
//						all.add(rb);
//					}
//				} catch (CoreException e) {
//					// ignore invalid bundles
//				}
//				localMonitor.worked(1);
//			}
//			localMonitor.done();
//			if (!all.isEmpty()) {
//				return (IResolvedBundle[]) all.toArray(new IResolvedBundle[all.size()]);
//			}
//		}
//		return null;
//	}

	/**
	 * Returns the home location with all variables resolved as a path.
	 * 
	 * @return resolved home location
	 * @throws CoreException
	 */
	private IPath resolveHomeLocation() throws CoreException {
		return new Path(resolveVariables(fHome));
	}

	/**
	 * Returns a URL to the configuration area associated with this profile or <code>null</code>
	 * if none.
	 * 
	 * @return configuration area URL or <code>null</code>
	 * @throws CoreException if unable to generate a URL or the user specified location does not exist
	 */
	private URL getConfigurationArea() throws CoreException {
		IPath home = resolveHomeLocation();
		IPath configuration = null;
		if (fConfiguration == null) {
			configuration = home.append("configuration"); //$NON-NLS-1$
		} else {
			configuration = new Path(resolveVariables(fConfiguration));
		}
		File file = configuration.toFile();
		if (file.exists()) {
			try {
				return file.toURL();
			} catch (MalformedURLException e) {
				throw new CoreException(new Status(IStatus.ERROR, PDECore.PLUGIN_ID, NLS.bind(Messages.ProfileBundleContainer_1, home.toOSString()), e));
			}
		} else if (fConfiguration != null) {
			// If the user specified config area does not exist throw an error
			throw new CoreException(new Status(IStatus.ERROR, PDECore.PLUGIN_ID, NLS.bind(Messages.ProfileBundleContainer_2, configuration.toOSString())));
		}
		return null;
	}

	private boolean isNullOrEqual(Object o1, Object o2) {
		if (o1 == null) {
			return o2 == null;
		}
		if (o2 == null) {
			return false;
		}
		return o1.equals(o2);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return new StringBuffer().append("Installation ").append(fHome).append(' ').append(fConfiguration == null ? "Default Configuration" : fConfiguration).toString(); //$NON-NLS-1$ //$NON-NLS-2$
	}

}
