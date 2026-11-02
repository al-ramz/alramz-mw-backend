package com.alramz;

import com.alramz.globalconfigurationsettings.model.GlobalConfigurationSettings;
import com.alramz.globalconfigurationsettings.repository.GlobalConfigurationSettingsRepository;
import com.alramz.service.impl.NinTradingNumberValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises the startup ApplicationRunner beans directly, without booting a context. */
class OnboardingServiceApplicationRunnersTest {

    private final OnboardingServiceApplication application = new OnboardingServiceApplication();
    private final DefaultApplicationArguments args = new DefaultApplicationArguments();

    @Test
    void runner_shouldLogStartupBannerForActiveAndDefaultProfiles() {
        MockEnvironment withProfile = new MockEnvironment().withProperty("server.port", "9090");
        withProfile.setActiveProfiles("dev", "local");

        assertThatNoException().isThrownBy(() -> application.runner(withProfile).run(args));
        assertThatNoException().isThrownBy(() -> application.runner(new MockEnvironment()).run(args));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ninTradingCheckRunner_shouldCheckNinAndTradingNumberWhenServiceIsEnabled() throws Exception {
        ApplicationContext context = mock(ApplicationContext.class);
        ObjectProvider<NinTradingNumberValidationService> provider = mock(ObjectProvider.class);
        NinTradingNumberValidationService service = mock(NinTradingNumberValidationService.class);
        when(context.getBeanProvider(NinTradingNumberValidationService.class)).thenReturn(provider);
        when(provider.getIfAvailable()).thenReturn(service);

        application.ninTradingCheckRunner(context).run(args);

        verify(service).checkIfNinOrTradingNumberExists("DFM", "0006545043", null);
        verify(service).checkIfNinOrTradingNumberExists("DFM", null, "0047938213");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ninTradingCheckRunner_shouldSkipWhenServiceIsDisabled() {
        ApplicationContext context = mock(ApplicationContext.class);
        ObjectProvider<NinTradingNumberValidationService> provider = mock(ObjectProvider.class);
        when(context.getBeanProvider(NinTradingNumberValidationService.class)).thenReturn(provider);

        assertThatNoException().isThrownBy(() -> application.ninTradingCheckRunner(context).run(args));
        verify(provider).getIfAvailable();
    }

    @Test
    void globalConfigurationSettingsDemoRunner_shouldQueryCountriesCurrenciesAndMap() throws Exception {
        GlobalConfigurationSettingsRepository repository = mock(GlobalConfigurationSettingsRepository.class);
        when(repository.find(anyString(), any(), any()))
                .thenReturn(List.of(new GlobalConfigurationSettings(1L, "COUNTRY_US", "COUNTRY", "United States", "ACTIVE")));
        when(repository.findAsMap("CURRENCY_USD")).thenReturn(Map.of("CURRENCY_USD", List.of("US Dollar")));

        application.globalConfigurationSettingsDemoRunner(repository).run(args);

        for (String identifier : List.of("COUNTRY_US", "COUNTRY_GB", "COUNTRY_AE", "CURRENCY_USD", "CURRENCY_AED")) {
            verify(repository).find(identifier, null, null);
        }
        verify(repository).findAsMap("CURRENCY_USD");
    }

    @Test
    void globalConfigurationSettingsDemoRunner_shouldSwallowRepositoryFailure() {
        GlobalConfigurationSettingsRepository repository = mock(GlobalConfigurationSettingsRepository.class);
        when(repository.find(anyString(), any(), any())).thenThrow(new IllegalStateException("table missing"));

        assertThatNoException().isThrownBy(() -> application.globalConfigurationSettingsDemoRunner(repository).run(args));
        verify(repository, never()).findAsMap(anyString());
    }
}
