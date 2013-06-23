External Problem Marker for Eclipse
===================================

This plug-in takes `stdout` from the script of your choice, for example:

	views.py:18:101: E501 line too long (102 > 100 characters)
	views.py:25:101: E501 line too long (105 > 100 characters)
	... etc ...

And transforms it to Eclipse problem markers:

![Problem markers](http://i.imgur.com/lJqAr.png)


Known Problems
--------------

**I seems to hang Eclipse's build process sometimes. :(** Haven't been able to figure out
*why* or *when* if happens. I'm not even sure if this problem is caused by the
plugin itself, or by the script I use with it. However, you should know, that this is
the first (and only?) Eclipse plugin I ever wrote.


Installation
------------

* Close Eclipse.
* Download the plugin file from
  [here](https://sourceforge.net/projects/eclipse-epm/files/).
* Put it into the `dropins` directory inside your Eclipse folder.

Then, go to your project directory and edit the `.project` file, add the following sections:

	<buildSpec>
		<!-- Other, existing builders for your project -->
		<buildCommand>...</buildCommand>
		
		<!-- ADD THIS ONE! Consider leaving the URL behind, for future reference. -->
		<buildCommand>
			<!-- About this plugin: https://github.com/wrygiel/eclipse-external-problem-marker -->
			<name>net.rygielski.eclipse.problemmarker.builder</name>
			<arguments>
				<dictionary>
					<key>filter</key>
					<value>^.*\.py$</value> <!-- or ^.*/some/dir/prefix/.*\.py -->
				</dictionary>
				<dictionary>
					<key>command</key>
					<value>cmd /c python D:\\PRIV\\Projekty\\pep8\\pep8.py "$1"</value>
				</dictionary>
				<dictionary>
					<key>output-match</key>
					<value>^([A-Z]:)?[^:]+:([0-9]+):([0-9]+): (.)(.*)$</value>
				</dictionary>
				<dictionary>
					<key>line-number</key>
					<value>$2</value>
				</dictionary>
				<dictionary>
					<key>severity</key>
					<value>W</value> <!-- or $4 -->
				</dictionary>
				<dictionary>
					<key>message</key>
					<value>$4$5</value>
				</dictionary>
			</arguments>
		</buildCommand>
	</buildSpec>
	
	<natures>
		<!-- Other, existing natures for your project -->
		<nature>...</nature>
		
		<!-- ADD THIS ONE! -->
		<nature>net.rygielski.eclipse.problemmarker.nature</nature>
	</natures>

Please note, that you must add **both** `<buildCommand>` and `<nature>` elements.

Reopen your project. Right-click on the project and select *Properties -> Builders*.
A new project builder named *External Problem Marker Builder* should be available.
Use the checkbox to turn it on/off.


Configuration
-------------

If you want to change the default configuration, you will need to know a good deal
about **regular expressions** (specifically, group substitution syntax).
All configuration is done via editing `<arguments>` elements in the `.project` file.

All keys are **required**. Here is what they mean:

  * **filter** (e.g. `^.*\.py$`) - A regular expression which will be used to filter
    files within your project. Only files which match the given path filter will be
	included in the build. Before version 1.1.0, this was matched against file names
	only, now it is matched against files' absolute paths.
  * **command** (e.g. `cmd /c python D:\\PRIV\\Projekty\\pep8\\pep8.py "$1"`) -
    The command to execute upon each of your matched files.
    * It **should** contain the `$1` wildcard which will be replaced with a
      file path upon build.
    * All backslashes within the command **must** be prefixed with another backslash. 
    * Whenever you rebuild your entire project, the command will be executed N times,
      where N is the total number of files matching the *filter*. Depending of the
      performance of your *command*, it might be a lengthy process.
    * Once built, successive builds are incremental. The command is executed once
      per each changed file.
    * All content which the command writes to `stderr` will be reported as
      error in line 1.
  * **output-match** (e.g. `^([A-Z]:)?[^:]+:([0-9]+):([0-9]+): (.)(.*)$`) -
    A regular expression to match lines from the *command*'s `stdout` to. It should
    match the whole line (so, it should begin with `^` and end with `$`). It should
    contain nested expressions at least in those places where the line number and
    warning message should be. Lines which do not match this expression will
    be ignored.
  * **line-number** (e.g. `$2`) - `$` character, followed by the index of *output-match*'s
  nested group expression which contains the line number.
  * **severity** (e.g. `W` or `$4`) - This should resolve into one of the letters
    E (error), W (warning) or I (info). If your `command` differentiates between
    various severites, you may use the `$N` syntax to match the proper nested group
    expression.
  * **message** - The message to display along the problem marker.


Contact
-------

Email me at rygielski@mimuw.edu.pl.
