package com.alramz.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** MMF / Athena integration settings; bound by {@link MmfConfiguration}. */
@Getter
@Setter
@ConfigurationProperties(prefix = MmfProperties.PREFIX)
public class MmfProperties {

    public static final String PREFIX = "mmf";

    private Custodian custodian = new Custodian();

    @Getter
    @Setter
    public static class Custodian {
        /** CL_CLIENT_IDs whose custodian positions Athena reconciles (was IS static data MMFATHENA_CUSTODIAN_POSITIONS). */
        private List<String> clientIds = new ArrayList<>();
    }
}
