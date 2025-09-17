package com.chae.promo.order.validator;

import com.chae.promo.order.dto.PurchaseItemDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

public class NoDuplicateProductCodesValidator
        implements ConstraintValidator<NoDuplicateProductCodes, List<PurchaseItemDTO>> {

    @Override
    public boolean isValid(List<PurchaseItemDTO> items, ConstraintValidatorContext ctx) {
        if (items == null || items.isEmpty()) return true;
        var codes = items.stream()
                .map(i -> i.getProductCode() == null ? null : i.getProductCode().trim())
                .filter(c -> c != null && !c.isEmpty())
                .toList();
        return codes.stream().distinct().count() == codes.size();
    }
}