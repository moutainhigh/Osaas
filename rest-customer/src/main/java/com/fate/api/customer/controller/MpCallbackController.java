package com.fate.api.customer.controller;

import com.fate.api.customer.config.wx.WxMpConfiguration;
import com.fate.api.customer.util.CurrentApplicationUtil;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.mp.api.WxMpMessageRouter;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

/**
 * @program: parent
 * @description: 微信公众号客服消息回调
 * @author: chenyixin
 * @create: 2019-05-30 00:29
 **/
@ApiIgnore//使用该注解忽略这个API
@RestController
@RequestMapping("/callback/{applicationId}/v1/mp/massage")
@Slf4j
public class MpCallbackController {

    @GetMapping(produces = "text/plain;charset=utf-8")
    public String authGet(
                          @RequestParam(name = "signature") String signature,
                          @RequestParam(name = "timestamp") String timestamp,
                          @RequestParam(name = "nonce") String nonce,
                          @RequestParam(name = "echostr") String echostr) {
        String appid= CurrentApplicationUtil.getMerchantApplication().getAppId();
        log.info("接收到来自微信服务器的认证消息：[{}, {}, {}, {}]", signature, timestamp, nonce, echostr);

        Assert.isTrue(!StringUtils.isAnyBlank(signature, timestamp, nonce, echostr),"请求参数非法，请核实!");

        WxMpService wxMpService= WxMpConfiguration.getMpService(appid);
        Assert.notNull(wxMpService,String.format("未找到对应appid=[%s]的配置，请核实！", appid));

        if (wxMpService.checkSignature(timestamp, nonce, signature)) {
            return echostr;
        }
        return "非法请求";
    }

    @PostMapping(produces = "application/xml; charset=UTF-8")
    public String post(@RequestBody String requestBody,
                       @RequestParam("signature") String signature,
                       @RequestParam("timestamp") String timestamp,
                       @RequestParam("nonce") String nonce,
                       @RequestParam("openid") String openid,
                       @RequestParam(name = "encrypt_type", required = false) String encType,
                       @RequestParam(name = "msg_signature", required = false) String msgSignature) {
        log.info("接收微信请求：[openid=[{}], [signature=[{}], encType=[{}], msgSignature=[{}], timestamp=[{}], nonce=[{}], requestBody=[{}] ",
                openid, signature, encType, msgSignature, timestamp, nonce, requestBody);
        String appid= CurrentApplicationUtil.getMerchantApplication().getAppId();

        Assert.isTrue(!StringUtils.isAnyBlank(signature, timestamp, nonce, openid),"请求参数非法，请核实!");

        WxMpService wxMpService= WxMpConfiguration.getMpService(appid);
        Assert.notNull(wxMpService,String.format("未找到对应appid=[%s]的配置，请核实！", appid));

        if (!wxMpService.checkSignature(timestamp, nonce, signature)) {
            throw new IllegalArgumentException("非法请求，可能属于伪造的请求！");
        }
        WxMpMessageRouter wxMpMessageRouter=WxMpConfiguration.getMessageRouter(appid);
        Assert.notNull(wxMpMessageRouter,String.format("未找到对应appid=[%s]的消息配置，请核实！", appid));

        String out = null;
        if (encType == null) {// 明文传输的消息
            WxMpXmlMessage inMessage = WxMpXmlMessage.fromXml(requestBody);
            WxMpXmlOutMessage outMessage = wxMpMessageRouter.route(inMessage);
            if (outMessage == null) {
                return "";
            }
            out = outMessage.toXml();
        } else if ("aes".equalsIgnoreCase(encType)) { // aes加密的消息

            WxMpXmlMessage inMessage = WxMpXmlMessage.fromEncryptedXml(requestBody, wxMpService.getWxMpConfigStorage(), timestamp, nonce, msgSignature);
            log.info("微信回调消息解密后内容为：{} ", inMessage.toString());
            WxMpXmlOutMessage outMessage = wxMpMessageRouter.route(inMessage);
            if (outMessage == null) {
                return "";
            }
            out = outMessage.toEncryptedXml(wxMpService.getWxMpConfigStorage());
        }

        log.debug("组装回复信息：{}", out);
        return out;
    }
}
