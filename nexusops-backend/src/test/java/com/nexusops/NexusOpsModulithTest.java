package com.nexusops;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class NexusOpsModulithTest {

    @Test
    void verifyModularArchitecture() {
        ApplicationModules modules = ApplicationModules.of(NexusOpsApplication.class);
        modules.verify();
    }
}

