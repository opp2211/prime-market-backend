package ru.maltsev.primemarketbackend.test.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {
    @GetMapping("/public")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/secure")
    public ResponseEntity<String> secure() {
        return ResponseEntity.ok("ok");
    }
}
