package com.pos.models

enum class AppScreen { DIAGNOSTIC, CHECKOUT, SETTINGS }
enum class PrintJob { STANDARD, NFC, CHECKOUT }

data class Product(val name: String, val price: Double)
data class CartItem(val product: Product, val quantity: Int)