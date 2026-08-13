package com.joaocarlos.products.web.controller;

import com.joaocarlos.core.dto.Product;
import com.joaocarlos.products.dto.ProductCreationRequest;
import com.joaocarlos.products.dto.ProductCreationResponse;
import com.joaocarlos.products.service.ProductService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductsController.class);

    private final ProductService productService;

    public ProductsController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<Product> findAll() {
        LOGGER.info("REST request to fetch all products");
        List<Product> products = productService.findAll();
        LOGGER.info("Fetched {} product(s)", products.size());
        return products;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductCreationResponse save(@RequestBody @Valid ProductCreationRequest request) {
        LOGGER.info("REST request to create product with name: {}, price: {}, quantity: {}",
                request.getName(), request.getPrice(), request.getQuantity());
        var product = new Product();
        BeanUtils.copyProperties(request, product);
        Product result = productService.save(product);

        var productCreationResponse = new ProductCreationResponse();
        BeanUtils.copyProperties(result, productCreationResponse);
        LOGGER.info("Product created successfully with id: {}", productCreationResponse.getId());
        return productCreationResponse;
    }
}
