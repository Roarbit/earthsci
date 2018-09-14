/*******************************************************************************
 * Copyright 2012 Geoscience Australia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package au.gov.ga.earthsci.application;

import java.util.ArrayList;
import java.util.List;

/**
 * Main class for starting the webstart version. Fixes the osgi.install.area
 * user.home bug (https://bugs.eclipse.org/bugs/show_bug.cgi?id=349834).
 * 
 * @author Michael de Hoog (michael.dehoog@ga.gov.au)
 */
public class WebStartMain
{
	public static void main(String[] args)
	{
		fixInstallArea();
		List<String> newArgs = new ArrayList<String>();
		for (String s : args)
		{

			for (String brokenArg : s.split(";;"))
			{
				newArgs.add(brokenArg);
			}
		}
		org.eclipse.equinox.launcher.WebStartMain.main(newArgs.toArray(new String[0]));
	}

	private final static String PROP_INSTALL_AREA = "jnlp.osgi.install.area"; //$NON-NLS-1$
	private final static String PROP_USER_HOME = "user.home"; //$NON-NLS-1$
	private final static String PROP_USER_DIR = "user.dir"; //$NON-NLS-1$

	public static void fixInstallArea()
	{
		String osgiInstallArea = System.getProperty(PROP_INSTALL_AREA);
		if (osgiInstallArea != null)
		{
			if (osgiInstallArea.startsWith("@" + PROP_USER_HOME)) //$NON-NLS-1$
			{
				osgiInstallArea = "file:/" + System.getProperty(PROP_USER_HOME) //$NON-NLS-1$
						+ osgiInstallArea.substring(1 + PROP_USER_HOME.length());
			}
			else if (osgiInstallArea.startsWith("@" + PROP_USER_DIR)) //$NON-NLS-1$
			{
				osgiInstallArea = "file:/" + System.getProperty(PROP_USER_DIR) //$NON-NLS-1$
						+ osgiInstallArea.substring(1 + PROP_USER_DIR.length());
			}
			System.setProperty(PROP_INSTALL_AREA, osgiInstallArea);
		}

		java.util.Properties props = System.getProperties();
		for (String key : props.stringPropertyNames())
		{
			if (key.startsWith("jnlp."))
			{
				System.setProperty(key.substring(5), props.getProperty(key));
			}
		}
	}
}
