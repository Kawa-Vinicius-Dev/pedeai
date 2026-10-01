package com.pedeai.catalog.service;

import com.pedeai.catalog.dto.ProductResponse;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.storage.ImageStorage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductImageServiceTest {
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    private final ProductService products = mock(ProductService.class);
    private final ImageStorage storage = mock(ImageStorage.class);
    private final ProductImageService service = new ProductImageService(products, storage);

    @Test
    void storesTheJpegUnderANewNameAndDeletesTheOldOne() {
        when(storage.enabled()).thenReturn(true);
        when(products.get(STORE_ID, PRODUCT)).thenReturn(product("https://fotos.test/antiga.jpg"));
        when(storage.put(any(), any(), any())).thenReturn("https://fotos.test/nova.jpg");

        service.upload(STORE_ID, PRODUCT, JPEG);

        verify(storage).put(argThat(key -> key.startsWith("stores/" + STORE_ID + "/products/" + PRODUCT + "/")
                && key.endsWith(".jpg")), eq(JPEG), eq("image/jpeg"));
        verify(products).changeImage(STORE_ID, PRODUCT, "https://fotos.test/nova.jpg");
        verify(storage).delete("https://fotos.test/antiga.jpg");
    }

    @Test
    void rejectsWhatIsNotAPhotoOrIsTooBigAndWorksOnlyWithR2Configured() {
        assertThatThrownBy(() -> service.upload(STORE_ID, PRODUCT, JPEG)).hasMessage(ImageStorage.DISABLED);
        when(storage.enabled()).thenReturn(true);
        assertThatThrownBy(() -> service.upload(STORE_ID, PRODUCT, "<svg onload=x>".getBytes()))
                .isInstanceOf(BusinessRuleException.class).hasMessage(ProductImageService.INVALID);
        assertThatThrownBy(() -> service.upload(STORE_ID, PRODUCT, new byte[(int) ProductImageService.MAX_BYTES + 1]))
                .hasMessage(ProductImageService.TOO_BIG);
        verify(storage, never()).put(any(), any(), any());
    }

    private static ProductResponse product(String imageUrl) {
        return new ProductResponse(PRODUCT, UUID.randomUUID(), null, "X-Burger", null, 2990, null, null, List.of(),
                true, true, true, null, imageUrl);
    }
}
