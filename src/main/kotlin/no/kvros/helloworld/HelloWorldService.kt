package no.kvros.helloworld

import org.springframework.stereotype.Service

@Service
class HelloWorldService {
    fun fetchHello(): String = "hello world"
}