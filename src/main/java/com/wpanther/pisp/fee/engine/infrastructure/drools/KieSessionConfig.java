package com.wpanther.pisp.fee.engine.infrastructure.drools;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KieSessionConfig {

    @Bean
    public KieContainer kieContainer() {
        return KieServices.Factory.get().getKieClasspathContainer();
    }
}
