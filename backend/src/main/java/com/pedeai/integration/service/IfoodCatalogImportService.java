package com.pedeai.integration.service;

import com.pedeai.catalog.dto.CatalogImportResponse;
import com.pedeai.catalog.dto.ImportIssueResponse;
import com.pedeai.catalog.service.CatalogImportService;
import com.pedeai.integration.config.IfoodProperties;
import com.pedeai.integration.domain.MarketplaceConnection;
import com.pedeai.integration.ifood.IfoodCatalogMapper;
import com.pedeai.integration.ifood.IfoodClient;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.shared.exception.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * Importa o cardápio da loja no iFood (categorias, produtos com código PDV, adicionais). O código PDV do iFood vira o
 * do produto aqui, o mesmo que casa os itens dos pedidos. Com o simulador e sem credenciais, usa um cardápio de
 * exemplo.
 */
@Service
public class IfoodCatalogImportService {
    static final String NOT_IFOOD = "Esta integração não é do iFood.";
    static final String UNAVAILABLE = "O PedeAí ainda não tem as credenciais do iFood.";

    private final IfoodProperties properties;
    private final IfoodClient ifood;
    private final ConnectionService connections;
    private final CatalogImportService catalogImportService;
    private final ObjectMapper json;

    public IfoodCatalogImportService(IfoodProperties properties, IfoodClient ifood, ConnectionService connections,
                                     CatalogImportService catalogImportService, ObjectMapper json) {
        this.properties = properties;
        this.ifood = ifood;
        this.connections = connections;
        this.catalogImportService = catalogImportService;
        this.json = json;
    }

    @Transactional
    public CatalogImportResponse importCatalog(UUID storeId, UUID connectionId, boolean dryRun) {
        MarketplaceConnection connection = connections.find(storeId, connectionId);
        if (connection.getProvider() != OrderSource.IFOOD) {
            throw new BusinessRuleException(NOT_IFOOD);
        }
        JsonNode categories;
        if (properties.configured()) {
            try {
                categories = ifood.catalogCategories(connection.getExternalMerchantId());
            } catch (IfoodClient.IfoodApiException e) {
                return new CatalogImportResponse(false, 0, 0, 0, 0, List.of(new ImportIssueResponse("iFood",
                        "Não deu para ler o cardápio no iFood: " + e.getMessage())));
            }
        } else if (properties.simulator()) {
            categories = json.readTree(SAMPLE);
        } else {
            throw new BusinessRuleException(UNAVAILABLE);
        }
        return catalogImportService.apply(storeId, IfoodCatalogMapper.toDraft(categories), dryRun);
    }

    /** Cardápio de exemplo do simulador, no formato da API de catálogo. */
    static final String SAMPLE = """
            [{"id":"cat-pizzas","name":"Pizzas","status":"AVAILABLE","template":"PIZZA","items":[
               {"id":"it-1","name":"Pizza Grande","description":"8 fatias","externalCode":"500","status":"AVAILABLE",
                "price":{"value":0},"optionGroups":[
                  {"name":"Sabores","min":1,"max":2,"options":[
                     {"name":"Calabresa","externalCode":"101","price":{"value":45.9}},
                     {"name":"Quatro queijos","externalCode":"102","price":{"value":52.9}}]},
                  {"name":"Borda","min":0,"max":1,"options":[
                     {"name":"Catupiry","externalCode":"201","price":{"value":8}}]}]}]},
             {"id":"cat-bebidas","name":"Bebidas","status":"AVAILABLE","items":[
               {"id":"it-2","name":"Refrigerante lata","externalCode":"900","status":"AVAILABLE",
                "price":{"value":7}},
               {"id":"it-3","name":"Suco natural","externalCode":"901","status":"UNAVAILABLE",
                "price":{"value":9.5}}]}]
            """;
}
