package com.lzj.railway.pay.channel;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.lzj.railway.framework.convention.exception.ServiceException;
import com.lzj.railway.pay.common.PayErrorCode;
import com.lzj.railway.pay.config.AliPayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

/** 支付宝电脑网站支付渠道，并负责异步通知的签名验证。 */
@Component
@RequiredArgsConstructor
public class AliPayPageChannel {
    private final AliPayProperties properties;

    /** 生成可直接提交到支付宝收银台的 HTML 表单。 */
    public String createPaymentPage(String paySn, Integer amountInFen, String subject) {
        validateConfiguration();
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(paySn);
        model.setTotalAmount(BigDecimal.valueOf(amountInFen, 2).toPlainString());
        model.setSubject(subject);
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(properties.getNotifyUrl());
        request.setBizModel(model);
        try {
            AlipayTradePagePayResponse response = client().pageExecute(request);
            if (!response.isSuccess()) {
                throw new ServiceException(response.getSubMsg(), PayErrorCode.PAYMENT_CHANNEL_FAILED);
            }
            return response.getBody();
        } catch (AlipayApiException exception) {
            throw new ServiceException("调用支付宝支付失败", exception, PayErrorCode.PAYMENT_CHANNEL_FAILED);
        }
    }

    /** 使用支付宝公钥验证通知签名，验签不通过的回调绝不能改变本地状态。 */
    public boolean verifyCallback(Map<String, String> parameters) {
        try {
            return AlipaySignature.rsaCheckV1(parameters, properties.getAlipayPublicKey(),
                    properties.getCharset(), properties.getSignType());
        } catch (AlipayApiException exception) {
            return false;
        }
    }

    /** 调用支付宝退款接口，退款请求号保证第三方侧幂等。 */
    public void refund(String paySn, String tradeNo, Integer amountInFen, String refundRequestNo) {
        validateConfiguration();
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(paySn);
        model.setTradeNo(tradeNo);
        model.setRefundAmount(BigDecimal.valueOf(amountInFen, 2).toPlainString());
        model.setOutRequestNo(refundRequestNo);
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizModel(model);
        try {
            AlipayTradeRefundResponse response = client().execute(request);
            if (!response.isSuccess()) {
                throw new ServiceException(response.getSubMsg(), PayErrorCode.PAYMENT_CHANNEL_FAILED);
            }
        } catch (AlipayApiException exception) {
            throw new ServiceException("调用支付宝退款失败", exception, PayErrorCode.PAYMENT_CHANNEL_FAILED);
        }
    }

    private AlipayClient client() throws AlipayApiException {
        return new DefaultAlipayClient(properties.getServerUrl(), properties.getAppId(), properties.getPrivateKey(),
                properties.getFormat(), properties.getCharset(), properties.getAlipayPublicKey(), properties.getSignType());
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getPrivateKey())
                || !StringUtils.hasText(properties.getAlipayPublicKey()) || !StringUtils.hasText(properties.getServerUrl())
                || !StringUtils.hasText(properties.getNotifyUrl())) {
            throw new ServiceException("支付宝配置不完整", PayErrorCode.PAYMENT_CHANNEL_FAILED);
        }
    }
}
