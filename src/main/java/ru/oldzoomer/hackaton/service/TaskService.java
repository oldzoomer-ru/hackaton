package ru.oldzoomer.hackaton.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oldzoomer.hackaton.entity.Task;
import ru.oldzoomer.hackaton.repository.TaskRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Log4j2
public class TaskService {

    private final TaskRepository taskRepo;

    @Transactional
    public Task addTask(Long hackathonId, String title, String assignee, LocalDate deadline, String notes) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title must not be blank");
        }
        if (hackathonId == null) {
            throw new IllegalArgumentException("Hackathon ID must not be null");
        }
        Task task = new Task();
        task.setHackathonId(hackathonId);
        task.setTitle(title);
        task.setAssignee(assignee);
        task.setDeadline(deadline);
        task.setNotes(notes);
        task.setStatus("TODO");
        Task saved = taskRepo.save(task);
        log.info("Task added: {} for hackathon {}", title, hackathonId);
        return saved;
    }

    @Transactional
    public Task markDone(Long taskId) {
        return taskRepo.findById(taskId).map(task -> {
            task.setStatus("DONE");
            task.setCompletedAt(java.time.LocalDateTime.now());
            Task saved = taskRepo.save(task);
            log.info("Task completed: {}", task.getTitle());
            return saved;
        }).orElseThrow(() -> new NoSuchElementException("Task not found with id: " + taskId));
    }

    public List<Task> findByHackathonId(Long hackathonId) {
        return taskRepo.findByHackathonId(hackathonId);
    }

    public List<Task> findOverdue() {
        return taskRepo.findOverdueTasks(LocalDate.now());
    }

    public List<Task> findInProgress() {
        return taskRepo.findInProgressTasks();
    }

    public List<Task> findDoneSince(int days) {
        return taskRepo.findDoneSince(LocalDate.now().minusDays(days));
    }

    public List<Task> findTasksDueInDays(int days) {
        return taskRepo.findTasksByDeadlineRange(LocalDate.now(), LocalDate.now().plusDays(days));
    }

    @Transactional
    public Task updateTaskStatus(Long taskId, String status) {
        return taskRepo.findById(taskId).map(task -> {
            task.setStatus(status);
            if ("DONE".equals(status)) {
                task.setCompletedAt(java.time.LocalDateTime.now());
            }
            Task saved = taskRepo.save(task);
            log.info("Task {} status updated to {}", taskId, status);
            return saved;
        }).orElseThrow(() -> new NoSuchElementException("Task not found with id: " + taskId));
    }
}
