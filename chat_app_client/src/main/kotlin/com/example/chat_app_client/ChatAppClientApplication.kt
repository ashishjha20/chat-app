package com.example.chat_app_client

import com.example.chat_app_client.service.ChatClientImpl
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ChatAppClientApplication

fun main(args: Array<String>) {
	val context = runApplication<ChatAppClientApplication>(*args)
	context.getBean(ChatClientImpl::class.java).start()
	context.close()
}