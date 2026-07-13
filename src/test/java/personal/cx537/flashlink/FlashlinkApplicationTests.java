package personal.cx537.flashlink;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import personal.cx537.flashlink.entity.ShortLink;
import personal.cx537.flashlink.mapper.ShortLinkMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FlashlinkApplicationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ShortLinkMapper shortLinkMapper;


    @Test
    void shouldReturnRedirectForValidCode() throws Exception {
        int i = 0;
        while (i++ < 100) {
            mockMvc.perform(get("/s/2TD6pyi6BbE"))
                    .andExpect(status().is(429));
        }
    }

    @Test
    void tt() throws Exception {
        // 前 2 次可能被放行（由 burst 行为决定），后面应该全部被限流
        int rateLimitedCount = 0;
        for (int i = 0; i < 100; i++) {
            MvcResult result = mockMvc.perform(get("/s/2TD6pyi6BbE"))
                    .andReturn();
            if (result.getResponse().getStatus() == 302) {
                rateLimitedCount++;
            }
        }
        // 100 次请求中，至少 98 次应该被限流（前 1~2 次可能被放行）
        assertTrue(rateLimitedCount >= 98, "限流次数不足，实际限流: " + rateLimitedCount);
    }

    @Test
    void tt2() throws Exception {
        List<ShortLink> shortLinks = shortLinkMapper.selectList(null);
        System.out.println(shortLinks);
    }
}
