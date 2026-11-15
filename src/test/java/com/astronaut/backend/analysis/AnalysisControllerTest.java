package com.astronaut.backend.analysis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static com.astronaut.backend.analysis.AnalysisTypes.*;
import com.astronaut.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

class AnalysisControllerTest {
    @Test void invalidDtoFailsBeforeModelInvocation() throws Exception {
        var service=mock(AnalysisService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new AnalysisController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/analysis/preview").contentType(MediaType.APPLICATION_JSON)
            .content("{\"readings\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/analysis/preview").contentType(MediaType.APPLICATION_JSON)
            .content("{\"readings\":[{\"metric\":\"heart_rate\",\"value\":75}]}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void successfulResultMustNotBeCached() throws Exception {
        var service=mock(AnalysisService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new AnalysisController(service)).build();
        mvc.perform(post("/api/analysis/astronauts/1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"allowRemoteAnalysis\":true}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(service).stored(1L,new StoredRequest(true,null));
    }
}
