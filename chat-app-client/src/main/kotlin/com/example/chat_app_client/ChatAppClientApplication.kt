package com.example.chat_app_client

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ChatAppClientApplication

fun main(args: Array<String>) {
	runApplication<ChatAppClientApplication>(*args)
}
