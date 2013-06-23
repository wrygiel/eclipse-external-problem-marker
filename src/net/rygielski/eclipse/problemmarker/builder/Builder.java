package net.rygielski.eclipse.problemmarker.builder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

public class Builder extends IncrementalProjectBuilder {

	private final static Logger LOGGER = Logger.getLogger(Builder.class
			.getName());

	class Issue {

		public String message;
		public int markerSeverity;
		public int lineNumber;

		public Issue(int markerSeverity, int lineNumber, String message) {
			super();
			this.message = message;
			this.markerSeverity = markerSeverity;
			this.lineNumber = lineNumber;
		}

	}

	class Config {
		public Pattern filterPattern;
		public Pattern outputLinePattern;
		public String lineNumberReplacement;
		public String messageReplacement;
		public String severityReplacement;
		public String command;
	}

	private Config config;

	private Config getConfig() {
		if (config == null) {
			String MSG = "Missing or invalid builder configuration.\n"
					+ "Please read the docs at: https://github.com/wrygiel/eclipse-external-problem-marker";
			Map<String, String> arguments = this.getCommand().getArguments();
			this.config = new Config();
			this.config.command = arguments.get("command");
			if (this.config.command == null)
				throw new RuntimeException(MSG);
			String filterRegexp = arguments.get("filter");
			if (filterRegexp == null)
				throw new RuntimeException(MSG);
			
			/* Version 1.0.0 allowed file-matching only. Version 1.1.0 allows
			 * path matching too. To stay backward-compatible, we'll check if
			 * there's a slash in the filter regexp. */
			
			if (filterRegexp.contains("/")) {
				/* Path matching */
			} else {
				/* Filename-matching. Convert it to a proper path-matching
				 * regexp. */
				if (filterRegexp.startsWith("^")) {
					filterRegexp = filterRegexp.substring(1);
				}
				filterRegexp = "^.*[/\\\\]" + filterRegexp;
			}
			this.config.filterPattern = Pattern.compile(filterRegexp);
			String outputLineRegexp = arguments.get("output-match");
			if (outputLineRegexp == null)
				throw new RuntimeException(MSG);
			this.config.outputLinePattern = Pattern.compile(outputLineRegexp);
			this.config.lineNumberReplacement = arguments.get("line-number");
			if (this.config.lineNumberReplacement == null)
				throw new RuntimeException(MSG);
			this.config.messageReplacement = arguments.get("message");
			if (this.config.messageReplacement == null)
				throw new RuntimeException(MSG);
			this.config.severityReplacement = arguments.get("severity");
			if (this.config.severityReplacement == null)
				throw new RuntimeException(MSG);
		}
		return config;
	}

	class DeltaVisitor implements IResourceDeltaVisitor {
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			switch (delta.getKind()) {
			case IResourceDelta.ADDED:
				// handle added resource
				addMarkersToResource(resource);
				break;
			case IResourceDelta.REMOVED:
				// handle removed resource
				break;
			case IResourceDelta.CHANGED:
				// handle changed resource
				addMarkersToResource(resource);
				break;
			}
			// return true to continue visiting children.
			return true;
		}
	}

	class ResourceVisitor implements IResourceVisitor {
		public boolean visit(IResource resource) {
			addMarkersToResource(resource);
			// return true to continue visiting children.
			return true;
		}
	}

	public static final String BUILDER_ID = "net.rygielski.eclipse.problemmarker.builder";

	private static final String MARKER_TYPE = "net.rygielski.eclipse.problemmarker.problem";

	protected IProject[] build(int kind, Map<String,String> args, IProgressMonitor monitor)
			throws CoreException {
		if (kind == FULL_BUILD) {
			fullBuild(monitor);
		} else {
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return null;
	}

	void addMarkersToResource(IResource resource) {
		LOGGER.log(Level.INFO, "Trying " + resource.getName() + "...");
		if (!(resource instanceof IFile)) {
			return;
		}
		String path = resource.getLocation().toString();
		path = path.replace('\\', '/');
		if (getConfig().filterPattern.matcher(path).find()) {
			IFile file = (IFile) resource;
			deleteMarkers(file);
			List<Issue> issues = getIssues(file.getRawLocation().makeAbsolute());
			try {
				for (Issue i : issues) {
					IMarker marker = file.createMarker(MARKER_TYPE);
					marker.setAttribute(IMarker.MESSAGE, i.message);
					marker.setAttribute(IMarker.SEVERITY, i.markerSeverity);
					marker.setAttribute(IMarker.LINE_NUMBER, i.lineNumber);
					marker.setAttribute(IMarker.TRANSIENT, true);
				}
			} catch (CoreException e) {
			}
		}
	}

	private List<Issue> getIssues(IPath fullPath) {
		LOGGER.log(Level.INFO, "Will generate issues for " + fullPath);
		List<Issue> issues = new ArrayList<Issue>();
		String line;
		Process p;
		String command = fullPath.makeAbsolute().toOSString()
				.replaceAll("^(.*)$", getConfig().command);
		try {
			LOGGER.log(Level.INFO, "Execute: " + command);
			p = Runtime.getRuntime().exec(command);
		} catch (IOException e1) {
			issues.add(new Issue(IMarker.SEVERITY_ERROR, 1,
					"Couldn't execute: " + command + "\n" + e1.getMessage()));
			return issues;
		}

		BufferedReader bri = new BufferedReader(new InputStreamReader(
				p.getInputStream()));
		BufferedReader bre = new BufferedReader(new InputStreamReader(
				p.getErrorStream()));
		try {
			while ((line = bri.readLine()) != null) {
				Issue issue = readIssue(line);
				if (issue != null)
					issues.add(issue);
			}
			bri.close();
			while ((line = bre.readLine()) != null) {
				issues.add(new Issue(IMarker.SEVERITY_ERROR, 1, "stderr: "
						+ line));
			}
			bre.close();
		} catch (IOException e) {
			issues.add(new Issue(IMarker.SEVERITY_ERROR, 1, "I/O error: "
					+ e.getMessage()));
			return issues;
		}

		try {
			p.waitFor();
		} catch (InterruptedException e) {
			issues.add(new Issue(IMarker.SEVERITY_ERROR, 1,
					"Command interrupted: " + e.getMessage()));
			return issues;
		}

		return issues;
	}

	private Issue readIssue(String line) {
		LOGGER.log(Level.INFO, "Parsing line: " + line);
		try {
			Matcher matcher = getConfig().outputLinePattern.matcher(line);
			if (matcher.find()) {
				int lineNumber = Integer.parseInt(matcher
						.replaceAll(getConfig().lineNumberReplacement));
				String message = matcher
						.replaceAll(getConfig().messageReplacement);
				String severityStr = matcher
						.replaceAll(getConfig().severityReplacement);
				int markerSeverity = IMarker.SEVERITY_ERROR;
				if (severityStr.startsWith("W"))
					markerSeverity = IMarker.SEVERITY_WARNING;
				else if (severityStr.startsWith("E"))
					markerSeverity = IMarker.SEVERITY_ERROR;
				else if (severityStr.startsWith("I"))
					markerSeverity = IMarker.SEVERITY_INFO;
				LOGGER.log(Level.INFO, "Line matched: " + severityStr + "@"
						+ lineNumber + " " + message);
				Issue issue = new Issue(markerSeverity, lineNumber, message);
				return issue;
			}
		} catch (RuntimeException e) {
			return new Issue(IMarker.SEVERITY_ERROR, 1,
					"Error when matching line: " + line + "\n" + e.getMessage());
		}
		return null;
	}

	private void deleteMarkers(IFile file) {
		try {
			file.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
		} catch (CoreException ce) {
		}
	}

	protected void fullBuild(final IProgressMonitor monitor)
			throws CoreException {
		setupLogger();
		try {
			getProject().accept(new ResourceVisitor());
		} catch (CoreException e) {
		}
	}

	void setupLogger() {
		/*
		 * if (LOGGER.getHandlers().length > 0) return;
		 * LOGGER.setLevel(Level.ALL); FileHandler fileTxt; try { fileTxt = new
		 * FileHandler("D:\\log.txt"); } catch (IOException e) { throw new
		 * RuntimeException(e); } SimpleFormatter formatterTxt = new
		 * SimpleFormatter(); fileTxt.setFormatter(formatterTxt);
		 * LOGGER.addHandler(fileTxt);
		 */
	}

	protected void incrementalBuild(IResourceDelta delta,
			IProgressMonitor monitor) throws CoreException {
		setupLogger();
		delta.accept(new DeltaVisitor());
	}
}
