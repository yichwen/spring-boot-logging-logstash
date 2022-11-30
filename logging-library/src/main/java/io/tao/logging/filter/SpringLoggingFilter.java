package io.tao.logging.filter;

import io.tao.logging.util.UniqueIDGenerator;
import io.tao.logging.wrapper.SpringRequestWrapper;
import io.tao.logging.wrapper.SpringResponseWrapper;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import static net.logstash.logback.argument.StructuredArguments.value;

public class SpringLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringLoggingFilter.class);
    private final UniqueIDGenerator generator;
    private final String ignorePatterns;
    private final boolean logHeaders;

    @Autowired
    ApplicationContext context;

    public SpringLoggingFilter(UniqueIDGenerator generator, String ignorePatterns, boolean logHeaders) {
        this.generator = generator;
        this.ignorePatterns = ignorePatterns;
        this.logHeaders = logHeaders;
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (ignorePatterns != null && request.getRequestURI().matches(ignorePatterns)) {
            chain.doFilter(request, response);
        } else {
            
            generator.generateAndSetMDC(request);
            try {
                getHandlerMethod(request);
            } catch (Exception e) {
                LOGGER.trace("Cannot get handler method");
            }
            
            final long startTime = System.currentTimeMillis();
            final SpringRequestWrapper wrappedRequest = new SpringRequestWrapper(request);
            
            // 请求日志处理
            if (logHeaders) {
                LOGGER.info("Request: method={}, uri={}, payload={}, headers={}, audit={}",
                        wrappedRequest.getMethod(),
                        wrappedRequest.getRequestURI(),
                        IOUtils.toString(wrappedRequest.getInputStream(), wrappedRequest.getCharacterEncoding()),
                        wrappedRequest.getAllHeaders(),
                        value("audit", true));
            } else {
                LOGGER.info("Request: method={}, uri={}, payload={}, audit={}",
                        wrappedRequest.getMethod(),
                        wrappedRequest.getRequestURI(),
                        IOUtils.toString(wrappedRequest.getInputStream(), wrappedRequest.getCharacterEncoding()),
                        value("audit", true));
            }
            
            final SpringResponseWrapper wrappedResponse = new SpringResponseWrapper(response);
            wrappedResponse.setHeader("X-Request-ID", MDC.get("X-Request-ID"));
            wrappedResponse.setHeader("X-Correlation-ID", MDC.get("X-Correlation-ID"));

            try {
                chain.doFilter(wrappedRequest, wrappedResponse);
            } catch (Exception e) {
                logResponse(startTime, wrappedResponse, 500);
                throw e;
            }
            logResponse(startTime, wrappedResponse, wrappedResponse.getStatus());
        }
    }

    // 响应日志处理
    private void logResponse(long startTime, SpringResponseWrapper wrappedResponse, int overriddenStatus) throws IOException {
        final long duration = System.currentTimeMillis() - startTime;
        wrappedResponse.setCharacterEncoding("UTF-8");
        if (logHeaders) {
            LOGGER.info("Response({} ms): status={}, payload={}, headers={}, audit={}", 
                    value("X-Response-Time", duration),
                    value("X-Response-Status", overriddenStatus), 
                    IOUtils.toString(wrappedResponse.getContentAsByteArray(), wrappedResponse.getCharacterEncoding()), 
                    wrappedResponse.getAllHeaders(), 
                    value("audit", true));
        } else {
            LOGGER.info("Response({} ms): status={}, payload={}, audit={}", 
                    value("X-Response-Time", duration),
                    value("X-Response-Status", overriddenStatus),
                    IOUtils.toString(wrappedResponse.getContentAsByteArray(), wrappedResponse.getCharacterEncoding()), 
                    value("audit", true));
        }
    }

    private void getHandlerMethod(HttpServletRequest request) throws Exception {
        // 获取 RequestMappingHandlerMapping 实例
        RequestMappingHandlerMapping mapping = (RequestMappingHandlerMapping) context.getBean("requestMappingHandlerMapping");
//        Map<RequestMappingInfo, HandlerMethod> handlerMethods = mapping.getHandlerMethods();
        // 根据请求获取 HandlerExecutionChain，再获取 HandlerMethod，将 HandlerMethod 的信息保存到 MDC 中
        HandlerExecutionChain handler = mapping.getHandler(request);
        if (Objects.nonNull(handler)) {
            HandlerMethod handlerMethod = (HandlerMethod) handler.getHandler();
            MDC.put("X-Operation-Name", handlerMethod.getBeanType().getSimpleName() + "." + handlerMethod.getMethod().getName());
        }
    }

}
