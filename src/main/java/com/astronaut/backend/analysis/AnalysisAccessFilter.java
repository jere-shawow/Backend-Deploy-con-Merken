package com.astronaut.backend.analysis;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Server-to-server secret. Never send this token to a browser or Android. */
@Component
public class AnalysisAccessFilter extends OncePerRequestFilter {
    private final AnalysisProperties properties;
    public AnalysisAccessFilter(AnalysisProperties properties) { this.properties=properties; }
    @Override protected boolean shouldNotFilter(HttpServletRequest r) {
        String path=r.getServletPath();
        return !(path.equals("/api/analysis") || path.startsWith("/api/analysis/"));
    }
    @Override protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s,
                                              FilterChain chain) throws ServletException, IOException {
        String secret=properties.apiToken();
        if (!properties.enabled() || secret==null || secret.length()<32) {
            s.sendError(503,"Ground analysis is not configured"); return;
        }
        String supplied=r.getHeader("X-Analysis-Token");
        if (supplied==null || supplied.length()>512 || !MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) {
            s.sendError(401,"Authentication required"); return;
        }
        if (r.getContentLengthLong()>32768) { s.sendError(413,"Request too large"); return; }
        chain.doFilter(r,s);
    }
}
