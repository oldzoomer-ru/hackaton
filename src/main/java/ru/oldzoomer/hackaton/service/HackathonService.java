package ru.oldzoomer.hackaton.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oldzoomer.hackaton.entity.Hackathon;
import ru.oldzoomer.hackaton.repository.HackathonRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
public class HackathonService {

    private final HackathonRepository hackathonRepo;

    public List<Hackathon> findAllActive() {
        return hackathonRepo.findActiveHackathons();
    }

    public List<Hackathon> findUpcomingRegistrations(int daysAhead) {
        return hackathonRepo.findUpcomingRegistrations(LocalDate.now().plusDays(daysAhead));
    }

    public List<Hackathon> findAllOrdered() {
        return hackathonRepo.findAllOrdered();
    }

    @Transactional
    public void updateHackathonStatus(Long id, String status) {
        hackathonRepo.findById(id).ifPresent(h -> {
            h.setStatus(status);
            hackathonRepo.save(h);
        });
    }

    public String getDaysUntil(LocalDate deadline) {
        if (deadline == null) return "N/A";
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), deadline);
        if (days < 0) return Math.abs(days) + " дн. назад (просрочен)";
        if (days == 0) return "Сегодня!";
        if (days == 1) return "Завтра";
        return days + " дн.";
    }
}
