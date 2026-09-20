package com.registraai.registro_coristas_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class RelogioConfig {

    /**
     * Fuso fixo de Pernambuco: a idade (e por isso a faixa etária e as regras de menor) depende da data local.
     * Com o fuso do servidor (UTC no deploy), a partir das 21h em Recife "hoje" já seria o dia seguinte.
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Recife"));
    }
}
