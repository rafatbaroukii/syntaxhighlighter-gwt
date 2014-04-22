/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package com.alexgorbatchev.syntaxhighlighter.compiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.alexgorbatchev.syntaxhighlighter.client.Brush.Source;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.user.rebind.SourceWriter;

/**
 * Generator that transforms an annotated {@link com.alexgorbatchev.syntaxhighlighter.client.Brush Brush} into a class
 * that extends the {@link com.alexgorbatchev.syntaxhighlighter.client.BrushImpl BrushImpl} class.
 * 
 * @author Xlorep DarkHelm
 * 
 */
public class BrushGenerator extends BaseGenerator {
	/**
	 * The fully-qualified BrushImpl class name.
	 */
	private static final String SUPER = "com.alexgorbatchev.syntaxhighlighter.client.BrushImpl";
	/**
	 * The list of imports for the generated BrushImpl subclasses.
	 */
	private static final String[] IMPORTS = { "com.google.gwt.core.client.GWT", "com.google.gwt.resources.client.ClientBundle",
					"com.google.gwt.resources.client.ClientBundle.Source", "com.google.gwt.resources.client.ExternalTextResource" };
	/**
	 * The {@link com.alexgorbatchev.syntaxhighlighter.client.Resources#coreScript() core script} name.
	 */
	private static final String CORE_SCRIPT = "shCore.js";
	
	@Override
	protected void generate(SourceWriter writer) throws UnableToCompleteException {
		if (getType().isInterface() == null) {
			log(TreeLogger.ERROR, "Brush \"" + getType().getQualifiedSourceName() + "\" must be an Interface.");
			throw new UnableToCompleteException();
		}
		String alias = getAlias(getScript());
		String source = buildPath(getScript());
		
		writer.println("interface Resource extends ClientBundle {");
		writer.indent();
		writer.println("@Source(\"" + source + "\")");
		writer.println("public ExternalTextResource script();");
		writer.outdent();
		writer.println("}");
		writer.println();
		writer.println("private static final Resource RES = GWT.create(Resource.class);");
		writer.println();
		writer.println("public " + getName() + "() {");
		writer.indent();
		writer.println("super(\"" + alias + "\", RES.script());");
		writer.outdent();
		writer.println("}");
	}
	
	@Override
	protected String getSuper() {
		return SUPER;
	}
	
	@Override
	protected String[] getImports() {
		return IMPORTS;
	}
	
	@Override
	protected String getName() throws UnableToCompleteException {
		return (getSuperName() + '_' + getScript()).replace('.', '_').replace('/', '_').replace(FSEP, "_");
	}
	
	/**
	 * Loads the given JavaScript file into a String.
	 * 
	 * @param path
	 *            the path & filename for the JavaScript file to load.
	 * 
	 * @return the loaded JavaScript file.
	 * 
	 * @throws UnableToCompleteException
	 */
	private String loadScript(String path) throws UnableToCompleteException {
		InputStream in = ClassLoader.getSystemResourceAsStream(path.replace(FSEP, "/"));
		Reader scriptReader = null;
		try {
			scriptReader = new InputStreamReader(in, "UTF-8");
		}
		catch (UnsupportedEncodingException ex) {
			log(TreeLogger.ERROR, null, ex);
			throw new UnableToCompleteException();
		}
		StringBuilder jScript = new StringBuilder();
		BufferedReader bufferedReader = new BufferedReader(scriptReader);
		try {
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				jScript.append(line);
				jScript.append(NL);
			}
		}
		catch (IOException ex) {
			log(TreeLogger.ERROR, null, ex);
		}
		finally {
			try {
				bufferedReader.close();
				scriptReader.close();
			}
			catch (IOException ex) {
				log(TreeLogger.ERROR, null, ex);
				throw new UnableToCompleteException();
			}
		}
		return jScript.toString();
	}
	
	/**
	 * Gets the alias to use for the generated Brush. This is done through JSR-223 using Rhino to actually load the
	 * SyntaxHighlighter into Rhino and then looking up the first alias defined for the brush that gets loaded into
	 * JavaScript.
	 * 
	 * @param scriptFile
	 *            the JavaScript filename to load.
	 * 
	 * @return the alias to use for the generated Brush.
	 * 
	 * @throws UnableToCompleteException
	 */
	private String getAlias(String scriptFile) throws UnableToCompleteException {
		StringBuilder jScript = new StringBuilder();
		jScript.append(loadScript(getPath(CORE_SCRIPT) + CORE_SCRIPT));
		jScript.append(NL);
		jScript.append(NL);
		jScript.append(loadScript(getPath(scriptFile) + scriptFile));
		jScript.append(NL);
		jScript.append(NL);
		jScript.append("function getAlias() {");
		jScript.append(NL);
		jScript.append("    for(item in SyntaxHighlighter.brushes) {");
		jScript.append(NL);
		jScript.append("        return SyntaxHighlighter.brushes[item].aliases[0];");
		jScript.append(NL);
		jScript.append("    }");
		jScript.append(NL);
		jScript.append("}");
		jScript.append(NL);
		ScriptEngineManager engineManager = new ScriptEngineManager();
		ScriptEngine engine = engineManager.getEngineByName("JavaScript");
		String alias = null;
		try {
			log(TreeLogger.DEBUG, "Retrieving the Alias from the script \"" + scriptFile + "\"");
			engine.eval(jScript.toString());
			Invocable invoker = (Invocable) engine;
			alias = String.valueOf(invoker.invokeFunction("getAlias"));
		}
		catch (NoSuchMethodException ex) {
			log(TreeLogger.ERROR, null, ex);
			throw new UnableToCompleteException();
		}
		catch (ScriptException ex) {
			log(TreeLogger.ERROR, null, ex);
			throw new UnableToCompleteException();
		}
		if (alias == null) {
			log(TreeLogger.ERROR, "Could not get the alias for this script.");
			throw new UnableToCompleteException();
		}
		else {
			log(TreeLogger.DEBUG, "Brush Alias: " + alias);
		}
		return alias;
	}
	
	/**
	 * Validates whether the generator can find the JavaScript file for the Brush.
	 * 
	 * @return the file name for the JavaScript file to use.
	 * 
	 * @throws UnableToCompleteException
	 */
	private String getScript() throws UnableToCompleteException {
		Source source = getType().getAnnotation(Source.class);
		if (source == null) {
			log(TreeLogger.ERROR, "No Brush Source found, cannot continue.");
			throw new UnableToCompleteException();
		}
		if (source.value() == null || source.value().trim().length() == 0) {
			log(TreeLogger.ERROR, "Empty or null Brush JavaScript filename.");
			throw new UnableToCompleteException();
		}
		String scriptPath = getPath(source.value());
		if (scriptPath != null) {
			log(TreeLogger.DEBUG, "Brush JavaScript File: " + scriptPath + source.value());
		}
		else {
			log(TreeLogger.ERROR, "Unable to locate Brush JavaScript file \"" + source.value() + "\".");
			throw new UnableToCompleteException();
		}
		return source.value();
	}
}