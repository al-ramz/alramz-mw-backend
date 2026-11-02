package com.alramz.globalconfigurationsettings.service;

import com.alramz.globalconfigurationsettings.model.GlobalConfigurationSettings;
import com.alramz.globalconfigurationsettings.repository.GlobalConfigurationSettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GlobalConfigurationSettingsServiceTest {

    private final GlobalConfigurationSettingsRepository repository = mock(GlobalConfigurationSettingsRepository.class);
    private final GlobalConfigurationSettingsService service = new GlobalConfigurationSettingsService(repository);

    @Test
    void delegatesEveryLookupToRepository() {
        List<GlobalConfigurationSettings> rows = List.of(mock(GlobalConfigurationSettings.class));
        when(repository.find("ID", "T", "A")).thenReturn(rows);
        when(repository.find("ID")).thenReturn(rows);
        when(repository.find("ID", "T")).thenReturn(rows);
        when(repository.findIdentifierTexts("ID", "T", "A")).thenReturn(List.of("x"));
        when(repository.findIdentifierTexts("ID", null, null)).thenReturn(List.of("y"));
        when(repository.findAsMap("ID")).thenReturn(Map.of("T", List.of("x")));
        when(repository.updateIdentifierText("ID", "T", "txt", "A")).thenReturn(1);

        assertThat(service.find("ID", "T", "A")).isSameAs(rows);
        assertThat(service.find("ID")).isSameAs(rows);
        assertThat(service.find("ID", "T")).isSameAs(rows);
        assertThat(service.findIdentifierTexts("ID", "T", "A")).containsExactly("x");
        assertThat(service.findIdentifierTexts("ID")).containsExactly("y");
        assertThat(service.findAsMap("ID")).containsEntry("T", List.of("x"));
        assertThat(service.updateIdentifierText("ID", "T", "txt", "A")).isEqualTo(1);
    }

    @Test
    void rejectsNullOrBlankIdentifier() {
        assertThatThrownBy(() -> service.find(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("identifier cannot be null or blank");
        assertThatThrownBy(() -> service.find(" ", "T")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findAsMap("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateIdentifierText(null, "T", "x", "A"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }
}
