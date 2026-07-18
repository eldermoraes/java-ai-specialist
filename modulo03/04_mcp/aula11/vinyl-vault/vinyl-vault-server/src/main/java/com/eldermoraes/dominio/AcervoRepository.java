package com.eldermoraes.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.eldermoraes.dto.Disco;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * O acervo do Sebo do Vini: repositório fake, em memória, com uma dúzia curta de discos
 * raros de exemplo.
 *
 * <p>O foco deste desafio é o protocolo, não a persistência: por isso não há banco de dados
 * nem JPA aqui, só um mapa. Mas repare numa decisão que não é acidental: usamos um
 * {@link ConcurrentHashMap}, e não um {@code HashMap} comum.
 *
 * <p>Por quê? Porque um MCP server remoto é um serviço HTTP concorrente: um único server
 * atende N agentes ao mesmo tempo, cada um numa thread. Esse é o mesmo instinto de
 * thread-safety que você já tem de qualquer serviço web: o estado compartilhado (este
 * acervo) precisa aguentar acesso concorrente. E aqui isso pesa dobrado: vender remove o
 * disco do mapa, então dois agentes tentando comprar o mesmo exemplar único ao mesmo tempo
 * é uma corrida real. Cuidado com estado mutável num bean singleton: aqui o
 * {@code ConcurrentHashMap} é justamente esse cuidado materializado.
 */
@ApplicationScoped
public class AcervoRepository {

    private final Map<String, Disco> discos = new ConcurrentHashMap<>();

    @PostConstruct
    void carregar() {
        // Clássicos brasileiros e alguns marcos internacionais, com repetição de artista de
        // propósito (Milton aparece em dois): assim listar_discos_por_artista tem o que listar.
        // Condições no jargão de sebo; preços de garimpo de raridade.
        adicionar(new Disco("LP-001", "Milton Nascimento", "Clube da Esquina", 1972,
                "capa com desgaste nas bordas, vinil excelente", new BigDecimal("480.00")));
        adicionar(new Disco("LP-002", "Novos Baianos", "Acabou Chorare", 1972,
                "capa muito boa, vinil com chiado leve", new BigDecimal("620.00")));
        adicionar(new Disco("LP-003", "Chico Buarque", "Construção", 1971,
                "capa impecável, vinil quase mint", new BigDecimal("390.00")));
        adicionar(new Disco("LP-004", "Elis Regina & Tom Jobim", "Elis & Tom", 1974,
                "capa com uma dobra, vinil excelente", new BigDecimal("550.00")));
        adicionar(new Disco("LP-005", "Secos & Molhados", "Secos & Molhados", 1973,
                "capa desbotada, vinil bom", new BigDecimal("280.00")));
        adicionar(new Disco("LP-006", "Pink Floyd", "The Dark Side of the Moon", 1973,
                "prensagem original, capa e vinil excelentes", new BigDecimal("720.00")));
        adicionar(new Disco("LP-007", "Miles Davis", "Kind of Blue", 1959,
                "capa com marca de água antiga, vinil muito bom", new BigDecimal("890.00")));
        adicionar(new Disco("LP-008", "Milton Nascimento", "Milagre dos Peixes", 1973,
                "capa com desgaste, vinil bom", new BigDecimal("340.00")));
    }

    private void adicionar(Disco disco) {
        discos.put(disco.id(), disco);
    }

    /** Busca um disco pelo id. Retorna {@code null} se não existir (ou se já foi vendido). */
    public Disco porId(String id) {
        return discos.get(id);
    }

    /**
     * Lista os discos de um artista. A busca é case-insensitive e por contém (assim
     * "milton" acha "Milton Nascimento"). Lista vazia se não houver nenhum, e isso não é
     * erro, é um resultado válido que o modelo interpreta naturalmente.
     */
    public List<Disco> porArtista(String artista) {
        String alvo = artista == null ? "" : artista.toLowerCase();
        return discos.values().stream()
                .filter(d -> d.artista().toLowerCase().contains(alvo))
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .collect(Collectors.toList());
    }

    /**
     * Vende um disco: remove o exemplar do acervo e devolve o disco vendido (para a mensagem
     * de confirmação). Retorna {@code null} se o id não existir. Como o exemplar é único, a
     * remoção é definitiva: não há "baixar 1 do estoque", o disco simplesmente sai do mapa.
     */
    public Disco vender(String id) {
        return discos.remove(id);
    }

    /** Todos os discos ainda no acervo. */
    public List<Disco> todos() {
        return discos.values().stream()
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .collect(Collectors.toList());
    }
}
