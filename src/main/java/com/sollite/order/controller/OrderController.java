package com.sollite.order.controller;

import com.sollite.global.util.AuthUtil;
import com.sollite.order.dto.AmendOrderRequest;
import com.sollite.order.dto.OrderDetailResponse;
import com.sollite.order.dto.OrderResponse;
import com.sollite.order.dto.PlaceOrderRequest;
import com.sollite.order.service.OrderService;
import com.sollite.user.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 주문 접수 (매수/매도, 지정가/시장가/현재가)
     */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            Authentication authentication,
            @Valid @RequestBody PlaceOrderRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(userId, request));
    }

    /**
     * 주문내역 조회
     * @param status ALL | PENDING | FILLED | CANCELLED (기본값: ALL)
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(
            Authentication authentication,
            @RequestParam(defaultValue = "ALL") String status) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(orderService.getOrders(userId, status));
    }

    /**
     * 주문 단건 + 체결내역 조회
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(
            Authentication authentication,
            @PathVariable Long orderId) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(orderService.getOrderDetail(userId, orderId));
    }

    /**
     * 주문 정정 (PENDING 상태만 가능)
     */
    @PatchMapping("/{orderId}")
    public ResponseEntity<OrderResponse> amendOrder(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody AmendOrderRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(orderService.amendOrder(userId, orderId, request));
    }

    /**
     * 주문 취소
     */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<OrderResponse> cancelOrder(
            Authentication authentication,
            @PathVariable Long orderId) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(orderService.cancelOrder(userId, orderId));
    }

    /**
     * 미체결 주문 전체 취소
     */
    @DeleteMapping("/pending/all")
    public ResponseEntity<MessageResponse> cancelAllPendingOrders(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        int count = orderService.cancelAllPendingOrders(userId);
        return ResponseEntity.ok(new MessageResponse(count + "건의 미체결 주문이 취소되었습니다."));
    }
}
