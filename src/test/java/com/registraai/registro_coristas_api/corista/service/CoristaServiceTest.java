package com.registraai.registro_coristas_api.corista.service;

import com.registraai.registro_coristas_api.corista.dto.CoristaFiltro;
import com.registraai.registro_coristas_api.corista.dto.CoristaRequest;
import com.registraai.registro_coristas_api.corista.exception.CoristaNaoEncontradoException;
import com.registraai.registro_coristas_api.corista.exception.PromocaoInvalidaException;
import com.registraai.registro_coristas_api.corista.model.Corista;
import com.registraai.registro_coristas_api.corista.model.ListaClassificacao;
import com.registraai.registro_coristas_api.corista.model.TamanhoCamisa;
import com.registraai.registro_coristas_api.corista.model.TipoVoz;
import com.registraai.registro_coristas_api.corista.repository.CoristaRepository;
import com.registraai.registro_coristas_api.pessoa.dto.PessoaRequest;
import com.registraai.registro_coristas_api.pessoa.exception.ResponsavelLegalObrigatorioException;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.service.PessoaService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoristaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-20T15:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneId.of("America/Recife"));
    // hoje (no fuso de Recife) = 2026-09-20
    private static final LocalDate ADULTO = LocalDate.of(2000, 5, 17);
    private static final LocalDate MENOR = LocalDate.of(2010, 3, 1);

    @Mock
    private CoristaRepository coristaRepository;

    @Mock
    private PessoaService pessoaService;

    private CoristaService coristaService;

    private final PessoaRequest pessoaRequest =
            new PessoaRequest("Maria", ADULTO, UUID.randomUUID(), null, null, null, null);

    @BeforeEach
    void criarService() {
        coristaService = new CoristaService(coristaRepository, pessoaService, RELOGIO);
    }

    private Pessoa pessoa(LocalDate nascimento) {
        return Pessoa.builder().id(UUID.randomUUID()).nome("Maria").dataNascimento(nascimento).build();
    }

    private CoristaRequest request(TipoVoz tipoVoz, TamanhoCamisa tamanhoCamisa, String ocupacao) {
        return new CoristaRequest(pessoaRequest, tipoVoz, tamanhoCamisa, ocupacao);
    }

    /** Request válido quando o teste não se importa com os campos exclusivos de corista. */
    private CoristaRequest requestPadrao() {
        return request(TipoVoz.SOPRANO, TamanhoCamisa.M, null);
    }

    private void repositorioDevolveOQueRecebe() {
        when(coristaRepository.save(any(Corista.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    // ---------- criar ----------

    @Test
    void criar_adolescente_entraNaListaDeAdolescentesComCamposNormalizados() {
        Pessoa pessoa = pessoa(MENOR);
        when(pessoaService.criar(pessoaRequest)).thenReturn(pessoa);
        repositorioDevolveOQueRecebe();

        Corista corista = coristaService.criar(request(TipoVoz.SOPRANO, TamanhoCamisa.M, "  Estudante "));

        assertThat(corista.getPessoa()).isSameAs(pessoa);
        assertThat(corista.getListaClassificacao()).isEqualTo(ListaClassificacao.ADOLESCENTE);
        assertThat(corista.getTipoVoz()).isEqualTo(TipoVoz.SOPRANO);
        assertThat(corista.getTamanhoCamisa()).isEqualTo(TamanhoCamisa.M);
        assertThat(corista.getOcupacao()).isEqualTo("Estudante");
        assertThat(corista.getPromovidoPor()).isNull();
        assertThat(corista.getPromovidoEm()).isNull();
    }

    @Test
    void criar_jovem_entraNaListaDeJovens() {
        when(pessoaService.criar(pessoaRequest)).thenReturn(pessoa(ADULTO));
        repositorioDevolveOQueRecebe();

        assertThat(coristaService.criar(requestPadrao()).getListaClassificacao())
                .isEqualTo(ListaClassificacao.JOVEM);
    }

    @Test
    void criar_listaSegueAIdadeNaFronteiraDosDezoitoAnos() {
        repositorioDevolveOQueRecebe();

        // faltando 1 dia para os 18 anos (17a 11m 30d): ainda adolescente
        when(pessoaService.criar(pessoaRequest)).thenReturn(pessoa(LocalDate.of(2008, 9, 21)));
        assertThat(coristaService.criar(requestPadrao()).getListaClassificacao())
                .isEqualTo(ListaClassificacao.ADOLESCENTE);

        // completou 18 anos hoje: jovem
        when(pessoaService.criar(pessoaRequest)).thenReturn(pessoa(LocalDate.of(2008, 9, 20)));
        assertThat(coristaService.criar(requestPadrao()).getListaClassificacao())
                .isEqualTo(ListaClassificacao.JOVEM);
    }

    @Test
    void criar_naoGravaCoristaQuandoAPessoaEhRejeitada() {
        when(pessoaService.criar(pessoaRequest)).thenThrow(new ResponsavelLegalObrigatorioException());

        assertThatThrownBy(() -> coristaService.criar(requestPadrao()))
                .isInstanceOf(ResponsavelLegalObrigatorioException.class);
        verify(coristaRepository, never()).save(any());
    }

    // ---------- buscar / listar ----------

    @Test
    void buscarPorId_retornaCoristaExistente() {
        UUID id = UUID.randomUUID();
        Corista existente = Corista.builder().id(id).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(existente));

        assertThat(coristaService.buscarPorId(id)).isSameAs(existente);
    }

    @Test
    void buscarPorId_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(coristaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coristaService.buscarPorId(id)).isInstanceOf(CoristaNaoEncontradoException.class);
    }

    @Test
    void listar_paginaOrdenandoPorNomeComDesempatePorId() {
        Page<Corista> pagina = new PageImpl<>(List.of(Corista.builder().build()));
        Pageable esperado = PageRequest.of(2, 15, Sort.by("pessoa.nome").and(Sort.by("id")));
        when(coristaRepository.findAll(ArgumentMatchers.<Specification<Corista>>any(), eq(esperado))).thenReturn(pagina);

        assertThat(coristaService.listar(new CoristaFiltro(null, null, null, null, null), 2, 15)).isSameAs(pagina);
    }

    // ---------- atualizar ----------

    @Test
    void atualizar_naoPromovido_listaVoltaASeguirAIdade() {
        UUID id = UUID.randomUUID();
        Pessoa jaAdulta = pessoa(ADULTO);
        Corista existente = Corista.builder().id(id).pessoa(jaAdulta).listaClassificacao(ListaClassificacao.ADOLESCENTE).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(existente));
        when(pessoaService.atualizar(jaAdulta.getId(), pessoaRequest)).thenReturn(jaAdulta);
        repositorioDevolveOQueRecebe();

        Corista atualizado = coristaService.atualizar(id, request(TipoVoz.TENOR, TamanhoCamisa.G, "Professor"));

        assertThat(atualizado.getListaClassificacao()).isEqualTo(ListaClassificacao.JOVEM);
        assertThat(atualizado.getTipoVoz()).isEqualTo(TipoVoz.TENOR);
        assertThat(atualizado.getTamanhoCamisa()).isEqualTo(TamanhoCamisa.G);
        assertThat(atualizado.getOcupacao()).isEqualTo("Professor");
    }

    @Test
    void atualizar_promovido_mantemListaJovemMesmoSendoMenor() {
        UUID id = UUID.randomUUID();
        Pessoa menor = pessoa(MENOR);
        UUID lider = UUID.randomUUID();
        Corista promovido = Corista.builder().id(id).pessoa(menor).listaClassificacao(ListaClassificacao.JOVEM)
                .promovidoPor(lider).promovidoEm(AGORA).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(promovido));
        when(pessoaService.atualizar(menor.getId(), pessoaRequest)).thenReturn(menor);
        repositorioDevolveOQueRecebe();

        Corista atualizado = coristaService.atualizar(id, requestPadrao());

        assertThat(atualizado.getListaClassificacao()).isEqualTo(ListaClassificacao.JOVEM);
        assertThat(atualizado.getPromovidoPor()).isEqualTo(lider);
        assertThat(atualizado.getPromovidoEm()).isEqualTo(AGORA);
    }

    @Test
    void atualizar_ocupacaoEmBrancoViraNulaEVozECamisaSaoSubstituidas() {
        UUID id = UUID.randomUUID();
        Pessoa pessoa = pessoa(ADULTO);
        Corista existente = Corista.builder().id(id).pessoa(pessoa).tipoVoz(TipoVoz.SOPRANO).tamanhoCamisa(TamanhoCamisa.M)
                .ocupacao("Estudante").listaClassificacao(ListaClassificacao.JOVEM).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(existente));
        when(pessoaService.atualizar(pessoa.getId(), pessoaRequest)).thenReturn(pessoa);
        repositorioDevolveOQueRecebe();

        Corista atualizado = coristaService.atualizar(id, request(TipoVoz.BAIXO, TamanhoCamisa.XGG, "   "));

        assertThat(atualizado.getTipoVoz()).isEqualTo(TipoVoz.BAIXO);
        assertThat(atualizado.getTamanhoCamisa()).isEqualTo(TamanhoCamisa.XGG);
        assertThat(atualizado.getOcupacao()).isNull();
    }

    @Test
    void atualizar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(coristaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coristaService.atualizar(id, requestPadrao()))
                .isInstanceOf(CoristaNaoEncontradoException.class);
        verify(coristaRepository, never()).save(any());
    }

    // ---------- inativar / aprovar ----------

    @Test
    void inativar_delegaAInativacaoDaPessoa() {
        UUID id = UUID.randomUUID();
        Pessoa pessoa = pessoa(ADULTO);
        when(coristaRepository.findById(id)).thenReturn(Optional.of(Corista.builder().id(id).pessoa(pessoa).build()));

        coristaService.inativar(id);

        verify(pessoaService).inativar(pessoa.getId());
        verify(coristaRepository, never()).delete(any(Corista.class));
    }

    @Test
    void inativar_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(coristaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coristaService.inativar(id)).isInstanceOf(CoristaNaoEncontradoException.class);
    }

    @Test
    void aprovar_delegaAAprovacaoDaPessoaERetornaOCorista() {
        UUID id = UUID.randomUUID();
        UUID aprovador = UUID.randomUUID();
        Pessoa pessoa = pessoa(ADULTO);
        Corista corista = Corista.builder().id(id).pessoa(pessoa).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(corista));

        assertThat(coristaService.aprovar(id, aprovador)).isSameAs(corista);
        verify(pessoaService).aprovar(pessoa.getId(), aprovador);
    }

    // ---------- promover ----------

    @Test
    void promover_adolescenteViraJovemComTrilhaEContinuaMenor() {
        UUID id = UUID.randomUUID();
        UUID lider = UUID.randomUUID();
        Pessoa menor = pessoa(MENOR);
        Corista adolescente = Corista.builder().id(id).pessoa(menor).listaClassificacao(ListaClassificacao.ADOLESCENTE).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(adolescente));
        repositorioDevolveOQueRecebe();

        Corista promovido = coristaService.promover(id, lider);

        assertThat(promovido.getListaClassificacao()).isEqualTo(ListaClassificacao.JOVEM);
        assertThat(promovido.getPromovidoPor()).isEqualTo(lider);
        assertThat(promovido.getPromovidoEm()).isEqualTo(AGORA);
        // a promoção muda a lista, não a idade: a pessoa segue menor e com as proteções de LGPD
        assertThat(promovido.getPessoa().menorDeIdade(LocalDate.of(2026, 9, 20))).isTrue();
    }

    @Test
    void promover_quemJaEstaNaListaDeJovensLancaExcecao() {
        UUID id = UUID.randomUUID();
        Corista jovem = Corista.builder().id(id).pessoa(pessoa(ADULTO)).listaClassificacao(ListaClassificacao.JOVEM).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(jovem));

        assertThatThrownBy(() -> coristaService.promover(id, UUID.randomUUID()))
                .isInstanceOf(PromocaoInvalidaException.class);
        assertThat(jovem.getPromovidoEm()).isNull();
        verify(coristaRepository, never()).save(any());
    }

    @Test
    void promover_quemJaTem18AnosOuMaisNaoEPromocaoAntecipada() {
        UUID id = UUID.randomUUID();
        // lista ainda ADOLESCENTE mas a idade já é de jovem (lista desatualizada): não é promoção antecipada
        Corista desatualizado = Corista.builder().id(id).pessoa(pessoa(ADULTO)).listaClassificacao(ListaClassificacao.ADOLESCENTE).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(desatualizado));

        assertThatThrownBy(() -> coristaService.promover(id, UUID.randomUUID()))
                .isInstanceOf(PromocaoInvalidaException.class);
        assertThat(desatualizado.getPromovidoPor()).isNull();
        verify(coristaRepository, never()).save(any());
    }

    @Test
    void promover_lancaExcecaoQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(coristaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coristaService.promover(id, UUID.randomUUID()))
                .isInstanceOf(CoristaNaoEncontradoException.class);
    }
}
