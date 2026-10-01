package com.pedeai.catalog.service;

import com.pedeai.catalog.dto.ImportDraft;
import com.pedeai.catalog.dto.ImportIssueResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lê a planilha de cardápio em CSV, como o Excel e o Google Planilhas salvam (com ponto e vírgula no Brasil).
 * Colunas, pelo nome do cabeçalho e em qualquer ordem: categoria, produto, preço, e as opcionais descrição, código,
 * disponível e adicionais (nomes de grupos já cadastrados, separados por "|").
 */
final class SpreadsheetParser {
    static final int MAX_ROWS = 2000;

    record Result(ImportDraft draft, List<ImportIssueResponse> errors) {
    }

    private SpreadsheetParser() {
    }

    static Result parse(String content) {
        List<ImportIssueResponse> errors = new ArrayList<>();
        String text = content.startsWith("\uFEFF") ? content.substring(1) : content;
        List<String> lines = text.lines().toList();
        if (lines.isEmpty() || lines.getFirst().isBlank()) {
            errors.add(new ImportIssueResponse("linha 1", "A planilha está vazia."));
            return new Result(new ImportDraft(List.of()), errors);
        }
        char delimiter = delimiter(lines.getFirst());
        Map<String, Integer> columns = header(split(lines.getFirst(), delimiter));
        for (String required : List.of("categoria", "produto", "preco")) {
            if (!columns.containsKey(required)) {
                errors.add(new ImportIssueResponse("linha 1", "Falta a coluna \"" + required
                        + "\" no cabeçalho. Baixe o modelo para ver as colunas."));
            }
        }
        if (!errors.isEmpty()) {
            return new Result(new ImportDraft(List.of()), errors);
        }
        Map<String, List<ImportDraft.Product>> byCategory = new LinkedHashMap<>();
        Map<String, String> categoryNames = new LinkedHashMap<>();
        int rows = 0;
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            String origin = "linha " + (index + 1);
            if (++rows > MAX_ROWS) {
                errors.add(new ImportIssueResponse(origin, "A planilha pode ter até " + MAX_ROWS + " produtos."));
                break;
            }
            List<String> cells = split(line, delimiter);
            String category = cell(cells, columns, "categoria");
            String name = cell(cells, columns, "produto");
            String price = cell(cells, columns, "preco");
            if (category.isEmpty() || name.isEmpty()) {
                errors.add(new ImportIssueResponse(origin, "Informe a categoria e o nome do produto."));
                continue;
            }
            Long cents = cents(price);
            if (cents == null) {
                errors.add(new ImportIssueResponse(origin, "Preço inválido: \"" + price + "\" (use 12,90)."));
                continue;
            }
            String available = cell(cells, columns, "disponivel").toLowerCase(Locale.ROOT);
            List<String> groups = Arrays.stream(cell(cells, columns, "adicionais").split("\\|"))
                    .map(String::trim).filter(group -> !group.isEmpty()).toList();
            String key = category.toLowerCase(Locale.ROOT);
            categoryNames.putIfAbsent(key, category);
            byCategory.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new ImportDraft.Product(origin, name,
                    emptyToNull(cell(cells, columns, "descricao")), cents, emptyToNull(cell(cells, columns, "codigo")),
                    !List.of("nao", "não", "n", "false", "0").contains(available), groups, List.of()));
        }
        List<ImportDraft.Category> categories = new ArrayList<>();
        byCategory.forEach((key, products) -> categories.add(new ImportDraft.Category(categoryNames.get(key),
                products)));
        return new Result(new ImportDraft(categories), errors);
    }

    /** "R$ 1.234,56", "1234,56", "12.90" ou "12" viram centavos. */
    static Long cents(String text) {
        String value = text.replace("R$", "").replace(" ", "").replace("\u00A0", "");
        if (value.isEmpty()) {
            return null;
        }
        if (value.contains(",")) {
            value = value.replace(".", "").replace(',', '.');
        }
        try {
            BigDecimal reais = new BigDecimal(value);
            return reais.signum() < 0 ? null : reais.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private static char delimiter(String header) {
        long semicolons = header.chars().filter(c -> c == ';').count();
        long tabs = header.chars().filter(c -> c == '\t').count();
        long commas = header.chars().filter(c -> c == ',').count();
        if (tabs > semicolons && tabs > commas) {
            return '\t';
        }
        return semicolons >= commas ? ';' : ',';
    }

    /** Divide uma linha respeitando aspas: "Pizza; grande" fica numa célula, e "" dentro de aspas vira ". */
    static List<String> split(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().trim());
        return cells;
    }

    /** "Preço", "PRECO " e "preço (R$)" valem como "preco"; "Nome" vale como "produto". */
    private static Map<String, Integer> header(List<String> cells) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            String name = Normalizer.normalize(cells.get(i), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT).replaceAll("\\(.*\\)", "").trim();
            String column = switch (name) {
                case "nome", "item", "produto" -> "produto";
                case "valor", "preco" -> "preco";
                case "descricao" -> "descricao";
                case "codigo", "codigo pdv", "sku" -> "codigo";
                case "disponivel" -> "disponivel";
                case "adicionais", "grupos" -> "adicionais";
                case "categoria" -> "categoria";
                default -> null;
            };
            if (column != null) {
                columns.putIfAbsent(column, i);
            }
        }
        return columns;
    }

    private static String cell(List<String> cells, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        return index == null || index >= cells.size() ? "" : cells.get(index);
    }

    private static String emptyToNull(String text) {
        return text.isEmpty() ? null : text;
    }
}
