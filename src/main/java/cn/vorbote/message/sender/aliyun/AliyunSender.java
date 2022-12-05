package cn.vorbote.message.sender.aliyun;

import cn.vorbote.core.exceptions.NotImplementedException;
import cn.vorbote.message.auth.UserProfile;
import cn.vorbote.message.model.BatchMessageRequest;
import cn.vorbote.message.model.MessageRequest;
import cn.vorbote.message.model.MessageResponse;
import cn.vorbote.message.sender.IMessageSender;
import cn.vorbote.message.sender.aliyun.config.AliyunConfig;
import cn.vorbote.message.sender.aliyun.config.AliyunRegion;
import cn.vorbote.message.sender.aliyun.models.SendMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * AliyunSender<br>
 * Created at 04/12/2022 19:41
 *
 * @author theod
 */
@Slf4j
public final class AliyunSender implements IMessageSender<Map<String, Object>> {

    private final UserProfile userProfile;

    private final String sign;

    private final ObjectMapper objectMapper;

    private final OkHttpClient okHttpClient;

    public AliyunSender(UserProfile userProfile, String sign, ObjectMapper objectMapper, OkHttpClient okHttpClient) {
        this.userProfile = userProfile;
        this.sign = sign;
        this.objectMapper = objectMapper;
        this.okHttpClient = okHttpClient;
    }

    public AliyunSender(UserProfile userProfile, String sign, ObjectMapper objectMapper) {
        this(userProfile, sign, objectMapper, new OkHttpClient());
    }

    public AliyunSender(UserProfile userProfile, String sign, OkHttpClient okHttpClient) {
        this(userProfile, sign, new ObjectMapper(), okHttpClient);
    }

    public AliyunSender(UserProfile userProfile, String sign) {
        this(userProfile, sign, new ObjectMapper(), new OkHttpClient());
    }

    private static String specialUrlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    private static String sign(String accessSecret, String stringToSign) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(accessSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signData);
    }

    public MessageResponse send(AliyunRegion region, MessageRequest<Map<String, Object>> request) throws Exception {
        final var df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(new SimpleTimeZone(0, "GMT"));// 这里一定要设置GMT时区

        final var timestamp = df.format(new Date());

        var payload = new SendMessageRequest()
                .setParams(request.getParams())
                .setTemplateId(request.getTemplateId())
                .setSign(sign)
                .setReceiver(request.getReceiver());

        var params = new HashMap<String, String>();
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureNonce", java.util.UUID.randomUUID().toString());
        params.put("AccessKeyId", userProfile.secretId());
        params.put("SignatureVersion", "1.0");
        params.put("Timestamp", timestamp);
        params.put("Format", "JSON");
        params.put("Action", AliyunConfig.ACTION);
        params.put("Version", "2017-05-25");
        params.put("RegionId", region.getRegion());

        // 4. 参数KEY排序
        var sortParams = new TreeMap<>(params) {{
            put("PhoneNumbers", payload.getReceiver());
            put("SignName", payload.getSign());
            put("TemplateParam", objectMapper.writeValueAsString(payload.getParams()));
            put("TemplateCode", payload.getTemplateId());
        }};
        // 5. 构造待签名的字符串
        var it = sortParams.keySet().iterator();
        var sortQueryStringTmp = new StringBuilder();
        while (it.hasNext()) {
            var key = it.next();
            sortQueryStringTmp.append("&").append(specialUrlEncode(key)).append("=").append(specialUrlEncode(sortParams.get(key)));
        }
        var sortedQueryString = sortQueryStringTmp.substring(1);
        var stringToSign = "POST" + "&" +
                specialUrlEncode("/") + "&" +
                specialUrlEncode(sortedQueryString);
        log.trace("Building string to sign, value is [{}]", stringToSign);

        // 6. 签名最后也要做特殊URL编码
        var signature = specialUrlEncode(sign(userProfile.secretKey() + "&", stringToSign));

        var urlQueryBuilder = new StringBuilder();
        params.forEach((key, value) -> urlQueryBuilder.append("&").append(specialUrlEncode(key)).append("=").append(specialUrlEncode(sortParams.get(key))));

        // 最终打印出合法GET请求的URL
        final var url = "https://" + region.getEndpoint() + "/?Signature=" + signature + urlQueryBuilder;

        var jsonPayload = objectMapper.writeValueAsString(payload);
        log.trace("Preparing request body, value is [{}]", jsonPayload);

        log.trace("Sending request to {}", url);
        var webCall = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonPayload, MediaType.parse("application/x-www-form-urlencoded")))
                .build();
        try (var webResp = okHttpClient.newCall(webCall).execute()) {
            var jsonResp = Optional.of(webResp).map(Response::body).map((item) -> {
                        try {
                            return item.string();
                        } catch (IOException e) {
                            log.error(e.getMessage());
                            return null;
                        }
                    })
                    .orElse("{}");
            log.trace(jsonResp);
        }

        return null;
    }

    /**
     * Send a SMS.
     *
     * @param request The data to send a sms.
     * @return The response data from sent message.
     * @throws JsonProcessingException ObjectMapper could make this exception because
     *                                 of the data is not serializable.
     */
    @Override
    public MessageResponse send(MessageRequest<Map<String, Object>> request) throws IOException {
        return null;
    }

    /**
     * Send several messages to multiple recipients.
     *
     * @param request The data to send a sms.
     * @return The response data from sent message.
     */
    @Override
    public MessageResponse batchSend(BatchMessageRequest<Map<String, Object>> request) {
        throw new NotImplementedException("""
                This method is not implemented yet. Please hold on for several versions.
                """);
    }
}
