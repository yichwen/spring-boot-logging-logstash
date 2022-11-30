package io.tao.logging.demo.controller;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
@RestController
public class TestController {
    
    @GetMapping("/get")
    public Map<String, Object> get() {
        Map<String, Object> map = new HashMap<>();
        map.put("path", "/get");
        map.put("method", HttpMethod.GET);
        map.put("status", HttpStatus.OK);
        return map;
    }
    
    @PostMapping("/post")
    public Map<String, Object> post(@RequestBody Map<String, Object> payload) {
        Map<String, Object> map = new HashMap<>();
        map.put("path", "/post");
        map.put("method", HttpMethod.POST);
        map.put("status", HttpStatus.OK);
        map.put("payload", payload);
        return map;
    }

}
