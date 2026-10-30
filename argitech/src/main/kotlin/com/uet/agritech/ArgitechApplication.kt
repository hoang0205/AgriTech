package com.uet.agritech

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
class ArgitechApplication

fun main(args: Array<String>) {
	runApplication<ArgitechApplication>(*args)
}
