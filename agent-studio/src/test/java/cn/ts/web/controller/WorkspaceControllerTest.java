package cn.ts.web.controller;

import cn.ts.web.workspace.controller.WorkspaceController;
import cn.ts.web.workspace.service.WorkspaceArchiveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkspaceController.class)
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkspaceArchiveService workspaceArchiveService;

    @Test
    void exportWorkspace_ShouldReturnZip() throws Exception {
        when(workspaceArchiveService.exportWorkspace()).thenReturn(new byte[]{1,2,3});

        mockMvc.perform(get("/api/workspace/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/zip"));
    }

    @Test
    void importWorkspace_ShouldReturnSummary() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "workspace.zip", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{1,2,3});
        when(workspaceArchiveService.importWorkspace(any(), any())).thenReturn(2);

        mockMvc.perform(multipart("/api/workspace/import")
                        .file(file)
                        .param("strategy", "merge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedFiles").value(2));
    }
}
