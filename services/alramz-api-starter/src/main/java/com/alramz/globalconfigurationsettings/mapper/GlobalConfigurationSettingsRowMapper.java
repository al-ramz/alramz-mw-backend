package com.alramz.globalconfigurationsettings.mapper;

import com.alramz.globalconfigurationsettings.model.GlobalConfigurationSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class GlobalConfigurationSettingsRowMapper implements RowMapper<GlobalConfigurationSettings> {

    private static final Logger logger = LoggerFactory.getLogger(GlobalConfigurationSettingsRowMapper.class);

    public GlobalConfigurationSettingsRowMapper() {
        logger.info("[Bean: GlobalConfigurationSettingsRowMapper] - Successfully Created");
    }

    @Override
    public GlobalConfigurationSettings mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new GlobalConfigurationSettings(
            rs.getLong("ID"),
            rs.getString("IDENTIFIER"),
            rs.getString("IDENTIFIER_TYPE"),
            rs.getString("IDENTIFIER_TEXT"),
            rs.getString("STATUS")
        );
    }
}
