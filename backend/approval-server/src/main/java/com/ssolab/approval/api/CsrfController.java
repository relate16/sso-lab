package com.ssolab.approval.api;
import java.util.Map;import org.springframework.security.web.csrf.CsrfToken;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1") public class CsrfController {@GetMapping("/csrf") Map<String,String> csrf(CsrfToken token){return Map.of("headerName",token.getHeaderName(),"parameterName",token.getParameterName(),"token",token.getToken());}}
