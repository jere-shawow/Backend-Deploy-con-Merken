package com.astronaut.backend.analysis;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.util.concurrent.atomic.AtomicBoolean;

class AnalysisAccessFilterTest {
    private MockHttpServletResponse request(boolean enabled,String configured,String supplied,String path) throws Exception {
        var filter=new AnalysisAccessFilter(new AnalysisProperties(enabled,"us-east-1","amazon.nova-pro-v1:0",configured));
        var req=new MockHttpServletRequest("POST",path);req.setServletPath(path);
        if(supplied!=null)req.addHeader("X-Analysis-Token",supplied);
        var response=new MockHttpServletResponse();
        filter.doFilter(req,response,(r,s)->s.getWriter().write("reached"));return response;
    }
    @Test void defaultDisabledAndMissingSecretFailClosed() throws Exception {
        assertThat(request(false,"x".repeat(40),null,"/api/analysis/preview").getStatus()).isEqualTo(503);
        assertThat(request(true,"",null,"/api/analysis/preview").getStatus()).isEqualTo(503);
    }
    @Test void requiresCorrectServerToken() throws Exception {
        assertThat(request(true,"x".repeat(40),null,"/api/analysis/preview").getStatus()).isEqualTo(401);
        assertThat(request(true,"x".repeat(40),"wrong","/api/analysis/preview").getStatus()).isEqualTo(401);
        assertThat(request(true,"x".repeat(40),"x".repeat(40),"/api/analysis/preview").getContentAsString()).isEqualTo("reached");
    }
    @Test void existingRoutesRemainUnaffected() throws Exception {
        assertThat(request(false,"",null,"/healthz").getContentAsString()).isEqualTo("reached");
    }
}
