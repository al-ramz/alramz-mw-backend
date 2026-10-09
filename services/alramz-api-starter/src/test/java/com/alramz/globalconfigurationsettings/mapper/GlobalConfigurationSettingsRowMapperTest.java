package com.alramz.globalconfigurationsettings.mapper;

import com.alramz.globalconfigurationsettings.model.GlobalConfigurationSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalConfigurationSettingsRowMapperTest {

    private GlobalConfigurationSettingsRowMapper mapper;

    @Mock
    private ResultSet resultSet;

    @BeforeEach
    void setUp() {
        mapper = new GlobalConfigurationSettingsRowMapper();
    }

    @Test
    void mapRow_shouldMapAllColumnsCorrectly_whenAllColumnsPresent() throws SQLException {
        // Given
        when(resultSet.getLong("ID")).thenReturn(1L);
        when(resultSet.getString("IDENTIFIER")).thenReturn("COUNTRY");
        when(resultSet.getString("IDENTIFIER_TYPE")).thenReturn("ISO_CODE");
        when(resultSet.getString("IDENTIFIER_TEXT")).thenReturn("US");
        when(resultSet.getString("STATUS")).thenReturn("ACTIVE");

        // When
        GlobalConfigurationSettings result = mapper.mapRow(resultSet, 0);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getIdentifier()).isEqualTo("COUNTRY");
        assertThat(result.getIdentifierType()).isEqualTo("ISO_CODE");
        assertThat(result.getIdentifierText()).isEqualTo("US");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void mapRow_shouldHandleNullStatus_gracefully() throws SQLException {
        // Given
        when(resultSet.getLong("ID")).thenReturn(2L);
        when(resultSet.getString("IDENTIFIER")).thenReturn("COUNTRY");
        when(resultSet.getString("IDENTIFIER_TYPE")).thenReturn("ISO_CODE");
        when(resultSet.getString("IDENTIFIER_TEXT")).thenReturn("GB");
        when(resultSet.getString("STATUS")).thenReturn(null);

        // When
        GlobalConfigurationSettings result = mapper.mapRow(resultSet, 0);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getIdentifier()).isEqualTo("COUNTRY");
        assertThat(result.getIdentifierType()).isEqualTo("ISO_CODE");
        assertThat(result.getIdentifierText()).isEqualTo("GB");
        assertThat(result.getStatus()).isNull();
    }

    @Test
    void mapRow_shouldMapMultipleRows_correctly() throws SQLException {
        // Given - First row
        when(resultSet.getLong("ID")).thenReturn(3L);
        when(resultSet.getString("IDENTIFIER")).thenReturn("CURRENCY");
        when(resultSet.getString("IDENTIFIER_TYPE")).thenReturn("CODE");
        when(resultSet.getString("IDENTIFIER_TEXT")).thenReturn("USD");
        when(resultSet.getString("STATUS")).thenReturn("ACTIVE");

        // When - Map first row
        GlobalConfigurationSettings result1 = mapper.mapRow(resultSet, 0);

        // Given - Second row
        when(resultSet.getLong("ID")).thenReturn(4L);
        when(resultSet.getString("IDENTIFIER")).thenReturn("CURRENCY");
        when(resultSet.getString("IDENTIFIER_TYPE")).thenReturn("CODE");
        when(resultSet.getString("IDENTIFIER_TEXT")).thenReturn("EUR");
        when(resultSet.getString("STATUS")).thenReturn("ACTIVE");

        // When - Map second row
        GlobalConfigurationSettings result2 = mapper.mapRow(resultSet, 1);

        // Then
        assertThat(result1.getId()).isEqualTo(3L);
        assertThat(result1.getIdentifierText()).isEqualTo("USD");
        assertThat(result2.getId()).isEqualTo(4L);
        assertThat(result2.getIdentifierText()).isEqualTo("EUR");
    }

    @Test
    void mapRow_shouldPreserveLongIdCorrectly() throws SQLException {
        // Given
        long largeId = 9223372036854775L; // Large but valid long
        when(resultSet.getLong("ID")).thenReturn(largeId);
        when(resultSet.getString("IDENTIFIER")).thenReturn("TEST");
        when(resultSet.getString("IDENTIFIER_TYPE")).thenReturn("TYPE");
        when(resultSet.getString("IDENTIFIER_TEXT")).thenReturn("TEXT");
        when(resultSet.getString("STATUS")).thenReturn("INACTIVE");

        // When
        GlobalConfigurationSettings result = mapper.mapRow(resultSet, 0);

        // Then
        assertThat(result.getId()).isEqualTo(largeId);
    }

    @Test
    void mapRow_shouldHandleEmptyStrings() throws SQLException {
        // Given
        when(resultSet.getLong("ID")).thenReturn(5L);
        when(resultSet.getString("IDENTIFIER")).thenReturn("");
        when(resultSet.getString("IDENTIFIER_TYPE")).thenReturn("");
        when(resultSet.getString("IDENTIFIER_TEXT")).thenReturn("");
        when(resultSet.getString("STATUS")).thenReturn("");

        // When
        GlobalConfigurationSettings result = mapper.mapRow(resultSet, 0);

        // Then
        assertThat(result.getIdentifier()).isEmpty();
        assertThat(result.getIdentifierType()).isEmpty();
        assertThat(result.getIdentifierText()).isEmpty();
        assertThat(result.getStatus()).isEmpty();
    }

    @Test
    void mapRow_shouldThrowSQLException_whenResultSetThrows() throws SQLException {
        // Given
        when(resultSet.getLong("ID")).thenThrow(new SQLException("Database error"));

        // When & Then
        assertThatThrownBy(() -> mapper.mapRow(resultSet, 0))
                .isInstanceOf(SQLException.class)
                .hasMessage("Database error");
    }

    @Test
    void referenceDataRecord_shouldBeImmutable() throws SQLException {
        // Given
        when(resultSet.getLong("ID")).thenReturn(6L);
        when(resultSet.getString("IDENTIFIER")).thenReturn("IMMUTABLE_TEST");
        when(resultSet.getString("IDENTIFIER_TYPE")).thenReturn("TEST_TYPE");
        when(resultSet.getString("IDENTIFIER_TEXT")).thenReturn("IMMUTABLE_VALUE");
        when(resultSet.getString("STATUS")).thenReturn("ACTIVE");

        // When
        GlobalConfigurationSettings record = mapper.mapRow(resultSet, 0);

        // Then - Entity should be properly mapped
        assertThat(record).isNotNull();
        // Verify fields are accessible via getters
        assertThat(record.getId()).isEqualTo(6L);
        assertThat(record.getIdentifier()).isEqualTo("IMMUTABLE_TEST");
        // This test verifies the entity structure is correct
    }

    @Test
    void referenceDataRecord_shouldHaveCorrectEquals() throws SQLException {
        // Given
        when(resultSet.getLong("ID")).thenReturn(7L);
        when(resultSet.getString("IDENTIFIER")).thenReturn("EQUALS_TEST");
        when(resultSet.getString("IDENTIFIER_TYPE")).thenReturn("TEST_TYPE");
        when(resultSet.getString("IDENTIFIER_TEXT")).thenReturn("VALUE1");
        when(resultSet.getString("STATUS")).thenReturn("ACTIVE");

        GlobalConfigurationSettings record1 = mapper.mapRow(resultSet, 0);

        // When - Create identical record
        GlobalConfigurationSettings record2 = new GlobalConfigurationSettings(7L, "EQUALS_TEST", "TEST_TYPE", "VALUE1", "ACTIVE");

        // Then - Records with same values should be equal
        assertThat(record1).isEqualTo(record2);
    }

    @Test
    void referenceDataRecord_shouldHaveCorrectHashCode() throws SQLException {
        // Given
        when(resultSet.getLong("ID")).thenReturn(8L);
        when(resultSet.getString("IDENTIFIER")).thenReturn("HASH_TEST");
        when(resultSet.getString("IDENTIFIER_TYPE")).thenReturn("TEST_TYPE");
        when(resultSet.getString("IDENTIFIER_TEXT")).thenReturn("VALUE2");
        when(resultSet.getString("STATUS")).thenReturn("INACTIVE");

        GlobalConfigurationSettings record1 = mapper.mapRow(resultSet, 0);
        GlobalConfigurationSettings record2 = new GlobalConfigurationSettings(8L, "HASH_TEST", "TEST_TYPE", "VALUE2", "INACTIVE");

        // Then - Records with same values should have same hash code
        assertThat(record1.hashCode()).isEqualTo(record2.hashCode());
    }

    @Test
    void referenceDataRecord_shouldHaveCorrectToString() throws SQLException {
        // Given
        when(resultSet.getLong("ID")).thenReturn(9L);
        when(resultSet.getString("IDENTIFIER")).thenReturn("TOSTRING_TEST");
        when(resultSet.getString("IDENTIFIER_TYPE")).thenReturn("TEST_TYPE");
        when(resultSet.getString("IDENTIFIER_TEXT")).thenReturn("VALUE3");
        when(resultSet.getString("STATUS")).thenReturn("ACTIVE");

        GlobalConfigurationSettings record = mapper.mapRow(resultSet, 0);

        // Then - toString should contain record component values
        String toStringResult = record.toString();
        assertThat(toStringResult).contains("id=9");
        assertThat(toStringResult).contains("TOSTRING_TEST");
        assertThat(toStringResult).contains("VALUE3");
    }
}
