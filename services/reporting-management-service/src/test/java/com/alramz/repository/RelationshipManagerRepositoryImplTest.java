package com.alramz.repository;

import com.alramz.exception.RelationshipManagerLookupException;
import com.alramz.model.CommissionResult;
import com.alramz.model.RelationshipManager;
import com.alramz.utils.SqlQueriesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationshipManagerRepositoryImplTest {

    private static final String FIND_ACTIVE_KEY = "relationship.manager.find.active";
    private static final String FIND_COMMISSIONS_KEY = "relationship.manager.commissions.find";
    private static final String COMMISSIONS_SQL = "SELECT X WHERE {dynamicWhereClause} AND Y";

    @Mock
    private NamedParameterJdbcTemplate brokNamedParameterJdbcTemplate;

    @Mock
    private SqlQueriesManager sqlQueriesManager;

    private RelationshipManagerRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new RelationshipManagerRepositoryImpl(brokNamedParameterJdbcTemplate, sqlQueriesManager);
    }

    @Test
    void findActiveRelationshipManagers_shouldLoadQueryKeyAndReturnMappedRows() throws IOException {
        String sql = "SELECT RM_NO, RM_NAME_EN, RM_NAME_AR FROM CB_RELATION_MANAGER WHERE NVL(RM_DISABLES,'N') != 'Y'";
        when(sqlQueriesManager.getSQLQueryFromConfig(FIND_ACTIVE_KEY)).thenReturn(sql);

        RelationshipManager rm = new RelationshipManager();
        rm.setId(101);
        rm.setNameEn("John Smith");
        rm.setNameAr("جون سميث");

        when(brokNamedParameterJdbcTemplate.query(eq(sql), any(RowMapper.class))).thenReturn(List.of(rm));

        List<RelationshipManager> result = repository.findActiveRelationshipManagers();

        assertThat(result).containsExactly(rm);
    }

    @Test
    void findActiveRelationshipManagers_rowMapper_shouldMapColumnsToFields() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig(FIND_ACTIVE_KEY)).thenReturn("SQL");

        ArgumentCaptor<RowMapper> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(brokNamedParameterJdbcTemplate.query(anyString(), captor.capture())).thenReturn(List.of());

        repository.findActiveRelationshipManagers();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("RM_NO")).thenReturn(101);
        when(rs.getString("RM_NAME_EN")).thenReturn("John Smith");
        when(rs.getString("RM_NAME_AR")).thenReturn("جون سميث");

        RelationshipManager mapped = (RelationshipManager) captor.getValue().mapRow(rs, 1);

        assertThat(mapped.getId()).isEqualTo(101);
        assertThat(mapped.getNameEn()).isEqualTo("John Smith");
        assertThat(mapped.getNameAr()).isEqualTo("جون سميث");
    }

    @Test
    void findActiveRelationshipManagers_shouldWrapIOExceptionAsLookupException() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig(FIND_ACTIVE_KEY)).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> repository.findActiveRelationshipManagers())
                .isInstanceOf(RelationshipManagerLookupException.class);
    }

    @Test
    void findActiveRelationshipManagers_shouldWrapDataAccessExceptionAsLookupException() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig(FIND_ACTIVE_KEY)).thenReturn("SQL");
        when(brokNamedParameterJdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> repository.findActiveRelationshipManagers())
                .isInstanceOf(RelationshipManagerLookupException.class);
    }

    @Test
    void findCommissionsByRelationshipManager_shouldLoadQueryAndBindDateAndCodeParams() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig(FIND_COMMISSIONS_KEY)).thenReturn(COMMISSIONS_SQL);

        CommissionResult cr = new CommissionResult();
        cr.setClientNumber("C-10293");
        cr.setClientName("Acme Trading LLC");
        cr.setTradingVolume(new BigDecimal("125000.50"));
        cr.setReceivedCommission(new BigDecimal("625.25"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        when(brokNamedParameterJdbcTemplate.query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class)))
                .thenReturn(List.of(cr));

        List<CommissionResult> results = repository.findCommissionsByRelationshipManager(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), List.of("3062", "3065"));

        assertThat(results).containsExactly(cr);
        assertThat(sqlCaptor.getValue())
                .isEqualTo("SELECT X WHERE (CL_RELATION_MANG IN (:relationshipManagerCodes)) AND Y");
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertThat(params.getValue("relationshipManagerCodes")).isEqualTo(List.of("3062", "3065"));
        assertThat(params.hasValue("islamicOfficeCode")).isFalse();
    }

    @Test
    void findCommissionsByRelationshipManager_shouldRestrictCode3031ToIslamic_forSoleCode3031() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig(FIND_COMMISSIONS_KEY)).thenReturn(COMMISSIONS_SQL);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        when(brokNamedParameterJdbcTemplate.query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.findCommissionsByRelationshipManager(LocalDate.now(), LocalDate.now(), List.of("3031"));

        assertThat(sqlCaptor.getValue()).isEqualTo(
                "SELECT X WHERE ((CL_RELATION_MANG = :islamicOfficeCode AND csc.SC_ISLAMIC = 'Y')) AND Y");
        assertThat(paramsCaptor.getValue().getValue("islamicOfficeCode")).isEqualTo("3031");
        assertThat(paramsCaptor.getValue().hasValue("relationshipManagerCodes")).isFalse();
    }

    @Test
    void findCommissionsByRelationshipManager_shouldOrIslamic3031WithOtherCodes_forCode3031PlusOtherCodes()
            throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig(FIND_COMMISSIONS_KEY)).thenReturn(COMMISSIONS_SQL);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        when(brokNamedParameterJdbcTemplate.query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.findCommissionsByRelationshipManager(
                LocalDate.now(), LocalDate.now(), List.of("3030", "3031", "3061"));

        assertThat(sqlCaptor.getValue()).isEqualTo("SELECT X WHERE ((CL_RELATION_MANG = :islamicOfficeCode"
                + " AND csc.SC_ISLAMIC = 'Y') OR CL_RELATION_MANG IN (:relationshipManagerCodes)) AND Y");
        assertThat(paramsCaptor.getValue().getValue("islamicOfficeCode")).isEqualTo("3031");
        assertThat(paramsCaptor.getValue().getValue("relationshipManagerCodes")).isEqualTo(List.of("3030", "3061"));
    }

    @Test
    void findCommissionsByRelationshipManager_shouldNotFilterByCode_whenCodesEmpty() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig(FIND_COMMISSIONS_KEY)).thenReturn(COMMISSIONS_SQL);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(brokNamedParameterJdbcTemplate.query(sqlCaptor.capture(), any(MapSqlParameterSource.class),
                any(RowMapper.class))).thenReturn(List.of());

        repository.findCommissionsByRelationshipManager(LocalDate.now(), LocalDate.now(), List.of());

        assertThat(sqlCaptor.getValue()).isEqualTo("SELECT X WHERE 1 = 1 AND Y");
    }

    @Test
    void findCommissionsByRelationshipManager_shouldNotFilterByCode_whenCodesNull() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig(FIND_COMMISSIONS_KEY)).thenReturn(COMMISSIONS_SQL);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(brokNamedParameterJdbcTemplate.query(sqlCaptor.capture(), any(MapSqlParameterSource.class),
                any(RowMapper.class))).thenReturn(List.of());

        repository.findCommissionsByRelationshipManager(LocalDate.now(), LocalDate.now(), null);

        assertThat(sqlCaptor.getValue()).isEqualTo("SELECT X WHERE 1 = 1 AND Y");
    }

    @Test
    void findCommissionsByRelationshipManager_rowMapper_shouldMapColumnsToFields() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig(FIND_COMMISSIONS_KEY)).thenReturn("SQL");
        ArgumentCaptor<RowMapper> mapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        when(brokNamedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), mapperCaptor.capture()))
                .thenReturn(List.of());

        repository.findCommissionsByRelationshipManager(LocalDate.now(), LocalDate.now(), null);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("CLIENT_NUMBER")).thenReturn("C-10293");
        when(rs.getString("CLIENT_NAME")).thenReturn("Acme Trading LLC");
        when(rs.getBigDecimal("TRADING_VOLUME")).thenReturn(new BigDecimal("125000.50"));
        when(rs.getBigDecimal("RECEIVED_COMMISSION")).thenReturn(new BigDecimal("625.25"));

        CommissionResult mapped = (CommissionResult) mapperCaptor.getValue().mapRow(rs, 1);

        assertThat(mapped.getClientNumber()).isEqualTo("C-10293");
        assertThat(mapped.getClientName()).isEqualTo("Acme Trading LLC");
        assertThat(mapped.getTradingVolume()).isEqualByComparingTo("125000.50");
        assertThat(mapped.getReceivedCommission()).isEqualByComparingTo("625.25");
    }

    @Test
    void findCommissionsByRelationshipManager_shouldWrapIOExceptionAsLookupException() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig(FIND_COMMISSIONS_KEY)).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> repository.findCommissionsByRelationshipManager(LocalDate.now(), LocalDate.now(), null))
                .isInstanceOf(RelationshipManagerLookupException.class);
    }

    @Test
    void findCommissionsByRelationshipManager_shouldWrapDataAccessExceptionAsLookupException() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig(FIND_COMMISSIONS_KEY)).thenReturn("SQL");
        when(brokNamedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> repository.findCommissionsByRelationshipManager(LocalDate.now(), LocalDate.now(), null))
                .isInstanceOf(RelationshipManagerLookupException.class);
    }
}
