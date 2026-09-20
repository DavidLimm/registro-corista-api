package com.registraai.registro_coristas_api.pessoa.service;

import com.registraai.registro_coristas_api.congregacao.exception.CongregacaoInativaException;
import com.registraai.registro_coristas_api.congregacao.model.Congregacao;
import com.registraai.registro_coristas_api.congregacao.service.CongregacaoService;
import com.registraai.registro_coristas_api.endereco.dto.EnderecoRequest;
import com.registraai.registro_coristas_api.endereco.service.EnderecoService;
import com.registraai.registro_coristas_api.pessoa.dto.PessoaFiltro;
import com.registraai.registro_coristas_api.pessoa.dto.PessoaRequest;
import com.registraai.registro_coristas_api.pessoa.exception.ConsentimentoLgpdObrigatorioException;
import com.registraai.registro_coristas_api.pessoa.exception.PessoaNaoEncontradaException;
import com.registraai.registro_coristas_api.pessoa.exception.ResponsavelLegalIncompletoException;
import com.registraai.registro_coristas_api.pessoa.exception.ResponsavelLegalObrigatorioException;
import com.registraai.registro_coristas_api.pessoa.exception.TransicaoDeStatusInvalidaException;
import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.model.StatusPessoa;
import com.registraai.registro_coristas_api.pessoa.repository.PessoaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Regras comuns a qualquer pessoa (corista, maestro, pastor...). Os papéis específicos, como o corista, usam este
 * service para gravar a parte "pessoa" do cadastro.
 */
@Service
@RequiredArgsConstructor
public class PessoaService {

    private final PessoaRepository pessoaRepository;
    private final CongregacaoService congregacaoService;
    private final EnderecoService enderecoService;
    private final Clock clock;

    /** Todo cadastro entra como {@code PENDENTE}; a aprovação é um passo separado. */
    @Transactional
    public Pessoa criar(PessoaRequest request) {
        Congregacao congregacao = buscarCongregacaoAtiva(request.congregacaoId());
        Pessoa pessoa = new Pessoa();
        pessoa.setCongregacao(congregacao);
        preencher(pessoa, request);
        vincularEndereco(pessoa, request.endereco());
        return pessoaRepository.save(pessoa);
    }

    @Transactional(readOnly = true)
    public Pessoa buscarPorId(UUID id) {
        return pessoaRepository.findById(id)
                .orElseThrow(() -> new PessoaNaoEncontradaException(id));
    }

    /** Ordenada por nome (o cliente não escolhe a ordenação). A faixa etária filtrada é a real, pela data de nascimento. */
    @Transactional(readOnly = true)
    public Page<Pessoa> listar(PessoaFiltro filtro, int pagina, int tamanho) {
        Specification<Pessoa> especificacao = Specification.unrestricted();
        if (filtro.nome() != null && !filtro.nome().isBlank()) {
            String trecho = "%" + escaparLike(filtro.nome().trim().toLowerCase()) + "%";
            especificacao = especificacao.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("nome")), trecho, '\\'));
        }
        if (filtro.areaId() != null) {
            especificacao = especificacao.and((root, query, cb) ->
                    cb.equal(root.get("congregacao").get("area").get("id"), filtro.areaId()));
        }
        if (filtro.congregacaoId() != null) {
            especificacao = especificacao.and((root, query, cb) ->
                    cb.equal(root.get("congregacao").get("id"), filtro.congregacaoId()));
        }
        if (filtro.faixaEtaria() != null) {
            LocalDate limite = Pessoa.nascimentoLimiteDaMaioridade(LocalDate.now(clock));
            especificacao = especificacao.and((root, query, cb) -> switch (filtro.faixaEtaria()) {
                case JOVEM -> cb.lessThanOrEqualTo(root.<LocalDate>get("dataNascimento"), limite);
                case ADOLESCENTE -> cb.greaterThan(root.<LocalDate>get("dataNascimento"), limite);
            });
        }
        if (filtro.status() != null) {
            especificacao = especificacao.and((root, query, cb) -> cb.equal(root.get("status"), filtro.status()));
        }
        // "id" desempata nomes iguais para a paginação ser estável
        PageRequest pageable = PageRequest.of(pagina, tamanho, Sort.by("nome").and(Sort.by("id")));
        return pessoaRepository.findAll(especificacao, pageable);
    }

    /**
     * Atualiza os dados cadastrais; o status e a trilha de aprovação não mudam por aqui. Se a congregação mudar, a
     * nova precisa estar ativa. O endereço só é alterado quando enviado. O consentimento, uma vez dado, é mantido.
     */
    @Transactional
    public Pessoa atualizar(UUID id, PessoaRequest request) {
        Pessoa pessoa = buscarPorId(id);
        if (!pessoa.getCongregacao().getId().equals(request.congregacaoId())) {
            pessoa.setCongregacao(buscarCongregacaoAtiva(request.congregacaoId()));
        }
        preencher(pessoa, request);
        vincularEndereco(pessoa, request.endereco());
        return pessoaRepository.save(pessoa);
    }

    /**
     * {@code PENDENTE -> APROVADO}, registrando quem aprovou. Ainda sem endpoint: a identidade de quem aprova
     * ({@code PRESBITERO+}) só existirá com a autenticação.
     */
    @Transactional
    public Pessoa aprovar(UUID id, UUID aprovadoPor) {
        Pessoa pessoa = buscarPorId(id);
        if (pessoa.getStatus() != StatusPessoa.PENDENTE) {
            throw new TransicaoDeStatusInvalidaException(pessoa.getStatus(), StatusPessoa.APROVADO);
        }
        pessoa.setStatus(StatusPessoa.APROVADO);
        pessoa.setAprovadoPor(aprovadoPor);
        pessoa.setAprovadoEm(Instant.now(clock));
        return pessoaRepository.save(pessoa);
    }

    /** Soft delete ({@code INATIVO}), nunca DELETE físico. Idempotente. */
    @Transactional
    public void inativar(UUID id) {
        Pessoa pessoa = buscarPorId(id);
        pessoa.setStatus(StatusPessoa.INATIVO);
        pessoaRepository.save(pessoa);
    }

    private Congregacao buscarCongregacaoAtiva(UUID congregacaoId) {
        Congregacao congregacao = congregacaoService.buscarPorId(congregacaoId);
        if (!congregacao.isAtiva()) {
            throw new CongregacaoInativaException(congregacao.getNome());
        }
        return congregacao;
    }

    // Normaliza (trim, telefone só com dígitos) e aplica as regras de LGPD. Se lançar, a transação inteira é
    // revertida, então não fica meia atualização gravada.
    private void preencher(Pessoa pessoa, PessoaRequest request) {
        pessoa.setNome(request.nome().trim());
        pessoa.setDataNascimento(request.dataNascimento());

        String responsavelNome = textoOpcional(request.responsavelLegalNome());
        String responsavelTelefone = telefoneSomenteDigitos(request.responsavelLegalTelefone());
        if ((responsavelNome == null) != (responsavelTelefone == null)) {
            throw new ResponsavelLegalIncompletoException();
        }
        pessoa.setResponsavelLegalNome(responsavelNome);
        pessoa.setResponsavelLegalTelefone(responsavelTelefone);

        if (Boolean.TRUE.equals(request.consentimentoLgpd()) && pessoa.getConsentimentoLgpdEm() == null) {
            pessoa.setConsentimentoLgpdEm(Instant.now(clock));
        }

        LocalDate hoje = LocalDate.now(clock);
        if (pessoa.menorDeIdade(hoje)) {
            if (responsavelNome == null) {
                throw new ResponsavelLegalObrigatorioException();
            }
            if (pessoa.getConsentimentoLgpdEm() == null) {
                throw new ConsentimentoLgpdObrigatorioException();
            }
        }
    }

    private void vincularEndereco(Pessoa pessoa, EnderecoRequest enderecoRequest) {
        if (enderecoRequest == null) {
            return;
        }
        if (pessoa.getEndereco() == null) {
            pessoa.setEndereco(enderecoService.criar(enderecoRequest));
        } else {
            enderecoService.atualizar(pessoa.getEndereco().getId(), enderecoRequest);
        }
    }

    private String textoOpcional(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }

    private String telefoneSomenteDigitos(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return null;
        }
        return telefone.replaceAll("\\D", "");
    }

    // o texto digitado é literal: % e _ não viram curinga do LIKE
    private String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
