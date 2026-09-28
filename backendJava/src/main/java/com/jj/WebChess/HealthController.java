package com.jj.WebChess;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class HealthController {

    @GetMapping("health")
    public ResponseEntity<Map<String, String>> getHealth() {
        return ResponseEntity.status(HttpStatus.OK).body(Map.of("status", "ok"));
    }
}