package com.registraai.registro_coristas_api.corista.service;

import com.registraai.registro_coristas_api.corista.model.MotivoDesligamento;
import com.registraai.registro_coristas_api.autenticacao.service.UsuarioLogado;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.corista.dto.CoristaFiltro;
import com.registraai.registro_coristas_api.corista.dto.CoristaRequest;
import com.registraai.registro_coristas_api.corista.exception.CoristaNaoEncontradoException;
import com.registraai.registro_coristas_api.corista.exception.CorrecaoDeNascimentoNegadaException;
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
import com.registraai.registro_coristas_api.role.model.Role;
import com.registraai.registro_coristas_api.usuario.dto.UsuarioCredenciaisRequest;
import com.registraai.registro_coristas_api.usuario.model.Usuario;
import com.registraai.registro_coristas_api.usuario.service.UsuarioService;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private UsuarioLogado usuarioLogado;

    private CoristaService coristaService;

    private final PessoaRequest pessoaRequest =
            new PessoaRequest("Maria", ADULTO, UUID.randomUUID(), null, null, null, null);

    @BeforeEach
    void criarService() {
        coristaService = new CoristaService(coristaRepository, pessoaService, usuarioService, usuarioLogado, RELOGIO);
        // por padrão quem chama é um ADMIN (as duas listas, e pode corrigir nascimento); os testes de recorte trocam isso
        Usuario admin = usuario("ADMIN");
        lenient().when(usuarioLogado.atual()).thenReturn(Optional.of(admin));
        lenient().when(usuarioLogado.temPermissao(any())).thenReturn(true);
    }

    private Usuario usuario(String... papeis) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setPessoa(Pessoa.builder().id(UUID.randomUUID()).nome("Logado").build());
        usuario.setRoles(Arrays.stream(papeis)
                .map(nome -> Role.builder().id(UUID.randomUUID()).nome(nome).build())
                .collect(Collectors.toSet()));
        return usuario;
    }

    private Pessoa pessoa(LocalDate nascimento) {
        return Pessoa.builder().id(UUID.randomUUID()).nome("Maria").dataNascimento(nascimento).build();
    }

    private CoristaRequest request(TipoVoz tipoVoz, TamanhoCamisa tamanhoCamisa, String ocupacao) {
        return new CoristaRequest(pessoaRequest, tipoVoz, tamanhoCamisa, ocupacao, null);
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

    @Test
    void criar_semUsuario_naoChamaUsuarioService() {
        when(pessoaService.criar(pessoaRequest)).thenReturn(pessoa(ADULTO));
        repositorioDevolveOQueRecebe();

        coristaService.criar(requestPadrao());

        verify(usuarioService, never()).criarComPapelUnico(any(), any(), any(), any());
    }

    @Test
    void criar_comUsuarioEAdolescente_criaLoginComPapelCoristaAdolescentes() {
        Pessoa pessoa = pessoa(MENOR);
        when(pessoaService.criar(pessoaRequest)).thenReturn(pessoa);
        repositorioDevolveOQueRecebe();
        CoristaRequest request = new CoristaRequest(pessoaRequest, TipoVoz.SOPRANO, TamanhoCamisa.M, null,
                new UsuarioCredenciaisRequest("ana@exemplo.com", "SenhaForte@123"));

        coristaService.criar(request);

        verify(usuarioService).criarComPapelUnico(pessoa, "ana@exemplo.com", "SenhaForte@123", "CORISTA_ADOLESCENTES");
    }

    @Test
    void criar_comUsuarioEJovem_criaLoginComPapelCoristaJovens() {
        Pessoa pessoa = pessoa(ADULTO);
        when(pessoaService.criar(pessoaRequest)).thenReturn(pessoa);
        repositorioDevolveOQueRecebe();
        CoristaRequest request = new CoristaRequest(pessoaRequest, TipoVoz.SOPRANO, TamanhoCamisa.M, null,
                new UsuarioCredenciaisRequest("bia@exemplo.com", "SenhaForte@123"));

        coristaService.criar(request);

        verify(usuarioService).criarComPapelUnico(pessoa, "bia@exemplo.com", "SenhaForte@123", "CORISTA_JOVENS");
    }

    // ---------- buscar / listar ----------

    @Test
    void buscarPorId_retornaCoristaExistente() {
        UUID id = UUID.randomUUID();
        Corista existente = Corista.builder().id(id).pessoa(pessoa(ADULTO)).listaClassificacao(ListaClassificacao.JOVEM).build();
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

        assertThat(coristaService.listar(new CoristaFiltro(null, null, null, null, null, null), 2, 15)).isSameAs(pagina);
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
        when(coristaRepository.findById(id)).thenReturn(Optional.of(Corista.builder().id(id).pessoa(pessoa).listaClassificacao(ListaClassificacao.JOVEM).build()));

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
        Corista corista = Corista.builder().id(id).pessoa(pessoa).listaClassificacao(ListaClassificacao.JOVEM).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(corista));

        assertThat(coristaService.aprovar(id, aprovador)).isSameAs(corista);
        verify(pessoaService).aprovar(pessoa.getId(), aprovador);
    }

    @Test
    void reprovar_delegaAReprovacaoDaPessoaERetornaOCorista() {
        UUID id = UUID.randomUUID();
        UUID reprovador = UUID.randomUUID();
        Pessoa pessoa = pessoa(ADULTO);
        Corista corista = Corista.builder().id(id).pessoa(pessoa).listaClassificacao(ListaClassificacao.JOVEM).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(corista));

        assertThat(coristaService.reprovar(id, reprovador)).isSameAs(corista);
        verify(pessoaService).reprovar(pessoa.getId(), reprovador);
    }

    @Test
    void afastar_delegaAoAfastamentoDaPessoaERetornaOCorista() {
        UUID id = UUID.randomUUID();
        UUID lider = UUID.randomUUID();
        Pessoa pessoa = pessoa(ADULTO);
        Corista corista = Corista.builder().id(id).pessoa(pessoa).listaClassificacao(ListaClassificacao.JOVEM).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(corista));

        assertThat(coristaService.afastar(id, lider)).isSameAs(corista);
        verify(pessoaService).afastar(pessoa.getId(), lider);
    }

    @Test
    void retornarDoAfastamento_delegaAoRetornoDaPessoaERetornaOCorista() {
        UUID id = UUID.randomUUID();
        UUID lider = UUID.randomUUID();
        Pessoa pessoa = pessoa(ADULTO);
        Corista corista = Corista.builder().id(id).pessoa(pessoa).listaClassificacao(ListaClassificacao.JOVEM).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(corista));

        assertThat(coristaService.retornarDoAfastamento(id, lider)).isSameAs(corista);
        verify(pessoaService).retornarDoAfastamento(pessoa.getId(), lider);
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

    // ---------- recorte de acesso, próprio cadastro e nascimento ----------

    private Usuario usuarioDe(Pessoa pessoaDoUsuario, String... papeis) {
        Usuario usuario = usuario(papeis);
        usuario.setPessoa(pessoaDoUsuario);
        return usuario;
    }

    private void logadoComo(Usuario usuario) {
        when(usuarioLogado.atual()).thenReturn(Optional.of(usuario));
    }

    private Corista coristaDaLista(UUID id, ListaClassificacao lista) {
        return Corista.builder().id(id).pessoa(pessoa(ADULTO)).listaClassificacao(lista).build();
    }

    @Test
    void buscarPorId_maestroDeJovens_naoEnxergaCoristaDeAdolescentes() {
        UUID id = UUID.randomUUID();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(coristaDaLista(id, ListaClassificacao.ADOLESCENTE)));
        logadoComo(usuario("MAESTRO_JOVENS"));

        assertThatThrownBy(() -> coristaService.buscarPorId(id)).isInstanceOf(CoristaNaoEncontradoException.class);
    }

    @Test
    void buscarPorId_maestroDeJovens_enxergaCoristaDeJovens() {
        UUID id = UUID.randomUUID();
        Corista jovem = coristaDaLista(id, ListaClassificacao.JOVEM);
        when(coristaRepository.findById(id)).thenReturn(Optional.of(jovem));
        logadoComo(usuario("MAESTRO_JOVENS"));

        assertThat(coristaService.buscarPorId(id)).isSameAs(jovem);
    }

    @Test
    void buscarPorId_semUsuarioLogado_naoEnxergaNinguem() {
        UUID id = UUID.randomUUID();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(coristaDaLista(id, ListaClassificacao.JOVEM)));
        when(usuarioLogado.atual()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coristaService.buscarPorId(id)).isInstanceOf(CoristaNaoEncontradoException.class);
    }

    @Test
    void buscarPorId_papelDesconhecido_naoEnxergaNinguem() {
        UUID id = UUID.randomUUID();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(coristaDaLista(id, ListaClassificacao.JOVEM)));
        logadoComo(usuario("PAPEL_CRIADO_DEPOIS"));

        assertThatThrownBy(() -> coristaService.buscarPorId(id)).isInstanceOf(CoristaNaoEncontradoException.class);
    }

    @Test
    void buscarPorId_corista_enxergaSoOProprioCadastro() {
        UUID id = UUID.randomUUID();
        Corista proprio = coristaDaLista(id, ListaClassificacao.JOVEM);
        UUID outroId = UUID.randomUUID();
        Corista outro = coristaDaLista(outroId, ListaClassificacao.JOVEM);
        when(coristaRepository.findById(id)).thenReturn(Optional.of(proprio));
        when(coristaRepository.findById(outroId)).thenReturn(Optional.of(outro));
        logadoComo(usuarioDe(proprio.getPessoa(), "CORISTA_JOVENS"));

        assertThat(coristaService.buscarPorId(id)).isSameAs(proprio);
        assertThatThrownBy(() -> coristaService.buscarPorId(outroId)).isInstanceOf(CoristaNaoEncontradoException.class);
    }

    @Test
    void buscarPorId_quemVirouLider_perdeAcessoAoCadastroAntigoDeCorista() {
        UUID id = UUID.randomUUID();
        Corista antigo = coristaDaLista(id, ListaClassificacao.ADOLESCENTE);
        when(coristaRepository.findById(id)).thenReturn(Optional.of(antigo));
        // mesma pessoa, mas agora só com papel de líder de jovens: nem o recorte (jovens) nem o "próprio" valem
        logadoComo(usuarioDe(antigo.getPessoa(), "LIDER_JOVENS"));

        assertThatThrownBy(() -> coristaService.buscarPorId(id)).isInstanceOf(CoristaNaoEncontradoException.class);
    }

    @Test
    void buscarPorId_presbitero_soEnxergaACongregacaoDele() {
        UUID id = UUID.randomUUID();
        Congregacao daIgreja = Congregacao.builder().id(UUID.randomUUID()).nome("Sede").build();
        Congregacao deOutra = Congregacao.builder().id(UUID.randomUUID()).nome("Outra").build();
        Corista nosso = coristaDaLista(id, ListaClassificacao.JOVEM);
        nosso.getPessoa().setCongregacao(daIgreja);
        UUID idDeFora = UUID.randomUUID();
        Corista deFora = coristaDaLista(idDeFora, ListaClassificacao.JOVEM);
        deFora.getPessoa().setCongregacao(deOutra);
        when(coristaRepository.findById(id)).thenReturn(Optional.of(nosso));
        when(coristaRepository.findById(idDeFora)).thenReturn(Optional.of(deFora));
        Pessoa presbitero = pessoa(ADULTO);
        presbitero.setCongregacao(daIgreja);
        logadoComo(usuarioDe(presbitero, "PRESBITERO"));

        assertThat(coristaService.buscarPorId(id)).isSameAs(nosso);
        assertThatThrownBy(() -> coristaService.buscarPorId(idDeFora)).isInstanceOf(CoristaNaoEncontradoException.class);
    }

    @Test
    void aprovar_dirigenteDaUniao_soAprovaAdolescentes() {
        UUID idJovem = UUID.randomUUID();
        UUID idAdolescente = UUID.randomUUID();
        Corista jovem = coristaDaLista(idJovem, ListaClassificacao.JOVEM);
        Corista adolescente = coristaDaLista(idAdolescente, ListaClassificacao.ADOLESCENTE);
        when(coristaRepository.findById(idJovem)).thenReturn(Optional.of(jovem));
        when(coristaRepository.findById(idAdolescente)).thenReturn(Optional.of(adolescente));
        logadoComo(usuario("DIRIGENTE_UNIAO"));
        UUID dirigente = UUID.randomUUID();

        assertThatThrownBy(() -> coristaService.aprovar(idJovem, dirigente)).isInstanceOf(CoristaNaoEncontradoException.class);
        verify(pessoaService, never()).aprovar(any(), any());

        coristaService.aprovar(idAdolescente, dirigente);
        verify(pessoaService).aprovar(adolescente.getPessoa().getId(), dirigente);
    }

    @Test
    void listar_semRecorte_devolvePaginaVaziaSemConsultarOBanco() {
        logadoComo(usuario("CORISTA_JOVENS"));

        Page<Corista> pagina = coristaService.listar(new CoristaFiltro(null, null, null, null, null, null), 0, 20);

        assertThat(pagina.getContent()).isEmpty();
        verify(coristaRepository, never()).findAll(ArgumentMatchers.<Specification<Corista>>any(), any(Pageable.class));
    }

    @Test
    void listar_comRecorte_consultaOBanco() {
        logadoComo(usuario("APOIO_ADOLESCENTES"));
        when(coristaRepository.findAll(ArgumentMatchers.<Specification<Corista>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        coristaService.listar(new CoristaFiltro(null, null, null, null, null, null), 0, 20);

        verify(coristaRepository).findAll(ArgumentMatchers.<Specification<Corista>>any(), any(Pageable.class));
    }

    @Test
    void atualizar_mudandoANascimentoSemPermissao_negaENaoGrava() {
        UUID id = UUID.randomUUID();
        Pessoa pessoa = pessoa(MENOR);
        Corista existente = Corista.builder().id(id).pessoa(pessoa).listaClassificacao(ListaClassificacao.ADOLESCENTE).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(existente));
        logadoComo(usuarioDe(pessoa, "CORISTA_ADOLESCENTES"));
        when(usuarioLogado.temPermissao("CORISTA_CORRIGIR_NASCIMENTO")).thenReturn(false);

        // pessoaRequest traz o nascimento ADULTO, diferente do MENOR gravado: o corista tentou "ficar mais velho"
        assertThatThrownBy(() -> coristaService.atualizar(id, requestPadrao()))
                .isInstanceOf(CorrecaoDeNascimentoNegadaException.class);
        verify(pessoaService, never()).atualizar(any(), any());
        verify(coristaRepository, never()).save(any());
    }

    @Test
    void atualizar_mantendoANascimentoSemPermissao_permite() {
        UUID id = UUID.randomUUID();
        Pessoa pessoa = pessoa(ADULTO);
        Corista existente = Corista.builder().id(id).pessoa(pessoa).listaClassificacao(ListaClassificacao.JOVEM).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(existente));
        when(pessoaService.atualizar(pessoa.getId(), pessoaRequest)).thenReturn(pessoa);
        repositorioDevolveOQueRecebe();
        logadoComo(usuarioDe(pessoa, "CORISTA_JOVENS"));
        lenient().when(usuarioLogado.temPermissao("CORISTA_CORRIGIR_NASCIMENTO")).thenReturn(false);

        assertThat(coristaService.atualizar(id, requestPadrao()).getPessoa()).isSameAs(pessoa);
    }

    @Test
    void atualizar_lider_corrigeANascimentoELista_voltaAAcompanharAIdade() {
        UUID id = UUID.randomUUID();
        Pessoa pessoa = pessoa(ADULTO);
        // mentiu a idade e entrou nos jovens (sem promoção); o líder corrige para a data de menor
        Corista existente = Corista.builder().id(id).pessoa(pessoa).listaClassificacao(ListaClassificacao.JOVEM).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(existente));
        Pessoa corrigida = pessoa(MENOR);
        repositorioDevolveOQueRecebe();
        logadoComo(usuario("LIDER_MOCIDADE"));
        when(usuarioLogado.temPermissao("CORISTA_CORRIGIR_NASCIMENTO")).thenReturn(true);

        // data diferente da gravada (ADULTO): é a correção que o líder faz
        CoristaRequest comNovaData = new CoristaRequest(
                new PessoaRequest("Maria", MENOR, UUID.randomUUID(), null, null, null, null),
                TipoVoz.SOPRANO, TamanhoCamisa.M, null, null);
        when(pessoaService.atualizar(pessoa.getId(), comNovaData.pessoa())).thenReturn(corrigida);

        assertThat(coristaService.atualizar(id, comNovaData).getListaClassificacao())
                .isEqualTo(ListaClassificacao.ADOLESCENTE);
    }

    @Test
    void atualizarVoz_trocaSoAVozDentroDoRecorte() {
        UUID id = UUID.randomUUID();
        Corista existente = Corista.builder().id(id).pessoa(pessoa(ADULTO)).tipoVoz(TipoVoz.SOPRANO)
                .tamanhoCamisa(TamanhoCamisa.M).listaClassificacao(ListaClassificacao.JOVEM).build();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(existente));
        repositorioDevolveOQueRecebe();
        logadoComo(usuario("MAESTRO_JOVENS"));

        Corista atualizado = coristaService.atualizarVoz(id, TipoVoz.BAIXO);

        assertThat(atualizado.getTipoVoz()).isEqualTo(TipoVoz.BAIXO);
        assertThat(atualizado.getTamanhoCamisa()).isEqualTo(TamanhoCamisa.M);
        assertThat(atualizado.getListaClassificacao()).isEqualTo(ListaClassificacao.JOVEM);
    }

    @Test
    void atualizarVoz_maestroDeAdolescentes_naoMexeEmJovem() {
        UUID id = UUID.randomUUID();
        when(coristaRepository.findById(id)).thenReturn(Optional.of(coristaDaLista(id, ListaClassificacao.JOVEM)));
        logadoComo(usuario("MAESTRO_ADOLESCENTES"));

        assertThatThrownBy(() -> coristaService.atualizarVoz(id, TipoVoz.BAIXO))
                .isInstanceOf(CoristaNaoEncontradoException.class);
        verify(coristaRepository, never()).save(any());
    }

    @Test
    void buscarPorId_coristaDesligado_naoEAcessivelParaNinguem() {
        UUID id = UUID.randomUUID();
        Corista desligado = coristaDaLista(id, ListaClassificacao.JOVEM);
        desligado.setDesligadoEm(AGORA);
        desligado.setMotivoDesligamento(MotivoDesligamento.VIROU_LIDER);
        when(coristaRepository.findById(id)).thenReturn(Optional.of(desligado));

        assertThatThrownBy(() -> coristaService.buscarPorId(id)).isInstanceOf(CoristaNaoEncontradoException.class);
    }
}
