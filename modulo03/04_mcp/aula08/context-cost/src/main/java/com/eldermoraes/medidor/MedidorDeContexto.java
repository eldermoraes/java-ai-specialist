package com.eldermoraes.medidor;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Mede, no boot, o custo de contexto de cada servidor MCP plugado, e imprime no log.
 *
 * <p>A ideia da aula é tornar a falha 1 (tool-definition overload) tangível: para cada
 * servidor, quantas tools ele expõe, quantos caracteres as definições dessas tools ocupam
 * e uma estimativa grosseira de tokens. Esses caracteres viajam em todo request ao modelo,
 * usando ou não as tools: é a "letra miúda" da conta N+M.
 *
 * <p><b>Estimativa, não medição exata.</b> Tokens aqui são caracteres÷4: serve para ver a
 * ordem de grandeza, não para bater o número do tokenizador. O disclaimer é impresso junto
 * da tabela, de propósito.
 *
 * <p>Os três clientes MCP são obtidos por CDI com o qualifier {@link McpClientName}: o
 * mesmo nome do {@code application.properties}. Usamos {@link Instance} (lookup preguiçoso)
 * para que os clientes só sejam criados/conectados quando o medidor realmente rodar; em
 * teste ele não roda (flag desligada), então nada tenta conectar.
 */
@ApplicationScoped
public class MedidorDeContexto {

    private static final Logger log = Logger.getLogger(MedidorDeContexto.class);

    /** Divisor da estimativa grosseira de tokens (regra de bolso: ~4 chars por token). */
    private static final int CHARS_POR_TOKEN = 4;

    @ConfigProperty(name = "medidor.contexto.enabled", defaultValue = "true")
    boolean habilitado;

    @Inject
    @McpClientName("filesystem")
    Instance<McpClient> filesystem;

    @Inject
    @McpClientName("deepwiki")
    Instance<McpClient> deepwiki;

    @Inject
    @McpClientName("context7")
    Instance<McpClient> context7;

    void aoIniciar(@Observes StartupEvent evento) {
        if (!habilitado) {
            // Em teste (%test.medidor.contexto.enabled=false) o medidor fica quieto:
            // nada de npx, nada de rede. O boot precisa funcionar offline.
            return;
        }

        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════════════╗");
        log.info("║  MEDIDOR DE CONTEXTO — custo das definições de tools por servidor MCP    ║");
        log.info("║  (estas definições viajam em TODO request ao modelo — falha 1)          ║");
        log.info("╚════════════════════════════════════════════════════════════════════════╝");
        log.info("servidor        | nº tools | chars defs | ~tokens (chars/4)");
        log.info("----------------+----------+------------+------------------");

        Medida total = medir("filesystem", filesystem)
                .mais(medir("deepwiki", deepwiki))
                .mais(medir("context7", context7));

        log.info("----------------+----------+------------+------------------");
        log.infof("%-15s | %8d | %10d | %16d", "TOTAL", total.tools(), total.chars(),
                total.chars() / CHARS_POR_TOKEN);
        log.info("");
        log.info("DISCLAIMER: estimativa chars/4 — ordem de grandeza, NÃO a contagem exata");
        log.info("do tokenizador. E lembre: esse custo se repete a CADA request, use-se ou");
        log.info("não uma tool. Uma pergunta como \"quanto é 2 + 2?\" paga a conta inteira.");
        log.info("");
    }

    /** Resultado de medir um servidor: quantas tools e quantos caracteres de definição. */
    private record Medida(long tools, long chars) {
        Medida mais(Medida outra) {
            return new Medida(tools + outra.tools, chars + outra.chars);
        }
    }

    /**
     * Mede um servidor e imprime a linha da tabela. Isolado em try/catch: um servidor
     * indisponível (remoto fora do ar, npx ausente) imprime "indisponível" e não derruba
     * o boot; os outros continuam sendo medidos.
     */
    private Medida medir(String nome, Instance<McpClient> clienteLookup) {
        try {
            if (clienteLookup.isUnsatisfied()) {
                // O cliente nem foi registrado (ex.: desligado por configuração).
                log.infof("%-15s | %8s | %10s | %16s", nome, "-", "-", "indisponível");
                return new Medida(0, 0);
            }
            McpClient cliente = clienteLookup.get();
            List<ToolSpecification> tools = cliente.listTools();

            long chars = 0;
            for (ToolSpecification tool : tools) {
                chars += tamanhoDefinicao(tool);
            }

            log.infof("%-15s | %8d | %10d | %16d", nome, tools.size(), chars,
                    chars / CHARS_POR_TOKEN);
            return new Medida(tools.size(), chars);
        } catch (Exception e) {
            // Servidor indisponível não pode quebrar a demo: registra e segue.
            log.infof("%-15s | %8s | %10s | %16s", nome, "?", "?", "indisponível");
            log.debugf("Falha ao medir o servidor MCP '%s': %s", nome, e.getMessage());
            return new Medida(0, 0);
        }
    }

    /**
     * Caracteres de uma definição de tool: nome + descrição + representação do schema JSON
     * dos parâmetros. É o que a tool "pesa" no contexto. A representação do schema usa o
     * {@code toString()} do {@code JsonObjectSchema}, informativo o bastante para a
     * ordem de grandeza que queremos.
     */
    private long tamanhoDefinicao(ToolSpecification tool) {
        long chars = 0;
        if (tool.name() != null) {
            chars += tool.name().length();
        }
        if (tool.description() != null) {
            chars += tool.description().length();
        }
        if (tool.parameters() != null) {
            chars += String.valueOf(tool.parameters()).length();
        }
        return chars;
    }
}
