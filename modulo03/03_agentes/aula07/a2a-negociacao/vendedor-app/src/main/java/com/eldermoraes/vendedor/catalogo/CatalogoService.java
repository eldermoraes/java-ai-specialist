package com.eldermoraes.vendedor.catalogo;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class CatalogoService {

    private static final List<Produto> CATALOGO = List.of(
            new Produto("Servidor Rack 2U", new BigDecimal("18000"), new BigDecimal("14500"), "15 dias úteis"),
            new Produto("Switch Gerenciável 48 portas", new BigDecimal("9500"), new BigDecimal("7800"), "10 dias úteis"),
            new Produto("Licença Software Pro (anual)", new BigDecimal("2400"), new BigDecimal("2000"), "imediato"),
            new Produto("Storage NAS 24TB", new BigDecimal("32000"), new BigDecimal("27000"), "20 dias úteis"),
            new Produto("No-break 6kVA", new BigDecimal("12000"), new BigDecimal("9800"), "12 dias úteis"));

    public Produto encontrar(String descricao) {
        if (descricao == null) {
            return defaultProduto();
        }
        String d = descricao.toLowerCase(Locale.ROOT);
        return CATALOGO.stream()
                .filter(p -> d.contains(palavraChave(p)))
                .findFirst()
                .orElse(defaultProduto());
    }

    private String palavraChave(Produto p) {
        String n = p.nome().toLowerCase(Locale.ROOT);
        if (n.contains("servidor")) return "servidor";
        if (n.contains("switch")) return "switch";
        if (n.contains("licença") || n.contains("licenca") || n.contains("software")) return "licença";
        if (n.contains("storage") || n.contains("nas")) return "storage";
        if (n.contains("no-break") || n.contains("nobreak")) return "no-break";
        return n;
    }

    private Produto defaultProduto() {
        return CATALOGO.get(0);
    }

    public List<Produto> listar() {
        return CATALOGO;
    }
}
