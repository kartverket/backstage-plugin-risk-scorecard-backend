package no.kvros.helloworld

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController

class HelloWorldController(val helloworldService: HelloWorldService) {
    @GetMapping("/helloworld")
    fun getHelloWorld (): String = helloworldService.fetchHello()

}


