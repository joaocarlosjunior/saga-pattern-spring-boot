package com.joaocarlos.products.service;

import com.joaocarlos.core.dto.Product;
import com.joaocarlos.core.exceptions.ProductInsufficientQuantityException;
import com.joaocarlos.products.dao.jpa.entity.ProductEntity;
import com.joaocarlos.products.dao.jpa.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public Product reserve(Product desiredProduct, UUID orderId) {
        LOGGER.info("Reserving productId: {} with quantity: {} for orderId: {}",
                desiredProduct.getId(), desiredProduct.getQuantity(), orderId);
        ProductEntity productEntity = productRepository.findById(desiredProduct.getId()).orElseThrow();
        if (desiredProduct.getQuantity() > productEntity.getQuantity()) {
            LOGGER.warn("Insufficient stock for productId: {} (Available: {}, Requested: {}) for orderId: {}",
                    productEntity.getId(), productEntity.getQuantity(), desiredProduct.getQuantity(), orderId);
            throw new ProductInsufficientQuantityException(productEntity.getId(), orderId);
        }

        productEntity.setQuantity(productEntity.getQuantity() - desiredProduct.getQuantity());
        productRepository.save(productEntity);
        LOGGER.info("Successfully reserved productId: {} for orderId: {}. Remaining stock: {}",
                desiredProduct.getId(), orderId, productEntity.getQuantity());

        var reservedProduct = new Product();
        BeanUtils.copyProperties(productEntity, reservedProduct);
        reservedProduct.setQuantity(desiredProduct.getQuantity());
        return reservedProduct;
    }

    @Override
    public void cancelReservation(Product productToCancel, UUID orderId) {
        LOGGER.info("Cancelling reservation of productId: {} (quantity: {}) for orderId: {}",
                productToCancel.getId(), productToCancel.getQuantity(), orderId);
        ProductEntity productEntity = productRepository.findById(productToCancel.getId()).orElseThrow();
        productEntity.setQuantity(productEntity.getQuantity() + productToCancel.getQuantity());
        productRepository.save(productEntity);
        LOGGER.info("Successfully restored stock for productId: {}. New stock: {}",
                productToCancel.getId(), productEntity.getQuantity());
    }

    @Override
    public Product save(Product product) {
        LOGGER.info("Saving new product with name: {}, price: {}, quantity: {}",
                product.getName(), product.getPrice(), product.getQuantity());
        ProductEntity productEntity = new ProductEntity();
        productEntity.setName(product.getName());
        productEntity.setPrice(product.getPrice());
        productEntity.setQuantity(product.getQuantity());
        productRepository.save(productEntity);
        LOGGER.info("Product saved with id: {}", productEntity.getId());

        return new Product(productEntity.getId(), product.getName(), product.getPrice(), product.getQuantity());
    }

    @Override
    public List<Product> findAll() {
        LOGGER.info("Fetching all products from repository");
        List<Product> products = productRepository.findAll().stream()
                .map(entity -> new Product(entity.getId(), entity.getName(), entity.getPrice(), entity.getQuantity()))
                .collect(Collectors.toList());
        LOGGER.info("Retrieved {} product(s) from repository", products.size());
        return products;
    }
}
