package com.rewin.itsm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Fails if the entity mappings or the Spring wiring are broken. */
@SpringBootTest
@ActiveProfiles("test")
class ItsmApplicationTests {

    @Test
    void contextLoads() {
    }
}
