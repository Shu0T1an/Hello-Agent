package cn.ts.web.controller;

import cn.ts.web.channel.controller.ChannelManagementController;
import cn.ts.web.channel.dto.ChannelConfigDTO;
import cn.ts.web.channel.runtime.ChannelRuntimeManager;
import cn.ts.web.channel.service.ChannelConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChannelManagementController.class)
class ChannelManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChannelConfigService channelConfigService;

    @MockBean
    private ChannelRuntimeManager channelRuntimeManager;

    private ChannelConfigDTO sample;

    @BeforeEach
    void setUp() {
        sample = new ChannelConfigDTO();
        sample.setId(1L);
        sample.setChannelName("ding-talk-main");
        sample.setChannelType("dingtalk");
        sample.setEnabled(true);
    }

    @Test
    void createChannelConfig_ShouldReturnSuccess() throws Exception {
        when(channelConfigService.create(any(ChannelConfigDTO.class))).thenReturn(sample);

        mockMvc.perform(post("/api/channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sample)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelName").value("ding-talk-main"));

        verify(channelConfigService).create(any(ChannelConfigDTO.class));
    }

    @Test
    void listChannelConfigs_ShouldReturnList() throws Exception {
        when(channelConfigService.list()).thenReturn(List.of(sample));

        mockMvc.perform(get("/api/channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].channelType").value("dingtalk"));
    }
}
