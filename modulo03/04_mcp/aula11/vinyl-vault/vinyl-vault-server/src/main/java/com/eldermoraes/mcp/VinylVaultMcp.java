package com.eldermoraes.mcp;

import java.util.List;

import org.jboss.logging.Logger;

import com.eldermoraes.dominio.AcervoRepository;
import com.eldermoraes.dto.Disco;
import com.eldermoraes.dto.DiscosDoArtista;

import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;

/**
 * O MCP server do Sebo do Vini: a classe que expõe três tools pelo protocolo.
 *
 * <p><b>Cuidado com o nome da anotação!</b> Este {@code @Tool} é o
 * {@code io.quarkiverse.mcp.server.Tool}, o do lado de quem expõe. Ele não é o
 * {@code dev.langchain4j.agent.tool.Tool} que você usou nos agentes (o do lado de quem
 * consome). Mesma palavra, bibliotecas diferentes, lados opostos do fio. É fácil importar a
 * errada e passar meia hora sem entender por que nada funciona: confira o import lá em cima.
 *
 * <p>Repare também que os métodos Java são camelCase ({@code buscarDisco}), mas o nome
 * exposto pelo protocolo, no atributo {@code name}, é snake_case ({@code buscar_disco}): a
 * convenção de fato do ecossistema MCP entre clients.
 *
 * <p><b>Sobre as descrições (leia com carinho: é o coração do desafio avançado).</b> A
 * descrição de cada tool é a interface para o modelo: quem a lê é o LLM do outro lado do
 * fio, não um humano. Por isso elas dizem só o que a tool faz, quando usá-la e o que espera de
 * cada argumento, nada de instruções escondidas. Uma descrição envenenada (tool poisoning) é
 * exatamente o que o scanner de segurança do desafio caça. O código aqui fica sempre limpo; o
 * experimento de sabotagem controlada fica só no README, como exercício guiado.
 */
public class VinylVaultMcp {

    private static final Logger LOG = Logger.getLogger(VinylVaultMcp.class);

    @Inject
    AcervoRepository acervo;

    /**
     * Busca um disco pelo identificador. Read-only: só lê o acervo, não muda nada.
     *
     * <p>{@code structuredContent=true} + retorno de um record = a extensão serializa o
     * {@link Disco} como {@code structuredContent} na resposta. As annotations descrevem o
     * comportamento de forma declarativa e honesta: esta tool só lê ({@code readOnlyHint=true}),
     * não destrói nada ({@code destructiveHint=false}) e chamá-la N vezes tem o mesmo efeito
     * ({@code idempotentHint=true}).
     */
    @Tool(name = "buscar_disco",
            description = """
                    Busca um disco do acervo do sebo pelo seu identificador e retorna seus
                    dados estruturados: artista, álbum, ano, condição do exemplar e preço.
                    Use quando o usuário quiser consultar UM disco específico e você tiver o
                    identificador dele (ex.: LP-001).""",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Buscar disco",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true))
    public Disco buscarDisco(
            @ToolArg(description = "O identificador do disco, ex.: LP-001") String id) {
        Disco disco = acervo.porId(id);
        if (disco == null) {
            // Erro de negócio, não falha de transporte. A ToolCallException vira uma
            // resposta de tool com isError=true (não um HTTP 500), e o modelo lê esse erro e
            // se recupera (tenta outro id, avisa o usuário). Um exemplar único que já foi
            // vendido também cai aqui: sumiu do acervo, some da busca.
            throw new ToolCallException("Disco não encontrado no acervo: " + id);
        }
        return disco;
    }

    /**
     * Lista os discos de um artista. Também read-only. Devolve o wrapper
     * {@link DiscosDoArtista} (o topo do structuredContent é sempre um objeto, nunca um array
     * solto). Artista sem discos não é erro: retorna uma lista vazia, e o modelo interpreta
     * isso naturalmente ("não temos nada desse artista no momento").
     */
    @Tool(name = "listar_discos_por_artista",
            description = """
                    Lista os discos do acervo de um determinado artista, pelo nome do artista
                    (busca parcial, ex.: "Milton" acha "Milton Nascimento"). Use quando o
                    usuário quiser ver o que o sebo tem de um artista. Se não houver nenhum
                    disco desse artista, retorna uma lista vazia.""",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Listar discos por artista",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true))
    public DiscosDoArtista listarDiscosPorArtista(
            @ToolArg(description = "O nome do artista, ex.: Milton Nascimento") String artista) {
        List<Disco> discos = acervo.porArtista(artista);
        return new DiscosDoArtista(artista, discos);
    }

    /**
     * Vende um disco: operação destrutiva, e é aqui que mora o superpoder deste desafio. O
     * exemplar é único: vender remove o disco do acervo para sempre.
     *
     * <p>Duas metades de "operações destrutivas nunca autônomas", ambas trazidas para dentro
     * do protocolo: a annotation {@code destructiveHint=true} avisa (o client pode destacar a
     * tool na UI ou exigir aprovação), e a <b>elicitation</b> confirma no momento da chamada.
     * Elicitation é o human-in-the-loop dos agentes promovido a primitiva de protocolo: o
     * server pausa no meio da chamada, pergunta ao usuário e só age com a resposta.
     *
     * <p>Guardrail da spec: elicitation é para confirmação e contexto, nunca para credencial
     * (senha, token, cartão). Aqui pedimos só a confirmação da venda: o comprador e o preço
     * combinado.
     */
    @Tool(name = "vender_disco",
            description = """
                    Vende um disco do acervo do sebo. Operação DESTRUTIVA: o exemplar é único,
                    então vender remove o disco do acervo para sempre. Antes de vender, o server
                    pede a confirmação do usuário pelo protocolo (elicitation), confirmando o
                    comprador e o preço combinado. Use quando o usuário pedir explicitamente
                    para comprar/vender um disco.""",
            annotations = @Tool.Annotations(
                    title = "Vender disco",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = false))
    public String venderDisco(
            @ToolArg(description = "O identificador do disco a vender, ex.: LP-001") String id,
            @ToolArg(description = "O nome do comprador (opcional; se ausente, será solicitado "
                    + "na confirmação)", required = false) String comprador,
            Elicitation elicitation) {

        // 0) Validação de input no lado server, o outro lado do MCP05: não confie no client.
        //    Mesmo antes de tocar no acervo, rejeite entrada inválida. Isso é parte do
        //    endurecimento do desafio: o server se protege, não presume que o client mandou
        //    algo são.
        if (id == null || id.isBlank()) {
            throw new ToolCallException("O identificador do disco é obrigatório para vender.");
        }

        // 1) O disco existe? Erro de negócio se não existir (isError, não HTTP 500).
        Disco disco = acervo.porId(id);
        if (disco == null) {
            throw new ToolCallException("Disco não encontrado no acervo: " + id);
        }

        // 2) Elicitation depende de negociação: o client precisa ter declarado a capability no
        //    handshake. Se ele não suporta (ou não suporta o form mode que usamos aqui), nunca
        //    execute a operação destrutiva assumindo uma confirmação que não veio: recuse com
        //    uma mensagem clara. Vender é irreversível; sem confirmação, não vende.
        //    (Para clients stateless a extensão documenta o padrão MRTR, a confirmação em duas
        //    fases, fora do escopo deste desafio; aqui a recusa segura basta.)
        if (!elicitation.isSupported() || !elicitation.isFormModeSupported()) {
            LOG.warnf("Venda de %s recusada: client sem suporte a elicitation.", id);
            return "Este client não suporta confirmação interativa (elicitation), então o disco "
                    + id + " NÃO foi vendido. Vender é uma operação destrutiva (o exemplar é "
                    + "único) e exige confirmação do usuário.";
        }

        // 3) Monta a elicitation: mensagem de confirmação + o comprador (obrigatório se não veio
        //    no argumento) + o preço combinado (sempre obrigatório; o preço de tabela é só
        //    referência, a venda fecha no valor combinado). Nada de credencial: só confirmação.
        boolean compradorAusente = (comprador == null || comprador.isBlank());
        ElicitationRequest.Builder builder = elicitation.requestBuilder()
                .setMessage("Confirma a venda do disco " + disco.id() + " — \""
                        + disco.album() + "\", de " + disco.artista()
                        + " (preço de tabela R$ " + disco.preco() + ")?");
        builder.addSchemaProperty("comprador", new ElicitationRequest.StringSchema.StringSchemaBuilder()
                .setTitle("Comprador")
                .setDescription("Nome de quem está comprando o disco.")
                .setRequired(compradorAusente)
                .build());
        builder.addSchemaProperty("preco_combinado", new ElicitationRequest.StringSchema.StringSchemaBuilder()
                .setTitle("Preço combinado")
                .setDescription("Valor final acordado para a venda (ex.: 450.00).")
                .setRequired(true)
                .build());

        // 4) sendAndAwait: pausa a chamada, pergunta, espera a resposta do usuário.
        ElicitationResponse resposta = builder.build().sendAndAwait();

        // 5) actionAccepted() = o usuário confirmou (ACCEPT). DECLINE/CANCEL = abortou.
        if (!resposta.actionAccepted()) {
            LOG.infof("Venda de %s abortada pelo usuário.", id);
            return "Venda abortada pelo usuário. O disco " + id + " continua no acervo.";
        }

        String compradorFinal = compradorAusente
                ? resposta.content().getString("comprador") : comprador;
        String precoCombinado = resposta.content().getString("preco_combinado");

        // 6) Confirmado: remove o exemplar único do acervo. A partir daqui, buscar_disco(id)
        //    passa a devolver isError: o disco não existe mais.
        acervo.vender(id);
        LOG.infof("Disco %s vendido para %s por R$ %s.", id, compradorFinal, precoCombinado);
        return "Disco " + id + " (\"" + disco.album() + "\", de " + disco.artista()
                + ") vendido para " + compradorFinal + " por R$ " + precoCombinado
                + ". Exemplar removido do acervo.";
    }
}
