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
 * <p>Detalhe importante (e é o veículo do critério de aceite nº 4): o "client" deste teste
 * não declara a capability de elicitation no handshake (o objeto {@code capabilities} do
 * initialize vai vazio). Por isso, ao chamar {@code vender_disco}, esperamos a recusa segura:
 * o server nunca vende sem uma confirmação que não pode obter, e o disco continua no acervo.
 */
@QuarkusTest
class VinylVaultMcpTest {

    static final String ACCEPT = "application/json, text/event-stream";

    /**
     * A resposta do endpoint pode vir como JSON puro OU como SSE (text/event-stream). Este
     * helper normaliza: se vier SSE, extrai o payload das linhas {@code data:}; senão, devolve
     * o corpo como está.
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
     * Faz o handshake (initialize + notifications/initialized) e devolve o Mcp-Session-Id para
     * as chamadas seguintes reusarem a mesma sessão.
     */
    String abrirSessao() {
        Response init = given()
                .contentType("application/json")
                .accept(ACCEPT)
                .body("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                          "protocolVersion":"2025-06-18",
                          "capabilities":{},
                          "clientInfo":{"name":"vinyl-vault-test","version":"1.0"}}}
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
                hasItems("buscar_disco", "listar_discos_por_artista", "vender_disco"));
    }

    @Test
    void buscarDiscoDevolveStructuredContent() {
        String sessionId = abrirSessao();
        String body = chamar(sessionId, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                  "name":"buscar_disco","arguments":{"id":"LP-001"}}}
                """);

        JsonPath json = JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.getBoolean("result.isError"), equalTo(false));
        org.hamcrest.MatcherAssert.assertThat(
                json.getString("result.structuredContent.id"), equalTo("LP-001"));
        org.hamcrest.MatcherAssert.assertThat(
                json.getString("result.structuredContent.artista"), equalTo("Milton Nascimento"));
        org.hamcrest.MatcherAssert.assertThat(
                json.getString("result.structuredContent.album"), equalTo("Clube da Esquina"));
    }

    @Test
    void discoInexistenteVemComoIsErrorNaoComoErroHttp() {
        String sessionId = abrirSessao();
        // Status HTTP continua 200 (é o chamar() que exige 200): o erro de negócio viaja dentro
        // da resposta da tool, com isError=true, não como falha de transporte.
        String body = chamar(sessionId, """
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{
                  "name":"buscar_disco","arguments":{"id":"LP-9999"}}}
                """);

        JsonPath json = JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.getBoolean("result.isError"), equalTo(true));
        org.hamcrest.MatcherAssert.assertThat(
                json.getString("result.content[0].text"),
                containsString("Disco não encontrado"));
    }

    @Test
    void listarDiscosPorArtistaDevolveWrapper() {
        String sessionId = abrirSessao();
        // "Milton" (busca parcial, case-insensitive) deve casar com os dois discos do Milton.
        String body = chamar(sessionId, """
                {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{
                  "name":"listar_discos_por_artista","arguments":{"artista":"Milton"}}}
                """);

        JsonPath json = JsonPath.from(body);
        org.hamcrest.MatcherAssert.assertThat(json.getBoolean("result.isError"), equalTo(false));
        // O topo do structuredContent é o objeto wrapper (artista + discos), nunca um array solto.
        org.hamcrest.MatcherAssert.assertThat(
                json.getString("result.structuredContent.artista"), equalTo("Milton"));
        org.hamcrest.MatcherAssert.assertThat(
                json.getList("result.structuredContent.discos").isEmpty(), equalTo(false));
    }

    @Test
    void venderSemElicitationRecusaENaoRemoveDoAcervo() {
        String sessionId = abrirSessao();

        // Como o handshake não declarou capability de elicitation, o server recusa com
        // segurança e não vende.
        String venda = chamar(sessionId, """
                {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{
                  "name":"vender_disco","arguments":{"id":"LP-005"}}}
                """);
        JsonPath jsonVenda = JsonPath.from(venda);
        org.hamcrest.MatcherAssert.assertThat(
                jsonVenda.getString("result.content[0].text"),
                containsString("NÃO foi vendido"));

        // Prova de que o estado não mudou: LP-005 continua no acervo (buscar não dá isError).
        String busca = chamar(sessionId, """
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{
                  "name":"buscar_disco","arguments":{"id":"LP-005"}}}
                """);
        JsonPath jsonBusca = JsonPath.from(busca);
        org.hamcrest.MatcherAssert.assertThat(jsonBusca.getBoolean("result.isError"), equalTo(false));
        org.hamcrest.MatcherAssert.assertThat(
                json_id(jsonBusca), equalTo("LP-005"));
    }

    private static String json_id(JsonPath json) {
        return json.getString("result.structuredContent.id");
    }
}
