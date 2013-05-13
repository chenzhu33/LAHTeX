package lah.tex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lah.spectre.multitask.ScheduleTaskManager;
import lah.spectre.process.TimedShell;
import lah.spectre.stream.StreamRedirector;
import lah.tex.compile.CompileDocument;
import lah.tex.manage.InstallPackage;
import lah.tex.manage.MakeFontConfigurations;
import lah.tex.manage.MakeLSR;
import lah.tex.manage.MakeLanguageConfigurations;

/**
 * Main expose interface to client of this library.
 * 
 * @author L.A.H.
 * 
 */
public class TeXMF extends ScheduleTaskManager<Task> {

	/**
	 * TeX languages that contain hyphenation
	 */
	public static final String[] ALL_HYPHEN_LANGUAGES = { "afrikaans", "ancientgreek", "arabic", "armenian", "basque",
			"bulgarian", "catalan", "chinese", "coptic", "croatian", "czech", "danish", "dutch", "english",
			"esperanto", "estonian", "ethiopic", "farsi", "finnish", "french", "friulan", "galician", "german",
			"greek", "hungarian", "icelandic", "indic", "indonesian", "interlingua", "irish", "italian", "kurmanji",
			"latin", "latvian", "lithuanian", "mongolian", "norwegian", "piedmontese", "polish", "portuguese",
			"romanian", "romansh", "russian", "sanskrit", "serbian", "slovak", "slovenian", "spanish", "swedish",
			"turkish", "turkmen", "ukrainian", "uppersorbian", "welsh" };

	/**
	 * My Dropbox shared folder containing the packages
	 */
	private static final String DROPBOX_ARCHIVE = "http://dl.dropbox.com/sh/wgsn35mknpaa29k/";

	/**
	 * Map a package name to its Dropbox's hash key
	 */
	private static Map<String, String> dropbox_keys_map;

	/**
	 * Pattern for lines in lahtex_dbkeys
	 */
	private static final Pattern package_key_pattern = Pattern.compile("([^ ]+) (.*)\n");

	private static TeXMF texmf_instance;

	public static final TeXMF getInstance(IEnvironment environment) {
		if (texmf_instance == null)
			texmf_instance = new TeXMF(environment);
		return texmf_instance;
	}

	private Map<Integer, TaskGroup> task_group_id_map;

	private List<TaskGroup> task_groups;

	private TeXMF(IEnvironment environment) {
		// super(Executors.newFixedThreadPool(3));
		this.task_groups = new ArrayList<TaskGroup>();
		this.task_group_id_map = new TreeMap<Integer, TaskGroup>();
		Task.task_manager = this;
		Task.environment = environment;
		Task.shell = new TimedShell();
		Task.make_lsr_task = new MakeLSR();
		notifyEnvironmentChanged();
	}

	/**
	 * Add a task to a group and enqueue it for execution.
	 * 
	 * This method has default access level since the intention is only to allow task to submit issue resolution tasks.
	 * 
	 * @param task
	 *            Task to add
	 * @param group
	 *            Containing group, for some unknown reason, this might be {@code null}
	 */
	void add(Task task, TaskGroup group) {
		task.setGroup(group);
		if (task != null && group != null && task != group.getMainTask() && !group.subordinated_tasks.contains(task))
			group.subordinated_tasks.add(task);
		enqueue(task);
	}

	/**
	 * Create a new task and submit for scheduled execution
	 * 
	 * @param task_type
	 *            One of {@link TaskType} enumeration
	 * @param args
	 *            A list of Strings as additional argument, interpretation depending on the task type
	 * @return A new {@link Task} created, added for scheduling
	 */
	public Task createTask(TaskType task_type, String[] args) {
		Task result_task;
		switch (task_type) {
		case COMPILE:
			result_task = new CompileDocument(args[0], args[1]);
			break;
		case INSTALL_PACKAGE:
			result_task = new InstallPackage(args);
			break;
		case MAKE_FONTCONFIG:
			result_task = new MakeFontConfigurations();
			break;
		case MAKE_LANGUAGES_CONFIG:
			result_task = new MakeLanguageConfigurations(args);
			break;
		default:
			result_task = null;
		}
		if (result_task != null) {
			TaskGroup result_group = new TaskGroup(result_task);
			synchronized (task_groups) {
				task_groups.add(result_group);
				task_group_id_map.put(result_group.getId(), result_group);
			}
			add(result_task, result_group);
		}
		onStateChanged(result_task);
		return result_task;
	}

	/**
	 * Generate the dropbox URL for a package
	 * 
	 * @param package_name
	 *            Name of package to get Dropbox URL for
	 * @return URL to the author's shared package file
	 * @throws Exception
	 */
	public String getDropboxPackageURL(String package_name) throws Exception {
		if (dropbox_keys_map == null) {
			Map<String, String> temp_dropbox_keys_map = new TreeMap<String, String>();
			String dbkeys = Task.environment.readLahTeXAsset(IEnvironment.LAHTEX_DBKEYS);
			Matcher matcher = package_key_pattern.matcher(dbkeys);
			while (matcher.find())
				temp_dropbox_keys_map.put(matcher.group(1), matcher.group(2));
			dropbox_keys_map = temp_dropbox_keys_map;
		}
		String key = (dropbox_keys_map == null ? null : dropbox_keys_map.get(package_name));
		return (key == null ? null : DROPBOX_ARCHIVE + key + "/" + package_name + InstallPackage.PACKAGE_EXTENSION);
	}

	/**
	 * Get the list of task groups
	 * 
	 * Client is advised to synchronized on access to this list
	 * 
	 * @return The list of task group
	 */
	public List<TaskGroup> getTaskGroups() {
		return task_groups;
	}

	public TaskGroup getTaskGroupWithId(int id) {
		return task_group_id_map.get(id);
	}

	/**
	 * Attempt to mount a temporary file system to boost performance
	 * 
	 * @return
	 */
	public boolean mountTempFS() {
		try {
			Task.shell.fork(new String[] { "su" }, null, StreamRedirector.STDOUT, 600000);
			Task.shell.fork(new String[] { "mount", "-t", "tmpfs", "/dev/ram",
					Task.environment.getTeXMFRootDirectory() + "/texmf-var/tmp" }, null, StreamRedirector.STDOUT,
					600000);
			return true;
		} catch (Exception e) {
			e.printStackTrace(System.out);
			return false;
		}
	}

	/**
	 * Method to be invoked by client when there are changes in environment
	 */
	public void notifyEnvironmentChanged() {
		Task.setupEnvironment();
	}

	@Override
	public void onStateChanged(Task task) {
		if (Task.environment != null)
			Task.environment.onStateChanged(task);
	}

	/**
	 * Remove an existing task
	 * 
	 * @param task
	 *            Task to remove from management
	 */
	public void remove(Task task) {
		synchronized (task_groups) {
			cancel(task);
			TaskGroup group = task.getGroup();
			if (group.main_task == task) {
				for (Task subtask : group.subordinated_tasks)
					cancel(subtask);
				task_groups.remove(group);
				task_group_id_map.remove(group.getId());
			} else
				group.subordinated_tasks.remove(task);
		}
	}

	/**
	 * Unmount the temporary file system
	 * 
	 * @return
	 */
	public boolean unmountTempFS() {
		try {
			Task.shell.fork(new String[] { "su" }, null, StreamRedirector.STDOUT, 600000);
			Task.shell.fork(new String[] { "umount", Task.environment.getTeXMFRootDirectory() + "/texmf-var/tmp" },
					null, StreamRedirector.STDOUT, 600000);
			return true;
		} catch (Exception e) {
			e.printStackTrace(System.out);
			return false;
		}
	}
}
