package com.example.restcontrollerdemo.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;

class DevTokenIssuerTest {

    private static final JwtProperties PROPERTIES =
            new JwtProperties("x".repeat(32), "iss", "aud", Duration.ofMinutes(5));

    private static DevTokenIssuer issuer(String address) throws UnknownHostException {
        ServerProperties server = new ServerProperties();
        server.setAddress(address == null ? null : InetAddress.getByName(address));
        return new DevTokenIssuer(PROPERTIES, Clock.systemUTC(), server);
    }

    @Test
    void allInterfacesBindFailsStartup() {
        assertThatThrownBy(() -> issuer(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SERVER_ADDRESS");
    }

    @Test
    void nonLoopbackBindFailsStartup() {
        assertThatThrownBy(() -> issuer("0.0.0.0")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loopbackBindIsAccepted() {
        assertThatCode(() -> issuer("127.0.0.1")).doesNotThrowAnyException();
    }
}
