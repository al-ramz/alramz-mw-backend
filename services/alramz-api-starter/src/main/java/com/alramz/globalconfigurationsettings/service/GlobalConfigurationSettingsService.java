package com.alramz.globalconfigurationsettings.service;

import com.alramz.globalconfigurationsettings.model.GlobalConfigurationSettings;
import com.alramz.globalconfigurationsettings.repository.GlobalConfigurationSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class GlobalConfigurationSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(GlobalConfigurationSettingsService.class);

    private final GlobalConfigurationSettingsRepository repository;

    public GlobalConfigurationSettingsService(GlobalConfigurationSettingsRepository repository) {
        this.repository = repository;
        logger.info("[Bean: GlobalConfigurationSettingsService] - Successfully Created");
    }

    public List<GlobalConfigurationSettings> find(String identifier, String identifierType, String status) {
        validateIdentifier(identifier);
        return repository.find(identifier, identifierType, status);
    }

    public List<GlobalConfigurationSettings> find(String identifier) {
        validateIdentifier(identifier);
        return repository.find(identifier);
    }

    public List<GlobalConfigurationSettings> find(String identifier, String identifierType) {
        validateIdentifier(identifier);
        return repository.find(identifier, identifierType);
    }

    public List<String> findIdentifierTexts(String identifier, String identifierType, String status) {
        validateIdentifier(identifier);
        return repository.findIdentifierTexts(identifier, identifierType, status);
    }

    public List<String> findIdentifierTexts(String identifier) {
        validateIdentifier(identifier);
        return repository.findIdentifierTexts(identifier, null, null);
    }

    public Map<String, List<String>> findAsMap(String identifier) {
        validateIdentifier(identifier);
        return repository.findAsMap(identifier);
    }

    public int updateIdentifierText(String identifier, String identifierType, String identifierText, String status) {
        validateIdentifier(identifier);
        return repository.updateIdentifierText(identifier, identifierType, identifierText, status);
    }

    private void validateIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier cannot be null or blank");
        }
    }
}
