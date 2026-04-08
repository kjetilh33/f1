package com.kinnovatio.f1.repository;

import com.kinnovatio.f1.model.SessionKeyedMessage;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.SQLException;
import java.util.Optional;

@ApplicationScoped
public class WeatherDataRepository {
    private static final Logger LOG = Logger.getLogger(WeatherDataRepository.class);
    private static final String weatherKey = "weatherData";

    @Inject
    AgroalDataSource storageDataSource;

    @Inject
    RepositoryUtilities repositoryUtilities;

    @ConfigProperty(name = "app.weather-data.table")
    String weatherDataTable;

    public Optional<SessionKeyedMessage> getWeatherData() {
        try {
            return repositoryUtilities.getRowFromKeyedTable(weatherDataTable, weatherKey);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
