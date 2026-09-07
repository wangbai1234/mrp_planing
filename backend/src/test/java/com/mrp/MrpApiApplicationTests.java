package com.mrp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = MrpApiApplication.class)
@ActiveProfiles("test")
class MrpApiApplicationTests {

    @Test
    void contextLoads() {
    }
}
