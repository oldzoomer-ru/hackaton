package ru.oldzoomer.hackaton.repository;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oldzoomer.hackaton.entity.Hackathon;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HackathonRepository extends CrudRepository<Hackathon, Long> {

    @Query("SELECT * FROM hackathons ORDER BY created_at DESC")
    List<Hackathon> findAllOrdered();

    @Query("SELECT * FROM hackathons WHERE registration_deadline <= :deadline AND status = 'REGISTERING' ORDER BY registration_deadline ASC")
    List<Hackathon> findUpcomingRegistrations(@Param("deadline") LocalDate deadline);

    @Query("SELECT * FROM hackathons WHERE status != 'COMPLETED' ORDER BY created_at DESC")
    List<Hackathon> findActiveHackathons();
}
