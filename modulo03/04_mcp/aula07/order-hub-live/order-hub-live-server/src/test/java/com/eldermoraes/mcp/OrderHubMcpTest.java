package com.eldermoraes.mcp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

/**
 * Teste do MCP server falando JSON-RPC cru contra o endpoint {@code /mcp}.
 *
 * <p>Este teste é o "MCP Inspector programático": ele não usa nenhum helper mágico, fala o
 * protocolo na unha (initialize → notifications/initialized → tools/list → tools/call). É
 * assim que você garante que o server não quebra silenciosamente numa mudança futura.
 *
 * <p>Detalhe importante: o "client" deste teste não declara a capability de elicitation no
 * handshake (o objeto {@code capabilities} do initialize vai vazio). Por isso, ao chamar
 * {@code cancelar_pedido}, esperamos a recusa segura: o server nunca cancela sem
 * confirmação que não pode obter.
 */
@QuarkusTest
class OrderHubMcpTest {

    static final String ACCEPT = "application/json, text/event-stream";

    /**
     * A resposta do endpoint pode vir como JSON puro OU como SSE (text/event-stream). Este
     * helper normaliza: se vier SSE, extrai o payload das linhas {@code data:}; senão,
     * devolve o corpo como está.
     */
    static String corpo(String body) {
        if (body != null && body.stripLeading().startsWith("data:")) {
            return Arrays.stream(body.split("\n"))
                    .filter(l -> l.startsWith("data:"))
                    .map(l -> l.substring("data:".length()).trim())
                    .collect(Collectors.joining());
        }
        return body;
    }

    /**
     * Faz o handshake (initialize + notifications/initialized) e devolve o Mcp-Session-Id
     * para as chamadas seguintes reusarem a mesma sessão.
     */
    String abrirSessao() {
        Response init = given()
                .contentType("application/json")
                .accept(ACCEPT)
                .body("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                          "protocolVersion":"2025-06-18",
                          "capabilities":{},
                          "clientInfo":{"name":"order-hub-test","version":"1.0"}}}
                        """)
                .when().post("/mcp")
                .then().statusCode(200)
                .extract().response();

        String sessionId = init.getHeader("Mcp-Session-Id");
        assertTrue(sessionId != null && !sessionId.isBlank(),
                "initialize deveria devolver o header Mcp-Session-Id");

        given()
                .contentType("application/json").accept(ACCEPT)
                .header("Mcp-Session-Id", sessionId)
                .body("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")
                .when().post("/mcp")
                .then().statusCode(202);

        return sessionId;
    }

    String chamar(String sessionId, String jsonRpc) {
        Response r = given()
                .contentType("application/json").accept(ACCEPT)
                .header("Mcp-Session-Id", sessionId)
                .body(jsonRpc)
                .when().post("/mcp")
                .then().statusCode(200)
                .extract().response();
        return corpo(r.asString());
    }

    @Test
    void listaAsTresToolsEmSnakeCase() {
        String sessionId = abrirSessao();
        String body = chamar(sessionId,
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");

        JsonPath json = JsonPath.from(body);
        java.util.List<String> nomes = json.getList("result.tools.name");
        org.hamcrest.MatcherAssert.assertThat(nomes,
                hasItems("buscar_pedido", "listar_pedidos_por_cliente", "cancelar_pedido"));
    }

    @Test
    void buscarPedidoDevolveStructuredContent() {
        String sessionId = abrirSessao();
        String body = chamar(sessionId, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                  "name":"buscar_pedido","arguments":{"id":"PED-1001"}}}
                """);

        JsonPath json = JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.getBoolean("result.isError"), equalTo(false));
        org.hamcrest.MatcherAssert.assertThat(
                json.getString("result.structuredContent.id"), equalTo("PED-1001"));
        org.hamcrest.MatcherAssert.assertThat(
                json.getString("result.structuredContent.cliente"), equalTo("TechNova"));
        org.hamcrest.MatcherAssert.assertThat(
                json.getString("result.structuredContent.status"), equalTo("ABERTO"));
        org.hamcrest.MatcherAssert.assertThat(
                json.getList("result.structuredContent.itens").isEmpty(), equalTo(false));
    }

    @Test
    void pedidoInexistenteVemComoIsErrorNaoComoErroHttp() {
        String sessionId = abrirSessao();
        // Status HTTP continua 200 (é o chamar() que exige 200): o erro de negócio viaja
        // dentro da resposta da tool, com isError=true, não como falha de transporte.
        String body = chamar(sessionId, """
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{
                  "name":"buscar_pedido","arguments":{"id":"PED-9999"}}}
                """);

        JsonPath json = JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.getBoolean("result.isError"), equalTo(true));
        org.hamcrest.MatcherAssert.assertThat(
                json.getString("result.content[0].text"),
                containsString("Pedido não encontrado"));
    }

    @Test
    void cancelarSemElicitationRecusaENaoAlteraOStatus() {
        String sessionId = abrirSessao();

        // Como o handshake não declarou capability de elicitation, o server recusa com
        // segurança e não cancela.
        String cancel = chamar(sessionId, """
                {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{
                  "name":"cancelar_pedido","arguments":{"id":"PED-1004"}}}
                """);
        JsonPath jsonCancel = JsonPath.from(cancel);
        org.hamcrest.MatcherAssert.assertThat(
                jsonCancel.getString("result.content[0].text"),
                containsString("NÃO foi cancelado"));

        // Prova de que o estado não mudou: PED-1004 continua ABERTO (não virou CANCELADO).
        String busca = chamar(sessionId, """
                {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{
                  "name":"buscar_pedido","arguments":{"id":"PED-1004"}}}
                """);
        JsonPath jsonBusca = JsonPath.from(busca);
        org.hamcrest.MatcherAssert.assertThat(
                jsonBusca.getString("result.structuredContent.status"), equalTo("ABERTO"));
    }
}
