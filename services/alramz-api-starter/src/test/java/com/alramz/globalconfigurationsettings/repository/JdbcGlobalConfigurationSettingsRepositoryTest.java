package com.alramz.globalconfigurationsettings.repository;

import com.alramz.globalconfigurationsettings.mapper.GlobalConfigurationSettingsRowMapper;
import com.alramz.globalconfigurationsettings.model.GlobalConfigurationSettings;
import com.alramz.utils.SqlQueriesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JdbcGlobalConfigurationSettingsRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate mockJdbcTemplate;

    @Mock
    private GlobalConfigurationSettingsRowMapper mockRowMapper;

    @Mock
    private SqlQueriesManager mockSqlQueriesManager;

    private JdbcGlobalConfigurationSettingsRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcGlobalConfigurationSettingsRepository(
            mockJdbcTemplate,
            mockRowMapper,
            mockSqlQueriesManager
        );
    }

    // === Query Key Selection Tests ===

    @Test
    void find_withAllArgsNonBlank_selectsCorrectQueryKey() throws Exception {
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find.with.type.and.status"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), any(MapSqlParameterSource.class), eq(mockRowMapper)))
            .thenReturn(List.of());

        repository.find("COUNTRY", "ISO", "ACTIVE");
        verify(mockSqlQueriesManager).getSQLQueryFromConfig("global.configuration.settings.find.with.type.and.status");
    }

    @Test
    void find_withNullIdentifierType_selectsStatusQuery() throws Exception {
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find.with.status"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), any(MapSqlParameterSource.class), eq(mockRowMapper)))
            .thenReturn(List.of());

        repository.find("COUNTRY", null, "ACTIVE");
        verify(mockSqlQueriesManager).getSQLQueryFromConfig("global.configuration.settings.find.with.status");
    }

    @Test
    void find_withBlankIdentifierType_selectsStatusQuery() throws Exception {
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find.with.status"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), any(MapSqlParameterSource.class), eq(mockRowMapper)))
            .thenReturn(List.of());

        repository.find("COUNTRY", "  ", "ACTIVE");
        verify(mockSqlQueriesManager).getSQLQueryFromConfig("global.configuration.settings.find.with.status");
    }

    @Test
    void find_withNullStatus_selectsTypeQuery() throws Exception {
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find.with.type"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), any(MapSqlParameterSource.class), eq(mockRowMapper)))
            .thenReturn(List.of());

        repository.find("COUNTRY", "ISO", null);
        verify(mockSqlQueriesManager).getSQLQueryFromConfig("global.configuration.settings.find.with.type");
    }

    @Test
    void find_withBlankStatus_selectsTypeQuery() throws Exception {
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find.with.type"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), any(MapSqlParameterSource.class), eq(mockRowMapper)))
            .thenReturn(List.of());

        repository.find("COUNTRY", "ISO", "   ");
        verify(mockSqlQueriesManager).getSQLQueryFromConfig("global.configuration.settings.find.with.type");
    }

    @Test
    void find_withOnlyIdentifier_selectsBaseQuery() throws Exception {
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), any(MapSqlParameterSource.class), eq(mockRowMapper)))
            .thenReturn(List.of());

        repository.find("COUNTRY", null, null);
        verify(mockSqlQueriesManager).getSQLQueryFromConfig("global.configuration.settings.find");
    }

    @Test
    void find_withIdentifierOnly_delegatesToThreeArgMethod() throws Exception {
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), any(MapSqlParameterSource.class), eq(mockRowMapper)))
            .thenReturn(List.of());

        repository.find("COUNTRY");
        verify(mockSqlQueriesManager).getSQLQueryFromConfig("global.configuration.settings.find");
    }

    @Test
    void find_withIdentifierAndType_delegatesToThreeArgMethod() throws Exception {
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find.with.type"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), any(MapSqlParameterSource.class), eq(mockRowMapper)))
            .thenReturn(List.of());

        repository.find("COUNTRY", "ISO");
        verify(mockSqlQueriesManager).getSQLQueryFromConfig("global.configuration.settings.find.with.type");
    }

    @Test
    void findIdentifierTexts_withAllArgs_returnsTextList() throws Exception {
        String sql = "SELECT IDENTIFIER_TEXT FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        List<String> expectedTexts = List.of("US", "GB", "DE");

        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find.identifier.texts.with.type.and.status"))
            .thenReturn(sql);
        when(mockJdbcTemplate.queryForList(eq(sql), any(MapSqlParameterSource.class), eq(String.class)))
            .thenReturn(expectedTexts);

        List<String> result = repository.findIdentifierTexts("COUNTRY", "ISO", "ACTIVE");
        assertThat(result).containsExactly("US", "GB", "DE");
    }

    @Test
    void findIdentifierTexts_withNullType_usesCorrectQueryKey() throws Exception {
        String sql = "SELECT IDENTIFIER_TEXT FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find.identifier.texts.with.status"))
            .thenReturn(sql);
        when(mockJdbcTemplate.queryForList(eq(sql), any(MapSqlParameterSource.class), eq(String.class)))
            .thenReturn(List.of());

        repository.findIdentifierTexts("COUNTRY", null, "ACTIVE");
        verify(mockSqlQueriesManager).getSQLQueryFromConfig("global.configuration.settings.find.identifier.texts.with.status");
    }

    @Test
    void findIdentifierTexts_withNullStatus_usesCorrectQueryKey() throws Exception {
        String sql = "SELECT IDENTIFIER_TEXT FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find.identifier.texts.with.type"))
            .thenReturn(sql);
        when(mockJdbcTemplate.queryForList(eq(sql), any(MapSqlParameterSource.class), eq(String.class)))
            .thenReturn(List.of());

        repository.findIdentifierTexts("COUNTRY", "ISO", null);
        verify(mockSqlQueriesManager).getSQLQueryFromConfig("global.configuration.settings.find.identifier.texts.with.type");
    }

    @Test
    void findAsMap_returnsMapWithIdentifierAsKey() throws Exception {
        GlobalConfigurationSettings data1 = new GlobalConfigurationSettings(1L, "COUNTRY", "ISO", "US", "ACTIVE");
        GlobalConfigurationSettings data2 = new GlobalConfigurationSettings(2L, "COUNTRY", "ISO", "GB", "ACTIVE");

        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), any(MapSqlParameterSource.class), eq(mockRowMapper)))
            .thenReturn(Arrays.asList(data1, data2));

        Map<String, List<String>> result = repository.findAsMap("COUNTRY");
        assertThat(result)
            .hasSize(1)
            .containsKey("COUNTRY")
            .containsValue(List.of("US", "GB"));
    }

    @Test
    void findAsMap_returnsImmutableMap() throws Exception {
        GlobalConfigurationSettings data = new GlobalConfigurationSettings(1L, "COUNTRY", "ISO", "US", "ACTIVE");
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), any(MapSqlParameterSource.class), eq(mockRowMapper)))
            .thenReturn(List.of(data));

        Map<String, List<String>> result = repository.findAsMap("COUNTRY");
        assertThatThrownBy(() -> result.put("NEW_KEY", List.of("value")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findAsMap_withUniqueIdentifier_containsAtMostOneEntry() throws Exception {
        GlobalConfigurationSettings data = new GlobalConfigurationSettings(1L, "COUNTRY", "ISO", "US", "ACTIVE");
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), any(MapSqlParameterSource.class), eq(mockRowMapper)))
            .thenReturn(List.of(data));

        Map<String, List<String>> result = repository.findAsMap("COUNTRY");
        assertThat(result).hasSize(1);
    }

    @Test
    void updateIdentifierText_withStatus_usesCorrectQueryKey() throws Exception {
        String sql = "UPDATE GLOBAL_CONFIGURATION_SETTINGS SET ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.update"))
            .thenReturn(sql);
        when(mockJdbcTemplate.update(eq(sql), any(MapSqlParameterSource.class)))
            .thenReturn(1);

        int result = repository.updateIdentifierText("COUNTRY", "ISO", "USA", "ACTIVE");
        assertThat(result).isEqualTo(1);
    }

    @Test
    void updateIdentifierText_withoutStatus_usesCorrectQueryKey() throws Exception {
        String sql = "UPDATE GLOBAL_CONFIGURATION_SETTINGS SET ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.update.without.status"))
            .thenReturn(sql);
        when(mockJdbcTemplate.update(eq(sql), any(MapSqlParameterSource.class)))
            .thenReturn(1);

        int result = repository.updateIdentifierText("COUNTRY", "ISO", "USA", null);
        assertThat(result).isEqualTo(1);
    }

    @Test
    void updateIdentifierText_zeroRowsAffected_returnsZero() throws Exception {
        String sql = "UPDATE GLOBAL_CONFIGURATION_SETTINGS SET ...";
        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.update"))
            .thenReturn(sql);
        when(mockJdbcTemplate.update(eq(sql), any(MapSqlParameterSource.class)))
            .thenReturn(0);

        int result = repository.updateIdentifierText("NONEXISTENT", "ISO", "USA", "ACTIVE");
        assertThat(result).isEqualTo(0);
    }

    @Test
    void find_withDataAccessException_logsAndRethrows() throws Exception {
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        DataAccessException testException = new EmptyResultDataAccessException(1);

        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), any(MapSqlParameterSource.class), eq(mockRowMapper)))
            .thenThrow(testException);

        assertThatThrownBy(() -> repository.find("COUNTRY"))
            .isInstanceOf(DataAccessException.class);
    }

    @Test
    void find_withNullIdentifier_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> repository.find(null, "ISO", "ACTIVE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("identifier cannot be null or blank");

        verifyNoInteractions(mockJdbcTemplate, mockSqlQueriesManager);
    }

    @Test
    void find_withBlankIdentifier_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> repository.find("   ", "ISO", "ACTIVE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("identifier cannot be null or blank");

        verifyNoInteractions(mockJdbcTemplate, mockSqlQueriesManager);
    }

    @Test
    void find_returnsImmutableList() throws Exception {
        GlobalConfigurationSettings data = new GlobalConfigurationSettings(1L, "COUNTRY", "ISO", "US", "ACTIVE");
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";

        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), any(MapSqlParameterSource.class), eq(mockRowMapper)))
            .thenReturn(List.of(data));

        List<GlobalConfigurationSettings> result = repository.find("COUNTRY");
        assertThatThrownBy(() -> result.add(new GlobalConfigurationSettings(2L, "CITY", "ISO", "NYC", "ACTIVE")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findIdentifierTexts_returnsImmutableList() throws Exception {
        String sql = "SELECT IDENTIFIER_TEXT FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        List<String> texts = List.of("US", "GB");

        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find.identifier.texts"))
            .thenReturn(sql);
        when(mockJdbcTemplate.queryForList(eq(sql), any(MapSqlParameterSource.class), eq(String.class)))
            .thenReturn(texts);

        List<String> result = repository.findIdentifierTexts("COUNTRY", null, null);
        assertThatThrownBy(() -> result.add("FR"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findAsMap_valueListIsImmutable() throws Exception {
        GlobalConfigurationSettings data = new GlobalConfigurationSettings(1L, "COUNTRY", "ISO", "US", "ACTIVE");
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";

        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), any(MapSqlParameterSource.class), eq(mockRowMapper)))
            .thenReturn(List.of(data));

        Map<String, List<String>> result = repository.findAsMap("COUNTRY");
        List<String> valueList = result.get("COUNTRY");

        assertThatThrownBy(() -> valueList.add("GB"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void find_bindsAllParametersCorrectly() throws Exception {
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find.with.type.and.status"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), captor.capture(), eq(mockRowMapper)))
            .thenReturn(List.of());

        repository.find("COUNTRY", "ISO", "ACTIVE");

        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValues())
            .containsEntry("identifier", "COUNTRY")
            .containsEntry("identifierType", "ISO")
            .containsEntry("status", "ACTIVE");
    }

    @Test
    void find_doesNotBindNullParameters() throws Exception {
        String sql = "SELECT * FROM GLOBAL_CONFIGURATION_SETTINGS WHERE ...";
        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        when(mockSqlQueriesManager.getSQLQueryFromConfig("global.configuration.settings.find.with.status"))
            .thenReturn(sql);
        when(mockJdbcTemplate.query(eq(sql), captor.capture(), eq(mockRowMapper)))
            .thenReturn(List.of());

        repository.find("COUNTRY", null, "ACTIVE");

        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValues())
            .containsEntry("identifier", "COUNTRY")
            .containsEntry("status", "ACTIVE")
            .doesNotContainKey("identifierType");
    }
}
