package com.mcc.mask;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SmokeTest {
    @Test
    void toolNameIsStable() {
        assertEquals("mask-tool", Main.toolName());
    }
}
