import cn.vorbote.message.auth.UserProfile;
import cn.vorbote.message.model.MessageRequest;
import cn.vorbote.message.sender.aliyun.AliyunSender;
import cn.vorbote.message.sender.aliyun.config.AliyunRegion;
import org.junit.Test;

import java.util.HashMap;

/**
 * AliyunSenderTemplateTest<br>
 * Created at 05/12/2022 14:36
 *
 * @author theod
 */
public class AliyunSenderTemplateTest {

    @Test
    public void run() throws Exception {
        final var aliyunSender = new AliyunSender(
                UserProfile.createProfile("", ""),
                "阿里云短信测试");
        aliyunSender.send(AliyunRegion.HANGZHOU, MessageRequest.createRequest("SMS_154950909", "",
                new HashMap<>() {{
            put("code", "123456");
        }}));
    }

}
