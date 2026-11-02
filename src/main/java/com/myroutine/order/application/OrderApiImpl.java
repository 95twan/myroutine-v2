package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.order.api.ConfirmedLineInfo;
import com.myroutine.order.api.OrderApi;
import com.myroutine.order.domain.OrderErrorCode;
import com.myroutine.order.domain.OrderLine;
import com.myroutine.order.domain.OrderLineStatus;
import com.myroutine.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class OrderApiImpl implements OrderApi {

    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public ConfirmedLineInfo getConfirmedLine(UUID memberId, UUID orderLineId) {
        OrderLine line = orderRepository.findLineByIdAndMemberId(orderLineId, memberId).orElseThrow(
                () -> new BusinessException(OrderErrorCode.ORDER_LINE_NOT_FOUND)
        );

        if (line.getStatus() != OrderLineStatus.CONFIRMED) {
            throw new BusinessException(OrderErrorCode.REVIEW_NOT_ALLOWED);
        }

        return new ConfirmedLineInfo(line.getId(), line.getProductId());
    }
}
