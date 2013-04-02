package lah.tex;

import java.util.LinkedList;
import java.util.List;

public class TaskGroup {

	final Task main_task;

	List<Task> subordinated_tasks;

	TaskGroup(Task main_task) {
		this.main_task = main_task;
		this.subordinated_tasks = new LinkedList<Task>();
	}

	public Task getMainTask() {
		return main_task;
	}

	public List<Task> getSubordinatedTasks() {
		return subordinated_tasks;
	}

}
