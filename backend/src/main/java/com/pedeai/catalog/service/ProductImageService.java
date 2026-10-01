package com.pedeai.catalog.service;

import com.pedeai.catalog.dto.ProductResponse;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.storage.ImageStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Foto do produto: a tela já reduz a imagem antes de mandar; aqui confere tamanho e formato pelo conteúdo (não pelo
 * nome do arquivo) e guarda no R2 com nome novo a cada troca, para nenhum cache mostrar a foto antiga.
 */
@Service
public class ProductImageService {
    static final long MAX_BYTES = 2 * 1024 * 1024;
    static final String TOO_BIG = "A foto pode ter no máximo 2 MB.";
    static final String INVALID = "Envie uma foto em JPEG ou PNG.";
    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);

    private final ProductService products;
    private final ImageStorage storage;

    public ProductImageService(ProductService products, ImageStorage storage) {
        this.products = products;
        this.storage = storage;
    }

    public ProductResponse upload(UUID storeId, UUID productId, byte[] bytes) {
        if (!storage.enabled()) {
            throw new BusinessRuleException(ImageStorage.DISABLED);
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessRuleException(TOO_BIG);
        }
        String extension = extension(bytes);
        String old = products.get(storeId, productId).imageUrl();
        String url = storage.put("stores/" + storeId + "/products/" + productId + "/" + UUID.randomUUID() + "."
                + extension, bytes, extension.equals("png") ? "image/png" : "image/jpeg");
        ProductResponse product = products.changeImage(storeId, productId, url);
        forget(old);
        return product;
    }

    public ProductResponse remove(UUID storeId, UUID productId) {
        String old = products.get(storeId, productId).imageUrl();
        ProductResponse product = products.changeImage(storeId, productId, null);
        forget(old);
        return product;
    }

    private void forget(String url) {
        try {
            storage.delete(url);
        } catch (RuntimeException e) {
            log.warn("Foto antiga não apagada do R2 ({}): {}", url, e.getMessage());
        }
    }

    static String extension(byte[] bytes) {
        if (bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "png";
        }
        throw new BusinessRuleException(INVALID);
    }
}
