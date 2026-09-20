package com.registraai.registro_coristas_api.pessoa.service;

import com.registraai.registro_coristas_api.area.model.Area;
import com.registraai.registro_coristas_api.congregacao.exception.CongregacaoInativaException;
import com.registraai.registro_coristas_api.congregacao.exception.CongregacaoNaoEncontradaException;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.congregacao.service.CongregacaoService;
import com.registraai.registro_coristas_api.endereco.dto.EnderecoRequest;
import com.registraai.registro_coristas_api.endereco.model.Endereco;
import com.registraai.registro_coristas_api.endereco.service.EnderecoService;
import com.registraai.registro_coristas_api.pessoa.dto.PessoaFiltro;
import com.registraai.registro_coristas_api.pessoa.dto.PessoaRequest;
import com.registraai.registro_coristas_api.pessoa.exception.ConsentimentoLgpdObrigatorioException;
import com.registraai.registro_coristas_api.pessoa.exception.PessoaNaoEncontradaException;
import com.registraai.registro_coristas_api.pessoa.exception.ResponsavelLegalIncompletoException;
import com.registraai.registro_coristas_api.pessoa.exception.ResponsavelLegalObrigatorioException;
import com.registraai.registro_coristas_api.pessoa.exception.TransicaoDeStatusInvalidaException;
import com.registraai.registro_coristas_api.pessoa.model.FaixaEtaria;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;
import com.registraai.registro_coristas_api.pessoa.repository.PessoaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PessoaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-20T15:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneId.of("America/Recife"));
    private static final LocalDate ADULTO = LocalDate.of(2000, 5, 17);
    private static final LocalDate MENOR = LocalDate.of(2010, 3, 1);

    @Mock
    private PessoaRepository pessoaRepository;

    @Mock
    private CongregacaoService congregacaoService;

    @Mock
    private EnderecoService enderecoService;

    private PessoaService pessoaService;

    private final UUID congregacaoId = UUID.randomUUID();
    private final Area area = Area.builder().id(UUID.randomUUID()).numero(40).nome("Área 40").build();
    private final Congregacao congregacao =
            Congregacao.builder().id(congregacaoId).area(area).nome("Sede").build();
    private final EnderecoRequest enderecoRequest =
            new EnderecoRequest("Rua da Aurora", "123", null, "Boa Vista", "Recife", "PE", "50050000");

    @BeforeEach
    void criarService() {
        pessoaService = new PessoaService(pessoaRepository, congregacaoService, enderecoService, RELOGIO);
    }

    private PessoaRequest request(LocalDate nascimento, EnderecoRequest endereco, String responsavelNome,
                                  String responsavelTelefone, Boolean consentimento) {
        return new PessoaRequest("  Maria da Silva ", nascimento, congregacaoId, endereco,
                responsavelNome, responsavelTelefone, consentimento);
    }

    private void congregacaoAtivaDisponivel() {
        when(congregacaoService.buscarPorId(congregacaoId)).thenReturn(congregacao);
    }

    private void repositorioDevolveOQueRecebe() {
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    // ---------- criar ----------

    @Test
    void criar_adulto_entraPendenteComDadosNormalizados() {
        congregacaoAtivaDisponivel();
        repositorioDevolveOQueRecebe();

        Pessoa pessoa = pessoaService.criar(request(ADULTO, null, null, null, null));

        assertThat(pessoa.getNome()).isEqualTo("Maria da Silva");
        assertThat(pessoa.getDataNascimento()).isEqualTo(ADULTO);
        assertThat(pessoa.getStatus()).isEqualTo(StatusPessoa.PENDENTE);
        assertThat(pessoa.getCongregacao()).isSameAs(congregacao);
        assertThat(pessoa.getEndereco()).isNull();
        assertThat(pessoa.getResponsavelLegalNome()).isNull();
        assertThat(pessoa.getConsentimentoLgpdEm()).isNull();
        assertThat(pessoa.getAprovadoPor()).isNull();
        verifyNoInteractions(enderecoService);
    }

    @Test
    void criar_comEnderecoGravaEVinculaViaEnderecoService() {
        Endereco endereco = Endereco.builder().id(UUID.randomUUID()).logradouro("Rua da Aurora").build();
        congregacaoAtivaDisponivel();
        when(enderecoService.criar(enderecoRequest)).thenReturn(endereco);
        repositorioDevolveOQueRecebe();

        Pessoa pessoa = pessoaService.criar(request(ADULTO, enderecoRequest, null, null, null));

        assertThat(pessoa.getEndereco()).isSameAs(endereco);
    }

    @Test
    void criar_adultoPodeTerResponsavelEConsentimentoOpcionais() {
        congregacaoAtivaDisponivel();
        repositorioDevolveOQueRecebe();

        Pessoa pessoa = pessoaService.criar(request(ADULTO, null, " José ", "(81) 3333-4444", true));

        assertThat(pessoa.getResponsavelLegalNome()).isEqualTo("José");
        assertThat(pessoa.getResponsavelLegalTelefone()).isEqualTo("8133334444");
        assertThat(pessoa.getConsentimentoLgpdEm()).isEqualTo(AGORA);
    }

    @Test
    void criar_menorComResponsavelEConsentimento_gravaTelefoneSoComDigitosEDataDoConsentimento() {
        congregacaoAtivaDisponivel();
        repositorioDevolveOQueRecebe();

        Pessoa pessoa = pessoaService.criar(request(MENOR, null, " José da Silva ", "(81) 99999-0000", true));

        assertThat(pessoa.getResponsavelLegalNome()).isEqualTo("José da Silva");
        assertThat(pessoa.getResponsavelLegalTelefone()).isEqualTo("81999990000");
        assertThat(pessoa.getConsentimentoLgpdEm()).isEqualTo(AGORA);
        assertThat(pessoa.getStatus()).isEqualTo(StatusPessoa.PENDENTE);
    }

    @Test
    void criar_menorSemResponsavel_lancaExcecaoENaoGravaNada() {
        congregacaoAtivaDisponivel();

        assertThatThrownBy(() -> pessoaService.criar(request(MENOR, enderecoRequest, null, null, true)))
                .isInstanceOf(ResponsavelLegalObrigatorioException.class);
        verify(pessoaRepository, never()).save(any());
        verifyNoInteractions(enderecoService);
    }

    @Test
    void criar_menorSemConsentimento_lancaExcecaoENaoGravaNada() {
        congregacaoAtivaDisponivel();

        assertThatThrownBy(() -> pessoaService.criar(request(MENOR, enderecoRequest, "José", "81999990000", null)))
                .isInstanceOf(ConsentimentoLgpdObrigatorioException.class);
        assertThatThrownBy(() -> pessoaService.criar(request(MENOR, enderecoRequest, "José", "81999990000", false)))
                .isInstanceOf(ConsentimentoLgpdObrigatorioException.class);
        verify(pessoaRepository, never()).save(any());
        verifyNoInteractions(enderecoService);
    }

    @Test
    void criar_responsavelComSoUmDosCampos_lancaExcecao() {
        congregacaoAtivaDisponivel();

        assertThatThrownBy(() -> pessoaService.criar(request(ADULTO, null, "José", null, null)))
                .isInstanceOf(ResponsavelLegalIncompletoException.class);
        assertThatThrownBy(() -> pessoaService.criar(request(ADULTO, null, "  ", "81999990000", null)))
                .isInstanceOf(ResponsavelLegalIncompletoException.class);
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void criar_lancaExcecaoQuandoCongregacaoEstaInativa() {
        Congregacao inativa = Congregacao.builder().id(congregacaoId).area(area).nome("Sede").ativa(false).build();
        when(congregacaoService.buscarPorId(congregacaoId)).thenReturn(inativa);

        assertThatThrownBy(() -> pessoaService.criar(request(ADULTO, null, null, null, null)))
                .isInstanceOf(CongregacaoInativaException.class);
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void criar_propagaExcecaoQuandoCongregacaoNaoExiste() {
        when(congregacaoService.buscarPorId(congregacaoId)).thenThrow(new CongregacaoNaoEncontradaException(congregacaoId));

        assertThatThrownBy(() -> pessoaService.criar(request(ADULTO, null, null, null, null)))
                .isInstanceOf(CongregacaoNaoEncontradaException.class);
        verify(pessoaRepository, never()).save(any());
    }

    // ---------- buscar ----------

    @Test
    void buscarPorId_retornaPessoaExistente() {
        UUID id = UUID.randomUUID();
        Pessoa existente = Pessoa.builder().id(id).build();
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(existente));

        assertThat(pessoaService.buscarPorId(id)).isSameAs(existente);
    }

    @Test
    void buscarPorId_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(pessoaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pessoaService.buscarPorId(id)).isInstanceOf(PessoaNaoEncontradaException.class);
    }

    // ---------- listar ----------

    @Test
    void listar_paginaOrdenandoPorNomeComDesempatePorId() {
        Page<Pessoa> pagina = new PageImpl<>(List.of(Pessoa.builder().build()));
        Pageable esperado = PageRequest.of(2, 15, Sort.by("nome").and(Sort.by("id")));
        when(pessoaRepository.findAll(ArgumentMatchers.<Specification<Pessoa>>any(), eq(esperado))).thenReturn(pagina);

        assertThat(pessoaService.listar(new PessoaFiltro(null, null, null, null, null), 2, 15)).isSameAs(pagina);
    }

    @Test
    void listar_comTodosOsFiltros_repassaAoRepositorioComAMesmaPaginacao() {
        Page<Pessoa> pagina = new PageImpl<>(List.of());
        Pageable esperado = PageRequest.of(0, 20, Sort.by("nome").and(Sort.by("id")));
        when(pessoaRepository.findAll(ArgumentMatchers.<Specification<Pessoa>>any(), eq(esperado))).thenReturn(pagina);
        PessoaFiltro filtro = new PessoaFiltro("  mar_ia ", UUID.randomUUID(), congregacaoId,
                FaixaEtaria.ADOLESCENTE, StatusPessoa.PENDENTE);

        assertThat(pessoaService.listar(filtro, 0, 20)).isSameAs(pagina);
    }

    // ---------- atualizar ----------

    private Pessoa pessoaAprovada(UUID id) {
        return Pessoa.builder()
                .id(id).nome("Antigo").dataNascimento(ADULTO).congregacao(congregacao)
                .status(StatusPessoa.APROVADO).aprovadoPor(UUID.randomUUID()).aprovadoEm(AGORA).build();
    }

    @Test
    void atualizar_mesmaCongregacao_alteraDadosSemMexerNoStatusNemNaTrilhaDeAprovacao() {
        UUID id = UUID.randomUUID();
        Pessoa existente = pessoaAprovada(id);
        UUID aprovador = existente.getAprovadoPor();
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(existente));
        repositorioDevolveOQueRecebe();

        Pessoa atualizada = pessoaService.atualizar(id, request(ADULTO, null, null, null, null));

        assertThat(atualizada.getNome()).isEqualTo("Maria da Silva");
        assertThat(atualizada.getStatus()).isEqualTo(StatusPessoa.APROVADO);
        assertThat(atualizada.getAprovadoPor()).isEqualTo(aprovador);
        assertThat(atualizada.getAprovadoEm()).isEqualTo(AGORA);
        verifyNoInteractions(congregacaoService);
    }

    @Test
    void atualizar_enderecoExistenteEAtualizadoNoLugar() {
        UUID id = UUID.randomUUID();
        UUID enderecoId = UUID.randomUUID();
        Pessoa existente = pessoaAprovada(id);
        existente.setEndereco(Endereco.builder().id(enderecoId).build());
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(existente));
        repositorioDevolveOQueRecebe();

        pessoaService.atualizar(id, request(ADULTO, enderecoRequest, null, null, null));

        verify(enderecoService).atualizar(enderecoId, enderecoRequest);
        verify(enderecoService, never()).criar(any());
    }

    @Test
    void atualizar_semEnderecoNoRequestMantemOAtual() {
        UUID id = UUID.randomUUID();
        Pessoa existente = pessoaAprovada(id);
        Endereco atual = Endereco.builder().id(UUID.randomUUID()).build();
        existente.setEndereco(atual);
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(existente));
        repositorioDevolveOQueRecebe();

        Pessoa atualizada = pessoaService.atualizar(id, request(ADULTO, null, null, null, null));

        assertThat(atualizada.getEndereco()).isSameAs(atual);
        verifyNoInteractions(enderecoService);
    }

    @Test
    void atualizar_pessoaSemEnderecoCriaEVinculaQuandoRequestTrazEndereco() {
        UUID id = UUID.randomUUID();
        Endereco novo = Endereco.builder().id(UUID.randomUUID()).build();
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(pessoaAprovada(id)));
        when(enderecoService.criar(enderecoRequest)).thenReturn(novo);
        repositorioDevolveOQueRecebe();

        Pessoa atualizada = pessoaService.atualizar(id, request(ADULTO, enderecoRequest, null, null, null));

        assertThat(atualizada.getEndereco()).isSameAs(novo);
    }

    @Test
    void atualizar_trocaDeCongregacaoExigeCongregacaoAtiva() {
        UUID id = UUID.randomUUID();
        UUID outraId = UUID.randomUUID();
        Pessoa existente = pessoaAprovada(id);
        Congregacao outraInativa = Congregacao.builder().id(outraId).area(area).nome("Filial").ativa(false).build();
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(existente));
        when(congregacaoService.buscarPorId(outraId)).thenReturn(outraInativa);
        var requestOutra = new PessoaRequest("Maria", ADULTO, outraId, null, null, null, null);

        assertThatThrownBy(() -> pessoaService.atualizar(id, requestOutra))
                .isInstanceOf(CongregacaoInativaException.class);
        assertThat(existente.getCongregacao()).isSameAs(congregacao);
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void atualizar_trocaDeCongregacaoParaUmaAtivaFunciona() {
        UUID id = UUID.randomUUID();
        UUID outraId = UUID.randomUUID();
        Congregacao outra = Congregacao.builder().id(outraId).area(area).nome("Filial").build();
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(pessoaAprovada(id)));
        when(congregacaoService.buscarPorId(outraId)).thenReturn(outra);
        repositorioDevolveOQueRecebe();

        Pessoa atualizada = pessoaService.atualizar(id, new PessoaRequest("Maria", ADULTO, outraId, null, null, null, null));

        assertThat(atualizada.getCongregacao()).isSameAs(outra);
    }

    @Test
    void atualizar_adultoQueViraMenorPorCorrecaoDaDataExigeResponsavel() {
        UUID id = UUID.randomUUID();
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(pessoaAprovada(id)));

        assertThatThrownBy(() -> pessoaService.atualizar(id, request(MENOR, null, null, null, true)))
                .isInstanceOf(ResponsavelLegalObrigatorioException.class);
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void atualizar_menorMantemConsentimentoJaDadoSemPrecisarReenviarNemRegravar() {
        UUID id = UUID.randomUUID();
        Instant consentimentoOriginal = Instant.parse("2026-01-01T00:00:00Z");
        Pessoa existente = pessoaAprovada(id);
        existente.setDataNascimento(MENOR);
        existente.setConsentimentoLgpdEm(consentimentoOriginal);
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(existente));
        repositorioDevolveOQueRecebe();

        Pessoa semReenviar = pessoaService.atualizar(id, request(MENOR, null, "José", "81999990000", null));
        assertThat(semReenviar.getConsentimentoLgpdEm()).isEqualTo(consentimentoOriginal);

        Pessoa reenviando = pessoaService.atualizar(id, request(MENOR, null, "José", "81999990000", true));
        assertThat(reenviando.getConsentimentoLgpdEm()).isEqualTo(consentimentoOriginal);
    }

    @Test
    void atualizar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(pessoaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pessoaService.atualizar(id, request(ADULTO, null, null, null, null)))
                .isInstanceOf(PessoaNaoEncontradaException.class);
    }

    // ---------- aprovar ----------

    @Test
    void aprovar_pendenteViraAprovadoComTrilha() {
        UUID id = UUID.randomUUID();
        UUID aprovador = UUID.randomUUID();
        Pessoa pendente = Pessoa.builder().id(id).build();
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(pendente));
        repositorioDevolveOQueRecebe();

        Pessoa aprovada = pessoaService.aprovar(id, aprovador);

        assertThat(aprovada.getStatus()).isEqualTo(StatusPessoa.APROVADO);
        assertThat(aprovada.getAprovadoPor()).isEqualTo(aprovador);
        assertThat(aprovada.getAprovadoEm()).isEqualTo(AGORA);
    }

    @Test
    void aprovar_foraDePendenteLancaExcecaoENaoAltera() {
        UUID id = UUID.randomUUID();
        Pessoa jaAprovada = pessoaAprovada(id);
        Pessoa inativa = Pessoa.builder().id(id).status(StatusPessoa.INATIVO).build();
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(jaAprovada), Optional.of(inativa));

        assertThatThrownBy(() -> pessoaService.aprovar(id, UUID.randomUUID()))
                .isInstanceOf(TransicaoDeStatusInvalidaException.class);
        assertThatThrownBy(() -> pessoaService.aprovar(id, UUID.randomUUID()))
                .isInstanceOf(TransicaoDeStatusInvalidaException.class);
        assertThat(inativa.getStatus()).isEqualTo(StatusPessoa.INATIVO);
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void aprovar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(pessoaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pessoaService.aprovar(id, UUID.randomUUID()))
                .isInstanceOf(PessoaNaoEncontradaException.class);
    }

    // ---------- inativar ----------

    @Test
    void inativar_marcaComoInativoSemRemoverDoBanco() {
        UUID id = UUID.randomUUID();
        Pessoa existente = pessoaAprovada(id);
        when(pessoaRepository.findById(id)).thenReturn(Optional.of(existente));

        pessoaService.inativar(id);

        assertThat(existente.getStatus()).isEqualTo(StatusPessoa.INATIVO);
        verify(pessoaRepository).save(existente);
        verify(pessoaRepository, never()).delete(any(Pessoa.class));
        verify(pessoaRepository, never()).deleteById(any());
    }

    @Test
    void inativar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(pessoaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pessoaService.inativar(id)).isInstanceOf(PessoaNaoEncontradaException.class);
    }
}
