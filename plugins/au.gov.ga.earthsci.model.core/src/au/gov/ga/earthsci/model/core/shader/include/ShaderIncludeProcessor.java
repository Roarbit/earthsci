/*******************************************************************************
 * Copyright 2013 Geoscience Australia
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
package au.gov.ga.earthsci.model.core.shader.include;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.gov.ga.earthsci.worldwind.common.util.Util;

/**
 * A simple shader {@code #include} processor that scans for {@code #include}
 * directives and returns a version of the shader text with the {@code #include}
 * directive replaced with the appropriate text.
 * <p/>
 * The processor supports sourcing of replacement strings from two locations:
 * <ol>
 * <li>Using the standard Java resource loading mechanism (see
 * {@link Class#getResource(String)}); or
 * <li>Using a named string constant that must be provided to the processor
 * using {@link #namedString(String, String)}
 * </ol>
 * 
 * @author James Navin (james.navin@ga.gov.au)
 * 
 */
public class ShaderIncludeProcessor
{

	private static final Pattern INCLUDE_PATTERN = Pattern.compile("\\s*#include (\\S+)"); //$NON-NLS-1$

	private final Map<String, String> namedStrings = new HashMap<String, String>();

	/**
	 * Register a named string with the processor for inclusion in the directive
	 * replacement process.
	 * 
	 * @param name
	 *            The name to register the replacement string under
	 * @param value
	 *            The value to use if this name is encountered
	 */
	public void namedString(String name, String value)
	{
		namedStrings.put(name, value);
	}

	/**
	 * Remove the named string from this processor.
	 * 
	 * @param name
	 *            The named string to remove
	 */
	public void deleteNamedString(String name)
	{
		namedStrings.remove(name);
	}

	/**
	 * Process the given source string and return the result.
	 * <p/>
	 * Loaded replacements will be processed recursively (e.g. replacement
	 * strings that include {@code #include} will be expanded prior to inclusion
	 * in the output).
	 * <p/>
	 * If any includes are unable to be processed, an exception will be
	 * generated.
	 * 
	 * @param source
	 *            The source string to process
	 * @return The source string, with {@code #include} directives replaced with
	 *         appropriate content.
	 * 
	 * @throws IOException
	 *             If something goes wrong when reading replacements
	 * 
	 * @see #process(String, boolean)
	 */
	public String process(String source) throws IOException
	{
		return process(source, false);
	}

	/**
	 * Process the given source string and return the result.
	 * <p/>
	 * Loaded replacements will be processed recursively (e.g. replacement
	 * strings that include {@code #include} will be expanded prior to inclusion
	 * in the output).
	 * <p/>
	 * If any includes are unable to be processed, and {@code failQuietly} is
	 * <code>false</code>, an exception will be generated. Otherwise the include
	 * will be replaced with the empty string.
	 * 
	 * @param source
	 *            The source string to process
	 * @param failQuietly
	 *            If <code>true</code>, includes that cannot be processed will
	 *            be replaced with the empty string; if <code>false</code>,
	 *            exceptions will be generated.
	 * 
	 * @return The source string, with {@code #include} directives replaced with
	 *         appropriate content.
	 * 
	 * @throws IOException
	 *             If something goes wrong when reading replacements
	 * 
	 * @see #process(String, boolean)
	 */
	public String process(String source, boolean failQuietly) throws IOException
	{
		if (source == null)
		{
			return null;
		}

		StringBuffer result = new StringBuffer();

		// Process line at a time;
		String[] lines = source.split("\n"); //$NON-NLS-1$
		int i = 0;
		for (String line : lines)
		{
			if (isInclude(line))
			{
				String name = getIncludeName(line);
				String substitute;
				try
				{
					substitute = getSubstitute(name);
					result.append(substitute);
				}
				catch (IOException e)
				{
					if (!failQuietly)
					{
						throw e;
					}
				}
			}
			else
			{
				result.append(line);
			}
			if (i < lines.length - 1)
			{
				result.append('\n');
			}
			i++;
		}
		return result.toString();
	}

	private boolean isInclude(String line)
	{
		return INCLUDE_PATTERN.matcher(line.trim()).matches();
	}

	private String getIncludeName(String line)
	{
		Matcher m = INCLUDE_PATTERN.matcher(line.trim());
		m.find();
		String result = m.group(1);
		return result;
	}

	private String getSubstitute(String name) throws IOException
	{
		// First check named strings
		if (namedStrings.containsKey(name))
		{
			return process(namedStrings.get(name));
		}

		// Otherwise try and load a resource
		try
		{
			String includedSource = Util.readStreamToString(getClass().getResourceAsStream(name));
			if (includedSource == null)
			{
				throw new IOException("No include found with name \"" + name + "\""); //$NON-NLS-1$ //$NON-NLS-2$
			}
			return process(includedSource);
		}
		catch (Exception e)
		{
			throw new IOException("Unable to load include \"" + name + "\"", e); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
}