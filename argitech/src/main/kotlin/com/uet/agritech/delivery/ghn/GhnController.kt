package com.uet.agritech.delivery.ghn

import com.uet.agritech.user.Role
import com.uet.agritech.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.security.Principal

@RestController
@RequestMapping("/api/delivery/ghn")
class GhnController(private val service: GhnShipmentService, private val users: UserRepository, private val shops: GhnShopService) {
    @GetMapping("/shop")
    fun shop(principal: Principal) = shops.view(seller(principal))

    @PostMapping("/shop/connect")
    fun connectShop(principal: Principal) = shops.connect(seller(principal))

    @PostMapping("/shop")
    fun createShop(principal: Principal, @RequestBody request: GhnShopRequest) = shops.register(seller(principal), request)

    @PutMapping("/shop/sender-address")
    fun senderAddress(principal: Principal, @RequestBody request: GhnSenderRequest) = shops.saveSender(seller(principal), request)

    @GetMapping("/shop/areas/{level}")
    fun shopAreas(principal: Principal, @PathVariable level: String, @RequestParam(required = false) parent: Int?): List<GhnArea> {
        seller(principal)
        return shops.areas(level, parent)
    }

    @GetMapping("/areas/{level}")
    fun areas(principal: Principal, @PathVariable level: String, @RequestParam(required = false) parent: Int?) =
        service.areas(seller(principal), level, parent)

    @GetMapping("/orders/{id}")
    fun view(principal: Principal, @PathVariable id: Long) = service.view(id, principal.name)

    @PostMapping("/orders/{id}/quote")
    fun quote(principal: Principal, @PathVariable id: Long, @RequestBody request: GhnParcelRequest) =
        service.quote(id, seller(principal), request)

    @PostMapping("/orders/{id}/reference")
    fun reference(principal: Principal, @PathVariable id: Long, @RequestBody request: GhnParcelRequest) =
        service.reference(id, seller(principal), request)

    @PostMapping("/orders/{id}/simulation")
    fun simulation(principal: Principal, @PathVariable id: Long) = service.simulation(id, seller(principal))

    @PostMapping("/orders/{id}")
    fun create(principal: Principal): Nothing {
        seller(principal)
        throw ResponseStatusException(HttpStatus.CONFLICT,
            "AgriTech chỉ dùng GHN để tham khảo cước. Hãy cập nhật app và chọn Lưu cước tham khảo.")
    }

    @PostMapping("/orders/{id}/retry")
    fun retry(principal: Principal, @PathVariable id: Long) = service.retry(id, seller(principal))

    @PostMapping("/orders/{id}/refresh")
    fun refresh(principal: Principal, @PathVariable id: Long) = service.refresh(id, principal.name)

    @PostMapping("/orders/{id}/release")
    fun release(principal: Principal, @PathVariable id: Long) = service.release(id, seller(principal))

    @PostMapping("/orders/{id}/cancel")
    fun cancel(principal: Principal, @PathVariable id: Long) = service.cancel(id, seller(principal))

    private fun seller(principal: Principal): String {
        val user = users.findByPhoneNumber(principal.name).orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED) }
        if (user.role != Role.FARMER) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ người bán được gửi hàng GHN.")
        return user.phoneNumber
    }
}
