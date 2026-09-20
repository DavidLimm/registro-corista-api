package com.registraai.registro_coristas_api.telefone.service;

import com.registraai.registro_coristas_api.pessoa.model.Pessoa;
import com.registraai.registro_coristas_api.pessoa.service.PessoaService;
import com.registraai.registro_coristas_api.telefone.dto.TelefoneRequest;
import com.registraai.registro_coristas_api.telefone.exception.TelefoneDuplicadoException;
import com.registraai.registro_coristas_api.telefone.exception.TelefoneNaoEncontradoException;
import com.registraai.registro_coristas_api.telefone.model.Telefone;
import com.registraai.registro_coristas_api.telefone.repository.TelefoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Telefones de uma pessoa. Invariante: quem tem ao menos um telefone tem exatamente um principal. Todo acesso é
 * feito a partir da pessoa dona, então um telefone nunca é lido nem alterado por outra pessoa.
 */
@Service
@RequiredArgsConstructor
public class TelefoneService {

    private final TelefoneRepository telefoneRepository;
    private final PessoaService pessoaService;

    /** O primeiro telefone da pessoa é sempre o principal; nos demais, só se {@code principal = true}. */
    @Transactional
    public Telefone criar(UUID pessoaId, TelefoneRequest request) {
        Pessoa pessoa = pessoaService.buscarPorId(pessoaId);
        String numero = somenteDigitos(request.numero());
        if (telefoneRepository.existsByPessoaIdAndNumero(pessoaId, numero)) {
            throw new TelefoneDuplicadoException(numero);
        }

        boolean principal = Boolean.TRUE.equals(request.principal()) || !telefoneRepository.existsByPessoaId(pessoaId);
        if (principal) {
            rebaixarPrincipalAtual(pessoaId);
        }

        Telefone telefone = new Telefone();
        telefone.setPessoa(pessoa);
        telefone.setNumero(numero);
        telefone.setWhatsapp(Boolean.TRUE.equals(request.whatsapp()));
        telefone.setPrincipal(principal);
        return telefoneRepository.save(telefone);
    }

    /** O principal vem primeiro. Lança 404 se a pessoa não existir. */
    @Transactional(readOnly = true)
    public List<Telefone> listar(UUID pessoaId) {
        pessoaService.buscarPorId(pessoaId);
        return telefoneRepository.findByPessoaIdOrderByPrincipalDescCriadoEmAsc(pessoaId);
    }

    @Transactional(readOnly = true)
    public Telefone buscarPorId(UUID pessoaId, UUID id) {
        return telefoneRepository.findByIdAndPessoaId(id, pessoaId)
                .orElseThrow(() -> new TelefoneNaoEncontradoException(id));
    }

    /** {@code principal = false} não rebaixa o principal atual: para trocar, marque outro telefone como principal. */
    @Transactional
    public Telefone atualizar(UUID pessoaId, UUID id, TelefoneRequest request) {
        Telefone telefone = buscarPorId(pessoaId, id);
        String numero = somenteDigitos(request.numero());
        if (telefoneRepository.existsByPessoaIdAndNumeroAndIdNot(pessoaId, numero, id)) {
            throw new TelefoneDuplicadoException(numero);
        }

        telefone.setNumero(numero);
        telefone.setWhatsapp(Boolean.TRUE.equals(request.whatsapp()));
        if (Boolean.TRUE.equals(request.principal()) && !telefone.isPrincipal()) {
            rebaixarPrincipalAtual(pessoaId);
            telefone.setPrincipal(true);
        }
        return telefoneRepository.save(telefone);
    }

    /**
     * Remove o telefone (o registro não tem status, então não há soft delete). Se era o principal e a pessoa ainda
     * tem outros, o mais antigo assume, para a invariante do principal continuar valendo.
     */
    @Transactional
    public void remover(UUID pessoaId, UUID id) {
        Telefone telefone = buscarPorId(pessoaId, id);
        boolean eraPrincipal = telefone.isPrincipal();
        telefoneRepository.delete(telefone);
        telefoneRepository.flush();

        if (eraPrincipal) {
            telefoneRepository.findByPessoaIdOrderByPrincipalDescCriadoEmAsc(pessoaId).stream()
                    .findFirst()
                    .ifPresent(proximo -> {
                        proximo.setPrincipal(true);
                        telefoneRepository.save(proximo);
                    });
        }
    }

    // O flush antes de marcar o novo principal é necessário: o Hibernate executa INSERTs antes de UPDATEs e o
    // índice único parcial (um principal por pessoa) não aceitaria dois principais nem por um instante.
    private void rebaixarPrincipalAtual(UUID pessoaId) {
        telefoneRepository.findByPessoaIdAndPrincipalTrue(pessoaId).ifPresent(atual -> {
            atual.setPrincipal(false);
            telefoneRepository.saveAndFlush(atual);
        });
    }

    private String somenteDigitos(String numero) {
        return numero.replaceAll("\\D", "");
    }
}
