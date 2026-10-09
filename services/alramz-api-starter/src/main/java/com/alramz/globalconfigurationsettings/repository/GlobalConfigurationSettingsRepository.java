package com.alramz.globalconfigurationsettings.repository;

import com.alramz.globalconfigurationsettings.model.GlobalConfigurationSettings;
import java.util.List;
import java.util.Map;

/**
 * Repository interface for global configuration settings access.
 * All returned collections are immutable.
 */
public interface GlobalConfigurationSettingsRepository {

    /**
     * Find global configuration settings by identifier, identifier type, and status.
     *
     * @param identifier the identifier (required)
     * @param identifierType the identifier type (optional, null to ignore)
     * @param status the status (optional, null to ignore)
     * @return an immutable list of global configuration settings records
     */
    List<GlobalConfigurationSettings> find(String identifier, String identifierType, String status);

    /**
     * Find global configuration settings by identifier only.
     * Delegates to {@link #find(String, String, String)} with nulls.
     *
     * @param identifier the identifier (required)
     * @return an immutable list of global configuration settings records
     */
    default List<GlobalConfigurationSettings> find(String identifier) {
        return find(identifier, null, null);
    }

    /**
     * Find global configuration settings by identifier and identifier type.
     * Delegates to {@link #find(String, String, String)} with null status.
     *
     * @param identifier the identifier (required)
     * @param identifierType the identifier type (required)
     * @return an immutable list of global configuration settings records
     */
    default List<GlobalConfigurationSettings> find(String identifier, String identifierType) {
        return find(identifier, identifierType, null);
    }

    /**
     * Find identifier texts by identifier, identifier type, and status.
     *
     * @param identifier the identifier (required)
     * @param identifierType the identifier type (optional, null to ignore)
     * @param status the status (optional, null to ignore)
     * @return an immutable list of identifier text values
     */
    List<String> findIdentifierTexts(String identifier, String identifierType, String status);

    /**
     * Find global configuration settings as a map where the key is the identifier.
     * Since IDENTIFIER is unique, the map contains at most one entry.
     * The values are immutable lists of identifier texts.
     *
     * @param identifier the identifier (required)
     * @return an immutable map with identifier as key and immutable list of texts as value
     */
    Map<String, List<String>> findAsMap(String identifier);

    /**
     * Update identifier text for matching records.
     *
     * @param identifier the identifier (required)
     * @param identifierType the identifier type (required)
     * @param identifierText the new identifier text
     * @param status the status (required)
     * @return the number of rows affected
     */
    int updateIdentifierText(String identifier, String identifierType, String identifierText, String status);
}
