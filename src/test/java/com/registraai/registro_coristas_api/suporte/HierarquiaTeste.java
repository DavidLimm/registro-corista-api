package com.registraai.registro_coristas_api.suporte;

import com.registraai.registro_coristas_api.agrupamento.model.Agrupamento;
import com.registraai.registro_coristas_api.agrupamento.model.Subdivisao;
import com.registraai.registro_coristas_api.agrupamento.model.TipoAgrupamento;
import com.registraai.registro_coristas_api.agrupamento.repository.AgrupamentoRepository;
import com.registraai.registro_coristas_api.agrupamento.repository.SubdivisaoRepository;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Monta a hierarquia (agrupamento → subdivisão) que toda congregação precisa nos testes. Como as 74 áreas já vêm
 * do seed, os testes que criam dados usam FILIAL com nome único para não colidir com elas nem entre si.
 */
public final class HierarquiaTeste {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger(1);

    private HierarquiaTeste() {
    }

    // ---------- em memória (testes unitários e de controller) ----------

    /** Área 40 (a do MVP), só em memória: sem persistir. */
    public static Agrupamento area40(UUID id) {
        Instant agora = Instant.parse("2026-10-11T12:00:00Z");
        return Agrupamento.builder().id(id).tipo(TipoAgrupamento.AREA).numero(40).criadoEm(agora).atualizadoEm(agora).build();
    }

    /** A subdivisão implícita da Área 40, só em memória. */
    public static Subdivisao subdivisaoDaArea40(UUID agrupamentoId, UUID subdivisaoId) {
        Instant agora = Instant.parse("2026-10-11T12:00:00Z");
        return Subdivisao.builder().id(subdivisaoId).agrupamento(area40(agrupamentoId)).criadoEm(agora).atualizadoEm(agora).build();
    }

    public static Subdivisao subdivisaoDaArea40() {
        return subdivisaoDaArea40(UUID.randomUUID(), UUID.randomUUID());
    }

    // ---------- com repositórios (testes @DataJpaTest) ----------

    /** Cria e salva uma filial de nome único com uma subdivisão (Área 1 da filial). */
    public static Subdivisao salvarSubdivisao(AgrupamentoRepository agrupamentoRepository, SubdivisaoRepository subdivisaoRepository) {
        Agrupamento filial = agrupamentoRepository.saveAndFlush(Agrupamento.builder()
                .tipo(TipoAgrupamento.FILIAL).nome("Filial de teste " + SEQUENCIA.getAndIncrement()).build());
        return subdivisaoRepository.saveAndFlush(Subdivisao.builder().agrupamento(filial).numero(1).build());
    }

    // ---------- pela API (testes de integração) ----------

    /** Cria uma filial de nome único e uma subdivisão nela; devolve o id da subdivisão. */
    public static String criarSubdivisao(MockMvc mockMvc) throws Exception {
        return criarSubdivisao(mockMvc, request -> request);
    }

    /** Igual, mas com quem faz a chamada (para testes sem usuário padrão na classe). */
    public static String criarSubdivisao(MockMvc mockMvc, RequestPostProcessor usuario) throws Exception {
        return criarSubdivisaoEm(mockMvc, usuario, criarFilial(mockMvc, usuario), 1);
    }

    /** @return o Location do agrupamento criado (ex.: {@code http://localhost/v1/api/agrupamentos/<id>}). */
    public static String criarFilial(MockMvc mockMvc, RequestPostProcessor usuario) throws Exception {
        return mockMvc.perform(post("/v1/api/agrupamentos").with(usuario).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tipo\": \"FILIAL\", \"nome\": \"Filial de teste %d\" }".formatted(SEQUENCIA.getAndIncrement())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    public static String criarFilial(MockMvc mockMvc) throws Exception {
        return criarFilial(mockMvc, request -> request);
    }

    /** @return o id da subdivisão criada no agrupamento cujo Location foi informado. */
    public static String criarSubdivisaoEm(MockMvc mockMvc, RequestPostProcessor usuario, String agrupamentoLocation, int numero)
            throws Exception {
        String location = mockMvc.perform(post(agrupamentoLocation + "/subdivisoes").with(usuario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"numero\": %d }".formatted(numero)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idDoLocation(location);
    }

    public static String criarSubdivisaoEm(MockMvc mockMvc, String agrupamentoLocation, int numero) throws Exception {
        return criarSubdivisaoEm(mockMvc, request -> request, agrupamentoLocation, numero);
    }

    public static String idDoLocation(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
