package com.lzj.railway.pay.controller;

import com.lzj.railway.pay.channel.AliPayPageChannel;
import com.lzj.railway.pay.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** 支付宝服务端异步通知入口，必须由网关匿名放行但严格验签。 */
@RestController
@RequestMapping("/api/pay/callback")
@RequiredArgsConstructor
public class AliPayCallbackController {
    private static final DateTimeFormatter PAYMENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final AliPayPageChannel aliPayPageChannel;
    private final PaymentService paymentService;

    /**
     * 返回 success 后支付宝才会停止重试。验签、金额或状态校验任一失败都返回 failure。
     */
    @PostMapping("/alipay")
    public String callback(@RequestParam Map<String, String> parameters) {
        if (!aliPayPageChannel.verifyCallback(parameters)) {
            return "failure";
        }
        try {
            String paySn = parameters.get("out_trade_no");
            String tradeNo = parameters.get("trade_no");
            String tradeStatus = parameters.get("trade_status");
            Integer amount = toFen(parameters.get("receipt_amount"));
            LocalDateTime paymentTime = LocalDateTime.parse(parameters.get("gmt_payment"), PAYMENT_TIME_FORMATTER);
            if (!StringUtils.hasText(paySn) || !StringUtils.hasText(tradeNo) || amount == null) {
                return "failure";
            }
            return paymentService.completeAliPay(paySn, tradeNo, amount, paymentTime, tradeStatus)
                    ? "success" : "failure";
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            return "failure";
        }
    }

    /** 将支付宝元金额无损转换为本系统使用的分。 */
    private Integer toFen(String amount) {
        if (!StringUtils.hasText(amount)) {
            return null;
        }
        return new BigDecimal(amount).movePointRight(2).intValueExact();
    }
}
