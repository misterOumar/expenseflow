package dev.misteroumar.expenseflow.receipt;

import dev.misteroumar.expenseflow.receipt.dto.ReceiptResponse;
import org.mapstruct.Mapper;

@Mapper
public interface ReceiptMapper {

    ReceiptResponse toResponse(Receipt receipt);
}
