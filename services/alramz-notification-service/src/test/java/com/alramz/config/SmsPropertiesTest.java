package com.alramz.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsPropertiesTest {

    private static final SmsProperties.Waselney WASELNEY =
            new SmsProperties.Waselney("https://waselney.example.com/send", "https://waselney.example.com/report", "wuser", "wpass");
    private static final SmsProperties.Veriphone VERIPHONE = new SmsProperties.Veriphone("https://veriphone.example.com", "vkey");

    static SmsProperties properties(final boolean live, final String provider, final SmsProperties.Etisalat etisalat,
                                    final SmsProperties.Waselney waselney, final SmsProperties.Veriphone veriphone,
                                    final SmsProperties.Dispatch dispatch) {
        return new SmsProperties(live, provider, 50, 1500, Duration.ofSeconds(5), Duration.ofSeconds(10),
                new SmsProperties.Defaults("4.2", 300), etisalat, waselney, veriphone, dispatch);
    }

    private static SmsProperties.Dispatch dispatch(final Integer pending, final Integer inProgress, final Integer invalid) {
        return new SmsProperties.Dispatch(true, 50, new SmsProperties.Status(pending, inProgress, invalid));
    }

    @Test
    void bothFlagsOffChecksNothingAndDefaultsNestedBlocks() {
        final SmsProperties props = new SmsProperties(false, null, 50, 1500, null, null, null, null, null, null, null);

        assertThat(props.etisalatPreferred()).isFalse();
        assertThat(props.defaults().msgCategory()).isEqualTo("4.2");
        assertThat(props.defaults().expiryInSecs()).isEqualTo(300);
        assertThat(props.etisalat().excludedPrefixes()).isEmpty();
        assertThat(props.dispatch().enabled()).isFalse();
        assertThat(props.dispatch().status().pending()).isNull();
        assertThat(props.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void blankListEntriesFromEmptyEnvVarsAreDropped() {
        final SmsProperties.Etisalat etisalat =
                new SmsProperties.Etisalat("u", "n", "p", List.of(" ", "971"), List.of("", " AE "));

        assertThat(etisalat.excludedPrefixes()).containsExactly("971");
        assertThat(etisalat.excludedCountryCodes()).containsExactly("AE");
    }

    @Test
    void etisalatPreferredIsCaseInsensitive() {
        assertThat(properties(false, " etisalat ", null, null, null, null).etisalatPreferred()).isTrue();
        assertThat(properties(false, "WASELNEY", null, null, null, null).etisalatPreferred()).isFalse();
    }

    // ---- E45 live-send credentials ----

    @Test
    void e45_liveSendNeedsWaselneyCredentials() {
        final SmsProperties.Waselney waselney = new SmsProperties.Waselney("https://w", null, "u", " ");

        assertThatThrownBy(() -> properties(true, "WASELNEY", null, waselney, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.sms.waselney.password")
                .hasMessageNotContaining("app.sms.waselney.send-url");
    }

    @Test
    void e45_liveSendWithEtisalatPreferredNeedsEtisalatCredentials() {
        final SmsProperties.Etisalat etisalat = new SmsProperties.Etisalat("https://e", null, "p", List.of(), List.of());

        assertThatThrownBy(() -> properties(true, "ETISALAT", etisalat, WASELNEY, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.sms.etisalat.username");
    }

    @Test
    void e45_liveSendWithPrefixesNeedsVeriphoneKey() {
        final SmsProperties.Etisalat withPrefixes =
                new SmsProperties.Etisalat("https://e", "u", "p", List.of("1"), List.of("US"));

        final SmsProperties.Veriphone noKey = new SmsProperties.Veriphone("https://v", "");

        assertThatThrownBy(() -> properties(true, "ETISALAT", withPrefixes, WASELNEY, noKey, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.sms.veriphone.api-key");
    }

    @Test
    void e45_completeLiveConfigurationStarts() {
        assertThatCode(() -> properties(true, "ETISALAT",
                new SmsProperties.Etisalat("https://e", "u", "p", List.of("1"), List.of("US")), WASELNEY, VERIPHONE, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> properties(true, "WASELNEY", null, WASELNEY, null, null)).doesNotThrowAnyException();
    }

    // ---- E44 dispatch statuses ----

    @Test
    void e44_dispatchNeedsEveryStatus() {
        final SmsProperties.Dispatch dispatch = dispatch(10, null, 30);

        assertThatThrownBy(() -> properties(false, null, null, null, null, dispatch))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.sms.dispatch.status.in-progress");
    }

    @Test
    void e44_dispatchStatusesMustBeDistinct() {
        final SmsProperties.Dispatch pendingEqualsInProgress = dispatch(10, 10, 30);
        final SmsProperties.Dispatch pendingEqualsSent = dispatch(88, 20, 30);
        final SmsProperties.Dispatch invalidEqualsFailed = dispatch(10, 20, 1);

        assertThatThrownBy(() -> properties(false, null, null, null, null, pendingEqualsInProgress))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("distinct");
        assertThatThrownBy(() -> properties(false, null, null, null, null, pendingEqualsSent))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("distinct");
        assertThatThrownBy(() -> properties(false, null, null, null, null, invalidEqualsFailed))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("distinct");
    }

    @Test
    void e44_dispatchBatchSizeMustBePositive() {
        final SmsProperties.Dispatch zeroBatch = new SmsProperties.Dispatch(true, 0, new SmsProperties.Status(10, 20, 30));

        assertThatThrownBy(() -> properties(false, null, null, null, null, zeroBatch))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("batch-size");
    }

    @Test
    void e44_validDispatchStatusesStart() {
        final SmsProperties props = properties(false, null, null, null, null, dispatch(10, 20, 30));

        assertThat(props.dispatch().enabled()).isTrue();
        assertThat(props.dispatch().status().inProgress()).isEqualTo(20);
    }

    @Test
    void toStringRedactsSecrets() {
        final String text = properties(true, "ETISALAT",
                new SmsProperties.Etisalat("https://e", "u", "epass", List.of("1"), List.of("US")), WASELNEY, VERIPHONE, null)
                .toString();

        assertThat(text).doesNotContain("epass").doesNotContain("wpass").doesNotContain("vkey").contains("***");
    }
}
