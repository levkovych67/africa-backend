package com.africe.backend.product.service;

import com.africe.backend.common.dto.ProductResponse;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.model.Product;
import com.africe.backend.common.model.ProductAttribute;
import com.africe.backend.common.model.ProductStatus;
import com.africe.backend.common.model.ProductVariant;
import com.africe.backend.product.repository.ArtistRepository;
import com.africe.backend.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository productRepository;
    @Mock ArtistRepository artistRepository;
    @Mock MongoTemplate mongoTemplate;
    @InjectMocks ProductService productService;

    private Product createTestProduct() {
        return Product.builder()
                .id("prod1")
                .slug("test-tshirt")
                .title("Test T-Shirt")
                .description("A test product")
                .basePrice(BigDecimal.valueOf(500))
                .status(ProductStatus.ACTIVE)
                .attributes(List.of(new ProductAttribute("Size", List.of("S", "M", "L"))))
                .variants(List.of(ProductVariant.builder()
                        .sku("TSHIRT-M")
                        .attributes(Map.of("Size", "M"))
                        .priceModifier(BigDecimal.ZERO)
                        .stock(10)
                        .build()))
                .images(List.of("img1.jpg"))
                .artistId("artist1")
                .build();
    }

    @Test
    void listActiveProducts_returnsPage() {
        Product product = createTestProduct();
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findByStatus(eq(ProductStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(page);

        Page<ProductResponse> result = productService.listActiveProducts(null, null, "newest", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).slug()).isEqualTo("test-tshirt");
    }

    @Test
    void listActiveProducts_withSearch() {
        Product product = createTestProduct();
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findByStatusAndTitleContainingIgnoreCase(
                eq(ProductStatus.ACTIVE), eq("shirt"), any(Pageable.class)))
                .thenReturn(page);

        Page<ProductResponse> result = productService.listActiveProducts("shirt", null, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void listActiveProducts_withArtistId() {
        Product product = createTestProduct();
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findByStatusAndArtistId(
                eq(ProductStatus.ACTIVE), eq("artist1"), any(Pageable.class)))
                .thenReturn(page);

        Page<ProductResponse> result = productService.listActiveProducts(null, "artist1", null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void listActiveProducts_withSearchAndArtist() {
        Product product = createTestProduct();
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findByStatusAndTitleContainingIgnoreCaseAndArtistId(
                eq(ProductStatus.ACTIVE), eq("shirt"), eq("artist1"), any(Pageable.class)))
                .thenReturn(page);

        Page<ProductResponse> result = productService.listActiveProducts("shirt", "artist1", null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getBySlug_found() {
        Product product = createTestProduct();
        when(productRepository.findBySlug("test-tshirt")).thenReturn(Optional.of(product));

        ProductResponse response = productService.getBySlug("test-tshirt");

        assertThat(response.id()).isEqualTo("prod1");
        assertThat(response.title()).isEqualTo("Test T-Shirt");
        assertThat(response.basePrice()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(response.variants()).hasSize(1);
        assertThat(response.variants().get(0).sku()).isEqualTo("TSHIRT-M");
    }

    @Test
    void getBySlug_notFound_throws() {
        when(productRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getBySlug("nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void toResponse_handlesNullVariantsAndAttributes() {
        Product product = Product.builder()
                .id("p1").slug("s").title("T").basePrice(BigDecimal.TEN)
                .status(ProductStatus.ACTIVE)
                .build();

        ProductResponse response = productService.toResponse(product);

        assertThat(response.variants()).isEmpty();
        assertThat(response.attributes()).isEmpty();
    }

    @Test
    void listActiveProducts_sortByPriceAsc() {
        Page<Product> page = new PageImpl<>(List.of());
        when(productRepository.findByStatus(eq(ProductStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(page);

        productService.listActiveProducts(null, null, "price_asc", 0, 20);
        // No assertion needed — verifying no exception for sort param
    }

    @Test
    void listActiveProducts_sortByPriceDesc() {
        Page<Product> page = new PageImpl<>(List.of());
        when(productRepository.findByStatus(eq(ProductStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(page);

        productService.listActiveProducts(null, null, "price_desc", 0, 20);
    }
}
