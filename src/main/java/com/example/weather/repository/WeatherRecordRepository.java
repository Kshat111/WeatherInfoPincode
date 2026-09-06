package com.example.weather.repository;

import com.example.weather.model.Location;
import com.example.weather.model.WeatherRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface WeatherRecordRepository extends JpaRepository<WeatherRecord, Long> {
    Optional<WeatherRecord> findByLocationAndDate(Location location, LocalDate date);
}
